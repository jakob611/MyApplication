package com.example.myapplication.persistence

import android.util.Log
import com.example.myapplication.data.PrivacySettings
import com.example.myapplication.data.PublicProfile
import com.example.myapplication.data.UserProfile
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object ProfileStore {
    private val firestore = Firebase.firestore

    /**
     * Update privacy settings for user
     */
    suspend fun updatePrivacySettings(
        email: String, // Kept for interface compatibility but ignored in favor of resolved ID
        settings: PrivacySettings
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val userRef = FirestoreHelper.getCurrentUserDocRef() // RESOLVED ID!

            val data = mapOf(
                "is_public_profile" to settings.isPublic,
                "show_level" to settings.showLevel,
                "show_badges" to settings.showBadges,
                "show_plan_path" to settings.showPlanPath,
                "show_challenges" to settings.showChallenges,
                "show_followers" to settings.showFollowers
            )

            userRef.update(data).await()

            Log.d("ProfileStore", "Updated privacy settings for ${userRef.id}")
            true
        } catch (e: Exception) {
            Log.e("ProfileStore", "Error updating privacy settings: ${e.message}")
            false
        }
    }

    /**
     * Get public profile - returns only data that user has marked as public
     */
    suspend fun getPublicProfile(userId: String): PublicProfile? = withContext(Dispatchers.IO) {
        try {
            // userId comes from search, so it is the correct document ID (Email or UID)
            val doc = FirestoreHelper.getUserRef(userId).get().await()

            if (!doc.exists()) return@withContext null


            // Check if profile is public
            val isPublic = doc.getBoolean("is_public_profile") ?: false
            if (!isPublic) return@withContext null

            // Get privacy flags
            val showLevel = doc.getBoolean("show_level") ?: false
            val showBadges = doc.getBoolean("show_badges") ?: false
            val showPlanPath = doc.getBoolean("show_plan_path") ?: false
            val showFollowers = doc.getBoolean("show_followers") ?: false

            // Get basic data
            val username = doc.getString("username") ?: ""
            val displayName = doc.getString("first_name") ?: ""

            // Filter data based on privacy settings
            PublicProfile(
                userId = userId,
                username = username,
                displayName = displayName,
                level = if (showLevel) (doc.get("xp") as? Number)?.toInt()?.let { xp ->
                    com.example.myapplication.data.UserProfile.calculateLevel(xp)
                } else null,
                badges = if (showBadges) {
                    when (val b = doc.get("badges")) {
                        is List<*> -> b.filterIsInstance<String>().map { badgeId ->
                            com.example.myapplication.data.BadgeDefinitions.getBadgeById(badgeId)
                                ?.copy(unlocked = true)
                        }.filterNotNull()
                        else -> null
                    }
                } else null,
                followers = if (showFollowers) (doc.get("followers") as? Number)?.toInt() else null,
                following = if (showFollowers) (doc.get("following") as? Number)?.toInt() else null,
                activePlanSummary = if (showPlanPath) {
                    // TODO: Get active plan summary if available
                    null
                } else null
            )
        } catch (e: Exception) {
            Log.e("ProfileStore", "Error getting public profile: ${e.message}")
            null
        }
    }

    /**
     * Search for public profiles by username, email, or first name
     */
    suspend fun searchPublicProfiles(query: String): List<PublicProfile> = withContext(Dispatchers.IO) {
        try {
            Log.d("ProfileStore", "🔍 Searching for '$query'...")
            // Search for users with public profiles
            val snapshot = firestore.collection("users")
                .whereEqualTo("is_public_profile", true)
                .get()
                .await()

            Log.d("ProfileStore", "🔍 Found ${snapshot.size()} public profiles total")

            val results = snapshot.documents.mapNotNull { doc ->
                val username = doc.getString("username") ?: ""
                val firstName = doc.getString("first_name") ?: ""
                val docId = doc.id // email or uid

                // Match against username, first name, or document ID (email)
                val matches = username.contains(query, ignoreCase = true) ||
                    firstName.contains(query, ignoreCase = true) ||
                    docId.contains(query, ignoreCase = true)

                Log.d("ProfileStore", "🔍 Doc ${docId}: username='$username', firstName='$firstName', matches=$matches")

                if (matches) {
                    getPublicProfile(docId)
                } else null
            }
            Log.d("ProfileStore", "🔍 Returning ${results.size} matching profiles")
            results
        } catch (e: Exception) {
            Log.e("ProfileStore", "Error searching profiles: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get top users by follower count
     */
    suspend fun getTopUsers(limit: Int = 10): List<PublicProfile> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("is_public_profile", true)
                .orderBy("followers", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                getPublicProfile(doc.id)
            }
        } catch (e: Exception) {
            Log.e("ProfileStore", "Error getting top users: ${e.message}")
            emptyList()
        }
    }
}
