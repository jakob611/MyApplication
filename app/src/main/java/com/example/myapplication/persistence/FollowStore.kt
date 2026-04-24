package com.example.myapplication.persistence

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FollowStore {
    private val firestore = Firebase.firestore

    /**
     * Follow a user — atomarna transakcija.
     * Deterministic doc ID "${followerId}_${followingId}" prepreči dvojno sledenje
     * celo pri sočasnih klikih (race condition safe).
     */
    suspend fun followUser(
        followerId: String, // Kept for compatibility, but we resolve the real ID internally
        followingId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val followerRef = FirestoreHelper.getCurrentUserDocRef()
            val resolvedFollowerId = followerRef.id

            // Prepreči self-following
            if (resolvedFollowerId == followingId) return@withContext false

            val followingRef = FirestoreHelper.getUserRef(followingId)
            // Deterministični doc ID prepreči dvojni zapis (unikatna kombinacija sledilec+tarča)
            val followDocRef = firestore.collection("follows")
                .document("${resolvedFollowerId}_${followingId}")

            var alreadyFollowing = false

            // Ena atomarna transakcija: preveri sledenje + zapiši + posodobi števce
            firestore.runTransaction { transaction ->
                val followSnap   = transaction.get(followDocRef)
                val followerSnap = transaction.get(followerRef)
                val followingSnap = transaction.get(followingRef)

                if (followSnap.exists()) {
                    alreadyFollowing = true
                    return@runTransaction
                }

                val currentFollowers = followingSnap.getLong("followers")?.toInt() ?: 0
                val currentFollowing  = followerSnap.getLong("following")?.toInt()  ?: 0

                // Zapiši follow dokument z determinističnim ID
                transaction.set(followDocRef, mapOf(
                    "followerId"  to resolvedFollowerId,
                    "followingId" to followingId,
                    "followedAt"  to FieldValue.serverTimestamp()
                ))

                // Atomarno posodobi oba števca (set+merge deluje tudi če doc ne obstaja)
                transaction.set(followingRef, mapOf("followers" to currentFollowers + 1), SetOptions.merge())
                transaction.set(followerRef,  mapOf("following"  to currentFollowing  + 1), SetOptions.merge())
            }.await()

            if (!alreadyFollowing) {
                // Obvestilo (nekritično — zunaj transakcije je OK)
                try {
                    firestore.collection("notifications")
                        .document(followingId)
                        .collection("items")
                        .add(mapOf(
                            "type"       to "new_follower",
                            "fromUserId" to resolvedFollowerId,
                            "message"    to "started following you",
                            "timestamp"  to FieldValue.serverTimestamp(),
                            "read"       to false
                        )).await()
                } catch (e: Exception) {
                    Log.w("FollowStore", "Obvestilo ni bilo poslano (nekritično): ${e.message}")
                }
                Log.d("FollowStore", "User $resolvedFollowerId followed $followingId")
            }

            true
        } catch (e: Exception) {
            Log.e("FollowStore", "Error following user: ${e.message}")
            false
        }
    }

    /**
     * Unfollow a user — atomarna transakcija.
     * Najprej preveri deterministični doc ID (nov format), nato fallback za stare zapise.
     */
    suspend fun unfollowUser(
        followerId: String,
        followingId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val followerRef = FirestoreHelper.getCurrentUserDocRef()
            val resolvedFollowerId = followerRef.id
            val followingRef = FirestoreHelper.getUserRef(followingId)

            val deterministicDocRef = firestore.collection("follows")
                .document("${resolvedFollowerId}_${followingId}")

            var deletedViaTransaction = false

            // Transakcija za novi format (deterministični doc ID)
            firestore.runTransaction { transaction ->
                val followSnap   = transaction.get(deterministicDocRef)
                val followerSnap = transaction.get(followerRef)
                val followingSnap = transaction.get(followingRef)

                if (!followSnap.exists()) return@runTransaction

                val currentFollowers = (followingSnap.getLong("followers")?.toInt() ?: 1).coerceAtLeast(1)
                val currentFollowing  = (followerSnap.getLong("following")?.toInt()  ?: 1).coerceAtLeast(1)

                transaction.delete(deterministicDocRef)
                transaction.set(followingRef, mapOf("followers" to currentFollowers - 1), SetOptions.merge())
                transaction.set(followerRef,  mapOf("following"  to currentFollowing  - 1), SetOptions.merge())
                deletedViaTransaction = true
            }.await()

            if (!deletedViaTransaction) {
                // Fallback: stari format z naključnim ID — poišči in počisti
                val query = firestore.collection("follows")
                    .whereEqualTo("followerId", resolvedFollowerId)
                    .whereEqualTo("followingId", followingId)
                    .get().await()

                if (query.isEmpty) return@withContext false

                for (doc in query.documents) {
                    doc.reference.delete().await()
                }
                // Server-side decrement je varen za stare zapise (brez transakcije je OK tu)
                followingRef.update("followers", FieldValue.increment(-1)).await()
                followerRef.update("following",  FieldValue.increment(-1)).await()
            }

            Log.d("FollowStore", "User $resolvedFollowerId unfollowed $followingId")
            true
        } catch (e: Exception) {
            Log.e("FollowStore", "Error unfollowing user: ${e.message}")
            false
        }
    }

    /**
     * Check if user is following another user.
     * Preveri deterministični doc ID (hiter O(1)) + fallback query za stare zapise.
     */
    suspend fun isFollowing(
        followerId: String,
        followingId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolvedId = FirestoreHelper.getCurrentUserDocRef().id

            // Hitro preverjanje z determinističnim ID (nov format)
            val deterministicDoc = firestore.collection("follows")
                .document("${resolvedId}_${followingId}")
                .get().await()
            if (deterministicDoc.exists()) return@withContext true

            // Fallback: query za stare zapise
            val query = firestore.collection("follows")
                .whereEqualTo("followerId", resolvedId)
                .whereEqualTo("followingId", followingId)
                .limit(1)
                .get().await()
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
                    doc.reference.delete()
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
