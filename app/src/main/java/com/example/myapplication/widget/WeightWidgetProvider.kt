package com.example.myapplication.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class WeightWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Weight widget enabled - first instance added")
        // Initial sync from Firestore to local cache
        syncFromFirestore(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Weight widget disabled - last instance removed")
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} weight widget(s)")
        appWidgetIds.forEach { id ->
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")
        when (intent.action) {
            ACTION_INCREMENT -> handleDelta(context, +STEP_KG)
            ACTION_DECREMENT -> handleDelta(context, -STEP_KG)
            ACTION_REFRESH -> syncFromFirestore(context)
            ACTION_OPEN_INPUT -> openManualInput(context)
        }
    }

    private fun openManualInput(context: Context) {
        // Open lightweight transparent Activity with just the input dialog
        val intent = Intent(context, WeightInputActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun syncFromFirestore(context: Context) {
        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        if (uid == null) {
            // Not logged in, just refresh with local cache
            refreshAll(context)
            return
        }

        val today = todayId()
        val db = Firebase.firestore
        db.collection("users").document(uid)
            .collection("dailyMetrics").document(today)
            .get()
            .addOnSuccessListener { snap ->
                val serverVal = (snap.get("weight") as? Number)?.toFloat() ?: 0f
                // Update local cache with server value
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().putFloat("weight_$today", serverVal).apply()
                // Refresh widget UI
                refreshAll(context)
                Log.d(TAG, "Synced weight from Firestore: $serverVal kg")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Sync weight from Firestore failed", e)
                // Still refresh with cached value
                refreshAll(context)
            }
    }

    private fun handleDelta(context: Context, delta: Float) {

        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        val today = todayId()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "weight_$today"

        // INSTANT UPDATE: Read current value, update locally, refresh UI immediately
        val currentVal = prefs.getFloat(key, 0f)
        val newVal = (currentVal + delta).coerceAtLeast(0f)
        prefs.edit().putFloat(key, newVal).apply()

        // Update widget UI instantly (no waiting for Firestore)
        refreshAll(context)

        // Background sync to Firestore (if logged in)
        if (uid != null) {
            val db = Firebase.firestore

            // Save to dailyMetrics (for widget sync)
            val metricsRef = db.collection("users").document(uid)
                .collection("dailyMetrics").document(today)
            metricsRef.set(
                mapOf("date" to today, "weight" to newVal, "updatedAt" to FieldValue.serverTimestamp()),
                com.google.firebase.firestore.SetOptions.merge()
            )

            // Also save to weightLogs (for Progress chart)
            val logsRef = db.collection("users").document(uid)
                .collection("weightLogs").document(today)
            logsRef.set(
                mapOf("date" to today, "weightKg" to newVal.toDouble(), "updatedAt" to FieldValue.serverTimestamp()),
                com.google.firebase.firestore.SetOptions.merge()
            ).addOnFailureListener { e ->
                Log.w(TAG, "Background sync failed for weight=$newVal", e)
            }
        }
    }

    private fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, WeightWidgetProvider::class.java))
        ids.forEach { id -> updateAppWidget(context, mgr, id) }
    }

    companion object {
        private const val TAG = "WeightWidget"
        private const val PREFS = "weight_widget_prefs"
        private const val STEP_KG = 0.5f

        const val ACTION_INCREMENT = "com.example.myapplication.widget.WEIGHT_INCREMENT"
        const val ACTION_DECREMENT = "com.example.myapplication.widget.WEIGHT_DECREMENT"
        const val ACTION_REFRESH = "com.example.myapplication.widget.WEIGHT_REFRESH"
        const val ACTION_OPEN_INPUT = "com.example.myapplication.widget.WEIGHT_OPEN_INPUT"

        private fun todayId(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val layoutId = context.resources.getIdentifier("widget_weight", "layout", context.packageName)
            val views = RemoteViews(context.packageName, layoutId)

            val minusId = context.resources.getIdentifier("btnWeightMinus", "id", context.packageName)
            val plusId = context.resources.getIdentifier("btnWeightPlus", "id", context.packageName)
            val rootId = context.resources.getIdentifier("rootWeight", "id", context.packageName)
            val txtId = context.resources.getIdentifier("txtWeightAmount", "id", context.packageName)

            views.setOnClickPendingIntent(minusId, pendingBroadcast(context, ACTION_DECREMENT))
            views.setOnClickPendingIntent(plusId, pendingBroadcast(context, ACTION_INCREMENT))
            views.setOnClickPendingIntent(rootId, pendingBroadcast(context, ACTION_OPEN_INPUT))
            views.setOnClickPendingIntent(txtId, pendingBroadcast(context, ACTION_OPEN_INPUT))

            // INSTANT DISPLAY: Always read from local cache first for immediate response
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = todayId()
            val key = "weight_$today"
            val cachedVal = prefs.getFloat(key, 0f)

            val displayText = if (cachedVal > 0f) {
                String.format("%.1f kg", cachedVal)
            } else {
                "-- kg"
            }

            views.setTextViewText(txtId, displayText)
            manager.updateAppWidget(appWidgetId, views)

            // Background: Sync from Firestore only on explicit refresh (not on every update)
        }

        private fun pendingBroadcast(context: Context, action: String): PendingIntent {
            val intent = Intent(context, WeightWidgetProvider::class.java).apply { this.action = action }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, action.hashCode(), intent, flags)
        }

        // Public method for app to update widget when weight changes
        fun updateWidgetFromApp(context: Context, weightKg: Float) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = todayId()
            prefs.edit().putFloat("weight_$today", weightKg).apply()

            // Refresh all widget instances
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WeightWidgetProvider::class.java))
            ids.forEach { id -> updateAppWidget(context, mgr, id) }
        }
    }
}

