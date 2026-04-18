package com.example.myapplication.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.HealthStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class StatsWidget : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        job.cancel()
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // PendingIntent to launch the app when clicking the widget
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Construct the RemoteViews object
        val views = RemoteViews(context.packageName, R.layout.widget_stats)
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
        views.setOnClickPendingIntent(android.R.id.background, pendingIntent)

        // Loading state
        views.setTextViewText(R.id.widget_steps_value, "...")
        views.setTextViewText(R.id.widget_calories_value, "...")
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // Fetch data asynchronously
        scope.launch {
            try {
                // Determine today's date string
                val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
                
                // Fetch stats from HealthStorage
                val stats = HealthStorage.getDailyStats(today)
                // Also get extra burned calories (workouts + logs) that might not be fully synced to daily_health yet
                // However, HealthStorage.getDailyStats usually reads what is saved.
                // Let's rely on what Progress screen uses: it reads daily_health collection.
                // HealthStorage.getDailyStats fetches exactly that document.
                
                val steps = stats?.steps ?: 0
                val calories = stats?.calories ?: 0

                // Update UI on Main Thread (AppWidgetManager handles cross-process, so we push updates)
                val updatedViews = RemoteViews(context.packageName, R.layout.widget_stats)
                updatedViews.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
                updatedViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                updatedViews.setTextViewText(R.id.widget_steps_value, steps.toString())
                updatedViews.setTextViewText(R.id.widget_calories_value, calories.toString())
                
                // Timestamp
                val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                val timeParams = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
                updatedViews.setTextViewText(R.id.widget_last_updated, "Updated $timeParams")

                appWidgetManager.updateAppWidget(appWidgetId, updatedViews)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}


