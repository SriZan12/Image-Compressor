package com.example.imagecompressor.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CompressionModelsTest {
  @Test
  fun reductionPercent_handlesCommonAndUnknownSizes() {
    assertEquals(75, calculateReductionPercent(originalSizeBytes = 1_000, compressedSizeBytes = 250))
    assertEquals(0, calculateReductionPercent(originalSizeBytes = 0, compressedSizeBytes = 250))
    assertEquals(0, calculateReductionPercent(originalSizeBytes = 100, compressedSizeBytes = 150))
  }

  @Test
  fun readableSize_formatsKilobytesAndMegabytes() {
    assertEquals("1.0 KB", 1024L.toReadableSize())
    assertEquals("1.00 MB", (1024L * 1024).toReadableSize())
  }
}
