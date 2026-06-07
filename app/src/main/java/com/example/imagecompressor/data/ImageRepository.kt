package com.example.imagecompressor.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.imagecompressor.data.local.CompressionHistoryDao
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.util.GallerySaver
import com.example.imagecompressor.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface ImageRepository {
  val history: Flow<List<CompressionHistoryEntity>>

  suspend fun inspectImages(uris: List<Uri>): List<SelectedImage>

  suspend fun compressImages(
    images: List<SelectedImage>,
    settings: CompressionSettings,
    onProgress: suspend (completed: Int, total: Int) -> Unit,
  ): List<CompressedImage>

  suspend fun saveToGallery(image: CompressedImage): CompressedImage

  suspend fun deleteHistory(item: CompressionHistoryEntity)

  suspend fun clearHistory()
}

class DefaultImageRepository(
  private val context: Context,
  private val historyDao: CompressionHistoryDao,
  private val imageCompressor: ImageCompressor,
  private val gallerySaver: GallerySaver,
) : ImageRepository {
  override val history = historyDao.observeAll()

  override suspend fun inspectImages(uris: List<Uri>): List<SelectedImage> =
    withContext(Dispatchers.IO) {
      uris.distinct().map { uri ->
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        imageCompressor.inspect(uri)
      }
    }

  override suspend fun compressImages(
    images: List<SelectedImage>,
    settings: CompressionSettings,
    onProgress: suspend (completed: Int, total: Int) -> Unit,
  ): List<CompressedImage> =
    withContext(Dispatchers.IO) {
      images.mapIndexed { index, image ->
        val compressed = imageCompressor.compress(image, settings)
        val historyId =
          historyDao.insert(
            CompressionHistoryEntity(
              createdAt = System.currentTimeMillis(),
              displayName = image.displayName,
              originalUri = image.uri.toString(),
              compressedUri = compressed.outputUri.toString(),
              originalSizeBytes = image.sizeBytes,
              compressedSizeBytes = compressed.compressedSizeBytes,
              width = compressed.width,
              height = compressed.height,
              outputFormat = compressed.format.name,
            )
          )
        onProgress(index + 1, images.size)
        compressed.copy(historyId = historyId)
      }
    }

  override suspend fun saveToGallery(image: CompressedImage): CompressedImage {
    val savedUri = gallerySaver.save(image)
    if (image.historyId > 0) historyDao.updateSavedPath(image.historyId, savedUri.toString())
    return image.copy(savedUri = savedUri)
  }

  override suspend fun deleteHistory(item: CompressionHistoryEntity) {
    historyDao.delete(item)
  }

  override suspend fun clearHistory() {
    historyDao.deleteAll()
  }
}
