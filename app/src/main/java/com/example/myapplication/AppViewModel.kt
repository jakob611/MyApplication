package com.example.myapplication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.profile.ObserveUserProfileUseCase
import com.example.myapplication.data.profile.FirestoreUserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AppIntent {
    data class StartListening(val email: String) : AppIntent()
    data class SetProfile(val profile: UserProfile) : AppIntent()
}

/**
 * AppViewModel — MVI vmesnik.
 * Drži stanje preko StateFlow in sprejema akcije preko handleIntent.
 */
class AppViewModel(
    private val observeUserProfileUseCase: ObserveUserProfileUseCase = ObserveUserProfileUseCase(
        FirestoreUserProfileRepository()
    )
) : ViewModel() {

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private var isListening = false

    fun handleIntent(intent: AppIntent) {
        when (intent) {
            is AppIntent.SetProfile -> {
                _userProfile.value = intent.profile
            }
            is AppIntent.StartListening -> {
                if (!isListening) {
                    isListening = true
                    viewModelScope.launch {
                        observeUserProfileUseCase(intent.email).collect { profile ->
                            _userProfile.value = profile
                        }
                    }
                }
            }
        }
    }
}
