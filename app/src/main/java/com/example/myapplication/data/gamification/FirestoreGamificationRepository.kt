package com.example.myapplication.data.gamification

import android.util.Log
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Zlata koda - KMP ready Firestore implementacija za Gamification,
 * ki zamenjuje AchievementStore in rešuje Heisenbug, ker BERE IZ ENOSTEGA VIRA RESNICE (Firestore).
 * NIMA NITI ENE SharedPreferences ODVISNOSTI.
 */
/**
 * Android implementation of GamificationRepository with direct Firestore integration.
 */
class FirestoreGamificationRepository : GamificationRepository {

    private val db get() = FirestoreHelper.getDb()

    private fun getTodayStr(): String {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    }

    private fun getYesterdayStr(): String {
        val yesterdayMillis = Clock.System.now().toEpochMilliseconds() - 86400000L
        return kotlinx.datetime.Instant.fromEpochMilliseconds(yesterdayMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    }

    override suspend fun awardXP(amount: Int, reason: String) {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)

                // Atomarno branje trenutnega XP in izračun novega nivoja — vse znotraj transakcije
                val currentXp = snapshot.getLong("xp")?.toInt() ?: 0
                val newXp = currentXp + amount
                // Level je izračunan znotraj transakcije — atomarno posodabljamo xp IN level hkrati
                val newLevel = UserProfile.calculateLevel(newXp)

                // Posodobi xp + level atomarno (set+merge varno za obstoječ in NOV dokument)
                transaction.set(userRef, mapOf(
                    "xp"    to newXp,
                    "level" to newLevel
                ), SetOptions.merge())

                // Log v podzbirko xp_history (znotraj iste transakcije)
                val logRef = userRef.collection("xp_history").document()
                transaction.set(logRef, mapOf(
                    "amount"    to amount,
                    "reason"    to reason,
                    "date"      to getTodayStr(),
                    "timestamp" to Clock.System.now().toEpochMilliseconds(),
                    "xpAfter"   to newXp,
                    "levelAfter" to newLevel
                ))
            }.await()
            Log.d("GamificationRepo", "✅ Dodeljeno $amount XP za '$reason'")
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Napaka pri beleženju XP-ja", e)
        }
    }

    override suspend fun getCurrentStreak(): Int {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return 0
        try {
            val snapshot = userRef.get().await()
            return snapshot.getLong("streak_days")?.toInt() ?: 0
        } catch (e: Exception) {
            return 0
        }
    }

    override suspend fun updateStreak(isWorkoutSuccess: Boolean) {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return
        val todayStr = getTodayStr()

        try {
            db.runTransaction { transaction ->
                // Skripta sedaj rešuje HEISENBUG preverjanja Double Logs:
                val todayLogRef = userRef.collection("daily_logs").document(todayStr)
                val existingLog = transaction.get(todayLogRef)

                if (existingLog.exists()) {
                    // Że zabeleženo danes, preprečimo "Dvojno dodajanje preko UI vs Workerja"
                    return@runTransaction
                }

                val snapshot = transaction.get(userRef)
                val currentStreak = snapshot.getLong("streak_days")?.toInt() ?: 0

                // Streak SE POVEČA samo kadar je zares Workout success.
                // Za Rest Day uporabnik ne dobi +1 streaka (kot si rekel: "na Rest stagnira").
                val newStreak = if (isWorkoutSuccess) currentStreak + 1 else currentStreak

                // Update Streak in Last Streak Update Date
                transaction.update(userRef, mapOf(
                    "streak_days" to newStreak,
                    "last_streak_update_date" to todayStr
                ))

                // Log success for the day
                transaction.set(todayLogRef, mapOf(
                    "date" to todayStr,
                    "status" to if (isWorkoutSuccess) "WORKOUT_DONE" else "REST_DONE",
                    "timestamp" to Clock.System.now().toEpochMilliseconds()
                ))
            }.await()
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Failed to update streak.", e)
        }
    }

    override suspend fun consumeStreakFreeze(): Boolean {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return false
        return try {
            var freezeConsumed = false
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentFreezes = snapshot.getLong("streak_freezes")?.toInt() ?: 0

                if (currentFreezes > 0) {
                    transaction.update(userRef, "streak_freezes", currentFreezes - 1)
                    freezeConsumed = true
                }
            }.await()
            freezeConsumed
        } catch (e: Exception) {
             false
        }
    }

    override suspend fun runMidnightStreakCheck() {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return
        val yesterdayStr = getYesterdayStr()

        try {
            val yesterdayLogRef = userRef.collection("daily_logs").document(yesterdayStr)
            val logSnap = yesterdayLogRef.get().await()

            if (!logSnap.exists()) {
                // Yesterday was MISSED (Ni ne workout logs, ne rest logs za včeraj)

                // ZDAJ PRIDE POSEBNA LOGIKA - 1. POGLEDAMO ALI LAHKO SWAPAMO REST DAY
                val canSwap = checkIfFutureRestDaysExistAndSwap(yesterdayStr)

                if (canSwap) {
                    // Swap uspešen, včeraj smo spremenili v REST day automatsko, streak ne pade.
                    userRef.collection("daily_logs").document(yesterdayStr).set(mapOf(
                        "date" to yesterdayStr,
                        "status" to "REST_SWAPPED",
                        "timestamp" to Clock.System.now().toEpochMilliseconds()
                    )).await()
                    Log.d("GamificationRepo", "Workout zgrešen, a je bil nadomeščen s Swap-om Rest dneva.")
                    return
                }

                // NI VEČ REST DAYEV V TEDNU. 2. POGLEDAMO ČE IMAMO STREAK FREEZE.
                val freezeUsed = consumeStreakFreeze()
                if (freezeUsed) {
                    // Uspešno uporabljen STREAK FREEZE.
                    userRef.collection("daily_logs").document(yesterdayStr).set(mapOf(
                        "date" to yesterdayStr,
                        "status" to "FROZEN",
                        "timestamp" to Clock.System.now().toEpochMilliseconds()
                    )).await()
                    Log.d("GamificationRepo", "Streak Freeze uspešno porabljen. Streak ohranjen.")
                } else {
                    // NO FREEZE. STREAK PADE NA 0.
                    userRef.update("streak_days", 0).await()
                    Log.d("GamificationRepo", "Ni rest dnevov in ni freeze-a. Streak je padel na 0.")
                }
            }
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Midnight streak check failed.", e)
        }
    }

    private suspend fun checkIfFutureRestDaysExistAndSwap(missedDateStr: String): Boolean {
        Log.d("GamificationRepo", "Checking if we can auto-swap missed workout ($missedDateStr) for a future REST day in current week")
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return false
        try {
            // 1. Dobimo plan dokument
            val docRef = db.collection("user_plans").document(userRef.id)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) return false

            @Suppress("UNCHECKED_CAST")
            val plansList = snapshot.get("plans") as? MutableList<Map<String, Any>> ?: return false
            if (plansList.isEmpty()) return false
            val currentPlan = plansList.first().toMutableMap()

            // 2. Najdemo kateri dan v planu smo (preštejemo dni zabeležene v daily_logs)
            val logsSnap = userRef.collection("daily_logs").get().await()
            val currentPlanDayNum = logsSnap.documents.size + 1

            @Suppress("UNCHECKED_CAST")
            val weeksList = currentPlan["weeks"] as? MutableList<Map<String, Any>> ?: return false

            var foundRestDay = false
            var targetRestWeekIndex = -1
            var targetRestDayIndex = -1
            var missedWeekIndex = -1
            var missedDayIndex = -1

            // Iskanje včerajšnjega dneva
            for ((wIndex, week) in weeksList.withIndex()) {
                @Suppress("UNCHECKED_CAST")
                val days = week["days"] as? MutableList<Map<String, Any>> ?: continue
                for ((dIndex, day) in days.withIndex()) {
                    val dayNum = (day["dayNumber"] as? Number)?.toInt() ?: 0
                    if (dayNum == currentPlanDayNum) {
                         missedWeekIndex = wIndex
                         missedDayIndex = dIndex
                    }
                    if (missedWeekIndex != -1 && dayNum > currentPlanDayNum) {
                        val isRest = day["isRestDay"] as? Boolean ?: false
                        if (isRest && !foundRestDay) {
                           targetRestWeekIndex = wIndex
                           targetRestDayIndex = dIndex
                           foundRestDay = true
                        }
                    }
                }
            }

            if (!foundRestDay || missedWeekIndex == -1) return false

            // Perform SWAP
            @Suppress("UNCHECKED_CAST")
            val missedWeek = (weeksList[missedWeekIndex] as Map<String, Any>).toMutableMap()
            @Suppress("UNCHECKED_CAST")
            val missedDays = (missedWeek["days"] as List<Map<String, Any>>).map { it.toMutableMap() }.toMutableList()

            @Suppress("UNCHECKED_CAST")
            val targetWeek = if (missedWeekIndex == targetRestWeekIndex) missedWeek else (weeksList[targetRestWeekIndex] as Map<String, Any>).toMutableMap()
            @Suppress("UNCHECKED_CAST")
            val targetDays = if (missedWeekIndex == targetRestWeekIndex) missedDays else (targetWeek["days"] as List<Map<String, Any>>).map { it.toMutableMap() }.toMutableList()

            // missedDay postane Rest
            missedDays[missedDayIndex]["isRestDay"] = true
            missedDays[missedDayIndex]["isSwapped"] = true

            // future day postane Workout (podeduje focus)
            val originalFocus = missedDays[missedDayIndex]["focusLabel"] ?: ""
            targetDays[targetRestDayIndex]["isRestDay"] = false
            targetDays[targetRestDayIndex]["focusLabel"] = originalFocus
            targetDays[targetRestDayIndex]["isSwapped"] = true

            missedWeek["days"] = missedDays
            weeksList[missedWeekIndex] = missedWeek

            if (missedWeekIndex != targetRestWeekIndex) {
                 targetWeek["days"] = targetDays
                 weeksList[targetRestWeekIndex] = targetWeek
            }

            currentPlan["weeks"] = weeksList
            plansList[0] = currentPlan

            docRef.update("plans", plansList).await()
            return true

        } catch (e: Exception) {
            Log.e("GamificationRepo", "Swap algorithem padel", e)
            return false
        }
    }
}
