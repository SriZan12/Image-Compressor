package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.BatchProgress
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImageCompressionFeature(
    private val state: MutableStateFlow<ImageCompressorUiState>,
    private val imageRepository: ImageRepository,
    private val scope: CoroutineScope,
    private val showMessage: (String) -> Unit,
) {
  fun compressSelectedImages() {
    val images = state.value.selectedImages
    if (images.isEmpty()) {
      showMessage("Select at least one image first.")
      return
    }
    scope.launch {
      state.update {
        it.copy(
          screen = AppScreen.RESULTS,
          compressedImages = emptyList(),
          progress = BatchProgress(total = images.size),
          isCompressing = true,
          message = null,
        )
      }
      runCatching {
          imageRepository.compressImages(images, state.value.compressionSettings) { completed, total ->
            state.update { current -> current.copy(progress = BatchProgress(completed, total)) }
          }
        }
        .onSuccess { compressed ->
          state.update {
            it.copy(
              compressedImages = compressed,
              isCompressing = false,
              message = "Compression complete.",
            )
          }
        }
        .onFailure { throwable ->
          state.update {
            it.copy(
              isCompressing = false,
              message = throwable.message ?: "Unable to compress the selected images.",
            )
          }
        }
    }
  }
}