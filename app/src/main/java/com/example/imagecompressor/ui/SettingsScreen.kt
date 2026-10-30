package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun SettingsScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier, onSetTheme: (ThemePreference) -> Unit,
  onSetDynamicColor: (Boolean) -> Unit, onClearHistory: () -> Unit) {
  var clear by remember { mutableStateOf(false) }
  if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text(stringResource(R.string.clear_history_title)) },
    text = { Text(stringResource(R.string.clear_history_body)) },
    confirmButton = { TextButton({ onClearHistory(); clear = false }) { Text(stringResource(R.string.remove_all)) } },
    dismissButton = { TextButton({ clear = false }) { Text(stringResource(R.string.cancel)) } })
  ScreenColumn(modifier) {
    SectionCard {
      Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge)
      Column(Modifier.selectableGroup()) {
        ThemePreference.entries.forEach { theme ->
          Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(state.themePreference == theme, role = Role.RadioButton,
            onClick = { onSetTheme(theme) }).padding(vertical = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(when (theme) { ThemePreference.SYSTEM -> R.drawable.ic_contrast; ThemePreference.LIGHT -> R.drawable.ic_light_mode; ThemePreference.DARK -> R.drawable.ic_dark_mode })
            Text(stringResource(when (theme) { ThemePreference.SYSTEM -> R.string.theme_system; ThemePreference.LIGHT -> R.string.theme_light; ThemePreference.DARK -> R.string.theme_dark }), Modifier.weight(1f))
            RadioButton(state.themePreference == theme, onClick = null)
          }
        }
      }
      HorizontalDivider()
      SettingSwitch(stringResource(R.string.device_colors), stringResource(R.string.device_colors_body), state.dynamicColor, onSetDynamicColor)
    }
    SectionCard {
      Text(stringResource(R.string.storage), style = MaterialTheme.typography.titleLarge)
      val used = state.history.filter { it.localPath != null }.sumOf { it.compressedSizeBytes }
      Text(stringResource(R.string.storage_usage, "%.1f MiB".format(used / (1024.0 * 1024))), style = MaterialTheme.typography.titleMedium)
      LinearProgressIndicator(progress = { (used / (512f * 1024 * 1024)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
      Text(stringResource(R.string.storage_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      ActionButton(stringResource(R.string.clear_history), { clear = true }, Modifier.fillMaxWidth(), R.drawable.ic_delete,
        secondary = true, enabled = state.history.isNotEmpty() && !state.isCompressing && !state.isSaving)
    }
    SectionCard {
      AppIcon(R.drawable.ic_lock)
      Text(stringResource(R.string.privacy), style = MaterialTheme.typography.titleMedium)
      Text(stringResource(R.string.privacy_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(stringResource(R.string.preferences_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
