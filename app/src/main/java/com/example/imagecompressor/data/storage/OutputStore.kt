package com.example.imagecompressor.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.imagecompressor.data.model.CompressedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Called under the repository's storage mutex. Never owns gallery or source files. */
class OutputStore(private val context: Context, val limitBytes: Long = 512L * 1024 * 1024) {
  val directory = File(context.filesDir, "compressed")
  private val temporaryDirectory = File(context.cacheDir, "compressed")

  fun ownedFile(path: String?): File? = path?.let { File(it) }?.takeIf {
    it.canonicalFile.parentFile == directory.canonicalFile && it.name.startsWith("result_")
  }
  fun readableFile(path: String?): File? = ownedFile(path)?.takeIf { it.isFile && it.length() > 0 }
  fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  fun bytesUsed(): Long = directory.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
  fun checkCapacity(additionalBytes: Long = 1) {
    if (additionalBytes > limitBytes - bytesUsed()) throw IOException(
      "Retained image storage is full. Save images to your gallery, then remove old results from History.")
  }

  fun retain(image: CompressedImage): CompressedImage {
    checkCapacity(image.compressedSizeBytes)
    val file = adoptTemporary(File(image.outputFilePath), image.format.extension)
    try { return image.copy(outputFilePath = file.absolutePath, outputUri = uri(file)) }
    catch (failure: Exception) { file.delete(); throw failure }
  }

  private fun adoptTemporary(source: File, extension: String): File {
    require(source.canonicalFile.parentFile == temporaryDirectory.canonicalFile && source.name.startsWith("ImageCompressor_"))
    if (!source.isFile || source.length() == 0L) throw IOException("This result is no longer available. Compress the original again.")
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Unable to create retained image storage.")
    val output = File(directory, "result_${UUID.randomUUID()}.$extension")
    Files.move(source.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
    return output
  }

  fun migrateLegacy(uriText: String): File? {
    val sourceUri = uriText.toUri()
    if (sourceUri.authority != "${context.packageName}.fileprovider" || sourceUri.pathSegments.size != 2 ||
      sourceUri.pathSegments.first() != "compressed_images") return null
    val file = File(temporaryDirectory, sourceUri.pathSegments.last())
    if (file.canonicalFile.parentFile != temporaryDirectory.canonicalFile || !file.name.startsWith("ImageCompressor_") || !file.isFile) return null
    // Existing user results survive migration even if they already exceed the new quota.
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Unable to create retained image storage.")
    val extension = file.extension.takeIf { it in setOf("jpg", "png", "webp") } ?: "jpg"
    val output = File(directory, "result_${UUID.randomUUID()}.$extension")
    // Copy before recording the new path; a process death must leave the old result recoverable.
    try { Files.copy(file.toPath(), output.toPath()) }
    catch (failure: Exception) { output.delete(); throw failure }
    return output
  }

  fun delete(path: String?) { ownedFile(path)?.let { if (it.exists() && !it.delete()) throw IOException("Unable to remove this retained result.") } }
  fun discardTemporary(path: String) {
    val file = File(path)
    if (file.canonicalFile.parentFile == temporaryDirectory.canonicalFile && file.name.startsWith("ImageCompressor_")) file.delete()
  }
  fun cleanOrphans(referenced: Set<String>, cleanTransientOutputs: Boolean = true) {
    directory.listFiles().orEmpty().filter { it.name.startsWith("result_") && it.absolutePath !in referenced }.forEach { delete(it.absolutePath) }
    context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("image-source-") || it.name.startsWith("image-candidate-") }.forEach { it.delete() }
    if (cleanTransientOutputs) temporaryDirectory.listFiles().orEmpty()
      .filter { it.name.startsWith("ImageCompressor_") && it.isFile }.forEach { it.delete() }
  }
}
