package com.example.imagecompressor.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.imagecompressor.data.model.CompressedImage
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

interface GalleryExporter {
  fun isReadable(uri: Uri): Boolean
  /** Keep a published export; remove an owned incomplete export. Never deletes published photos. */
  fun recover(uri: Uri): Uri?
  suspend fun save(image: CompressedImage, pendingUri: Uri?,
    onReserved: suspend (Uri?) -> Unit, onPublished: suspend (Uri) -> Unit): Uri
}

class GallerySaver(private val context: Context) : GalleryExporter {
  private val resolver = context.contentResolver
  private val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
  private val relativePath = "${Environment.DIRECTORY_PICTURES}/Image Compressor/"

  override fun isReadable(uri: Uri): Boolean = try {
    resolver.openAssetFileDescriptor(uri, "r")?.use { it.length != 0L } ?: false
  } catch (_: Exception) { false }

  private fun pending(uri: Uri): Boolean? = resolver.query(uri,
    arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)?.use {
    if (it.moveToFirst()) it.getInt(0) != 0 else null
  }

  override fun recover(uri: Uri): Uri? = when (pending(uri)) {
    false -> uri.takeIf(::isReadable)
    true -> { resolver.delete(uri, null, null); null }
    null -> null
  }

  override suspend fun save(image: CompressedImage, pendingUri: Uri?,
    onReserved: suspend (Uri?) -> Unit, onPublished: suspend (Uri) -> Unit): Uri = withContext(Dispatchers.IO) {
    val source = File(image.outputFilePath)
    if (!source.isFile || source.length() == 0L) throw IOException("This result is unavailable. Compress the original again.")
    val name = "ImageCompressor_${image.historyId}_${source.name}"
    // Stable, app-owned name closes the insert/journal process-death window without creating duplicates.
    val existing = pendingUri ?: resolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
      "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ?",
      arrayOf(name, relativePath, context.packageName), null)?.use {
      if (it.moveToFirst()) Uri.withAppendedPath(collection, it.getLong(0).toString()) else null
    }
    if (existing != null && pending(existing) == false && isReadable(existing)) {
      withContext(NonCancellable) { onPublished(existing) }
      return@withContext existing
    }
    var published = false
    val uri = withContext(NonCancellable) {
      val reserved = existing?.takeIf { pending(it) == true } ?: resolver.insert(collection, ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, image.format.mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
        put(MediaStore.Images.Media.IS_PENDING, 1)
      }) ?: throw IOException("Unable to create a gallery entry. Check available storage.")
      try { onReserved(reserved) } catch (failure: Throwable) {
        try { resolver.delete(reserved, null, null) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        throw failure
      }
      reserved
    }
    try {
      val coroutine = currentCoroutineContext()
      resolver.openOutputStream(uri, "w")?.use { output ->
        source.inputStream().use { input ->
          val buffer = ByteArray(65536)
          while (true) {
            coroutine.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
          }
          output.flush()
        }
      } ?: throw IOException("Unable to open the gallery destination.")
      coroutine.ensureActive()
      // Publication and its journal update must finish together even if the caller is cancelled.
      withContext(NonCancellable) {
        if (resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) != 1)
          throw IOException("The gallery could not finish saving this image.")
        published = true
        onPublished(uri)
      }
      uri
    } catch (failure: Throwable) {
      if (!published) withContext(NonCancellable) {
        try { resolver.delete(uri, null, null); onReserved(null) }
        catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
      }
      throw failure
    }
  }
}
