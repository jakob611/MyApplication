package com.example.myapplication.temp

import com.example.myapplication.data.UserProfile
import kotlinx.coroutines.flow.Flow

class ObserveUserProfileUseCase(
    private val repository: UserProfileRepository
) {
    operator fun invoke(email: String): Flow<UserProfile> {
        return repository.observeUserProfile(email)
    }
}
