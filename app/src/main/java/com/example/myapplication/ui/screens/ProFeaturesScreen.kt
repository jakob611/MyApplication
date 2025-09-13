package com.example.myapplication.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.core.view.isVisible
import com.example.myapplication.databinding.ProFeaturesBinding

@Composable
fun ProFeaturesScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onFreeTrial: () -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    AndroidViewBinding(factory = ProFeaturesBinding::inflate, modifier = modifier) {
        // Kliki
        btnBack.setOnClickListener { onBack() }
        btnUpgrade.setOnClickListener { onContinue() }
        btnFreeTrial.setOnClickListener { onFreeTrial() }

        // Error message
        tvError.isVisible = !errorMessage.isNullOrBlank()
        tvError.text = errorMessage.orEmpty()
    }
}