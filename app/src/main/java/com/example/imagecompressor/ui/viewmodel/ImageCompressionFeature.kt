package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.userMessage
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.example.imagecompressor.ui.state.BatchProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImageCompressionFeature(
  private val state: MutableStateFlow<ImageCompressorUiState>,
  private val imageRepository: ImageRepository,
  private val scope: CoroutineScope,
  private val showMessage: (String) -> Unit,
) {
  private var job: Job? = null
  fun compressSelectedImages() {
    val snapshot = state.value
    if (snapshot.selectedImages.isEmpty()) { showMessage("Select at least one image first."); return }
    launch(resetResults = true) { imageRepository.compressImages(snapshot.selectedImages, snapshot.compressionSettings) }
  }
  fun retryRemaining() { launch { imageRepository.retryBatch() } }
  private fun launch(resetResults: Boolean = false, operation: suspend () -> Unit) {
    if (job?.isActive == true || state.value.isCompressing || state.value.isInitializing || state.value.initializationError != null || state.value.isLoadingImages || state.value.isSaving) return
    job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
      state.update {
        val ready = if (resetResults) it.copy(batch = null, compressedImages = emptyList(), compareImage = null,
          progress = BatchProgress(total = it.selectedImages.size), saveErrors = emptyMap()) else it
        ready.copy(screen = AppScreen.RESULTS, isCompressing = true, isCancelling = false, message = null)
      }
      try { operation() }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { showMessage(userMessage(failure, "Compression was interrupted. Completed images were kept.")) }
      finally { state.update { it.copy(isCompressing = false, isCancelling = false) } }
    }
  }
  fun cancel() {
    if (job?.isActive == true) {
      state.update { it.copy(isCancelling = true) }
      job?.cancel()
    }
  }
}
