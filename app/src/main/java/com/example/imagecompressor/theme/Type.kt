package com.example.imagecompressor.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.imagecompressor.R

private val AppFontFamily =
    FontFamily(
        Font(resId = R.font.poppins_thin, weight = FontWeight.Thin),
        Font(
            resId = R.font.poppins_thinitalic,
            weight = FontWeight.Thin,
            style = FontStyle.Italic,
        ),
        Font(resId = R.font.poppins_extralight, weight = FontWeight.ExtraLight),
        Font(
            resId = R.font.poppins_extralightitalic,
            weight = FontWeight.ExtraLight,
            style = FontStyle.Italic,
        ),
        Font(resId = R.font.poppins_light, weight = FontWeight.Light),
        Font(
            resId = R.font.poppins_lightitalic,
            weight = FontWeight.Light,
            style = FontStyle.Italic,
        ),
        Font(resId = R.font.poppins_regular, weight = FontWeight.Normal),
        Font(
            resId = R.font.poppins_italic,
            weight = FontWeight.Normal,
            style = FontStyle.Italic,
        ),
        Font(resId = R.font.poppins_medium, weight = FontWeight.Medium),
        Font(
            resId = R.font.poppins_mediumitalic,
            weight = FontWeight.Medium,
            style = FontStyle.Italic,
        ),
        Font(resId = R.font.poppins_semibold, weight = FontWeight.SemiBold),
        Font(
            resId = R.font.poppins_semibolditalic,
            weight = FontWeight.SemiBold,
            style = FontStyle.Italic,
        ),
        Font(resId = R.font.poppins_bold, weight = FontWeight.Bold),
        Font(
            resId = R.font.poppins_bolditalic,
            weight = FontWeight.Bold,
            style = FontStyle.Italic,
        ),
        Font(resId = R.font.poppins_extrabold, weight = FontWeight.ExtraBold),
        Font(
            resId = R.font.poppins_extrabolditalic,
            weight = FontWeight.ExtraBold,
            style = FontStyle.Italic,
        ),
        Font(resId = R.font.poppins_black, weight = FontWeight.Black),
        Font(
            resId = R.font.poppins_blackitalic,
            weight = FontWeight.Black,
            style = FontStyle.Italic,
        ),
    )

fun appTypography(): Typography =
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
