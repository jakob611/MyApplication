// Leftover file from KMP migration. Unused.
/*
package com.example.myapplication.data.auth

import com.example.myapplication.domain.auth.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseAuthRepositoryImpl : AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    override fun observeUserId(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { authState ->
            trySend(authState.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
}
*/

