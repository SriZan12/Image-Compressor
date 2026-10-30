package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.userMessage
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GallerySaveFeature(private val state: MutableStateFlow<ImageCompressorUiState>,
  private val imageRepository: ImageRepository, private val scope: CoroutineScope,
  private val showMessage: (String) -> Unit) {
  fun saveToGallery(image: CompressedImage) = save(listOf(image))
  fun saveAllToGallery() = save(state.value.compressedImages.filter { it.savedUri == null })
  private fun save(images: List<CompressedImage>) {
    if (state.value.isSaving || state.value.isInitializing || state.value.initializationError != null) return
    if (images.isEmpty()) { showMessage("All available results are already saved."); return }
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      state.update { it.copy(isSaving = true, saveErrors = emptyMap(), message = null) }
      var savedCount = 0
      try {
        for (image in images) {
          try {
            val saved = imageRepository.saveToGallery(image)
            savedCount++
            state.update { current -> current.copy(
              compressedImages = current.compressedImages.map { if (it.historyId == saved.historyId) saved else it },
              compareImage = current.compareImage?.let { if (it.historyId == saved.historyId) saved else it }) }
          } catch (cancelled: CancellationException) { throw cancelled }
          catch (failure: Exception) { state.update {
            it.copy(saveErrors = it.saveErrors + (image.historyId to userMessage(failure, "Unable to save this image. Check available storage and retry.")))
          } }
        }
        val failed = state.value.saveErrors.size
        showMessage(if (failed == 0) "Saved to Pictures / Image Compressor."
          else "$savedCount saved. $failed could not be saved. Please try again.")
      } finally { state.update { it.copy(isSaving = false) } }
    }
  }
}
