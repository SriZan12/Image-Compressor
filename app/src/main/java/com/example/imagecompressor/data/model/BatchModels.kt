package com.example.imagecompressor.data.model

const val MAX_BATCH_IMAGES = 30

enum class BatchStatus { RUNNING, COMPLETED, CANCELLED, INTERRUPTED }
enum class ItemStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

data class BatchItem(
  val id: String,
  val image: SelectedImage,
  val status: ItemStatus,
  val result: CompressedImage? = null,
  val error: String? = null,
) {
  val canRetry get() = status in setOf(ItemStatus.FAILED, ItemStatus.CANCELLED, ItemStatus.INTERRUPTED)
}

data class BatchSnapshot(val id: String, val settings: CompressionSettings, val status: BatchStatus, val items: List<BatchItem>) {
  val results get() = items.mapNotNull { it.result }
  val finished get() = items.count { it.status !in setOf(ItemStatus.PENDING, ItemStatus.RUNNING) }
}

data class SelectionResult(val images: List<SelectedImage>, val errors: List<String>)
