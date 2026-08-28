package com.example.ui.screens.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SnapScanDatabase
import com.example.data.model.ScanHistoryItem
import com.example.data.repository.ScanHistoryRepository
import com.example.utils.ParsedQrResult
import com.example.utils.QrCodeParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isTorchOn: Boolean = false,
    val isFrontCamera: Boolean = false,
    val activeResult: ParsedQrResult? = null,
    val isScanningPaused: Boolean = false,
    val lastScannedValue: String? = null,
    val feedbackMessage: String? = null
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScanHistoryRepository

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    init {
        val database = SnapScanDatabase.getDatabase(application)
        repository = ScanHistoryRepository(database.scanHistoryDao())
    }

    fun onQrDetected(rawValue: String) {
        if (_uiState.value.isScanningPaused) return
        if (rawValue == _uiState.value.lastScannedValue && _uiState.value.activeResult != null) return

        val parsed = QrCodeParser.parse(rawValue)
        _uiState.update {
            it.copy(
                activeResult = parsed,
                isScanningPaused = true,
                lastScannedValue = rawValue
            )
        }

        // Auto save to local history database
        viewModelScope.launch {
            repository.insertScan(parsed.toScanHistoryItem())
        }
    }

    fun dismissResult() {
        _uiState.update {
            it.copy(
                activeResult = null,
                isScanningPaused = false
            )
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
