package com.example.imagecompressor.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CompressionModelsTest {
  @Test
  fun reductionPercent_handlesCommonAndUnknownSizes() {
    assertEquals(75, calculateReductionPercent(originalSizeBytes = 1_000, compressedSizeBytes = 250))
    assertEquals(0, calculateReductionPercent(originalSizeBytes = 0, compressedSizeBytes = 250))
    assertEquals(-50, calculateReductionPercent(originalSizeBytes = 100, compressedSizeBytes = 150))
  }

  @Test
  fun sizeChange_doesNotHideGrowthOrSmallDifferences() {
    assertEquals("50% larger", describeSizeChange(100, 150))
    assertEquals("Less than 1% larger", describeSizeChange(1000, 1001))
    assertEquals("Less than 1% smaller", describeSizeChange(1000, 999))
    assertEquals("Same file size", describeSizeChange(1000, 1000))
    assertEquals("Original size unknown", describeSizeChange(0, 1000))
  }

  @Test
  fun targetStatus_usesExactFinalBytes() {
    assertEquals(TargetSizeStatus.NOT_REQUESTED, targetSizeStatus(999, null))
    assertEquals(TargetSizeStatus.MET, targetSizeStatus(1000, 1000))
    assertEquals(TargetSizeStatus.UNMET, targetSizeStatus(1001, 1000))
  }

  @Test
  fun readableSize_formatsKilobytesAndMegabytes() {
    assertEquals("1 B", 1L.toReadableSize())
    assertEquals("1.0 KB", 1000L.toReadableSize())
    assertEquals("1.00 MB", (1000L * 1000).toReadableSize())
  }
}
