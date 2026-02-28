package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Privacy Policy",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Last updated: November 14, 2025",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            SectionTitle("1. Introduction")
            SectionContent(
                "Glow Upp (\"we\", \"our\", or \"us\") is committed to protecting your privacy. " +
                "This Privacy Policy explains how we collect, use, disclose, and safeguard your " +
                "information when you use our mobile application."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("2. Information We Collect")
            SectionContent(
                "We collect information that you provide directly to us, including:\n\n" +
                "• Account information (email, name)\n" +
                "• Profile data (body measurements, fitness goals)\n" +
                "• Nutrition logs and meal tracking data\n" +
                "• Progress photos and measurements\n" +
                "• Device information and usage data"
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("3. How We Use Your Information")
            SectionContent(
                "We use your information to:\n\n" +
                "• Provide and maintain our service\n" +
                "• Personalize your experience\n" +
                "• Generate AI-powered fitness and nutrition plans\n" +
                "• Track your progress and provide insights\n" +
                "• Communicate with you about updates and features\n" +
                "• Improve our services and develop new features"
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("4. Data Storage and Security")
            SectionContent(
                "Your data is stored securely using Firebase and Google Cloud services. " +
                "We implement appropriate technical and organizational measures to protect " +
                "your personal information against unauthorized access, alteration, disclosure, or destruction."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("5. Third-Party Services")
            SectionContent(
                "We use third-party services including:\n\n" +
                "• Firebase (Google) for authentication and data storage\n" +
                "• FatSecret API for nutrition data\n" +
                "• OpenFoodFacts for food information\n" +
                "• Google AI for personalized recommendations"
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("6. Your Rights")
            SectionContent(
                "You have the right to:\n\n" +
                "• Access your personal data\n" +
                "• Correct inaccurate data\n" +
                "• Request deletion of your data\n" +
                "• Export your data\n" +
                "• Opt-out of certain data collection"
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("7. Children's Privacy")
            SectionContent(
                "Our service is not intended for children under 13 years of age. " +
                "We do not knowingly collect personal information from children under 13."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("8. Changes to This Policy")
            SectionContent(
                "We may update this Privacy Policy from time to time. We will notify you " +
                "of any changes by posting the new Privacy Policy on this page and updating " +
                "the \"Last updated\" date."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("9. Contact Us")
            SectionContent(
                "If you have questions about this Privacy Policy, please contact us through " +
                "the Contact page in the app."
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SectionContent(content: String) {
    Text(
        content,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 24.sp
    )
}

