package com.example.myapplication.viewmodels

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.UserProfile
import com.example.myapplication.persistence.FirestoreHelper
import com.example.myapplication.utils.AppToast
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.example.myapplication.data.settings.UserProfileManager

data class ShopState(
    val userXP: Int = 0,
    val streakFreezes: Int = 0,
    val coupons: List<String> = emptyList(),
    val isLoading: Boolean = false
)

class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ShopState())
    val state: StateFlow<ShopState> = _state

    private val context = app.applicationContext
    private val currentUserEmail: String
        get() = Firebase.auth.currentUser?.email ?: ""

    init {
        refreshData()
    }

    fun refreshData() {
        val email = currentUserEmail
        if (email.isBlank()) return

        viewModelScope.launch {
            if (email.isNotBlank()) {
                val profile = UserProfileManager.loadProfileFromFirestore(email)
                    ?: UserProfileManager.loadProfile(email)
                _state.value = _state.value.copy(
                    userXP = profile.xp,
                    streakFreezes = profile.streakFreezes
                )
            }
        }
    }

    /**
     * Nakup Streak Freeze — zaščiteno pred double-spend z Firestore transakcijo.
     * Transakcija atomarno prebere xp + streak_freezes, preveri pogoje in posodobi oba.
     */
    fun buyStreakFreeze() {
        val email = currentUserEmail
        if (email.isBlank()) return
        if (_state.value.isLoading) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val PRICE = 300
                val MAX_FREEZES = 3

                val db = FirestoreHelper.getDb()
                val userRef = FirestoreHelper.getCurrentUserDocRef()

                var purchaseResult = "PENDING"
                var finalXP = 0
                var finalFreezes = 0

                // Firestore transakcija prepreči double-spend ob hitrem kliku
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    val currentXP      = snapshot.getLong("xp")?.toInt()            ?: 0
                    val currentFreezes = snapshot.getLong("streak_freezes")?.toInt() ?: 0

                    when {
                        currentFreezes >= MAX_FREEZES -> purchaseResult = "MAX_FREEZES"
                        currentXP < PRICE             -> purchaseResult = "NOT_ENOUGH_XP"
                        else -> {
                            val newXP      = currentXP - PRICE
                            val newFreezes = currentFreezes + 1
                            val newLevel   = UserProfile.calculateLevel(newXP)

                            // Atomarno posodobi xp, level in streak_freezes
                            transaction.set(userRef, mapOf(
                                "xp"             to newXP,
                                "level"          to newLevel,
                                "streak_freezes" to newFreezes
                            ), SetOptions.merge())

                            // XP history log (znotraj iste transakcije)
                            val logRef = userRef.collection("xp_history").document()
                            transaction.set(logRef, mapOf(
                                "xp"          to -PRICE,
                                "source"      to "SHOP_SPEND",
                                "description" to "Bought Streak Freeze",
                                "timestamp"   to System.currentTimeMillis()
                            ))

                            finalXP      = newXP
                            finalFreezes = newFreezes
                            purchaseResult = "SUCCESS"
                        }
                    }
                }.await()

                withContext(Dispatchers.Main) {
                    when (purchaseResult) {
                        "MAX_FREEZES"   -> com.example.myapplication.utils.AppToast.showWarning(context, "Max 3 Streak Freezes allowed!")
                        "NOT_ENOUGH_XP" -> com.example.myapplication.utils.AppToast.showError(context, "Not enough XP! Need $PRICE XP.")
                        "SUCCESS"       -> com.example.myapplication.utils.AppToast.showSuccess(context, "Streak Freeze Purchased!")
                    }
                }

                if (purchaseResult == "SUCCESS") {
                    _state.value = _state.value.copy(userXP = finalXP, streakFreezes = finalFreezes)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    com.example.myapplication.utils.AppToast.showError(context, "Purchase failed: ${e.message}")
                }
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Nakup kupona — zaščiteno pred double-spend z Firestore transakcijo.
     */
    fun buyCoupon() {
        val email = currentUserEmail
        if (email.isBlank() || _state.value.isLoading) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val PRICE = 500

                val db = FirestoreHelper.getDb()
                val userRef = FirestoreHelper.getCurrentUserDocRef()

                var purchaseResult = "PENDING"
                var finalXP = 0

                // Firestore transakcija — atomarno preveri XP in deduktiraj
                db.runTransaction { transaction ->
                    val snapshot   = transaction.get(userRef)
                    val currentXP  = snapshot.getLong("xp")?.toInt() ?: 0

                    if (currentXP < PRICE) {
                        purchaseResult = "NOT_ENOUGH_XP"
                        return@runTransaction
                    }

                    val newXP    = currentXP - PRICE
                    val newLevel = UserProfile.calculateLevel(newXP)

                    transaction.set(userRef, mapOf(
                        "xp"    to newXP,
                        "level" to newLevel
                    ), SetOptions.merge())

                    val logRef = userRef.collection("xp_history").document()
                    transaction.set(logRef, mapOf(
                        "xp"          to -PRICE,
                        "source"      to "SHOP_SPEND",
                        "description" to "Bought Coupon PRO10",
                        "timestamp"   to System.currentTimeMillis()
                    ))

                    finalXP = newXP
                    purchaseResult = "SUCCESS"
                }.await()

                withContext(Dispatchers.Main) {
                    when (purchaseResult) {
                        "NOT_ENOUGH_XP" -> com.example.myapplication.utils.AppToast.showError(context, "Not enough XP! Need $PRICE XP.")
                        "SUCCESS"       -> com.example.myapplication.utils.AppToast.showSuccess(context, "Coupon 'PRO10' Unlocked!")
                    }
                }

                if (purchaseResult == "SUCCESS") {
                    _state.value = _state.value.copy(userXP = finalXP)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    com.example.myapplication.utils.AppToast.showError(context, "Purchase failed: ${e.message}")
                }
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}

