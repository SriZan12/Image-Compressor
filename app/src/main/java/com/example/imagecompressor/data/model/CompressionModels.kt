package com.example.imagecompressor.data.model

import android.net.Uri
import kotlin.math.roundToInt

enum class OutputFormat(val label: String, val extension: String, val mimeType: String) {
  JPEG("JPEG", "jpg", "image/jpeg"),
  PNG("PNG", "png", "image/png"),
  WEBP("WEBP", "webp", "image/webp"),
}

enum class ResizeMode(val label: String) {
  ORIGINAL("Keep original"),
  PERCENTAGE("By percentage"),
  CUSTOM("Custom size"),
}

enum class ThemePreference(val label: String) {
  SYSTEM("Use device setting"),
  LIGHT("Light"),
  DARK("Dark"),
}

data class CompressionSettings(
  val quality: Int = 80,
  val targetBytes: Long? = null,
  val resizeMode: ResizeMode = ResizeMode.ORIGINAL,
  val resizePercent: Int = 100,
  val customWidth: String = "",
  val customHeight: String = "",
  val preserveAspectRatio: Boolean = true,
  val outputFormat: OutputFormat = OutputFormat.JPEG,
)

data class SelectedImage(
  val uri: Uri,
  val displayName: String,
  val sizeBytes: Long,
  val width: Int,
  val height: Int,
  val format: String,
) {
  val id: String = uri.toString()
}

data class CompressedImage(
  val original: SelectedImage,
  val outputUri: Uri,
  val outputFilePath: String,
  val compressedSizeBytes: Long,
  val width: Int,
  val height: Int,
  val format: OutputFormat,
  val historyId: Long = 0,
  val savedUri: Uri? = null,
) {
  val reductionPercent: Int
    get() = calculateReductionPercent(original.sizeBytes, compressedSizeBytes)
}

fun calculateReductionPercent(originalSizeBytes: Long, compressedSizeBytes: Long): Int {
  if (originalSizeBytes <= 0) return 0
  return ((1.0 - compressedSizeBytes.toDouble() / originalSizeBytes) * 100)
    .roundToInt()
    .coerceIn(0, 100)
}

fun Long.toReadableSize(): String {
  if (this <= 0L) return "Unknown size"
  val kb = this / 1024.0
  if (kb < 1024) return "${"%.1f".format(kb)} KB"
  return "${"%.2f".format(kb / 1024.0)} MB"
}
