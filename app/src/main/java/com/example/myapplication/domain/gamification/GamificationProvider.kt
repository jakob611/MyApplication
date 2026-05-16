package com.example.myapplication.domain.gamification

/**
 * ⚠️ DEPRECATED — ta razred je bil premaknjen v data/gamification/GamificationFactory.kt
 *
 * GamificationProvider je bil factory ki je klical data layer konstruktorje (AndroidFirestore, SharedPrefs).
 * To krši Clean Architecture — factory (composition root) ne sme biti v domain sloju.
 *
 * @see com.example.myapplication.data.gamification.GamificationFactory
 *
 * Ostane kot kompilacijska lupina za backward compatibility, ne vsebuje logike.
 * Posodobitev kličočih krajev: zamenjaj z GamificationFactory.provide(context).
 */
@Deprecated("Premaknjeno v data/gamification/GamificationFactory.kt",
    replaceWith = ReplaceWith("GamificationFactory.provide(context)",
        "com.example.myapplication.data.gamification.GamificationFactory"))
object GamificationProvider
