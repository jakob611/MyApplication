package com.example.myapplication.data.gamification

import android.util.Log
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.domain.gamification.GamificationState
import com.example.myapplication.domain.model.UserDayStatus
import com.example.myapplication.data.store.FirestoreHelper
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
 * Faza 21: Unified moveToNextDay() nadomešča processActivityCompletion() + updateStreak().
 * - Ena atomarna Firestore transakcija za VSE poti aktivnostnega zaključka
 * - getYesterdayStr() DST-safe kotlinx.datetime algebra
 * - UserDayStatus tipsko-varni enum nadomešča raztresene String konstante
 */
class FirestoreGamificationRepository : GamificationRepository {

    private val db get() = FirestoreHelper.getDb()

    private fun getTodayStr(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    /** DST-safe izračun včerajšnjega datuma prek kotlinx.datetime algebre. */
    private fun getYesterdayStr(): String =
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.minus(1, DateTimeUnit.DAY).toString()

    private fun getTodayEpoch(): Long =
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.toEpochDays().toLong()

    // ─────────────────────────────────────────────────────────────────────────
    // awardXP — za neodvisne klice (login, plan, itd.)
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun awardXP(amount: Int, reason: String) {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return
        try {
            db.runTransaction { transaction ->
                val snapshot  = transaction.get(userRef)
                val currentXp = snapshot.getLong("xp")?.toInt() ?: 0
                val newXp     = currentXp + amount
                val newLevel  = UserProfile.calculateLevel(newXp)
                transaction.set(userRef, mapOf("xp" to newXp, "level" to newLevel), SetOptions.merge())
                val logRef = userRef.collection("xp_history").document()
                transaction.set(logRef, mapOf(
                    "amount" to amount, "reason" to reason, "date" to getTodayStr(),
                    "timestamp" to Clock.System.now().toEpochMilliseconds(),
                    "xpAfter" to newXp, "levelAfter" to newLevel
                ))
            }.await()
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Napaka pri beleženju XP-ja", e)
        }
    }

    override suspend fun getCurrentStreak(): Int {
        // Faza 34 — HIGH-01 Fix: Propagiraj izjemo navzgor namesto tihega 0.
        // Prejšnji catch { 0 } je ob mrežni napaki vrnil streak=0, kar je UI potencialno
        // resetiral streak na ničlo brez dejanske spremembe v Firestoreu.
        val userRef = FirestoreHelper.getCurrentUserDocRef()
            ?: throw IllegalStateException("Uporabnik ni prijavljen — getCurrentStreak zahteva veljavno sejo.")
        return userRef.get().await().getLong("streak_days")?.toInt() ?: 0
    }

    override suspend fun markRestDayPending() {
        val userRef  = FirestoreHelper.getCurrentUserDocRef() ?: return
        val todayStr = getTodayStr()
        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                val existing = UserDayStatus.fromFirestore(dailyHistory[todayStr]?.toString())
                // Idempotentno — ne prepiši zaključenih statusov
                if (existing.isDoneToday) return@runTransaction null
                transaction.update(userRef, mapOf(
                    "dailyHistory.$todayStr" to UserDayStatus.REST_DAY_PENDING.firestoreValue
                ))
                null
            }.await()
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Napaka pri markRestDayPending()", e)
        }
    }

    override suspend fun getTodayStatus(): UserDayStatus {
        // Faza 34 — HIGH-02 Fix: Propagiraj izjemo namesto tihega WORKOUT_PENDING fallback-a.
        // Tihi fallback je bil nevaren: auth/mrežna napaka bi sistema pustila misliti,
        // da vadba danes ni bila opravljena, kar bi dovolilo duplikatni zapis streaka.
        val userRef = FirestoreHelper.getCurrentUserDocRef()
            ?: throw IllegalStateException("Uporabnik ni prijavljen — getTodayStatus zahteva veljavno sejo.")
        val snapshot = userRef.get().await()
        @Suppress("UNCHECKED_CAST")
        val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
        return UserDayStatus.fromFirestore(dailyHistory[getTodayStr()]?.toString())
    }

    override suspend fun consumeStreakFreeze(): Boolean {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return false
        return try {
            // Faza 34 — CRIT-03 Fix: Transakcija neposredno vrne Boolean — brez externalne
            // mutable var `consumed`, ki bi bila ranljiva na retry-prone lambda izvedbo.
            val consumed: Boolean = db.runTransaction { transaction ->
                val snap = transaction.get(userRef)
                val curr = snap.getLong("streak_freezes")?.toInt() ?: 0
                if (curr > 0) {
                    transaction.update(userRef, "streak_freezes", curr - 1)
                    true   // atomarno: porabi in vrni true
                } else {
                    false  // ni zamrznitev na voljo
                }
            }.await() ?: false
            consumed
        } catch (e: Exception) { false }
    }

    override suspend fun logBurnedCalories(todayStr: String, calories: Double) {
        try {
            val userRef     = FirestoreHelper.getCurrentUserDocRef() ?: return
            val dailyLogRef = db.collection("dailyLogs").document(todayStr)
            db.runTransaction { transaction ->
                val snap     = transaction.get(dailyLogRef)
                val existing = (snap.get("burnedCalories") as? Number)?.toDouble() ?: 0.0
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
        // Faza 34 — CRIT-02 Fix: Propagiraj izjemo — brez tihega fallback GamificationState().
        // Klicatelji (ManageGamificationUseCase.getGamificationStateFlow) upravljajo napake.
        val userRef  = FirestoreHelper.getCurrentUserDocRef()
            ?: throw IllegalStateException("Uporabnik ni prijavljen — getGamificationState zahteva auth.")
        val snapshot = userRef.get().await()
        val weeklyTarget = snapshot.getLong("weekly_target")?.toInt() ?: 0
        val workoutDone  = getTodayStatus().isDoneToday
        return GamificationState(weeklyTarget = weeklyTarget, workoutDoneToday = workoutDone)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // moveToNextDay — SSOT za VSE aktivnostne zaključke (Faza 21)
    //
    // ENA atomarna Firestore transakcija za:
    //   ① De-duplikacija (ne prepiše WORKOUT_DONE z nižjo prioriteto)
    //   ② Streak izračun (epoch-based, Streak Freeze podpora)
    //   ③ plan_day +1 (samo kadar incrementPlanDay=true)
    //   ④ XP + Level atomarno
    //   ⑤ dailyHistory vpis z UserDayStatus.firestoreValue
    //   ⑥ burnedCalories v dailyLogs (Nutrition bridge)
    //   ⑦ Faza 34 — CRIT-03: workoutSessionDoc atomarno v workoutSessions (brez ločenega write-a)
    //
    // Če transakcija SPODLETI → Room ni posodobljena (callerji prejmejo -1).
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun moveToNextDay(
        newStatus: UserDayStatus,
        xpToBeAwarded: Int,
        xpReason: String,
        caloriesBurned: Double,
        incrementPlanDay: Boolean,
        workoutSessionDoc: Map<String, Any>?
    ): Int {
        // Samo zaključitveni statusi so dovoljeni
        require(newStatus.isDoneToday) {
            "moveToNextDay zahteva zaključitveni status (WORKOUT_DONE, REST_DAY_DONE, REST_WORKOUT_DONE)."
        }

        val userRef    = FirestoreHelper.getCurrentUserDocRef() ?: return 0
        val todayStr   = getTodayStr()
        val todayEpoch = getTodayEpoch()
        val nowMillis  = Clock.System.now().toEpochMilliseconds()

        return try {
            // Faza 34 — CRIT-03 Fix: Transakcija neposredno vrne Int kot rezultat (ne prek externalne
            // mutable var `resultStreak`). Odpravi race condition ob retry-prone transakcijah.
            val resultStreak: Int = db.runTransaction { transaction ->
                // ── READ faza ─────────────────────────────────────────────
                val snapshot = transaction.get(userRef)

                @Suppress("UNCHECKED_CAST")
                val dailyHistory   = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                val existingStatus = UserDayStatus.fromFirestore(dailyHistory[todayStr]?.toString())

                // De-dup: WORKOUT_DONE je najvišja prioriteta, ne prepiši ga
                if (existingStatus == UserDayStatus.WORKOUT_DONE || existingStatus == newStatus) {
                    Log.d("GamificationRepo", "moveToNextDay: $todayStr že '$existingStatus' — de-dup preskoček.")
                    return@runTransaction snapshot.getLong("streak_days")?.toInt() ?: 0
                }

                val oldStreak     = (snapshot.getLong("streak_days")         ?: 0L).toInt()
                val oldLastEpoch  =  snapshot.getLong("last_activity_epoch") ?: 0L
                val oldPlanDay    = (snapshot.getLong("plan_day")            ?: 1L).toInt()
                val oldFreezes    = (snapshot.getLong("streak_freezes")      ?: 0L).toInt()
                val currentXp     = (snapshot.getLong("xp")                  ?: 0L).toInt()

                // Burned calories za Nutrition bridge
                val dailyLogRef = db.collection("dailyLogs").document(todayStr)
                val logSnapshot = if (caloriesBurned > 0.0) transaction.get(dailyLogRef) else null
                val existingCals = (logSnapshot?.get("burnedCalories") as? Number)?.toDouble() ?: 0.0

                // ── Streak izračun ─────────────────────────────────────────
                val dayDiff    = todayEpoch - oldLastEpoch
                var newFreezes = oldFreezes
                val newStreak: Int = when {
                    !newStatus.contributesToStreak -> oldStreak          // REST_WORKOUT_DONE = ohrani
                    oldLastEpoch == 0L             -> 1                  // Prva aktivnost kdajkoli
                    dayDiff == 0L                  -> oldStreak          // Isti dan (de-dup zgoraj bi ujel)
                    dayDiff == 1L                  -> oldStreak + 1      // Včeraj aktiven → podaljšaj
                    oldFreezes > 0                 -> {                  // Vrzel > 1 dan → preveri Freeze
                        newFreezes = oldFreezes - 1
                        Log.d("GamificationRepo", "❄️ moveToNextDay: Streak Freeze porabljen! Ostalo: $newFreezes")
                        oldStreak
                    }
                    else -> 1                                            // Ni freeze → ponastavi na 1
                }

                // ── Plan day napredovanje ──────────────────────────────────
                val newPlanDay = if (incrementPlanDay) oldPlanDay + 1 else oldPlanDay

                // ── XP + Level ─────────────────────────────────────────────
                val newXp    = currentXp + xpToBeAwarded
                val newLevel = UserProfile.calculateLevel(newXp)

                // ── WRITE faza ─────────────────────────────────────────────
                val userUpdates = mutableMapOf<String, Any>(
                    "streak_days"             to newStreak,
                    "plan_day"                to newPlanDay,
                    "last_activity_epoch"     to todayEpoch,
                    "last_streak_update_date" to todayStr,
                    "dailyHistory.$todayStr"  to newStatus.firestoreValue,
                    "xp"                      to newXp,
                    "level"                   to newLevel
                )
                if (newFreezes != oldFreezes) userUpdates["streak_freezes"] = newFreezes
                transaction.update(userRef, userUpdates)

                // XP history log
                if (xpToBeAwarded > 0) {
                    val xpLogRef = userRef.collection("xp_history").document()
                    transaction.set(xpLogRef, mapOf(
                        "amount" to xpToBeAwarded, "reason" to xpReason,
                        "date" to todayStr, "timestamp" to nowMillis,
                        "xpAfter" to newXp, "levelAfter" to newLevel
                    ))
                }

                // Burned calories → Nutrition bridge
                if (caloriesBurned > 0.0 && logSnapshot != null) {
                    transaction.set(
                        dailyLogRef,
                        mapOf("burnedCalories" to existingCals + caloriesBurned, "userId" to userRef.id),
                        SetOptions.merge()
                    )
                }

                // Faza 34 — CRIT-03: Atomarni zapis workout session dokumenta.
                // Oba zapisa (gamification + seja) sta v isti transakciji → all-or-nothing.
                if (workoutSessionDoc != null) {
                    val sessionRef = userRef.collection("workoutSessions").document()
                    transaction.set(sessionRef, workoutSessionDoc)
                }

                // Faza 34 — CRIT-03 Fix: Popravljeni log prehoda streak=$oldStreak→$newStreak
                Log.d("GamificationRepo",
                    "✅ moveToNextDay [$newStatus]: streak=$oldStreak→$newStreak, " +
                    "planDay=$oldPlanDay→$newPlanDay, xp=+$xpToBeAwarded(→$newXp), " +
                    "level=$newLevel, cals=$caloriesBurned, freezeUsed=${newFreezes != oldFreezes}, " +
                    "workoutDocSaved=${workoutSessionDoc != null}")

                newStreak  // atomarni return vrednosti iz transakcije (ne prek var)
            }.await() ?: 0
            resultStreak
        } catch (e: Exception) {
            Log.e("GamificationRepo", "❌ moveToNextDay spodletel: ${e.message}", e)
            // Faza 31.6 avdit: vrnemo -1 (ne 0) kot ekspliciten signal napake.
            // 0 je legitimna vrednost de-dup preskakovanj; -1 nedvoumno pomeni "Firestore je spodletel".
            // VM preverja `takeIf { it > 0 }` → -1 bo pravilno filtiran kot napaka.
            -1
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // runMidnightStreakCheck — Strogi statusni check za včerajšnji dan
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun runMidnightStreakCheck() {
        val userRef      = FirestoreHelper.getCurrentUserDocRef() ?: return
        val yesterdayStr = getYesterdayStr()

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()

                val yesterdayStatus = UserDayStatus.fromFirestore(dailyHistory[yesterdayStr]?.toString())
                val safeStatuses    = setOf(
                    UserDayStatus.WORKOUT_DONE, UserDayStatus.REST_WORKOUT_DONE,
                    UserDayStatus.REST_DAY_DONE, UserDayStatus.FROZEN
                )
                if (yesterdayStatus in safeStatuses) {
                    Log.d("GamificationRepo", "✅ Midnight check: včeraj ($yesterdayStr) = '$yesterdayStatus' — ni kazni.")
                    return@runTransaction null
                }

                Log.d("GamificationRepo", "⚠️ Midnight check: včeraj ($yesterdayStr) = '$yesterdayStatus' → kazen.")
                val currentFreezes = (snapshot.getLong("streak_freezes") ?: 0L).toInt()
                if (currentFreezes > 0) {
                    transaction.update(userRef, mapOf(
                        "streak_freezes"             to (currentFreezes - 1),
                        "dailyHistory.$yesterdayStr" to UserDayStatus.FROZEN.firestoreValue
                    ))
                } else {
                    transaction.update(userRef, mapOf(
                        "streak_days"                to 0,
                        "dailyHistory.$yesterdayStr" to UserDayStatus.MISSED.firestoreValue
                    ))
                }
                null
            }.await()
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Polnočni streak check je spodletel.", e)
        }
    }
}

