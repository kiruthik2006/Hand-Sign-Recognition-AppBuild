package com.example.gesturerecog

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.framework.image.BitmapImageBuilder

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.gesturerecog.databinding.ActivityMainBinding

import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import androidx.camera.core.ImageProxy
import android.graphics.BitmapFactory
import androidx.camera.core.ExperimentalGetImage


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var handOverlay: HandLandmarkOverlay
    private var handLandmarker: HandLandmarker? = null
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // Set up view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handOverlay = findViewById(R.id.handOverlay)
        setupHandLandmarker()

        // Ask for camera permission
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) @OptIn(androidx.camera.core.ExperimentalGetImage::class) { imageProxy ->
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val bitmap = imageProxy.toBitmap()

                if (bitmap != null && handLandmarker != null) {
                    try {
                        val mpImage = BitmapImageBuilder(bitmap).build()
                        val result = handLandmarker!!.detect(mpImage)
                        val allHands = result.landmarks()
                        if (allHands.isNotEmpty()) {
                            handOverlay.setLandmarks(allHands[0]) // Show first hand's landmarks
                            Log.d("HandDetection", "Hands detected: ${allHands.size}")
                        } else {
                            handOverlay.setLandmarks(emptyList())
                        }
                    } catch (e: Exception) {
                        Log.e("HandDetection", "Error during detection", e)
                    }
                }

                imageProxy.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }
    private fun setupHandLandmarker() {
        try {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("hand_landmarker.task")
                        .build()
                )
                .setRunningMode(RunningMode.VIDEO)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(this, options)

            Log.d("HandLandmarker", "HandLandmarker initialized!")

        } catch (e: Exception) {
            Log.e("HandLandmarker", "Error initializing HandLandmarker", e)
        }
    }

// Helper function to convert ImageProxy to Bitmap for MediaPipe

@androidx.annotation.OptIn(ExperimentalGetImage::class) private fun ImageProxy.toBitmap(): Bitmap? {
    val yuvImage = image?.toYuvImage() ?: return null
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

private fun Image.toYuvImage(): YuvImage? {
    val yBuffer = planes[0].buffer // Y
    val uBuffer = planes[1].buffer // U
    val vBuffer = planes[2].buffer // V

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    return YuvImage(nv21, ImageFormat.NV21, width, height, null)
}
}


class HandLandmarkOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark>? = null
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 10f
    }

    fun setLandmarks(landmarks: List<NormalizedLandmark>) {
        this.landmarks = landmarks
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(100f, 100f, 20f, paint) // <- test dot
        landmarks?.forEachIndexed { index, landmark ->
            val x = landmark.x() * width
            val y = landmark.y() * height
            Log.d("LandmarkCoords", "[$index] x=${landmark.x()}, y=${landmark.y()} → scaled=($x, $y)")
            canvas.drawCircle(x, y, 8f, paint)
        }
    }
}