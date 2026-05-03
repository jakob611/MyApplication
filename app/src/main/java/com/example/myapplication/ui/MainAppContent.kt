@file:Suppress("DEPRECATION")
package com.example.myapplication.ui

// =====================================================================
// MainAppContent.kt — Vstopna točka za celotno UI kompozicijo.
// Ekstrakcija iz MainActivity: vsa Composable logika tukaj.
// MainActivity ostane samo: onCreate, setContent, firebaseAuthWithGoogle.
// =====================================================================

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.*
import com.example.myapplication.data.PlanResult
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.settings.UserProfileManager
import com.example.myapplication.network.OpenFoodFactsProduct
import com.example.myapplication.persistence.PlanDataStore
import com.example.myapplication.ui.home.CommunityScreen
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.utils.HapticFeedback
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * MainAppContent — koren Composable. Prejme ViewModel-e iz MainActivity.
 * Vsebuje: auth stanje, screen routing, Drawer, BottomBar, sync overlay, badge animacija.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    appViewModel: AppViewModel,
    navViewModel: NavigationViewModel,
    initialDarkMode: Boolean,
    intentExtras: State<Bundle?>,
    coldStartEpochMs: Long,
    googleSignInClient: GoogleSignInClient,
    onFirebaseGoogleAuth: (
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onFinishActivity: () -> Unit = {}
) {
    val context = LocalContext.current

    val bodyOverviewViewModel: BodyOverviewViewmodel = viewModel(factory = MyViewModelFactory())
    val plans: List<PlanResult> by bodyOverviewViewModel.plans.collectAsState()
    val userProfile by appViewModel.userProfile.collectAsState()
    val nutritionViewModel: com.example.myapplication.viewmodels.NutritionViewModel =
        viewModel(factory = MyViewModelFactory(context))
    val networkObserver = remember { com.example.myapplication.utils.NetworkObserver(context) }
    val isOnline by networkObserver.observe().collectAsState(initial = true)
    val currentScreen by navViewModel.currentScreen.collectAsState()

    var isCheckingAuth by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var userEmail by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDarkMode by remember { mutableStateOf(initialDarkMode) }

    var selectedPlan by remember { mutableStateOf<PlanResult?>(null) }
    var isExtraWorkoutSession by remember { mutableStateOf(false) }
    var extraFocusAreas by remember { mutableStateOf<List<String>>(emptyList()) }
    var extraEquipment by remember { mutableStateOf<Set<String>>(emptySet()) }

    var scannedProduct by remember { mutableStateOf<Pair<OpenFoodFactsProduct, String>?>(null) }
    val nutritionSnackbarHostState = remember { SnackbarHostState() }
    var unlockedBadge by remember { mutableStateOf<com.example.myapplication.data.Badge?>(null) }
    var showWeightInputDialog by remember { mutableStateOf(false) }
    var openBarcodeScan by remember { mutableStateOf(false) }
    var openFoodSearch by remember { mutableStateOf(false) }
    var pendingNavigateToNutrition by remember { mutableStateOf(false) }
    var pendingOpenBarcodeScan by remember { mutableStateOf(false) }
    var pendingOpenFoodSearch by remember { mutableStateOf(false) }
    var pendingNavigateToBodyModule by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val isProfileReady by appViewModel.isProfileReady.collectAsState()
    val syncStatusMessage by appViewModel.syncStatusMessage.collectAsState()

    // ── Google sign-in launcher ──────────────────────────────────────────────
    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val acct = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            onFirebaseGoogleAuth(acct, {
                val user = Firebase.auth.currentUser
                if (user?.email != null) {
                    isLoggedIn = true; userEmail = user.email!!
                    appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                    scope.launch {
                        val dark = UserProfileManager.isDarkMode(userEmail)
                        isDarkMode = dark
                        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("dark_mode", dark).apply()
                    }
                    navViewModel.clearStack(); navViewModel.navigateTo(Screen.Dashboard)
                } else errorMessage = "Google sign-in error."
            }, { err -> errorMessage = err })
        } catch (e: Exception) { errorMessage = "Google sign-in failed: ${e.localizedMessage}" }
    }

    // ── Sync + Auth listeners ────────────────────────────────────────────────
    LaunchedEffect(isProfileReady) { if (isProfileReady) bodyOverviewViewModel.refreshPlans() }

    DisposableEffect(Unit) {
        val a = com.google.firebase.auth.FirebaseAuth.getInstance()
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { fa ->
            val u = fa.currentUser
            if (u != null && (u.isEmailVerified || u.providerData.any { it.providerId == "google.com" }))
                scope.launch { appViewModel.startInitialSync(context, u.email ?: "") }
            else appViewModel.resetSyncState()
        }
        a.addAuthStateListener(listener)
        onDispose { a.removeAuthStateListener(listener) }
    }

    // ── Startup init ─────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        Log.i("AppPerf", "First paint: ${android.os.SystemClock.elapsedRealtime() - coldStartEpochMs} ms")
        val appFlags = context.getSharedPreferences("app_flags", Context.MODE_PRIVATE)
        if (appFlags.getBoolean("fresh_start_on_login", false)) {
            context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("algorithm_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            appFlags.edit().remove("fresh_start_on_login").apply()
        }
        val migPrefs = context.getSharedPreferences("migration_flags", Context.MODE_PRIVATE)
        if (!migPrefs.getBoolean("faza5_legacy_purge_done", false)) {
            com.example.myapplication.persistence.DailySyncManager.clearLegacyCache(context)
            migPrefs.edit().putBoolean("faza5_legacy_purge_done", true).apply()
        }
        val user = Firebase.auth.currentUser
        if (user != null && (user.isEmailVerified || user.providerData.any { it.providerId == "google.com" })) {
            userEmail = user.email ?: ""; isLoggedIn = true
            appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
            val firestoreDark = UserProfileManager.isDarkMode(userEmail)
            isDarkMode = firestoreDark
            context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("dark_mode", firestoreDark).apply()
            navViewModel.navigateTo(if (pendingNavigateToNutrition) Screen.Nutrition else Screen.Dashboard)
            appViewModel.handleIntent(AppIntent.StartListening(userEmail))
            scope.launch(Dispatchers.IO) {
                try {
                    val remote = UserProfileManager.loadProfileFromFirestore(userEmail)
                    if (remote != null) {
                        UserProfileManager.saveProfile(remote)
                        remote.activityLevel?.replace("x","")?.toIntOrNull()?.takeIf { it > 0 }?.let { parsed ->
                            context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE).edit().putInt("weekly_target", parsed).apply()
                        }
                        withContext(Dispatchers.Main) { appViewModel.handleIntent(AppIntent.SetProfile(remote)) }
                    }
                } catch (_: Exception) {}
            }
            scope.launch(Dispatchers.IO) {
                try { com.example.myapplication.domain.gamification.GamificationProvider.provide(context).recordLoginOnly() }
                catch (e: Exception) { Log.e("MainApp", "Login streak: ${e.message}") }
            }
            scope.launch(Dispatchers.IO) {
                try { com.example.myapplication.workers.WeeklyStreakWorker.ensureScheduled(context, UserProfileManager.loadProfile(userEmail).startOfWeek) }
                catch (e: Exception) { Log.e("MainApp", "WeeklyStreakWorker: ${e.message}") }
            }
            try { com.example.myapplication.worker.RunRouteCleanupWorker.ensureScheduled(context) } catch (_: Exception) {}
            scope.launch(Dispatchers.IO) {
                delay(1500)
                try { com.example.myapplication.domain.gamification.GamificationProvider.provide(context).checkAndSyncBadgesOnStartup() }
                catch (e: Exception) { Log.e("MainApp", "Badge sync: ${e.message}") }
            }
            scope.launch {
                try { PlanDataStore.migrateLocalPlansToFirestore(context) } catch (_: Exception) {}
                try { com.example.myapplication.widget.StreakWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                try { com.example.myapplication.widget.PlanDayWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()?.let {
                    try { com.example.myapplication.persistence.DailySyncManager.syncOnAppOpen(context, it) } catch (_: Exception) {}
                }
            }
        } else navViewModel.navigateTo(Screen.Index)
        isCheckingAuth = false
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            try {
                com.example.myapplication.workers.StreakReminderWorker.createNotificationChannel(context)
                com.example.myapplication.workers.StreakReminderWorker.scheduleForToday(context)
            } catch (e: Exception) { Log.e("MainApp", "StreakReminder: ${e.message}") }
        }
    }

    // ── Widget intent handling ────────────────────────────────────────────────
    LaunchedEffect(intentExtras.value) {
        val extras = intentExtras.value ?: return@LaunchedEffect
        if (extras.getBoolean("OPEN_WEIGHT_INPUT", false)) { showWeightInputDialog = true; if (isLoggedIn) navViewModel.navigateTo(Screen.Progress) }
        if (extras.getString("NAVIGATE_TO") == "nutrition") {
            val wantsScan = extras.getBoolean("OPEN_BARCODE_SCAN", false); val wantsSearch = extras.getBoolean("OPEN_FOOD_SEARCH", false)
            if (isLoggedIn && !isCheckingAuth) { navViewModel.navigateTo(Screen.Nutrition); delay(150); if (wantsScan) openBarcodeScan = true; if (wantsSearch) openFoodSearch = true }
            else { pendingNavigateToNutrition = true; pendingOpenBarcodeScan = wantsScan; pendingOpenFoodSearch = wantsSearch }
        }
        if (extras.getString("NAVIGATE_TO") == "body_module") {
            if (isLoggedIn && !isCheckingAuth) navViewModel.navigateTo(Screen.BodyModuleHome) else pendingNavigateToBodyModule = true
        }
    }
    LaunchedEffect(isCheckingAuth, isLoggedIn, pendingNavigateToBodyModule) {
        if (!isCheckingAuth && isLoggedIn && pendingNavigateToBodyModule) { navViewModel.navigateTo(Screen.BodyModuleHome); pendingNavigateToBodyModule = false }
    }
    LaunchedEffect(isCheckingAuth, isLoggedIn, pendingNavigateToNutrition, pendingOpenBarcodeScan, pendingOpenFoodSearch) {
        if (!isCheckingAuth && isLoggedIn && pendingNavigateToNutrition) {
            navViewModel.navigateTo(Screen.Nutrition); delay(150)
            if (pendingOpenBarcodeScan) openBarcodeScan = true; if (pendingOpenFoodSearch) openFoodSearch = true
            pendingNavigateToNutrition = false; pendingOpenBarcodeScan = false; pendingOpenFoodSearch = false
        }
    }

    // ── Navigacijski pomočniki ────────────────────────────────────────────────
    fun navigateTo(screen: Screen) = navViewModel.navigateTo(screen)
    fun navigateBack() = navViewModel.navigateBack(
        isLoggedIn = isLoggedIn, hasSelectedPlan = selectedPlan != null,
        onClearSelectedPlan = { selectedPlan = null }, onClearError = { errorMessage = null },
        onFinish = onFinishActivity
    )
    fun performLogout() {
        AuthRepository.signOut(context)
        appViewModel.handleIntent(AppIntent.SetProfile(com.example.myapplication.data.UserProfile(email = "")))
        appViewModel.resetSyncState(); nutritionViewModel.clearUser()
        isLoggedIn = false; navViewModel.clearStack()
    }

    // ── Theme + BackHandler ───────────────────────────────────────────────────
    val shouldUseDarkMode = if (!isLoggedIn && currentScreen is Screen.Index) true else isDarkMode
    MyApplicationTheme(darkTheme = shouldUseDarkMode) {
        BackHandler { navigateBack() }

        when {
            isCheckingAuth -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
            }
            isLoggedIn -> {
                // ── Logged-in UI: Drawer + Scaffold + Screen routing ──────────
                val enableDrawerGestures = when (currentScreen) {
                    is Screen.Dashboard, is Screen.Progress, is Screen.BodyModuleHome,
                    is Screen.BodyOverview, is Screen.MyPlans, is Screen.FaceModule,
                    is Screen.HairModule, is Screen.Shop, is Screen.Community,
                    is Screen.MyAccount, is Screen.ExerciseHistory,
                    is Screen.ManualExerciseLog, is Screen.Nutrition -> true
                    else -> currentScreen !is Screen.RunTracker && currentScreen !is Screen.ActivityLog
                }

                ModalNavigationDrawer(
                    drawerState = drawerState, gesturesEnabled = enableDrawerGestures,
                    drawerContent = {
                        FigmaDrawerContent(
                            userProfile = userProfile,
                            onClose = { scope.launch { drawerState.close() } },
                            onLogout = ::performLogout,
                            onProfileUpdate = { p ->
                                appViewModel.handleIntent(AppIntent.SetProfile(p))
                                UserProfileManager.saveProfile(p)
                                scope.launch { UserProfileManager.saveProfileFirestore(p) }
                            },
                            isDarkMode = isDarkMode,
                            onDarkModeToggle = {
                                isDarkMode = !isDarkMode
                                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().putBoolean("dark_mode", isDarkMode).apply()
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
                    val showBottomBar by remember { derivedStateOf {
                        selectedPlan == null && when (currentScreen) {
                            is Screen.Dashboard, is Screen.Progress, is Screen.BodyOverview,
                            is Screen.MyPlans, is Screen.BodyModuleHome, is Screen.Nutrition,
                            is Screen.Community, is Screen.MyAccount, is Screen.FaceModule,
                            is Screen.HairModule, is Screen.Shop -> true
                            else -> false
                        }
                    }}
                    Scaffold(
                        topBar = {
                            if (currentScreen is Screen.Dashboard)
                                GlobalHeaderBar(isOnline = isOnline,
                                    onOpenMenu = { HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN); scope.launch { drawerState.open() } },
                                    onProClick = { errorMessage = null; navViewModel.navigateTo(Screen.ProFeatures) })
                        },
                        snackbarHost = {
                            SnackbarHost(hostState = nutritionSnackbarHostState,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                            ) { data ->
                                Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
                                    shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp, shadowElevation = 8.dp,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        },
                        bottomBar = {
                            if (showBottomBar) AppBottomBar(currentScreen = currentScreen, onSelect = { target -> selectedPlan = null; navigateTo(target) })
                        },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        val shouldApplyPadding = currentScreen !is Screen.Progress && currentScreen !is Screen.Nutrition && currentScreen !is Screen.Community
                        Box(Modifier.fillMaxSize().then(if (shouldApplyPadding) Modifier.padding(innerPadding) else Modifier)) {
                            // ── Screen Routing ────────────────────────────────
                            when {
                                currentScreen is Screen.LoadingWorkout -> LoadingWorkoutScreen(onLoadingComplete = {
                                    scope.launch { isExtraWorkoutSession = false; navViewModel.replaceTo(Screen.WorkoutSession) }
                                })
                                currentScreen is Screen.BodyModuleHome -> BodyModuleHomeScreen(
                                    onBack = ::navigateBack, onStartPlan = { navigateTo(Screen.BodyOverview) },
                                    onStartWorkout = { plan -> if (plan == null) { navigateTo(Screen.BodyOverview); return@BodyModuleHomeScreen }; selectedPlan = plan; navigateTo(Screen.LoadingWorkout) },
                                    onStartAdditionalWorkout = { navigateTo(Screen.GenerateWorkout) },
                                    currentPlan = plans.maxByOrNull { it.createdAt },
                                    onOpenHistory = { navigateTo(Screen.ExerciseHistory) },
                                    onOpenManualLog = { navigateTo(Screen.ManualExerciseLog) },
                                    onStartRun = { navigateTo(Screen.RunTracker) },
                                    onOpenActivityLog = { navigateTo(Screen.ActivityLog) }
                                )
                                currentScreen is Screen.ActivityLog -> ActivityLogScreen(onBack = ::navigateBack)
                                currentScreen is Screen.WorkoutSession -> {
                                    val frozenPlan = remember(currentScreen) { selectedPlan ?: plans.maxByOrNull { it.createdAt } }
                                    WorkoutSessionScreen(
                                        currentPlan = frozenPlan, isExtra = isExtraWorkoutSession,
                                        extraFocusAreas = extraFocusAreas, extraEquipment = extraEquipment,
                                        onBack = { selectedPlan = null; isExtraWorkoutSession = false; extraFocusAreas = emptyList(); extraEquipment = emptySet(); navViewModel.popTo(Screen.BodyModuleHome) },
                                        onFinished = {
                                            selectedPlan = null; isExtraWorkoutSession = false; extraFocusAreas = emptyList(); extraEquipment = emptySet()
                                            navViewModel.popTo(Screen.BodyModuleHome)
                                            try { com.example.myapplication.widget.PlanDayWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                                            try { com.example.myapplication.widget.StreakWidgetProvider.refreshAll(context) } catch (_: Exception) {}
                                        },
                                        onXPAdded = { appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail))) },
                                        onBadgeUnlocked = { badge -> unlockedBadge = badge; appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail))) }
                                    )
                                }
                                currentScreen is Screen.FaceModule -> FaceModuleScreen(
                                    onBack = ::navigateBack,
                                    onGoldenRatio = { navigateTo(Screen.GoldenRatio) }
                                )
                                currentScreen is Screen.GoldenRatio -> GoldenRatioScreen(onBack = ::navigateBack)
                                currentScreen is Screen.HairModule -> HairModuleScreen(onBack = ::navigateBack)
                                currentScreen is Screen.Shop -> ShopScreen(onBack = ::navigateBack)
                                currentScreen is Screen.Progress -> ProgressScreen(
                                    openWeightInput = showWeightInputDialog, userProfile = userProfile, isOnline = isOnline,
                                    onOpenMenu = { HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN); scope.launch { drawerState.open() } },
                                    onProClick = { errorMessage = null; navViewModel.navigateTo(Screen.ProFeatures) }
                                )
                                currentScreen is Screen.Nutrition -> NutritionScreen(
                                    plan = plans.maxByOrNull { it.createdAt },
                                    onScanBarcode = { openBarcodeScan = false; navigateTo(Screen.BarcodeScanner) },
                                    scannedProduct = scannedProduct,
                                    onProductConsumed = { scannedProduct = null },
                                    openBarcodeScan = openBarcodeScan, openFoodSearch = openFoodSearch,
                                    onXPAdded = { appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail))) },
                                    snackbarHostState = nutritionSnackbarHostState,
                                    userProfile = userProfile, isOnline = isOnline,
                                    onOpenMenu = { HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN); scope.launch { drawerState.open() } },
                                    onProClick = { errorMessage = null; navViewModel.navigateTo(Screen.ProFeatures) },
                                    onOpenAdditives = { navigateTo(Screen.EAdditives) }
                                )
                                currentScreen is Screen.BarcodeScanner -> BarcodeScannerScreen(
                                    onDismiss = ::navigateBack,
                                    onProductScanned = { product, barcode -> scannedProduct = Pair(product, barcode); navigateBack() }
                                )
                                currentScreen is Screen.EAdditives -> EAdditivesScreen(onNavigateBack = ::navigateBack)
                                currentScreen is Screen.ExerciseHistory -> ExerciseHistoryScreen(onBack = ::navigateBack)
                                currentScreen is Screen.ManualExerciseLog -> ManualExerciseLogScreen(onBack = ::navigateBack)
                                currentScreen is Screen.RunTracker -> RunTrackerScreen(onBack = ::navigateBack)
                                currentScreen is Screen.GenerateWorkout -> GenerateWorkoutScreen(
                                    onBack = ::navigateBack, currentPlan = plans.maxByOrNull { it.createdAt },
                                    onSelectWorkout = { gen ->
                                        val latestPlan = plans.maxByOrNull { it.createdAt }
                                        if (latestPlan != null) {
                                            isExtraWorkoutSession = true
                                            extraFocusAreas = gen.focus.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                            extraEquipment = gen.equipment.ifEmpty { setOf("bodyweight") }
                                            selectedPlan = latestPlan; navigateTo(Screen.WorkoutSession)
                                        } else navigateTo(Screen.BodyOverview)
                                    }
                                )
                                currentScreen is Screen.Community -> CommunityScreen(
                                    isOnline = isOnline,
                                    onOpenMenu = { HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN); scope.launch { drawerState.open() } },
                                    onProClick = { errorMessage = null; navViewModel.navigateTo(Screen.ProFeatures) },
                                    onViewProfile = { userId -> navigateTo(Screen.PublicProfile(userId)) }
                                )
                                currentScreen is Screen.BodyOverview -> BodyOverviewScreen(
                                    plans = plans, onCreateNewPlan = { navigateTo(Screen.BodyModule) }, onBack = ::navigateBack
                                )
                                currentScreen is Screen.Dashboard -> DashboardScreen(
                                    userEmail = userEmail,
                                    onLogout = ::performLogout,
                                    onModuleClick = { module -> when(module) {
                                        "Body" -> navigateTo(Screen.BodyModuleHome); "Face" -> navigateTo(Screen.FaceModule)
                                        "Hair" -> navigateTo(Screen.HairModule); "Shop" -> navigateTo(Screen.Shop) }
                                    },
                                    onAccountClick = { navigateTo(Screen.MyAccount) },
                                    onProClick = { navigateTo(Screen.ProFeatures) },
                                    onOpenMenu = { HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.DRAWER_OPEN); scope.launch { drawerState.open() } },
                                    showLocalHeader = false
                                )
                                currentScreen is Screen.ProFeatures -> ProFeaturesScreen(
                                    onFreeTrial = {}, onContinue = { if (isLoggedIn) { errorMessage = null; navigateTo(Screen.ProSubscription) } },
                                    onBack = { errorMessage = null; navigateBack() }, errorMessage = errorMessage
                                )
                                currentScreen is Screen.BodyModule -> BodyPlanQuizScreen(
                                    onBack = ::navigateBack,
                                    onQuizDataCollected = { quizData ->
                                        @Suppress("UNCHECKED_CAST")
                                        val limitsList = quizData["limitations"] as? List<String> ?: emptyList()
                                        val updatedFromQuiz = userProfile.copy(
                                            height = (quizData["height"] as? Number)?.toDouble() ?: (quizData["height"] as? String)?.toDoubleOrNull(),
                                            age = (quizData["age"] as? Number)?.toInt() ?: (quizData["age"] as? String)?.toIntOrNull(),
                                            gender = quizData["gender"] as? String,
                                            activityLevel = quizData["frequency"] as? String,
                                            experience = quizData["experience"] as? String,
                                            bodyFat = quizData["bodyFat"] as? String,
                                            workoutGoal = quizData["goal"] as? String ?: "",
                                            limitations = limitsList,
                                            nutritionStyle = quizData["nutrition"] as? String,
                                            sleepHours = quizData["sleep"] as? String
                                        )
                                        appViewModel.handleIntent(AppIntent.SetProfile(updatedFromQuiz))
                                        UserProfileManager.saveProfile(updatedFromQuiz)
                                        scope.launch { UserProfileManager.saveProfileFirestore(updatedFromQuiz) }
                                        (quizData["frequency"] as? String)?.replace("x","")?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.let { freq ->
                                            context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE).edit().putInt("weekly_target", freq).apply()
                                        }
                                    },
                                    onFinish = { plan ->
                                        scope.launch {
                                            if (plans.isNotEmpty()) plans.forEach { PlanDataStore.deletePlan(context, it.id) }
                                            PlanDataStore.addPlan(context, plan)
                                            var currentProfile = userProfile
                                            if (currentProfile.height == null || currentProfile.height == 0.0) currentProfile = UserProfileManager.loadProfile(userEmail)
                                            val finalProfile = currentProfile.copy(equipment = plan.equipment, focusAreas = plan.focusAreas)
                                            UserProfileManager.saveProfileFirestore(finalProfile); UserProfileManager.saveProfile(finalProfile)
                                            appViewModel.handleIntent(AppIntent.SetProfile(finalProfile))
                                            com.example.myapplication.domain.gamification.GamificationProvider.provide(context).recordPlanCreation()
                                            appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                                            com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()?.let { uid ->
                                                val nutritionPlan = com.example.myapplication.data.NutritionPlan(
                                                    calories = plan.calories, protein = plan.protein, carbs = plan.carbs, fat = plan.fat,
                                                    algorithmData = plan.algorithmData, lastUpdated = System.currentTimeMillis()
                                                )
                                                com.example.myapplication.persistence.NutritionPlanStore.saveNutritionPlan(uid, nutritionPlan)
                                            }
                                            val weeklyTargetToSave = userProfile.activityLevel?.replace("x","")?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: plan.trainingDays.takeIf { it > 0 } ?: 3
                                            if (userEmail.isNotBlank()) {
                                                try {
                                                    com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                                                        .set(mapOf("plan_day" to 1, "weekly_target" to weeklyTargetToSave, "weekly_done" to 0),
                                                            com.google.firebase.firestore.SetOptions.merge()).await()
                                                } catch (e: Exception) { Log.e("MainApp", "New plan Firestore init: ${e.message}") }
                                            }
                                            bodyOverviewViewModel.refreshPlans()
                                            navViewModel.navigateTo(Screen.BodyOverview)
                                        }
                                    }
                                )
                                currentScreen is Screen.ProSubscription -> ProSubscriptionScreen(onBack = ::navigateBack, onSubscribed = { navigateTo(Screen.Dashboard) })
                                currentScreen is Screen.MyPlans -> MyPlansScreen(
                                    plans = plans, onPlanClick = { selectedPlan = it },
                                    onPlanDelete = { plan -> scope.launch { PlanDataStore.deletePlan(context, plan.id) } }
                                )
                                currentScreen is Screen.MyAccount -> MyAccountScreen(
                                    userProfile = userProfile,
                                    onNavigateToDevSettings = { navigateTo(Screen.DeveloperSettings) },
                                    onProfileUpdate = { p -> appViewModel.handleIntent(AppIntent.SetProfile(p)); UserProfileManager.saveProfile(p); scope.launch { UserProfileManager.saveProfileFirestore(p) } },
                                    onDeleteAllData = {
                                        scope.launch {
                                            try { UserProfileManager.deleteUserData(userEmail); PlanDataStore.deleteAllPlans(); PlanDataStore.clearPlan(context); UserProfileManager.clearAllLocalData(); bodyOverviewViewModel.clearPlans(); errorMessage = "All data deleted." }
                                            catch (e: Exception) { errorMessage = "Delete failed: ${e.localizedMessage}" }
                                        }
                                    },
                                    onDeleteAccount = {
                                        scope.launch {
                                            try {
                                                UserProfileManager.deleteUserData(userEmail); PlanDataStore.deleteAllPlans(); PlanDataStore.clearPlan(context); UserProfileManager.clearAllLocalData()
                                                com.example.myapplication.persistence.FirestoreHelper.clearCache()
                                                try { Firebase.auth.currentUser?.delete()?.await() }
                                                catch (_: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                                                    Firebase.auth.signOut(); errorMessage = "Account data deleted. Please sign in again and retry."
                                                }
                                                bodyOverviewViewModel.clearPlans(); isLoggedIn = false; userEmail = ""
                                                appViewModel.handleIntent(AppIntent.SetProfile(com.example.myapplication.data.UserProfile(email = "")))
                                                navViewModel.clearStack(); navViewModel.navigateTo(Screen.Index)
                                                scope.launch { drawerState.close() }
                                            } catch (e: Exception) { errorMessage = "Account deletion failed: ${e.localizedMessage}" }
                                        }
                                    },
                                    onBack = ::navigateBack
                                )
                                currentScreen is Screen.DeveloperSettings -> DeveloperSettingsScreen(onBack = ::navigateBack, userProfile = userProfile, currentPlan = plans.maxByOrNull { it.createdAt })
                                currentScreen is Screen.DebugDashboard -> DebugDashboardScreen(onBack = ::navigateBack)
                                currentScreen is Screen.PrivacyPolicy -> PrivacyPolicyScreen(onBack = ::navigateBack)
                                currentScreen is Screen.TermsOfService -> TermsOfServiceScreen(onBack = ::navigateBack)
                                currentScreen is Screen.Contact -> ContactScreen(onBack = ::navigateBack)
                                currentScreen is Screen.About -> AboutScreen(onBack = ::navigateBack)
                                currentScreen is Screen.Achievements -> {
                                    val prefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                    AchievementsScreen(
                                        userProfile = userProfile, activePlan = plans.firstOrNull(),
                                        currentPlanDay = prefs.getInt("plan_day", 1), weeklyDone = prefs.getInt("weekly_done", 0),
                                        onRefresh = { bodyOverviewViewModel.refreshPlans() }, onBack = ::navigateBack
                                    )
                                }
                                currentScreen is Screen.PublicProfile -> {
                                    val userId = (currentScreen as Screen.PublicProfile).userId
                                    var profileData by remember { mutableStateOf<com.example.myapplication.data.PublicProfile?>(null) }
                                    LaunchedEffect(userId) { profileData = com.example.myapplication.persistence.ProfileStore.getPublicProfile(userId) }
                                    profileData?.let { PublicProfileScreen(profile = it, onBack = ::navigateBack) }
                                        ?: Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                                }
                                currentScreen is Screen.HealthConnect -> HealthConnectScreen(onBack = ::navigateBack)
                            }
                            // ── Sync overlay ──────────────────────────────────
                            if (!isProfileReady) {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.80f)), Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(16.dp))
                                        Text(syncStatusMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                // ── Logged-out screen routing ─────────────────────────────────
                Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
                    Box(Modifier.then(if (currentScreen is Screen.Login) Modifier.padding(innerPadding) else Modifier)) {
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
                                    if (email.isBlank()) { errorMessage = context.getString(com.example.myapplication.R.string.error_email_required); return@LoginScreen }
                                    Firebase.auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                                        errorMessage = if (task.isSuccessful) context.getString(com.example.myapplication.R.string.success_password_reset_sent)
                                        else task.exception?.localizedMessage ?: context.getString(com.example.myapplication.R.string.error_password_reset_failed)
                                    }
                                },
                                onLogin = { email, password ->
                                    errorMessage = null
                                    if (email.isBlank()) { errorMessage = context.getString(com.example.myapplication.R.string.error_email_required); return@LoginScreen }
                                    if (password.isBlank()) { errorMessage = context.getString(com.example.myapplication.R.string.error_password_required); return@LoginScreen }
                                    Firebase.auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val u = Firebase.auth.currentUser
                                            if (u != null && u.isEmailVerified) {
                                                isLoggedIn = true; userEmail = email
                                                appViewModel.handleIntent(AppIntent.SetProfile(UserProfileManager.loadProfile(userEmail)))
                                                scope.launch { isDarkMode = UserProfileManager.isDarkMode(userEmail) }
                                                navViewModel.clearStack(); navViewModel.navigateTo(Screen.Dashboard)
                                            } else if (u != null) { errorMessage = context.getString(com.example.myapplication.R.string.error_verify_email_first); Firebase.auth.signOut() }
                                            else errorMessage = context.getString(com.example.myapplication.R.string.error_no_user)
                                        } else errorMessage = task.exception?.localizedMessage ?: context.getString(com.example.myapplication.R.string.error_login_failed)
                                    }
                                },
                                onSignup = { email, password, confirmPassword ->
                                    errorMessage = null
                                    if (email.isBlank()) { errorMessage = context.getString(com.example.myapplication.R.string.error_email_required); return@LoginScreen }
                                    if (password.isBlank()) { errorMessage = context.getString(com.example.myapplication.R.string.error_password_required); return@LoginScreen }
                                    if (password != confirmPassword) { errorMessage = context.getString(com.example.myapplication.R.string.error_passwords_no_match); return@LoginScreen }
                                    Firebase.auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                                        if (task.isSuccessful) { Firebase.auth.currentUser?.sendEmailVerification(); errorMessage = context.getString(com.example.myapplication.R.string.success_signup); Firebase.auth.signOut() }
                                        else errorMessage = task.exception?.localizedMessage ?: context.getString(com.example.myapplication.R.string.error_signup_failed)
                                    }
                                },
                                onBackToHome = { errorMessage = null; navViewModel.navigateTo(Screen.Index) },
                                errorMessage = errorMessage,
                                onGoogleSignInClick = { errorMessage = null; googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                                onTermsClick = { navigateTo(Screen.TermsOfService) },
                                onPrivacyClick = { navigateTo(Screen.PrivacyPolicy) }
                            )
                            currentScreen is Screen.ProFeatures -> ProFeaturesScreen(
                                onFreeTrial = {}, onContinue = { errorMessage = null; navigateTo(Screen.Login()) },
                                onBack = { errorMessage = null; navigateBack() }, errorMessage = errorMessage
                            )
                            currentScreen is Screen.TermsOfService -> TermsOfServiceScreen(onBack = ::navigateBack)
                            currentScreen is Screen.PrivacyPolicy -> PrivacyPolicyScreen(onBack = ::navigateBack)
                        }
                    }
                }
            }
        }

        // Badge animacija overlay
        unlockedBadge?.let { badge ->
            com.example.myapplication.ui.components.BadgeUnlockAnimation(badge = badge, onDismiss = { unlockedBadge = null })
        }
    }
}


