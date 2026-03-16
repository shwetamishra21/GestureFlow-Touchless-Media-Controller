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
import kotlin.math.sqrt
import kotlin.math.abs

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
                .setMinHandDetectionConfidence(0.3f)   // was 0.5 — more forgiving
                .setMinHandPresenceConfidence(0.3f)    // was 0.5
                .setMinTrackingConfidence(0.3f)        // was 0.5
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
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            var bitmap = imageProxy.toBitmap()

            // For front camera: rotate AND mirror horizontally
            bitmap = bitmap.rotateBitmap(rotationDegrees.toFloat())
            bitmap = bitmap.mirrorHorizontally()

            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = handLandmarker?.detect(mpImage)

            if (result == null || result.landmarks().isEmpty()) {
                Log.d(TAG, "No hand detected")
                DetectionResult(gesture = GestureType.NONE, landmarks = null)
            } else {
                val landmarks = result.landmarks()[0]

                Log.d(TAG, "Landmarks: wrist y=${landmarks[0].y()}, " +
                        "index tip y=${landmarks[8].y()}, " +
                        "index pip y=${landmarks[6].y()}, " +
                        "thumb tip y=${landmarks[4].y()}")

                val pinchDistance = getPinchDistance(landmarks)
                val gestureType = classifyGesture(landmarks, pinchDistance)

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

    private fun classifyGesture(
        landmarks: List<NormalizedLandmark>,
        pinchDistance: Float
    ): GestureType {
        // y=0 is TOP, y=1 is BOTTOM in normalized coords
        // Extended finger = tip.y < pip.y (tip is higher = smaller y value)
        val indexExtended  = landmarks[8].y()  < landmarks[6].y()  - FINGER_BEND_THRESHOLD
        val middleExtended = landmarks[12].y() < landmarks[10].y() - FINGER_BEND_THRESHOLD
        val ringExtended   = landmarks[16].y() < landmarks[14].y() - FINGER_BEND_THRESHOLD
        val pinkyExtended  = landmarks[20].y() < landmarks[18].y() - FINGER_BEND_THRESHOLD

        // Thumb: compare x-distance from wrist instead of y (thumb moves laterally)
        val thumbExtended = abs(landmarks[4].x() - landmarks[0].x()) >
                abs(landmarks[3].x() - landmarks[0].x()) + 0.02f

        Log.d(TAG, "Fingers → index=$indexExtended middle=$middleExtended " +
                "ring=$ringExtended pinky=$pinkyExtended thumb=$thumbExtended " +
                "pinch=$pinchDistance")

        return when {
            // Open palm — all 4 fingers clearly extended (check this BEFORE pinch)
            indexExtended && middleExtended &&
                    ringExtended && pinkyExtended -> GestureType.OPEN_PALM

            // Fist — all 4 fingers folded
            !indexExtended && !middleExtended &&
                    !ringExtended && !pinkyExtended -> GestureType.FIST

            // Two fingers — index + middle up, ring + pinky down
            indexExtended && middleExtended &&
                    !ringExtended && !pinkyExtended -> GestureType.TWO_FINGERS

            // Pinch — checked last so open palm doesn't get swallowed
            pinchDistance < PINCH_THRESHOLD -> GestureType.PINCH

            else -> GestureType.NONE
        }
    }

    private fun getPinchDistance(landmarks: List<NormalizedLandmark>): Float {
        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val dx = thumbTip.x() - indexTip.x()
        val dy = thumbTip.y() - indexTip.y()
        return sqrt(dx * dx + dy * dy)
    }

    fun close() {
        handLandmarker?.close()
    }

    companion object {
        private const val TAG = "GestureDetector"
        private const val PINCH_THRESHOLD = 0.12f       // was 0.08 — easier to trigger
        private const val FINGER_BEND_THRESHOLD = 0.02f // hysteresis buffer to reduce noise
    }
}

fun Bitmap.rotateBitmap(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.mirrorHorizontally(): Bitmap {
    val matrix = Matrix().apply { postScale(-1f, 1f, width / 2f, height / 2f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}