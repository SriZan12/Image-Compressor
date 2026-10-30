package com.example.imagecompressor.ui.editor

import android.app.Application
import android.net.Uri
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.ui.state.EditorPreview
import com.example.imagecompressor.util.TemporaryImageProcessor
import com.example.imagecompressor.util.TemporaryPreview
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiveEditorSessionTest {
  @Test fun rapidAdjustmentsDebounceToLatestSettingsAndDuplicateRequestsDoNoWork() = runTest {
    val processor = FakeProcessor()
    val session = LiveEditorSession(backgroundScope, processor)
    val source = source("one")
    session.request(source, settings(80))
    assertEquals(EditorPreview.Updating(), session.state.value)
    runCurrent()
    advanceTimeBy(75)
    session.request(source, settings(60))
    runCurrent()
    advanceTimeBy(75)
    session.request(source, settings(40))
    runCurrent()
    advanceTimeBy(149)
    runCurrent()
    assertTrue(processor.calls.isEmpty())
    assertNull((session.state.value as EditorPreview.Updating).estimatedBytes)
    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf(40), processor.calls.map { it.second.quality })
    val ready = session.state.value as EditorPreview.Ready
    assertEquals(settings(40), ready.settings)
    assertEquals(processor.outputs.single().image, ready.image)
    session.request(source, settings(40))
    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(1, processor.calls.size)
    session.close()
    runCurrent()
    assertEquals(1, processor.outputs.single().disposals)
    assertEquals(1, processor.closes)
  }

  @Test fun aNewSettingImmediatelyInvalidatesExportButRetainsTheSameSourcePreviewUntilReplacement() = runTest {
    val processor = FakeProcessor()
    val session = LiveEditorSession(backgroundScope, processor)
    val source = source("one")
    session.request(source, settings(80))
    finishDebounce()
    val first = processor.outputs.single()
    assertTrue(session.state.value is EditorPreview.Ready)
    session.request(source, settings(60))
    val updating = session.state.value as EditorPreview.Updating
    assertEquals(first.image, updating.previousImage)
    assertNull(updating.estimatedBytes)
    assertEquals(0, first.disposals)
    finishDebounce()
    assertEquals(1, first.disposals)
    assertEquals(0, processor.outputs.last().disposals)
    assertEquals(settings(60), (session.state.value as EditorPreview.Ready).settings)
    session.request(null, settings(60))
    assertEquals(EditorPreview.NotCalculated, session.state.value)
    assertEquals(1, processor.outputs.last().disposals)
    session.close()
    runCurrent()
    assertTrue(processor.outputs.all { it.disposals == 1 })
  }

  @Test fun changedSourceMetadataOrIdentityCannotReuseThePreviousPixels() = runTest {
    val processor = FakeProcessor()
    val session = LiveEditorSession(backgroundScope, processor)
    val original = source("one")
    session.request(original, settings(80))
    finishDebounce()
    val first = processor.outputs.single()
    session.request(original.copy(sizeBytes = original.sizeBytes + 1), settings(80))
    assertEquals(EditorPreview.Updating(), session.state.value)
    assertEquals(1, first.disposals)
    val replacement = source("two")
    session.request(replacement, settings(80))
    assertNull((session.state.value as EditorPreview.Updating).previousImage)
    finishDebounce()
    assertEquals(listOf(original, replacement), processor.calls.map { it.first })
    assertEquals(replacement, (session.state.value as EditorPreview.Ready).image.original)
    session.close()
    runCurrent()
  }

  @Test fun noncooperativeCompletionIsDiscardedAndOnlyTheLatestRequestStartsAfterItDrains() = runTest {
    val processor = FakeProcessor()
    val releaseFirst = CompletableDeferred<Unit>()
    processor.prepare = { image, settings ->
      if (settings.quality == 80) withContext(NonCancellable) {
        releaseFirst.await()
        processor.output(image, settings)
      } else processor.output(image, settings)
    }
    val session = LiveEditorSession(backgroundScope, processor)
    val observed = mutableListOf<EditorPreview>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session.state.toList(observed) }
    val firstSource = source("one")
    session.request(firstSource, settings(80))
    finishDebounce()
    assertEquals(1, processor.active)
    session.request(firstSource, settings(60))
    session.request(firstSource, settings(50))
    val latestSource = source("two")
    session.request(latestSource, settings(40))
    runCurrent()
    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(1, processor.calls.size)
    assertTrue(session.state.value is EditorPreview.Updating)
    releaseFirst.complete(Unit)
    runCurrent()
    assertEquals(1, processor.outputs.single().disposals)
    assertTrue(observed.none { it is EditorPreview.Ready })
    finishDebounce()
    val ready = session.state.value as EditorPreview.Ready
    assertEquals(latestSource, ready.image.original)
    assertEquals(settings(40), ready.settings)
    assertEquals(listOf(80, 40), processor.calls.map { it.second.quality })
    assertEquals(1, processor.maximumActive)
    assertEquals(listOf(ready), observed.filterIsInstance<EditorPreview.Ready>())
    session.close()
    runCurrent()
    assertTrue(processor.outputs.all { it.disposals == 1 })
  }

  @Test fun failureRequiresAnExplicitRetryAndRetryUsesTheCurrentSnapshot() = runTest {
    val processor = FakeProcessor()
    processor.prepare = { image, settings ->
      if (processor.calls.size == 1) throw IOException("The photo could not be read. Try again.")
      processor.output(image, settings)
    }
    val session = LiveEditorSession(backgroundScope, processor)
    val image = source("one")
    val settings = settings(70)
    session.request(image, settings)
    finishDebounce()
    assertTrue(session.state.value is EditorPreview.Failure)
    assertTrue(processor.outputs.isEmpty())
    session.request(image, settings)
    finishDebounce()
    assertEquals(1, processor.calls.size)
    session.retry()
    assertEquals(EditorPreview.Updating(), session.state.value)
    finishDebounce()
    assertEquals(2, processor.calls.size)
    assertEquals(settings, (session.state.value as EditorPreview.Ready).settings)
    session.close()
    runCurrent()
    assertEquals(1, processor.closes)
  }

  @Test fun closeInvalidatesImmediatelyAndDisposesALateResultBeforeClosingTheProcessor() = runTest {
    val processor = FakeProcessor()
    val release = CompletableDeferred<Unit>()
    processor.prepare = { image, settings -> withContext(NonCancellable) {
      release.await()
      processor.output(image, settings)
    } }
    val session = LiveEditorSession(backgroundScope, processor)
    session.request(source("one"), settings(80))
    finishDebounce()
    session.close()
    assertEquals(EditorPreview.NotCalculated, session.state.value)
    session.request(source("two"), settings(40))
    session.retry()
    runCurrent()
    assertEquals(0, processor.closes)
    assertEquals(1, processor.active)
    release.complete(Unit)
    runCurrent()
    assertEquals(1, processor.outputs.single().disposals)
    assertEquals(0, processor.active)
    assertEquals(1, processor.closes)
    assertEquals(1, processor.calls.size)
    assertEquals(EditorPreview.NotCalculated, session.state.value)
    session.close()
    runCurrent()
    assertEquals(1, processor.closes)
  }

  @Test fun cancellingTheParentScopeCleansOwnershipEvenWithoutExplicitClose() = runTest {
    val parent = SupervisorJob()
    val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + parent)
    val processor = FakeProcessor()
    val session = LiveEditorSession(scope, processor)
    session.request(source("one"), settings(80))
    finishDebounce()
    val owned = processor.outputs.single()
    parent.cancel()
    runCurrent()
    assertEquals(EditorPreview.NotCalculated, session.state.value)
    assertEquals(1, owned.disposals)
    assertEquals(1, processor.closes)
    session.close() // Mirrors ViewModel.onCleared after viewModelScope has already been cancelled.
    assertEquals(1, processor.closes)
  }

  @Test fun anAlreadyCancelledScopeClosesTheProcessorWithoutStartingWork() = runTest {
    val parent = SupervisorJob().apply { cancel() }
    val processor = FakeProcessor()
    val session = LiveEditorSession(CoroutineScope(StandardTestDispatcher(testScheduler) + parent), processor)
    session.request(source("one"), settings(80))
    finishDebounce()
    assertEquals(EditorPreview.NotCalculated, session.state.value)
    assertTrue(processor.calls.isEmpty())
    assertEquals(1, processor.closes)
  }

  @Test fun anArtifactForChangedSourceMetadataIsRejectedAndDisposed() = runTest {
    val processor = FakeProcessor()
    processor.prepare = { image, settings -> processor.output(image.copy(sizeBytes = image.sizeBytes + 1), settings) }
    val session = LiveEditorSession(backgroundScope, processor)
    session.request(source("one"), settings(80))
    finishDebounce()
    val failure = session.state.value as EditorPreview.Failure
    assertTrue(failure.message.contains("Choose it again"))
    assertEquals(1, processor.outputs.single().disposals)
    session.close()
    runCurrent()
  }

  @Test fun acquiringRequiresTheExactReadySourceSettingsAndDisplayedArtifact() = runTest {
    val processor = FakeProcessor(File(RuntimeEnvironment.getApplication().cacheDir, "leased-edit"))
    val session = LiveEditorSession(backgroundScope, processor)
    val image = source("one")
    val settings = settings(80)
    session.request(image, settings)
    assertNull(session.acquire(image, settings))
    finishDebounce()
    val ready = session.state.value as EditorPreview.Ready
    val path = ready.image.outputFilePath
    assertNull(session.acquire(source("other"), settings, path))
    assertNull(session.acquire(image.copy(sizeBytes = image.sizeBytes + 1), settings, path))
    assertNull(session.acquire(image, settings.copy(quality = 70), path))
    assertNull(session.acquire(image, settings, "$path-older"))
    assertTrue(session.isCurrent(image, settings, path))
    val lease = checkNotNull(session.acquire(image, settings, path))
    assertSame(ready.image, lease.image)

    session.request(image, settings.copy(quality = 60))
    assertFalse(session.isCurrent(image, settings, path))
    assertNull(session.acquire(image, settings, path))
    assertNull(session.acquire(image, settings.copy(quality = 60)))
    finishDebounce()
    val replacement = session.state.value as EditorPreview.Ready
    assertNotEquals(path, replacement.image.outputFilePath)
    assertNull(session.acquire(image, replacement.settings, path))
    assertTrue(session.isCurrent(image, replacement.settings, replacement.image.outputFilePath))
    assertTrue("Changing settings must not remove bytes held by an export", File(path).isFile)
    session.request(source("two"), settings)
    assertFalse(File(replacement.image.outputFilePath).exists())
    assertTrue("Changing source must not remove another operation's leased bytes", File(path).isFile)
    lease.close()
    lease.close()
    assertFalse(File(path).exists())
    assertEquals(1, processor.outputs.first().disposals)
    session.close()
    runCurrent()
  }

  @Test fun suspensionAndSessionCloseInvalidateReadyButKeepLeasedBytesUntilExportEnds() = runTest {
    val processor = FakeProcessor(File(RuntimeEnvironment.getApplication().cacheDir, "leased-pause"))
    val session = LiveEditorSession(backgroundScope, processor)
    val image = source("one")
    val settings = settings(80)
    session.request(image, settings)
    finishDebounce()
    val ready = session.state.value as EditorPreview.Ready
    val file = File(ready.image.outputFilePath)
    val bytes = file.readBytes()
    val lease = checkNotNull(session.acquire(image, settings, file.path))
    session.request(null, settings)
    assertEquals(EditorPreview.NotCalculated, session.state.value)
    assertNull(session.acquire(image, settings, file.path))
    assertArrayEquals(bytes, file.readBytes())
    session.close()
    runCurrent()
    assertEquals(1, processor.closes)
    assertArrayEquals(bytes, file.readBytes())
    lease.close()
    assertFalse(file.exists())
    assertEquals(1, processor.outputs.single().disposals)
  }

  private fun TestScope.finishDebounce() {
    runCurrent()
    advanceTimeBy(150)
    runCurrent()
  }

  private fun source(name: String) = SelectedImage(Uri.parse("content://photos/$name"), "$name.jpg",
    1_200_000, 1200, 900, "JPEG")
  private fun settings(quality: Int) = CompressionSettings(quality = quality, targetBytes = 500_000)

  private class Owned(val image: CompressedImage, private val file: File? = null) {
    var disposals = 0
    val preview = TemporaryPreview(image) { disposals++; file?.delete() }
  }

  private class FakeProcessor(private val directory: File? = null) : TemporaryImageProcessor {
    val calls = mutableListOf<Pair<SelectedImage, CompressionSettings>>()
    val outputs = mutableListOf<Owned>()
    var active = 0
    var maximumActive = 0
    var closes = 0
    var prepare: suspend (SelectedImage, CompressionSettings) -> TemporaryPreview = { image, settings -> output(image, settings) }
    override suspend fun preparePreview(image: SelectedImage, settings: CompressionSettings): TemporaryPreview {
      calls += image to settings
      active++
      maximumActive = maxOf(maximumActive, active)
      try { return prepare(image, settings) } finally { active-- }
    }
    fun output(image: SelectedImage, settings: CompressionSettings): TemporaryPreview {
      val file = directory?.let { File(it, "preview-${outputs.size}.jpg").apply {
        parentFile!!.mkdirs()
        writeText("measured-preview-${outputs.size}")
      } }
      val path = file?.path ?: "/temporary/preview-${outputs.size}.jpg"
      return Owned(CompressedImage(image, Uri.parse("file://$path"), path, file?.length() ?: 480_000,
        image.width, image.height, OutputFormat.JPEG, targetBytes = settings.targetBytes), file).also(outputs::add).preview
    }
    override fun close() { closes++ }
  }
}
