package com.example.myapplication.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.ui.theme.UppColors

@Composable
fun IndexScreen(
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit = onLoginClick,
    onViewProFeatures: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Fullscreen background image with blur
        Image(
            painter = painterResource(R.drawable.male_fitness1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(8.dp)
        )
        // UPP Figma gradient overlay: oranžno → temno
        Box(
            Modifier
                .fillMaxSize()
                .background(UppColors.SplashGradient)
        )
        // Top right login/signup buttons
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onLoginClick,
                shape = RoundedCornerShape(30),
                border = BorderStroke(1.5.dp, UppColors.LightGray)
            ) {
                Text("Login", color = UppColors.White, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSignUpClick,
                colors = ButtonDefaults.buttonColors(containerColor = UppColors.Orange),
                shape = RoundedCornerShape(30)
            ) {
                Text("Sign Up", color = UppColors.White, fontWeight = FontWeight.SemiBold)
            }
        }
        // Main centered content
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Badge chip
            Surface(
                color = UppColors.Orange.copy(alpha = 0.2f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, UppColors.Orange),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Become a GOAT",
                    color = UppColors.Orange,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
                    fontSize = 14.sp
                )
            }
            // UPP — Figma gradient naslov
            Text(
                text = "UPP",
                fontSize = 64.sp,
                color = UppColors.Orange,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Start your glow up journey with AI\npersonalized plans, recommended\nproducts, AI analysis of your face\nand skin care routines.",
                fontSize = 16.sp,
                color = UppColors.LightGray,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 12.dp, bottom = 36.dp),
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )
            // CTA gumbi
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(containerColor = UppColors.Orange),
                shape = RoundedCornerShape(30),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Start Journey", fontSize = 18.sp, color = UppColors.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Already have an account? Log In",
                fontSize = 14.sp,
                color = UppColors.LightGray,
                modifier = Modifier.clickable(onClick = onLoginClick)
            )
        }
    }
}
