package com.example.myapplication.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.myapplication.persistence.PlanDataStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose

class BodyOverviewViewmodel(private val context: Context) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    // StateFlow for current user ID
    private val _currentUserId = MutableStateFlow<String?>(null)

    // Plans flow that reacts to user changes
    val plans: StateFlow<List<PlanResult>> = _currentUserId
        .filterNotNull() // Only proceed when user is logged in
        .flatMapLatest { userId ->
            PlanDataStore.plansFlow(context)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Monitor auth state changes
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            // Listen to Firebase auth state changes
            val authStateFlow = callbackFlow<String?> {
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    trySend(auth.currentUser?.uid)
                }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }

            authStateFlow.collect { userId ->
                _currentUserId.value = userId
            }
        }
    }

    fun clearPlans() {
        _currentUserId.value = null
    }

    fun refreshPlans() {
        _currentUserId.value = auth.currentUser?.uid
    }
}