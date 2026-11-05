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

enum class TargetSizeStatus { NOT_REQUESTED, MET, UNMET }
enum class CompressionAdjustment { MEMORY_LIMIT, TARGET_SIZE, JPEG_BACKGROUND, SDR_CONVERSION }

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
  val allowTargetResize: Boolean = true,
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
  val targetBytes: Long? = null,
  val adjustments: Set<CompressionAdjustment> = emptySet(),
  val encodingAttempts: Int = 1,
  val encodingQuality: Int? = null,
  /** Digest of the immutable temporary candidate, used only when explicitly retaining a preview. */
  val verifiedSha256: String? = null,
) {
  val reductionPercent: Int
    get() = calculateReductionPercent(original.sizeBytes, compressedSizeBytes)

  val targetSizeStatus: TargetSizeStatus
    get() = targetSizeStatus(compressedSizeBytes, targetBytes)
}

fun targetSizeStatus(actualBytes: Long, targetBytes: Long?): TargetSizeStatus = when {
  targetBytes == null -> TargetSizeStatus.NOT_REQUESTED
  actualBytes <= targetBytes -> TargetSizeStatus.MET
  else -> TargetSizeStatus.UNMET
}

fun calculateReductionPercent(originalSizeBytes: Long, compressedSizeBytes: Long): Int {
  if (originalSizeBytes <= 0) return 0
  return ((1.0 - compressedSizeBytes.toDouble() / originalSizeBytes) * 100)
    .roundToInt()
    .coerceAtMost(100)
}

fun describeSizeChange(originalBytes: Long, outputBytes: Long): String {
  if (originalBytes <= 0) return "Original size unknown"
  if (originalBytes == outputBytes) return "Same file size"
  val percent = kotlin.math.abs(calculateReductionPercent(originalBytes, outputBytes).toLong())
  val amount = if (percent == 0L) "Less than 1%" else "$percent%"
  return "$amount ${if (outputBytes < originalBytes) "smaller" else "larger"}"
}

fun Long.toReadableSize(): String {
  if (this <= 0L) return "Unknown size"
  if (this < 1000L) return "$this B"
  val kb = this / 1000.0
  if (kb < 1000) return "${"%.1f".format(kb)} KB"
  return "${"%.2f".format(kb / 1000.0)} MB"
}
