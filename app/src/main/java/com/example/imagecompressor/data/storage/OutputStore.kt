package com.example.imagecompressor.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.util.compression.ImageContainer
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
      "There isn’t enough room for another copy in the app. Save older copies to your gallery, then remove them from Recent photos.")
  }

  fun retain(image: CompressedImage): CompressedImage {
    checkCapacity(image.compressedSizeBytes)
    val file = adoptTemporary(File(image.outputFilePath), image.format.extension)
    try { return image.copy(outputFilePath = file.absolutePath, outputUri = uri(file)) }
    catch (failure: Exception) { file.delete(); throw failure }
  }

  /** Stable identity survives a cancelled return and does not depend on the preview still existing. */
  fun previewDestination(image: CompressedImage): File {
    val source = File(image.outputFilePath)
    val canonical = source.canonicalFile
    val work = canonical.parentFile
    val session = work?.parentFile
    val root = File(context.cacheDir, "editor-previews").canonicalFile
    if (!source.isAbsolute || source.absolutePath != canonical.path ||
      session == null || session.parentFile != root || !Regex("session_[0-9a-fA-F-]{36}").matches(session.name) ||
      !Regex("work_[A-Za-z0-9_-]+").matches(work.name) ||
      !Regex("Preview_[A-Za-z0-9_-]+\\.${image.format.extension}").matches(canonical.name) ||
      image.outputUri != Uri.fromFile(canonical) || image.compressedSizeBytes <= 0 ||
      image.width <= 0 || image.height <= 0 || image.verifiedSha256?.matches(Regex("[0-9a-f]{64}")) != true) {
      throw IOException("This preview is no longer valid. Adjust the photo and try again.")
    }
    val identity = MessageDigest.getInstance("SHA-256").digest(canonical.path.toByteArray(Charsets.UTF_8)).hex()
    return File(directory, "result_preview_$identity.${image.format.extension}")
  }

  /** Copy the measured candidate, never encode again or move the editor's displayed file. */
  suspend fun retainPreview(image: CompressedImage): CompressedImage {
    val destination = previewDestination(image)
    if (destination.exists()) {
      verifyPreviewCopy(destination, image)
      return image.copy(outputFilePath = destination.absolutePath, outputUri = uri(destination))
    }
    val source = File(image.outputFilePath)
    if (!source.isFile || source.length() != image.compressedSizeBytes)
      throw IOException("The preview changed or was removed. Adjust the photo and try again.")
    checkCapacity(image.compressedSizeBytes)
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Unable to create retained image storage.")
    val temporary = File.createTempFile("preview-copy_", ".part", directory)
    try {
      val coroutine = currentCoroutineContext()
      val digest = MessageDigest.getInstance("SHA-256")
      var bytes = 0L
      source.inputStream().use { input ->
        temporary.outputStream().use { output ->
          val buffer = ByteArray(65_536)
          while (true) {
            coroutine.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            bytes += count
            if (bytes > image.compressedSizeBytes) throw changedPreview()
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
          }
          output.fd.sync()
        }
      }
      if (bytes != image.compressedSizeBytes || digest.digest().hex() != image.verifiedSha256) throw changedPreview()
      if (ImageContainer.validate(temporary) { coroutine.ensureActive() }.mimeType != image.format.mimeType)
        throw changedPreview()
      coroutine.ensureActive()
      Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
      return image.copy(outputFilePath = destination.absolutePath, outputUri = uri(destination))
    } finally { temporary.delete() }
  }

  /** A stable path is not enough: retries must still point at the very same measured bytes. */
  suspend fun verifyPreviewCopy(file: File, image: CompressedImage) {
    if (!file.isFile || file.length() != image.compressedSizeBytes) throw changedPreview()
    val coroutine = currentCoroutineContext()
    val digest = MessageDigest.getInstance("SHA-256")
    var bytes = 0L
    file.inputStream().use { input ->
      val buffer = ByteArray(65_536)
      while (true) {
        coroutine.ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        bytes += count
        if (bytes > image.compressedSizeBytes) throw changedPreview()
        digest.update(buffer, 0, count)
      }
    }
    coroutine.ensureActive()
    if (bytes != image.compressedSizeBytes || digest.digest().hex() != image.verifiedSha256) throw changedPreview()
  }

  private fun changedPreview() = IOException("The preview changed. Adjust the photo and try again before saving.")
  private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

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
    directory.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("preview-copy_") && it.extension == "part" }.forEach { it.delete() }
    context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("image-source-") || it.name.startsWith("image-candidate-") }.forEach { it.delete() }
    if (cleanTransientOutputs) temporaryDirectory.listFiles().orEmpty()
      .filter { it.name.startsWith("ImageCompressor_") && it.isFile }.forEach { it.delete() }
  }
}
