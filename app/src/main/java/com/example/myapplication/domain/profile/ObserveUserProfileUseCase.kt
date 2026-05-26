package com.example.myapplication.domain.profile

import com.example.myapplication.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

class ObserveUserProfileUseCase(
    private val repository: UserProfileRepository
) {
    operator fun invoke(email: String): Flow<UserProfile> {
        return repository.observeUserProfile(email)
    }
}
