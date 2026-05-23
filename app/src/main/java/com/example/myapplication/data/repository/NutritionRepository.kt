package com.example.myapplication.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Faza 29.8 — Repozitorijski vmesnik za prehranske operacije.
 *
 * NutritionViewModel NIKOLI ne referira FoodRepositoryImpl direktno.
 * Implementacija: FoodRepositoryImpl (production) / InMemoryNutritionRepository (testi)
 *
 * Pragmatična odločitev: vmesnik je v data sloju ker metode vračajo Firebase tipe
 * (DocumentSnapshot, QuerySnapshot). Za popoln KMP prehod bi se potrebovalo domenske modele.
 */
interface NutritionRepository {

    /** Shrani količino vode za ta dan v Firestore. */
    suspend fun logWater(amountMl: Int, dateStr: String)

    /** Shrani sledeno živilo v dailyLogs/{dateStr}/items. */
    suspend fun logFood(foodItem: Map<String, Any>, dateStr: String)

    /** Atomarno odstrani živilo in odšteje kalorije iz consumedCalories. */
    suspend fun removeFoodItem(foodId: String, dateStr: String, caloriesKcal: Double)

    /** Real-time Firestore listener za dnevni log (voda, kalorije, items). */
    fun observeDailyLog(uid: String, todayId: String): Flow<DocumentSnapshot>

    /** Real-time Firestore listener za shranjene custom meale. */
    fun observeCustomMeals(uid: String): Flow<QuerySnapshot>

    /** Naloži seznam živil iz custom obroka. null = ne obstaja. */
    suspend fun getCustomMealItems(uid: String, mealId: String): List<Map<String, Any>>?
}

