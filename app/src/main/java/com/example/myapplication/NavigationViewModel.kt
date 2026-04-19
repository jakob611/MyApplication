package com.example.myapplication

// =====================================================================
// NavigationViewModel.kt
// Drži navigacijski stack kot StateFlow — preživi screen rotation.
// Korak 5b refaktoriranja.
// =====================================================================

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NavigationViewModel : ViewModel() {

    // Stack prejšnjih zaslonov (brez trenutnega)
    private val _stack = MutableStateFlow<List<Screen>>(emptyList())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Index)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _previousScreen = MutableStateFlow<Screen>(Screen.Index)
    val previousScreen: StateFlow<Screen> = _previousScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            _stack.update { it + _currentScreen.value }
            _previousScreen.value = _currentScreen.value
            _currentScreen.value = screen
        }
    }

    fun clearStack() {
        _stack.value = emptyList()
    }

    fun popTo(screen: Screen) {
        val currentStack = _stack.value
        val index = currentStack.indexOfLast { it == screen }
        if (index != -1) {
            _previousScreen.value = _currentScreen.value
            _currentScreen.value = screen
            _stack.value = currentStack.take(index)
        } else {
            navigateTo(screen)
        }
    }

    /**
     * Navigira nazaj. Ker ima navigateBack() dostop do selectedPlan in isLoggedIn,
     * te vrednosti podamo kot parametre.
     * Vrne true če je bila akcija "finish app" (na Dashboard ko smo logiran).
     */
    fun navigateBack(
        isLoggedIn: Boolean,
        hasSelectedPlan: Boolean,
        onClearSelectedPlan: () -> Unit,
        onClearError: () -> Unit,
        onFinish: () -> Unit
    ) {
        val current = _currentScreen.value
        val stack = _stack.value

        when {
            hasSelectedPlan -> onClearSelectedPlan()
            current is Screen.MyAccount || current is Screen.Achievements -> {
                val previous = stack.lastOrNull()
                if (previous != null) {
                    _previousScreen.value = current
                    _currentScreen.value = previous
                    _stack.update { it.dropLast(1) }
                } else {
                    _currentScreen.value = if (isLoggedIn) Screen.Dashboard else Screen.Index
                }
            }
            current is Screen.ProFeatures -> {
                onClearError()
                _currentScreen.value = _previousScreen.value
            }
            current is Screen.ProSubscription -> {
                onClearError()
                _currentScreen.value = Screen.ProFeatures
            }
            current is Screen.Login -> {
                onClearError()
                _currentScreen.value = Screen.Index
            }
            stack.isNotEmpty() -> {
                val previous = stack.lastOrNull()
                if (previous != null) {
                    _previousScreen.value = current
                    _currentScreen.value = previous
                    _stack.update { it.dropLast(1) }
                } else {
                    _currentScreen.value = if (isLoggedIn) Screen.Dashboard else Screen.Index
                }
            }
            current is Screen.Dashboard -> {
                if (!isLoggedIn) _currentScreen.value = Screen.Index
                else onFinish()
            }
            isLoggedIn -> _currentScreen.value = Screen.Dashboard
            else -> _currentScreen.value = Screen.Index
        }
    }
}


