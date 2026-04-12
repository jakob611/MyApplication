package com.example.myapplication.ui.screens

import android.content.Context
import com.example.myapplication.domain.math.Point2D
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
import coil.compose.AsyncImage
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.*
import com.example.myapplication.domain.looksmaxing.models.*
import com.example.myapplication.domain.looksmaxing.CalculateGoldenRatioUseCase
import com.example.myapplication.domain.looksmaxing.FaceDetectorProvider

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
                        val useCase = CalculateGoldenRatioUseCase()
                        val markers = useCase.manualMeasurementsToMarkers(manualMeasurements)
                        calculatedScore = useCase.calculateBeautyScore(markers)
                        advancedAnalysis = useCase.calculateAdvancedGoldenRatio(
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
    val coroutineScope = rememberCoroutineScope()
    val calculatedScore = remember { mutableStateOf<Double?>(null) }
    val advancedAnalysis = remember { mutableStateOf<GoldenRatioAnalysis?>(null) }
    val faceData = remember { mutableStateOf<DetectedFaceData?>(null) }
    val isLoading = remember { mutableStateOf(false) }

    val photoUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri.value != null) {
            processImage(context, photoUri.value!!, calculatedScore, advancedAnalysis, isLoading, coroutineScope, faceData)
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
            photoUri.value = uri
            processImage(context, uri, calculatedScore, advancedAnalysis, isLoading, coroutineScope, faceData)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (photoUri.value != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(bottom = 16.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photoUri.value,
                    contentDescription = "Selected Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )

                if (isLoading.value) {
                    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = "scanner_offset"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * offsetY
                        drawLine(
                            color = Color(0xFF00FF00),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 6f
                        )
                        drawRect(
                            color = Color(0xFF00FF00).copy(alpha = 0.2f),
                            topLeft = Offset(0f, y - 20f),
                            size = androidx.compose.ui.geometry.Size(size.width, 40f)
                        )
                    }
                    Text(
                        "ANALYZING...",
                        color = Color(0xFF00FF00),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    )
                } else if (calculatedScore.value != null && faceData.value != null) {
                    val data = faceData.value!!
                    // Draw Golden Ratio grid overlay on top of photo
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val scale = max(size.width / data.imageWidth.toFloat(), size.height / data.imageHeight.toFloat())
                        val offsetX = (size.width - data.imageWidth * scale) / 2f
                        val offsetY = (size.height - data.imageHeight * scale) / 2f

                        fun transform(p: Point2D): Offset {
                            return Offset(p.x * scale + offsetX, p.y * scale + offsetY)
                        }

                        val markers = data.markers

                        // Draw connected grid lines
                        val pTop = markers[1]
                        val pBottom = markers[33]
                        val pLeftFace = markers[19]
                        val pRightFace = markers[20]
                        val pLeftEye = markers[15]
                        val pRightEye = markers[16]
                        val pNose = markers[23]
                        val pMouthL = markers[27]
                        val pMouthR = markers[29]
                        val pMouthB = markers[31]

                        val lineColor = Color(0xFFFFD700).copy(alpha = 0.8f)

                        // Vertical symmetry line
                        if (pTop != null && pBottom != null) {
                            drawLine(lineColor, transform(pTop), transform(pBottom), strokeWidth = 3f)
                        }
                        
                        // Face width bounds
                        if (pLeftFace != null && pRightFace != null) {
                            drawLine(lineColor.copy(alpha=0.4f), transform(pLeftFace), Offset(transform(pLeftFace).x, transform(pBottom ?: pLeftFace).y), strokeWidth = 2f)
                            drawLine(lineColor.copy(alpha=0.4f), transform(pRightFace), Offset(transform(pRightFace).x, transform(pBottom ?: pRightFace).y), strokeWidth = 2f)
                        }

                        // Horizontal lines
                        if (pLeftEye != null && pRightEye != null) {
                            drawLine(Color.Cyan.copy(alpha = 0.6f), transform(pLeftEye), transform(pRightEye), strokeWidth = 3f)
                        }
                        if (pMouthL != null && pMouthR != null) {
                            drawLine(Color.Cyan.copy(alpha = 0.6f), transform(pMouthL), transform(pMouthR), strokeWidth = 3f)
                        }
                        
                        // Triangle from nose to mouth
                        if (pNose != null && pMouthL != null && pMouthR != null) {
                            val path = Path().apply {
                                moveTo(transform(pNose).x, transform(pNose).y)
                                lineTo(transform(pMouthL).x, transform(pMouthL).y)
                                lineTo(transform(pMouthR).x, transform(pMouthR).y)
                                close()
                            }
                            drawPath(path, color = Color(0xFFFFD700).copy(alpha = 0.4f), style = Stroke(width = 3f))
                        }

                        // Draw individual marker points
                        markers.values.forEach { p ->
                            drawCircle(Color.Red, radius = 6f, center = transform(p))
                            drawCircle(Color.White, radius = 3f, center = transform(p))
                        }
                    }
                }
            }
        }

        if (!isLoading.value) {
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
    loadingState: MutableState<Boolean>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    faceDataState: MutableState<DetectedFaceData?>
) {
    loadingState.value = true
    scoreState.value = null
    faceDataState.value = null

    val detector = FaceDetectorProvider.provideFaceDetector(context)

    coroutineScope.launch {
        try {
            val faceData = detector.detectFace(uri)

            delay(1000L) // Add scanning effect delay

            if (faceData == null || faceData.markers.isEmpty()) {
                com.example.myapplication.utils.AppToast.showWarning(context, "No face detected!")
                loadingState.value = false
                return@launch
            }

            val markers = faceData.markers
            val width = faceData.imageWidth.toFloat()
            val height = faceData.imageHeight.toFloat()

            val useCase = CalculateGoldenRatioUseCase()

            val eyeDist = useCase.distance(markers[15] ?: Point2D(0f,0f), markers[16] ?: Point2D(1f,0f))
            val mouthWidth = useCase.distance(markers[27] ?: Point2D(0f,0f), markers[29] ?: Point2D(1f,0f))

            // Ideal height/width for tight ML bounding box is ~1.4
            val faceRatio = height.toDouble() / width.toDouble()
            val devBase = abs(faceRatio - 1.4) / 1.4

            // Ideal eye dist / mouth width is ~1.0
            val ratioEyesMouth = if (mouthWidth > 0.0) eyeDist / mouthWidth else 1.0
            val devEyesMouth = abs(ratioEyesMouth - 1.0)

            // Face symmetry (distance from eyes to bounding box edges)
            val leftEdge = markers[19] ?: Point2D(0f,0f)
            val leftEye = markers[15] ?: Point2D(0f,0f)
            val rightEye = markers[16] ?: Point2D(0f,0f)
            val rightEdge = markers[20] ?: Point2D(0f,0f)
            val leftDist = useCase.distance(leftEye, leftEdge)
            val rightDist = useCase.distance(rightEye, rightEdge)
            val devSymmetry = abs(leftDist - rightDist) / (leftDist + rightDist + 1.0)

            // Penalize score dynamically if face is tilted or turned (3D rotation)
            val turnPenalty = abs(faceData.headEulerAngleY).toDouble() * 0.003
            val tiltPenalty = abs(faceData.headEulerAngleZ).toDouble() * 0.003

            val totalDeviation = (devBase * 0.3) + (devEyesMouth * 0.3) + (devSymmetry * 0.4) + turnPenalty + tiltPenalty

            // Realistic mapping: Average deviation ~0.1 to 0.15 gives scores ~ 62-75%
            // Highly symmetrical faces give scores 85-95%.
            val stretchedScore = 1.0 - (totalDeviation * 2.5)
            val finalScore = stretchedScore.coerceIn(0.35, 0.99)

            faceDataState.value = faceData
            scoreState.value = finalScore
            loadingState.value = false
            com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS)
        } catch (e: Exception) {
            com.example.myapplication.utils.AppToast.showError(context, "Detection failed: ${e.message}")
            loadingState.value = false
        }
    }
}

@Composable
fun MLKitScanningOverlay(
    isLoading: Boolean,
    calculatedScore: Double?,
    faceData: DetectedFaceData?
) {
    if (isLoading) {
        val infiniteTransition = rememberInfiniteTransition(label = "scanner")
        val offsetY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "scanner_offset"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height * offsetY
            drawLine(
                color = Color(0xFF00FF00),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 6f
            )
            drawRect(
                color = Color(0xFF00FF00).copy(alpha = 0.2f),
                topLeft = Offset(0f, y - 20f),
                size = androidx.compose.ui.geometry.Size(size.width, 40f)
            )
        }
    } else if (calculatedScore != null && faceData != null) {
        val data = faceData
        // Draw Golden Ratio grid overlay on top of photo
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = max(size.width / data.imageWidth.toFloat(), size.height / data.imageHeight.toFloat())
            val offsetX = (size.width - data.imageWidth * scale) / 2f
            val offsetY = (size.height - data.imageHeight * scale) / 2f

            fun transform(p: Point2D): Offset {
                return Offset(p.x * scale + offsetX, p.y * scale + offsetY)
            }

            val markers = data.markers

            // Draw connected grid lines
            val pTop = markers[1]
            val pBottom = markers[33]
            val pLeftFace = markers[19]
            val pRightFace = markers[20]
            val pLeftEye = markers[15]
            val pRightEye = markers[16]
            val pNose = markers[23]
            val pMouthL = markers[27]
            val pMouthR = markers[29]
            val pMouthB = markers[31]

            val lineColor = Color(0xFFFFD700).copy(alpha = 0.8f)

            // Vertical symmetry line
            if (pTop != null && pBottom != null) {
                drawLine(lineColor, transform(pTop), transform(pBottom), strokeWidth = 3f)
            }

            // Face width bounds
            if (pLeftFace != null && pRightFace != null) {
                drawLine(lineColor.copy(alpha=0.4f), transform(pLeftFace), Offset(transform(pLeftFace).x, transform(pBottom ?: pLeftFace).y), strokeWidth = 2f)
                drawLine(lineColor.copy(alpha=0.4f), transform(pRightFace), Offset(transform(pRightFace).x, transform(pBottom ?: pRightFace).y), strokeWidth = 2f)
            }

            // Horizontal lines
            if (pLeftEye != null && pRightEye != null) {
                drawLine(Color.Cyan.copy(alpha = 0.6f), transform(pLeftEye), transform(pRightEye), strokeWidth = 3f)
            }
            if (pMouthL != null && pMouthR != null) {
                drawLine(Color.Cyan.copy(alpha = 0.6f), transform(pMouthL), transform(pMouthR), strokeWidth = 3f)
            }

            // Triangle from nose to mouth
            if (pNose != null && pMouthL != null && pMouthR != null) {
                val path = Path().apply {
                    moveTo(transform(pNose).x, transform(pNose).y)
                    lineTo(transform(pMouthL).x, transform(pMouthL).y)
                    lineTo(transform(pMouthR).x, transform(pMouthR).y)
                    close()
                }
                drawPath(path, color = Color(0xFFFFD700).copy(alpha = 0.4f), style = Stroke(width = 3f))
            }

            // Draw individual marker points
            markers.values.forEach { p ->
                drawCircle(Color.Red, radius = 6f, center = transform(p))
                drawCircle(Color.White, radius = 3f, center = transform(p))
            }
        }
    }
}
