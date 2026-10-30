package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class HistoryFeature(private val imageRepository: ImageRepository, private val scope: CoroutineScope,
  private val showMessage: (String) -> Unit) {
  fun deleteHistory(item: CompressionHistoryEntity) = remove { imageRepository.deleteHistory(item) }
  fun clearHistory() = remove { imageRepository.clearHistory() }
  private fun remove(action: suspend () -> Unit) {
    scope.launch {
      try { action(); showMessage("Removed from Shrink. Originals and gallery photos are safe.") }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { showMessage(userMessage(failure, "Unable to remove these copies. Try again.")) }
    }
  }
}
