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

class QuickMealWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "QuickMeal widget enabled")
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} QuickMeal widget(s)")
        appWidgetIds.forEach { id ->
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")

        when {
            intent.action?.startsWith(ACTION_ADD_MEAL_PREFIX) == true -> {
                val mealId = intent.action?.removePrefix(ACTION_ADD_MEAL_PREFIX)
                if (mealId != null) {
                    handleAddMeal(context, mealId)
                }
            }
            intent.action == ACTION_SCAN -> openBarcodeScan(context)
            intent.action == ACTION_SEARCH -> openFoodSearch(context)
            intent.action == ACTION_REFRESH -> refreshAll(context)
            intent.action == ACTION_OPEN_APP -> openApp(context)
        }
    }

    private fun handleAddMeal(context: Context, mealId: String) {
        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        if (uid == null) {
            Log.w(TAG, "Not logged in, cannot add meal")
            return
        }

        Log.d(TAG, "Adding meal: $mealId")

        val mealType = getCurrentMealType()
        val today = todayId()

        val db = Firebase.firestore
        db.collection("users").document(uid)
            .collection("customMeals").document(mealId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.w(TAG, "Meal $mealId not found")
                    return@addOnSuccessListener
                }

                val mealName = doc.getString("name") ?: "Unknown Meal"
                val items = doc.get("items") as? List<*> ?: emptyList<Any>()

                Log.d(TAG, "Found meal: $mealName with ${items.size} items")

                // Debug: Log first item to see structure
                items.firstOrNull()?.let { firstItem ->
                    Log.d(TAG, "First item structure: $firstItem")
                }

                // Convert items to tracked foods format
                val trackedItems = items.mapNotNull { any ->
                    val m = any as? Map<*, *> ?: return@mapNotNull null

                    // Parse amount (stored as "amt" string)
                    val amtStr = m["amt"] as? String ?: "1.0"
                    val amount = amtStr.toDoubleOrNull() ?: 1.0

                    // Build map with required fields + optional fields only if present
                    val baseMap = mutableMapOf<String, Any>(
                        "id" to UUID.randomUUID().toString(),
                        "name" to (m["name"] as? String ?: ""),
                        "meal" to mealType.toString(),
                        "amount" to amount,
                        "unit" to (m["unit"] as? String ?: "servings"),
                        "caloriesKcal" to ((m["caloriesKcal"] as? Number)?.toDouble() ?: 0.0),
                        "proteinG" to ((m["proteinG"] as? Number)?.toDouble() ?: 0.0),
                        "carbsG" to ((m["carbsG"] as? Number)?.toDouble() ?: 0.0),
                        "fatG" to ((m["fatG"] as? Number)?.toDouble() ?: 0.0)
                    )

                    // Add optional fields only if they exist and are not null
                    (m["fiberG"] as? Number)?.toDouble()?.let { baseMap["fiberG"] = it }
                    (m["sugarG"] as? Number)?.toDouble()?.let { baseMap["sugarG"] = it }
                    (m["saturatedFatG"] as? Number)?.toDouble()?.let { baseMap["saturatedFatG"] = it }
                    (m["sodiumMg"] as? Number)?.toDouble()?.let { baseMap["sodiumMg"] = it }
                    (m["potassiumMg"] as? Number)?.toDouble()?.let { baseMap["potassiumMg"] = it }
                    (m["cholesterolMg"] as? Number)?.toDouble()?.let { baseMap["cholesterolMg"] = it }

                    baseMap.toMap() // Return immutable map
                }

                // Get existing daily log
                val dailyLogRef = db.collection("users").document(uid)
                    .collection("dailyLogs").document(today)

                dailyLogRef.get().addOnSuccessListener { dailyDoc ->
                    val existingItems = (dailyDoc.get("items") as? List<*>) ?: emptyList<Any>()
                    val newItems = existingItems + trackedItems

                    // Debug: Log what we're saving
                    Log.d(TAG, "Saving ${trackedItems.size} items. First item: ${trackedItems.firstOrNull()}")

                    dailyLogRef.set(
                        mapOf(
                            "date" to today,
                            "items" to newItems,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).addOnSuccessListener {
                        Log.d(TAG, "Successfully added meal $mealName to $mealType")

                        // Refresh widget to update
                        refreshAll(context)
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to add meal", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch meal $mealId", e)
            }
    }

    private fun openApp(context: Context) {
        try {
            val intent = Intent(context, com.example.myapplication.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "nutrition")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app", e)
        }
    }


    private fun openBarcodeScan(context: Context) {
        try {
            val intent = Intent(context, com.example.myapplication.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO", "nutrition")
                putExtra("OPEN_BARCODE_SCAN", true)
            }
            context.startActivity(intent)
            Log.d(TAG, "Launching barcode scan")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open barcode scan", e)
        }
    }

    private fun openFoodSearch(context: Context) {
        try {
            val intent = Intent(context, com.example.myapplication.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO", "nutrition")
                putExtra("OPEN_FOOD_SEARCH", true)
            }
            context.startActivity(intent)
            Log.d(TAG, "Launching food search")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open food search", e)
        }
    }

    private fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, QuickMealWidgetProvider::class.java))
        ids.forEach { id -> updateAppWidget(context, mgr, id) }
    }

    companion object {
        private const val TAG = "QuickMealWidget"
        private const val ACTION_ADD_MEAL_PREFIX = "com.example.myapplication.widget.ADD_MEAL_"
        private const val ACTION_REFRESH = "com.example.myapplication.widget.MEAL_REFRESH"
        private const val ACTION_OPEN_APP = "com.example.myapplication.widget.MEAL_OPEN_APP"
        private const val ACTION_SCAN = "com.example.myapplication.widget.MEAL_SCAN"
        private const val ACTION_SEARCH = "com.example.myapplication.widget.MEAL_SEARCH"

        // MealType enum - must be inside companion object
        private enum class MealType {
            Breakfast, Lunch, Dinner, Snacks
        }

        private fun todayId(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        private fun getCurrentMealType(): MealType {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timeInMinutes = hour * 60 + minute

            return when {
                timeInMinutes in 300..599 -> MealType.Breakfast  // 5:00-9:59
                timeInMinutes in 690..899 -> MealType.Lunch      // 11:30-14:59
                timeInMinutes in 1080..1199 -> MealType.Dinner   // 18:00-19:59
                else -> MealType.Snacks
            }
        }

        fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val layoutId = context.resources.getIdentifier("widget_quick_meal", "layout", context.packageName)
            val views = RemoteViews(context.packageName, layoutId)

            // Get current meal type
            val currentMeal = getCurrentMealType()
            val mealLabel = when (currentMeal) {
                MealType.Breakfast -> "🍳 Breakfast"
                MealType.Lunch -> "🍽️ Lunch"
                MealType.Dinner -> "🍲 Dinner"
                MealType.Snacks -> "🍪 Snacks"
            }

            // Set meal type label
            val labelId = context.resources.getIdentifier("txtMealType", "id", context.packageName)
            views.setTextViewText(labelId, mealLabel)

            // Fetch custom meals from Firestore and update widget
            val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
            if (uid != null) {
                fetchCustomMealsAndUpdate(context, manager, appWidgetId, views, uid)
            } else {
                // Not logged in - show placeholder
                setPlaceholderMeals(context, views)
                manager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun fetchCustomMealsAndUpdate(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            views: RemoteViews,
            uid: String
        ) {
            val db = Firebase.firestore
            db.collection("users").document(uid)
                .collection("customMeals")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(2)  // Fetch 2 newest meals
                .get()
                .addOnSuccessListener { snapshot ->
                    val meals = snapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("name") ?: return@mapNotNull null
                        Pair(doc.id, name)
                    }

                    if (meals.isEmpty()) {
                        setPlaceholderMeals(context, views)
                    } else {
                        setMealButtons(context, views, meals)
                    }

                    // Set scan and search buttons
                    setScanAndSearchButtons(context, views)

                    manager.updateAppWidget(appWidgetId, views)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to fetch custom meals", e)
                    setPlaceholderMeals(context, views)
                    setScanAndSearchButtons(context, views)
                    manager.updateAppWidget(appWidgetId, views)
                }
        }

        private fun setMealButtons(context: Context, views: RemoteViews, meals: List<Pair<String, String>>) {
            val buttonIds = listOf("btnMeal1", "btnMeal2")  // Only 2 meal buttons now

            meals.forEachIndexed { index, (mealId, mealName) ->
                if (index < buttonIds.size) {
                    val btnId = context.resources.getIdentifier(buttonIds[index], "id", context.packageName)
                    views.setTextViewText(btnId, mealName)
                    views.setOnClickPendingIntent(btnId, pendingAddMeal(context, mealId))
                }
            }

            // Hide unused meal buttons (if only 1 meal exists)
            for (i in meals.size until buttonIds.size) {
                val btnId = context.resources.getIdentifier(buttonIds[i], "id", context.packageName)
                views.setTextViewText(btnId, "—")
                views.setOnClickPendingIntent(btnId, pendingOpenApp(context))
            }
        }

        private fun setPlaceholderMeals(context: Context, views: RemoteViews) {
            val buttonIds = listOf("btnMeal1", "btnMeal2")  // Only 2 meal buttons
            buttonIds.forEach { btnName ->
                val btnId = context.resources.getIdentifier(btnName, "id", context.packageName)
                views.setTextViewText(btnId, "+ Add\nMeal")
                views.setOnClickPendingIntent(btnId, pendingOpenApp(context))
            }
        }

        private fun setScanAndSearchButtons(context: Context, views: RemoteViews) {
            // Set Scan button
            val scanId = context.resources.getIdentifier("btnScan", "id", context.packageName)
            views.setTextViewText(scanId, "📷\nScan")
            views.setOnClickPendingIntent(scanId, pendingScan(context))

            // Set Search button
            val searchId = context.resources.getIdentifier("btnSearch", "id", context.packageName)
            views.setTextViewText(searchId, "🔍\nSearch")
            views.setOnClickPendingIntent(searchId, pendingSearch(context))
        }

        private fun pendingAddMeal(context: Context, mealId: String): PendingIntent {
            val intent = Intent(context, QuickMealWidgetProvider::class.java).apply {
                action = ACTION_ADD_MEAL_PREFIX + mealId
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, mealId.hashCode(), intent, flags)
        }

        private fun pendingOpenApp(context: Context): PendingIntent {
            val intent = Intent(context, QuickMealWidgetProvider::class.java).apply {
                action = ACTION_OPEN_APP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, ACTION_OPEN_APP.hashCode(), intent, flags)
        }

        private fun pendingScan(context: Context): PendingIntent {
            val intent = Intent(context, QuickMealWidgetProvider::class.java).apply {
                action = ACTION_SCAN
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, ACTION_SCAN.hashCode(), intent, flags)
        }

        private fun pendingSearch(context: Context): PendingIntent {
            val intent = Intent(context, QuickMealWidgetProvider::class.java).apply {
                action = ACTION_SEARCH
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, ACTION_SEARCH.hashCode(), intent, flags)
        }

        fun forceRefresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, QuickMealWidgetProvider::class.java))
            ids.forEach { id -> updateAppWidget(context, mgr, id) }
        }
    }
}

