package com.example.myapplication.domain.barcode

import com.example.myapplication.data.barcode.AndroidMLKitBarcodeScanner

object BarcodeScannerProvider {
    // Provides an instance of BarcodeScanner, keeping ML Kit separated from UI
    fun provideBarcodeScanner(): BarcodeScanner {
        return AndroidMLKitBarcodeScanner()
    }
}

