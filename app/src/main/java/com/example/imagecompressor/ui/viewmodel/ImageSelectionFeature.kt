package com.example.imagecompressor.ui.viewmodel

import android.net.Uri
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImageSelectionFeature(
    private val state: MutableStateFlow<ImageCompressorUiState>,
    private val imageRepository: ImageRepository,
    private val scope: CoroutineScope,
) {
  fun addSelectedImages(uris: List<Uri>) {
    if (uris.isEmpty()) return
    scope.launch {
      state.update { it.copy(isLoadingImages = true, message = null) }
      runCatching { imageRepository.inspectImages(uris) }
        .onSuccess { inspected ->
          state.update { current ->
            val merged = (current.selectedImages + inspected).distinctBy { it.id }
            current.copy(
              selectedImages = merged,
              isLoadingImages = false,
              screen = AppScreen.PREVIEW,
              message = "${inspected.size} image${if (inspected.size == 1) "" else "s"} selected.",
            )
          }
        }
        .onFailure { throwable ->
          state.update {
            it.copy(
              isLoadingImages = false,
              message = throwable.message ?: "Unable to read the selected images.",
            )
          }
        }
    }
  }

  fun removeSelectedImage(image: SelectedImage) {
    state.update { current ->
      current.copy(selectedImages = current.selectedImages.filterNot { it.id == image.id })
    }
  }

  fun clearSelectedImages() {
    state.update { it.copy(selectedImages = emptyList()) }
  }
}