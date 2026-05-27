package com.example.myapplication.domain.usecase

import android.util.Log
import com.example.myapplication.data.store.FirestoreHelper
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Faza 49 — SRP refaktoriranje: Domenski UseCase za popolno brisanje računa.
 *
 * Odgovornost: Atomarno brisanje vseh Firestore podkolekcij, glavnega dokumenta,
 * [user_plans] kolekcije in [follows] relacij prek vseh znanih identifikatorjev
 * (email, UID, resolvedId). Ne briše lokalnih podatkov — to naredi [UserLocalStore.clearAllLocalData].
 *
 * Klicatelji: [ui.MainAppContent] (onDeleteAllData + onDeleteAccount callbacks)
 *
 * Arhitekturna opomba: UseCase je v domenskem paketu ker koordinira domensko operacijo
 * "brisanje računa". Firebase dostop je nujen ker projekt nima AccountRepository vmesnika.
 */
class DeleteAccountUseCase {

    private val subcollections = listOf(
        "weightLogs", "dailyLogs", "dailyMetrics", "daily_health",
        "customMeals", "nutritionPlan", "meal_feedback", "exerciseLogs",
        "workoutSessions", "xp_history", "activePlan", "runSessions"
    )

    /**
     * Izvede popolno brisanje vseh Firestore podatkov za [email].
     *
     * Briše:
     * - vse podkolekcije pod [users/{email}] in [users/{uid}]
     * - [users/{email}] in [users/{uid}] glavna dokumenta
     * - [user_plans/{resolvedId|uid|email}]
     * - [follows] relacije kjer je uporabnik [followerId] ali [followingId]
     */
    suspend fun invoke(email: String) {
        if (email.isBlank()) return
        val uid = Firebase.auth.currentUser?.uid
        val db = FirestoreHelper.getDb()

        try {
            // Briši podkolekcije in dokument pod email-based identifikatorjem
            for (sub in subcollections) {
                try {
                    val docs = db.collection("users").document(email).collection(sub).get().await()
                    for (doc in docs) { doc.reference.delete().await() }
                } catch (_: Exception) {}
            }
            db.collection("users").document(email).delete().await()

            // Briši podkolekcije in dokument pod UID-based identifikatorjem (če se razlikuje)
            if (uid != null && uid != email) {
                for (sub in subcollections) {
                    try {
                        val docs = db.collection("users").document(uid).collection(sub).get().await()
                        for (doc in docs) { doc.reference.delete().await() }
                    } catch (_: Exception) {}
                }
                try { db.collection("users").document(uid).delete().await() } catch (_: Exception) {}
            }

            // Briši user_plans prek vseh znanih identifikatorjev
            val resolvedId = FirestoreHelper.getCurrentUserDocId() ?: email
            for (key in setOf(resolvedId, uid, email).filterNotNull()) {
                try { db.collection("user_plans").document(key).delete().await() } catch (_: Exception) {}
            }

            // Briši follows relacije
            try {
                for (key in setOf(resolvedId, uid, email).filterNotNull()) {
                    val followerDocs = db.collection("follows").whereEqualTo("followerId", key).get().await()
                    for (doc in followerDocs) { doc.reference.delete().await() }
                    val followingDocs = db.collection("follows").whereEqualTo("followingId", key).get().await()
                    for (doc in followingDocs) { doc.reference.delete().await() }
                }
            } catch (_: Exception) {}

        } catch (e: Exception) {
            Log.e("DeleteAccountUseCase", "❌ Error deleting user data", e)
            throw e
        }
    }
}

