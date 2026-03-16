package com.example.gestureflow

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.gestureflow.ui.theme.GestureFlowTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var handGestureDetector: HandGestureDetector
    private lateinit var mediaActionController: MediaActionController

    private val showCameraState = mutableStateOf(false)
    val volumeState = mutableFloatStateOf(0f)  // shared with receiver

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (streamType == AudioManager.STREAM_MUSIC) {
                    volumeState.floatValue = mediaActionController.getVolumeFraction()
                }
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showCameraState.value = true
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handGestureDetector = HandGestureDetector(this)
        mediaActionController = MediaActionController(this)

        // Set initial volume
        volumeState.floatValue = mediaActionController.getVolumeFraction()

        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) showCameraState.value = true
        else requestPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            GestureFlowTheme {
                val showCamera by showCameraState
                if (showCamera) {
                    GestureFlowScreen(
                        handGestureDetector = handGestureDetector,
                        mediaController = mediaActionController,
                        volumeState = volumeState
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register receiver and sync volume whenever app comes to foreground
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        registerReceiver(volumeReceiver, filter)
        volumeState.floatValue = mediaActionController.getVolumeFraction()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(volumeReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        handGestureDetector.close()
    }
}

@Composable
fun GestureFlowScreen(
    handGestureDetector: HandGestureDetector,
    mediaController: MediaActionController,
    volumeState: MutableFloatState
) {
    var gestureLabel by remember { mutableStateOf("--") }
    var volumeLevel by volumeState   // ← directly backed by the BroadcastReceiver state
    var lastGesture by remember { mutableStateOf(GestureType.NONE) }
    var lastActionTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            detector = handGestureDetector,
            onGestureDetected = { gesture, pinchDistance ->
                val now = System.currentTimeMillis()
                val debounceMs = if (gesture == GestureType.PINCH) 50L else 600L

                gestureLabel = when (gesture) {
                    GestureType.OPEN_PALM -> "PLAY / PAUSE"
                    GestureType.FIST      -> "MUTE"
                    GestureType.TWO_FINGERS -> "NEXT TRACK"
                    GestureType.PINCH     -> "VOLUME CONTROL"
                    GestureType.NONE      -> "--"
                }

                // Pinch: continuous volume update
                if (gesture == GestureType.PINCH) {
                    val newVolume = (pinchDistance * 2f).coerceIn(0f, 1f)
                    mediaController.setVolume(newVolume)
                    volumeLevel = mediaController.getVolumeFraction()
                }

                // One-shot gestures with debounce
                if (gesture != GestureType.PINCH &&
                    gesture != GestureType.NONE &&
                    gesture != lastGesture &&
                    now - lastActionTime > debounceMs
                ) {
                    when (gesture) {
                        GestureType.OPEN_PALM -> mediaController.togglePlayPause()
                        GestureType.TWO_FINGERS -> mediaController.nextTrack()
                        GestureType.FIST -> {
                            mediaController.toggleMute()
                            volumeLevel = mediaController.getVolumeFraction()
                        }
                        else -> {}
                    }
                    lastActionTime = now
                }

                lastGesture = gesture
            }
        )

        GestureLabel(
            label = gestureLabel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        VolumeBar(
            volume = volumeLevel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp)
        )
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    detector: HandGestureDetector,
    onGestureDetected: (GestureType, Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            val result = detector.detect(imageProxy)
                            onGestureDetected(result.gesture, result.pinchDistance)
                            imageProxy.close()
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
fun GestureLabel(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Gesture: $label",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun VolumeBar(volume: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Volume: ${(volume * 100).toInt()}%",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LinearProgressIndicator(
            progress = { volume },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = Color(0xFF00E676),
            trackColor = Color.White.copy(alpha = 0.2f),
        )
    }
}