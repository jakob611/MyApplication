package com.example.myapplication.domain.looksmaxing

import android.content.Context
import com.example.myapplication.data.looksmaxing.AndroidMLKitFaceDetector

object FaceDetectorProvider {
    fun provideFaceDetector(context: Context): FaceDetector {
        return AndroidMLKitFaceDetector(context)
    }
}