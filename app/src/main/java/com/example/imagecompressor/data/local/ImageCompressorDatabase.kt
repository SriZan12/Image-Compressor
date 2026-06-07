package com.example.imagecompressor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CompressionHistoryEntity::class], version = 1, exportSchema = true)
abstract class ImageCompressorDatabase : RoomDatabase() {
  abstract fun historyDao(): CompressionHistoryDao
}
