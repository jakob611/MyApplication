package com.example.myapplication.data

import android.content.Context
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object XPManager {

    // XP rewards for different activities
    const val XP_WEIGHT_ENTRY = 10
    const val XP_WITHIN_20_CALORIE_GOAL = 25
    const val XP_STREAK = 5 // Per day of streak
    const val XP_WORKOUT_COMPLETED = 50
    const val XP_EXERCISE_COMPLETED = 15
    const val XP_RUN_COMPLETED = 40

    suspend fun awardXP(
        context: Context,
        xpAmount: Int,
        reason: String
    ) = withContext(Dispatchers.IO) {
        try {
            val user = Firebase.auth.currentUser ?: return@withContext
            val uid = user.uid
            val email = user.email ?: ""

            // Load current profile
            var currentProfile = UserPreferences.loadProfile(context, email)

            // Add XP
            val newXP = currentProfile.xp + xpAmount
            val updatedProfile = currentProfile.copy(xp = newXP)

            // Save locally
            UserPreferences.saveProfile(context, updatedProfile)

            // Save to Firestore
            UserPreferences.saveProfileFirestore(updatedProfile)

            // Log XP activity (optional, for stats)
            logXPActivity(uid, xpAmount, reason)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun logXPActivity(
        uid: String,
        xpAmount: Int,
        reason: String
    ) = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val data = mapOf(
                "xp" to xpAmount,
                "reason" to reason,
                "timestamp" to timestamp
            )
            Firebase.firestore
                .collection("users")
                .document(uid)
                .collection("xp_log")
                .add(data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

