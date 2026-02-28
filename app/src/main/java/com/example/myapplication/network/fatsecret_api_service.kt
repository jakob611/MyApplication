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
    val description: String?,
    val imageUrl: String? = null
)

data class RecipeSummary(
    val id: String,
    val name: String,
    val description: String?,
    val caloriesKcal: Int?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val imageUrl: String? = null
)

data class RecipeDetail(
    val id: String,
    val name: String,
    val description: String?,
    val caloriesKcal: Int?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val servings: Int?,
    val prepTimeMin: Int?,
    val cookTimeMin: Int?,
    val directions: List<String>?,
    val ingredients: List<String>?,
    val imageUrl: String? = null
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
    val potassiumMg: Double? = null,
    val imageUrl: String? = null
)

data class AutocompleteSuggestion(
    val suggestion: String
)

data class BarcodeResult(
    val foodId: String?,
    val foodName: String?
)

object FatSecretApi {
    private const val TAG = "FatSecretAPI"
    private const val OPENFOODFACTS_API = "https://world.openfoodfacts.org/cgi/search.pl"

    init {
        // Debug logging ob inicializaciji
        android.util.Log.d(TAG, "=== FatSecretApi Initialization ===")
        android.util.Log.d(TAG, "BuildConfig.FATSECRET_BASE_URL: '${BuildConfig.FATSECRET_BASE_URL}'")
        android.util.Log.d(TAG, "BuildConfig.BACKEND_API_KEY: ${if (BuildConfig.BACKEND_API_KEY.isNullOrBlank()) "EMPTY" else "***SET***"}")
        android.util.Log.d(TAG, "Resolved baseUrl: ${baseUrl()}")
    }

    /**
     * Try to get food image from OpenFoodFacts as fallback
     */
    private suspend fun getOpenFoodFactsImage(foodName: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = OPENFOODFACTS_API.toHttpUrl().newBuilder()
                .addQueryParameter("search_terms", foodName)
                .addQueryParameter("search_simple", "1")
                .addQueryParameter("action", "process")
                .addQueryParameter("json", "1")
                .addQueryParameter("page_size", "1")
                .build()

            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val root = JSONObject(body)
                val products = root.optJSONArray("products") ?: return@use null
                if (products.length() == 0) return@use null

                val product = products.getJSONObject(0)
                product.optString("image_url")?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "OpenFoodFacts image fetch failed for '$foodName': ${e.message}")
            null
        }
    }

    private fun baseUrl(): String {
        val fromConfig = BuildConfig.FATSECRET_BASE_URL
        return if (fromConfig.isNullOrBlank()) "https://fatsecret-551351477998.europe-west1.run.app" else fromConfig
    }

    private val client: OkHttpClient by lazy {
        val backendKey = BuildConfig.BACKEND_API_KEY ?: ""
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
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

    /**
     * Helper funkcija za preverjanje ali je API dostopen.
     * Poskusi z enostavnim health check klicem.
     */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl().trimEnd('/')}/health".toHttpUrl()
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val isOk = resp.isSuccessful
                android.util.Log.d(TAG, "Health check: $isOk, code: ${resp.code}")
                isOk
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Health check failed: ${e.message}", e)
            false
        }
    }

    suspend fun searchFoods(q: String, page: Int = 1, pageSize: Int = 20): List<FoodSummary> =
        withContext(Dispatchers.IO) {
            if (q.isBlank()) return@withContext emptyList()

            val baseUrlValue = baseUrl()
            android.util.Log.d(TAG, "Base URL: $baseUrlValue")
            android.util.Log.d(TAG, "Searching foods: q=$q, page=$page, pageSize=$pageSize")

            val url = "${baseUrlValue.trimEnd('/')}/foods/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("pageSize", pageSize.toString())
                .build()

            android.util.Log.d(TAG, "Request URL: $url")

            val req = Request.Builder().url(url).get().build()

            try {
                client.newCall(req).execute().use { resp ->
                    android.util.Log.d(TAG, "Response code: ${resp.code}")
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string()
                        android.util.Log.e(TAG, "HTTP ${resp.code}: $errorBody")
                        throw IOException("HTTP ${resp.code}: $errorBody")
                    }
                    val body = resp.body?.string().orEmpty()
                    android.util.Log.d(TAG, "Response body length: ${body.length}")
                    val root = JSONObject(body)
                    val foodsObj = root.optJSONObject("foods") ?: return@use emptyList<FoodSummary>()
                    val foodNode = foodsObj.opt("food") ?: return@use emptyList<FoodSummary>()

                    // Simple inline parsing - NO OpenFoodFacts fallback for search (too slow)
                    fun parseItem(obj: JSONObject): FoodSummary {
                        val foodImagesNode = obj.optJSONObject("food_images")
                        val imageUrl = foodImagesNode
                            ?.optJSONObject("food_image")
                            ?.optString("image_url")
                            ?.takeIf { it.isNotBlank() }

                        return FoodSummary(
                            id = obj.optString("food_id"),
                            name = obj.optString("food_name"),
                            brand = obj.optString("brand_name", null),
                            description = obj.optString("food_description", null),
                            imageUrl = imageUrl
                        )
                    }

                    val result = when (foodNode) {
                        is JSONArray -> List(foodNode.length()) { i -> parseItem(foodNode.getJSONObject(i)) }
                        is JSONObject -> listOf(parseItem(foodNode))
                        else -> emptyList()
                    }
                    android.util.Log.d(TAG, "Found ${result.size} foods")
                    result
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Search foods error: ${e.message}", e)
                throw e
            }
        }

    suspend fun getFoodDetail(foodId: String): FoodDetail =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl().trimEnd('/')}/foods/$foodId".toHttpUrl()
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.body?.string()}")
                val body = resp.body?.string().orEmpty()
                android.util.Log.d(TAG, "Food detail raw response (first 500 chars): ${body.take(500)}")
                val root = JSONObject(body)
                val food = root.optJSONObject("food") ?: JSONObject()
                val name = food.optString("food_name", "")
                android.util.Log.d(TAG, "Food keys available: ${food.keys().asSequence().toList()}")

                val servings = food.optJSONObject("servings")
                val servingNode = servings?.opt("serving")

                // Try to find 100g serving first, otherwise use first serving
                val firstServing: JSONObject? = when (servingNode) {
                    is JSONArray -> {
                        // Look for 100g serving
                        var hundred: JSONObject? = null
                        for (i in 0 until servingNode.length()) {
                            val s = servingNode.getJSONObject(i)
                            val metricAmount = s.optString("metric_serving_amount").toDoubleOrNull()
                            val metricUnit = s.optString("metric_serving_unit").lowercase()
                            if (metricAmount == 100.0 && metricUnit == "g") {
                                hundred = s
                                break
                            }
                        }
                        // If no 100g serving found, use first
                        hundred ?: (if (servingNode.length() > 0) servingNode.getJSONObject(0) else null)
                    }
                    is JSONObject -> servingNode
                    else -> null
                }

                fun d(key: String): Double? = firstServing?.optString(key)?.toDoubleOrNull()

                // Extract image URL from food_images if available
                val imageUrl = food.optJSONObject("food_images")
                    ?.optJSONObject("food_image")
                    ?.optString("image_url")
                    ?.takeIf { it.isNotBlank() }



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
                    potassiumMg = d("potassium"),
                    imageUrl = imageUrl
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

    // ===== RECIPES =====

    suspend fun searchRecipes(q: String, page: Int = 1, pageSize: Int = 20): List<RecipeSummary> =
        withContext(Dispatchers.IO) {
            if (q.isBlank()) return@withContext emptyList()
            val url = "${baseUrl().trimEnd('/')}/recipes/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("pageSize", pageSize.toString())
                .build()

            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.body?.string()}")
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val recipesObj = root.optJSONObject("recipes") ?: return@use emptyList<RecipeSummary>()
                val recipeNode = recipesObj.opt("recipe") ?: return@use emptyList<RecipeSummary>()

                fun parseRecipeSummary(obj: JSONObject): RecipeSummary {
                    return RecipeSummary(
                        id = obj.optString("recipe_id"),
                        name = obj.optString("recipe_name"),
                        description = obj.optString("recipe_description", null),
                        caloriesKcal = obj.optString("recipe_calories")?.toIntOrNull(),
                        proteinG = obj.optString("recipe_protein")?.toDoubleOrNull(),
                        carbsG = obj.optString("recipe_carbohydrate")?.toDoubleOrNull(),
                        fatG = obj.optString("recipe_fat")?.toDoubleOrNull(),
                        imageUrl = obj.optString("recipe_image", null)
                    )
                }

                when (recipeNode) {
                    is JSONArray -> List(recipeNode.length()) { i -> parseRecipeSummary(recipeNode.getJSONObject(i)) }
                    is JSONObject -> listOf(parseRecipeSummary(recipeNode))
                    else -> emptyList()
                }
            }
        }

    suspend fun getRecipeDetail(recipeId: String): RecipeDetail =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl().trimEnd('/')}/recipes/$recipeId".toHttpUrl()
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.body?.string()}")
                val body = resp.body?.string().orEmpty()

                // Debug logging - log raw response
                android.util.Log.d("FatSecretAPI", "Recipe detail response: ${body.take(500)}")

                val root = JSONObject(body)
                val recipe = root.optJSONObject("recipe") ?: JSONObject()

                // Debug log recipe keys
                android.util.Log.d("FatSecretAPI", "Recipe keys: ${recipe.keys().asSequence().toList()}")

                val directionsNode = recipe.opt("directions")
                android.util.Log.d("FatSecretAPI", "Directions node type: ${directionsNode?.javaClass?.simpleName}")
                android.util.Log.d("FatSecretAPI", "Directions node: $directionsNode")

                // Parse directions - FatSecret can return:
                // 1. JSONArray of strings: ["1. Step...", "2. Step..."]
                // 2. JSONObject with nested structure
                val directions: List<String> = when (directionsNode) {
                    is JSONArray -> {
                        // Check if array contains strings or objects
                        List(directionsNode.length()) { i ->
                            val item = directionsNode.opt(i)
                            when (item) {
                                is String -> item // Direct string
                                is JSONObject -> {
                                    // Object with direction_description
                                    val num = item.optInt("direction_number", i + 1)
                                    val desc = item.optString("direction_description", "")
                                    if (desc.isNotEmpty()) "$num. $desc" else ""
                                }
                                else -> ""
                            }
                        }.filter { it.isNotEmpty() }
                    }
                    is JSONObject -> {
                        // Nested structure: {"directions": {"direction": [...]}}
                        val directionArray = directionsNode.opt("direction")
                        when (directionArray) {
                            is JSONArray -> List(directionArray.length()) { i ->
                                val item = directionArray.opt(i)
                                when (item) {
                                    is String -> item
                                    is JSONObject -> {
                                        val num = item.optInt("direction_number", i + 1)
                                        val desc = item.optString("direction_description", "")
                                        if (desc.isNotEmpty()) "$num. $desc" else ""
                                    }
                                    else -> ""
                                }
                            }.filter { it.isNotEmpty() }
                            is JSONObject -> {
                                val desc = directionArray.optString("direction_description", "")
                                if (desc.isNotEmpty()) listOf("1. $desc") else emptyList()
                            }
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }

                // Parse ingredients - FatSecret can return:
                // 1. JSONArray of strings: ["ingredient1", "ingredient2"]
                // 2. JSONObject with nested structure: {"ingredients": {"ingredient": [...]}}
                val ingredientsRaw = recipe.opt("ingredients")
                android.util.Log.d("FatSecretAPI", "Ingredients node type: ${ingredientsRaw?.javaClass?.simpleName}")
                android.util.Log.d("FatSecretAPI", "Ingredients node: $ingredientsRaw")

                val ingredients: List<String> = when (ingredientsRaw) {
                    is JSONArray -> {
                        // Direct array of strings
                        List(ingredientsRaw.length()) { i ->
                            val item = ingredientsRaw.opt(i)
                            when (item) {
                                is String -> item
                                is JSONObject -> item.optString("ingredient_description", "")
                                else -> ""
                            }
                        }.filter { it.isNotEmpty() }
                    }
                    is JSONObject -> {
                        // Nested structure: {"ingredients": {"ingredient": [...]}}
                        val ingredientArray = ingredientsRaw.opt("ingredient")
                        when (ingredientArray) {
                            is JSONArray -> List(ingredientArray.length()) { i ->
                                val item = ingredientArray.opt(i)
                                when (item) {
                                    is String -> item
                                    is JSONObject -> item.optString("ingredient_description", "")
                                    else -> ""
                                }
                            }.filter { it.isNotEmpty() }
                            is JSONObject -> {
                                val desc = ingredientArray.optString("ingredient_description", "")
                                if (desc.isNotEmpty()) listOf(desc) else emptyList()
                            }
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }

                RecipeDetail(
                    id = recipeId,
                    name = recipe.optString("recipe_name", ""),
                    description = recipe.optString("recipe_description", null),
                    caloriesKcal = recipe.optString("recipe_calories")?.toIntOrNull(),
                    proteinG = recipe.optString("recipe_protein")?.toDoubleOrNull(),
                    carbsG = recipe.optString("recipe_carbohydrate")?.toDoubleOrNull(),
                    fatG = recipe.optString("recipe_fat")?.toDoubleOrNull(),
                    servings = recipe.optString("number_of_servings")?.toIntOrNull(),
                    prepTimeMin = recipe.optString("preparation_time_min")?.toIntOrNull(),
                    cookTimeMin = recipe.optString("cooking_time_min")?.toIntOrNull(),
                    directions = directions,
                    ingredients = ingredients,
                    imageUrl = recipe.optString("recipe_image", null)
                )
            }
        }

    /**
     * Autocomplete suggestions for food search
     */
    suspend fun getFoodAutocomplete(query: String, maxResults: Int = 10): List<AutocompleteSuggestion> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()

            val baseUrlValue = baseUrl()
            android.util.Log.d(TAG, "Autocomplete: q=$query, baseUrl=$baseUrlValue")

            val url = "${baseUrlValue.trimEnd('/')}/foods/autocomplete".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("maxResults", maxResults.toString())
                .build()

            android.util.Log.d(TAG, "Autocomplete URL: $url")

            try {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    android.util.Log.d(TAG, "Autocomplete response code: ${resp.code}")

                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string()
                        android.util.Log.e(TAG, "Autocomplete failed: HTTP ${resp.code}, body: $errorBody")
                        return@use emptyList<AutocompleteSuggestion>()
                    }

                    val body = resp.body?.string().orEmpty()
                    android.util.Log.d(TAG, "Autocomplete response body: ${body.take(200)}")

                    val root = JSONObject(body)
                    val suggestionsObj = root.optJSONObject("suggestions")
                    if (suggestionsObj == null) {
                        android.util.Log.w(TAG, "No 'suggestions' object in response")
                        return@use emptyList<AutocompleteSuggestion>()
                    }

                    val suggestionNode = suggestionsObj.opt("suggestion")
                    if (suggestionNode == null) {
                        android.util.Log.w(TAG, "No 'suggestion' node in suggestions object")
                        return@use emptyList<AutocompleteSuggestion>()
                    }

                    val result = when (suggestionNode) {
                        is JSONArray -> List(suggestionNode.length()) { i ->
                            AutocompleteSuggestion(suggestionNode.optString(i))
                        }
                        is String -> listOf(AutocompleteSuggestion(suggestionNode))
                        else -> emptyList()
                    }

                    android.util.Log.d(TAG, "Autocomplete returning ${result.size} suggestions")
                    result
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Autocomplete exception: ${e.message}", e)
                emptyList()
            }
        }

    /**
     * Lookup food by barcode
     * Returns null if not found - can then fallback to OpenFoodFacts
     */
    suspend fun getFoodByBarcode(barcode: String): BarcodeResult? =
        withContext(Dispatchers.IO) {
            if (barcode.isBlank()) return@withContext null
            val url = "${baseUrl().trimEnd('/')}/foods/barcode".toHttpUrl().newBuilder()
                .addQueryParameter("barcode", barcode)
                .build()

            val req = Request.Builder().url(url).get().build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        android.util.Log.w(TAG, "Barcode lookup failed: HTTP ${resp.code}")
                        return@use null
                    }
                    val body = resp.body?.string().orEmpty()
                    val root = JSONObject(body)

                    // FatSecret returns {"food_id": {"value": "12345"}}
                    val foodIdObj = root.optJSONObject("food_id")
                    val foodId = foodIdObj?.optString("value")?.takeIf { it.isNotBlank() }
                        ?: return@use null

                    // Fetch food name
                    val foodName = try {
                        val detail = getFoodDetail(foodId)
                        detail.name
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to fetch food detail for barcode: ${e.message}")
                        null
                    }

                    BarcodeResult(foodId = foodId, foodName = foodName)
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Barcode lookup error: ${e.message}")
                null
            }
        }
}