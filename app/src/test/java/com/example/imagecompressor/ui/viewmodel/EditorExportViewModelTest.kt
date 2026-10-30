package com.example.imagecompressor.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.data.preferences.AppPreferencesRepository
import com.example.imagecompressor.ui.ImageCompressorViewModel
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.EditorAction
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.util.TemporaryImageProcessor
import com.example.imagecompressor.util.TemporaryPreview
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercises actual ViewModel/session wiring; durable storage and the native encoder are fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EditorExportViewModelTest {
  @Test fun saveUsesTheExactDisplayedCandidateAndSurvivesPauseWithoutEncodingAgain() = runTest {
    withHarness {
      preparePhoto(this@runTest)
      val displayed = ready().image
      vm.saveEditorPhoto()
      runCurrent()

      assertEquals(listOf(displayed), repository.retainedCandidates)
      val saved = ready().image
      assertTrue(saved.historyId > 0)
      assertEquals(displayed.compressedSizeBytes, saved.compressedSizeBytes)
      assertEquals(displayed.original, saved.original)
      assertEquals(listOf(saved.historyId), repository.saveCalls)
      assertEquals(gallery(saved.historyId), saved.savedUri)
      assertEquals("Saved to Pictures / Image Compressor.", vm.uiState.value.message)
      assertEquals(saved.historyId, savedState.get<Long>("editorResultId"))
      assertEquals(vm.uiState.value.compressionSettings,
        SettingsCodec.decode(savedState["editorResultSettings"]))
      assertEquals(1, repository.history.value.size)
      assertEquals(0, repository.batchWrites)
      assertTrue(processor.disposed.contains(displayed.outputUri))

      vm.setEditorResumed(false)
      runCurrent()
      assertEquals(saved, ready().image)
      vm.setEditorResumed(true)
      advanceTimeBy(1_000)
      runCurrent()
      assertEquals(saved, ready().image)
      assertEquals(1, processor.prepared.size)
      vm.saveEditorPhoto() // Saved is idempotent even if invoked outside the disabled button.
      runCurrent()
      assertEquals(listOf(saved.historyId), repository.saveCalls)
    }
  }

  @Test fun shareReusesTheSavedReceiptAfterTheShareSheetPausesTheApp() = runTest {
    withHarness {
      preparePhoto(this@runTest)
      vm.saveEditorPhoto()
      runCurrent()
      val saved = ready().image
      var shared: Uri? = null
      vm.shareEditorPhoto { uri -> shared = uri; vm.setEditorResumed(false) }
      runCurrent()
      assertEquals(saved.outputUri, shared)
      assertEquals(listOf(saved.historyId), repository.shareCalls)
      assertEquals(saved, ready().image)
      assertEquals(gallery(saved.historyId), ready().image.savedUri)
      assertEquals(1, repository.retainedCandidates.size)
      assertEquals(1, repository.saveCalls.size)

      vm.setEditorResumed(true)
      advanceTimeBy(1_000)
      runCurrent()
      assertEquals(1, processor.prepared.size)
      assertEquals(saved, ready().image)
      assertEquals(1, repository.history.value.size)
    }
  }

  @Test fun shareThenSaveKeepsOneHistoryRecordAndOneEncoding() = runTest {
    withHarness {
      preparePhoto(this@runTest)
      var shared: Uri? = null
      vm.shareEditorPhoto { shared = it; vm.setEditorResumed(false) }
      runCurrent()
      val receipt = ready().image
      assertEquals(receipt.outputUri, shared)
      assertNull(receipt.savedUri)
      assertTrue(repository.saveCalls.isEmpty())

      vm.setEditorResumed(true)
      runCurrent()
      vm.saveEditorPhoto()
      runCurrent()
      assertEquals(receipt.historyId, ready().image.historyId)
      assertEquals(gallery(receipt.historyId), ready().image.savedUri)
      assertEquals(1, processor.prepared.size)
      assertEquals(1, repository.retainedCandidates.size)
      assertEquals(1, repository.history.value.size)
      assertEquals(listOf(receipt.historyId), repository.saveCalls)
      assertEquals(0, repository.batchWrites)
    }
  }

  @Test fun aValidSavedReceiptRestoresBeforeTheStateWriterAndSkipsTheEncoder() = runTest {
    val source = photo("first")
    val settings = CompressionSettings(quality = 63, targetBytes = 500_000)
    val receipt = durable(source, id = 42).copy(savedUri = gallery(42))
    val repository = RecordingRepository().apply { seed(receipt) }
    val state = savedReceiptState(source, settings, receipt.historyId)
    withHarness(repository, state) {
      assertEquals(AppScreen.HOME, vm.uiState.value.screen)
      vm.navigateTo(AppScreen.COMPRESSION_SETTINGS)
      vm.setEditorResumed(true)
      runCurrent()
      advanceTimeBy(1_000)
      runCurrent()
      assertEquals(AppScreen.COMPRESSION_SETTINGS, vm.uiState.value.screen)
      assertEquals(settings, vm.uiState.value.compressionSettings)
      assertEquals(receipt, ready().image)
      assertEquals(receipt.historyId, savedState.get<Long>("editorResultId"))
      assertEquals(SettingsCodec.encode(settings), savedState.get<String>("editorResultSettings"))
      assertTrue(processor.prepared.isEmpty())
      assertTrue(repository.retainedCandidates.isEmpty())
      assertTrue(repository.saveCalls.isEmpty())

      var shared: Uri? = null
      vm.shareEditorPhoto { shared = it }
      runCurrent()
      assertEquals(receipt.outputUri, shared)
      assertEquals(listOf(receipt.historyId), repository.shareCalls)
      assertTrue(processor.prepared.isEmpty())
    }
  }

  @Test fun aMissingRestoredReceiptRecomputesWithoutClaimingTheOldFileIsSaved() = runTest {
    val source = photo("first")
    val settings = CompressionSettings(quality = 63, targetBytes = 500_000)
    withHarness(savedState = savedReceiptState(source, settings, 42)) {
      assertNull(vm.uiState.value.editorRetained)
      assertNull(savedState.get<Long>("editorResultId"))
      assertEquals(AppScreen.HOME, vm.uiState.value.screen)
      vm.navigateTo(AppScreen.COMPRESSION_SETTINGS)
      vm.setEditorResumed(true)
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(1, processor.prepared.size)
      assertEquals(source, processor.prepared.single().first)
      assertEquals(settings, processor.prepared.single().second)
      assertEquals(0L, ready().image.historyId)
      assertNull(ready().image.savedUri)
      assertTrue(repository.retainedCandidates.isEmpty())
      assertTrue(repository.history.value.isEmpty())
      assertEquals(0, repository.batchWrites)
    }
  }

  @Test fun aGalleryOnlyReceiptCannotRestoreOldBytesAsAVerifiedEditorResult() = runTest {
    val source = photo("first")
    val settings = CompressionSettings(quality = 63, targetBytes = 500_000)
    val galleryOnly = durable(source, id = 42).copy(outputFilePath = "",
      outputUri = gallery(42), savedUri = gallery(42))
    val repository = RecordingRepository().apply { seed(galleryOnly) }
    withHarness(repository, savedReceiptState(source, settings, galleryOnly.historyId)) {
      assertNull(vm.uiState.value.editorRetained)
      assertEquals(AppScreen.HOME, vm.uiState.value.screen)
      vm.navigateTo(AppScreen.COMPRESSION_SETTINGS)
      vm.setEditorResumed(true)
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(1, processor.prepared.size)
      assertEquals(0L, ready().image.historyId)
      assertNull(ready().image.savedUri)
      assertNotEquals(galleryOnly.outputUri, ready().image.outputUri)
      assertEquals(galleryOnly.historyId, repository.history.value.single().id)
      assertTrue(repository.retainedCandidates.isEmpty())
      assertEquals(0, repository.batchWrites)
    }
  }

  @Test fun laggingHistoryListsCannotEraseOrDowngradeAnAuthoritativeSavedReceipt() = runTest {
    withHarness {
      preparePhoto(this@runTest)
      vm.saveEditorPhoto()
      runCurrent()
      val receipt = ready().image
      val observedRow = vm.uiState.value.history.single()
      assertEquals(receipt.historyId, observedRow.id)
      assertNotNull(observedRow.savedPath)

      // Simulate an old Room list arriving after retention/save returned. getResult still knows
      // the authoritative durable copy. This must also work after a newer row was already seen.
      repository.history.value = emptyList()
      runCurrent()
      assertEquals(receipt, ready().image)
      assertEquals(receipt, vm.uiState.value.editorRetained!!.image)
      repository.history.value = listOf(observedRow.copy(savedPath = null, availability = "RETAINED"))
      runCurrent()
      assertEquals(receipt, ready().image)
      assertEquals(gallery(receipt.historyId), ready().image.savedUri)
      advanceTimeBy(1_000)
      runCurrent()
      assertEquals(1, processor.prepared.size)
      assertEquals(receipt.historyId, savedState.get<Long>("editorResultId"))
      assertEquals(1, repository.retainedCandidates.size)
      assertEquals(1, repository.saveCalls.size)
    }
  }

  @Test fun changedSettingsAndSourceInvalidateTheReceiptAndPrepareFreshCandidates() = runTest {
    withHarness {
      preparePhoto(this@runTest)
      vm.saveEditorPhoto()
      runCurrent()
      val savedId = ready().image.historyId
      vm.updateQuality(47)
      assertNull(vm.uiState.value.editorRetained)
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(2, processor.prepared.size)
      assertEquals(47, ready().settings.quality)
      assertEquals(0L, ready().image.historyId)
      assertNull(ready().image.savedUri)
      assertNull(savedState.get<Long>("editorResultId"))
      assertEquals(savedId, repository.history.value.single().id)

      vm.clearSelectedImages()
      vm.addSelectedImages(listOf(photo("second").uri))
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertNull(vm.uiState.value.editorRetained)
      assertEquals(photo("second"), ready().image.original)
      assertEquals(3, processor.prepared.size)
      assertEquals(1, repository.retainedCandidates.size)
      assertEquals(listOf(savedId), repository.saveCalls)
      assertEquals(0, repository.batchWrites)
    }
  }

  @Test fun savingBlocksConflictingEditsSelectionNavigationAndDuplicateExports() = runTest {
    withHarness {
      preparePhoto(this@runTest)
      val gate = CompletableDeferred<Unit>()
      repository.beforeSave = { gate.await() }
      vm.saveEditorPhoto()
      runCurrent()
      val before = vm.uiState.value
      assertTrue(before.isSaving)
      assertEquals(EditorAction.SAVE, before.editorAction)
      assertNotNull(before.editorRetained)

      var shared = false
      vm.updateQuality(20)
      vm.addSelectedImages(listOf(photo("second").uri))
      vm.removeSelectedImage(before.selectedImages.single())
      vm.clearSelectedImages()
      vm.navigateTo(AppScreen.HOME)
      vm.saveEditorPhoto()
      vm.shareEditorPhoto { shared = true }
      vm.setEditorResumed(false)
      vm.setEditorResumed(true)
      runCurrent()
      advanceTimeBy(500)
      runCurrent()
      assertEquals(before.compressionSettings, vm.uiState.value.compressionSettings)
      assertEquals(before.selectedImages, vm.uiState.value.selectedImages)
      assertEquals(AppScreen.COMPRESSION_SETTINGS, vm.uiState.value.screen)
      assertEquals(1, processor.prepared.size)
      assertEquals(1, repository.retainedCandidates.size)
      assertEquals(1, repository.saveCalls.size)
      assertTrue(repository.shareCalls.isEmpty())
      assertFalse(shared)

      gate.complete(Unit)
      runCurrent()
      assertFalse(vm.uiState.value.isSaving)
      assertNull(vm.uiState.value.editorAction)
      assertEquals(gallery(ready().image.historyId), ready().image.savedUri)
    }
  }

  @Test fun pausingDuringRetentionPinsTheCandidateAndDefersSharingUntilReturn() = runTest {
    withHarness {
      preparePhoto(this@runTest)
      val candidate = ready().image
      val gate = CompletableDeferred<Unit>()
      repository.beforeRetain = { gate.await() }
      var shared: Uri? = null
      vm.shareEditorPhoto { shared = it }
      runCurrent()
      assertTrue(vm.uiState.value.isSaving)
      vm.setEditorResumed(false)
      runCurrent()
      assertFalse("Export lease must outlive the editor owner", processor.disposed.contains(candidate.outputUri))
      gate.complete(Unit)
      runCurrent()
      assertNull(shared)
      assertTrue(vm.uiState.value.editorExportError!!.contains("Tap Share"))
      val receipt = ready().image
      assertTrue(receipt.historyId > 0)
      assertTrue(processor.disposed.contains(candidate.outputUri))
      assertFalse(vm.uiState.value.isSaving)

      vm.setEditorResumed(true)
      runCurrent()
      advanceTimeBy(500)
      runCurrent()
      vm.shareEditorPhoto { shared = it }
      runCurrent()
      assertEquals(receipt.outputUri, shared)
      assertNull(vm.uiState.value.editorExportError)
      assertEquals(receipt.historyId, ready().image.historyId)
      assertEquals(1, processor.prepared.size)
      assertEquals(listOf(candidate), repository.retainedCandidates)
      assertEquals(1, repository.history.value.size)
      assertTrue(repository.saveCalls.isEmpty())
    }
  }

  @Test fun aGalleryFailureKeepsTheReceiptForRetryWithoutAnotherEncodeOrHistoryRow() = runTest {
    withHarness {
      preparePhoto(this@runTest)
      repository.beforeSave = { throw IOException("Storage is full. Free some space and try again.") }
      vm.saveEditorPhoto()
      runCurrent()
      val receipt = ready().image
      assertTrue(receipt.historyId > 0)
      assertNull(receipt.savedUri)
      assertFalse(vm.uiState.value.isSaving)
      assertTrue(vm.uiState.value.editorExportError!!.contains("Storage is full"))
      assertEquals(receipt.historyId, savedState.get<Long>("editorResultId"))

      repository.beforeSave = {}
      vm.saveEditorPhoto()
      runCurrent()
      assertEquals(receipt.historyId, ready().image.historyId)
      assertEquals(gallery(receipt.historyId), ready().image.savedUri)
      assertNull(vm.uiState.value.editorExportError)
      assertEquals(listOf(receipt.historyId, receipt.historyId), repository.saveCalls)
      assertEquals(1, repository.retainedCandidates.size)
      assertEquals(1, repository.history.value.size)
      assertEquals(1, processor.prepared.size)
      assertEquals(0, repository.batchWrites)
    }
  }

  private suspend fun TestScope.withHarness(
    repository: RecordingRepository = RecordingRepository(),
    savedState: SavedStateHandle = SavedStateHandle(),
    block: suspend Harness.() -> Unit,
  ) {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val harness = Harness(repository, savedState)
    try {
      runCurrent()
      assertFalse(harness.vm.uiState.value.isInitializing)
      assertNull(harness.vm.uiState.value.initializationError)
      harness.block()
    } finally {
      harness.store.clear()
      runCurrent()
      Dispatchers.resetMain()
    }
  }

  private class Harness(val repository: RecordingRepository, val savedState: SavedStateHandle) {
    val preferences = MemoryPreferences()
    val processor = RecordingPreviewProcessor()
    val store = ViewModelStore()
    val vm = ImageCompressorViewModel(RuntimeEnvironment.getApplication(), savedState,
      repository, preferences, previewProcessor = processor).also { store.put("editor", it) }

    fun preparePhoto(scope: TestScope) {
      vm.addSelectedImages(listOf(photo("first").uri))
      vm.updateCompressionSettings(CompressionSettings(quality = 63, targetBytes = 500_000))
      vm.setEditorResumed(true)
      scope.runCurrent()
      scope.advanceTimeBy(150)
      scope.runCurrent()
      assertEquals(1, processor.prepared.size)
      assertEquals(0L, ready().image.historyId)
    }

    fun ready() = vm.uiState.value.editorPreview as EditorPreview.Ready
  }

  private class RecordingPreviewProcessor : TemporaryImageProcessor {
    val prepared = mutableListOf<Pair<SelectedImage, CompressionSettings>>()
    val disposed = mutableListOf<Uri>()
    override suspend fun preparePreview(image: SelectedImage, settings: CompressionSettings): TemporaryPreview {
      prepared += image to settings
      val number = prepared.size
      val path = "/temporary/editor/preview-$number.jpg"
      val candidate = CompressedImage(image, Uri.parse("file://$path"), path,
        123_456 + number.toLong(), image.width, image.height, settings.outputFormat,
        targetBytes = settings.targetBytes, encodingQuality = settings.quality, verifiedSha256 = "a".repeat(64))
      return TemporaryPreview(candidate) { disposed += candidate.outputUri }
    }
    override fun close() {}
  }

  private class MemoryPreferences : AppPreferencesRepository {
    override val onboardingComplete = MutableStateFlow(true)
    override val themePreference = MutableStateFlow(ThemePreference.SYSTEM)
    override val dynamicColor = MutableStateFlow(true)
    override var lastCompressionSettings = CompressionSettings()
    override fun setDynamicColor(enabled: Boolean) { dynamicColor.value = enabled }
    override fun rememberCompressionSettings(settings: CompressionSettings) { lastCompressionSettings = settings }
    override fun setOnboardingComplete() { onboardingComplete.value = true }
    override fun setThemePreference(preference: ThemePreference) { themePreference.value = preference }
  }

  private class RecordingRepository : ImageRepository {
    override val history = MutableStateFlow(emptyList<CompressionHistoryEntity>())
    override val batch = MutableStateFlow<BatchSnapshot?>(null)
    val retainedCandidates = mutableListOf<CompressedImage>()
    val saveCalls = mutableListOf<Long>()
    val shareCalls = mutableListOf<Long>()
    var batchWrites = 0
    var beforeRetain: suspend () -> Unit = {}
    var beforeSave: suspend () -> Unit = {}
    private val records = linkedMapOf<Long, CompressedImage>()
    private var nextId = 1L

    fun seed(image: CompressedImage) {
      records[image.historyId] = image
      nextId = maxOf(nextId, image.historyId + 1)
      publishHistory()
    }

    override suspend fun initialize() {}
    override suspend fun refreshAvailability() {}
    override suspend fun inspectImages(uris: List<Uri>) = SelectionResult(
      uris.map { photo(checkNotNull(it.lastPathSegment)) }, emptyList())

    override suspend fun retainPreview(image: CompressedImage): CompressedImage {
      retainedCandidates += image
      beforeRetain()
      val id = nextId++
      return image.copy(historyId = id, outputUri = retainedUri(id), outputFilePath = "/retained/$id.jpg",
        verifiedSha256 = null).also(::seed)
    }

    override suspend fun saveToGallery(image: CompressedImage): CompressedImage {
      saveCalls += image.historyId
      beforeSave()
      return checkNotNull(records[image.historyId]).copy(savedUri = gallery(image.historyId)).also(::seed)
    }

    override suspend fun getResult(id: Long) = records[id]
    override suspend fun shareUri(id: Long): Uri {
      shareCalls += id
      return checkNotNull(records[id]).outputUri
    }

    override suspend fun compressImages(images: List<SelectedImage>, settings: CompressionSettings) { batchWrites++ }
    override suspend fun retryBatch() { batchWrites++ }
    override suspend fun dismissBatch() { batchWrites++ }
    override suspend fun deleteHistory(item: CompressionHistoryEntity) { error("Editor export must not delete history") }
    override suspend fun clearHistory() { error("Editor export must not clear history") }

    private fun publishHistory() {
      history.value = records.values.map { image -> CompressionHistoryEntity(
        id = image.historyId, createdAt = image.historyId, displayName = image.original.displayName,
        originalUri = image.original.uri.toString(), compressedUri = image.outputUri.toString(),
        originalSizeBytes = image.original.sizeBytes, compressedSizeBytes = image.compressedSizeBytes,
        width = image.width, height = image.height, outputFormat = image.format.name,
        savedPath = image.savedUri?.toString(), localPath = image.outputFilePath.takeIf { it.isNotEmpty() },
        availability = if (image.savedUri == null) "RETAINED" else "EXPORTED",
        sourceWidth = image.original.width, sourceHeight = image.original.height,
        sourceFormat = image.original.format, targetBytes = image.targetBytes,
        encodingAttempts = image.encodingAttempts, encodingQuality = image.encodingQuality,
      ) }
    }
  }

  private companion object {
    fun photo(name: String) = SelectedImage(Uri.parse("content://photos/$name"), name,
      1_200_000, 4000, 3000, "JPEG")
    fun retainedUri(id: Long) = Uri.parse("content://com.example.imagecompressor.fileprovider/retained_images/result_$id.jpg")
    fun gallery(id: Long) = Uri.parse("content://media/external/images/media/$id")
    fun durable(source: SelectedImage, id: Long) = CompressedImage(source, retainedUri(id), "/retained/$id.jpg",
      123_456, source.width, source.height, OutputFormat.JPEG, historyId = id, targetBytes = 500_000)
    fun savedReceiptState(source: SelectedImage, settings: CompressionSettings, id: Long) = SavedStateHandle(mapOf(
      "screen" to AppScreen.COMPRESSION_SETTINGS.name,
      "selection" to arrayListOf(source.uri.toString()),
      "settings" to SettingsCodec.encode(settings),
      "editorResultId" to id,
      "editorResultSettings" to SettingsCodec.encode(settings),
    ))
  }
}
