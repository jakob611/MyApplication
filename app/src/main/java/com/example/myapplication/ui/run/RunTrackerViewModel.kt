package com.example.myapplication.ui.run

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.service.RunTrackingService
import com.example.myapplication.domain.model.ActivityType
import com.example.myapplication.domain.model.LocationPoint
import com.example.myapplication.domain.model.RunSession
import com.example.myapplication.data.repository.OfflineFirstWorkoutRepository
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.repository.RunRepository
import com.example.myapplication.domain.repository.WorkoutRepository
import com.example.myapplication.domain.usecase.CalculateRunCaloriesUseCase
import com.example.myapplication.data.store.FirestoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Rezultat uspešno shranjene kardio seje.
 * Vrnjeno iz [RunTrackerViewModel.saveCurrentRunSession].
 */
data class SavedRunResult(
    val sessionId: String,
    val caloriesKcal: Int
)

/**
 * ViewModel za RunTrackerScreen — branje zgodovine tekov in shranjevanje zaključene seje.
 *
 * Faza 58b: Overhauled arhitektura.
 *  - K-1 Fix: sessionId prihaja DIREKTNO iz [RunTrackingService.activeSessionId].
 *  - K-3 Fix: Vse Firestore operacije so tukaj, ne v Screen-u — prek [RunRepository].
 *  - UDF: UI sproži event → ViewModel izvede I/O → UI prikaže stanje.
 *
 * @param workoutRepo         Legacy Firestore paginacija (load-more).
 * @param gamificationUseCase XP podeljevanje ob zaključku teka.
 * @param offlineRepo         Offline-first Room + delta sync (nullable za legacy tovarne).
 * @param runRepository       SSOT za Firestore shranjevanje kardio sej (K-3 fix).
 * @param calculateCaloriesUseCase UseCase za energetski izračun (V-4 + K-3 fix).
 */
class RunTrackerViewModel(
    private val workoutRepo: WorkoutRepository,
    private val gamificationUseCase: ManageGamificationUseCase,
    private val offlineRepo: OfflineFirstWorkoutRepository? = null,
    private val runRepository: RunRepository,
    private val calculateCaloriesUseCase: CalculateRunCaloriesUseCase = CalculateRunCaloriesUseCase()
) : ViewModel() {

    companion object {
        private const val TAG = "RunTrackerVM"
    }

    // ── Offline-First StateFlow ────────────────────────────────────────────
    /** Live seznam sej iz Room (0ms ob zagonu — ne čaka Firestore) */
    private val _sessions = MutableStateFlow<List<RunSession>>(emptyList())
    val sessions: StateFlow<List<RunSession>> = _sessions.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Save stanje — null=mirovanje, true=shranjujem, false=napaka */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        // Začni opazovati Room takoj — UI dobi podatke brez čakanja na Firestore
        offlineRepo?.sessionsFlow
            ?.onEach { _sessions.value = it }
            ?.launchIn(viewModelScope)
    }

    // ── Service binding (K-1 fix) ─────────────────────────────────────────
    /**
     * Referenca na vezani RunTrackingService.
     * @Volatile zagotavlja vidnost spremembe med Main in IO nitjo.
     */
    @Volatile private var runService: RunTrackingService? = null

    /**
     * Nastavi referenco na vezani Service.
     * Kliče se iz ServiceConnection.onServiceConnected() v Screen-u.
     */
    fun onServiceConnected(service: RunTrackingService) {
        runService = service
        Log.d(TAG, "Service connected — activeSessionId=${service.activeSessionId}")
    }

    /** Počisti referenco ob prekinitvi vezave. */
    fun onServiceDisconnected() {
        Log.d(TAG, "Service disconnected")
        runService = null
    }

    /**
     * Enolični ID trenutne GPS seje — SSOT, prihaja iz Service-a.
     * K-1 Fix: Screen NE sme generirati novega UUID za shranjevanje.
     *          Preberi ta ID pred gradnjo sessionData mape.
     * Vrne null ko service ne teče.
     */
    val activeSessionId: String? get() = runService?.activeSessionId

    // ── Firestore delta sync ───────────────────────────────────────────────
    /**
     * Prenese samo NOVE seje iz Firestore (od zadnjega lokalnega createdAt).
     * Room flow se samodejno posodobi → UI prikaže brez explicitnega klica.
     */
    fun syncFromFirestore() {
        offlineRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                offlineRepo.syncFromFirestore()
            } catch (e: Exception) {
                Log.e(TAG, "Sync napaka: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** Vrne Room GPS točke za sejo (surove > kompresiran fallback) */
    suspend fun getGpsPoints(sessionId: String): List<LocationPoint>? =
        offlineRepo?.getGpsPoints(sessionId)

    /** Zbriše sejo iz Room (CASCADE zbriše tudi GPS točke) */
    fun deleteFromRoom(sessionId: String) {
        offlineRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            offlineRepo.deleteSession(sessionId)
        }
    }

    // ── Live UI helpers ────────────────────────────────────────────────────
    /**
     * Izračuna kalorije za prikaz MED SLEDENJEM (live preview v Screen-u).
     * Delegira na [CalculateRunCaloriesUseCase] — Screen ne vsebuje poslovne logike.
     * Varna za klic v remember {} bloku — čisto izračunska funkcija brez stranskih učinkov.
     */
    fun calculateLiveCalories(
        activityType: ActivityType,
        durationSeconds: Long,
        distanceKm: Double,
        elevationGainM: Float,
        userWeightKg: Double
    ): Int = calculateCaloriesUseCase(
        CalculateRunCaloriesUseCase.Input(
            activityType = activityType,
            durationSeconds = durationSeconds,
            distanceKm = distanceKm,
            elevationGainM = elevationGainM,
            userWeightKg = userWeightKg
        )
    )

    // ── Gamification ───────────────────────────────────────────────────────
    fun awardRunXP(xp: Int) {
        viewModelScope.launch {
            gamificationUseCase.awardXP(xp, "RUN_COMPLETED")
        }
    }

    // ── Kardio seja — shranjevanje ─────────────────────────────────────────
    /**
     * Shrani zaključeno kardio sejo v Firestore prek [RunRepository].
     *
     * ## Bugfix log:
     *   - K-1 Fix: sessionId prihaja DIREKTNO iz [RunTrackingService.activeSessionId].
     *              Screen NE generira novega UUID → Room in Firestore dobita ENAK ID.
     *   - K-3 Fix: Firestore klici so tukaj, ne v Screen-u. UI ostane čist.
     *   - S-5 Fix: sessionStartTime prihaja iz Service-a, ne rekonstruiran iz elapsed časa.
     *
     * @param activityType       Tip aktivnosti (RUN, WALK, SPRINT, …)
     * @param distanceMeters     Prevožena razdalja v metrih
     * @param durationSeconds    Trajanje v sekundah (brez pavz)
     * @param maxSpeedMps        Maksimalna hitrost (m/s)
     * @param avgSpeedMps        Povprečna hitrost (m/s)
     * @param elevationGainM     Skupni vzpon (m)
     * @param elevationLossM     Skupni spust (m)
     * @param userWeightKg       Telesna teža za UseCase (prihaja iz profila)
     * @param compressedPoints   RDP-kompresirana pot za Firestore (iz RouteCompressor)
     * @param rawPointCount      Število surovih GPS točk (za statistiko)
     * @param isSmoothed         Ali je bila pot map-matched/smoothed
     * @param userId             Firestore user doc ID (iz FirestoreHelper)
     * @param sessionStartTime   Začetek seje (epoch ms) — prihaja iz Service-a (S-5 fix)
     * @param date               ISO-8601 datum (npr. "2026-06-03") za DailyLog posodobitev
     * @param shareActivity      Ali deliti aktivnost v publicActivities
     * @return [Result.success] z [SavedRunResult] ali [Result.failure] ob napaki
     */
    suspend fun saveCurrentRunSession(
        activityType: ActivityType,
        distanceMeters: Double,
        durationSeconds: Long,
        maxSpeedMps: Float,
        avgSpeedMps: Float,
        elevationGainM: Float,
        elevationLossM: Float,
        userWeightKg: Double,
        compressedPoints: List<Map<String, Any>>,
        rawPointCount: Int,
        isSmoothed: Boolean,
        userId: String,
        sessionStartTime: Long,
        date: String,
        shareActivity: Boolean
    ): Result<SavedRunResult> = withContext(Dispatchers.IO) {
        _isSaving.value = true
        runCatching {
            // ── K-1 Fix: sessionId iz Service-a, ne generiran v Screen-u ─────────
            val sessionId = runService?.activeSessionId
                ?: run {
                    Log.w(TAG, "⚠️ K-1 risk: Service ni vezan med shranjevanjem — generiram UUID kot fallback!")
                    UUID.randomUUID().toString()
                }

            // ── V-4 / K-3 Fix: UseCase izračuna kalorije — ne UI layer ───────────
            val caloriesKcal = calculateCaloriesUseCase(
                CalculateRunCaloriesUseCase.Input(
                    activityType = activityType,
                    durationSeconds = durationSeconds,
                    distanceKm = distanceMeters / 1000.0,
                    elevationGainM = elevationGainM,
                    userWeightKg = userWeightKg
                )
            )
            Log.d(TAG, "Izračunane kalorije: $caloriesKcal kcal za ${"%.2f".format(distanceMeters / 1000.0)} km, ${durationSeconds}s")

            val endTime = System.currentTimeMillis()

            // ── Gradnja standardizirane session mape ─────────────────────────────
            val sessionData: Map<String, Any> = buildMap {
                put("id", sessionId)
                put("userId", userId)
                put("startTime", sessionStartTime)          // S-5 Fix: iz Service-a
                put("endTime", endTime)
                put("durationSeconds", durationSeconds.toInt())
                put("distanceMeters", distanceMeters)
                put("maxSpeedMps", maxSpeedMps)
                put("avgSpeedMps", avgSpeedMps)
                put("caloriesKcal", caloriesKcal)           // V-4 Fix: realen izračun
                put("elevationGainM", elevationGainM)
                put("elevationLossM", elevationLossM)
                put("activityType", activityType.name)
                put("createdAt", endTime)
                put("polylinePoints", compressedPoints)
                put("pointsCount", rawPointCount)
                put("isSmoothed", isSmoothed)
            }

            // ── Shrani sejo prek Repository (K-3 Fix) ────────────────────────────
            runRepository.saveRunSession(sessionId, sessionData)
                .getOrElse { throw it }

            // ── Posodobi DailyLog burnedCalories ─────────────────────────────────
            runRepository.addBurnedCalories(date, caloriesKcal)
                .onFailure { Log.e(TAG, "addBurnedCalories spodletelo: ${it.message}") }

            // ── Javna aktivnost (opcijsko) ────────────────────────────────────────
            if (shareActivity && compressedPoints.isNotEmpty()) {
                val publicRoutePoints = compressedPoints
                    .take(500)
                    .map { point ->
                        buildMap<String, Any> {
                            (point["lat"] as? Double)?.let { put("lat", it) }
                            (point["lng"] as? Double)?.let { put("lng", it) }
                        }
                    }
                val publicData: Map<String, Any> = mapOf(
                    "activityType" to activityType.name,
                    "distanceMeters" to distanceMeters,
                    "elevationGainM" to elevationGainM,
                    "elevationLossM" to elevationLossM,
                    "avgSpeedMps" to avgSpeedMps,
                    "maxSpeedMps" to maxSpeedMps,
                    "caloriesKcal" to caloriesKcal,
                    "startTime" to sessionStartTime,
                    "routePoints" to publicRoutePoints
                )
                runRepository.savePublicActivity(sessionId, publicData)
                    .onFailure { Log.w(TAG, "savePublicActivity spodletelo (nekritično): ${it.message}") }
            }

            Log.d(TAG, "✅ Seja $sessionId uspešno shranjena ($caloriesKcal kcal, ${"%.1f".format(distanceMeters / 1000.0)} km)")
            SavedRunResult(sessionId = sessionId, caloriesKcal = caloriesKcal)
        }.also {
            _isSaving.value = false
            if (it.isFailure) Log.e(TAG, "❌ saveCurrentRunSession spodletelo: ${it.exceptionOrNull()?.message}")
        }
    }

    // ── Legacy paginacija (Firestore direktno) ─────────────────────────────
    private var lastVisibleDoc: Any? = null
    var isLastPage = false
        private set

    /**
     * Naloži pretekle teke iz Firestore z load-more paginacijo.
     * Offline-First: za osnovno nalaganje raje uporabi [sessions] flow.
     */
    fun loadRunSessions(isLoadMore: Boolean = false, onResult: (List<RunSession>) -> Unit) {
        val userId = FirestoreHelper.getCurrentUserDocId()
        if (userId == null) { onResult(emptyList()); return }
        if (isLoadMore && isLastPage) { onResult(emptyList()); return }
        if (!isLoadMore) { lastVisibleDoc = null; isLastPage = false }

        viewModelScope.launch {
            val (sessions, lastDoc) = workoutRepo.getRunSessions(userId, lastVisibleDoc, 15)
            lastVisibleDoc = lastDoc
            if (sessions.isEmpty() || sessions.size < 15) isLastPage = true
            onResult(sessions)
        }
    }
}


