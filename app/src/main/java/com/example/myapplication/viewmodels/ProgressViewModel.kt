package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.awaitClose
import com.example.myapplication.persistence.FirestoreHelper
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
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@mapNotNull null
                        date to w
                    }?.sortedBy { it.first } ?: emptyList()
                    trySend(logs)
                }
            awaitClose { listener.remove() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun awardWeightLogXP() {
        viewModelScope.launch {
            gamificationUseCase.awardXP(50, "WEIGHT_ENTRY")
        }
    }

    suspend fun saveWeightLog(uid: String, dateStr: String, weightKg: Double, onSaved: () -> Unit) {
        try {
            com.example.myapplication.persistence.FirestoreHelper.getUserRef(uid)
                .collection("weightLogs").document(dateStr)
                .set(mapOf("date" to dateStr, "weightKg" to weightKg))
                .await()
            onSaved()
        } catch (_: Exception) {}
    }
}
