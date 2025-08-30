package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProFeaturesScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onFreeTrial: () -> Unit,
    errorMessage: String? = null
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 0.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back Arrow
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(0.dp))
            // Title
            Text(
                "Speed up your glow-up\nwith GlowUpp Pro",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp)
            )
            Spacer(Modifier.height(18.dp))

            // Feature comparison - "Free vs Pro"
            FeatureComparisonTable()

            Spacer(Modifier.height(16.dp))

            // View all features label
            Text(
                "View all features",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 20.dp)
            )

            Spacer(Modifier.height(14.dp))

            // Pricing Options
            PricingOptions()

            Spacer(Modifier.height(20.dp))

            // Upgrade to Pro button
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976F6)),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(52.dp)
                    .shadow(1.dp, RoundedCornerShape(14.dp))
            ) {
                Text("Upgrade to Pro", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(12.dp))

            // Join member text
            Text(
                "Join 1000 Pro Members!",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Spacer(Modifier.height(14.dp))

            // Free Trial Button
            Button(
                onClick = onFreeTrial,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976F6)),
                modifier = Modifier
                    .fillMaxWidth(0.60f)
                    .height(42.dp)
            ) {
                Text("7 days Free Trial", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(7.dp))

            // No payment method needed text
            Text(
                "No Payment method needed, cancel anytime!",
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            // Error message
            errorMessage?.let {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = it,
                    color = Color.Red,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun FeatureComparisonTable() {
    val rows = listOf(
        Pair("No chats with AI", "24/7 AI Coach"),
        Pair("No updates", "Weekly updated workout plans"),
        Pair("Limited view of macros", "Full view of Macros"),
        Pair("Adds", "No adds")
    )
    val density = LocalDensity.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        rows.forEach { (left, right) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FeatureOval(
                    text = left,
                    bgColor = Color(0xFFE5E7EB),
                    textColor = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FeatureOval(
                    text = right,
                    bgColor = Color(0xFF1976F6),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f)
                        .drawBlueShadow(density)
                )
            }
        }
    }
}

// Custom extension to draw fuzzy shadow for blue ovals
fun Modifier.drawBlueShadow(density: Density): Modifier = this.then(
    Modifier.drawBehind {
        val shadowColor = Color(0x661976F6)
        val radius = with(density) { 10.dp.toPx() }
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = shadowColor
            }
            canvas.drawRoundRect(
                left = -6f,
                top = 5f,
                right = size.width + 6f,
                bottom = size.height + 6f,
                radius,
                radius,
                paint
            )
        }
    }
)

@Composable
fun FeatureOval(text: String, bgColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(40.dp)
            .background(bgColor, RoundedCornerShape(50))
            .border(
                width = 0.dp,
                color = Color.Transparent,
                shape = RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 15.sp,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PricingOptions() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        // Monthly
        PricingCard(
            title = "Monthly",
            price = "7,82€",
            sub = "per month",
            blue = false
        )
        Spacer(Modifier.width(18.dp))
        // Annual
        PricingCard(
            title = "Annually",
            price = "74,99€",
            sub = "per year",
            blue = true
        )
    }
}

@Composable
fun PricingCard(title: String, price: String, sub: String, blue: Boolean) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(80.dp)
            .border(
                width = 2.dp,
                color = if (blue) Color(0xFF1976F6) else Color.Black,
                shape = RoundedCornerShape(12.dp)
            )
            .background(if (blue) Color(0xFF1976F6) else Color.White, RoundedCornerShape(12.dp))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (blue) Color.White else Color.Black
            )
            Text(
                price,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (blue) Color.White else Color.Black
            )
            Text(
                sub,
                fontSize = 13.sp,
                color = if (blue) Color.White else Color.Black
            )
            if (blue) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Save 20%",
                        fontSize = 12.sp,
                        color = Color(0xFF1976F6),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}