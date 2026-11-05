package com.example.imagecompressor.play

import com.example.imagecompressor.data.model.BatchStatus
import com.example.imagecompressor.data.model.ItemStatus
import com.example.imagecompressor.play.update.WorkSafety
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.ui.state.ImageCompressorUiState

/** One safety definition for Play dialogs, restart controls, and the review pause across routes. */
fun ImageCompressorUiState.toPlayWorkSafety(): WorkSafety {
  val activeBatch = batch?.let { snapshot ->
    snapshot.status == BatchStatus.RUNNING || snapshot.items.any { it.status == ItemStatus.RUNNING }
  } == true
  val unfinishedBatch = batch?.let { snapshot ->
    snapshot.status != BatchStatus.COMPLETED || snapshot.items.any {
      it.status != ItemStatus.COMPLETED || it.result?.savedUri == null
    }
  } == true
  val openSelection = selectedImages.isNotEmpty() &&
    (screen !in setOf(AppScreen.HOME, AppScreen.SETTINGS) || !hasDurablySavedSelection())
  val editorHasUnsavedResult = editorRetained?.let { it.image.savedUri == null } == true ||
    (editorPreview as? EditorPreview.Ready)?.let { it.image.savedUri == null } == true
  return WorkSafety(
    isBusy = isInitializing || initializationError != null || isCompressing || isCancelling ||
      isSaving || isLoadingImages || isExternalFlowActive || isPreparingShare || isEditorProcessing ||
      editorAction != null || editorPreview is EditorPreview.Loading ||
      editorPreview is EditorPreview.Updating || activeBatch,
    hasPendingWork = openSelection || compressedImages.any { it.savedUri == null } ||
      unfinishedBatch || editorHasUnsavedResult,
  )
}

/**
 * Home is a natural pause after saving. Production deliberately retains the selection and receipt;
 * a review must not require discarding that work. Open editor routes still prevent restart.
 */
fun ImageCompressorUiState.isNaturalPlayPause(): Boolean {
  if (screen != AppScreen.HOME || toPlayWorkSafety().isBusy || message != null ||
    editorExportError != null || saveErrors.isNotEmpty()) return false
  if (compressedImages.any { it.savedUri == null }) return false
  val completedBatch = batch?.takeIf { snapshot ->
    snapshot.status == BatchStatus.COMPLETED && snapshot.items.all { item ->
      item.status == ItemStatus.COMPLETED && item.result?.savedUri != null
    }
  }
  if (batch != null && completedBatch == null) return false
  if (editorRetained?.image?.savedUri == null && editorRetained != null) return false
  if ((editorPreview as? EditorPreview.Ready)?.image?.savedUri == null && editorPreview is EditorPreview.Ready) return false
  return hasDurablySavedSelection()
}

/** Compare full source metadata and settings; an old saved receipt cannot complete a new edit. */
private fun ImageCompressorUiState.hasDurablySavedSelection(): Boolean {
  if (selectedImages.isEmpty()) return true
  val savedEditor = editorRetained?.let { receipt ->
    receipt.settings == compressionSettings && receipt.image.original == selectedImages.singleOrNull() &&
      receipt.image.savedUri != null && receipt.image.historyId > 0
  } == true
  val savedBatch = batch?.let { snapshot ->
    snapshot.status == BatchStatus.COMPLETED && snapshot.settings == compressionSettings &&
      snapshot.items.map { it.image }.toSet() == selectedImages.toSet() &&
      snapshot.items.all { item ->
        item.status == ItemStatus.COMPLETED && item.result?.let { result ->
          result.original == item.image && result.savedUri != null && result.historyId > 0
        } == true
      }
  } == true
  return savedEditor || savedBatch
}
