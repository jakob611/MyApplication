package com.example.myapplication.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SamsungHealthManager(private val context: Context) {

    fun isSamsungHealthInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.samsung.android.shealth", 0)
            true
        } catch (e: Exception) {
            try {
                context.packageManager.getPackageInfo("com.sec.android.app.shealth", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun isHealthConnectAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    fun healthConnectInstallIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
    }

    fun samsungHealthInstallIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("market://details?id=com.samsung.android.shealth")
    }

    suspend fun createHealthConnectPermissionIntent(healthManager: HealthDataManager): Intent? = withContext(Dispatchers.IO) {
        try {
            if (!isHealthConnectAvailable()) {
                return@withContext null
            }

            val client = HealthConnectClient.getOrCreate(context)
            val permissions = healthManager.getHealthConnectPermissions()

            PermissionController.createRequestPermissionResultContract().createIntent(
                context,
                permissions
            )
        } catch (e: Exception) {
            android.util.Log.e("SamsungHealthManager", "Error creating permission intent", e)
            null
        }
    }


    suspend fun getSamsungHealthData(): HealthData = withContext(Dispatchers.IO) {
        if (!isSamsungHealthInstalled()) {
            return@withContext HealthData(
                connectionError = "Samsung Health ni nameščen. Namesti ga in omogoči sinhronizacijo s Health Connect."
            )
        }

        if (!isHealthConnectAvailable()) {
            return@withContext HealthData(
                connectionError = "Health Connect ni nameščen. Namesti Health Connect in omogoči sinhronizacijo v Samsung Health nastavitvah."
            )
        }

        return@withContext HealthData(
            connectionError = "Odpri Samsung Health → Settings → Data permissions → Sync with Health Connect, nato dovoli pravice v Health Connect."
        )
    }

    fun openSamsungHealth() {
        val pkgs = listOf("com.samsung.android.shealth", "com.sec.android.app.shealth")
        for (pkg in pkgs) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply { `package` = pkg }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // try next
            }
        }
    }
}
