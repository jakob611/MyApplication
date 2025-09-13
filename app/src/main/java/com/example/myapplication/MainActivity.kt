package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.persistence.PlanDataStore
import com.example.myapplication.ui.home.CommunityScreen
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

// Barve (lokalne)
val DrawerDark = Color(0xFF17223B)
val DrawerBlue = Color(0xFF2563EB)
val DrawerYellow = Color(0xFFFEE440)
val DrawerItemBg = Color(0xFF25304A)
val DrawerUnselected = Color(0xFFD7E3F3)

// Screens
sealed class Screen {
    object Progress : Screen()
    object Nutrition : Screen()
    object Community : Screen()
    object FaceModule : Screen()
    object GoldenRatio : Screen()
    object GeneratePlan : Screen()
    object BodyOverview : Screen()
    object Index : Screen()
    object Login : Screen()
    object Dashboard : Screen()
    object Features : Screen()
    object BodyModule : Screen()
    object ProSubscription : Screen()
    object ProFeatures : Screen()
    object MyPlans : Screen()
    object MyAccount : Screen()
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

private fun screenToIndex(screen: Screen): Int = when (screen) {
    Screen.Dashboard -> 0
    Screen.Progress, Screen.BodyOverview, Screen.MyPlans, Screen.BodyModule -> 1
    Screen.Nutrition -> 2
    Screen.Community, Screen.MyAccount -> 3
    else -> 0
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
    val currentIndex = screenToIndex(currentScreen)
    NavigationBar(containerColor = Color.White) {
        bottomItems.forEach { item ->
            val selected = currentIndex == item.index
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(onSelectIndex(item.index)) },
                icon = {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.label,
                        tint = Color.Unspecified
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = Color.Black,
                    unselectedIconColor = Color(0xFF666666),
                    unselectedTextColor = Color(0xFF666666),
                    indicatorColor = Color(0x14000000)
                )
            )
        }
    }
}


class MainActivity : ComponentActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            val context = LocalContext.current
            val bodyOverviewViewModel: BodyOverviewViewmodel =
                viewModel(factory = MyViewModelFactory(context))
            val plans by bodyOverviewViewModel.plans.collectAsState()

            var currentScreen by remember { mutableStateOf<Screen>(Screen.Index) }
            var previousScreen by remember { mutableStateOf<Screen>(Screen.Index) }
            var isLoggedIn by remember { mutableStateOf(false) }
            var userEmail by remember { mutableStateOf("user@email.com") }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var selectedPlan by remember { mutableStateOf<PlanResult?>(null) }

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

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
                    currentScreen = Screen.Dashboard
                    PlanDataStore.migrateLocalPlansToFirestore(context)
                    bodyOverviewViewModel.refreshPlans()
                }
            }

            BackHandler {
                when {
                    currentScreen is Screen.GoldenRatio -> currentScreen = Screen.FaceModule
                    currentScreen is Screen.FaceModule -> currentScreen = Screen.Dashboard
                    selectedPlan != null -> selectedPlan = null
                    currentScreen is Screen.ProFeatures -> {
                        errorMessage = null
                        currentScreen = previousScreen
                    }
                    currentScreen is Screen.Login -> {
                        errorMessage = null
                        currentScreen = Screen.Index
                    }
                    currentScreen is Screen.ProSubscription -> {
                        errorMessage = null
                        currentScreen = Screen.ProFeatures
                    }
                    currentScreen is Screen.Features -> currentScreen = Screen.Index
                    currentScreen is Screen.BodyModule -> currentScreen = Screen.Dashboard
                    currentScreen is Screen.BodyOverview -> currentScreen = Screen.Dashboard
                    currentScreen is Screen.MyPlans -> currentScreen = Screen.Dashboard
                    currentScreen is Screen.MyAccount -> currentScreen = Screen.Dashboard
                    currentScreen is Screen.Dashboard -> {
                        if (!isLoggedIn) currentScreen = Screen.Index else finish()
                    }
                    else -> finish()
                }
            }

            MyApplicationTheme {

                if (isLoggedIn) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            FigmaDrawerContent(
                                userEmail = userEmail,
                                onClose = { scope.launch { drawerState.close() } },
                                onLogout = {
                                    bodyOverviewViewModel.clearPlans()
                                    Firebase.auth.signOut()
                                    isLoggedIn = false
                                    userEmail = ""
                                    currentScreen = Screen.Index
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    ) {
                        // ...
                        val showBottomBar = isLoggedIn &&
                                selectedPlan == null &&
                                when (currentScreen) {
                                    is Screen.Dashboard,
                                    is Screen.Progress,
                                    is Screen.BodyOverview,
                                    is Screen.MyPlans,
                                    is Screen.Nutrition,
                                    is Screen.Community,
                                    is Screen.MyAccount,
                                        // Dodano:
                                    is Screen.FaceModule,
                                        // Po želji pusti prikazan bar tudi med analizo zlate reze:
                                    is Screen.GoldenRatio -> true
                                    else -> false
                                }
// ...
                        Scaffold(
                            // KLJUČNO: globalni top bar JE vedno viden na vseh screenih
                            topBar = {
                                // Prikaži topBar povsod, razen na ProFeaturesScreen
                                if (currentScreen !is Screen.Index && currentScreen !is Screen.ProFeatures) {
                                    GlobalHeaderBar(
                                        onOpenMenu = { scope.launch { drawerState.open() } },
                                        onProClick = {
                                            errorMessage = null
                                            previousScreen = currentScreen
                                            currentScreen = Screen.ProFeatures
                                        }
                                    )
                                }
                            },
                            bottomBar = {
                                if (showBottomBar) {
                                    AppBottomBar(
                                        currentScreen = currentScreen,
                                        onSelect = { target ->
                                            selectedPlan = null
                                            currentScreen = target
                                        }
                                    )
                                }
                            },
                            containerColor = DrawerDark
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                when {
                                    currentScreen is Screen.FaceModule -> FaceModuleScreen(
                                        onBack = { currentScreen = Screen.Dashboard },
                                        onGoldenRatioClick = { currentScreen = Screen.GoldenRatio }
                                    )
                                    currentScreen is Screen.GoldenRatio -> GoldenRatioScreen(
                                        onBack = { currentScreen = Screen.FaceModule }
                                    )
                                    currentScreen is Screen.Progress -> ProgressScreen()
                                    currentScreen is Screen.Nutrition -> {
                                        val currentPlan = plans.maxByOrNull { it.createdAt }
                                        NutritionScreen(
                                            plan = currentPlan
                                        )
                                    }
                                    currentScreen is Screen.Community -> CommunityScreen()
                                    currentScreen is Screen.BodyOverview -> BodyOverviewScreen(
                                        plans = plans,
                                        onCreateNewPlan = { currentScreen = Screen.BodyModule }
                                    )
                                    currentScreen is Screen.GeneratePlan -> GeneratePlanScreen()
                                    selectedPlan != null -> PlanReportScreen(
                                        plan = selectedPlan!!,
                                        onBack = { selectedPlan = null }
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
                                                "Body" -> currentScreen = Screen.BodyOverview
                                                "Hair" -> { /* future */ }
                                                "Face" -> currentScreen = Screen.FaceModule
                                            }
                                        },
                                        onAccountClick = { currentScreen = Screen.MyAccount },
                                        onProClick = {
                                            errorMessage = null
                                            previousScreen = Screen.Dashboard
                                            currentScreen = Screen.ProFeatures
                                        },
                                        onOpenMenu = { scope.launch { drawerState.open() } },
                                        showLocalHeader = false // skrij lokalni header, ker je globalni top bar povsod
                                    )
                                    currentScreen is Screen.ProFeatures -> ProFeaturesScreen(
                                        onFreeTrial = { /* akcija ob kliku na free trial gumb */ },
                                        onContinue = {
                                            if (isLoggedIn) {
                                                errorMessage = null
                                                currentScreen = Screen.ProSubscription
                                            }
                                        },
                                        onBack = {
                                            errorMessage = null
                                            currentScreen = previousScreen
                                        },
                                        errorMessage = errorMessage
                                    )
                                    currentScreen is Screen.Features -> FeaturesScreen(
                                        onBack = { currentScreen = Screen.Index }
                                    )
                                    currentScreen is Screen.BodyModule -> BodyPlanQuizScreen(
                                        onBack = { currentScreen = Screen.Dashboard },
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
                                                // 3) Osveži in pojdi na pregled
                                                bodyOverviewViewModel.refreshPlans()
                                                currentScreen = Screen.BodyOverview
                                            }
                                        }
                                    )
                                    currentScreen is Screen.ProSubscription -> ProSubscriptionScreen(
                                        onBack = { currentScreen = Screen.ProFeatures },
                                        onSubscribed = { currentScreen = Screen.Dashboard }
                                    )
                                    currentScreen is Screen.MyPlans -> MyPlansScreen(
                                        plans = plans,
                                        onPlanClick = { plan -> selectedPlan = plan },
                                        onPlanDelete = { plan ->
                                            scope.launch { PlanDataStore.deletePlan(context, plan.id) }
                                        }
                                    )
                                    currentScreen is Screen.MyAccount -> MyAccountScreen(userEmail = userEmail)
                                }
                            }
                        }
                    }
                } else {
                    // Logged out – globalni top bar je prav tako viden
                    Scaffold(
                        topBar = {
                            GlobalHeaderBar(
                                onOpenMenu = { /* ni drawerja v logout toku */ },
                                onProClick = {
                                    errorMessage = null
                                    previousScreen = Screen.Index
                                    currentScreen = Screen.ProFeatures
                                }
                            )
                        },
                        containerColor = DrawerDark
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when {
                                currentScreen is Screen.Index -> IndexScreen(
                                    onLoginClick = {
                                        errorMessage = null
                                        currentScreen = Screen.Login
                                    },
                                    onViewProFeatures = {
                                        errorMessage = null
                                        previousScreen = Screen.Index
                                        currentScreen = Screen.ProFeatures
                                    }
                                )
                                currentScreen is Screen.Login -> LoginScreen(
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
                                )
                                currentScreen is Screen.ProFeatures -> ProFeaturesScreen(
                                    onFreeTrial = { /* akcija ob kliku na free trial gumb */ },
                                    onContinue = {
                                        errorMessage = null
                                        currentScreen = Screen.Login
                                    },
                                    onBack = {
                                        errorMessage = null
                                        currentScreen = previousScreen
                                    },
                                    errorMessage = errorMessage
                                )
                            }
                        }
                    }
                }
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
    val PrimaryBlue = Color(0xFF2563EB)

    Surface(color = Color(0xFFF3F4F6)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElevatedCard(
                onClick = onOpenMenu,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = PrimaryBlue),
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
                    containerColor = PrimaryBlue,
                    labelColor = Color.White
                )
            )
        }
    }
}

/* ===== Figma-style Drawer (EN) – ostane nespremenjen ===== */
@Composable
private fun FigmaDrawerContent(
    userEmail: String,
    onClose: () -> Unit,
    onLogout: () -> Unit,
) {
    val PrimaryBlue = Color(0xFF2563EB)
    val SheetBg = Color(0xFFE9EAEC)

    Surface(
        color = SheetBg,
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onClose) { Text("← Back") }
                Spacer(Modifier.weight(1f))
            }

            Surface(
                color = PrimaryBlue,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .padding(vertical = 8.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "User’s\nStickman",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            ExpandableSection(title = "Personal data", initiallyExpanded = true) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 12.dp)) {
                    LabeledValue("Username", "XXXXXX")
                    LabeledValue("Email", userEmail.ifBlank { "Unknown" })
                    LabeledValue("Name", "Xxx, Xxxxx, Xxxxx, Xxxxxxxx")
                    LabeledValue("Surname", "Xxxx xxx")
                    LabeledValue("Address", "Xxxx xxx xxXxXxXX")
                }
            }

            ExpandableSection(title = "Profile status") {
                Column(modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 12.dp)) {
                    LabeledValue("Level", "Xxx")
                    LabeledValue("Followers", "XXX")
                    LabeledValue("Badges", "XXX, Xxxxx, Xxxxx, Xxxxxxxxx")
                }
            }

            ExpandableSection(title = "Settings") {
                Column(modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 12.dp)) {
                    LabeledValue("Theme", "Light / Dark")
                    TextButton(onClick = onLogout, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Log out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    LabeledValue("Privacy", "XXXXXX")
                    LabeledValue("Notifications", "XXXXXX")
                    LabeledValue("Two-factor auth", "XXXXXXXXXXXX")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrow-rotate")

    Surface(
        color = Color.Transparent,
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = Color.Black
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black
                )
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(text = "$label:", fontWeight = FontWeight.SemiBold, color = Color.Black)
        Text(text = value, color = Color.Black)
        Spacer(Modifier.height(2.dp))
    }
}