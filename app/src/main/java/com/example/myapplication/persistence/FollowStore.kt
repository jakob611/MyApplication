package com.example.myapplication.persistence

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FollowStore {
    private val firestore = Firebase.firestore

    /**
     * Follow a user
     */
    suspend fun followUser(
        followerId: String, // Kept for compatibility, but we resolve the real ID internally
        followingId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Resolve the current user's document ID (follower)
            val followerRef = FirestoreHelper.getCurrentUserDocRef()
            val resolvedFollowerId = followerRef.id

            // Prevent self-following
            if (resolvedFollowerId == followingId) return@withContext false

            // Check if already following
            if (isFollowing(resolvedFollowerId, followingId)) return@withContext true

            // Add to follows collection (flat structure)
            firestore.collection("follows")
                .add(mapOf(
                    "followerId" to resolvedFollowerId,
                    "followingId" to followingId,
                    "followedAt" to FieldValue.serverTimestamp()
                ))
                .await()

            // Increment follower count on TARGET user
            FirestoreHelper.getUserRef(followingId)
                .update("followers", FieldValue.increment(1))
                .await()

            // Increment following count on CURRENT user
            followerRef.update("following", FieldValue.increment(1))
                .await()

            // Create notification for followed user
            firestore.collection("notifications")
                .document(followingId)
                .collection("items")
                .add(mapOf(
                    "type" to "new_follower",
                    "fromUserId" to resolvedFollowerId,
                    "message" to "started following you",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "read" to false
                ))
                .await()

            Log.d("FollowStore", "User $resolvedFollowerId followed $followingId")
            true
        } catch (e: Exception) {
            Log.e("FollowStore", "Error following user: ${e.message}")
            false
        }
    }

    /**
     * Unfollow a user
     */
    suspend fun unfollowUser(
        followerId: String,
        followingId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val followerRef = FirestoreHelper.getCurrentUserDocRef()
            val resolvedFollowerId = followerRef.id
            val followingRef = FirestoreHelper.getUserRef(followingId)

            // Find relationship doc
            val query = firestore.collection("follows")
                .whereEqualTo("followerId", resolvedFollowerId)
                .whereEqualTo("followingId", followingId)
                .get()
                .await()

            if (query.isEmpty) return@withContext false

            for (doc in query.documents) {
                doc.reference.delete().await()
            }

            // Decrement follower count
            followingRef.update("followers", FieldValue.increment(-1))
                .await()

            // Decrement following count
            followerRef.update("following", FieldValue.increment(-1))
                .await()

            Log.d("FollowStore", "User $resolvedFollowerId unfollowed $followingId")
            true
        } catch (e: Exception) {
            Log.e("FollowStore", "Error unfollowing user: ${e.message}")
            false
        }
    }

    /**
     * Check if user is following another user
     */
    suspend fun isFollowing(
        followerId: String,
        followingId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolvedId = FirestoreHelper.getCurrentUserDocRef().id
            val query = firestore.collection("follows")
                .whereEqualTo("followerId", resolvedId)
                .whereEqualTo("followingId", followingId)
                .limit(1)
                .get()
                .await()
            !query.isEmpty
        } catch (e: Exception) {
            Log.e("FollowStore", "Error checking follow status: ${e.message}")
            false
        }
    }


    /**
     * Get list of followers
     */
    suspend fun getFollowers(userId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("follows")
                .whereEqualTo("followingId", userId)
                .get()
                .await()

            // Popravek self-follow buga: pobriši če uporabnik sledi sam sebi
            val validDocs = snapshot.documents.filter { doc ->
                val follower = doc.getString("followerId")
                if (follower == userId) {
                    doc.reference.delete() // Izbriši invaliden zapis v ozadju
                    false
                } else {
                    true
                }
            }

            val validFollowers = validDocs.mapNotNull { it.getString("followerId") }
            
            // Popravi count v Firestore, če ne ustreza realnemu številu
            try {
                val userRef = FirestoreHelper.getUserRef(userId)
                val userDoc = userRef.get().await()
                val currentCount = userDoc.getLong("followers")?.toInt() ?: 0
                if (currentCount != validFollowers.size) {
                    userRef.update("followers", validFollowers.size).await()
                }
            } catch (e: Exception) {
                Log.e("FollowStore", "Error syncing follower count: ${e.message}")
            }

            validFollowers
        } catch (e: Exception) {
            Log.e("FollowStore", "Error getting followers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get list of following
     */
    suspend fun getFollowing(userId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("follows")
                .whereEqualTo("followerId", userId)
                .get()
                .await()

            // Popravek self-follow buga: pobriši če uporabnik sledi sam sebi
            val validDocs = snapshot.documents.filter { doc ->
                val following = doc.getString("followingId")
                if (following == userId) {
                    doc.reference.delete()
                    false
                } else {
                    true
                }
            }

            val validFollowing = validDocs.mapNotNull { it.getString("followingId") }
            
            // Popravi count v Firestore, če ne ustreza realnemu številu
            try {
                val userRef = FirestoreHelper.getUserRef(userId)
                val userDoc = userRef.get().await()
                val currentCount = userDoc.getLong("following")?.toInt() ?: 0
                if (currentCount != validFollowing.size) {
                    userRef.update("following", validFollowing.size).await()
                }
            } catch (e: Exception) {
                Log.e("FollowStore", "Error syncing following count: ${e.message}")
            }

            validFollowing
        } catch (e: Exception) {
            Log.e("FollowStore", "Error getting following: ${e.message}")
            emptyList()
        }
    }
}
