@file:Suppress("DEPRECATION")
package com.example.myapplication

// =====================================================================
// MainActivity.kt — minimalna vstopna točka.
// Vsa Composable logika → ui/MainAppContent.kt
// =====================================================================

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.core.worker.DailySyncWorker
import com.example.myapplication.data.repository.AdvancedExerciseRepository
import com.example.myapplication.ui.MainAppContent
import com.example.myapplication.ui.main.AppViewModel
import com.example.myapplication.ui.navigation.NavigationViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private val intentExtras = mutableStateOf<Bundle?>(null)
    private var initialDarkMode = false
    private var coldStartEpochMs: Long = 0L
    private var isSubscribed = false
    private var paymentStatus: String? = null

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d("MainActivity", "Notification permission granted: $isGranted")
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentExtras.value = intent.extras
    }

    override fun onPause() {
        super.onPause()
        try { DailySyncWorker.schedule(this) } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    @androidx.compose.material3.ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        coldStartEpochMs = android.os.SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)

        // Prepreči bel blisk: nastavi temno ozadje okna PRED setContent
        window.setBackgroundDrawable(ColorDrawable(0xFF181818.toInt()))

        // Sinhrono preberi dark mode PRED setContent → prepreči bel blisk
        initialDarkMode = getSharedPreferences("user_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", false)

        // Prednalaganje exercises.json v ozadju (eager init)
        GlobalScope.launch(Dispatchers.IO) {
            val json = applicationContext.assets.open("exercises.json").bufferedReader().use { it.readText() }
            AdvancedExerciseRepository.init(json)
        }

        intentExtras.value = intent.extras

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail().build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            // Surface z eksplicitnim Background barvo — prepreči privzeto belo Material ozadje
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF181818)
            ) {
                val appViewModel: AppViewModel = viewModel()
                val navViewModel: NavigationViewModel = viewModel()

                MainAppContent(
                    appViewModel = appViewModel,
                    navViewModel = navViewModel,
                    initialDarkMode = initialDarkMode,
                    intentExtras = intentExtras,
                    coldStartEpochMs = coldStartEpochMs,
                    googleSignInClient = googleSignInClient,
                    onFirebaseGoogleAuth = { account, onSuccess, onError ->
                        firebaseAuthWithGoogle(account, onSuccess, onError)
                    },
                    onFinishActivity = { finish() }
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
