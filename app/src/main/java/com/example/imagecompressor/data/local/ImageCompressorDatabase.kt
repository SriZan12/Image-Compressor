package com.example.imagecompressor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CompressionHistoryEntity::class, BatchEntity::class, BatchItemEntity::class], version = 2, exportSchema = true)
abstract class ImageCompressorDatabase : RoomDatabase() {
  abstract fun historyDao(): CompressionHistoryDao
  abstract fun batchDao(): BatchDao

  companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        listOf("localPath TEXT", "pendingSaveUri TEXT", "availability TEXT NOT NULL DEFAULT 'UNAVAILABLE'",
          "sourceWidth INTEGER NOT NULL DEFAULT 0", "sourceHeight INTEGER NOT NULL DEFAULT 0",
          "sourceFormat TEXT NOT NULL DEFAULT 'IMAGE'", "targetBytes INTEGER", "adjustments TEXT NOT NULL DEFAULT ''",
          "encodingAttempts INTEGER NOT NULL DEFAULT 1", "encodingQuality INTEGER").forEach {
          db.execSQL("ALTER TABLE compression_history ADD COLUMN $it")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS compression_batch (id TEXT NOT NULL PRIMARY KEY, settingsJson TEXT NOT NULL, status TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS compression_batch_item (
          id TEXT NOT NULL PRIMARY KEY, batchId TEXT NOT NULL, position INTEGER NOT NULL,
          sourceUri TEXT NOT NULL, displayName TEXT NOT NULL, sourceBytes INTEGER NOT NULL,
          sourceWidth INTEGER NOT NULL, sourceHeight INTEGER NOT NULL, sourceFormat TEXT NOT NULL,
          status TEXT NOT NULL, historyId INTEGER, error TEXT)""")
      }
    }
  }
}
