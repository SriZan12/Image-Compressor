package com.example.imagecompressor.ui.design

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.Coil
import coil.ImageLoader
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.TargetSizeStatus
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.ui.AppChrome
import com.example.imagecompressor.ui.CompressionSettingsScreen
import com.example.imagecompressor.ui.editor.LiveEditorSession
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.example.imagecompressor.ui.state.PhotoEditorState
import com.example.imagecompressor.util.ImageCompressor
import com.example.imagecompressor.util.TemporaryImageProcessor
import com.example.imagecompressor.util.TemporaryPreview
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Actual temporary engine files rendered through the live session and production screen adapter.
 * The controlled gate makes an in-flight state observable; it does not invent file sizes or pixels.
 * Native host rendering is layout/interaction evidence, not an Android-device latency benchmark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveEditorRenderTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val context get() = RuntimeEnvironment.getApplication()
  private lateinit var loader: ImageLoader
  private var activeSession: LiveEditorSession? = null

  @Before fun prepareNativeImages() {
    val provider = checkNotNull(context.packageManager.resolveContentProvider(
      "${context.packageName}.fileprovider", PackageManager.GET_META_DATA))
    Robolectric.buildContentProvider(FileProvider::class.java).create(provider)
    // Software Canvas is specific to the native host renderer. Coil still fetches and decodes files.
    loader = ImageLoader.Builder(context).allowHardware(false).build()
    Coil.setImageLoader(loader)
  }

  @After fun releaseSession() {
    activeSession?.close()
    if (::loader.isInitialized) loader.shutdown()
    Coil.reset()
  }

  @Test fun choosingALimitAutomaticallyChecksARealFileWithoutAnotherCompressionTap() {
    val harness = editor(quality = 100, gateTarget = 100_000)
    screen(harness)
    val initial = awaitResult(harness, 500_000)
    assertTrue("The fixture must exercise an actual reduction to the smaller limit",
      initial.image.compressedSizeBytes > 100_000)
    assertMeasured(harness, initial)
    compose.onNodeWithText("Fits your 500 KB limit").assertIsDisplayed()
    compose.onNodeWithText("Make smaller").assertDoesNotExist()
    val preparations = harness.processor.preparations.get()
    compose.onNodeWithText("Save photo").assertIsEnabled().performClick()
    compose.onNodeWithText("Share").assertIsEnabled().performClick()
    assertEquals(listOf(initial.image), harness.saveRequests)
    assertEquals(listOf(initial.image), harness.shareRequests)
    assertEquals("Export callbacks use the checked result without starting another encode",
      preparations, harness.processor.preparations.get())
    val summaryHeight = compose.onNodeWithTag("editor-size-summary").getUnclippedBoundsInRoot().let { it.bottom - it.top }
    capture("live-editor-ready-phone", harness)

    compose.onNodeWithText("100 KB").performScrollTo().performClick()
    compose.waitUntil(10_000) { harness.processor.started.isCompleted }
    val updating = harness.session.state.value as EditorPreview.Updating
    assertNull("An unfinished encode has no invented estimate", updating.estimatedBytes)
    assertEquals(initial.image, updating.previousImage)
    assertNull(harness.presentation().currentResult)
    compose.onNodeWithText("Save photo").assertIsNotEnabled()
    compose.onNodeWithText("Share").assertIsNotEnabled()
    compose.onNodeWithText("Updating size…").assertIsDisplayed()
    compose.onNodeWithText("Fits your 500 KB limit").assertDoesNotExist()
    compose.onNodeWithText(verifiedLabel(initial)).assertDoesNotExist()
    assertEquals("Updating must preserve summary height so the slider stays in place", summaryHeight,
      compose.onNodeWithTag("editor-size-summary").getUnclippedBoundsInRoot().let { it.bottom - it.top })
    compose.onNodeWithContentDescription("Photo preview").performScrollTo()
    capture("live-editor-updating-phone", harness)

    harness.processor.release.complete(Unit)
    val result = awaitResult(harness, 100_000)
    assertMeasured(harness, result)
    assertEquals(summaryHeight, compose.onNodeWithTag("editor-size-summary").getUnclippedBoundsInRoot().let { it.bottom - it.top })
    assertTrue(result.image.compressedSizeBytes <= 100_000)
    assertTrue(result.image.compressedSizeBytes < initial.image.compressedSizeBytes)
    compose.onNodeWithText("Fits your 100 KB limit").assertIsDisplayed()
    compose.onNodeWithText("Save photo").assertIsEnabled().performClick()
    assertEquals(result.image, harness.saveRequests.last())
    assertEquals("Choosing a target must not invoke the retained batch action", 0, harness.compressTaps)
    assertArrayEquals("Preview work must preserve the original", harness.originalBytes, harness.sourceFile.readBytes())
  }

  @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
  fun tabletShowsTheRealMeasuredPreviewInTwoPanes() {
    val harness = editor()
    screen(harness, dark = true)
    val result = awaitResult(harness, 500_000)
    assertMeasured(harness, result)
    awaitImage()
    val preview = compose.onNodeWithTag("editor-photo-frame").getUnclippedBoundsInRoot()
    val controls = compose.onNodeWithTag("editor-size-summary").getUnclippedBoundsInRoot()
    assertTrue("The tablet retains a generous photo beside the controls", controls.left >= preview.right)
    compose.onNodeWithText("Save photo").performScrollTo().assertIsDisplayed().assertIsEnabled()
    compose.onNodeWithText("Share").assertIsDisplayed().assertIsEnabled()
    compose.onNodeWithText("Make smaller").assertDoesNotExist()
    capture("live-editor-tablet-dark", harness)
  }

  @Test fun comparisonUsesTheCurrentTemporaryFileWithoutSavingOrReencoding() {
    val harness = editor()
    screen(harness)
    val result = awaitResult(harness, 500_000)
    val preparations = harness.processor.preparations.get()
    compose.onNodeWithText("Compare with original").performScrollTo().performClick()
    compose.onNodeWithTag("editor-comparison-dialog").assertIsDisplayed()
    val slider = compose.onNodeWithContentDescription("Before and after comparison")
    compose.waitUntil(10_000) {
      !slider.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
    }
    slider.assertIsEnabled().assertHeightIsAtLeast(48.dp)
    slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.7f)) }
    val inComparison = hasAnyAncestor(hasTestTag("editor-comparison-dialog"))
    compose.onAllNodes(hasText("Save photo") and inComparison).assertCountEquals(0)
    compose.onAllNodes(hasText("Share") and inComparison).assertCountEquals(0)
    capture("live-editor-comparison-phone", harness, dialog = true)
    compose.onNodeWithText("Close").assertIsDisplayed().performClick()
    compose.onNodeWithTag("editor-comparison-dialog").assertDoesNotExist()
    assertEquals(result, harness.session.state.value)
    assertEquals(preparations, harness.processor.preparations.get())
    assertTrue(harness.saveRequests.isEmpty())
    assertTrue(harness.shareRequests.isEmpty())
    assertEquals(0, harness.compressTaps)

    compose.onNodeWithText("Compare with original").performClick()
    compose.waitUntil(10_000) {
      !slider.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
    }
    compose.runOnIdle { harness.settings = harness.settings.copy(quality = 70) }
    compose.onNodeWithTag("editor-comparison-dialog").assertDoesNotExist()
    val changed = awaitResult(harness, 500_000)
    assertEquals(70, changed.settings.quality)
  }

  @Test @Config(qualifiers = "w800dp-h360dp-land-mdpi")
  fun comparisonInAShortWindowKeepsCloseAndControlsReachable() {
    val harness = editor()
    screen(harness)
    awaitResult(harness, 500_000)
    compose.onNodeWithText("Compare with original").performScrollTo().performClick()
    compose.onNodeWithText("Close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    val slider = compose.onNodeWithContentDescription("Before and after comparison")
    compose.waitUntil(10_000) { !slider.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
    slider.performScrollTo().assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
    compose.onNodeWithText("Close").assertIsDisplayed().performClick()
    compose.onNodeWithTag("editor-comparison-dialog").assertDoesNotExist()
    assertEquals(0, harness.compressTaps)
    assertTrue(harness.saveRequests.isEmpty())
  }

  @Test fun sliderAndNewerTargetSupersedeAnInFlightRequest() {
    val harness = editor(quality = 100, gateTarget = 100_000)
    screen(harness)
    val previous = awaitResult(harness, 500_000)
    compose.onNodeWithText("100 KB").performScrollTo().performClick()
    compose.waitUntil(10_000) { harness.processor.started.isCompleted }
    compose.onNodeWithContentDescription("Photo detail").performScrollTo()
      .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(70f)) }
    assertEquals(70, harness.settings.quality)
    assertEquals("Moving the slider keeps the chosen limit", 100_000L, harness.settings.targetBytes)
    assertNull("The old measured file cannot be current after a settings change",
      PhotoEditorState(harness.original, harness.settings, previous).currentResult)

    compose.onNodeWithText("200 KB").performScrollTo().performClick()
    val latest = awaitResult(harness, 200_000)
    assertEquals(70, latest.settings.quality)
    assertMeasured(harness, latest)
    harness.processor.release.complete(Unit)
    compose.waitForIdle()
    assertEquals("Cancelled older work must not replace the newer result", latest, harness.session.state.value)
    compose.onNodeWithText("Fits your 200 KB limit").assertIsDisplayed()
    assertEquals(0, harness.compressTaps)
  }

  private class Harness(
    val sourceFile: File,
    val original: SelectedImage,
    val processor: GatedProcessor,
    initialSettings: CompressionSettings,
  ) {
    val originalBytes = sourceFile.readBytes()
    var settings by mutableStateOf(initialSettings)
    var targetDraftValid by mutableStateOf(true)
    var compressTaps = 0
    val saveRequests = mutableListOf<com.example.imagecompressor.data.model.CompressedImage>()
    val shareRequests = mutableListOf<com.example.imagecompressor.data.model.CompressedImage>()
    lateinit var session: LiveEditorSession
    fun presentation() = PhotoEditorState(original, settings, session.state.value)
  }

  private fun editor(quality: Int = 80, gateTarget: Long? = null): Harness {
    val input = File(context.cacheDir, "compressed/live-editor-inputs/astronaut.png").apply {
      parentFile!!.mkdirs()
      checkNotNull(LiveEditorRenderTest::class.java.classLoader?.getResourceAsStream("compression/astronaut.png")).use { source ->
        outputStream().use(source::copyTo)
      }
    }
    val engine = ImageCompressor(context, workingPixelLimit = 1_000_000)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", input)
    val original = runBlocking { engine.inspect(uri) }
    return Harness(input, original, GatedProcessor(engine.createPreviewProcessor(), gateTarget),
      CompressionSettings(quality = quality, targetBytes = 500_000))
  }

  private fun screen(harness: Harness, dark: Boolean = false) {
    compose.setContent {
      val scope = rememberCoroutineScope()
      val session = remember { LiveEditorSession(scope, harness.processor) }
      harness.session = session
      activeSession = session
      val preview by session.state.collectAsState()
      LaunchedEffect(harness.settings, harness.targetDraftValid) {
        session.request(harness.original.takeIf { harness.targetDraftValid }, harness.settings)
      }
      DisposableEffect(session) { onDispose { session.close() } }
      ImageCompressorTheme(darkTheme = dark, dynamicColor = false) {
        Surface {
          AppChrome(AppScreen.COMPRESSION_SETTINGS) { modifier ->
            CompressionSettingsScreen(ImageCompressorUiState(
              screen = AppScreen.COMPRESSION_SETTINGS,
              selectedImages = listOf(harness.original),
              compressionSettings = harness.settings,
              editorPreview = preview,
              isInitializing = false,
            ), modifier, onSettingsChange = { harness.settings = it },
              onCompress = { harness.compressTaps++ }, onEditSelection = {},
              onRetryPreview = session::retry,
              onTargetDraftValidityChange = { harness.targetDraftValid = it },
              animatePlaceholders = false,
              onSave = { harness.saveRequests += checkNotNull(harness.presentation().currentResult) },
              onShare = { harness.shareRequests += checkNotNull(harness.presentation().currentResult) })
          }
        }
      }
    }
  }

  private fun awaitResult(harness: Harness, target: Long): EditorPreview.Ready {
    compose.waitUntil(20_000) {
      val value = harness.session.state.value
      value is EditorPreview.Ready && value.settings == harness.settings && value.settings.targetBytes == target
    }
    compose.waitForIdle()
    // Engine readiness precedes Coil's draw readiness. Let the actual file decode finish before
    // changing requests or tearing down the native host; do not interrupt its decoder in teardown.
    awaitImage()
    return harness.session.state.value as EditorPreview.Ready
  }

  private fun verifiedLabel(result: EditorPreview.Ready) = context.resources.getQuantityString(
    R.plurals.editor_verified_bytes, result.image.compressedSizeBytes.toInt(), result.image.compressedSizeBytes)

  private fun assertMeasured(harness: Harness, result: EditorPreview.Ready) {
    val output = File(result.image.outputFilePath)
    assertTrue(output.isFile)
    assertTrue(output.length() > 0)
    assertEquals(output.length(), result.image.compressedSizeBytes)
    assertEquals(harness.original.id, result.image.original.id)
    assertEquals(0L, result.image.historyId)
    assertNull(result.image.savedUri)
    assertEquals(TargetSizeStatus.MET, result.image.targetSizeStatus)
    assertEquals(result.image, harness.presentation().currentResult)
    compose.onNodeWithText(verifiedLabel(result)).assertIsDisplayed()
    compose.onNodeWithText(result.image.compressedSizeBytes.toReadableSize()).assertIsDisplayed()
  }

  private fun awaitImage() {
    compose.waitUntil(10_000) {
      compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Preview ready"),
        useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithContentDescription("Photo preview").assertIsDisplayed()
    compose.waitForIdle()
  }

  private fun capture(name: String, harness: Harness, dialog: Boolean = false) {
    if (!dialog) awaitImage() else compose.waitForIdle()
    val directory = File(checkNotNull(System.getProperty("shrink.screenshots"))).apply { mkdirs() }
    compose.runOnIdle {
      val view = if (dialog) checkNotNull(ShadowDialog.getLatestDialog().window).decorView
        else compose.activity.findViewById<android.view.View>(android.R.id.content)
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(Canvas(bitmap))
      File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
      bitmap.recycle()
    }
    val preview = harness.session.state.value
    val metadata = JSONObject().put("renderer", "Native Robolectric Compose; actual engine and Coil")
      .put("fixture", "compression/astronaut.png; see fixture NOTICE.md")
      .put("originalBytes", harness.original.sizeBytes)
      .put("targetBytes", harness.settings.targetBytes)
      .put("previewState", if (preview is EditorPreview.Ready) "Ready" else "Updating")
    if (preview is EditorPreview.Ready) metadata.put("verifiedBytes", File(preview.image.outputFilePath).length())
    File(directory, "$name.json").writeText(metadata.toString(2))
  }

  private class GatedProcessor(
    private val delegate: TemporaryImageProcessor,
    private val gateTarget: Long?,
  ) : TemporaryImageProcessor {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val preparations = AtomicInteger()
    override suspend fun preparePreview(image: SelectedImage, settings: CompressionSettings): TemporaryPreview {
      if (gateTarget != null && settings.targetBytes == gateTarget) {
        started.complete(Unit)
        release.await()
      }
      preparations.incrementAndGet()
      return delegate.preparePreview(image, settings)
    }
    override fun close() = delegate.close()
  }
}
