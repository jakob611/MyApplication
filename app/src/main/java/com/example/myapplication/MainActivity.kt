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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.UserPreferences
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
import java.time.LocalDate

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
            com.example.myapplication.data.AdvancedExerciseRepository.init(applicationContext)
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
                Log.i("AppPerf", "🚀 First paint arrived at: $timeToPaint ms")
            }

            val context = LocalContext.current
            val bodyOverviewViewModel: BodyOverviewViewmodel =
                viewModel(factory = MyViewModelFactory())
            val plans: List<com.example.myapplication.data.PlanResult> by bodyOverviewViewModel.plans.collectAsState()

            val appViewModel: AppViewModel =
                viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application))
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

            // ----- Widget deep-link pending stanja -----
            var pendingNavigateToNutrition by remember { mutableStateOf(false) }
            var pendingOpenBarcodeScan by remember { mutableStateOf(false) }
            var pendingOpenFoodSearch by remember { mutableStateOf(false) }

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            // Notification Permission for Android 13+
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    Log.d("MainActivity", "Notification permission granted: $isGranted")
                }
            )

            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!hasPerm) {
                        delay(500) // Wait for activity to settle
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // ----- Google Sign-In launcher -----
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
                            appViewModel.setProfile(UserPreferences.loadProfile(context, userEmail))
                            scope.launch { isDarkMode = UserPreferences.isDarkMode(userEmail) }
                            navViewModel.clearStack()
                            navViewModel.navigateTo(Screen.Dashboard)
                        },
                        onError = { errorMessage = it }
                    )
                } catch (e: Exception) {
                    errorMessage = "Google sign-in failed: ${e.localizedMessage}"
                }
            }

            // ----- Auth check ob zagonu -----
            LaunchedEffect(Unit) {
                val user = Firebase.auth.currentUser
                if (user != null && (user.isEmailVerified || user.providerData.any { it.providerId == "google.com" })) {
                    isLoggedIn = true
                    userEmail = user.email ?: ""

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

                    appViewModel.setProfile(UserPreferences.loadProfile(context, userEmail))
                    isDarkMode = UserPreferences.isDarkMode(userEmail)
                    navViewModel.navigateTo(if (pendingNavigateToNutrition) Screen.Nutrition else Screen.Dashboard)

                    scope.launch(Dispatchers.IO) {
                        try { com.example.myapplication.persistence.AchievementStore.recordLoginOnly(context, userEmail) }
                        catch (e: Exception) { Log.e("MainActivity", "Login streak error: ${e.message}") }
                    }
                    scope.launch(Dispatchers.IO) {
                        try {
                            val profile = UserPreferences.loadProfile(context, userEmail)
                            com.example.myapplication.workers.WeeklyStreakWorker.ensureScheduled(context, profile.startOfWeek)
                        } catch (e: Exception) { Log.e("MainActivity", "WeeklyStreakWorker error: ${e.message}") }
                    }

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
                                withContext(Dispatchers.Main) { appViewModel.setProfile(remote) }
                            }
                        } catch (_: Exception) {}
                    }
                    scope.launch(Dispatchers.IO) {
                        delay(1500)
                        try { com.example.myapplication.persistence.AchievementStore.checkAndSyncBadgesOnStartup(context, userEmail) }
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
                    appViewModel.startListening(userEmail)
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
                        is Screen.GoldenRatio, is Screen.HairModule, is Screen.Shop,
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
                                    bodyOverviewViewModel.clearPlans()
                                    com.example.myapplication.persistence.FirestoreHelper.clearCache()
                                    Firebase.auth.signOut()
                                    isLoggedIn = false; userEmail = ""
                                    appViewModel.setProfile(com.example.myapplication.data.UserProfile(email = ""))
                                    navViewModel.clearStack(); navViewModel.navigateTo(Screen.Index)
                                    scope.launch { drawerState.close() }
                                },
                                                onProfileUpdate = { updatedProfile ->
                                                    appViewModel.setProfile(updatedProfile)
                                                    UserPreferences.saveProfile(context, updatedProfile)
                                                    scope.launch { UserPreferences.saveProfileFirestore(updatedProfile) }
                                                },
                                                isDarkMode = isDarkMode,
                                onDarkModeToggle = {
                                    isDarkMode = !isDarkMode
                                    scope.launch { UserPreferences.setDarkMode(userEmail, isDarkMode) }
                                },
                                onNavigateToPrivacyPolicy = { navigateTo(Screen.PrivacyPolicy); scope.launch { drawerState.close() } },
                                onNavigateToTermsOfService = { navigateTo(Screen.TermsOfService); scope.launch { drawerState.close() } },
                                onNavigateToContact = { navigateTo(Screen.Contact); scope.launch { drawerState.close() } },
                                onNavigateToAbout = { navigateTo(Screen.About); scope.launch { drawerState.close() } },
                                onNavigateToLevelPath = { navigateTo(Screen.Achievements); scope.launch { drawerState.close() } },
                                onNavigateToBadges = { navigateTo(Screen.Achievements); scope.launch { drawerState.close() } },
                                onNavigateToHealthConnect = { navigateTo(Screen.HealthConnect); scope.launch { drawerState.close() } },
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
                                    is Screen.GoldenRatio, is Screen.HairModule, is Screen.Shop -> true
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
                                // Za dodajanje novega zaslona: dodaj Screen objekt v AppNavigation.kt
                                // in dodaj when blok tukaj.
                                // ============================================================
                                when {
                                    currentScreen is Screen.LoadingWorkout -> LoadingWorkoutScreen(
                                        onLoadingComplete = {
                                            scope.launch {
                                                try {
                                                    // Preveri v lokalnih prefs ali je bila vadba danes že opravljena
                                                    val bmPrefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                                    val lastWorkoutEpoch = bmPrefs.getLong("last_workout_epoch", 0L)
                                                    val todayEpoch = LocalDate.now().toEpochDay()
                                                    val workoutDoneToday = lastWorkoutEpoch == todayEpoch
                                                    if (workoutDoneToday) navigateTo(Screen.GenerateWorkout)
                                                    else { isExtraWorkoutSession = false; navigateTo(Screen.WorkoutSession) }
                                                } catch (_: Exception) { isExtraWorkoutSession = false; navigateTo(Screen.WorkoutSession) }
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
                                        onOpenActivityLog = { navigateTo(Screen.ActivityLog) },
                                        onOpenShop = { navigateTo(Screen.Shop) }
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
                                                navViewModel.navigateTo(Screen.BodyModuleHome)
                                            },
                                            onFinished = {
                                                selectedPlan = null; isExtraWorkoutSession = false
                                                extraFocusAreas = emptyList(); extraEquipment = emptySet()
                                                navViewModel.navigateTo(Screen.BodyModuleHome)
                                                // Posodobi Plan Day widget takoj po končani vadbi
                                                try { com.example.myapplication.widget.PlanDayWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                                                try { com.example.myapplication.widget.StreakWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                                            },
                                            onXPAdded = { appViewModel.setProfile(UserPreferences.loadProfile(context, userEmail)) },
                                            onBadgeUnlocked = { badge ->
                                                unlockedBadge = badge
                                                appViewModel.setProfile(UserPreferences.loadProfile(context, userEmail))
                                            }
                                        )
                                    }
                                    currentScreen is Screen.FaceModule -> FaceModuleScreen(
                                        onBack = { navigateBack() },
                                        onGoldenRatioClick = { navigateTo(Screen.GoldenRatio) }
                                    )
                                    currentScreen is Screen.GoldenRatio -> GoldenRatioScreen(onBack = { navigateBack() })
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
                                            onOpenEAdditives = { navigateTo(Screen.EAdditives) },
                                            scannedProduct = scannedProduct,
                                            onProductConsumed = { scannedProduct = null },
                                            openBarcodeScan = openBarcodeScan,
                                            openFoodSearch = openFoodSearch,
                                            onXPAdded = { appViewModel.setProfile(UserPreferences.loadProfile(context, userEmail)) },
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
                                            }
                                        )
                                    }
                                    currentScreen is Screen.BarcodeScanner -> BarcodeScannerScreen(
                                        onDismiss = { navigateBack() },
                                        onProductScanned = { product, barcode -> scannedProduct = Pair(product, barcode); navigateBack() }
                                    )
                                    currentScreen is Screen.EAdditives -> EAdditivesScreen(onNavigateBack = { navigateBack() })
                                    currentScreen is Screen.ExerciseHistory -> ExerciseHistoryScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.ManualExerciseLog -> ManualExerciseLogScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.RunTracker -> RunTrackerScreen(
                                        onBackPressed = { navigateBack() },
                                        userProfile = userProfile
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
                                    currentScreen is Screen.Dashboard -> DashboardScreen(
                                        userEmail = userEmail,
                                        onLogout = { Firebase.auth.signOut(); isLoggedIn = false; userEmail = ""; navViewModel.navigateTo(Screen.Index) },
                                        onModuleClick = { moduleTitle ->
                                            when (moduleTitle) {
                                                "Body" -> navigateTo(Screen.BodyModuleHome)
                                                "Face" -> navigateTo(Screen.FaceModule)
                                                "Hair" -> navigateTo(Screen.HairModule)
                                                "Shop" -> navigateTo(Screen.Shop)
                                            }
                                        },
                                        onAccountClick = { navigateTo(Screen.MyAccount) },
                                        onProClick = { errorMessage = null; navigateTo(Screen.ProFeatures) },
                                        onOpenMenu = {
                                            HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN)
                                            scope.launch { drawerState.open() }
                                        },
                                        showLocalHeader = false
                                    )
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
                                            appViewModel.setProfile(updatedFromQuiz)
                                            UserPreferences.saveProfile(context, updatedFromQuiz)
                                            val freqStr = quizData["frequency"] as? String
                                            val freqParsed = freqStr?.replace("x", "")?.replace("X", "")?.trim()?.toIntOrNull()
                                            if (freqParsed != null && freqParsed > 0) {
                                                context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                                    .edit().putInt("weekly_target", freqParsed).apply()
                                                Log.d("MainActivity", "weekly_target set from quiz: $freqParsed")
                                            }
                                            scope.launch {
                                                try {
                                                    val bioRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                                                    val bioData = mapOf(
                                                        "height" to (newHeight ?: 0.0), "age" to (newAge ?: 0),
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
                                                } catch (e: Exception) { Log.e("MainActivity", "Firestore biometric save error", e) }
                                            }
                                        },
                                        onFinish = { plan ->
                                            scope.launch {
                                                if (plans.isNotEmpty()) plans.forEach { PlanDataStore.deletePlan(context, it.id) }
                                                PlanDataStore.addPlan(context, plan)
                                                var currentProfile = userProfile
                                                if (currentProfile.height == null || currentProfile.height == 0.0)
                                                    currentProfile = UserPreferences.loadProfile(context, userEmail)
                                                val finalProfile = currentProfile.copy(equipment = plan.equipment, focusAreas = plan.focusAreas)
                                                UserPreferences.saveProfileFirestore(finalProfile)
                                                UserPreferences.saveProfile(context, finalProfile)
                                                appViewModel.setProfile(finalProfile)
                                                com.example.myapplication.persistence.AchievementStore.recordPlanCreation(context, userEmail)
                                                appViewModel.setProfile(UserPreferences.loadProfile(context, userEmail))
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
                                                val bodyPrefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                                // Vzemi frequency direktno iz plana — ne iz userProfile ki se morda ni pravilno naložil
                                                val freqFromPlan = plan.trainingDays.takeIf { it > 0 }
                                                val actParsed = userProfile.activityLevel?.replace("x", "")?.replace("X", "")?.trim()?.toIntOrNull()
                                                val weeklyTargetToSave = actParsed?.takeIf { it > 0 } ?: freqFromPlan ?: 3
                                                bodyPrefs.edit().putInt("weekly_target", weeklyTargetToSave).apply()
                                                // Shrani v Firestore POD EMAIL (ne UID) — ker getWorkoutStats bere users/{email}
                                                if (userEmail.isNotBlank()) {
                                                    com.example.myapplication.data.UserPreferences.saveWorkoutStats(
                                                        email = userEmail,
                                                        streak = bodyPrefs.getInt("streak_days", 0),
                                                        totalWorkouts = bodyPrefs.getInt("total_workouts_completed", 0),
                                                        weeklyDone = bodyPrefs.getInt("weekly_done", 0),
                                                        lastWorkoutEpoch = bodyPrefs.getLong("last_workout_epoch", 0L),
                                                        planDay = bodyPrefs.getInt("plan_day", 1),
                                                        weeklyTarget = weeklyTargetToSave
                                                    )
                                                    android.util.Log.d("MainActivity", "✅ weekly_target saved to Firestore users/$userEmail: $weeklyTargetToSave")
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
                                            appViewModel.setProfile(updatedProfile)
                                            UserPreferences.saveProfile(context, updatedProfile)
                                            scope.launch { UserPreferences.saveProfileFirestore(updatedProfile) }
                                        },
                                        onDeleteAllData = {
                                            scope.launch {
                                                try {
                                                    UserPreferences.deleteUserData(userEmail); PlanDataStore.deleteAllPlans()
                                                    PlanDataStore.clearPlan(context); UserPreferences.clearAllLocalData(context)
                                                    bodyOverviewViewModel.clearPlans(); errorMessage = "All data deleted."
                                                } catch (e: Exception) { errorMessage = "Delete failed: ${e.localizedMessage}" }
                                            }
                                        },
                                        onDeleteAccount = {
                                            scope.launch {
                                                try {
                                                    UserPreferences.deleteUserData(userEmail); PlanDataStore.deleteAllPlans()
                                                    PlanDataStore.clearPlan(context); UserPreferences.clearAllLocalData(context)
                                                    com.example.myapplication.persistence.FirestoreHelper.clearCache()
                                                    try { Firebase.auth.currentUser?.delete()?.await() }
                                                    catch (reauth: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                                                        Firebase.auth.signOut()
                                                        errorMessage = "Account data deleted. Please sign in again and retry to fully delete account."
                                                    }
                                                    bodyOverviewViewModel.clearPlans(); isLoggedIn = false; userEmail = ""
                                                    appViewModel.setProfile(com.example.myapplication.data.UserProfile(email = ""))
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
                                    currentScreen is Screen.PrivacyPolicy -> PrivacyPolicyScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.TermsOfService -> TermsOfServiceScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.Contact -> ContactScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.About -> AboutScreen(onBack = { navigateBack() })
                                    currentScreen is Screen.LevelPath -> {
                                        val prefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                        LevelPathScreen(userProfile = userProfile, activePlan = plans.firstOrNull(), currentPlanDay = prefs.getInt("plan_day", 1), onBack = { navigateBack() })
                                    }
                                    currentScreen is Screen.BadgesScreen -> BadgesScreenContent(
                                        userProfile = userProfile,
                                        onBack = { navigateBack() }
                                    )
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
                                    currentScreen is Screen.HealthConnect -> HealthConnectScreen(onBack = { navigateBack() })
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
                                                    appViewModel.setProfile(UserPreferences.loadProfile(context, userEmail))
                                                    scope.launch { isDarkMode = UserPreferences.isDarkMode(userEmail) }
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
