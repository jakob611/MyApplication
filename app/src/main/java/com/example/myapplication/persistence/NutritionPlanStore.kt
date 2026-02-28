package com.example.myapplication.persistence

import android.util.Log
import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.ui.screens.AlgorithmData
import com.example.myapplication.utils.calculateAdvancedBMR
import com.example.myapplication.utils.calculateEnhancedTDEE
import com.example.myapplication.utils.calculateSmartCalories
import com.example.myapplication.utils.calculateOptimalMacros
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * NutritionPlanStore - Objekt za shranjevanje in posodabljanje nutrition plana
 * Nutrition plan se shranjuje ločeno od training plana in se avtomatsko posodablja ob vnosu teže
 */
object NutritionPlanStore {

    private val firestore = Firebase.firestore

    /**
     * Shrani nutrition plan v Firestore
     */
    suspend fun saveNutritionPlan(userId: String, plan: NutritionPlan) {
        try {
            val userRef = FirestoreHelper.getCurrentUserDocRef()

            // ...existing code...
            val data = mapOf(
                "calories" to plan.calories,
                "protein" to plan.protein,
                "carbs" to plan.carbs,
                "fat" to plan.fat,
                "lastUpdated" to plan.lastUpdated,
                "algorithmData" to plan.algorithmData?.let { algo ->
                    mapOf(
                        "bmi" to (algo.bmi ?: 0.0),
                        "bmr" to (algo.bmr ?: 0.0),
                        "tdee" to (algo.tdee ?: 0.0),
                        "proteinPerKg" to (algo.proteinPerKg ?: 0.0),
                        "caloriesPerKg" to (algo.caloriesPerKg ?: 0.0),
                        "caloricStrategy" to (algo.caloricStrategy ?: ""),
                        "detailedTips" to (algo.detailedTips ?: emptyList<String>()),
                        "macroBreakdown" to (algo.macroBreakdown ?: ""),
                        "trainingStrategy" to (algo.trainingStrategy ?: "")
                    )
                }
            )

            userRef.collection("nutritionPlan")
                .document("current")
                .set(data)
                .await()

            Log.d("NutritionPlanStore", "Nutrition plan saved successfully to ${userRef.id}")
        } catch (e: Exception) {
            Log.e("NutritionPlanStore", "Error saving nutrition plan: ${e.message}")
        }
    }

    /**
     * Naloži nutrition plan iz Firestore
     */
    suspend fun loadNutritionPlan(userId: String): NutritionPlan? {
        return try {
            val userRef = FirestoreHelper.getCurrentUserDocRef()

            val doc = userRef.collection("nutritionPlan")
                .document("current")
                .get()
                .await()

            if (!doc.exists()) return null
            // ...existing code...
            val algoMap = doc.get("algorithmData") as? Map<*, *>
            val algorithmData = algoMap?.let {
                AlgorithmData(
                    bmi = (it["bmi"] as? Number)?.toDouble(),
                    bmr = (it["bmr"] as? Number)?.toDouble(),
                    tdee = (it["tdee"] as? Number)?.toDouble(),
                    proteinPerKg = (it["proteinPerKg"] as? Number)?.toDouble(),
                    caloriesPerKg = (it["caloriesPerKg"] as? Number)?.toDouble(),
                    caloricStrategy = it["caloricStrategy"] as? String,
                    detailedTips = it["detailedTips"] as? List<String> ?: emptyList(),
                    macroBreakdown = it["macroBreakdown"] as? String,
                    trainingStrategy = it["trainingStrategy"] as? String
                )
            }

            NutritionPlan(
                calories = (doc.get("calories") as? Number)?.toInt() ?: 0,
                protein = (doc.get("protein") as? Number)?.toInt() ?: 0,
                carbs = (doc.get("carbs") as? Number)?.toInt() ?: 0,
                fat = (doc.get("fat") as? Number)?.toInt() ?: 0,
                lastUpdated = (doc.get("lastUpdated") as? Number)?.toLong() ?: System.currentTimeMillis(),
                algorithmData = algorithmData
            )
        } catch (e: Exception) {
            Log.e("NutritionPlanStore", "Error loading nutrition plan: ${e.message}")
            null
        }
    }

    /**
     * Recalculate nutrition plan glede na novo težo
     * Prebere UserProfile (parametri), najnovejšo težo iz weightLogs, in ponovno izračuna kalorije/makroje
     */
    suspend fun recalculateNutritionPlan(userId: String, newWeightKg: Double): Boolean {
        return try {
            Log.d("NutritionPlanStore", "🔥 START recalculation for userId=$userId, weight=$newWeightKg")

            // 1. Naloži UserProfile
            val userRef = FirestoreHelper.getCurrentUserDocRef()
            val profileDoc = userRef.get().await()

            if (!profileDoc.exists()) {
                Log.e("NutritionPlanStore", "❌ User profile NOT FOUND for userId=${userRef.id}")
                return false
            }
            Log.d("NutritionPlanStore", "✅ User profile loaded successfully from ${userRef.id}")


            val height = (profileDoc.get("height") as? Number)?.toDouble()
            val age = (profileDoc.get("age") as? Number)?.toInt()
            val gender = profileDoc.getString("gender")
            val activityLevel = profileDoc.getString("activityLevel")
            val experience = profileDoc.getString("experience")
            val bodyFat = profileDoc.getString("bodyFat")
            val goal = profileDoc.getString("workoutGoal")

            Log.d("NutritionPlanStore", "📊 Loaded params: height=$height, age=$age, gender=$gender, goal=$goal, activityLevel=$activityLevel")

            val limitations = when (val lim = profileDoc.get("limitations")) {
                is List<*> -> lim.filterIsInstance<String>()
                is String -> lim.split(',').filter { it.isNotBlank() }
                else -> emptyList()
            }

            val nutritionStyle = profileDoc.getString("nutritionStyle")
            val sleepHours = profileDoc.getString("sleepHours")

            // 2. Preveri ali so vsi potrebni parametri prisotni
            if (height == null || age == null || gender == null) {
                Log.e("NutritionPlanStore", "Missing required parameters (height, age, gender)")
                return false
            }

            // 3. VALIDACIJA: Preveri ali so parametri smiselni
            if (height <= 0 || height > 300) {
                Log.e("NutritionPlanStore", "Invalid height: $height cm (must be 0-300)")
                return false
            }
            if (age <= 0 || age > 150) {
                Log.e("NutritionPlanStore", "Invalid age: $age (must be 0-150)")
                return false
            }
            if (newWeightKg <= 0 || newWeightKg > 500) {
                Log.e("NutritionPlanStore", "Invalid weight: $newWeightKg kg (must be 0-500)")
                return false
            }

            // 4. Izračunaj BMI, BMR, TDEE, kalorije, makroje
            val heightM = height / 100.0
            val bmi = newWeightKg / (heightM * heightM)
            val isMale = gender == "Male"
            val bodyFatPercent = bodyFat?.toDoubleOrNull()

            val bmr = calculateAdvancedBMR(newWeightKg, height, age, isMale, bodyFatPercent)
            val tdee = calculateEnhancedTDEE(bmr, activityLevel, experience, age, limitations, sleepHours)
            val targetCalories = calculateSmartCalories(tdee, goal, experience, bmi, age, isMale, bodyFatPercent, limitations)
            val macros = calculateOptimalMacros(targetCalories, newWeightKg, goal, experience, age, isMale, bodyFatPercent, nutritionStyle, limitations)

            val proteinPerKg = macros.first.toDouble() / newWeightKg
            val caloriesPerKg = targetCalories / newWeightKg

            fun getBMICategory(bmi: Double): String = when {
                bmi < 18.5 -> "Underweight"
                bmi < 25.0 -> "Normal weight"
                bmi < 30.0 -> "Overweight"
                else -> "Obese"
            }

            // 4. Ustvari AlgorithmData
            val algorithmData = AlgorithmData(
                bmi = bmi,
                bmr = bmr,
                tdee = tdee,
                proteinPerKg = proteinPerKg,
                caloriesPerKg = caloriesPerKg,
                caloricStrategy = "Calculated deficit/surplus: ${"%.0f".format(tdee - targetCalories)} kcal",
                detailedTips = listOf(
                    "BMI: ${"%.1f".format(bmi)} - ${getBMICategory(bmi)}",
                    "BMR: ${bmr.toInt()} kcal (basal metabolic rate)",
                    "TDEE: ${tdee.toInt()} kcal (total daily energy expenditure)",
                    "Protein goal: ${"%.1f".format(proteinPerKg)}g per kg body weight",
                    "Daily caloric need: ${"%.0f".format(targetCalories)} kcal",
                    "Training frequency: ${activityLevel} optimal for ${experience ?: "your"} level",
                    "Sleep optimization: 8-9 hours recommended for recovery"
                ),
                macroBreakdown = "Protein: ${"%.1f".format(proteinPerKg)}g/kg (${macros.first}g total), " +
                        "Carbs: ${macros.second}g, Fat: ${macros.third}g, " +
                        "Calories: ${"%.0f".format(targetCalories)} kcal/day",
                trainingStrategy = null
            )

            // 5. Ustvari nov NutritionPlan
            val newPlan = NutritionPlan(
                calories = targetCalories.toInt(),
                protein = macros.first,
                carbs = macros.second,
                fat = macros.third,
                algorithmData = algorithmData,
                lastUpdated = System.currentTimeMillis()
            )

            // 6. Shrani v Firestore
            saveNutritionPlan(userId, newPlan)

            Log.d("NutritionPlanStore", "Nutrition plan recalculated: ${newPlan.calories} kcal, P:${newPlan.protein}g C:${newPlan.carbs}g F:${newPlan.fat}g")
            true
        } catch (e: Exception) {
            Log.e("NutritionPlanStore", "Error recalculating nutrition plan: ${e.message}", e)
            false
        }
    }
}
