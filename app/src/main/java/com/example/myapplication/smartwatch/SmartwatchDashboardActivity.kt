package com.example.myapplication.smartwatch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.utils.HealthData
import com.example.myapplication.utils.HealthDataManager
import com.example.myapplication.utils.SamsungHealthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SmartwatchDashboardActivity : AppCompatActivity() {
    private lateinit var healthManager: HealthDataManager
    private lateinit var samsungHealthManager: SamsungHealthManager
    private lateinit var stepsText: TextView
    private lateinit var heartRateText: TextView
    private lateinit var sleepText: TextView
    private lateinit var distanceText: TextView
    private lateinit var caloriesText: TextView
    private lateinit var statusText: TextView
    private lateinit var refreshButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var instructionsCard: CardView

    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        android.util.Log.d("SmartwatchDashboard", "Permissions granted: $granted")
        if (granted.containsAll(healthManager.getHealthConnectPermissions())) {
            loadData()
        } else {
            Toast.makeText(this, "Health Connect permissions not fully granted. Please grant all permissions.", Toast.LENGTH_LONG).show()
            loadData() // Still try to load partial data
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smartwatch_dashboard)

        healthManager = HealthDataManager(this)
        samsungHealthManager = SamsungHealthManager(this)

        stepsText = findViewById(R.id.steps_value)
        heartRateText = findViewById(R.id.heart_value)
        sleepText = findViewById(R.id.sleep_value)
        distanceText = findViewById(R.id.distance_value)
        caloriesText = findViewById(R.id.calories_value)
        statusText = findViewById(R.id.status_value)
        refreshButton = findViewById(R.id.refresh_button)
        progress = findViewById(R.id.progress)
        instructionsCard = findViewById(R.id.instructions_card)

        findViewById<Button>(R.id.refresh_button).setOnClickListener {
            loadData()
        }

        findViewById<Button>(R.id.open_health_connect_button).setOnClickListener {
            openHealthConnectSettings()
        }

        // Load data immediately
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun openHealthConnectSettings() {
        try {
            val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Cannot open Health Connect settings", Toast.LENGTH_SHORT).show()
        }
    }


    private fun loadData() {
        progress.visibility = View.VISIBLE
        refreshButton.isEnabled = false

        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d("SmartwatchDashboard", "Starting refresh...")

                    // Pridobi najnovejše podatke iz Health Connect
                    val refreshedData = healthManager.refreshHealthConnectData()
                    android.util.Log.d("SmartwatchDashboard", "Refresh result: connected=${refreshedData.isConnected}, steps=${refreshedData.steps}")
                    refreshedData
                } catch (e: Exception) {
                    android.util.Log.e("SmartwatchDashboard", "Error refreshing data", e)
                    healthManager.getHealthData()
                }
            }

            // Pokazi toast s rezultatom samo če smo uspešno pridobili podatke
            if (data.isConnected && data.steps > 0) {
                Toast.makeText(this@SmartwatchDashboardActivity, "✓ Podatki osveženi iz Samsung Health!", Toast.LENGTH_SHORT).show()
            }

            render(data)
        }
    }

    private fun render(data: HealthData) {
        progress.visibility = View.GONE
        refreshButton.isEnabled = true

        if (!data.connectionError.isNullOrEmpty()) {
            // Prikaži navodila če je napaka
            instructionsCard.visibility = View.VISIBLE

            statusText.text = data.connectionError
            stepsText.text = "-"
            heartRateText.text = "-"
            sleepText.text = "-"
            distanceText.text = "-"
            caloriesText.text = "-"

            val lower = data.connectionError.lowercase()
            when {
                lower.contains("health connect") && lower.contains("permissions") -> {
                    refreshButton.text = "GRANT PERMISSIONS"
                    refreshButton.setOnClickListener {
                        lifecycleScope.launch {
                            try {
                                val permissions = healthManager.getHealthConnectPermissions()
                                android.util.Log.d("SmartwatchDashboard", "Requesting permissions: $permissions")
                                requestPermissions.launch(permissions)
                            } catch (e: Exception) {
                                android.util.Log.e("SmartwatchDashboard", "Error launching permission request", e)
                                try {
                                    startActivity(samsungHealthManager.healthConnectInstallIntent())
                                } catch (_: Exception) {
                                    Toast.makeText(this@SmartwatchDashboardActivity, "Install Health Connect from Play Store", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                lower.contains("no data available") -> {
                    refreshButton.text = "OPEN SAMSUNG HEALTH"
                    refreshButton.setOnClickListener {
                        samsungHealthManager.openSamsungHealth()
                        Toast.makeText(this, "Go to Settings → Connected services → Health Connect → Enable sync", Toast.LENGTH_LONG).show()
                    }
                }
                lower.contains("samsung health") -> {
                    refreshButton.text = "OPEN SAMSUNG HEALTH"
                    refreshButton.setOnClickListener {
                        samsungHealthManager.openSamsungHealth()
                        Toast.makeText(this, "Go to Settings → Connected services → Health Connect → Enable sync", Toast.LENGTH_LONG).show()
                    }
                }
                lower.contains("health connect not installed") -> {
                    refreshButton.text = "INSTALL HEALTH CONNECT"
                    refreshButton.setOnClickListener {
                        try {
                            startActivity(samsungHealthManager.healthConnectInstallIntent())
                        } catch (_: Exception) {
                            Toast.makeText(this, "Open Play Store and search for 'Health Connect'", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                else -> {
                    refreshButton.text = "REFRESH"
                    refreshButton.setOnClickListener { loadData() }
                }
            }

            return
        }

        // Če ni napake in imamo podatke, skrij navodila
        val hasData = data.steps > 0 || data.heartRate > 0 || data.sleepMinutes > 0 ||
                      data.distance > 0 || data.caloriesBurned > 0

        instructionsCard.visibility = if (hasData) View.GONE else View.VISIBLE

        // Nastavi default refresh gumb
        refreshButton.text = "REFRESH"
        refreshButton.setOnClickListener {
            loadData()
        }

        // Preveri če so podatki iz Samsung Health
        val dataSource = if (data.isConnected && data.steps > 0) {
            "Samsung Health ✓"
        } else if (data.isConnected) {
            "Connected ✓"
        } else {
            "Checking..."
        }

        statusText.text = dataSource
        statusText.setTextColor(if (data.isConnected && data.steps > 0) 0xFF4CAF50.toInt() else 0xFF9C27B0.toInt())

        stepsText.text = data.steps.toString()
        heartRateText.text = if (data.heartRate > 0) "${data.heartRate} bpm" else "-"
        sleepText.text = if (data.sleepMinutes > 0) "${data.sleepMinutes} min" else "-"
        distanceText.text = if (data.distance > 0) String.format(Locale.US, "%.2f km", data.distance) else "-"
        caloriesText.text = if (data.caloriesBurned > 0) String.format(Locale.US, "%.0f kcal", data.caloriesBurned) else "-"
    }
}
