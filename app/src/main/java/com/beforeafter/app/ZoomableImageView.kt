package com.beforeafter.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private val lastTouchPoint = PointF()
    private val startTouchPoint = PointF()
    
    private var mode = NONE
    private var scale = 1f
    private var minScale = 0.5f
    private var maxScale = 3f
    private var overlayText = "" // Text to overlay on image
    private var showText = true // Whether to show text overlay
    
    // Click listener for text editing
    var onTextClickListener: (() -> Unit)? = null
    
    // Click listener for image replacement
    var onImageClickListener: (() -> Unit)? = null
    
    private var viewWidth = 0f
    private var viewHeight = 0f
    private var imageWidth = 0f
    private var imageHeight = 0f
    
    private val scaleDetector: ScaleGestureDetector
    
    // Rotation (controlled by button, not gesture)
    private var rotation = 0f
    
    // Text overlay paint
    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, android.R.color.white)
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, ContextCompat.getColor(context, android.R.color.black))
    }
    
    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
        const val ROTATE = 3
    }
    
    init {
        scaleType = ScaleType.MATRIX
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        imageMatrix = matrix
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        
        if (drawable != null) {
            setImageToCenter()
        }
    }
    
    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (drawable != null && viewWidth > 0 && viewHeight > 0) {
            setImageToCenter()
        }
    }
    
    private fun setImageToCenter() {
        drawable?.let { d ->
            imageWidth = d.intrinsicWidth.toFloat()
            imageHeight = d.intrinsicHeight.toFloat()
            
            // Calculate scale to fit the image in the view
            val scaleX = viewWidth / imageWidth
            val scaleY = viewHeight / imageHeight
            scale = min(scaleX, scaleY) // Fit entire image in view
            
            // Update min and max scale relative to fit scale
            minScale = scale * 0.5f
            maxScale = scale * 4f
            
            // Apply transformations: scale first, then translate to center, then rotate around view center
            matrix.reset()
            
            // Scale the image
            matrix.postScale(scale, scale)
            
            // Translate to center the scaled image in the view
            val scaledWidth = imageWidth * scale
            val scaledHeight = imageHeight * scale
            val dx = (viewWidth - scaledWidth) / 2f
            val dy = (viewHeight - scaledHeight) / 2f
            matrix.postTranslate(dx, dy)
            
            // Apply rotation around view center (if any)
            if (rotation != 0f) {
                matrix.postRotate(rotation, viewWidth / 2f, viewHeight / 2f)
            }
            
            imageMatrix = matrix
        }
    }
    
        override fun onTouchEvent(event: MotionEvent): Boolean {
        // If there's no image, don't consume touch events - let parent handle them
        if (drawable == null) {
            return false
        }
        
        scaleDetector.onTouchEvent(event)

        val currentPoint = PointF(event.x, event.y)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchPoint.set(currentPoint)
                startTouchPoint.set(currentPoint)
                mode = DRAG
                
                // Check if click is on text for editing (priority over image click)
                if (showText && overlayText.isNotEmpty()) {
                    val textBounds = Rect()
                    textPaint.getTextBounds(overlayText, 0, overlayText.length, textBounds)
                    val textX = (viewWidth - textBounds.width()) / 2f
                    val textY = viewHeight - 40f
                    
                    val textRect = RectF(
                        textX - 20f, textY - textBounds.height() - 10f,
                        textX + textBounds.width() + 20f, textY + 10f
                    )
                    
                    if (textRect.contains(currentPoint.x, currentPoint.y)) {
                        // Text was clicked, trigger edit
                        onTextClickListener?.invoke()
                        return true
                    }
                }
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                lastTouchPoint.set(currentPoint)
                mode = ZOOM
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = currentPoint.x - lastTouchPoint.x
                    val dy = currentPoint.y - lastTouchPoint.y
                    
                    // Only apply translation if there's significant movement
                    if (abs(dx) > 3f || abs(dy) > 3f) {
                        matrix.postTranslate(dx, dy)
                        fixTranslation()
                        imageMatrix = matrix
                    }
                }
                lastTouchPoint.set(currentPoint)
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // Check for tap (short touch without much movement)
                if (event.action == MotionEvent.ACTION_UP && mode == DRAG) {
                    val dx = abs(currentPoint.x - startTouchPoint.x)
                    val dy = abs(currentPoint.y - startTouchPoint.y)
                    
                    // If it was a short tap (not a drag), trigger image replacement
                    if (dx < 10f && dy < 10f && drawable != null) {
                        onImageClickListener?.invoke()
                        return true
                    }
                }
                mode = NONE
            }
        }
        
        return true
    }
    
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newScale = scale * scaleFactor
            
            if (newScale in minScale..maxScale) {
                scale = newScale
                
                // Apply scaling around the gesture focus point
                matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                
                // Fix translation to keep image in bounds
                fixTranslation()
                imageMatrix = matrix
            }
            return true
        }
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            mode = NONE
        }
    }
    
    private fun fixTranslation() {
        val values = FloatArray(9)
        matrix.getValues(values)
        
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        val scaleX = values[Matrix.MSCALE_X]
        val scaleY = values[Matrix.MSCALE_Y]
        
        // Calculate the actual bounds of the transformed image
        val bounds = android.graphics.RectF(0f, 0f, imageWidth, imageHeight)
        matrix.mapRect(bounds)
        
        val imageLeft = bounds.left
        val imageTop = bounds.top  
        val imageRight = bounds.right
        val imageBottom = bounds.bottom
        
        val imageDisplayWidth = imageRight - imageLeft
        val imageDisplayHeight = imageBottom - imageTop
        
        var fixTransX = transX
        var fixTransY = transY
        
        // Fix horizontal translation
        if (imageDisplayWidth <= viewWidth) {
            // Center horizontally if image is smaller than view
            fixTransX = transX + (viewWidth - imageDisplayWidth) / 2f - imageLeft
        } else {
            // Limit panning if image is larger than view
            if (imageLeft > 0) fixTransX = transX - imageLeft
            if (imageRight < viewWidth) fixTransX = transX + (viewWidth - imageRight)
        }
        
        // Fix vertical translation  
        if (imageDisplayHeight <= viewHeight) {
            // Center vertically if image is smaller than view
            fixTransY = transY + (viewHeight - imageDisplayHeight) / 2f - imageTop
        } else {
            // Limit panning if image is larger than view
            if (imageTop > 0) fixTransY = transY - imageTop
            if (imageBottom < viewHeight) fixTransY = transY + (viewHeight - imageBottom)
        }
        
        // Only apply fix if there's a significant difference
        if (abs(fixTransX - transX) > 1f || abs(fixTransY - transY) > 1f) {
            matrix.postTranslate(fixTransX - transX, fixTransY - transY)
        }
    }
    
    fun getCroppedBitmap(targetWidth: Int, targetHeight: Int): Bitmap? {
        val d = drawable ?: return null
        
        // Create a bitmap that matches the view size
        val viewBitmap = Bitmap.createBitmap(
            viewWidth.toInt(), viewHeight.toInt(), Bitmap.Config.ARGB_8888
        )
        val viewCanvas = Canvas(viewBitmap)
        
        // Fill with transparent background
        viewCanvas.drawColor(android.graphics.Color.TRANSPARENT)
        
        // Save canvas state
        viewCanvas.save()
        
        // Apply the current image transformation matrix
        viewCanvas.concat(imageMatrix)
        
        // Draw the image
        d.setBounds(0, 0, imageWidth.toInt(), imageHeight.toInt())
        d.draw(viewCanvas)
        
        // Restore canvas state for text overlay
        viewCanvas.restore()
        
        // Draw text overlay on top (not transformed)
        if (showText && overlayText.isNotEmpty()) {
            val textBounds = Rect()
            textPaint.getTextBounds(overlayText, 0, overlayText.length, textBounds)
            
            val textX = (viewWidth - textBounds.width()) / 2f
            val textY = viewHeight - 40f // 40dp from bottom
            
            viewCanvas.drawText(overlayText, textX, textY, textPaint)
        }
        
        // Scale to target dimensions
        return Bitmap.createScaledBitmap(viewBitmap, targetWidth, targetHeight, true)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw text overlay if enabled and text is set
        if (showText && overlayText.isNotEmpty()) {
            val textBounds = Rect()
            textPaint.getTextBounds(overlayText, 0, overlayText.length, textBounds)
            
            val x = (width - textBounds.width()) / 2f
            val y = height - 40f // 40dp from bottom
            
            canvas.drawText(overlayText, x, y, textPaint)
        }
    }
    
    // Public methods for controlling the image
    
    fun setOverlayText(text: String) {
        overlayText = text
        invalidate()
    }
    
    fun showTextOverlay(show: Boolean) {
        showText = show
        invalidate()
    }
    
    fun getCurrentRotation(): Float = rotation
    
    fun getOverlayText(): String = overlayText
    
    fun rotateImage(degrees: Float = 90f) {
        rotation += degrees
        rotation = rotation % 360f
        if (rotation < 0) rotation += 360f
        setImageToCenter()
        invalidate()
    }
}
