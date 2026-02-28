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
import androidx.compose.material.icons.Icons
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
    val accent: Color,
    val iconType: String
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
            title = "Body",
            subtitle = "Training plan & runs",
            accent = Color(0xFF2563EB),
            iconType = "body"
        ),
        DashboardModule(
            title = "Face",
            subtitle = "Skincare & face routine",
            accent = Color(0xFF7C3AED),
            iconType = "face"
        ),
        DashboardModule(
            title = "Hair",
            subtitle = "Hair routine & tips",
            accent = Color(0xFF0EA5E9),
            iconType = "hair"
        ),
        DashboardModule(
            title = "Shop",
            subtitle = "Products & recommendations",
            accent = Color(0xFF10B981),
            iconType = "shop"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        modules.forEach { m ->
            DashboardModuleCard(
                modifier = Modifier.weight(1f),
                title = m.title,
                subtitle = m.subtitle,
                accent = m.accent,
                iconType = m.iconType,
                onClick = { onModuleClick(m.title) }
            )
        }
    }
}

@Composable
private fun DashboardModuleCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    accent: Color,
    iconType: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize() // Fill the card size
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp), // bigger icon container
                contentAlignment = Alignment.Center
            ) {
                val icon = when (iconType) {
                    "body" -> Icons.Filled.FitnessCenter
                    "face" -> Icons.Filled.Face
                    "hair" -> Icons.Filled.Waves
                    else -> Icons.Filled.ShoppingCart
                }
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp) // bigger icon
                )
            }

            Spacer(Modifier.size(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 44.dp)
                    .padding(start = 10.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = accent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {}
            }
        }
    }
}