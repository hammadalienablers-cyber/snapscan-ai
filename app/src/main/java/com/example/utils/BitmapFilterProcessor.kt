package com.example.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

enum class PhotoFilterOption(val displayName: String, val isAi: Boolean = false) {
    ORIGINAL("Original"),
    BLACK_AND_WHITE("B & W"),
    SEPIA("Sepia"),
    BRIGHTNESS_CONTRAST("Adjust"),
    BEAUTY_SMOOTH("Beauty"),
    AI_BG_REMOVE("AI Cutout", isAi = true),
    AI_ENHANCE("AI Enhance", isAi = true)
}

object BitmapFilterProcessor {

    fun applyGrayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    fun applySepia(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val matrix = ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /**
     * @param brightness value from -100f to +100f (0 = default)
     * @param contrast value from 0.5f to 2.0f (1.0 = default)
     */
    fun applyBrightnessAndContrast(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Contrast formula: scale = contrast; translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        val scale = contrast
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness

        val matrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /**
     * Basic Beauty / Smoothing filter:
     * Gentle soft focus + warm highlights + skin luminosity glow
     */
    fun applyBeautyFilter(source: Bitmap, intensity: Float = 0.65f): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Draw base image
        canvas.drawBitmap(source, 0f, 0f, null)

        // Create a downscaled blurred version for soft glow
        val scale = 0.25f
        val smallWidth = max(1, (width * scale).toInt())
        val smallHeight = max(1, (height * scale).toInt())
        val smallBitmap = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)

        val blurred = Bitmap.createScaledBitmap(smallBitmap, width, height, true)

        // Blend blurred layer with soft alpha for dream-like smoothing
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (min(1f, intensity) * 110).toInt()
        }
        canvas.drawBitmap(blurred, 0f, 0f, glowPaint)

        // Apply slight warmth & gentle contrast enhancement
        val warmthMatrix = ColorMatrix(
            floatArrayOf(
                1.05f, 0f,    0f,    0f, 8f,
                0f,    1.02f, 0f,    0f, 4f,
                0f,    0f,    0.98f, 0f, 0f,
                0f,    0f,    0f,    1f, 0f
            )
        )
        val warmthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(warmthMatrix)
        }
        val finalResult = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalResult)
        finalCanvas.drawBitmap(output, 0f, 0f, warmthPaint)

        return finalResult
    }
}
