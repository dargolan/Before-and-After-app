package com.beforeafter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class CameraActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: View
    private lateinit var flipButton: ImageView
    private lateinit var flashButton: ImageView
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var outputFileUri: Uri? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var currentZoomRatio = 1f
    private var isFlashOn = false
    
    // Pinch-to-zoom
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var baseZoomRatio = 1f
    
    companion object {
        const val EXTRA_OUTPUT_URI = "output_uri"
        const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up preview view
        previewView = PreviewView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Set up pinch-to-zoom gesture detector
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newZoom = baseZoomRatio * scaleFactor
                camera?.cameraControl?.let { control ->
                    val zoomState = camera?.cameraInfo?.zoomState?.value
                    val minZoom = zoomState?.minZoomRatio ?: 1f
                    val maxZoom = zoomState?.maxZoomRatio ?: 1f
                    val clampedZoom = newZoom.coerceIn(minZoom, maxZoom)
                    control.setZoomRatio(clampedZoom)
                    currentZoomRatio = clampedZoom
                }
                return true
            }
            
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                baseZoomRatio = currentZoomRatio
                return true
            }
        })
        
        // Override touch event to handle pinch-to-zoom
        previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }
        
        // Create black bottom bar with proper layout - same size as toast (17.5% of screen height)
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val bottomBarHeight = (screenHeight * 0.175).toInt() // Same as toast height
        
        val bottomBar = android.widget.FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@CameraActivity, R.drawable.camera_bottom_bar)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                bottomBarHeight
            ).apply {
                gravity = android.view.Gravity.BOTTOM
            }
            setPadding(32, 24, 32, 32)
        }
        
        // White circle capture button (no icon) - centered, 20% bigger (144x144)
        captureButton = View(this).apply {
            background = ContextCompat.getDrawable(this@CameraActivity, R.drawable.capture_button_circle)
            layoutParams = android.widget.FrameLayout.LayoutParams(144, 144).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.CENTER_VERTICAL
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { takePhoto() }
        }
        
        // Flash button - positioned on the left
        flashButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_flash_off)
            layoutParams = android.widget.FrameLayout.LayoutParams(80, 80).apply {
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                marginStart = 32
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleFlash() }
        }
        
        // Flip camera button - positioned on the right, much bigger
        flipButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_flip_camera)
            layoutParams = android.widget.FrameLayout.LayoutParams(80, 80).apply {
                gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                marginEnd = 32
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { flipCamera() }
        }
        
        bottomBar.addView(flashButton)
        bottomBar.addView(captureButton)
        bottomBar.addView(flipButton)
        
        val container = android.widget.FrameLayout(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(previewView)
            addView(bottomBar)
        }
        
        setContentView(container)
        
        // Request camera permission if needed
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            // Image capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this,
                    currentCameraSelector,
                    preview,
                    imageCapture
                )
                
                // Check if camera has flash and update button visibility/state
                val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
                flashButton.visibility = if (hasFlash) View.VISIBLE else View.INVISIBLE
                if (!hasFlash) {
                    isFlashOn = false
                    flashButton.setImageResource(R.drawable.ic_flash_off)
                }
                
                // Observe zoom state changes
                camera?.cameraInfo?.zoomState?.observe(this@CameraActivity, Observer { zoomState ->
                    currentZoomRatio = zoomState.zoomRatio
                })
                
                // Initialize zoom ratio
                val initialZoomState = camera?.cameraInfo?.zoomState?.value
                currentZoomRatio = initialZoomState?.zoomRatio ?: 1f
                baseZoomRatio = currentZoomRatio
            } catch (exc: Exception) {
                Toast.makeText(this, "Use case binding failed", Toast.LENGTH_SHORT).show()
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun flipCamera() {
        // Turn off flash when flipping (front cameras don't have flash)
        if (isFlashOn) {
            isFlashOn = false
            camera?.cameraControl?.enableTorch(false)
            flashButton.setImageResource(R.drawable.ic_flash_off)
        }
        
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()
    }
    
    private fun toggleFlash() {
        // Check if camera has flash capability
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
        if (!hasFlash) {
            Toast.makeText(this, "Flash not available on this camera", Toast.LENGTH_SHORT).show()
            return
        }
        
        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
        
        // Update icon
        flashButton.setImageResource(
            if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        // Create output file
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        if (!picturesDir.exists()) {
            picturesDir.mkdirs()
        }
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(picturesDir, "BeforeAfter_Camera_${timeStamp}.jpg")
        
        // Create output file options
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        // Take picture and save to file
        imageCapture.takePicture(
            outputFileOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        // Save file URI and return immediately (no preview)
                        outputFileUri = Uri.fromFile(photoFile)
                        
                        // Notify media scanner
                        android.media.MediaScannerConnection.scanFile(
                            this@CameraActivity,
                            arrayOf(photoFile.absolutePath),
                            null,
                            null
                        )
                        
                        // Return result immediately
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_OUTPUT_URI, outputFileUri.toString())
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                }
                
                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@CameraActivity,
                            "Error capturing photo: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        exception.printStackTrace()
                    }
                }
            }
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
