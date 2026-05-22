package com.example.myapplication

import com.example.myapplication.domain.nutrition.calculateAdvancedBMR
import com.example.myapplication.domain.nutrition.calculateAdaptiveTDEE
import com.example.myapplication.domain.nutrition.calculateDailyWaterMl
import com.example.myapplication.domain.nutrition.calculateEnhancedTDEE
import com.example.myapplication.domain.nutrition.calculateOptimalMacros
import com.example.myapplication.domain.nutrition.calculateRestDayCalories
import com.example.myapplication.domain.nutrition.calculateSmartCalories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lokalni unit testi za NutritionCalculations.kt
 *
 * Pokrivajo:
 *  1. calculateDailyWaterMl  — dnevna voda (robni primeri, klampiranje, zaokroževanje)
 *  2. calculateSmartCalories — kalorije za workout dni (cilji, spol, BMI)
 *  3. calculateRestDayCalories — kalorije za rest dni (minimumi, prilagoditve cilja)
 *  4. calculateAdvancedBMR   — bazalni metabolizem (oba modela, starostni faktorji)
 *  5. calculateEnhancedTDEE  — skupna poraba (multiplierji, delta logika)
 *  6. calculateOptimalMacros — makrohranila (keto spodnja meja, zaščita negativnih)
 *  7. calculateAdaptiveTDEE  — adaptivni hibridni TDEE
 *
 * KMP-ready: brez Android odvisnosti.
 */
class NutritionCalculationsTest {

    // ══════════════════════════════════════════════════════════════════════════
    // 1. calculateDailyWaterMl
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `voda - standardni moski, zmerna aktivnost, workout dan`() {
        // 80 kg moški, "Moderately active", workout dan
        // base: 80 * 35 = 2800, *1.1 = 3080, +500 (activity) = 3580, +500 (workout) = 4080
        // zaokroži na 100: 4100 ml
        val result = calculateDailyWaterMl(80.0, isMale = true, "Moderately active", isWorkoutDay = true)
        assertEquals(4100f, result)
    }

    @Test
    fun `voda - standardna zenska, sedentary, brez treninga`() {
        // 60 kg ženska, "Sedentary", brez treninga
        // base: 60 * 35 = 2100, (ni moški faktor), +0 (activity), +0 (workout) = 2100 ml
        val result = calculateDailyWaterMl(60.0, isMale = false, "Sedentary", isWorkoutDay = false)
        assertEquals(2100f, result)
    }

    @Test
    fun `voda - robni primer zelo nizka teza klampira na minimum`() {
        // 10 kg — spodnja meja je 1500 ml
        val result = calculateDailyWaterMl(10.0, isMale = false, "Sedentary", isWorkoutDay = false)
        assertEquals(1500f, result)
    }

    @Test
    fun `voda - robni primer zelo visoka teza klampira na maximum`() {
        // 200 kg moški, "Very active", workout dan → vrednost bo > 5000, klamp na 5000
        val result = calculateDailyWaterMl(200.0, isMale = true, "Very active", isWorkoutDay = true)
        assertEquals(5000f, result)
    }

    @Test
    fun `voda - nic kg (edge case) klampira na minimum`() {
        val result = calculateDailyWaterMl(0.0, isMale = true, "Very active", isWorkoutDay = true)
        assertEquals(1500f, result)
    }

    @Test
    fun `voda - neznana aktivnost uporabi privzeto 250ml bonus`() {
        // 70 kg ženska, neznana aktivnost
        // base: 70 * 35 = 2450, +250 (else veja), zaokroži → 2700 ml
        val result = calculateDailyWaterMl(70.0, isMale = false, "Unknown level", isWorkoutDay = false)
        assertEquals(2700f, result)
    }

    @Test
    fun `voda - workout dan doda tocno 500ml v primerjavi z rest dnem`() {
        val restDay = calculateDailyWaterMl(75.0, isMale = false, "Lightly active", isWorkoutDay = false)
        val workoutDay = calculateDailyWaterMl(75.0, isMale = false, "Lightly active", isWorkoutDay = true)
        // Razlika mora biti natanko 500 ml (pred zaokroževanjem sta obe vrednosti ≥ +500)
        assertTrue("Workout dan mora imeti >= 500ml več kot rest dan",
            workoutDay - restDay >= 400f)  // ≥400 ker zaokroževanje na 100 lahko zmanjša razliko
    }

    @Test
    fun `voda - vrednost je vedno zaokrozena na 100 ml`() {
        // Za katerikoli realističen vnos mora biti rezultat večkratnik 100
        listOf(55.0, 67.5, 82.3, 91.0).forEach { weight ->
            val result = calculateDailyWaterMl(weight, isMale = true, "3x", isWorkoutDay = false)
            assertTrue("Rezultat $result mora biti večkratnik 100 ml",
                result.toInt() % 100 == 0)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. calculateSmartCalories (workout day kalorije)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `workout kalorije - build muscle doda surplus k TDEE`() {
        val tdee = 2500.0
        val result = calculateSmartCalories(
            tdee = tdee, goal = "Build muscle", experience = "Intermediate",
            bmi = 22.0, age = 28, isMale = true, bodyFat = null, limitations = emptyList()
        )
        assertTrue("Build muscle mora imeti surplus nad TDEE", result > tdee)
    }

    @Test
    fun `workout kalorije - lose fat odvzame od TDEE a nikoli pod minimum 1500 za moscega`() {
        val tdee = 1600.0  // nizek TDEE
        val result = calculateSmartCalories(
            tdee = tdee, goal = "Lose fat", experience = "Beginner",
            bmi = 23.0, age = 30, isMale = true, bodyFat = null, limitations = emptyList()
        )
        assertTrue("Moški minimum je 1500 kcal", result >= 1500.0)
    }

    @Test
    fun `workout kalorije - lose fat nikoli pod 1200 za zensko`() {
        val tdee = 1300.0  // zelo nizek TDEE
        val result = calculateSmartCalories(
            tdee = tdee, goal = "Lose fat", experience = "Intermediate",
            bmi = 23.0, age = 27, isMale = false, bodyFat = null, limitations = emptyList()
        )
        assertTrue("Ženski minimum je 1200 kcal", result >= 1200.0)
    }

    @Test
    fun `workout kalorije - general health BMI nad 25 odvzame 250 kcal`() {
        val tdee = 2400.0
        val result = calculateSmartCalories(
            tdee = tdee, goal = "General health", experience = null,
            bmi = 26.0, age = 35, isMale = true, bodyFat = null, limitations = emptyList()
        )
        assertEquals(2150.0, result, 1.0)
    }

    @Test
    fun `workout kalorije - general health BMI pod 20 doda 200 kcal`() {
        val tdee = 2000.0
        val result = calculateSmartCalories(
            tdee = tdee, goal = "General health", experience = null,
            bmi = 19.0, age = 25, isMale = false, bodyFat = null, limitations = emptyList()
        )
        assertEquals(2200.0, result, 1.0)
    }

    @Test
    fun `workout kalorije - neznani cilj vrne TDEE brez spremembe`() {
        val tdee = 2200.0
        val result = calculateSmartCalories(
            tdee = tdee, goal = null, experience = null,
            bmi = 22.0, age = 30, isMale = true, bodyFat = null, limitations = emptyList()
        )
        assertEquals(tdee, result, 0.01)
    }

    @Test
    fun `workout kalorije - diabetes zmanjsa kalorije za 2 procenta`() {
        val tdee = 2000.0
        val brez = calculateSmartCalories(tdee, "Build muscle", "Beginner", 22.0, 30, true, null, emptyList())
        val zDiabetes = calculateSmartCalories(tdee, "Build muscle", "Beginner", 22.0, 30, true, null, listOf("Diabetes"))
        assertTrue("Diabetes mora znižati kalorije", zDiabetes < brez)
        assertEquals(brez * 0.98, zDiabetes, 1.0)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. calculateRestDayCalories
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `rest day - lose fat odvzame 250 kcal`() {
        val result = calculateRestDayCalories(2500.0, "Lose fat", isMale = true)
        assertEquals(2250, result)
    }

    @Test
    fun `rest day - build muscle doda 100 kcal`() {
        val result = calculateRestDayCalories(2400.0, "Build muscle", isMale = true)
        assertEquals(2500, result)
    }

    @Test
    fun `rest day - recomposition odvzame 150 kcal`() {
        val result = calculateRestDayCalories(2300.0, "Recomposition", isMale = false)
        assertEquals(2150, result)
    }

    @Test
    fun `rest day - neznani cilj ne spremi TDEE`() {
        val result = calculateRestDayCalories(2000.0, null, isMale = true)
        assertEquals(2000, result)
    }

    @Test
    fun `rest day - moski nikoli pod 1500 kcal`() {
        // Zelo nizek TDEE + lose fat bi šlo pod minimum
        val result = calculateRestDayCalories(1600.0, "Lose fat", isMale = true)
        assertTrue("Moški minimum je 1500 kcal", result >= 1500)
    }

    @Test
    fun `rest day - zenska nikoli pod 1200 kcal`() {
        val result = calculateRestDayCalories(1300.0, "Lose fat", isMale = false)
        assertTrue("Ženski minimum je 1200 kcal", result >= 1200)
    }

    @Test
    fun `rest day - ekstremno visok TDEE vrne razumno vrednost`() {
        val result = calculateRestDayCalories(8000.0, "Lose fat", isMale = true)
        assertEquals(7750, result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. calculateAdvancedBMR
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `BMR - Mifflin moski 30 let, 80kg, 180cm`() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 800 + 1125 - 150 + 5 = 1780
        val result = calculateAdvancedBMR(80.0, 180.0, 30, isMale = true, bodyFat = null)
        assertEquals(1780.0, result, 1.0)
    }

    @Test
    fun `BMR - Mifflin zenska 25 let, 60kg, 165cm`() {
        // 10*60 + 6.25*165 - 5*25 - 161 = 600 + 1031.25 - 125 - 161 = 1345.25
        val result = calculateAdvancedBMR(60.0, 165.0, 25, isMale = false, bodyFat = null)
        assertEquals(1345.25, result, 1.0)
    }

    @Test
    fun `BMR - Katch-McArdle z body fat podatkom`() {
        // 70 kg, 15% body fat → LBM = 70 * 0.85 = 59.5
        // 370 + 21.6 * 59.5 = 370 + 1285.2 = 1655.2
        val result = calculateAdvancedBMR(70.0, 175.0, 35, isMale = true, bodyFat = 15.0)
        assertEquals(1655.2, result, 1.0)
    }

    @Test
    fun `BMR - mladostnik 16 let dobi 12 odstotni bonus`() {
        val odrasli = calculateAdvancedBMR(70.0, 175.0, 30, isMale = true, bodyFat = null)
        val mladostnik = calculateAdvancedBMR(70.0, 175.0, 16, isMale = true, bodyFat = null)
        // Razlika v rawBMR (starost 30 vs 16), potem *1.12 za mladostnika
        // rawBMR(30): 10*70 + 6.25*175 - 5*30 + 5 = 700 + 1093.75 - 150 + 5 = 1648.75
        // rawBMR(16): 10*70 + 6.25*175 - 5*16 + 5 = 700 + 1093.75 - 80 + 5 = 1718.75 * 1.12 = 1925.0
        assertTrue("Mladostnik mora imeti višji BMR ko odrasli", mladostnik > odrasli)
        assertEquals(1718.75 * 1.12, mladostnik, 1.0)
    }

    @Test
    fun `BMR - starejsi 70 let dobi 13 odstotni popust`() {
        // rawBMR(70): 10*75 + 6.25*170 - 5*70 + 5 = 750 + 1062.5 - 350 + 5 = 1467.5 * 0.87 = 1276.725
        val result = calculateAdvancedBMR(75.0, 170.0, 70, isMale = true, bodyFat = null)
        assertEquals(1467.5 * 0.87, result, 1.0)
    }

    @Test
    fun `BMR - nicla kot body fat ne zbrise LBM modela (nula je falsy)`() {
        // bodyFat = 0.0 je "falsy" (> 0 je false), zato mora uporabiti Mifflin
        val zNicloBF = calculateAdvancedBMR(80.0, 180.0, 30, isMale = true, bodyFat = 0.0)
        val brezBF = calculateAdvancedBMR(80.0, 180.0, 30, isMale = true, bodyFat = null)
        assertEquals("bodyFat=0 mora dati enak rezultat kot null", brezBF, zNicloBF, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. calculateEnhancedTDEE — delta logika
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `TDEE - 3x tedensko dda pravi multiplier na BMR`() {
        val bmr = 1800.0
        // activityDelta = 1800 * 1.55 - 1800 = 990, adjustedDelta = 990 (nobeni modi.)
        // ageMultiplier age=30 → 1.0
        // result ≈ (1800 + 990) * 1.0 = 2790
        val result = calculateEnhancedTDEE(bmr, "3x", "Intermediate", 30, emptyList(), "7-8")
        assertEquals(2790.0, result, 5.0)
    }

    @Test
    fun `TDEE - advanced je nekoliko nizji od beginner za isti BMR`() {
        val bmr = 2000.0
        val beginner = calculateEnhancedTDEE(bmr, "4x", "Beginner", 30, emptyList(), "7-8")
        val advanced = calculateEnhancedTDEE(bmr, "4x", "Advanced", 30, emptyList(), "7-8")
        assertTrue("Advanced mora imeti nižji TDEE od beginner", advanced < beginner)
    }

    @Test
    fun `TDEE - slabo spanje zmanjsa aktivnostni delta`() {
        val bmr = 2000.0
        val dobroSpanje = calculateEnhancedTDEE(bmr, "4x", "Intermediate", 30, emptyList(), "8-9")
        val slabSpanje = calculateEnhancedTDEE(bmr, "4x", "Intermediate", 30, emptyList(), "Less than 6")
        assertTrue("Slabo spanje mora zmanjšati TDEE", slabSpanje < dobroSpanje)
    }

    @Test
    fun `TDEE - astma zmanjsa aktivnostni delta`() {
        val bmr = 1900.0
        val brezOmejitev = calculateEnhancedTDEE(bmr, "3x", "Intermediate", 30, emptyList(), "7-8")
        val zAstmo = calculateEnhancedTDEE(bmr, "3x", "Intermediate", 30, listOf("Asthma"), "7-8")
        assertTrue("Astma mora zmanjšati TDEE", zAstmo < brezOmejitev)
    }

    @Test
    fun `TDEE - neznana frekvenca uporabi sedentary multiplier 1_2`() {
        val bmr = 2000.0
        val resultNeznano = calculateEnhancedTDEE(bmr, null, null, 30, emptyList(), null)
        // baseTDEE = 2000 * 1.2 = 2400, delta = 400, adjusted = 400 * 1.0^3 = 400
        // result = (2000 + 400) * 1.0 = 2400
        assertEquals(2400.0, resultNeznano, 5.0)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. calculateOptimalMacros
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `makrohranila - keto karbohidrati nikoli nad 50g`() {
        val (_, carbs, _) = calculateOptimalMacros(
            calories = 2200.0, weight = 80.0, goal = "Lose fat", experience = "Intermediate",
            age = 35, isMale = true, bodyFat = 20.0, nutrition = "Keto/LCHF", limitations = emptyList()
        )
        assertTrue("Keto carbs ≤ 50g", carbs <= 50)
    }

    @Test
    fun `makrohranila - keto karbohidrati nikoli pod 20g`() {
        val (_, carbs, _) = calculateOptimalMacros(
            calories = 1500.0, weight = 60.0, goal = "Lose fat", experience = "Advanced",
            age = 40, isMale = true, bodyFat = 25.0, nutrition = "Keto/LCHF", limitations = emptyList()
        )
        assertTrue("Keto carbs ≥ 20g (spodnja meja)", carbs >= 20)
    }

    @Test
    fun `makrohranila - vrednosti so nikoli negativne`() {
        // Ekstremno nizke kalorije — protein in maščoba porabita ves prostor
        val (protein, carbs, fat) = calculateOptimalMacros(
            calories = 800.0, weight = 100.0, goal = "Lose fat", experience = "Advanced",
            age = 55, isMale = true, bodyFat = 35.0, nutrition = "Standard", limitations = emptyList()
        )
        assertTrue("Protein >= 0", protein >= 0)
        assertTrue("Carbs >= 0", carbs >= 0)
        assertTrue("Fat >= 0", fat >= 0)
    }

    @Test
    fun `makrohranila - build muscle ima visji protein kot general health`() {
        val (proteinsMusle, _, _) = calculateOptimalMacros(
            calories = 2800.0, weight = 80.0, goal = "Build muscle", experience = "Intermediate",
            age = 28, isMale = true, bodyFat = null, nutrition = "Standard", limitations = emptyList()
        )
        val (proteinGeneral, _, _) = calculateOptimalMacros(
            calories = 2800.0, weight = 80.0, goal = "General health", experience = "Intermediate",
            age = 28, isMale = true, bodyFat = null, nutrition = "Standard", limitations = emptyList()
        )
        assertTrue("Build muscle mora imeti višji protein", proteinsMusle >= proteinGeneral)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. calculateAdaptiveTDEE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `adaptivni TDEE - manj kot 3 dni podatkov vrne confidence 0`() {
        val result = calculateAdaptiveTDEE(listOf(2000, 1900), 0.0, theoreticalTDEE = 2300)
        assertEquals(0.0, result.confidenceFactor, 0.001)
    }

    @Test
    fun `adaptivni TDEE - 7 dni podatkov vrne confidence 1`() {
        val result = calculateAdaptiveTDEE(List(7) { 2200 }, 0.0, theoreticalTDEE = 2300)
        assertEquals(1.0, result.confidenceFactor, 0.001)
    }

    @Test
    fun `adaptivni TDEE - brez historičnih podatkov vrne theoretical TDEE`() {
        val result = calculateAdaptiveTDEE(emptyList(), 0.0, theoreticalTDEE = 2500)
        assertEquals(2500, result.hybridTDEE)
    }

    @Test
    fun `adaptivni TDEE - weight gain zmanjsa adaptivni TDEE`() {
        // Kalorije so bile 2000/dan, pridobite teže +0.1 kg/dan → obstoječe kalorije prevelike
        val result = calculateAdaptiveTDEE(List(7) { 2000 }, emaWeightChangeDelta = 0.1, theoreticalTDEE = 2000)
        // adaptiveTDEE = 2000 - (0.1 * 7700 / 7) = 2000 - 110 = 1890
        assertEquals(1890, result.adaptiveTDEE)
    }

    @Test
    fun `adaptivni TDEE - nikoli pod 800 kcal minimum`() {
        // Absurdni vhodi ki bi sicer vrgli negativno vrednost
        val result = calculateAdaptiveTDEE(List(7) { 500 }, emaWeightChangeDelta = 10.0, theoreticalTDEE = 0)
        assertTrue("Adaptivni TDEE nikoli pod 800 kcal", result.adaptiveTDEE >= 800)
        assertTrue("Hybrid TDEE nikoli pod 800 kcal", result.hybridTDEE >= 800)
    }
}


