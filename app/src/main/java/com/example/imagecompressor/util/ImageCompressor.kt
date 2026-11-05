package com.example.imagecompressor.util

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionAdjustment
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.util.compression.CompressionPolicy
import com.example.imagecompressor.util.compression.EncodedCandidate
import com.example.imagecompressor.util.compression.ExifOrientation
import com.example.imagecompressor.util.compression.ImageContainer
import com.example.imagecompressor.util.compression.PixelSize
import com.example.imagecompressor.util.compression.TargetSizeSearch
import java.io.File
import java.io.IOException
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.channels.Channels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** On-device, bounded-memory still-image pipeline. All output metadata is newly generated. */
class ImageCompressor(
  private val context: Context,
  private val workingPixelLimit: Long = CompressionPolicy.pixelBudget(Runtime.getRuntime().maxMemory()),
) : ImageProcessor {
  init { require(workingPixelLimit in 1..CompressionPolicy.MAX_WORKING_PIXELS) }
  private val contentResolver = context.contentResolver

  override suspend fun inspect(uri: Uri): SelectedImage = withContext(Dispatchers.IO) {
    val descriptor = contentResolver.openAssetFileDescriptor(uri, "r")
      ?: throw IOException("Unable to open the selected image.")
    descriptor.use { asset ->
      asset.createInputStream().use { input ->
        val length = seekableWholeFileLength(asset, input)
        if (length != null) {
          if (length == 0L) throw IOException("The selected image is empty.")
          if (length > CompressionPolicy.MAX_INPUT_BYTES) throw IOException("Choose an image no larger than 100 MiB.")
          val info = readInfo(asset, input, length)
          // Providers can replace or truncate files after selection. Compression revalidates its own
          // staged snapshot; inspection also avoids publishing metadata for a changed-length file.
          if (input.channel.size() != length) throw IOException("This image changed while opening. Select it again.")
          selectedImage(uri, info, length)
        } else {
          // Pipes, unknown sizes and slices keep the bounded snapshot path. Reuse this provider
          // stream rather than opening a second cloud/provider request after probing its metadata.
          val source = stageSource(input)
          try { selectedImage(uri, readInfo(source), source.length()) }
          finally { source.delete() }
        }
      }
    }
  }

  private fun selectedImage(uri: Uri, info: ImageInfo, bytes: Long): SelectedImage {
    val size = info.orientation.upright(info.size)
    return SelectedImage(uri, displayName(uri), bytes, size.width, size.height, info.container.label)
  }

  private fun seekableWholeFileLength(asset: AssetFileDescriptor, input: FileInputStream): Long? {
    val length = asset.parcelFileDescriptor.statSize
    if (asset.startOffset != 0L || length < 0L ||
      (asset.declaredLength != AssetFileDescriptor.UNKNOWN_LENGTH && asset.declaredLength != length)) return null
    return try {
      input.channel.position(0)
      length.takeIf { input.channel.size() == it }
    } catch (_: IOException) { null }
  }

  /** Lightweight factory: filesystem work starts only when its first preview is prepared on IO. */
  fun createPreviewProcessor(): TemporaryImageProcessor = SessionTemporaryImageProcessor(context.applicationContext) { image, settings, directory ->
    process(image, settings, previewDirectory = directory)
  }

  override suspend fun compress(image: SelectedImage, settings: CompressionSettings): CompressedImage =
    process(image, settings)

  private suspend fun process(image: SelectedImage, settings: CompressionSettings, previewDirectory: File? = null): CompressedImage {
    // Outside withContext so prompt cancellation on dispatcher return also removes unpublished results.
    var published: File? = null
    try {
      return withContext(Dispatchers.IO) {
        pipelineMutex.withLock {
          val workingDirectory = previewDirectory ?: context.cacheDir
          val source = stageSource(image.uri, workingDirectory)
          var base: Bitmap? = null
          try {
            val info = readInfo(source)
            val uprightSource = info.orientation.upright(info.size)
            val requested = CompressionPolicy.requestedSize(uprightSource, settings)
            val desired = requested.fitPixels(workingPixelLimit)
            val options = BitmapFactory.Options().apply {
              inSampleSize = CompressionPolicy.sampleSize(info.size, desired, info.orientation, workingPixelLimit)
              inPreferredConfig = Bitmap.Config.ARGB_8888
              inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }
            currentCoroutineContext().ensureActive()
            val decoded = BitmapFactory.decodeFile(source.absolutePath, options)
              ?: throw IOException("Unable to decode this image. It may be damaged.")
            val adjustments = mutableSetOf<CompressionAdjustment>()
            try {
              currentCoroutineContext().ensureActive()
              val decodedSize = PixelSize(decoded.width, decoded.height)
              if (decodedSize.pixels > workingPixelLimit) throw IOException("This image exceeds the available memory budget.")
              // Sampling can undershoot the requested size; never enlarge it again.
              val actual = desired.fitInside(info.orientation.upright(decodedSize))
              if (actual != requested) adjustments += CompressionAdjustment.MEMORY_LIMIT
              if (settings.outputFormat == OutputFormat.JPEG && decoded.hasAlpha()) {
                adjustments += CompressionAdjustment.JPEG_BACKGROUND
              }
              if (info.wideGamut || (Build.VERSION.SDK_INT >= 34 && decoded.hasGainmap())) {
                adjustments += CompressionAdjustment.SDR_CONVERSION
              }
              base = render(decoded, actual, info.orientation, settings.outputFormat == OutputFormat.JPEG)
            } finally {
              decoded.recycle()
            }
            val upright = checkNotNull(base)
            val initial = PixelSize(upright.width, upright.height)
            val result = TargetSizeSearch().search(
              initial, settings.quality, settings.targetBytes, settings.outputFormat != OutputFormat.PNG,
              settings.allowTargetResize,
              encode = { size, quality ->
                currentCoroutineContext().ensureActive()
                val bitmap = if (size == initial) upright else
                  upright.scale(size.width, size.height, true)
                try {
                  encode(bitmap, size, quality, settings.outputFormat, workingDirectory)
                } finally {
                  if (bitmap !== upright) bitmap.recycle()
                }
              },
              discard = { it.delete() },
            )
            val candidate = result.candidate
            try {
              currentCoroutineContext().ensureActive()
              if (candidate.size != initial) adjustments += CompressionAdjustment.TARGET_SIZE
              val directory = previewDirectory ?: File(context.cacheDir, "compressed")
              if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Unable to create the output folder. Check available storage.")
              val output = File.createTempFile(if (previewDirectory == null) "ImageCompressor_" else "Preview_",
                ".${settings.outputFormat.extension}", directory)
              published = output
              Files.move(candidate.value.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
              currentCoroutineContext().ensureActive()
              check(output.length() == candidate.bytes && candidate.bytes > 0)
              CompressedImage(
                original = image.copy(sizeBytes = source.length(), width = uprightSource.width,
                  height = uprightSource.height, format = info.container.label),
                outputUri = if (previewDirectory == null)
                  FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output) else Uri.fromFile(output),
                outputFilePath = output.absolutePath,
                compressedSizeBytes = output.length(),
                width = candidate.size.width,
                height = candidate.size.height,
                format = settings.outputFormat,
                targetBytes = settings.targetBytes,
                adjustments = adjustments.toSet(),
                encodingAttempts = result.attempts,
                encodingQuality = candidate.quality.takeIf { settings.outputFormat != OutputFormat.PNG },
              )
            } finally {
              candidate.value.delete()
            }
          } finally {
            base?.recycle()
            source.delete()
          }
        }
      }
    } catch (failure: Throwable) {
      published?.delete()
      if (failure is OutOfMemoryError) throw IOException("Not enough memory to process this image. Try a smaller image or close other apps.", failure)
      throw failure
    }
  }

  private suspend fun stageSource(uri: Uri, directory: File = context.cacheDir): File {
    val input = contentResolver.openInputStream(uri) ?: throw IOException("Unable to open the selected image.")
    return input.use { stageSource(it, directory) }
  }

  /** The caller owns input; both compression and descriptor fallback use the same bounded copy. */
  private suspend fun stageSource(input: InputStream, directory: File = context.cacheDir): File {
    val file = File.createTempFile("image-source-", ".tmp", directory)
    var complete = false
    try {
      val coroutine = currentCoroutineContext()
      file.outputStream().use { output ->
        val buffer = ByteArray(65536)
        var total = 0L
        while (true) {
          coroutine.ensureActive()
          val count = input.read(buffer)
          if (count < 0) break
          total += count
          if (total > CompressionPolicy.MAX_INPUT_BYTES) throw IOException("Choose an image no larger than 100 MiB.")
          output.write(buffer, 0, count)
        }
        if (total == 0L) throw IOException("The selected image is empty.")
      }
      complete = true
      return file
    } finally {
      if (!complete) file.delete()
    }
  }

  private data class ImageInfo(val size: PixelSize, val orientation: ExifOrientation,
    val container: ImageContainer, val wideGamut: Boolean)

  private suspend fun readInfo(asset: AssetFileDescriptor, input: FileInputStream, length: Long): ImageInfo {
    val coroutine = currentCoroutineContext()
    val container = ImageContainer.validate(input, length) { coroutine.ensureActive() }
    coroutine.ensureActive()
    input.channel.position(0)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFileDescriptor(asset.fileDescriptor, null, bounds)
    val size = checkedBounds(bounds)
    coroutine.ensureActive()
    input.channel.position(0)
    // Borrow the rewound channel without descriptor duplication or an AFD slice stream's consumed
    // remaining-byte counter. The outer input/asset scope retains sole ownership of its lifetime.
    val exif = ExifInterface(Channels.newInputStream(input.channel))
    val orientation = ExifOrientation.from(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1))
    coroutine.ensureActive()
    return ImageInfo(size, orientation, container, bounds.outColorSpace?.isWideGamut == true)
  }

  private suspend fun readInfo(file: File): ImageInfo {
    val coroutine = currentCoroutineContext()
    val container = ImageContainer.validate(file) { coroutine.ensureActive() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val size = checkedBounds(bounds)
    val orientation = ExifOrientation.from(ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1))
    return ImageInfo(size, orientation, container, bounds.outColorSpace?.isWideGamut == true)
  }

  private fun checkedBounds(bounds: BitmapFactory.Options): PixelSize {
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unable to read this image's dimensions.")
    return PixelSize(bounds.outWidth, bounds.outHeight).also(CompressionPolicy::validateInput)
  }

  private fun render(source: Bitmap, size: PixelSize, orientation: ExifOrientation, whiteBackground: Boolean): Bitmap {
    // A fresh SDR sRGB buffer combines orientation, resize, color conversion and optional flattening.
    val result = createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888,
      hasAlpha = !whiteBackground, colorSpace = ColorSpace.get(ColorSpace.Named.SRGB))
    try {
      val canvas = Canvas(result)
      if (whiteBackground) canvas.drawColor(Color.WHITE)
      val matrix = Matrix().apply { setValues(orientation.matrix(PixelSize(source.width, source.height), size)) }
      canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
      return result
    } catch (failure: Throwable) {
      result.recycle()
      throw failure
    }
  }

  private suspend fun encode(bitmap: Bitmap, size: PixelSize, quality: Int, format: OutputFormat, directory: File): EncodedCandidate<File> {
    val file = File.createTempFile("image-candidate-", ".${format.extension}", directory)
    var complete = false
    try {
      currentCoroutineContext().ensureActive()
      val codec = when (format) {
        OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
        OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        OutputFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
      }
      file.outputStream().use { output ->
        if (!bitmap.compress(codec, quality, output)) throw IOException("The image encoder could not produce an output.")
      }
      currentCoroutineContext().ensureActive()
      // Include metadata overhead in every measured candidate, not after target selection.
      ExifInterface(file).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        saveAttributes()
      }
      currentCoroutineContext().ensureActive()
      val bytes = file.length()
      if (bytes <= 0) throw IOException("The image encoder produced an empty output.")
      complete = true
      return EncodedCandidate(file, size, quality, bytes)
    } catch (failure: IOException) {
      throw IOException("Unable to write the compressed image. Check available storage.", failure)
    } finally {
      if (!complete) file.delete()
    }
  }

  private fun displayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) return cursor.getString(index) ?: "Selected image"
      }
    }
    return "Selected image"
  }

  private companion object {
    // Batch and overlapping callers share one native bitmap pipeline per process.
    val pipelineMutex = Mutex()
  }
}
