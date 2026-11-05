package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.userMessage
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GallerySaveFeature(
  private val state: MutableStateFlow<ImageCompressorUiState>,
  private val imageRepository: ImageRepository,
  private val scope: CoroutineScope,
  private val showMessage: (String) -> Unit,
  private val onSuccessfulSaves: (Int) -> Unit,
) {
  // Preserve the existing trailing-message-lambda API used by workflow owners and tests.
  constructor(
    state: MutableStateFlow<ImageCompressorUiState>,
    imageRepository: ImageRepository,
    scope: CoroutineScope,
    showMessage: (String) -> Unit,
  ) : this(state, imageRepository, scope, showMessage, {})

  private val countedHistoryIds = mutableSetOf<Long>()

  fun saveToGallery(image: CompressedImage) {
    val images = reserveSave { current ->
      // UI callbacks and Room emissions may lag a durable save. Resolve the current identity,
      // rather than trusting an old card's URI or a path changed by storage reconciliation.
      val latest = current.compressedImages.find { it.historyId == image.historyId }
        ?: current.compareImage?.takeIf { it.historyId == image.historyId }
      listOfNotNull(latest?.takeIf { it.savedUri == null })
    } ?: return
    if (images.isEmpty()) {
      showMessage("This image is already saved or is no longer selected.")
      return
    }
    save(images)
  }

  fun saveAllToGallery() {
    val images = reserveSave { current ->
      current.compressedImages.filter { it.savedUri == null }.distinctBy { it.historyId }
    } ?: return
    if (images.isEmpty()) {
      showMessage("All available results are already saved.")
      return
    }
    save(images)
  }

  /** Reserve before dispatch so rapid taps cannot start overlapping writes. */
  private fun reserveSave(
    selectImages: (ImageCompressorUiState) -> List<CompressedImage>,
  ): List<CompressedImage>? {
    if (!scope.isActive) return null
    while (true) {
      val current = state.value
      if (current.isSaving || current.isCompressing || current.isLoadingImages ||
        current.isInitializing || current.initializationError != null || current.isCancelling ||
        current.editorAction != null || current.isExternalFlowActive) return null
      val images = selectImages(current)
      if (images.isEmpty()) return images
      if (state.compareAndSet(current, current.copy(isSaving = true, saveErrors = emptyMap(), message = null)))
        return images
    }
  }

  private fun save(images: List<CompressedImage>) {
    val job = scope.launch {
      var savedCount = 0
      for (image in images) {
        ensureActive()
        try {
          // The repository's durable history receipt is authoritative even if the displayed
          // batch has not received its latest Room emission. Already exported copies do not earn
          // another review success or another gallery write.
          val durable = imageRepository.getResult(image.historyId) ?: image
          check(durable.historyId == image.historyId) { "This result changed. Review it and try again." }
          val alreadySaved = durable.savedUri != null
          val saved = if (alreadySaved) durable else imageRepository.saveToGallery(durable)
          check(saved.historyId == image.historyId && saved.savedUri != null) {
            "We couldn’t confirm where this photo was saved. Please try again."
          }
          savedCount++
          // Publish each receipt before starting the next item. Partial failures retain all
          // completed items, and subsequent attempts select only results that still need a save.
          state.update { current -> current.copy(
            compressedImages = current.compressedImages.map { if (it.historyId == saved.historyId) saved else it },
            compareImage = current.compareImage?.let { if (it.historyId == saved.historyId) saved else it },
          ) }
          if (!alreadySaved && countedHistoryIds.add(saved.historyId)) {
            try {
              onSuccessfulSaves(1)
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              // Optional review bookkeeping cannot turn a confirmed gallery save into an error.
            }
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (failure: Exception) {
          state.update { current -> current.copy(saveErrors = current.saveErrors +
            (image.historyId to userMessage(failure, "Unable to save this image. Check available storage and retry."))) }
        }
      }
      val failed = state.value.saveErrors.size
      showMessage(if (failed == 0) "Saved to Pictures / Image Compressor."
        else "$savedCount saved. $failed could not be saved. Please try again.")
    }
    // This also releases a synchronous reservation if the owner is cancelled before dispatch.
    job.invokeOnCompletion { state.update { it.copy(isSaving = false) } }
  }
}
