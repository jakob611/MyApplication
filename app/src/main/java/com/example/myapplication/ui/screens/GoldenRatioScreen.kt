package com.example.myapplication.ui.screens

import android.content.Context
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.io.File
import kotlin.math.*

data class Proportion(
    val name: String,
    val d1: Pair<Int, Int>,
    val d2: Pair<Int, Int>,
    val weight: Double = 1.0
)

data class RatioResult(
    val name: String,
    val ratio: Double,
    val deviation: Double,
    val score: Double,
    val ideal: Double,
    val weight: Double
)

data class GoldenRatioAnalysis(
    val ratios: List<RatioResult>,
    val weightedScore: Double
)

fun calculateAdvancedGoldenRatio(
    markers: Map<Int, PointF>,
    normalizeBy: Pair<Int, Int>? = null
): GoldenRatioAnalysis {
    val phi = (1 + sqrt(5.0)) / 2 

    val proportions = listOf(
        Proportion("Središče-zgornja/spodnja", Pair(11, 31), Pair(31, 33), weight = 1.0),
        Proportion("Središče-nos/usta", Pair(23, 31), Pair(31, 33), weight = 1.2),
        Proportion("Širina obraza / razdalja med zenicama", Pair(19, 20), Pair(15, 16), weight = 1.5),
        Proportion("Višina nosu / širina nosu", Pair(11, 23), Pair(15, 16), weight = 1.3),
        Proportion("Višina zgornje ustnice / spodnje ustnice", Pair(23, 31), Pair(31, 33), weight = 1.0),
    )

    val normalizer = if (normalizeBy != null) {
        val pA = markers[normalizeBy.first]
        val pB = markers[normalizeBy.second]
        if (pA != null && pB != null) distance(pA, pB) else 1.0
    } else 1.0

    val ratioResults = proportions.mapNotNull { prop ->
        val p1 = markers[prop.d1.first]
        val p2 = markers[prop.d1.second]
        val p3 = markers[prop.d2.first]
        val p4 = markers[prop.d2.second]
        if (p1 == null || p2 == null || p3 == null || p4 == null) return@mapNotNull null

        val d1 = distance(p1, p2) / normalizer
        val d2 = distance(p3, p4) / normalizer
        if (d2 == 0.0) return@mapNotNull null

        val ratio = d1 / d2
        val deviation = abs(ratio - phi) / phi
        val score = max(0.0, 1.0 - deviation)
        RatioResult(prop.name, ratio, deviation, score, phi, prop.weight)
    }

    val totalWeight = ratioResults.sumOf { it.weight }
    val weightedScore = if (totalWeight > 0)
        ratioResults.sumOf { it.score * it.weight } / totalWeight
    else 0.0

    return GoldenRatioAnalysis(ratioResults, weightedScore)
}

private fun distance(p1: PointF, p2: PointF): Double {
    return sqrt((p1.x - p2.x).toDouble().pow(2) + (p1.y - p2.y).toDouble().pow(2))
}

@Composable
fun GoldenRatioScreen(
    onBack: () -> Unit = {}
) {
    var analysisMode by remember { mutableStateOf("auto") }
    val manualMeasurements = remember { mutableStateMapOf<String, Float>() }
    var calculatedScore by remember { mutableStateOf<Double?>(null) }
    var advancedAnalysis by remember { mutableStateOf<GoldenRatioAnalysis?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Golden Ratio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                "Golden Ratio Analysis",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                "Medieval beauty assessment using mathematical proportions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1810)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "About Golden Ratio",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        "The golden ratio (φ ≈ 1.618) has been used since antiquity to assess facial beauty. " +
                                "This technique analyzes various facial proportions and compares them to the ideal golden ratio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB6C6E6)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    onClick = { analysisMode = "auto" },
                    label = { Text("Auto Analysis") },
                    selected = analysisMode == "auto",
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = Color(0xFF2A1810)
                    )
                )
                FilterChip(
                    onClick = { analysisMode = "manual" },
                    label = { Text("Manual Calculator") },
                    selected = analysisMode == "manual",
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = Color(0xFF2A1810)
                    )
                )
            }

            if (analysisMode == "auto") {
                AutoAnalysisSection()
            } else {
                ManualCalculatorSection(
                    measurements = manualMeasurements,
                    onMeasurementChange = { key, value ->
                        manualMeasurements[key] = value
                    },
                    onCalculate = {
                        val markers = manualMeasurementsToMarkers(manualMeasurements)
                        calculatedScore = calculateBeautyScore(markers)
                        advancedAnalysis = calculateAdvancedGoldenRatio(
                            markers,
                            normalizeBy = Pair(19, 20)
                        )
                    },
                    calculatedScore = calculatedScore,
                    advancedAnalysis = advancedAnalysis
                )
            }
        }
    }
}

@Composable
fun AutoAnalysisSection() {
    val context = LocalContext.current
    val calculatedScore = remember { mutableStateOf<Double?>(null) }
    val advancedAnalysis = remember { mutableStateOf<GoldenRatioAnalysis?>(null) }
    val isLoading = remember { mutableStateOf(false) }

    val photoUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri.value != null) {
            processImage(context, photoUri.value!!, calculatedScore, advancedAnalysis, isLoading)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val file = File(context.cacheDir, "face_analysis_${System.currentTimeMillis()}.jpg")
                file.parentFile?.mkdirs()
                val uri = FileProvider.getUriForFile(
                    context.applicationContext,
                    "${context.packageName}.fileprovider",
                    file
                )
                photoUri.value = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                android.util.Log.e("GoldenRatio", "Camera launch failed", e)
                com.example.myapplication.utils.AppToast.showError(context, "Error launching camera: ${e.message}")
            }
        } else {
            com.example.myapplication.utils.AppToast.showError(context, "Camera permission is required to take a photo.")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            processImage(context, uri, calculatedScore, advancedAnalysis, isLoading)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading.value) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(16.dp))
            Text("Analyzing facial symmetry...", color = Color(0xFFB6C6E6))
        } else {
            Button(
                onClick = {
                    val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.CAMERA
                    )
                    if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        try {
                            val file = File(context.cacheDir, "face_analysis_${System.currentTimeMillis()}.jpg")
                            // Ensure directory exists
                            file.parentFile?.mkdirs()

                            val uri = FileProvider.getUriForFile(
                                context.applicationContext, // Use application context to be safe
                                "${context.packageName}.fileprovider",
                                file
                            )
                            photoUri.value = uri
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            android.util.Log.e("GoldenRatio", "Camera launch failed", e)
                            android.widget.Toast.makeText(
                                context,
                                "Error launching camera: ${e.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = "Camera",
                    tint = Color(0xFF2A1810),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Take Photo for Analysis",
                    color = Color(0xFF2A1810),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    galleryLauncher.launch("image/*")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Upload from Gallery",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        calculatedScore.value?.let { score ->
            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("AI BEAUTY SCORE", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${(score * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Based on ML Kit Landmark Detection",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ManualCalculatorSection(
    measurements: MutableMap<String, Float>,
    onMeasurementChange: (String, Float) -> Unit,
    onCalculate: () -> Unit,
    calculatedScore: Double?,
    advancedAnalysis: GoldenRatioAnalysis?
) {
    val measurementInputs = remember { 
        mutableStateMapOf<String, String>().apply { 
             measurements.forEach { (k, v) -> put(k, v.toString()) }
        } 
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1810)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "Manual Measurements (in mm)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val keyMeasurements = listOf(
                "face_height" to "Face Height",
                "face_width" to "Face Width",
                "eye_width" to "Eye Width",
                "nose_width" to "Nose Width",
                "mouth_width" to "Mouth Width",
                "forehead_height" to "Forehead Height",
                "nose_height" to "Nose Height",
                "lower_face_height" to "Lower Face Height"
            )

            keyMeasurements.forEach { (key, label) ->
                OutlinedTextField(
                    value = measurementInputs[key] ?: "",
                    onValueChange = { value ->
                        measurementInputs[key] = value
                        value.toFloatOrNull()?.let { onMeasurementChange(key, it) }
                    },
                    label = { Text(label, color = Color(0xFFB6C6E6)) },
                    suffix = { Text("mm", color = Color(0xFFB6C6E6)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = Color(0xFF6B7A99),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(Modifier.height(16.dp))

            val validInputs = keyMeasurements.count { (key, _) ->
                measurementInputs[key]?.toFloatOrNull() != null
            }

            Button(
                onClick = onCalculate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                enabled = validInputs >= 4
            ) {
                Text(
                    "Analiziraj",
                    color = Color(0xFF2A1810),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            calculatedScore?.let { score ->
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Beauty Score",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "${(score * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White
                        )
                    }
                }
            }

            // Advanced Analysis Display
            advancedAnalysis?.let { analysis ->
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Napredni Golden Ratio pregled",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "Skupni napredni Beauty Score: ${(analysis.weightedScore * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        analysis.ratios.forEach { r ->
                            Text(
                                "${r.name}: ${"%.2f".format(r.ratio)} (deviation: ${"%.1f".format(r.deviation * 100)}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB6C6E6)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun processImage(
    context: Context,
    uri: Uri,
    scoreState: MutableState<Double?>,
    analysisState: MutableState<GoldenRatioAnalysis?>,
    loadingState: MutableState<Boolean>
) {
    loadingState.value = true
    try {
        val image: InputImage
        try {
            image = InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            loadingState.value = false
            return
        }

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
        val detector = FaceDetection.getClient(options)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    com.example.myapplication.utils.AppToast.showWarning(context, "No face detected!")
                    loadingState.value = false
                    return@addOnSuccessListener
                }
                
                val face = faces[0]
                val markers = mutableMapOf<Int, PointF>()
                
                face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { markers[15] = PointF(it.x, it.y) }
                face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { markers[16] = PointF(it.x, it.y) }
                face.getLandmark(FaceLandmark.NOSE_BASE)?.position?.let { markers[23] = PointF(it.x, it.y) }
                face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let { markers[27] = PointF(it.x, it.y) }
                face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let { markers[29] = PointF(it.x, it.y) }
                face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.let { markers[31] = PointF(it.x, it.y) }
                
                val bounds = face.boundingBox
                markers[19] = PointF(bounds.left.toFloat(), bounds.centerY().toFloat())
                markers[20] = PointF(bounds.right.toFloat(), bounds.centerY().toFloat())
                markers[1] = PointF(bounds.centerX().toFloat(), bounds.top.toFloat())
                markers[33] = PointF(bounds.centerX().toFloat(), bounds.bottom.toFloat())
                
                val width = bounds.width().toFloat()
                val height = bounds.height().toFloat()
                var rawScore = 0.85 
                
                val faceRatio = height / width
                val deviation = abs(faceRatio - 1.618)
                rawScore -= deviation * 0.5 
                
                val finalScore = rawScore.coerceIn(0.1, 0.99)
                
                scoreState.value = finalScore
                loadingState.value = false
            }
            .addOnFailureListener { e ->
                com.example.myapplication.utils.AppToast.showError(context, "Detection failed: ${e.message}")
                loadingState.value = false
            }

    } catch (e: Exception) {
        loadingState.value = false
    }
}

fun manualMeasurementsToMarkers(measurements: Map<String, Float>): Map<Int, PointF> {
    val markers = mutableMapOf<Int, PointF>()
    val faceHeight = measurements["face_height"] ?: 180f
    val faceWidth = measurements["face_width"] ?: 140f
    val foreheadHeight = measurements["forehead_height"] ?: 60f
    val noseHeight = measurements["nose_height"] ?: 50f
    val lowerFaceHeight = measurements["lower_face_height"] ?: 70f

    val centerX = faceWidth / 2f
    markers[1] = PointF(centerX, 0f) 
    markers[33] = PointF(centerX, faceHeight)
    markers[11] = PointF(centerX, foreheadHeight) 
    markers[23] = PointF(centerX, foreheadHeight + noseHeight * 0.8f) 
    markers[31] = PointF(centerX, faceHeight - lowerFaceHeight * 0.5f) 
    markers[19] = PointF(0f, faceHeight * 0.5f) 
    markers[20] = PointF(faceWidth, faceHeight * 0.5f) 
    val eyeY = foreheadHeight
    markers[15] = PointF(centerX - 20f, eyeY)
    markers[16] = PointF(centerX + 20f, eyeY) 
    return markers
}

fun calculateBeautyScore(markers: Map<Int, PointF>): Double {
    val phi = (1 + sqrt(5.0)) / 2
    val scores = mutableListOf<Double>()

    val proportions = listOf(
        Proportion("Prop1", Pair(11, 31), Pair(31, 33)),
        Proportion("Prop2", Pair(11, 23), Pair(23, 33)),
        Proportion("Prop3", Pair(11, 21), Pair(21, 25)),
    )

    for (prop in proportions) {
        val p1 = markers[prop.d1.first]
        val p2 = markers[prop.d1.second]
        val p3 = markers[prop.d2.first]
        val p4 = markers[prop.d2.second]
        if (p1 == null || p2 == null || p3 == null || p4 == null) continue

        val d1 = distance(p1, p2)
        val d2 = distance(p3, p4)
        if (d2 == 0.0) continue

        val ratio = d1 / d2
        val deviation = abs(ratio - phi) / phi
        val score = max(0.0, 1.0 - deviation)
        scores.add(score)
    }

    return if (scores.isEmpty()) 0.0 else scores.average()
}





