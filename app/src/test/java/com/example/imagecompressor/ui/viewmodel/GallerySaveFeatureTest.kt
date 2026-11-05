package com.example.imagecompressor.ui.viewmodel

import android.net.Uri
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.BatchSnapshot
import com.example.imagecompressor.data.model.SelectionResult
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.EditorAction
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class GallerySaveFeatureTest {
  @Test
  fun partialBatchCountsOnlySuccessAndRetryDoesNotSaveItAgain() {
    val first = image("first")
    val second = image("second")
    val state = state(first, second)
    var failSecond = true
    val repository = FakeRepository { image ->
      if (image.outputFilePath == "second" && failSecond) error("Storage unavailable")
      image.copy(savedUri = mock(Uri::class.java))
    }
    var successfulSaves = 0
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val feature = GallerySaveFeature(state, repository, scope, { message -> state.value = state.value.copy(message = message) }, { successfulSaves += it })

    feature.saveAllToGallery()
    assertEquals(listOf("first", "second"), repository.attemptedPaths)
    assertNotNull(state.value.compressedImages[0].savedUri)
    assertNull(state.value.compressedImages[1].savedUri)
    assertEquals(1, successfulSaves)
    assertFalse(state.value.isSaving)

    failSecond = false
    feature.saveAllToGallery()
    assertEquals(listOf("first", "second", "second"), repository.attemptedPaths)
    assertEquals(2, successfulSaves)
    assertTrue(state.value.compressedImages.all { it.savedUri != null })
    assertFalse(state.value.isSaving)
    scope.cancel()
  }

  @Test
  fun duplicateTapAndStaleCardCannotSaveOrCountTwice() = runBlocking {
    val image = image("first")
    val state = state(image)
    val gate = CompletableDeferred<Unit>()
    val repository = FakeRepository {
      gate.await()
      it.copy(savedUri = mock(Uri::class.java))
    }
    var successfulSaves = 0
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val feature = GallerySaveFeature(state, repository, scope, { message -> state.value = state.value.copy(message = message) }, { successfulSaves += it })

    feature.saveToGallery(image)
    assertTrue(state.value.isSaving)
    feature.saveToGallery(image)
    feature.saveAllToGallery()
    assertEquals(listOf("first"), repository.attemptedPaths)

    gate.complete(Unit)
    assertFalse(state.value.isSaving)
    feature.saveToGallery(image) // Callback captured the unsaved image before recomposition.
    assertEquals(listOf("first"), repository.attemptedPaths)
    assertEquals(1, successfulSaves)
    scope.cancel()
  }

  @Test
  fun bookkeepingFailureDoesNotChangeSuccessfulSaveIntoError() {
    val image = image("first")
    val state = state(image)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val repository = FakeRepository { it.copy(savedUri = mock(Uri::class.java)) }
    val feature = GallerySaveFeature(state, repository, scope, { message -> state.value = state.value.copy(message = message) }, { error("Preferences unavailable") })

    feature.saveToGallery(image)

    assertNotNull(state.value.compressedImages.single().savedUri)
    assertEquals("Saved to Pictures / Image Compressor.", state.value.message)
    assertFalse(state.value.isSaving)
    scope.cancel()
  }

  @Test
  fun cancellationReleasesBusyStateAndDoesNotCountAnUnfinishedWrite() {
    val image = image("first")
    val state = state(image)
    val gate = CompletableDeferred<Unit>()
    val repository = FakeRepository { gate.await(); it.copy(savedUri = mock(Uri::class.java)) }
    var successfulSaves = 0
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val feature = GallerySaveFeature(state, repository, scope, { message -> state.value = state.value.copy(message = message) }, { successfulSaves += it })

    feature.saveToGallery(image)
    assertTrue(state.value.isSaving)
    scope.cancel()

    assertFalse(state.value.isSaving)
    assertNull(state.value.compressedImages.single().savedUri)
    assertEquals(0, successfulSaves)
    assertNull(state.value.message)
  }

  @Test
  fun alreadyCancelledScopeStillReleasesSynchronousReservation() {
    val image = image("first")
    val state = state(image)
    val repository = FakeRepository { error("Should not run") }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    scope.cancel()
    val feature = GallerySaveFeature(state, repository, scope, {})

    feature.saveToGallery(image)

    assertFalse(state.value.isSaving)
    assertTrue(repository.attemptedPaths.isEmpty())
  }

  @Test
  fun compressionInProgressPreventsSaving() {
    val image = image("first")
    val state = MutableStateFlow(state(image).value.copy(isCompressing = true))
    val repository = FakeRepository { error("Should not run") }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val feature = GallerySaveFeature(state, repository, scope, {})

    feature.saveToGallery(image)
    feature.saveAllToGallery()

    assertTrue(repository.attemptedPaths.isEmpty())
    assertFalse(state.value.isSaving)
    scope.cancel()
  }

  @Test
  fun batchContinuesAfterMiddleFailureAndRetryKeepsEveryDurableSuccess() {
    val first = image("first")
    val second = image("second")
    val third = image("third")
    val state = state(first, second, third)
    var failSecond = true
    var successfulSaves = 0
    val repository = FakeRepository {
      if (it.historyId == second.historyId && failSecond) error("Storage unavailable")
      it.copy(savedUri = mock(Uri::class.java))
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val feature = GallerySaveFeature(state, repository, scope,
      { message -> state.value = state.value.copy(message = message) }, { successfulSaves += it })

    feature.saveAllToGallery()

    assertEquals(listOf("first", "second", "third"), repository.attemptedPaths)
    assertEquals(setOf(first.historyId, third.historyId), repository.durableResults.keys)
    assertEquals(setOf(second.historyId), state.value.saveErrors.keys)
    assertEquals(2, successfulSaves)
    assertEquals("2 saved. 1 could not be saved. Please try again.", state.value.message)

    failSecond = false
    feature.saveAllToGallery()

    assertEquals(listOf("first", "second", "third", "second"), repository.attemptedPaths)
    assertTrue(state.value.saveErrors.isEmpty())
    assertTrue(state.value.compressedImages.all { it.savedUri != null })
    assertEquals(3, successfulSaves)
    scope.cancel()
  }

  @Test
  fun staleRestoredBatchUsesDurableReceiptWithoutReexportingOrCountingAgain() {
    val old = image("old-path")
    val saved = old.copy(outputFilePath = "retained-path", savedUri = mock(Uri::class.java))
    val state = MutableStateFlow(state(old).value.copy(compareImage = old))
    val repository = FakeRepository { error("Already exported") }
    repository.durableResults[old.historyId] = saved
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var successfulSaves = 0
    val feature = GallerySaveFeature(state, repository, scope, {}, { successfulSaves += it })

    feature.saveToGallery(old)

    assertEquals(saved, state.value.compressedImages.single())
    assertEquals(saved, state.value.compareImage)
    assertTrue(repository.attemptedPaths.isEmpty())
    assertTrue(state.value.saveErrors.isEmpty())
    assertEquals(0, successfulSaves)
    assertFalse(state.value.isSaving)
    scope.cancel()
  }

  @Test
  fun recreatedSaveOwnerDoesNotCountDurableExportTwice() {
    val image = image("first")
    val repository = FakeRepository { it.copy(savedUri = mock(Uri::class.java)) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var successfulSaves = 0
    GallerySaveFeature(state(image), repository, scope, {}, { successfulSaves += it }).saveAllToGallery()
    // A restored UI can momentarily contain the older unsaved snapshot before Room emits.
    val restored = state(image)
    GallerySaveFeature(restored, repository, scope, {}, { successfulSaves += it }).saveAllToGallery()

    assertEquals(listOf("first"), repository.attemptedPaths)
    assertEquals(1, successfulSaves)
    assertNotNull(restored.value.compressedImages.single().savedUri)
    scope.cancel()
  }

  @Test
  fun initializationCancellationAndOtherActiveWorkPreventSaving() {
    val image = image("first")
    val base = state(image).value
    val blocked = listOf(
      base.copy(isInitializing = true),
      base.copy(initializationError = "Storage unavailable"),
      base.copy(isCancelling = true),
      base.copy(isLoadingImages = true),
      base.copy(isExternalFlowActive = true),
      base.copy(editorAction = EditorAction.SAVE),
    )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val repository = FakeRepository { error("Should not run") }
    for (snapshot in blocked) {
      val state = MutableStateFlow(snapshot)
      val feature = GallerySaveFeature(state, repository, scope, {})
      feature.saveToGallery(image)
      feature.saveAllToGallery()
      assertFalse(state.value.isSaving)
    }
    assertTrue(repository.attemptedPaths.isEmpty())
    scope.cancel()
  }

  @Test
  fun unconfirmedOrWrongIdentityReceiptDoesNotPublishOrEarnReviewSuccess() {
    val image = image("first")
    for (receipt in listOf(image, image.copy(historyId = image.historyId + 1, savedUri = mock(Uri::class.java)))) {
      val state = state(image)
      val repository = FakeRepository { receipt }
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
      var successfulSaves = 0
      val feature = GallerySaveFeature(state, repository, scope, {}, { successfulSaves += it })

      feature.saveToGallery(image)

      assertEquals(image, state.value.compressedImages.single())
      assertEquals(setOf(image.historyId), state.value.saveErrors.keys)
      assertEquals(0, successfulSaves)
      assertFalse(state.value.isSaving)
      scope.cancel()
    }
  }

  @Test
  fun distinctDurableIdentitiesAreNotCollapsedByMatchingOldPaths() {
    val first = image("first")
    val second = image("second").copy(outputFilePath = first.outputFilePath)
    val state = state(first, second)
    val repository = FakeRepository { it.copy(savedUri = mock(Uri::class.java)) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var successfulSaves = 0
    val feature = GallerySaveFeature(state, repository, scope, {}, { successfulSaves += it })

    feature.saveAllToGallery()

    assertEquals(2, repository.attemptedPaths.size)
    assertEquals(setOf(first.historyId, second.historyId), repository.durableResults.keys)
    assertEquals(2, successfulSaves)
    assertTrue(state.value.compressedImages.all { it.savedUri != null })
    scope.cancel()
  }

  private fun state(vararg images: CompressedImage) = MutableStateFlow(
    ImageCompressorUiState(screen = AppScreen.RESULTS, compressedImages = images.toList(), isInitializing = false)
  )

  private fun image(path: String): CompressedImage {
    val uri = mock(Uri::class.java)
    val original = SelectedImage(uri, path, 1_000, 10, 10, "image/jpeg")
    return CompressedImage(original, uri, path, 200, 10, 10, OutputFormat.JPEG, historyId = path.hashCode().toLong() and 0x7fffffffL)
  }

  private class FakeRepository(
    val save: suspend (CompressedImage) -> CompressedImage,
  ) : ImageRepository {
    val attemptedPaths = mutableListOf<String>()
    val durableResults = mutableMapOf<Long, CompressedImage>()
    override val history: Flow<List<CompressionHistoryEntity>> = flowOf(emptyList())
    override val batch: Flow<BatchSnapshot?> = flowOf(null)
    override suspend fun initialize() = Unit
    override suspend fun refreshAvailability() = Unit
    override suspend fun saveToGallery(image: CompressedImage): CompressedImage {
      attemptedPaths += image.outputFilePath
      return save(image).also { if (it.savedUri != null) durableResults[it.historyId] = it }
    }
    override suspend fun getResult(id: Long): CompressedImage? = durableResults[id]
    override suspend fun inspectImages(uris: List<Uri>): SelectionResult = error("Unused")
    override suspend fun compressImages(images: List<SelectedImage>, settings: CompressionSettings): Unit = error("Unused")
    override suspend fun retryBatch(): Unit = error("Unused")
    override suspend fun dismissBatch(): Unit = error("Unused")
    override suspend fun shareUri(id: Long): Uri = error("Unused")
    override suspend fun deleteHistory(item: CompressionHistoryEntity) = error("Unused")
    override suspend fun clearHistory() = error("Unused")
  }
}
