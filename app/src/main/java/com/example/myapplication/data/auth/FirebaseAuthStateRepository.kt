package com.example.myapplication.data.auth

import com.example.myapplication.domain.auth.AuthStateRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Faza 30.2 — Firebase implementacija AuthStateRepository vmesnika.
 *
 * Edina datoteka v projektu, ki sme klicati FirebaseAuth — presentation in domain
 * sloja tega ne smeta vedeti.
 *
 * observeCurrentUserEmail() emitira email ob spremembi auth stanja (login/logout)
 * in takoj ob naročnini.
 */
class FirebaseAuthStateRepository : AuthStateRepository {

    override fun observeCurrentUserEmail(): Flow<String?> = callbackFlow {
        val auth = FirebaseAuth.getInstance()

        // Pošlji začetno vrednost takoj
        trySend(auth.currentUser?.email)

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.email)
        }
        auth.addAuthStateListener(listener)

        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }
}

