package com.example.myapplication.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
fun PlanPathScreen(
    currentDay: Int,
    isTodayDone: Boolean,
    message: String,
    subMessage: String,
    totalDays: Int = 30,
    onBack: () -> Unit,
    onStartDay: (Int) -> Unit,
    onExtraWorkout: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Plan view",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            // Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isTodayDone) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onExtraWorkout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Do Extra Workout")
                        }
                    }
                }
            }

            // Path View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(vertical = 24.dp)
            ) {
                PathCanvas(
                    startDay = 1,
                    endDay = 7,
                    currentDay = currentDay,
                    isTodayDone = isTodayDone,
                    onNodeClick = { day ->
                        if (day < currentDay) {
                            // Already done
                        } else if (day == currentDay) {
                            if (isTodayDone) {
                                // Already done today
                            } else {
                                onStartDay(day)
                            }
                        } else {
                            // Future -> Locked
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PathCanvas(
    startDay: Int,
    endDay: Int,
    currentDay: Int,
    isTodayDone: Boolean,
    onNodeClick: (Int) -> Unit
) {
    val nodeRadius = 28.dp
    val spacing = 100.dp
    val horizontalSway = 100.dp // Amplitude of the wave

    // We need to layout items. Since it is a canvas with interactions, we might want to manually compose layout or use Box with offsets.
    // Using Box with offsets is easier for clicks.

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(spacing * (endDay - startDay + 1) + 50.dp) // Height estimation
    ) {
        val density = LocalContext.current.resources.displayMetrics.density
        val totalNodes = endDay - startDay + 1

        // Draw the curved line first
        Canvas(modifier = Modifier.matchParentSize()) {
            val path = Path()
            val swayPx = horizontalSway.toPx()
            val centerX = size.width / 2

            // Calculate positions
            val points = (0 until totalNodes).map { i ->
                val y = (i * spacing.toPx()) + 50.dp.toPx()
                // Sine wave pattern:
                // i=0 -> x=center (or slightly left/right)
                // We want a snake.
                val x = centerX + (swayPx * sin(i.toDouble() * 1.5)).toFloat() // 1.5 factor controls frequency roughly
                Offset(x, y)
            }

            if (points.isNotEmpty()) {
                path.moveTo(points[0].x, points[0].y)
                // Draw bezier or straight lines to next points
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i+1]

                    // Control points for bezier to make it smooth
                    val cp1 = Offset(p1.x, p1.y + spacing.toPx() / 2)
                    val cp2 = Offset(p2.x, p2.y - spacing.toPx() / 2)

                    path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                }

                drawPath(
                    path = path,
                    color = Color.Gray.copy(alpha = 0.5f),
                    style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f))
                )
            }
        }

        // Draw Nodes
        val centerXWindow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp / 2

        for (i in 0 until totalNodes) {
            val dayNumber = startDay + i
            val yOffset = (i * spacing.value).dp + 50.dp - nodeRadius // Center vertical
            val xSway = (horizontalSway.value * sin(i.toDouble() * 1.5))
            val xOffset = centerXWindow + xSway.dp - nodeRadius // Center horizontal

            val state = when {
                dayNumber < currentDay -> NodeState.DONE
                dayNumber == currentDay -> if (isTodayDone) NodeState.DONE else NodeState.CURRENT
                else -> NodeState.LOCKED
            }

            Box(
                modifier = Modifier
                    .offset(x = xOffset, y = yOffset)
                    .size(nodeRadius * 2)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            NodeState.DONE -> Color(0xFFE0E0E0) // Gray background for done
                            NodeState.CURRENT -> Color(0xFF2563EB) // Blue for current
                            NodeState.LOCKED -> Color(0xFFE0E0E0) // Gray for locked
                        }
                    )
                    .clickable(enabled = state != NodeState.LOCKED && state != NodeState.DONE) {
                        onNodeClick(dayNumber)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (state == NodeState.CURRENT) {
                    Text(
                        text = "$dayNumber",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                } else {
                    Text(
                        text = "$dayNumber",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

enum class NodeState {
    DONE, CURRENT, LOCKED
}
