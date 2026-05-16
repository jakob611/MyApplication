package com.example.myapplication.data.gamification

import android.content.Context
import com.example.myapplication.domain.gamification.ManageGamificationUseCase

/**
 * GamificationFactory — composition root za ManageGamificationUseCase.
 *
 * Pravilna lokacija: data/ sloj (factory sme imeti dostop do concrete implementacij).
 * Nadomešča domain/gamification/GamificationProvider (ki je bil v napačnem sloju).
 *
 * Singleton pattern: isti UseCase za celo aplikacijo (Firestore listener-ji se ne podvoijo).
 */
object GamificationFactory {
    @Volatile
    private var instance: ManageGamificationUseCase? = null

    fun provide(@Suppress("UNUSED_PARAMETER") context: Context): ManageGamificationUseCase {
        return instance ?: synchronized(this) {
            instance ?: ManageGamificationUseCase(
                repository = FirestoreGamificationRepository()
            ).also { instance = it }
        }
    }
}
