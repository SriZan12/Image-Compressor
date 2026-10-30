package com.example.imagecompressor.validation

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.imagecompressor.ImageCompressorApplication
import com.example.imagecompressor.MainActivity
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.TargetSizeStatus
import com.example.imagecompressor.ui.ImageCompressorViewModel
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.EditorPreview
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Use a dedicated test installation: this exercises real Room, codecs and MediaStore. */
@RunWith(AndroidJUnit4::class)
class NativeWorkflowTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun automaticPreviewExportsExactBytesOnceAndKeepsEditorSavedAfterRecreation() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val repository = (context.applicationContext as ImageCompressorApplication).container.imageRepository
    lateinit var model: ImageCompressorViewModel
    compose.runOnUiThread { model = ViewModelProvider(compose.activity)[ImageCompressorViewModel::class.java] }
    compose.waitUntil(15_000) { !model.uiState.value.isInitializing }
    assertNull(model.uiState.value.initializationError)
    // Fail visibly on an occupied installation; never clear someone else's results to run a test.
    runBlocking {
      assertTrue("Use a dedicated empty test installation", repository.history.first().isEmpty())
      assertNull("A previous batch must not be replaced by a test", repository.batch.first())
    }
    val directory = File(context.cacheDir, "compressed/workflow-${UUID.randomUUID()}")
    check(directory.mkdirs())
    val input = File(directory, "photo.png")
    instrumentation.context.assets.open("compression/astronaut.png").use { source ->
      input.outputStream().use(source::copyTo)
    }
    val originalBytes = input.readBytes()
    val unavailableInput = File(directory, "photo-unavailable.png")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", input)
    try {
      compose.runOnUiThread {
        model.completeOnboarding()
        model.updateCompressionSettings(CompressionSettings())
        // The system picker is outside this test; exercise its real URI handoff boundary.
        model.addSelectedImages(listOf(uri))
      }
      compose.waitUntil(15_000) { !model.uiState.value.isLoadingImages }
      assertEquals(1, model.uiState.value.selectedImages.size)
      compose.onNodeWithText(context.getString(R.string.editor_custom_limit)).performScrollTo().performClick()
      compose.onNodeWithText("KB").performScrollTo().performClick()
      val limitField = compose.onNode(hasSetTextAction() and hasText(context.getString(R.string.size_limit)))
      limitField.performScrollTo().performTextReplacement("50")
      limitField.performImeAction()
      // Picking a limit prepares a real candidate automatically, without starting a retained batch.
      compose.waitUntil(30_000) {
        val current = model.uiState.value
        val ready = current.editorPreview as? EditorPreview.Ready
        ready != null && ready.settings == current.compressionSettings && ready.settings.targetBytes == 50_000L
      }
      val preview = (model.uiState.value.editorPreview as EditorPreview.Ready).image
      assertEquals(AppScreen.COMPRESSION_SETTINGS, model.uiState.value.screen)
      assertEquals(TargetSizeStatus.MET, preview.targetSizeStatus)
      assertTrue(preview.compressedSizeBytes in 1..50_000)
      assertEquals(0L, preview.historyId)
      assertTrue(model.uiState.value.compressedImages.isEmpty())
      compose.onNodeWithText(context.getString(R.string.compress_photos)).assertDoesNotExist()
      val previewBytes = File(preview.outputFilePath).readBytes()
      assertEquals(preview.compressedSizeBytes, previewBytes.size.toLong())
      assertNotNull(BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes.size)?.also { it.recycle() })
      assertArrayEquals(originalBytes, input.readBytes())
      runBlocking {
        assertTrue("Live preview must not add history", repository.history.first().isEmpty())
        assertNull("Live preview must not create a batch", repository.batch.first())
      }

      // Only this test-owned source is moved. Save must still work from the measured candidate;
      // reopening/re-encoding the source would fail. Restore it before recreating the activity.
      assertTrue(input.renameTo(unavailableInput))
      compose.onNodeWithText(context.getString(R.string.save_gallery)).performClick()
      compose.waitUntil(15_000) {
        !model.uiState.value.isSaving && model.uiState.value.editorRetained?.image?.savedUri != null
      }
      val saved = checkNotNull(model.uiState.value.editorRetained).image
      val savedUri = checkNotNull(saved.savedUri)
      assertEquals(AppScreen.COMPRESSION_SETTINGS, model.uiState.value.screen)
      assertTrue(saved.historyId > 0)
      assertEquals(preview.encodingAttempts, saved.encodingAttempts)
      assertEquals(preview.encodingQuality, saved.encodingQuality)
      assertArrayEquals(previewBytes, File(saved.outputFilePath).readBytes())
      assertArrayEquals(previewBytes, context.contentResolver.openInputStream(savedUri)!!.use { it.readBytes() })
      assertTrue(unavailableInput.renameTo(input))
      context.contentResolver.query(savedUri, arrayOf(MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
        assertTrue(it.moveToFirst())
        assertEquals("Pictures/Image Compressor/", it.getString(0))
        assertEquals(0, it.getInt(1))
      }
      assertEquals("Saving twice must reuse the published copy", savedUri,
        runBlocking { repository.saveToGallery(saved).savedUri })
      compose.onNodeWithText(context.getString(R.string.saved)).assertIsNotEnabled()

      // Exercise the editor's share handoff without opening an external chooser during the test.
      val sharedUri = AtomicReference<Uri>()
      compose.runOnUiThread { model.shareEditorPhoto { sharedUri.set(it) } }
      compose.waitUntil(15_000) { sharedUri.get() != null && !model.uiState.value.isSaving }
      val shareUri = checkNotNull(sharedUri.get())
      assertEquals("content", shareUri.scheme)
      assertArrayEquals(previewBytes, context.contentResolver.openInputStream(shareUri)!!.use { it.readBytes() })
      runBlocking {
        assertEquals(saved.historyId, repository.history.first().single().id)
        assertNull("Save and Share must not start a batch", repository.batch.first())
      }
      assertEquals(AppScreen.COMPRESSION_SETTINGS, model.uiState.value.screen)
      assertTrue(model.uiState.value.compressedImages.isEmpty())

      compose.activityRule.scenario.recreate()
      compose.runOnUiThread { model = ViewModelProvider(compose.activity)[ImageCompressorViewModel::class.java] }
      compose.waitUntil(15_000) {
        !model.uiState.value.isInitializing && model.uiState.value.editorRetained?.image?.savedUri == savedUri
      }
      assertEquals(AppScreen.COMPRESSION_SETTINGS, model.uiState.value.screen)
      assertEquals(saved.historyId, model.uiState.value.editorRetained?.image?.historyId)
      compose.onNodeWithText(context.getString(R.string.saved)).assertIsDisplayed().assertIsNotEnabled()
      assertEquals(1, runBlocking { repository.history.first().size })
      assertArrayEquals(originalBytes, input.readBytes())
    } finally {
      if (unavailableInput.exists() && !input.exists()) unavailableInput.renameTo(input)
      // Remove only records/exports tied to this unique fixture, even after an assertion fails.
      runBlocking {
        val owned = repository.history.first().filter { it.originalUri == uri.toString() }
        for (row in owned) {
          row.savedPath?.let { context.contentResolver.delete(android.net.Uri.parse(it), null, null) }
          repository.deleteHistory(row)
        }
        val batch = repository.batch.first()
        if (batch != null && batch.items.all { it.image.uri == uri }) repository.dismissBatch()
      }
      directory.deleteRecursively()
    }
  }
}
