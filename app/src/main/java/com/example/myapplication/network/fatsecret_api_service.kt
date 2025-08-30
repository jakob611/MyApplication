package com.example.myapplication.network

import com.example.myapplication.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class FoodSummary(
    val id: String,
    val name: String,
    val brand: String?,
    val description: String?
)

data class FoodDetail(
    val id: String,
    val name: String,
    // osnovni makri
    val caloriesKcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    // prikaz porcije
    val servingDescription: String?,            // npr. "1 cup", "1 container"
    val numberOfUnits: Double? = null,          // npr. 0.75
    val measurementDescription: String? = null, // npr. "cup"
    // metrični del porcije
    val metricServingAmount: Double? = null,    // npr. 170
    val metricServingUnit: String? = null,      // "g" ali "ml" (včasih pride "oz" ali "fl oz")
    // dodatna polja (če obstajajo)
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    val saturatedFatG: Double? = null,
    val monounsaturatedFatG: Double? = null,
    val polyunsaturatedFatG: Double? = null,
    val transFatG: Double? = null,
    val cholesterolMg: Double? = null,
    val sodiumMg: Double? = null,
    val potassiumMg: Double? = null
)

object FatSecretApi {
    private fun baseUrl(): String {
        val fromConfig = BuildConfig.FATSECRET_BASE_URL
        return if (fromConfig.isNullOrBlank()) "https://fatsecret-551351477998.europe-west1.run.app" else fromConfig
    }

    private val client: OkHttpClient by lazy {
        val backendKey = BuildConfig.BACKEND_API_KEY ?: ""
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val rb = chain.request().newBuilder()
                    .header("User-Agent", "LooksMaxingApp/1.0 (Android)")
                    .header("Accept", "application/json")
                if (backendKey.isNotBlank()) {
                    rb.header("Authorization", "Bearer $backendKey")
                }
                chain.proceed(rb.build())
            }
            .build()
    }

    suspend fun searchFoods(q: String, page: Int = 1, pageSize: Int = 20): List<FoodSummary> =
        withContext(Dispatchers.IO) {
            if (q.isBlank()) return@withContext emptyList()
            val url = "${baseUrl().trimEnd('/')}/foods/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("pageSize", pageSize.toString())
                .build()

            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.body?.string()}")
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val foodsObj = root.optJSONObject("foods") ?: return@use emptyList<FoodSummary>()
                val foodNode = foodsObj.opt("food") ?: return@use emptyList<FoodSummary>()

                fun parseItem(obj: JSONObject): FoodSummary =
                    FoodSummary(
                        id = obj.optString("food_id"),
                        name = obj.optString("food_name"),
                        brand = obj.optString("brand_name", null),
                        description = obj.optString("food_description", null)
                    )

                when (foodNode) {
                    is JSONArray -> List(foodNode.length()) { i -> parseItem(foodNode.getJSONObject(i)) }
                    is JSONObject -> listOf(parseItem(foodNode))
                    else -> emptyList()
                }
            }
        }

    suspend fun getFoodDetail(foodId: String): FoodDetail =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl().trimEnd('/')}/foods/$foodId".toHttpUrl()
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.body?.string()}")
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val food = root.optJSONObject("food") ?: JSONObject()
                val name = food.optString("food_name", "")

                val servings = food.optJSONObject("servings")
                val servingNode = servings?.opt("serving")
                val firstServing: JSONObject? = when (servingNode) {
                    is JSONArray -> if (servingNode.length() > 0) servingNode.getJSONObject(0) else null
                    is JSONObject -> servingNode
                    else -> null
                }

                fun d(key: String): Double? = firstServing?.optString(key)?.toDoubleOrNull()

                FoodDetail(
                    id = foodId,
                    name = name,
                    caloriesKcal = d("calories"),
                    proteinG = d("protein"),
                    carbsG = d("carbohydrate"),
                    fatG = d("fat"),
                    servingDescription = firstServing?.optString("serving_description"),
                    numberOfUnits = d("number_of_units"),
                    measurementDescription = firstServing?.optString("measurement_description"),
                    metricServingAmount = d("metric_serving_amount"),
                    metricServingUnit = firstServing?.optString("metric_serving_unit"),
                    fiberG = d("fiber"),
                    sugarG = d("sugar"),
                    saturatedFatG = d("saturated_fat"),
                    monounsaturatedFatG = d("monounsaturated_fat"),
                    polyunsaturatedFatG = d("polyunsaturated_fat"),
                    transFatG = d("trans_fat"),
                    cholesterolMg = d("cholesterol"),
                    sodiumMg = d("sodium"),
                    potassiumMg = d("potassium")
                )
            }
        }

    // Priporočila iz FatSecret baze: več seed iskanj, deduplikacija, omejeno število
    suspend fun recommendedFoods(limit: Int = 8): List<FoodSummary> {
        val seeds = listOf(
            "chicken", "rice", "oats", "yogurt",
            "egg", "banana", "salad", "bread",
            "soup", "beef", "tuna", "apple"
        )
        // LinkedHashMap ohrani vrstni red in omogoča deduplikacijo po id
        val out = LinkedHashMap<String, FoodSummary>()
        for (q in seeds) {
            if (out.size >= limit) break
            val list = runCatching { searchFoods(q, page = 1, pageSize = 10) }
                .getOrElse { emptyList() }
            for (fs in list) {
                if (!out.containsKey(fs.id)) {
                    out[fs.id] = fs
                    if (out.size >= limit) break
                }
            }
        }
        return out.values.take(limit)
    }
}