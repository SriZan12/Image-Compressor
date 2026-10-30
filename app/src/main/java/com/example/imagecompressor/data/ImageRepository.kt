package com.example.imagecompressor.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import androidx.core.net.toUri
import com.example.imagecompressor.data.local.*
import com.example.imagecompressor.data.model.*
import com.example.imagecompressor.data.storage.OutputStore
import com.example.imagecompressor.util.GalleryExporter
import com.example.imagecompressor.util.ImageProcessor
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface ImageRepository {
  val history: Flow<List<CompressionHistoryEntity>>
  val batch: Flow<BatchSnapshot?>
  suspend fun initialize()
  suspend fun refreshAvailability()
  suspend fun inspectImages(uris: List<Uri>): SelectionResult
  suspend fun retainPreview(image: CompressedImage): CompressedImage =
    throw UnsupportedOperationException("This repository cannot retain temporary previews.")
  suspend fun compressImages(images: List<SelectedImage>, settings: CompressionSettings)
  suspend fun retryBatch()
  suspend fun dismissBatch()
  suspend fun saveToGallery(image: CompressedImage): CompressedImage
  suspend fun getResult(id: Long): CompressedImage?
  suspend fun shareUri(id: Long): Uri
  suspend fun deleteHistory(item: CompressionHistoryEntity)
  suspend fun clearHistory()
}

class DefaultImageRepository(
  private val context: Context,
  private val database: ImageCompressorDatabase,
  private val imageCompressor: ImageProcessor,
  private val gallerySaver: GalleryExporter,
  private val outputs: OutputStore = OutputStore(context),
) : ImageRepository {
  private val historyDao = database.historyDao()
  private val batchDao = database.batchDao()
  private val initialization = Mutex()
  private val storage = Mutex()
  private val worker = Mutex()
  private var initialized = false

  override val history = flow { initialize(); emitAll(historyDao.observeAll()) }
  override val batch: Flow<BatchSnapshot?> = flow {
    initialize()
    emitAll(combine(batchDao.observeCurrent(), historyDao.observeAll()) { batches, history ->
      batches.firstOrNull()?.let { stored ->
        BatchSnapshot(stored.batch.id, SettingsCodec.decode(stored.batch.settingsJson), BatchStatus.valueOf(stored.batch.status),
          stored.items.sortedBy { it.position }.map { item ->
            BatchItem(item.id, item.selectedImage(), ItemStatus.valueOf(item.status),
              history.firstOrNull { it.id == item.historyId }?.let(::result), item.error)
          })
      }
    })
  }

  override suspend fun initialize() = withContext(Dispatchers.IO) {
    initialization.withLock {
      if (!initialized) {
        storage.withLock {
          // Free leftovers before legacy copying, so a killed migration cannot fill storage forever.
          outputs.cleanOrphans(historyDao.all().mapNotNull { it.localPath }.toSet(), cleanTransientOutputs = false)
          database.withTransaction {
            if (batchDao.current()?.batch?.status == BatchStatus.RUNNING.name) {
              batchDao.interruptItems(ItemStatus.INTERRUPTED.name, "Interrupted before completion. Retry, or select the original again if access has expired.")
              batchDao.setStatus(BatchStatus.INTERRUPTED.name)
            }
          }
          reconcile()
          outputs.cleanOrphans(historyDao.all().mapNotNull { it.localPath }.toSet())
        }
        initialized = true
      }
    }
  }

  private suspend fun reconcile() {
    for (old in historyDao.all()) reconcile(old)
  }

  private suspend fun reconcile(old: CompressionHistoryEntity): CompressionHistoryEntity {
      val migrated = if (old.localPath == null) outputs.migrateLegacy(old.compressedUri) else null
      val local = outputs.readableFile(old.localPath) ?: migrated
      val exported = old.pendingSaveUri?.let { gallerySaver.recover(it.toUri()) }
        ?: old.savedPath?.let(Uri::parse)?.takeIf(gallerySaver::isReadable)
      val updated = old.copy(localPath = local?.absolutePath, compressedUri = local?.let(outputs::uri)?.toString() ?: old.compressedUri,
        pendingSaveUri = null, savedPath = exported?.toString(),
        availability = when { exported != null -> "EXPORTED"; local != null -> "RETAINED"; else -> "UNAVAILABLE" })
      if (updated != old) historyDao.update(updated)
      return updated
  }

  override suspend fun refreshAvailability() = withContext(Dispatchers.IO) {
    initialize()
    storage.withLock { reconcile() }
  }

  override suspend fun inspectImages(uris: List<Uri>): SelectionResult = withContext(Dispatchers.IO) {
    initialize()
    require(uris.distinct().size <= MAX_BATCH_IMAGES) { "Select at most $MAX_BATCH_IMAGES images per batch." }
    val images = mutableListOf<SelectedImage>()
    val errors = mutableListOf<String>()
    for (uri in uris.distinct()) {
      currentCoroutineContext().ensureActive()
      try {
        require(uri.toString().length <= 4096) { "This image link is too long. Select the image from another provider." }
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* Some providers grant access only for this session. */ }
        images += imageCompressor.inspect(uri).let { it.copy(displayName = it.displayName.take(200)) }
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (failure: Exception) { errors += userMessage(failure, "Unable to read this image. Select it again.") }
    }
    SelectionResult(images, errors)
  }

  override suspend fun retainPreview(image: CompressedImage): CompressedImage = withContext(Dispatchers.IO) {
    initialize()
    storage.withLock {
      currentCoroutineContext().ensureActive()
      val destination = outputs.previewDestination(image)
      val destinationUri = outputs.uri(destination).toString()
      val existing = historyDao.findByOutput(destination.absolutePath, destinationUri)
      if (existing != null) {
        requireMatchingPreview(existing, image)
        val available = reconcile(existing)
        if (available.localPath != null) {
          outputs.verifyPreviewCopy(destination, image)
          return@withLock checkNotNull(result(available)).copy(verifiedSha256 = image.verifiedSha256)
        }
        if (available.savedPath != null)
          return@withLock checkNotNull(result(available)).copy(verifiedSha256 = image.verifiedSha256)
      }
      var committed = false
      try {
        val retained = outputs.retainPreview(image)
        currentCoroutineContext().ensureActive()
        // Only the small durable commit is non-cancellable. The potentially large copy above can
        // stop immediately; a cancelled dispatcher return below must still leave history usable.
        val stored = withContext(NonCancellable) {
          database.withTransaction {
            val old = historyDao.findByOutput(destination.absolutePath, destinationUri)
            val row = if (old != null) {
              requireMatchingPreview(old, image)
              old.copy(localPath = retained.outputFilePath, compressedUri = retained.outputUri.toString(),
                availability = if (old.savedPath != null) "EXPORTED" else "RETAINED")
                .also { historyDao.update(it) }
            } else {
              val new = CompressionHistoryEntity(createdAt = System.currentTimeMillis(),
                displayName = retained.original.displayName, originalUri = retained.original.uri.toString(),
                compressedUri = retained.outputUri.toString(), originalSizeBytes = retained.original.sizeBytes,
                compressedSizeBytes = retained.compressedSizeBytes, width = retained.width, height = retained.height,
                outputFormat = retained.format.name, localPath = retained.outputFilePath, availability = "RETAINED",
                sourceWidth = retained.original.width, sourceHeight = retained.original.height,
                sourceFormat = retained.original.format, targetBytes = retained.targetBytes,
                adjustments = retained.adjustments.joinToString(",") { it.name },
                encodingAttempts = retained.encodingAttempts, encodingQuality = retained.encodingQuality)
              new.copy(id = historyDao.insert(new))
            }
            row
          }.also { committed = true }
        }
        checkNotNull(result(stored)).copy(verifiedSha256 = image.verifiedSha256)
      } catch (failure: Throwable) {
        if (!committed) withContext(NonCancellable) {
          try {
            // The move may have succeeded before URI construction or Room failed. Clean by the
            // predetermined path, while preserving any result that actually reached the database.
            if (historyDao.findByOutput(destination.absolutePath, destinationUri)?.localPath != destination.absolutePath)
              outputs.delete(destination.absolutePath)
          } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        }
        throw failure
      }
    }
  }

  private fun requireMatchingPreview(stored: CompressionHistoryEntity, image: CompressedImage) {
    if (stored.originalUri != image.original.uri.toString() || stored.originalSizeBytes != image.original.sizeBytes ||
      stored.sourceWidth != image.original.width || stored.sourceHeight != image.original.height ||
      stored.sourceFormat != image.original.format || stored.compressedSizeBytes != image.compressedSizeBytes ||
      stored.width != image.width || stored.height != image.height || stored.outputFormat != image.format.name ||
      stored.targetBytes != image.targetBytes || stored.encodingQuality != image.encodingQuality ||
      stored.encodingAttempts != image.encodingAttempts ||
      stored.adjustments.split(',').filter { it.isNotEmpty() }.toSet() != image.adjustments.map { it.name }.toSet())
      throw IOException("This preview changed. Adjust the photo and try again before saving.")
  }

  override suspend fun compressImages(images: List<SelectedImage>, settings: CompressionSettings) = withContext(Dispatchers.IO) {
    initialize()
    require(images.isNotEmpty() && images.size <= MAX_BATCH_IMAGES) { "Choose between 1 and $MAX_BATCH_IMAGES images." }
    check(worker.tryLock()) { "A compression batch is already running." }
    try {
      storage.withLock {
        outputs.checkCapacity()
        val id = UUID.randomUUID().toString()
        database.withTransaction {
          batchDao.deleteItems(); batchDao.deleteBatch()
          batchDao.insert(BatchEntity(id, SettingsCodec.encode(settings), BatchStatus.RUNNING.name))
          batchDao.insertItems(images.distinctBy { it.id }.mapIndexed { index, image ->
            BatchItemEntity(UUID.randomUUID().toString(), id, index, image.uri.toString(), image.displayName,
              image.sizeBytes, image.width, image.height, image.format, ItemStatus.PENDING.name)
          })
        }
      }
      executeBatch()
    } finally { try { settleUnfinished() } finally { worker.unlock() } }
  }

  override suspend fun retryBatch() = withContext(Dispatchers.IO) {
    initialize()
    check(worker.tryLock()) { "A compression batch is already running." }
    try {
      storage.withLock {
        outputs.checkCapacity()
        database.withTransaction {
          val batch = batchDao.current() ?: throw IOException("This batch is no longer available.")
          for (item in batch.items.filter { it.status in setOf("FAILED", "CANCELLED", "INTERRUPTED") }) {
            batchDao.updateItem(item.id, ItemStatus.PENDING.name)
          }
          batchDao.setStatus(BatchStatus.RUNNING.name)
        }
      }
      executeBatch()
    } finally { try { settleUnfinished() } finally { worker.unlock() } }
  }

  private suspend fun settleUnfinished() {
    val cancelled = !currentCoroutineContext().isActive
    withContext(NonCancellable) {
      database.withTransaction {
        if (batchDao.current()?.batch?.status == BatchStatus.RUNNING.name) {
          batchDao.interruptItems(if (cancelled) "CANCELLED" else "INTERRUPTED", "Interrupted before completion. Retry the remaining images.")
          batchDao.setStatus(if (cancelled) "CANCELLED" else "INTERRUPTED")
        }
      }
    }
  }

  private suspend fun executeBatch() {
    try {
      val batch = checkNotNull(batchDao.current())
      val settings = SettingsCodec.decode(batch.batch.settingsJson)
      for (item in batch.items.sortedBy { it.position }.filter { it.status == ItemStatus.PENDING.name }) {
        currentCoroutineContext().ensureActive()
        batchDao.updateItem(item.id, ItemStatus.RUNNING.name)
        try {
          storage.withLock { outputs.checkCapacity() }
          val encoded = imageCompressor.compress(item.selectedImage(), settings)
          // A completed item is committed independently of later items or caller cancellation.
          withContext(NonCancellable) {
            storage.withLock {
              var retained: CompressedImage? = null
              try {
                retained = outputs.retain(encoded)
                val output = retained
                database.withTransaction {
                  val id = historyDao.insert(CompressionHistoryEntity(createdAt = System.currentTimeMillis(),
                    displayName = output.original.displayName, originalUri = output.original.uri.toString(),
                    compressedUri = output.outputUri.toString(), originalSizeBytes = output.original.sizeBytes,
                    compressedSizeBytes = output.compressedSizeBytes, width = output.width, height = output.height,
                    outputFormat = output.format.name, localPath = output.outputFilePath, availability = "RETAINED",
                    sourceWidth = output.original.width, sourceHeight = output.original.height, sourceFormat = output.original.format,
                    targetBytes = output.targetBytes, adjustments = output.adjustments.joinToString(",") { it.name },
                    encodingAttempts = output.encodingAttempts, encodingQuality = output.encodingQuality))
                  batchDao.updateItem(item.id, ItemStatus.COMPLETED.name, id)
                }
              } catch (failure: Throwable) {
                try { retained?.let { outputs.delete(it.outputFilePath) } }
                catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
                throw failure
              } finally { outputs.discardTemporary(encoded.outputFilePath) }
            }
          }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
          batchDao.updateItem(item.id, ItemStatus.FAILED.name, error = userMessage(failure, "Unable to compress this image. Try again."))
        }
      }
      batchDao.setStatus(BatchStatus.COMPLETED.name)
    } catch (cancelled: CancellationException) {
      withContext(NonCancellable) { database.withTransaction {
        batchDao.interruptItems(ItemStatus.CANCELLED.name, "Cancelled. Completed images were kept.")
        batchDao.setStatus(BatchStatus.CANCELLED.name)
      } }
      throw cancelled
    } catch (failure: Exception) {
      withContext(NonCancellable) { database.withTransaction {
        batchDao.interruptItems(ItemStatus.INTERRUPTED.name, "Interrupted. Completed images were kept; retry the remaining images.")
        batchDao.setStatus(BatchStatus.INTERRUPTED.name)
      } }
      throw failure
    }
  }

  private fun result(item: CompressionHistoryEntity): CompressedImage? {
    if (item.availability == "UNAVAILABLE") return null
    val uri = item.localPath?.let { item.compressedUri.toUri() } ?: item.savedPath?.let(Uri::parse) ?: return null
    return CompressedImage(SelectedImage(item.originalUri.toUri(), item.displayName, item.originalSizeBytes,
      item.sourceWidth, item.sourceHeight, item.sourceFormat), uri, item.localPath.orEmpty(), item.compressedSizeBytes,
      item.width, item.height, OutputFormat.valueOf(item.outputFormat), historyId = item.id,
      savedUri = item.savedPath?.let(Uri::parse), targetBytes = item.targetBytes,
      adjustments = item.adjustments.split(',').mapNotNull { value -> CompressionAdjustment.entries.find { it.name == value } }.toSet(),
      encodingAttempts = item.encodingAttempts, encodingQuality = item.encodingQuality)
  }

  override suspend fun getResult(id: Long): CompressedImage? = withContext(Dispatchers.IO) {
    initialize()
    storage.withLock { historyDao.find(id)?.let { result(reconcile(it)) } }
  }

  override suspend fun shareUri(id: Long): Uri = withContext(Dispatchers.IO) {
    val image = getResult(id) ?: throw IOException("This result is unavailable. Compress the original again.")
    // Gallery copies can be edited by other apps. Prefer our immutable retained candidate whenever
    // it is still available, so sharing from the editor sends the bytes that were measured.
    val uri = if (image.outputFilePath.isNotEmpty()) image.outputUri else image.savedUri ?: image.outputUri
    if (!gallerySaver.isReadable(uri)) throw IOException("This image can no longer be opened. Select the original again.")
    uri
  }

  override suspend fun saveToGallery(image: CompressedImage): CompressedImage = withContext(Dispatchers.IO) {
    initialize()
    storage.withLock {
      val stored = historyDao.find(image.historyId) ?: throw IOException("This result was removed. Compress the original again.")
      stored.savedPath?.let(Uri::parse)?.takeIf(gallerySaver::isReadable)?.let { return@withLock checkNotNull(result(stored)) }
      val actual = result(stored.copy(savedPath = null)) ?: throw IOException("This result is unavailable. Compress the original again.")
      gallerySaver.save(actual, stored.pendingSaveUri?.let(Uri::parse),
        onReserved = { historyDao.setPendingSave(stored.id, it?.toString()) },
        onPublished = { historyDao.updateSavedPath(stored.id, it.toString()) })
      checkNotNull(historyDao.find(stored.id)?.let(::result))
    }
  }

  private suspend fun requireIdle() {
    check(!worker.isLocked && batchDao.current()?.batch?.status != BatchStatus.RUNNING.name) { "Wait for compression to finish or cancel it first." }
  }
  override suspend fun dismissBatch() = withContext(Dispatchers.IO) {
    initialize()
    storage.withLock { requireIdle(); database.withTransaction { batchDao.deleteItems(); batchDao.deleteBatch() } }
  }
  override suspend fun deleteHistory(item: CompressionHistoryEntity) = withContext(Dispatchers.IO) {
    initialize()
    storage.withLock {
      requireIdle()
      val stored = historyDao.find(item.id) ?: return@withLock
      // Remove the owned file first: failure keeps the record available for retry.
      outputs.delete(stored.localPath)
      database.withTransaction {
        if (batchDao.current()?.items?.any { it.historyId == item.id } == true) {
          batchDao.deleteItems(); batchDao.deleteBatch()
        }
        historyDao.delete(stored)
      }
    }
  }
  override suspend fun clearHistory() = withContext(Dispatchers.IO) {
    initialize()
    storage.withLock {
      requireIdle()
      historyDao.all().forEach { outputs.delete(it.localPath) }
      database.withTransaction { batchDao.deleteItems(); batchDao.deleteBatch(); historyDao.deleteAll() }
    }
  }
}

fun userMessage(failure: Exception, fallback: String): String = when (failure) {
  is SecurityException -> "Access to this image has expired. Select the original again."
  is android.database.sqlite.SQLiteException -> "Unable to update image history. Check available storage and try again."
  is java.io.FileNotFoundException -> "This image is no longer available. Select the original again."
  is IOException -> failure.message?.takeIf { !it.contains('/') } ?: fallback
  is IllegalArgumentException, is IllegalStateException -> failure.message?.takeIf { !it.contains('/') } ?: fallback
  else -> fallback
}
