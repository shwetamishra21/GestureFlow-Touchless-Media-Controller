package com.example.gestureflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com. gestureflow. GestureType
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

class HandGestureDetector(private val context: Context) {

    data class DetectionResult(
        val gesture: GestureType,
        val landmarks: HandLandmarkerResult?,
        val pinchDistance: Float = 0f
    )

    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detect(imageProxy: ImageProxy): DetectionResult {
        return try {
            val bitmap = imageProxy
                .toBitmap()
                .rotateBitmap(imageProxy.imageInfo.rotationDegrees.toFloat())

            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = handLandmarker?.detect(mpImage)

            if (result == null || result.landmarks().isEmpty()) {
                DetectionResult(gesture = GestureType.NONE, landmarks = null)
            } else {
                val landmarks = result.landmarks()[0]
                val gestureType = classifyGesture(landmarks)
                val pinchDistance = getPinchDistance(landmarks)

                DetectionResult(
                    gesture = gestureType,
                    landmarks = result,
                    pinchDistance = pinchDistance
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DetectionResult(gesture = GestureType.NONE, landmarks = null)
        }
    }

    private fun classifyGesture(
        landmarks: List<NormalizedLandmark>
    ): GestureType {
        val indexExtended = landmarks[8].y() < landmarks[6].y()
        val middleExtended = landmarks[12].y() < landmarks[10].y()
        val ringExtended = landmarks[16].y() < landmarks[14].y()
        val pinkyExtended = landmarks[20].y() < landmarks[18].y()

        val pinchDist = getPinchDistance(landmarks)
        val isPinching = pinchDist < PINCH_THRESHOLD

        return when {
            !indexExtended && !middleExtended &&
                    !ringExtended && !pinkyExtended && !isPinching -> GestureType.FIST

            indexExtended && middleExtended &&
                    ringExtended && pinkyExtended -> GestureType.OPEN_PALM

            indexExtended && middleExtended &&
                    !ringExtended && !pinkyExtended -> GestureType.TWO_FINGERS

            isPinching -> GestureType.PINCH

            else -> GestureType.NONE
        }
    }

    private fun getPinchDistance(
        landmarks: List<NormalizedLandmark>
    ): Float {
        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val dx = thumbTip.x() - indexTip.x()
        val dy = thumbTip.y() - indexTip.y()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun close() {
        handLandmarker?.close()
    }

    companion object {
        private const val PINCH_THRESHOLD = 0.08f
    }
}

fun Bitmap.rotateBitmap(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
