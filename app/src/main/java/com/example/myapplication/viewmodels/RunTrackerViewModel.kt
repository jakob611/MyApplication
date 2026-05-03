package com.example.myapplication.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.LocationPoint
import com.example.myapplication.data.RunSession
import com.example.myapplication.data.local.OfflineFirstWorkoutRepository
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.workout.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel za RunTrackerScreen — branje zgodovine tekov.
 * Faza 3: Offline-First razširitev — Room prvi, Firestore delta sync v ozadju.
 *
 * @param offlineRepo  Repository za Room + Firestore delta sync.
 *                     Null le pri legacy tovarnah brez Room podpore.
 */
class RunTrackerViewModel(
    private val workoutRepo: WorkoutRepository,
    private val gamificationUseCase: ManageGamificationUseCase,
    private val offlineRepo: OfflineFirstWorkoutRepository? = null
) : ViewModel() {

    // ── Offline-First StateFlow ────────────────────────────────────────────
    /** Live seznam sej iz Room (0ms ob zagonu — ne čaka Firestore) */
    private val _sessions = MutableStateFlow<List<RunSession>>(emptyList())
    val sessions: StateFlow<List<RunSession>> = _sessions.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        // Začni opazovati Room takoj — UI dobi podatke brez čakanja na Firestore
        offlineRepo?.sessionsFlow
            ?.onEach { _sessions.value = it }
            ?.launchIn(viewModelScope)
    }

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
                Log.e("RunTrackerVM", "Sync napaka: ${e.message}")
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

    // ── Gamification ───────────────────────────────────────────────────────
    fun awardRunXP(xp: Int) {
        viewModelScope.launch {
            gamificationUseCase.awardXP(xp, "RUN_COMPLETED")
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
        val userId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
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
