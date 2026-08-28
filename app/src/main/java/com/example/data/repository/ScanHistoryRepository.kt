package com.example.data.repository

import com.example.data.local.ScanHistoryDao
import com.example.data.model.ScanHistoryItem
import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(private val scanHistoryDao: ScanHistoryDao) {
    val allScans: Flow<List<ScanHistoryItem>> = scanHistoryDao.getAllScans()

    suspend fun insertScan(item: ScanHistoryItem): Long {
        return scanHistoryDao.insertScan(item)
    }

    suspend fun deleteScan(item: ScanHistoryItem) {
        scanHistoryDao.deleteScan(item)
    }

    suspend fun deleteById(id: Long) {
        scanHistoryDao.deleteById(id)
    }

    suspend fun clearAll() {
        scanHistoryDao.clearAllHistory()
    }
}
