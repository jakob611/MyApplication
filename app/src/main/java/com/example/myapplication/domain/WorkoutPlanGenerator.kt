package com.example.myapplication.domain
import com.example.myapplication.data.PlanResult
import com.example.myapplication.data.WeekPlan
import com.example.myapplication.data.DayPlan
import com.example.myapplication.utils.calculateAdvancedBMR
import com.example.myapplication.utils.calculateEnhancedTDEE
import com.example.myapplication.utils.calculateSmartCalories
import com.example.myapplication.utils.calculateOptimalMacros
// --- PLAN STRUCTURE GENERATION ---

/**
 * Generira plan z uvodnim tednom (Intro Week) če ne začnemo na ponedeljek.
 * Skupaj bo 4 tedni "pravih" treningov + morebitni uvodni dnevi.
 */
fun generatePlanWeeks(trainingDaysPerWeek: Int, focusAreas: List<String> = emptyList()): List<WeekPlan> {
    val safeTrainingDays = trainingDaysPerWeek.coerceIn(1, 7)

    // Določi dejanske pozicije rest dni glede na safeTrainingDays
    // POZICIJE SO 0-BASED (0=ponedeljek, 6=nedelja)
    // Število rest dni = 7 - safeTrainingDays
    val actualRestPositions: Set<Int> = when (safeTrainingDays) {
        7 -> emptySet()                          // 0 rest dni: W W W W W W W
        6 -> setOf(3)                            // 1 rest dan:  W W W R W W W
        5 -> setOf(3, 6)                         // 2 rest dni:  W W W R W W R
        4 -> setOf(2, 5, 6)                      // 3 rest dni:  W W R W W R R
        3 -> setOf(1, 3, 5, 6)                   // 4 rest dni:  W R W R W R R
        2 -> setOf(1, 2, 4, 5, 6)               // 5 rest dni:  W R R W R R R
        1 -> (1..6).toSet()                      // 6 rest dni:  W R R R R R R
        else -> (safeTrainingDays until 7).toSet()
    }

    val weeks = mutableListOf<WeekPlan>()
    val totalWeeks = 4
    var globalDayCounter = 1
    var workoutDayIndex = 0 // za rotacijo focusAreas

    // Pripravi fokus labele za workout dni
    val focuses = if (focusAreas.isNotEmpty()) focusAreas else listOf("Full Body")

    // --- INTRO WEEK LOGIC ---
    val today = java.time.LocalDate.now()
    val dayOfWeek = today.dayOfWeek.value // 1=Mon, 7=Sun
    val isMonday = dayOfWeek == 1
    
    // Removed duplicate weeks declaration
    
    // Uvodni dnevi (Intro) - samo če NI ponedeljek
    if (!isMonday) {
        val daysUntilSunday = 8 - dayOfWeek // e.g. Tue(2) -> 8-2 = 6 days (Tue, Wed, Thu, Fri, Sat, Sun)
        val introDays = mutableListOf<DayPlan>()
        
        // Progression of focuses: match recognized keys in WorkoutGenerator
        // Start with lighter/mobility, move to full body/strength
        val progression = listOf("Flexibility", "Cardio", "Balance", "Full Body", "Full Body", "Full Body")
        
        // Intro teden je lažji / intro
        for (i in 0 until daysUntilSunday) {
            val isRest = (i == daysUntilSunday - 1) // Zadnji dan (Nedelja) je vedno Rest v intru
            
            // Določi fokus glede na pozicijo - proti koncu tedna "več fokusa" (Full Body)
            // Začnemo z mehkimi (Flexibility/Cardio), končamo z Rest
            val focus = if (isRest) "Rest" else {
                 if (daysUntilSunday <= 2) "Full Body" // Samo petek/sobota -> kar Full Body
                 else {
                     // Mapiramo indeks i na napredek
                     // i=0 (prvi dan) -> Flexibility
                     // i -> mapiraj na progression array
                     val pIndex = (i.toDouble() / (daysUntilSunday - 1) * (progression.size - 1)).toInt().coerceIn(0, progression.size - 1)
                     progression[pIndex]
                 }
            }
            
            introDays.add(
                DayPlan(
                    dayNumber = globalDayCounter,
                    exercises = emptyList(),
                    isRestDay = isRest,
                    focusLabel = focus
                )
            )
            globalDayCounter++
        }
        weeks.add(WeekPlan(weekNumber = 0, days = introDays))
    }

    // --- REGULAR WEEKS ---
    for (weekNum in 1..totalWeeks) {
        val days = mutableListOf<DayPlan>()
        for (dayInWeek in 0 until 7) { // 0 = ponedeljek, 6 = nedelja
            val isRest = dayInWeek in actualRestPositions
            val focus = if (isRest) "Rest" else {
                focuses[workoutDayIndex % focuses.size]
            }
            if (!isRest) workoutDayIndex++

            days.add(
                DayPlan(
                    dayNumber = globalDayCounter,
                    exercises = emptyList(),
                    isRestDay = isRest,
                    focusLabel = focus
                )
            )
            globalDayCounter++
        }
        weeks.add(WeekPlan(weekNumber = weekNum, days = days))
    }

    return weeks
}

// --- COMPLETELY UPGRADED ALGORITHM ---

fun generateAdvancedCustomPlan(
    gender: String?, age: String, height: String, weight: String, bodyFat: String?,
    goal: String?, experience: String?, location: String?,
    freq: String?, // Added freq parameter
    workoutDuration: String?, // Added param
    equipment: List<String>,
    focusAreas: List<String>, // Added param
    limitations: List<String>, nutrition: String?, sleep: String?
): PlanResult {

    // Parse basic data with enhanced validation
    val weightKg = weight.toDoubleOrNull() ?: 70.0
    val heightCm = height.toDoubleOrNull() ?: 175.0
    val ageYears = age.toIntOrNull() ?: 25
    val bodyFatPercent = bodyFat?.toDoubleOrNull()
    val isMale = gender == "Male"

    // Advanced body composition analysis
    val heightM = heightCm / 100.0
    val bmi = weightKg / (heightM * heightM)

    // Enhanced BMR calculation with body fat consideration
    val bmr = calculateAdvancedBMR(weightKg, heightCm, ageYears, isMale, bodyFatPercent)

    // Sophisticated TDEE calculation
    val tdee = calculateEnhancedTDEE(bmr, freq, experience, ageYears, limitations, sleep)

    // Advanced caloric target with multiple factors
    val targetCalories = calculateSmartCalories(tdee, goal, experience, bmi, ageYears, isMale, bodyFatPercent, limitations)

    // Precision macronutrient distribution
    val macros = calculateOptimalMacros(targetCalories, weightKg, goal, experience, ageYears, isMale, bodyFatPercent, nutrition, limitations)

    // Intelligent training program design
    val trainingDays = determineOptimalTrainingDays(freq, experience, ageYears, goal, limitations)
    val trainingPlan = generateIntelligentTrainingPlan(goal, experience, location, trainingDays, limitations, ageYears, isMale, bodyFatPercent)

    // Dynamic session length calculation - use user's choice if provided
    val sessionLength = when(workoutDuration) {
        "15-30 min" -> 25
        "30-45 min" -> 40
        "45-60 min" -> 55
        "60+ min" -> 75
        else -> calculateOptimalSessionLength(experience, goal, location, ageYears, limitations, trainingDays)
    }

    // Comprehensive, personalized recommendations
    val tips = generatePersonalizedTips(
        goal, experience, location, limitations, nutrition, sleep,
        ageYears, isMale, bmi, trainingDays, macros.first, macros.second, macros.third,
        bodyFatPercent, targetCalories, tdee
    )

    // Generate actual plan structure: 4 weeks × 7 days (workout + rest) based on frequency
    val weeksStructure = generatePlanWeeks(trainingDays, focusAreas)

    return PlanResult(
        weeks = weeksStructure,
        id = java.util.UUID.randomUUID().toString(),
        name = "",
        calories = targetCalories.toInt(),
        protein = macros.first,
        carbs = macros.second,
        fat = macros.third,
        trainingPlan = trainingPlan,
        trainingDays = trainingDays,
        sessionLength = sessionLength,
        tips = tips,
        createdAt = System.currentTimeMillis(),
        trainingLocation = location ?: "Home",
        experience = experience,
        goal = goal,
        equipment = equipment,
        focusAreas = focusAreas,
        startDate = java.time.LocalDate.now().toString()
    )
}

// --- TRAINING AND PLANNING FUNCTIONS ---

fun determineOptimalTrainingDays(frequency: String?, experience: String?, age: Int, goal: String?, limitations: List<String>): Int {
    // Spoštujemo izbiro uporabnika — vrnemo točno tisto vrednost ki jo je izbral.
    // Skrajšanje frekvence (npr. Beginner max 4x) povzroča napako v generatePlanWeeks:
    // plan bi imel 4 vadbe namesto 5, čeprav je uporabnik izbral 5x.
    val baseFrequency = when (frequency) {
        "2x" -> 2
        "3x" -> 3
        "4x" -> 4
        "5x" -> 5
        "6x" -> 6
        else -> 3
    }

    return maxOf(baseFrequency, 2)  // Minimum 2 dni, sicer točno to kar je izbral uporabnik
}

fun generateIntelligentTrainingPlan(
    goal: String?, experience: String?, location: String?, trainingDays: Int,
    limitations: List<String>, age: Int, isMale: Boolean, bodyFat: Double?
): String {

    val hasKneeIssues = limitations.contains("Knee injury")
    val hasShoulderIssues = limitations.contains("Shoulder injury")
    val hasBackPain = limitations.contains("Back pain")
    val hasAsthma = limitations.contains("Asthma")
    val hasCardiovascular = limitations.any { it in listOf("High blood pressure", "Diabetes") }
    val isHome = location == "Home"

    val cardioModification = when {
        hasAsthma -> "low-intensity steady-state with breathing focus"
        hasCardiovascular -> "moderate-intensity with heart rate monitoring"
        age > 55 -> "low-impact steady-state"
        else -> "mixed intensity (HIIT and steady-state)"
    }

    return when (goal) {
        "Build muscle" -> when {
            experience == "Beginner" && trainingDays <= 3 -> {
                if (isHome) {
                    buildString {
                        append("Beginner full-body routine 3x/week with bodyweight and basic equipment. ")
                        append("Focus: progressive push-ups, squats, lunges, planks. ")
                        if (hasBackPain) append("Emphasize core stability and avoid spinal loading. ")
                        if (hasKneeIssues) append("Use supported squats and avoid deep ranges. ")
                        append("Start with 2 sets, progress to 3 sets over 4 weeks.")
                    }
                } else {
                    buildString {
                        append("Full-body beginner strength program 3x/week. ")
                        append("Master: squats, deadlifts")
                        if (hasBackPain) append(" (rack pulls or trap bar)")
                        append(", bench press")
                        if (hasShoulderIssues) append(" (incline or machine press)")
                        append(", rows, overhead press. Progressive overload focus.")
                    }
                }
            }

            experience == "Intermediate" && trainingDays == 4 -> {
                buildString {
                    append("Upper/Lower split 4x/week. Upper: push/pull supersets. Lower: ")
                    if (hasKneeIssues) {
                        append("machine-based quad/glute work, ")
                    } else {
                        append("squats, deadlift variations, ")
                    }
                    append("unilateral training. Periodized progression.")
                }
            }

            experience == "Advanced" && trainingDays >= 5 -> {
                if (isHome) {
                    "Advanced bodyweight progressions: weighted calisthenics, gymnastic progressions, unilateral strength work. 5-6x/week with skill development focus."
                } else {
                    buildString {
                        append("Push/Pull/Legs/Arms split or body-part specialization. ")
                        append("Advanced techniques: cluster sets, rest-pause, periodization. ")
                        if (bodyFat != null && bodyFat > (if (isMale) 15 else 25)) {
                            append("Include metabolic finishers.")
                        }
                    }
                }
            }

            else -> {
                "Progressive overload strength training with compound focus. Adjust volume based on recovery capacity and age."
            }
        }

        "Lose fat" -> {
            buildString {
                append("Fat loss program combining strength training with $cardioModification. ")
                when {
                    experience == "Beginner" -> append("Circuit training 3x/week with metabolic focus. ")
                    trainingDays >= 4 -> append("Upper/lower split with cardio acceleration. ")
                    else -> append("Full-body strength with superset format. ")
                }
                if (hasCardiovascular) {
                    append("Monitor heart rate, stay in moderate zones.")
                } else if (!hasAsthma && age < 40) {
                    append("Include 2x HIIT sessions weekly.")
                }
            }
        }

        "Recomposition" -> {
            buildString {
                append("Body recomposition program balancing strength and conditioning. ")
                when {
                    experience == "Beginner" -> append("Full-body strength 3x/week with strategic cardio placement. ")
                    trainingDays >= 4 -> append("Push/pull/legs with targeted cardio. Strength priority. ")
                    else -> append("Compound-focused training with minimal but effective cardio. ")
                }
                append("Requires patience and consistent nutrition.")
            }
        }

        "Improve endurance" -> {
            buildString {
                append("Endurance-focused program with $cardioModification. ")
                when {
                    trainingDays <= 3 -> append("2 days strength for injury prevention, 2-3 days aerobic base building. ")
                    trainingDays >= 5 -> append("Periodized approach: 70% aerobic base, 20% tempo, 10% intervals. 2-3 strength days. ")
                    else -> append("3 days aerobic work, 2 days strength training. ")
                }
                if (hasAsthma) append("Focus on breathing patterns and gradual progression.")
            }
        }

        "General health" -> {
            buildString {
                append("Balanced health and wellness program. ")
                append("Combination of strength training (${trainingDays/2 + 1}x), ")
                append("cardiovascular exercise (${trainingDays/2}x), and mobility work. ")
                if (age > 50) append("Emphasis on functional movements and bone health. ")
                if (hasCardiovascular) append("Heart-healthy moderate intensity focus.")
            }
        }

        else -> {
            "Balanced fitness routine combining strength and cardiovascular training based on individual needs and limitations."
        }
    }
}

fun calculateOptimalSessionLength(experience: String?, goal: String?, location: String?, age: Int, limitations: List<String>, trainingDays: Int): Int {
    val baseLength = when (experience) {
        "Beginner" -> 40
        "Intermediate" -> 60
        "Advanced" -> 75
        else -> 55
    }

    val goalAdjustment = when (goal) {
        "Build muscle" -> when (experience) {
            "Advanced" -> 20
            "Intermediate" -> 15
            else -> 5
        }
        "Lose fat" -> -10  // Shorter, more intense
        "Improve endurance" -> 25
        "General health" -> 0
        else -> 0
    }

    val locationAdjustment = when (location) {
        "Home" -> -10  // Less equipment transition time
        "Gym" -> 5     // Travel time between equipment
        else -> 0
    }

    val ageAdjustment = when {
        age < 25 -> 5
        age in 25..40 -> 0
        age in 41..55 -> -5
        age > 55 -> -15
        else -> 0
    }

    val frequencyAdjustment = when {
        trainingDays >= 6 -> -15  // Shorter sessions for high frequency
        trainingDays <= 2 -> 15   // Longer sessions for low frequency
        else -> 0
    }

    val limitationAdjustment = when {
        limitations.any { it in listOf("Asthma", "High blood pressure") } -> -10
        limitations.any { it in listOf("Knee injury", "Shoulder injury", "Back pain") } -> -5
        else -> 0
    }

    val totalLength = baseLength + goalAdjustment + locationAdjustment + ageAdjustment + frequencyAdjustment + limitationAdjustment

    return maxOf(30, minOf(120, totalLength))  // Cap between 30-120 minutes
}

fun generatePersonalizedTips(
    goal: String?, experience: String?, location: String?, limitations: List<String>,
    nutrition: String?, sleep: String?, age: Int, isMale: Boolean, bmi: Double,
    trainingDays: Int, protein: Int, carbs: Int, fat: Int, bodyFat: Double?,
    calories: Double, tdee: Double
): List<String> {

    val tips = mutableListOf<String>()

    // Caloric and metabolic insights
    val deficit = tdee - calories
    when {
        deficit > 300 -> tips.add("⚡ You're in a ${deficit.toInt()} calorie deficit - expect 0.5-0.7kg fat loss per week")
        deficit > 0 -> tips.add("⚡ Moderate ${deficit.toInt()} calorie deficit - sustainable fat loss of 0.3-0.5kg per week")
        deficit < -200 -> tips.add("⚡ ${(-deficit).toInt()} calorie surplus for muscle gain - expect 0.25-0.5kg per week")
        else -> tips.add("⚡ Maintenance calories - ideal for body recomposition")
    }

    // Body composition specific advice
    if (bodyFat != null) {
        when {
            bodyFat < (if (isMale) 10 else 16) -> {
                tips.add("🎯 Very lean physique - focus on performance and muscle quality over further fat loss")
                tips.add("💪 Consider reverse dieting to improve metabolic health")
            }
            bodyFat > (if (isMale) 20 else 30) -> {
                tips.add("🎯 Prioritize fat loss with strength training to preserve muscle mass")
                tips.add("📊 Track measurements and progress photos over scale weight")
            }
            else -> {
                tips.add("🎯 Good body composition range - ideal for recomposition goals")
            }
        }
    }

    // Goal-specific advanced tips
    when (goal) {
        "Build muscle" -> {
            tips.add("🥩 Distribute ${protein}g protein across 4-5 meals (20-40g per meal) for optimal synthesis")
            tips.add("⏰ Consume 20-40g protein within 2 hours post-workout")
            if (experience == "Beginner") {
                tips.add("📈 Linear progression: add 2.5-5kg to compounds weekly for first 3 months")
            } else {
                tips.add("📊 Track volume (sets × reps × weight) and aim for 5-10% weekly increases")
            }
        }

        "Lose fat" -> {
            tips.add("🔥 High protein (${protein}g) preserves muscle in deficit - aim for 0.8-1.2g per lb bodyweight")
            tips.add("🏃‍♂️ Mix strength training with cardio - strength maintains muscle, cardio burns calories")
            if (bmi > 30) {
                tips.add("🚶‍♂️ Start with low-impact activities (walking, swimming) to protect joints")
            } else {
                tips.add("⚡ Include 2-3 HIIT sessions weekly for enhanced fat oxidation")
            }
        }

        "Recomposition" -> {
            tips.add("⚖️ Recomp is slow - track strength gains and body measurements over scale weight")
            tips.add("🎯 Ideal for your experience level - maintain calories around ${calories.toInt()}")
            tips.add("⏳ Expect visible changes in 8-12 weeks with consistent training and nutrition")
        }
    }

    // Age-specific recommendations
    // Age-specific recommendations
    when {
        age < 25 -> {
            tips.add("🚀 Peak anabolic years - take advantage with consistent training and nutrition")
            tips.add("💪 Your recovery is excellent - you can handle higher training volumes")
        }
        age in 25..35 -> {
            tips.add("⚖️ Metabolism starts slowing - pay attention to portion control and food quality")
            tips.add("🧘‍♂️ Stress management becomes more important for recovery")
        }
        age in 36..50 -> {
            tips.add("🔧 Recovery takes longer - prioritize sleep and stress management")
            tips.add("💀 Bone density focus: include weight-bearing exercises")
            tips.add("🔄 Consider deload weeks every 4-6 weeks")
        }
        age > 50 -> {
            tips.add("🦴 Bone health priority: resistance training 2-3x/week minimum")
            tips.add("🤸‍♂️ Include balance and mobility work to prevent falls")
            tips.add("💊 Consider vitamin D and calcium supplementation")
            tips.add("👨‍⚕️ Regular health check-ups become increasingly important")
        }
    }

    // Training frequency and recovery insights
    when (trainingDays) {
        2 -> {
            tips.add("💯 Full-body workouts maximize efficiency with 2 sessions")
            tips.add("🎯 Focus on compound movements for maximum muscle activation")
        }
        in 3..4 -> {
            tips.add("✅ Optimal training frequency for most goals and recovery")
            tips.add("📅 Allow at least one full rest day between sessions")
        }
        in 5..6 -> {
            tips.add("📊 High frequency requires excellent recovery: 8+ hours sleep, proper nutrition")
            tips.add("🔄 Consider periodizing intensity - not every session should be maximal")
            tips.add("👂 Listen to your body - reduce volume if constantly fatigued")
        }
    }

    // Nutrition-specific advanced guidance
    when (nutrition) {
        "Vegetarian" -> {
            tips.add("🌱 Combine legumes + grains for complete amino acid profiles")
            tips.add("💊 Monitor B12, iron, zinc levels - consider supplementation")
            tips.add("🥛 Include dairy/eggs for high-quality protein if tolerated")
        }
        "Vegan" -> {
            tips.add("🌿 Plan protein carefully: legumes, quinoa, hemp seeds, plant proteins")
            tips.add("💊 Essential supplements: B12, D3, algae omega-3s, possibly iron/zinc")
            tips.add("🌈 Eat rainbow variety for micronutrient completeness")
        }
        "Keto/LCHF" -> {
            tips.add("⚡ Time remaining carbs (${carbs}g) around workouts for performance")
            tips.add("🥑 Focus on healthy fats: avocados, nuts, olive oil, fatty fish")
            tips.add("💧 Increase water and electrolyte intake on low-carb")
        }
        "Intermittent fasting" -> {
            tips.add("⏰ Train either fasted or during feeding window based on preference")
            tips.add("🍽️ Break fast with protein-rich meal for muscle preservation")
            tips.add("💧 Stay hydrated during fasting periods")
        }
    }

    // Sleep optimization (crucial for results)
    when (sleep) {
        "Less than 6" -> {
            tips.add("😴 CRITICAL: Poor sleep severely impacts muscle growth and fat loss")
            tips.add("🌙 Sleep hygiene: dark room, cool temp, no screens 1hr before bed")
            tips.add("☕ Avoid caffeine 6+ hours before bedtime")
            tips.add("📉 You may need to reduce training intensity until sleep improves")
        }
        "6-7" -> {
            tips.add("😴 Aim for 7-9 hours for optimal recovery and performance")
            tips.add("⏰ Consistent sleep/wake times even on weekends")
        }
        "7-8" -> {
            tips.add("✅ Excellent sleep! This significantly supports your fitness goals")
            tips.add("🔄 Maintain this sleep quality for continued progress")
        }
        in listOf("8-9", "9+") -> {
            tips.add("🌟 Outstanding sleep quality - your recovery is optimized")
            tips.add("💪 This gives you a significant advantage in reaching your goals")
        }
    }

    // Medical condition considerations
    limitations.forEach { limitation ->
        when (limitation) {
            "Knee injury" -> {
                tips.add("🦵 Avoid deep squats and jumping - focus on quad/glute strengthening")
                tips.add("🚴‍♂️ Low-impact cardio: cycling, swimming, elliptical")
                tips.add("🔥 Always warm up thoroughly and consider knee support")
            }
            "Shoulder injury" -> {
                tips.add("💪 Avoid overhead movements - focus on horizontal push/pull patterns")
                tips.add("🏋️‍♂️ Strengthen rotator cuffs with light resistance band work")
                tips.add("🌡️ Thorough shoulder warm-up before any upper body training")
            }
            "Back pain" -> {
                tips.add("🧘‍♂️ Core strengthening and hip mobility are priorities")
                tips.add("🏋️‍♂️ Use machines or dumbbells instead of barbell initially")
                tips.add("📐 Focus on neutral spine positioning in all exercises")
            }
            "Asthma" -> {
                tips.add("💨 Always have inhaler available during workouts")
                tips.add("🌡️ Warm up gradually, avoid sudden intense bursts")
                tips.add("🏊‍♂️ Swimming often triggers fewer asthma symptoms")
                tips.add("❄️ Cold weather exercise may trigger symptoms")
            }
            "High blood pressure" -> {
                tips.add("📈 Monitor heart rate during exercise - stay in moderate zones")
                tips.add("🧘‍♂️ Include relaxation techniques and stress management")
                tips.add("🧂 Reduce sodium intake and focus on whole foods")
            }
            "Diabetes" -> {
                tips.add("📊 Monitor blood glucose before, during, and after exercise")
                tips.add("🍎 Time carbohydrate intake around workouts carefully")
                tips.add("👨‍⚕️ Work with healthcare provider on exercise prescription")
            }
        }
    }

    // Advanced macronutrient timing
    tips.add("🍽️ Spread protein intake: ${(protein/4.0).toInt()}-${(protein/3.0).toInt()}g per meal for optimal absorption")

    when {
        carbs < 100 -> {
            tips.add("⚡ Low carb may affect high-intensity performance - monitor energy levels")
            tips.add("🥑 Ensure adequate fat intake for hormone production")
        }
        carbs > 300 -> {
            tips.add("🍞 High carb intake great for performance - time around workouts")
            tips.add("🏃‍♂️ Consider carb cycling: higher on training days, lower on rest days")
        }
        else -> {
            tips.add("⚖️ Balanced carb intake - adjust timing based on training schedule")
        }
    }

    // BMI-specific guidance
    when {
        bmi < 18.5 -> {
            tips.add("📈 Underweight: Focus on gradual weight gain with strength training")
            tips.add("🍽️ Eat calorie-dense, nutritious foods frequently")
        }
        bmi > 30 -> {
            tips.add("🎯 Obesity range: Prioritize sustainable lifestyle changes over quick fixes")
            tips.add("👨‍⚕️ Consider working with healthcare provider for comprehensive approach")
        }
        bmi in 25.0..29.9 -> {
            tips.add("⚖️ Overweight range: Small, sustainable changes yield big results")
            tips.add("📊 Focus on body composition over scale weight")
        }
    }

    // Progressive overload and tracking
    when (experience) {
        "Beginner" -> {
            tips.add("📈 Track workouts: aim to add weight, reps, or sets each week")
            tips.add("🎯 Focus on form over weight - build movement patterns first")
            tips.add("📚 Learn proper exercise technique from reliable sources")
        }
        "Intermediate" -> {
            tips.add("📊 Periodize training: vary intensity and volume every 4-6 weeks")
            tips.add("🔍 Track key metrics: strength gains, body measurements, energy levels")
            tips.add("🧘‍♂️ Recovery becomes more important - manage stress actively")
        }
        "Advanced" -> {
            tips.add("🔬 Fine-tune based on individual response and weak points")
            tips.add("📈 Consider advanced techniques: cluster sets, rest-pause, autoregulation")
            tips.add("🧠 Mental training becomes crucial - visualization and mindset work")
        }
    }

    // Hydration and general health
    tips.add("💧 Hydration goal: clear/pale yellow urine as indicator")
    tips.add("🥗 Include 5-7 servings fruits/vegetables daily for micronutrients")
    tips.add("🧘‍♂️ Manage stress: meditation, yoga, or other relaxation techniques")
    tips.add("📸 Track progress photos and measurements, not just scale weight")
    tips.add("🔄 Consistency over perfection - sustainable habits win long-term")

    // Location-specific equipment recommendations
    if (location == "Home") {
        tips.add("🏠 Essential home equipment: adjustable dumbbells, resistance bands, pull-up bar")
        tips.add("📱 Use fitness apps or online videos for guidance and motivation")
        tips.add("🏠 Create dedicated workout space for consistency")
    } else if (location == "Gym") {
        tips.add("🏋️‍♂️ Learn gym etiquette and don't hesitate to ask for equipment help")
        tips.add("📋 Have a plan before entering - maximize efficiency and focus")
        tips.add("🎵 Use variety of equipment to prevent boredom and plateaus")
    }

    // Final motivational and practical advice
    tips.add("📅 Schedule workouts like important appointments - consistency is key")
    tips.add("🎯 Set both process goals (workout frequency) and outcome goals (strength/weight)")
    tips.add("👥 Consider finding a workout partner or trainer for accountability")
    tips.add("📖 Continuously educate yourself about nutrition and training")
    tips.add("🏆 Celebrate small wins - progress isn't always linear")

    // Return the most relevant tips (limit to prevent overwhelming the user)
    return tips.distinct().take(15)
}






