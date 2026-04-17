package com.example.myapplication.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.myapplication.persistence.PlanDataStore
import com.example.myapplication.data.PlanResult
import com.example.myapplication.persistence.FirestoreHelper
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