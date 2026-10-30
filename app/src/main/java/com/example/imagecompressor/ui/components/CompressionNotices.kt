package com.example.imagecompressor.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.*

@Composable
fun CompressionNotices(image: CompressedImage, showTarget: Boolean = true) {
  if (showTarget) when (image.targetSizeStatus) {
    TargetSizeStatus.MET -> StatusPill(stringResource(R.string.within_target, checkNotNull(image.targetBytes).toReadableSize()), success = true, icon = R.drawable.ic_check_circle)
    TargetSizeStatus.UNMET -> {
      StatusPill(stringResource(R.string.review_target, checkNotNull(image.targetBytes).toReadableSize()), icon = R.drawable.ic_info)
      Notice(stringResource(R.string.target_unmet_body))
    }
    TargetSizeStatus.NOT_REQUESTED -> Unit
  }
  image.adjustments.forEach { adjustment ->
    Text(stringResource(when (adjustment) {
      CompressionAdjustment.MEMORY_LIMIT -> R.string.memory_adjustment
      CompressionAdjustment.TARGET_SIZE -> R.string.target_adjustment
      CompressionAdjustment.JPEG_BACKGROUND -> R.string.jpeg_adjustment
      CompressionAdjustment.SDR_CONVERSION -> R.string.sdr_adjustment
    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
