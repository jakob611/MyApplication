package com.example.myapplication.network

import com.example.myapplication.BuildConfig
import com.example.myapplication.ui.screens.PlanResult
import com.example.myapplication.ui.screens.WeekPlan
import com.example.myapplication.ui.screens.DayPlan
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// Opomba: Če tega modula ne uporabljaš (ker kličeš PlanDataStore.requestAIPlan),
// ga lahko izbrišeš. Če ga uporabljaš, je posodobljen na Cloud Run + Authorization.
fun requestAIPlan(
    quizData: Map<String, Any>,
    onResult: (PlanResult?) -> Unit,
    onError: (String?) -> Unit
) {
    val baseUrl = BuildConfig.FITNESS_API_BASE_URL.ifBlank {
        "https://fitness-plan-api-551351477998.europe-west1.run.app"
    }
    val url = "${baseUrl.trimEnd('/')}/generate-plan"

    val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    val json = JSONObject().apply {
        put("user_data", JSONObject(quizData))
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

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message)

        override fun onResponse(call: Call, response: Response) {
            if (!response.isSuccessful) {
                onError("HTTP ${response.code}: ${response.body?.string()}"); return
            }
            val bodyString = response.body?.string() ?: return onError("Empty response")
            try {
                val jsonResponse = JSONObject(bodyString)
                val weeks = parseWeeksFromJson(jsonResponse.optJSONArray("weeks"))
                val plan = PlanResult(
                    id = java.util.UUID.randomUUID().toString(),
                    name = jsonResponse.optString("name", "AI Plan"),
                    calories = jsonResponse.optInt("calories", 2000),
                    protein = jsonResponse.optInt("protein", 150),
                    carbs = jsonResponse.optInt("carbs", 200),
                    fat = jsonResponse.optInt("fat", 50),
                    trainingPlan = jsonResponse.optString(
                        "suggestedWorkout",
                        jsonResponse.optString("trainingPlan", "Default training plan")
                    ),
                    trainingDays = jsonResponse.optInt("trainingDays", 3),
                    sessionLength = jsonResponse.optInt("sessionLength", 60),
                    tips = jsonResponse.optJSONArray("tips")?.let { arr ->
                        List(arr.length()) { i -> arr.getString(i) }
                    } ?: emptyList(),
                    createdAt = System.currentTimeMillis(),
                    trainingLocation = jsonResponse.optString("trainingLocation", ""),
                    weeks = weeks
                )
                onResult(plan)
            } catch (e: Exception) {
                onError("Parse error: ${e.message}")
            }
        }
    })
}

fun parseWeeksFromJson(weeksJson: JSONArray?): List<WeekPlan> {
    if (weeksJson == null) return emptyList()
    val weeks = mutableListOf<WeekPlan>()
    for (i in 0 until weeksJson.length()) {
        val weekObj = weeksJson.getJSONObject(i)
        val weekNumber = weekObj.optInt("weekNumber", i + 1)
        val daysJson = weekObj.optJSONArray("days") ?: JSONArray()
        val days = mutableListOf<DayPlan>()
        for (j in 0 until daysJson.length()) {
            val dayObj = daysJson.getJSONObject(j)
            val dayNumber = dayObj.optInt("dayNumber", j + 1)
            val exArray = dayObj.optJSONArray("exercises") ?: JSONArray()
            val exercises = (0 until exArray.length()).map { exArray.getString(it) }
            days.add(DayPlan(dayNumber, exercises))
        }
        weeks.add(WeekPlan(weekNumber, days))
    }
    return weeks
}