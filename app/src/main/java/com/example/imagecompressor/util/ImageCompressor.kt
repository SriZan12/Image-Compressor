package com.example.imagecompressor.util

import android.content.Context
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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    val source = stageSource(uri)
    try {
      val info = readInfo(source)
      val size = info.orientation.upright(info.size)
      SelectedImage(uri, displayName(uri), source.length(), size.width, size.height, info.container.label)
    } finally {
      source.delete()
    }
  }

  override suspend fun compress(image: SelectedImage, settings: CompressionSettings): CompressedImage {
    // Outside withContext so prompt cancellation on dispatcher return also removes unpublished results.
    var published: File? = null
    try {
      return withContext(Dispatchers.IO) {
        pipelineMutex.withLock {
          val source = stageSource(image.uri)
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
                  encode(bitmap, size, quality, settings.outputFormat)
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
              val directory = File(context.cacheDir, "compressed")
              if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Unable to create the output folder. Check available storage.")
              val output = File.createTempFile("ImageCompressor_", ".${settings.outputFormat.extension}", directory)
              published = output
              Files.move(candidate.value.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
              currentCoroutineContext().ensureActive()
              check(output.length() == candidate.bytes && candidate.bytes > 0)
              CompressedImage(
                original = image.copy(sizeBytes = source.length(), width = uprightSource.width,
                  height = uprightSource.height, format = info.container.label),
                outputUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output),
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

  private suspend fun stageSource(uri: Uri): File {
    val file = File.createTempFile("image-source-", ".tmp", context.cacheDir)
    var complete = false
    try {
      val coroutine = currentCoroutineContext()
      val input = contentResolver.openInputStream(uri) ?: throw IOException("Unable to open the selected image.")
      input.use { source ->
        file.outputStream().use { output ->
          val buffer = ByteArray(65536)
          var total = 0L
          while (true) {
            coroutine.ensureActive()
            val count = source.read(buffer)
            if (count < 0) break
            total += count
            if (total > CompressionPolicy.MAX_INPUT_BYTES) throw IOException("Choose an image no larger than 100 MiB.")
            output.write(buffer, 0, count)
          }
          if (total == 0L) throw IOException("The selected image is empty.")
        }
      }
      complete = true
      return file
    } finally {
      if (!complete) file.delete()
    }
  }

  private data class ImageInfo(val size: PixelSize, val orientation: ExifOrientation,
    val container: ImageContainer, val wideGamut: Boolean)

  private suspend fun readInfo(file: File): ImageInfo {
    val coroutine = currentCoroutineContext()
    val container = ImageContainer.validate(file) { coroutine.ensureActive() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unable to read this image's dimensions.")
    val size = PixelSize(bounds.outWidth, bounds.outHeight)
    CompressionPolicy.validateInput(size)
    val orientation = ExifOrientation.from(ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1))
    return ImageInfo(size, orientation, container, bounds.outColorSpace?.isWideGamut == true)
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

  private suspend fun encode(bitmap: Bitmap, size: PixelSize, quality: Int, format: OutputFormat): EncodedCandidate<File> {
    val file = File.createTempFile("image-candidate-", ".${format.extension}", context.cacheDir)
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
