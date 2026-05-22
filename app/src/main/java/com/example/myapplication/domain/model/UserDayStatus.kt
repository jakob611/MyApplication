package com.example.myapplication.domain.model

/**
 * SSOT za stanje trenutnega dne v planu.
 *
 * Nadomešča raztresene String konstante ("WORKOUT_DONE", "STRETCHING_DONE", ...)
 * po vsej kodi z enim tipsko-varnim enum razredom.
 *
 * Firestore shrani vrednost [firestoreValue] — npr. "WORKOUT_DONE".
 * Branje: [UserDayStatus.fromFirestore(rawString)].
 *
 * KMP-ready: brez Android odvisnosti.
 */
enum class UserDayStatus(val firestoreValue: String) {

    /** Workout dan — trening še ni opravljen. Privzeto za delovne dni. */
    WORKOUT_PENDING(""),

    /** Trening uspešno opravljen. Streak +1, plan_day +1 (redni workout). */
    WORKOUT_DONE("WORKOUT_DONE"),

    /** Rest dan — raztezanje še čaka. Privzeto za počitniške dni. */
    REST_DAY_PENDING("PENDING_STRETCHING"),

    /** Raztezanje na rest dnevu opravljeno. Streak +1, plan_day ostane. */
    REST_DAY_DONE("STRETCHING_DONE"),

    /** Dodatni trening na rest dnevu. Streak ohranjen (ne poveča), plan_day ostane. */
    REST_WORKOUT_DONE("REST_WORKOUT_DONE"),

    /** Streak Freeze je bil porabljen za ta dan. Streak ohranjen. */
    FROZEN("FROZEN"),

    /** Dan zamešan brez aktivnosti, freeze ni bil na voljo. Streak = 0. */
    MISSED("MISSED");

    // ── Pomožne lastnosti ────────────────────────────────────────────────────

    /**
     * Vrne [true] kadar dan šteje kot "zaključen" — ni potrebna nadaljnja akcija.
     * Pokriva: redni workout, raztezanje, dodatni rest-day workout.
     */
    val isDoneToday: Boolean
        get() = this == WORKOUT_DONE || this == REST_DAY_DONE || this == REST_WORKOUT_DONE

    /**
     * Vrne [true] kadar ta status prispeva k streak +1.
     * REST_WORKOUT_DONE ohrani streak (ne poveča).
     */
    val contributesToStreak: Boolean
        get() = this == WORKOUT_DONE || this == REST_DAY_DONE

    /**
     * Vrne [true] kadar plan_day mora napredovati za +1.
     * Samo redni workout (ne extra, ne stretching).
     */
    val shouldIncrementPlanDay: Boolean
        get() = this == WORKOUT_DONE

    // ── Companion ────────────────────────────────────────────────────────────

    companion object {
        /**
         * Pretvori surovi Firestore string v tipsko-varni [UserDayStatus].
         *
         * @param value Raw string iz Firestore dailyHistory (ali null če ni vnosa).
         * @param isRestDay Če [true] in [value] je null/prazen → vrne [REST_DAY_PENDING]
         *                  namesto [WORKOUT_PENDING].
         */
        fun fromFirestore(value: String?, isRestDay: Boolean = false): UserDayStatus =
            when (value) {
                "WORKOUT_DONE"       -> WORKOUT_DONE
                "STRETCHING_DONE"    -> REST_DAY_DONE
                "PENDING_STRETCHING" -> REST_DAY_PENDING
                "REST_WORKOUT_DONE"  -> REST_WORKOUT_DONE
                "FROZEN"             -> FROZEN
                "MISSED"             -> MISSED
                else                 -> if (isRestDay) REST_DAY_PENDING else WORKOUT_PENDING
            }
    }
}

