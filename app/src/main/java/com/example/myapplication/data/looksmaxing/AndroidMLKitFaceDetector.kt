package com.example.myapplication.data.looksmaxing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.myapplication.domain.looksmaxing.FaceDetector
import com.example.myapplication.domain.looksmaxing.models.DetectedFaceData
import com.example.myapplication.domain.math.Point2D
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await

class AndroidMLKitFaceDetector(private val context: Context) : FaceDetector {
    override suspend fun detectFace(image: Any): DetectedFaceData? {
        val inputImage = when (image) {
            is Bitmap -> InputImage.fromBitmap(image, 0)
            is Uri -> InputImage.fromFilePath(context, image)
            else -> return null
        }

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()

        val detector = FaceDetection.getClient(options)

        return try {
            val faces = detector.process(inputImage).await()
            val face = faces.firstOrNull() ?: return null

            val markers = mutableMapOf<Int, Point2D>()

            face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { markers[15] = Point2D(it.x, it.y) }
            face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { markers[16] = Point2D(it.x, it.y) }
            face.getLandmark(FaceLandmark.NOSE_BASE)?.position?.let { markers[23] = Point2D(it.x, it.y) }
            face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let { markers[27] = Point2D(it.x, it.y) }
            face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let { markers[29] = Point2D(it.x, it.y) }
            face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.let { markers[31] = Point2D(it.x, it.y) }

            val bounds = face.boundingBox
            markers[19] = Point2D(bounds.left.toFloat(), bounds.centerY().toFloat())
            markers[20] = Point2D(bounds.right.toFloat(), bounds.centerY().toFloat())
            markers[1] = Point2D(bounds.centerX().toFloat(), bounds.top.toFloat())
            markers[33] = Point2D(bounds.centerX().toFloat(), bounds.bottom.toFloat())

            val width = inputImage.width
            val height = inputImage.height

            DetectedFaceData(
                markers = markers,
                imageWidth = width,
                imageHeight = height,
                headEulerAngleY = face.headEulerAngleY,
                headEulerAngleZ = face.headEulerAngleZ
            )
        } catch (e: Exception) {
            null
        } finally {
            detector.close()
        }
    }
}
