package com.example.myapplication

// =====================================================================
// AppNavigation.kt
// Vsebuje: Screen sealed class (vse zaslone aplikacije),
//          BottomBar podatke in AppBottomBar composable.
// Če dodajaš nov zaslon: dodaj objekt v Screen + po potrebi v screenToIndex().
// =====================================================================

import android.util.Log
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.myapplication.utils.HapticFeedback

// ------- Globalna barva (uporablja se v celi aplikaciji) -------
val DrawerBlue = Color(0xFF2563EB)

// ------- Vsi zasloni aplikacije -------
sealed class Screen {
    object Index : Screen()
    data class Login(val startInSignUp: Boolean = false) : Screen()
    object Dashboard : Screen()

    // Telo / vadba
    object BodyModuleHome : Screen()
    object BodyModule : Screen()       // Quiz za kreiranje plana
    object BodyOverview : Screen()     // Pregled plana
    object WorkoutSession : Screen()
    object LoadingWorkout : Screen()
    object GenerateWorkout : Screen()
    object ExerciseHistory : Screen()
    object ManualExerciseLog : Screen()
    object RunTracker : Screen()

    // Napredek
    object Progress : Screen()
    object MyPlans : Screen()
    object Achievements : Screen()
    object LevelPath : Screen()
    object BadgesScreen : Screen()

    // Prehrana
    object Nutrition : Screen()
    object BarcodeScanner : Screen()
    object EAdditives : Screen()

    // Skupnost
    object Community : Screen()
    data class PublicProfile(val userId: String) : Screen()

    // Telo / obraz / lasje
    object FaceModule : Screen()
    object GoldenRatio : Screen()
    object HairModule : Screen()

    // Pro / Shop
    object ProFeatures : Screen()
    object ProSubscription : Screen()
    object Shop : Screen()
    object Features : Screen()

    // Račun
    object MyAccount : Screen()
    object DeveloperSettings : Screen()
    object HealthConnect : Screen()

    // Pravne strani
    object PrivacyPolicy : Screen()
    object TermsOfService : Screen()
    object Contact : Screen()
    object About : Screen()
}

// ------- Spodnja navigacijska vrstica -------

private data class BottomItem(
    val index: Int,
    val label: String,
    val iconRes: Int
)

private val bottomItems = listOf(
    BottomItem(0, "Home",      R.drawable.ic_home),
    BottomItem(1, "Progress",  R.drawable.ic_progress),
    BottomItem(2, "Nutrition", R.drawable.ic_nutrition),
    BottomItem(3, "Community", R.drawable.ic_community),
)

/** Vrne index spodnje vrstice za dani zaslon (0=Home, 1=Progress, 2=Nutrition, 3=Community) */
fun screenToIndex(screen: Screen): Int {
    val index = when (screen) {
        is Screen.Dashboard, is Screen.BodyModule, is Screen.BodyModuleHome,
        is Screen.BodyOverview, is Screen.FaceModule, is Screen.HairModule,
        is Screen.Shop, is Screen.ExerciseHistory, is Screen.RunTracker -> 0
        is Screen.Progress, is Screen.MyPlans -> 1
        is Screen.Nutrition -> 2
        is Screen.Community, is Screen.MyAccount -> 3
        else -> 0
    }
    Log.d("BottomBar", "screenToIndex: screen=$screen, index=$index")
    return index
}

private fun indexToScreen(index: Int): Screen = when (index) {
    0 -> Screen.Dashboard
    1 -> Screen.Progress
    2 -> Screen.Nutrition
    3 -> Screen.Community
    else -> Screen.Dashboard
}

@Composable
fun AppBottomBar(
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
                        HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.LIGHT_CLICK)
                        onSelect(indexToScreen(item.index))
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

