package com.example.myapplication.persistence

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * MealFeedbackStore — shranjuje všečke in pogostost uporabe hrane za posameznega uporabnika.
 *
 * Firestore struktura: users/{email}/meal_feedback/{foodId}
 *   - like: Int (1 = všeček, -1 = ni všeček, 0 = nič)
 *   - recentUsage: Int (število uporab)
 *
 * VARNOST: vse funkcije takoj vrnejo privzeto vrednost če uporabnik ni prijavljen —
 * nikoli ne pišemo na users/no-user/ pot.
 */
class MealFeedbackStore {
    private val firestore = Firebase.firestore

    // uid je property (ne val) — pridobi vsakič svežo vrednost, ker se user lahko odjavi/prijavi
    private val uid: String? get() = FirestoreHelper.getCurrentUserDocId()

    // Pomožna funkcija — vrne DocumentReference ali null če ni prijavljen
    // Namesto uid ?: "no-user" povsod
    private fun feedbackRef(foodId: String) = uid?.let {
        firestore.collection("users").document(it)
            .collection("meal_feedback").document(foodId)
    }

    suspend fun getLike(foodId: String): Int {
        val ref = feedbackRef(foodId) ?: return 0  // ni prijavljen → vrni 0
        return (ref.get().await().getLong("like") ?: 0L).toInt()
    }

    suspend fun setLike(foodId: String, value: Int) {
        val ref = feedbackRef(foodId) ?: return  // ni prijavljen → preskoči
        ref.set(mapOf("like" to value), SetOptions.merge()).await()
    }

    suspend fun incrementUsage(foodId: String) {
        val ref = feedbackRef(foodId) ?: return  // ni prijavljen → preskoči
        ref.set(
            mapOf("recentUsage" to FieldValue.increment(1)),
            SetOptions.merge()
        ).await()
    }

    suspend fun getUsage(foodId: String): Int {
        val ref = feedbackRef(foodId) ?: return 0  // ni prijavljen → vrni 0
        return (ref.get().await().getLong("recentUsage") ?: 0L).toInt()
    }
}