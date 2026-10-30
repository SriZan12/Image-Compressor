package com.example.imagecompressor.data

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import com.example.imagecompressor.data.local.BatchEntity
import com.example.imagecompressor.data.local.ImageCompressorDatabase
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.data.storage.OutputStore
import com.example.imagecompressor.util.GalleryExporter
import com.example.imagecompressor.util.ImageCompressor
import com.example.imagecompressor.util.ImageProcessor
import com.example.imagecompressor.util.TemporaryImageProcessor
import com.example.imagecompressor.util.TemporaryPreview
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
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

/** Native candidates, real Room commits and real private-file copies; export never invokes encode. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewRetentionTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private lateinit var database: ImageCompressorDatabase
  private lateinit var repository: DefaultImageRepository
  private val processors = mutableListOf<TemporaryImageProcessor>()
  private val forbiddenEncoder = object : ImageProcessor {
    override suspend fun inspect(uri: Uri): SelectedImage = error("Retention must not inspect the source again")
    override suspend fun compress(image: SelectedImage, settings: CompressionSettings): CompressedImage =
      error("Retention must never encode again")
  }
  private val gallery = object : GalleryExporter {
    var calls = 0
    val saved = mutableMapOf<Uri, File>()
    override fun isReadable(uri: Uri): Boolean = saved[uri]?.isFile == true || try {
      context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } ?: false
    } catch (_: Exception) { false }
    override fun recover(uri: Uri): Uri? = uri.takeIf { saved[it]?.isFile == true }
    override suspend fun save(image: CompressedImage, pendingUri: Uri?, onReserved: suspend (Uri?) -> Unit,
      onPublished: suspend (Uri) -> Unit): Uri {
      calls++
      val uri = Uri.parse("content://test-gallery/${image.historyId}")
      onReserved(uri)
      val output = File(context.filesDir, "retention-test-gallery/${image.historyId}.jpg")
      output.parentFile!!.mkdirs()
      File(image.outputFilePath).copyTo(output, overwrite = true)
      saved[uri] = output
      onPublished(uri)
      return uri
    }
  }

  @Before fun setup() {
    Robolectric.buildContentProvider(FileProvider::class.java).create(checkNotNull(context.packageManager.resolveContentProvider(
      "${context.packageName}.fileprovider", PackageManager.GET_META_DATA)))
    database = Room.inMemoryDatabaseBuilder(context, ImageCompressorDatabase::class.java).build()
    repository = repo()
  }
  private fun repo(limit: Long = 512L * 1024 * 1024) =
    DefaultImageRepository(context, database, forbiddenEncoder, gallery, OutputStore(context, limit))

  @After fun clean() {
    processors.forEach(TemporaryImageProcessor::close)
    database.close()
    context.cacheDir.deleteRecursively()
    File(context.filesDir, "compressed").deleteRecursively()
    File(context.filesDir, "retention-test-inputs").deleteRecursively()
    File(context.filesDir, "retention-test-gallery").deleteRecursively()
  }

  private fun fixture(): File = File(context.filesDir, "retention-test-inputs/chart.png").apply {
    parentFile!!.mkdirs()
    checkNotNull(PreviewRetentionTest::class.java.classLoader?.getResourceAsStream("compression/chart.png")).use { input ->
      outputStream().use(input::copyTo)
    }
  }
  private suspend fun preview(format: OutputFormat = OutputFormat.JPEG): TemporaryPreview {
    val engine = ImageCompressor(context, workingPixelLimit = 1_000_000)
    val processor = engine.createPreviewProcessor().also(processors::add)
    return processor.preparePreview(engine.inspect(Uri.fromFile(fixture())),
      CompressionSettings(quality = 77, targetBytes = if (format == OutputFormat.JPEG) 50_000 else null, outputFormat = format))
  }
  private fun retainedFiles() = File(context.filesDir, "compressed").listFiles().orEmpty().filter { it.isFile }
  private suspend fun expectFailure(action: suspend () -> Unit) {
    try { action(); fail("Expected a rejected preview") } catch (_: IOException) { }
  }

  @Test fun savesTheExactMeasuredBytesAndKeepsThemAfterEditorAndSourceAreClosed() = runBlocking {
    val preview = preview()
    val source = File(checkNotNull(preview.image.original.uri.path))
    val originalBytes = source.readBytes()
    val bytes = File(preview.image.outputFilePath).readBytes()
    assertTrue(database.historyDao().all().isEmpty())
    val retained = repository.retainPreview(preview.image)
    assertArrayEquals(bytes, File(retained.outputFilePath).readBytes())
    assertEquals(preview.image.compressedSizeBytes, retained.compressedSizeBytes)
    assertEquals(preview.image.verifiedSha256, retained.verifiedSha256)
    assertEquals(preview.image.targetBytes, retained.targetBytes)
    assertEquals(preview.image.encodingQuality, retained.encodingQuality)
    assertEquals(preview.image.encodingAttempts, retained.encodingAttempts)
    assertEquals(preview.image.adjustments, retained.adjustments)
    assertTrue(retained.historyId > 0)
    assertEquals("content", retained.outputUri.scheme)
    assertEquals("retained_images", retained.outputUri.pathSegments.first())
    preview.close()
    processors.forEach(TemporaryImageProcessor::close)
    assertFalse(File(preview.image.outputFilePath).exists())
    assertArrayEquals(originalBytes, source.readBytes())
    source.delete() // Export does not depend on a surviving provider grant or original.
    assertEquals(retained.outputUri, repository.shareUri(retained.historyId))
    context.contentResolver.openInputStream(retained.outputUri)!!.use { assertArrayEquals(bytes, it.readBytes()) }
    val decoded = checkNotNull(BitmapFactory.decodeFile(retained.outputFilePath))
    try { assertEquals(retained.width, decoded.width); assertEquals(retained.height, decoded.height) }
    finally { decoded.recycle() }
    assertEquals(1, database.historyDao().all().size)
  }

  @Test fun concurrentAndRepeatedRequestsCreateOneHistoryItemWithoutResettingBatch() = runBlocking {
    val preview = preview()
    repository.initialize()
    val batch = BatchEntity("existing-batch", SettingsCodec.encode(CompressionSettings(quality = 31)), "COMPLETED")
    database.batchDao().insert(batch)
    val copies = awaitAll(async { repository.retainPreview(preview.image) }, async { repository.retainPreview(preview.image) })
    assertEquals(copies[0].historyId, copies[1].historyId)
    assertEquals(copies[0].outputFilePath, copies[1].outputFilePath)
    preview.close()
    assertEquals(copies[0].historyId, repository.retainPreview(preview.image).historyId)
    assertEquals(batch, database.batchDao().current()!!.batch)
    assertEquals(1, retainedFiles().size)
    assertEquals(1, database.historyDao().all().size)
  }

  @Test fun leaseCanFinishRetentionAfterTheEditorCloses() = runBlocking {
    val preview = preview()
    val lease = checkNotNull(preview.acquire())
    preview.close()
    processors.forEach(TemporaryImageProcessor::close)
    assertTrue(File(lease.image.outputFilePath).isFile)
    val retained = repository.retainPreview(lease.image)
    lease.close()
    assertFalse(File(lease.image.outputFilePath).exists())
    assertTrue(File(retained.outputFilePath).isFile)
  }

  @Test fun retryRepairsAMissingLocalFileWithoutDuplicatingHistory() = runBlocking {
    val preview = preview()
    val first = repository.retainPreview(preview.image)
    File(first.outputFilePath).delete()
    repository.refreshAvailability()
    assertEquals("UNAVAILABLE", database.historyDao().all().single().availability)
    val restored = repository.retainPreview(preview.image)
    assertEquals(first.historyId, restored.historyId)
    assertArrayEquals(File(preview.image.outputFilePath).readBytes(), File(restored.outputFilePath).readBytes())
    assertEquals(1, database.historyDao().all().size)
  }

  @Test fun savedCopySupportsRetryAfterBothPrivateFilesDisappear() = runBlocking {
    val preview = preview()
    val retained = repository.retainPreview(preview.image)
    val saved = repository.saveToGallery(retained)
    assertArrayEquals(File(preview.image.outputFilePath).readBytes(), gallery.saved[saved.savedUri]!!.readBytes())
    File(retained.outputFilePath).delete()
    preview.close()
    val retried = repository.retainPreview(preview.image)
    assertEquals(retained.historyId, retried.historyId)
    assertEquals(saved.savedUri, retried.savedUri)
    assertEquals(saved.savedUri, repository.shareUri(retried.historyId))
    repository.saveToGallery(retried)
    assertEquals(1, gallery.calls)
    assertEquals(1, database.historyDao().all().size)
  }

  @Test fun sharingAfterSavingPrefersTheMeasuredPrivateCopyEvenIfTheGalleryCopyChanges() = runBlocking {
    val preview = preview()
    val retained = repository.retainPreview(preview.image)
    val bytes = File(retained.outputFilePath).readBytes()
    val saved = repository.saveToGallery(retained)
    gallery.saved[saved.savedUri]!!.writeText("edited by another app")
    assertEquals(retained.outputUri, repository.shareUri(retained.historyId))
    context.contentResolver.openInputStream(repository.shareUri(retained.historyId))!!.use {
      assertArrayEquals(bytes, it.readBytes())
    }
  }

  @Test fun rejectsMissingChangedAndSameLengthTamperedBytesWithoutCreatingHistory() = runBlocking {
    val preview = preview()
    val image = preview.image
    val file = File(image.outputFilePath)
    val original = file.readBytes()
    file.delete()
    expectFailure { repository.retainPreview(image) }
    file.writeBytes(original + byteArrayOf(0))
    expectFailure { repository.retainPreview(image) }
    file.writeBytes(original.clone().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() })
    expectFailure { repository.retainPreview(image) }
    assertTrue(retainedFiles().isEmpty())
    assertTrue(database.historyDao().all().isEmpty())
    file.writeBytes(original)
    assertTrue(repository.retainPreview(image).historyId > 0)
  }

  @Test fun rejectsInvalidOwnershipUnverifiedAndDisguisedFileType() = runBlocking {
    val preview = preview(OutputFormat.PNG)
    val image = preview.image
    expectFailure { repository.retainPreview(image.copy(verifiedSha256 = null)) }
    val original = File(checkNotNull(image.original.uri.path))
    expectFailure { repository.retainPreview(image.copy(outputFilePath = original.path, outputUri = Uri.fromFile(original))) }
    val link = File(File(image.outputFilePath).parentFile, "Preview_link.png")
    Files.createSymbolicLink(link.toPath(), File(image.outputFilePath).toPath())
    expectFailure { repository.retainPreview(image.copy(outputFilePath = link.path, outputUri = Uri.fromFile(link))) }
    val disguised = File(File(image.outputFilePath).parentFile, "Preview_disguised.jpg")
    File(image.outputFilePath).copyTo(disguised)
    expectFailure { repository.retainPreview(image.copy(outputFilePath = disguised.path,
      outputUri = Uri.fromFile(disguised), format = OutputFormat.JPEG)) }
    assertTrue(database.historyDao().all().isEmpty())
    assertTrue(retainedFiles().isEmpty())
    assertTrue(original.isFile)
    assertTrue(File(image.outputFilePath).isFile)
  }

  @Test fun tamperedRetainedCopyIsRejectedInsteadOfReportedAsTheVerifiedPreview() = runBlocking {
    val preview = preview()
    val retained = repository.retainPreview(preview.image)
    val file = File(retained.outputFilePath)
    val original = file.readBytes()
    file.writeBytes(original.clone().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() })
    expectFailure { repository.retainPreview(preview.image) }
    assertTrue(file.isFile)
    assertEquals(1, database.historyDao().all().size)
    file.writeBytes(original)
    assertEquals(retained.historyId, repository.retainPreview(preview.image).historyId)
  }

  @Test fun capacityFailureKeepsPreviewRetryableAndExistingCopyDoesNotConsumeQuotaTwice() = runBlocking {
    val preview = preview()
    repository = repo(preview.image.compressedSizeBytes - 1)
    expectFailure { repository.retainPreview(preview.image) }
    assertTrue(File(preview.image.outputFilePath).isFile)
    assertTrue(database.historyDao().all().isEmpty())
    assertTrue(retainedFiles().isEmpty())
    repository = repo(preview.image.compressedSizeBytes)
    val saved = repository.retainPreview(preview.image)
    assertEquals(saved.historyId, repository.retainPreview(preview.image).historyId)
    assertEquals(preview.image.compressedSizeBytes, OutputStore(context).bytesUsed())
  }

  @Test fun databaseFailureRollsBackTheCopiedFileAndLeavesPreviewRetryable() = runBlocking {
    val preview = preview()
    repository.initialize()
    database.openHelper.writableDatabase.execSQL(
      "CREATE TRIGGER reject_preview BEFORE INSERT ON compression_history BEGIN SELECT RAISE(ABORT, 'storage full'); END")
    try { repository.retainPreview(preview.image); fail("Expected database rejection") }
    catch (_: android.database.sqlite.SQLiteException) { }
    assertTrue(database.historyDao().all().isEmpty())
    assertTrue(retainedFiles().isEmpty())
    assertTrue(File(preview.image.outputFilePath).isFile)
    database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_preview")
    assertTrue(repository.retainPreview(preview.image).historyId > 0)
  }

  @Test fun cancellationAfterCommitKeepsTheDurableFileAndRetryDoesNotDuplicateIt() = runBlocking {
    val preview = preview()
    val caller = QueuedCallerDispatcher()
    val request = async(caller, start = CoroutineStart.UNDISPATCHED) { repository.retainPreview(preview.image) }
    val resume = checkNotNull(caller.tasks.poll(10, TimeUnit.SECONDS)) { "Retention did not return" }
    val committed = database.historyDao().all().single()
    assertTrue(File(checkNotNull(committed.localPath)).isFile)
    request.cancel()
    resume.run()
    try { request.await(); fail("Expected cancelled return") } catch (_: CancellationException) { }
    assertTrue(File(checkNotNull(committed.localPath)).isFile)
    val retry = repository.retainPreview(preview.image)
    assertEquals(committed.id, retry.historyId)
    assertEquals(1, database.historyDao().all().size)
    assertEquals(1, retainedFiles().size)
  }

  @Test fun startupRemovesAbandonedCopiesWithoutTouchingActivePrivatePreview() = runBlocking {
    val preview = preview()
    val orphan = File(context.filesDir, "compressed/preview-copy_abandoned.part").apply {
      parentFile!!.mkdirs(); writeText("interrupted copy")
    }
    repository.initialize()
    assertFalse(orphan.exists())
    assertTrue(File(preview.image.outputFilePath).isFile)
    assertTrue(database.historyDao().all().isEmpty())
  }

  private class QueuedCallerDispatcher : CoroutineDispatcher() {
    val tasks = LinkedBlockingQueue<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
  }
}
