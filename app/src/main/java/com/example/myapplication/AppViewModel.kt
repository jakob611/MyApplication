package com.example.myapplication

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.UserProfile
import com.example.myapplication.data.settings.UserProfileManager
import com.example.myapplication.domain.profile.ObserveUserProfileUseCase
import com.example.myapplication.data.profile.FirestoreUserProfileRepository
import com.example.myapplication.persistence.FirestoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AppIntent {
    data class StartListening(val email: String) : AppIntent()
    data class SetProfile(val profile: UserProfile) : AppIntent()
}

/**
 * AppViewModel — MVI vmesnik.
 * Drži stanje preko StateFlow in sprejema akcije preko handleIntent.
 * Vsebuje tudi InitialSyncManager logiko (prestavljena iz MainActivity).
 */
class AppViewModel(
    private val observeUserProfileUseCase: ObserveUserProfileUseCase = ObserveUserProfileUseCase(
        FirestoreUserProfileRepository()
    )
) : ViewModel() {

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    // ── InitialSyncManager state ──────────────────────────────────────────
    /** false = sinhronizacija v teku → prikaži overlay čez cel zaslon */
    private val _isProfileReady = MutableStateFlow(false)
    val isProfileReady: StateFlow<Boolean> = _isProfileReady.asStateFlow()

    /** true = sync je aktivno v teku — hard guard proti ponovnemu vstopu */
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Besedilo prikazano v sync overlayu */
    private val _syncStatusMessage = MutableStateFlow("Syncing your fitness data…")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    private var isListening = false
    // Prepreči dvojni klic startInitialSync
    private var isSyncStarted = false

    fun handleIntent(intent: AppIntent) {
        when (intent) {
            is AppIntent.SetProfile -> {
                _userProfile.value = intent.profile
            }
            is AppIntent.StartListening -> {
                if (!isListening) {
                    isListening = true
                    viewModelScope.launch {
                        observeUserProfileUseCase(intent.email).collect { profile ->
                            _userProfile.value = profile
                        }
                    }
                }
            }
        }
    }

    /**
     * InitialSyncManager — sproži se takoj ob LoginState.
     * Preverja ali je to nova naprava (sync_prefs) in izvede:
     *   - Nova naprava: vzporeden fetch profila + planov + teže (intenzivni sync)
     *   - Znana naprava: samo profil (Firestore cache topel)
     * Ko je sync končan, nastavi isProfileReady = true → overlay izgine.
     */
    fun startInitialSync(context: Context, userEmail: String) {
        // Double guard: isSyncStarted (session-lifetime) + _isSyncing (coroutine-lifetime)
        if (isSyncStarted) return
        if (_isSyncing.value) return
        isSyncStarted = true
        _isSyncing.value = true

        viewModelScope.launch {
            try {
                val initialSyncUid = FirestoreHelper.getCurrentUserDocId()
                val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val needsInitialSync = initialSyncUid != null &&
                    !syncPrefs.getBoolean("initial_sync_done_$initialSyncUid", false)

                if (needsInitialSync) {
                    _syncStatusMessage.value = "Downloading your fitness profile (XP, Plans & Progress)…"
                }

                withContext(Dispatchers.IO) {
                    if (needsInitialSync && initialSyncUid != null) {
                        // ── Intenzivni sync ob novi napravi ──────────────────────────────
                        val db = FirestoreHelper.getDb()
                        val userRef = FirestoreHelper.getCurrentUserDocRef()

                        val profileDeferred = async {
                            try { UserProfileManager.loadProfileFromFirestore(userEmail) }
                            catch (_: Exception) { null }
                        }
                        val plansDeferred = async {
                            try { db.collection("user_plans").document(initialSyncUid).get().await() }
                            catch (_: Exception) { null }
                        }
                        val weightDeferred = async {
                            try {
                                userRef?.collection("weightLogs")
                                    ?.orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                    ?.limit(10)?.get()?.await()
                            } catch (_: Exception) { null }
                        }

                        val remote = profileDeferred.await()
                        plansDeferred.await()   // segreje Firestore cache za plane
                        weightDeferred.await()  // segreje Firestore cache za teže

                        if (remote != null) {
                            UserProfileManager.saveProfile(remote)
                            val actParsed = remote.activityLevel?.replace("x", "")?.toIntOrNull()
                            if (actParsed != null && actParsed > 0) {
                                context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                    .edit().putInt("weekly_target", actParsed).apply()
                            }
                            withContext(Dispatchers.Main) { _userProfile.value = remote }
                        }

                        syncPrefs.edit().putBoolean("initial_sync_done_$initialSyncUid", true).apply()
                        // PII varnost: UID se NE izpisuje v log
                        Log.i("AppViewModel", "✅ InitialSync končan")

                        withContext(Dispatchers.Main) {
                            _syncStatusMessage.value = "Profile Ready! ✓"
                        }
                        delay(1500)

                    } else {
                        // ── Normalni zagon: samo profil (Firestore cache že topel) ──────
                        val remote = UserProfileManager.loadProfileFromFirestore(userEmail)
                        if (remote != null) {
                            UserProfileManager.saveProfile(remote)
                            val actParsed = remote.activityLevel?.replace("x", "")?.toIntOrNull()
                            if (actParsed != null && actParsed > 0) {
                                context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                                    .edit().putInt("weekly_target", actParsed).apply()
                            }
                            withContext(Dispatchers.Main) { _userProfile.value = remote }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "InitialSync napaka: ${e.message}")
                // Kritična Firestore napaka (npr. PERMISSION_DENIED) — sporoči uporabniku
                // Opomba: context se prenese prek startInitialSync parametra, kar je varno
                // ker je lifecycle vezana na Activity in je AppViewModel Application-scoped.
                com.example.myapplication.utils.FirestoreErrorHandler.handle(
                    context, e, "AppViewModel.startInitialSync"
                )
            } finally {
                _isSyncing.value = false
                _isProfileReady.value = true
            }
        }
    }

    /** Ponastavi sync stanje ob odjavi — naslednji login bo izvedel svež sync. */
    fun resetSyncState() {
        isSyncStarted = false
        _isSyncing.value = false
        _isProfileReady.value = false
        _syncStatusMessage.value = "Syncing your fitness data…"
    }
}
