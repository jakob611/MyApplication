package com.example.myapplication.data.nutrition

import com.example.myapplication.network.FatSecretApi
import com.example.myapplication.network.FoodDetail
import com.example.myapplication.network.FoodSummary
import com.example.myapplication.network.RecipeDetail
import com.example.myapplication.network.RecipeSummary
import com.example.myapplication.persistence.FirestoreHelper
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose

/**
 * Singleton implementacija repozitorija,
 * ki skrbi tako za FatSecret API Iskanje kot za Firestore logiranje.
 * UI ne sme več sam logirati v bazo.
 */
object FoodRepositoryImpl {

    suspend fun searchFoodByName(query: String, maxResults: Int = 20): List<FoodSummary> {
        return FatSecretApi.searchFoods(query, 1, maxResults)
    }

    suspend fun getFoodAutocomplete(query: String, maxResults: Int = 5): List<String> {
        return FatSecretApi.getFoodAutocomplete(query, maxResults).map { it.suggestion }
    }

    suspend fun getFoodDetail(id: String): FoodDetail {
        return FatSecretApi.getFoodDetail(id)
    }

    suspend fun searchRecipes(query: String, maxResults: Int = 20): List<RecipeSummary> {
        return FatSecretApi.searchRecipes(query, 1, maxResults)
    }

    suspend fun getRecipeDetail(id: String): RecipeDetail {
        return FatSecretApi.getRecipeDetail(id)
    }

    fun observeCustomMeals(uid: String): Flow<com.google.firebase.firestore.QuerySnapshot> = callbackFlow {
        val listener = FirestoreHelper.getUserRef(uid)
            .collection("customMeals")
            .addSnapshotListener { snaps, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snaps != null) {
                    trySend(snaps)
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeDailyLog(uid: String, todayId: String): Flow<com.google.firebase.firestore.DocumentSnapshot> = callbackFlow {
        val docRef = FirestoreHelper.getUserRef(uid).collection("dailyLogs").document(todayId)
        val listener = docRef.addSnapshotListener { doc, error ->
            android.util.Log.d("DEBUG_DATA", "Poslušam dokument: ${docRef.path}")
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (doc != null) {
                android.util.Log.d("DEBUG_DATA", "Novo stanje iz baze (dailyLog): ${doc.data}")
                trySend(doc)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun logCustomMeal(name: String, itemsList: List<Any>): String {
        val docRef = FirestoreHelper.getCurrentUserDocRef()
        val mealData = mapOf(
            "name" to name,
            "items" to itemsList,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // Zamenjavo klasičnega .add na Batch ali Transaction
        return FirestoreHelper.getDb().runTransaction { transaction ->
            val newRef = docRef.collection("customMeals").document()
            transaction.set(newRef, mealData)
            newRef.id
        }.await()
    }

    suspend fun deleteCustomMeal(mealId: String) {
        val docRef = FirestoreHelper.getCurrentUserDocRef()
        val mealRef = docRef.collection("customMeals").document(mealId)

        FirestoreHelper.getDb().runTransaction { transaction ->
            transaction.delete(mealRef)
        }.await()
    }

    suspend fun logWater(amountMl: Int, dateStr: String) {
        val docRef = FirestoreHelper.getCurrentUserDocRef()
        val dailyLogRef = docRef.collection("dailyLogs").document(dateStr)
        val data = mapOf(
            "date" to dateStr,
            "waterMl" to amountMl,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        FirestoreHelper.getDb().runTransaction { transaction ->
            transaction.set(dailyLogRef, data, com.google.firebase.firestore.SetOptions.merge())
            null
        }.await()
    }

    suspend fun logFood(foodItem: Map<String, Any>, dateStr: String) {
        val docRef = FirestoreHelper.getCurrentUserDocRef()
        val dailyLogRef = docRef.collection("dailyLogs").document(dateStr)
        val calories = (foodItem["caloriesKcal"] as? Number)?.toDouble() ?: 0.0

        FirestoreHelper.getDb().runTransaction { transaction ->
            val snapshot = transaction.get(dailyLogRef)
            val currentConsumed = (snapshot.get("consumedCalories") as? Number)?.toDouble() ?: 0.0
            val newConsumed = currentConsumed + calories

            val data = mutableMapOf<String, Any>(
                "consumedCalories" to newConsumed,
                "items" to com.google.firebase.firestore.FieldValue.arrayUnion(foodItem),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            // Ensure the date field is present
            if (!snapshot.exists() || !snapshot.contains("date")) {
                data["date"] = dateStr
            }

            transaction.set(dailyLogRef, data, com.google.firebase.firestore.SetOptions.merge())
            null
        }.await()
    }
}
