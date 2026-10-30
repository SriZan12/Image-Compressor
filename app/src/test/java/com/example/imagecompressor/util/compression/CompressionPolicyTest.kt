package com.example.imagecompressor.util.compression

import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.ResizeMode
import org.junit.Assert.*
import org.junit.Test

class CompressionPolicyTest {
  @Test fun validatesInputBeforeAllocation() {
    CompressionPolicy.validateInput(PixelSize(8000, 6000))
    assertThrows(IllegalArgumentException::class.java) { CompressionPolicy.validateInput(PixelSize(10000, 6000)) }
    assertThrows(IllegalArgumentException::class.java) { CompressionPolicy.validateInput(PixelSize(20000, 1)) }
    assertTrue(CompressionPolicy.pixelBudget(128L * 1024 * 1024) < 6_000_000)
    assertEquals(16_000_000L, CompressionPolicy.pixelBudget(1024L * 1024 * 1024))
  }

  @Test fun dimensionsRespectAspectRatioWithoutEnlargement() {
    val source = PixelSize(4000, 3000)
    val custom = CompressionSettings(resizeMode = ResizeMode.CUSTOM, customWidth = "800")
    assertEquals(PixelSize(800, 600), CompressionPolicy.requestedSize(source, custom))
    assertEquals(PixelSize(400, 300), CompressionPolicy.requestedSize(source, custom.copy(customHeight = "300")))
    assertEquals(PixelSize(800, 3000), CompressionPolicy.requestedSize(source, custom.copy(preserveAspectRatio = false)))
    assertEquals(PixelSize(1000, 750), CompressionPolicy.requestedSize(source,
      CompressionSettings(resizeMode = ResizeMode.PERCENTAGE, resizePercent = 25)))
    listOf(custom.copy(customWidth = "0"), custom.copy(customWidth = "-1"),
      custom.copy(customWidth = ""), custom.copy(customWidth = "9000"),
      custom.copy(customWidth = "999999999999"), custom.copy(customWidth = "abc"),
      custom.copy(targetBytes = 0), custom.copy(quality = 9)).forEach { invalid ->
      assertThrows(IllegalArgumentException::class.java) { CompressionPolicy.requestedSize(source, invalid) }
    }
  }

  @Test fun skinnyImagesAndLargeSourcesStayWithinPixelBudget() {
    for (size in listOf(PixelSize(8000, 6000), PixelSize(16384, 1), PixelSize(1, 16384))) {
      for (budget in listOf(1L, 10L, 1024L, 1_000_000L)) {
        assertTrue(size.fitPixels(budget).pixels <= budget)
        for (orientation in ExifOrientation.entries) {
          val sample = CompressionPolicy.sampleSize(size, orientation.upright(size).fitPixels(budget), orientation, budget)
          assertEquals(0, sample and (sample - 1))
          val decoded = PixelSize((size.width + sample - 1) / sample, (size.height + sample - 1) / sample)
          assertTrue(decoded.pixels <= budget)
        }
      }
    }
  }

  @Test fun samplingAccountsForOrientationAndNeverUpscales() {
    val raw = PixelSize(4000, 3000)
    assertEquals(4, CompressionPolicy.sampleSize(raw, PixelSize(750, 1000), ExifOrientation.ROTATE_90, 16_000_000))
    assertEquals(PixelSize(1000, 750), PixelSize(2000, 1500).fitInside(PixelSize(1000, 750)))
    assertEquals(PixelSize(1000, 750), PixelSize(1000, 750).fitInside(raw))
  }

  @Test fun allExifMatricesMapPixelCentersIncludingMirrors() {
    // Independently enumerated transformed coordinates for source pixel (0,0) of a 3x2 image.
    val expected = listOf(0 to 0, 2 to 0, 2 to 1, 0 to 1, 0 to 0, 1 to 0, 1 to 2, 0 to 2)
    ExifOrientation.entries.forEachIndexed { index, orientation ->
      val source = PixelSize(3, 2)
      val m = orientation.matrix(source, orientation.upright(source))
      assertEquals(expected[index].first + 0.5f, m[0] * 0.5f + m[1] * 0.5f + m[2], 0.0001f)
      assertEquals(expected[index].second + 0.5f, m[3] * 0.5f + m[4] * 0.5f + m[5], 0.0001f)
    }
  }
}
