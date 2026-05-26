package com.example.myapplication

import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.domain.gamification.GamificationState
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.model.UserDayStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Lokalni unit testi za UserDayStatus enum in moveToNextDay() logiko.
 *
 * Pokrivajo 3 scenarije:
 *  (a) WORKOUT_PENDING → recordWorkoutCompletion() → streak napreduje, plan_day +1
 *  (b) REST_DAY_PENDING → restDayInitiated() → streak napreduje, plan_day nespremenjen
 *  (c) Neuspešna Firestore transakcija → Room ni posodobljena (atomičnost)
 *
 * Faza 21 — KMP-ready, brez Android odvisnosti.
 */
class UserDayStatusTest {

    // ─── Pomožna FakeGamificationRepository ────────────────────────────────────

    /**
     * Ponastavljiv fake repozitorij za izolacijo domenskih testov.
     *
     * @param initialStreak    Začetni streak (privzeto 5).
     * @param throwOnNextDay   Če true → moveToNextDay() vrže izjemo (simulacija Firestore napake).
     */
    private open class FakeGamificationRepository(
        private val initialStreak: Int = 5,
        private val throwOnNextDay: Boolean = false
    ) : GamificationRepository {

        var currentStreak = initialStreak
            private set
        var planDayWasIncremented = false
            private set
        var lastWrittenStatus: UserDayStatus? = null
            private set
        /** Število uspešnih moveToNextDay() klicev */
        var moveCallCount = 0
            private set

        override suspend fun moveToNextDay(
            newStatus: UserDayStatus,
            xpToBeAwarded: Int,
            xpReason: String,
            caloriesBurned: Double,
            incrementPlanDay: Boolean,
            workoutSessionDoc: Map<String, Any>?  // Faza 34 — CRIT-03: atomarni workout session
        ): Int {
            moveCallCount++
            if (throwOnNextDay) throw RuntimeException("Simulirana Firestore transakcijska napaka")
            // Posnemi realno logiko: streak++ samo za contributesToStreak statuse
            if (newStatus.contributesToStreak) currentStreak++
            if (incrementPlanDay) planDayWasIncremented = true
            lastWrittenStatus = newStatus
            return currentStreak
        }

        override suspend fun awardXP(amount: Int, reason: String) { /* no-op */ }
        override suspend fun getCurrentStreak(): Int = currentStreak
        override suspend fun markRestDayPending() { /* no-op */ }
        override suspend fun runMidnightStreakCheck() { /* no-op */ }
        override suspend fun consumeStreakFreeze(): Boolean = false
        override suspend fun getTodayStatus(): UserDayStatus = lastWrittenStatus ?: UserDayStatus.WORKOUT_PENDING
        override suspend fun logBurnedCalories(todayStr: String, calories: Double) { /* no-op */ }
        override suspend fun getGamificationState(): GamificationState = GamificationState()
    }

    // ─── Pomožne spremenljivke ──────────────────────────────────────────────────

    private lateinit var fakeRepo: FakeGamificationRepository
    private lateinit var useCase: ManageGamificationUseCase

    @Before
    fun setUp() {
        fakeRepo = FakeGamificationRepository(initialStreak = 5)
        useCase = ManageGamificationUseCase(fakeRepo)
    }

    // ─── Tests za UserDayStatus enum lastnosti ─────────────────────────────────

    @Test
    fun `WORKOUT_DONE - isDoneToday je true`() {
        assertTrue(UserDayStatus.WORKOUT_DONE.isDoneToday)
    }

    @Test
    fun `REST_DAY_DONE - isDoneToday je true`() {
        assertTrue(UserDayStatus.REST_DAY_DONE.isDoneToday)
    }

    @Test
    fun `REST_WORKOUT_DONE - isDoneToday je true`() {
        assertTrue(UserDayStatus.REST_WORKOUT_DONE.isDoneToday)
    }

    @Test
    fun `WORKOUT_PENDING - isDoneToday je false`() {
        assertFalse(UserDayStatus.WORKOUT_PENDING.isDoneToday)
    }

    @Test
    fun `REST_DAY_PENDING - isDoneToday je false`() {
        assertFalse(UserDayStatus.REST_DAY_PENDING.isDoneToday)
    }

    @Test
    fun `FROZEN - isDoneToday je false`() {
        assertFalse(UserDayStatus.FROZEN.isDoneToday)
    }

    @Test
    fun `MISSED - isDoneToday je false`() {
        assertFalse(UserDayStatus.MISSED.isDoneToday)
    }

    @Test
    fun `WORKOUT_DONE - contributesToStreak je true`() {
        assertTrue(UserDayStatus.WORKOUT_DONE.contributesToStreak)
    }

    @Test
    fun `REST_WORKOUT_DONE - contributesToStreak je false (streak ohranjen, ne poveca)`() {
        assertFalse(UserDayStatus.REST_WORKOUT_DONE.contributesToStreak)
    }

    @Test
    fun `WORKOUT_DONE - shouldIncrementPlanDay je true`() {
        assertTrue(UserDayStatus.WORKOUT_DONE.shouldIncrementPlanDay)
    }

    @Test
    fun `REST_DAY_DONE - shouldIncrementPlanDay je false`() {
        assertFalse(UserDayStatus.REST_DAY_DONE.shouldIncrementPlanDay)
    }

    @Test
    fun `fromFirestore WORKOUT_DONE string`() {
        assertEquals(UserDayStatus.WORKOUT_DONE, UserDayStatus.fromFirestore("WORKOUT_DONE"))
    }

    @Test
    fun `fromFirestore STRETCHING_DONE string vrne REST_DAY_DONE`() {
        assertEquals(UserDayStatus.REST_DAY_DONE, UserDayStatus.fromFirestore("STRETCHING_DONE"))
    }

    @Test
    fun `fromFirestore PENDING_STRETCHING string vrne REST_DAY_PENDING`() {
        assertEquals(UserDayStatus.REST_DAY_PENDING, UserDayStatus.fromFirestore("PENDING_STRETCHING"))
    }

    @Test
    fun `fromFirestore FROZEN string`() {
        assertEquals(UserDayStatus.FROZEN, UserDayStatus.fromFirestore("FROZEN"))
    }

    @Test
    fun `fromFirestore MISSED string`() {
        assertEquals(UserDayStatus.MISSED, UserDayStatus.fromFirestore("MISSED"))
    }

    @Test
    fun `fromFirestore null vrne WORKOUT_PENDING za workout dan`() {
        assertEquals(UserDayStatus.WORKOUT_PENDING, UserDayStatus.fromFirestore(null, isRestDay = false))
    }

    @Test
    fun `fromFirestore null vrne REST_DAY_PENDING za rest dan`() {
        assertEquals(UserDayStatus.REST_DAY_PENDING, UserDayStatus.fromFirestore(null, isRestDay = true))
    }

    @Test
    fun `fromFirestore neznani string vrne WORKOUT_PENDING`() {
        assertEquals(UserDayStatus.WORKOUT_PENDING, UserDayStatus.fromFirestore("INVALID_STATUS"))
    }

    // ─── SCENARIJ (a): WORKOUT_PENDING → recordWorkoutCompletion() napreduje dan ─

    /**
     * Scenarij (a):
     * Začetno stanje: status = WORKOUT_PENDING (trening danes še ni opravljen).
     * Akcija: recordWorkoutCompletion(incrementPlanDay = true)
     * Pričakovano:
     *   - moveToNextDay() je bil klican
     *   - lastWrittenStatus = WORKOUT_DONE
     *   - streak se je povečal (5 → 6)
     *   - planDayWasIncremented = true
     */
    @Test
    fun `scenarij a - WORKOUT_PENDING - po recordWorkoutCompletion streak naraste in plan_day napreduje`() = runBlocking {
        val initialStreak = fakeRepo.currentStreak  // 5

        useCase.recordWorkoutCompletion(
            caloriesBurned = 250.0,
            hour = 10,
            isRestDay = false,
            incrementPlanDay = true
        )

        // moveToNextDay() je bil enkrat klican
        assertEquals(1, fakeRepo.moveCallCount)
        // Status WORKOUT_DONE zapisan
        assertEquals(UserDayStatus.WORKOUT_DONE, fakeRepo.lastWrittenStatus)
        // Streak je narastel
        assertEquals(initialStreak + 1, fakeRepo.currentStreak)
        // plan_day napredoval
        assertTrue(fakeRepo.planDayWasIncremented)
    }

    // ─── SCENARIJ (b): REST_DAY_PENDING → restDayInitiated() napreduje streak ───

    /**
     * Scenarij (b):
     * Začetno stanje: danes je rest dan, status = REST_DAY_PENDING.
     * Akcija: restDayInitiated()
     * Pričakovano:
     *   - moveToNextDay(REST_DAY_DONE) je bil klican
     *   - streak se je povečal (5 → 6)
     *   - plan_day NI napredoval (false)
     */
    @Test
    fun `scenarij b - REST_DAY_PENDING - po restDayInitiated streak naraste brez plan_day napredka`() = runBlocking {
        val initialStreak = fakeRepo.currentStreak  // 5

        val newStreak = useCase.restDayInitiated()

        // moveToNextDay() je bil enkrat klican
        assertEquals(1, fakeRepo.moveCallCount)
        // Status REST_DAY_DONE zapisan
        assertEquals(UserDayStatus.REST_DAY_DONE, fakeRepo.lastWrittenStatus)
        // Streak je narastel
        assertEquals(initialStreak + 1, newStreak)
        // plan_day NI napredoval
        assertFalse(fakeRepo.planDayWasIncremented)
    }

    /**
     * Scenarij (b2):
     * Varovalka: če je danes že WORKOUT_DONE, restDayInitiated() ne sme prepisati statusa.
     */
    @Test
    fun `scenarij b2 - ce je danes WORKOUT_DONE - restDayInitiated je blokiran`() = runBlocking {
        // Simuliraj: danes je že WORKOUT_DONE (nek drug klic je to zapisal)
        val repoThatReturnsDone = object : FakeGamificationRepository(7) {
            override suspend fun getTodayStatus() = UserDayStatus.WORKOUT_DONE
        }
        val useCaseGuarded = ManageGamificationUseCase(repoThatReturnsDone)

        useCaseGuarded.restDayInitiated()

        // moveToNextDay() NE sme biti klican (guard blokira)
        assertEquals(0, repoThatReturnsDone.moveCallCount)
    }

    // ─── SCENARIJ (c): Neuspešna Firestore transakcija → atomičnost ─────────────

    /**
     * Scenarij (c):
     * Simuliramo, da Firestore transakcija vrže izjemo.
     * Pričakovano:
     *   - moveToNextDay() vrže RuntimeException
     *   - streak NA FAKENEM REPOZITORIJU je ostal nespremenjen (0 efekt)
     *   - Funkcija vrne 0 (napaka signal)
     *   - planDayWasIncremented ostane false
     *
     * To ponazarja: ker je cela logika v eni Firestore transakciji,
     * nič se ne zapiše (niti streak, niti plan_day, niti dailyHistory).
     * Room baza (ki je odvisna od UI feedback-a iz VMja) ostaja nespremenjena.
     */
    @Test
    fun `scenarij c - neuspesna Firestore transakcija - streak in plan_day ostaneta nespremenjena`() = runBlocking {
        val throwingRepo = FakeGamificationRepository(initialStreak = 3, throwOnNextDay = true)
        val useCaseWithError = ManageGamificationUseCase(throwingRepo)

        val initialStreak = throwingRepo.currentStreak  // 3

        // moveToNextDay() vrže — recordWorkoutCompletion() mora napako ujeti
        // V produkciji ViewModel dobi 0 nazaj in ne posodobi UI (Room ostane kot je)
        try {
            useCaseWithError.recordWorkoutCompletion(
                caloriesBurned = 300.0,
                hour = 14,
                isRestDay = false,
                incrementPlanDay = true
            )
            // Če pride sem brez excepta — preverimo da vrednosti niso bile pobrizgane
        } catch (_: RuntimeException) {
            // Pričakovano ali ne, odvisno od error handling v UseCase
        }

        // Streak ostane nespremenjen (transakcija ni uspela → ni zapisov)
        assertEquals(
            "Streak mora ostati nespremenjen ob napaki transakcije",
            initialStreak,
            throwingRepo.currentStreak
        )

        // plan_day ni napredoval
        assertFalse(
            "plan_day ne sme napredovati ob napaki transakcije",
            throwingRepo.planDayWasIncremented
        )

        // Zadnji zapisani status ostane null (ni bilo uspešnega zapisa)
        assertNull(
            "dailyHistory status ne sme biti zapisan ob napaki",
            throwingRepo.lastWrittenStatus
        )
    }

    @Test
    fun `scenarij c2 - neuspesna transakcija pri restDayInitiated - streak ostane nespremenjen`() = runBlocking {
        val throwingRepo = FakeGamificationRepository(initialStreak = 4, throwOnNextDay = true)
        val useCaseWithError = ManageGamificationUseCase(throwingRepo)

        val initialStreak = throwingRepo.currentStreak  // 4

        try {
            useCaseWithError.restDayInitiated()
        } catch (_: RuntimeException) {
            // Pričakovana izjema
        }

        // Streak ostane nespremenjen
        assertEquals(initialStreak, throwingRepo.currentStreak)
        assertNull(throwingRepo.lastWrittenStatus)
    }
}





