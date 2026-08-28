package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.QrType
import com.example.data.model.ScanHistoryItem

class Converters {
    @TypeConverter
    fun fromQrType(type: QrType): String = type.name

    @TypeConverter
    fun toQrType(value: String): QrType {
        return try {
            QrType.valueOf(value)
        } catch (e: Exception) {
            QrType.TEXT
        }
    }
}

@Database(entities = [ScanHistoryItem::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SnapScanDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SnapScanDatabase? = null

        fun getDatabase(context: Context): SnapScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SnapScanDatabase::class.java,
                    "snapscan_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
