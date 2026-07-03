package com.example.imagecompressor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.ui.components.CommonCard
import com.example.imagecompressor.ui.components.FigmaChip
import com.example.imagecompressor.ui.components.OutlinedPillButton
import com.example.imagecompressor.ui.components.PrivacyBadge
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun SettingsScreen(
    state: ImageCompressorUiState,
    modifier: Modifier = Modifier,
    onSetTheme: (ThemePreference) -> Unit,
    onClearHistory: () -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      CommonCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        Text(
          "Appearance",
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 22.sp,
          lineHeight = 28.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          "Choose how the interface should look.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 14.sp,
          lineHeight = 20.sp
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          ThemePreference.entries.forEach { preference ->
            FigmaChip(
              preference.label,
              selected = state.themePreference == preference,
              onClick = { onSetTheme(preference) })
          }
        }
      }
    }
    item {
      CommonCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        PrivacyBadge(text = "Privacy First", compact = true)
        Text(
          "Images are compressed locally on your device.",
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 18.sp,
          lineHeight = 24.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          "The app does not upload your photos to a server.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 14.sp,
          lineHeight = 20.sp
        )
      }
    }
    item {
      CommonCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        Text(
          "History",
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 22.sp,
          lineHeight = 28.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          "${state.history.size} compression record${if (state.history.size == 1) "" else "s"}",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 14.sp,
          lineHeight = 20.sp
        )
        OutlinedPillButton(
          text = "Clear History",
          leading = "×",
          onClick = onClearHistory,
          modifier = Modifier.fillMaxWidth().height(52.dp)
        )
      }
    }
  }
}
