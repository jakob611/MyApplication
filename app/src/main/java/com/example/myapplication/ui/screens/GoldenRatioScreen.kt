package com.example.myapplication.ui.screens

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Definicija proporcij za napredno analizo zlatega reza na obrazu.
 * Vsaka proporcija vsebuje dva razmerja med markerji in težo.
 */
data class Proportion(
    val name: String,
    val d1: Pair<Int, Int>,
    val d2: Pair<Int, Int>,
    val weight: Double = 1.0 // Pomen razmerja
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

/**
 * Napredna funkcija za analizo zlatega reza na obrazu.
 * Uporablja več markerjev in ponderirana razmerja, ter prikaže podrobne rezultate.
 */
fun calculateAdvancedGoldenRatio(
    markers: Map<Int, PointF>,
    normalizeBy: Pair<Int, Int>? = null // npr. širina obraza
): GoldenRatioAnalysis {
    val phi = (1 + sqrt(5.0)) / 2 // Zlati rez ≈ 1.618

    val proportions = listOf(
        Proportion("Središče-zgornja/spodnja", Pair(11, 31), Pair(31, 33), weight = 1.0),
        Proportion("Središče-nos/usta", Pair(23, 31), Pair(31, 33), weight = 1.2),
        Proportion("Širina obraza / razdalja med zenicama", Pair(19, 20), Pair(15, 16), weight = 1.5),
        Proportion("Višina nosu / širina nosu", Pair(11, 23), Pair(15, 16), weight = 1.3),
        Proportion("Višina zgornje ustnice / spodnje ustnice", Pair(23, 31), Pair(31, 33), weight = 1.0),
        // Dodaj še več proporcij po želji!
    )

    // Normalizacija po širini obraza (opcijsko)
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

    // Ponderirano povprečje
    val totalWeight = ratioResults.sumOf { it.weight }
    val weightedScore = if (totalWeight > 0)
        ratioResults.sumOf { it.score * it.weight } / totalWeight
    else 0.0

    return GoldenRatioAnalysis(ratioResults, weightedScore)
}

/**
 * Izračun evklidske razdalje med dvema točkama.
 */
private fun distance(p1: PointF, p2: PointF): Double {
    return sqrt((p1.x - p2.x).toDouble().pow(2) + (p1.y - p2.y).toDouble().pow(2))
}

// ---- UI DEL ----

@Composable
fun GoldenRatioScreen(
    onBack: () -> Unit = {}
) {
    var analysisMode by remember { mutableStateOf("auto") }
    var manualMeasurements by remember { mutableStateOf(mutableMapOf<String, Float>()) }
    var calculatedScore by remember { mutableStateOf<Double?>(null) }
    var advancedAnalysis by remember { mutableStateOf<GoldenRatioAnalysis?>(null) }

    val backgroundGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF17223B),
            Color(0xFF25304A),
            Color(0xFF193446),
            Color(0xFF1E2D24)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                "Golden Ratio Analysis",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                color = Color(0xFFFEE440),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                "Medieval beauty assessment using mathematical proportions",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7A99),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Info Card
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
                            tint = Color(0xFFFEE440),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "About Golden Ratio",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFEE440)
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

            // Mode Toggle
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
                        selectedContainerColor = Color(0xFFFEE440),
                        selectedLabelColor = Color(0xFF2A1810)
                    )
                )
                FilterChip(
                    onClick = { analysisMode = "manual" },
                    label = { Text("Manual Calculator") },
                    selected = analysisMode == "manual",
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFEE440),
                        selectedLabelColor = Color(0xFF2A1810)
                    )
                )
            }

            if (analysisMode == "auto") {
                // Auto Analysis Section
                AutoAnalysisSection()
            } else {
                ManualCalculatorSection(
                    measurements = manualMeasurements,
                    onMeasurementChange = { key, value ->
                        manualMeasurements[key] = value
                    },
                    onCalculate = {
                        // Pretvori meritve v markerje
                        val markers = manualMeasurementsToMarkers(manualMeasurements)
                        calculatedScore = calculateBeautyScore(markers)
                        advancedAnalysis = calculateAdvancedGoldenRatio(
                            markers,
                            normalizeBy = Pair(19, 20) // normalize by face width
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                // TODO: Implement camera functionality
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEE440)
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
                // TODO: Implement gallery functionality
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFFEE440)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "Upload from Gallery",
                style = MaterialTheme.typography.titleMedium
            )
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
    val measurementInputs = remember { mutableStateMapOf<String, String>() }

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
                color = Color(0xFFFEE440),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val keyMeasurements = listOf(
                "face_height" to "Face Height (hairline to chin)",
                "face_width" to "Face Width (at cheekbones)",
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
                        focusedBorderColor = Color(0xFFFEE440),
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
                    containerColor = Color(0xFFFEE440)
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
                        containerColor = Color(0xFF1A2435)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Beauty Score",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFEE440)
                        )
                        Text(
                            "${(score * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White
                        )
                        Text(
                            "Based on Golden Ratio proportions",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB6C6E6)
                        )
                    }
                }
            }

            // Napredni prikaz analiziranih razmerij
            advancedAnalysis?.let { analysis ->
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF25304A)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Napredni Golden Ratio pregled",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFEE440),
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
                                "${r.name}: ${"%.2f".format(r.ratio)} (odstopanje: ${"%.1f".format(r.deviation * 100)}%)",
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

/**
 * Pretvori ročne meritve v markerje za analizo.
 */
fun manualMeasurementsToMarkers(measurements: Map<String, Float>): Map<Int, PointF> {
    val markers = mutableMapOf<Int, PointF>()
    val faceHeight = measurements["face_height"] ?: 180f
    val faceWidth = measurements["face_width"] ?: 140f
    val foreheadHeight = measurements["forehead_height"] ?: 60f
    val noseHeight = measurements["nose_height"] ?: 50f
    val lowerFaceHeight = measurements["lower_face_height"] ?: 70f

    val centerX = faceWidth / 2f

    markers[1] = PointF(centerX, 0f) // Vrh glave
    markers[33] = PointF(centerX, faceHeight) // Dno brade
    markers[11] = PointF(centerX, foreheadHeight) // Središče zenic
    markers[23] = PointF(centerX, foreheadHeight + noseHeight * 0.8f) // Nosnice
    markers[31] = PointF(centerX, faceHeight - lowerFaceHeight * 0.5f) // Središče ustnic

    markers[19] = PointF(0f, faceHeight * 0.5f) // Leva stran obraza
    markers[20] = PointF(faceWidth, faceHeight * 0.5f) // Desna stran obraza

    val eyeY = foreheadHeight
    markers[15] = PointF(centerX - 20f, eyeY) // Notranji rob levega očesa
    markers[16] = PointF(centerX + 20f, eyeY) // Notranji rob desnega očesa

    return markers
}

/**
 * Osnovni izračun ocene lepote glede na zlatni rez.
 * (Ohranjeno zaradi združljivosti z obstoječo funkcionalnostjo)
 */
fun calculateBeautyScore(markers: Map<Int, PointF>): Double {
    val phi = (1 + sqrt(5.0)) / 2 // Zlati rez ≈ 1.618
    val scores = mutableListOf<Double>()

    val proportions = listOf(
        Proportion("Prop1", Pair(11, 31), Pair(31, 33)),
        Proportion("Prop2", Pair(11, 23), Pair(23, 33)),
        Proportion("Prop3", Pair(11, 21), Pair(21, 25)),
        Proportion("Prop4", Pair(3, 9), Pair(9, 17)),
        Proportion("Prop5", Pair(11, 23), Pair(23, 29)),
        Proportion("Prop6", Pair(27, 29), Pair(29, 32)),
        Proportion("Prop7", Pair(23, 27), Pair(27, 29)),
        Proportion("Prop8", Pair(19, 15), Pair(15, 20)),
        Proportion("Prop9", Pair(19, 15), Pair(15, 16)),
        Proportion("Prop10", Pair(2, 13), Pair(13, 19)),
        Proportion("Prop11", Pair(19, 13), Pair(13, 15)),
        Proportion("Prop12", Pair(19, 7), Pair(7, 13)),
        Proportion("Prop13", Pair(2, 24), Pair(24, 30)),
        Proportion("Prop14", Pair(29, 27), Pair(27, 30)),
        Proportion("Prop15", Pair(34, 35), Pair(35, 36)),
        Proportion("Prop16", Pair(1, 33), Pair(19, 20))
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