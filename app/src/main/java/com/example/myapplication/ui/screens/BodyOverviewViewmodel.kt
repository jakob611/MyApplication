package com.example.myapplication.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.domain.repository.PlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Faza 41 — Clean Architecture: Anomaly 2 Fix.
 * ViewModel NE sme uvažati iz data paketa (PlanDataStore, FirestoreHelper).
 * Samo domenski vmesnik PlanRepository je dovoljen.
 *
 * Klic chain: BodyOverviewVM → PlanRepository (domain) → PlanRepositoryImpl (data) → PlanDataStore
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyOverviewViewmodel(
    private val planRepository: PlanRepository
) : ViewModel() {

    private val _isActive = MutableStateFlow(true)

    val plans: StateFlow<List<PlanResult>> = _isActive
        .filter { it }
        .flatMapLatest {
            planRepository.observePlans()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearPlans() {
        _isActive.value = false
    }

    fun refreshPlans() {
        // Reaktivni Flow se samodejno osveži ob spremembi v Firestore.
        // Ta metoda zagotavlja, da je flow aktiven (ob ponovni prijavi).
        _isActive.value = true
    }
}