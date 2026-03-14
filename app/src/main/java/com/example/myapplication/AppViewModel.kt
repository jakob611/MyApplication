package com.example.myapplication

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.UserPreferences
import com.example.myapplication.data.UserProfile
import com.example.myapplication.persistence.FirestoreHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * AppViewModel — drži userProfile kot StateFlow.
 * Real-time Firestore listener kliče documentToUserProfile() direktno iz snapshota —
 * brez dvojnega branja. Korak 5a refaktoriranja.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile

    /**
     * Nastavi profil direktno (npr. ob loginu iz lokalnih prefs ali ob ročni posodobitvi).
     */
    fun setProfile(profile: UserProfile) {
        _userProfile.value = profile
    }

    /**
     * Požene real-time Firestore listener.
     * Ob vsakem snapshotu pokliče UserPreferences.documentToUserProfile() direktno —
     * ena sama logika mapiranja, brez podvojenega Firestore branja.
     */
    fun startListening(email: String) {
        viewModelScope.launch {
            try {
                val userRef = FirestoreHelper.getCurrentUserDocRef()
                userRef.addSnapshotListener { snap, error ->
                    if (error != null) {
                        Log.e("AppViewModel", "Firestore listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snap != null && snap.exists()) {
                        try {
                            _userProfile.value = UserPreferences.documentToUserProfile(snap, email)
                            Log.d("AppViewModel", "✅ userProfile refreshed from Firestore snapshot")
                        } catch (e: Exception) {
                            Log.e("AppViewModel", "Error mapping profile: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Error starting Firestore listener: ${e.message}")
            }
        }
    }
}
