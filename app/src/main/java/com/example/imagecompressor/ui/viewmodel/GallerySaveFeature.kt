package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GallerySaveFeature(
    private val state: MutableStateFlow<ImageCompressorUiState>,
    private val imageRepository: ImageRepository,
    private val scope: CoroutineScope,
    private val showMessage: (String) -> Unit,
) {
  fun saveToGallery(image: CompressedImage) {
    if (image.savedUri != null) {
      showMessage("This image is already saved.")
      return
    }
    scope.launch {
      state.update { it.copy(isSaving = true, message = null) }
      runCatching { imageRepository.saveToGallery(image) }
        .onSuccess { saved ->
          state.update { current ->
            current.copy(
              compressedImages =
                current.compressedImages.map { if (it.outputFilePath == saved.outputFilePath) saved else it },
              compareImage =
                current.compareImage?.let {
                  if (it.outputFilePath == saved.outputFilePath) saved else it
                },
              isSaving = false,
              message = "Saved to your gallery.",
            )
          }
        }
        .onFailure { throwable ->
          state.update {
            it.copy(isSaving = false, message = throwable.message ?: "Unable to save the image.")
          }
        }
    }
  }

  fun saveAllToGallery() {
    val images = state.value.compressedImages.filter { it.savedUri == null }
    if (images.isEmpty()) {
      showMessage("All compressed images are already saved.")
      return
    }
    scope.launch {
      state.update { it.copy(isSaving = true, message = null) }
      runCatching {
          val savedByPath =
            images.associate { image ->
              val saved = imageRepository.saveToGallery(image)
              saved.outputFilePath to saved
            }
          state.update { current ->
            current.copy(
              compressedImages =
                current.compressedImages.map { image -> savedByPath[image.outputFilePath] ?: image },
              compareImage =
                current.compareImage?.let { image -> savedByPath[image.outputFilePath] ?: image },
            )
          }
        }
        .onSuccess {
          state.update { it.copy(isSaving = false, message = "Saved all images to your gallery.") }
        }
        .onFailure { throwable ->
          state.update {
            it.copy(isSaving = false, message = throwable.message ?: "Unable to save every image.")
          }
        }
    }
  }
}