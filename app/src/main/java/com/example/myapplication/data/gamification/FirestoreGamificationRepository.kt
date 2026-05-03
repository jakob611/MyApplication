package com.example.myapplication.data.gamification

import android.util.Log
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Zlata koda - KMP ready Firestore implementacija za Gamification.
 * NIMA NITI ENE SharedPreferences ODVISNOSTI.
 *
 * Faza 4b: dailyHistory mapa v glavnem doc (ne subcollection daily_logs).
 * Streak +1 za Workout, +1 za Stretching (Rest day), 0 za Freeze, =0 brez freeze-a.
 * checkIfFutureRestDaysExistAndSwap je IZBRISAN — auto-swap ni več potreben.
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

    /**
     * Faza 4b — Nova Streak logika (Daily Habit):
     * +1 za workout DAN in uspešen trening.
     * +1 za rest DAN in opravljeno raztezanje (activityType = "STRETCHING_DONE").
     * De-dup: dailyHistory mapa v glavnem dokumentu (hitrejše branje, brez subcollection).
     * @return Novi streak po posodobitvi (0 ob napaki)
     */
    override suspend fun updateStreak(isWorkoutSuccess: Boolean, activityType: String): Int {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return 0
        val todayStr = getTodayStr()

        return try {
            var computedStreak = 0
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)

                // De-dup: preveri dailyHistory mapo v glavnem dokumentu
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                if (dailyHistory.containsKey(todayStr)) {
                    val existingStatus = dailyHistory[todayStr]?.toString() ?: "?"
                    Log.d("GamificationRepo", "Danes že zabeleženo kot $existingStatus — preskočim.")
                    computedStreak = snapshot.getLong("streak_days")?.toInt() ?: 0
                    return@runTransaction null
                }

                val currentStreak = snapshot.getLong("streak_days")?.toInt() ?: 0
                // Streak +1 samo ob uspešnem dnevu (workout ali stretching)
                val newStreak = if (isWorkoutSuccess) currentStreak + 1 else currentStreak
                computedStreak = newStreak

                // Zapiši streak + dailyHistory.$todayStr — vse v enem update()
                // Dot notation v update() = nested field path (Firestore standard)
                transaction.update(userRef, mapOf(
                    "streak_days" to newStreak,
                    "last_streak_update_date" to todayStr,
                    "dailyHistory.$todayStr" to activityType
                ))
                null
            }.await()
            Log.d("GamificationRepo", "✅ Streak posodobljen: $computedStreak ($activityType)")
            computedStreak
        } catch (e: Exception) {
            Log.e("GamificationRepo", "Napaka pri posodabljanju streaka.", e)
            0
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

    /**
     * Faza 4b — Polnočni streak check (brez auto-swap logike).
     *
     * Pravila:
     * - Dan je zabeležen v dailyHistory mapi → ni akcije.
     * - Dan ni zabeležen + ima freeze → auto-porabi freeze, zapiši "FROZEN".
     * - Dan ni zabeležen + ni freeze → streak = 0, zapiši "MISSED".
     *
     * checkIfFutureRestDaysExistAndSwap() je IZBRISAN (Faza 4b).
     */
    override suspend fun runMidnightStreakCheck() {
        val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return
        val yesterdayStr = getYesterdayStr()

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)

                // Preveri dailyHistory mapo v glavnem dokumentu
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()

                if (dailyHistory.containsKey(yesterdayStr)) {
                    // Včeraj je bil uspešno zabeležen — ni akcije
                    Log.d("GamificationRepo", "Včeraj ($yesterdayStr) zabeležen: ${dailyHistory[yesterdayStr]}")
                    return@runTransaction null
                }

                // Včerajšnji dan ni bil opravljen
                val currentFreezes = (snapshot.getLong("streak_freezes") ?: 0L).toInt()

                if (currentFreezes > 0) {
                    // Auto-porabi Streak Freeze
                    transaction.update(userRef, mapOf(
                        "streak_freezes" to (currentFreezes - 1),
                        "dailyHistory.$yesterdayStr" to "FROZEN"
                    ))
                    Log.d("GamificationRepo", "❄️ Streak Freeze auto-porabljen. Ostalo: ${currentFreezes - 1}")
                } else {
                    // Ni freeze-a → streak pade na 0
                    transaction.update(userRef, mapOf(
                        "streak_days" to 0,
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



