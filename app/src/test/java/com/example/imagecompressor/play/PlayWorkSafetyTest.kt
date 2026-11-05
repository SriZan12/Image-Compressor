package com.example.imagecompressor.play

import android.app.Application
import android.net.Uri
import com.example.imagecompressor.data.model.BatchItem
import com.example.imagecompressor.data.model.BatchSnapshot
import com.example.imagecompressor.data.model.BatchStatus
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.ItemStatus
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.EditorAction
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayWorkSafetyTest {
  private val idle = ImageCompressorUiState(screen = AppScreen.HOME, isInitializing = false)
  private val settings = CompressionSettings()
  private val source = SelectedImage(Uri.parse("content://photos/one"), "one", 1_000, 100, 100, "JPEG")
  private val image = CompressedImage(source, Uri.parse("content://private/one"), "/private/one.jpg",
    500, 100, 100, OutputFormat.JPEG, historyId = 42)

  @Test fun initializationRestorationEditorDrainAndSharePreparationAreBusyOnEveryRoute() {
    val busyStates = listOf(
      idle.copy(isInitializing = true), idle.copy(initializationError = "Restoration failed"),
      idle.copy(isPreparingShare = true), idle.copy(isExternalFlowActive = true),
      idle.copy(isEditorProcessing = true), idle.copy(editorAction = EditorAction.SHARE),
      idle.copy(editorPreview = EditorPreview.Loading), idle.copy(editorPreview = EditorPreview.Updating()),
      idle.copy(isCancelling = true), idle.copy(isSaving = true), idle.copy(isCompressing = true),
    )
    for (state in busyStates) for (screen in listOf(AppScreen.HOME, AppScreen.SETTINGS)) {
      val routed = state.copy(screen = screen)
      assertTrue(routed.toPlayWorkSafety().isBusy)
      assertFalse(routed.toPlayWorkSafety().canRestart)
      assertFalse(routed.isNaturalPlayPause())
    }
  }

  @Test fun enteringSettingsCannotDiscardLiveEditorWorkOrAnUnsavedReceipt() {
    for (state in listOf(
      idle.copy(selectedImages = listOf(source)),
      idle.copy(editorRetained = EditorPreview.Ready(image, settings)),
      idle.copy(editorPreview = EditorPreview.Ready(image, settings)),
      idle.copy(compressedImages = listOf(image)),
    )) {
      assertTrue(state.copy(screen = AppScreen.SETTINGS).toPlayWorkSafety().hasPendingWork)
      assertFalse(state.copy(screen = AppScreen.SETTINGS).toPlayWorkSafety().canRestart)
      assertFalse(state.isNaturalPlayPause())
    }
  }

  @Test fun activeAndRecoveredUnfinishedBatchesPreventRestartWithoutTransientBusyFlags() {
    val active = BatchSnapshot("batch", settings, BatchStatus.RUNNING,
      listOf(BatchItem("one", source, ItemStatus.RUNNING)))
    assertTrue(idle.copy(batch = active).toPlayWorkSafety().isBusy)
    for (status in listOf(ItemStatus.PENDING, ItemStatus.FAILED, ItemStatus.CANCELLED, ItemStatus.INTERRUPTED)) {
      val recovered = active.copy(status = BatchStatus.INTERRUPTED,
        items = listOf(BatchItem("one", source, status)))
      val state = idle.copy(batch = recovered)
      assertTrue(state.toPlayWorkSafety().hasPendingWork)
      assertFalse(state.toPlayWorkSafety().canRestart)
      assertFalse(state.isNaturalPlayPause())
    }
  }

  @Test fun savedDurableResultsPermitExplicitRestartAndNaturalPause() {
    val saved = image.copy(savedUri = Uri.parse("content://media/42"))
    val batch = BatchSnapshot("batch", settings, BatchStatus.COMPLETED,
      listOf(BatchItem("one", source, ItemStatus.COMPLETED, saved)))
    val state = idle.copy(batch = batch, compressedImages = listOf(saved))
    assertTrue(state.toPlayWorkSafety().canRestart)
    assertTrue(state.isNaturalPlayPause())
    assertTrue(idle.isNaturalPlayPause())
  }

  @Test fun saveThenHomeCanReviewWithoutDiscardingTheCurrentEditorSession() {
    val saved = image.copy(savedUri = Uri.parse("content://media/42"))
    val receipt = EditorPreview.Ready(saved, settings)
    val completed = idle.copy(selectedImages = listOf(source), editorRetained = receipt, editorPreview = receipt)
    assertTrue(completed.isNaturalPlayPause())
    assertTrue(completed.toPlayWorkSafety().canRestart)
    assertTrue(completed.copy(screen = AppScreen.SETTINGS).toPlayWorkSafety().canRestart)
    assertFalse(completed.copy(screen = AppScreen.COMPRESSION_SETTINGS).toPlayWorkSafety().canRestart)
    assertFalse(completed.copy(compressionSettings = settings.copy(quality = 40)).toPlayWorkSafety().canRestart)
    assertFalse(completed.copy(compressionSettings = settings.copy(quality = 40)).isNaturalPlayPause())
    assertFalse(completed.copy(selectedImages = listOf(source.copy(sizeBytes = 2_000))).isNaturalPlayPause())
    assertFalse(completed.copy(editorRetained = receipt.copy(image = image)).isNaturalPlayPause())
  }

  @Test fun completedBatchMustCoverAllCurrentSelectedWorkWithSavedMatchingResults() {
    val saved = image.copy(savedUri = Uri.parse("content://media/42"))
    val snapshot = BatchSnapshot("batch", settings, BatchStatus.COMPLETED,
      listOf(BatchItem("one", source, ItemStatus.COMPLETED, saved)))
    val completed = idle.copy(selectedImages = listOf(source), batch = snapshot, compressedImages = listOf(saved))
    assertTrue(completed.isNaturalPlayPause())
    assertFalse(completed.copy(compressionSettings = settings.copy(quality = 40)).isNaturalPlayPause())
    assertFalse(completed.copy(selectedImages = listOf(source, source.copy(uri = Uri.parse("content://photos/two")))).isNaturalPlayPause())
    assertFalse(completed.copy(batch = snapshot.copy(items = listOf(BatchItem("one", source, ItemStatus.FAILED)))).isNaturalPlayPause())
    assertFalse(completed.copy(batch = snapshot.copy(items = listOf(BatchItem("one", source, ItemStatus.COMPLETED, image))))
      .isNaturalPlayPause())
  }
}
