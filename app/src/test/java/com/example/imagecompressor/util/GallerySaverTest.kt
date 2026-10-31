package com.example.imagecompressor.util

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.example.imagecompressor.data.model.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GallerySaverTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private lateinit var provider: TestMediaProvider
  private lateinit var saver: GallerySaver
  private lateinit var image: CompressedImage
  @Before fun setup() {
    provider = Robolectric.buildContentProvider(TestMediaProvider::class.java).create("media").get()
    saver = GallerySaver(context)
    val file = File(context.cacheDir, "result_test.jpg").apply { writeBytes(ByteArray(100_000) { it.toByte() }) }
    val source = SelectedImage(Uri.parse("content://source/original"), "photo", 200000, 100, 100, "JPEG")
    image = CompressedImage(source, Uri.fromFile(file), file.absolutePath, file.length(), 100, 100, OutputFormat.JPEG, historyId = 42)
  }
  @After fun cleanup() { context.cacheDir.deleteRecursively() }

  @Test fun completeSavePublishesExactBytesAndStableNamePreventsDuplicateExports() = runBlocking {
    var journal: Uri? = null
    val first = saver.save(image, null, { journal = it }, {})
    assertEquals(first, journal)
    assertEquals(0, provider.rows.values.single().values.getAsInteger(MediaStore.Images.Media.IS_PENDING))
    assertArrayEquals(File(image.outputFilePath).readBytes(), provider.rows.values.single().file.readBytes())
    val second = saver.save(image, null, {}, {})
    assertEquals(first, second)
    assertEquals(1, provider.inserts)
  }

  @Test fun failedPublicationRemovesPendingFileAndClearsJournal() = runBlocking {
    provider.failPublication = true
    var journal: Uri? = null
    try { saver.save(image, null, { journal = it }, {}); fail("Expected publication failure") }
    catch (_: IOException) { }
    assertNull(journal)
    assertTrue(provider.rows.isEmpty())
    assertTrue(File(image.outputFilePath).exists())
  }

  @Test fun cancelledCopyRemovesPendingEntryWithoutDeletingSource() = runBlocking {
    val job = Job()
    var journal: Uri? = null
    try {
      withContext(job) { saver.save(image, null, {
        journal = it
        if (it != null) job.cancel()
      }, {}) }
      fail("Expected cancellation")
    } catch (_: CancellationException) { }
    assertNull(journal)
    assertTrue(provider.rows.isEmpty())
    assertEquals(100_000L, File(image.outputFilePath).length())
  }

  @Test fun databaseAcknowledgementFailureDoesNotDeletePublishedPhotoAndRecoveryFindsIt() = runBlocking {
    var journal: Uri? = null
    try { saver.save(image, null, { journal = it }, { throw IOException("database unavailable") }); fail("Expected acknowledgement failure") }
    catch (_: IOException) { }
    assertEquals(1, provider.rows.size)
    assertEquals(0, provider.rows.values.single().values.getAsInteger(MediaStore.Images.Media.IS_PENDING))
    assertEquals(journal, saver.recover(checkNotNull(journal)))
    assertEquals(1, provider.rows.size)
    val saved = saver.save(image, journal, {}, {})
    assertEquals(journal, saved)
    assertEquals(1, provider.inserts)
  }

  @Test fun startupRecoveryDeletesOnlyIncompleteExports() = runBlocking {
    val pending = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
      put(MediaStore.Images.Media.IS_PENDING, 1)
    })!!
    assertNull(saver.recover(pending))
    val published = saver.save(image, null, {}, {})
    assertEquals(published, saver.recover(published))
    assertEquals(1, provider.rows.size)
  }

  @Test fun revokedPendingExportDoesNotBlockRecoveryOfOtherPhotos() = runBlocking {
    val unavailable = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
      put(MediaStore.Images.Media.IS_PENDING, 1)
    })!!
    provider.deniedQueries += unavailable
    assertNull(saver.recover(unavailable))
    val published = saver.save(image, null, {}, {})
    assertEquals(published, saver.recover(published))
    assertEquals(100_000L, File(image.outputFilePath).length())
  }

  @Test fun recoveryAndRetryNeverModifyAnotherAppsPendingPhoto() = runBlocking {
    val other = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
      put(MediaStore.Images.Media.IS_PENDING, 1)
    })!!
    val otherRow = provider.rows.getValue(checkNotNull(other.lastPathSegment).toLong())
    otherRow.values.put(MediaStore.Images.Media.OWNER_PACKAGE_NAME, "other.photo.app")
    otherRow.file.writeText("Keep the other app's photo")
    assertNull(saver.recover(other))
    val saved = saver.save(image, other, {}, {})
    assertNotEquals(other, saved)
    assertEquals("Keep the other app's photo", otherRow.file.readText())
    assertEquals(1, otherRow.values.getAsInteger(MediaStore.Images.Media.IS_PENDING))
    assertEquals(2, provider.rows.size)
  }
}

/** Exercises real ContentResolver/PFD streaming and MediaStore publication protocol with controlled failures. */
class TestMediaProvider : ContentProvider() {
  data class Row(val values: ContentValues, val file: File)
  val rows = mutableMapOf<Long, Row>()
  var inserts = 0
  var failPublication = false
  val deniedQueries = mutableSetOf<Uri>()
  override fun onCreate() = true
  override fun getType(uri: Uri) = "image/jpeg"
  override fun insert(uri: Uri, values: ContentValues?): Uri {
    val id = (++inserts).toLong()
    val stored = ContentValues(values ?: ContentValues()).apply { put(MediaStore.Images.Media.OWNER_PACKAGE_NAME, context!!.packageName) }
    rows[id] = Row(stored, File(context!!.cacheDir, "gallery-$id.jpg").apply { writeBytes(byteArrayOf()) })
    return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
  }
  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
    if (uri in deniedQueries) throw SecurityException("Access revoked")
    val columns = projection ?: arrayOf(MediaStore.Images.Media._ID)
    val cursor = MatrixCursor(columns)
    val id = uri.lastPathSegment?.toLongOrNull()
    val matching = if (id != null) rows.filterKeys { it == id } else rows.filterValues {
      selectionArgs == null || (it.values.getAsString(MediaStore.Images.Media.DISPLAY_NAME) == selectionArgs[0] &&
        it.values.getAsString(MediaStore.Images.Media.RELATIVE_PATH) == selectionArgs[1] &&
        it.values.getAsString(MediaStore.Images.Media.OWNER_PACKAGE_NAME) == selectionArgs[2])
    }
    matching.forEach { (key, row) -> cursor.addRow(columns.map { if (it == MediaStore.Images.Media._ID) key else row.values[it] }.toTypedArray()) }
    return cursor
  }
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
    if (failPublication) return 0
    val row = rows[uri.lastPathSegment?.toLongOrNull()] ?: return 0
    row.values.putAll(values)
    return 1
  }
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
    val row = rows.remove(uri.lastPathSegment?.toLongOrNull()) ?: return 0
    row.file.delete(); return 1
  }
  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
    ParcelFileDescriptor.open(rows[uri.lastPathSegment?.toLongOrNull()]?.file ?: throw java.io.FileNotFoundException(), ParcelFileDescriptor.parseMode(mode))
}
