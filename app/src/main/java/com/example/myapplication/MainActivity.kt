@file:Suppress("DEPRECATION") // Suppress deprecation warnings for GoogleSignIn usage until migration to Credential Manager

package com.example.myapplication

// =====================================================================
// MainActivity.kt
// Vsebuje: samo MainActivity class (onCreate, auth, app state, navigacija).
// Screen sealed class → AppNavigation.kt
// Drawer / Header     → AppDrawer.kt
// =====================================================================

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.settings.UserProfileManager
import com.example.myapplication.network.OpenFoodFactsProduct
import com.example.myapplication.persistence.PlanDataStore
import com.example.myapplication.ui.home.CommunityScreen
import com.example.myapplication.ui.screens.*
import com.example.myapplication.data.PlanResult
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.utils.HapticFeedback
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await


class MainActivity : ComponentActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient

    // Store intent extras to trigger effects
    private val intentExtras = mutableStateOf<Bundle?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentExtras.value = intent.extras
    }

    override fun onPause() {
        super.onPause()
        try {
            com.example.myapplication.worker.DailySyncWorker.schedule(this)
        } catch (_: Exception) {}
    }

    private var isSubscribed = false
    private var paymentStatus: String? = null
    
    // Performance timer
    private var coldStartEpochMs: Long = 0L

    // Push notification permission launcher
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            Log.d("MainActivity", "Notification permission granted: $isGranted")
        }

    @Suppress("DEPRECATION")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        coldStartEpochMs = android.os.SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)

        // Eager load AdvancedExerciseRepository in background immediately upon app startup
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val jsonString = applicationContext.assets.open("exercises.json").bufferedReader().use { it.readText() }
            com.example.myapplication.data.AdvancedExerciseRepository.init(jsonString)
        }

        intentExtras.value = intent.extras

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            // First paint measurement
            LaunchedEffect(Unit) {
                val timeToPaint = android.os.SystemClock.elapsedRealtime() - coldStartEpochMs
                Log.i("AppPerf", " First paint arrived at: $timeToPaint ms")
            }

            val context = LocalContext.current
            val bodyOverviewViewModel: BodyOverviewViewmodel =
                viewModel(factory = MyViewModelFactory())
            val plans: List<com.example.myapplication.data.PlanResult> by bodyOverviewViewModel.plans.collectAsState()

            val appViewModel: AppViewModel = viewModel()
            val userProfile by appViewModel.userProfile.collectAsState()

            val networkObserver = remember { com.example.myapplication.utils.NetworkObserver(context) }
            val isOnline by networkObserver.observe().collectAsState(initial = true)

            val navViewModel: NavigationViewModel = viewModel()
            val currentScreen by navViewModel.currentScreen.collectAsState()
            val previousScreen by navViewModel.previousScreen.collectAsState()

            var isCheckingAuth by remember { mutableStateOf(true) }

            // ----- Auth stanje -----
            var isLoggedIn by remember { mutableStateOf(false) }
            var userEmail by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var isDarkMode by remember { mutableStateOf(false) }

            // ----- Vadba stanje -----
            var selectedPlan by remember { mutableStateOf<PlanResult?>(null) }
            var isExtraWorkoutSession by remember { mutableStateOf(false) }
            var extraFocusAreas by remember { mutableStateOf<List<String>>(emptyList()) }
            var extraEquipment by remember { mutableStateOf<Set<String>>(emptySet()) }

            // ----- Prehrana stanje -----
            var scannedProduct by remember { mutableStateOf<Pair<OpenFoodFactsProduct, String>?>(null) }
            val nutritionSnackbarHostState = remember { SnackbarHostState() }

            // ----- Badge animacija -----
            var unlockedBadge by remember { mutableStateOf<com.example.myapplication.data.Badge?>(null) }

            val scope = rememberCoroutineScope()
            val drawerState = rememberDrawerState(DrawerValue.Closed)

            var pendingNavigateToNutrition by remember { mutableStateOf(false) }
            var pendingOpenBarcodeScan by remember { mutableStateOf(false) }
            var pendingOpenFoodSearch by remember { mutableStateOf(false) }

            val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                    firebaseAuthWithGoogle(account, onSuccess = {
                        val user = Firebase.auth.currentUser
                        if (user != null && user.email != null) {
                            isLoggedIn = true; userEmail = user.email!!
                            appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                            scope.launch { isDarkMode = UserProfileManager.isDarkMode(userEmail) }
                            navViewModel.clearStack(); navViewModel.navigateTo(Screen.Dashboard)
                        } else errorMessage = "Google sign-in error."
                    }, onError = { err -> errorMessage = err })
                } catch (e: Exception) { errorMessage = "Google sign-in failed: ${e.localizedMessage}" }
            }

            // === INITIAL AUTH & SYNC FLOW ===
            LaunchedEffect(Unit) {
                appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                val accountEmail = com.google.firebase.ktx.Firebase.auth.currentUser?.email

                // Preveri fresh_start flag (nastavljen ob delete data/account)
                val appFlags = context.getSharedPreferences("app_flags", Context.MODE_PRIVATE)
                if (appFlags.getBoolean("fresh_start_on_login", false)) {
                    // Pobriši vse bm_prefs na čisto — novo začetno stanje
                    context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    context.getSharedPreferences("algorithm_prefs", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    // Počisti flag
                    appFlags.edit().remove("fresh_start_on_login").apply()
                    Log.d("MainActivity", "✅ Fresh start: bm_prefs reset")
                }

                // ── Faza 5: One-time migracija — pobriši stare lokalne SharedPrefs caches ──
                // water_cache, burned_cache, calories_cache, food_cache so bili legaccy
                // vmesni buffer za sync. Firestore SDK (isPersistenceEnabled=true) zdaj
                // skrbi za offline delovanje sam — te datoteke niso več potrebne.
                val migPrefs = context.getSharedPreferences("migration_flags", Context.MODE_PRIVATE)
                if (!migPrefs.getBoolean("faza5_legacy_purge_done", false)) {
                    com.example.myapplication.persistence.DailySyncManager.clearLegacyCache(context)
                    migPrefs.edit().putBoolean("faza5_legacy_purge_done", true).apply()
                    Log.i("MainActivity", "✅ Faza 5: Legacy lokalni cache počiščen → Firestore je Single Source of Truth")
                }
                // ──────────────────────────────────────────────────────────────────────────

                    if (accountEmail != null) {
                        userEmail = accountEmail

                        // Nastavi uporabniški profil
                        appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                        isDarkMode = UserProfileManager.isDarkMode(userEmail)

                    // Preveri in sinhroniziraj badge ob zagonu
                    try {
                        val useCase = com.example.myapplication.domain.gamification.GamificationProvider.provide(context)
                        useCase.checkAndSyncBadgesOnStartup()
                    }
                    catch (e: Exception) { Log.e("MainActivity", "Badge sync error: ${e.message}") }

                    // Sinhronizacija načrtov
                    try { PlanDataStore.migrateLocalPlansToFirestore(context) } catch (_: Exception) {}
                    bodyOverviewViewModel.refreshPlans()

                    // Osveži podatke o profilu iz Firestore
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val remote = UserProfileManager.loadProfileFromFirestore(userEmail)
                            if (remote != null) {
                                UserProfileManager.saveProfile(remote)
                                val actParsed = remote.activityLevel?.replace("x", "")?.toIntOrNull()
                                if (actParsed != null && actParsed > 0) {
                                    context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                        .edit().putInt("weekly_target", actParsed).apply()
                                }
                                withContext(Dispatchers.Main) { appViewModel.handleIntent(AppIntent.SetProfile(remote)) }
                            }
                        } catch (_: Exception) {}
                    }

                    // Nastavitev start poslušanja s pomočjo UseCase
                    appViewModel.handleIntent(AppIntent.StartListening(userEmail))
                }
                isCheckingAuth = false
            }

            // ----- Auth check ob zagonu -----
            var isSyncing by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val user = Firebase.auth.currentUser
                if (user != null && (user.isEmailVerified || user.providerData.any { it.providerId == "google.com" })) {
                    isLoggedIn = true
                    userEmail = user.email ?: ""
                    isSyncing = true // show "Syncing your fitness data…" overlay

                    appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                    isDarkMode = UserProfileManager.isDarkMode(userEmail)
                    navViewModel.navigateTo(if (pendingNavigateToNutrition) Screen.Nutrition else Screen.Dashboard)

                    scope.launch(Dispatchers.IO) {
                        try {
                            val useCase = com.example.myapplication.domain.gamification.GamificationProvider.provide(context)
                            useCase.recordLoginOnly()
                        }
                        catch (e: Exception) { Log.e("MainActivity", "Login streak error: ${e.message}") }
                    }
                    scope.launch(Dispatchers.IO) {
                        try {
                            val profile = UserProfileManager.loadProfile(userEmail)
                            com.example.myapplication.workers.WeeklyStreakWorker.ensureScheduled(context, profile.startOfWeek)
                        } catch (e: Exception) { Log.e("MainActivity", "WeeklyStreakWorker error: ${e.message}") }
                    }

                    // Tedenski čistilec starih GPS run_routes .json datotek
                    try {
                        com.example.myapplication.worker.RunRouteCleanupWorker.ensureScheduled(context)
                    } catch (e: Exception) { Log.e("MainActivity", "RunRouteCleanupWorker error: ${e.message}") }

                    delay(250)
                    scope.launch(Dispatchers.IO) {
                        try {
                            val remote = UserProfileManager.loadProfileFromFirestore(userEmail)
                            if (remote != null) {
                                UserProfileManager.saveProfile(remote)
                                val actParsed = remote.activityLevel?.replace("x", "")?.toIntOrNull()
                                if (actParsed != null && actParsed > 0) {
                                    context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                        .edit().putInt("weekly_target", actParsed).apply()
                                }
                                withContext(Dispatchers.Main) {
                                    appViewModel.handleIntent(AppIntent.SetProfile(remote))
                                    isSyncing = false // ← data loaded, hide overlay
                                }
                            } else {
                                withContext(Dispatchers.Main) { isSyncing = false }
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) { isSyncing = false }
                        }
                    }
                    scope.launch(Dispatchers.IO) {
                        delay(1500)
                        try {
                            val useCase = com.example.myapplication.domain.gamification.GamificationProvider.provide(context)
                            useCase.checkAndSyncBadgesOnStartup()
                        }
                        catch (e: Exception) { Log.e("MainActivity", "Badge sync error: ${e.message}") }
                    }
                    scope.launch {
                        try { PlanDataStore.migrateLocalPlansToFirestore(context) } catch (_: Exception) {}
                        bodyOverviewViewModel.refreshPlans()
                        try { com.example.myapplication.widget.StreakWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                        try { com.example.myapplication.widget.PlanDayWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                        val syncUid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                        if (syncUid != null) {
                            try { com.example.myapplication.persistence.DailySyncManager.syncOnAppOpen(context, syncUid) } catch (_: Exception) {}
                        }
                    }

                    // Real-time Firestore listener — kliče loadProfileFromFirestore() (ni ročnega gradnje UserProfile)
                    appViewModel.handleIntent(AppIntent.StartListening(userEmail))
                } else {
                    navViewModel.navigateTo(Screen.Index)
                }
                isCheckingAuth = false
            }

            // ----- Workers scheduling ob loginu (email, Google, auto-login) -----
            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    try {
                        com.example.myapplication.workers.StreakReminderWorker.createNotificationChannel(context)
                        com.example.myapplication.workers.StreakReminderWorker.scheduleForToday(context)
                    } catch (e: Exception) { Log.e("MainActivity", "StreakReminderWorker schedule error: ${e.message}") }
                }
            }

            // ----- Widget intent handling -----
            var showWeightInputDialog by remember { mutableStateOf(false) }
            var openBarcodeScan by remember { mutableStateOf(false) }
            var openFoodSearch by remember { mutableStateOf(false) }
            var pendingNavigateToBodyModule by remember { mutableStateOf(false) }

            LaunchedEffect(intentExtras.value) {
                val extras = intentExtras.value ?: return@LaunchedEffect
                Log.d("MainActivity", "Intent extras: NAVIGATE_TO=${extras.getString("NAVIGATE_TO")}")
                if (extras.getBoolean("OPEN_WEIGHT_INPUT", false)) {
                    showWeightInputDialog = true
                    if (isLoggedIn) navViewModel.navigateTo(Screen.Progress)
                }
                if (extras.getString("NAVIGATE_TO") == "nutrition") {
                    val wantsScan = extras.getBoolean("OPEN_BARCODE_SCAN", false)
                    val wantsSearch = extras.getBoolean("OPEN_FOOD_SEARCH", false)
                    if (isLoggedIn && !isCheckingAuth) {
                        navViewModel.navigateTo(Screen.Nutrition)
                        delay(150)
                        if (wantsScan) openBarcodeScan = true
                        if (wantsSearch) openFoodSearch = true
                    } else {
                        pendingNavigateToNutrition = true
                        pendingOpenBarcodeScan = wantsScan
                        pendingOpenFoodSearch = wantsSearch
                    }
                }
                if (extras.getString("NAVIGATE_TO") == "body_module") {
                    if (isLoggedIn && !isCheckingAuth) {
                        navViewModel.navigateTo(Screen.BodyModuleHome)
                    } else {
                        pendingNavigateToBodyModule = true
                    }
                }
            }

            LaunchedEffect(isCheckingAuth, isLoggedIn, pendingNavigateToBodyModule) {
                if (!isCheckingAuth && isLoggedIn && pendingNavigateToBodyModule) {
                    navViewModel.navigateTo(Screen.BodyModuleHome)
                    pendingNavigateToBodyModule = false
                }
            }

            LaunchedEffect(isCheckingAuth, isLoggedIn, pendingNavigateToNutrition, pendingOpenBarcodeScan, pendingOpenFoodSearch) {
                if (!isCheckingAuth && isLoggedIn && pendingNavigateToNutrition) {
                    navViewModel.navigateTo(Screen.Nutrition)
                    delay(150)
                    if (pendingOpenBarcodeScan) openBarcodeScan = true
                    if (pendingOpenFoodSearch) openFoodSearch = true
                    pendingNavigateToNutrition = false; pendingOpenBarcodeScan = false; pendingOpenFoodSearch = false
                }
            }

            // ----- Navigacijski pomočniki -----
            fun navigateTo(screen: Screen) = navViewModel.navigateTo(screen)

            fun navigateBack() = navViewModel.navigateBack(
                isLoggedIn = isLoggedIn,
                hasSelectedPlan = selectedPlan != null,
                onClearSelectedPlan = { selectedPlan = null },
                onClearError = { errorMessage = null },
                onFinish = { finish() }
            )

            BackHandler { navigateBack() }

            val shouldUseDarkMode = if (!isLoggedIn && currentScreen is Screen.Index) true else isDarkMode

            MyApplicationTheme(darkTheme = shouldUseDarkMode) {
                if (isCheckingAuth) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary) }
                } else if (isLoggedIn) {
                    val enableDrawerGestures = when (currentScreen) {
                        is Screen.Dashboard, is Screen.Progress, is Screen.BodyModuleHome,
                        is Screen.BodyOverview, is Screen.MyPlans, is Screen.FaceModule,
                        is Screen.HairModule, is Screen.Shop,
                        is Screen.Community, is Screen.MyAccount, is Screen.ExerciseHistory,
                        is Screen.ManualExerciseLog, is Screen.Nutrition -> true
                        else -> currentScreen !is Screen.RunTracker && currentScreen !is Screen.ActivityLog
                    }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = enableDrawerGestures,
                        drawerContent = {
                            FigmaDrawerContent(
                                userProfile = userProfile,
                                onClose = { scope.launch { drawerState.close() } },
                                onLogout = {
                                    com.google.firebase.ktx.Firebase.auth.signOut()
                                    googleSignInClient.signOut()
                                    com.example.myapplication.persistence.FirestoreHelper.clearCache()
                                    appViewModel.handleIntent(AppIntent.SetProfile(com.example.myapplication.data.UserProfile(email = "")))
                                    val lp = context.getSharedPreferences("local_prefs", Context.MODE_PRIVATE)
                                    lp.edit().putString("fcm_token", "").apply()
                                    isLoggedIn = false
                                    navViewModel.clearStack()
                                },
                                                onProfileUpdate = { updatedProfile ->
                                                    appViewModel.handleIntent(AppIntent.SetProfile(updatedProfile))
                                                    UserProfileManager.saveProfile(updatedProfile)
                                                    scope.launch { UserProfileManager.saveProfileFirestore(updatedProfile) }
                                                },
                                                isDarkMode = isDarkMode,
                                onDarkModeToggle = {
                                    isDarkMode = !isDarkMode
                                    scope.launch { UserProfileManager.setDarkMode(userEmail, isDarkMode) }
                                },
                                onNavigateToPrivacyPolicy = { navigateTo(Screen.PrivacyPolicy); scope.launch { drawerState.close() } },
                                onNavigateToTermsOfService = { navigateTo(Screen.TermsOfService); scope.launch { drawerState.close() } },
                                onNavigateToContact = { navigateTo(Screen.Contact); scope.launch { drawerState.close() } },
                                onNavigateToAbout = { navigateTo(Screen.About); scope.launch { drawerState.close() } },
                                onNavigateToAchievements = { navigateTo(Screen.Achievements); scope.launch { drawerState.close() } },
                                onNavigateToBadges = { navigateTo(Screen.Achievements); scope.launch { drawerState.close() } },
                                onNavigateToHealthConnect = { navigateTo(Screen.HealthConnect); scope.launch { drawerState.close() } },
                                onNavigateToDebugDashboard = { navigateTo(Screen.DebugDashboard); scope.launch { drawerState.close() } },
                                onNavigateToMyAccount = { navigateTo(Screen.MyAccount); scope.launch { drawerState.close() } }
                            )
                        }
                    ) {
                        val showBottomBar by remember {
                            derivedStateOf {
                                isLoggedIn && selectedPlan == null && when (currentScreen) {
                                    is Screen.Dashboard, is Screen.Progress, is Screen.BodyOverview,
                                    is Screen.MyPlans, is Screen.BodyModuleHome, is Screen.Nutrition,
                                    is Screen.Community, is Screen.MyAccount, is Screen.FaceModule,
                                    is Screen.HairModule, is Screen.Shop -> true
                                    else -> false
                                }
                            }
                        }

                        Scaffold(
                            topBar = {
                                if (currentScreen is Screen.Dashboard) {
                                    GlobalHeaderBar(
                                        isOnline = isOnline,
                                        onOpenMenu = {
                                            HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN)
                                            scope.launch { drawerState.open() }
                                        },
                                        onProClick = {
                                            errorMessage = null
                                            navViewModel.navigateTo(Screen.ProFeatures)
                                        }
                                    )
                                }
                            },
                            snackbarHost = {
                                SnackbarHost(
                                    hostState = nutritionSnackbarHostState,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                                ) { data ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        shape = RoundedCornerShape(20.dp),
                                        tonalElevation = 6.dp, shadowElevation = 8.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            },
                            bottomBar = {
                                if (showBottomBar) {
                                    AppBottomBar(currentScreen = currentScreen, onSelect = { target ->
                                        selectedPlan = null; navigateTo(target)
                                    })
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.background
                        ) { innerPadding ->
                            val shouldApplyPadding = currentScreen !is Screen.Progress && currentScreen !is Screen.Nutrition && currentScreen !is Screen.Community
                            Box(modifier = Modifier.fillMaxSize().then(if (shouldApplyPadding) Modifier.padding(innerPadding) else Modifier)) {
                                // ============================================================
                                // SCREEN ROUTING — vsak zaslon ima svojo vrstico
                                // ============================================================
                                when {
                                    currentScreen is Screen.LoadingWorkout -> LoadingWorkoutScreen(
                                        onLoadingComplete = {
                                            scope.launch {
                                                isExtraWorkoutSession = false
                                                // replaceTo() namesto navigateTo() — LoadingWorkout NE gre v back-stack,
                                                // Back iz WorkoutSession gre direktno na BodyModuleHome (popTo).
                                                navViewModel.replaceTo(Screen.WorkoutSession)
                                            }
                                        }
                                    )
                                    currentScreen is Screen.BodyModuleHome -> BodyModuleHomeScreen(
                                        onBack = { navigateBack() },
                                        onStartPlan = { navigateTo(Screen.BodyOverview) },
                                        onStartWorkout = { plan ->
                                            if (plan == null) { navigateTo(Screen.BodyOverview); return@BodyModuleHomeScreen }
                                            selectedPlan = plan; navigateTo(Screen.LoadingWorkout)
                                        },
                                        onStartAdditionalWorkout = { navigateTo(Screen.GenerateWorkout) },
                                        currentPlan = plans.maxByOrNull { it.createdAt },
                                        onOpenHistory = { navigateTo(Screen.ExerciseHistory) },
                                        onOpenManualLog = { navigateTo(Screen.ManualExerciseLog) },
                                        onStartRun = { navigateTo(Screen.RunTracker) },
                                        onOpenActivityLog = { navigateTo(Screen.ActivityLog) }
                                    )
                                    currentScreen is Screen.ActivityLog -> ActivityLogScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.WorkoutSession -> {
                                        val frozenPlan = remember(currentScreen) { selectedPlan ?: plans.maxByOrNull { it.createdAt } }
                                        WorkoutSessionScreen(
                                            currentPlan = frozenPlan,
                                            isExtra = isExtraWorkoutSession,
                                            extraFocusAreas = extraFocusAreas,
                                            extraEquipment = extraEquipment,
                                            onBack = {
                                                selectedPlan = null; isExtraWorkoutSession = false
                                                extraFocusAreas = emptyList(); extraEquipment = emptySet()
                                                navViewModel.popTo(Screen.BodyModuleHome)
                                            },
                                            onFinished = {
                                                selectedPlan = null; isExtraWorkoutSession = false
                                                extraFocusAreas = emptyList(); extraEquipment = emptySet()
                                                navViewModel.popTo(Screen.BodyModuleHome)
                                                // Posodobi Plan Day widget takoj po končani vadbi
                                                try { com.example.myapplication.widget.PlanDayWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                                                try { com.example.myapplication.widget.StreakWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                                            },
                                            onXPAdded = { appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail))) },
                                            onBadgeUnlocked = { badge ->
                                                unlockedBadge = badge
                                                appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                                            }
                                        )
                                    }
                                    currentScreen is Screen.FaceModule -> FaceModuleScreen(
                                        onBack = { navigateBack() }
                                    )

                                    currentScreen is Screen.HairModule -> HairModuleScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.Shop -> ShopScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.Progress -> ProgressScreen(
                                        openWeightInput = showWeightInputDialog,
                                        userProfile = userProfile,
                                        isOnline = isOnline,
                                        onOpenMenu = {
                                            HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN)
                                            scope.launch { drawerState.open() }
                                        },
                                        onProClick = {
                                            errorMessage = null
                                            navViewModel.navigateTo(Screen.ProFeatures)
                                        }
                                    )
                                    currentScreen is Screen.Nutrition -> {
                                        NutritionScreen(
                                            plan = plans.maxByOrNull { it.createdAt },
                                            onScanBarcode = { openBarcodeScan = false; navigateTo(Screen.BarcodeScanner) },
                                            scannedProduct = scannedProduct,
                                            onProductConsumed = { scannedProduct = null },
                                            openBarcodeScan = openBarcodeScan,
                                            openFoodSearch = openFoodSearch,
                                            onXPAdded = { appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail))) },
                                            snackbarHostState = nutritionSnackbarHostState,
                                            userProfile = userProfile,
                                            isOnline = isOnline,
                                            onOpenMenu = {
                                                HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN)
                                                scope.launch { drawerState.open() }
                                            },
                                            onProClick = {
                                                errorMessage = null
                                                navViewModel.navigateTo(Screen.ProFeatures)
                                            },
                                            // ✅ Faza 16.3: E-Additives navigacija
                                            onOpenAdditives = { navigateTo(Screen.EAdditives) }
                                        )
                                    }
                                    currentScreen is Screen.BarcodeScanner -> BarcodeScannerScreen(
                                        onDismiss = { navigateBack() },
                                        onProductScanned = { product, barcode -> scannedProduct = Pair(product, barcode); navigateBack() }
                                    )
                                    // ✅ Faza 16.3: E-Additives zaslon
                                    currentScreen is Screen.EAdditives -> com.example.myapplication.ui.screens.EAdditivesScreen(
                                        onNavigateBack = { navigateBack() }
                                    )

                                    currentScreen is Screen.ExerciseHistory -> ExerciseHistoryScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.ManualExerciseLog -> ManualExerciseLogScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.RunTracker -> RunTrackerScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.GenerateWorkout -> GenerateWorkoutScreen(
                                        onBack = { navigateBack() },
                                        currentPlan = plans.maxByOrNull { it.createdAt },
                                        onSelectWorkout = { generatedWorkout ->
                                            val latestPlan = plans.maxByOrNull { it.createdAt }
                                            if (latestPlan != null) {
                                                isExtraWorkoutSession = true
                                                extraFocusAreas = generatedWorkout.focus.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                                extraEquipment = generatedWorkout.equipment.ifEmpty { setOf("bodyweight") }
                                                selectedPlan = latestPlan
                                                navigateTo(Screen.WorkoutSession)
                                            } else navigateTo(Screen.BodyOverview)
                                        }
                                    )
                                    currentScreen is Screen.Community -> CommunityScreen(
                                        isOnline = isOnline,
                                        onOpenMenu = {
                                            HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN)
                                            scope.launch { drawerState.open() }
                                        },
                                        onProClick = {
                                            errorMessage = null
                                            navViewModel.navigateTo(Screen.ProFeatures)
                                        },
                                        onViewProfile = { userId -> navigateTo(Screen.PublicProfile(userId)) }
                                    )
                                    currentScreen is Screen.BodyOverview -> BodyOverviewScreen(
                                        plans = plans,
                                        onCreateNewPlan = { navigateTo(Screen.BodyModule) },
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.Dashboard -> {
                                        com.example.myapplication.ui.screens.DashboardScreen(
                                            userEmail = userEmail,
                                            onLogout = {
                                                com.google.firebase.ktx.Firebase.auth.signOut()
                                                googleSignInClient.signOut()
                                                com.example.myapplication.persistence.FirestoreHelper.clearCache()
                                                appViewModel.handleIntent(AppIntent.SetProfile(com.example.myapplication.data.UserProfile(email = "")))
                                                isLoggedIn = false
                                                navViewModel.clearStack()
                                            },
                                            onModuleClick = { module ->
                                                when(module) {
                                                    "Body" -> navigateTo(Screen.BodyModuleHome)
                                                    "Face" -> navigateTo(Screen.FaceModule)
                                                    "Hair" -> navigateTo(Screen.HairModule)
                                                    "Shop" -> navigateTo(Screen.Shop)
                                                }
                                            },
                                            onAccountClick = { navigateTo(Screen.MyAccount) },
                                            onProClick = { navigateTo(Screen.ProFeatures) },
                                            onOpenMenu = {
                                                com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.DRAWER_OPEN)
                                                scope.launch { drawerState.open() }
                                            },
                                            showLocalHeader = false
                                        )
                                    }
                                    currentScreen is Screen.ProFeatures -> ProFeaturesScreen(
                                        onFreeTrial = {},
                                        onContinue = { if (isLoggedIn) { errorMessage = null; navigateTo(Screen.ProSubscription) } },
                                        onBack = { errorMessage = null; navigateBack() },
                                        errorMessage = errorMessage
                                    )
                                    currentScreen is Screen.BodyModule -> BodyPlanQuizScreen(
                                        onBack = { navigateBack() },
                                        onQuizDataCollected = { quizData ->
                                            @Suppress("UNCHECKED_CAST")
                                            val limitationsList = quizData["limitations"] as? List<String> ?: emptyList()
                                            Log.d("MainActivity", "onQuizDataCollected: height=${quizData["height"]}")
                                            val newHeight = (quizData["height"] as? Number)?.toDouble() ?: (quizData["height"] as? String)?.toDoubleOrNull()
                                            val newAge = (quizData["age"] as? Number)?.toInt() ?: (quizData["age"] as? String)?.toIntOrNull()
                                            val newGender = quizData["gender"] as? String
                                            val updatedFromQuiz = userProfile.copy(
                                                height = newHeight, age = newAge, gender = newGender,
                                                activityLevel = quizData["frequency"] as? String,
                                                experience = quizData["experience"] as? String,
                                                bodyFat = quizData["bodyFat"] as? String,
                                                workoutGoal = quizData["goal"] as? String ?: "",
                                                limitations = limitationsList,
                                                nutritionStyle = quizData["nutrition"] as? String,
                                                sleepHours = quizData["sleep"] as? String
                                            )
                                            appViewModel.handleIntent(AppIntent.SetProfile(updatedFromQuiz))
                                            UserProfileManager.saveProfile(updatedFromQuiz)
                                            scope.launch { UserProfileManager.saveProfileFirestore(updatedFromQuiz) }

                                            val freqStr = quizData["frequency"] as? String
                                            val freqParsed = freqStr?.replace("x", "")?.replace("X", "")?.trim()?.toIntOrNull()
                                            if (freqParsed != null && freqParsed > 0) {
                                                context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                                    .edit().putInt("weekly_target", freqParsed).apply()
                                                Log.d("MainActivity", "weekly_target set from quiz: $freqParsed")
                                            }
                                        },
                                        onFinish = { plan ->
                                            scope.launch {
                                                if (plans.isNotEmpty()) plans.forEach { PlanDataStore.deletePlan(context, it.id) }
                                                PlanDataStore.addPlan(context, plan)
                                                var currentProfile = userProfile
                                                if (currentProfile.height == null || currentProfile.height == 0.0)
                                                    currentProfile = UserProfileManager.loadProfile(userEmail)
                                                val finalProfile = currentProfile.copy(equipment = plan.equipment, focusAreas = plan.focusAreas)
                                                UserProfileManager.saveProfileFirestore(finalProfile)
                                                UserProfileManager.saveProfile(finalProfile)
                                                appViewModel.handleIntent(AppIntent.SetProfile(finalProfile))
                                                val useCase = com.example.myapplication.domain.gamification.GamificationProvider.provide(context)
                                                useCase.recordPlanCreation()
                                                appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                                                val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                                                if (uid != null) {
                                                    val nutritionPlan = com.example.myapplication.data.NutritionPlan(
                                                        calories = plan.calories, protein = plan.protein,
                                                        carbs = plan.carbs, fat = plan.fat,
                                                        algorithmData = plan.algorithmData,
                                                        lastUpdated = System.currentTimeMillis()
                                                    )
                                                    com.example.myapplication.persistence.NutritionPlanStore.saveNutritionPlan(uid, nutritionPlan)
                                                }
                                                val freqFromPlan = plan.trainingDays.takeIf { it > 0 }
                                                val actParsed = userProfile.activityLevel?.replace("x", "")?.replace("X", "")?.trim()?.toIntOrNull()
                                                val weeklyTargetToSave = actParsed?.takeIf { it > 0 } ?: freqFromPlan ?: 3
                                                // ✅ Global Audit (Faza 13.3): NE beremo streak/total/lastEpoch iz bm_prefs (deprecated).
                                                // Ob novem planu posodabljamo SAMO plan-specifична polja: plan_day=1, weekly_target, weekly_done=0.
                                                // streak_days ostane nespremenjen v Firestoreu — user ne izgubi streaka pri zamenjavi plana.
                                                if (userEmail.isNotBlank()) {
                                                    try {
                                                        com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                                                            .set(
                                                                mapOf(
                                                                    "plan_day"      to 1,
                                                                    "weekly_target" to weeklyTargetToSave,
                                                                    "weekly_done"   to 0
                                                                ),
                                                                com.google.firebase.firestore.SetOptions.merge()
                                                            ).await()
                                                        android.util.Log.d("MainActivity", "✅ New plan init: plan_day=1, weekly_target=$weeklyTargetToSave (streak preserved)")
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("MainActivity", "❌ New plan Firestore init failed: ${e.message}")
                                                    }
                                                }
                                                bodyOverviewViewModel.refreshPlans()
                                                navViewModel.navigateTo(Screen.BodyOverview)
                                            }
                                        }
                                    )
                                    currentScreen is Screen.ProSubscription -> ProSubscriptionScreen(
                                        onBack = { navigateBack() },
                                        onSubscribed = { navigateTo(Screen.Dashboard) }
                                    )
                                    currentScreen is Screen.MyPlans -> MyPlansScreen(
                                        plans = plans,
                                        onPlanClick = { plan -> selectedPlan = plan },
                                        onPlanDelete = { plan -> scope.launch { PlanDataStore.deletePlan(context, plan.id) } }
                                    )
                                    currentScreen is Screen.MyAccount -> MyAccountScreen(
                                        userProfile = userProfile,
                                        onNavigateToDevSettings = { navigateTo(Screen.DeveloperSettings) },
                                        onProfileUpdate = { updatedProfile ->
                                            appViewModel.handleIntent(AppIntent.SetProfile(updatedProfile))
                                            UserProfileManager.saveProfile(updatedProfile)
                                            scope.launch { UserProfileManager.saveProfileFirestore(updatedProfile) }
                                        },
                                        onDeleteAllData = {
                                            scope.launch {
                                                try {
                                                    UserProfileManager.deleteUserData(userEmail); PlanDataStore.deleteAllPlans()
                                                    PlanDataStore.clearPlan(context); UserProfileManager.clearAllLocalData()
                                                    bodyOverviewViewModel.clearPlans(); errorMessage = "All data deleted."
                                                } catch (e: Exception) { errorMessage = "Delete failed: ${e.localizedMessage}" }
                                            }
                                        },
                                        onDeleteAccount = {
                                            scope.launch {
                                                try {
                                                    UserProfileManager.deleteUserData(userEmail); PlanDataStore.deleteAllPlans()
                                                    PlanDataStore.clearPlan(context); UserProfileManager.clearAllLocalData()
                                                    com.example.myapplication.persistence.FirestoreHelper.clearCache()
                                                    try { Firebase.auth.currentUser?.delete()?.await() }
                                                    catch (reauth: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                                                        Firebase.auth.signOut()
                                                        errorMessage = "Account data deleted. Please sign in again and retry to fully delete account."
                                                    }
                                                    bodyOverviewViewModel.clearPlans(); isLoggedIn = false; userEmail = ""
                                                    appViewModel.handleIntent(AppIntent.SetProfile(com.example.myapplication.data.UserProfile(email = "")))
                                                    navViewModel.clearStack(); navViewModel.navigateTo(Screen.Index)
                                                    scope.launch { drawerState.close() }
                                                } catch (e: Exception) { errorMessage = "Account deletion failed: ${e.localizedMessage}" }
                                            }
                                        },
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.DeveloperSettings -> DeveloperSettingsScreen(
                                        onBack = { navigateBack() },
                                        userProfile = userProfile,
                                        currentPlan = plans.maxByOrNull { it.createdAt }
                                    )
                                    currentScreen is Screen.DebugDashboard -> com.example.myapplication.ui.screens.DebugDashboardScreen(
                                        onBack = { navigateBack() }
                                    )
                                    currentScreen is Screen.PrivacyPolicy -> PrivacyPolicyScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.TermsOfService -> TermsOfServiceScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.Contact -> ContactScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.About -> AboutScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.Achievements -> {
                                        val prefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                        AchievementsScreen(
                                            userProfile = userProfile, activePlan = plans.firstOrNull(),
                                            currentPlanDay = prefs.getInt("plan_day", 1),
                                            weeklyDone = prefs.getInt("weekly_done", 0),
                                            onRefresh = { bodyOverviewViewModel.refreshPlans() },
                                            onBack = { navigateBack() }
                                        )
                                    }
                                    currentScreen is Screen.PublicProfile -> {
                                        val userId = (currentScreen as Screen.PublicProfile).userId
                                        var profileData by remember { mutableStateOf<com.example.myapplication.data.PublicProfile?>(null) }
                                        LaunchedEffect(userId) { profileData = com.example.myapplication.persistence.ProfileStore.getPublicProfile(userId) }
                                        profileData?.let { PublicProfileScreen(profile = it, onBack = { navigateBack() }) }
                                            ?: Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                                    }
                                     currentScreen is Screen.HealthConnect -> HealthConnectScreen(
                                        onBack = { navigateBack() }
                                    )
                                }

                                // ── Initial Sync Overlay ─��────────────────────────────────────
                                if (isSyncing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.80f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                "Syncing your fitness data…",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Odjavljeni uporabnik
                    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
                        val shouldApplyPadding = currentScreen is Screen.Login
                        Box(modifier = Modifier.then(if (shouldApplyPadding) Modifier.padding(innerPadding) else Modifier)) {
                            when {
                                currentScreen is Screen.Index -> IndexScreen(
                                    onLoginClick = { errorMessage = null; navigateTo(Screen.Login(startInSignUp = false)) },
                                    onSignUpClick = { errorMessage = null; navigateTo(Screen.Login(startInSignUp = true)) },
                                    onViewProFeatures = { errorMessage = null; navigateTo(Screen.ProFeatures) }
                                )
                                currentScreen is Screen.Login -> LoginScreen(
                                    startInSignUp = (currentScreen as Screen.Login).startInSignUp,
                                    onForgotPassword = { email ->
                                        errorMessage = null
                                        if (email.isBlank()) { errorMessage = "Please enter your email."; return@LoginScreen }
                                        Firebase.auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                                            errorMessage = if (task.isSuccessful) "Password reset email sent to $email"
                                            else task.exception?.localizedMessage ?: "Failed to send reset email"
                                        }
                                    },
                                    onLogin = { email, password ->
                                        errorMessage = null
                                        if (email.isBlank()) { errorMessage = "Please enter your email."; return@LoginScreen }
                                        if (password.isBlank()) { errorMessage = "Please enter your password."; return@LoginScreen }
                                        Firebase.auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                val user = Firebase.auth.currentUser
                                                if (user != null && user.isEmailVerified) {
                                                    isLoggedIn = true; userEmail = email
                                                    appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                                                    scope.launch { isDarkMode = UserProfileManager.isDarkMode(userEmail) }
                                                    navViewModel.clearStack(); navViewModel.navigateTo(Screen.Dashboard)
                                                } else if (user != null) { errorMessage = "Please verify your email first!"; Firebase.auth.signOut() }
                                                else errorMessage = "Error: no user."
                                            } else errorMessage = task.exception?.localizedMessage ?: "Login failed."
                                        }
                                    },
                                    onSignup = { email, password, confirmPassword ->
                                        errorMessage = null
                                        if (email.isBlank()) { errorMessage = "Please enter your email."; return@LoginScreen }
                                        if (password.isBlank()) { errorMessage = "Please enter your password."; return@LoginScreen }
                                        if (password != confirmPassword) { errorMessage = "Passwords do not match."; return@LoginScreen }
                                        Firebase.auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                Firebase.auth.currentUser?.sendEmailVerification()
                                                errorMessage = "Sign-up successful! Check your email to verify."
                                                Firebase.auth.signOut()
                                            } else errorMessage = task.exception?.localizedMessage ?: "Sign-up failed."
                                        }
                                    },
                                    onBackToHome = { errorMessage = null; navViewModel.navigateTo(Screen.Index) },
                                    errorMessage = errorMessage,
                                    onGoogleSignInClick = { errorMessage = null; googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                                    onTermsClick = { navigateTo(Screen.TermsOfService) },
                                    onPrivacyClick = { navigateTo(Screen.PrivacyPolicy) }
                                )
                                currentScreen is Screen.ProFeatures -> ProFeaturesScreen(
                                    onFreeTrial = {},
                                    onContinue = { errorMessage = null; navigateTo(Screen.Login()) },
                                    onBack = { errorMessage = null; navigateBack() },
                                    errorMessage = errorMessage
                                )
                                currentScreen is Screen.TermsOfService -> TermsOfServiceScreen(onBack = { navigateBack() })
                                currentScreen is Screen.PrivacyPolicy -> PrivacyPolicyScreen(onBack = { navigateBack() })
                            }
                        }
                    }
                }
            }

            // Badge animacija overlay (prikaže se čez vse)
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
        Firebase.auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) onSuccess()
            else onError(task.exception?.localizedMessage ?: "Google sign-in failed.")
        }
    }
}
