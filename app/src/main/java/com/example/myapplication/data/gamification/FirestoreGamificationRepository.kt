package com.example.myapplication.data.gamification

import android.util.Log
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.domain.gamification.GamificationState
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Zlata koda - KMP ready Firestore implementacija za Gamification.
 * NIMA NITI ENE SharedPreferences ODVISNOSTI.
 *
 * Faza 12: Unified Streak + XP + Calories Engine.
 * - getYesterdayStr() popravljena (DST-safe kotlinx.datetime algebra)
 * - processWorkoutCompletion: ENA atomarna transakcija za streak + XP + kalorije
 * - updateStreak: epoch-based (last_activity_epoch), enaka logika kot processWorkoutCompletion
 * - last_activity_epoch nadomešča last_workout_epoch (pokriva trening IN raztezanje)
 */
class FirestoreGamificationRepository : GamificationRepository {

    private val db get() = FirestoreHelper.getDb()

    private fun getTodayStr(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    /** Faza 12: DST-safe izračun včerajšnjega datuma prek kotlinx.datetime algebre. */
    private fun getYesterdayStr(): String =
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .minus(1, DateTimeUnit.DAY)
            .toString()

    private fun getTodayEpoch(): Long =
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.toEpochDays().toLong()

    // ─────────────────────────────────────────────────────────────────────────
    // awardXP — ostane za neodvisne klice (login, plan, rest day, extra workout)
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun awardXP(amount: Int, reason: String) {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return
        try {
            db.runTransaction { transaction ->
                val snapshot    = transaction.get(userRef)
                val currentXp   = snapshot.getLong("xp")?.toInt() ?: 0
                val newXp       = currentXp + amount
                val newLevel    = UserProfile.calculateLevel(newXp)

                transaction.set(userRef, mapOf(
                    "xp"    to newXp,
                    "level" to newLevel
                ), SetOptions.merge())

                val logRef = userRef.collection("xp_history").document()
                transaction.set(logRef, mapOf(
                    "amount"     to amount,
                    "reason"     to reason,
                    "date"       to getTodayStr(),
                    "timestamp"  to Clock.System.now().toEpochMilliseconds(),
                    "xpAfter"    to newXp,
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
        return try {
            userRef.get().await().getLong("streak_days")?.toInt() ?: 0
        } catch (e: Exception) { 0 }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateStreak — Faza 12: Epoch-based (pokriva raztezanje na rest dnevu)
    // Uporablja last_activity_epoch (skupno polje za trening IN raztezanje)
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun updateStreak(isWorkoutSuccess: Boolean, activityType: String): Int {
        val userRef    = FirestoreHelper.getCurrentUserDocRef() ?: return 0
        val todayStr   = getTodayStr()
        val todayEpoch = getTodayEpoch()

        return try {
            var computedStreak = 0
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)

                // De-dup: dan je že zabeležen → preskoči
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                if (dailyHistory.containsKey(todayStr)) {
                    computedStreak = snapshot.getLong("streak_days")?.toInt() ?: 0
                    Log.d("GamificationRepo", "updateStreak: $todayStr že zabeležen (${dailyHistory[todayStr]}) — preskočim.")
                    return@runTransaction null
                }

                val oldStreak    = (snapshot.getLong("streak_days")          ?: 0L).toInt()
                val oldLastEpoch =  snapshot.getLong("last_activity_epoch")  ?: 0L
                val oldFreezes   = (snapshot.getLong("streak_freezes")       ?: 0L).toInt()

                val dayDiff = todayEpoch - oldLastEpoch
                var newFreezes = oldFreezes

                val newStreak: Int = when {
                    !isWorkoutSuccess  -> oldStreak          // ni uspeh → ohrani brez spremembe
                    oldLastEpoch == 0L -> 1                  // prvi zapis kdajkoli
                    dayDiff == 0L      -> oldStreak          // isti dan (ne bi smelo sem, de-dup zgoraj)
                    dayDiff == 1L      -> oldStreak + 1      // včeraj aktiven → podaljšaj
                    else -> {
                        // Vrzel — preveri Streak Freeze
                        if (oldFreezes > 0) {
                            newFreezes = oldFreezes - 1
                            Log.d("GamificationRepo", "❄️ updateStreak Freeze porabljen! Ostalo: $newFreezes")
                            oldStreak  // streak se ohrani, NE poveča
                        } else { 1 }
                    }
                }
                computedStreak = newStreak

                val updates = mutableMapOf<String, Any>(
                    "streak_days"             to newStreak,
                    "last_activity_epoch"     to todayEpoch,
                    "last_streak_update_date" to todayStr,
                    "dailyHistory.$todayStr"  to activityType
                )
                if (newFreezes != oldFreezes) updates["streak_freezes"] = newFreezes

                transaction.update(userRef, updates)
                Log.d("GamificationRepo", "✅ updateStreak: streak=$newStreak, activityType=$activityType, dayDiff=$dayDiff")
                null
            }.await()
            computedStreak
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Napaka pri updateStreak()", e)
            0
        }
    }

    override suspend fun markRestDayPending() {
        val userRef  = FirestoreHelper.getCurrentUserDocRef() ?: return
        val todayStr = getTodayStr()
        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                val existing = dailyHistory[todayStr]?.toString()
                // Idempotentno — ne prepiši zaključenih statusov
                if (existing == "STRETCHING_DONE" || existing == "WORKOUT_DONE") return@runTransaction null
                transaction.update(userRef, mapOf("dailyHistory.$todayStr" to "PENDING_STRETCHING"))
                null
            }.await()
            Log.d("GamificationRepo", "✅ Rest day $todayStr → PENDING_STRETCHING")
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Napaka pri markRestDayPending()", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // processActivityCompletion — Faza 12b: ENA atomarna transakcija za VSE aktivnosti
    //
    // KRITIČNA FIX (Faza 12b): isRestDay=true NE sme zaobiti transakcije!
    // Brez vpisa v dailyHistory in last_activity_epoch midnight worker vidi prazen dan
    // in kaznuje uporabnika z Freeze porabo ali streakResetom — čeprav je treniral.
    //
    // Streak matrika:
    //   dayDiff == 1L + isRestDay=true  → ohrani streak (rest day ne prispeva +1)
    //   dayDiff == 1L + isRestDay=false → streak + 1     (redni workout)
    //   dayDiff > 1L                    → freeze? ohrani : 1
    //
    // dailyHistory status:
    //   isRestDay=false → "WORKOUT_DONE"
    //   isRestDay=true  → "REST_WORKOUT_DONE"
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun processActivityCompletion(
        isRestDay: Boolean,
        incrementPlanDay: Boolean,
        xpToBeAwarded: Int,
        xpReason: String,
        caloriesBurned: Double
    ) {
        val userRef    = FirestoreHelper.getCurrentUserDocRef() ?: return
        val todayStr   = getTodayStr()
        val todayEpoch = getTodayEpoch()
        val nowMillis  = Clock.System.now().toEpochMilliseconds()
        val todayStatus = if (isRestDay) "REST_WORKOUT_DONE" else "WORKOUT_DONE"

        try {
            db.runTransaction { transaction ->
                // ── READ faza (vse read-e pred write-i) ────────────────────
                val snapshot = transaction.get(userRef)

                // De-dup: prepreči ponavljajoče klice istega tipa za isti dan
                // "WORKOUT_DONE" je višja prioriteta — ne prepiši ga z "REST_WORKOUT_DONE"
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                val existingStatus = dailyHistory[todayStr]?.toString()
                if (existingStatus == "WORKOUT_DONE" || existingStatus == todayStatus) {
                    Log.d("GamificationRepo", "processActivityCompletion: danes že '$existingStatus' — preskočim.")
                    return@runTransaction null
                }

                val oldStreak    = (snapshot.getLong("streak_days")         ?: 0L).toInt()
                val oldLastEpoch =  snapshot.getLong("last_activity_epoch") ?: 0L
                val oldPlanDay   = (snapshot.getLong("plan_day")            ?: 1L).toInt()
                val oldFreezes   = (snapshot.getLong("streak_freezes")      ?: 0L).toInt()
                val currentXp    = (snapshot.getLong("xp")                  ?: 0L).toInt()

                // Atomarno branje dailyLogs dokumenta (za akumulacijo kalorij)
                val dailyLogRef      = db.collection("dailyLogs").document(todayStr)
                val dailyLogSnapshot = transaction.get(dailyLogRef)
                val existingCals     = (dailyLogSnapshot.get("burnedCalories") as? Number)?.toDouble() ?: 0.0

                // ── Streak izračun (epoch-based z isRestDay matriko) ────────
                val dayDiff    = todayEpoch - oldLastEpoch
                var newFreezes = oldFreezes
                val newStreak: Int = when {
                    oldLastEpoch == 0L -> 1          // prvi zapis kdajkoli
                    dayDiff == 0L      -> oldStreak  // isti dan (ne bi smelo sem — de-dup zgoraj)
                    dayDiff == 1L      -> {
                        // Včeraj aktiven: rest day ohrani streak, redni workout ga poveča
                        if (isRestDay) oldStreak else oldStreak + 1
                    }
                    else -> {
                        // Vrzel > 1 dan: preveri Streak Freeze
                        if (oldFreezes > 0) {
                            newFreezes = oldFreezes - 1
                            Log.d("GamificationRepo", "❄️ processActivityCompletion Freeze porabljen! Ostalo: $newFreezes")
                            oldStreak  // ohrani streak, NE poveča
                        } else {
                            1          // ni freeze → ponastavi na 1
                        }
                    }
                }

                // ── XP + Level ─────────────────────────────────────────────
                val newXp    = currentXp + xpToBeAwarded
                val newLevel = UserProfile.calculateLevel(newXp)

                // ── Plan day (samo za redne workouty) ─────────────────────
                val newPlanDay = if (incrementPlanDay) oldPlanDay + 1 else oldPlanDay

                // ── WRITE faza ─────────────────────────────────────────────
                // 1. Glavni user dokument — streak + XP + plan_day + epoch + dailyHistory
                val userUpdates = mutableMapOf<String, Any>(
                    "streak_days"             to newStreak,
                    "plan_day"                to newPlanDay,
                    "last_activity_epoch"     to todayEpoch,   // ← VEDNO posodobi (zavaruje pred midnight check)
                    "last_streak_update_date" to todayStr,
                    "dailyHistory.$todayStr"  to todayStatus,  // "WORKOUT_DONE" ali "REST_WORKOUT_DONE"
                    "xp"                      to newXp,
                    "level"                   to newLevel
                )
                if (newFreezes != oldFreezes) userUpdates["streak_freezes"] = newFreezes
                transaction.update(userRef, userUpdates)

                // 2. XP history log (subcollection)
                val xpLogRef = userRef.collection("xp_history").document()
                transaction.set(xpLogRef, mapOf(
                    "amount"     to xpToBeAwarded,
                    "reason"     to xpReason,
                    "date"       to todayStr,
                    "timestamp"  to nowMillis,
                    "xpAfter"    to newXp,
                    "levelAfter" to newLevel
                ))

                // 3. Burned calories v dailyLogs (Nutrition bridge)
                if (caloriesBurned > 0.0) {
                    transaction.set(
                        dailyLogRef,
                        mapOf(
                            "burnedCalories" to existingCals + caloriesBurned,
                            "userId"         to userRef.id
                        ),
                        SetOptions.merge()
                    )
                }

                Log.d("GamificationRepo",
                    "✅ processActivityCompletion [isRestDay=$isRestDay]: " +
                    "streak=$newStreak, planDay=$newPlanDay, " +
                    "xp=+$xpToBeAwarded (→$newXp), level=$newLevel, " +
                    "calories=$caloriesBurned, status=$todayStatus, " +
                    "freezeUsed=${newFreezes != oldFreezes}")
                null
            }.await()
        } catch (e: Exception) {
            Log.e("GamificationRepo", "❌ processActivityCompletion failed: ${e.message}", e)
        }
    }

    override suspend fun getTodayStatus(): String? {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return null
        return try {
            val snapshot = userRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
            dailyHistory[getTodayStr()]?.toString()
        } catch (e: Exception) { null }
    }

    override suspend fun consumeStreakFreeze(): Boolean {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return false
        return try {
            var freezeConsumed = false
            db.runTransaction { transaction ->
                val snapshot       = transaction.get(userRef)
                val currentFreezes = snapshot.getLong("streak_freezes")?.toInt() ?: 0
                if (currentFreezes > 0) {
                    transaction.update(userRef, "streak_freezes", currentFreezes - 1)
                    freezeConsumed = true
                }
            }.await()
            freezeConsumed
        } catch (e: Exception) { false }
    }

    /** Ostane v vmesniku za neodvisne callers (extra workout na rest dnevu). */
    override suspend fun logBurnedCalories(todayStr: String, calories: Double) {
        try {
            val userRef     = FirestoreHelper.getCurrentUserDocRef() ?: return
            val dailyLogRef = db.collection("dailyLogs").document(todayStr)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(dailyLogRef)
                val existing = (snapshot.get("burnedCalories") as? Number)?.toDouble() ?: 0.0
                transaction.set(
                    dailyLogRef,
                    mapOf("burnedCalories" to existing + calories, "userId" to userRef.id),
                    SetOptions.merge()
                )
                null
            }.await()
        } catch (e: Exception) {
            Log.e("GamificationRepo", "logBurnedCalories failed: ${e.message}")
        }
    }

    override suspend fun getGamificationState(): GamificationState {
        return try {
            val userRef     = FirestoreHelper.getCurrentUserDocRef() ?: return GamificationState()
            val snapshot    = userRef.get().await()
            val weeklyTarget    = snapshot.getLong("weekly_target")?.toInt() ?: 0
            val todayStatus     = getTodayStatus() ?: ""
            val workoutDoneToday = todayStatus == "WORKOUT_DONE"
                    || todayStatus == "REST_WORKOUT_DONE"
                    || todayStatus == "STRETCHING_DONE"
            GamificationState(weeklyTarget = weeklyTarget, workoutDoneToday = workoutDoneToday)
        } catch (e: Exception) { GamificationState() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // runMidnightStreakCheck — Faza 12: DST-safe getYesterdayStr()
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun runMidnightStreakCheck() {
        val userRef      = FirestoreHelper.getCurrentUserDocRef() ?: return
        val yesterdayStr = getYesterdayStr()

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()

                if (dailyHistory.containsKey(yesterdayStr)) {
                    Log.d("GamificationRepo", "Včeraj ($yesterdayStr) zabeležen: ${dailyHistory[yesterdayStr]}")
                    return@runTransaction null
                }

                val currentFreezes = (snapshot.getLong("streak_freezes") ?: 0L).toInt()
                if (currentFreezes > 0) {
                    transaction.update(userRef, mapOf(
                        "streak_freezes"              to (currentFreezes - 1),
                        "dailyHistory.$yesterdayStr"  to "FROZEN"
                    ))
                    Log.d("GamificationRepo", "❄️ Midnight Freeze auto-porabljen. Ostalo: ${currentFreezes - 1}")
                } else {
                    transaction.update(userRef, mapOf(
                        "streak_days"                to 0,
                        "dailyHistory.$yesterdayStr" to "MISSED"
                    ))
                    Log.d("GamificationRepo", "💔 Ni freeze-a. Streak je padel na 0.")
                }
                null
            }.await()
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Polnočni streak check je spodletel.", e)
        }
    }
}



