package com.example.myapplication.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import com.example.myapplication.data.store.PlanDataStore
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.data.store.FirestoreHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class BodyOverviewViewmodel : ViewModel() {
    private val _currentUserId = MutableStateFlow<String?>(null)

    val plans: StateFlow<List<PlanResult>> = _currentUserId
        .filterNotNull()
        .flatMapLatest {
            PlanDataStore.plansFlow()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshPlans()
    }

    fun clearPlans() {
        _currentUserId.value = null
    }

    fun refreshPlans() {
        _currentUserId.value = FirestoreHelper.getCurrentUserDocId()
    }
}