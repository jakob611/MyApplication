package com.example.myapplication.widget

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Transparent Activity that shows a water input dialog from widget.
 * Closes immediately after user saves or cancels.
 */
class WaterInputActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make activity transparent and show behind lockscreen if needed
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        setContent {
            MaterialTheme {
                WaterInputDialog(
                    onDismiss = { finish() },
                    onSaved = { finish() }
                )
            }
        }
    }

    @Composable
    private fun WaterInputDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        // uid v remember{} — ne sme se klicati ob vsakem recomposition
        val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }
        var waterInput by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        if (uid == null) {
            // Not logged in - show error and close
            LaunchedEffect(Unit) {
                errorMessage = "Please log in first"
                kotlinx.coroutines.delay(2000)
                onDismiss()
            }
        }

        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            title = { Text("Enter Water Amount") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = waterInput,
                        onValueChange = {
                            waterInput = it.filter { c -> c.isDigit() }.take(5)
                            errorMessage = null
                        },
                        placeholder = { Text("e.g. 500 ml") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = errorMessage != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                    Text("Date: ${kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && waterInput.isNotBlank(),
                    onClick = {
                        val w = waterInput.toIntOrNull()
                        if (w == null || w <= 0) {
                            errorMessage = "Please enter valid water amount"
                            return@TextButton
                        }
                        if (uid == null) {
                            errorMessage = "Not logged in"
                            return@TextButton
                        }

                        saving = true
                        val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()

                        coroutineScope.launch {
                            try {
                                com.example.myapplication.data.nutrition.FoodRepositoryImpl.logWater(w, today)
                                // Posodobi lokalni cache — NutritionScreen bere iz tega
                                com.example.myapplication.persistence.DailySyncManager
                                    .saveWaterLocally(context, w, today)
                                // Update widget
                                WaterWidgetProvider.updateWidgetFromApp(context, w)
                                onSaved()
                            } catch (e: Exception) {
                                Log.e("WaterInput", "Failed to save water", e)
                                errorMessage = "Save failed"
                                saving = false
                            }
                        }
                    }
                ) { Text(if (saving) "Saving..." else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") }
            }
        )
    }
}

