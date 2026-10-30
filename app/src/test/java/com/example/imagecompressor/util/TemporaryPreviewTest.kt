package com.example.imagecompressor.util

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.TargetSizeStatus
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real native encodes and file lifetimes; scheduling tests explicitly exercise ownership races. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TemporaryPreviewTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private val engine get() = ImageCompressor(context, workingPixelLimit = 1_000_000)
  private val previewRoot get() = File(context.cacheDir, "editor-previews")
  private val processors = mutableListOf<TemporaryImageProcessor>()

  @Before fun provider() {
    val info = checkNotNull(context.packageManager.resolveContentProvider(
      "${context.packageName}.fileprovider", PackageManager.GET_META_DATA))
    Robolectric.buildContentProvider(FileProvider::class.java).create(info)
  }

  @After fun clean() {
    processors.forEach(TemporaryImageProcessor::close)
    context.cacheDir.deleteRecursively()
    File(context.filesDir, "preview-test-inputs").deleteRecursively()
  }

  private fun fixture(name: String = "astronaut.png"): File = File(context.filesDir, "preview-test-inputs/$name").apply {
    parentFile!!.mkdirs()
    checkNotNull(TemporaryPreviewTest::class.java.classLoader?.getResourceAsStream("compression/$name")).use { source ->
      outputStream().use(source::copyTo)
    }
  }
  private fun processor() = engine.createPreviewProcessor().also(processors::add)
  private suspend fun selected(file: File) = engine.inspect(Uri.fromFile(file))
  private fun previewFiles() = if (previewRoot.exists()) previewRoot.walkTopDown().filter { it.isFile }.toList() else emptyList()
  private fun assertNoScratch() {
    assertTrue(context.cacheDir.walkTopDown().none { it.isFile &&
      (it.name.startsWith("image-source-") || it.name.startsWith("image-candidate-")) })
  }

  @Test fun previewIsExactMeasuredTargetCandidateWithoutShareOrHistoryCapability() = runBlocking {
    val original = fixture()
    val bytes = original.readBytes()
    val source = selected(original)
    val settings = CompressionSettings(quality = 80, targetBytes = 50_000)
    val preview = processor().preparePreview(source, settings)
    val result = preview.image
    val file = File(result.outputFilePath)
    assertTrue(file.canonicalPath.startsWith(previewRoot.canonicalPath + File.separator))
    assertEquals(Uri.fromFile(file), result.outputUri)
    assertEquals(file.length(), result.compressedSizeBytes)
    assertEquals(MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) },
      result.verifiedSha256)
    assertTrue(checkNotNull(result.verifiedSha256).matches(Regex("[0-9a-f]{64}")))
    assertEquals(TargetSizeStatus.MET, result.targetSizeStatus)
    assertTrue(file.length() in 1L..50_000L)
    assertEquals(0L, result.historyId)
    assertNull(result.savedUri)
    assertFalse(File(context.cacheDir, "compressed").exists())
    assertThrows(IllegalArgumentException::class.java) {
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val decoded = checkNotNull(BitmapFactory.decodeFile(file.path))
    try {
      assertEquals(result.width, decoded.width)
      assertEquals(result.height, decoded.height)
    } finally { decoded.recycle() }
    assertArrayEquals(bytes, original.readBytes())
    assertNoScratch()
    preview.close()
    preview.close()
    assertTrue(preview.isClosed)
    assertFalse(file.exists())
    assertTrue(previewFiles().isEmpty())
  }

  @Test fun previewAndRetainedCompressionUseTheSameEncoderAndFinalBytes() = runBlocking {
    val source = selected(fixture())
    val settings = CompressionSettings(quality = 72, targetBytes = 25_000)
    val preview = processor().preparePreview(source, settings)
    val retained = engine.compress(source, settings)
    assertEquals(preview.image.width, retained.width)
    assertEquals(preview.image.height, retained.height)
    assertEquals(preview.image.encodingQuality, retained.encodingQuality)
    assertEquals(preview.image.encodingAttempts, retained.encodingAttempts)
    assertArrayEquals(File(preview.image.outputFilePath).readBytes(), File(retained.outputFilePath).readBytes())
    assertEquals("content", retained.outputUri.scheme)
    assertEquals(File(context.cacheDir, "compressed").canonicalFile, File(retained.outputFilePath).canonicalFile.parentFile)
    preview.close()
    assertTrue(File(retained.outputFilePath).isFile)
    assertNoScratch()
  }

  @Test fun impossibleLimitReturnsMeasuredUnmetResultWithinTheSearchBound() = runBlocking {
    val original = fixture("noise.png")
    val bytes = original.readBytes()
    val preview = processor().preparePreview(selected(original), CompressionSettings(targetBytes = 100))
    assertEquals(TargetSizeStatus.UNMET, preview.image.targetSizeStatus)
    assertTrue(preview.image.compressedSizeBytes > 100)
    assertTrue(preview.image.encodingAttempts in 1..48)
    assertEquals(File(preview.image.outputFilePath).length(), preview.image.compressedSizeBytes)
    assertArrayEquals(bytes, original.readBytes())
    assertNoScratch()
  }

  @Test fun factoryAndUnusedCloseDoNotCreatePreviewStorage() {
    assertFalse(previewRoot.exists())
    val processor = processor()
    assertFalse(previewRoot.exists())
    processor.close()
    processor.close()
    assertFalse(previewRoot.exists())
  }

  @Test fun closingOneSessionCannotDeleteAnotherSessionsLiveFiles() = runBlocking {
    val source = selected(fixture("chart.png"))
    val first = processor()
    val second = processor()
    val a = first.preparePreview(source, CompressionSettings())
    val b = second.preparePreview(source, CompressionSettings())
    val aFile = File(a.image.outputFilePath)
    val bFile = File(b.image.outputFilePath)
    assertNotEquals(aFile.parentFile!!.parentFile, bFile.parentFile!!.parentFile)
    first.close()
    assertTrue(a.isClosed)
    assertFalse(aFile.exists())
    assertFalse(b.isClosed)
    assertTrue(bFile.isFile)
    a.close()
    assertTrue(bFile.isFile)
    try { first.preparePreview(source, CompressionSettings()); fail("Closed session must reject work") }
    catch (_: CancellationException) { /* expected */ }
    second.close()
    assertFalse(bFile.exists())
    assertTrue(previewFiles().isEmpty())
  }

  @Test fun exportLeasesKeepTheExactFileAliveAfterPreviewAndProcessorClose() = runBlocking {
    val processor = processor()
    val preview = processor.preparePreview(selected(fixture("chart.png")), CompressionSettings())
    val file = File(preview.image.outputFilePath)
    val bytes = file.readBytes()
    val first = checkNotNull(preview.acquire())
    val second = checkNotNull(preview.acquire())
    assertSame(preview.image, first.image)
    assertSame(first.image, second.image)
    preview.close()
    assertTrue(preview.isClosed)
    assertNull("Closed owners must not grant new leases", preview.acquire())
    processor.close()
    assertArrayEquals(bytes, file.readBytes())
    first.close()
    first.close()
    assertTrue("The second export still owns these bytes", file.isFile)
    assertArrayEquals(bytes, File(second.image.outputFilePath).readBytes())
    second.close()
    second.close()
    assertFalse(file.exists())
    assertTrue(previewFiles().isEmpty())
    assertTrue(previewRoot.listFiles().orEmpty().none { it.isDirectory })
  }

  @Test fun releasingOneLeaseDoesNotCloseTheSessionsOwnership() = runBlocking {
    val preview = processor().preparePreview(selected(fixture("chart.png")), CompressionSettings())
    val file = File(preview.image.outputFilePath)
    val first = checkNotNull(preview.acquire())
    first.close()
    first.close()
    assertFalse(preview.isClosed)
    assertTrue(file.isFile)
    val next = checkNotNull(preview.acquire())
    preview.close()
    assertTrue(file.isFile)
    next.close()
    assertFalse(file.exists())
  }

  @Test fun fingerprintRejectsAnOutputWhoseMeasuredByteCountChanged() = runBlocking {
    val image = SelectedImage(Uri.parse("content://photo/source"), "photo.jpg", 20, 1, 1, "JPEG")
    val processor = SessionTemporaryImageProcessor(context) { original, _, directory ->
      val file = File(directory, "changed.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
      CompressedImage(original, Uri.fromFile(file), file.path, 2, 1, 1, OutputFormat.JPEG)
    }.also(processors::add)
    try { processor.preparePreview(image, CompressionSettings()); fail("The size must match the fingerprinted file") }
    catch (_: IOException) { /* expected */ }
    assertTrue(previewFiles().isEmpty())
  }

  @Test fun firstSessionRemovesAbandonedOwnedDirectoriesButPreservesUnrelatedFiles() = runBlocking {
    val abandoned = File(previewRoot, "session_${UUID.randomUUID()}").apply { mkdirs() }
    File(abandoned, "unfinished.jpg").writeText("old process output")
    val unrelated = File(previewRoot, "unrelated.txt").apply { writeText("keep") }
    val preview = processor().preparePreview(selected(fixture("chart.png")), CompressionSettings())
    assertFalse(abandoned.exists())
    assertEquals("keep", unrelated.readText())
    assertTrue(File(preview.image.outputFilePath).isFile)
    assertNoScratch()
  }

  @Test fun damagedInputLeavesNoPreviewOrScratchAndKeepsOriginal() = runBlocking {
    val original = fixture("truncated.png")
    val bytes = original.readBytes()
    val untrusted = SelectedImage(Uri.fromFile(original), "photo.png", 1, 1, 1, "JPEG")
    try { processor().preparePreview(untrusted, CompressionSettings()); fail("Expected validation failure") }
    catch (_: IOException) { /* expected */ }
    assertArrayEquals(bytes, original.readBytes())
    assertTrue(previewFiles().isEmpty())
    assertFalse(File(context.cacheDir, "compressed").exists())
    assertNoScratch()
  }

  @Test fun cancellationOnDispatcherReturnDisposesTheAlreadyEncodedCandidate() = runBlocking {
    val source = selected(fixture("chart.png"))
    val processor = processor()
    val caller = QueuedCallerDispatcher()
    val request = async(caller, start = CoroutineStart.UNDISPATCHED) {
      processor.preparePreview(source, CompressionSettings())
    }
    val resume = checkNotNull(caller.tasks.poll(10, TimeUnit.SECONDS)) { "Native preview did not complete" }
    assertEquals("The test must cancel after publication, before ownership reaches the caller", 1, previewFiles().size)
    request.cancel()
    resume.run()
    try { request.await(); fail("Expected cancellation") }
    catch (_: CancellationException) { /* expected */ }
    assertTrue(previewFiles().isEmpty())
    assertNoScratch()
  }

  @Test fun closingDuringNativePreparationRejectsPublicationAndCleansOnlyThatRequest() = runBlocking {
    val file = fixture("chart.png")
    val bytes = file.readBytes()
    val opened = CountDownLatch(1)
    val release = CountDownLatch(1)
    val uri = Uri.parse("content://temporary-preview-slow/photo")
    shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
      object : FilterInputStream(bytes.inputStream()) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
          opened.countDown()
          check(release.await(10, TimeUnit.SECONDS)) { "Test source was never released" }
          return super.read(buffer, offset, length)
        }
      }
    }
    val processor = processor()
    val request = async(Dispatchers.Default) {
      processor.preparePreview(SelectedImage(uri, "photo.png", bytes.size.toLong(), 320, 192, "PNG"), CompressionSettings())
    }
    try {
      assertTrue(opened.await(10, TimeUnit.SECONDS))
      processor.close()
    } finally { release.countDown() }
    try { request.await(); fail("Closed session must not publish") }
    catch (_: CancellationException) { /* expected */ }
    assertTrue(previewFiles().isEmpty())
    assertArrayEquals(bytes, file.readBytes())
    assertNoScratch()
  }

  private class QueuedCallerDispatcher : CoroutineDispatcher() {
    val tasks = LinkedBlockingQueue<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
  }
}
