package com.example.myapplication.data.repository

import com.example.myapplication.data.settings.UserPreferencesRepository
import com.example.myapplication.domain.model.UserDayStatus
import com.example.myapplication.domain.repository.WorkoutStats
import com.example.myapplication.domain.repository.WorkoutStatsRepository
import com.example.myapplication.data.store.FirestoreHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Data-layer implementacija WorkoutStatsRepository.
 *
 * Bere podatke iz Firestore (prek FirestoreHelper) in lokalne SharedPrefs (fallback).
 * Wrappira UserProfileManager.getWorkoutStats() logiko v domenski interface.
 *
 * todayIsRest je vedno false — ViewModel izračuna isRestDay iz plan modela
 * (ki je presentation concern, ne domenski concern).
 *
 * @param prefs UserPreferencesRepository za lokalni fallback (SharedPrefs)
 */
class UserWorkoutStatsRepository(
    private val prefs: UserPreferencesRepository
) : WorkoutStatsRepository {

    override suspend fun getWorkoutStats(email: String): WorkoutStats? {
        return try {
            val docRef = FirestoreHelper.getCurrentUserDocRef() ?: return null
            val doc = docRef.get().await()
            if (!doc.exists()) return null

            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            val todayStr = today.toString()

            @Suppress("UNCHECKED_CAST")
            val dailyHistory = (doc.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
            // Faza 21: pretvori surovi Firestore string v tipsko-varni UserDayStatus
            val todayStatus = UserDayStatus.fromFirestore(dailyHistory[todayStr]?.toString())

            // ✅ FIX: weeklyDone se izračuna dinamično iz dailyHistory tekočega tedna (pon–danes).
            // S tem se števec samodejno ponastavi ob novem tednu — ni potrebe po ločenem reset polju.
            val daysFromMonday = today.dayOfWeek.value - 1 // Monday.value=1, Sunday.value=7 → pon=0
            val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
            val weeklyDoneCalc = dailyHistory.entries.count { (dateStr, status) ->
                val s = status.toString()
                if (s != "WORKOUT_DONE" && s != "REST_WORKOUT_DONE") return@count false
                try { val d = LocalDate.parse(dateStr); d in monday..today } catch (_: Exception) { false }
            }

            WorkoutStats(
                streakDays = doc.getLong("streak_days")?.toInt() ?: 0,
                streakFreezes = doc.getLong("streak_freezes")?.toInt() ?: 0,
                weeklyDone = weeklyDoneCalc,
                weeklyTarget = doc.getLong("weekly_target")?.toInt() ?: 3,
                planDay = doc.getLong("plan_day")?.toInt() ?: 1,
                totalWorkoutsCompleted = doc.getLong("total_workouts_completed")?.toInt() ?: 0,
                lastWorkoutEpoch = doc.getLong("last_workout_epoch") ?: 0L,
                todayStatus = todayStatus,
                todayIsRest = false,  // ViewModel izračuna iz plan modela
                dailyKcal = prefs.getDailyCalories().toInt()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Faza 32.9 — Reaktivna Firestore poslušalnica z [callbackFlow].
     *
     * Pritrdi [addSnapshotListener] na dokument trenutnega uporabnika.
     * Ob vsakem Firestore eventu mapira snapshot v [WorkoutStats] in ga pošlje dolvodnemu
     * zbiralcu prek [trySend]. Ob napaki ali odhodu zbiralca [awaitClose] pravilno
     * odstrani poslušalnico — brez memory leakov.
     *
     * Napaka v poslušalnici se propagira prek [close(error)] →
     * ViewModel catch blok jo ujame in prikaže ShowSnackbar.
     */
    override fun observeWorkoutStats(email: String): Flow<WorkoutStats?> = callbackFlow {
        val docRef = FirestoreHelper.getCurrentUserDocRef() ?: run {
            // Ni avtenticiranega uporabnika — propagiramo napako navzgor
            close(IllegalStateException("User not authenticated — cannot observe workout stats"))
            return@callbackFlow
        }

        // Pre-fetch lokalnih kalorij v callbackFlow suspend kontekstu (pred listener-jem)
        val localKcal = prefs.getDailyCalories().toInt()

        val registration = docRef.addSnapshotListener { snapshot, error ->
            // Firestore je sporočil napako (izguba mreže, auth revokacija itd.)
            // close(error) propagira izjemo do collect {} catch bloka v UseCase-u
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            // Dokument ne obstaja (prvi vpis ali izbrisan profil) → null signal
            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }

            // Varno mapiranje snapshot-a → WorkoutStats
            try {
                val today = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                val todayStr = today.toString()

                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (snapshot.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                val todayStatus  = UserDayStatus.fromFirestore(dailyHistory[todayStr]?.toString())

                val daysFromMonday = today.dayOfWeek.value - 1
                val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
                val weeklyDoneCalc = dailyHistory.entries.count { (dateStr, status) ->
                    val s = status.toString()
                    if (s != "WORKOUT_DONE" && s != "REST_WORKOUT_DONE") return@count false
                    try { val d = LocalDate.parse(dateStr); d in monday..today } catch (_: Exception) { false }
                }

                trySend(WorkoutStats(
                    streakDays             = snapshot.getLong("streak_days")?.toInt() ?: 0,
                    streakFreezes          = snapshot.getLong("streak_freezes")?.toInt() ?: 0,
                    weeklyDone             = weeklyDoneCalc,
                    weeklyTarget           = snapshot.getLong("weekly_target")?.toInt() ?: 3,
                    planDay                = snapshot.getLong("plan_day")?.toInt() ?: 1,
                    totalWorkoutsCompleted = snapshot.getLong("total_workouts_completed")?.toInt() ?: 0,
                    lastWorkoutEpoch       = snapshot.getLong("last_workout_epoch") ?: 0L,
                    todayStatus            = todayStatus,
                    todayIsRest            = false,
                    dailyKcal              = localKcal
                ))
            } catch (e: Exception) {
                // Mapiranje snapshot-a je spodletelo — propagiraj napako
                close(e)
            }
        }

        // Ko zbiralec prekine (navigacija, lifecycle, cancel) — odstrani poslušalnico
        awaitClose { registration.remove() }
    }

    override suspend fun isWorkoutDoneToday(): Boolean = prefs.isWorkoutDoneToday()

    override suspend fun getPlanDay(): Int = prefs.getPlanDay()

    override suspend fun getDailyCalories(): Int = prefs.getDailyCalories().toInt()
}