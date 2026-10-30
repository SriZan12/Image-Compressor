package com.example.imagecompressor.data.model

import java.math.BigDecimal

enum class CompressionPreset { SMALLER, BALANCED, BEST_QUALITY, SIZE_LIMIT }
enum class TargetUnit(val label: String, val bytes: Long) { KB("KB", 1_000), MB("MB", 1_000_000) }

fun CompressionPreset.settings(): CompressionSettings = when (this) {
  CompressionPreset.SMALLER -> CompressionSettings(quality = 65, resizeMode = ResizeMode.PERCENTAGE, resizePercent = 75)
  CompressionPreset.BALANCED -> CompressionSettings()
  CompressionPreset.BEST_QUALITY -> CompressionSettings(quality = 92)
  CompressionPreset.SIZE_LIMIT -> CompressionSettings(targetBytes = 1_000_000)
}

fun CompressionSettings.preset(): CompressionPreset? =
  if (targetBytes != null) CompressionPreset.SIZE_LIMIT else CompressionPreset.entries.firstOrNull { it.settings() == this }

/** Upload limits use decimal KB/MB. Never round a limit upwards or accept a fraction of a byte. */
fun parseTargetBytes(text: String, unit: TargetUnit): Long? = try {
  if (text.length > 16) null else BigDecimal(text.trim().replace(',', '.')).multiply(BigDecimal.valueOf(unit.bytes))
    .longValueExact().takeIf { it in 1..100_000_000 }
} catch (_: IllegalArgumentException) { null } catch (_: ArithmeticException) { null }

fun targetText(bytes: Long, unit: TargetUnit): String = BigDecimal.valueOf(bytes)
  .divide(BigDecimal.valueOf(unit.bytes)).stripTrailingZeros().toPlainString()
