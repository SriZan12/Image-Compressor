package com.example.imagecompressor.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.preferences.AppPreferencesRepository
import com.example.imagecompressor.ui.ImageCompressorViewModel
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.ui.state.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WorkflowTest {
  private fun image(name: String) = SelectedImage(Uri.parse("content://photos/$name"), name, 1000, 10, 10, "JPEG")
  private fun output(id: Long) = CompressedImage(image("$id"), Uri.parse("content://outputs/$id"), "/result-$id", 500, 10, 10, OutputFormat.JPEG, historyId = id)

  @Test fun compressionSnapshotsSettingsRejectsDuplicateJobsAndPropagatesCancellation() = runTest {
    val repo = FakeRepository()
    val entered = CompletableDeferred<Unit>()
    repo.compress = { _, settings ->
      repo.quality = settings.quality
      entered.complete(Unit)
      try { awaitCancellation() } finally { repo.cancelled = true }
    }
    val state = MutableStateFlow(ImageCompressorUiState(AppScreen.PREVIEW, selectedImages = listOf(image("one")), isInitializing = false,
      compressionSettings = CompressionSettings(quality = 73)))
    val feature = ImageCompressionFeature(state, repo, backgroundScope, {})
    feature.compressSelectedImages()
    state.update { it.copy(compressionSettings = CompressionSettings(quality = 20)) }
    feature.compressSelectedImages()
    runCurrent()
    assertTrue(entered.isCompleted)
    assertEquals(1, repo.compressionCalls)
    assertEquals(73, repo.quality)
    feature.cancel()
    runCurrent()
    assertTrue(repo.cancelled)
    assertFalse(state.value.isCompressing)
    assertNull(state.value.message)
  }

  @Test fun savingUpdatesEachSuccessBeforeLaterFailureAndRejectsDuplicateClicks() = runTest {
    val repo = FakeRepository()
    val gate = CompletableDeferred<Unit>()
    repo.save = { image ->
      if (image.historyId == 2L) { gate.await(); throw IOException("Storage is full.") }
      image.copy(savedUri = Uri.parse("content://gallery/${image.historyId}"))
    }
    val state = MutableStateFlow(ImageCompressorUiState(AppScreen.RESULTS, compressedImages = listOf(output(1), output(2)), isInitializing = false))
    val feature = GallerySaveFeature(state, repo, backgroundScope) { message -> state.update { it.copy(message = message) } }
    feature.saveAllToGallery()
    feature.saveAllToGallery()
    runCurrent()
    assertNotNull(state.value.compressedImages.first().savedUri)
    assertEquals(2, repo.saveCalls)
    gate.complete(Unit)
    runCurrent()
    assertFalse(state.value.isSaving)
    assertEquals(setOf(2L), state.value.saveErrors.keys)
    assertNotNull(state.value.compressedImages.first().savedUri)
    feature.saveAllToGallery()
    runCurrent()
    assertEquals(3, repo.saveCalls) // Retry only the item that still has no saved URI.
  }

  @Test fun repeatedPickerSelectionsRespectThirtyItemLimitAndKeepReadableFiles() = runTest {
    val repo = FakeRepository()
    repo.inspect = { uris -> SelectionResult(uris.filterNot { it.lastPathSegment == "bad" }.map { image(it.lastPathSegment!!) }, listOf("Unreadable image")) }
    val state = MutableStateFlow(ImageCompressorUiState(AppScreen.HOME, selectedImages = (1..29).map { image("$it") }, isInitializing = false))
    val feature = ImageSelectionFeature(state, repo, backgroundScope)
    feature.addSelectedImages(listOf(image("30").uri, image("31").uri))
    runCurrent()
    assertEquals(30, state.value.selectedImages.size)
    assertEquals(AppScreen.COMPRESSION_SETTINGS, state.value.screen)
    feature.addSelectedImages(listOf(image("32").uri))
    assertEquals(30, state.value.selectedImages.size)
    assertTrue(state.value.message!!.contains("at most 30"))
  }

  @Test fun addingPhotosWhileEditingKeepsTheSelectionScreenAndPickerCancellationKeepsTheDraft() = runTest {
    val state = MutableStateFlow(ImageCompressorUiState(AppScreen.PREVIEW, selectedImages = listOf(image("one")), isInitializing = false))
    val feature = ImageSelectionFeature(state, FakeRepository(), backgroundScope)
    feature.addSelectedImages(listOf(image("two").uri))
    runCurrent()
    assertEquals(AppScreen.PREVIEW, state.value.screen)
    assertEquals(listOf("one", "two"), state.value.selectedImages.map { it.displayName })
    val beforeCancel = state.value
    feature.addSelectedImages(emptyList())
    runCurrent()
    assertEquals(beforeCancel, state.value)
  }

  @Test fun unreadablePickerResultDoesNotNavigateAwayFromTheCurrentScreen() = runTest {
    val repo = FakeRepository().apply { inspect = { SelectionResult(emptyList(), listOf("Photo unavailable")) } }
    val state = MutableStateFlow(ImageCompressorUiState(AppScreen.HOME, selectedImages = listOf(image("kept")), isInitializing = false))
    ImageSelectionFeature(state, repo, backgroundScope).addSelectedImages(listOf(image("missing").uri))
    runCurrent()
    assertEquals(AppScreen.HOME, state.value.screen)
    assertEquals(listOf("kept"), state.value.selectedImages.map { it.displayName })
    assertTrue(state.value.message!!.contains("Photo unavailable"))
  }

  @Test fun interruptedBatchOverridesStaleScreenAndRestoresItsSettingsAndResults() = runTest {
    val repo = FakeRepository()
    val batch = BatchSnapshot("batch", CompressionSettings(quality = 61), BatchStatus.INTERRUPTED,
      listOf(BatchItem("one", image("one"), ItemStatus.COMPLETED, output(1)), BatchItem("two", image("two"), ItemStatus.INTERRUPTED)))
    val handle = SavedStateHandle(mapOf("screen" to AppScreen.HOME.name, "settings" to SettingsCodec.encode(CompressionSettings(quality = 90))))
    val restored = restoreSession(handle, batch, repo, true)
    assertEquals(AppScreen.RESULTS, restored.screen)
    assertEquals(61, restored.settings.quality)
    assertEquals(2, restored.selection.size)
    assertTrue(restored.message!!.contains("interrupted"))
  }

  @Test fun recreationPreservesNewSelectionAndSettingsRatherThanReplacingThemWithAnOlderCompletedBatch() = runTest {
    val repo = FakeRepository()
    val batch = BatchSnapshot("old", CompressionSettings(quality = 80), BatchStatus.COMPLETED,
      listOf(BatchItem("old", image("old"), ItemStatus.COMPLETED, output(1))))
    val settings = CompressionSettings(quality = 60, allowTargetResize = false, outputFormat = OutputFormat.WEBP)
    val saved = SavedStateHandle(mapOf("screen" to AppScreen.COMPRESSION_SETTINGS.name,
      "selection" to arrayListOf(image("new").uri.toString()), "settings" to SettingsCodec.encode(settings)))
    val restored = restoreSession(saved, batch, repo, true)
    assertEquals(listOf("new"), restored.selection.map { it.displayName })
    assertEquals(settings, restored.settings)
    assertEquals(AppScreen.COMPRESSION_SETTINGS, restored.screen)
  }

  @Test fun comparisonRestoresByHistoryIdAndExpiredSelectionGetsAnActionableMessage() = runTest {
    val repo = FakeRepository()
    repo.result = output(7)
    repo.inspect = { SelectionResult(emptyList(), listOf("Revoked permission")) }
    val saved = SavedStateHandle(mapOf("screen" to AppScreen.IMAGE_COMPARE.name,
      "compareId" to 7L, "selection" to arrayListOf(image("gone").uri.toString())))
    val restored = restoreSession(saved, null, repo, true)
    assertEquals(7L, restored.compare!!.historyId)
    assertEquals(AppScreen.IMAGE_COMPARE, restored.screen)
    assertTrue(restored.message!!.contains("Select them again"))
  }

  @Test fun startupFailureBlocksWorkUntilExplicitRetryRestoresTheSession() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val repo = FakeRepository().apply { initializeFailure = IOException("Storage is unavailable.") }
    val preferences = object : AppPreferencesRepository {
      override val onboardingComplete = MutableStateFlow(true)
      override val themePreference = MutableStateFlow(ThemePreference.SYSTEM)
      override val dynamicColor = MutableStateFlow(true)
      override var lastCompressionSettings = CompressionSettings()
      override fun setDynamicColor(enabled: Boolean) { dynamicColor.value = enabled }
      override fun rememberCompressionSettings(settings: CompressionSettings) { lastCompressionSettings = settings }
      override fun setOnboardingComplete() { onboardingComplete.value = true }
      override fun setThemePreference(preference: ThemePreference) { themePreference.value = preference }
    }
    val store = ViewModelStore()
    try {
      val vm = ImageCompressorViewModel(RuntimeEnvironment.getApplication(), SavedStateHandle(), repo, preferences)
      store.put("test", vm)
      runCurrent()
      assertFalse(vm.uiState.value.isInitializing)
      assertEquals("Storage is unavailable.", vm.uiState.value.initializationError)
      vm.addSelectedImages(listOf(image("blocked").uri))
      runCurrent()
      assertTrue(vm.uiState.value.selectedImages.isEmpty())
      repo.initializeFailure = null
      vm.refreshAvailability()
      runCurrent()
      assertNull(vm.uiState.value.initializationError)
      vm.addSelectedImages(listOf(image("readable").uri))
      runCurrent()
      assertEquals(listOf("readable"), vm.uiState.value.selectedImages.map { it.displayName })
    } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
  }

  @Test fun comparisonReturnsToItsOriginAndBusyNavigationIsBlocked() {
    val state = MutableStateFlow(ImageCompressorUiState(AppScreen.HISTORY, isInitializing = false))
    val navigation = NavigationFeature(state) {}
    navigation.openImageCompare(output(1))
    navigation.goBack()
    assertEquals(AppScreen.HISTORY, state.value.screen)
    navigation.navigateTo(AppScreen.RESULTS)
    navigation.openImageCompare(output(1))
    navigation.goBack()
    assertEquals(AppScreen.RESULTS, state.value.screen)
    state.update { it.copy(isCompressing = true) }
    navigation.goBack()
    assertEquals(AppScreen.RESULTS, state.value.screen)
  }

  private class FakeRepository : ImageRepository {
    override val history = MutableStateFlow(emptyList<CompressionHistoryEntity>())
    override val batch = MutableStateFlow<BatchSnapshot?>(null)
    var compressionCalls = 0; var quality = 0; var cancelled = false; var saveCalls = 0
    var result: CompressedImage? = null
    var initializeFailure: Exception? = null
    var compress: suspend (List<SelectedImage>, CompressionSettings) -> Unit = { _, _ -> }
    var save: suspend (CompressedImage) -> CompressedImage = { it }
    var inspect: suspend (List<Uri>) -> SelectionResult = { uris -> SelectionResult(uris.map { SelectedImage(it, it.lastPathSegment!!, 1000, 10, 10, "JPEG") }, emptyList()) }
    override suspend fun initialize() { initializeFailure?.let { throw it } }
    override suspend fun refreshAvailability() { }
    override suspend fun inspectImages(uris: List<Uri>) = inspect(uris)
    override suspend fun compressImages(images: List<SelectedImage>, settings: CompressionSettings) { compressionCalls++; compress(images, settings) }
    override suspend fun retryBatch() { }
    override suspend fun dismissBatch() { }
    override suspend fun saveToGallery(image: CompressedImage): CompressedImage { saveCalls++; return save(image) }
    override suspend fun getResult(id: Long) = result
    override suspend fun shareUri(id: Long): Uri = error("Not used")
    override suspend fun deleteHistory(item: CompressionHistoryEntity) { }
    override suspend fun clearHistory() { }
  }
}
