package com.example.ui.screens.editor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.utils.AiImageProcessor
import com.example.utils.BitmapFilterProcessor
import com.example.utils.ImageFileHelper
import com.example.utils.PhotoFilterOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val originalBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val selectedFilter: PhotoFilterOption = PhotoFilterOption.ORIGINAL,
    val brightness: Float = 0f, // -100 to 100
    val contrast: Float = 1.0f, // 0.5 to 2.0
    val beautyIntensity: Float = 0.65f,
    val isProcessingAi: Boolean = false,
    val aiStatusMessage: String? = null,
    val userMessage: String? = null,
    val isComparingOriginal: Boolean = false
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var filterJob: Job? = null

    fun setImageUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    // Downscale if unreasonably large for mobile RAM
                    val maxSide = 1920
                    val scaled = if (bitmap.width > maxSide || bitmap.height > maxSide) {
                        val ratio = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * ratio).toInt(),
                            (bitmap.height * ratio).toInt(),
                            true
                        )
                    } else bitmap

                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                originalBitmap = scaled,
                                previewBitmap = scaled,
                                selectedFilter = PhotoFilterOption.ORIGINAL,
                                brightness = 0f,
                                contrast = 1.0f,
                                userMessage = "Photo loaded successfully"
                            )
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(userMessage = "Could not decode selected image") }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(userMessage = "Failed to load photo: ${e.message}") }
                }
            }
        }
    }

    fun setDirectBitmap(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                originalBitmap = bitmap,
                previewBitmap = bitmap,
                selectedFilter = PhotoFilterOption.ORIGINAL,
                brightness = 0f,
                contrast = 1.0f,
                userMessage = "Photo captured"
            )
        }
    }

    fun selectFilter(filter: PhotoFilterOption) {
        val original = _uiState.value.originalBitmap ?: return
        _uiState.update { it.copy(selectedFilter = filter) }

        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            when (filter) {
                PhotoFilterOption.ORIGINAL -> {
                    _uiState.update { it.copy(previewBitmap = original, isProcessingAi = false) }
                }
                PhotoFilterOption.BLACK_AND_WHITE -> {
                    val bw = BitmapFilterProcessor.applyGrayscale(original)
                    _uiState.update { it.copy(previewBitmap = bw, isProcessingAi = false) }
                }
                PhotoFilterOption.SEPIA -> {
                    val sepia = BitmapFilterProcessor.applySepia(original)
                    _uiState.update { it.copy(previewBitmap = sepia, isProcessingAi = false) }
                }
                PhotoFilterOption.BRIGHTNESS_CONTRAST -> {
                    val adjusted = BitmapFilterProcessor.applyBrightnessAndContrast(
                        original,
                        _uiState.value.brightness,
                        _uiState.value.contrast
                    )
                    _uiState.update { it.copy(previewBitmap = adjusted, isProcessingAi = false) }
                }
                PhotoFilterOption.BEAUTY_SMOOTH -> {
                    val beauty = BitmapFilterProcessor.applyBeautyFilter(
                        original,
                        _uiState.value.beautyIntensity
                    )
                    _uiState.update { it.copy(previewBitmap = beauty, isProcessingAi = false) }
                }
                PhotoFilterOption.AI_BG_REMOVE -> {
                    _uiState.update {
                        it.copy(
                            isProcessingAi = true,
                            aiStatusMessage = "AI Neural Segmentation removing background..."
                        )
                    }
                    val result = AiImageProcessor.removeBackgroundOnDevice(original)
                    result.onSuccess { cutout ->
                        _uiState.update {
                            it.copy(
                                previewBitmap = cutout,
                                isProcessingAi = false,
                                aiStatusMessage = null,
                                userMessage = "Background removed successfully!"
                            )
                        }
                    }.onFailure { exc ->
                        _uiState.update {
                            it.copy(
                                previewBitmap = original,
                                isProcessingAi = false,
                                aiStatusMessage = null,
                                userMessage = "Cutout error: ${exc.message ?: "Try a photo with clear subject"}"
                            )
                        }
                    }
                }
                PhotoFilterOption.AI_ENHANCE -> {
                    _uiState.update {
                        it.copy(
                            isProcessingAi = true,
                            aiStatusMessage = "AI Enhancing, denoising and colorizing photo..."
                        )
                    }
                    val result = AiImageProcessor.enhanceAndColorizePhoto(original)
                    result.onSuccess { enhanced ->
                        _uiState.update {
                            it.copy(
                                previewBitmap = enhanced,
                                isProcessingAi = false,
                                aiStatusMessage = null,
                                userMessage = "AI Enhancement complete!"
                            )
                        }
                    }.onFailure { exc ->
                        _uiState.update {
                            it.copy(
                                previewBitmap = original,
                                isProcessingAi = false,
                                aiStatusMessage = null,
                                userMessage = "Enhancement note: ${exc.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateBrightness(value: Float) {
        _uiState.update { it.copy(brightness = value) }
        applyAdjustmentDebounced()
    }

    fun updateContrast(value: Float) {
        _uiState.update { it.copy(contrast = value) }
        applyAdjustmentDebounced()
    }

    fun updateBeautyIntensity(value: Float) {
        _uiState.update { it.copy(beautyIntensity = value) }
        val original = _uiState.value.originalBitmap ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val beauty = BitmapFilterProcessor.applyBeautyFilter(original, value)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(previewBitmap = beauty) }
            }
        }
    }

    private fun applyAdjustmentDebounced() {
        val original = _uiState.value.originalBitmap ?: return
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val adjusted = BitmapFilterProcessor.applyBrightnessAndContrast(
                original,
                _uiState.value.brightness,
                _uiState.value.contrast
            )
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(previewBitmap = adjusted) }
            }
        }
    }

    fun setComparing(isComparing: Boolean) {
        _uiState.update { it.copy(isComparingOriginal = isComparing) }
    }

    fun resetAll() {
        val original = _uiState.value.originalBitmap
        _uiState.update {
            it.copy(
                previewBitmap = original,
                selectedFilter = PhotoFilterOption.ORIGINAL,
                brightness = 0f,
                contrast = 1.0f,
                beautyIntensity = 0.65f,
                isProcessingAi = false,
                aiStatusMessage = null,
                userMessage = "Filters reset"
            )
        }
    }

    fun saveToGallery(context: Context) {
        val bitmapToSave = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(aiStatusMessage = "Saving to gallery...") }
            val result = ImageFileHelper.saveBitmapToGallery(context, bitmapToSave)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        aiStatusMessage = null,
                        userMessage = "Saved to Gallery in Pictures/SnapScanAI!"
                    )
                }
            }.onFailure { exc ->
                _uiState.update {
                    it.copy(
                        aiStatusMessage = null,
                        userMessage = "Save failed: ${exc.message}"
                    )
                }
            }
        }
    }

    fun sharePhoto(context: Context) {
        val bitmapToShare = _uiState.value.previewBitmap ?: return
        viewModelScope.launch {
            ImageFileHelper.shareBitmap(context, bitmapToShare)
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
