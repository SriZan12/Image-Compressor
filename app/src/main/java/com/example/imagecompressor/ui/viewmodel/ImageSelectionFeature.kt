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
  fun addSelectedImages(uris: List<Uri>, replace: Boolean = false) {
    if (uris.isEmpty() || state.value.isLoadingImages || state.value.isCompressing || state.value.isSaving || state.value.isInitializing || state.value.initializationError != null) return
    val existing = if (replace) emptySet() else state.value.selectedImages.map { it.uri }.toSet()
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
        // Do not discard a working selection when the picker is cancelled or every photo fails.
        val replacing = replace && outcome.images.isNotEmpty()
        if (replacing) imageRepository.dismissBatch()
        state.update { current ->
          val selected = ((if (replacing) emptyList() else current.selectedImages) + outcome.images).distinctBy { it.id }
          current.copy(selectedImages = selected,
            editorImageId = current.editorImageId.takeIf { !replacing && selected.any { image -> image.id == it } },
            editorRetained = current.editorRetained.takeIf { outcome.images.isEmpty() },
            editorExportError = current.editorExportError.takeIf { outcome.images.isEmpty() },
            compressedImages = if (replacing) emptyList() else current.compressedImages,
            compareImage = if (replacing) null else current.compareImage,
            batch = if (replacing) null else current.batch,
            saveErrors = if (replacing) emptyMap() else current.saveErrors,
            screen = when {
              !replacing && current.screen == AppScreen.PREVIEW -> AppScreen.PREVIEW
              outcome.images.isNotEmpty() -> AppScreen.COMPRESSION_SETTINGS
              else -> current.screen
            },
            message = buildString {
              if (requested.size > accepted.size) append("Choose up to $MAX_BATCH_IMAGES photos at once. ")
              if (outcome.errors.isNotEmpty()) append("${outcome.errors.size} could not be opened. ${outcome.errors.first()}")
            }.trim().ifEmpty { null })
        }
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { state.update { it.copy(message = userMessage(failure, "Unable to read the selected images.")) } }
      finally { state.update { it.copy(isLoadingImages = false) } }
    }
  }
  fun removeSelectedImage(image: SelectedImage) {
    if (!state.value.isCompressing && !state.value.isLoadingImages && !state.value.isSaving) state.update { current ->
      current.copy(selectedImages = current.selectedImages.filterNot { it.id == image.id },
        editorImageId = current.editorImageId.takeUnless { it == image.id }, editorRetained = null, editorExportError = null)
    }
  }
  fun clearSelectedImages() {
    if (!state.value.isCompressing && !state.value.isLoadingImages && !state.value.isSaving)
      state.update { it.copy(selectedImages = emptyList(), editorImageId = null, editorRetained = null, editorExportError = null) }
  }
}
