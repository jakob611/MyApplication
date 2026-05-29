package com.example.myapplication.data.gamification

import android.util.Log
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.domain.gamification.GamificationState
import com.example.myapplication.domain.gamification.GamificationUpdateResult
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
        } catch (e: Exception) {
            Log.e("FirestoreGamificationRepo", "consumeStreakFreeze failed", e)
            false
        }
    }

    override suspend fun logBurnedCalories(todayStr: String, calories: Double) {
        try {
            val userRef     = FirestoreHelper.getCurrentUserDocRef() ?: return
            // FIX Faza 46: user-scoped pot namesto globalnega db.collection("dailyLogs")
            val dailyLogRef = userRef.collection("dailyLogs").document(todayStr)
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
            Log.e("FirestoreGamificationRepo", "Failed to update user-scoped daily log (logBurnedCalories)", e)
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
    // BP-3 / BP-4 Fix: Vrne Result<GamificationUpdateResult> namesto Int.
    // Transakcija vrne DEJANSKI newStreak + newPlanDay (brani iz Firestore snapshot-a).
    // Ni več tihega -1 return-a ob napaki — Result.failure propagira napako navzgor.
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun moveToNextDay(
        newStatus: UserDayStatus,
        xpToBeAwarded: Int,
        xpReason: String,
        caloriesBurned: Double,
        incrementPlanDay: Boolean,
        workoutSessionDoc: Map<String, Any>?
    ): Result<GamificationUpdateResult> {
        // Samo zaključitveni statusi so dovoljeni (programska napaka → throw direktno)
        require(newStatus.isDoneToday) {
            "moveToNextDay zahteva zaključitveni status (WORKOUT_DONE, REST_DAY_DONE, REST_WORKOUT_DONE)."
        }

        val userRef = FirestoreHelper.getCurrentUserDocRef()
            ?: return Result.failure(
                IllegalStateException("Uporabnik ni prijavljen — moveToNextDay zahteva veljavno sejo.")
            )
        val todayStr   = getTodayStr()
        val todayEpoch = getTodayEpoch()
        val nowMillis  = Clock.System.now().toEpochMilliseconds()

        return try {
            // BP-4 Fix: Transakcija direktno vrne GamificationUpdateResult — ne Int.
            // .await() na Task<GamificationUpdateResult> → null ob neričakovani null transakciji
            // → IllegalStateException namesto tihega nadaljevanja.
            val gamResult: GamificationUpdateResult = db.runTransaction { transaction ->
                // ── READ faza ─────────────────────────────────────────────
                val snapshot = transaction.get(userRef)

                @Suppress("UNCHECKED_CAST")
                val dailyHistory   = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                val existingStatus = UserDayStatus.fromFirestore(dailyHistory[todayStr]?.toString())

                // De-dup: WORKOUT_DONE je najvišja prioriteta, ne prepiši ga.
                // BP-3 Fix: vrni DEJANSKE vrednosti iz Firestore (ne 0 ali Int za lokalni izračun).
                if (existingStatus == UserDayStatus.WORKOUT_DONE || existingStatus == newStatus) {
                    Log.d("GamificationRepo", "moveToNextDay: $todayStr že '$existingStatus' — de-dup preskoček.")
                    return@runTransaction GamificationUpdateResult(
                        newStreak  = snapshot.getLong("streak_days")?.toInt() ?: 0,
                        newPlanDay = (snapshot.getLong("plan_day") ?: 1L).toInt()
                    )
                }

                val oldStreak    = (snapshot.getLong("streak_days")         ?: 0L).toInt()
                val oldLastEpoch =  snapshot.getLong("last_activity_epoch") ?: 0L
                val oldPlanDay   = (snapshot.getLong("plan_day")            ?: 1L).toInt()
                val oldFreezes   = (snapshot.getLong("streak_freezes")      ?: 0L).toInt()
                val currentXp    = (snapshot.getLong("xp")                  ?: 0L).toInt()

                // Burned calories za Nutrition bridge
                // FIX Faza 46: user-scoped pot namesto globalnega db.collection("dailyLogs")
                val dailyLogRef  = userRef.collection("dailyLogs").document(todayStr)
                val logSnapshot  = if (caloriesBurned > 0.0) transaction.get(dailyLogRef) else null
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
                // BP-3 Fix: newPlanDay se izračuna iz SVEŽEGA oldPlanDay (Firestore snapshot),
                // ne iz parametra currentPlanDay (ki je lahko zastarelo UI stanje).
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
                if (workoutSessionDoc != null) {
                    val sessionRef = userRef.collection("workoutSessions").document()
                    transaction.set(sessionRef, workoutSessionDoc)
                }

                Log.d("GamificationRepo",
                    "✅ moveToNextDay [$newStatus]: streak=$oldStreak→$newStreak, " +
                    "planDay=$oldPlanDay→$newPlanDay, xp=+$xpToBeAwarded(→$newXp), " +
                    "level=$newLevel, cals=$caloriesBurned, freezeUsed=${newFreezes != oldFreezes}, " +
                    "workoutDocSaved=${workoutSessionDoc != null}")

                // BP-3 / BP-4 Fix: Vrne GamificationUpdateResult z DEJANSKIMI vrednostmi —
                // ne samo newStreak (Int). ViewModel zdaj ve kaj je Firestore dejansko zapisal.
                GamificationUpdateResult(newStreak = newStreak, newPlanDay = newPlanDay)
            }.await()
                ?: throw IllegalStateException("Firestore transaction failed to mutate gamification metrics")

            Result.success(gamResult)
        } catch (e: Exception) {
            Log.e("GamificationRepo", "❌ moveToNextDay spodletel: ${e.message}", e)
            // BP-4 Fix: Result.failure propagira napako navzgor (ne -1).
            // ManageGamificationUseCase.getOrThrow() remeče izjemo →
            // UpdateBodyMetricsUseCase → Result.failure → VM Snackbar.
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // runMidnightStreakCheck — Strogi statusni check za včerajšnji dan
    //
    // Faza 54 — Anomaly #4 Fix:
    //   yesterdayWasRestDay=true → REST_DAY_PENDING se doda v safeStatuses (ni kazni
    //   za neoplavljeno raztezanje na počitniškem dnevu).
    //   yesterdayWasRestDay=true + WORKOUT_PENDING (app ni bila odprta) → dan se
    //   samodejno zaključi kot REST_DAY_DONE brez streak kazni.
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun runMidnightStreakCheck(yesterdayWasRestDay: Boolean) {
        val userRef      = FirestoreHelper.getCurrentUserDocRef() ?: return
        val yesterdayStr = getYesterdayStr()

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()

                val yesterdayStatus = UserDayStatus.fromFirestore(dailyHistory[yesterdayStr]?.toString())

                // Faza 54: safeStatuses se razširi z REST_DAY_PENDING kadar je včeraj bil počitniški dan.
                // Raztezanje ni obvezno — neuporabljeno PENDING_STRETCHING ne sme povzročiti streak reseta.
                val safeStatuses = buildSet {
                    addAll(listOf(
                        UserDayStatus.WORKOUT_DONE, UserDayStatus.REST_WORKOUT_DONE,
                        UserDayStatus.REST_DAY_DONE, UserDayStatus.FROZEN
                    ))
                    if (yesterdayWasRestDay) add(UserDayStatus.REST_DAY_PENDING)
                }

                if (yesterdayStatus in safeStatuses) {
                    Log.d("GamificationRepo", "✅ Midnight check: včeraj ($yesterdayStr) = '$yesterdayStatus' — ni kazni.")
                    return@runTransaction null
                }

                // Faza 54: Posebni primer — app ni bila odprta na počitniški dan (WORKOUT_PENDING).
                // Ker je yesterdayWasRestDay=true, samodejno zaključimo dan kot REST_DAY_DONE
                // namesto da bi kaznovali streaka.
                if (yesterdayWasRestDay && yesterdayStatus == UserDayStatus.WORKOUT_PENDING) {
                    Log.d("GamificationRepo",
                        "✅ Midnight check: včeraj ($yesterdayStr) = počitniški dan (app ni bila odprta) → " +
                        "samodejni REST_DAY_DONE, brez kazni.")
                    transaction.update(userRef, mapOf(
                        "dailyHistory.$yesterdayStr" to UserDayStatus.REST_DAY_DONE.firestoreValue
                    ))
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

