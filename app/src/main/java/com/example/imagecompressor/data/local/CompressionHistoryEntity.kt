package com.example.imagecompressor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

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
)
