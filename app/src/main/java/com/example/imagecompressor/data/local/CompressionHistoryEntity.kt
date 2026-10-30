package com.example.imagecompressor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "compression_history")
data class CompressionHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val createdAt: Long,
  val displayName: String,
  val originalUri: String,
  val compressedUri: String,
  val originalSizeBytes: Long,
  val compressedSizeBytes: Long,
  val width: Int,
  val height: Int,
  val outputFormat: String,
  val savedPath: String? = null,
  val localPath: String? = null,
  val pendingSaveUri: String? = null,
  @ColumnInfo(defaultValue = "'UNAVAILABLE'") val availability: String = "UNAVAILABLE",
  @ColumnInfo(defaultValue = "0") val sourceWidth: Int = 0,
  @ColumnInfo(defaultValue = "0") val sourceHeight: Int = 0,
  @ColumnInfo(defaultValue = "'IMAGE'") val sourceFormat: String = "IMAGE",
  val targetBytes: Long? = null,
  @ColumnInfo(defaultValue = "''") val adjustments: String = "",
  @ColumnInfo(defaultValue = "1") val encodingAttempts: Int = 1,
  val encodingQuality: Int? = null,
)
