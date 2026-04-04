package com.example.myapplication.persistence

import android.content.Context
import com.example.myapplication.network.FoodSummary
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object RecentFoodStore {
    private const val PREFS_NAME = "recent_foods_prefs"
    private const val KEY_RECENT_FOODS = "recent_foods_list"
    private const val MAX_RECENT_FOODS = 20

    fun getRecentFoods(context: Context): List<FoodSummary> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECENT_FOODS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FoodSummary>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecentFood(context: Context, food: FoodSummary) {
        val current = getRecentFoods(context).toMutableList()
        // Remove if it already exists, to move it to the top
        current.removeAll { it.id == food.id }
        current.add(0, food)
        
        val trimmed = current.take(MAX_RECENT_FOODS)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECENT_FOODS, Gson().toJson(trimmed)).apply()
    }
}

