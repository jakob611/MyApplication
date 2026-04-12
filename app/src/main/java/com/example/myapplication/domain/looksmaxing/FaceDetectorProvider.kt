package com.example.myapplication.domain.looksmaxing

/**
 * Domenski vmesnik za Face Detector Provider.
 * Implementacija z Android Contextom bo injicirana iz data/platform modula.
 */
interface FaceDetectorProvider {
    fun provideFaceDetector(): FaceDetector
}