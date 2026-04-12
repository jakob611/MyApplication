package com.example.myapplication.domain.looksmaxing

import com.example.myapplication.domain.looksmaxing.models.DetectedFaceData

interface FaceDetector {
    suspend fun detectFace(image: Any): DetectedFaceData?
}
