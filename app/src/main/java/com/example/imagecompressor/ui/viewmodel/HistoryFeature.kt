package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class HistoryFeature(
  private val imageRepository: ImageRepository,
  private val scope: CoroutineScope,
  private val showMessage: (String) -> Unit,
) {
  fun deleteHistory(item: CompressionHistoryEntity) {
    scope.launch {
      runCatching { imageRepository.deleteHistory(item) }
        .onFailure { showMessage(it.message ?: "Unable to remove this history item.") }
    }
  }

  fun clearHistory() {
    scope.launch {
      runCatching { imageRepository.clearHistory() }
        .onSuccess { showMessage("History cleared.") }
        .onFailure { showMessage(it.message ?: "Unable to clear history.") }
    }
  }
}