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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel
import com.example.myapplication.domain.model.BodyRatioStatus
import com.example.myapplication.domain.model.BodyField
import com.example.myapplication.viewmodels.BodyUiEvent
import com.example.myapplication.viewmodels.GoldenRatioUiState
import com.example.myapplication.R

/**
 * Faza 30.7 — UI mapper: Pretvori domain enum v ID string resursa.
 * Faza 31.1 — Vrača Int (R.string.*) namesto hardkodiranega niza.
 *   UI kliče stringResource(r.status.toDisplayStringRes()) → lokalizabilen prikaz.
 *   Emojiji so del string resursa v strings.xml (univerzalni, ne lokalizirajo se).
 */
fun BodyRatioStatus.toDisplayStringRes(): Int = when (this) {
    BodyRatioStatus.GOLDEN_RATIO -> R.string.status_golden_ratio
    BodyRatioStatus.EXCELLENT    -> R.string.status_excellent
    BodyRatioStatus.GOOD         -> R.string.status_good
    BodyRatioStatus.AVERAGE      -> R.string.status_average
    BodyRatioStatus.NEEDS_WORK   -> R.string.status_needs_work
}

@Composable
fun AutoAnalysisSection() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val calculatedScore = remember { mutableStateOf<Double?>(null) }
    val advancedAnalysis = remember { mutableStateOf<GoldenRatioAnalysis?>(null) }
    val faceData = remember { mutableStateOf<DetectedFaceData?>(null) }
    val isLoading = remember { mutableStateOf(false) }

    // FIX: Ločimo file URI (za kamero) od display URI (za AsyncImage).
    // displayUri se nastavi ŠELE ko kamera uspešno shrani sliko → Coil ne cachira prazne datoteke.
    // rememberSaveable: Uri je Parcelable → preživi config change (zasuk zaslona).
    val displayUri = rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraFileUri = remember { mutableStateOf<Uri?>(null) }  // samo za launch

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraFileUri.value != null) {
            // Šele zdaj nastavimo displayUri → Coil dobi veljavno sliko
            displayUri.value = cameraFileUri.value
            processImage(context, cameraFileUri.value!!, calculatedScore, advancedAnalysis, isLoading, coroutineScope, faceData)
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
                cameraFileUri.value = uri  // shranimo file URI, NE display URI
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
            displayUri.value = uri  // galerija: takoj prikaži
            processImage(context, uri, calculatedScore, advancedAnalysis, isLoading, coroutineScope, faceData)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (displayUri.value != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(bottom = 16.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // FIX: diskCachePolicy DISABLED → Coil vedno reloadira svežo sliko iz kamere
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(displayUri.value)
                        .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                        .crossfade(true)
                        .build(),
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
                            file.parentFile?.mkdirs()

                            val uri = FileProvider.getUriForFile(
                                context.applicationContext,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            cameraFileUri.value = uri  // FIX: shranimo le file URI, NE display URI
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

    val detector = com.example.myapplication.data.looksmaxing.AndroidMLKitFaceDetector(context)

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

// ── Navigacijski wrapper ─────────────────────────────────────────────────────
// Faza 30.1: UI je PASIVEN — bere stanje iz ViewModela, ne drži surovih podatkov.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldenRatioScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    // Faza 31.1: enoten UI state — nadomešča bodyProfile + goldenRatioResults + isSaving
    val bodyVm: BodyModuleHomeViewModel = viewModel(
        factory = com.example.myapplication.ui.screens.MyViewModelFactory(context.applicationContext)
    )
    val uiState by bodyVm.goldenRatioUiState.collectAsStateWithLifecycle()

    // Faza 30.9 — SnackbarHostState za prikaz enkratnih napak
    val snackbarHostState = remember { SnackbarHostState() }

    // Faza 31.0 — flowWithLifecycle: zbiranje se samodejno ustavi ko zaslon ni odprt
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        bodyVm.uiEvents
            .flowWithLifecycle(lifecycleOwner.lifecycle)
            .collect { event ->
                when (event) {
                                    is BodyUiEvent.SaveSuccess -> {
                                        snackbarHostState.showSnackbar(
                                            message  = "✅ Meritve uspešno shranjene!",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    is BodyUiEvent.Error -> {
                                        snackbarHostState.showSnackbar(
                                            message     = "❌ ${event.message}",
                                            actionLabel = "Zapri",
                                            duration    = SnackbarDuration.Long
                                        )
                                    }
                                     is BodyUiEvent.ShowSnackbar -> {
                                        // Faza 32.4 — Prehodna napaka akcij (SwapDays, CompleteWorkoutSession itd.)
                                        snackbarHostState.showSnackbar(
                                            message     = "⚠️ ${event.message}",
                                            actionLabel = "Zapri",
                                            duration    = SnackbarDuration.Long
                                        )
                                    }
                                    // Faza 35 — AuthExpired: na tem zaslonu samo prikaži opozorilo.
                                    // BodyModuleHomeScreen že skrbi za navigacijo nazaj.
                                    is BodyUiEvent.AuthExpired -> {
                                        snackbarHostState.showSnackbar(
                                            message  = "⚠️ Seja je potekla. Prijavite se znova.",
                                            duration = SnackbarDuration.Long
                                        )
                                    }
                                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Golden Ratio Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        // Faza 30.9 — Snackbar za prikaz napak in potrditev ob shranjevanju
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── Sekcija 1: Obrazna analiza (kamera / galerija) ─────────────────
            // Neodvisna od profila — vedno prikazana
            AutoAnalysisSection()

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // ── Sekcija 2: Telesni Zlati Rez — Faza 31.1: when (uiState) ───────
            // Loading → spinner (profil se nalaga iz Firestore)
            // Success → celotni vmesnik z vnosi in opcijskim rezultatom
            // Error   → sporočilo o napaki (vrednosti zunaj bioloških meja)
            when (val state = uiState) {
                is GoldenRatioUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                }

                is GoldenRatioUiState.Success -> {
                    BodyGoldenRatioSection(
                        profileHeight      = state.profileHeight,
                        // Faza 31.4: strings iz VM stanja (ne lokalnih vars)
                        shoulderInput      = state.shoulderInput,
                        waistInput         = state.waistInput,
                        hipInput           = state.hipInput,
                        goldenRatioResults = state.data,
                        isSaving           = state.isSaving,
                        invalidFields      = state.invalidFields,
                        onInputChanged     = { s, w, h -> bodyVm.updateInputText(s, w, h) },
                        onSave             = { shoulder, waist, hip, height ->
                            bodyVm.saveBodyMeasurements(shoulder, waist, hip, height)
                        }
                    )
                }

                is GoldenRatioUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = stringResource(state.messageRes),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Faza 30.1 — Pasivni UI za telesni Zlati Rez.
 * Faza 31.4 — Input strings prihajajo iz VM stanja (ne lokalnega rememberSaveable).
 *   Preživijo vsak UI state transition in config change brez izgube vrednosti.
 */
@Composable
fun BodyGoldenRatioSection(
    profileHeight: Double?,
    /** Faza 31.4 — surovi vnosni teksti iz GoldenRatioUiState.Success */
    shoulderInput: String,
    waistInput: String,
    hipInput: String,
    goldenRatioResults: com.example.myapplication.domain.model.BodyGoldenRatioResult?,
    isSaving: Boolean = false,
    /**
     * Faza 31.3 — Set polj, ki so zunaj bioloških meja (30–250 cm).
     * UI nastavi isError = true SAMO na poljih znotraj tega seta.
     */
    invalidFields: Set<BodyField> = emptySet(),
    /**
     * Faza 31.5 — true ko so vsa 4 polja uspešno parsana v > 0.
     * false + data==null + invalidFields.isEmpty() → prikaži napotek za vnos.
     */
    isInputComplete: Boolean = false,
    /** Faza 31.4 — Callback z surovimi string vrednostmi (parsing je v VM) */
    onInputChanged: (shoulder: String, waist: String, hip: String) -> Unit,
    onSave: ((shoulder: Double, waist: Double, hip: Double, height: Double) -> Unit)? = null
) {
    // Višina iz profila — samo kot prikazna vrednost
    val heightDisplay = profileHeight?.let { "${"%.0f".format(it)} cm" } ?: "—"

    // Faza 31.9 — Popravek #3: Lokalni rememberSaveable za TextField vrednosti.
    //
    // Prejšnje stanje: TextField.value = state.shoulderInput (iz VM StateFlow).
    // Ko je uporabnik vtipkal znak → onValueChange → VM StateFlow posodobitev → rekomposicija →
    // TextField dobi novo vrednost iz StateFlow → ASYNC zanka → kazalec je skočil na konec.
    //
    // Popravek: TextField.value = lokalni var (sync, brez async round-tripa).
    // ViewModel še vedno prejema vsako spremembo za izračun Golden Ratio.
    // rememberSaveable (ne remember) zagotavlja preživetje rotacije zaslona.
    //
    // Inicializacija iz VM parametrov: enkrat ob prvem kompozicija, ko imamo obstoječe vrednosti
    // (npr. pri navigaciji nazaj z obstoječimi podatki). LaunchedEffect zagotovi, da se sync
    // izvede samo ko se VM vrednosti spremenijo EXTERNALLY (ne med tipkanjem, ker tipkanje
    // posodablja VM ki sproži ta LaunchedEffect, toda lokalni var je že enak → brez efekta).
    var localShoulder by rememberSaveable { mutableStateOf(shoulderInput) }
    var localWaist    by rememberSaveable { mutableStateOf(waistInput) }
    var localHip      by rememberSaveable { mutableStateOf(hipInput) }

    // Sync samo ob ZUNANJI spremembi VM vrednosti (ne med tipkanjem).
    // Tipkanje: local → VM (posybodobitev) → VM emitira isto vrednost → lokalni == VM → NoOp.
    // Zunanja sprememba (npr. resetiranje obrazca): VM se spremeni → lokalni != VM → sync.
    LaunchedEffect(shoulderInput) { if (localShoulder != shoulderInput) localShoulder = shoulderInput }
    LaunchedEffect(waistInput)    { if (localWaist    != waistInput)    localWaist    = waistInput    }
    LaunchedEffect(hipInput)      { if (localHip      != hipInput)      localHip      = hipInput      }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2340)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "🏆 Telesni Zlati Rez (Adonis Index)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Idealno razmerje ramen/pas ≈ 1.618 (φ)",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8899BB)
            )
            Spacer(Modifier.height(16.dp))

            // Višina iz profila (samo-prikazna)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Višina (iz profila): ", color = Color(0xFFB6C6E6), style = MaterialTheme.typography.bodyMedium)
                Text(heightDisplay, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))

            // Vnos meritev
            // Faza 31.9 — FieldConfig: value iz LOKALNEGA stanja (ne VM StateFlow) → brez cursor jumping.
            // onValueChange: (1) posodobi lokalni var (sync, takojšen prikaz) in
            //                (2) obvesti VM za izračun Golden Ratio (VM ne vrne vrednosti nazaj v TextField).
            data class FieldConfig(
                val bodyField: BodyField,
                val label: String,
                val value: String,
                val onValueChange: (String) -> Unit
            )
            val fields = listOf(
                FieldConfig(BodyField.SHOULDER, "Obseg ramen (cm)", localShoulder,
                    { newVal -> localShoulder = newVal; onInputChanged(newVal, localWaist, localHip) }),
                FieldConfig(BodyField.WAIST,    "Obseg pasu (cm)",  localWaist,
                    { newVal -> localWaist = newVal;    onInputChanged(localShoulder, newVal, localHip) }),
                FieldConfig(BodyField.HIP,      "Obseg bokov (cm)", localHip,
                    { newVal -> localHip = newVal;      onInputChanged(localShoulder, localWaist, newVal) })
            )
            fields.forEach { field ->
                OutlinedTextField(
                    value = field.value,
                    onValueChange = field.onValueChange,
                    label = { Text(field.label, color = Color(0xFFB6C6E6)) },
                    suffix = { Text("cm", color = Color(0xFFB6C6E6)) },
                    // Faza 31.3: isError = true SAMO na poljih, ki so v invalidFields setu
                    isError = invalidFields.contains(field.bodyField),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = Color(0xFF6B7A99),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        errorBorderColor     = MaterialTheme.colorScheme.error,
                        errorLabelColor      = MaterialTheme.colorScheme.error,
                        errorCursorColor     = MaterialTheme.colorScheme.error
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            // Inline validacijska napaka pod polji (vidna samo ko je vsaj eno polje neveljavno)
            if (invalidFields.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 2.dp, bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = stringResource(R.string.error_invalid_measurements),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Gumb za shranjevanje
            if (onSave != null) {
                // Faza 31.9: canSave bere iz LOKALNIH spremenljivk (ne VM state parametrov)
                val canSave = localShoulder.isNotBlank() && localWaist.isNotBlank() && invalidFields.isEmpty()
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (!isSaving && canSave) {
                            // Parsiraj v Double za onSave callback (backward compat)
                            onSave(
                                localShoulder.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                localWaist.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                localHip.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                profileHeight ?: 0.0
                            )
                        }
                    },
                    enabled = canSave && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF2A1810),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            "💾 Shrani meritve",
                            color = Color(0xFF2A1810),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Faza 31.6 — HEIGHT opozorilo je VEDNO vidno, neodvisno od tega ali je izračun izveden.
            // Ker UseCase preskoči HEIGHT validacijo ko heightCm == 0.0, vrne ValidationResult.Success
            // in prikažejo se rezultati BREZ waistToHeightRatio — uporabnik pa ne ve zakaj.
            // Popravek: opozorilo je zunaj goldenRatioResults?.let bloka.
            val heightMissing = profileHeight == null || profileHeight == 0.0
            if (heightMissing && invalidFields.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        "⚠️ V nastavitvah profila morate najprej nastaviti svojo višino, da lahko izračunamo razmerja.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Pasivni prikaz rezultatov iz ViewModela
            goldenRatioResults?.let { r ->
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1810))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            // Faza 31.1: stringResource() → lokalizabilen niz iz strings.xml
                            stringResource(r.status.toDisplayStringRes()),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Razmerje ramen/pas: ${"%.3f".format(r.shoulderToWaistRatio)} (φ ≈ 1.618)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        if (r.waistToHeightRatio > 0.0) {
                            Text(
                                "Razmerje pas/višina: ${"%.3f".format(r.waistToHeightRatio)} (ideal < 0.50)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB6C6E6)
                            )
                        }
                        if (r.shoulderToHipRatio > 0.0) {
                            Text(
                                "Razmerje ramen/boki: ${"%.3f".format(r.shoulderToHipRatio)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB6C6E6)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Skupni rezultat: ${"%.0f".format(r.overallScore * 100)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Odmik od φ: ${"%.1f".format(r.deviationFromPhi * 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8899BB)
                        )
                    }
                }
            } ?: run {
                // Faza 31.5/31.6 — Ko ni rezultata in ni napak in višina je OK → nevtralni napotek
                if (invalidFields.isEmpty() && !heightMissing && !isInputComplete) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            "💡 Vnesite vse mere za izračun vašega Zlatega reza.",
                            color = Color(0xFF8899BB),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

