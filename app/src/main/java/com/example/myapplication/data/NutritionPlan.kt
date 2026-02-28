package com.example.myapplication.data

import com.example.myapplication.ui.screens.AlgorithmData

/**
 * NutritionPlan - Ločen plan samo za nutrition (kalorije/makroji)
 * Ta plan se avtomatsko posodablja ob vnosu nove teže, medtem ko training plan ostane nespremenjen
 */
data class NutritionPlan(
    val calories: Int = 0,
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
    val algorithmData: AlgorithmData? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
