package com.example.imagecompressor.validation

import android.graphics.BitmapFactory
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
import com.example.imagecompressor.data.model.TargetSizeStatus
import com.example.imagecompressor.ui.ImageCompressorViewModel
import com.example.imagecompressor.ui.state.AppScreen
import java.io.File
import java.util.UUID
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

  @Test fun selectedPhotoCompressesSavesOnceAndSurvivesActivityRecreation() {
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
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", input)
    try {
      compose.runOnUiThread {
        model.completeOnboarding()
        // The system picker is outside this test; exercise its real URI handoff boundary.
        model.addSelectedImages(listOf(uri))
      }
      compose.waitUntil(15_000) { !model.uiState.value.isLoadingImages }
      assertEquals(1, model.uiState.value.selectedImages.size)
      compose.onNodeWithText(context.getString(R.string.preset_limit)).performScrollTo().performClick()
      val limitField = compose.onNode(hasSetTextAction() and hasText(context.getString(R.string.size_limit)))
      limitField.performScrollTo().performTextReplacement("0.05")
      limitField.performImeAction()
      compose.onNodeWithText(context.getString(R.string.compress_photos)).performClick()
      compose.waitUntil(30_000) { !model.uiState.value.isCompressing && model.uiState.value.compressedImages.size == 1 }
      val result = model.uiState.value.compressedImages.single()
      assertEquals(AppScreen.RESULTS, model.uiState.value.screen)
      assertEquals(TargetSizeStatus.MET, result.targetSizeStatus)
      assertTrue(result.compressedSizeBytes in 1..50_000)
      val outputBytes = File(result.outputFilePath).readBytes()
      assertEquals(result.compressedSizeBytes, outputBytes.size.toLong())
      assertNotNull(BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size)?.also { it.recycle() })
      assertArrayEquals(originalBytes, input.readBytes())
      // Sharing must expose a readable content URI, never a raw private file path.
      val shareUri = runBlocking { repository.shareUri(result.historyId) }
      assertEquals("content", shareUri.scheme)
      assertArrayEquals(outputBytes, context.contentResolver.openInputStream(shareUri)!!.use { it.readBytes() })

      compose.onNodeWithText(context.getString(R.string.save_gallery)).performClick()
      compose.waitUntil(15_000) { !model.uiState.value.isSaving && model.uiState.value.compressedImages.singleOrNull()?.savedUri != null }
      val saved = model.uiState.value.compressedImages.single()
      val savedUri = checkNotNull(saved.savedUri)
      assertArrayEquals(outputBytes, context.contentResolver.openInputStream(savedUri)!!.use { it.readBytes() })
      context.contentResolver.query(savedUri, arrayOf(MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
        assertTrue(it.moveToFirst())
        assertEquals("Pictures/Image Compressor/", it.getString(0))
        assertEquals(0, it.getInt(1))
      }
      assertEquals("Saving twice must reuse the published copy", savedUri,
        runBlocking { repository.saveToGallery(saved).savedUri })
      compose.onNodeWithText(context.getString(R.string.saved)).assertIsNotEnabled()

      compose.activityRule.scenario.recreate()
      compose.runOnUiThread { model = ViewModelProvider(compose.activity)[ImageCompressorViewModel::class.java] }
      compose.waitUntil(15_000) { !model.uiState.value.isInitializing && model.uiState.value.compressedImages.size == 1 }
      assertEquals(AppScreen.RESULTS, model.uiState.value.screen)
      assertEquals(savedUri, model.uiState.value.compressedImages.single().savedUri)
      assertArrayEquals(originalBytes, input.readBytes())
    } finally {
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
