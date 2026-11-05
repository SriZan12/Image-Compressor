package com.example.imagecompressor.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
  @Test fun fallbackThemesAndSuccessTextMeetNormalTextContrast() {
    listOf(ShrinkLightColors to LightStatusColors, ShrinkDarkColors to DarkStatusColors).forEach { (c, s) ->
      listOf(c.primary to c.onPrimary, c.primaryContainer to c.onPrimaryContainer,
        c.background to c.onBackground, c.surface to c.onSurface, c.surfaceContainerLow to c.onSurfaceVariant,
        c.surfaceContainer to c.onSurfaceVariant, c.errorContainer to c.onErrorContainer,
        c.background to c.error, c.background to s.success, s.successContainer to s.onSuccessContainer).forEach { (a, b) ->
        assertTrue("$a and $b must have at least 4.5:1 contrast", contrast(a, b) >= 4.5f)
      }
    }
  }
  private fun contrast(a: Color, b: Color): Float = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
}
