package com.example.imagecompressor.validation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.test.accessibility.enableAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.ui.*
import com.example.imagecompressor.ui.state.*
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Accessibility Test Framework checks. API 34+; unsupported by Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class AccessibilitySmokeTest {
  @Test fun lightScreensPassAccessibilityChecks() = checkScreens(dark = false)
  @Test fun darkScreensPassAccessibilityChecks() = checkScreens(dark = true)

  private fun checkScreens(dark: Boolean) = runAndroidComposeUiTest<ComponentActivity> {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = File(instrumentation.targetContext.cacheDir, "accessibility-${UUID.randomUUID()}")
    check(directory.mkdirs())
    try {
      val original = File(directory, "photo.png")
      instrumentation.context.assets.open("compression/astronaut.png").use { source -> original.outputStream().use(source::copyTo) }
      val bitmap = checkNotNull(BitmapFactory.decodeFile(original.path))
      val output = File(directory, "photo.jpg")
      try { output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) } }
      finally { bitmap.recycle() }
      val selected = SelectedImage(Uri.fromFile(original), "Photo", original.length(), 512, 512, "PNG")
      val photo = CompressedImage(selected, Uri.fromFile(output), output.path, output.length(), 512, 512, OutputFormat.JPEG)
      val settings = CompressionSettings(quality = 80)
      val measured = EditorPreview.Ready(photo, settings)
      val recent = CompressionHistoryEntity(id = 1, createdAt = 1_700_000_000_000, displayName = "Photo",
        originalUri = selected.id, compressedUri = photo.outputUri.toString(), localPath = output.path,
        originalSizeBytes = selected.sizeBytes, compressedSizeBytes = photo.compressedSizeBytes,
        width = 512, height = 512, outputFormat = "JPEG", availability = "RETAINED")
      var state by mutableStateOf(ImageCompressorUiState(AppScreen.ONBOARDING, isInitializing = false,
        compressionSettings = settings, history = listOf(recent)))
      var saves = 0
      var shares = 0
      enableAccessibilityChecks()
      setContent {
        ImageCompressorTheme(darkTheme = dark, dynamicColor = false) { Surface {
          AppChrome(state.screen, onBack = { state = state.copy(screen = AppScreen.HOME) },
            onNavigate = { state = state.copy(screen = it) }) { modifier ->
            when (state.screen) {
              AppScreen.ONBOARDING -> OnboardingScreen(modifier,
                { state = state.copy(screen = AppScreen.HOME) }, { state = state.copy(screen = AppScreen.HOME) })
              AppScreen.HOME -> HomeScreen(state, modifier,
                { state = state.copy(screen = AppScreen.COMPRESSION_SETTINGS, selectedImages = listOf(selected),
                  editorPreview = measured) }, { state = state.copy(screen = AppScreen.COMPRESSION_SETTINGS) },
                { state = state.copy(screen = AppScreen.HISTORY) })
              AppScreen.PREVIEW -> PreviewScreen(state, modifier, {}, {},
                { state = state.copy(screen = AppScreen.COMPRESSION_SETTINGS) })
              AppScreen.HISTORY -> HistoryScreen(state, modifier, {}, { shares++ }, {}, {})
              // This smoke test supplies a measured fixture and checks the actual controls. The
              // real ViewModel, persistence and byte-identical exports are covered by NativeWorkflowTest.
              AppScreen.COMPRESSION_SETTINGS -> CompressionSettingsScreen(state, modifier,
                { state = state.copy(compressionSettings = it,
                  editorPreview = if (it == measured.settings) measured else EditorPreview.NotCalculated) },
                { error("A single photo must not start a batch") }, { state = state.copy(screen = AppScreen.PREVIEW) },
                onSave = { saves++ }, onShare = { shares++ })
              AppScreen.RESULTS -> ResultsScreen(state, modifier, {}, { shares++ },
                { state = state.copy(screen = AppScreen.IMAGE_COMPARE, compareImage = it) }, {}, {}, {})
              AppScreen.IMAGE_COMPARE -> ImageCompareScreen(state, modifier, {}, { shares++ })
              AppScreen.SETTINGS -> SettingsScreen(state, modifier,
                { state = state.copy(themePreference = it) }, { state = state.copy(dynamicColor = it) }, {})
            }
          }
        } }
      }
      // Actions trigger root-view checks; no error checks are suppressed.
      onNodeWithText("Not now").performScrollTo().performClick()
      onNodeWithText("Choose photos").performClick()
      onNodeWithText("Advanced").performScrollTo().performClick()
      onNodeWithContentDescription("Photo detail").performScrollTo()
        .performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
      onNodeWithText("Make smaller").assertDoesNotExist()
      onNodeWithText("Save photo").performScrollTo().performClick()
      assertEquals(1, saves)
      onNodeWithText("Compare with original").performScrollTo().performClick()
      onNodeWithTag("editor-comparison-dialog").assertIsDisplayed()
      waitUntil(timeoutMillis = 10_000) { onAllNodes(SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Loading preview"
      )).fetchSemanticsNodes().isEmpty() }
      onNodeWithContentDescription("Before and after comparison").performScrollTo()
        .performSemanticsAction(SemanticsActions.SetProgress) { it(.7f) }
      onNodeWithContentDescription("Zoom in").performScrollTo().performClick()
      onNodeWithText("Close").performClick()
      onNodeWithTag("editor-comparison-dialog").assertDoesNotExist()
      onNodeWithText("Share").performScrollTo().performClick()
      assertEquals(1, shares)
      onNodeWithContentDescription("Back").performClick()
      onNodeWithText("Settings").performClick()
      onNodeWithText("Match phone colors").performScrollTo().performClick()
      onNodeWithText("Recent").performClick()
      onNodeWithContentDescription("Share Photo").performScrollTo().performClick()
      assertEquals(2, shares)
      onNodeWithContentDescription("Remove Photo").performScrollTo().performClick()
      onNodeWithText("Keep").performClick()
      onNodeWithText("Home").performClick()
      onNodeWithText("Continue · 1 photo").performScrollTo().performClick()
      onNodeWithText("Change").performScrollTo().performClick()
      onNodeWithContentDescription("Remove Photo").assertIsEnabled()
      onNodeWithText("Choose size").performScrollTo().performClick()
    } finally { directory.deleteRecursively() }
  }
}
