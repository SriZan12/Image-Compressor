package com.example.imagecompressor.data.preferences

import android.app.Application
import com.example.imagecompressor.data.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PreferencesTest {
  @Test fun lastSettingsAndThemeSurviveRepositoryRecreation() {
    val context = RuntimeEnvironment.getApplication()
    val settings = CompressionSettings(targetBytes = 500_000, quality = 75, outputFormat = OutputFormat.WEBP,
      resizeMode = ResizeMode.CUSTOM, customWidth = "900", allowTargetResize = false)
    DefaultAppPreferencesRepository(context).apply {
      rememberCompressionSettings(settings)
      setThemePreference(ThemePreference.DARK)
      setDynamicColor(false)
      setOnboardingComplete()
    }
    DefaultAppPreferencesRepository(context).apply {
      assertEquals(settings, lastCompressionSettings)
      assertEquals(ThemePreference.DARK, themePreference.value)
      assertFalse(dynamicColor.value)
      assertTrue(onboardingComplete.value)
    }
  }
}
