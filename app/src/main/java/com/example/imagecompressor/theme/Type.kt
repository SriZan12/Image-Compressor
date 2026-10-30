package com.example.imagecompressor.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.R

// Bundled weights keep typography available offline and avoid a downloadable-font layout shift.
private val PoppinsFamily = FontFamily(
  Font(R.font.poppins_regular, FontWeight.Normal),
  Font(R.font.poppins_medium, FontWeight.Medium),
  Font(R.font.poppins_semibold, FontWeight.SemiBold),
)

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
  fontFamily = PoppinsFamily, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
)

// Use scalable text units throughout; supporting copy stays at least 13sp before user scaling.
val Typography = Typography(
  displayLarge = type(40, 48, FontWeight.SemiBold), displayMedium = type(32, 40, FontWeight.SemiBold),
  displaySmall = type(32, 40, FontWeight.SemiBold), headlineLarge = type(32, 40, FontWeight.SemiBold),
  headlineMedium = type(28, 36, FontWeight.SemiBold), headlineSmall = type(24, 32, FontWeight.SemiBold),
  titleLarge = type(22, 28, FontWeight.SemiBold), titleMedium = type(16, 24, FontWeight.SemiBold),
  titleSmall = type(14, 20, FontWeight.Medium), bodyLarge = type(16, 24), bodyMedium = type(16, 24),
  bodySmall = type(13, 20), labelLarge = type(14, 20, FontWeight.Medium),
  labelMedium = type(13, 20, FontWeight.Medium), labelSmall = type(13, 20, FontWeight.Medium),
)
