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
      var state by mutableStateOf(ImageCompressorUiState(AppScreen.HOME, isInitializing = false))
      var shares = 0
      enableAccessibilityChecks()
      setContent {
        ImageCompressorTheme(darkTheme = dark, dynamicColor = false) { Surface {
          AppChrome(state.screen, onBack = { state = state.copy(screen = AppScreen.HOME) },
            onNavigate = { state = state.copy(screen = it) }) { modifier ->
            when (state.screen) {
              AppScreen.HOME -> HomeScreen(state, modifier,
                { state = state.copy(screen = AppScreen.COMPRESSION_SETTINGS, selectedImages = listOf(selected)) }, {}, {})
              AppScreen.COMPRESSION_SETTINGS -> CompressionSettingsScreen(state, modifier,
                { state = state.copy(compressionSettings = it) },
                { state = state.copy(screen = AppScreen.RESULTS, compressedImages = listOf(photo)) }, {})
              AppScreen.RESULTS -> ResultsScreen(state, modifier, {}, { shares++ }, {}, {}, {}, {})
              AppScreen.SETTINGS -> SettingsScreen(state, modifier,
                { state = state.copy(themePreference = it) }, { state = state.copy(dynamicColor = it) }, {})
              else -> error("Unexpected screen in accessibility smoke test")
            }
          }
        } }
      }
      // Actions trigger root-view checks; no error checks are suppressed.
      onNodeWithText("Select photos").performClick()
      onNodeWithText("Advanced").performScrollTo().performClick()
      onNodeWithContentDescription("Encoder quality").performScrollTo()
        .performSemanticsAction(SemanticsActions.SetProgress) { it(82f) }
      onNodeWithText("Compress photos").performClick()
      waitUntil(timeoutMillis = 10_000) { onAllNodes(SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Loading preview"
      )).fetchSemanticsNodes().isEmpty() }
      onNodeWithContentDescription("Before and after comparison").performScrollTo()
        .performSemanticsAction(SemanticsActions.SetProgress) { it(.7f) }
      onNodeWithContentDescription("Zoom in").performScrollTo().performClick()
      onNodeWithText("Share").performClick()
      assertEquals(1, shares)
      onNodeWithContentDescription("Back").performClick()
      onNodeWithText("Settings").performClick()
      onNodeWithText("Use device colors").performScrollTo().performClick()
    } finally { directory.deleteRecursively() }
  }
}
