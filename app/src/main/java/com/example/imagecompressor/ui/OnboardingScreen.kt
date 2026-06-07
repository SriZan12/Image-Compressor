package com.example.imagecompressor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.R
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.ui.components.FigmaPrimaryButton
import com.example.imagecompressor.ui.components.PrivacyBadge

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier, onContinue: () -> Unit) {
  var slide by rememberSaveable { mutableStateOf(0) }
  val slides =
    listOf(
      OnboardingSlide(
        image = R.drawable.figma_onboarding_1,
        glow = FigmaUi.PrimaryHero.copy(alpha = 0.2f),
        title = "Compress images\neasily",
        body = "Reduce file size without losing quality\nusing our advanced local processing.",
      ),
      OnboardingSlide(
        image = R.drawable.figma_onboarding_2,
        glow = FigmaUi.Green.copy(alpha = 0.35f),
        title = "Resize and convert\nformats",
        body = "Batch process your media into the\nperfect dimensions and modern file\ntypes.",
      ),
      OnboardingSlide(
        image = R.drawable.figma_onboarding_3,
        glow = Color(0xFF944A00).copy(alpha = 0.14f),
        title = "Private by design",
        body = "Everything happens locally on your\ndevice. Your photos never leave your\nhand.",
      ),
    )
  val current = slides[slide]

  Column(modifier = modifier.fillMaxSize().background(FigmaUi.Background)) {
    Row(
      modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "Compressor",
        color = FigmaUi.Primary,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
      )
      TextButton(onClick = onContinue) {
        Text("Skip", color = FigmaUi.Body, fontSize = 14.sp, fontWeight = FontWeight.Medium)
      }
    }

    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .widthIn(max = 448.dp)
            .padding(bottom = 32.dp)
            .drawBehind { drawRoundRect(color = current.glow, cornerRadius = androidx.compose.ui.geometry.CornerRadius(48.dp.toPx())) },
      ) {
        Image(
          painter = painterResource(current.image),
          contentDescription = null,
          modifier =
            Modifier
              .fillMaxWidth()
              .height(358.dp)
              .clip(RoundedCornerShape(32.dp))
              .shadow(1.dp, RoundedCornerShape(32.dp)),
          contentScale = ContentScale.Crop,
        )
        if (slide == 2) {
            PrivacyBadge(
                text = "On-Device",
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                compact = true,
            )
        }
      }

      Text(
        text = current.title,
        color = FigmaUi.Ink,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(16.dp))
      Text(
        text = current.body,
        color = FigmaUi.Body,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
      )
    }

    Column(
      modifier = Modifier.fillMaxWidth().background(FigmaUi.Background).padding(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        slides.indices.forEach { index ->
          Box(
            modifier =
              Modifier
                .height(8.dp)
                .width(if (index == slide) 32.dp else 8.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(if (index == slide) FigmaUi.Primary else FigmaUi.Border)
          )
        }
      }
        FigmaPrimaryButton(
            text = if (slide == slides.lastIndex) "Get Started  ✓" else "Continue  →",
            onClick = {
                if (slide == slides.lastIndex) onContinue() else slide += 1
            },
        )
    }
  }
}

private data class OnboardingSlide(
  val image: Int,
  val glow: Color,
  val title: String,
  val body: String,
)
