package com.example.imagecompressor.data.model

import org.junit.Assert.*
import org.junit.Test

class CompressionPresetsTest {
  @Test fun uploadLimitsAreExactDecimalBytesAndUnitSwitchesPreserveThem() {
    assertEquals(500_000L, parseTargetBytes("500", TargetUnit.KB))
    assertEquals(1_500_000L, parseTargetBytes("1,5", TargetUnit.MB))
    assertEquals(1L, parseTargetBytes("0.000001", TargetUnit.MB))
    assertEquals(100_000_000L, parseTargetBytes("100", TargetUnit.MB))
    val bytes = 123_456L
    TargetUnit.entries.forEach { assertEquals(bytes, parseTargetBytes(targetText(bytes, it), it)) }
  }
  @Test fun invalidLimitsCannotBecomeAnUnlimitedOrRoundedUpRequest() {
    listOf("", "0", "-1", "NaN", "Infinity", "100.1", "0.0000009", "999999999999999999999").forEach {
      assertNull(it, parseTargetBytes(it, TargetUnit.MB))
    }
  }
  @Test fun manualChangesAreNotMislabeledAsAnUnchangedPreset() {
    assertEquals(CompressionPreset.BALANCED, CompressionSettings().preset())
    assertNull(CompressionSettings(outputFormat = OutputFormat.PNG).preset())
    assertNull(CompressionPreset.SMALLER.settings().copy(quality = 75).preset())
    assertEquals(CompressionPreset.SIZE_LIMIT, CompressionSettings(targetBytes = 300_000).preset())
    assertTrue(CompressionPreset.SMALLER.settings().quality < CompressionPreset.BALANCED.settings().quality)
    assertTrue(CompressionPreset.BEST_QUALITY.settings().quality > CompressionPreset.BALANCED.settings().quality)
  }
}
