package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

@Composable
fun ShopScreen(
    onBack: () -> Unit
) {
    val PrimaryBlue = Color(0xFF2563EB)
    val products = remember {
        listOf(
            ShopItem("Microneedling Roller", "Enhances topical absorption and stimulates scalp circulation.", "$19.99", R.drawable.ic_nutrition),
            ShopItem("Growth Serum", "Peptide + caffeine complex for supporting follicle vitality.", "$39.99", R.drawable.ic_progress),
            ShopItem("Biotin Complex", "Advanced blend with biotin, zinc, and essential B vitamins.", "$24.99", R.drawable.ic_home),
            ShopItem("Silk Pillowcase", "Reduces friction to minimize breakage and frizz overnight.", "$29.99", R.drawable.ic_community)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Inline header with back button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Shop",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            Text(
                "Recommended Tools",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        items(products) { p -> ProductCard(p, PrimaryBlue) }
    }
}

data class ShopItem(
    val name: String,
    val description: String,
    val price: String,
    val iconRes: Int
)

@Composable
private fun ProductCard(item: ShopItem, accent: Color) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.name,
                        tint = accent,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(item.price, color = accent, fontWeight = FontWeight.ExtraBold)
            }
            Button(onClick = { /* TODO: Add to cart */ }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text("Buy", color = Color.White)
            }
        }
    }
}
