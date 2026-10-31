package com.example.imagecompressor.ui.design

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.ui.AppChrome
import com.example.imagecompressor.ui.PhotoEditorScreen
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.ui.state.PhotoEditorState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native Compose presentation checks. Fixture bytes are real; these do not measure device latency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEditorRenderTest {
  @get:Rule(order = 0) val hostActivity = ComposeHostActivityRule()
  @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun verifiedPhoneShowsOriginalInformationAndExportsTheMeasuredResult() {
    val photo = fixture()
    val state = ready(photo)
    var saves = 0
    var shares = 0
    var comparisons = 0
    screen { modifier -> PhotoEditorScreen(state, modifier, {}, {}, onSave = { saves++ },
      onShare = { shares++ }, onCompare = { comparisons++ }, animatePlaceholders = false) }

    compose.onNodeWithText("Original: ${photo.original.sizeBytes.toReadableSize()}").assertIsDisplayed()
    compose.onNodeWithText("512 × 512").assertIsDisplayed()
    compose.onNodeWithText(photo.compressedSizeBytes.toReadableSize()).assertIsDisplayed()
    compose.onNodeWithText("Fits your 500 KB limit").assertIsDisplayed()
    compose.onNodeWithText("Picture detail", substring = true).assertDoesNotExist()
    val save = compose.onNodeWithText("Save photo").assertIsDisplayed().assertIsEnabled()
    val share = compose.onNodeWithText("Share").assertIsDisplayed().assertIsEnabled()
    capture("editor-ready-phone")
    save.performClick()
    share.performClick()
    compose.onNodeWithText("Compare with original").performScrollTo().performClick()
    assertEquals(1, saves)
    assertEquals(1, shares)
    assertEquals(1, comparisons)
    assertEquals(File(photo.outputFilePath).length(), photo.compressedSizeBytes)
  }

  @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
  fun darkTabletPlacesControlsBesideThePhoto() {
    val photo = fixture()
    screen(dark = true) { modifier -> PhotoEditorScreen(ready(photo), modifier, {}, {},
      onSave = {}, onShare = {}, onCompare = {}, animatePlaceholders = false) }
    awaitPreviews()
    val preview = compose.onNodeWithTag("editor-photo-frame").getUnclippedBoundsInRoot()
    val size = compose.onNodeWithTag("editor-size-summary").getUnclippedBoundsInRoot()
    assertTrue("The controls belong in the second pane", size.left >= preview.right)
    compose.onNodeWithText("Save photo").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Share").assertIsDisplayed()
    capture("editor-tablet-dark")
  }

  @Test fun loadingKeepsKnownPreviewAndMetadataGeometryStable() {
    val photo = fixture()
    val ready = ready(photo)
    var preview by mutableStateOf<EditorPreview>(EditorPreview.Loading)
    screen { modifier -> PhotoEditorScreen(ready.copy(preview = preview), modifier, {}, {},
      onSave = {}, onShare = {}, animatePlaceholders = false) }
    val tags = listOf("editor-photo-frame", "editor-original-size", "editor-dimensions", "editor-size-summary")
    val loadingBounds = tags.associateWith { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
    compose.onNodeWithText("Opening your photo…").assertIsDisplayed()
    compose.onNodeWithText("Save photo").assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsNotEnabled()
    compose.onNodeWithText("Original: ${photo.original.sizeBytes.toReadableSize()}").assertDoesNotExist()
    capture("editor-loading-phone", awaitImages = false)

    compose.runOnIdle { preview = ready.preview }
    awaitPreviews()
    tags.forEach { tag -> assertEquals("Loading and ready geometry must match for $tag",
      loadingBounds.getValue(tag), compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()) }
    compose.onNodeWithText("Original: ${photo.original.sizeBytes.toReadableSize()}").assertIsDisplayed()
    compose.onNodeWithText("Save photo").assertIsEnabled()
  }

  @Test fun estimatedSizeIsLabelledAndCannotBeExported() {
    val photo = fixture()
    val state = ready(photo).copy(preview = EditorPreview.Updating(
      estimatedBytes = photo.compressedSizeBytes, previousImage = photo))
    assertEquals("Keep the last same-source image visible while checking new bytes", photo, state.previewImage)
    assertNull("An estimate must not expose an exportable file", state.currentResult)
    screen { modifier -> PhotoEditorScreen(state, modifier, {}, {}, onSave = {}, onShare = {},
      animatePlaceholders = false) }
    compose.onNodeWithText("Estimated size · checking the final file…").assertIsDisplayed()
    compose.onNodeWithText(photo.compressedSizeBytes.toReadableSize()).assertIsDisplayed()
    compose.onNodeWithText("Fits your 500 KB limit").assertDoesNotExist()
    compose.onNodeWithText("Save photo").assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsNotEnabled()
    capture("editor-estimating-phone")
  }

  @Test fun failedPreparationKeepsThePhotoAndOffersRetry() {
    val photo = fixture()
    val message = "We couldn’t make a copy. Please try again."
    var retried = false
    screen { modifier -> PhotoEditorScreen(ready(photo).copy(preview = EditorPreview.Failure(message)),
      modifier, {}, {}, onSave = {}, onShare = {}, onRetry = { retried = true }, animatePlaceholders = false) }
    compose.onNodeWithText(message).assertIsDisplayed()
    compose.onNodeWithText("Save photo").assertDoesNotExist()
    compose.onNodeWithText("Share").assertDoesNotExist()
    capture("editor-error-phone")
    compose.onNodeWithText("Try again").assertIsDisplayed().performClick()
    assertTrue(retried)
    compose.onNodeWithContentDescription("Photo preview").assertExists()
  }

  @Test @Config(qualifiers = "w320dp-h800dp-mdpi")
  fun largeTextStacksExportsAndKeepsTheSliderAccessible() {
    val photo = fixture()
    var requested: CompressionSettings? = null
    screen(fontScale = 2f) { modifier -> PhotoEditorScreen(ready(photo), modifier, { requested = it }, {},
      onSave = {}, onShare = {}, onCompare = {}, animatePlaceholders = false) }
    val save = compose.onNodeWithText("Save photo").performScrollTo().assertIsDisplayed().assertIsEnabled()
    val share = compose.onNodeWithText("Share").performScrollTo().assertIsDisplayed().assertIsEnabled()
    assertInsideRoot(save)
    assertInsideRoot(share)
    assertTrue("Save and Share must stack instead of clipping", share.getUnclippedBoundsInRoot().top >= save.getUnclippedBoundsInRoot().bottom)
    val slider = compose.onNodeWithContentDescription("Photo detail")
    slider.performScrollTo().assertIsDisplayed().assertIsEnabled()
    assertInsideRoot(slider)
    slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(60f)) }
    assertEquals(60, requested?.quality)
    assertEquals(500_000L, requested?.targetBytes)
    capture("editor-large-text")
  }

  @Test fun staleSettingsOrDifferentSourceNeverEnableExports() {
    val photo = fixture()
    val verified = ready(photo)
    var state by mutableStateOf(verified)
    screen { modifier -> PhotoEditorScreen(state, modifier, {}, {}, onSave = {}, onShare = {},
      onCompare = {}, animatePlaceholders = false) }
    compose.onNodeWithText("Save photo").assertIsEnabled()
    compose.runOnIdle { state = verified.copy(settings = verified.settings.copy(quality = 40)) }
    assertEquals("A stale same-source result can remain visible", photo, state.previewImage)
    assertNull("Visible stale pixels must not make an older file exportable", state.currentResult)
    compose.onNodeWithText("Updating size…").assertIsDisplayed()
    compose.onNodeWithText("Save photo").assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsNotEnabled()
    compose.onNodeWithText("Compare with original").assertDoesNotExist()

    compose.runOnIdle { state = state.copy(preview = EditorPreview.Updating(previousImage = photo)) }
    assertEquals(photo, state.previewImage)
    assertNull(state.currentResult)
    compose.onNodeWithText("Save photo").assertIsNotEnabled()
    // A second real, cached file exercises source changes without an unregistered provider stub.
    val otherSource = photo.original.copy(uri = photo.outputUri)
    compose.runOnIdle { state = state.copy(original = otherSource) }
    assertNull("A new source must not display an unrelated previous image", state.previewImage)
    assertNull(state.currentResult)
    compose.onNodeWithText("Share").assertIsNotEnabled()

    compose.runOnIdle { state = verified.copy(original = otherSource) }
    assertNull("Ready-state image retention also needs the source guard", state.previewImage)
    assertNull(state.currentResult)
    compose.onNodeWithText("Updating size…").assertExists()
    compose.onNodeWithText("Save photo").assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsNotEnabled()
    compose.runOnIdle { state = verified.copy(original = photo.original.copy(sizeBytes = photo.original.sizeBytes + 1)) }
    assertNull("The same URI with changed metadata must not retain an old preview", state.previewImage)
    assertNull(state.currentResult)
    compose.onNodeWithText("Fits your 500 KB limit").assertDoesNotExist()
    compose.onNodeWithText("Save photo").assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsNotEnabled()
    compose.runOnIdle { state = verified }
    compose.onNodeWithText("Save photo").assertIsEnabled()
    compose.onNodeWithText("Share").assertIsEnabled()
    compose.onNodeWithText("Compare with original").assertIsEnabled()
  }

  @Test fun roundedFileSizeCannotTurnAnUnmetLimitIntoSuccess() {
    // Synthetic metadata deliberately tests rounding. It is never used in a design screenshot.
    val photo = fixture().copy(compressedSizeBytes = 500_001, targetBytes = 500_000)
    assertEquals(500_000L.toReadableSize(), photo.compressedSizeBytes.toReadableSize())
    var savedAnyway = false
    screen { modifier -> PhotoEditorScreen(ready(photo), modifier, {}, {}, onSave = { savedAnyway = true },
      onShare = {}, animatePlaceholders = false) }
    compose.onNodeWithText("Still over your 500 KB limit").assertIsDisplayed()
    compose.onNodeWithText("Fits your 500 KB limit").assertDoesNotExist()
    compose.onNodeWithText("Save photo").assertDoesNotExist()
    compose.onNodeWithText("Save anyway").assertIsEnabled().performClick()
    assertTrue(savedAnyway)
  }

  @Test fun sharingBlocksDuplicateActionsAndEditingWithoutCallingItSaving() {
    val photo = fixture()
    val state = ready(photo).copy(isSaving = true, isSharing = true)
    var saves = 0
    var shares = 0
    var comparisons = 0
    screen { modifier -> PhotoEditorScreen(state, modifier, {}, {}, onSave = { saves++ },
      onShare = { shares++ }, onCompare = { comparisons++ }, animatePlaceholders = false) }

    compose.onNodeWithText(verifiedSize(photo)).assertIsDisplayed()
    compose.onNodeWithText("Save photo").assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
    compose.onNodeWithText("Preparing…").assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
    compose.onNodeWithText("Saving…").assertDoesNotExist()
    compose.onNodeWithContentDescription("Photo detail").assertIsNotEnabled()
    compose.onNodeWithText("Compare with original").performScrollTo().assertIsNotEnabled().performTouchInput { click() }
    assertEquals(0, saves)
    assertEquals(0, shares)
    assertEquals(0, comparisons)
    assertEquals(photo, state.currentResult)
  }

  @Test fun savingMarksOnlySaveBusyAndPreservesTheVerifiedResult() {
    val photo = fixture()
    val state = ready(photo).copy(isSaving = true)
    screen { modifier -> PhotoEditorScreen(state, modifier, {}, {}, onSave = {}, onShare = {},
      onCompare = {}, animatePlaceholders = false) }

    compose.onNodeWithText(verifiedSize(photo)).assertIsDisplayed()
    compose.onNodeWithText("Saving…").assertIsDisplayed().assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsDisplayed().assertIsNotEnabled()
    compose.onNodeWithText("Preparing…").assertDoesNotExist()
    compose.onNodeWithContentDescription("Photo detail").assertIsNotEnabled()
    compose.onNodeWithText("Compare with original").performScrollTo().assertIsNotEnabled()
    assertEquals(photo, state.currentResult)
  }

  @Test fun exportFailureKeepsTheVerifiedCopyAvailableForSaveAndShareRetry() {
    val photo = fixture()
    val message = "We couldn’t save this photo. Free up some space and try again."
    val state = ready(photo).copy(exportError = message)
    var saves = 0
    var shares = 0
    var refreshes = 0
    screen { modifier -> PhotoEditorScreen(state, modifier, {}, {}, onSave = { saves++ },
      onShare = { shares++ }, onCompare = {}, onRetry = { refreshes++ }, animatePlaceholders = false) }

    compose.onNodeWithText(verifiedSize(photo)).assertIsDisplayed()
    compose.onNodeWithText("Fits your 500 KB limit").assertIsDisplayed()
    compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Save photo").assertIsEnabled().performClick()
    compose.onNodeWithText("Share").assertIsEnabled().performClick()
    assertEquals(1, saves)
    assertEquals(1, shares)
    assertEquals(photo, state.currentResult)
    assertEquals(photo.compressedSizeBytes, File(photo.outputFilePath).length())
    compose.onNodeWithText("Update preview").performScrollTo().assertIsEnabled().performClick()
    assertEquals(1, refreshes)
  }

  @Test fun savedCopyShowsItsLocationAndCanStillBeShared() {
    // The saved URI is presentation metadata; the displayed pixels still come from the real fixture.
    val photo = fixture().copy(savedUri = Uri.parse("content://media/external/images/media/42"))
    var saves = 0
    var shares = 0
    screen { modifier -> PhotoEditorScreen(ready(photo), modifier, {}, {}, onSave = { saves++ },
      onShare = { shares++ }, onCompare = {}, animatePlaceholders = false) }

    compose.onNodeWithText("Saved").assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
    compose.onNodeWithText("Share").assertIsDisplayed().assertIsEnabled().performClick()
    compose.onNodeWithText("Saved in Pictures / Image Compressor").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Compare with original").assertIsEnabled()
    assertEquals(0, saves)
    assertEquals(1, shares)
  }

  @Test fun invalidCustomDraftDisablesExportsWithoutChangingTheSavedLimit() {
    val photo = fixture()
    var state by mutableStateOf(ready(photo))
    var comparisons = 0
    screen { modifier -> PhotoEditorScreen(state, modifier, { state = state.copy(settings = it) }, {},
      onSave = {}, onShare = {}, onCompare = { comparisons++ }, animatePlaceholders = false) }
    compose.onNodeWithText("Custom").performScrollTo().performClick()
    val field = compose.onNode(hasSetTextAction() and hasText("Size limit"))
    field.performScrollTo().performTextReplacement("0")
    compose.onNodeWithText("Save photo").assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsNotEnabled()
    compose.onNodeWithText("Fits your 500 KB limit").assertDoesNotExist()
    compose.onNodeWithText("Compare with original").performScrollTo().assertIsNotEnabled().performTouchInput { click() }
    assertEquals("An invalid draft must not open comparison with the old valid limit", 0, comparisons)
    assertEquals(500_000L, state.settings.targetBytes)
    field.performScrollTo()
    field.performTextReplacement("0.5")
    field.performImeAction()
    compose.onNodeWithText("Save photo").assertIsEnabled()
    compose.onNodeWithText("Share").assertIsEnabled()
    compose.onNodeWithText("Compare with original").performScrollTo().assertIsEnabled().performClick()
    assertEquals(1, comparisons)
  }

  private fun verifiedSize(photo: CompressedImage): String = RuntimeEnvironment.getApplication().resources
    .getQuantityString(R.plurals.editor_verified_bytes, photo.compressedSizeBytes.toInt(), photo.compressedSizeBytes)

  private fun ready(photo: CompressedImage): PhotoEditorState {
    val settings = CompressionSettings(quality = 65, targetBytes = 500_000)
    return PhotoEditorState(photo.original, settings, EditorPreview.Ready(photo, settings))
  }

  private fun screen(dark: Boolean = false, fontScale: Float = 1f, content: @Composable (Modifier) -> Unit) {
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
        ImageCompressorTheme(darkTheme = dark, dynamicColor = false) {
          Surface { AppChrome(AppScreen.COMPRESSION_SETTINGS, content = content) }
        }
      }
    }
  }

  private fun fixture(): CompressedImage {
    val context = RuntimeEnvironment.getApplication()
    val original = File(context.cacheDir, "editor-astronaut.png")
    checkNotNull(javaClass.classLoader?.getResourceAsStream("compression/astronaut.png")).use { source ->
      original.outputStream().use(source::copyTo)
    }
    val bitmap = checkNotNull(BitmapFactory.decodeFile(original.path))
    val output = File(context.cacheDir, "editor-astronaut.jpg")
    output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 65, it)) }
    val smaller = checkNotNull(BitmapFactory.decodeFile(output.path))
    // Cache actual decoded fixture pixels; this is deterministic rendering, not a cold-load benchmark.
    val loader = ImageLoader.Builder(context).build()
    loader.memoryCache!!.set(MemoryCache.Key(Uri.fromFile(original).toString()), MemoryCache.Value(bitmap))
    loader.memoryCache!!.set(MemoryCache.Key(Uri.fromFile(output).toString()), MemoryCache.Value(smaller))
    Coil.setImageLoader(loader)
    return CompressedImage(SelectedImage(Uri.fromFile(original), "astronaut.png", original.length(), bitmap.width, bitmap.height, "PNG"),
      Uri.fromFile(output), output.path, output.length(), smaller.width, smaller.height, OutputFormat.JPEG,
      historyId = 1, targetBytes = 500_000, encodingQuality = 65)
  }

  private fun assertInsideRoot(node: SemanticsNodeInteraction) {
    val root = compose.onRoot().getUnclippedBoundsInRoot()
    val bounds = node.getUnclippedBoundsInRoot()
    assertTrue("Control must fit the available width", bounds.left >= root.left && bounds.right <= root.right)
    assertTrue("Control must keep a 48dp target: $bounds\n${node.printToString()}", bounds.bottom - bounds.top >= 48.dp)
  }

  private fun awaitPreviews() {
    compose.waitUntil(timeoutMillis = 10_000) {
      compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Loading preview"), useUnmergedTree = true)
        .fetchSemanticsNodes().isEmpty()
    }
    compose.waitForIdle()
  }

  private fun capture(name: String, awaitImages: Boolean = true) {
    if (awaitImages) awaitPreviews() else compose.waitForIdle()
    val file = File(System.getProperty("shrink.screenshots"), "$name.png").apply { parentFile?.mkdirs() }
    compose.runOnIdle {
      val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(Canvas(bitmap))
      file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
    }
  }
}
