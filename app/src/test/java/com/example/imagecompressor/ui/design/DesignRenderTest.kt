package com.example.imagecompressor.ui.design

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.Insets
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.ui.*
import com.example.imagecompressor.ui.state.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose layouts drawn through Android's native Canvas, without an emulator or mock images. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignRenderTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun screen(screen: AppScreen, dark: Boolean = false, fontScale: Float = 1f, content: @Composable (Modifier) -> Unit) {
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
        ImageCompressorTheme(darkTheme = dark, dynamicColor = false) { Surface { AppChrome(screen, content = content) } }
      }
    }
  }

  @Test fun homeShowsSelectionAndPrivateProcessing() {
    screen(AppScreen.HOME) { HomeScreen(state(AppScreen.HOME), it, {}, {}, {}) }
    compose.onNodeWithText("Select photos").assertIsDisplayed().assertHasClickAction()
    compose.onNodeWithText("Your photos stay on your device. Originals are always kept.").assertExists()
    capture("home-light")
  }

  @Test fun onboardingCanStartWithoutPagingThroughATour() {
    var started = false
    screen(AppScreen.ONBOARDING) { OnboardingScreen(it, { started = true }, {}) }
    compose.onNodeWithText("Get started").performScrollTo().performClick()
    assertTrue(started)
    capture("onboarding-light")
  }

  @Test fun presetsHideAdvancedAndRejectInvalidUploadLimit() {
    val photo = fixture()
    var settings by mutableStateOf(CompressionSettings())
    var requested: CompressionSettings? = null
    screen(AppScreen.COMPRESSION_SETTINGS) { CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(
      selectedImages = listOf(photo.original), compressionSettings = settings), it, { settings = it }, { requested = settings }, {}) }
    compose.onNodeWithText("Encoder quality", substring = true).assertDoesNotExist()
    compose.onNodeWithText("Compress photos").assertIsDisplayed()
    capture("presets-light")
    compose.onNodeWithText("Under a size limit").performScrollTo().performClick()
    val field = compose.onNode(hasSetTextAction() and hasText("Maximum size per photo"))
    field.performScrollTo().performTextReplacement("0")
    compose.onNodeWithText("Compress photos").assertIsNotEnabled()
    field.performScrollTo().performTextReplacement("0.5")
    compose.onNodeWithText("Compress photos").assertIsDisplayed().assertIsEnabled().performClick()
    assertEquals(500_000L, requested?.targetBytes)
  }

  @Test fun comparisonSliderAndPinnedActionsAreAccessibleOnPhone() {
    val photo = fixture()
    var saved = false; var shared = false
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo)), it,
      { saved = true }, { shared = true }, {}, {}, {}, {}) }
    compose.onNodeWithText("Save to gallery").assertIsDisplayed().performClick()
    compose.onNodeWithText("Share").assertIsDisplayed().performClick()
    assertTrue(saved && shared)
    awaitPreviews()
    val slider = compose.onNodeWithContentDescription("Before and after comparison")
    slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.75f)) }
    assertEquals(.75f, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .001f)
    compose.onNodeWithContentDescription("Zoom in").performScrollTo().performClick()
    compose.onNodeWithText(" · 150%").assertExists()
    compose.onNodeWithText("Fit", substring = true).performClick()
    // Return to the savings headline for the review capture.
    compose.onNodeWithText("smaller in total").performScrollTo()
    capture("results-light")
  }

  @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
  fun tabletShowsComparisonAndDetailsInSeparatePanes() {
    val photo = fixture()
    screen(AppScreen.IMAGE_COMPARE, dark = true) { ImageCompareScreen(state(AppScreen.IMAGE_COMPARE).copy(compareImage = photo), it, {}, {}) }
    val imageBounds = compose.onNodeWithContentDescription("Compressed", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val details = compose.onNodeWithText("Photo details").fetchSemanticsNode().boundsInRoot
    assertTrue("Details must sit to the right of the image", details.left >= imageBounds.right)
    compose.onNodeWithText("Share").assertIsDisplayed()
    capture("compare-tablet-dark")
  }

  @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
  fun narrowLargeTextKeepsActionsReachableWithoutHorizontalOverflow() {
    screen(AppScreen.HOME, fontScale = 2f) { HomeScreen(state(AppScreen.HOME), it, {}, {}, {}) }
    compose.onNodeWithText("Select photos").performScrollTo().assertIsDisplayed()
    assertInsideRoot(compose.onNodeWithText("Select photos"))
    capture("home-large-text")
  }

  @Test @Config(qualifiers = "w640dp-h320dp-land-mdpi")
  fun shortLandscapeSettingsScrollToThePrimaryAction() {
    val photo = fixture()
    screen(AppScreen.COMPRESSION_SETTINGS) { CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(selectedImages = listOf(photo.original)), it, {}, {}, {}) }
    compose.onNodeWithText("Compress photos").performScrollTo().assertIsDisplayed()
    assertInsideRoot(compose.onNodeWithText("Compress photos"))
  }

  @Test fun darkSettingsExposeThemeAndDynamicColorSwitch() {
    var selected by mutableStateOf(ThemePreference.DARK)
    var dynamic by mutableStateOf(true)
    screen(AppScreen.SETTINGS, dark = true) { SettingsScreen(state(AppScreen.SETTINGS).copy(themePreference = selected, dynamicColor = dynamic), it,
      { selected = it }, { dynamic = it }, {}) }
    capture("settings-dark")
    compose.onNodeWithText("Light").performClick()
    compose.onNodeWithText("Light").assertIsSelected()
    assertEquals(ThemePreference.LIGHT, selected)
    compose.onNodeWithText("Use device colors").performClick()
    assertFalse(dynamic)
  }

  @Test fun historyRemovalExplainsThatOriginalsAndGalleryCopiesAreKept() {
    val photo = fixture()
    val row = CompressionHistoryEntity(id = 1, createdAt = 1_700_000_000_000, displayName = photo.original.displayName,
      originalUri = photo.original.uri.toString(), compressedUri = photo.outputUri.toString(), originalSizeBytes = photo.original.sizeBytes,
      compressedSizeBytes = photo.compressedSizeBytes, width = photo.width, height = photo.height, outputFormat = "JPEG", availability = "RETAINED")
    var removed = false
    screen(AppScreen.HISTORY) { HistoryScreen(state(AppScreen.HISTORY).copy(history = listOf(row)), it, { removed = true }, {}, {}, {}) }
    capture("history-light")
    compose.onNodeWithText("Remove").performClick()
    compose.onNodeWithText("This removes the copy kept in Shrink and its history entry. Originals and gallery copies are kept.").assertIsDisplayed()
    compose.onNodeWithText("Keep").performClick()
    assertFalse(removed)
  }

  @Test fun interruptedBatchOffersRetryAndDoesNotClaimCompletion() {
    val photo = fixture()
    val batch = BatchSnapshot("batch", CompressionSettings(), BatchStatus.INTERRUPTED,
      listOf(BatchItem("one", photo.original, ItemStatus.COMPLETED, photo),
        BatchItem("two", photo.original.copy(displayName = "unreadable.jpg"), ItemStatus.INTERRUPTED)))
    var retried = false
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(batch = batch, compressedImages = listOf(photo)), it, {}, {}, {}, {}, {}, { retried = true }) }
    compose.onNodeWithText("Pick up where you left off.").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Retry remaining photos").performScrollTo().performClick()
    assertTrue(retried)
    compose.onNodeWithText("Ready for what is next.").assertDoesNotExist()
  }

  @Test @Config(qualifiers = "w320dp-h800dp-mdpi")
  fun largeTextResultsStackSaveAndShareWithoutClipping() {
    val photo = fixture()
    screen(AppScreen.RESULTS, fontScale = 2f) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo)), it, {}, {}, {}, {}, {}, {}) }
    val save = compose.onNodeWithText("Save to gallery").assertIsDisplayed()
    val share = compose.onNodeWithText("Share").assertIsDisplayed()
    assertInsideRoot(save); assertInsideRoot(share)
    assertTrue(share.fetchSemanticsNode().boundsInRoot.top >= save.fetchSemanticsNode().boundsInRoot.bottom)
    compose.onNodeWithContentDescription("Before and after comparison").performScrollTo().assertIsDisplayed()
    capture("results-large-text")
  }

  @Test fun unknownSourceSizeDoesNotProduceInventedBatchSavings() {
    val photo = fixture()
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo,
      photo.copy(historyId = 2, original = photo.original.copy(sizeBytes = 0)))), it, {}, {}, {}, {}, {}, {}) }
    compose.onAllNodesWithText("Original size unknown")[0].assertIsDisplayed()
    compose.onNodeWithText("smaller in total").assertDoesNotExist()
  }

  @Test fun selectionGridOffersIndividualRemoval() {
    val photo = fixture()
    var removed: SelectedImage? = null
    screen(AppScreen.PREVIEW) { PreviewScreen(state(AppScreen.PREVIEW).copy(selectedImages = listOf(photo.original)), it, {}, { removed = it }, {}) }
    capture("selection-light")
    compose.onNodeWithContentDescription("Remove astronaut.png").performClick()
    assertEquals(photo.original, removed)
  }

  @Test fun progressOffersCancellationAndExplainsCompletedPhotosAreKept() {
    val photo = fixture()
    var cancelled = false
    screen(AppScreen.RESULTS) { CompressionProgressScreen(state(AppScreen.RESULTS).copy(isCompressing = true,
      selectedImages = listOf(photo.original), progress = BatchProgress(0, 1)), it, { cancelled = true }) }
    compose.onNodeWithText("Completed photos are kept if you cancel.").assertExists()
    compose.onNodeWithText("Cancel compression").performScrollTo().performClick()
    assertTrue(cancelled)
    capture("progress-light")
  }

  @Test @Config(qualifiers = "w1280dp-h900dp-land-mdpi")
  fun comparisonKeepsZoomAndSliderAcrossLiveWindowResizes() {
    val photo = fixture()
    var width by mutableIntStateOf(600)
    compose.setContent { ImageCompressorTheme(dynamicColor = false) { Surface {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Box(Modifier.width(width.dp).fillMaxHeight().testTag("resizable-window")) {
          AppChrome(AppScreen.IMAGE_COMPARE) { ImageCompareScreen(state(AppScreen.IMAGE_COMPARE).copy(compareImage = photo), it, {}, {}) }
        }
      }
    } } }
    awaitPreviews()
    val slider = compose.onNodeWithContentDescription("Before and after comparison")
    slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.75f) }
    compose.onNodeWithContentDescription("Zoom in").performScrollTo().performClick()
    for (nextWidth in listOf(1280, 840, 360, 600)) {
      compose.runOnIdle { width = nextWidth }
      compose.waitForIdle()
      assertEquals(.75f, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .001f)
      compose.onNodeWithText(" · 150%").assertExists()
      compose.onNodeWithText("Share").assertIsDisplayed()
    }
    capture("compare-split-window")
  }

  @Test @Config(qualifiers = "w1280dp-h900dp-land-mdpi")
  fun invalidSizeDraftSurvivesTabletToPhoneResizeAndSavedStateRestoration() {
    val photo = fixture()
    var width by mutableIntStateOf(1280)
    var settings by mutableStateOf(CompressionSettings(targetBytes = 1_000_000))
    val restoration = StateRestorationTester(compose)
    restoration.setContent { ImageCompressorTheme(dynamicColor = false) { Surface {
      Box(Modifier.width(width.dp).fillMaxHeight()) {
        AppChrome(AppScreen.COMPRESSION_SETTINGS) { CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(
          selectedImages = listOf(photo.original), compressionSettings = settings), it, { settings = it }, {}, {}) }
      }
    } } }
    val field = compose.onNode(hasSetTextAction() and hasText("Maximum size per photo"))
    field.performScrollTo().performTextReplacement("0")
    compose.runOnIdle { width = 600 }
    field.performScrollTo().assertTextContains("0")
    compose.onNodeWithText("Compress photos").assertIsNotEnabled()
    restoration.emulateSavedInstanceStateRestore()
    field.performScrollTo().assertTextContains("0")
    compose.onNodeWithText("Compress photos").assertIsNotEnabled()
    assertEquals(1_000_000L, settings.targetBytes) // Invalid text must not silently change the saved limit.
  }

  @Test @Config(qualifiers = "w360dp-h800dp-mdpi")
  fun sizeEntryKeepsItsDraftAboveKeyboardAndDoneReleasesFocus() {
    val photo = fixture()
    var settings by mutableStateOf(CompressionSettings(targetBytes = 1_000_000))
    screen(AppScreen.COMPRESSION_SETTINGS) { CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(
      selectedImages = listOf(photo.original), compressionSettings = settings), it, { settings = it }, {}, {}) }
    val field = compose.onNode(hasSetTextAction() and hasText("Maximum size per photo"))
    field.performScrollTo().performTextReplacement("0.5")
    compose.runOnIdle {
      val keyboard = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 300))
        .setVisible(WindowInsetsCompat.Type.ime(), true).build()
      ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, keyboard)
    }
    field.performScrollTo().assertIsDisplayed().assertIsFocused()
    assertTrue(field.getUnclippedBoundsInRoot().bottom <= 500.dp)
    field.performImeAction()
    field.assertIsNotFocused()
    assertEquals(500_000L, settings.targetBytes)
    capture("size-limit-keyboard")
    compose.onNodeWithText("Compress photos").performScrollTo().assertIsDisplayed()
    assertTrue(compose.onNodeWithText("Compress photos").getUnclippedBoundsInRoot().bottom <= 500.dp)
  }

  @Test @Config(qualifiers = "w320dp-h800dp-mdpi")
  fun historyActionsWrapAtLargeTextWithoutBreakingTheirLabels() {
    val photo = fixture()
    val row = CompressionHistoryEntity(id = 1, createdAt = 1_700_000_000_000, displayName = "holiday-photo.png",
      originalUri = photo.original.uri.toString(), compressedUri = photo.outputUri.toString(), originalSizeBytes = photo.original.sizeBytes,
      compressedSizeBytes = photo.compressedSizeBytes, width = photo.width, height = photo.height, outputFormat = "JPEG", availability = "RETAINED")
    screen(AppScreen.HISTORY, fontScale = 2f) { HistoryScreen(state(AppScreen.HISTORY).copy(history = listOf(row)), it, {}, {}, {}, {}) }
    for (label in listOf("Share", "Remove")) {
      val node = compose.onNodeWithText(label)
      node.performScrollTo().assertIsDisplayed()
      val layouts = mutableListOf<TextLayoutResult>()
      compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
      assertEquals(1, layouts.single().lineCount)
      val layout = layouts.single()
      assertFalse("$label: size=${layout.size}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, line=${layout.getLineBottom(0)}", layout.hasVisualOverflow)
      val bounds = node.getUnclippedBoundsInRoot()
      assertTrue(bounds.bottom - bounds.top >= 48.dp)
    }
    capture("history-large-text")
  }

  @Test fun removingTheLastPhotoLeavesAnActionableEmptySelection() {
    val photo = fixture()
    var selected by mutableStateOf(listOf(photo.original))
    var add = false
    screen(AppScreen.PREVIEW) { PreviewScreen(state(AppScreen.PREVIEW).copy(selectedImages = selected), it, { add = true },
      { selected = selected.filterNot { current -> current.id == it.id } }, {}) }
    compose.onNodeWithContentDescription("Remove astronaut.png").performClick()
    compose.onNodeWithText("No photos selected").assertIsDisplayed()
    compose.onNodeWithText("Done").assertDoesNotExist()
    compose.onNodeWithText("Add photos").assertIsEnabled().performClick()
    assertTrue(add)
  }

  @Test @Config(qualifiers = "w840dp-h900dp-mdpi")
  fun batchTotalsAreSeparateFromThePreviewAndEachPhotoCanBeOpened() {
    val photo = fixture()
    val second = photo.copy(historyId = 2, original = photo.original.copy(displayName = "second-photo.png"))
    var opened: CompressedImage? = null
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo, second)), it, {}, {}, { opened = it }, {}, {}, {}) }
    compose.onNodeWithText("Batch after: ${(photo.compressedSizeBytes * 2).toReadableSize()}").assertIsDisplayed()
    compose.onNodeWithText("Preview: astronaut.png").assertIsDisplayed()
    compose.onNodeWithText("Save all to gallery").assertIsDisplayed()
    compose.onNodeWithContentDescription("Compare second-photo.png").performScrollTo().performClick()
    assertEquals(2L, opened?.historyId)
    capture("batch-tablet")
  }

  @Test fun missingOriginalKeepsTheResultUsableAndDisablesComparison() {
    val photo = fixture().let { it.copy(original = it.original.copy(uri = Uri.fromFile(File("/missing/original.png")))) }
    var shared = false
    screen(AppScreen.IMAGE_COMPARE) { ImageCompareScreen(state(AppScreen.IMAGE_COMPARE).copy(compareImage = photo), it, {}, { shared = true }) }
    awaitPreviews()
    compose.onNodeWithText("The original preview is unavailable. Select it again if you need to compare.").assertExists()
    compose.onNodeWithContentDescription("Before and after comparison").assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsDisplayed().performClick()
    assertTrue(shared)
  }

  @Test fun swipingAnUnzoomedComparisonScrollsThePage() {
    val photo = fixture()
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo)), it, {}, {}, {}, {}, {}, {}) }
    awaitPreviews()
    val image = compose.onNodeWithContentDescription("Compressed", useUnmergedTree = true)
    val before = image.getUnclippedBoundsInRoot().top
    image.performTouchInput { swipeUp() }
    val after = image.getUnclippedBoundsInRoot().top
    assertTrue("A photo at fit must not trap a vertical page swipe", after < before - 20.dp)
    compose.onNodeWithText("Share").assertIsDisplayed()
  }

  private fun state(screen: AppScreen) = ImageCompressorUiState(screen, isInitializing = false)

  private fun fixture(): CompressedImage {
    val context = RuntimeEnvironment.getApplication()
    val original = File(context.cacheDir, "astronaut.png")
    checkNotNull(javaClass.classLoader?.getResourceAsStream("compression/astronaut.png")).use { input -> original.outputStream().use(input::copyTo) }
    val bitmap = BitmapFactory.decodeFile(original.path)
    val output = File(context.cacheDir, "astronaut-smaller.jpg")
    output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 65, it)) }
    val smaller = BitmapFactory.decodeFile(output.path)
    // Preload actual fixture pixels into Coil's normal cache so capture does not race disk decoding.
    val loader = ImageLoader.Builder(context).build()
    loader.memoryCache!!.set(MemoryCache.Key(Uri.fromFile(original).toString()), MemoryCache.Value(bitmap))
    loader.memoryCache!!.set(MemoryCache.Key(Uri.fromFile(output).toString()), MemoryCache.Value(smaller))
    Coil.setImageLoader(loader)
    return CompressedImage(SelectedImage(Uri.fromFile(original), "astronaut.png", original.length(), bitmap.width, bitmap.height, "PNG"),
      Uri.fromFile(output), output.path, output.length(), smaller.width, smaller.height, OutputFormat.JPEG, historyId = 1)
  }

  private fun assertInsideRoot(node: SemanticsNodeInteraction) {
    val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
    val bounds = node.fetchSemanticsNode().boundsInRoot
    assertTrue("Control must fit in the available width", bounds.left >= root.left && bounds.right <= root.right)
  }

  private fun awaitPreviews() {
    compose.waitUntil(timeoutMillis = 10_000) {
      compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Loading preview"), useUnmergedTree = true)
        .fetchSemanticsNodes().isEmpty()
    }
    compose.waitForIdle()
  }

  private fun capture(name: String) {
    awaitPreviews()
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
