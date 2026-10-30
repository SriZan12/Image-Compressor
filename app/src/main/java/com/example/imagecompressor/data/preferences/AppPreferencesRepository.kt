package com.example.imagecompressor.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SettingsCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppPreferencesRepository {
  val onboardingComplete: StateFlow<Boolean>
  val themePreference: StateFlow<ThemePreference>
  val dynamicColor: StateFlow<Boolean>
  val lastCompressionSettings: CompressionSettings

  fun setOnboardingComplete()

  fun setThemePreference(preference: ThemePreference)
  fun setDynamicColor(enabled: Boolean)
  fun rememberCompressionSettings(settings: CompressionSettings)
}

class DefaultAppPreferencesRepository(context: Context) : AppPreferencesRepository {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  private val _onboardingComplete =
    MutableStateFlow(preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false))
  override val onboardingComplete = _onboardingComplete.asStateFlow()

  private val _themePreference =
    MutableStateFlow(
      runCatching {
          ThemePreference.valueOf(
            preferences.getString(KEY_THEME_PREFERENCE, ThemePreference.SYSTEM.name).orEmpty()
          )
        }
        .getOrDefault(ThemePreference.SYSTEM)
    )
  override val themePreference = _themePreference.asStateFlow()

  private val _dynamicColor = MutableStateFlow(preferences.getBoolean("dynamic_color", true))
  override val dynamicColor = _dynamicColor.asStateFlow()
  override val lastCompressionSettings: CompressionSettings
    get() = SettingsCodec.decode(preferences.getString("compression_settings", null))

  override fun setDynamicColor(enabled: Boolean) {
    preferences.edit { putBoolean("dynamic_color", enabled) }
    _dynamicColor.value = enabled
  }
  override fun rememberCompressionSettings(settings: CompressionSettings) {
    preferences.edit { putString("compression_settings", SettingsCodec.encode(settings)) }
  }

  override fun setOnboardingComplete() {
    preferences.edit { putBoolean(KEY_ONBOARDING_COMPLETE, true) }
    _onboardingComplete.value = true
  }

  override fun setThemePreference(preference: ThemePreference) {
    preferences.edit { putString(KEY_THEME_PREFERENCE, preference.name) }
    _themePreference.value = preference
  }

  private companion object {
    const val PREFERENCES_NAME = "image_compressor_preferences"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val KEY_THEME_PREFERENCE = "theme_preference"
  }
}
