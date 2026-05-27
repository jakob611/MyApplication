package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.auth.AuthStateRepository
import com.example.myapplication.domain.metrics.MetricsRepository
import com.example.myapplication.domain.model.AlgorithmData
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.domain.nutrition.calculateOptimalMacros
import com.example.myapplication.domain.usecase.CalculateDailyCalorieTargetUseCase
import com.example.myapplication.domain.workout.generateAdvancedCustomPlan
import com.example.myapplication.domain.workout.generateIntelligentTrainingPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ---------------------------------------------------------------------------
// QuizUiState — verseal interface Anomaly 1 (BodyModule Clean Arch)
// Faza 42 — premaknjena vsa poslovna logika iz Composable-a v ViewModel.
// ---------------------------------------------------------------------------

sealed interface QuizUiState {
    /** Začetno stanje — kviz ni bil še oddan. */
    data object Idle : QuizUiState

    /** Generiranje plana in shranjevanje teže v teku. */
    data object Loading : QuizUiState

    /**
     * Plan uspešno generiran.
     * @param plan  Generiran [PlanResult] — pripravljn za shranjevanje.
     * @param answers Surovi odgovori kviza — za klic onQuizDataCollected callbacka.
     */
    data class Success(val plan: PlanResult, val answers: QuizAnswers) : QuizUiState

    /** Napaka pri generiranju plana. */
    data class Error(val message: String) : QuizUiState
}

// ---------------------------------------------------------------------------
// QuizAnswers — vse zbrane vrednosti iz 14-koračnega kviza + ime plana
// ---------------------------------------------------------------------------

data class QuizAnswers(
    val gender: String?,
    val age: String,
    val height: String,
    val weight: String,
    val bodyFat: String?,
    val goal: String?,
    val experience: String?,
    val trainingLocation: String?,
    val frequency: String?,
    val workoutDuration: String?,
    val equipment: List<String>,
    val focusAreas: List<String>,
    val limitations: List<String>,
    val nutrition: String?,
    val sleep: String?,
    val planName: String
) {
    /** Pretvori odgovore v surovi Map za onQuizDataCollected callback. */
    fun toMap(): Map<String, Any> = mapOf(
        "gender"          to (gender ?: ""),
        "age"             to age,
        "height"          to height,
        "weight"          to weight,
        "bodyFat"         to (bodyFat ?: ""),
        "goal"            to (goal ?: ""),
        "experience"      to (experience ?: ""),
        "trainingLocation" to (trainingLocation ?: ""),
        "frequency"       to (frequency ?: ""),
        "equipment"       to equipment,
        "focusAreas"      to focusAreas,
        "limitations"     to limitations,
        "nutrition"       to (nutrition ?: ""),
        "sleep"           to (sleep ?: "")
    )
}

// ---------------------------------------------------------------------------
// BodyPlanQuizViewModel
// Odvisnosti sprejema prek konstruktorja (DI) — BREZ konkretnih data razredov!
// ---------------------------------------------------------------------------

class BodyPlanQuizViewModel(
    private val metricsRepository: MetricsRepository,
    private val authRepository: AuthStateRepository
) : ViewModel() {

    // --- Stanje generiranja (Submit) ---
    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Idle)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    // --- Predogled plana (prikazan preden uporabnik klikne "Save plan") ---
    private val _previewPlan = MutableStateFlow<PlanResult?>(null)
    val previewPlan: StateFlow<PlanResult?> = _previewPlan.asStateFlow()

    // -----------------------------------------------------------------------
    // computePreview — kliče se ko UI doseže zadnji korak kviza.
    // Izračuna AlgorithmData + generira PlanResult brez shranjevanja.
    // -----------------------------------------------------------------------
    fun computePreview(answers: QuizAnswers) {
        viewModelScope.launch {
            val algorithmData = computeAlgorithmData(answers)
            val plan = generateAdvancedCustomPlan(
                answers.gender, answers.age, answers.height, answers.weight,
                answers.bodyFat, answers.goal, answers.experience, answers.trainingLocation,
                answers.frequency, answers.workoutDuration, answers.equipment,
                answers.focusAreas, answers.limitations, answers.nutrition, answers.sleep
            ).copy(algorithmData = algorithmData)
            _previewPlan.value = plan
        }
    }

    // -----------------------------------------------------------------------
    // submitQuiz — kliče se ob kliku "Save plan".
    // Vsa poslovna logika, Auth preverba in shranjevanje teže so TUKAJ.
    // -----------------------------------------------------------------------
    fun submitQuiz(answers: QuizAnswers) {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading

            // 1. Auth preverba — brez Firebase SDK v UI
            val uid = authRepository.getCurrentUid()
            if (uid == null) {
                _uiState.value = QuizUiState.Error("Please log in to generate plan")
                return@launch
            }

            try {
                // 2. Izračun AlgorithmData (BMI, BMR, TDEE, makri) — čisti domenski klic
                val algorithmData = computeAlgorithmData(answers)

                // 3. Generiranje plana (domenski klic — pure Kotlin, brez suspend)
                val finalPlan = generateAdvancedCustomPlan(
                    answers.gender, answers.age, answers.height, answers.weight,
                    answers.bodyFat, answers.goal, answers.experience, answers.trainingLocation,
                    answers.frequency, answers.workoutDuration, answers.equipment,
                    answers.focusAreas, answers.limitations, answers.nutrition, answers.sleep
                ).copy(
                    name = answers.planName,
                    createdAt = System.currentTimeMillis(),
                    algorithmData = algorithmData
                )

                // 4. Emit Success — navigacija se sproži takoj v UI
                _uiState.value = QuizUiState.Success(finalPlan, answers)

                // 5. Shranjevanje začetne teže v ozadju (fire-and-forget).
                //    Napaka ne blokira navigacije.
                val weightKg = answers.weight.toDoubleOrNull()
                if (weightKg != null) {
                    launch {
                        try {
                            val dateStr = Clock.System.now()
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                .date.toString()
                            metricsRepository.saveWeight(uid, weightKg.toFloat(), dateStr)
                        } catch (_: Exception) {
                            // Fire-and-forget: napaka pri shranjevanju teže je tiha.
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = QuizUiState.Error("Error generating plan: ${e.message}")
            }
        }
    }

    /** Ponastavi stanje na Idle (npr. po navigaciji nazaj). */
    fun resetState() {
        _uiState.value = QuizUiState.Idle
        _previewPlan.value = null
    }

    // -----------------------------------------------------------------------
    // computeAlgorithmData — SSOT za BMI/BMR/TDEE izračun
    // Iz Composable-a premaknjeno sem (Clean Arch Anomaly 1 fix).
    // -----------------------------------------------------------------------
    private fun computeAlgorithmData(answers: QuizAnswers): AlgorithmData? {
        val heightInt = answers.height.toIntOrNull()
        val weightInt = answers.weight.toIntOrNull()
        val ageInt    = answers.age.toIntOrNull()

        // Guard: prepreči Division-by-zero (Faza 31.7 logika, zdaj v VM)
        if (heightInt == null || heightInt <= 0 ||
            weightInt == null || weightInt <= 0 ||
            ageInt    == null || ageInt    <= 0 ||
            answers.gender == null) return null

        val weightKg      = weightInt.toDouble()
        val heightCm      = heightInt.toDouble()
        val heightM       = heightCm / 100.0
        val bmi           = weightKg / (heightM * heightM)
        val isMale        = answers.gender == "Male"
        val bodyFatPct    = answers.bodyFat?.toDoubleOrNull()

        // Faza 9 SSOT: CalculateDailyCalorieTargetUseCase za BMR + TDEE + kalorični cilj
        val useCaseInput = CalculateDailyCalorieTargetUseCase.Input(
            weightKg          = weightKg,
            heightCm          = heightCm,
            ageYears          = ageInt,
            isMale            = isMale,
            activityLevel     = answers.frequency,
            goal              = answers.goal ?: "",
            bodyFatPercentage = bodyFatPct,
            experience        = answers.experience,
            limitations       = answers.limitations,
            sleep             = answers.sleep
        )
        val calorieResult  = CalculateDailyCalorieTargetUseCase().invoke(useCaseInput)
        val bmr            = calorieResult.bmr
        val tdee           = calorieResult.tdee
        val targetCalories = calorieResult.dailyCalorieTarget.toDouble()

        val macros        = calculateOptimalMacros(
            targetCalories, weightKg, answers.goal, answers.experience,
            ageInt, isMale, bodyFatPct, answers.nutrition, answers.limitations
        )
        val proteinPerKg  = macros.first.toDouble() / weightKg
        val caloriesPerKg = targetCalories / weightKg
        val trainingDays  = answers.frequency?.replace("x", "")?.toIntOrNull() ?: 3
        val trainingPlan  = generateIntelligentTrainingPlan(
            answers.goal, answers.experience, answers.trainingLocation,
            trainingDays, answers.limitations, ageInt, isMale, bodyFatPct
        )

        fun bmiCategory(b: Double) = when {
            b < 18.5 -> "Underweight"
            b < 25.0 -> "Normal weight"
            b < 30.0 -> "Overweight"
            else     -> "Obese"
        }

        return AlgorithmData(
            bmi           = bmi,
            bmr           = bmr,
            tdee          = tdee,
            proteinPerKg  = proteinPerKg,
            caloriesPerKg = caloriesPerKg,
            caloricStrategy = "Calculated deficit/surplus: ${"%.0f".format(tdee - targetCalories)} kcal",
            detailedTips  = listOf(
                "BMI: ${"%.1f".format(bmi)} - ${bmiCategory(bmi)}",
                "BMR: ${bmr.toInt()} kcal (basal metabolic rate)",
                "TDEE: ${tdee.toInt()} kcal (total daily energy expenditure)",
                "Protein goal: ${"%.1f".format(proteinPerKg)}g per kg body weight",
                "Daily caloric need: ${"%.0f".format(targetCalories)} kcal",
                "Recommended intake: ${"%.0f".format(targetCalories)} kcal/day",
                "Training frequency: ${answers.frequency} days per week optimal for ${answers.experience} level",
                "Sleep optimization: 8-9 hours recommended for recovery"
            ),
            macroBreakdown  = "Protein: ${"%.1f".format(proteinPerKg)}g/kg (${macros.first}g total), " +
                    "Carbs: ${macros.second}g, Fat: ${macros.third}g, " +
                    "Calories: ${"%.0f".format(targetCalories)} kcal/day, " +
                    "Fat loss deficit: ${"%.0f".format(tdee - targetCalories)} kcal/day",
            trainingStrategy = trainingPlan
        )
    }
}



