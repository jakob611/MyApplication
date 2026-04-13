package com.example.myapplication.data.nutrition

import com.example.myapplication.network.FatSecretApi
import com.example.myapplication.network.FoodDetail
import com.example.myapplication.network.FoodSummary
import com.example.myapplication.network.RecipeDetail
import com.example.myapplication.network.RecipeSummary
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock

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

    suspend fun logCustomMeal(name: String, itemsList: List<Any>): String {
        val docRef = FirestoreHelper.getCurrentUserDocRef()
        val mealData = mapOf(
            "name" to name,
            "items" to itemsList,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // Zamenjavo klasičnega .add na Batch ali Transaction
        return FirebaseFirestore.getInstance().runTransaction { transaction ->
            val newRef = docRef.collection("customMeals").document()
            transaction.set(newRef, mealData)
            newRef.id
        }.await()
    }

    suspend fun deleteCustomMeal(mealId: String) {
        val docRef = FirestoreHelper.getCurrentUserDocRef()
        val mealRef = docRef.collection("customMeals").document(mealId)

        FirebaseFirestore.getInstance().runTransaction { transaction ->
            transaction.delete(mealRef)
        }.await()
    }

    fun observeCustomMeals(uid: String, onData: (com.google.firebase.firestore.QuerySnapshot?) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("customMeals")
            .addSnapshotListener { snaps, _ -> onData(snaps) }
    }

    fun observeDailyLog(uid: String, todayId: String, onData: (com.google.firebase.firestore.DocumentSnapshot?) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("dailyLogs").document(todayId)
            .addSnapshotListener { doc, _ -> onData(doc) }
    }

    suspend fun logWater(amountMl: Int, dateStr: String) {
        val docRef = FirestoreHelper.getCurrentUserDocRef()
        val dailyLogRef = docRef.collection("dailyLogs").document(dateStr)
        val data = mapOf(
            "date" to dateStr,
            "waterMl" to amountMl,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().runTransaction { transaction ->
            transaction.set(dailyLogRef, data, com.google.firebase.firestore.SetOptions.merge())
            null
        }.await()
    }
}
