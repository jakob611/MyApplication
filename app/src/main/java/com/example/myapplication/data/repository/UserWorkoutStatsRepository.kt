package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.settings.UserPreferencesRepository
import com.example.myapplication.domain.model.DomainException
import com.example.myapplication.domain.model.UserDayStatus
import com.example.myapplication.domain.repository.WorkoutStats
import com.example.myapplication.domain.repository.WorkoutStatsRepository
import com.example.myapplication.data.store.FirestoreHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Data-layer implementacija WorkoutStatsRepository.
 *
 * Bere podatke iz Firestore (prek FirestoreHelper) in lokalne SharedPrefs (fallback).
 * Wrappira UserProfileManager.getWorkoutStats() logiko v domenski interface.
 *
 * todayIsRest je vedno false — ViewModel izračuna isRestDay iz plan modela
 * (ki je presentation concern, ne domenski concern).
 *
 * @param prefs UserPreferencesRepository za lokalni fallback (SharedPrefs)
 */
class UserWorkoutStatsRepository(
    private val prefs: UserPreferencesRepository
) : WorkoutStatsRepository {

    companion object {
        private const val TAG = "UserWorkoutStatsRepo"
    }

    override suspend fun getWorkoutStats(email: String): WorkoutStats? {
        return try {
            val docRef = FirestoreHelper.getCurrentUserDocRef() ?: return null
            val doc = docRef.get().await()
            if (!doc.exists()) return null

            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            val todayStr = today.toString()

            @Suppress("UNCHECKED_CAST")
            val dailyHistory = (doc.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
            // Faza 21: pretvori surovi Firestore string v tipsko-varni UserDayStatus
            val todayStatus = UserDayStatus.fromFirestore(dailyHistory[todayStr]?.toString())

            // ✅ FIX: weeklyDone se izračuna dinamično iz dailyHistory tekočega tedna (pon–danes).
            // S tem se števec samodejno ponastavi ob novem tednu — ni potrebe po ločenem reset polju.
            val daysFromMonday = today.dayOfWeek.value - 1 // Monday.value=1, Sunday.value=7 → pon=0
            val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
            val weeklyDoneCalc = dailyHistory.entries.count { (dateStr, status) ->
                val s = status.toString()
                if (s != "WORKOUT_DONE" && s != "REST_WORKOUT_DONE") return@count false
                try { val d = LocalDate.parse(dateStr); d in monday..today } catch (_: Exception) { false }
            }

            WorkoutStats(
                streakDays = doc.getLong("streak_days")?.toInt() ?: 0,
                streakFreezes = doc.getLong("streak_freezes")?.toInt() ?: 0,
                weeklyDone = weeklyDoneCalc,
                weeklyTarget = doc.getLong("weekly_target")?.toInt() ?: 3,
                planDay = doc.getLong("plan_day")?.toInt() ?: 1,
                totalWorkoutsCompleted = doc.getLong("total_workouts_completed")?.toInt() ?: 0,
                lastWorkoutEpoch = doc.getLong("last_workout_epoch") ?: 0L,
                todayStatus = todayStatus,
                todayIsRest = false,  // ViewModel izračuna iz plan modela
                dailyKcal = prefs.getDailyCalories().toInt()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Faza 32.9 — Reaktivna Firestore poslušalnica z [callbackFlow].
     *
     * Faza 34 — CRIT-01 Fix: Firestore callback se zdaj izvaja na [Dispatchers.Default]
     * prek `Dispatchers.Default.asExecutor()` — O(n) LocalDate.parse() zanka in
     * TimeZone lookup ne blokirata več Main niti.
     * `.flowOn(Dispatchers.Default)` zagotavlja, da celotni upstream (builder blok +
     * awaitClose) teče na Default dispatcherju.
     *
     * Faza 34 — HIGH-03 Fix: Vsi [trySend] klici imajo validacijo rezultata —
     * zasičeni kanali ne izgubijo eventov tiho, ampak beležijo opozorilo.
     */
    override fun observeWorkoutStats(email: String): Flow<WorkoutStats?> = callbackFlow {
        val docRef = FirestoreHelper.getCurrentUserDocRef() ?: run {
            // Ni avtenticiranega uporabnika — propagiramo napako navzgor
            close(IllegalStateException("User not authenticated — cannot observe workout stats"))
            return@callbackFlow
        }

        // Pre-fetch lokalnih kalorij v callbackFlow suspend kontekstu (pred listener-jem)
        val localKcal = prefs.getDailyCalories().toInt()

        // Faza 34 — CRIT-01 Fix: Executor premakne callback na Dispatchers.Default —
        // O(n) mapiranje (LocalDate.parse, TimeZone lookup) se ne izvaja na Main niti.
        //
        // Faza 37 — Clean Architecture: FirebaseFirestoreException se prevede v platformsko-
        // nevtralni DomainException TUKAJ, v data sloju. Domain/presentation sloja NIKOLI
        // ne vidita Firebase tipov — izjemsko mapiranje je izključno odgovornost data sloja.
        val registration = docRef.addSnapshotListener(Dispatchers.Default.asExecutor()) { snapshot, error ->
            if (error != null) {
                val domainError = if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    DomainException.AuthenticationExpired
                } else {
                    DomainException.NetworkFailure(error.message ?: "Firestore listener error: ${error.code}")
                }
                close(domainError)
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                val r = trySend(null)
                if (r.isFailure) Log.w(TAG, "callbackFlow buffer poln — null snapshot event izgubljen")
                return@addSnapshotListener
            }

            try {
                val today = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                val todayStr = today.toString()

                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                val todayStatus  = UserDayStatus.fromFirestore(dailyHistory[todayStr]?.toString())

                val daysFromMonday = today.dayOfWeek.value - 1
                val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
                val weeklyDoneCalc = dailyHistory.entries.count { (dateStr, status) ->
                    val s = status.toString()
                    if (s != "WORKOUT_DONE" && s != "REST_WORKOUT_DONE") return@count false
                    try { val d = LocalDate.parse(dateStr); d in monday..today } catch (_: Exception) { false }
                }

                val r = trySend(WorkoutStats(
                    streakDays             = snapshot.getLong("streak_days")?.toInt() ?: 0,
                    streakFreezes          = snapshot.getLong("streak_freezes")?.toInt() ?: 0,
                    weeklyDone             = weeklyDoneCalc,
                    weeklyTarget           = snapshot.getLong("weekly_target")?.toInt() ?: 3,
                    planDay                = snapshot.getLong("plan_day")?.toInt() ?: 1,
                    totalWorkoutsCompleted = snapshot.getLong("total_workouts_completed")?.toInt() ?: 0,
                    lastWorkoutEpoch       = snapshot.getLong("last_workout_epoch") ?: 0L,
                    todayStatus            = todayStatus,
                    todayIsRest            = false,
                    dailyKcal              = localKcal
                ))
                if (r.isFailure) Log.w(TAG, "callbackFlow buffer poln — stats event izgubljen (Firestore emit preskočen)")
            } catch (e: Exception) {
                close(e)
            }
        }

        // Ko zbiralec prekine (navigacija, lifecycle, cancel) — odstrani poslušalnico
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.Default)  // Faza 34 — CRIT-01: celoten upstream na Default dispatcher

    override suspend fun isWorkoutDoneToday(): Boolean = prefs.isWorkoutDoneToday()

    override suspend fun getPlanDay(): Int = prefs.getPlanDay()

    override suspend fun getDailyCalories(): Int = prefs.getDailyCalories().toInt()
}