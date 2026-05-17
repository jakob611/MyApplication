package com.example.myapplication.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.data.store.FirestoreHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.LocalDate

class ProgressViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    private val uidFlow = MutableStateFlow<String?>(null)

    init {
        uidFlow.value = FirestoreHelper.getCurrentUserDocId()
    }

    val weightLogsState: StateFlow<List<Pair<LocalDate, Double>>> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList())
        else callbackFlow {
            val listener = FirestoreHelper.getUserRef(uid).collection("weightLogs")
                .addSnapshotListener { snap, _ ->
                    val logs = snap?.documents?.mapNotNull { d ->
                        val dateStr = d.getString("date") ?: d.id
                        val w = (d.get("weightKg") as? Number)?.toDouble() ?: return@mapNotNull null
                        val date = runCatching { LocalDate.Companion.parse(dateStr) }.getOrNull()
                            ?: return@mapNotNull null
                        date to w
                    }?.sortedBy { it.first } ?: emptyList()
                    trySend(logs)
                }
            awaitClose { listener.remove() }
        }
    }.stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), emptyList())

    fun awardWeightLogXP() {
        viewModelScope.launch {
            gamificationUseCase.awardXP(50, "WEIGHT_ENTRY")
        }
    }

    suspend fun saveWeightLog(uid: String, dateStr: String, weightKg: Double, onSaved: () -> Unit) {
        try {
            FirestoreHelper.getUserRef(uid)
                .collection("weightLogs").document(dateStr)
                .set(mapOf("date" to dateStr, "weightKg" to weightKg))
                .await()
            onSaved()
        } catch (_: Exception) {}
    }
}