package com.example.myapplication.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

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
        // Fullscreen background image with blur and overlay
        Image(
            painter = painterResource(R.drawable.male_fitness1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(8.dp)
        )
        // Gradient overlay for readability
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xC8073069),
                            Color(0xB9162d2d),
                            Color(0xB9000000)
                        )
                    )
                )
        )
        // Top right login/signup buttons
        Row(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onLoginClick,
                shape = RoundedCornerShape(30),
                border = BorderStroke(1.5.dp, Color(0xFF2563EB))
            ) {
                Text("Login", color = Color(0xFF2563EB))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSignUpClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(30)
            ) {
                Text("Sign Up")
            }
        }
        // Main centered content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tag
            Surface(
                color = Color(0xFFfee440),
                shape = RoundedCornerShape(50),
                shadowElevation = 6.dp,
                modifier = Modifier.padding(bottom = 18.dp)
            ) {
                Text(
                    text = "Become a GOAT",
                    color = Color(0xFF293241),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    fontSize = 15.sp
                )
            }
            // Title
            Text(
                text = "GlowUpp",
                fontSize = 52.sp,
                color = Color(0xFFffd600),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Text(
                text = "Transform yourself in 3 areas \n with planned workouts, AI analysis,\n personally recommended products and diets.",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 16.dp, bottom = 30.dp),
                lineHeight = 27.sp
            )
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onLoginClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(30),
                    modifier = Modifier.height(60.dp)
                ) {
                    Text("Start Your Journey", fontSize = 17.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                OutlinedButton(
                    onClick = onViewProFeatures,
                    shape = RoundedCornerShape(30),
                    border = BorderStroke(2.dp, Color(0xFF2563EB)),
                    modifier = Modifier.height(60.dp)
                ) {
                    Text("View Pro Features", fontSize = 17.sp, color = Color(0xFF2563EB))
                }
            }
        }
    }
}