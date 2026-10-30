package com.example.imagecompressor.ui.viewmodel

import android.net.Uri
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.userMessage
import com.example.imagecompressor.ui.editor.LiveEditorSession
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.EditorAction
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.example.imagecompressor.util.compression.CompressionPolicy
import com.example.imagecompressor.util.compression.PixelSize
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Exports the exact measured candidate once; retries use its durable history receipt. */
class EditorExportFeature(
  private val state: MutableStateFlow<ImageCompressorUiState>,
  private val repository: ImageRepository,
  private val session: LiveEditorSession,
  private val scope: CoroutineScope,
  private val isResumed: () -> Boolean,
  private val isTargetDraftValid: () -> Boolean,
) {
  fun save() = export(EditorAction.SAVE)

  fun share(onReady: (Uri) -> Unit) = export(EditorAction.SHARE, onReady)

  private fun export(action: EditorAction, onReady: ((Uri) -> Unit)? = null) {
    val snapshot = state.value
    if (!scope.isActive || !isResumed() || !isTargetDraftValid() ||
      snapshot.screen != AppScreen.COMPRESSION_SETTINGS || snapshot.isInitializing ||
      snapshot.initializationError != null || snapshot.isLoadingImages || snapshot.isCompressing ||
      snapshot.isSaving) return
    val source = snapshot.selectedImages.singleOrNull() ?: return
    val settings = snapshot.compressionSettings
    if (runCatching { CompressionPolicy.requestedSize(PixelSize(source.width, source.height), settings) }.isFailure) return
    val ready = snapshot.editorPreview as? EditorPreview.Ready ?: return
    if (ready.settings != settings || ready.image.original != source || ready.image.compressedSizeBytes <= 0) return
    val retained = ready.image.historyId > 0
    if (retained && snapshot.editorRetained != ready) {
      stalePreview()
      return
    }
    if (action == EditorAction.SAVE && retained && ready.image.savedUri != null) return

    // Acquire before the first suspension. Source/settings alone cannot identify a same-settings
    // retry that replaced the displayed file; pin the exact output path as well.
    val lease = if (retained) null else session.acquire(source, settings, ready.image.outputFilePath)
    if (!retained && (lease == null || lease.image != ready.image)) {
      lease?.close()
      stalePreview()
      return
    }
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      state.update { it.copy(isSaving = true, editorAction = action, editorExportError = null, message = null) }
      try {
        val durable = if (retained) repository.getResult(ready.image.historyId)
          ?: throw IOException("This copy is unavailable. Make a new preview and try again.")
        else repository.retainPreview(checkNotNull(lease).image)
        if (durable.outputFilePath.isBlank())
          throw IOException("This copy is no longer available. Update the preview and try again.")
        if (durable.historyId <= 0 || !durable.matchesMeasuredImage(ready.image) ||
          (retained && durable.historyId != ready.image.historyId))
          throw IOException("We couldn’t check this copy. Make a new preview and try again.")
        publishReceipt(durable, source, settings)
        if (!state.value.matches(source, settings) || !isTargetDraftValid())
          throw IOException("The photo changed. Review the current preview and try again.")

        when (action) {
          EditorAction.SAVE -> {
            val saved = if (durable.savedUri != null) durable else repository.saveToGallery(durable)
            if (saved.historyId != durable.historyId || !saved.matchesMeasuredImage(durable) || saved.savedUri == null)
              throw IOException("We couldn’t confirm where this photo was saved. Please try again.")
            publishReceipt(saved, source, settings)
            state.update { it.copy(message = "Saved to Pictures / Image Compressor.") }
          }
          EditorAction.SHARE -> {
            val uri = repository.shareUri(durable.historyId)
            val current = state.value
            if (isResumed() && current.screen == AppScreen.COMPRESSION_SETTINGS &&
              current.matches(source, settings) && current.editorRetained?.image?.historyId == durable.historyId) {
              onReady?.invoke(uri)
            } else {
              state.update { it.copy(editorExportError = "Your copy is ready. Tap Share when you return to the photo.") }
            }
          }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        val fallback = if (action == EditorAction.SAVE)
          "Unable to save this photo. Check available storage and try again."
        else "Unable to share this photo. Please try again."
        state.update { it.copy(editorExportError = userMessage(failure, fallback)) }
      } finally {
        try { lease?.close() }
        finally { state.update { it.copy(isSaving = false, editorAction = null) } }
      }
    }
  }

  private fun publishReceipt(image: CompressedImage, source: SelectedImage, settings: CompressionSettings) {
    state.update { current ->
      if (current.matches(source, settings)) {
        val receipt = EditorPreview.Ready(image, settings)
        current.copy(editorRetained = receipt, editorPreview = receipt)
      } else current
    }
  }

  private fun stalePreview() {
    state.update { it.copy(editorExportError = "This preview changed. Wait for the new photo and try again.") }
  }

  private fun ImageCompressorUiState.matches(source: SelectedImage, settings: CompressionSettings) =
    selectedImages.singleOrNull() == source && compressionSettings == settings

  // Storage locations and gallery state may change on retention/reconciliation. The measured
  // image metadata must not. The temporary fingerprint is verified by retention, not stored in Room.
  private fun CompressedImage.matchesMeasuredImage(other: CompressedImage) =
    original == other.original && compressedSizeBytes == other.compressedSizeBytes &&
      width == other.width && height == other.height && format == other.format &&
      targetBytes == other.targetBytes && adjustments == other.adjustments &&
      encodingAttempts == other.encodingAttempts && encodingQuality == other.encodingQuality
}
