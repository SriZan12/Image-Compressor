package com.example.imagecompressor.ui.viewmodel

import android.net.Uri
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.ui.state.AppScreen
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
    val feature = GallerySaveFeature(state, repository, scope, {}, { successfulSaves += it })

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
    val feature = GallerySaveFeature(state, repository, scope, {}, { successfulSaves += it })

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
    val feature = GallerySaveFeature(state, repository, scope, {}, { error("Preferences unavailable") })

    feature.saveToGallery(image)

    assertNotNull(state.value.compressedImages.single().savedUri)
    assertEquals("Saved to your gallery.", state.value.message)
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
    val feature = GallerySaveFeature(state, repository, scope, {}, { successfulSaves += it })

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

  private fun state(vararg images: CompressedImage) = MutableStateFlow(
    ImageCompressorUiState(screen = AppScreen.RESULTS, compressedImages = images.toList())
  )

  private fun image(path: String): CompressedImage {
    val uri = mock(Uri::class.java)
    val original = SelectedImage(uri, path, 1_000, 10, 10, "image/jpeg")
    return CompressedImage(original, uri, path, 200, 10, 10, OutputFormat.JPEG)
  }

  private class FakeRepository(
    val save: suspend (CompressedImage) -> CompressedImage,
  ) : ImageRepository {
    val attemptedPaths = mutableListOf<String>()
    override val history: Flow<List<CompressionHistoryEntity>> = flowOf(emptyList())
    override suspend fun saveToGallery(image: CompressedImage): CompressedImage {
      attemptedPaths += image.outputFilePath
      return save(image)
    }
    override suspend fun inspectImages(uris: List<Uri>): List<SelectedImage> = error("Unused")
    override suspend fun compressImages(
      images: List<SelectedImage>,
      settings: CompressionSettings,
      onProgress: suspend (completed: Int, total: Int) -> Unit,
    ): List<CompressedImage> = error("Unused")
    override suspend fun deleteHistory(item: CompressionHistoryEntity) = error("Unused")
    override suspend fun clearHistory() = error("Unused")
  }
}
