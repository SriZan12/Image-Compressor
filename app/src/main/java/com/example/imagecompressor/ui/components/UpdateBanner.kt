package com.example.imagecompressor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.imagecompressor.R
import com.example.imagecompressor.play.update.UpdateState
import com.example.imagecompressor.play.update.UpdateStatus
import com.example.imagecompressor.theme.Space
import kotlin.math.roundToInt

@Composable
fun UpdateBanner(update: UpdateState, canRestart: Boolean, onRestart: () -> Unit, modifier: Modifier = Modifier) {
  if (update.status !in setOf(UpdateStatus.PENDING, UpdateStatus.DOWNLOADING, UpdateStatus.DOWNLOADED)) return
  SectionCard(modifier.padding(horizontal = Space.page, vertical = Space.sm)) {
    val progress = update.downloadFraction
    val percent = progress?.let { (it * 100).roundToInt() }
    val title = when (update.status) {
      UpdateStatus.DOWNLOADED -> stringResource(R.string.play_update_ready)
      UpdateStatus.DOWNLOADING -> if (percent == null) stringResource(R.string.play_downloading)
        else stringResource(R.string.play_downloading_percent, percent)
      else -> stringResource(R.string.play_pending)
    }
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    if (update.status == UpdateStatus.DOWNLOADED) {
      Text(stringResource(if (canRestart) R.string.play_restart_when_ready else R.string.play_finish_work),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      ActionButton(stringResource(R.string.play_restart_action), onRestart, Modifier.fillMaxWidth(),
        icon = R.drawable.ic_restart_alt, enabled = canRestart && !update.isCompleting)
    } else {
      Text(stringResource(R.string.play_continue_work), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (progress != null && update.status == UpdateStatus.DOWNLOADING) {
        val description = stringResource(R.string.play_download_progress, checkNotNull(percent))
        LinearProgressIndicator(progress = { progress },
          modifier = Modifier.fillMaxWidth().semantics { stateDescription = description })
      } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
  }
}
