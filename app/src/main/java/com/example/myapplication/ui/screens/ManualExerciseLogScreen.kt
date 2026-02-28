package com.example.myapplication.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

// ─── Data classes ─────────────────────────────────────────────────────────────

/** Osnovna informacija za prikaz v seznamu in logiranje */
internal data class ExerciseInfo(
    val name: String,
    val description: String,
    val caloriesPerMinPerKg: Double,
    val defaultSets: Int,
    val defaultReps: Int,
    val defaultDuration: Int?,
    val score: Double = 0.0,           // Za razvrščanje
    val gender: String = "universal",  // "male", "female", "universal"
    val difficulty: Int = 5,
    val primaryMuscle: String = "",
    val primaryMuscleIntensity: Int = 0,
    val muscleDetails: List<Pair<String, String>> = emptyList(),
    val videoUrl: String = "",
    val equipment: String = "bodyweight"  // npr. "bodyweight", "dumbbells", "barbell"
)

// ─── Gender cache ──────────────────────────────────────────────────────────────

/** Singleton za caching spola, opreme in user score-a — naložimo enkrat ob zagonu */
object GenderCache {
    private const val PREFS_NAME = "gender_cache"
    private const val KEY_GENDER = "gender"
    private const val KEY_LOADED = "loaded"
    private const val KEY_EQUIPMENT = "equipment"
    private const val KEY_USER_SCORE = "user_score"
    private const val KEY_FOCUS_AREAS = "focus_areas"

    private var cached: String? = null
    private var cachedEquipment: Set<String>? = null
    private var cachedUserScore: Float = 5f
    private var cachedFocusAreas: List<String>? = null

    fun getGender(context: Context): String? {
        if (cached != null) return cached
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_LOADED, false)) return null
        cached = prefs.getString(KEY_GENDER, null)
        return cached
    }

    fun getEquipment(context: Context): Set<String> {
        cachedEquipment?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val eq = prefs.getStringSet(KEY_EQUIPMENT, null)
        return eq ?: setOf("bodyweight")
    }

    fun getUserScore(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_USER_SCORE, 5f)
    }

    fun getFocusAreas(context: Context): List<String> {
        cachedFocusAreas?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fa = prefs.getString(KEY_FOCUS_AREAS, "") ?: ""
        return if (fa.isBlank()) emptyList() else fa.split(",").filter { it.isNotBlank() }
    }

    fun loadFromFirestoreIfNeeded(context: Context, onDone: (String?) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LOADED, false)) {
            cached = prefs.getString(KEY_GENDER, null)
            cachedEquipment = prefs.getStringSet(KEY_EQUIPMENT, null)
            cachedUserScore = prefs.getFloat(KEY_USER_SCORE, 5f)
            cachedFocusAreas = prefs.getString(KEY_FOCUS_AREAS, "")
                ?.split(",")?.filter { it.isNotBlank() }
            onDone(cached)
            return
        }
        // POPRAVLJENO: beri iz email dokumenta, ne UID
        val docRef = try {
            com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()?.let { id ->
                Firebase.firestore.collection("users").document(id)
            }
        } catch (_: Exception) { null }

        if (docRef == null) { onDone(null); return }

        docRef.get()
            .addOnSuccessListener { doc ->
                val g = doc.getString("gender")
                @Suppress("UNCHECKED_CAST")
                val eqList = (doc.get("equipment") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val eqSet = (eqList.map { it.lowercase() } + listOf("bodyweight")).toSet()
                // focusAreas
                @Suppress("UNCHECKED_CAST")
                val focusList = (doc.get("focusAreas") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val userScore = (doc.get("currentDifficulty") as? Number)?.toFloat()
                    ?: (doc.get("experienceScore") as? Number)?.toFloat()
                    ?: run {
                        // Fallback iz experience stringa
                        when (doc.getString("experience")) {
                            "Beginner" -> 4f
                            "Intermediate" -> 7f
                            "Advanced" -> 9f
                            else -> 5f
                        }
                    }
                prefs.edit()
                    .putString(KEY_GENDER, g)
                    .putStringSet(KEY_EQUIPMENT, eqSet)
                    .putFloat(KEY_USER_SCORE, userScore)
                    .putString(KEY_FOCUS_AREAS, focusList.joinToString(","))
                    .putBoolean(KEY_LOADED, true)
                    .apply()
                cached = g
                cachedEquipment = eqSet
                cachedUserScore = userScore
                cachedFocusAreas = focusList
                onDone(g)
            }
            .addOnFailureListener { onDone(null) }
    }

    /** Pokliči pri odjavi za brisanje cache-a */
    fun clear(context: Context) {
        cached = null
        cachedEquipment = null
        cachedUserScore = 5f
        cachedFocusAreas = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

// ─── Exercise Repository ───────────────────────────────────────────────────────

/** Singleton za enkraten nakladen seznam vaj in score lookup table */
object ExerciseRepository {
    private var exercises: List<ExerciseInfo>? = null

    internal fun getExercises(context: Context): List<ExerciseInfo> {
        exercises?.let { return it }
        val loaded = loadAllExercisesInternal(context)
        exercises = loaded
        return loaded
    }

    internal fun getFiltered(context: Context, gender: String?): List<ExerciseInfo> {
        val all = getExercises(context)
        return if (gender == null) all
        else all.filter { ex ->
            ex.gender == "universal" || ex.gender == gender.lowercase()
        }
    }

    /**
     * Vrne filtrirane in rangirane vaje glede na:
     * - spol, opremo
     * - userScore: vaje katerih difficulty je blizu userScore dobijo bonus
     * - focusAreas: vaje ki ustrezajo fokusnemu področju dobijo bonus (nastavljeno v planu)
     */
    internal fun getFilteredForUser(
        context: Context,
        gender: String?,
        userEquipment: Set<String>,
        userScore: Float,
        focusAreas: List<String> = emptyList()
    ): List<ExerciseInfo> {
        val all = getExercises(context)
        val filtered = all.filter { ex ->
            val genderOk = gender == null || ex.gender == "universal" || ex.gender == gender.lowercase()
            val exEq = ex.equipment.lowercase().trim()
            val eqOk = exEq == "bodyweight" || exEq.isBlank() ||
                userEquipment.any { userEq ->
                    val u = userEq.lowercase().trim()
                    exEq.contains(u) || u.contains(exEq)
                }
            genderOk && eqOk
        }

        // Mapa fokusov na mišična polja (enaka kot WorkoutGenerator)
        val focusMuscleKeys: Set<String> = focusAreas
            .filter { !it.equals("None", ignoreCase = true) && !it.equals("Full Body", ignoreCase = true) }
            .flatMap { focus ->
                when (focus.lowercase().trim()) {
                    "upper body"  -> listOf("prsni", "hrbet", "ramena", "biceps", "triceps", "prednje podlakti")
                    "lower body"  -> listOf("noge – quads", "noge – hamstrings", "zadnjica", "meča")
                    "core"        -> listOf("trebuh / core")
                    "cardio"      -> listOf("kardio")
                    "flexibility" -> listOf("raztezanje")
                    "legs"        -> listOf("noge – quads", "noge – hamstrings", "zadnjica", "meča")
                    "arms"        -> listOf("biceps", "triceps", "prednje podlakti")
                    "chest"       -> listOf("prsni")
                    "back"        -> listOf("hrbet")
                    "abs"         -> listOf("trebuh / core")
                    "shoulders"   -> listOf("ramena")
                    else          -> listOf(focus.lowercase())
                }
            }.toSet()

        return filtered.sortedByDescending { ex -> personalizedScore(ex, userScore, focusMuscleKeys) }
    }

    /** Personaliziran score: vaje bližje userScore dobijo bonus + bonus za ujemanje s focusAreas */
    private fun personalizedScore(ex: ExerciseInfo, userScore: Float, focusMuscleKeys: Set<String> = emptySet()): Double {
        val baseScore = ex.score
        // Bonus za ujemanje težavnosti
        val diffDistance = Math.abs(ex.difficulty.toFloat() - userScore)
        val diffBonus = (10.0 - diffDistance) / 10.0  // 0..1
        // Bonus za ujemanje fokusa — preveri vse mišice vaje
        val focusBonus = if (focusMuscleKeys.isEmpty()) {
            0.0
        } else {
            val muscleNames = ex.muscleDetails.map { it.first.lowercase() }
            val matches = muscleNames.any { muscle ->
                focusMuscleKeys.any { key -> muscle.contains(key) || key.contains(muscle) }
            }
            // Primarno ujemanje dobi večji bonus
            val primaryMuscle = ex.primaryMuscle.lowercase()
            val primaryMatch = focusMuscleKeys.any { key ->
                primaryMuscle.contains(key) || key.contains(primaryMuscle)
            }
            when {
                primaryMatch -> 8.0   // vaja ima primarno mišico v fokusu → velik bonus
                matches      -> 4.0   // sekundarna/terciarna ujemanje → manjši bonus
                else         -> -3.0  // vaja ni v fokusu → negativen bonus (spusti nižje v seznam)
            }
        }
        return baseScore + diffBonus * 5.0 + focusBonus
    }

    /** Izračun base score-a vaje */
    private fun calculateScore(
        difficulty: Int,
        muscleDetails: List<Pair<String, String>>,
        calPerKgPerHour: Double
    ): Double {
        var muscleScore = 0.0
        for ((_, intensityStr) in muscleDetails) {
            if (intensityStr.isBlank()) continue
            val type = intensityStr.firstOrNull { it.isLetter() }
            val num = intensityStr.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
            val typeWeight = when (type) {
                'P' -> 3.0
                'S' -> 2.0
                'T' -> 1.0
                else -> 0.0
            }
            muscleScore += typeWeight * num
        }
        val calScore = calPerKgPerHour / 10.0
        val diffScore = difficulty / 10.0
        return (0.4 * muscleScore) + (0.4 * calScore) + (0.2 * diffScore)
    }

    private fun loadAllExercisesInternal(context: Context): List<ExerciseInfo> {
        return try {
            val json = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            val list = mutableListOf<ExerciseInfo>()

            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val rawName = obj.optString("name", "Exercise ${i + 1}")
                    val displayName = rawName.replace("_", " ")
                    val description = obj.optString("description", "")
                    val difficulty = obj.optInt("difficulty", 5)
                    val calPerKgPerHour = obj.optDouble("calories_per_kg_per_hour", 3.0)
                    val caloriesPerMinPerKg = calPerKgPerHour / 60.0
                    val primaryMuscle = obj.optString("primary_muscle", "")
                    val videoUrl = obj.optString("video_url", "")
                    val equipment = obj.optString("equipment", "bodyweight").lowercase()

                    // Spol: iz polja "gender" ali iz naziva video URL-ja
                    val gender = when {
                        obj.has("gender") -> obj.optString("gender", "universal").lowercase()
                        videoUrl.contains("female", ignoreCase = true) -> "female"
                        videoUrl.contains("male", ignoreCase = true) && !videoUrl.contains("female", ignoreCase = true) -> "male"
                        else -> "universal"
                    }

                    // Mišice: preberi vsa muscle_intensity_ polja
                    val muscleDetails = mutableListOf<Pair<String, String>>()
                    var primaryIntensity = 0
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key.startsWith("muscle_intensity_")) {
                            val muscleName = key.removePrefix("muscle_intensity_")
                            val value = obj.optString(key, "").trim()
                            if (value.isNotBlank()) {
                                muscleDetails.add(Pair(muscleName, value))
                                if (value.startsWith("P")) {
                                    val num = value.filter { it.isDigit() }.toIntOrNull() ?: 0
                                    if (num > primaryIntensity) primaryIntensity = num
                                }
                            }
                        }
                    }

                    // Parse sets/reps
                    val typical = obj.optString("typical_sets_reps", "3x12")
                    var sets = 3; var reps = 12; var duration: Int? = null
                    if (typical.contains("x")) {
                        val parts = typical.split("x")
                        sets = parts[0].trim().toIntOrNull() ?: 3
                        val rest = parts[1].trim().lowercase()
                        if (rest.contains("s") && !rest.contains("sets")) {
                            duration = rest.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 30
                            reps = 0
                        } else {
                            reps = rest.replace(Regex("[^0-9-]"), "").split("-").lastOrNull()?.toIntOrNull() ?: 12
                        }
                    }

                    val score = calculateScore(difficulty, muscleDetails, calPerKgPerHour)

                    list.add(ExerciseInfo(
                        name = displayName,
                        description = description,
                        caloriesPerMinPerKg = caloriesPerMinPerKg,
                        defaultSets = sets,
                        defaultReps = reps,
                        defaultDuration = duration,
                        score = score,
                        gender = gender,
                        difficulty = difficulty,
                        primaryMuscle = primaryMuscle,
                        primaryMuscleIntensity = primaryIntensity,
                        muscleDetails = muscleDetails,
                        videoUrl = videoUrl,
                        equipment = equipment
                    ))
                } catch (_: Exception) {}
            }
            // Razvrsti po score-u (padajoče)
            list.sortedByDescending { it.score }
        } catch (e: Exception) {
            android.util.Log.e("ExerciseRepository", "Error loading exercises: ${e.message}")
            emptyList()
        }
    }
}

// ─── VideoUrlManager (nespremenjen) ───────────────────────────────────────────

private object VideoUrlManager {
    private var normalizedToOriginalMap: Map<String, String>? = null

    private fun loadMapIfNeeded(context: Context) {
        if (normalizedToOriginalMap != null) return
        try {
            val map = mutableMapOf<String, String>()
            val inputStream = context.assets.open("video_filenames.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val nameWithoutExt = trimmed.removeSuffix(".mp4")
                    val normalized = normalizeName(nameWithoutExt)
                    map[normalized] = trimmed
                }
            }
            normalizedToOriginalMap = map
        } catch (e: Exception) {
            normalizedToOriginalMap = emptyMap()
        }
    }

    private fun normalizeName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()

    fun getVideoUrl(context: Context, exerciseName: String, preloadedUrl: String = ""): String {
        if (preloadedUrl.isNotBlank()) return preloadedUrl
        loadMapIfNeeded(context)
        val baseUrl = "https://storage.googleapis.com/fitness-videos-glowupp/"
        val normalizedInput = normalizeName(exerciseName)
        val map = normalizedToOriginalMap ?: emptyMap()
        var match = map[normalizedInput + "female"]
        if (match != null) return baseUrl + match
        match = map[normalizedInput]
        if (match != null) return baseUrl + match
        match = map.entries.firstOrNull { it.key.startsWith(normalizedInput) }?.value
        if (match != null) return baseUrl + match
        val namePart = exerciseName.trim().replace(" ", "_")
        return "${baseUrl}${namePart}_female.mp4"
    }
}

private fun getVideoUrlForExercise(context: Context, exercise: ExerciseInfo): String =
    VideoUrlManager.getVideoUrl(context, exercise.name, exercise.videoUrl)

// ─── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualExerciseLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedExercise by remember { mutableStateOf<ExerciseInfo?>(null) }
    var gender by remember { mutableStateOf<String?>(GenderCache.getGender(context)) }
    var equipment by remember { mutableStateOf(GenderCache.getEquipment(context)) }
    var userScore by remember { mutableStateOf(GenderCache.getUserScore(context)) }
    var focusAreas by remember { mutableStateOf(GenderCache.getFocusAreas(context)) }
    var detailExercise by remember { mutableStateOf<ExerciseInfo?>(null) }

    // Naloži spol, opremo, userScore in focusAreas iz Firestorea enkrat
    LaunchedEffect(Unit) {
        GenderCache.loadFromFirestoreIfNeeded(context) { g ->
            gender = g
            equipment = GenderCache.getEquipment(context)
            userScore = GenderCache.getUserScore(context)
            focusAreas = GenderCache.getFocusAreas(context)
        }
    }

    val allExercises = remember(gender, equipment, userScore, focusAreas) {
        ExerciseRepository.getFilteredForUser(context, gender, equipment, userScore, focusAreas)
    }
    val filteredExercises = remember(searchQuery, allExercises) {
        if (searchQuery.isBlank()) allExercises
        else allExercises.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (detailExercise != null) detailExercise!!.name else if (selectedExercise != null) "Log: ${selectedExercise!!.name}" else "Log Exercise") },
                navigationIcon = {
                    IconButton(onClick = {
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK)
                        when {
                            detailExercise != null -> detailExercise = null
                            selectedExercise != null -> selectedExercise = null
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        when {
            detailExercise != null -> ExerciseDetailScreen(
                exercise = detailExercise!!,
                onLog = { selectedExercise = detailExercise; detailExercise = null },
                onDismiss = { detailExercise = null },
                modifier = Modifier.padding(padding)
            )
            selectedExercise != null -> ExerciseEntryScreen(
                exercise = selectedExercise!!,
                onDismiss = { selectedExercise = null },
                onSave = { sets, reps, duration ->
                    logExerciseToFirestore(context, selectedExercise!!, sets, reps, duration)
                    selectedExercise = null
                    onBack()
                },
                modifier = Modifier.padding(padding)
            )
            else -> ExerciseListView(
                exercises = filteredExercises,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onExerciseClick = { detailExercise = it },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

// ─── Exercise List View ────────────────────────────────────────────────────────

@Composable
private fun ExerciseListView(
    exercises: List<ExerciseInfo>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onExerciseClick: (ExerciseInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search exercises...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        if (exercises.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No exercises found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(exercises) { exercise ->
                    ExerciseListItem(exercise = exercise, onClick = { onExerciseClick(exercise) })
                }
            }
        }
    }
}

// ─── Exercise List Item (brez avtomatskega videa) ─────────────────────────────

@Composable
private fun ExerciseListItem(exercise: ExerciseInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK)
            onClick()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (exercise.primaryMuscle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = exercise.primaryMuscle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Difficulty chip
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Diff: ${exercise.difficulty}/10", fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

// ─── Exercise Detail Screen ────────────────────────────────────────────────────

@Composable
private fun ExerciseDetailScreen(
    exercise: ExerciseInfo,
    onLog: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoUrl = remember(exercise) { getVideoUrlForExercise(context, exercise) }
    var isPlaying by remember { mutableStateOf(false) }
    var isVideoLoading by remember { mutableStateOf(false) }
    val exoPlayer = remember(videoUrl, isPlaying) {
        if (!isPlaying) null
        else {
            isVideoLoading = true
            ExoPlayer.Builder(context).build().apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == androidx.media3.common.Player.STATE_READY ||
                            state == androidx.media3.common.Player.STATE_ENDED) {
                            isVideoLoading = false
                        }
                    }
                })
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                volume = 0f
            }
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        // Video — skrčen, razširi ob kliku Play
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
            if (isPlaying && exoPlayer != null) {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isVideoLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // Kompakten placeholder - isto kot med workouti
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPlaying = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play video",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("Play exercise video", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Tap to load and play", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(exercise.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        // Težavnost
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Difficulty:", fontWeight = FontWeight.Medium)
            LinearProgressIndicator(
                progress = { exercise.difficulty / 10f },
                modifier = Modifier.weight(1f).height(8.dp),
                color = when {
                    exercise.difficulty <= 3 -> Color(0xFF4CAF50)
                    exercise.difficulty <= 6 -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
            )
            Text("${exercise.difficulty}/10", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        // Opis
        if (exercise.description.isNotBlank()) {
            Text("Exercise description", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(exercise.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }

        // Mišice
        if (exercise.muscleDetails.isNotEmpty()) {
            Text("Muscle impact", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            exercise.muscleDetails.sortedWith(
                compareBy { when { it.second.startsWith("P") -> 0; it.second.startsWith("S") -> 1; else -> 2 } }
            ).forEach { (muscle, intensity) ->
                val typeLabel = when {
                    intensity.startsWith("P") -> "Primary"
                    intensity.startsWith("S") -> "Secondary"
                    else -> "Tertiary"
                }
                val num = intensity.filter { it.isDigit() }.toIntOrNull() ?: 0
                val typeColor = when {
                    intensity.startsWith("P") -> Color(0xFF4CAF50)
                    intensity.startsWith("S") -> Color(0xFFFFC107)
                    else -> Color(0xFF9E9E9E)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(muscle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("$typeLabel ($num/10)", style = MaterialTheme.typography.bodySmall, color = typeColor, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Log gumb
        Button(
            onClick = onLog,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Log this exercise")
        }
    }
}

// ─── Exercise Entry Screen (za vnos setov/repov) ──────────────────────────────

@Composable
private fun ExerciseEntryScreen(
    exercise: ExerciseInfo,
    onDismiss: () -> Unit,
    onSave: (sets: Int, reps: Int, durationSeconds: Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sets by remember { mutableStateOf(exercise.defaultSets.toString()) }
    var reps by remember { mutableStateOf(exercise.defaultReps.toString()) }
    var durationMinutes by remember { mutableStateOf(if (exercise.defaultDuration != null) (exercise.defaultDuration / 60).toString() else "") }
    var durationSeconds by remember { mutableStateOf(if (exercise.defaultDuration != null) (exercise.defaultDuration % 60).toString() else "") }
    var useReps by remember { mutableStateOf(exercise.defaultDuration == null) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text(exercise.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (exercise.description.isNotBlank()) {
            Text(exercise.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = sets,
            onValueChange = { sets = it.filter { c -> c.isDigit() } },
            label = { Text("Sets") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = useReps, onClick = { useReps = true }, label = { Text("Reps") }, modifier = Modifier.weight(1f))
            FilterChip(selected = !useReps, onClick = { useReps = false }, label = { Text("Duration") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        if (useReps) {
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it.filter { c -> c.isDigit() } },
                label = { Text("Reps per set") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Minutes per set") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = durationSeconds,
                    onValueChange = { durationSeconds = it.filter { c -> c.isDigit() } },
                    label = { Text("Seconds per set") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK)
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Cancel") }
            Button(
                onClick = {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.HEAVY_CLICK)
                    val setsInt = sets.toIntOrNull() ?: 3
                    val repsInt = reps.toIntOrNull() ?: 12
                    val durSec = if (useReps) null else {
                        val mins = durationMinutes.toIntOrNull() ?: 0
                        val secs = durationSeconds.toIntOrNull() ?: 0
                        (mins * 60 + secs).takeIf { it > 0 }
                    }
                    onSave(setsInt, repsInt, durSec)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add")
            }
        }
    }
}

// ─── Firestore logging ────────────────────────────────────────────────────────

private fun logExerciseToFirestore(context: Context, exercise: ExerciseInfo, sets: Int, reps: Int, durationSeconds: Int?) {
    val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: return
    Firebase.firestore.collection("users").document(uid).collection("weightLogs")
        .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
        .limit(1)
        .get()
        .addOnSuccessListener { snapshot ->
            val userWeightKg = (snapshot.documents.firstOrNull()?.get("weightKg") as? Number)?.toDouble() ?: 70.0
            val activeMinutes: Double
            val restMinutes: Double
            if (durationSeconds != null) {
                activeMinutes = (sets * durationSeconds) / 60.0
                restMinutes = 0.0
            } else {
                val secondsPerRep = 3.0
                activeMinutes = (sets * reps * secondsPerRep) / 60.0
                restMinutes = ((sets - 1) * 60.0) / 60.0
            }
            val activeRate = exercise.caloriesPerMinPerKg * userWeightKg
            val restRate = 0.0167 * userWeightKg
            val totalCalories = (activeMinutes * activeRate) + (restMinutes * restRate)
            val caloriesRounded = kotlin.math.round(totalCalories).toInt()
            val log = hashMapOf(
                "name" to exercise.name,
                "date" to com.google.firebase.Timestamp.now(),
                "caloriesKcal" to caloriesRounded,
                "sets" to sets,
                "reps" to reps,
                "durationSeconds" to durationSeconds
            )
            Firebase.firestore.collection("users").document(uid)
                .collection("exerciseLogs")
                .add(log)
                .addOnSuccessListener {
                    val xpForExercise = (caloriesRounded / 5).coerceAtLeast(25)
                    val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return@addOnSuccessListener
                    com.example.myapplication.data.UserPreferences.addXPWithCallback(context, userEmail, xpForExercise) { _ ->
                        android.widget.Toast.makeText(context, "+$xpForExercise XP Earned!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
        }
}

// Stara pomožna funkcija za nazaj-kompatibilnost z ostalimi klici
private fun parseCaloriesPerMinutePerKg(raw: String): Double {
    val numberRegex = Regex("[0-9]+(?:\\.[0-9]+)?")
    val match = numberRegex.find(raw)
    val base = match?.value?.toDoubleOrNull() ?: return 0.07
    val lower = raw.lowercase()
    return if (lower.contains("per hour") || lower.contains("/hour") || lower.contains("hour")) {
        base / 60.0
    } else base
}
