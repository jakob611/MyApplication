package com.example.myapplication.data.network

import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.domain.model.DayPlan
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.domain.model.WeekPlan
import com.example.myapplication.domain.network.PlanNetworkService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Faza 43 — Implementacija [PlanNetworkService] za klic Cloud Run AI API.
 *
 * IZLUŠČENO IZ: `data/store/PlanDataStore.requestAIPlan()` (SRP kršitev Anomaly 5).
 *
 * ODGOVORNOSTI (SAMO):
 *   - OkHttp klient instantiacija in konfiguracija (timeouts)
 *   - Sestavljanje HTTP POST zahteve na /generate-plan endpoint
 *   - JSON serializacija vhodnih kviz podatkov
 *   - JSON deserializacija odgovora v [PlanResult]
 *   - Wrapping callback-based OkHttp v Kotlin coroutine (suspendCancellableCoroutine)
 *
 * BREZ ODGOVORNOSTI:
 *   - Lokalni DataStore branje/pisanje (→ PlanDataStore)
 *   - Firestore sinhroniziacija (→ PlanDataStore)
 *   - Plan CRUD operacije (→ PlanDataStore)
 */
class PlanApiClient : PlanNetworkService {

    private val auth = FirebaseAuth.getInstance()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Asinhrono generira trening plan prek Cloud Run AI API.
     * OkHttp enqueue klic je zavit v [suspendCancellableCoroutine] za coroutine-friendly API.
     */
    override suspend fun generatePlan(quizData: Map<String, Any>): Result<PlanResult> =
        suspendCancellableCoroutine { continuation ->
            val baseUrl = BuildConfig.FITNESS_API_BASE_URL.ifBlank {
                "https://fitness-plan-api-551351477998.europe-west1.run.app"
            }
            val url = "${baseUrl.trimEnd('/')}/generate-plan"

            try {
                val currentUser = auth.currentUser
                val userId = currentUser?.uid
                val userEmail = currentUser?.email

                if (userId == null) {
                    continuation.resume(Result.failure(Exception("User not logged in")))
                    return@suspendCancellableCoroutine
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

                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("Authorization", "Bearer ${BuildConfig.BACKEND_API_KEY}")
                    .build()

                val call = httpClient.newCall(request)

                // Prekliči OkHttp klic ob Coroutine cancellation
                continuation.invokeOnCancellation { call.cancel() }

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "AI request failed: ${e.message}")
                        val error = if (e.message?.contains("timeout", ignoreCase = true) == true) {
                            "AI is taking longer than expected. Please try again or use the local plan."
                        } else {
                            "Connection error: ${e.localizedMessage}"
                        }
                        continuation.resume(Result.failure(Exception(error)))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        if (!response.isSuccessful) {
                            continuation.resume(
                                Result.failure(Exception("Server error ${response.code}: $responseBody"))
                            )
                            return
                        }

                        val planResult = try {
                            val jsonObject = JSONObject(responseBody ?: "{}")
                            val weeks = if (jsonObject.has("weeks")) {
                                parseWeeksFromJson(jsonObject.getJSONArray("weeks"))
                            } else emptyList()

                            PlanResult(
                                id = UUID.randomUUID().toString(),
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
                                experience = jsonObject.optString("experience").takeIf { it.isNotEmpty() },
                                goal = jsonObject.optString("goal").takeIf { it.isNotEmpty() },
                                weeks = weeks,
                                algorithmData = null
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing AI response: ${e.message}")
                            null
                        }

                        if (planResult != null) {
                            continuation.resume(Result.success(planResult))
                        } else {
                            continuation.resume(Result.failure(Exception("Failed to parse AI response")))
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "AI request setup failed: ${e.message}")
                continuation.resume(Result.failure(Exception("Request setup error: ${e.localizedMessage}")))
            }
        }

    // -----------------------------------------------------------------------
    // JSON parsing helpers — PREMAKNJENO IZ PlanDataStore (Faza 43 SRP fix)
    // -----------------------------------------------------------------------

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
                val exercisesJson = dayObj.optJSONArray("exercises") ?: JSONArray()
                val exercises = mutableListOf<String>()
                for (k in 0 until exercisesJson.length()) {
                    exercises.add(exercisesJson.getString(k))
                }
                val isRestDay = dayObj.optBoolean("isRestDay", false)
                val focusLabel = dayObj.optString("focusLabel", "")
                days.add(DayPlan(dayNumber, exercises, isRestDay, focusLabel))
            }
            weeks.add(WeekPlan(weekNumber, days))
        }
        return weeks
    }

    companion object {
        private const val TAG = "PlanApiClient"
    }
}

