package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class QrType {
    URL,
    WIFI,
    TEXT,
    EMAIL,
    PHONE,
    SMS,
    GEO
}

@Entity(tableName = "scan_history")
data class ScanHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawValue: String,
    val type: QrType,
    val title: String,
    val displayDetails: String,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
    val wifiEncryption: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
