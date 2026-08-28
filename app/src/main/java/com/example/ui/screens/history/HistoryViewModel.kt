package com.example.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SnapScanDatabase
import com.example.data.model.QrType
import com.example.data.model.ScanHistoryItem
import com.example.data.repository.ScanHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val searchQuery: String = "",
    val selectedFilterType: QrType? = null,
    val showClearAllDialog: Boolean = false,
    val userMessage: String? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScanHistoryRepository

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    init {
        val database = SnapScanDatabase.getDatabase(application)
        repository = ScanHistoryRepository(database.scanHistoryDao())
    }

    val historyItems: StateFlow<List<ScanHistoryItem>> = combine(
        repository.allScans,
        _uiState
    ) { scans, state ->
        scans.filter { item ->
            val matchesQuery = if (state.searchQuery.isBlank()) true else {
                item.title.contains(state.searchQuery, ignoreCase = true) ||
                item.rawValue.contains(state.searchQuery, ignoreCase = true) ||
                item.displayDetails.contains(state.searchQuery, ignoreCase = true)
            }
            val matchesType = if (state.selectedFilterType == null) true else {
                item.type == state.selectedFilterType
            }
            matchesQuery && matchesType
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectFilterType(type: QrType?) {
        _uiState.update { it.copy(selectedFilterType = type) }
    }

    fun deleteItem(item: ScanHistoryItem) {
        viewModelScope.launch {
            repository.deleteScan(item)
            _uiState.update { it.copy(userMessage = "Scan removed from history") }
        }
    }

    fun setShowClearAllDialog(show: Boolean) {
        _uiState.update { it.copy(showClearAllDialog = show) }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
            _uiState.update {
                it.copy(
                    showClearAllDialog = false,
                    userMessage = "All scan history cleared"
                )
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
