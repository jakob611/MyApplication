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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * PlanDayWidget — prikazuje:
 *  - 🔥 streak
 *  - Week X Day Y (dan v planu)
 *  - Focus area tega dne (npr. "Push", "Legs", "Rest 😴")
 *
 * Klik → odpre MainActivity z extra NAVIGATE_TO=body_module,
 * ki takoj prikaže BodyModuleHome (kjer je Plan Path gumb).
 *
 * Posodablja se:
 *  - ob odprtju aplikacije (refreshAll iz MainActivity)
 *  - ob DATE_CHANGED
 *  - ob ACTION_REFRESH (klik na widget — osveži brez navigacije)
 */
class PlanDayWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        syncFromFirestore(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { id -> updateAppWidget(context, appWidgetManager, id) }
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
        private const val TAG = "PlanDayWidget"
        private const val PREFS = "plan_day_widget_prefs"
        private const val KEY_STREAK = "streak"
        private const val KEY_DAY_LABEL = "day_label"
        private const val KEY_FOCUS = "focus_area"

        const val ACTION_REFRESH = "com.example.myapplication.widget.PLAN_DAY_REFRESH"

        /** Kliče MainActivity ob vsakem odprtju — posodobi cache in widget UI. */
        fun refreshAll(context: Context) {
            syncFromFirestore(context)
        }

        /**
         * Kliče aplikacija takoj ko dobi nove podatke (po končani vadbi, po posodobitvi plana).
         */
        fun updateWidgetFromApp(context: Context, streak: Int, dayLabel: String, focusArea: String) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_STREAK, streak)
                .putString(KEY_DAY_LABEL, dayLabel)
                .putString(KEY_FOCUS, focusArea)
                .apply()
            refreshWidgetUI(context)
        }

        private fun syncFromFirestore(context: Context) {
            val userDocId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
            if (userDocId == null) {
                refreshWidgetUI(context)
                return
            }

            // Korak 1: streak_days + plan_day iz users/{resolvedDocId} (email-first)
            com.example.myapplication.persistence.FirestoreHelper.getUserRef(userDocId)
                .get()
                .addOnSuccessListener { userSnap ->
                    val streak = (userSnap.getLong("streak_days") ?: 0L).toInt()
                    val planDay = (userSnap.getLong("plan_day") ?: 1L).toInt()

                    // Korak 2: plan data iz user_plans/{resolvedDocId}
                    Firebase.firestore.collection("user_plans").document(userDocId)
                        .get()
                        .addOnSuccessListener { planSnap ->
                            @Suppress("UNCHECKED_CAST")
                            val plans = planSnap.get("plans") as? List<Map<String, Any>>
                            val latestPlan = plans?.maxByOrNull {
                                (it["createdAt"] as? Number)?.toLong() ?: 0L
                            }

                            val trainingDays = (latestPlan?.get("trainingDays") as? Number)?.toInt() ?: 0
                            val startDate = latestPlan?.get("startDate") as? String ?: ""

                            // Dan v planu glede na startDate (absolutni kalendar)
                            val todayPlanDay = if (startDate.isNotBlank()) {
                                try {
                                    val start = java.time.LocalDate.parse(startDate)
                                    val today = java.time.LocalDate.now()
                                    val diff = java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt() + 1
                                    diff.coerceAtLeast(1)
                                } catch (_: Exception) { planDay }
                            } else planDay

                            // Izračunaj "Week X Day Y" label
                            val dayLabel = if (trainingDays > 0) {
                                planDayToLabel(todayPlanDay, trainingDays)
                            } else "Day $todayPlanDay"

                            // Pridobi focus area iz weeks strukture
                            val focusArea = extractFocusArea(latestPlan, todayPlanDay)

                            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putInt(KEY_STREAK, streak)
                                .putString(KEY_DAY_LABEL, dayLabel)
                                .putString(KEY_FOCUS, focusArea)
                                .apply()

                            refreshWidgetUI(context)
                            Log.d(TAG, "Synced: streak=$streak dayLabel=$dayLabel focus=$focusArea")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Failed to load plan", e)
                            // Shrani vsaj streak
                            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putInt(KEY_STREAK, streak)
                                .putString(KEY_DAY_LABEL, "Day $planDay")
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
         * Iz Firestore plan mape izlušči focusLabel za dani absolutni dan v planu.
         * Strukturira se: plan.weeks[].days[].focusLabel in plan.weeks[].days[].isRestDay
         */
        @Suppress("UNCHECKED_CAST")
        private fun extractFocusArea(plan: Map<String, Any>?, absolutePlanDay: Int): String {
            if (plan == null) return ""

            val weeks = plan["weeks"] as? List<Map<String, Any>> ?: return ""

            // Zloži vse days iz vseh tednov v eno listo (vrstni red je ohranjen)
            val allDays = weeks.flatMap { week ->
                (week["days"] as? List<Map<String, Any>>) ?: emptyList()
            }

            // absolutePlanDay je 1-based, indeks je 0-based
            val dayIndex = absolutePlanDay - 1
            val day = allDays.getOrNull(dayIndex) ?: return ""

            val isRest = day["isRestDay"] as? Boolean ?: false
            if (isRest) return "Rest 😴"

            val label = day["focusLabel"] as? String ?: ""
            return label.ifBlank { "Workout 💪" }
        }

        /**
         * Pretvori absolutni dan plana v "Week X Day Y".
         * Šteje samo trening dni (ne rest dni) — podobno kot StreakWidgetProvider.
         */
        fun planDayToLabel(planDay: Int, trainingDaysPerWeek: Int): String {
            if (trainingDaysPerWeek <= 0) return "Day $planDay"
            val day = planDay.coerceAtLeast(1)
            val weekNum = ((day - 1) / trainingDaysPerWeek) + 1
            val dayInWeek = ((day - 1) % trainingDaysPerWeek) + 1
            return "Week $weekNum · Day $dayInWeek"
        }

        private fun refreshWidgetUI(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PlanDayWidgetProvider::class.java))
            ids.forEach { id -> updateAppWidget(context, mgr, id) }
        }

        fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_plan_day)

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val streak = prefs.getInt(KEY_STREAK, 0)
            val dayLabel = prefs.getString(KEY_DAY_LABEL, "Week 1 · Day 1") ?: "Week 1 · Day 1"
            val focusArea = prefs.getString(KEY_FOCUS, "") ?: ""

            views.setTextViewText(R.id.txtPlanStreak, "🔥 $streak")
            views.setTextViewText(R.id.txtPlanDayLabel, dayLabel)
            views.setTextViewText(R.id.txtFocusArea, focusArea.ifBlank { "—" })

            // Klik → odpre MainActivity na BodyModuleHome (Plan Path)
            val launchIntent = Intent(context, com.example.myapplication.MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "body_module")
            }
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val launchPending = PendingIntent.getActivity(context, appWidgetId, launchIntent, pendingFlags)
            views.setOnClickPendingIntent(R.id.rootPlanDay, launchPending)

            manager.updateAppWidget(appWidgetId, views)
        }
    }
}

