package com.beforeafter.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/**
 * AI-based ranking utility for Before/After image pairs
 * Uses on-device image analysis to score transformations
 */
class AIRankingUtil {

    companion object {
        /**
         * Calculates a quality score for a before/after image pair
         * @param beforeBitmap The before image
         * @param afterBitmap The after image
         * @return Score from 0.0 to 100.0 (higher is better transformation)
         */
        fun calculateTransformationScore(beforeBitmap: Bitmap?, afterBitmap: Bitmap?): Float {
            if (beforeBitmap == null || afterBitmap == null) return 0f
            
            var totalScore = 0f
            var scoreComponents = 0
            
            // 1. Color Vibrancy Improvement (0-25 points)
            val colorScore = calculateColorImprovementScore(beforeBitmap, afterBitmap)
            totalScore += colorScore
            scoreComponents++
            
            // 2. Contrast Enhancement (0-25 points)
            val contrastScore = calculateContrastImprovementScore(beforeBitmap, afterBitmap)
            totalScore += contrastScore
            scoreComponents++
            
            // 3. Brightness Optimization (0-25 points)
            val brightnessScore = calculateBrightnessImprovementScore(beforeBitmap, afterBitmap)
            totalScore += brightnessScore
            scoreComponents++
            
            // 4. Overall Visual Appeal (0-25 points)
            val appealScore = calculateVisualAppealScore(beforeBitmap, afterBitmap)
            totalScore += appealScore
            scoreComponents++
            
            return if (scoreComponents > 0) totalScore / scoreComponents else 0f
        }
        
        /**
         * Analyzes color vibrancy improvement
         */
        private fun calculateColorImprovementScore(before: Bitmap, after: Bitmap): Float {
            val beforeVibrancy = calculateColorVibrancy(before)
            val afterVibrancy = calculateColorVibrancy(after)
            
            val improvement = afterVibrancy - beforeVibrancy
            return maxOf(0f, minOf(25f, improvement * 100f + 12.5f))
        }
        
        /**
         * Analyzes contrast improvement
         */
        private fun calculateContrastImprovementScore(before: Bitmap, after: Bitmap): Float {
            val beforeContrast = calculateContrast(before)
            val afterContrast = calculateContrast(after)
            
            val improvement = afterContrast - beforeContrast
            return maxOf(0f, minOf(25f, improvement * 50f + 12.5f))
        }
        
        /**
         * Analyzes brightness optimization
         */
        private fun calculateBrightnessImprovementScore(before: Bitmap, after: Bitmap): Float {
            val beforeBrightness = calculateAverageBrightness(before)
            val afterBrightness = calculateAverageBrightness(after)
            
            // Optimal brightness is around 0.4-0.6 (40-60%)
            val beforeOptimal = 1f - abs(beforeBrightness - 0.5f) * 2f
            val afterOptimal = 1f - abs(afterBrightness - 0.5f) * 2f
            
            val improvement = afterOptimal - beforeOptimal
            return maxOf(0f, minOf(25f, improvement * 25f + 12.5f))
        }
        
        /**
         * Calculates overall visual appeal improvement
         */
        private fun calculateVisualAppealScore(before: Bitmap, after: Bitmap): Float {
            // Combine multiple factors for overall appeal
            val beforeSharpness = calculateSharpness(before)
            val afterSharpness = calculateSharpness(after)
            
            val sharpnessImprovement = afterSharpness - beforeSharpness
            return maxOf(0f, minOf(25f, sharpnessImprovement * 100f + 12.5f))
        }
        
        /**
         * Calculates color vibrancy of an image
         */
        private fun calculateColorVibrancy(bitmap: Bitmap): Float {
            val sampleSize = minOf(bitmap.width, bitmap.height, 100)
            val stepX = bitmap.width / sampleSize
            val stepY = bitmap.height / sampleSize
            
            var totalSaturation = 0f
            var pixelCount = 0
            
            for (x in 0 until bitmap.width step stepX) {
                for (y in 0 until bitmap.height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    val hsv = FloatArray(3)
                    Color.colorToHSV(pixel, hsv)
                    totalSaturation += hsv[1] // Saturation component
                    pixelCount++
                }
            }
            
            return if (pixelCount > 0) totalSaturation / pixelCount else 0f
        }
        
        /**
         * Calculates image contrast
         */
        private fun calculateContrast(bitmap: Bitmap): Float {
            val sampleSize = minOf(bitmap.width, bitmap.height, 100)
            val stepX = bitmap.width / sampleSize
            val stepY = bitmap.height / sampleSize
            
            val brightnesses = mutableListOf<Float>()
            
            for (x in 0 until bitmap.width step stepX) {
                for (y in 0 until bitmap.height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / (3f * 255f)
                    brightnesses.add(brightness)
                }
            }
            
            if (brightnesses.isEmpty()) return 0f
            
            val mean = brightnesses.average().toFloat()
            val variance = brightnesses.map { (it - mean).pow(2) }.average().toFloat()
            return sqrt(variance) // Standard deviation as contrast measure
        }
        
        /**
         * Calculates average brightness of an image
         */
        private fun calculateAverageBrightness(bitmap: Bitmap): Float {
            val sampleSize = minOf(bitmap.width, bitmap.height, 100)
            val stepX = bitmap.width / sampleSize
            val stepY = bitmap.height / sampleSize
            
            var totalBrightness = 0f
            var pixelCount = 0
            
            for (x in 0 until bitmap.width step stepX) {
                for (y in 0 until bitmap.height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / (3f * 255f)
                    totalBrightness += brightness
                    pixelCount++
                }
            }
            
            return if (pixelCount > 0) totalBrightness / pixelCount else 0f
        }
        
        /**
         * Calculates image sharpness using edge detection
         */
        private fun calculateSharpness(bitmap: Bitmap): Float {
            val sampleSize = minOf(bitmap.width, bitmap.height, 50)
            val stepX = bitmap.width / sampleSize
            val stepY = bitmap.height / sampleSize
            
            var totalEdgeStrength = 0f
            var edgeCount = 0
            
            for (x in stepX until bitmap.width - stepX step stepX) {
                for (y in stepY until bitmap.height - stepY step stepY) {
                    val centerPixel = bitmap.getPixel(x, y)
                    val rightPixel = bitmap.getPixel(x + stepX, y)
                    val bottomPixel = bitmap.getPixel(x, y + stepY)
                    
                    val centerGray = getGrayscale(centerPixel)
                    val rightGray = getGrayscale(rightPixel)
                    val bottomGray = getGrayscale(bottomPixel)
                    
                    val horizontalEdge = abs(centerGray - rightGray)
                    val verticalEdge = abs(centerGray - bottomGray)
                    val edgeStrength = sqrt(horizontalEdge.pow(2) + verticalEdge.pow(2))
                    
                    totalEdgeStrength += edgeStrength
                    edgeCount++
                }
            }
            
            return if (edgeCount > 0) totalEdgeStrength / edgeCount else 0f
        }
        
        /**
         * Converts pixel to grayscale value
         */
        private fun getGrayscale(pixel: Int): Float {
            return (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f) / 255f
        }
        
        /**
         * Gets a descriptive ranking category based on score
         */
        fun getRankingCategory(score: Float): String {
            return when {
                score >= 80f -> "🏆 Incredible Transformation"
                score >= 65f -> "⭐ Great Improvement"
                score >= 50f -> "👍 Good Progress"
                score >= 35f -> "📈 Noticeable Change"
                score >= 20f -> "🔍 Subtle Enhancement"
                else -> "📊 Comparison Ready"
            }
        }
        
        /**
         * Gets ranking emoji based on score
         */
        fun getRankingEmoji(score: Float): String {
            return when {
                score >= 80f -> "🏆"
                score >= 65f -> "⭐"
                score >= 50f -> "👍"
                score >= 35f -> "📈"
                score >= 20f -> "🔍"
                else -> "📊"
            }
        }
    }
}
