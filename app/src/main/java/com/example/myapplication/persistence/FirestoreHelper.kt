package com.example.myapplication.persistence

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object FirestoreHelper {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private const val TAG = "FirestoreHelper"

    // Cache resolved doc ID to avoid repeated network calls
    @Volatile
    private var cachedResolvedId: String? = null
    @Volatile
    private var cachedForUid: String? = null

    /**
     * SYNCHRONOUS helper: returns the best user document ID without network calls.
     * Uses email if available (primary), else UID (fallback).
     *
     * IMPORTANT: For subcollections (weightLogs, dailyLogs etc.), data may exist
     * under UID if user was created before migration. Use resolvedDocId() (suspend)
     * for accurate resolution, or this for quick synchronous access.
     */
    fun getCurrentUserDocId(): String? {
        val user = auth.currentUser ?: return null
        // If we have a cached resolved ID for this user, use it
        if (cachedResolvedId != null && cachedForUid == user.uid) {
            return cachedResolvedId
        }
        // Otherwise, prefer email
        return user.email?.takeIf { it.isNotBlank() } ?: user.uid
    }

    /**
     * Resolves the correct document ID for the current user.
     * Hierarchy:
     * 1. Email (Priority)
     * 2. UID (Fallback)
     *
     * This method checks if a document exists with the Email.
     * If not, it checks if one exists with the UID.
     * If found by UID but not Email, it performs a MIGRATION to Email.
     * If neither exists, it returns Email (as the new default).
     */
    suspend fun getCurrentUserDocRef(): DocumentReference {
        val user = auth.currentUser ?: throw IllegalStateException("User not logged in")
        val email = user.email
        val uid = user.uid

        // validation of email as document id
        if (email.isNullOrBlank()) {
            Log.w(TAG, "User has no email, falling back to UID: $uid")
            cachedResolvedId = uid
            cachedForUid = uid
            return db.collection("users").document(uid)
        }

        val emailRef = db.collection("users").document(email)
        val uidRef = db.collection("users").document(uid)

        try {
            // Check if email doc exists
            val emailDoc = emailRef.get().await()
            if (emailDoc.exists()) {
                // Primary path exists, use it
                cachedResolvedId = email
                cachedForUid = uid
                return emailRef
            }

            // Check if UID doc exists (Legacy data)
            val uidDoc = uidRef.get().await()
            if (uidDoc.exists()) {
                Log.i(TAG, "Legacy UID document found. Migrating to Email: $email")

                // MIGRATE DATA - copy top-level document
                val data = uidDoc.data
                if (data != null) {
                    emailRef.set(data).await()
                    Log.i(TAG, "Migrated top-level user data from UID to Email")

                    // Migrate key subcollections
                    migrateSubcollection(uid, email, "weightLogs")
                    migrateSubcollection(uid, email, "dailyLogs")
                    migrateSubcollection(uid, email, "dailyMetrics")
                    migrateSubcollection(uid, email, "daily_health")
                    migrateSubcollection(uid, email, "customMeals")
                    migrateSubcollection(uid, email, "nutritionPlan")
                    migrateSubcollection(uid, email, "runSessions")
                }
                cachedResolvedId = email
                cachedForUid = uid
                return emailRef
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving user doc: ${e.message}")
        }

        // Default to Email for new users / empty state
        cachedResolvedId = email
        cachedForUid = uid
        return emailRef
    }

    /**
     * Migrate a subcollection from UID-based doc to Email-based doc.
     */
    private suspend fun migrateSubcollection(fromUid: String, toEmail: String, subcollectionName: String) {
        try {
            val srcDocs = db.collection("users").document(fromUid)
                .collection(subcollectionName).get().await()

            if (srcDocs.isEmpty) return

            val destRef = db.collection("users").document(toEmail).collection(subcollectionName)

            for (doc in srcDocs.documents) {
                val data = doc.data ?: continue
                destRef.document(doc.id).set(data).await()
            }
            Log.i(TAG, "Migrated subcollection '$subcollectionName': ${srcDocs.size()} docs")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to migrate subcollection '$subcollectionName': ${e.message}")
        }
    }

    /**
     * Helper to get document for ANY user (e.g. searching public profiles)
     */
    fun getUserRef(userId: String): DocumentReference {
        return db.collection("users").document(userId)
    }

    /**
     * Clear cache (call on logout)
     */
    fun clearCache() {
        cachedResolvedId = null
        cachedForUid = null
    }
}
