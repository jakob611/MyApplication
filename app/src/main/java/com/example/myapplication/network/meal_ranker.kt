package com.example.myapplication.network

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

data class MealFeatures(
    val kcalDiff: Float,
    val proteinDiff: Float,
    val carbsDiff: Float,
    val fatDiff: Float,
    val mealType: Int,
    val timeOfDay: Int,
    val vegan: Int,
    val vegetarian: Int,
    val glutenFree: Int,
    val halal: Int,
    val country: Int,
    val like: Int,
    val recentUsage: Int
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        kcalDiff, proteinDiff, carbsDiff, fatDiff, mealType.toFloat(), timeOfDay.toFloat(),
        vegan.toFloat(), vegetarian.toFloat(), glutenFree.toFloat(), halal.toFloat(),
        country.toFloat(), like.toFloat(), recentUsage.toFloat()
    )
}

class MealRanker(context: Context) {
    private val tflite: Interpreter

    init {
        val modelFile = FileUtil.loadMappedFile(context, "meal_ranker.tflite")
        tflite = Interpreter(modelFile)
    }

    fun rankMeals(featuresList: List<MealFeatures>): List<Float> {
        val input = Array(featuresList.size) { featuresList[it].toFloatArray() }
        val output = Array(featuresList.size) { FloatArray(1) }
        tflite.run(input, output)
        return output.map { it[0] }
    }
}