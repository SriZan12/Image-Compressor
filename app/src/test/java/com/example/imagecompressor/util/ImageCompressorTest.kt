package com.example.imagecompressor.util

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorSpace
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionAdjustment
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.TargetSizeStatus
import com.example.imagecompressor.util.compression.ImageContainer
import com.example.imagecompressor.util.compression.CompressionPolicy
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.CRC32
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native Skia codecs/Canvas, not Robolectric's no-op legacy bitmap shadows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageCompressorTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private val compressor get() = ImageCompressor(context, workingPixelLimit = 1_000_000)
  private val inputDir get() = File(context.cacheDir, "compressed/test-inputs").apply { mkdirs() }

  @Before fun registerProvider() {
    // Each Robolectric method has a fresh cache directory. Attaching the provider resets its
    // process-static path cache to that directory, just as a new Android process would.
    val info = checkNotNull(context.packageManager.resolveContentProvider(
      "${context.packageName}.fileprovider", PackageManager.GET_META_DATA))
    Robolectric.buildContentProvider(FileProvider::class.java).create(info)
  }

  @After fun clean() { context.cacheDir.deleteRecursively() }

  private fun fixture(name: String): File = File(inputDir, name).apply {
    checkNotNull(ImageCompressorTest::class.java.classLoader?.getResourceAsStream("compression/$name")).use { source ->
      outputStream().use { source.copyTo(it) }
    }
  }
  private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  private suspend fun selected(file: File) = compressor.inspect(uri(file))
  private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
  }
  private fun decode(file: File): Bitmap = checkNotNull(BitmapFactory.decodeFile(file.absolutePath))
  private fun decode(result: CompressedImage) = decode(File(result.outputFilePath))
  private fun assertNoTemporaryFiles() {
    assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("image-source-") || it.name.startsWith("image-candidate-") })
  }

  @Test fun allEightExifStatesProduceUprightPixelsAndStripPrivateMetadata() = runBlocking {
    val chart = decode(fixture("chart.png"))
    try {
      for (orientation in 1..8) {
        val input = File(inputDir, "orientation-$orientation.jpg")
        input.outputStream().use { assertTrue(chart.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        ExifInterface(input).apply {
          setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
          setAttribute(ExifInterface.TAG_ARTIST, "private test author")
          setLatLong(51.5, -0.1)
          saveAttributes()
        }
        val originalBytes = input.readBytes()
        val raw = decode(input)
        val output = compressor.compress(selected(input), CompressionSettings(outputFormat = OutputFormat.PNG))
        val result = decode(output)
        try {
          val w = raw.width; val h = raw.height
          assertEquals(if (orientation >= 5) h else w, result.width)
          assertEquals(if (orientation >= 5) w else h, result.height)
          val actual = pixels(result)
          val expected = IntArray(w * h)
          val source = pixels(raw)
          for (y in 0 until h) for (x in 0 until w) {
            val destination = when (orientation) {
              1 -> x to y
              2 -> w - 1 - x to y
              3 -> w - 1 - x to h - 1 - y
              4 -> x to h - 1 - y
              5 -> y to x
              6 -> h - 1 - y to x
              7 -> h - 1 - y to w - 1 - x
              else -> y to w - 1 - x
            }
            expected[destination.second * result.width + destination.first] = source[y * w + x]
          }
          assertArrayEquals("EXIF $orientation", expected, actual)
          val exif = ExifInterface(output.outputFilePath)
          assertEquals(1, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
          assertNull(exif.getAttribute(ExifInterface.TAG_ARTIST))
          assertNull(exif.latLong)
          assertArrayEquals(originalBytes, input.readBytes())
          assertEquals(ColorSpace.Named.SRGB.ordinal, result.colorSpace!!.id)
          assertEquals(output.compressedSizeBytes, File(output.outputFilePath).length())
        } finally { raw.recycle(); result.recycle() }
      }
      assertNoTemporaryFiles()
    } finally { chart.recycle() }
  }

  @Test fun pngAndWebpPreserveAlphaWhileJpegUsesWhite() = runBlocking {
    val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.TRANSPARENT)
    for (y in 0 until 32) for (x in 32 until 64) bitmap.setPixel(x, y, Color.argb(128, 255, 0, 0))
    val input = File(inputDir, "alpha.png")
    input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val source = selected(input)
    try {
      for (format in OutputFormat.entries) {
        val output = compressor.compress(source, CompressionSettings(outputFormat = format))
        val result = decode(output)
        try {
          assertEquals(format.mimeType, BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(output.outputFilePath, this)
          }.outMimeType)
          if (format == OutputFormat.JPEG) {
            val white = result.getPixel(4, 4)
            assertTrue(Color.red(white) >= 250 && Color.green(white) >= 250 && Color.blue(white) >= 250)
            assertTrue(output.adjustments.contains(CompressionAdjustment.JPEG_BACKGROUND))
            assertEquals(255, Color.alpha(result.getPixel(48, 4)))
          } else {
            assertEquals(0, Color.alpha(result.getPixel(4, 4)))
            assertEquals(128, Color.alpha(result.getPixel(48, 4)))
            assertFalse(output.adjustments.contains(CompressionAdjustment.JPEG_BACKGROUND))
          }
          assertEquals(format.name, ImageContainer.validate(File(output.outputFilePath)).name)
          // Re-import outputs through the same container/decode checks.
          assertEquals(output.compressedSizeBytes, compressor.inspect(output.outputUri).sizeBytes)
        } finally { result.recycle() }
      }
      assertNoTemporaryFiles()
    } finally { bitmap.recycle() }
  }

  @Test fun targetStatusUsesFinalFileBytesAndReportsImpossibleTargets() = runBlocking {
    // Re-check input bytes instead of trusting stale or malicious selection metadata.
    val source = selected(fixture("astronaut.png")).copy(sizeBytes = 1, width = 1, height = 1, format = "JPEG")
    val baseline = compressor.compress(source, CompressionSettings())
    assertEquals(512, baseline.original.width)
    assertEquals("PNG", baseline.original.format)
    assertTrue(baseline.original.sizeBytes > 1)
    val target = baseline.compressedSizeBytes - 1
    val output = compressor.compress(source, CompressionSettings(targetBytes = target, allowTargetResize = false))
    assertEquals(TargetSizeStatus.MET, output.targetSizeStatus)
    assertTrue(File(output.outputFilePath).length() <= target)
    assertEquals(File(output.outputFilePath).length(), output.compressedSizeBytes)
    assertEquals(512, output.width)
    assertTrue(output.encodingAttempts in 2..8)
    val impossible = compressor.compress(source, CompressionSettings(targetBytes = 1, outputFormat = OutputFormat.PNG))
    assertEquals(TargetSizeStatus.UNMET, impossible.targetSizeStatus)
    assertEquals(256, maxOf(impossible.width, impossible.height))
    assertTrue(impossible.encodingAttempts <= 6)
    assertTrue(impossible.adjustments.contains(CompressionAdjustment.TARGET_SIZE))
    assertNull(impossible.encodingQuality)
    assertNotNull(decode(impossible).also { it.recycle() })
    assertNoTemporaryFiles()
  }

  @Test fun lowMemoryBudgetDownsamplesBeforeDecodeAndDoesNotUpscale() = runBlocking {
    val source = selected(fixture("large-48mp.png"))
    val result = ImageCompressor(context, workingPixelLimit = 100_000).compress(source,
      CompressionSettings(outputFormat = OutputFormat.PNG))
    assertTrue(result.width.toLong() * result.height <= 100_000)
    // 8000x6000 at sample 32 is 250x188; it must never be enlarged back to the requested bound.
    assertTrue(result.width <= 250 && result.height <= 188)
    assertTrue(result.adjustments.contains(CompressionAdjustment.MEMORY_LIMIT))
    assertEquals(8000, result.original.width)
    assertNoTemporaryFiles()
  }

  @Test fun rejectsCorruptionEvenIfCallerSkipsInspectAndCleansStagingFiles() = runBlocking {
    for (name in listOf("corrupt.png", "truncated.png")) {
      val input = fixture(name)
      val original = input.readBytes()
      val untrusted = SelectedImage(uri(input), name, 1, 1, 1, "JPEG")
      try {
        compressor.compress(untrusted, CompressionSettings())
        fail("Expected rejection of $name")
      } catch (_: IOException) { /* expected */ }
      assertArrayEquals(original, input.readBytes())
      assertNoTemporaryFiles()
    }
    assertTrue(File(context.cacheDir, "compressed").listFiles().orEmpty().none { it.isFile })
  }

  @Test fun oversizedEmbeddedMetadataIsRejectedOnOpeningAndCompressionWithoutChangingTheSource() = runBlocking {
    val original = fixture("chart.png").readBytes()
    val type = "eXIf".toByteArray(Charsets.US_ASCII)
    val payload = ByteArray((CompressionPolicy.MAX_METADATA_BYTES + 1).toInt())
    val chunk = ByteArrayOutputStream().apply {
      DataOutputStream(this).apply {
        writeInt(payload.size); write(type); write(payload)
        writeInt(CRC32().apply { update(type); update(payload) }.value.toInt())
      }
    }.toByteArray()
    val input = File(inputDir, "oversized-metadata.png").apply {
      writeBytes(original.copyOfRange(0, 33) + chunk + original.copyOfRange(33, original.size))
    }
    val before = input.readBytes()
    try { compressor.inspect(uri(input)); fail("Opening must bound metadata before EXIF parsing") }
    catch (failure: IOException) { assertTrue(failure.message!!.contains("embedded information")) }
    val untrusted = SelectedImage(uri(input), input.name, input.length(), 320, 192, "PNG")
    try { compressor.compress(untrusted, CompressionSettings()); fail("Compression must revalidate metadata") }
    catch (failure: IOException) { assertTrue(failure.message!!.contains("embedded information")) }
    assertArrayEquals(before, input.readBytes())
    assertNoTemporaryFiles()
  }

  @Test fun outputFailureDeletesCandidatesAndKeepsOriginal() = runBlocking {
    val input = File(context.cacheDir, "original.png").apply {
      checkNotNull(ImageCompressorTest::class.java.classLoader?.getResourceAsStream("compression/chart.png")).use { writeBytes(it.readBytes()) }
    }
    val source = compressor.inspect(Uri.fromFile(input))
    val original = input.readBytes()
    // An inaccessible output directory simulates publication failure after a successful encode.
    File(context.cacheDir, "compressed").writeText("not a directory")
    try {
      compressor.compress(source, CompressionSettings())
      fail("Expected output failure")
    } catch (_: IOException) { /* expected */ }
    assertArrayEquals(original, input.readBytes())
    assertNoTemporaryFiles()
  }

  @Test fun cancelledRequestPublishesNothing() = runBlocking {
    val source = selected(fixture("chart.png"))
    val job = Job().apply { cancel() }
    try {
      withContext(job) { compressor.compress(source, CompressionSettings()) }
      fail("Expected cancellation")
    } catch (_: CancellationException) { /* expected */ }
    assertNoTemporaryFiles()
    assertTrue(File(context.cacheDir, "compressed").listFiles().orEmpty().none { it.isFile })
  }

  @Test fun wideGamutInputBecomesSrgbWithAnExplicitNotice() = runBlocking {
    val wide = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888, false,
      ColorSpace.get(ColorSpace.Named.DISPLAY_P3))
    val input = File(inputDir, "wide.jpg")
    try {
      wide.eraseColor(Color.RED)
      input.outputStream().use { assertTrue(wide.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
    } finally { wide.recycle() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(input.absolutePath, bounds)
    assertTrue("The test must actually contain wide-gamut pixels", bounds.outColorSpace!!.isWideGamut)
    val output = compressor.compress(selected(input), CompressionSettings())
    assertTrue(output.adjustments.contains(CompressionAdjustment.SDR_CONVERSION))
    val decoded = decode(output)
    try { assertTrue(decoded.colorSpace!!.isSrgb) } finally { decoded.recycle() }
    assertNoTemporaryFiles()
  }

  @Test fun encodedSizeLimitIsEnforcedByStreamingRegardlessOfReportedSize() = runBlocking {
    val input = File(context.cacheDir, "oversize.bin")
    RandomAccessFile(input, "rw").use { it.setLength(100L * 1024 * 1024 + 1) }
    val image = SelectedImage(Uri.fromFile(input), "photo.jpg", 1, 1, 1, "JPEG")
    try {
      compressor.compress(image, CompressionSettings())
      fail("Expected encoded-size rejection")
    } catch (failure: IOException) { assertTrue(failure.message!!.contains("100 MiB")) }
    assertEquals(100L * 1024 * 1024 + 1, input.length())
    assertNoTemporaryFiles()
  }
}
