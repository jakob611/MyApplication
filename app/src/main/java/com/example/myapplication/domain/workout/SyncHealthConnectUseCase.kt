package com.example.myapplication.domain.workout

import android.content.Context
import android.util.Log
import com.example.myapplication.health.HealthConnectManager
import com.example.myapplication.persistence.FirestoreHelper
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime

class SyncHealthConnectUseCase {
    suspend operator fun invoke(context: Context) {
        val healthManager = HealthConnectManager.getInstance(context)
        if (!healthManager.isAvailable() || !healthManager.hasAllPermissions()) {
            return
        }

        try {
            val now = Clock.System.now().toJavaInstant()
            val tz = TimeZone.currentSystemDefault()
            val startOfDay = Clock.System.now().toLocalDateTime(tz).date
            val startOfDayJavaObj = java.time.LocalDate.of(startOfDay.year, startOfDay.monthNumber, startOfDay.dayOfMonth).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            val todayId = startOfDay.toString()

            val uid = FirestoreHelper.getCurrentUserDocId() ?: return
            val db = FirestoreHelper.getDb()
            val ref = db.collection("users").document(uid).collection("dailyLogs").document(todayId)

            val doc = ref.get().await()
            val serverBurned = (doc.get("burnedCalories") as? Number)?.toInt() ?: 0

            val healthConnectCalories = healthManager.readCalories(startOfDayJavaObj, now)

            val prefs = context.getSharedPreferences("hc_sync_prefs", Context.MODE_PRIVATE)
            val lastSyncedKey = "hc_kcal_$todayId"
            val lastSyncedHcKcal = prefs.getInt(lastSyncedKey, 0)

            val delta = healthConnectCalories - lastSyncedHcKcal

            if (delta > 0) {
                ref.set(mapOf(
                    "date" to todayId,
                    "burnedCalories" to com.google.firebase.firestore.FieldValue.increment(delta.toDouble())
                ), com.google.firebase.firestore.SetOptions.merge()).await()

                prefs.edit().putInt(lastSyncedKey, healthConnectCalories).apply()
                Log.d("DEBUG_DATA", "Successfully synced Health Connect delta: $delta")
            } else if (healthConnectCalories > 0 && serverBurned == 0) {
                // Initial sync fallback if delta logic failed previously
                 ref.set(mapOf(
                    "date" to todayId,
                    "burnedCalories" to healthConnectCalories
                ), com.google.firebase.firestore.SetOptions.merge()).await()
                prefs.edit().putInt(lastSyncedKey, healthConnectCalories).apply()
                Log.d("DEBUG_DATA", "Initial synced Health Connect: $healthConnectCalories")
            }

        } catch (e: Exception) {
            Log.e("SyncHealthConnect", "Failed to sync health connect data", e)
        }
    }
}
