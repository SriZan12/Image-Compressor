package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GallerySaveFeature(
    private val state: MutableStateFlow<ImageCompressorUiState>,
    private val imageRepository: ImageRepository,
    private val scope: CoroutineScope,
    private val showMessage: (String) -> Unit,
    private val onSuccessfulSaves: (Int) -> Unit = {},
) {
  fun saveToGallery(image: CompressedImage) {
    val images = reserveSave { current ->
      // A card's callback can still contain the image from before its save completed.
      val latest = current.compressedImages.find { it.outputFilePath == image.outputFilePath }
        ?: current.compareImage?.takeIf { it.outputFilePath == image.outputFilePath }
      when {
        latest == null -> emptyList()
        latest.savedUri != null -> emptyList()
        else -> listOf(latest)
      }
    } ?: return
    if (images.isEmpty()) {
      showMessage("This image is already saved or is no longer selected.")
      return
    }
    save(images, "Saved to your gallery.", "Unable to save the image.")
  }

  fun saveAllToGallery() {
    val images = reserveSave { current ->
      current.compressedImages.filter { it.savedUri == null }.distinctBy { it.outputFilePath }
    } ?: return
    if (images.isEmpty()) {
      showMessage("All compressed images are already saved.")
      return
    }
    save(images, "Saved all images to your gallery.", "Unable to save every image.")
  }

  /** Reserve synchronously, before dispatching work, so repeated taps cannot overlap saves. */
  private fun reserveSave(
    selectImages: (ImageCompressorUiState) -> List<CompressedImage>,
  ): List<CompressedImage>? {
    while (true) {
      val current = state.value
      if (current.isSaving || current.isCompressing) return null
      val images = selectImages(current)
      if (images.isEmpty()) return images
      if (state.compareAndSet(current, current.copy(isSaving = true, message = null))) return images
    }
  }

  private fun save(images: List<CompressedImage>, successMessage: String, failureMessage: String) {
    val job = scope.launch {
      try {
        images.forEach { image ->
          ensureActive()
          val saved = imageRepository.saveToGallery(image)
          checkNotNull(saved.savedUri) { failureMessage }
          // Publish every success before the next write. A partial batch can then be retried
          // without duplicating images that were already saved.
          state.update { current ->
            current.copy(
              compressedImages = current.compressedImages.map {
                if (it.outputFilePath == saved.outputFilePath) saved else it
              },
              compareImage = current.compareImage?.let {
                if (it.outputFilePath == saved.outputFilePath) saved else it
              },
            )
          }
          try {
            onSuccessfulSaves(1)
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            // Optional engagement bookkeeping must never turn a successful save into an error.
          }
        }
        state.update { it.copy(message = successMessage) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (exception: Exception) {
        state.update { it.copy(message = exception.message ?: failureMessage) }
      }
    }
    // One completion path also covers a scope cancelled before the coroutine starts.
    job.invokeOnCompletion { state.update { it.copy(isSaving = false) } }
  }
}
