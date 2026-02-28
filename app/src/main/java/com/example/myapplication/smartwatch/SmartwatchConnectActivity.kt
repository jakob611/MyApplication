package com.example.myapplication.smartwatch

import android.Manifest
import android.view.View
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.utils.HealthDataManager
import com.example.myapplication.utils.SamsungHealthManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import kotlinx.coroutines.launch

class SmartwatchConnectActivity : AppCompatActivity() {
    private lateinit var healthManager: HealthDataManager
    private lateinit var samsungHealthManager: SamsungHealthManager
    private val providerPackages = listOf(
        Provider("Samsung Health", "com.samsung.android.shealth", R.drawable.ic_glow_upp_icon, listOf("com.sec.android.app.shealth")),
        Provider("Fitbit", "com.fitbit.FitbitMobile", R.drawable.ic_glow_upp_icon),
        Provider("Google Fit / Wear OS", "com.google.android.apps.fitness", R.drawable.ic_glow_upp_icon),
        Provider("Huawei Health", "com.huawei.health", R.drawable.ic_glow_upp_icon),
        Provider("Garmin Connect", "com.garmin.android.apps.connectmobile", R.drawable.ic_glow_upp_icon),
        Provider("Xiaomi Mi Fitness", "com.xiaomi.hm.health", R.drawable.ic_glow_upp_icon)
    )

    private var selectedProvider: Provider? = null
    private lateinit var container: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button

    private lateinit var healthConnectPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    // Permission launchers
    private val activityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            requestHealthConnectOrGoogleFit()
        } else {
            Toast.makeText(this, "Activity recognition dovoljenje je potrebno", Toast.LENGTH_SHORT).show()
        }
    }

    private val googleFitPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onPermissionsGranted()
        } else {
            Toast.makeText(this, "Google Fit dovoljenja potrebna", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smartwatch_connect)

        healthManager = HealthDataManager(this)
        samsungHealthManager = SamsungHealthManager(this)
        container = findViewById(R.id.providers_container)
        statusText = findViewById(R.id.status_text)
        connectButton = findViewById(R.id.connect_button)

        // Initialize Health Connect permission launcher
        // We'll use StartActivityForResult and manually create the permission intent
        healthConnectPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            android.util.Log.d("SmartwatchConnect", "Health Connect permissions result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                lifecycleScope.launch {
                    val client = try {
                        HealthConnectClient.getOrCreate(this@SmartwatchConnectActivity)
                    } catch (e: Exception) {
                        null
                    }

                    if (client != null) {
                        val requiredPermissions = healthManager.getHealthConnectPermissions()
                        val grantedPermissions = try {
                            client.permissionController.getGrantedPermissions()
                        } catch (e: Exception) {
                            emptySet()
                        }

                        android.util.Log.d("SmartwatchConnect", "Required: ${requiredPermissions.size}, Granted: ${grantedPermissions.size}")

                        if (grantedPermissions.containsAll(requiredPermissions)) {
                            android.util.Log.d("SmartwatchConnect", "All required permissions granted!")
                            onPermissionsGranted()
                        } else {
                            val missing = requiredPermissions - grantedPermissions
                            android.util.Log.d("SmartwatchConnect", "Missing permissions: $missing")
                            Toast.makeText(this@SmartwatchConnectActivity, "Nekatera dovoljenja niso odobrena. Poskusi ponovno.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@SmartwatchConnectActivity, "Napaka pri preverjanju dovoljenj.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "Dovoljenja niso odobrena", Toast.LENGTH_SHORT).show()
            }
        }

        inflateRows()
        connectButton.setOnClickListener {
            val provider = selectedProvider
            if (provider == null) {
                Toast.makeText(this, "Izberi nameščeno aplikacijo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val installed = healthManager.isWearablePackageInstalled(provider.packageName, provider.alternates)
            if (!installed) {
                Toast.makeText(this, "${provider.name} ni nameščena. Namesti aplikacijo.", Toast.LENGTH_LONG).show()
                openStore(provider.packageName)
                return@setOnClickListener
            }

            // Save selection (but not connected yet)
            getSharedPreferences("smartwatch_prefs", MODE_PRIVATE)
                .edit()
                .putString("provider", provider.packageName)
                .putBoolean("connected", false)
                .apply()

            // Request permissions
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        // First check Activity Recognition for step tracking
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasActivityRecognition = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasActivityRecognition) {
                activityPermissionLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
                return
            }
        }
        requestHealthConnectOrGoogleFit()
    }

    private fun requestHealthConnectOrGoogleFit() {
        lifecycleScope.launch {
            android.util.Log.d("SmartwatchConnect", "=== REQUESTING PERMISSIONS ===")

            // Vsi providers (Samsung Health, Fitbit, itd.) uporabljajo Health Connect
            val sdkAvailable = try {
                healthManager.isHealthConnectAvailable()
            } catch (e: Exception) {
                android.util.Log.e("SmartwatchConnect", "isHealthConnectAvailable failed", e)
                false
            }

            android.util.Log.d("SmartwatchConnect", "SDK Available: $sdkAvailable")

            if (sdkAvailable) {
                android.util.Log.d("SmartwatchConnect", "Health Connect SDK is available - proceeding with HC")

                try {
                    // Najprej inicializiraj client
                    val success = healthManager.initializeHealthConnect()
                    android.util.Log.d("SmartwatchConnect", "HealthConnect init: $success")

                    if (!success) {
                        throw Exception("Failed to initialize HC client")
                    }

                    val permissions = healthManager.getHealthConnectPermissions()
                    android.util.Log.d("SmartwatchConnect", "Requesting ${permissions.size} permissions: $permissions")

                    // Use permission controller to create proper Intent
                    val client = HealthConnectClient.getOrCreate(this@SmartwatchConnectActivity)
                    android.util.Log.d("SmartwatchConnect", "HC Client created")

                    val contract = PermissionController.createRequestPermissionResultContract()
                    val intent = contract.createIntent(this@SmartwatchConnectActivity, permissions)

                    android.util.Log.d("SmartwatchConnect", "Launching HC permission dialog...")
                    healthConnectPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    android.util.Log.e("SmartwatchConnect", "Failed to launch HC permission request", e)
                    e.printStackTrace()

                    Toast.makeText(this@SmartwatchConnectActivity,
                        "Napaka pri zahtevanju dovoljenj: ${e.message}",
                        Toast.LENGTH_LONG).show()

                    // Fallback: try opening Health Connect app direktno
                    try {
                        android.util.Log.d("SmartwatchConnect", "Trying fallback to HC settings...")
                        val fallbackIntent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                        startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        android.util.Log.e("SmartwatchConnect", "Failed to open HC settings", e2)
                        Toast.makeText(this@SmartwatchConnectActivity,
                            "Odpri Samsung Health in dovoli dostop do zdravstvenega podatka",
                            Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                android.util.Log.d("SmartwatchConnect", "HC SDK NOT available, trying Google Fit")
                requestGoogleFitPermissions()
            }
        }
    }

    private fun requestGoogleFitPermissions() {
        val fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .build()

        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            val intent = GoogleSignIn.getClient(this, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .signInIntent
            googleFitPermissionLauncher.launch(intent)
        } else {
            onPermissionsGranted()
        }
    }

    private fun onPermissionsGranted() {
        // Mark as connected
        getSharedPreferences("smartwatch_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("connected", true)
            .apply()

        Toast.makeText(this, "Povezano z ${selectedProvider?.name}", Toast.LENGTH_SHORT).show()

        // Open dashboard
        val intent = Intent(this, SmartwatchDashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        refreshRows()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // No special handling needed - permissions are handled by launcher
    }

    private fun inflateRows() {
        container.removeAllViews()
        providerPackages.forEach { provider ->
            val row = layoutInflater.inflate(R.layout.item_provider_row, container, false)
            row.findViewById<TextView>(R.id.provider_name).text = provider.name
            row.findViewById<ImageView>(R.id.provider_icon).setImageResource(provider.iconRes)
            bindRow(row, provider)
            container.addView(row)
        }
    }

    private fun refreshRows() {
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val provider = providerPackages[i]
            bindRow(row, provider)
        }
    }

    private fun bindRow(row: View, provider: Provider) {
        val installed = healthManager.isWearablePackageInstalled(provider.packageName, provider.alternates)
        val status = row.findViewById<TextView>(R.id.provider_status)
        status.text = if (installed) "Installed" else "Not installed"
        row.isEnabled = installed
        row.alpha = if (installed) 1f else 0.4f
        row.setOnClickListener {
            if (!installed) {
                statusText.text = "${provider.name} ni nameščena. Dolg pritisk odpre trgovino."
                return@setOnClickListener
            }
            selectedProvider = provider
            highlightSelection(container, row)
            statusText.text = "Selected ${provider.name} - tap Connect to authorize"
        }
        row.setOnLongClickListener {
            openStore(provider.packageName)
            true
        }
        // auto-select first installed
        if (installed && selectedProvider == null) {
            selectedProvider = provider
            highlightSelection(container, row)
            statusText.text = "Selected ${provider.name} - tap Connect to authorize"
        }
        connectButton.isEnabled = selectedProvider != null
        connectButton.alpha = if (connectButton.isEnabled) 1f else 0.5f
    }

    private fun highlightSelection(container: LinearLayout, selectedRow: android.view.View) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.isSelected = child == selectedRow
            child.setBackgroundResource(if (child == selectedRow) R.drawable.bg_provider_selected else R.drawable.bg_provider_normal)
        }
    }

    private fun openStore(pkg: String) {
        val uri = Uri.parse("market://details?id=$pkg")
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            startActivity(goToMarket)
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    data class Provider(val name: String, val packageName: String, val iconRes: Int, val alternates: List<String> = emptyList())
}
