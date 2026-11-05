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
import androidx.compose.ui.semantics.getOrNull
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
  @get:Rule(order = 0) val hostActivity = ComposeHostActivityRule()
  @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun homeKeepsPreviousResultsBehindAnExplicitAction() {
    val photo = fixture()
    val batch = BatchSnapshot("previous", CompressionSettings(), BatchStatus.COMPLETED,
      listOf(BatchItem("one", photo.original, ItemStatus.COMPLETED, photo)))
    var reviews = 0
    screen(AppScreen.HOME) { HomeScreen(state(AppScreen.HOME).copy(batch = batch, compressedImages = listOf(photo)), it,
      {}, {}, {}, { reviews++ }) }
    compose.onNodeWithText("Choose photos").assertIsDisplayed()
    assertEquals(0, reviews)
    compose.onNodeWithText("Review previous photos").performClick()
    assertEquals(1, reviews)
    capture("home-previous-results")
  }

  @Test fun homeSeparatesContinuingFromChoosingNewPhotos() {
    val photo = fixture()
    var continued = false
    var replaced = false
    screen(AppScreen.HOME) { HomeScreen(state(AppScreen.HOME).copy(selectedImages = listOf(photo.original)), it,
      { replaced = true }, { continued = true }, {}) }
    compose.onNodeWithText("Continue · 1 photo").performClick()
    compose.onNodeWithText("Choose new photos").performClick()
    assertTrue(continued && replaced)
    compose.onNodeWithText("Add photos").assertDoesNotExist()
    capture("step5-home-phone")
  }

  @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
  fun batchSampleCanChangeWithoutChangingTheSharedLimit() {
    val photo = fixture()
    val sources = batchSources(photo)
    val settings = CompressionSettings(targetBytes = 500_000)
    var selected by mutableStateOf(sources.first())
    screen(AppScreen.COMPRESSION_SETTINGS, dark = true) {
      CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(selectedImages = sources,
        editorImageId = selected.id, compressionSettings = settings,
        editorPreview = EditorPreview.Ready(photo.copy(original = selected, targetBytes = 500_000), settings)), it,
        {}, {}, {}, onPreviewPhoto = { selected = it })
    }
    val second = compose.onNodeWithContentDescription("Preview photo 2 of 2, astronaut-smaller.jpg")
    second.performClick().assertIsSelected()
    compose.onNodeWithContentDescription("Preview photo 1 of 2, astronaut.png").assertIsNotSelected()
    compose.onNodeWithText("Preview 2 of 2 · settings apply to every photo").assertIsDisplayed()
    compose.onNodeWithText("500 KB").assertIsSelected()
    compose.onNodeWithText("Prepare 2 photos").performScrollTo().assertIsEnabled()
    capture("step5-batch-tablet-dark")
    compose.onNodeWithText("Compare with original").performScrollTo().performClick()
    compose.onNodeWithText("Close").performClick()
    assertEquals(sources[1], selected)
  }

  @Test fun editorComparisonReopensAfterRestorationWhenTheFreshPreviewIsReady() {
    val photo = fixture()
    val settings = CompressionSettings()
    var preview: EditorPreview by mutableStateOf(EditorPreview.Ready(photo, settings))
    val restoration = StateRestorationTester(compose)
    restoration.setContent { ImageCompressorTheme(dynamicColor = false) { Surface {
      AppChrome(AppScreen.COMPRESSION_SETTINGS) {
        CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(selectedImages = listOf(photo.original),
          compressionSettings = settings, editorPreview = preview), it, {}, {}, {})
      }
    } } }
    compose.onNodeWithText("Compare with original").performScrollTo().performClick()
    compose.onNodeWithText("Close").assertIsDisplayed()
    compose.runOnIdle { preview = EditorPreview.Updating() }
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithText("Close").assertDoesNotExist()
    compose.runOnIdle { preview = EditorPreview.Ready(photo, settings) }
    compose.onNodeWithText("Close").assertIsDisplayed().performClick()
    compose.onNodeWithText("Save photo").assertExists()
  }

  @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
  fun largeTextSettingsNavigationAndThemeRowsHaveUnclippedLabels() {
    screen(AppScreen.SETTINGS, fontScale = 2f) { SettingsScreen(state(AppScreen.SETTINGS), it, {}, {}, {}) }
    capture("step5-navigation-large-text")
    for (label in listOf("Home", "Recent", "Settings")) {
      // Settings is also the page title; inspect each visible label independently.
      compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        layouts.forEach {
          assertFalse("Navigation label $label clips vertically", it.didOverflowHeight)
          // GetTextLayoutResult reconstructs centered paragraphs at the maximum constraint
          // (280px here), even when the painted text node wraps to 78px. Check glyph width,
          // not the reconstructed paragraph's alignment offset; allow pixel rounding.
          val lineWidth = it.getLineRight(0) - it.getLineLeft(0)
          assertTrue("Navigation label $label clips horizontally", lineWidth <= it.size.width + 1f)
          assertEquals("Navigation words must stay intact", 1, it.lineCount)
        }
      }
    }
    for (label in listOf("System", "Light", "Dark", "Match phone colors")) {
      compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
      val bounds = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
      assertTrue(bounds.bottom - bounds.top >= 48.dp)
    }
    capture("step5-settings-large-text")
  }

  @Test @Config(qualifiers = "w480dp-h240dp-land-mdpi")
  fun startupStatusRemainsReachableInAShortWindowWithLargeText() {
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
        ImageCompressorTheme(dynamicColor = false) { Surface { SplashScreen() } }
      }
    }
    compose.onNodeWithText("Getting things ready…").performScrollTo().assertIsDisplayed()
    capture("step5-startup-short-window")
  }

  @Test fun recentUnavailablePhotoHasARecoveryActionAndNamedItemActions() {
    val photo = fixture()
    val row = CompressionHistoryEntity(id = 1, createdAt = 1_700_000_000_000, displayName = "holiday.png",
      originalUri = photo.original.uri.toString(), compressedUri = photo.outputUri.toString(),
      originalSizeBytes = photo.original.sizeBytes, compressedSizeBytes = photo.compressedSizeBytes,
      width = 512, height = 512, outputFormat = "JPEG", availability = "UNAVAILABLE")
    var choose = false
    screen(AppScreen.HISTORY) { HistoryScreen(state(AppScreen.HISTORY).copy(history = listOf(row)), it,
      {}, {}, {}, { choose = true }) }
    compose.onNodeWithContentDescription("Share holiday.png").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Remove holiday.png").assertHasClickAction()
    compose.onNodeWithText("Choose new photos").performScrollTo().performClick()
    assertTrue(choose)
    capture("step5-recent-recovery")
  }

  @Test fun batchOverLimitWarningAndExactBytesRemainVisibleBeforeExport() {
    val photo = fixture().copy(targetBytes = 1)
    val second = photo.copy(historyId = 2, original = photo.original.copy(displayName = "second.png"))
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo, second)),
      it, {}, {}, {}, {}, {}, {}) }
    compose.onNodeWithText("2 photos are still over the limit. Review them before saving or sharing.").assertIsDisplayed()
    compose.onAllNodesWithText("Verified size: ${String.format(java.util.Locale.US, "%,d", photo.compressedSizeBytes)} bytes")[0].performScrollTo().assertIsDisplayed()
    capture("step5-batch-review-phone")
  }

  @Test fun failedBatchOffersSizeAdjustmentEvenWhenNoPhotoSucceeded() {
    val photo = fixture().original
    val batch = BatchSnapshot("failed", CompressionSettings(), BatchStatus.COMPLETED,
      listOf(BatchItem("one", photo, ItemStatus.FAILED, error = "Could not read this photo. Choose it again.")))
    var adjusted = false
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(batch = batch, selectedImages = listOf(photo)),
      it, {}, {}, {}, {}, {}, {}, onAdjustSize = { adjusted = true }) }
    compose.onNodeWithText("Try unfinished photos again").performScrollTo().assertIsEnabled()
    compose.onNodeWithText("Try another size").performScrollTo().performClick()
    assertTrue(adjusted)
    compose.onNodeWithText("Save all").assertDoesNotExist()
  }

  private fun screen(screen: AppScreen, dark: Boolean = false, fontScale: Float = 1f, content: @Composable (Modifier) -> Unit) {
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
        ImageCompressorTheme(darkTheme = dark, dynamicColor = false) { Surface { AppChrome(screen, content = content) } }
      }
    }
  }

  @Test fun homeShowsSelectionAndPrivateProcessing() {
    screen(AppScreen.HOME) { HomeScreen(state(AppScreen.HOME), it, {}, {}, {}) }
    compose.onNodeWithText("Choose photos").assertIsDisplayed().assertHasClickAction()
    compose.onNodeWithText("Only on your device. Your originals stay safe.").assertExists()
    capture("home-light")
  }

  @Test fun onboardingCanStartWithoutPagingThroughATour() {
    var started = false
    screen(AppScreen.ONBOARDING) { OnboardingScreen(it, { started = true }, {}) }
    compose.onNodeWithText("Choose photos").performScrollTo().performClick()
    assertTrue(started)
    capture("onboarding-light")
  }

  @Test fun batchPresetsHideAdvancedAndRejectInvalidUploadLimit() {
    val photo = fixture()
    var settings by mutableStateOf(CompressionSettings())
    var requested: CompressionSettings? = null
    screen(AppScreen.COMPRESSION_SETTINGS) { CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(
      selectedImages = batchSources(photo), compressionSettings = settings), it, { settings = it }, { requested = settings }, {}) }
    compose.onNodeWithText("Picture detail", substring = true).assertDoesNotExist()
    compose.onNodeWithText("Prepare 2 photos").assertIsDisplayed()
    capture("presets-light")
    compose.onNodeWithText("Custom").performScrollTo().performClick()
    val field = compose.onNode(hasSetTextAction() and hasText("Size limit"))
    field.performScrollTo().performTextReplacement("0")
    compose.onNodeWithText("Prepare 2 photos").assertIsNotEnabled()
    field.performScrollTo().performTextReplacement("0.5")
    compose.onNodeWithText("Prepare 2 photos").assertIsDisplayed().assertIsEnabled().performClick()
    assertEquals(500_000L, requested?.targetBytes)
  }

  @Test fun resultsLeadWithActualSizeAndKeepComparisonOptional() {
    val photo = fixture()
    var saved = false; var shared = false; var compared: CompressedImage? = null
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo)), it,
      { saved = true }, { shared = true }, { compared = it }, {}, {}, {}) }
    compose.onNodeWithText("New size").assertIsDisplayed()
    compose.onNodeWithText(photo.compressedSizeBytes.toReadableSize()).assertIsDisplayed()
    compose.onNodeWithContentDescription("Before and after comparison").assertDoesNotExist()
    compose.onNodeWithText("Save photo").assertIsDisplayed().performClick()
    compose.onNodeWithText("Share").assertIsDisplayed().performClick()
    assertTrue(saved && shared)
    capture("results-light")
    compose.onNodeWithText("Compare with original").performScrollTo().performClick()
    assertEquals(photo, compared)
    compose.onNodeWithText("512 × 512 · JPEG").assertDoesNotExist()
    compose.onNodeWithText("Photo details").performScrollTo().performClick()
    compose.onNodeWithText("512 × 512 · JPEG").performScrollTo().assertIsDisplayed()
  }

  @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
  fun tabletShowsComparisonAndDetailsInSeparatePanes() {
    val photo = fixture()
    screen(AppScreen.IMAGE_COMPARE, dark = true) { ImageCompareScreen(state(AppScreen.IMAGE_COMPARE).copy(compareImage = photo), it, {}, {}) }
    val imageBounds = compose.onNodeWithContentDescription("After", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val details = compose.onNodeWithText("Photo details").fetchSemanticsNode().boundsInRoot
    assertTrue("Details must sit to the right of the image", details.left >= imageBounds.right)
    compose.onNodeWithText("Share").assertIsDisplayed()
    capture("compare-tablet-dark")
  }

  @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
  fun narrowLargeTextKeepsActionsReachableWithoutHorizontalOverflow() {
    screen(AppScreen.HOME, fontScale = 2f) { HomeScreen(state(AppScreen.HOME), it, {}, {}, {}) }
    compose.onNodeWithText("Choose photos").performScrollTo().assertIsDisplayed()
    assertInsideRoot(compose.onNodeWithText("Choose photos"))
    capture("home-large-text")
  }

  @Test @Config(qualifiers = "w640dp-h320dp-land-mdpi")
  fun shortLandscapeBatchSettingsScrollToThePrimaryAction() {
    val photo = fixture()
    screen(AppScreen.COMPRESSION_SETTINGS) { CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(selectedImages = batchSources(photo)), it, {}, {}, {}) }
    compose.onNodeWithText("Prepare 2 photos").performScrollTo().assertIsDisplayed()
    assertInsideRoot(compose.onNodeWithText("Prepare 2 photos"))
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
    compose.onNodeWithText("Match phone colors").performClick()
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
    compose.onNodeWithText("Try unfinished photos again").performScrollTo().performClick()
    assertTrue(retried)
    compose.onNodeWithText("Ready to share").assertDoesNotExist()
  }

  @Test @Config(qualifiers = "w320dp-h800dp-mdpi")
  fun largeTextResultsStackSaveAndShareWithoutClipping() {
    val photo = fixture()
    screen(AppScreen.RESULTS, fontScale = 2f) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo)), it, {}, {}, {}, {}, {}, {}) }
    val save = compose.onNodeWithText("Save photo").performScrollTo().assertIsDisplayed()
    val share = compose.onNodeWithText("Share").performScrollTo().assertIsDisplayed()
    assertInsideRoot(save); assertInsideRoot(share)
    assertTrue(share.fetchSemanticsNode().boundsInRoot.top >= save.fetchSemanticsNode().boundsInRoot.bottom)
    compose.onNodeWithText("Compare with original").performScrollTo().assertIsDisplayed()
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
    compose.onNodeWithText("Stop").performScrollTo().performClick()
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
          selectedImages = batchSources(photo), compressionSettings = settings), it, { settings = it }, {}, {}) }
      }
    } } }
    compose.onNodeWithText("Custom").performScrollTo().performClick()
    val field = compose.onNode(hasSetTextAction() and hasText("Size limit"))
    field.performScrollTo().performTextReplacement("0")
    compose.runOnIdle { width = 600 }
    field.performScrollTo().assertTextContains("0")
    compose.onNodeWithText("Prepare 2 photos").assertIsNotEnabled()
    restoration.emulateSavedInstanceStateRestore()
    field.performScrollTo().assertTextContains("0")
    compose.onNodeWithText("Prepare 2 photos").assertIsNotEnabled()
    assertEquals(1_000_000L, settings.targetBytes) // Invalid text must not silently change the saved limit.
  }

  @Test @Config(qualifiers = "w360dp-h800dp-mdpi")
  fun sizeEntryKeepsItsDraftAboveKeyboardAndDoneReleasesFocus() {
    val photo = fixture()
    var settings by mutableStateOf(CompressionSettings(targetBytes = 1_000_000))
    screen(AppScreen.COMPRESSION_SETTINGS) { CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(
      selectedImages = batchSources(photo), compressionSettings = settings), it, { settings = it }, {}, {}) }
    compose.onNodeWithText("Custom").performScrollTo().performClick()
    val field = compose.onNode(hasSetTextAction() and hasText("Size limit"))
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
    compose.onNodeWithText("Prepare 2 photos").performScrollTo().assertIsDisplayed()
    assertTrue(compose.onNodeWithText("Prepare 2 photos").getUnclippedBoundsInRoot().bottom <= 500.dp)
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
    compose.onNodeWithText("Choose size").assertDoesNotExist()
    compose.onNodeWithText("Add photos").assertIsEnabled().performClick()
    assertTrue(add)
  }

  @Test @Config(qualifiers = "w840dp-h900dp-mdpi")
  fun batchTotalsAreSeparateFromThePreviewAndEachPhotoCanBeOpened() {
    val photo = fixture()
    val second = photo.copy(historyId = 2, original = photo.original.copy(displayName = "second-photo.png"))
    var opened: CompressedImage? = null
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo, second)), it, {}, {}, { opened = it }, {}, {}, {}) }
    compose.onNodeWithText("Total size").assertIsDisplayed()
    compose.onNodeWithText((photo.compressedSizeBytes * 2).toReadableSize()).assertIsDisplayed()
    compose.onNodeWithText("Preview: astronaut.png").assertIsDisplayed()
    compose.onNodeWithText("Save all").assertIsDisplayed()
    compose.onNodeWithContentDescription("Compare second-photo.png").performScrollTo().performClick()
    assertEquals(2L, opened?.historyId)
    capture("batch-tablet")
  }

  @Test fun missingOriginalKeepsTheResultUsableAndDisablesComparison() {
    val photo = fixture().let { it.copy(original = it.original.copy(uri = Uri.fromFile(File("/missing/original.png")))) }
    var shared = false
    screen(AppScreen.IMAGE_COMPARE) { ImageCompareScreen(state(AppScreen.IMAGE_COMPARE).copy(compareImage = photo), it, {}, { shared = true }) }
    awaitPreviews()
    compose.onNodeWithText("The original is no longer available to compare. You can still save or share this copy.").assertExists()
    compose.onNodeWithContentDescription("Before and after comparison").assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsDisplayed().performClick()
    assertTrue(shared)
  }

  @Test fun swipingAnUnzoomedComparisonScrollsThePage() {
    val photo = fixture()
    screen(AppScreen.IMAGE_COMPARE) { ImageCompareScreen(state(AppScreen.IMAGE_COMPARE).copy(compareImage = photo), it, {}, {}) }
    awaitPreviews()
    val image = compose.onNodeWithContentDescription("After", useUnmergedTree = true)
    val before = image.getUnclippedBoundsInRoot().top
    image.performTouchInput { swipeUp() }
    val after = image.getUnclippedBoundsInRoot().top
    assertTrue("A photo at fit must not trap a vertical page swipe", after < before - 20.dp)
    compose.onNodeWithText("Share").assertIsDisplayed()
  }

  @Test fun batchQuickSizeChoicesUseExactBytesAndRepairAnInvalidDraft() {
    val photo = fixture()
    var settings by mutableStateOf(CompressionSettings())
    var requested: CompressionSettings? = null
    screen(AppScreen.COMPRESSION_SETTINGS) { CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(
      selectedImages = batchSources(photo), compressionSettings = settings), it, { settings = it }, { requested = settings }, {}) }
    compose.onNodeWithContentDescription("Photo detail").assertIsEnabled()
    compose.onNodeWithText("Prepare 2 photos").performClick()
    assertEquals(CompressionSettings(), requested)
    compose.onNodeWithText("Custom").performScrollTo().performClick()
    val field = compose.onNode(hasSetTextAction() and hasText("Size limit"))
    field.performScrollTo().performTextReplacement("0")
    compose.onNodeWithText("Prepare 2 photos").assertIsNotEnabled()
    compose.onNodeWithText("200 KB").performScrollTo().performClick()
    compose.onNodeWithText("200 KB").assertIsSelected()
    compose.onNodeWithText("Custom").performScrollTo().performClick()
    field.assertTextContains("0.2")
    compose.onNodeWithText("KB").performScrollTo().performClick()
    field.assertTextContains("200")
    compose.onNodeWithText("Prepare 2 photos").assertIsDisplayed().performClick()
    assertEquals(200_000L, requested?.targetBytes)
    capture("size-limit-light")
  }

  @Test fun rememberedOptionsAreExplainedAndResetKeepsTheChosenLimit() {
    val photo = fixture()
    var settings by mutableStateOf(CompressionSettings(targetBytes = 200_000, outputFormat = OutputFormat.PNG, quality = 92))
    screen(AppScreen.COMPRESSION_SETTINGS) { CompressionSettingsScreen(state(AppScreen.COMPRESSION_SETTINGS).copy(
      selectedImages = listOf(photo.original), compressionSettings = settings), it, { settings = it }, {}, {}) }
    compose.onNodeWithText("File type").assertDoesNotExist()
    compose.onNodeWithText("Your saved choices are being used.").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Use recommended choices").performScrollTo().performClick()
    assertEquals(CompressionSettings(targetBytes = 200_000), settings)
    compose.onNodeWithText("Your saved choices are being used.").assertDoesNotExist()
    compose.onNodeWithText("Advanced").performScrollTo().performClick()
    compose.onNodeWithText("File type").performScrollTo().assertIsDisplayed()
  }

  @Test fun unmetLimitIsVisibleWithoutOpeningDetailsAndOffersAnotherSize() {
    val photo = fixture().copy(targetBytes = 1)
    var changedSize = false
    screen(AppScreen.RESULTS) { ResultsScreen(state(AppScreen.RESULTS).copy(compressedImages = listOf(photo)), it,
      {}, {}, {}, {}, {}, {}, onAdjustSize = { changedSize = true }) }
    compose.onNodeWithText("Still over your 1 B limit").assertIsDisplayed()
    compose.onNodeWithText("We could not make this photo small enough. Try another size, or use a different photo.").assertIsDisplayed()
    compose.onNodeWithText("Try another size").performScrollTo().performClick()
    assertTrue(changedSize)
    compose.onNodeWithText("Save photo").assertIsEnabled()
  }

  @Test fun comparisonSupportsSliderZoomAndResetOnPhone() {
    val photo = fixture()
    screen(AppScreen.IMAGE_COMPARE) { ImageCompareScreen(state(AppScreen.IMAGE_COMPARE).copy(compareImage = photo), it, {}, {}) }
    awaitPreviews()
    val slider = compose.onNodeWithContentDescription("Before and after comparison")
    slider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.75f)) }
    assertEquals(.75f, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .001f)
    compose.onNodeWithContentDescription("Zoom in").performScrollTo().performClick()
    compose.onNodeWithText(" · 150%").assertExists()
    compose.onNodeWithText("Reset view", substring = true).performClick()
    compose.onNodeWithText(" · 100%").assertExists()
    capture("compare-phone")
  }

  private fun state(screen: AppScreen) = ImageCompressorUiState(screen, isInitializing = false)

  /** Two real source URIs keep explicit batch preparation coverage scoped to the batch flow. */
  private fun batchSources(photo: CompressedImage) = listOf(photo.original,
    SelectedImage(photo.outputUri, "astronaut-smaller.jpg", photo.compressedSizeBytes,
      photo.width, photo.height, photo.format.name))

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
