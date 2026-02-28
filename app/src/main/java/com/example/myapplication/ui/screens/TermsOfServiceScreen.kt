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
fun TermsOfServiceScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service") },
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
                "Terms of Service",
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

            SectionTitle("1. Acceptance of Terms")
            SectionContent(
                "By accessing or using Glow Upp, you agree to be bound by these Terms of Service " +
                "and all applicable laws and regulations. If you do not agree with any of these terms, " +
                "you are prohibited from using this service."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("2. Use License")
            SectionContent(
                "We grant you a personal, non-exclusive, non-transferable license to use Glow Upp " +
                "for your personal fitness and nutrition tracking purposes. This license does not " +
                "include the right to:\n\n" +
                "• Modify or copy the application\n" +
                "• Use the application for commercial purposes\n" +
                "• Reverse engineer or decompile the application\n" +
                "• Remove any copyright or proprietary notations"
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("3. User Accounts")
            SectionContent(
                "You are responsible for:\n\n" +
                "• Maintaining the confidentiality of your account\n" +
                "• All activities that occur under your account\n" +
                "• Notifying us immediately of any unauthorized use\n" +
                "• Providing accurate and complete information"
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("4. Medical Disclaimer")
            SectionContent(
                "Glow Upp is NOT a substitute for professional medical advice, diagnosis, or treatment. " +
                "The information and recommendations provided by our app are for general informational " +
                "purposes only. Always consult with a qualified healthcare professional before starting " +
                "any fitness or nutrition program."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("5. Premium Subscription")
            SectionContent(
                "• Premium features require a paid subscription\n" +
                "• Subscriptions auto-renew unless cancelled\n" +
                "• Refunds are subject to our refund policy\n" +
                "• We reserve the right to modify pricing with notice"
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("6. User Content")
            SectionContent(
                "You retain ownership of content you submit to Glow Upp (photos, measurements, logs). " +
                "By submitting content, you grant us a license to use, store, and process this content " +
                "to provide our services to you."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("7. Prohibited Conduct")
            SectionContent(
                "You agree not to:\n\n" +
                "• Use the app for any illegal purposes\n" +
                "• Harass, abuse, or harm other users\n" +
                "• Attempt to gain unauthorized access\n" +
                "• Interfere with the app's functionality\n" +
                "• Upload malicious code or viruses"
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("8. Limitation of Liability")
            SectionContent(
                "Glow Upp and its creators shall not be liable for any indirect, incidental, special, " +
                "consequential, or punitive damages resulting from your use or inability to use the service. " +
                "This includes damages for injuries, weight loss/gain issues, or health problems."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("9. Termination")
            SectionContent(
                "We reserve the right to terminate or suspend your account at any time for violations " +
                "of these Terms of Service or for any other reason at our sole discretion."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("10. Changes to Terms")
            SectionContent(
                "We may revise these Terms of Service at any time. Continued use of the app after " +
                "changes constitutes acceptance of the new terms."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("11. Governing Law")
            SectionContent(
                "These Terms shall be governed by and construed in accordance with applicable laws, " +
                "without regard to conflict of law provisions."
            )

            Spacer(Modifier.height(16.dp))

            SectionTitle("12. Contact Information")
            SectionContent(
                "For questions about these Terms of Service, please contact us through the Contact " +
                "page in the app."
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

