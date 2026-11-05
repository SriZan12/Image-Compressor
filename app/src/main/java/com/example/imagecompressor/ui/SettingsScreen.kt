package com.example.imagecompressor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.play.update.UpdateState
import com.example.imagecompressor.play.update.UpdateStatus
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.ui.components.FigmaCard
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
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit,
    onRateApp: () -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().background(FigmaUi.Background),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      FigmaCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        Text(
          "Appearance",
          color = FigmaUi.Ink,
          fontSize = 22.sp,
          lineHeight = 28.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          "Choose how the interface should look.",
          color = FigmaUi.Body,
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
      FigmaCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        Text("App updates", color = FigmaUi.Ink, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        val updateMessage = when {
          updateState.isChecking -> "Checking Google Play…"
          updateState.isCompleting || updateState.status == UpdateStatus.INSTALLING -> "Finishing the update…"
          updateState.status == UpdateStatus.UP_TO_DATE -> "You’re using the latest version available to you."
          updateState.status == UpdateStatus.AVAILABLE -> "An update is available. Finish any current task to continue."
          updateState.status == UpdateStatus.PENDING -> "Preparing your update. You can keep using the app."
          updateState.status == UpdateStatus.DOWNLOADING -> "Downloading your update. You can keep using the app."
          updateState.status == UpdateStatus.DOWNLOADED -> "Your update is ready. Use Restart to update when your work is finished."
          updateState.status == UpdateStatus.CANCELED -> "Update canceled. You can check again whenever you’re ready."
          updateState.status == UpdateStatus.FAILED -> "Updates are unavailable right now. Check your connection and try again through Google Play."
          else -> "Get the latest version through Google Play. Your photos stay on your device."
        }
        Text(updateMessage, color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp)
        val canOpenPlay = !state.isCompressing && !state.isSaving && !state.isLoadingImages && !state.isExternalFlowActive
        OutlinedPillButton(
          text = if (updateState.isChecking) "Checking…" else "Check for updates",
          leading = "↻",
          onClick = onCheckForUpdates,
          enabled = canOpenPlay && !updateState.isChecking && !updateState.isCompleting && updateState.activePrompt == null,
          modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { role = Role.Button },
        )
        OutlinedPillButton(
          text = "Rate this app",
          leading = "★",
          onClick = onRateApp,
          enabled = canOpenPlay && !updateState.isCompleting,
          modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { role = Role.Button },
        )
        Text("Opens the app’s Google Play listing.", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp)
      }
    }
    item {
      FigmaCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        PrivacyBadge(text = "Privacy First", compact = true)
        Text(
          "Images are compressed locally on your device.",
          color = FigmaUi.Ink,
          fontSize = 18.sp,
          lineHeight = 24.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          "The app does not upload your photos to a server.",
          color = FigmaUi.Body,
          fontSize = 14.sp,
          lineHeight = 20.sp
        )
      }
    }
    item {
      FigmaCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        Text(
          "History",
          color = FigmaUi.Ink,
          fontSize = 22.sp,
          lineHeight = 28.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          "${state.history.size} compression record${if (state.history.size == 1) "" else "s"}",
          color = FigmaUi.Body,
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
