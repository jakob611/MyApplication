package com.example.myapplication.viewmodels

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.UserPreferences
import com.example.myapplication.data.UserProfile
import com.example.myapplication.persistence.AchievementStore
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ShopState(
    val userXP: Int = 0,
    val streakFreezes: Int = 0,
    val coupons: List<String> = emptyList(),
    val isLoading: Boolean = false
)

class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ShopState())
    val state: StateFlow<ShopState> = _state

    private val context = app.applicationContext
    private val currentUserEmail: String
        get() = Firebase.auth.currentUser?.email ?: ""

    init {
        refreshData()
    }

    fun refreshData() {
        val email = currentUserEmail
        if (email.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val profile = UserPreferences.loadProfileFromFirestore(email)
                ?: UserPreferences.loadProfile(context, email)
            
            // Assume we store coupons in a subcollection or just local logic for now?
            // For MVP, just show XP and freezes.
            _state.value = _state.value.copy(
                userXP = profile.xp,
                streakFreezes = profile.streakFreezes
            )
        }
    }

    fun buyStreakFreeze() {
        val email = currentUserEmail
        if (email.isBlank()) return
        if (_state.value.isLoading) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true)
            try {
                // 1. Load fresh profile
                val profile = UserPreferences.loadProfileFromFirestore(email)
                    ?: UserPreferences.loadProfile(context, email)
                
                val PRICE = 300
                val MAX_FREEZES = 3

                if (profile.streakFreezes >= MAX_FREEZES) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Max 3 Streak Freezes allowed!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (profile.xp < PRICE) {
                    withContext(Dispatchers.Main) {
                         Toast.makeText(context, "Not enough XP! Need $PRICE XP.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 2. Transaction (Deduct XP, Add Freeze)
                val newXP = profile.xp - PRICE
                val newFreezes = profile.streakFreezes + 1
                
                val updatedProfile = profile.copy(
                    xp = newXP,
                    streakFreezes = newFreezes
                )

                // Save
                UserPreferences.saveProfile(context, updatedProfile)
                UserPreferences.saveProfileFirestore(updatedProfile)

                // Log XP spend
                try {
                    FirestoreHelper.getCurrentUserDocRef()
                        .collection("xp_history")
                        .add(mapOf(
                            "xp" to -PRICE,
                            "source" to "SHOP_SPEND",
                            "description" to "Bought Streak Freeze",
                            "timestamp" to System.currentTimeMillis()
                        ))
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                     Toast.makeText(context, "❄️ Streak Freeze Purchased!", Toast.LENGTH_SHORT).show()
                }
                
                // Refresh local state
                _state.value = _state.value.copy(
                    userXP = newXP,
                    streakFreezes = newFreezes
                )

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Purchase failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun buyCoupon() {
        // Mock implementation for discount
        val email = currentUserEmail
        if (email.isBlank() || _state.value.isLoading) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true)
            try {
                 val profile = UserPreferences.loadProfileFromFirestore(email)
                    ?: UserPreferences.loadProfile(context, email)
                
                val PRICE = 500
                if (profile.xp < PRICE) {
                    withContext(Dispatchers.Main) {
                         Toast.makeText(context, "Not enough XP! Need $PRICE XP.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Deduct XP
                val newXP = profile.xp - PRICE
                val updatedProfile = profile.copy(xp = newXP)
                
                UserPreferences.saveProfile(context, updatedProfile)
                UserPreferences.saveProfileFirestore(updatedProfile)

                // In real app, save coupon to database. Here just a toast.
                 withContext(Dispatchers.Main) {
                     Toast.makeText(context, "🎟️ Coupon 'PRO10' Unlocked!", Toast.LENGTH_LONG).show()
                }
                
                _state.value = _state.value.copy(userXP = newXP)

            } catch (e: Exception) {
                 withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Purchase failed", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}

