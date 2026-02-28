package com.example.myapplication.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.Badge
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun BadgeUnlockAnimation(
    badge: Badge,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var iconScale by remember { mutableFloatStateOf(0f) }

    // Scale animation: 0 -> 1.5 -> 1.0
    val animatedScale by animateFloatAsState(
        targetValue = iconScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "badgeScale"
    )

    // Auto-dismiss after 3 seconds
    LaunchedEffect(Unit) {
        visible = true
        delay(100)
        iconScale = 1.5f
        delay(300)
        iconScale = 1.0f
        delay(3000)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            // Confetti particles
            ConfettiEffect()

            // Badge unlock content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    "BADGE UNLOCKED!",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                // Badge icon with scale animation
                Icon(
                    imageVector = getBadgeIcon(badge.iconName),
                    contentDescription = badge.name,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier
                        .size(120.dp)
                        .scale(animatedScale)
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    badge.name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    badge.description,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700)
                    )
                ) {
                    Text("Continue", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ConfettiEffect() {
    var particles by remember {
        mutableStateOf(List(50) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = -0.1f,
                size = Random.nextFloat() * 8f + 4f,
                color = listOf(
                    Color(0xFFFFD700),
                    Color(0xFF2563EB),
                    Color(0xFF13EF92),
                    Color(0xFFF04C4C),
                    Color(0xFFFEE440)
                ).random(),
                speedY = Random.nextFloat() * 0.002f + 0.001f,
                rotation = Random.nextFloat() * 360f
            )
        })
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16) // ~60 FPS
            particles = particles.map { p ->
                p.copy(
                    y = if (p.y > 1.1f) -0.1f else p.y + p.speedY,
                    rotation = p.rotation + 2f
                )
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val centerX = size.width * particle.x
            val centerY = size.height * particle.y

            drawCircle(
                color = particle.color,
                radius = particle.size,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val speedY: Float,
    val rotation: Float
)

private fun getBadgeIcon(iconName: String): ImageVector {
    return when (iconName) {
        "EmojiEvents" -> Icons.Filled.EmojiEvents
        "FitnessCenter" -> Icons.Filled.FitnessCenter
        "LocalFireDepartment" -> Icons.Filled.LocalFireDepartment
        "Star" -> Icons.Filled.Star
        "Person" -> Icons.Filled.Person
        "Group" -> Icons.Filled.Group
        "WbSunny" -> Icons.Filled.WbSunny
        "NightsStay" -> Icons.Filled.NightsStay
        "CalendarToday" -> Icons.Filled.CalendarToday
        else -> Icons.Filled.EmojiEvents
    }
}
