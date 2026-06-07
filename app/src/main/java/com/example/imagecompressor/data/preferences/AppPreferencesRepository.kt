package com.example.imagecompressor.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.example.imagecompressor.data.model.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppPreferencesRepository {
  val onboardingComplete: StateFlow<Boolean>
  val themePreference: StateFlow<ThemePreference>

  fun setOnboardingComplete()

  fun setThemePreference(preference: ThemePreference)
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
