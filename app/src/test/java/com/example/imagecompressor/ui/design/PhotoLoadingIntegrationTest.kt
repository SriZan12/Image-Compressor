package com.example.imagecompressor.ui.design

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.Fetcher
import coil.intercept.Interceptor
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.Options
import coil.request.SuccessResult
import coil.size.Size
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.ui.components.Photo
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Coil fetch/decode/cache integration. No manually seeded bitmaps or timing assertions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoLoadingIntegrationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var loader: ImageLoader

  @After fun releaseLoader() {
    if (::loader.isInitialized) loader.shutdown()
    Coil.reset()
  }

  @Test fun coldPreviewDecodesToDisplayBoundsAndReentryUsesMemoryCache() {
    val events = installLoader()
    val uri = fixture()
    var visible by mutableStateOf(true)
    content { if (visible) Photo(uri, "Photo", Modifier.size(120.dp).testTag("photo")) }
    awaitReady()
    assertEquals(1, events.fetches.get())
    assertEquals(1, events.decodes.get())
    assertEquals(DataSource.DISK, events.successes.single().dataSource)
    val size = events.sizes.single()
    assertEquals(Size(240, 240), size)
    val decoded = events.successes.single().drawable
    assertTrue("512px source should be sampled for the 240px frame", decoded.intrinsicWidth < 512)
    assertTrue("Sampled preview should cover its frame", decoded.intrinsicWidth >= 240)
    assertEquals(decoded.intrinsicWidth, decoded.intrinsicHeight)

    compose.runOnIdle { visible = false }
    compose.onNodeWithTag("photo").assertDoesNotExist()
    compose.runOnIdle { visible = true }
    awaitReady()
    assertEquals(2, events.successes.size)
    assertEquals(DataSource.MEMORY_CACHE, events.successes.last().dataSource)
    assertEquals("A warm preview must not reopen the source", 1, events.fetches.get())
    assertEquals("A warm preview must not decode again", 1, events.decodes.get())
  }

  @Test fun unrelatedRecompositionDoesNotRepeatThePhotoRequest() {
    val events = installLoader()
    val uri = fixture()
    var editRevision by mutableIntStateOf(0)
    content { Photo(uri, "Photo, edit $editRevision", Modifier.size(120.dp).testTag("photo")) }
    awaitReady()
    repeat(5) {
      compose.runOnIdle { editRevision++ }
      compose.onNodeWithContentDescription("Photo, edit $editRevision").assertExists()
      compose.waitForIdle()
    }
    assertEquals(1, events.starts.get())
    assertEquals(1, events.fetches.get())
    assertEquals(1, events.decodes.get())
  }

  @Test fun growingTheViewportRequestsEnoughDetailWithoutReloadingForUnrelatedEdits() {
    val events = installLoader()
    val uri = fixture("large-12mp.png")
    var side by mutableStateOf(120.dp)
    var editRevision by mutableIntStateOf(0)
    content { Photo(uri, "Photo, edit $editRevision", Modifier.size(side).testTag("photo")) }
    awaitReady()
    assertEquals(Size(240, 240), events.sizes.single())
    assertTrue(events.successes.single().drawable.intrinsicWidth < 640)
    repeat(3) {
      compose.runOnIdle { editRevision++ }
      compose.onNodeWithContentDescription("Photo, edit $editRevision").assertExists()
    }
    assertEquals("Unrelated edits must not re-fetch the smaller preview", 1, events.fetches.get())

    compose.runOnIdle { side = 320.dp }
    compose.onNodeWithTag("photo").assertWidthIsEqualTo(320.dp).assertHeightIsEqualTo(320.dp)
    // A 4:3 source in a 640px square FIT viewport needs at least 640 × 480 decoded pixels.
    compose.waitUntil(10_000) { events.sizes.lastOrNull() == Size(640, 640) }
    awaitReady()
    assertEquals(Size(640, 640), events.sizes.last())
    val larger = events.successes.last().drawable
    assertTrue("The wider viewport needs a wider decode", larger.intrinsicWidth >= 640)
    assertTrue("The full 4:3 image must retain enough vertical detail", larger.intrinsicHeight >= 480)
    assertTrue("The preview must still be sampled", larger.intrinsicWidth < 4000)
    assertEquals(2, events.fetches.get())
    assertEquals(2, events.decodes.get())
    repeat(3) {
      compose.runOnIdle { editRevision++ }
      compose.onNodeWithContentDescription("Photo, edit $editRevision").assertExists()
    }
    assertEquals("Unrelated edits must not re-fetch the larger preview", 2, events.fetches.get())
    assertEquals(2, events.decodes.get())
  }

  @Test fun resizingWhileLoadingKeepsTheActualStageAndFinishesWithoutAStuckPlaceholder() {
    val gate = RequestGate()
    val events = installLoader(gate)
    val uri = fixture()
    var side by mutableStateOf(120.dp)
    content { Photo(uri, "Photo", Modifier.size(side).testTag("photo")) }
    compose.waitUntil(10_000) { gate.started.isCompleted }
    compose.onNodeWithTag("photo").assert(status("Loading preview"))
    compose.runOnIdle { side = 200.dp }
    compose.onNodeWithTag("photo").assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(200.dp)
      .assert(status("Loading preview"))
    gate.release.complete(Unit)
    awaitReady()
    compose.onNodeWithTag("photo").assertWidthIsEqualTo(200.dp).assertHeightIsEqualTo(200.dp)
    assertEquals(1, events.successes.size)
    assertEquals(1, events.decodes.get())
  }

  @Test fun failedFileCanRecoverOnReentryWithTheSameUri() {
    val events = installLoader()
    val context = RuntimeEnvironment.getApplication()
    val file = File(context.cacheDir, "preview-after-retry.png")
    file.delete()
    val uri = Uri.fromFile(file)
    var visible by mutableStateOf(true)
    content { if (visible) Photo(uri, "Photo", Modifier.size(120.dp).testTag("photo")) }
    compose.waitUntil(10_000) {
      compose.onAllNodes(status("Preview unavailable"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag("photo").assert(status("Preview unavailable"))
    compose.runOnIdle { visible = false }
    compose.onNodeWithTag("photo").assertDoesNotExist()
    copyFixture(file)
    compose.runOnIdle { visible = true }
    awaitReady()
    assertEquals(2, events.starts.get())
    assertEquals(1, events.successes.size)
    compose.onNodeWithText("Preview unavailable").assertDoesNotExist()
  }

  private fun content(block: @Composable () -> Unit) = compose.setContent {
    ImageCompressorTheme(dynamicColor = false) { Surface { Box { block() } } }
  }

  private fun status(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

  private fun awaitReady() {
    compose.waitUntil(10_000) {
      compose.onAllNodes(status("Preview ready"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag("photo").assert(status("Preview ready"))
    compose.waitForIdle()
  }

  private fun fixture(name: String = "astronaut.png"): Uri {
    val file = File(RuntimeEnvironment.getApplication().cacheDir, "coil-preview-$name")
    copyFixture(file, name)
    return Uri.fromFile(file)
  }

  private fun copyFixture(file: File, name: String = "astronaut.png") {
    checkNotNull(javaClass.classLoader?.getResourceAsStream("compression/$name")).use { source ->
      file.outputStream().use(source::copyTo)
    }
  }

  private fun installLoader(gate: RequestGate? = null): LoadEvents {
    val events = LoadEvents()
    loader = ImageLoader.Builder(RuntimeEnvironment.getApplication())
      // Robolectric draws on a software Canvas. Production continues to use Coil's defaults.
      .allowHardware(false)
      .eventListenerFactory { events }
      .apply { if (gate != null) components { add(gate) } }
      .build()
    Coil.setImageLoader(loader)
    return events
  }

  private class LoadEvents : EventListener {
    val starts = AtomicInteger()
    val fetches = AtomicInteger()
    val decodes = AtomicInteger()
    val sizes = Collections.synchronizedList(mutableListOf<Size>())
    val successes = Collections.synchronizedList(mutableListOf<SuccessResult>())
    override fun onStart(request: ImageRequest) { starts.incrementAndGet() }
    override fun resolveSizeEnd(request: ImageRequest, size: Size) { sizes += size }
    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) { fetches.incrementAndGet() }
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) { decodes.incrementAndGet() }
    override fun onSuccess(request: ImageRequest, result: SuccessResult) { successes += result }
  }

  /** Deterministically holds a real request in its loading state; no production delay is introduced. */
  private class RequestGate : Interceptor {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
      started.complete(Unit)
      release.await()
      return chain.proceed(chain.request)
    }
  }
}
