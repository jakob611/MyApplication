package com.example.myapplication.persistence

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class MealFeedbackStore {
    private val firestore = Firebase.firestore
    private val uid: String? get() = FirestoreHelper.getCurrentUserDocId()

    // Feedback structure: users/{uid}/meal_feedback/{foodId} {like, recentUsage}
    suspend fun getLike(foodId: String): Int {
        val doc = firestore.collection("users")
            .document(uid ?: "no-user")
            .collection("meal_feedback")
            .document(foodId)
            .get().await()
        return (doc.getLong("like") ?: 0L).toInt()
    }

    suspend fun setLike(foodId: String, value: Int) {
        firestore.collection("users")
            .document(uid ?: "no-user")
            .collection("meal_feedback")
            .document(foodId)
            .set(mapOf("like" to value), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun incrementUsage(foodId: String) {
        firestore.collection("users")
            .document(uid ?: "no-user")
            .collection("meal_feedback")
            .document(foodId)
            .set(mapOf("recentUsage" to com.google.firebase.firestore.FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun getUsage(foodId: String): Int {
        val doc = firestore.collection("users")
            .document(uid ?: "no-user")
            .collection("meal_feedback")
            .document(foodId)
            .get().await()
        return (doc.getLong("recentUsage") ?: 0L).toInt()
    }
}