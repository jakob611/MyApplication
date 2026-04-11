package com.example.myapplication.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector

private data class DashboardModule(
    val title: String,
    val icon: ImageVector,
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
            icon = Icons.Filled.FitnessCenter,
            containerColor = MaterialTheme.colorScheme.secondary,
            textColor = MaterialTheme.colorScheme.onSecondary
        ),
        DashboardModule(
            title = "HAIR MODULE",
            icon = Icons.Filled.Waves,
            containerColor = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary
        ),
        DashboardModule(
            title = "FACE MODULE",
            icon = Icons.Filled.Face,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        DashboardModule(
            title = "SHOP",
            icon = Icons.Filled.ShoppingCart,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            textColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                icon = m.icon,
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
    icon: ImageVector,
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = textColor.copy(alpha = 0.8f)
            )
        }
    }
}