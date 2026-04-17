package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.RunSession
import com.example.myapplication.domain.workout.WorkoutRepository
import kotlinx.coroutines.launch

/**
 * ViewModel za RunTrackerScreen — samo branje zgodovine tekov.
 * Dejansko sledenje teka izvaja RunTrackingService (foreground service).
 */
class RunTrackerViewModel(private val workoutRepo: WorkoutRepository) : ViewModel() {

    private var lastVisibleDoc: Any? = null
    var isLastPage = false
        private set

    /**
     * Naloži pretekle teke iz Firestore z load-more paginacijo.
     */
    fun loadRunSessions(isLoadMore: Boolean = false, onResult: (List<RunSession>) -> Unit) {
        val userId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        if (userId == null) {
            onResult(emptyList())
            return
        }

        if (isLoadMore && isLastPage) {
            onResult(emptyList())
            return
        }

        if (!isLoadMore) {
            lastVisibleDoc = null
            isLastPage = false
        }

        viewModelScope.launch {
            val (sessions, lastDoc) = workoutRepo.getRunSessions(userId, lastVisibleDoc, 15)
            lastVisibleDoc = lastDoc
            if (sessions.isEmpty() || sessions.size < 15) {
                isLastPage = true
            }
            onResult(sessions)
        }
    }
}
