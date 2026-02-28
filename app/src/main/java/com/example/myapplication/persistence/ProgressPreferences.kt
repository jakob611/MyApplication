package com.example.myapplication.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.ui.screens.ProgressRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.progressDataStore by preferencesDataStore(name = "progress_prefs")

object ProgressPreferences {
    private val RANGE_KEY = stringPreferencesKey("progress_range")

    fun rangeFlow(context: Context): Flow<ProgressRange> =
        context.progressDataStore.data.map { prefs ->
            val stored = prefs[RANGE_KEY]
            stored?.let { runCatching { ProgressRange.valueOf(it) }.getOrNull() } ?: ProgressRange.WEEK
        }

    suspend fun saveRange(context: Context, range: ProgressRange) {
        context.progressDataStore.edit { prefs ->
            prefs[RANGE_KEY] = range.name
        }
    }
}

