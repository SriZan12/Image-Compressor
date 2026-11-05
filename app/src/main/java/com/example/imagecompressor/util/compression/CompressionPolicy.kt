package com.example.imagecompressor.util.compression

import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.ResizeMode
import kotlin.math.min
import kotlin.math.sqrt

internal data class PixelSize(val width: Int, val height: Int) {
  init { require(width > 0 && height > 0) { "Image dimensions must be positive." } }
  val pixels: Long get() = width.toLong() * height
  val longEdge: Int get() = maxOf(width, height)

  fun fitPixels(limit: Long): PixelSize {
    require(limit > 0)
    if (pixels <= limit) return this
    val scale = sqrt(limit.toDouble() / pixels)
    var w = (width * scale).toInt().coerceAtLeast(1)
    var h = (height * scale).toInt().coerceAtLeast(1)
    // Extremely narrow images can round their short edge up to one pixel.
    if (w.toLong() * h > limit) {
      if (w >= h) w = (limit / h).toInt().coerceAtLeast(1)
      else h = (limit / w).toInt().coerceAtLeast(1)
    }
    return PixelSize(w, h)
  }

  fun fitInside(bounds: PixelSize): PixelSize {
    val scale = minOf(1.0, bounds.width.toDouble() / width, bounds.height.toDouble() / height)
    return PixelSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
  }
}

/** EXIF transforms act on pixel edges; using width/height translations preserves pixel centers. */
internal enum class ExifOrientation(val value: Int, val swapsAxes: Boolean = false) {
  NORMAL(1), MIRROR_HORIZONTAL(2), ROTATE_180(3), MIRROR_VERTICAL(4),
  TRANSPOSE(5, true), ROTATE_90(6, true), TRANSVERSE(7, true), ROTATE_270(8, true);

  fun upright(size: PixelSize) = if (swapsAxes) PixelSize(size.height, size.width) else size

  fun matrix(source: PixelSize, output: PixelSize): FloatArray {
    val w = source.width.toFloat()
    val h = source.height.toFloat()
    val values = when (this) {
      NORMAL -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
      MIRROR_HORIZONTAL -> floatArrayOf(-1f, 0f, w, 0f, 1f, 0f)
      ROTATE_180 -> floatArrayOf(-1f, 0f, w, 0f, -1f, h)
      MIRROR_VERTICAL -> floatArrayOf(1f, 0f, 0f, 0f, -1f, h)
      TRANSPOSE -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f)
      ROTATE_90 -> floatArrayOf(0f, -1f, h, 1f, 0f, 0f)
      TRANSVERSE -> floatArrayOf(0f, -1f, h, -1f, 0f, w)
      ROTATE_270 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, w)
    }
    val upright = upright(source)
    val sx = output.width.toFloat() / upright.width
    val sy = output.height.toFloat() / upright.height
    return floatArrayOf(values[0] * sx, values[1] * sx, values[2] * sx,
      values[3] * sy, values[4] * sy, values[5] * sy, 0f, 0f, 1f)
  }

  companion object {
    fun from(value: Int) = entries.firstOrNull { it.value == value } ?: NORMAL
  }
}

internal object CompressionPolicy {
  const val MAX_INPUT_BYTES = 100L * 1024 * 1024
  // EXIF/XMP/profile parsers may copy a whole metadata chunk even when pixels are sampled.
  const val MAX_METADATA_BYTES = 4L * 1024 * 1024
  const val MAX_INPUT_PIXELS = 50_000_000L
  const val MAX_EDGE = 16_384
  const val MAX_WORKING_PIXELS = 16_000_000L

  // Reserve room for decoded + transformed/resized ARGB buffers and codec scratch.
  // This bounds engine allocations, not the entire application's process PSS.
  fun pixelBudget(maxHeapBytes: Long): Long =
    (maxHeapBytes / 2 / 12).coerceIn(1, MAX_WORKING_PIXELS)

  fun validateInput(size: PixelSize) {
    require(size.width <= MAX_EDGE && size.height <= MAX_EDGE && size.pixels <= MAX_INPUT_PIXELS) {
      "This image exceeds the 50 megapixel or 16,384 pixel edge limit."
    }
  }

  fun requestedSize(source: PixelSize, settings: CompressionSettings): PixelSize {
    require(settings.quality in 10..100) { "Quality must be between 10 and 100." }
    require(settings.targetBytes == null || settings.targetBytes > 0) { "Target size must be positive." }
    return when (settings.resizeMode) {
      ResizeMode.ORIGINAL -> source
      ResizeMode.PERCENTAGE -> {
        require(settings.resizePercent in 10..100) { "Resize percentage must be between 10 and 100." }
        PixelSize((source.width.toLong() * settings.resizePercent / 100).toInt().coerceAtLeast(1),
          (source.height.toLong() * settings.resizePercent / 100).toInt().coerceAtLeast(1))
      }
      ResizeMode.CUSTOM -> {
        fun dimension(text: String): Int? {
          if (text.isBlank()) return null
          return text.toIntOrNull()?.takeIf { it in 1..MAX_EDGE }
            ?: throw IllegalArgumentException("Enter a dimension between 1 and 16,384 pixels.")
        }
        val w = dimension(settings.customWidth)
        val h = dimension(settings.customHeight)
        require(w != null || h != null) { "Enter a custom width or height." }
        if (settings.preserveAspectRatio) {
          val scale = min(w?.toDouble()?.div(source.width) ?: Double.POSITIVE_INFINITY,
            h?.toDouble()?.div(source.height) ?: Double.POSITIVE_INFINITY)
          require(scale <= 1) { "Output dimensions cannot enlarge the original image." }
          PixelSize((source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1))
        } else {
          val requested = PixelSize(w ?: source.width, h ?: source.height)
          require(requested.width <= source.width && requested.height <= source.height) {
            "Output dimensions cannot enlarge the original image."
          }
          requested
        }
      }
    }
  }

  fun sampleSize(raw: PixelSize, wanted: PixelSize, orientation: ExifOrientation, pixelBudget: Long): Int {
    val rawWanted = orientation.upright(wanted) // Swapping axes is its own inverse.
    fun sampled(sample: Int) = PixelSize((raw.width + sample - 1) / sample, (raw.height + sample - 1) / sample)
    var sample = 1
    while (sample < raw.longEdge) {
      val next = sampled(sample * 2)
      if (next.width < rawWanted.width || next.height < rawWanted.height) break
      sample *= 2
    }
    while (sampled(sample).pixels > pixelBudget) sample *= 2
    return sample
  }
}
