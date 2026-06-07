package com.example.imagecompressor.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.imagecompressor.data.model.CompressedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class GallerySaver(private val context: Context) {
  suspend fun save(image: CompressedImage): Uri =
    withContext(Dispatchers.IO) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveWithMediaStore(image)
      } else {
        saveLegacy(image)
      }
    }

  private fun saveWithMediaStore(image: CompressedImage): Uri {
    val resolver = context.contentResolver
    val values =
      ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, File(image.outputFilePath).name)
        put(MediaStore.Images.Media.MIME_TYPE, image.format.mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Image Compressor")
        put(MediaStore.Images.Media.IS_PENDING, 1)
      }
    val uri =
      resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Unable to create a gallery entry.")
    try {
      resolver.openOutputStream(uri)?.use { output ->
        FileInputStream(image.outputFilePath).use { input -> input.copyTo(output) }
      } ?: error("Unable to open the gallery destination.")
      values.clear()
      values.put(MediaStore.Images.Media.IS_PENDING, 0)
      resolver.update(uri, values, null, null)
      return uri
    } catch (throwable: Throwable) {
      resolver.delete(uri, null, null)
      throw throwable
    }
  }

  private suspend fun saveLegacy(image: CompressedImage): Uri {
    if (
      ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      error("Storage permission is needed to save images on Android 8 and 9.")
    }
    val picturesDirectory =
      File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
          "Image Compressor",
        )
        .apply { mkdirs() }
    val outputFile = File(picturesDirectory, File(image.outputFilePath).name)
    withContext(Dispatchers.IO) {
      FileInputStream(image.outputFilePath).use { input ->
        FileOutputStream(outputFile).use { output -> input.copyTo(output) }
      }
    }
    return suspendCancellableCoroutine { continuation ->
      MediaScannerConnection.scanFile(
        context,
        arrayOf(outputFile.absolutePath),
        arrayOf(image.format.mimeType),
      ) { _, uri -> continuation.resume(uri ?: Uri.fromFile(outputFile)) }
    }
  }
}
