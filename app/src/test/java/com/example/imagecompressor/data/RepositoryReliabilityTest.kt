package com.example.imagecompressor.data

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import com.example.imagecompressor.data.local.*
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.data.storage.OutputStore
import com.example.imagecompressor.util.GalleryExporter
import com.example.imagecompressor.util.ImageProcessor
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RepositoryReliabilityTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private lateinit var database: ImageCompressorDatabase
  private lateinit var processor: Processor
  private lateinit var gallery: Exporter
  private lateinit var repository: DefaultImageRepository
  @Before fun setup() {
    Robolectric.buildContentProvider(FileProvider::class.java).create(checkNotNull(context.packageManager.resolveContentProvider(
      "${context.packageName}.fileprovider", PackageManager.GET_META_DATA)))
    database = Room.inMemoryDatabaseBuilder(context, ImageCompressorDatabase::class.java).build()
    processor = Processor(); gallery = Exporter(); repository = repo()
  }
  private fun repo(limit: Long = 512L * 1024 * 1024) = DefaultImageRepository(context, database, processor, gallery, OutputStore(context, limit))
  @After fun close() { database.close(); context.cacheDir.deleteRecursively(); File(context.filesDir, "compressed").deleteRecursively() }
  private fun image(name: String) = SelectedImage(Uri.parse("content://selected/$name"), name, 1234, 128, 128, "JPEG")
  private suspend fun snapshot() = checkNotNull(repository.batch.first())

  private inner class Processor : ImageProcessor {
    val calls = mutableListOf<String>()
    val failures = mutableSetOf<String>()
    var before: suspend (SelectedImage) -> Unit = {}
    override suspend fun inspect(uri: Uri): SelectedImage {
      if (uri.lastPathSegment in failures) throw SecurityException("revoked")
      return image(checkNotNull(uri.lastPathSegment))
    }
    override suspend fun compress(image: SelectedImage, settings: CompressionSettings): CompressedImage {
      calls += image.displayName
      before(image)
      if (image.displayName in failures) throw IOException("The image is damaged.")
      val directory = File(context.cacheDir, "compressed").apply { mkdirs() }
      val file = File(directory, "ImageCompressor_${UUID.randomUUID()}.jpg").apply { writeBytes(ByteArray(32) { it.toByte() }) }
      return CompressedImage(image, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        file.absolutePath, file.length(), 128, 128, OutputFormat.JPEG, targetBytes = settings.targetBytes)
    }
  }
  private inner class Exporter : GalleryExporter {
    var calls = 0
    val published = mutableSetOf<Uri>()
    override fun isReadable(uri: Uri): Boolean = uri in published || try {
      context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } ?: false
    } catch (_: Exception) { false }
    override fun recover(uri: Uri): Uri? = uri.takeIf { it in published }
    override suspend fun save(image: CompressedImage, pendingUri: Uri?, onReserved: suspend (Uri?) -> Unit, onPublished: suspend (Uri) -> Unit): Uri {
      calls++
      val uri = Uri.parse("content://gallery/${image.historyId}")
      onReserved(uri); published += uri; onPublished(uri)
      return uri
    }
  }

  @Test fun failureKeepsOtherResultsAndRetryOnlyProcessesFailedItems() = runBlocking {
    processor.failures += "bad"
    repository.compressImages(listOf(image("first"), image("bad"), image("last")), CompressionSettings(quality = 73))
    val before = snapshot()
    assertEquals(listOf(ItemStatus.COMPLETED, ItemStatus.FAILED, ItemStatus.COMPLETED), before.items.map { it.status })
    assertEquals(2, before.results.size)
    val originalIds = before.results.map { it.historyId }
    processor.failures.clear()
    repository.retryBatch()
    val after = snapshot()
    assertEquals(3, after.results.size)
    assertTrue(after.results.map { it.historyId }.containsAll(originalIds))
    assertEquals(listOf("first", "bad", "last", "bad"), processor.calls)
    assertEquals(73, after.settings.quality)
    assertEquals(3, database.historyDao().all().size)
    assertTrue(after.results.all { File(it.outputFilePath).parentFile == File(context.filesDir, "compressed") })
  }

  @Test fun cancellationKeepsCommittedItemsAndRetrySkipsThem() = runBlocking {
    val entered = CompletableDeferred<Unit>()
    processor.before = { if (it.displayName == "slow") { entered.complete(Unit); awaitCancellation() } }
    val job = launch { repository.compressImages(listOf(image("first"), image("slow"), image("last")), CompressionSettings()) }
    withTimeout(10000) { entered.await() }
    job.cancelAndJoin()
    assertEquals(BatchStatus.CANCELLED, snapshot().status)
    assertEquals(listOf(ItemStatus.COMPLETED, ItemStatus.CANCELLED, ItemStatus.CANCELLED), snapshot().items.map { it.status })
    assertEquals(1, snapshot().results.size)
    processor.before = {}
    repository.retryBatch()
    assertEquals(3, snapshot().results.size)
    assertEquals(1, processor.calls.count { it == "first" })
  }

  @Test fun processRestartMarksUnfinishedItemsInterruptedAndKeepsCompletedFiles() = runBlocking {
    repository.compressImages(listOf(image("first")), CompressionSettings(targetBytes = 50))
    val retained = snapshot().results.single()
    val batchId = snapshot().id
    database.batchDao().insertItems(listOf(BatchItemEntity("interrupted-item", batchId, 1, image("second").uri.toString(),
      "second", 1234, 128, 128, "JPEG", "RUNNING")))
    database.batchDao().setStatus("RUNNING")
    File(context.filesDir, "compressed/result_orphan.jpg").writeText("unfinished")
    File(context.cacheDir, "image-source-abandoned.tmp").writeText("partial")
    repository = repo()
    repository.initialize()
    val restored = snapshot()
    assertEquals(BatchStatus.INTERRUPTED, restored.status)
    assertEquals(ItemStatus.INTERRUPTED, restored.items.last().status)
    assertEquals(retained.historyId, restored.results.single().historyId)
    assertTrue(File(retained.outputFilePath).exists())
    assertFalse(File(context.filesDir, "compressed/result_orphan.jpg").exists())
    assertFalse(File(context.cacheDir, "image-source-abandoned.tmp").exists())
    repository.retryBatch()
    assertEquals(2, snapshot().results.size)
  }

  @Test fun repeatedConcurrentSaveRequestsCreateOneGalleryCopyAndDeletionKeepsIt() = runBlocking {
    repository.compressImages(listOf(image("photo")), CompressionSettings())
    val output = snapshot().results.single()
    val copies = awaitAll(async { repository.saveToGallery(output) }, async { repository.saveToGallery(output) })
    assertEquals(1, gallery.calls)
    assertEquals(copies[0].savedUri, copies[1].savedUri)
    val history = database.historyDao().all().single()
    assertEquals("EXPORTED", history.availability)
    repository.deleteHistory(history)
    assertFalse(File(output.outputFilePath).exists())
    assertTrue(checkNotNull(copies[0].savedUri) in gallery.published)
    assertTrue(database.historyDao().all().isEmpty())
  }

  @Test fun recoveryRecognizesExportPublishedBeforeDatabaseAcknowledgement() = runBlocking {
    repository.compressImages(listOf(image("photo")), CompressionSettings())
    val output = snapshot().results.single()
    val published = Uri.parse("content://gallery/recovered")
    gallery.published += published
    database.historyDao().setPendingSave(output.historyId, published.toString())
    repository = repo()
    repository.initialize()
    assertEquals(published, repository.saveToGallery(output).savedUri)
    assertEquals(0, gallery.calls)
    assertNull(database.historyDao().find(output.historyId)!!.pendingSaveUri)
  }

  @Test fun quotaFailurePreservesPreviousResultAndRemovesNewTemporaryOutput() = runBlocking {
    repository = repo(limit = 40)
    repository.compressImages(listOf(image("first"), image("second")), CompressionSettings())
    val batch = snapshot()
    assertEquals(1, batch.results.size)
    assertEquals(ItemStatus.FAILED, batch.items.last().status)
    assertTrue(batch.items.last().error!!.contains("another copy"))
    assertEquals(32L, OutputStore(context).bytesUsed())
    assertTrue(File(context.cacheDir, "compressed").listFiles().orEmpty().none { it.isFile })
  }

  @Test fun unavailableFileIsMarkedAndCannotBeShared() = runBlocking {
    repository.compressImages(listOf(image("photo")), CompressionSettings())
    val result = snapshot().results.single()
    File(result.outputFilePath).delete()
    repository.refreshAvailability()
    assertEquals("UNAVAILABLE", database.historyDao().all().single().availability)
    try { repository.shareUri(result.historyId); fail("Expected unavailable result") } catch (_: IOException) { }
  }

  @Test fun selectionKeepsReadableImagesWhenAnotherGrantIsRevokedAndEnforcesLimit() = runBlocking {
    processor.failures += "revoked"
    val selection = repository.inspectImages(listOf(image("ok").uri, image("revoked").uri))
    assertEquals(listOf("ok"), selection.images.map { it.displayName })
    assertTrue(selection.errors.single().contains("Select the original again"))
    try { repository.inspectImages((0..30).map { image("image-$it").uri }); fail("Expected batch limit") }
    catch (_: IllegalArgumentException) { }
  }

  @Test fun clearHistoryKeepsGalleryCopiesAndOriginals() = runBlocking {
    val original = File(context.filesDir, "original.jpg").apply { writeText("original") }
    repository.compressImages(listOf(image("one"), image("two")), CompressionSettings())
    repository.saveToGallery(snapshot().results.first())
    repository.clearHistory()
    assertEquals("original", original.readText())
    assertEquals(1, gallery.published.size)
    assertEquals(0L, OutputStore(context).bytesUsed())
    assertTrue(database.historyDao().all().isEmpty())
    assertNull(repository.batch.first())
  }

  @Test fun legacyCacheResultIsCopiedIntoDurableStorageWithoutLosingItsHistory() = runBlocking {
    val directory = File(context.cacheDir, "compressed").apply { mkdirs() }
    val file = File(directory, "ImageCompressor_legacy.jpg").apply { writeText("existing result") }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val id = database.historyDao().insert(CompressionHistoryEntity(createdAt = 1, displayName = "legacy", originalUri = "content://original/legacy",
      compressedUri = uri.toString(), originalSizeBytes = 100, compressedSizeBytes = file.length(), width = 10, height = 10, outputFormat = "JPEG"))
    repository.initialize()
    val row = database.historyDao().find(id)!!
    assertEquals("RETAINED", row.availability)
    assertEquals("existing result", File(checkNotNull(row.localPath)).readText())
    assertTrue(row.compressedUri.contains("retained_images"))
    assertFalse(file.exists())
  }

  @Test fun databaseWriteFailureRollsBackOnlyTheNewFile() = runBlocking {
    processor.before = { if (it.displayName == "second") database.openHelper.writableDatabase.execSQL(
      "CREATE TRIGGER reject_new_result BEFORE INSERT ON compression_history BEGIN SELECT RAISE(ABORT, 'storage full'); END") }
    repository.compressImages(listOf(image("first"), image("second")), CompressionSettings())
    assertEquals(listOf(ItemStatus.COMPLETED, ItemStatus.FAILED), snapshot().items.map { it.status })
    assertEquals(1, database.historyDao().all().size)
    assertEquals(32L, OutputStore(context).bytesUsed())
    assertTrue(File(context.cacheDir, "compressed").listFiles().orEmpty().none { it.isFile })
  }

  @Test fun duplicateBatchAndDeletionAreRejectedWhileTheWorkerIsActive() = runBlocking {
    val entered = CompletableDeferred<Unit>()
    processor.before = { entered.complete(Unit); awaitCancellation() }
    val job = launch { repository.compressImages(listOf(image("slow")), CompressionSettings()) }
    withTimeout(10000) { entered.await() }
    try {
      try { repository.compressImages(listOf(image("duplicate")), CompressionSettings()); fail("Expected duplicate rejection") }
      catch (_: IllegalStateException) { }
      try { repository.clearHistory(); fail("Expected active job protection") }
      catch (_: IllegalStateException) { }
      assertEquals(listOf("slow"), processor.calls)
    } finally { job.cancelAndJoin() }
  }

  @Test fun cleanupNeverTraversesOutsideTheOwnedDirectory() {
    val original = File(context.filesDir, "original-owned-by-user.jpg").apply { writeText("keep") }
    val store = OutputStore(context)
    store.delete(original.absolutePath)
    store.delete(File(store.directory, "../original-owned-by-user.jpg").path)
    assertEquals("keep", original.readText())
  }
}
