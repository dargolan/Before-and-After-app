package com.beforeafter.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            // Initialize AdMob
            MobileAds.initialize(this) {}
            
            initializeViews()
            setupImageLaunchers()
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
                       try {
                           val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                           beforeBitmap = fixImageOrientation(originalBitmap, imageUri!!)
                           imageViewBefore.setImageBitmap(beforeBitmap)
                    imageViewBefore.setOverlayText("BEFORE")
                    imageViewBefore.onTextClickListener = {
                        showTextEditDialog(imageViewBefore, "Edit Before Text")
                    }
                    imageViewBefore.onImageClickListener = {
                        selectBeforeImage() // Allow replacing the image
                    }
                                               plusIconBefore.visibility = View.GONE
                           controlsBefore.visibility = View.VISIBLE
                } catch (e: IOException) {
                    Toast.makeText(this, "Error loading before image", Toast.LENGTH_SHORT).show()
                }
            }
        }

        afterImageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
                               if (result.resultCode == RESULT_OK && result.data != null) {
                       val imageUri = result.data?.data
                       try {
                           val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                           afterBitmap = fixImageOrientation(originalBitmap, imageUri!!)
                           imageViewAfter.setImageBitmap(afterBitmap)
                    imageViewAfter.setOverlayText("AFTER")
                    imageViewAfter.onTextClickListener = {
                        showTextEditDialog(imageViewAfter, "Edit After Text")
                    }
                    imageViewAfter.onImageClickListener = {
                        selectAfterImage() // Allow replacing the image
                    }
                                               plusIconAfter.visibility = View.GONE
                           controlsAfter.visibility = View.VISIBLE
                } catch (e: IOException) {
                    Toast.makeText(this, "Error loading after image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

        private fun setupClickListeners() {
        // Make entire image container clickable
        beforeImageContainer.setOnClickListener { selectBeforeImage() }
        afterImageContainer.setOnClickListener { selectAfterImage() }
        
        buttonExport.setOnClickListener { exportCombinedImage() }
        
        // Rotation controls
        rotateBeforeButton.setOnClickListener { imageViewBefore.rotateImage() }
        rotateAfterButton.setOnClickListener { imageViewAfter.rotateImage() }
        
        // Text editing controls
        editTextBeforeButton.setOnClickListener { 
            showTextEditDialog(imageViewBefore, "Edit Before Text")
        }
        editTextAfterButton.setOnClickListener { 
            showTextEditDialog(imageViewAfter, "Edit After Text")
        }
    }

    private fun selectBeforeImage() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        beforeImageLauncher.launch(intent)
    }

    private fun selectAfterImage() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        afterImageLauncher.launch(intent)
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

        // Notify the media scanner so the image appears in the gallery
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
            data = Uri.fromFile(imageFile)
        }
        sendBroadcast(mediaScanIntent)
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val allPermissionsGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
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
               
               val message = "Your before & after comparison has been saved to your gallery."
               
               AlertDialog.Builder(this)
                   .setTitle("Export Complete!")
                   .setMessage(message)
                   .setView(imageView)
                   .setPositiveButton("Great!") { _, _ -> }
                   .setNeutralButton("Download") { _, _ ->
                       downloadToDownloads(bitmap)
                   }
                   .show()
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