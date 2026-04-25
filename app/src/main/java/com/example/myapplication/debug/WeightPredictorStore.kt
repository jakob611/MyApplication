package com.example.myapplication.debug

/**
 * Singleton za shranjevanje Weight Predictor surovih vrednosti — za Debug Dashboard.
 * Posodablja se ob vsakem preračunu napovedi v ProgressScreen.
 */
object WeightPredictorStore {
    /** EMA-sglajana trenutna teža (kg) */
    @Volatile var lastEmaWeightKg: Double = 0.0

    /** Povprečni dnevni kalorični balans (negativno = deficit) */
    @Volatile var lastAvgDailyBalanceKcal: Double = 0.0

    /** Napoved teže čez 30 dni (kg) */
    @Volatile var last30DayPredictionKg: Double = 0.0

    /** Ciljni datum (niz ISO datum) ali null če cilj ni dosegljiv / ni nastavljen */
    @Volatile var lastGoalDateStr: String? = null

    /** Ciljna teža iz UserProfile (null = ni nastavljena) */
    @Volatile var lastGoalWeightKg: Double? = null

    /** Koliko dni traja, da dosežeš cilj (null = ni dosegljivo/ni cilja) */
    @Volatile var lastDaysToGoal: Int? = null

    /** Koliko aktivnih dni (z vnosi) je bilo v zadnjih 7 dneh */
    @Volatile var lastActiveDaysCount: Int = 0

    /** true ko je napoved izračunana vsaj 1x */
    @Volatile var isReady: Boolean = false
}

