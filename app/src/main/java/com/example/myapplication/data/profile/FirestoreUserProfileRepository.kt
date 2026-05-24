package com.example.myapplication.data.profile

import android.util.Log
import com.example.myapplication.data.UserProfile
import com.example.myapplication.data.settings.UserProfileManager
import com.example.myapplication.domain.profile.UserProfileRepository
import com.example.myapplication.data.store.FirestoreHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreUserProfileRepository : UserProfileRepository {
    /**
     * Faza 31.7 — Memory Leak fix:
     * Prejšnja implementacija je uporabljala `launch {}` znotraj `callbackFlow {}`.
     * `awaitClose {}` je tekel vzporedno z `launch` — če je bil flow preklican preden
     * je `launch` dosegel `addSnapshotListener()`, je bil `awaitClose { listener?.remove() }`
     * klican z `listener == null` (no-op). Naknadno nastavljen listener se NIKOLI ni odstranil
     * → trajno uhajanje pomnilnika (Memory Leak).
     *
     * Popravek: `getCurrentUserDocRef()` je suspend funkcija, ki jo pokličemo NEPOSREDNO
     * znotraj `callbackFlow` (ki je sam suspend kontekst). `awaitClose` je zdaj znotraj
     * `try` bloka, *po* uspešni dodelitvi listenerja → zagotovljeno čiščenje.
     */
    override fun observeUserProfile(email: String): Flow<UserProfile> = callbackFlow {
        try {
            val userRef = FirestoreHelper.getCurrentUserDocRef()
            val listener = userRef.addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("FirestoreUserProfileRep", "Firestore listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    try {
                        val profile = UserProfileManager.documentToUserProfile(snap, email)
                        trySend(profile)
                        Log.d("FirestoreUserProfileRep", "✅ userProfile refreshed from Firestore snapshot")
                    } catch (e: Exception) {
                        Log.e("FirestoreUserProfileRep", "Error mapping profile: ${e.message}")
                    }
                }
            }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            Log.e("FirestoreUserProfileRep", "Error starting Firestore listener: ${e.message}")
            close(e)
        }
    }
}
