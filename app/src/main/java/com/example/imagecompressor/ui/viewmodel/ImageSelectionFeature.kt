package com.example.imagecompressor.ui.viewmodel

import android.net.Uri
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.userMessage
import com.example.imagecompressor.data.model.MAX_BATCH_IMAGES
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImageSelectionFeature(private val state: MutableStateFlow<ImageCompressorUiState>,
  private val imageRepository: ImageRepository, private val scope: CoroutineScope) {
  fun addSelectedImages(uris: List<Uri>) {
    if (uris.isEmpty() || state.value.isLoadingImages || state.value.isCompressing || state.value.isInitializing || state.value.initializationError != null) return
    val existing = state.value.selectedImages.map { it.uri }.toSet()
    val available = MAX_BATCH_IMAGES - existing.size
    val requested = uris.distinct().filterNot { it in existing }
    val accepted = requested.take(available.coerceAtLeast(0))
    if (accepted.isEmpty()) {
      state.update { it.copy(message = "Select at most $MAX_BATCH_IMAGES images. Already selected images are kept.") }; return
    }
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      state.update { it.copy(isLoadingImages = true, message = null) }
      try {
        val outcome = imageRepository.inspectImages(accepted)
        state.update { current ->
          current.copy(selectedImages = (current.selectedImages + outcome.images).distinctBy { it.id },
            screen = when {
              current.screen == AppScreen.PREVIEW -> AppScreen.PREVIEW
              outcome.images.isNotEmpty() -> AppScreen.COMPRESSION_SETTINGS
              else -> current.screen
            },
            message = buildString {
              append("${outcome.images.size} images added.")
              if (requested.size > accepted.size) append(" Only the first $MAX_BATCH_IMAGES selections are kept.")
              if (outcome.errors.isNotEmpty()) append(" ${outcome.errors.size} could not be opened. ${outcome.errors.first()}")
            })
        }
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { state.update { it.copy(message = userMessage(failure, "Unable to read the selected images.")) } }
      finally { state.update { it.copy(isLoadingImages = false) } }
    }
  }
  fun removeSelectedImage(image: SelectedImage) {
    if (!state.value.isCompressing && !state.value.isLoadingImages) state.update { current ->
      current.copy(selectedImages = current.selectedImages.filterNot { it.id == image.id })
    }
  }
  fun clearSelectedImages() {
    if (!state.value.isCompressing && !state.value.isLoadingImages) state.update { it.copy(selectedImages = emptyList()) }
  }
}
