package com.example.myapplication.domain.nutrition

/**
 * Entiteta hrane v naši čisti KMP arhitekturi.
 * Predstavlja samo podatke, ne glede na to, ali je prišla iz FatSecret, OpenFoodFacts ali lokalne baze.
 */
data class FoodEntity(
    val id: String,
    val name: String,
    val brand: String? = null,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val barcode: String? = null,
    val servingSize: String = "100g"
)

/**
 * Domain vmesnik - Single Source of Truth za iskanje hrane.
 * UI (Compose) ne bo več nikoli klical FatSecret kliče na pamet,
 * niti ne bo reševal "Fallbackov", ampak le še ta repozitorij.
 */
interface FoodRepository {

    /**
     * Iskanje po imenu
     * @param query Ime živila (npr. "Banana")
     * @return Seznam najdenih živil, združen/fallback po različnih bazah.
     */
    suspend fun searchFoodByName(query: String): List<FoodEntity>

    /**
     * Iskanje s čitalnikom črtne kode.
     * @param barcode Skenirana 13/8-mestna koda.
     * @return Živilo ali null, če ni najdeno.
     */
    suspend fun searchFoodByBarcode(barcode: String): FoodEntity?
}

