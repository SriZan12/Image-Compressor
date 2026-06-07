package com.example.imagecompressor.ui.viewmodel

import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.data.preferences.AppPreferencesRepository

class SettingsFeature(
  private val preferencesRepository: AppPreferencesRepository,
) {
  fun setThemePreference(preference: ThemePreference) {
    preferencesRepository.setThemePreference(preference)
  }
}