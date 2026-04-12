package com.example.myapplication.data.barcode

import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.myapplication.domain.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class AndroidMLKitBarcodeScanner : BarcodeScanner {

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun processImage(imageProxy: ImageProxy, onBarcodeDetected: (String) -> Unit) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        when (barcode.valueType) {
                            Barcode.TYPE_PRODUCT -> {
                                barcode.rawValue?.let { value ->
                                    Log.d("BarcodeScanner", "Detected barcode: $value")
                                    onBarcodeDetected(value)
                                }
                            }
                            else -> {
                                barcode.rawValue?.let { value ->
                                    Log.d("BarcodeScanner", "Detected barcode (other type): $value")
                                    onBarcodeDetected(value)
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BarcodeScanner", "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                    // Close the image Proxy so the next one can be processed
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

