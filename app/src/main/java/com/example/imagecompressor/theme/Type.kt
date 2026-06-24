package com.example.imagecompressor.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

private val AppFontFamily = FontFamily.SansSerif

fun AppTypography(): Typography =
    Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = AppFontFamily),
            displayMedium = displayMedium.copy(fontFamily = AppFontFamily),
            displaySmall = displaySmall.copy(fontFamily = AppFontFamily),
            headlineLarge = headlineLarge.copy(fontFamily = AppFontFamily),
            headlineMedium = headlineMedium.copy(fontFamily = AppFontFamily),
            headlineSmall = headlineSmall.copy(fontFamily = AppFontFamily),
            titleLarge = titleLarge.copy(fontFamily = AppFontFamily),
            titleMedium = titleMedium.copy(fontFamily = AppFontFamily),
            titleSmall = titleSmall.copy(fontFamily = AppFontFamily),
            bodyLarge = bodyLarge.copy(fontFamily = AppFontFamily),
            bodyMedium = bodyMedium.copy(fontFamily = AppFontFamily),
            bodySmall = bodySmall.copy(fontFamily = AppFontFamily),
            labelLarge = labelLarge.copy(fontFamily = AppFontFamily),
            labelMedium = labelMedium.copy(fontFamily = AppFontFamily),
            labelSmall = labelSmall.copy(fontFamily = AppFontFamily),
        )
    }
