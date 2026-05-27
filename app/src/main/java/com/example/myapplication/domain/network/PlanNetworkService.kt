package com.example.myapplication.domain.network

import com.example.myapplication.domain.model.PlanResult

/**
 * Faza 43 — Domenski vmesnik za mrežne operacije generiranja planav (SRP fix).
 *
 * RAZLOG OBSTOJA:
 *   Anomaly 5: `PlanDataStore` je mešal lokalno/Firestore persistenco z OkHttp HTTP klici.
 *   Ta vmesnik STROGO ločuje mrežni dostop od persistance plasti.
 *
 * ARHITEKTURNA PRAVILA:
 *   - Implementacija živi v data sloju: `data.network.PlanApiClient`
 *   - Klicatelji (ViewModel, UseCase) poznajo SAMO ta vmesnik — brez OkHttp odvisnosti v višjih plasteh
 *   - `PlanDataStore` NE implementira tega vmesnika (samo persistenca)
 *
 * KMP-READY: Brez Android odvisnosti.
 */
interface PlanNetworkService {

    /**
     * Asinhrono generira trening plan prek oddaljene AI API storitve.
     *
     * @param quizData Surovi kviz podatki (gender, age, goal, experience, limitations, ...).
     *                 Ključi: "user_id", "user_email", "gender", "age", "height", "weight",
     *                         "goal", "experience", "training_location", "trainingDays",
     *                         "limitations", "nutrition", "sleep".
     * @return [Result.success] z generiranim [PlanResult] ali [Result.failure] z opisno napako.
     *         Timeout napaka: "AI is taking longer than expected. Please try again or use the local plan."
     *         Mrežna napaka: "Connection error: ..."
     *         Strežniška napaka: "Server error {code}: ..."
     */
    suspend fun generatePlan(quizData: Map<String, Any>): Result<PlanResult>
}


