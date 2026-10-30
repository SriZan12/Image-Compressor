package com.example.imagecompressor.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * A loading fill, without its own layout or accessibility announcement.
 *
 * Pass the same size and clip modifiers as the content it replaces. This keeps photo, text and
 * action placeholders aligned with their eventual content, including its corner radius. Use
 * [animate] = false for deterministic captures; inspection and reduced-motion modes are static.
 */
@Composable
fun EditorPlaceholder(modifier: Modifier = Modifier, animate: Boolean = true) {
  // MotionDurationScale is supplied through Compose's coroutine context, not a CompositionLocal.
  val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
  val moving = animate && !LocalInspectionMode.current && motionScale > 0f && ValueAnimator.areAnimatorsEnabled()
  val phase: State<Float>? = if (moving) {
    val transition = rememberInfiniteTransition(label = "Editor loading")
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
      label = "Loading highlight position",
    )
  } else null
  val background = MaterialTheme.colorScheme.surfaceContainerHigh
  val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)

  Box(modifier.clearAndSetSemantics { }.drawWithCache {
    val bandWidth = (size.width * .55f).coerceAtLeast(1f)
    val brush = Brush.horizontalGradient(
      colors = listOf(Color.Transparent, highlight, Color.Transparent),
      startX = 0f,
      endX = bandWidth,
    )
    onDrawBehind {
      drawRect(background)
      phase?.value?.let { progress ->
        // Read animation state only during drawing: layout and composition do not run each frame.
        clipRect {
          withTransform({ translate(left = (size.width + bandWidth) * progress - bandWidth) }) {
            drawRect(brush, size = Size(bandWidth, size.height))
          }
        }
      }
    }
  })
}
