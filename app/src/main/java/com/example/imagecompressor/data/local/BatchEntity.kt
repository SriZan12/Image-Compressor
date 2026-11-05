package com.example.imagecompressor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import com.example.imagecompressor.data.model.SelectedImage
import androidx.core.net.toUri

@Entity(tableName = "compression_batch")
data class BatchEntity(@PrimaryKey val id: String, val settingsJson: String, val status: String)

@Entity(tableName = "compression_batch_item")
data class BatchItemEntity(
  @PrimaryKey val id: String,
  val batchId: String,
  val position: Int,
  val sourceUri: String,
  val displayName: String,
  val sourceBytes: Long,
  val sourceWidth: Int,
  val sourceHeight: Int,
  val sourceFormat: String,
  val status: String,
  val historyId: Long? = null,
  val error: String? = null,
) {
  fun selectedImage() = SelectedImage(sourceUri.toUri(), displayName, sourceBytes, sourceWidth, sourceHeight, sourceFormat)
}

data class BatchWithItems(
  @Embedded val batch: BatchEntity,
  @Relation(parentColumn = "id", entityColumn = "batchId") val items: List<BatchItemEntity>,
)
