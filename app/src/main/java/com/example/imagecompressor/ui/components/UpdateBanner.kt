package com.example.imagecompressor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.play.update.UpdateState
import com.example.imagecompressor.play.update.UpdateStatus
import com.example.imagecompressor.theme.FigmaUi
import kotlin.math.roundToInt

@Composable
fun UpdateBanner(
  update: UpdateState,
  canRestart: Boolean,
  onRestart: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (update.status !in setOf(UpdateStatus.PENDING, UpdateStatus.DOWNLOADING, UpdateStatus.DOWNLOADED)) return
  FigmaCard(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), padding = 16.dp) {
    val progress = update.downloadFraction
    val percent = progress?.let { (it * 100).roundToInt() }
    val title = when (update.status) {
      UpdateStatus.DOWNLOADED -> "Update ready"
      UpdateStatus.DOWNLOADING -> if (percent == null) "Downloading update" else "Downloading update · $percent%"
      else -> "Preparing update"
    }
    Text(title, color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    if (update.status == UpdateStatus.DOWNLOADED) {
      Text(
        if (canRestart) "Restart when you’re ready to use the new version."
        else "Finish saving or sharing your photos, then use Compress More or clear your selection before restarting.",
        color = FigmaUi.Body,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      )
      FigmaPrimaryButton(text = "Restart to update", onClick = onRestart, enabled = canRestart && !update.isCompleting)
    } else {
      Text("You can keep using the app.", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp)
      if (progress != null && update.status == UpdateStatus.DOWNLOADING) {
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier.fillMaxWidth().semantics { stateDescription = "$percent percent downloaded" },
          color = FigmaUi.Primary,
          trackColor = FigmaUi.Border,
        )
      } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = FigmaUi.Primary, trackColor = FigmaUi.Border)
      }
    }
  }
}
