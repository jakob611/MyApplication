package com.example.myapplication.persistence

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.BuildConfig
import com.example.myapplication.ui.screens.AlgorithmData
import com.example.myapplication.ui.screens.PlanResult
import com.example.myapplication.ui.screens.WeekPlan
import com.example.myapplication.ui.screens.DayPlan
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// Firebase imports
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore(name = "plans_datastore")

object PlanDataStore {
    private val PLANS_KEY = stringPreferencesKey("my_plans")
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private const val PLANS_COLLECTION = "user_plans"


    // Dodaj znotraj object PlanDataStore
    suspend fun migrateLocalPlansToFirestore(context: Context) {
        val userId = getCurrentUserId() ?: return
        try {
            // preberi lokalne plane
            val localPlans = loadLocalPlansAndSend(context)
            if (localPlans.isEmpty()) return

            // preveri, če user že ima plane v Firestore
            val snapshot = firestore.collection(PLANS_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (!snapshot.exists()) {
                // migriraj lokalne -> Firestore
                savePlans(context, localPlans)
                Log.d("PlanDataStore", "Migrated ${localPlans.size} local plans to Firestore")
            } else {
                Log.d("PlanDataStore", "User already has plans in Firestore, skipping migration")
            }
        } catch (e: Exception) {
            Log.e("PlanDataStore", "Error migrating local plans: ${e.message}")
        }
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun plansFlow(context: Context): Flow<List<PlanResult>> = callbackFlow {
        val userId = getCurrentUserId()
        Log.d("PlanDataStore", "plansFlow called with userId: $userId")

        if (userId == null) {
            Log.e("PlanDataStore", "User not logged in - returning empty list")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection(PLANS_COLLECTION)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PlanDataStore", "Error listening to plans: ${error.message}")
                    trySend(emptyList()); return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val plansData = snapshot.get("plans") as? List<Map<String, Any>>
                    val plans = plansData?.mapNotNull { convertMapToPlanResult(it) } ?: emptyList()
                    trySend(plans)
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose { listener.remove() }
    }

    private suspend fun loadLocalPlansAndSend(context: Context): List<PlanResult> {
        return try {
            val prefs = context.dataStore.data.first()
            val json = prefs[PLANS_KEY] ?: "[]"
            val type = object : TypeToken<List<PlanResult>>() {}.type
            Gson().fromJson<List<PlanResult>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("PlanDataStore", "Failed to load local plans: ${e.message}")
            emptyList()
        }
    }

    suspend fun savePlans(context: Context, plans: List<PlanResult>) {
        val userId = getCurrentUserId()
        saveToLocalDataStore(context, plans)

        if (userId == null) {
            Log.e("PlanDataStore", "Cannot save plans to Firestore: User not logged in")
            return
        }

        try {
            val plansData = plans.map { plan ->
                val planMap = hashMapOf<String, Any>(
                    "id" to plan.id,
                    "name" to plan.name,
                    "calories" to plan.calories,
                    "protein" to plan.protein,
                    "carbs" to plan.carbs,
                    "fat" to plan.fat,
                    "trainingPlan" to plan.trainingPlan,
                    "trainingDays" to plan.trainingDays,
                    "sessionLength" to plan.sessionLength,
                    "tips" to plan.tips,
                    "createdAt" to plan.createdAt,
                    "trainingLocation" to plan.trainingLocation,
                    "experience" to (plan.experience ?: ""),
                    "goal" to (plan.goal ?: ""),
                    "weeks" to plan.weeks.map { week ->
                        hashMapOf<String, Any>(
                            "weekNumber" to week.weekNumber,
                            "days" to week.days.map { day ->
                                hashMapOf<String, Any>(
                                    "dayNumber" to day.dayNumber,
                                    "exercises" to day.exercises
                                )
                            }
                        )
                    }
                )
                plan.algorithmData?.let { algo ->
                    planMap["algorithmData"] = hashMapOf<String, Any>(
                        "bmi" to algo.bmi,
                        "bmr" to algo.bmr,
                        "tdee" to algo.tdee,
                        "proteinPerKg" to algo.proteinPerKg,
                        "caloriesPerKg" to algo.caloriesPerKg,
                        "caloricStrategy" to algo.caloricStrategy,
                        "detailedTips" to algo.detailedTips,
                        "macroBreakdown" to algo.macroBreakdown,
                        "trainingStrategy" to algo.trainingStrategy
                    )
                }
                planMap
            }

            firestore.collection(PLANS_COLLECTION)
                .document(userId)
                .set(hashMapOf("plans" to plansData))
                .await()

            Log.d("PlanDataStore", "Plans saved successfully to Firestore")
        } catch (e: Exception) {
            Log.e("PlanDataStore", "Error saving plans to Firestore: ${e.message}")
        }
    }

    suspend fun addPlan(context: Context, newPlan: PlanResult) {
        val userId = getCurrentUserId()
        if (userId == null) {
            val localPlans = loadLocalPlansAndSend(context).toMutableList()
            localPlans.add(newPlan)
            saveToLocalDataStore(context, localPlans)
            return
        }

        try {
            val currentPlansSnapshot = firestore.collection(PLANS_COLLECTION)
                .document(userId)
                .get()
                .await()

            @Suppress("UNCHECKED_CAST")
            val currentPlansData = if (currentPlansSnapshot.exists()) {
                currentPlansSnapshot.get("plans") as? List<Map<String, Any>> ?: emptyList()
            } else emptyList()

            val currentPlans = currentPlansData.mapNotNull { convertMapToPlanResult(it) }.toMutableList()
            currentPlans.add(newPlan)
            savePlans(context, currentPlans)
        } catch (e: Exception) {
            Log.e("PlanDataStore", "Error adding plan to Firestore: ${e.message}")
            val localPlans = loadLocalPlansAndSend(context).toMutableList()
            localPlans.add(newPlan)
            saveToLocalDataStore(context, localPlans)
        }
    }

    suspend fun deletePlan(context: Context, planId: String) {
        val userId = getCurrentUserId()
        if (userId == null) {
            val localPlans = loadLocalPlansAndSend(context).filter { it.id != planId }
            saveToLocalDataStore(context, localPlans)
            return
        }

        try {
            val currentPlansSnapshot = firestore.collection(PLANS_COLLECTION)
                .document(userId)
                .get()
                .await()

            @Suppress("UNCHECKED_CAST")
            val currentPlansData = if (currentPlansSnapshot.exists()) {
                currentPlansSnapshot.get("plans") as? List<Map<String, Any>> ?: emptyList()
            } else emptyList()

            val updatedPlans = currentPlansData
                .mapNotNull { convertMapToPlanResult(it) }
                .filter { it.id != planId }

            savePlans(context, updatedPlans)
        } catch (e: Exception) {
            Log.e("PlanDataStore", "Error deleting plan from Firestore: ${e.message}")
            val localPlans = loadLocalPlansAndSend(context).filter { it.id != planId }
            saveToLocalDataStore(context, localPlans)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertMapToPlanResult(planMap: Map<String, Any>): PlanResult? {
        return try {
            val weeks = (planMap["weeks"] as? List<Map<String, Any>>)?.map { weekMap ->
                val weekNumber = (weekMap["weekNumber"] as? Number)?.toInt() ?: 1
                val days = (weekMap["days"] as? List<Map<String, Any>>)?.map { dayMap ->
                    val dayNumber = (dayMap["dayNumber"] as? Number)?.toInt() ?: 1
                    val exercises = dayMap["exercises"] as? List<String> ?: emptyList()
                    DayPlan(dayNumber, exercises)
                } ?: emptyList()
                WeekPlan(weekNumber, days)
            } ?: emptyList()

            PlanResult(
                id = planMap["id"] as? String ?: "",
                name = planMap["name"] as? String ?: "",
                calories = (planMap["calories"] as? Number)?.toInt() ?: 0,
                protein = (planMap["protein"] as? Number)?.toInt() ?: 0,
                carbs = (planMap["carbs"] as? Number)?.toInt() ?: 0,
                fat = (planMap["fat"] as? Number)?.toInt() ?: 0,
                trainingPlan = planMap["trainingPlan"] as? String ?: "",
                trainingDays = (planMap["trainingDays"] as? Number)?.toInt() ?: 0,
                sessionLength = (planMap["sessionLength"] as? Number)?.toInt() ?: 0,
                tips = planMap["tips"] as? List<String> ?: emptyList(),
                createdAt = (planMap["createdAt"] as? Number)?.toLong() ?: 0L,
                trainingLocation = planMap["trainingLocation"] as? String ?: "",
                experience = planMap["experience"] as? String,
                goal = planMap["goal"] as? String,
                weeks = weeks,
                algorithmData = (planMap["algorithmData"] as? Map<String, Any>)?.let { algoMap ->
                    AlgorithmData(
                        bmi = (algoMap["bmi"] as? Number)?.toDouble() ?: 0.0,
                        bmr = (algoMap["bmr"] as? Number)?.toDouble() ?: 0.0,
                        tdee = (algoMap["tdee"] as? Number)?.toDouble() ?: 0.0,
                        proteinPerKg = (algoMap["proteinPerKg"] as? Number)?.toDouble() ?: 0.0,
                        caloriesPerKg = (algoMap["caloriesPerKg"] as? Number)?.toDouble() ?: 0.0,
                        caloricStrategy = algoMap["caloricStrategy"] as? String ?: "",
                        detailedTips = algoMap["detailedTips"] as? List<String> ?: emptyList(),
                        macroBreakdown = algoMap["macroBreakdown"] as? String ?: "",
                        trainingStrategy = algoMap["trainingStrategy"] as? String ?: ""
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("PlanDataStore", "Error converting plan map: ${e.message}")
            null
        }
    }

    private suspend fun saveToLocalDataStore(context: Context, plans: List<PlanResult>) {
        try {
            val json = Gson().toJson(plans)
            context.dataStore.edit { prefs -> prefs[PLANS_KEY] = json }
        } catch (e: Exception) {
            Log.e("PlanDataStore", "Error saving to local datastore: ${e.message}")
        }
    }

    // KLJUČNO: klic na Cloud Run z Authorization headerjem
    fun requestAIPlan(
        quizData: Map<String, Any>,
        onResult: (PlanResult) -> Unit,
        onError: (String?) -> Unit
    ) {
        val baseUrl = BuildConfig.FITNESS_API_BASE_URL.ifBlank {
            "https://fitness-plan-api-551351477998.europe-west1.run.app"
        }
        val url = "${baseUrl.trimEnd('/')}/generate-plan"

        try {
            val currentUser = auth.currentUser
            val userId = currentUser?.uid
            val userEmail = currentUser?.email

            if (userId == null) {
                onError("User not logged in"); return
            }

            val json = JSONObject().apply {
                put("user_data", JSONObject().apply {
                    put("user_id", userId)
                    put("user_email", userEmail ?: "")
                    put("gender", quizData["gender"] ?: "")
                    put("age", quizData["age"] ?: "")
                    put("height", quizData["height"] ?: "")
                    put("weight", quizData["weight"] ?: "")
                    put("goal", quizData["goal"] ?: "")
                    put("experience", quizData["experience"] ?: "")
                    put("training_location", quizData["training_location"] ?: "")
                    put("trainingDays", quizData["trainingDays"] ?: 3)
                    put("limitations", JSONArray(quizData["limitations"] as? List<*> ?: emptyList<String>()))
                    put("nutrition", quizData["nutrition"] ?: "")
                    put("sleep", quizData["sleep"] ?: "")
                })
                put("timestamp", System.currentTimeMillis())
            }

            val body = RequestBody.create(
                "application/json; charset=utf-8".toMediaTypeOrNull(),
                json.toString()
            )

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer ${BuildConfig.BACKEND_API_KEY}")
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .callTimeout(240, TimeUnit.SECONDS)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("PlanDataStore", "AI request failed: ${e.message}")
                    if (e.message?.contains("timeout", ignoreCase = true) == true) {
                        onError("AI is taking longer than expected. Please try again or use the local plan.")
                    } else {
                        onError("Connection error: ${e.localizedMessage}")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        onError("Server error ${response.code}: $responseBody"); return
                    }

                    val planObj = try {
                        val jsonObject = JSONObject(responseBody ?: "{}")
                        val weeks = if (jsonObject.has("weeks")) {
                            parseWeeksFromJson(jsonObject.getJSONArray("weeks"))
                        } else emptyList()

                        PlanResult(
                            id = java.util.UUID.randomUUID().toString(),
                            name = jsonObject.optString("name", "AI Plan"),
                            calories = jsonObject.optInt("calories", 2000),
                            protein = jsonObject.optInt("protein", 150),
                            carbs = jsonObject.optInt("carbs", 200),
                            fat = jsonObject.optInt("fat", 50),
                            trainingPlan = jsonObject.optString(
                                "suggestedWorkout",
                                jsonObject.optString("trainingPlan", "Default training plan")
                            ),
                            trainingDays = jsonObject.optInt("trainingDays", 3),
                            sessionLength = jsonObject.optInt("sessionLength", 60),
                            tips = parseStringArray(jsonObject.optJSONArray("tips")),
                            createdAt = System.currentTimeMillis(),
                            trainingLocation = quizData["training_location"] as? String ?: "Home",
                            experience = jsonObject.optString("experience", null),
                            goal = jsonObject.optString("goal", null),
                            weeks = weeks,
                            algorithmData = null
                        )
                    } catch (e: Exception) {
                        Log.e("PlanDataStore", "Error parsing AI response: ${e.message}")
                        null
                    }

                    if (planObj != null) onResult(planObj) else onError("Failed to parse AI response")
                }
            })
        } catch (e: Exception) {
            Log.e("PlanDataStore", "AI request setup failed: ${e.message}")
            onError("Request setup error: ${e.localizedMessage}")
        }
    }

    private fun parseStringArray(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()
        return buildList {
            for (i in 0 until jsonArray.length()) add(jsonArray.getString(i))
        }
    }

    fun parseWeeksFromJson(weeksJson: JSONArray): List<WeekPlan> {
        val weeks = mutableListOf<WeekPlan>()
        for (i in 0 until weeksJson.length()) {
            val weekObj = weeksJson.getJSONObject(i)
            val weekNumber = weekObj.getInt("weekNumber")
            val daysJson = weekObj.getJSONArray("days")
            val days = mutableListOf<DayPlan>()
            for (j in 0 until daysJson.length()) {
                val dayObj = daysJson.getJSONObject(j)
                val dayNumber = dayObj.getInt("dayNumber")
                val exercisesJson = dayObj.getJSONArray("exercises")
                val exercises = mutableListOf<String>()
                for (k in 0 until exercisesJson.length()) {
                    exercises.add(exercisesJson.getString(k))
                }
                days.add(DayPlan(dayNumber, exercises))
            }
            weeks.add(WeekPlan(weekNumber, days))
        }
        return weeks
    }
}