package com.example.myapplication.data.profile

import android.util.Log
import com.example.myapplication.data.UserPreferences
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.profile.UserProfileRepository
import com.example.myapplication.persistence.FirestoreHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class FirestoreUserProfileRepository : UserProfileRepository {
    override fun observeUserProfile(email: String): Flow<UserProfile> = callbackFlow {
        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        launch {
            try {
                val userRef = FirestoreHelper.getCurrentUserDocRef()
                listener = userRef.addSnapshotListener { snap, error ->
                    if (error != null) {
                        Log.e("FirestoreUserProfileRep", "Firestore listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snap != null && snap.exists()) {
                        try {
                            val profile = UserPreferences.documentToUserProfile(snap, email)
                            trySend(profile)
                            Log.d("FirestoreUserProfileRep", "✅ userProfile refreshed from Firestore snapshot")
                        } catch (e: Exception) {
                            Log.e("FirestoreUserProfileRep", "Error mapping profile: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreUserProfileRep", "Error starting Firestore listener: ${e.message}")
                close(e)
            }
        }
        awaitClose { listener?.remove() }
    }
}
