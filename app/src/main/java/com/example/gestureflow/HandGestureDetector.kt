package com.example.gestureflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

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
            Log.d(TAG, "HandLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "HandLandmarker init failed: ${e.message}", e)
        }
    }

    fun detect(imageProxy: ImageProxy): DetectionResult {
        return try {
            // Always rotate to upright — front camera is typically 270°
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap().rotateBitmap(rotationDegrees.toFloat())

            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = handLandmarker?.detect(mpImage)

            if (result == null || result.landmarks().isEmpty()) {
                Log.d(TAG, "No hand detected (rotation was $rotationDegrees°)")
                DetectionResult(gesture = GestureType.NONE, landmarks = null)
            } else {
                val landmarks = result.landmarks()[0]

                // Log raw landmark y-values to verify orientation
                Log.d(TAG, "Hand detected! index tip y=${landmarks[8].y()}, " +
                        "index pip y=${landmarks[6].y()}, " +
                        "middle tip y=${landmarks[12].y()}, " +
                        "wrist y=${landmarks[0].y()}")

                val gestureType = classifyGesture(landmarks)
                val pinchDistance = getPinchDistance(landmarks)

                Log.d(TAG, "Gesture=$gestureType  pinch=$pinchDistance")

                DetectionResult(
                    gesture = gestureType,
                    landmarks = result,
                    pinchDistance = pinchDistance
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Detection error: ${e.message}", e)
            DetectionResult(gesture = GestureType.NONE, landmarks = null)
        }
    }

    private fun classifyGesture(landmarks: List<NormalizedLandmark>): GestureType {
        // In normalized coords: y=0 is TOP of image, y=1 is BOTTOM
        // So fingertip EXTENDED means tip.y < pip.y (tip is higher up = smaller y)
        val indexExtended  = landmarks[8].y()  < landmarks[6].y()
        val middleExtended = landmarks[12].y() < landmarks[10].y()
        val ringExtended   = landmarks[16].y() < landmarks[14].y()
        val pinkyExtended  = landmarks[20].y() < landmarks[18].y()

        val pinchDist = getPinchDistance(landmarks)
        val isPinching = pinchDist < PINCH_THRESHOLD

        Log.d(TAG, "index=$indexExtended middle=$middleExtended " +
                "ring=$ringExtended pinky=$pinkyExtended pinch=$pinchDist")

        return when {
            isPinching -> GestureType.PINCH

            !indexExtended && !middleExtended &&
                    !ringExtended && !pinkyExtended -> GestureType.FIST

            indexExtended && middleExtended &&
                    ringExtended && pinkyExtended -> GestureType.OPEN_PALM

            indexExtended && middleExtended &&
                    !ringExtended && !pinkyExtended -> GestureType.TWO_FINGERS

            else -> GestureType.NONE
        }
    }

    private fun getPinchDistance(landmarks: List<NormalizedLandmark>): Float {
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
        private const val TAG = "GestureDetector"
        private const val PINCH_THRESHOLD = 0.08f
    }
}

fun Bitmap.rotateBitmap(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}