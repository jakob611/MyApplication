package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(
    userEmail: String,
    onLogout: () -> Unit,
    onModuleClick: (String) -> Unit,
    onAccountClick: () -> Unit,
    onProClick: () -> Unit,
    onOpenMenu: () -> Unit,
    showLocalHeader: Boolean = false // globalni top bar je zdaj povsod, zato je lokalni skrit
) {
    val PrimaryBlue = Color(0xFF2563EB)
    val SurfaceBg = Color(0xFFF3F4F6)
    val CardBg = Color(0xFFE9EAEC)
    val TitleColor = Color(0xFF111827)
    val BodyColor = Color(0xFF4B5563)
    val ProgressTrack = Color(0xFFD1D5DB)

    val backgroundGradient = Brush.verticalGradient(listOf(SurfaceBg, Color.White, SurfaceBg))

    Surface(color = SurfaceBg) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (showLocalHeader) {
                    // Lokalni header samo, če ga želiš izrecno prikazati
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = onOpenMenu,
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = PrimaryBlue.copy(alpha = 0.95f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "User's\nStickman",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            AssistChip(
                                onClick = onProClick,
                                label = {
                                    Text(
                                        text = "Upgrade to Premium",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = PrimaryBlue,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // BODY MODULE
                item {
                    ModuleCard(
                        title = "BODY MODULE",
                        description = "Description: Bla bal doefu ohf hfpo eh odeg fog fo zdog dfo fozg of fog fsog fsg fsi gfsog fsog fsog fsog fsog fsog.",
                        progress = 0.10f,
                        primaryBlue = PrimaryBlue,
                        cardBg = CardBg,
                        titleColor = TitleColor,
                        bodyColor = BodyColor,
                        progressTrack = ProgressTrack,
                        onEnter = { onModuleClick("Body") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FitnessCenter,
                                contentDescription = "Body",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    )
                }

                // FACE MODULE
                item {
                    ModuleCard(
                        title = "FACE MODULE",
                        description = "Description: Bla bal doefu ohf hfpo eh odeg fog fo zdog dfo fozg of fog fsog fsg fsi gfsog fsog fsog fsog fsog fsog.",
                        progress = 0.05f,
                        primaryBlue = PrimaryBlue,
                        cardBg = CardBg,
                        titleColor = TitleColor,
                        bodyColor = BodyColor,
                        progressTrack = ProgressTrack,
                        onEnter = { onModuleClick("Face") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Face,
                                contentDescription = "Face",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    title: String,
    description: String,
    progress: Float,
    primaryBlue: Color,
    cardBg: Color,
    titleColor: Color,
    bodyColor: Color,
    progressTrack: Color,
    onEnter: () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor
                    )
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(color = bodyColor)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color = primaryBlue)
                )
                Spacer(Modifier.width(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) }, // NOV API: lambda
                    color = primaryBlue,
                    trackColor = progressTrack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            ElevatedButton(
                onClick = onEnter,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = primaryBlue,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier.widthIn(min = 140.dp)
            ) {
                Text(
                    text = "ENTER",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}