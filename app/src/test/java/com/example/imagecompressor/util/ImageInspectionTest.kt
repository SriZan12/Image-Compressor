package com.example.imagecompressor.util

import android.app.Application
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver

/** Real descriptors and native bounds decoding; these checks do not predict phone latency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageInspectionTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private val inputs get() = File(context.filesDir, "inspection-inputs").apply { mkdirs() }

  @Before fun provider() {
    InspectionProvider.entries.clear()
    InspectionProvider.opened.clear()
    InspectionProvider.writers.clear()
    InspectionProvider.openCount = 0
    Robolectric.buildContentProvider(InspectionProvider::class.java).create(ProviderInfo().apply {
      authority = InspectionProvider.AUTHORITY
      name = InspectionProvider::class.java.name
      exported = false
      grantUriPermissions = true
    })
  }

  @After fun clean() {
    InspectionProvider.writers.forEach { it.join(2_000) }
    context.cacheDir.deleteRecursively()
    inputs.deleteRecursively()
  }

  private fun fixture(name: String) = File(inputs, name).apply {
    checkNotNull(ImageInspectionTest::class.java.classLoader?.getResourceAsStream("compression/$name")).use { source ->
      outputStream().use(source::copyTo)
    }
  }

  private fun expose(file: File, offset: Long = 0, length: Long = AssetFileDescriptor.UNKNOWN_LENGTH,
    unknownStat: Boolean = false, pipe: Boolean = false): Uri {
    val key = "photo-${InspectionProvider.entries.size}"
    InspectionProvider.entries[key] = InspectionProvider.Entry(file, offset, length, unknownStat, pipe)
    return Uri.parse("content://${InspectionProvider.AUTHORITY}/$key")
  }

  private fun noStagingContext(): Context {
    val blocked = File(context.cacheDir, "not-a-directory").apply { writeText("No inspection staging allowed") }
    return object : ContextWrapper(context) { override fun getCacheDir(): File = blocked }
  }

  private fun assertCleanAndClosed() {
    assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("image-source-") })
    assertTrue("Inspection must close every provider descriptor", InspectionProvider.opened.all { !it.valid() })
  }

  @Test fun wholeSeekableImagesNeedNoWritableCacheAndKeepExactMetadata() = runBlocking {
    val png = fixture("chart.png")
    val bitmap = checkNotNull(BitmapFactory.decodeFile(png.path))
    val jpeg = File(inputs, "chart.jpg")
    val webp = File(inputs, "chart.webp")
    try {
      jpeg.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
      webp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it)) }
    } finally { bitmap.recycle() }
    val inspector = ImageCompressor(noStagingContext())
    for ((file, format) in listOf(png to "PNG", jpeg to "JPEG", webp to "WEBP")) {
      val original = file.readBytes()
      val selected = inspector.inspect(expose(file, length = file.length()))
      assertEquals(file.length(), selected.sizeBytes)
      assertEquals(320, selected.width)
      assertEquals(192, selected.height)
      assertEquals(format, selected.format)
      assertEquals(file.name, selected.displayName)
      assertArrayEquals(original, file.readBytes())
    }
    assertEquals(3, InspectionProvider.openCount)
    assertCleanAndClosed()
  }

  @Test fun descriptorMetadataRespectsAllEightExifOrientations() = runBlocking {
    val bitmap = checkNotNull(BitmapFactory.decodeFile(fixture("chart.png").path))
    try {
      val inspector = ImageCompressor(noStagingContext())
      for (orientation in 1..8) {
        val file = File(inputs, "orientation-$orientation.jpg")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
        ExifInterface(file).apply {
          setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
          saveAttributes()
        }
        val original = file.readBytes()
        for (length in listOf(AssetFileDescriptor.UNKNOWN_LENGTH, file.length())) {
          val selected = inspector.inspect(expose(file, length = length))
          assertEquals(if (orientation >= 5) 192 else 320, selected.width)
          assertEquals(if (orientation >= 5) 320 else 192, selected.height)
        }
        assertArrayEquals(original, file.readBytes())
      }
      assertCleanAndClosed()
    } finally { bitmap.recycle() }
  }

  @Test fun offsetAssetStagesOnlyItsBoundedPhotoAndDoesNotReopenProvider() = runBlocking {
    val bytes = fixture("chart.png").readBytes()
    val file = File(inputs, "asset.bin").apply { writeBytes(ByteArray(17) { 7 } + bytes + ByteArray(29) { 9 }) }
    val selected = ImageCompressor(context).inspect(expose(file, 17, bytes.size.toLong()))
    assertEquals(bytes.size.toLong(), selected.sizeBytes)
    assertEquals(320, selected.width)
    assertEquals(192, selected.height)
    assertEquals(1, InspectionProvider.openCount)
    assertCleanAndClosed()
  }

  @Test fun zeroOffsetSliceDoesNotInspectTrailingBackingFileBytes() = runBlocking {
    val bytes = fixture("chart.png").readBytes()
    val file = File(inputs, "slice.bin").apply { writeBytes(bytes + "outside the selected asset".toByteArray()) }
    val selected = ImageCompressor(context).inspect(expose(file, length = bytes.size.toLong()))
    assertEquals(bytes.size.toLong(), selected.sizeBytes)
    assertEquals("PNG", selected.format)
    assertEquals(1, InspectionProvider.openCount)
    assertCleanAndClosed()
  }

  @Test @Config(shadows = [InspectionResolverShadow::class])
  fun unknownDescriptorSizeFallsBackWithoutLosingBytesOrLeakingFiles() = runBlocking {
    val file = fixture("astronaut.png")
    val selected = ImageCompressor(context).inspect(expose(file, unknownStat = true))
    assertEquals(file.length(), selected.sizeBytes)
    assertEquals(512, selected.width)
    assertEquals(1, InspectionProvider.openCount)
    assertCleanAndClosed()
  }

  @Test @Config(shadows = [InspectionResolverShadow::class])
  fun pipeProviderFallsBackToBoundedStaging() = runBlocking {
    val file = fixture("chart.png")
    val selected = ImageCompressor(context).inspect(expose(file, pipe = true))
    assertEquals(file.length(), selected.sizeBytes)
    assertEquals(320, selected.width)
    assertEquals(1, InspectionProvider.openCount)
    assertCleanAndClosed()
  }

  @Test fun directInspectionStillRejectsDamagedAndOversizeInputs() = runBlocking {
    val inspector = ImageCompressor(noStagingContext())
    val damaged = fixture("chart.png").apply {
      val bytes = readBytes()
      bytes[45] = (bytes[45].toInt() xor 1).toByte()
      writeBytes(bytes)
    }
    for (file in listOf(damaged, fixture("corrupt.png"), fixture("truncated.png"))) {
      val original = file.readBytes()
      try { inspector.inspect(expose(file)); fail("Expected damaged input rejection") }
      catch (failure: IOException) {
        if (file == damaged) assertTrue("The full PNG payload must be checked", failure.message!!.contains("checksum"))
      }
      assertArrayEquals(original, file.readBytes())
    }
    val oversize = File(inputs, "oversize.png")
    RandomAccessFile(oversize, "rw").use { it.setLength(100L * 1024 * 1024 + 1) }
    try { inspector.inspect(expose(oversize)); fail("Expected encoded size rejection") }
    catch (failure: IOException) { assertTrue(failure.message!!.contains("100 MiB")) }
    assertCleanAndClosed()
  }

  @Test fun cancelledInspectionDoesNotOpenOrStageAFile() = runBlocking {
    val uri = expose(fixture("chart.png"))
    val cancelled = Job().apply { cancel() }
    try { withContext(cancelled) { ImageCompressor(context).inspect(uri) }; fail("Expected cancellation") }
    catch (_: CancellationException) { /* expected */ }
    assertEquals(0, InspectionProvider.openCount)
    assertCleanAndClosed()
  }
}

class InspectionProvider : ContentProvider() {
  data class Entry(val file: File, val offset: Long, val length: Long, val unknownStat: Boolean, val pipe: Boolean)
  companion object {
    const val AUTHORITY = "com.example.inspection"
    val entries = mutableMapOf<String, Entry>()
    val opened = CopyOnWriteArrayList<FileDescriptor>()
    val writers = CopyOnWriteArrayList<Thread>()
    var openCount = 0
    lateinit var instance: InspectionProvider
  }
  override fun onCreate(): Boolean { instance = this; return true }
  override fun getType(uri: Uri) = "image/png"
  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
    val entry = checkNotNull(entries[uri.lastPathSegment])
    return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(entry.file.name)) }
  }
  override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
    val entry = checkNotNull(entries[uri.lastPathSegment])
    openCount++
    val descriptor = ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
    opened += descriptor.fileDescriptor
    val exposed = if (entry.unknownStat || entry.pipe) object : ParcelFileDescriptor(descriptor) {
      override fun getStatSize() = if (entry.pipe) 0L else -1L
      override fun getFileDescriptor(): FileDescriptor = descriptor.fileDescriptor
      override fun close() = descriptor.close()
    } else descriptor
    if (entry.pipe) {
      // Robolectric's createPipe() is a seekable temporary file. Model the real provider contract
      // explicitly: bytes arrive through a pipe, there is no seekable channel, and stat size is 0.
      val incoming = PipedInputStream(65_536)
      val outgoing = PipedOutputStream(incoming)
      writers += thread(isDaemon = true, name = "Inspection test pipe") {
        outgoing.use { output -> entry.file.inputStream().use { it.copyTo(output) } }
      }
      return object : AssetFileDescriptor(exposed, 0, AssetFileDescriptor.UNKNOWN_LENGTH) {
        override fun createInputStream(): FileInputStream = object : FileInputStream(exposed.fileDescriptor) {
          override fun read(): Int = incoming.read()
          override fun read(bytes: ByteArray): Int = incoming.read(bytes)
          override fun read(bytes: ByteArray, offset: Int, length: Int): Int = incoming.read(bytes, offset, length)
          override fun getChannel(): FileChannel = throw IOException("Illegal seek on provider pipe")
          override fun close() { try { incoming.close() } finally { super.close() } }
        }
      }
    }
    return AssetFileDescriptor(exposed, entry.offset, entry.length)
  }
  override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

/** Preserve test-only descriptor behavior that a Binder parcel would flatten to Robolectric's
 * regular-file PFD shadow. Normal seekable and bounded-slice tests use the real resolver path. */
@Implements(ContentResolver::class)
class InspectionResolverShadow : ShadowContentResolver() {
  @Implementation
  protected fun openAssetFileDescriptor(uri: Uri, mode: String): AssetFileDescriptor =
    InspectionProvider.instance.openAssetFile(uri, mode)
}
