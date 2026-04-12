package com.example.myapplication.domain.barcode

import androidx.camera.core.ImageProxy

interface BarcodeScanner {
    fun processImage(imageProxy: ImageProxy, onBarcodeDetected: (String) -> Unit)
}

