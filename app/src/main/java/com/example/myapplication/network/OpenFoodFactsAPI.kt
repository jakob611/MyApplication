package com.example.myapplication.network

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

data class OpenFoodFactsProduct(
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("brands") val brands: String? = null,
    @SerializedName("quantity") val quantity: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("nutriments") val nutriments: Nutriments? = null,
    @SerializedName("ingredients_text") val ingredientsText: String? = null,
    @SerializedName("serving_size") val servingSize: String? = null,
    @SerializedName("countries") val countries: String? = null,
    @SerializedName("countries_tags") val countriesTags: List<String>? = null,
    @SerializedName("manufacturing_places") val manufacturingPlaces: String? = null,
    @SerializedName("origins") val origins: String? = null,
    @SerializedName("allergens") val allergens: String? = null,
    @SerializedName("allergens_tags") val allergensTags: List<String>? = null,
    @SerializedName("traces") val traces: String? = null,
    @SerializedName("traces_tags") val tracesTags: List<String>? = null,
    @SerializedName("nutriscore_grade") val nutriscoreGrade: String? = null,
    @SerializedName("nova_group") val novaGroup: Int? = null,
    @SerializedName("ecoscore_grade") val ecoscoreGrade: String? = null,
    @SerializedName("labels") val labels: String? = null,
    @SerializedName("labels_tags") val labelsTags: List<String>? = null,
    @SerializedName("categories") val categories: String? = null,
    @SerializedName("code") val barcode: String? = null
)

data class Nutriments(
    @SerializedName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerializedName("proteins_100g") val proteins100g: Double? = null,
    @SerializedName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerializedName("fat_100g") val fat100g: Double? = null,
    @SerializedName("fiber_100g") val fiber100g: Double? = null,
    @SerializedName("sugars_100g") val sugars100g: Double? = null,
    @SerializedName("salt_100g") val salt100g: Double? = null,
    @SerializedName("saturated-fat_100g") val saturatedFat100g: Double? = null,
    @SerializedName("sodium_100g") val sodium100g: Double? = null,
    @SerializedName("potassium_100g") val potassium100g: Double? = null,
    @SerializedName("cholesterol_100g") val cholesterol100g: Double? = null
)

data class OpenFoodFactsResponse(
    @SerializedName("status") val status: Int = 0,
    @SerializedName("product") val product: OpenFoodFactsProduct? = null
)

object OpenFoodFactsAPI {
    private const val TAG = "OpenFoodFactsAPI"
    private const val BASE_URL = "https://world.openfoodfacts.org/api/v2/product"

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)  // Povečan timeout
        .readTimeout(45, TimeUnit.SECONDS)     // Povečan timeout
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)         // Avtomatski retry
        .build()

    private val gson = Gson()

    /**
     * Ugotovi državo izvora iz prvih 3 številk barkode (GS1 prefix)
     */
    fun getCountryFromBarcode(barcode: String): String? {
        if (barcode.length < 3) return null

        val prefix = barcode.take(3).toIntOrNull() ?: return null

        return when (prefix) {
            in 0..19 -> "USA, Canada"
            in 20..29 -> "In-Store / Restricted Circulation"
            in 30..39 -> "USA - Drugs"
            in 40..49 -> "Restricted Distribution (e.g., coupons)"
            in 50..59 -> "USA - Reserved"
            in 60..139 -> "USA, Canada"
            in 200..299 -> "Restricted Distribution"
            in 300..379 -> "France, Monaco"
            380 -> "Bulgaria"
            383 -> "Slovenia"
            385 -> "Croatia"
            387 -> "Bosnia and Herzegovina"
            389 -> "Montenegro"
            in 400..440 -> "Germany"
            in 450..459, 490, 491 -> "Japan"
            460, 469 -> "Russia"
            in 470..479 -> "Kyrgyzstan, Taiwan"
            in 480..489 -> "Philippines"
            in 500..509 -> "United Kingdom"
            520, 521 -> "Greece"
            528 -> "Lebanon"
            529 -> "Cyprus"
            in 530..539 -> "Albania, North Macedonia, Kosovo"
            in 540..549 -> "Belgium, Luxembourg"
            560 -> "Portugal"
            569 -> "Iceland"
            in 570..579 -> "Denmark, Faroe Islands, Greenland"
            590 -> "Poland"
            594 -> "Romania"
            599 -> "Hungary"
            in 600..601 -> "South Africa"
            603 -> "Ghana"
            604 -> "Senegal"
            608 -> "Bahrain"
            609 -> "Mauritius"
            in 610..619 -> "Morocco"
            in 620..621 -> "Algeria"
            622 -> "Egypt"
            624 -> "Libya"
            625 -> "Jordan"
            626 -> "Iran"
            627 -> "Kuwait"
            628 -> "Saudi Arabia"
            629 -> "United Arab Emirates"
            in 640..649 -> "Finland"
            in 690..699 -> "China"
            in 700..709 -> "Norway"
            729 -> "Israel"
            in 730..739 -> "Sweden"
            740 -> "Guatemala"
            741 -> "El Salvador"
            742 -> "Honduras"
            743 -> "Nicaragua"
            744 -> "Costa Rica"
            745 -> "Panama"
            746 -> "Dominican Republic"
            750 -> "Mexico"
            in 754..755 -> "Canada"
            in 759..759 -> "Venezuela"
            in 760..769 -> "Switzerland, Liechtenstein"
            in 770..771 -> "Colombia"
            773 -> "Uruguay"
            775 -> "Peru"
            777 -> "Bolivia"
            778, 779 -> "Argentina"
            780 -> "Chile"
            in 784..786 -> "Paraguay, Ecuador, Brazil"
            in 789..790 -> "Brazil"
            in 800..839 -> "Italy, San Marino, Vatican"
            in 840..849 -> "Spain, Andorra"
            in 850..859 -> "Cuba"
            860 -> "Serbia"
            865 -> "Mongolia"
            867 -> "North Korea"
            868, 869 -> "Turkey"
            in 870..879 -> "Netherlands"
            880 -> "South Korea"
            in 884..885 -> "Cambodia, Thailand"
            888 -> "Singapore"
            in 890..899 -> "India"
            in 900..919 -> "Austria"
            in 930..939 -> "Australia"
            in 940..949 -> "New Zealand"
            in 950..959 -> "Global Office (GTIN-8)"
            in 955..959 -> "Malaysia"
            in 960..969 -> "Global Office"
            in 977..977 -> "Serial Publications (ISSN)"
            in 978..979 -> "Books (ISBN)"
            980 -> "Refund Receipts"
            in 981..984 -> "Common Currency Coupons"
            990, 991, 992 -> "Coupons"
            993, 994, 995, 996, 997, 998, 999 -> "Coupons"
            else -> null
        }
    }

    suspend fun getProductByBarcode(barcode: String): OpenFoodFactsResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/$barcode.json"
            Log.d(TAG, "Fetching product from: $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "GlowUpp - Android - Version 1.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                Log.e(TAG, "API call failed: ${response.code}")
                return@withContext null
            }

            Log.d(TAG, "Response body: ${body.take(500)}")
            val result = gson.fromJson(body, OpenFoodFactsResponse::class.java)

            if (result.status == 1 && result.product != null) {
                Log.d(TAG, "Product found: ${result.product.productName}")
                result
            } else {
                Log.d(TAG, "Product not found (status: ${result.status})")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching product: ${e.message}", e)
            null
        }
    }
}
