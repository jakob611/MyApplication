package com.example.myapplication.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AlgorithmPreferences
import com.example.myapplication.data.RefinedExercise
import com.example.myapplication.data.settings.UserProfileManager
import com.example.myapplication.domain.LastExerciseRecord
import com.example.myapplication.domain.WorkoutGenerationParams
import com.example.myapplication.domain.WorkoutGenerator
import com.example.myapplication.domain.WorkoutGoal
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.firestore.Query
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─── UI stanje generiranja treninga ────────────────────────────────────────
sealed class WorkoutGenerationState {
    /** Začetno stanje — generator še ni bil zagnan. */
    object Idle : WorkoutGenerationState()

    /** Generator čaka na zgodovino iz Firestorea (Memory Bridge). */
    object LoadingHistory : WorkoutGenerationState()

    /**
     * Generator uspešno zaključen.
     * @param exercises Generiran seznam vaj (z morebitno volume progresijo).
     * @param isProgressiveOverload true = vsaj ena vaja ima +5 % reps/težo glede na zadnjo sejo.
     */
    data class Ready(
        val exercises: List<RefinedExercise>,
        val isProgressiveOverload: Boolean = false
    ) : WorkoutGenerationState()

    /** Napaka pri generiranju (Firestore, prazna knjižnica vaj …). */
    data class Error(val message: String) : WorkoutGenerationState()
}

/**
 * ViewModel za generiranje treningov — Faza 12 (The Firestore Bridge).
 *
 * Odgovornosti:
 * 1. Pridobi zadnjo workout sejo iz Firestorea (fetchLastSessionForFocus).
 * 2. Prebere spol, cilj in težavnost iz uporabniškega profila (Firestore SSOT).
 * 3. Zgradi WorkoutGenerationParams prek AlgorithmPreferences.loadParamsWithOverrides() —
 *    SharedPrefs so samo fallback, Firestore vrednosti imejo prednost.
 * 4. Kliče WorkoutGenerator.generateWorkout() + applyVolumeProgression().
 * 5. Izpostavi [state] StateFlow za UI opazovanje.
 *
 * Klic: [prepareWorkout] → počakaj na [state] == Ready ali Error.
 */
class WorkoutSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<WorkoutGenerationState>(WorkoutGenerationState.Idle)
    val state: StateFlow<WorkoutGenerationState> = _state.asStateFlow()

    /** Ločen signal za UI badge "🔥 Danes močneje!" */
    private val _isProgressiveOverload = MutableStateFlow(false)
    val isProgressiveOverload: StateFlow<Boolean> = _isProgressiveOverload.asStateFlow()

    // ─── AlgorithmPreferences nastavitve (brez Context v composable) ─────────
    private fun getAlgoSettings(): SharedPreferencesSettings {
        return SharedPreferencesSettings(
            getApplication<Application>().getSharedPreferences("algorithm_prefs", Context.MODE_PRIVATE)
        )
    }

    /**
     * Resetiraj stanje nazaj na Idle (pokliči pred každim novim generiranjem,
     * da se izogneš race conditionom z StateFlow.first { }).
     */
    fun reset() {
        _state.value = WorkoutGenerationState.Idle
        _isProgressiveOverload.value = false
    }

    /**
     * Pripravi trening:
     * 1. Synchronously postavi state = LoadingHistory (zagotovi, da StateFlow.first { } ne
     *    takoj dobi starega Ready stanja).
     * 2. Asinhrono pridobi profil + zadnjo sejo iz Firestorea.
     * 3. Zgradi params z override vrednostmi (SSOT princip).
     * 4. Generiraj workout + apliciraj volume progresijo.
     * 5. Postavi state = Ready ali Error.
     *
     * @param focusAreas   Dnevni fokus mišic (že razrešen v WorkoutSessionScreen).
     * @param equipment    Dostopna oprema.
     * @param planDay      Trenutni dan plana (za deterministični seed).
     * @param exerciseCount Število vaj.
     * @param durationMinutes Trajanje seje v minutah.
     * @param targetDifficulty Ciljna težavnost (že izračunana v WorkoutSessionScreen).
     * @param goalFromPlan   WorkoutGoal razrešen iz PlanResult.
     * @param planExperience "Beginner", "Intermediate", "Advanced" — za profile fallback.
     */
    fun prepareWorkout(
        focusAreas: Set<String>,
        equipment: Set<String>,
        planDay: Int,
        exerciseCount: Int,
        durationMinutes: Int,
        targetDifficulty: Float,
        goalFromPlan: WorkoutGoal,
        planExperience: String
    ) {
        // SYNCHRONOUS: nastavi LoadingHistory PREDEN launch — zagotovi pravilno first { } kolekcijo
        _state.value = WorkoutGenerationState.LoadingHistory

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ── 1. Pridobi profil iz Firestorea (gender, goal) ────────────────────
                val uid = FirestoreHelper.getCurrentUserDocId() ?: ""
                val profile = runCatching {
                    UserProfileManager.loadProfileFromFirestore(uid)
                }.getOrNull() ?: UserProfileManager.loadProfile(uid)

                val genderOverride = profile.gender?.lowercase()?.trim() ?: ""

                // Preferiramo cilj iz Firestore profila; fallback = cilj iz plana
                val goalFromProfile: WorkoutGoal? = when (profile.workoutGoal.trim()) {
                    "Build muscle" -> WorkoutGoal.MUSCLE_GAIN
                    "Lose fat"    -> WorkoutGoal.WEIGHT_LOSS
                    "Recomposition" -> WorkoutGoal.STRENGTH
                    "Improve endurance" -> WorkoutGoal.ENDURANCE
                    else -> null
                }

                android.util.Log.d("WorkoutSessionVM",
                    "Profile loaded: gender=$genderOverride, goal=${profile.workoutGoal}")

                // ── 2. Pridobi zadnjo sejo za ta fokus (Memory Bridge) ────────────────
                val lastSession = fetchLastSessionForFocus(focusAreas.firstOrNull() ?: "")

                android.util.Log.d("WorkoutSessionVM",
                    "Last session: ${lastSession.size} records for focus '${focusAreas.firstOrNull()}'")

                // ── 3. Zgradi params z override vrednostmi iz Firestorea (SSOT) ───────
                val algoSettings = getAlgoSettings()
                AlgorithmPreferences.initDifficultyForPlan(algoSettings, planExperience)

                val params: WorkoutGenerationParams = AlgorithmPreferences.loadParamsWithOverrides(
                    settings = algoSettings,
                    difficultyOverride = targetDifficulty,
                    goalOverride = goalFromProfile ?: goalFromPlan,
                    focusOverride = focusAreas,
                    equipmentOverride = equipment,
                    genderOverride = genderOverride,
                    planDayOverride = planDay
                ).copy(
                    exerciseCount = exerciseCount,
                    durationMinutes = durationMinutes
                )

                android.util.Log.d("WorkoutSessionVM",
                    "Params built: focus=$focusAreas, gender=$genderOverride, " +
                    "difficulty=${params.targetDifficultyLevel}, planDay=$planDay, goal=${params.goal}")

                // ── 4. Generiraj workout ──────────────────────────────────────────────
                val generator = WorkoutGenerator()
                val rawExercises = generator.generateWorkout(params)

                if (rawExercises.isEmpty()) {
                    android.util.Log.w("WorkoutSessionVM", "Generator returned empty list!")
                    _state.value = WorkoutGenerationState.Error(
                        "Could not generate workout. Please check your settings."
                    )
                    return@launch
                }

                // ── 5. Apliciraj Volume Progression (Memory Bridge) ───────────────────
                val finalExercises = if (lastSession.isNotEmpty()) {
                    generator.applyVolumeProgression(rawExercises, lastSession)
                } else {
                    rawExercises
                }

                // Preveri ali je bila kaka vaja dejansko povečana (pogoj za 🔥 badge)
                val hasProgression = lastSession.isNotEmpty() &&
                    finalExercises.zip(rawExercises).any { (after, before) ->
                        after.parsedReps > before.parsedReps
                    }

                if (hasProgression) {
                    android.util.Log.d("WorkoutSessionVM", "🔥 PROGRESSIVE OVERLOAD detektiran!")
                }

                _isProgressiveOverload.value = hasProgression
                _state.value = WorkoutGenerationState.Ready(finalExercises, hasProgression)

            } catch (e: Exception) {
                android.util.Log.e("WorkoutSessionVM", "Error preparing workout: ${e.message}", e)
                _state.value = WorkoutGenerationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Pridobi seznam LastExerciseRecord iz zadnje workout seje v Firestoreu,
     * kjer se focusAreas ujema s podanim fokusom.
     *
     * Algoritem:
     * 1. Naloži zadnjih 10 workoutSessions (sortirano po timestamp DESC).
     * 2. Poišči PRVI dokument, katerega focusAreas vsebuje iskani fokus.
     * 3. Fallback: vzemi najnovejšo sejo (ne glede na fokus) — progresija je boljša kot nič.
     * 4. Razčleni exercises[] → LastExerciseRecord (samo vaje z reps > 0).
     *
     * Opomba: Starejše seje (shranjene pred Fazo 12) nimajo focusAreas/reps polj
     *   → vrnemo emptyList(), generator jih bo preskočil.
     */
    private suspend fun fetchLastSessionForFocus(focus: String): List<LastExerciseRecord> {
        return try {
            val ref = FirestoreHelper.getCurrentUserDocRef()
            val snapshot = ref.collection("workoutSessions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()

            if (snapshot.isEmpty) {
                android.util.Log.d("WorkoutSessionVM", "No workout sessions found in Firestore")
                return emptyList()
            }

            val focusLower = focus.lowercase().trim()

            // Poišči dokument z ujemajočim fokusom
            val matchingDoc = if (focusLower.isNotBlank()) {
                snapshot.documents.firstOrNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val docFocusAreas = doc.get("focusAreas") as? List<String>
                    if (!docFocusAreas.isNullOrEmpty()) {
                        docFocusAreas.any { saved ->
                            val sLower = saved.lowercase()
                            sLower.contains(focusLower) || focusLower.contains(sLower)
                        }
                    } else false
                } ?: snapshot.documents.firstOrNull()  // Fallback: najnovejša seja
            } else {
                snapshot.documents.firstOrNull()
            }

            if (matchingDoc == null) {
                android.util.Log.d("WorkoutSessionVM", "No matching session for focus='$focus'")
                return emptyList()
            }

            android.util.Log.d("WorkoutSessionVM",
                "Using session ${matchingDoc.id} for focus='$focus' " +
                "(focusAreas=${matchingDoc.get("focusAreas")})")

            // Razčleni exercises
            @Suppress("UNCHECKED_CAST")
            val rawExercises = matchingDoc.get("exercises") as? List<Map<String, Any>>
                ?: emptyList()

            rawExercises.mapNotNull { exMap ->
                try {
                    val name    = exMap["name"] as? String ?: return@mapNotNull null
                    val reps    = (exMap["reps"] as? Number)?.toInt() ?: 0
                    val sets    = (exMap["sets"] as? Number)?.toInt() ?: 1
                    val weight  = (exMap["weightKg"] as? Number)?.toFloat() ?: 0f

                    if (name.isBlank() || reps <= 0) return@mapNotNull null

                    LastExerciseRecord(
                        exerciseName = name,
                        reps         = reps,
                        sets         = sets,
                        weightKg     = weight
                    )
                } catch (e: Exception) {
                    android.util.Log.w("WorkoutSessionVM", "Failed to parse exercise: $exMap")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WorkoutSessionVM",
                "fetchLastSessionForFocus error for '$focus': ${e.message}")
            emptyList()
        }
    }
}


