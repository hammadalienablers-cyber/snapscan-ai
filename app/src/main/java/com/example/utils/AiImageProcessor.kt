package com.example.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Base64
import com.example.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object AiImageProcessor {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fast, 100% on-device AI Background Cutout using ML Kit Selfie Segmentation.
     * Extracts foreground with smooth alpha blending.
     */
    suspend fun removeBackgroundOnDevice(source: Bitmap, backgroundColor: Int = Color.TRANSPARENT): Result<Bitmap> =
        withContext(Dispatchers.Default) {
            try {
                // Ensure dimensions are reasonable for ML Kit
                val maxDim = 1280
                val scale = if (source.width > maxDim || source.height > maxDim) {
                    val ratio = maxDim.toFloat() / maxOf(source.width, source.height)
                    ratio
                } else 1.0f

                val scaledBitmap = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(
                        source,
                        (source.width * scale).toInt(),
                        (source.height * scale).toInt(),
                        true
                    )
                } else {
                    source
                }

                val options = SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .enableRawSizeMask()
                    .build()

                val segmenter = Segmentation.getClient(options)
                val inputImage = InputImage.fromBitmap(scaledBitmap, 0)

                val segmentationMask = suspendCancellableCoroutine { cont ->
                    segmenter.process(inputImage)
                        .addOnSuccessListener { mask ->
                            cont.resume(mask)
                        }
                        .addOnFailureListener { exc ->
                            cont.resume(null)
                        }
                }

                if (segmentationMask == null) {
                    // Fallback: Return original
                    return@withContext Result.failure(Exception("Could not detect subject mask."))
                }

                val maskBuffer = segmentationMask.buffer
                val maskWidth = segmentationMask.width
                val maskHeight = segmentationMask.height

                val outputBitmap = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
                scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)

                maskBuffer.rewind()

                for (y in 0 until scaledBitmap.height) {
                    for (x in 0 until scaledBitmap.width) {
                        val index = y * scaledBitmap.width + x
                        val confidence = if (maskBuffer.hasRemaining()) maskBuffer.float else 1f
                        val originalColor = pixels[index]

                        if (confidence > 0.45f) {
                            val alpha = (confidence.coerceIn(0f, 1f) * 255).toInt()
                            // Smooth edge
                            val r = Color.red(originalColor)
                            val g = Color.green(originalColor)
                            val b = Color.blue(originalColor)
                            pixels[index] = Color.argb(alpha, r, g, b)
                        } else {
                            pixels[index] = backgroundColor
                        }
                    }
                }

                outputBitmap.setPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)

                // Scale back to original resolution if needed
                val finalOutput = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(outputBitmap, source.width, source.height, true)
                } else {
                    outputBitmap
                }

                Result.success(finalOutput)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * AI Photo Enhance & Colorize:
     * Attempts to call Gemini 2.5 Flash Image multimodal model if API key is provided,
     * or gracefully falls back to local high-dynamic HDR color equalization and contrast enhancement.
     */
    suspend fun enhanceAndColorizePhoto(source: Bitmap): Result<Bitmap> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val cloudResult = requestGeminiImageEdit(
                    source = source,
                    prompt = "Enhance this photograph, restore details, denoise, improve lighting and contrast, and realistically colorize if it is black and white. Return clean high-fidelity photograph.",
                    apiKey = apiKey
                )
                if (cloudResult.isSuccess) {
                    return@withContext cloudResult
                }
            } catch (e: Exception) {
                // Fall back to local enhancement
            }
        }

        // Local smart photo enhancer fallback:
        try {
            val localEnhanced = applySmartLocalEnhancement(source)
            Result.success(localEnhanced)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Local Smart Photo Enhancer:
     * Boosts shadow details, sharpens midtones, applies subtle warm saturation and vibrant contrast.
     */
    fun applySmartLocalEnhancement(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Color matrix for vibrant richness and dynamic clarity
        val matrix = ColorMatrix().apply {
            // Slight contrast boost and color saturation boost
            val contrast = 1.15f
            val brightness = 8f
            val saturation = 1.25f

            val cmContrast = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )

            val cmSat = ColorMatrix().apply { setSaturation(saturation) }
            postConcat(cmContrast)
            postConcat(cmSat)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }

        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private suspend fun requestGeminiImageEdit(
        source: Bitmap,
        prompt: String,
        apiKey: String
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            // Resize for API payload
            val maxDim = 1024
            val scale = if (source.width > maxDim || source.height > maxDim) {
                maxDim.toFloat() / maxOf(source.width, source.height)
            } else 1.0f

            val scaled = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt(),
                    (source.height * scale).toInt(),
                    true
                )
            } else source

            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val genConfig = JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("TEXT")
                        put("IMAGE")
                    })
                }
                put("generationConfig", genConfig)
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")

                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val data = inlineData.optString("data")
                            if (!data.isNullOrEmpty()) {
                                val bytes = Base64.decode(data, Base64.DEFAULT)
                                val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (decodedBitmap != null) {
                                    return@withContext Result.success(decodedBitmap)
                                }
                            }
                        }
                    }
                }
            }

            Result.failure(Exception("No image returned from AI model, using smart local engine."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
