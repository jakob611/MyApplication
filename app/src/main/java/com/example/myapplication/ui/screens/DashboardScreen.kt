package com.example.myapplication.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DashboardModule(
    val title: String,
    val subtitle: String,
    val containerColor: Color,
    val textColor: Color,
    val enabled: Boolean = true
)

@Composable
fun DashboardScreen(
    userEmail: String,
    onLogout: () -> Unit,
    onModuleClick: (String) -> Unit,
    onAccountClick: () -> Unit,
    onProClick: () -> Unit,
    onOpenMenu: () -> Unit,
    showLocalHeader: Boolean
) {
    val modules = listOf(
        DashboardModule(
            title = "BODY MODULE",
            subtitle = "Unlock your full physical potential with customized workout plans, intelligent progress tracking, and complete nutrition management.",
            containerColor = MaterialTheme.colorScheme.secondary,
            textColor = MaterialTheme.colorScheme.onSecondary
        ),
        DashboardModule(
            title = "HAIR MODULE",
            subtitle = "Coming Soon: Discover specialized hair care routines, maintenance plans, and expert advice for healthier hair.",
            containerColor = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary
        ),
        DashboardModule(
            title = "FACE MODULE",
            subtitle = "Analyze your facial features, monitor your skin health, and practice targeted exercises to improve your natural glow.",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        modules.forEach { m ->
            DashboardModuleCard(
                modifier = Modifier.weight(1f),
                title = m.title,
                subtitle = m.subtitle,
                containerColor = m.containerColor,
                textColor = m.textColor,
                enabled = m.enabled,
                onClick = { if (m.enabled) onModuleClick(m.title.replace(" MODULE", "").lowercase().replaceFirstChar { it.uppercase() }) } // Mapping back to standard "Body", "Face"
            )
        }
    }
}

@Composable
private fun DashboardModuleCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    containerColor: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                 if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = textColor.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { 0.2f },
                modifier = Modifier.fillMaxWidth(0.6f).height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = textColor.copy(alpha = 0.1f),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(
                    text = "ENTER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}