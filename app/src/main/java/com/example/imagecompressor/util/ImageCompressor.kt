package com.example.imagecompressor.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.ResizeMode
import com.example.imagecompressor.data.model.SelectedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageCompressor(private val context: Context) {
  private val contentResolver = context.contentResolver

  suspend fun inspect(uri: Uri): SelectedImage =
    withContext(Dispatchers.IO) {
      val (displayName, reportedSize) = queryNameAndSize(uri)
      val bounds = decodeBounds(uri)
      val rotation = readRotationDegrees(uri)
      val (width, height) =
        if (rotation == 90 || rotation == 270) bounds.second to bounds.first else bounds

      SelectedImage(
        uri = uri,
        displayName = displayName,
        sizeBytes = reportedSize.takeIf { it > 0 } ?: queryAssetLength(uri),
        width = width,
        height = height,
        format = contentResolver.getType(uri)?.substringAfter('/')?.uppercase() ?: "IMAGE",
      )
    }

  suspend fun compress(
    image: SelectedImage,
    settings: CompressionSettings,
  ): CompressedImage =
    withContext(Dispatchers.IO) {
      val requestedDimensions = calculateOutputDimensions(image, settings)
      var bitmap = decodeSampledBitmap(image.uri, requestedWidth = requestedDimensions.first, requestedHeight = requestedDimensions.second)
      bitmap = scaleBitmap(bitmap, width = requestedDimensions.first, height = requestedDimensions.second)
      bitmap = flattenTransparencyForJpeg(bitmap, settings.outputFormat)

      var quality = settings.quality.coerceIn(MIN_QUALITY, MAX_QUALITY)
      var bytes = compressToBytes(bitmap, settings.outputFormat, quality)
      val targetBytes = settings.targetBytes
      var attempts = 0

      // Quality controls lossy formats. If that is not enough, or PNG is selected,
      // reduce dimensions gradually so target-size compression still converges.
      while (targetBytes != null && bytes.size > targetBytes && attempts < MAX_TARGET_ATTEMPTS) {
        if (settings.outputFormat != OutputFormat.PNG && quality > MIN_QUALITY) {
          quality = max(MIN_QUALITY, quality - QUALITY_STEP)
        } else {
          val nextWidth = (bitmap.width * TARGET_SCALE_STEP).roundToInt().coerceAtLeast(1)
          val nextHeight = (bitmap.height * TARGET_SCALE_STEP).roundToInt().coerceAtLeast(1)
          if (nextWidth == bitmap.width && nextHeight == bitmap.height) break
          bitmap = scaleBitmap(bitmap, nextWidth, nextHeight)
        }
        bytes = compressToBytes(bitmap, settings.outputFormat, quality)
        attempts++
      }

      val outputDirectory = File(context.cacheDir, "compressed").apply { mkdirs() }
      val filename =
        "ImageCompressor_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}" +
          ".${settings.outputFormat.extension}"
      val outputFile = File(outputDirectory, filename)
      FileOutputStream(outputFile).use { it.write(bytes) }

      // Pixels are rotated during decode, so exported images should be treated as upright.
      runCatching {
        ExifInterface(outputFile.absolutePath).apply {
          setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
          saveAttributes()
        }
      }

      val outputWidth = bitmap.width
      val outputHeight = bitmap.height
      bitmap.recycle()

      CompressedImage(
        original = image,
        outputUri =
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile),
        outputFilePath = outputFile.absolutePath,
        compressedSizeBytes = outputFile.length(),
        width = outputWidth,
        height = outputHeight,
        format = settings.outputFormat,
      )
    }

  private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name =
          if (nameIndex >= 0) cursor.getString(nameIndex) ?: "Selected image"
          else "Selected image"
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
        return name to size
      }
    }
    return "Selected image" to 0L
  }

  private fun queryAssetLength(uri: Uri): Long =
    runCatching { contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L }
      .getOrDefault(0L)

  private fun decodeBounds(uri: Uri): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val stream = contentResolver.openInputStream(uri) ?: error("Unable to open the selected image.")
    // Bounds-only decoding intentionally returns null; the populated options carry the dimensions.
    stream.use { BitmapFactory.decodeStream(it, null, options) }
    if (options.outWidth <= 0 || options.outHeight <= 0) {
      error("This image format could not be decoded.")
    }
    return options.outWidth to options.outHeight
  }

  private fun decodeSampledBitmap(uri: Uri, requestedWidth: Int, requestedHeight: Int): Bitmap {
    val (sourceWidth, sourceHeight) = decodeBounds(uri)
    var sampleSize = 1
    while (
      sourceWidth / (sampleSize * 2) >= requestedWidth &&
        sourceHeight / (sampleSize * 2) >= requestedHeight
    ) {
      sampleSize *= 2
    }
    while (sourceWidth.toLong() * sourceHeight / (sampleSize.toLong() * sampleSize) > MAX_BITMAP_PIXELS) {
      sampleSize *= 2
    }

    val options =
      BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    val decoded =
      contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Unable to decode the selected image.")
    return rotateBitmap(decoded, readRotationDegrees(uri))
  }

  private fun readRotationDegrees(uri: Uri): Int =
    runCatching {
        contentResolver.openInputStream(uri)?.use { stream ->
          when (
            ExifInterface(stream).getAttributeInt(
              ExifInterface.TAG_ORIENTATION,
              ExifInterface.ORIENTATION_NORMAL,
            )
          ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
          }
        } ?: 0
      }
      .getOrDefault(0)

  private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val rotated =
      Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
  }

  private fun calculateOutputDimensions(
    image: SelectedImage,
    settings: CompressionSettings,
  ): Pair<Int, Int> {
    val sourceWidth = image.width.coerceAtLeast(1)
    val sourceHeight = image.height.coerceAtLeast(1)
    val dimensions =
      when (settings.resizeMode) {
        ResizeMode.ORIGINAL -> sourceWidth to sourceHeight
        ResizeMode.PERCENTAGE -> {
          val scale = settings.resizePercent.coerceIn(10, 100) / 100.0
          (sourceWidth * scale).roundToInt().coerceAtLeast(1) to
            (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        }
        ResizeMode.CUSTOM -> {
          val requestedWidth = settings.customWidth.toIntOrNull()?.coerceAtLeast(1) ?: sourceWidth
          val requestedHeight = settings.customHeight.toIntOrNull()?.coerceAtLeast(1) ?: sourceHeight
          if (settings.preserveAspectRatio) {
            val scale = min(requestedWidth.toDouble() / sourceWidth, requestedHeight.toDouble() / sourceHeight)
            (sourceWidth * scale).roundToInt().coerceAtLeast(1) to
              (sourceHeight * scale).roundToInt().coerceAtLeast(1)
          } else {
            requestedWidth to requestedHeight
          }
        }
      }
    return constrainToPixelBudget(dimensions.first, dimensions.second)
  }

  private fun constrainToPixelBudget(width: Int, height: Int): Pair<Int, Int> {
    val pixels = width.toLong() * height
    if (pixels <= MAX_BITMAP_PIXELS) return width to height
    val scale = sqrt(MAX_BITMAP_PIXELS.toDouble() / pixels)
    return (width * scale).roundToInt().coerceAtLeast(1) to
      (height * scale).roundToInt().coerceAtLeast(1)
  }

  private fun scaleBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
    if (bitmap.width == width && bitmap.height == height) return bitmap
    val scaled = bitmap.scale(width, height)
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
  }

  private fun flattenTransparencyForJpeg(bitmap: Bitmap, outputFormat: OutputFormat): Bitmap {
    if (outputFormat != OutputFormat.JPEG || !bitmap.hasAlpha()) return bitmap
    val flattened = createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
      drawColor(Color.WHITE)
      drawBitmap(bitmap, 0f, 0f, null)
    }
    bitmap.recycle()
    return flattened
  }

  private fun compressToBytes(bitmap: Bitmap, outputFormat: OutputFormat, quality: Int): ByteArray {
    val format =
      when (outputFormat) {
        OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
        OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        OutputFormat.WEBP ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
          else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
      }
    return ByteArrayOutputStream().use { output ->
      check(bitmap.compress(format, quality, output)) { "Unable to encode the compressed image." }
      output.toByteArray()
    }
  }

  private companion object {
    const val MIN_QUALITY = 10
    const val MAX_QUALITY = 100
    const val QUALITY_STEP = 5
    const val TARGET_SCALE_STEP = 0.9
    const val MAX_TARGET_ATTEMPTS = 40
    const val MAX_BITMAP_PIXELS = 16_000_000L
  }
}
