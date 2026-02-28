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

class WaterWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled - first instance added")
        // Initial sync from Firestore to local cache
        syncFromFirestore(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled - last instance removed")
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widget(s)")
        appWidgetIds.forEach { id ->
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")
        when (intent.action) {
            ACTION_INCREMENT -> handleDelta(context, +STEP_ML)
            ACTION_DECREMENT -> handleDelta(context, -STEP_ML)
            ACTION_REFRESH -> syncFromFirestore(context)
            ACTION_OPEN_INPUT -> openManualInput(context)
        }
    }

    private fun openManualInput(context: Context) {
        // Open lightweight transparent Activity with just the input dialog
        val intent = Intent(context, WaterInputActivity::class.java).apply {
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
            .collection("dailyLogs").document(today)
            .get()
            .addOnSuccessListener { snap ->
                val serverVal = (snap.get("waterMl") as? Number)?.toInt() ?: 0
                // Update local cache with server value
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().putInt("water_$today", serverVal).apply()
                // Refresh widget UI
                refreshAll(context)
                Log.d(TAG, "Synced from Firestore: $serverVal ml")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Sync from Firestore failed", e)
                // Still refresh with cached value
                refreshAll(context)
            }
    }

    private fun handleDelta(context: Context, delta: Int) {

        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        val today = todayId()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "water_$today"

        // INSTANT UPDATE: Read current value, update locally, refresh UI immediately
        val currentVal = prefs.getInt(key, 0)
        val newVal = (currentVal + delta).coerceAtLeast(0)
        prefs.edit().putInt(key, newVal).apply()

        // Update widget UI instantly (no waiting for Firestore)
        refreshAll(context)

        // Background sync to Firestore (if logged in)
        if (uid != null) {
            val db = Firebase.firestore
            val ref = db.collection("users").document(uid)
                .collection("dailyLogs").document(today)

            // Use simple set (not transaction) for speed - eventual consistency is OK for widget
            val data = mapOf(
                "date" to today,
                "waterMl" to newVal,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener { e ->
                    Log.w(TAG, "Background sync failed for waterMl=$newVal", e)
                    // UI already updated, user doesn't see failure
                }
        }
    }

    private fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
        ids.forEach { id -> updateAppWidget(context, mgr, id) }
    }

    companion object {
        private const val TAG = "WaterWidget"
        private const val PREFS = "water_widget_prefs"
        private const val STEP_ML = 50

        const val ACTION_INCREMENT = "com.example.myapplication.widget.WATER_INCREMENT"
        const val ACTION_DECREMENT = "com.example.myapplication.widget.WATER_DECREMENT"
        const val ACTION_REFRESH = "com.example.myapplication.widget.WATER_REFRESH"
        const val ACTION_OPEN_INPUT = "com.example.myapplication.widget.WATER_OPEN_INPUT"

        private fun todayId(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val layoutId = context.resources.getIdentifier("widget_water", "layout", context.packageName)
            val views = RemoteViews(context.packageName, layoutId)

            val minusId = context.resources.getIdentifier("btnMinus", "id", context.packageName)
            val plusId = context.resources.getIdentifier("btnPlus", "id", context.packageName)
            val rootId = context.resources.getIdentifier("root", "id", context.packageName)
            val txtId = context.resources.getIdentifier("txtAmount", "id", context.packageName)

            views.setOnClickPendingIntent(minusId, pendingBroadcast(context, ACTION_DECREMENT))
            views.setOnClickPendingIntent(plusId, pendingBroadcast(context, ACTION_INCREMENT))
            views.setOnClickPendingIntent(rootId, pendingBroadcast(context, ACTION_REFRESH))
            views.setOnClickPendingIntent(txtId, pendingBroadcast(context, ACTION_OPEN_INPUT))

            // INSTANT DISPLAY: Always read from local cache first for immediate response
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = todayId()
            val key = "water_$today"
            val cachedVal = prefs.getInt(key, 0)

            val displayText = "$cachedVal ml"
            views.setTextViewText(txtId, displayText)

            // Adjust text size based on number of digits (4+ digits = smaller font)
            val textSize = if (cachedVal >= 1000) 15f else 18f // 15sp for 4+ digits, 18sp otherwise
            views.setTextViewTextSize(txtId, android.util.TypedValue.COMPLEX_UNIT_SP, textSize)

            manager.updateAppWidget(appWidgetId, views)

            // Background: Sync from Firestore only on explicit refresh (not on every update)
            // This prevents slowdown - app updates will sync back via snapshot listener
        }

        private fun pendingBroadcast(context: Context, action: String): PendingIntent {
            val intent = Intent(context, WaterWidgetProvider::class.java).apply { this.action = action }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, action.hashCode(), intent, flags)
        }

        // Public method for app to update widget when water changes
        fun updateWidgetFromApp(context: Context, waterMl: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = todayId()
            prefs.edit().putInt("water_$today", waterMl).apply()

            // Refresh all widget instances
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
            ids.forEach { id -> updateAppWidget(context, mgr, id) }
        }
    }
}
