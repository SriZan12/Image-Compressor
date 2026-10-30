package com.example.imagecompressor.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val ShrinkLightColors = lightColorScheme(
  primary = Color(0xFF006B60), onPrimary = Color.White,
  primaryContainer = Color(0xFFD3F3EB), onPrimaryContainer = Color(0xFF002F29),
  secondary = Color(0xFF50645F), onSecondary = Color.White,
  secondaryContainer = Color(0xFFE0EBE6), onSecondaryContainer = Color(0xFF263B35),
  tertiary = Color(0xFF526078), onTertiary = Color.White,
  tertiaryContainer = Color(0xFFDAE2F7), onTertiaryContainer = Color(0xFF15233A),
  background = Color(0xFFFAFAF7), onBackground = Color(0xFF1B211F),
  surface = Color(0xFFFAFAF7), onSurface = Color(0xFF1B211F),
  surfaceVariant = Color(0xFFE6EAE5), onSurfaceVariant = Color(0xFF4C5651),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F4EF),
  surfaceContainer = Color(0xFFEDEFEA), surfaceContainerHigh = Color(0xFFE7EAE4),
  surfaceContainerHighest = Color(0xFFE1E5DF),
  outline = Color(0xFF727B75), outlineVariant = Color(0xFFCCD3CC),
  error = Color(0xFFBA1A1A), onError = Color.White,
  errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

internal val ShrinkDarkColors = darkColorScheme(
  primary = Color(0xFF82D5C4), onPrimary = Color(0xFF00382F),
  primaryContainer = Color(0xFF005047), onPrimaryContainer = Color(0xFFA1F2E0),
  secondary = Color(0xFFB7CCC4), onSecondary = Color(0xFF22352F),
  secondaryContainer = Color(0xFF394D46), onSecondaryContainer = Color(0xFFD3E8E0),
  tertiary = Color(0xFFB9C7E2), onTertiary = Color(0xFF233148),
  tertiaryContainer = Color(0xFF3B4960), onTertiaryContainer = Color(0xFFDAE2F7),
  background = Color(0xFF101513), onBackground = Color(0xFFE2E8E1),
  surface = Color(0xFF101513), onSurface = Color(0xFFE2E8E1),
  surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFBEC9C0),
  surfaceContainerLowest = Color(0xFF0B100E), surfaceContainerLow = Color(0xFF191E1B),
  surfaceContainer = Color(0xFF1D231F), surfaceContainerHigh = Color(0xFF272D29),
  surfaceContainerHighest = Color(0xFF323834),
  outline = Color(0xFF88948B), outlineVariant = Color(0xFF404943),
  error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

@Immutable
data class StatusColors(val success: Color, val successContainer: Color, val onSuccessContainer: Color)
internal val LightStatusColors = StatusColors(Color(0xFF27633B), Color(0xFFDBEFDE), Color(0xFF174B2A))
internal val DarkStatusColors = StatusColors(Color(0xFFA4D5AE), Color(0xFF203C29), Color(0xFFBFEBC6))
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
