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
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.util.TemporaryImageProcessor
import com.example.imagecompressor.util.TemporaryPreview
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Integration boundary: live editing may change preferences, never persistent image results. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiveEditorViewModelTest {
  @Test fun changingTheBatchSampleDisposesTheOldPreviewWithoutWritingResults() = runTest {
    withHarness {
      vm.addSelectedImages(listOf(photo("first").uri, photo("second").uri))
      vm.setEditorResumed(true)
      runCurrent(); advanceTimeBy(150); runCurrent()
      val old = (vm.uiState.value.editorPreview as EditorPreview.Ready).image
      vm.previewSelectedImage(photo("second"))
      runCurrent()
      assertFalse(vm.uiState.value.editorPreview is EditorPreview.Ready)
      assertTrue(processor.disposed.contains(old.outputUri))
      advanceTimeBy(150); runCurrent()
      assertEquals(photo("second"), (vm.uiState.value.editorPreview as EditorPreview.Ready).image.original)
      assertEquals(photo("second").id, savedState.get<String>("editorImageId"))
      vm.previewSelectedImage(photo("not-selected"))
      runCurrent()
      assertEquals(photo("second"), vm.uiState.value.editorSource)
      assertNoPersistentImageWrites()
    }
  }

  @Test fun recreationRestoresTheChosenBatchSampleAndRegeneratesOnlyThatPhoto() = runTest {
    withHarness(SavedStateHandle(mapOf("screen" to AppScreen.COMPRESSION_SETTINGS.name,
      "selection" to arrayListOf(photo("first").id, photo("second").id), "editorImageId" to photo("second").id))) {
      vm.setEditorResumed(true)
      runCurrent(); advanceTimeBy(150); runCurrent()
      assertEquals(photo("second"), vm.uiState.value.editorSource)
      assertEquals(listOf(photo("second")), processor.requests.map { it.image })
      assertNoPersistentImageWrites()
    }
  }

  @Test fun draggingDebouncesTheFirstPhotoWithoutCreatingPersistentResults() = runTest {
    withHarness {
      vm.addSelectedImages(listOf(photo("first").uri, photo("second").uri))
      vm.setEditorResumed(true)
      runCurrent()
      vm.updateQuality(72)
      runCurrent()
      advanceTimeBy(100)
      vm.updateQuality(41)
      vm.updateTargetBytes(500_000)
      runCurrent()
      advanceTimeBy(149)
      runCurrent()
      assertTrue(processor.requests.isEmpty())

      advanceTimeBy(1)
      runCurrent()
      assertEquals(1, processor.requests.size)
      val requested = processor.requests.single()
      assertEquals("first", requested.image.displayName)
      assertEquals(41, requested.settings.quality)
      assertEquals(500_000L, requested.settings.targetBytes)
      val ready = vm.uiState.value.editorPreview as EditorPreview.Ready
      assertEquals(requested.settings, ready.settings)
      assertEquals(requested.image, ready.image.original)
      assertEquals(0L, ready.image.historyId)
      assertEquals(requested.settings, preferences.lastCompressionSettings)
      assertNoPersistentImageWrites()
    }
  }

  @Test fun invalidDimensionsAndCustomDraftPauseWorkUntilBothAreValid() = runTest {
    withHarness {
      openEditor()
      advanceTimeBy(150)
      runCurrent()
      assertTrue(vm.uiState.value.editorPreview is EditorPreview.Ready)
      vm.updateCompressionSettings(CompressionSettings(resizeMode = ResizeMode.CUSTOM,
        customWidth = "0", customHeight = "0"))
      runCurrent()
      advanceTimeBy(300)
      runCurrent()
      assertEquals(1, processor.requests.size)
      assertFalse(vm.uiState.value.editorPreview is EditorPreview.Ready)

      vm.setEditorTargetDraftValid(false)
      vm.updateCompressionSettings(CompressionSettings(targetBytes = 200_000,
        resizeMode = ResizeMode.CUSTOM, customWidth = "120", customHeight = "90"))
      runCurrent()
      advanceTimeBy(300)
      runCurrent()
      assertEquals(1, processor.requests.size)
      assertFalse(vm.uiState.value.editorPreview is EditorPreview.Ready)

      vm.setEditorTargetDraftValid(true)
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(2, processor.requests.size)
      val ready = vm.uiState.value.editorPreview as EditorPreview.Ready
      assertEquals(200_000L, ready.settings.targetBytes)
      assertEquals("120", ready.settings.customWidth)
      assertNoPersistentImageWrites()
    }
  }

  @Test fun pausingCancelsInFlightWorkAndResumeUsesTheLatestSettings() = runTest {
    withHarness {
      processor.beforeResult = { awaitCancellation() }
      openEditor()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(1, processor.requests.size)
      vm.setEditorResumed(false)
      runCurrent()
      assertEquals(1, processor.cancelled)
      assertFalse(vm.uiState.value.editorPreview is EditorPreview.Ready)
      vm.updateQuality(56)
      runCurrent()
      advanceTimeBy(1_000)
      runCurrent()
      assertEquals(1, processor.requests.size)

      processor.beforeResult = {}
      vm.setEditorResumed(true)
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(2, processor.requests.size)
      assertEquals(56, (vm.uiState.value.editorPreview as EditorPreview.Ready).settings.quality)
      assertNoPersistentImageWrites()
    }
  }

  @Test fun inspectingAdditionalPhotosPausesLiveWorkUntilSelectionSettles() = runTest {
    withHarness {
      openEditor()
      advanceTimeBy(150)
      runCurrent()
      val inspected = CompletableDeferred<SelectionResult>()
      repository.inspect = { inspected.await() }
      vm.addSelectedImages(listOf(photo("second").uri))
      runCurrent()
      assertTrue(vm.uiState.value.isLoadingImages)
      assertFalse(vm.uiState.value.editorPreview is EditorPreview.Ready)
      vm.updateQuality(55)
      runCurrent()
      advanceTimeBy(300)
      runCurrent()
      assertEquals(1, processor.requests.size)

      inspected.complete(SelectionResult(listOf(photo("second")), emptyList()))
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertFalse(vm.uiState.value.isLoadingImages)
      assertEquals(2, vm.uiState.value.selectedImages.size)
      assertEquals(2, processor.requests.size)
      val ready = vm.uiState.value.editorPreview as EditorPreview.Ready
      assertEquals("first", ready.image.original.displayName)
      assertEquals(55, ready.settings.quality)
      assertNoPersistentImageWrites()
    }
  }

  @Test fun leavingTheEditorReleasesItsCandidateAndReturningRefreshesIt() = runTest {
    withHarness {
      openEditor()
      advanceTimeBy(150)
      runCurrent()
      val first = (vm.uiState.value.editorPreview as EditorPreview.Ready).image
      vm.navigateTo(AppScreen.HOME)
      runCurrent()
      advanceTimeBy(300)
      runCurrent()
      assertEquals(1, processor.requests.size)
      assertTrue(processor.disposed.contains(first.outputUri))
      assertFalse(vm.uiState.value.editorPreview is EditorPreview.Ready)

      vm.navigateTo(AppScreen.COMPRESSION_SETTINGS)
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(2, processor.requests.size)
      val refreshed = (vm.uiState.value.editorPreview as EditorPreview.Ready).image
      assertNotEquals(first.outputUri, refreshed.outputUri)
      assertNoPersistentImageWrites()
    }
  }

  @Test fun previewFailureCanRetryWithoutStartingAPersistentBatch() = runTest {
    withHarness {
      processor.beforeResult = { throw IOException("Unable to read this photo. Try again.") }
      openEditor()
      advanceTimeBy(150)
      runCurrent()
      assertTrue(vm.uiState.value.editorPreview is EditorPreview.Failure)
      assertEquals(AppScreen.COMPRESSION_SETTINGS, vm.uiState.value.screen)

      processor.beforeResult = {}
      vm.retryEditorPreview()
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(2, processor.requests.size)
      assertTrue(vm.uiState.value.editorPreview is EditorPreview.Ready)
      assertNoPersistentImageWrites()
    }
  }

  @Test fun finalActionCancelsPreviewAndStartsOnePersistentBatchWithAllSelectedPhotos() = runTest {
    withHarness {
      val finished = CompletableDeferred<Unit>()
      repository.compress = { finished.await() }
      processor.beforeResult = { awaitCancellation() }
      vm.addSelectedImages(listOf(photo("first").uri, photo("second").uri))
      vm.updateCompressionSettings(CompressionSettings(quality = 64, targetBytes = 500_000))
      vm.setEditorResumed(true)
      runCurrent()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(1, processor.requests.size)
      vm.compressSelectedImages()
      vm.compressSelectedImages()
      runCurrent()
      assertEquals(1, processor.cancelled)
      assertEquals(listOf("compress"), repository.writes)
      assertEquals(listOf("first", "second"), repository.compressedPhotos.map { it.displayName })
      assertEquals(64, repository.compressedSettings!!.quality)
      assertEquals(500_000L, repository.compressedSettings!!.targetBytes)
      assertEquals(AppScreen.RESULTS, vm.uiState.value.screen)
      assertTrue(vm.uiState.value.isCompressing)
      assertFalse(vm.uiState.value.editorPreview is EditorPreview.Ready)

      finished.complete(Unit)
      runCurrent()
      advanceTimeBy(1_000)
      runCurrent()
      assertFalse(vm.uiState.value.isCompressing)
      assertEquals(1, processor.requests.size)
      assertEquals(listOf("compress"), repository.writes)
    }
  }

  @Test fun clearingTheViewModelCancelsWorkAndClosesItsTemporaryProcessor() = runTest {
    withHarness {
      processor.beforeResult = { awaitCancellation() }
      openEditor()
      advanceTimeBy(150)
      runCurrent()
      assertEquals(1, processor.requests.size)
      store.clear()
      runCurrent()
      assertEquals(1, processor.cancelled)
      assertEquals(1, processor.closeCalls)
      advanceTimeBy(1_000)
      runCurrent()
      assertEquals(1, processor.requests.size)
      assertNoPersistentImageWrites()
    }
  }

  private suspend fun TestScope.withHarness(savedState: SavedStateHandle = SavedStateHandle(), block: suspend Harness.() -> Unit) {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val harness = Harness(savedState)
    try {
      runCurrent()
      assertFalse(harness.vm.uiState.value.isInitializing)
      harness.block()
    } finally {
      harness.store.clear()
      runCurrent()
      Dispatchers.resetMain()
    }
  }

  private class Harness(val savedState: SavedStateHandle) {
    val repository = RecordingRepository()
    val preferences = MemoryPreferences()
    val processor = RecordingPreviewProcessor()
    val store = ViewModelStore()
    val vm = ImageCompressorViewModel(RuntimeEnvironment.getApplication(), savedState,
      repository, preferences, previewProcessor = processor).also { store.put("editor", it) }

    fun openEditor() {
      vm.addSelectedImages(listOf(photo("first").uri))
      vm.setEditorResumed(true)
    }

    fun assertNoPersistentImageWrites() {
      assertTrue("Previewing must not create, save, or remove persistent results", repository.writes.isEmpty())
      assertTrue(repository.history.value.isEmpty())
      assertNull(repository.batch.value)
      assertTrue(vm.uiState.value.compressedImages.isEmpty())
    }
  }

  private data class PreviewRequest(val image: SelectedImage, val settings: CompressionSettings)

  private class RecordingPreviewProcessor : TemporaryImageProcessor {
    val requests = mutableListOf<PreviewRequest>()
    val disposed = mutableListOf<Uri>()
    var beforeResult: suspend () -> Unit = {}
    var cancelled = 0
    var closeCalls = 0

    override suspend fun preparePreview(image: SelectedImage, settings: CompressionSettings): TemporaryPreview {
      requests += PreviewRequest(image, settings)
      val number = requests.size
      try { beforeResult() }
      catch (cancelled: CancellationException) { this.cancelled++; throw cancelled }
      val uri = Uri.parse("content://temporary/$number")
      val output = CompressedImage(image, uri, "/temporary/$number", 123_456,
        image.width, image.height, settings.outputFormat, targetBytes = settings.targetBytes)
      return TemporaryPreview(output) { disposed += uri }
    }

    override fun close() { closeCalls++ }
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
    val writes = mutableListOf<String>()
    var compressedPhotos = emptyList<SelectedImage>()
    var compressedSettings: CompressionSettings? = null
    var compress: suspend () -> Unit = {}
    var inspect: suspend (List<Uri>) -> SelectionResult = { uris ->
      SelectionResult(uris.map { photo(checkNotNull(it.lastPathSegment)) }, emptyList())
    }
    override suspend fun initialize() {}
    override suspend fun refreshAvailability() {}
    override suspend fun inspectImages(uris: List<Uri>) = inspect(uris)
    override suspend fun compressImages(images: List<SelectedImage>, settings: CompressionSettings) {
      writes += "compress"
      compressedPhotos = images
      compressedSettings = settings
      compress()
    }
    override suspend fun retryBatch() { writes += "retry batch" }
    override suspend fun dismissBatch() { writes += "dismiss batch" }
    override suspend fun saveToGallery(image: CompressedImage): CompressedImage {
      writes += "save"
      return image
    }
    override suspend fun getResult(id: Long): CompressedImage? = null
    override suspend fun shareUri(id: Long): Uri = error("Temporary previews must not be shared")
    override suspend fun deleteHistory(item: CompressionHistoryEntity) { writes += "delete history" }
    override suspend fun clearHistory() { writes += "clear history" }
  }

  private companion object {
    fun photo(name: String) = SelectedImage(Uri.parse("content://photos/$name"), name,
      1_200_000, 4000, 3000, "JPEG")
  }
}
