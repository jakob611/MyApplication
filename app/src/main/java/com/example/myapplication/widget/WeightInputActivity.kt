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
import com.google.firebase.firestore.FieldValue
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch

/**
 * Transparent Activity that shows a weight input dialog from widget.
 * Closes immediately after user saves or cancels.
 */
class WeightInputActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make activity transparent and show behind lockscreen if needed
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        setContent {
            MaterialTheme {
                WeightInputDialog(
                    onDismiss = { finish() },
                    onSaved = { finish() }
                )
            }
        }
    }

    @Composable
    private fun WeightInputDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
        val context = LocalContext.current
        // uid v remember{} — ne sme se klicati ob vsakem recomposition
        val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }
        var weightInput by remember { mutableStateOf("") }
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
            title = { Text("Enter Weight") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = {
                            weightInput = it.filter { c -> c.isDigit() || c == '.' }.take(6)
                            errorMessage = null
                        },
                        placeholder = { Text("e.g. 75.3 kg") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    enabled = !saving && weightInput.isNotBlank(),
                    onClick = {
                        val w = weightInput.toDoubleOrNull()
                        if (w == null || w <= 0) {
                            errorMessage = "Please enter valid weight"
                            return@TextButton
                        }
                        if (uid == null) {
                            errorMessage = "Not logged in"
                            return@TextButton
                        }

                        saving = true
                        val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()

                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val saveWeightUseCase = com.example.myapplication.domain.metrics.SaveWeightUseCase(
                                com.example.myapplication.data.metrics.MetricsRepositoryImpl()
                            )
                            val result = saveWeightUseCase.execute(uid, w.toFloat(), today)

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (result.isSuccess) {
                                    Log.d("WeightInput", "Successfully saved weight via UseCase")
                                    WeightWidgetProvider.updateWidgetFromApp(context, w.toFloat())
                                    onSaved()
                                } else {
                                    Log.e("WeightInput", "Failed to save weight", result.exceptionOrNull())
                                    errorMessage = "Save failed"
                                    saving = false
                                }
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
