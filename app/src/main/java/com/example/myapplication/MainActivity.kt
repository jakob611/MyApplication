@file:Suppress("DEPRECATION") // Suppress deprecation warnings for GoogleSignIn usage until migration to Credential Manager

package com.example.myapplication

import com.example.myapplication.ui.screens.ExerciseHistoryScreen
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log // Optimized: simplified Log usage
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myapplication.data.UserPreferences
import com.example.myapplication.network.OpenFoodFactsProduct
import com.example.myapplication.persistence.PlanDataStore
import com.example.myapplication.ui.home.CommunityScreen
import com.example.myapplication.ui.screens.DeveloperSettingsScreen
import com.example.myapplication.ui.screens.NutritionScreen
import com.example.myapplication.ui.screens.RunTrackerScreen
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.utils.HapticFeedback
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await // Added for delete support
import java.time.LocalDate

// Removed unused colors to clean up resources
val DrawerBlue = Color(0xFF2563EB)

// Screens
sealed class Screen {
    object BodyModuleHome : Screen()

    object Progress : Screen()
    object Nutrition : Screen()
    object Community : Screen()
    object FaceModule : Screen()
    object GoldenRatio : Screen()
    object GeneratePlan : Screen()
    object BodyOverview : Screen()
    object Index : Screen()
    data class Login(val startInSignUp: Boolean = false) : Screen()
    object Dashboard : Screen()
    object Features : Screen()
    object BodyModule : Screen()
    object ProSubscription : Screen()
    object ProFeatures : Screen()
    object MyPlans : Screen()
    object MyAccount : Screen()
    object BarcodeScanner : Screen()
    object EAdditives : Screen() // E-Additives screen
    object PrivacyPolicy : Screen()
    object TermsOfService : Screen()
    object Contact : Screen()
    object About : Screen()
    object HealthConnect : Screen() // Health Connect integration

    // New screens
    object HairModule : Screen()
    object Shop : Screen()
    object WorkoutSession : Screen()
    object Achievements : Screen()
    object LoadingWorkout : Screen() // Loading screen for workout preparation
    object ExerciseHistory : Screen()
    object ManualExerciseLog : Screen() // ADDED
    object RunTracker : Screen()
    object GenerateWorkout : Screen() // NEW
    object DeveloperSettings : Screen() // DEV: Algorithm
    object LevelPath : Screen() // Level progression path
    object BadgesScreen : Screen() // All badges display
    data class PublicProfile(val userId: String) : Screen() // View public profile
}

// Bottom bar
private data class BottomItem(
    val index: Int,
    val label: String,
    val screenRepresentative: Screen,
    val iconRes: Int
)

private val bottomItems = listOf(
    BottomItem(0, "Home", Screen.Dashboard, R.drawable.ic_home),
    BottomItem(1, "Progress", Screen.Progress, R.drawable.ic_progress),
    BottomItem(2, "Nutrition", Screen.Nutrition, R.drawable.ic_nutrition),
    BottomItem(3, "Community", Screen.Community, R.drawable.ic_community),
)

private fun screenToIndex(screen: Screen): Int {
    val index = when (screen) {
        // Treat BodyOverview like a Home-related screen so bottom bar highlights Home
        Screen.Dashboard, Screen.BodyModule, Screen.BodyModuleHome, Screen.BodyOverview, Screen.FaceModule, Screen.HairModule, Screen.Shop, Screen.ExerciseHistory, Screen.RunTracker -> 0
        Screen.Progress, Screen.MyPlans -> 1
        Screen.Nutrition -> 2
        Screen.Community, Screen.MyAccount -> 3
        else -> 0
    }
    // Optimized: Removed redundant android.util.
    Log.d("BottomBar", "screenToIndex: screen=$screen, index=$index")
    return index
}

private fun onSelectIndex(index: Int): Screen = when (index) {
    0 -> Screen.Dashboard
    1 -> Screen.Progress
    2 -> Screen.Nutrition
    3 -> Screen.Community
    else -> Screen.Dashboard
}

@Composable
private fun AppBottomBar(
    currentScreen: Screen,
    onSelect: (Screen) -> Unit
) {
    val context = LocalContext.current
    val currentIndex = screenToIndex(currentScreen)
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        bottomItems.forEach { item ->
            val selected = currentIndex == item.index
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        HapticFeedback.performHapticFeedback(
                            context,
                            HapticFeedback.FeedbackType.LIGHT_CLICK
                        )
                        onSelect(onSelectIndex(item.index))
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.label,
                        tint = Color.Unspecified
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}


class MainActivity : ComponentActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient

    // Store intent extras to trigger effects
    private val intentExtras = mutableStateOf<Bundle?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: update activity's intent
        intentExtras.value = intent.extras // Trigger recomposition
    }

    override fun onPause() {
        super.onPause()
        // Razporedi zanesljiv sync z WorkManager (čaka na omrežje, retry ob napaki)
        try {
            com.example.myapplication.worker.DailySyncWorker.schedule(this)
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION") // Suppress deprecation for GoogleSignInOptions
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MapLibre initialization removed (disabled for now)

        // Set initial intent extras
        intentExtras.value = intent.extras

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            val context = LocalContext.current
            val bodyOverviewViewModel: BodyOverviewViewmodel =
                viewModel(factory = MyViewModelFactory())
            val plans by bodyOverviewViewModel.plans.collectAsState()

            var isCheckingAuth by remember { mutableStateOf(true) } // Novo: loading state

            // Navigation stack system - tracks screen history
            val navigationStack = remember { mutableStateListOf<Screen>(Screen.Index) }
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Index) }
            var previousScreen by remember { mutableStateOf<Screen>(Screen.Index) }
            var isLoggedIn by remember { mutableStateOf(false) }
            var userEmail by remember { mutableStateOf("user@email.com") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var selectedPlan by remember { mutableStateOf<PlanResult?>(null) }
            var isExtraWorkoutSession by remember { mutableStateOf(false) }
            var extraFocusAreas by remember { mutableStateOf<List<String>>(emptyList()) }
            var extraEquipment by remember { mutableStateOf<Set<String>>(emptySet()) }
            var isDarkMode by remember { mutableStateOf(false) } // Default FALSE (light mode)
            var userProfile by remember { mutableStateOf(com.example.myapplication.data.UserProfile(email = userEmail)) }
            var scannedProduct by remember { mutableStateOf<Pair<OpenFoodFactsProduct, String>?>(null) } // Product + barcode
            var unlockedBadge by remember { mutableStateOf<com.example.myapplication.data.Badge?>(null) } // Badge unlock animation

            // Pending deep-links from widgets if they arrive before auth completes
            var pendingNavigateToNutrition by remember { mutableStateOf(false) }
            var pendingOpenBarcodeScan by remember { mutableStateOf(false) }
            var pendingOpenFoodSearch by remember { mutableStateOf(false) }

            // Removed unused variables xpPopupAmount, showXpPopup to clean up code
            val nutritionSnackbarHostState = remember { SnackbarHostState() }

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            // Removed unused lifecycleOwner

            val googleSignInLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(Exception::class.java)
                    firebaseAuthWithGoogle(
                        account,
                        onSuccess = {
                            isLoggedIn = true
                            userEmail = account?.email ?: ""
                            userProfile = UserPreferences.loadProfile(context, userEmail)
                            scope.launch {
                                isDarkMode = UserPreferences.isDarkMode(userEmail)
                            }
                            navigationStack.clear() // Clear navigation stack on login
                            currentScreen = Screen.Dashboard
                        },
                        onError = { errorMessage = it }
                    )
                } catch (e: Exception) {
                    errorMessage = "Google sign-in failed: ${e.localizedMessage}"
                }
            }

            LaunchedEffect(Unit) {
                val user = Firebase.auth.currentUser
                if (user != null && (user.isEmailVerified || user.providerData.any { it.providerId == "google.com" })) {
                    isLoggedIn = true
                    userEmail = user.email ?: ""

                    // Minimal/faster: load local profile immediately to avoid blocking first frames.
                    userProfile = UserPreferences.loadProfile(context, userEmail)
                    isDarkMode = UserPreferences.isDarkMode(userEmail)
                    if (pendingNavigateToNutrition) {
                        currentScreen = Screen.Nutrition
                    } else {
                        currentScreen = Screen.Dashboard
                    }

                    // Update login streak and award daily login XP
                    scope.launch(Dispatchers.IO) {
                        try {
                            com.example.myapplication.persistence.AchievementStore.updateLoginStreak(context, userEmail)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error updating login streak: ${e.message}")
                        }
                    }

                    // Načrtuj tedenski streak Worker (enkrat tedensko ob polnoči)
                    scope.launch(Dispatchers.IO) {
                        try {
                            val profile = UserPreferences.loadProfile(context, userEmail)
                            com.example.myapplication.workers.WeeklyStreakWorker.ensureScheduled(
                                context, profile.startOfWeek
                            )
                        } catch (e: Exception) {
                            Log.e("MainActivity", "WeeklyStreakWorker schedule error: ${e.message}")
                        }
                    }

                    // Let Compose render at least one frame before heavier work.
                    delay(250)
                    scope.launch(Dispatchers.IO) {
                        try {
                            val remote = UserPreferences.loadProfileFromFirestore(userEmail)
                            if (remote != null) {
                                UserPreferences.saveProfile(context, remote)
                                val actParsed = remote.activityLevel?.replace("x", "")?.toIntOrNull()
                                if (actParsed != null && actParsed > 0) {
                                    context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                        .edit().putInt("weekly_target", actParsed).apply()
                                }
                                withContext(Dispatchers.Main) {
                                    userProfile = remote
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }

                    // STARTUP BADGE SYNC — crash recovery safety net
                    // Runs after a short delay so UI is responsive first
                    scope.launch(Dispatchers.IO) {
                        delay(1500) // Wait for Firestore profile to load
                        try {
                            com.example.myapplication.persistence.AchievementStore
                                .checkAndSyncBadgesOnStartup(context, userEmail)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Startup badge sync error: ${e.message}")
                        }
                    }

                    // Plans migration + refresh can be expensive (disk + network). Do it after UI is visible.
                    scope.launch {
                        try {
                            PlanDataStore.migrateLocalPlansToFirestore(context)
                        } catch (_: Exception) {
                        }
                        bodyOverviewViewModel.refreshPlans()
                        // Posodobi streak widget ob odprtju aplikacije
                        try {
                            com.example.myapplication.widget.StreakWidgetProvider.refreshAll(context)
                        } catch (_: Exception) {}
                        // Ob odprtju aplikacije sinciraj VČERAJŠNJE podatke (če niso bili syncani)
                        val syncUid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                        if (syncUid != null) {
                            try {
                                com.example.myapplication.persistence.DailySyncManager.syncOnAppOpen(context, syncUid)
                            } catch (_: Exception) {}
                        }
                    }

                    // DODAJ: Real-time listener za userProfile iz Firestore-a
                    scope.launch {
                        val userRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                        userRef.addSnapshotListener { snap, _ ->
                            if (snap?.exists() == true) {
                                try {
                                    val username = snap.getString("username") ?: ""
                                    val firstName = snap.getString("first_name") ?: ""
                                    val lastName = snap.getString("last_name") ?: ""
                                    val address = snap.getString("address") ?: ""
                                    val xp = (snap.get("xp") as? Number)?.toInt() ?: 0
                                    val followers = (snap.get("followers") as? Number)?.toInt() ?: 0
                                    val badges = when (val b = snap.get("badges")) {
                                        is List<*> -> b.filterIsInstance<String>()
                                        is String -> b.split(',').filter { it.isNotBlank() }
                                        else -> emptyList()
                                    }
                                    val weightUnit = snap.getString("weight_unit") ?: "kg"

                                    val speedUnit = snap.getString("speed_unit") ?: "km/h"
                                    val startOfWeek = snap.getString("start_of_week") ?: "Monday"

                                    // Privacy settings (KRITIČNO - manjkajoče!)
                                    val isPublic = snap.getBoolean("is_public_profile") ?: false
                                    val showLevel = snap.getBoolean("show_level") ?: false
                                    val showBadges = snap.getBoolean("show_badges") ?: false
                                    val showPlanPath = snap.getBoolean("show_plan_path") ?: false
                                    val showChallenges = snap.getBoolean("show_challenges") ?: false
                                    val showFollowers = snap.getBoolean("show_followers") ?: false

                                    // Profile picture URL (KRITIČNO - manjkajoče!)
                                    val profilePictureUrl = snap.getString("profile_picture_url")

                                    // 🔥 KRITIČNO: PREBERI biometrijske podatke (height, age, gender) iz Firestore!
                                    val height = (snap.get("height") as? Number)?.toDouble()
                                    val age = (snap.get("age") as? Number)?.toInt()
                                    val gender = snap.getString("gender")
                                    val activityLevel = snap.getString("activityLevel")
                                    val experience = snap.getString("experience")
                                    val bodyFat = snap.getString("bodyFat")
                                    val workoutGoal = snap.getString("workoutGoal") ?: ""
                                    val limitations = when (val lim = snap.get("limitations")) {
                                        is List<*> -> lim.filterIsInstance<String>()
                                        is String -> lim.split(',').filter { it.isNotBlank() }
                                        else -> emptyList()
                                    }
                                    val nutritionStyle = snap.getString("nutritionStyle")
                                    val sleepHours = snap.getString("sleepHours")
                                    val equipment = when (val eq = snap.get("equipment")) {
                                        is List<*> -> eq.filterIsInstance<String>()
                                        else -> emptyList()
                                    }
                                    val focusAreas = when (val fa = snap.get("focusAreas")) {
                                        is List<*> -> fa.filterIsInstance<String>()
                                        else -> emptyList()
                                    }

                                    val updatedProfile = com.example.myapplication.data.UserProfile(
                                        username = username,
                                        email = userEmail,
                                        firstName = firstName,
                                        lastName = lastName,
                                        address = address,
                                        xp = xp,
                                        followers = followers,
                                        badges = badges,
                                        weightUnit = weightUnit,
                                        speedUnit = speedUnit,
                                        startOfWeek = startOfWeek,
                                        // Privacy settings
                                        isPublicProfile = isPublic,
                                        showLevel = showLevel,
                                        showBadges = showBadges,
                                        showPlanPath = showPlanPath,
                                        showChallenges = showChallenges,
                                        showFollowers = showFollowers,
                                        // Profile picture
                                        profilePictureUrl = profilePictureUrl,
                                        // 🔥 BIOMETRIJSKI PODATKI - KRITIČNO ZA NUTRITION RECALCULATION!
                                        height = height,
                                        age = age,
                                        gender = gender,
                                        activityLevel = activityLevel,
                                        experience = experience,
                                        bodyFat = bodyFat,
                                        workoutGoal = workoutGoal,
                                        limitations = limitations,
                                        nutritionStyle = nutritionStyle,
                                        sleepHours = sleepHours,
                                        equipment = equipment,
                                        focusAreas = focusAreas
                                    )
                                    userProfile = updatedProfile
                                    // Sync activityLevel → bm_prefs weekly_target
                                    val actParsed = activityLevel?.replace("x", "")?.toIntOrNull()
                                    if (actParsed != null && actParsed > 0) {
                                        context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                            .edit().putInt("weekly_target", actParsed).apply()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                } else {
                    // Uporabnik ni prijavljen - prikaži Index screen
                    currentScreen = Screen.Index
                }
                // Ko je preverjanje končano, skrij loading screen
                isCheckingAuth = false
            }

            // Handle weight widget manual input intent
            var showWeightInputDialog by remember { mutableStateOf(false) }
            var openBarcodeScan by remember { mutableStateOf(false) }
            var openFoodSearch by remember { mutableStateOf(false) }

            // React to intent changes (including onNewIntent)
            LaunchedEffect(intentExtras.value) {
                val extras = intentExtras.value ?: return@LaunchedEffect

                Log.d("MainActivity", "Processing intent extras: NAVIGATE_TO=${extras.getString("NAVIGATE_TO")}, SCAN=${extras.getBoolean("OPEN_BARCODE_SCAN")}, SEARCH=${extras.getBoolean("OPEN_FOOD_SEARCH")}")


                // Weight input from widget
                if (extras.getBoolean("OPEN_WEIGHT_INPUT", false)) {
                    showWeightInputDialog = true
                    if (isLoggedIn) {
                        currentScreen = Screen.Progress
                    }
                }

                // Navigate to Nutrition from Quick Meal widget
                if (extras.getString("NAVIGATE_TO") == "nutrition") {
                    val wantsScan = extras.getBoolean("OPEN_BARCODE_SCAN", false)
                    val wantsSearch = extras.getBoolean("OPEN_FOOD_SEARCH", false)

                    if (isLoggedIn && !isCheckingAuth) {
                        Log.d("MainActivity", "Navigating to Nutrition screen (immediate)")
                        currentScreen = Screen.Nutrition
                        // Wait for NutritionScreen to compose
                        delay(150)
                        if (wantsScan) {
                            Log.d("MainActivity", "Setting openBarcodeScan = true")
                            openBarcodeScan = true
                        }
                        if (wantsSearch) {
                            Log.d("MainActivity", "Setting openFoodSearch = true")
                            openFoodSearch = true
                        }
                    } else {
                        // Defer navigation until auth finishes
                        Log.d("MainActivity", "Deferring navigation to Nutrition until after auth")
                        pendingNavigateToNutrition = true
                        pendingOpenBarcodeScan = wantsScan
                        pendingOpenFoodSearch = wantsSearch
                    }
                }
            }

            // If we had pending actions, convert them into state flags once UI is ready
            LaunchedEffect(isCheckingAuth, isLoggedIn, pendingNavigateToNutrition, pendingOpenBarcodeScan, pendingOpenFoodSearch) {
                if (!isCheckingAuth && isLoggedIn && pendingNavigateToNutrition) {
                    currentScreen = Screen.Nutrition
                    // Allow composition
                    delay(150)
                    if (pendingOpenBarcodeScan) {
                        openBarcodeScan = true
                    }
                    if (pendingOpenFoodSearch) {
                        openFoodSearch = true
                    }
                    // Clear pending
                    pendingNavigateToNutrition = false
                    pendingOpenBarcodeScan = false
                    pendingOpenFoodSearch = false
                }
            }


            // Navigation helper functions
            fun navigateTo(screen: Screen) {
                // Don't add duplicates if navigating to the same screen
                if (currentScreen != screen) {
                    navigationStack.add(currentScreen)
                    previousScreen = currentScreen
                    currentScreen = screen
                }
            }

            fun navigateBack() {
                when {
                    // Special case: if a plan is selected, clear it first
                    selectedPlan != null -> {
                        selectedPlan = null
                    }
                    // Screens opened from drawer - go back to previous screen AND open drawer
                    currentScreen is Screen.LevelPath || currentScreen is Screen.BadgesScreen || currentScreen is Screen.MyAccount || currentScreen is Screen.Achievements -> {
                        val previous = navigationStack.removeLastOrNull()
                        if (previous != null) {
                            previousScreen = currentScreen
                            currentScreen = previous
                        } else {
                            if (isLoggedIn) currentScreen = Screen.Dashboard else currentScreen = Screen.Index
                        }
                        // Open the drawer
                        scope.launch { drawerState.open() }
                    }
                    // Special navigation overrides (screens that should go to specific places)
                    currentScreen is Screen.ProFeatures -> {
                        errorMessage = null
                        currentScreen = previousScreen
                    }
                    currentScreen is Screen.ProSubscription -> {
                        errorMessage = null
                        currentScreen = Screen.ProFeatures
                    }
                    currentScreen is Screen.Login -> {
                        errorMessage = null
                        currentScreen = Screen.Index
                    }
                    // If we have navigation history, use it
                    navigationStack.isNotEmpty() -> {
                        val previous = navigationStack.removeLastOrNull()
                        if (previous != null) {
                            previousScreen = currentScreen
                            currentScreen = previous
                        } else {
                            // Fallback if somehow empty
                            if (isLoggedIn) currentScreen = Screen.Dashboard else currentScreen = Screen.Index
                        }
                    }
                    // Special case: Dashboard can exit app if logged in
                    currentScreen is Screen.Dashboard -> {
                        if (!isLoggedIn) {
                            currentScreen = Screen.Index
                        } else {
                            finish()
                        }
                    }
                    // Default fallback
                    isLoggedIn -> currentScreen = Screen.Dashboard
                    else -> currentScreen = Screen.Index
                }
            }

            BackHandler {
                navigateBack()
            }

            // Samo Index screen je vedno dark mode; ostalo uporablja uporabnikovo nastavitev
            val shouldUseDarkMode = if (!isLoggedIn && currentScreen is Screen.Index) {
                true // Index screen vedno dark
            } else {
                isDarkMode // Uporabnikova nastavitev
            }

            MyApplicationTheme(darkTheme = shouldUseDarkMode) {
                // Prikaži splash/loading screen dokler se preverja authentication
                if (isCheckingAuth) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = DrawerBlue)
                    }
                } else if (isLoggedIn) {
                    // OneUI + ModalNavigationDrawer swipe gestures can fight with vertical scrolling
                    // on several screens and cause heavy jank / FPS drops. Keep drawer accessible
                    // via the menu button, but disable swipe gestures on those screens.
                    val enableDrawerGestures = when (currentScreen) {
                        // Enable swipe gestures again (user requested old behavior).
                        // If we still see jank later, we can selectively disable only on problematic screens.
                        is Screen.Dashboard,
                        is Screen.Progress,
                        is Screen.BodyModuleHome,
                        is Screen.BodyOverview,
                        is Screen.MyPlans,
                        is Screen.FaceModule,
                        is Screen.GoldenRatio,
                        is Screen.HairModule,
                        is Screen.Shop,
                        is Screen.Community,
                        is Screen.MyAccount,
                        is Screen.ExerciseHistory,
                        is Screen.ManualExerciseLog,
                        is Screen.Nutrition -> true
                        else -> currentScreen !is Screen.RunTracker
                    }
                     ModalNavigationDrawer(
                         drawerState = drawerState,
                         gesturesEnabled = enableDrawerGestures,
                         drawerContent = {
                             FigmaDrawerContent(
                                 userProfile = userProfile,
                                 onClose = { scope.launch { drawerState.close() } },
                                 onLogout = {
                                     bodyOverviewViewModel.clearPlans()
                                     com.example.myapplication.persistence.FirestoreHelper.clearCache()
                                     Firebase.auth.signOut()
                                     isLoggedIn = false
                                     userEmail = ""
                                     userProfile = com.example.myapplication.data.UserProfile(email = "")
                                     navigationStack.clear() // Clear navigation stack on logout
                                     currentScreen = Screen.Index
                                     scope.launch { drawerState.close() }
                                 },
                                 onProfileUpdate = { updatedProfile ->
                                     userProfile = updatedProfile
                                     UserPreferences.saveProfile(context, updatedProfile)
                                     // Also persist to Firestore
                                     scope.launch { UserPreferences.saveProfileFirestore(updatedProfile) }
                                 },
                                 isDarkMode = isDarkMode,
                                 onDarkModeToggle = {
                                     isDarkMode = !isDarkMode
                                     scope.launch {
                                         UserPreferences.setDarkMode(userEmail, isDarkMode)
                                     }
                                 },
                                 onNavigateToPrivacyPolicy = {
                                     navigateTo(Screen.PrivacyPolicy)
                                     scope.launch { drawerState.close() }
                                 },
                                 onNavigateToTermsOfService = {
                                     navigateTo(Screen.TermsOfService)
                                     scope.launch { drawerState.close() }
                                 },
                                 onNavigateToContact = {
                                     navigateTo(Screen.Contact)
                                     scope.launch { drawerState.close() }
                                 },
                                 onNavigateToAbout = {
                                     navigateTo(Screen.About)
                                     scope.launch { drawerState.close() }
                                 },
                                 onNavigateToLevelPath = {
                                     navigateTo(Screen.Achievements)
                                     scope.launch { drawerState.close() }
                                 },
                                 onNavigateToBadges = {
                                     navigateTo(Screen.Achievements)
                                     scope.launch { drawerState.close() }
                                 },
                                 onNavigateToHealthConnect = {
                                     navigateTo(Screen.HealthConnect)
                                     scope.launch { drawerState.close() }
                                 },
                                 onNavigateToMyAccount = {
                                     navigateTo(Screen.MyAccount)
                                     scope.launch { drawerState.close() }
                                 }
                             )
                         }
                     ) {
                        // Optimized: use derivedStateOf to avoid recomputation on every recompose
                        val showBottomBar by remember {
                            derivedStateOf {
                                isLoggedIn &&
                                        selectedPlan == null &&
                                        when (currentScreen) {
                                            is Screen.Dashboard,
                                            is Screen.Progress,
                                            is Screen.BodyOverview,
                                            is Screen.MyPlans,
                                            is Screen.BodyModuleHome,
                                            is Screen.Nutrition,
                                            is Screen.Community,
                                            is Screen.MyAccount,
                                            is Screen.FaceModule,
                                            is Screen.GoldenRatio,
                                            is Screen.HairModule,
                                            is Screen.Shop -> true
                                            is Screen.BarcodeScanner -> false
                                            is Screen.EAdditives -> false
                                            is Screen.WorkoutSession -> false
                                            is Screen.LoadingWorkout -> false
                                            is Screen.RunTracker -> false
                                            else -> false
                                        }
                            }
                        }

                        Scaffold(
                            // KLJUČNO: globalni top bar je viden samo na glavnih home pagih
                            topBar = {
                                // Prikaži topBar samo na Dashboard, Progress, Nutrition in Community
                                if (currentScreen is Screen.Dashboard ||
                                    currentScreen is Screen.Progress ||
                                    currentScreen is Screen.Nutrition ||
                                    currentScreen is Screen.Community) {
                                    GlobalHeaderBar(
                                        onOpenMenu = {
                                            HapticFeedback.performHapticFeedback(
                                                context,
                                                HapticFeedback.FeedbackType.DRAWER_OPEN
                                            )
                                            scope.launch { drawerState.open() }
                                        },
                                        onProClick = {
                                            errorMessage = null
                                            previousScreen = currentScreen
                                            currentScreen = Screen.ProFeatures
                                        }
                                    )
                                }
                            },
                            snackbarHost = {
                                SnackbarHost(
                                    hostState = nutritionSnackbarHostState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) { data ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        shape = RoundedCornerShape(20.dp),
                                        tonalElevation = 6.dp,
                                        shadowElevation = 8.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 18.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = data.visuals.message,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            },
                            bottomBar = {
                                if (showBottomBar) {
                                    AppBottomBar(
                                        currentScreen = currentScreen,
                                        onSelect = { target ->
                                            selectedPlan = null
                                            navigateTo(target)
                                        }
                                    )
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.background
                        ) { innerPadding ->
                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                when {
                                    currentScreen is Screen.LoadingWorkout -> LoadingWorkoutScreen(
                                        onLoadingComplete = {
                                            // Async check: is today's workout already done?
                                            scope.launch {
                                                try {
                                                    val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                                                    if (uid != null) {
                                                        val today = LocalDate.now().toString()
                                                        val doc = Firebase.firestore
                                                            .collection("users").document(uid)
                                                            .collection("workouts").document(today)
                                                            .get().await()
                                                        if (doc.exists()) {
                                                            // Workout already done today
                                                            navigateTo(Screen.GenerateWorkout)
                                                        } else {
                                                            isExtraWorkoutSession = false
                                                            navigateTo(Screen.WorkoutSession)
                                                        }
                                                    } else {
                                                        isExtraWorkoutSession = false
                                                        navigateTo(Screen.WorkoutSession)
                                                    }
                                                } catch (_: Exception) {
                                                    isExtraWorkoutSession = false
                                                    navigateTo(Screen.WorkoutSession)
                                                }
                                            }
                                        }
                                    )
                                    currentScreen is Screen.BodyModuleHome -> BodyModuleHomeScreen(
                                        onBack = { navigateBack() },
                                        onStartPlan = { navigateTo(Screen.BodyOverview) },
                                        onStartWorkout = { plan ->
                                            // Check if plan exists first
                                            if (plan == null) {
                                                // No plan - redirect to create plan screen
                                                navigateTo(Screen.BodyOverview)
                                                return@BodyModuleHomeScreen
                                            }

                                            // Takoj pojdi na loading screen (prepreči blisk BodyModuleHome)
                                            selectedPlan = plan
                                            navigateTo(Screen.LoadingWorkout)
                                        },
                                        onStartAdditionalWorkout = {
                                            // Go directly to GenerateWorkout screen
                                            navigateTo(Screen.GenerateWorkout)
                                        },
                                        currentPlan = plans.maxByOrNull { it.createdAt },
                                        onOpenHistory = { navigateTo(Screen.ExerciseHistory) },
                                        onOpenManualLog = { navigateTo(Screen.ManualExerciseLog) },
                                        onStartRun = { navigateTo(Screen.RunTracker) }
                                    )
                                    currentScreen is Screen.WorkoutSession -> {
                                        val frozenPlan = remember(currentScreen) {
                                            selectedPlan ?: plans.maxByOrNull { it.createdAt }
                                        }
                                        WorkoutSessionScreen(
                                            currentPlan = frozenPlan,
                                            isExtra = isExtraWorkoutSession,
                                            extraFocusAreas = extraFocusAreas,
                                            extraEquipment = extraEquipment,
                                            onBack = {
                                                selectedPlan = null
                                                isExtraWorkoutSession = false
                                                extraFocusAreas = emptyList()
                                                extraEquipment = emptySet()
                                                currentScreen = Screen.BodyModuleHome
                                            },
                                            onFinished = {
                                                selectedPlan = null
                                                isExtraWorkoutSession = false
                                                extraFocusAreas = emptyList()
                                                extraEquipment = emptySet()
                                                currentScreen = Screen.BodyModuleHome
                                            },
                                            onXPAdded = {
                                                userProfile = UserPreferences.loadProfile(context, userEmail)
                                            },
                                            onBadgeUnlocked = { badge ->
                                                unlockedBadge = badge
                                                userProfile = UserPreferences.loadProfile(context, userEmail)
                                            }
                                        )
                                    }
                                    currentScreen is Screen.FaceModule -> FaceModuleScreen(
                                        onBack = { navigateBack() },
                                        onGoldenRatioClick = { navigateTo(Screen.GoldenRatio) }
                                    )
                                    currentScreen is Screen.GoldenRatio -> GoldenRatioScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.HairModule -> HairModuleScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.Shop -> ShopScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.Progress -> ProgressScreen(
                                        openWeightInput = showWeightInputDialog,
                                        userProfile = userProfile
                                    )
                                    currentScreen is Screen.Nutrition -> {
                                        val currentPlan = plans.maxByOrNull { it.createdAt }
                                        NutritionScreen(
                                            plan = currentPlan,
                                            onScanBarcode = {
                                                openBarcodeScan = false // Reset flag
                                                navigateTo(Screen.BarcodeScanner)
                                            },
                                            onOpenEAdditives = { navigateTo(Screen.EAdditives) },
                                            scannedProduct = scannedProduct,
                                            onProductConsumed = { scannedProduct = null },
                                            openBarcodeScan = openBarcodeScan,
                                            openFoodSearch = openFoodSearch,
                                            onXPAdded = {
                                                // Osveži userProfile iz SharedPreferences
                                                userProfile = UserPreferences.loadProfile(context, userEmail)
                                            },
                                            snackbarHostState = nutritionSnackbarHostState,
                                            userProfile = userProfile // Pass userProfile
                                        )
                                    }
                                    currentScreen is Screen.BarcodeScanner -> {
                                        BarcodeScannerScreen(
                                            onDismiss = { navigateBack() },
                                            onProductScanned = { product, barcode ->
                                                scannedProduct = Pair(product, barcode)
                                                navigateBack()
                                            }
                                        )
                                    }
                                    currentScreen is Screen.EAdditives -> {
                                        EAdditivesScreen(
                                            onNavigateBack = { navigateBack() }
                                        )
                                    }
                                    currentScreen is Screen.ExerciseHistory -> ExerciseHistoryScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.ManualExerciseLog -> ManualExerciseLogScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.RunTracker -> RunTrackerScreen(
                                        onBackPressed = { navigateBack() },
                                        userProfile = userProfile // Pass userProfile
                                    )
                                    currentScreen is Screen.GenerateWorkout -> GenerateWorkoutScreen(
                                        onBack = { navigateBack() },
                                        currentPlan = plans.maxByOrNull { it.createdAt },
                                        onSelectWorkout = { generatedWorkout ->
                                            val latestPlan = plans.maxByOrNull { it.createdAt }
                                            if (latestPlan != null) {
                                                isExtraWorkoutSession = true
                                                // Shrani fokus in opremo KI JU JE IZBRAL UPORABNIK
                                                extraFocusAreas = generatedWorkout.focus
                                                    .split(",").map { it.trim() }.filter { it.isNotBlank() }
                                                extraEquipment = generatedWorkout.equipment
                                                    .ifEmpty { setOf("bodyweight") }
                                                // selectedPlan ostane NESPREMENJEN — brez .copy()!
                                                selectedPlan = latestPlan
                                                navigateTo(Screen.WorkoutSession)
                                            } else {
                                                navigateTo(Screen.BodyOverview)
                                            }
                                        }
                                    )
                                    currentScreen is Screen.Community -> CommunityScreen(
                                        onViewProfile = { userId ->
                                            navigateTo(Screen.PublicProfile(userId))
                                        }
                                    )
                                    currentScreen is Screen.BodyOverview -> BodyOverviewScreen(
                                        plans = plans,
                                        onCreateNewPlan = { navigateTo(Screen.BodyModule) },
                                        onBack = { navigateBack() }
                                    )

                                    currentScreen is Screen.Dashboard -> DashboardScreen(
                                        userEmail = userEmail,
                                        onLogout = {
                                            Firebase.auth.signOut()
                                            isLoggedIn = false
                                            userEmail = ""
                                            currentScreen = Screen.Index
                                        },
                                        onModuleClick = { moduleTitle ->
                                            when (moduleTitle) {
                                                "Body" -> navigateTo(Screen.BodyModuleHome)
                                                "Face" -> navigateTo(Screen.FaceModule)
                                                "Hair" -> navigateTo(Screen.HairModule)
                                                "Shop" -> navigateTo(Screen.Shop)
                                            }
                                        },
                                        onAccountClick = { navigateTo(Screen.MyAccount) },
                                        onProClick = {
                                            errorMessage = null
                                            previousScreen = Screen.Dashboard
                                            navigateTo(Screen.ProFeatures)
                                        },
                                        onOpenMenu = {
                                            HapticFeedback.performHapticFeedback(
                                                context,
                                                HapticFeedback.FeedbackType.DRAWER_OPEN
                                            )
                                            scope.launch { drawerState.open() }
                                        },
                                        showLocalHeader = false // skrij lokalni header, ker je globalni top bar povsod
                                    )
                                    currentScreen is Screen.ProFeatures -> ProFeaturesScreen(
                                        onFreeTrial = { /* akcija ob kliku na free trial gumb */ },
                                        onContinue = {
                                            if (isLoggedIn) {
                                                errorMessage = null
                                                navigateTo(Screen.ProSubscription)
                                            }
                                        },
                                        onBack = {
                                            errorMessage = null
                                            navigateBack()
                                        },
                                        errorMessage = errorMessage
                                    )
                                    currentScreen is Screen.Features -> FeaturesScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.BodyModule -> {
                                        // Uporabimo key da prisilimo recomposition ko se userProfile spremeni,
                                        // ampak to bi resetiralo screen state.
                                        // Namesto tega moramo zagotoviti da onFinish uporablja TRENUTNI userProfile.
                                        // Ker je userProfile state variabla, dostop znotraj launch bi moral biti OK,
                                        // ampak za vsak slučaj preverimo logiko.

                                        BodyPlanQuizScreen(
                                        onBack = { navigateBack() },
                                        onQuizDataCollected = { quizData ->
                                            @Suppress("UNCHECKED_CAST")
                                            val limitationsList = quizData["limitations"] as? List<String> ?: emptyList()

                                            Log.d("MainActivity", "🔥 onQuizDataCollected CALLED! height=${quizData["height"]}, age=${quizData["age"]}, gender=${quizData["gender"]}")

                                            val newHeight = (quizData["height"] as? Number)?.toDouble()
                                                ?: (quizData["height"] as? String)?.toDoubleOrNull()
                                            val newAge = (quizData["age"] as? Number)?.toInt()
                                                ?: (quizData["age"] as? String)?.toIntOrNull()
                                            val newGender = quizData["gender"] as? String

                                            userProfile = userProfile.copy(
                                                height = newHeight,
                                                age = newAge,
                                                gender = newGender,
                                                activityLevel = quizData["frequency"] as? String,
                                                experience = quizData["experience"] as? String,
                                                bodyFat = quizData["bodyFat"] as? String,
                                                workoutGoal = quizData["goal"] as? String ?: "",
                                                limitations = limitationsList,
                                                nutritionStyle = quizData["nutrition"] as? String,
                                                sleepHours = quizData["sleep"] as? String
                                            )

                                            // Shrani lokalno
                                            UserPreferences.saveProfile(context, userProfile)

                                            // 🔥 KRITIČNO: Takoj shrani biometrijske podatke v Firestore!
                                            // onFinish se pokliče iz iste coroutine, compose state se morda ni posodobil,
                                            // zato moramo biometrijo shraniti TUKAJ neposredno.
                                            scope.launch {
                                                try {
                                                    val bioRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                                                    val bioData = mapOf(
                                                        "height" to (newHeight ?: 0.0),
                                                        "age" to (newAge ?: 0),
                                                        "gender" to (newGender ?: ""),
                                                        "activityLevel" to (quizData["frequency"] as? String ?: ""),
                                                        "experience" to (quizData["experience"] as? String ?: ""),
                                                        "bodyFat" to (quizData["bodyFat"] as? String ?: ""),
                                                        "workoutGoal" to (quizData["goal"] as? String ?: ""),
                                                        "limitations" to limitationsList,
                                                        "nutritionStyle" to (quizData["nutrition"] as? String ?: ""),
                                                        "sleepHours" to (quizData["sleep"] as? String ?: "")
                                                    )
                                                    bioRef.set(bioData, com.google.firebase.firestore.SetOptions.merge()).await()
                                                    Log.d("MainActivity", "✅ Biometric data saved directly to Firestore: height=$newHeight, age=$newAge, gender=$newGender")
                                                } catch (e: Exception) {
                                                    Log.e("MainActivity", "❌ Failed to save biometric data to Firestore", e)
                                                }
                                            }

                                            Log.d("MainActivity", "🔥 userProfile UPDATED! height=${userProfile.height}, age=${userProfile.age}, gender=${userProfile.gender}")
                                        },

                                        onFinish = { plan ->
                                            scope.launch {
                                                // 1) Pobriši vse obstoječe plane (maksimalno 1 naj ostane)
                                                if (plans.isNotEmpty()) {
                                                    plans.forEach { old ->
                                                        PlanDataStore.deletePlan(context, old.id)
                                                    }
                                                }
                                                // 2) Dodaj nov plan
                                                PlanDataStore.addPlan(context, plan)

                                                // 3) KRITIČNO: Združi VSE podatke iz userProfile (ki ima že biometrijo) + equipment + focusAreas
                                                var currentProfile = userProfile
                                                if (currentProfile.height == null || currentProfile.height == 0.0) {
                                                    Log.w("MainActivity", "⚠️ userProfile missing height, trying to recover from SharedPreferences")
                                                    currentProfile = UserPreferences.loadProfile(context, userEmail)
                                                }

                                                val finalProfile = currentProfile.copy(
                                                    equipment = plan.equipment,
                                                    focusAreas = plan.focusAreas
                                                )

                                                Log.d("MainActivity", "🔥 Saving UserProfile to Firestore: height=${finalProfile.height}, age=${finalProfile.age}, gender=${finalProfile.gender}")

                                                UserPreferences.saveProfileFirestore(finalProfile)
                                                UserPreferences.saveProfile(context, finalProfile) // ← lokalni cache
                                                userProfile = finalProfile

                                                // 4) Record plan creation for achievements AFTER saving profile
                                                // This way recordPlanCreation reads the latest profile and increments totalPlansCreated
                                                com.example.myapplication.persistence.AchievementStore.recordPlanCreation(context, userEmail)
                                                // Reload profile to get updated badges and totalPlansCreated
                                                userProfile = UserPreferences.loadProfile(context, userEmail)

                                                // 5) Ustvari začetni NutritionPlan iz plan podatkov
                                                val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                                                if (uid != null) {
                                                    val nutritionPlan = com.example.myapplication.data.NutritionPlan(
                                                        calories = plan.calories,
                                                        protein = plan.protein,
                                                        carbs = plan.carbs,
                                                        fat = plan.fat,
                                                        algorithmData = plan.algorithmData,
                                                        lastUpdated = System.currentTimeMillis()
                                                    )
                                                    com.example.myapplication.persistence.NutritionPlanStore.saveNutritionPlan(uid, nutritionPlan)
                                                }

                                                // 6) Zapiši weekly_target iz activityLevel v SharedPreferences
                                                val bodyPrefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                                val actParsed = userProfile.activityLevel?.replace("x", "")?.toIntOrNull()
                                                if (actParsed != null && actParsed > 0) {
                                                    bodyPrefs.edit().putInt("weekly_target", actParsed).apply()
                                                    Log.d("MainActivity", "✅ weekly_target set to $actParsed from activityLevel")
                                                } else if (plan.trainingDays > 0) {
                                                    bodyPrefs.edit().putInt("weekly_target", plan.trainingDays).apply()
                                                    Log.d("MainActivity", "✅ weekly_target set to ${plan.trainingDays} from plan (fallback)")
                                                }

                                                // 7) Osveži in pojdi na pregled
                                                bodyOverviewViewModel.refreshPlans()
                                                currentScreen = Screen.BodyOverview
                                            }
                                        }
                                    )
                                    }
                                    currentScreen is Screen.ProSubscription -> ProSubscriptionScreen(
                                        onBack = { navigateBack() },
                                        onSubscribed = { navigateTo(Screen.Dashboard) }
                                    )
                                    currentScreen is Screen.MyPlans -> MyPlansScreen(
                                        plans = plans,
                                        onPlanClick = { plan -> selectedPlan = plan },
                                        onPlanDelete = { plan ->
                                            scope.launch { PlanDataStore.deletePlan(context, plan.id) }
                                        }
                                    )
                                    currentScreen is Screen.MyAccount -> MyAccountScreen(
                                        userProfile = userProfile,
                                        onNavigateToDevSettings = { navigateTo(Screen.DeveloperSettings) },
                                        onProfileUpdate = { updatedProfile ->
                                            userProfile = updatedProfile
                                            UserPreferences.saveProfile(context, updatedProfile)
                                            scope.launch { UserPreferences.saveProfileFirestore(updatedProfile) }
                                        },
                                        onDeleteAllData = {
                                            scope.launch {
                                                try {
                                                    UserPreferences.deleteUserData(userEmail)
                                                    PlanDataStore.deleteAllPlans()
                                                    PlanDataStore.clearPlan(context)
                                                    UserPreferences.clearAllLocalData(context)
                                                    bodyOverviewViewModel.clearPlans()
                                                    errorMessage = "All data deleted."
                                                } catch (e: Exception) {
                                                    errorMessage = "Delete failed: ${e.localizedMessage}"
                                                }
                                            }
                                        },
                                        onDeleteAccount = {
                                            scope.launch {
                                                try {
                                                    UserPreferences.deleteUserData(userEmail)
                                                    PlanDataStore.deleteAllPlans()
                                                    PlanDataStore.clearPlan(context)
                                                    UserPreferences.clearAllLocalData(context)
                                                    com.example.myapplication.persistence.FirestoreHelper.clearCache()

                                                    // Delete Firebase Auth account
                                                    // Note: may throw FirebaseAuthRecentLoginRequiredException
                                                    try {
                                                        Firebase.auth.currentUser?.delete()?.await()
                                                    } catch (reauth: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                                                        // Re-auth required - sign out instead and inform user
                                                        Firebase.auth.signOut()
                                                        errorMessage = "Account data deleted. Please sign in again and retry to fully delete account."
                                                    }

                                                    bodyOverviewViewModel.clearPlans()
                                                    isLoggedIn = false
                                                    userEmail = ""
                                                    userProfile = com.example.myapplication.data.UserProfile(email = "")
                                                    navigationStack.clear()
                                                    currentScreen = Screen.Index
                                                    scope.launch { drawerState.close() }
                                                } catch (e: Exception) {
                                                    errorMessage = "Account deletion failed: ${e.localizedMessage}"
                                                }
                                            }
                                        },
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.DeveloperSettings -> {
                                        val latestPlan = plans.maxByOrNull { it.createdAt }
                                        DeveloperSettingsScreen(
                                            onBack = { navigateBack() },
                                            userProfile = userProfile,
                                            currentPlan = latestPlan
                                        )
                                    }
                                    currentScreen is Screen.PrivacyPolicy -> PrivacyPolicyScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.TermsOfService -> TermsOfServiceScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.Contact -> ContactScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.About -> AboutScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.LevelPath -> {
                                        val prefs = context.getSharedPreferences("body_module", Context.MODE_PRIVATE)
                                        val planDay = prefs.getInt("plan_day", 1)
                                        LevelPathScreen(
                                            userProfile = userProfile,
                                            activePlan = plans.firstOrNull(),
                                            currentPlanDay = planDay,
                                            onBack = { navigateBack() }
                                        )
                                    }
                                    currentScreen is Screen.BadgesScreen -> {
                                        // Badges so zdaj del LevelPathScreen
                                        val prefs = context.getSharedPreferences("body_module", Context.MODE_PRIVATE)
                                        val planDay = prefs.getInt("plan_day", 1)
                                        LevelPathScreen(
                                            userProfile = userProfile,
                                            activePlan = plans.firstOrNull(),
                                            currentPlanDay = planDay,
                                            onBack = { navigateBack() }
                                        )
                                    }
                                    currentScreen is Screen.Achievements -> {
                                        // Read workout tracking data from SharedPreferences
                                        val prefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                        val planDay = prefs.getInt("plan_day", 1)
                                        val weeklyDone = prefs.getInt("weekly_done", 0)

                                        AchievementsScreen(
                                            userProfile = userProfile,
                                            activePlan = plans.firstOrNull(),
                                            currentPlanDay = planDay,
                                            weeklyDone = weeklyDone,
                                            onRefresh = { bodyOverviewViewModel.refreshPlans() },
                                            onBack = { navigateBack() }
                                        )
                                    }
                                    currentScreen is Screen.PublicProfile -> {
                                        val userId = (currentScreen as Screen.PublicProfile).userId
                                        var profileData by remember { mutableStateOf<com.example.myapplication.data.PublicProfile?>(null) }

                                        LaunchedEffect(userId) {
                                            profileData = com.example.myapplication.persistence.ProfileStore.getPublicProfile(userId)
                                        }

                                        profileData?.let { profile ->
                                            PublicProfileScreen(
                                                profile = profile,
                                                onBack = { navigateBack() }
                                            )
                                        } ?: Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                    currentScreen is Screen.HealthConnect -> HealthConnectScreen(
                                        onBack = { navigateBack() }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Logged out – brez top bar
                    Scaffold(
                        topBar = { /* No top bar when logged out */ },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        // Ne dodaj padding na Index in ProFeatures screenih
                        val shouldApplyPadding = currentScreen is Screen.Login
                        Box(modifier = Modifier.then(
                            if (shouldApplyPadding) Modifier.padding(innerPadding) else Modifier
                        )) {
                            when {
                                currentScreen is Screen.Index -> IndexScreen(
                                    onLoginClick = {
                                        errorMessage = null
                                        navigateTo(Screen.Login(startInSignUp = false))
                                    },
                                    onSignUpClick = {
                                        errorMessage = null
                                        navigateTo(Screen.Login(startInSignUp = true))
                                    },
                                    onViewProFeatures = {
                                        errorMessage = null
                                        previousScreen = Screen.Index
                                        navigateTo(Screen.ProFeatures)
                                    }
                                )
                                currentScreen is Screen.Login -> LoginScreen(
                                    startInSignUp = (currentScreen as Screen.Login).startInSignUp,
                                    onForgotPassword = { email ->
                                        errorMessage = null
                                        Firebase.auth.sendPasswordResetEmail(email)
                                            .addOnCompleteListener { task ->
                                                errorMessage = if (task.isSuccessful) {
                                                    "Password reset email sent to $email"
                                                } else {
                                                    task.exception?.localizedMessage
                                                        ?: "Failed to send reset email"
                                                }
                                            }
                                    },
                                    onLogin = { email, password ->
                                        errorMessage = null
                                        Firebase.auth.signInWithEmailAndPassword(email, password)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val user = Firebase.auth.currentUser
                                                    if (user != null && user.isEmailVerified) {
                                                        isLoggedIn = true
                                                        userEmail = email
                                                        userProfile = UserPreferences.loadProfile(context, userEmail)
                                                        scope.launch {
                                                            isDarkMode = UserPreferences.isDarkMode(userEmail)
                                                        }
                                                        navigationStack.clear() // Clear navigation stack on login
                                                        currentScreen = Screen.Dashboard
                                                    } else if (user != null) {
                                                        errorMessage = "Please verify your email first!"
                                                        Firebase.auth.signOut()
                                                    } else {
                                                        errorMessage = "Error: no user."
                                                    }
                                                } else {
                                                    errorMessage = task.exception?.localizedMessage
                                                        ?: "Login failed."
                                                }
                                            }
                                    },
                                    onSignup = { email, password, confirmPassword ->
                                        errorMessage = null
                                        if (password != confirmPassword) {
                                            errorMessage = "Passwords do not match."
                                            return@LoginScreen
                                        }
                                        Firebase.auth.createUserWithEmailAndPassword(email, password)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val user = Firebase.auth.currentUser
                                                    user?.sendEmailVerification()
                                                    errorMessage =
                                                        "Sign-up successful! Check your email to verify."
                                                    Firebase.auth.signOut()
                                                } else {
                                                    errorMessage = task.exception?.localizedMessage
                                                        ?: "Sign-up failed."
                                                }
                                            }
                                    },
                                    onBackToHome = {
                                        errorMessage = null
                                        currentScreen = Screen.Index
                                    },
                                    errorMessage = errorMessage,
                                    onGoogleSignInClick = {
                                        errorMessage = null
                                        val signInIntent = googleSignInClient.signInIntent
                                        googleSignInLauncher.launch(signInIntent)
                                    },
                                    onTermsClick = { navigateTo(Screen.TermsOfService) },
                                    onPrivacyClick = { navigateTo(Screen.PrivacyPolicy) }
                                )
                                currentScreen is Screen.ProFeatures -> ProFeaturesScreen(
                                    onFreeTrial = { /* akcija ob kliku na free trial gumb */ },
                                    onContinue = {
                                        errorMessage = null
                                        navigateTo(Screen.Login())
                                    },
                                    onBack = {
                                        errorMessage = null
                                        navigateBack()
                                    },
                                    errorMessage = errorMessage
                                )
                                currentScreen is Screen.TermsOfService -> TermsOfServiceScreen(
                                    onBack = { navigateBack() }
                                )
                                currentScreen is Screen.PrivacyPolicy -> PrivacyPolicyScreen(
                                    onBack = { navigateBack() }
                                )
                            }
                        }
                    }
                }
            }

            // Badge Unlock Animation Overlay
            unlockedBadge?.let { badge ->
                com.example.myapplication.ui.components.BadgeUnlockAnimation(
                    badge = badge,
                    onDismiss = { unlockedBadge = null }
                )
            }
        }
    }

    private fun firebaseAuthWithGoogle(
        acct: GoogleSignInAccount?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(acct?.idToken, null)
        Firebase.auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onError(task.exception?.localizedMessage ?: "Google sign-in failed.")
                }
            }
    }
}

/* ===== Globalni header bar (vedno viden) ===== */
@Composable
private fun GlobalHeaderBar(
    onOpenMenu: () -> Unit,
    onProClick: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .statusBarsPadding() // samodejno doda prostor pod status bar / izrez
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElevatedCard(
                onClick = onOpenMenu,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = DrawerBlue),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "User's\nStickman",
                        color = Color.White,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            AssistChip(
                onClick = onProClick,
                label = {
                    Text(
                        text = "Upgrade to Premium",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = DrawerBlue,
                    labelColor = Color.White
                )
            )
        }
    }
}

/* ===== Professional Drawer with Edit & Level System ===== */
@Composable
private fun FigmaDrawerContent(
    userProfile: com.example.myapplication.data.UserProfile,
    onClose: () -> Unit,
    onLogout: () -> Unit,
    onProfileUpdate: (com.example.myapplication.data.UserProfile) -> Unit,
    isDarkMode: Boolean,
    onDarkModeToggle: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfService: () -> Unit,
    onNavigateToContact: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLevelPath: () -> Unit = {},
    onNavigateToBadges: () -> Unit = {},
    onNavigateToHealthConnect: () -> Unit = {},
    onNavigateToMyAccount: () -> Unit // Added callback
) {
    val PrimaryBlue = Color(0xFF2563EB)
    // Drawer uporablja uporabnikovo isDarkMode nastavitev
    val SheetBg = if (isDarkMode) Color(0xFF1F2937) else Color.White
    val CardBg = if (isDarkMode) Color(0xFF374151) else Color(0xFFF3F4F6)
    val TextPrimary = if (isDarkMode) Color.White else Color.Black
    val TextSecondary = if (isDarkMode) Color(0xFFD1D5DB) else Color(0xFF6B7280)

    var isEditingPersonal by remember { mutableStateOf(false) }
    var editedProfile by remember { mutableStateOf(userProfile) }
    var showEquipmentDialog by remember { mutableStateOf(false) }
    // Upload logic removed
    var isPublicPrivacy by remember { mutableStateOf(userProfile.isPublicProfile) }
    var showLevelPrivacy by remember { mutableStateOf(userProfile.showLevel) }
    var showBadgesPrivacy by remember { mutableStateOf(userProfile.showBadges) }
    var showPlanPathPrivacy by remember { mutableStateOf(userProfile.showPlanPath) }
    var showChallengesPrivacy by remember { mutableStateOf(userProfile.showChallenges) }
    var showFollowersPrivacy by remember { mutableStateOf(userProfile.showFollowers) }

    LaunchedEffect(userProfile) {
        editedProfile = userProfile
        isPublicPrivacy = userProfile.isPublicProfile
        showLevelPrivacy = userProfile.showLevel
        showBadgesPrivacy = userProfile.showBadges
        showPlanPathPrivacy = userProfile.showPlanPath
        showChallengesPrivacy = userProfile.showChallenges
        showFollowersPrivacy = userProfile.showFollowers
    }

    Surface(
        color = SheetBg,
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            ) {
                TextButton(onClick = onClose) {
                    Text("← Back", color = TextPrimary)
                }
                Spacer(Modifier.weight(1f))
            }

            // 1. Stickman (No upload logic)
            Surface(
                color = PrimaryBlue,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .padding(vertical = 8.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    // Always show Stickman text, no image loading
                    Text(
                        text = "User's\nStickman",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2. Stats Tile (Level, Followers, Badges)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clickable { onNavigateToLevelPath() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Level Header with Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = PrimaryBlue.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "LEVEL",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${userProfile.level}",
                                color = PrimaryBlue,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "⭐ ${userProfile.xp}",
                                color = Color(0xFFFEE440),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Total XP",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // XP Progress Bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Next Level",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${(userProfile.progressToNextLevel * 100).toInt()}%",
                                color = PrimaryBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        // Animated progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(TextSecondary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(userProfile.progressToNextLevel)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                PrimaryBlue,
                                                Color(0xFF60A5FA)
                                            )
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${userProfile.xp - userProfile.xpForCurrentLevel} / ${userProfile.xpForNextLevel - userProfile.xpForCurrentLevel} XP",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))

                    // Stats Row - Badges klikabilno
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "👥",
                                fontSize = 24.sp
                            )
                            Text(
                                userProfile.followers.toString(),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Followers",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToBadges() }
                        ) {
                            Text(
                                "🏆",
                                fontSize = 24.sp
                            )
                            // Izračunaj pravilno število odklenjenih badgov (shranjeni + avtomatsko odklenjeni)
                            val autoUnlockedCount = calculateAutoUnlockedBadgeCount(userProfile)
                            val totalUnlockedBadges = (userProfile.badges.toSet() + autoUnlockedCount.first).size
                            Text(
                                totalUnlockedBadges.toString(),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Badges",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 3. Connect with Health Connect Button
            Button(
                onClick = {
                    onNavigateToHealthConnect()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.HealthAndSafety,
                    contentDescription = "Health Connect",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Connect with Health Connect", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            // 4. Dark Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode", color = TextPrimary, fontWeight = FontWeight.Bold)
                Switch(checked = isDarkMode, onCheckedChange = { onDarkModeToggle() })
            }

            Spacer(Modifier.height(12.dp))

            // 5. Settings + Privacy Settings (Grouped)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Settings & Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))

                    // Unit settings
                    SettingsDropdownRow(
                        label = "Weight Unit",
                        currentValue = userProfile.weightUnit,
                        options = listOf("kg", "lb"),
                        textColor = TextPrimary
                    ) { newValue ->
                        onProfileUpdate(userProfile.copy(weightUnit = newValue))
                    }

                    SettingsDropdownRow(
                        label = "Speed Unit",
                        currentValue = userProfile.speedUnit,
                        options = listOf("km/h", "mph", "m/s"),
                        textColor = TextPrimary
                    ) { newValue ->
                        onProfileUpdate(userProfile.copy(speedUnit = newValue))
                    }

                    SettingsDropdownRow(
                        label = "Start of Week",
                        currentValue = userProfile.startOfWeek,
                        options = listOf("Monday", "Saturday", "Sunday"),
                        textColor = TextPrimary
                    ) { newValue ->
                        onProfileUpdate(userProfile.copy(startOfWeek = newValue))
                    }

                    // Detailed Calories Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Detailed Calories", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(
                                "Show fat/protein/carbs segments",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = userProfile.detailedCalories,
                            onCheckedChange = { newValue ->
                                onProfileUpdate(userProfile.copy(detailedCalories = newValue))
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))

                    // Public Profile Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Public Profile", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(
                                "Allow others to see your profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = isPublicPrivacy,
                            onCheckedChange = { newValue ->
                                isPublicPrivacy = newValue
                                if (!newValue) {
                                    showLevelPrivacy = false
                                    showBadgesPrivacy = false
                                    showPlanPathPrivacy = false
                                    showChallengesPrivacy = false
                                    showFollowersPrivacy = false
                                }
                                onProfileUpdate(userProfile.copy(
                                    isPublicProfile = newValue,
                                    showLevel = if (newValue) showLevelPrivacy else false,
                                    showBadges = if (newValue) showBadgesPrivacy else false,
                                    showPlanPath = if (newValue) showPlanPathPrivacy else false,
                                    showChallenges = if (newValue) showChallengesPrivacy else false,
                                    showFollowers = if (newValue) showFollowersPrivacy else false
                                ))
                            }
                        )
                    }

                    if (isPublicPrivacy) {
                         // Privacy Sub-toggles
                         Spacer(Modifier.height(4.dp))
                         // ... repeating sub-toggles logic concisely ...
                         val privacyToggles = listOf(
                             "Show Level" to showLevelPrivacy,
                             "Show Badges" to showBadgesPrivacy,
                             "Show Plan Path" to showPlanPathPrivacy,
                             "Show Challenges" to showChallengesPrivacy,
                             "Show Followers" to showFollowersPrivacy
                         )

                         // Helper for privacy rows to save space
                         privacyToggles.forEachIndexed { index, (label, checked) ->
                             Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { newValue ->
                                        when(index) {
                                            0 -> { showLevelPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showLevel = newValue)) }
                                            1 -> { showBadgesPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showBadges = newValue)) }
                                            2 -> { showPlanPathPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showPlanPath = newValue)) }
                                            3 -> { showChallengesPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showChallenges = newValue)) }
                                            4 -> { showFollowersPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showFollowers = newValue)) }
                                        }
                                    }
                                )
                            }
                         }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 6. Equipment
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEquipmentDialog = true }
            ) {
                 Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Equipment", style = MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (userProfile.equipment.isEmpty()) "None" else "${userProfile.equipment.size} items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary.copy(alpha = 0.7f)
                        )
                        Icon(Icons.Filled.KeyboardArrowRight, null, tint = TextPrimary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 7. My Account Info (Header)
            Text(
                "My Account Info",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(12.dp))

            // 8. Info & Support (Tile)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Info & Support",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    NavigationRow("Privacy Policy", TextPrimary) { onNavigateToPrivacyPolicy() }
                    NavigationRow("Terms of Service", TextPrimary) { onNavigateToTermsOfService() }
                    NavigationRow("Contact", TextPrimary) { onNavigateToContact() }
                    NavigationRow("About", TextPrimary) { onNavigateToAbout() }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 9. Personal Data (Tile)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Personal Data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        TextButton(onClick = {
                            if (isEditingPersonal) {
                                onProfileUpdate(editedProfile)
                            }
                            isEditingPersonal = !isEditingPersonal
                        }) {
                            Text(if (isEditingPersonal) "Save" else "Edit", color = PrimaryBlue)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (isEditingPersonal) {
                        OutlinedTextField(
                            value = editedProfile.username,
                            onValueChange = { editedProfile = editedProfile.copy(username = it) },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                focusedLabelColor = PrimaryBlue
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editedProfile.firstName,
                            onValueChange = { editedProfile = editedProfile.copy(firstName = it) },
                            label = { Text("First Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                focusedLabelColor = PrimaryBlue
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editedProfile.lastName,
                            onValueChange = { editedProfile = editedProfile.copy(lastName = it) },
                            label = { Text("Last Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                focusedLabelColor = PrimaryBlue
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editedProfile.address,
                            onValueChange = { editedProfile = editedProfile.copy(address = it) },
                            label = { Text("Address") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                focusedLabelColor = PrimaryBlue
                            )
                        )
                    } else {
                        ProfileDataRow("Username", editedProfile.username.ifBlank { "Not set" }, TextSecondary, TextPrimary)
                        ProfileDataRow("First Name", editedProfile.firstName.ifBlank { "Not set" }, TextSecondary, TextPrimary)
                        ProfileDataRow("Last Name", editedProfile.lastName.ifBlank { "Not set" }, TextSecondary, TextPrimary)
                        ProfileDataRow("Address", editedProfile.address.ifBlank { "Not set" }, TextSecondary, TextPrimary)
                    }

                    Spacer(Modifier.height(8.dp))
                    ProfileDataRow("Email", userProfile.email, TextSecondary, TextPrimary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 10. My Account Button
            Button(
                onClick = {
                    onNavigateToMyAccount()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray.copy(alpha = 0.2f),
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("My Account", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))

            // 11. Logout button
            var showLogoutDialog by remember { mutableStateOf(false) }

            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Log out", fontWeight = FontWeight.SemiBold)
            }

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text("Log out") },
                    text = { Text("Are you sure you want to log out?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showLogoutDialog = false
                                onLogout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("Log out")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showLogoutDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }

    if (showEquipmentDialog) {
        EquipmentSelectionDialog(
            currentSelection = userProfile.equipment,
            onDismiss = { showEquipmentDialog = false },
            onSave = { newEquipment ->
                onProfileUpdate(userProfile.copy(equipment = newEquipment))
                showEquipmentDialog = false
            },
            isDarkMode = isDarkMode
        )
    }
}

@Composable
fun EquipmentSelectionDialog(
    currentSelection: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    isDarkMode: Boolean
) {
    val options = listOf("Dumbbells", "Barbell", "Kettlebell", "Pull-up Bar", "Resistance Bands", "Bench", "Treadmill", "None (Bodyweight)")
    var selection by remember { mutableStateOf(currentSelection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Equipment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selection = if (option == "None (Bodyweight)") {
                                    if (selection.contains(option)) emptyList() else listOf(option)
                                } else {
                                    val newSet = selection.toMutableSet()
                                    newSet.remove("None (Bodyweight)")
                                    if (newSet.contains(option)) newSet.remove(option) else newSet.add(option)
                                    newSet.toList()
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = selection.contains(option),
                            onCheckedChange = { isChecked ->
                                selection = if (option == "None (Bodyweight)") {
                                    if (isChecked) listOf(option) else emptyList()
                                } else {
                                    val newSet = selection.toMutableSet()
                                    newSet.remove("None (Bodyweight)")
                                    if (isChecked) newSet.add(option) else newSet.remove(option)
                                    newSet.toList()
                                }
                            }
                        )
                        Text(option, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selection) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProfileDataRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    labelColor: Color = Color.Unspecified
) {
    // Removed unused PrimaryBlue

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = labelColor,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun NavigationRow(label: String, textColor: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor)
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownRow(
    label: String,
    currentValue: String,
    options: List<String>,
    textColor: Color,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )

        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, textColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Expand",
                    tint = textColor
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (option == currentValue) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            expanded = false
                            onValueChange(option)
                        },
                        contentPadding = PaddingValues(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Izračuna avtomatsko odklenjene badge ID-je glede na napredek uporabnika
 * Vrne Pair(Set<String> autoUnlockedIds, Int totalAutoUnlocked)
 */
private fun calculateAutoUnlockedBadgeCount(userProfile: com.example.myapplication.data.UserProfile): Pair<Set<String>, Int> {
    val autoUnlockedIds = mutableSetOf<String>()

    // Workout badges
    if (userProfile.totalWorkoutsCompleted >= 1) autoUnlockedIds.add("first_workout")
    if (userProfile.totalWorkoutsCompleted >= 10) autoUnlockedIds.add("committed_10")
    if (userProfile.totalWorkoutsCompleted >= 50) autoUnlockedIds.add("committed_50")
    if (userProfile.totalWorkoutsCompleted >= 100) autoUnlockedIds.add("committed_100")
    if (userProfile.totalWorkoutsCompleted >= 250) autoUnlockedIds.add("committed_250")
    if (userProfile.totalWorkoutsCompleted >= 500) autoUnlockedIds.add("committed_500")

    // Calorie badges
    if (userProfile.totalCaloriesBurned >= 1000) autoUnlockedIds.add("calorie_crusher_1k")
    if (userProfile.totalCaloriesBurned >= 5000) autoUnlockedIds.add("calorie_crusher_5k")
    if (userProfile.totalCaloriesBurned >= 10000) autoUnlockedIds.add("calorie_crusher_10k")

    // Level badges
    if (userProfile.level >= 5) autoUnlockedIds.add("level_5")
    if (userProfile.level >= 10) autoUnlockedIds.add("level_10")
    if (userProfile.level >= 25) autoUnlockedIds.add("level_25")
    if (userProfile.level >= 50) autoUnlockedIds.add("level_50")

    // Follower badges
    if (userProfile.followers >= 1) autoUnlockedIds.add("first_follower")
    if (userProfile.followers >= 10) autoUnlockedIds.add("social_butterfly")
    if (userProfile.followers >= 50) autoUnlockedIds.add("influencer")
    if (userProfile.followers >= 100) autoUnlockedIds.add("celebrity")

    // Time-based badges
    if (userProfile.earlyBirdWorkouts >= 5) autoUnlockedIds.add("early_bird")
    if (userProfile.nightOwlWorkouts >= 5) autoUnlockedIds.add("night_owl")

    // Streak badges
    if (userProfile.currentLoginStreak >= 7) autoUnlockedIds.add("week_warrior")
    if (userProfile.currentLoginStreak >= 30) autoUnlockedIds.add("month_master")
    if (userProfile.currentLoginStreak >= 365) autoUnlockedIds.add("year_champion")

    // Plan badges
    if (userProfile.totalPlansCreated >= 1) autoUnlockedIds.add("first_plan")
    if (userProfile.totalPlansCreated >= 5) autoUnlockedIds.add("plan_master")

    return Pair(autoUnlockedIds, autoUnlockedIds.size)
}

