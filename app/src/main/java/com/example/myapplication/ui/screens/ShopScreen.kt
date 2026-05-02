package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.R
import com.example.myapplication.viewmodels.ShopViewModel

@Composable
fun ShopScreen(
    onBack: () -> Unit
) {
    val vm: ShopViewModel = viewModel()
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.refreshData()
    }

    val PrimaryBlue = MaterialTheme.colorScheme.primary
    val products = remember {
        listOf(
            ShopItem("Microneedling Roller", "Promote collagen production.", "$19.99", R.drawable.ic_nutrition),
            ShopItem("Growth Serum", "Peptide complex for fuller hair.", "$39.99", R.drawable.ic_progress),
            ShopItem("Biotin Complex", "Essential vitamins for hair & skin.", "$24.99", R.drawable.ic_home),
            ShopItem("Silk Pillowcase", "Reduce friction while sleeping.", "$29.99", R.drawable.ic_community)
        )
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Shop", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "XP", tint = Color(0xFFF57C00), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${state.userXP} XP", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Spend Your XP",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            item {
                UpgradeItem(
                    title = "Streak Freeze ❄️",
                    desc = "Maintain your streak on missed days. (Max 3)",
                    cost = "300 XP",
                    owned = "${state.streakFreezes}/3",
                    canAfford = state.userXP >= 300 && state.streakFreezes < 3,
                    onClick = { vm.buyStreakFreeze() }
                )
            }

            item {
                UpgradeItem(
                    title = "10% Coupon \uD83C\uDF9F️",
                    desc = "Get 10% off any item in the store.",
                    cost = "500 XP",
                    owned = "",
                    canAfford = state.userXP >= 500,
                    onClick = { vm.buyCoupon() }
                )
            }

            item {
                 HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                 Text(
                    "Recommended Tools",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            items(items = products, key = { it.name }) { p -> ProductCard(p, PrimaryBlue) }
        }
    }
}

@Composable
fun UpgradeItem(
    title: String,
    desc: String,
    cost: String,
    owned: String,
    canAfford: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                if (owned.isNotEmpty()) {
                    Text("Owned: $owned", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = onClick,
                enabled = canAfford,
                colors = ButtonDefaults.buttonColors(containerColor = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(cost)
            }
        }
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
                    // Placeholder icon since we don't have real product images yet
                    Text("🛍️", fontSize = 24.sp)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        item.price,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    // Backlog: "Add to cart" funkcionalnost za fizične izdelke (Faza 6)
                    Text(
                        "ADD",
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
