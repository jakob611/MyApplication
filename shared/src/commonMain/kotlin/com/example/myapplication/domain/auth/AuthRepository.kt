package com.example.myapplication.domain.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeUserId(): Flow<String?>
    fun getCurrentUserId(): String?
}
