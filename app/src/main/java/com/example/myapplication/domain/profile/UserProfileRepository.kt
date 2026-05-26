package com.example.myapplication.domain.profile

import com.example.myapplication.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    fun observeUserProfile(email: String): Flow<UserProfile>
}
