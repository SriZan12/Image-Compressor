package com.example.imagecompressor.ui.viewmodel

import android.app.Application
import android.net.Uri
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.ui.editor.LiveEditorSession
import com.example.imagecompressor.ui.state.*
import com.example.imagecompressor.util.TemporaryImageProcessor
import com.example.imagecompressor.util.TemporaryPreview
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real session ownership and private files; repository operations are observed, never encoded. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EditorExportFeatureTest {
  @Test fun savingRetainsTheExactCandidateAndSharingReusesItsSavedReceipt() = runTest {
    withReady {
      val shown = ready().image
      val bytes = File(shown.outputFilePath).readBytes()
      feature.save()
      runCurrent()
      val receipt = checkNotNull(state.value.editorRetained)
      assertEquals(listOf(shown), repository.retainedInputs)
      assertEquals(listOf(receipt.image.historyId), repository.savedIds)
      assertArrayEquals(bytes, File(receipt.image.outputFilePath).readBytes())
      assertEquals(shown.compressedSizeBytes, receipt.image.compressedSizeBytes)
      assertEquals(settings, receipt.settings)
      assertNotNull(receipt.image.savedUri)
      assertEquals(receipt, state.value.editorPreview)
      assertEquals("Saved to Pictures / Image Compressor.", state.value.message)
      assertFalse(state.value.isSaving)

      val shares = mutableListOf<Uri>()
      feature.share(shares::add)
      runCurrent()
      feature.save() // A saved receipt must not publish another gallery copy.
      runCurrent()
      assertEquals(listOf(receipt.image.savedUri), shares)
      assertEquals(1, repository.retainedInputs.size)
      assertEquals(1, repository.savedIds.size)
      assertEquals(listOf(receipt.image.historyId), repository.sharedIds)
      assertEquals(1, processor.outputs.size)
      assertNoBatchWork()
    }
  }

  @Test fun sameSettingsWithAnOlderDisplayedPathCannotAcquireTheReplacement() = runTest {
    withReady {
      val old = ready()
      session.retry()
      runCurrent()
      val replacement = ready()
      assertEquals(old.settings, replacement.settings)
      assertNotEquals(old.image.outputFilePath, replacement.image.outputFilePath)
      state.update { it.copy(editorPreview = old) }
      feature.save()
      feature.share { fail("An obsolete preview must not be shared") }
      runCurrent()
      assertTrue(repository.retainedInputs.isEmpty())
      assertFalse(state.value.isSaving)
      assertNotNull(state.value.editorExportError)
      assertTrue(File(replacement.image.outputFilePath).exists())
      assertNoBatchWork()
    }
  }

  @Test fun duplicateSaveAndShareClicksDuringRetentionStartOnlyOneAction() = runTest {
    withReady {
      val retain = CompletableDeferred<Unit>()
      repository.beforeRetain = { retain.await() }
      feature.save()
      assertTrue(state.value.isSaving)
      assertEquals(EditorAction.SAVE, state.value.editorAction)
      feature.save()
      feature.share { fail("A duplicate action must not launch sharing") }
      assertEquals(1, repository.retainedInputs.size)
      assertTrue(repository.savedIds.isEmpty())
      retain.complete(Unit)
      runCurrent()
      assertEquals(1, repository.retainedInputs.size)
      assertEquals(1, repository.savedIds.size)
      assertTrue(repository.sharedIds.isEmpty())
      assertFalse(state.value.isSaving)
      assertNull(state.value.editorAction)
      assertNoBatchWork()
    }
  }

  @Test fun pausingDuringShareKeepsThePinnedFileAndRequiresAForegroundRetry() = runTest {
    withReady {
      val shown = ready().image
      val bytes = File(shown.outputFilePath).readBytes()
      val retain = CompletableDeferred<Unit>()
      repository.beforeRetain = { retain.await() }
      val shares = mutableListOf<Uri>()
      feature.share(shares::add)
      assertEquals(EditorAction.SHARE, state.value.editorAction)
      resumed = false
      session.request(null, settings)
      runCurrent()
      assertTrue("The export lease must outlive session suspension", File(shown.outputFilePath).exists())
      assertEquals(0, processor.outputs.single().disposals)

      retain.complete(Unit)
      runCurrent()
      val receipt = checkNotNull(state.value.editorRetained)
      assertArrayEquals(bytes, File(receipt.image.outputFilePath).readBytes())
      assertTrue(shares.isEmpty())
      assertTrue(checkNotNull(state.value.editorExportError).contains("Tap Share"))
      assertFalse(File(shown.outputFilePath).exists())
      assertEquals(1, processor.outputs.single().disposals)
      assertFalse(state.value.isSaving)

      resumed = true
      feature.share(shares::add)
      runCurrent()
      assertEquals(listOf(receipt.image.outputUri), shares)
      assertEquals(listOf(shown), repository.retainedInputs)
      assertNull(state.value.editorExportError)
      assertNoBatchWork()
    }
  }

  @Test fun failedGallerySaveRetriesTheRetainedReceiptWithoutRetainingAgain() = runTest {
    withReady {
      repository.saveFailure = IOException("Storage is full. Free some space and try again.")
      feature.save()
      runCurrent()
      val receipt = checkNotNull(state.value.editorRetained)
      assertEquals(receipt, ready())
      assertNull(receipt.image.savedUri)
      assertNotNull(state.value.editorExportError)
      assertFalse(state.value.isSaving)
      session.request(null, settings)
      runCurrent()
      assertTrue(File(receipt.image.outputFilePath).exists())

      repository.saveFailure = null
      feature.save()
      runCurrent()
      assertEquals(1, repository.retainedInputs.size)
      assertEquals(listOf(receipt.image.historyId, receipt.image.historyId), repository.savedIds)
      assertEquals(receipt.image.historyId, checkNotNull(state.value.editorRetained).image.historyId)
      assertNotNull(ready().image.savedUri)
      assertNull(state.value.editorExportError)
      assertEquals(1, processor.outputs.size)
      assertNoBatchWork()
    }
  }

  @Test fun failedShareRetriesTheSameReceiptWithoutEncodingOrRepublishing() = runTest {
    withReady {
      repository.shareFailure = IOException("This photo cannot be shared right now.")
      val shares = mutableListOf<Uri>()
      feature.share(shares::add)
      runCurrent()
      val receipt = checkNotNull(state.value.editorRetained)
      assertTrue(shares.isEmpty())
      assertNotNull(state.value.editorExportError)
      repository.shareFailure = null
      feature.share(shares::add)
      runCurrent()
      assertEquals(listOf(receipt.image.outputUri), shares)
      assertEquals(1, repository.retainedInputs.size)
      assertEquals(listOf(receipt.image.historyId, receipt.image.historyId), repository.sharedIds)
      assertTrue(repository.savedIds.isEmpty())
      assertEquals(1, processor.outputs.size)
      assertNoBatchWork()
    }
  }

  @Test fun failedRetentionLeavesTheCandidateRetryableAndReleasesItsExportLease() = runTest {
    withReady {
      val shown = ready()
      repository.beforeRetain = { throw IOException("Storage is full.") }
      feature.save()
      runCurrent()
      assertNull(state.value.editorRetained)
      assertEquals(shown, ready())
      assertTrue(File(shown.image.outputFilePath).exists())
      assertNotNull(state.value.editorExportError)

      repository.beforeRetain = {}
      feature.save()
      runCurrent()
      assertEquals(listOf(shown.image, shown.image), repository.retainedInputs)
      assertEquals(1, repository.results.size)
      session.request(null, settings)
      runCurrent()
      assertFalse("Neither the failed nor successful action may leak a lease", File(shown.image.outputFilePath).exists())
      assertEquals(1, processor.outputs.single().disposals)
      assertNoBatchWork()
    }
  }

  @Test fun invalidDraftGeometrySourceOrNonEditorStateCannotStartAnExport() = runTest {
    withReady {
      val valid = state.value
      targetDraftValid = false
      feature.save()
      feature.share { fail("Invalid target draft") }
      targetDraftValid = true
      resumed = false
      feature.save()
      feature.share { fail("Paused editor") }
      resumed = true
      val invalidSettings = settings.copy(resizeMode = ResizeMode.CUSTOM, customWidth = "0")
      val variants = listOf(
        valid.copy(compressionSettings = invalidSettings, editorPreview = ready().copy(settings = invalidSettings)),
        valid.copy(selectedImages = listOf(source.copy(sizeBytes = source.sizeBytes + 1))),
        valid.copy(compressionSettings = settings.copy(quality = 40)),
        valid.copy(selectedImages = listOf(source, source.copy(uri = Uri.parse("content://photo/second")))),
        valid.copy(screen = AppScreen.HOME),
        valid.copy(isInitializing = true),
        valid.copy(initializationError = "Storage unavailable"),
        valid.copy(isLoadingImages = true),
        valid.copy(isCompressing = true),
        valid.copy(isSaving = true),
        valid.copy(editorPreview = EditorPreview.Updating(estimatedBytes = 400_000)),
      )
      for (invalid in variants) {
        state.value = invalid
        feature.save()
        feature.share { fail("Invalid or busy state must not share") }
        runCurrent()
      }
      assertTrue(repository.retainedInputs.isEmpty())
      assertTrue(repository.savedIds.isEmpty())
      assertTrue(repository.sharedIds.isEmpty())
      state.value = valid
      session.request(null, settings)
      runCurrent()
      assertEquals(1, processor.outputs.single().disposals)
      assertNoBatchWork()
    }
  }

  @Test fun aRetainedLookingPreviewMustMatchItsFullReceipt() = runTest {
    withReady {
      val impostor = ready().copy(image = ready().image.copy(historyId = 123))
      state.update { it.copy(editorPreview = impostor) }
      feature.save()
      feature.share { fail("A history ID alone is not a receipt") }
      state.update { it.copy(editorRetained = impostor.copy(image = impostor.image.copy(compressedSizeBytes = 1))) }
      feature.save()
      runCurrent()
      assertTrue(repository.retainedInputs.isEmpty())
      assertTrue(repository.fetchedIds.isEmpty())
      assertTrue(repository.savedIds.isEmpty())
      assertTrue(repository.sharedIds.isEmpty())
      assertNotNull(state.value.editorExportError)
      assertNoBatchWork()
    }
  }

  @Test fun reloadedReceiptWithDifferentMeasuredMetadataCannotBeSaved() = runTest {
    withReady {
      feature.share {}
      runCurrent()
      val receipt = checkNotNull(state.value.editorRetained)
      val expected = receipt.image
      val replacements = listOf(
        expected.copy(outputFilePath = ""),
        expected.copy(historyId = expected.historyId + 1),
        expected.copy(original = source.copy(sizeBytes = source.sizeBytes + 1)),
        expected.copy(compressedSizeBytes = expected.compressedSizeBytes + 1),
        expected.copy(width = expected.width - 1),
        expected.copy(height = expected.height - 1),
        expected.copy(format = OutputFormat.WEBP),
        expected.copy(targetBytes = 100_000),
        expected.copy(adjustments = setOf(CompressionAdjustment.TARGET_SIZE)),
        expected.copy(encodingAttempts = expected.encodingAttempts + 1),
        expected.copy(encodingQuality = 30),
      )
      for (replacement in replacements) {
        repository.results[expected.historyId] = replacement
        feature.save()
        runCurrent()
        assertEquals(receipt, state.value.editorRetained)
        assertTrue(repository.savedIds.isEmpty())
        assertNotNull(state.value.editorExportError)
        assertFalse(state.value.isSaving)
      }
      assertEquals(1, repository.retainedInputs.size)
      assertNoBatchWork()
    }
  }

  @Test fun changedInputDuringExportCannotReceiveTheOldReceiptOrLaunchSharing() = runTest {
    withReady {
      val retain = CompletableDeferred<Unit>()
      repository.beforeRetain = { retain.await() }
      val shares = mutableListOf<Uri>()
      feature.share(shares::add)
      state.update { it.copy(selectedImages = listOf(source.copy(width = 1000))) }
      retain.complete(Unit)
      runCurrent()
      assertNull(state.value.editorRetained)
      assertTrue(shares.isEmpty())
      assertTrue(repository.sharedIds.isEmpty())
      assertEquals(1, repository.results.size)
      assertTrue(repository.results.values.all { File(it.outputFilePath).exists() })
      assertNotNull(state.value.editorExportError)
      assertNoBatchWork()
    }
  }

  @Test fun settingsChangedDuringRetentionPreventAGallerySideEffect() = runTest {
    withReady {
      val retain = CompletableDeferred<Unit>()
      repository.beforeRetain = { retain.await() }
      feature.save()
      state.update { it.copy(compressionSettings = settings.copy(quality = 42)) }
      retain.complete(Unit)
      runCurrent()
      assertNull(state.value.editorRetained)
      assertTrue(repository.savedIds.isEmpty())
      assertEquals(1, repository.results.size)
      assertNotNull(state.value.editorExportError)
      assertFalse(state.value.isSaving)
      assertNoBatchWork()
    }
  }

  private suspend fun TestScope.withReady(block: suspend Harness.() -> Unit) {
    val harness = Harness(this)
    try {
      harness.session.request(harness.source, harness.settings)
      runCurrent()
      assertTrue(harness.state.value.editorPreview is EditorPreview.Ready)
      harness.block()
    } finally {
      harness.session.close()
      runCurrent()
      harness.directory.deleteRecursively()
    }
  }

  private class Harness(scope: TestScope) {
    val directory = Files.createTempDirectory(RuntimeEnvironment.getApplication().cacheDir.toPath(), "editor-export-").toFile()
    val source = SelectedImage(Uri.parse("content://photo/original"), "original.jpg", 1_200_000, 4000, 3000, "JPEG")
    val settings = CompressionSettings(targetBytes = 500_000)
    val state = MutableStateFlow(ImageCompressorUiState(AppScreen.COMPRESSION_SETTINGS,
      selectedImages = listOf(source), compressionSettings = settings, isInitializing = false))
    val processor = FilePreviewProcessor(directory)
    val session = LiveEditorSession(scope.backgroundScope, processor, debounceMillis = 0)
    val repository = RecordingRepository(directory)
    var resumed = true
    var targetDraftValid = true
    val feature = EditorExportFeature(state, repository, session, scope.backgroundScope, { resumed }, { targetDraftValid })

    init {
      scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
        session.state.collect { preview ->
          state.update { current -> current.copy(editorPreview = current.editorRetained ?: preview) }
        }
      }
    }

    fun ready() = state.value.editorPreview as EditorPreview.Ready
    fun assertNoBatchWork() { assertEquals(0, repository.batchMutations); assertNull(repository.batch.value) }
  }

  private class OwnedOutput(val file: File) { var disposals = 0 }

  private class FilePreviewProcessor(private val directory: File) : TemporaryImageProcessor {
    val outputs = mutableListOf<OwnedOutput>()
    override suspend fun preparePreview(image: SelectedImage, settings: CompressionSettings): TemporaryPreview {
      val output = OwnedOutput(File(directory, "preview-${outputs.size}.jpg").apply {
        writeBytes(ByteArray(97) { (it + outputs.size).toByte() })
      })
      outputs += output
      val measured = CompressedImage(image, Uri.fromFile(output.file), output.file.path,
        output.file.length(), image.width, image.height, settings.outputFormat, targetBytes = settings.targetBytes)
      return TemporaryPreview(measured) { output.disposals++; output.file.delete() }
    }
    override fun close() {}
  }

  private class RecordingRepository(private val directory: File) : ImageRepository {
    override val history = MutableStateFlow(emptyList<CompressionHistoryEntity>())
    override val batch = MutableStateFlow<BatchSnapshot?>(null)
    val retainedInputs = mutableListOf<CompressedImage>()
    val savedIds = mutableListOf<Long>()
    val sharedIds = mutableListOf<Long>()
    val fetchedIds = mutableListOf<Long>()
    val results = mutableMapOf<Long, CompressedImage>()
    var batchMutations = 0
    var beforeRetain: suspend () -> Unit = {}
    var saveFailure: Exception? = null
    var shareFailure: Exception? = null

    override suspend fun retainPreview(image: CompressedImage): CompressedImage {
      retainedInputs += image
      beforeRetain()
      val source = File(image.outputFilePath)
      check(source.isFile) { "The candidate was deleted before retention could finish" }
      val id = results.size.toLong() + 1
      val retained = File(directory, "retained-$id.jpg")
      source.copyTo(retained)
      return image.copy(historyId = id, outputFilePath = retained.path,
        outputUri = Uri.parse("content://retained/$id")).also { results[id] = it }
    }
    override suspend fun saveToGallery(image: CompressedImage): CompressedImage {
      savedIds += image.historyId
      saveFailure?.let { throw it }
      return image.copy(savedUri = Uri.parse("content://gallery/${image.historyId}"))
        .also { results[it.historyId] = it }
    }
    override suspend fun shareUri(id: Long): Uri {
      sharedIds += id
      shareFailure?.let { throw it }
      return checkNotNull(results[id]).let { it.savedUri ?: it.outputUri }
    }
    override suspend fun getResult(id: Long): CompressedImage? { fetchedIds += id; return results[id] }
    override suspend fun initialize() {}
    override suspend fun refreshAvailability() {}
    override suspend fun inspectImages(uris: List<Uri>) = error("Export must not reopen the original")
    override suspend fun compressImages(images: List<SelectedImage>, settings: CompressionSettings) { batchMutations++ }
    override suspend fun retryBatch() { batchMutations++ }
    override suspend fun dismissBatch() { batchMutations++ }
    override suspend fun deleteHistory(item: CompressionHistoryEntity) { batchMutations++ }
    override suspend fun clearHistory() { batchMutations++ }
  }
}
