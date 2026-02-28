package com.example.myapplication.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.myapplication.R
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Widget ki prikazuje streak (🔥 N) in trenutni dan v planu (npr. "Week 2 Day 3").
 * Posodablja se:
 *  - enkrat na dan ob polnoči (DATE_CHANGED broadcast)
 *  - ob vsakem odprtju aplikacije (klic refreshAll iz MainActivity)
 *  - ob kliku na widget (ACTION_REFRESH)
 */
class StreakWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        syncFromFirestore(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { id -> updateAppWidget(context, appWidgetManager, id) }
        // Try to sync from Firestore in background
        syncFromFirestore(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED -> syncFromFirestore(context)
        }
    }

    companion object {
        private const val TAG = "StreakWidget"
        private const val PREFS = "streak_widget_prefs"
        private const val KEY_STREAK = "streak_count"
        private const val KEY_PLAN_DAY_LABEL = "plan_day_label"

        const val ACTION_REFRESH = "com.example.myapplication.widget.STREAK_REFRESH"

        /**
         * Kliče aplikacija ob odprtju – posodobi cache in widget.
         */
        fun refreshAll(context: Context) {
            syncFromFirestore(context)
        }

        /**
         * Kliče aplikacija ko se streak/planDay posodobi – takoj zapiše v cache in osveži widget.
         */
        fun updateWidgetFromApp(context: Context, streak: Int, planDayLabel: String) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_STREAK, streak)
                .putString(KEY_PLAN_DAY_LABEL, planDayLabel)
                .apply()
            refreshWidgetUI(context)
        }

        private fun syncFromFirestore(context: Context) {
            val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
            val email = Firebase.auth.currentUser?.email

            if (uid == null || email == null) {
                refreshWidgetUI(context)
                return
            }

            val db = Firebase.firestore

            // Beri streak_days in plan_day iz users/{email}
            db.collection("users").document(email)
                .get()
                .addOnSuccessListener { userSnap ->
                    val streak = (userSnap.getLong("streak_days") ?: 0L).toInt()
                    val planDay = (userSnap.getLong("plan_day") ?: 1L).toInt()

                    // Beri plan iz user_plans/{uid} da dobimo trainingDays
                    db.collection("user_plans").document(uid)
                        .get()
                        .addOnSuccessListener { planSnap ->
                            @Suppress("UNCHECKED_CAST")
                            val plans = planSnap.get("plans") as? List<Map<String, Any>>
                            val firstPlan = plans?.firstOrNull()
                            val trainingDays = (firstPlan?.get("trainingDays") as? Number)?.toInt() ?: 0

                            val label = if (trainingDays > 0) planDayToLabel(planDay, trainingDays) else "Day $planDay"

                            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putInt(KEY_STREAK, streak)
                                .putString(KEY_PLAN_DAY_LABEL, label)
                                .apply()

                            refreshWidgetUI(context)
                            Log.d(TAG, "Synced: streak=$streak, planDay=$planDay, label=$label, trainingDays=$trainingDays")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Failed to load plan, using plain day label", e)
                            val label = "Day $planDay"
                            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putInt(KEY_STREAK, streak)
                                .putString(KEY_PLAN_DAY_LABEL, label)
                                .apply()
                            refreshWidgetUI(context)
                        }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to load user doc", e)
                    refreshWidgetUI(context)
                }
        }

        /**
         * Pretvori absolutni planDay v "Week X Day Y" glede na število trening dni na teden.
         * planDay je 1-based (prvi trening = 1).
         */
        fun planDayToLabel(planDay: Int, trainingDaysPerWeek: Int): String {
            if (trainingDaysPerWeek <= 0) return "Day $planDay"
            val day = planDay.coerceAtLeast(1)
            val weekNum = ((day - 1) / trainingDaysPerWeek) + 1
            val dayInWeek = ((day - 1) % trainingDaysPerWeek) + 1
            return "Week $weekNum Day $dayInWeek"
        }

        private fun refreshWidgetUI(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, StreakWidgetProvider::class.java))
            ids.forEach { id -> updateAppWidget(context, mgr, id) }
        }

        fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_streak)

            // Klik na widget → osveži
            val refreshIntent = Intent(context, StreakWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val refreshPending = PendingIntent.getBroadcast(context, ACTION_REFRESH.hashCode(), refreshIntent, flags)
            views.setOnClickPendingIntent(R.id.rootStreak, refreshPending)

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val streak = prefs.getInt(KEY_STREAK, 0)
            val planLabel = prefs.getString(KEY_PLAN_DAY_LABEL, "Week 1 Day 1") ?: "Week 1 Day 1"

            views.setTextViewText(R.id.txtStreakCount, "🔥 $streak")
            views.setTextViewText(R.id.txtPlanDay, planLabel)

            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
