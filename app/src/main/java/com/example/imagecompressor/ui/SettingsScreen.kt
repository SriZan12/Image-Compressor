package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.play.update.UpdateState
import com.example.imagecompressor.play.update.UpdateStatus
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.ImageCompressorUiState

@Composable
fun SettingsScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier, onSetTheme: (ThemePreference) -> Unit,
  onSetDynamicColor: (Boolean) -> Unit, onClearHistory: () -> Unit,
  updateState: UpdateState = UpdateState(), onCheckForUpdates: () -> Unit = {},
  onRateApp: () -> Unit = {}, playActionsEnabled: Boolean = true) {
  var clear by rememberSaveable { mutableStateOf(false) }
  if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text(stringResource(R.string.clear_history_title)) },
    text = { Text(stringResource(R.string.clear_history_body)) },
    confirmButton = { TextButton({ onClearHistory(); clear = false }) { Text(stringResource(R.string.remove_all)) } },
    dismissButton = { TextButton({ clear = false }) { Text(stringResource(R.string.cancel)) } })
  ScreenColumn(modifier) {
    SectionCard {
      Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
      Column(Modifier.selectableGroup()) {
        ThemePreference.entries.forEach { theme ->
          Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(state.themePreference == theme, role = Role.RadioButton,
            onClick = { onSetTheme(theme) }).padding(vertical = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(when (theme) { ThemePreference.SYSTEM -> R.drawable.ic_contrast; ThemePreference.LIGHT -> R.drawable.ic_light_mode; ThemePreference.DARK -> R.drawable.ic_dark_mode }, size = 28.dp)
            Text(stringResource(when (theme) { ThemePreference.SYSTEM -> R.string.theme_system; ThemePreference.LIGHT -> R.string.theme_light; ThemePreference.DARK -> R.string.theme_dark }), Modifier.weight(1f))
            RadioButton(state.themePreference == theme, onClick = null)
          }
        }
      }
      HorizontalDivider()
      SettingSwitch(stringResource(R.string.device_colors), stringResource(R.string.device_colors_body), state.dynamicColor, onSetDynamicColor)
    }
    SectionCard {
      Text(stringResource(R.string.play_updates_title), style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() })
      val message = when {
        updateState.isChecking -> R.string.play_checking_detail
        updateState.isCompleting || updateState.status == UpdateStatus.INSTALLING -> R.string.play_finishing_update
        updateState.status == UpdateStatus.UP_TO_DATE -> R.string.play_up_to_date
        updateState.status == UpdateStatus.AVAILABLE -> R.string.play_update_available
        updateState.status == UpdateStatus.PENDING -> R.string.play_update_pending_detail
        updateState.status == UpdateStatus.DOWNLOADING -> R.string.play_update_downloading_detail
        updateState.status == UpdateStatus.DOWNLOADED -> R.string.play_update_ready_detail
        updateState.status == UpdateStatus.CANCELED -> R.string.play_update_canceled
        updateState.status == UpdateStatus.FAILED -> R.string.play_update_unavailable
        else -> R.string.play_updates_description
      }
      Text(stringResource(message), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      val canOpenPlay = playActionsEnabled && !state.isCompressing && !state.isSaving &&
        !state.isLoadingImages && !state.isExternalFlowActive && !state.isPreparingShare &&
        !state.isInitializing && !state.isCancelling && state.editorAction == null
      ActionButton(stringResource(if (updateState.isChecking) R.string.play_checking else R.string.play_check_updates),
        onCheckForUpdates, Modifier.fillMaxWidth(), R.drawable.ic_download, secondary = true,
        enabled = canOpenPlay && !updateState.isChecking && !updateState.isCompleting && updateState.activePrompt == null)
      ActionButton(stringResource(R.string.play_rate_app), onRateApp, Modifier.fillMaxWidth(), secondary = true,
        enabled = canOpenPlay && !updateState.isCompleting && updateState.activePrompt == null)
      Text(stringResource(R.string.play_rate_description), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionCard {
      Text(stringResource(R.string.storage), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
      val used = state.history.filter { it.localPath != null }.sumOf { it.compressedSizeBytes }
      Text(stringResource(R.string.storage_usage, if (used == 0L) "0 MB" else used.toReadableSize()), style = MaterialTheme.typography.titleMedium)
      LinearProgressIndicator(progress = { (used / (512f * 1024 * 1024)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
      Text(stringResource(R.string.storage_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      ActionButton(stringResource(R.string.clear_history), { clear = true }, Modifier.fillMaxWidth(), R.drawable.ic_delete,
        secondary = true, enabled = state.history.isNotEmpty() && !state.isCompressing && !state.isSaving)
    }
    SectionCard {
      AppIcon(R.drawable.ic_lock)
      Text(stringResource(R.string.privacy), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
      Text(stringResource(R.string.privacy_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(stringResource(R.string.preferences_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
