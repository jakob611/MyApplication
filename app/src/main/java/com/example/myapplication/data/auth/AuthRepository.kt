package com.example.myapplication.data.auth

import android.content.Context
import android.util.Log
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth

/**
 * AuthRepository — centralizirana auth/session logika.
 *
 * Odgovoren za:
 * - Preverjanje stanja prijave
 * - Čiščenje seje ob odjavi (Firebase signOut + cache + lokalni prefs)
 * - Enkratni vhod za vse "kdo sem" operacije
 *
 * PREPOVEDANO: navigacija, UI stanje, ViewModel klici.
 * Navigacijo ureja NavigationViewModel, UI stanje ureja AppViewModel.
 */
object AuthRepository {

    private val auth: FirebaseAuth get() = Firebase.auth

    /** Vrni email prijavljenega uporabnika ali null */
    fun getCurrentEmail(): String? = auth.currentUser?.email

    /** Vrni UID prijavljenega uporabnika ali null */
    fun getCurrentUid(): String? = auth.currentUser?.uid

    /** Je uporabnik prijavljen in verificiran? */
    fun isLoggedIn(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified || user.providerData.any { it.providerId == "google.com" }
    }

    /**
     * Odjavi uporabnika in počisti vse lokalne podatke seje.
     * Kliče se iz: AppDrawer.onLogout, MyAccountScreen.onDeleteAccount, DashboardScreen.onLogout.
     *
     * OPOMBA: Poklici `nutritionViewModel.clearUser()` IN `appViewModel.resetSyncState()`
     * Ŝe vedno ostajata v klicateljih — te viewmodel odvisnosti niso del AuthRepository.
     *
     * @param context za clearPreferences klice
     */
    fun signOut(context: Context) {
        try {
            auth.signOut()
            FirestoreHelper.clearCache()
            // FCM token počisti, da ne posajna push notifikacij odjavljenem userju
            context.getSharedPreferences("local_prefs", Context.MODE_PRIVATE)
                .edit().putString("fcm_token", "").apply()
            Log.d("AuthRepository", "✅ Odjava uspela — cache in token počiščena")
        } catch (e: Exception) {
            Log.e("AuthRepository", "❌ Napaka pri odjavi: ${e.message}")
        }
    }

    /**
     * Počisti VSE lokalne podatke (za "Delete all data" funkcijo).
     * Ne odjavlja uporabnika iz Firebase.
     */
    fun clearLocalPreferences(context: Context) {
        listOf("bm_prefs", "algorithm_prefs", "user_prefs", "local_prefs", "sync_prefs").forEach { prefName ->
            try {
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (_: Exception) {}
        }
        Log.d("AuthRepository", "✅ Lokalni prefs počiščeni")
    }
}

