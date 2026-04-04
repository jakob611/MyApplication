package com.example.myapplication.persistence

import android.util.Log
import com.example.myapplication.data.PublicActivity
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
                "show_streak" to settings.showStreak, // Add showStreak
                "show_plan_path" to settings.showPlanPath,
                "show_challenges" to settings.showChallenges,
                "show_followers" to settings.showFollowers,
                "share_activities" to settings.shareActivities
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
            val doc = FirestoreHelper.getUserRef(userId).get().await()
            mapToPublicProfile(doc, fetchActivities = true)
        } catch (e: Exception) {
            Log.e("ProfileStore", "Error getting public profile: ${e.message}")
            null
        }
    }

    /**
     * Internal mapper to convert DocumentSnapshot to PublicProfile
     */
    private suspend fun mapToPublicProfile(doc: com.google.firebase.firestore.DocumentSnapshot, fetchActivities: Boolean): PublicProfile? {
        if (!doc.exists()) return null

        // Check if profile is public
        val isPublic = doc.getBoolean("is_public_profile") ?: false
        if (!isPublic) return null

        // Get privacy flags
        val showLevel = doc.getBoolean("show_level") ?: false
        val showBadges = doc.getBoolean("show_badges") ?: false
        val showStreak = doc.getBoolean("show_streak") ?: false
        val showPlanPath = doc.getBoolean("show_plan_path") ?: false
        val showFollowers = doc.getBoolean("show_followers") ?: false
        val shareActivities = doc.getBoolean("share_activities") ?: false

        // Get basic data
        val userId = doc.id
        val username = doc.getString("username") ?: ""
        val displayName = doc.getString("first_name") ?: ""

        // Naloži javne aktivnosti (komprimirane rute) samo če je shareActivities=true AND fetchActivities=true
        val recentActivities: List<PublicActivity>? = if (shareActivities && fetchActivities) {
            try {
                val actSnap = firestore.collection("users").document(userId)
                    .collection("publicActivities")
                    .orderBy("startTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
                    .await()
                actSnap.documents.mapNotNull { d ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val rawPts = d.get("routePoints") as? List<Map<String, Any>> ?: emptyList()
                        val pts = rawPts.mapNotNull { pt ->
                            val lat = (pt["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                            val lng = (pt["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
                            Pair(lat, lng)
                        }
                        PublicActivity(
                            id = d.id,
                            activityType = d.getString("activityType") ?: "RUN",
                            distanceMeters = (d.get("distanceMeters") as? Number)?.toDouble() ?: 0.0,
                            durationSeconds = (d.get("durationSeconds") as? Number)?.toInt() ?: 0,
                            caloriesKcal = (d.get("caloriesKcal") as? Number)?.toInt() ?: 0,
                            elevationGainM = (d.get("elevationGainM") as? Number)?.toFloat() ?: 0f,
                            elevationLossM = (d.get("elevationLossM") as? Number)?.toFloat() ?: 0f,
                            avgSpeedMps = (d.get("avgSpeedMps") as? Number)?.toFloat() ?: 0f,
                            maxSpeedMps = (d.get("maxSpeedMps") as? Number)?.toFloat() ?: 0f,
                            startTime = (d.get("startTime") as? Number)?.toLong() ?: 0L,
                            routePoints = pts
                        )
                    } catch (_: Exception) { null }
                }
            } catch (_: Exception) { null }
        } else null

        // Filter data based on viewing permissions
        return PublicProfile(
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
            streak = if (showStreak) (doc.get("streak_days") as? Number)?.toInt() ?: 0 else null,
            followers = if (showFollowers) (doc.get("followers") as? Number)?.toInt() else null,
            following = if (showFollowers) (doc.get("following") as? Number)?.toInt() else null,
            activePlanSummary = if (showPlanPath) null else null,
            recentActivities = recentActivities
        )
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

                if (matches) {
                    // Use lightweight mapping (no activities) for lists
                    mapToPublicProfile(doc, fetchActivities = false)
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
     * Get ALL public profiles (for Community Screen)
     */
    suspend fun getAllPublicProfiles(): List<PublicProfile> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("is_public_profile", true)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                mapToPublicProfile(doc, fetchActivities = false)
            }
        } catch (e: Exception) {
            Log.e("ProfileStore", "Error getting all profiles: ${e.message}")
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
                mapToPublicProfile(doc, fetchActivities = false)
            }
        } catch (e: Exception) {
            Log.e("ProfileStore", "Error getting top users: ${e.message}")
            emptyList()
        }
    }
}
