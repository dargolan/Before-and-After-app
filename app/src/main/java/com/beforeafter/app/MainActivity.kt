package com.beforeafter.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private companion object {
        const val PERMISSION_REQUEST_CODE = 1
        const val TARGET_WIDTH = 250 // Fixed width for each image (reduced to fit screen)
        const val TARGET_HEIGHT = 333 // Fixed height for each image (reduced to fit screen)
    }

    private lateinit var imageViewBefore: ZoomableImageView
    private lateinit var imageViewAfter: ZoomableImageView
    private lateinit var plusIconBefore: ImageView
    private lateinit var plusIconAfter: ImageView
    private lateinit var beforeImageContainer: FrameLayout
    private lateinit var afterImageContainer: FrameLayout
    private lateinit var controlsBefore: LinearLayout
    private lateinit var controlsAfter: LinearLayout
    private lateinit var rotateBeforeButton: ImageView
    private lateinit var rotateAfterButton: ImageView
    private lateinit var editTextBeforeButton: ImageView
    private lateinit var editTextAfterButton: ImageView
    private lateinit var buttonExport: Button
    private lateinit var footerText: TextView

    private var beforeBitmap: Bitmap? = null
    private var afterBitmap: Bitmap? = null
    
    private var interstitialAd: InterstitialAd? = null

    private lateinit var beforeImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var afterImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var beforeCameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var afterCameraLauncher: ActivityResultLauncher<Intent>
    private var cameraResultFile: File? = null
    private var cameraImageUri: Uri? = null
    private var cameraImageFile: File? = null // Track the actual file
    private var isCameraForBefore: Boolean = true // Track which slot we're capturing for
    private var shareLoadingOverlay: View? = null // Loading overlay for share button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Override window animations to prevent black screen transitions
            window.setWindowAnimations(android.R.style.Animation_Activity)
            
            setContentView(R.layout.activity_main)

            // Initialize AdMob
            MobileAds.initialize(this) {}
            
            initializeViews()
            setupImageLaunchers()
            setupCameraLaunchers()
            setupClickListeners()
            setupFooterLink()
            loadInterstitialAd()
            checkPermissions()
        } catch (e: Exception) {
            // Basic crash protection
            e.printStackTrace()
            Toast.makeText(this, "Error initializing app", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Override transition when returning from camera to prevent black screen
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun initializeViews() {
        imageViewBefore = findViewById(R.id.imageViewBefore)
        imageViewAfter = findViewById(R.id.imageViewAfter)
        plusIconBefore = findViewById(R.id.plusIconBefore)
        plusIconAfter = findViewById(R.id.plusIconAfter)
        beforeImageContainer = findViewById(R.id.beforeImageContainer)
        afterImageContainer = findViewById(R.id.afterImageContainer)
        controlsBefore = findViewById(R.id.controlsBefore)
        controlsAfter = findViewById(R.id.controlsAfter)
        rotateBeforeButton = findViewById(R.id.rotateBeforeButton)
        rotateAfterButton = findViewById(R.id.rotateAfterButton)
        editTextBeforeButton = findViewById(R.id.editTextBeforeButton)
        editTextAfterButton = findViewById(R.id.editTextAfterButton)
        buttonExport = findViewById(R.id.buttonExport)
        footerText = findViewById(R.id.footerText)
    }

    private fun setupImageLaunchers() {
        beforeImageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
                               if (result.resultCode == RESULT_OK && result.data != null) {
                       val imageUri = result.data?.data
                if (imageUri != null) {
                    loadImageFromUri(imageUri, true)
                }
            }
        }

        afterImageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
                               if (result.resultCode == RESULT_OK && result.data != null) {
                       val imageUri = result.data?.data
                if (imageUri != null) {
                    loadImageFromUri(imageUri, false)
                }
            }
        }
    }
    
    private fun setupCameraLaunchers() {
        beforeCameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val uriString = result.data?.getStringExtra(CameraActivity.EXTRA_OUTPUT_URI)
                if (uriString != null) {
                    val uri = Uri.parse(uriString)
                    handleCameraResultFromCustomCamera(uri, true)
                } else {
                    handleCameraResult(true)
                }
            }
        }

        afterCameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val uriString = result.data?.getStringExtra(CameraActivity.EXTRA_OUTPUT_URI)
                if (uriString != null) {
                    val uri = Uri.parse(uriString)
                    handleCameraResultFromCustomCamera(uri, false)
                } else {
                    handleCameraResult(false)
                }
            }
        }
    }
    
    private fun handleCameraResultFromCustomCamera(imageUri: Uri, isBefore: Boolean) {
        // CRITICAL: Capture the other slot's state IMMEDIATELY
        var otherSlotBitmap: Bitmap? = null
        var otherSlotOverlayText: String? = null
        var otherSlotControlsVisible = false
        
        if (isBefore) {
            otherSlotBitmap = afterBitmap
            otherSlotOverlayText = if (afterBitmap != null) imageViewAfter.getOverlayText() else null
            otherSlotControlsVisible = controlsAfter.visibility == View.VISIBLE
        } else {
            otherSlotBitmap = beforeBitmap
            otherSlotOverlayText = if (beforeBitmap != null) imageViewBefore.getOverlayText() else null
            otherSlotControlsVisible = controlsBefore.visibility == View.VISIBLE
        }
        
        // Load image in background thread
        Thread {
            try {
                // Load bitmap directly from file
                val file = File(imageUri.path ?: return@Thread)
                if (!file.exists() || file.length() == 0L) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: Camera image not found", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }
                
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: run {
                    runOnUiThread {
                        Toast.makeText(this, "Error: Could not load image", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }
                
                // Fix orientation
                val fixedBitmap = fixImageOrientation(bitmap, imageUri)
                
                // Update UI on main thread
                val preservedOtherBitmap = otherSlotBitmap
                val preservedOtherOverlayText = otherSlotOverlayText
                val preservedOtherControlsVisible = otherSlotControlsVisible
                
                runOnUiThread {
                    try {
                        // CRITICAL: Restore other slot FIRST
                        if (preservedOtherBitmap != null) {
                            if (isBefore) {
                                afterBitmap = preservedOtherBitmap
                                imageViewAfter.setImageBitmap(afterBitmap!!)
                                imageViewAfter.setOverlayText(preservedOtherOverlayText ?: "AFTER")
                                imageViewAfter.onTextClickListener = {
                                    showTextEditDialog(imageViewAfter, "Edit After Text")
                                }
                                imageViewAfter.onImageClickListener = {
                                    showImageSourceDialog(false)
                                }
                                if (!preservedOtherControlsVisible) {
                                    plusIconAfter.visibility = View.GONE
                                    controlsAfter.visibility = View.VISIBLE
                                }
                            } else {
                                beforeBitmap = preservedOtherBitmap
                                imageViewBefore.setImageBitmap(beforeBitmap!!)
                                imageViewBefore.setOverlayText(preservedOtherOverlayText ?: "BEFORE")
                                imageViewBefore.onTextClickListener = {
                                    showTextEditDialog(imageViewBefore, "Edit Before Text")
                                }
                                imageViewBefore.onImageClickListener = {
                                    showImageSourceDialog(true)
                                }
                                if (!preservedOtherControlsVisible) {
                                    plusIconBefore.visibility = View.GONE
                                    controlsBefore.visibility = View.VISIBLE
                                }
                            }
                        }
                        
                        // Now load the new image
                        if (isBefore) {
                            beforeBitmap = fixedBitmap
                            imageViewBefore.setImageBitmap(beforeBitmap!!)
                            imageViewBefore.setOverlayText("BEFORE")
                            imageViewBefore.onTextClickListener = {
                                showTextEditDialog(imageViewBefore, "Edit Before Text")
                            }
                            imageViewBefore.onImageClickListener = {
                                showImageSourceDialog(true)
                            }
                            plusIconBefore.visibility = View.GONE
                            controlsBefore.visibility = View.VISIBLE
                        } else {
                            afterBitmap = fixedBitmap
                            imageViewAfter.setImageBitmap(afterBitmap!!)
                            imageViewAfter.setOverlayText("AFTER")
                            imageViewAfter.onTextClickListener = {
                                showTextEditDialog(imageViewAfter, "Edit After Text")
                            }
                            imageViewAfter.onImageClickListener = {
                                showImageSourceDialog(false)
                            }
                            plusIconAfter.visibility = View.GONE
                            controlsAfter.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error loading camera image: ${e.message}", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error loading camera image: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }.start()
    }
    
    private fun handleCameraResult(isBefore: Boolean) {
        // CRITICAL: Capture the other slot's state IMMEDIATELY on UI thread before background work
        // This ensures we have the actual current state, not a stale reference
        var otherSlotBitmap: Bitmap? = null
        var otherSlotOverlayText: String? = null
        var otherSlotControlsVisible = false
        
        runOnUiThread {
            if (isBefore) {
                otherSlotBitmap = afterBitmap
                otherSlotOverlayText = if (afterBitmap != null) imageViewAfter.getOverlayText() else null
                otherSlotControlsVisible = controlsAfter.visibility == View.VISIBLE
            } else {
                otherSlotBitmap = beforeBitmap
                otherSlotOverlayText = if (beforeBitmap != null) imageViewBefore.getOverlayText() else null
                otherSlotControlsVisible = controlsBefore.visibility == View.VISIBLE
            }
        }
        
        // Store references for later use
        val otherSlotImageView = if (isBefore) imageViewAfter else imageViewBefore
        val otherSlotControls = if (isBefore) controlsAfter else controlsBefore
        val otherSlotPlusIcon = if (isBefore) plusIconAfter else plusIconBefore
        
        // NEW APPROACH: Query MediaStore for the most recent image taken
        // This works regardless of where the camera app saved the file
        Thread {
            try {
                // Minimal wait time for faster response
                Thread.sleep(100)
                
                // Query MediaStore for the most recently added image
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED
                )
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                
                // Get current time in seconds (DATE_ADDED is in seconds since epoch)
                val currentTimeSeconds = System.currentTimeMillis() / 1000
                // Look for images added in the last 10 seconds
                val timeThreshold = currentTimeSeconds - 10
                
                val cursor = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Images.Media.DATE_ADDED} > ?",
                    arrayOf(timeThreshold.toString()),
                    sortOrder
                )
                
                var loadedBitmap: Bitmap? = null
                var imageUri: Uri? = null
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val id = it.getLong(idColumn)
                        imageUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        
                        // Load bitmap in background thread BEFORE switching to UI thread
                        try {
                            loadedBitmap = if (imageUri!!.scheme == "file") {
                                android.graphics.BitmapFactory.decodeFile(imageUri!!.path)
                            } else {
                                contentResolver.openInputStream(imageUri!!)?.use { inputStream ->
                                    android.graphics.BitmapFactory.decodeStream(inputStream)
                                }
                            }
                            // Fix orientation
                            if (loadedBitmap != null) {
                                loadedBitmap = fixImageOrientation(loadedBitmap!!, imageUri!!)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                // If MediaStore query failed, try the file we created as fallback
                if (loadedBitmap == null) {
                    val file = cameraImageFile
                    if (file != null) {
                        // Wait for file with retries
                        var attempts = 0
                        while (!file.exists() || file.length() == 0L) {
                            if (attempts >= 15) break
                            Thread.sleep(200)
                            attempts++
                        }
                        
                        if (file.exists() && file.length() > 0) {
                            try {
                                loadedBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                imageUri = Uri.fromFile(file)
                                if (loadedBitmap != null && imageUri != null) {
                                    loadedBitmap = fixImageOrientation(loadedBitmap!!, imageUri!!)
                                }
                                MediaScannerConnection.scanFile(
                                    this@MainActivity,
                                    arrayOf(file.absolutePath),
                                    null,
                                    null
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                
                // Now load on UI thread with bitmap already ready (instant display)
                val finalBitmap = loadedBitmap
                val preservedOtherBitmap = otherSlotBitmap
                val preservedOtherOverlayText = otherSlotOverlayText
                val preservedOtherControlsVisible = otherSlotControlsVisible
                
                if (finalBitmap != null) {
                    runOnUiThread {
                        try {
                            // CRITICAL: ALWAYS restore the other slot FIRST, before loading new image
                            // This ensures it never gets cleared
                            if (preservedOtherBitmap != null) {
                                if (isBefore) {
                                    // Restore AFTER slot
                                    afterBitmap = preservedOtherBitmap
                                    imageViewAfter.setImageBitmap(afterBitmap!!)
                                    imageViewAfter.setOverlayText(preservedOtherOverlayText ?: "AFTER")
                                    imageViewAfter.onTextClickListener = {
                                        showTextEditDialog(imageViewAfter, "Edit After Text")
                                    }
                                    imageViewAfter.onImageClickListener = {
                                        showImageSourceDialog(false)
                                    }
                                    if (!preservedOtherControlsVisible) {
                                        plusIconAfter.visibility = View.GONE
                                        controlsAfter.visibility = View.VISIBLE
                                    }
                                } else {
                                    // Restore BEFORE slot
                                    beforeBitmap = preservedOtherBitmap
                                    imageViewBefore.setImageBitmap(beforeBitmap!!)
                                    imageViewBefore.setOverlayText(preservedOtherOverlayText ?: "BEFORE")
                                    imageViewBefore.onTextClickListener = {
                                        showTextEditDialog(imageViewBefore, "Edit Before Text")
                                    }
                                    imageViewBefore.onImageClickListener = {
                                        showImageSourceDialog(true)
                                    }
                                    if (!preservedOtherControlsVisible) {
                                        plusIconBefore.visibility = View.GONE
                                        controlsBefore.visibility = View.VISIBLE
                                    }
                                }
                            }
                            
                            // Now load the new image (AFTER ensuring other slot is preserved)
                            if (isBefore) {
                                beforeBitmap = finalBitmap
                                imageViewBefore.setImageBitmap(beforeBitmap!!)
                                imageViewBefore.setOverlayText("BEFORE")
                                imageViewBefore.onTextClickListener = {
                                    showTextEditDialog(imageViewBefore, "Edit Before Text")
                                }
                                imageViewBefore.onImageClickListener = {
                                    showImageSourceDialog(true)
                                }
                                plusIconBefore.visibility = View.GONE
                                controlsBefore.visibility = View.VISIBLE
                            } else {
                                afterBitmap = finalBitmap
                                imageViewAfter.setImageBitmap(afterBitmap!!)
                                imageViewAfter.setOverlayText("AFTER")
                                imageViewAfter.onTextClickListener = {
                                    showTextEditDialog(imageViewAfter, "Edit After Text")
                                }
                                imageViewAfter.onImageClickListener = {
                                    showImageSourceDialog(false)
                                }
                                plusIconAfter.visibility = View.GONE
                                controlsAfter.visibility = View.VISIBLE
                            }
                            
                            // Clear references after successful load
                            cameraImageUri = null
                            cameraImageFile = null
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Error loading camera image: ${e.message}", Toast.LENGTH_SHORT).show()
                            e.printStackTrace()
                        }
                    }
                } else {
                    // If all methods failed
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error: Could not find camera image. Please try again.", Toast.LENGTH_LONG).show()
                        cameraImageUri = null
                        cameraImageFile = null
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error loading camera image: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }.start()
    }
    
    private fun loadImageToSlot(bitmap: Bitmap, isBefore: Boolean) {
        // Preserve the other slot's bitmap to prevent it from being cleared
        val otherBitmap = if (isBefore) afterBitmap else beforeBitmap
        
        if (isBefore) {
            beforeBitmap = bitmap
                           imageViewBefore.setImageBitmap(beforeBitmap)
                    imageViewBefore.setOverlayText("BEFORE")
                    imageViewBefore.onTextClickListener = {
                        showTextEditDialog(imageViewBefore, "Edit Before Text")
                    }
                    imageViewBefore.onImageClickListener = {
                showImageSourceDialog(true)
                    }
                                               plusIconBefore.visibility = View.GONE
                           controlsBefore.visibility = View.VISIBLE
            
            // Ensure the other slot's image is still displayed if it was previously loaded
            if (otherBitmap != null) {
                // Re-apply the other slot's bitmap to ensure it's still visible
                imageViewAfter.setImageBitmap(afterBitmap)
                if (controlsAfter.visibility != View.VISIBLE) {
                    plusIconAfter.visibility = View.GONE
                    controlsAfter.visibility = View.VISIBLE
                }
            }
        } else {
            afterBitmap = bitmap
                           imageViewAfter.setImageBitmap(afterBitmap)
                    imageViewAfter.setOverlayText("AFTER")
                    imageViewAfter.onTextClickListener = {
                        showTextEditDialog(imageViewAfter, "Edit After Text")
                    }
                    imageViewAfter.onImageClickListener = {
                showImageSourceDialog(false)
                    }
                                               plusIconAfter.visibility = View.GONE
                           controlsAfter.visibility = View.VISIBLE
            
            // Ensure the other slot's image is still displayed if it was previously loaded
            if (otherBitmap != null) {
                // Re-apply the other slot's bitmap to ensure it's still visible
                imageViewBefore.setImageBitmap(beforeBitmap)
                if (controlsBefore.visibility != View.VISIBLE) {
                    plusIconBefore.visibility = View.GONE
                    controlsBefore.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun loadImageFromUri(imageUri: Uri, isBefore: Boolean) {
        try {
            // Preserve the other slot's bitmap to prevent it from being cleared
            val otherBitmap = if (isBefore) afterBitmap else beforeBitmap
            
            val originalBitmap = if (imageUri.scheme == "file") {
                // For file:// URIs, read directly from file
                android.graphics.BitmapFactory.decodeFile(imageUri.path)
            } else {
                // For content:// URIs, use ContentResolver with InputStream (modern approach)
                try {
                    contentResolver.openInputStream(imageUri)?.use { inputStream ->
                        android.graphics.BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } ?: run {
                Toast.makeText(this, "Error: Could not load image", Toast.LENGTH_SHORT).show()
                return
            }
            
            val fixedBitmap = fixImageOrientation(originalBitmap, imageUri)
            
            if (isBefore) {
                beforeBitmap = fixedBitmap
                imageViewBefore.setImageBitmap(beforeBitmap)
                imageViewBefore.setOverlayText("BEFORE")
                imageViewBefore.onTextClickListener = {
                    showTextEditDialog(imageViewBefore, "Edit Before Text")
                }
                imageViewBefore.onImageClickListener = {
                    showImageSourceDialog(true)
                }
                plusIconBefore.visibility = View.GONE
                controlsBefore.visibility = View.VISIBLE
                
                // Ensure the other slot's image is still displayed
                if (otherBitmap != null && imageViewAfter.drawable == null) {
                    imageViewAfter.setImageBitmap(otherBitmap)
                }
            } else {
                afterBitmap = fixedBitmap
                           imageViewAfter.setImageBitmap(afterBitmap)
                    imageViewAfter.setOverlayText("AFTER")
                    imageViewAfter.onTextClickListener = {
                        showTextEditDialog(imageViewAfter, "Edit After Text")
                    }
                    imageViewAfter.onImageClickListener = {
                    showImageSourceDialog(false)
                    }
                                               plusIconAfter.visibility = View.GONE
                           controlsAfter.visibility = View.VISIBLE
                
                // Ensure the other slot's image is still displayed
                if (otherBitmap != null && imageViewBefore.drawable == null) {
                    imageViewBefore.setImageBitmap(otherBitmap)
                }
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

        private fun setupClickListeners() {
        // Make entire image container clickable (only when no image is loaded)
        beforeImageContainer.setOnClickListener { 
            if (beforeBitmap == null) {
                showImageSourceDialog(true)
            }
        }
        afterImageContainer.setOnClickListener { 
            if (afterBitmap == null) {
                showImageSourceDialog(false)
            }
        }
        
        buttonExport.setOnClickListener { exportCombinedImage() }
        
        // Rotation controls
        rotateBeforeButton.setOnClickListener { imageViewBefore.rotateImage() }
        rotateAfterButton.setOnClickListener { imageViewAfter.rotateImage() }
        
        // Text editing controls - these should work directly without opening gallery
        editTextBeforeButton.setOnClickListener { 
            showTextEditDialog(imageViewBefore, "Edit Before Text")
        }
        editTextAfterButton.setOnClickListener { 
            showTextEditDialog(imageViewAfter, "Edit After Text")
        }
    }

    private fun showImageSourceDialog(isBefore: Boolean) {
        // Create a bottom sheet style layout with rounded top corners and 90% opacity
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 32)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bottom_sheet_background)
        }
        
        // Title (lighter weight)
        val titleText = TextView(this).apply {
            text = "Choose Source"
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 0)
            setTypeface(null, android.graphics.Typeface.NORMAL) // Lighter weight instead of BOLD
        }
        layout.addView(titleText)
        
        // Spacer to push icons down towards middle (reduced weight to move icons up more)
        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 0.1f // Further reduced to move icons up a bit more
            }
        }
        layout.addView(topSpacer)
        
        // Bottom spacer to prevent icons from touching bottom
        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56 // Increased bottom spacer to move icons up more
            )
        }
        
        // Icons row
        val iconsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        
        // Gallery icon (add_photo_alternate) - bigger and further apart
        val galleryIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_add_photo_alternate)
            layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                marginEnd = 96  // Increased spacing between icons
            }
            isClickable = true
            isFocusable = true
        }
        
        // Camera icon (photo_camera) - bigger
        val cameraIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_photo_camera)
            layoutParams = LinearLayout.LayoutParams(120, 120)
            isClickable = true
            isFocusable = true
        }
        
        iconsRow.addView(galleryIcon)
        iconsRow.addView(cameraIcon)
        layout.addView(iconsRow)
        layout.addView(bottomSpacer)
        
        // Create bottom sheet dialog - make it 75% taller
        val dialog = android.app.Dialog(this)
        dialog.setContentView(layout)
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val dialogHeight = (screenHeight * 0.175).toInt() // 75% taller (was ~10%, now ~17.5%)
        dialog.window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dialogHeight
        )
        dialog.window?.setGravity(android.view.Gravity.BOTTOM)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Set click listeners
        cameraIcon.setOnClickListener {
            dialog.dismiss()
            openCamera(isBefore)
        }
        
        galleryIcon.setOnClickListener {
            dialog.dismiss()
            selectFromGallery(isBefore)
        }
        
        dialog.show()
    }
    
    private fun selectFromGallery(isBefore: Boolean) {
        // Use ACTION_GET_CONTENT instead of ACTION_PICK to avoid folder navigation
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        if (isBefore) {
            beforeImageLauncher.launch(intent)
        } else {
        afterImageLauncher.launch(intent)
        }
    }
    
    private fun openCamera(isBefore: Boolean) {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CODE
            )
            return
        }
        
        try {
            isCameraForBefore = isBefore
            
            // Launch custom camera activity
            val intent = Intent(this, CameraActivity::class.java)
            
            // Use fade animation for smooth transition
            val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            
            if (isBefore) {
                beforeCameraLauncher.launch(intent, options)
            } else {
                afterCameraLauncher.launch(intent, options)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }



    private fun exportCombinedImage() {
        val before = beforeBitmap
        val after = afterBitmap

        if (before == null || after == null) {
            Toast.makeText(this, "Please select both before and after images first", Toast.LENGTH_SHORT).show()
            return
        }

        // Show ad first, then export
        if (interstitialAd != null) {
            interstitialAd?.show(this)
        } else {
            // If no ad is loaded, proceed directly
            performExport()
        }
    }
    
    private fun performExport() {
        val before = beforeBitmap
        val after = afterBitmap
        
        if (before == null || after == null) return

        try {
            val combinedBitmap = createCombinedBitmap(before, after)
            saveBitmapToGallery(combinedBitmap)
            
            // Show preview popup
            showExportPreview(combinedBitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Error exporting image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createCombinedBitmap(before: Bitmap, after: Bitmap): Bitmap {
        // Get cropped versions from the zoomable image views
        val croppedBefore = imageViewBefore.getCroppedBitmap(TARGET_WIDTH, TARGET_HEIGHT)
            ?: Bitmap.createScaledBitmap(before, TARGET_WIDTH, TARGET_HEIGHT, true)
        
        val croppedAfter = imageViewAfter.getCroppedBitmap(TARGET_WIDTH, TARGET_HEIGHT)
            ?: Bitmap.createScaledBitmap(after, TARGET_WIDTH, TARGET_HEIGHT, true)

        // Create a new bitmap for the combined image with fixed dimensions
        val combinedWidth = TARGET_WIDTH * 2
        val combinedBitmap = Bitmap.createBitmap(combinedWidth, TARGET_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)

        // Draw the before image on the left
        canvas.drawBitmap(croppedBefore, 0f, 0f, null)

        // Draw the after image on the right
        canvas.drawBitmap(croppedAfter, TARGET_WIDTH.toFloat(), 0f, null)

        return combinedBitmap
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "BeforeAfter_$timeStamp.jpg"

        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val imageFile = File(picturesDir, fileName)

        FileOutputStream(imageFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }

        // Notify the media scanner so the image appears in the gallery (modern approach)
        MediaScannerConnection.scanFile(
            this,
            arrayOf(imageFile.absolutePath),
            null,
            null
        )
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        // Storage permissions (for older Android versions)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        
        // Camera permission
        permissions.add(Manifest.permission.CAMERA)

        val allPermissionsGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (!allGranted) {
                Toast.makeText(this, "Permissions are required to use this app", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showTextEditDialog(imageView: ZoomableImageView, title: String) {
        val editText = EditText(this).apply {
            setText(imageView.getOverlayText())
            hint = getString(R.string.enter_text)
        }
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                imageView.setOverlayText(editText.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
               private fun showExportPreview(bitmap: Bitmap) {
               val imageView = ImageView(this)
               imageView.setImageBitmap(bitmap)
               imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.adjustViewBounds = true
               
               val message = "Your before & after comparison has been saved to your gallery."
               
        // Create a frame layout for share icon with white circle frame
        val shareContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // White circle frame background (double size: 128x128)
        val circleFrame = View(this).apply {
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.share_icon_circle)
            layoutParams = FrameLayout.LayoutParams(128, 128).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        
        // Share icon (white Material style - double size: 64x64)
        val shareIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_share)
            layoutParams = FrameLayout.LayoutParams(64, 64).apply {
                gravity = android.view.Gravity.CENTER
            }
            isClickable = true
            isFocusable = true
        }
        
        // Make the entire container clickable with larger touch area
        shareContainer.setOnClickListener {
            showShareLoadingIndicator()
            shareImage(bitmap)
        }
        
        // Also make icon clickable as backup
        shareIcon.setOnClickListener {
            showShareLoadingIndicator()
            shareImage(bitmap)
        }
        
        // Increase touch area by adding padding
        shareContainer.setPadding(20, 20, 20, 20)
        shareContainer.isClickable = true
        shareContainer.isFocusable = true
        
        shareContainer.addView(circleFrame)
        shareContainer.addView(shareIcon)
        
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(24, 8, 24, 8)
        }
        buttonLayout.addView(shareContainer)
        
        val dialogLayout = createDialogLayout(imageView, buttonLayout)
        
        val dialog = AlertDialog.Builder(this)
                   .setTitle("Export Complete!")
                   .setMessage(message)
            .setView(dialogLayout)
            .setPositiveButton("See in Photos") { _, _ ->
                // Open the phone's Pictures folder (where images are saved)
                try {
                    // Try opening Pictures folder using DocumentsProvider
                    val picturesIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("content://com.android.externalstorage.documents/document/primary%3APictures")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    
                    try {
                        startActivity(picturesIntent)
                    } catch (e: Exception) {
                        // Fallback: Try opening with MediaStore (general gallery)
                        val mediaIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            startActivity(mediaIntent)
                        } catch (e2: Exception) {
                            // Final fallback: open any gallery app
                            val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                startActivity(galleryIntent)
                            } catch (e3: Exception) {
                                Toast.makeText(this, "Could not open gallery", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open gallery", Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(true)
            .create()
        
        // Handle clicking outside to dismiss and reset
        dialog.setOnCancelListener {
            resetApp()
        }
        
        dialog.show()
        
        // Make the "See in Photos" button text white and center it at bottom
        val seeInPhotosButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        seeInPhotosButton?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        
        // Center the button by wrapping it in a centered container
        seeInPhotosButton?.parent?.let { parent ->
            if (parent is ViewGroup) {
                val parentLayout = parent as ViewGroup
                val buttonIndex = parentLayout.indexOfChild(seeInPhotosButton)
                if (buttonIndex >= 0) {
                    parentLayout.removeViewAt(buttonIndex)
                    val centeredContainer = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    centeredContainer.addView(seeInPhotosButton)
                    parentLayout.addView(centeredContainer, buttonIndex)
                }
            }
        }
    }
    
    private fun createDialogLayout(imageView: ImageView, buttonLayout: LinearLayout): View {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8) // Reduced top/bottom padding
        }
        
        // Add image view with constraints (reduced height)
        val imageParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            height = resources.displayMetrics.heightPixels / 3 // Reduced from /2 to /3
        }
        imageView.layoutParams = imageParams
        mainLayout.addView(imageView)
        
        // Add spacing (reduced)
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                8 // Reduced from 16 to 8
            )
        }
        mainLayout.addView(spacer)
        
        // Add share icon container (centered)
        mainLayout.addView(buttonLayout)
        
        return mainLayout
    }
    
    private fun shareImage(bitmap: Bitmap) {
        // Save file in background thread for faster response
        Thread {
            try {
                // Save bitmap to a temporary file in cache directory
                val cacheDir = File(cacheDir, "shared_images")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                val imageFile = File(cacheDir, "before_after_${System.currentTimeMillis()}.jpg")
                FileOutputStream(imageFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                
                // Use FileProvider for secure file sharing (required for Android 7+)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    imageFile
                )
                
                // Create share intent on main thread
                runOnUiThread {
                    hideShareLoadingIndicator()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Check out my before and after comparison! Powered by traaacks.com")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    startActivity(Intent.createChooser(shareIntent, "Share via"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideShareLoadingIndicator()
                    Toast.makeText(this, "Error sharing image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }.start()
    }
    
    private fun showShareLoadingIndicator() {
        // Create a semi-transparent overlay with a progress indicator
        val rootView = window.decorView.rootView as? android.view.ViewGroup ?: return
        
        // Remove existing overlay if any
        hideShareLoadingIndicator()
        
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x80000000.toInt()) // Semi-transparent black (50% opacity)
            alpha = 0f
        }
        
        val progressBar = android.widget.ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
        
        overlay.addView(progressBar)
        rootView.addView(overlay)
        shareLoadingOverlay = overlay
        
        // Animate fade in
        overlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }
    
    private fun hideShareLoadingIndicator() {
        shareLoadingOverlay?.let { overlay ->
            overlay.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    val parent = overlay.parent as? android.view.ViewGroup
                    parent?.removeView(overlay)
                    shareLoadingOverlay = null
                }
                .start()
        }
           }
    
    private fun downloadToDownloads(bitmap: Bitmap) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            val fileName = "BeforeAfter_$timestamp.jpg"
            val file = File(downloadsDir, fileName)
            
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            
            // Notify media scanner so the file appears in gallery/file manager
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
            
            Toast.makeText(this, "Image downloaded to Downloads folder!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error downloading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupFooterLink() {
        val text = "Powered by traaacks.com"
        val spannableString = SpannableString(text)
        
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://traaacks.com"))
                startActivity(intent)
            }
            
            override fun updateDrawState(ds: android.text.TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false // Remove default underline, we'll add our own
            }
        }
        
        val start = text.indexOf("traaacks.com")
        val end = start + "traaacks.com".length
        
        // Apply clickable span
        spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        // Apply white color
        spannableString.setSpan(
            ForegroundColorSpan(android.graphics.Color.WHITE), 
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // Apply underline
        spannableString.setSpan(
            UnderlineSpan(), 
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        footerText.text = spannableString
        footerText.movementMethod = LinkMovementMethod.getInstance()
    }
    
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            this,
            "ca-app-pub-3066652012789007/4920512893", // Production ad unit ID
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    // Log the error for debugging
                    println("AdMob Error: ${adError.message}")
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    println("AdMob: Interstitial ad loaded successfully!")
                    
                    // Set up ad callbacks
                    interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            // Called when a click is recorded for an ad.
                        }

                        override fun onAdDismissedFullScreenContent() {
                            // Called when ad is dismissed.
                            interstitialAd = null
                            performExport() // Continue with export after ad
                            loadInterstitialAd() // Load next ad
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                            // Called when ad fails to show.
                            interstitialAd = null
                            performExport() // Continue with export even if ad fails
                        }

                        override fun onAdImpression() {
                            // Called when an impression is recorded for an ad.
                        }

                        override fun onAdShowedFullScreenContent() {
                            // Called when ad is shown.
                        }
                    }
                }
            }
        )
    }
    
    /**
     * Fixes image orientation based on EXIF data
     */
    private fun resetApp() {
        // Reset all images and UI to initial state
        beforeBitmap = null
        afterBitmap = null
        imageViewBefore.setImageDrawable(null)
        imageViewAfter.setImageDrawable(null)
        plusIconBefore.visibility = View.VISIBLE
        plusIconAfter.visibility = View.VISIBLE
        controlsBefore.visibility = View.GONE
        controlsAfter.visibility = View.GONE
        imageViewBefore.setOverlayText("BEFORE")
        imageViewAfter.setOverlayText("AFTER")
    }
    
    private fun fixImageOrientation(bitmap: Bitmap, imageUri: Uri): Bitmap {
        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap // No rotation needed
            }
            
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            return bitmap // Return original if there's an error
        }
    }
}