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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.ui.components.PrimaryButton
import com.example.imagecompressor.ui.components.PrivacyBadge

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit
) {
    var slide by rememberSaveable { mutableIntStateOf(0) }

    val slides = remember {
        listOf(
            OnboardingSlide(
                image = R.drawable.figma_onboarding_1,
                title = "Compress Images\nEasily",
                body = "Reduce file size without losing quality\nusing our advanced local processing.",
            ),
            OnboardingSlide(
                image = R.drawable.figma_onboarding_2,
                title = "Resize and convert\nformats",
                body = "Batch process your media into the\nperfect dimensions and modern file\ntypes.",
            ),
            OnboardingSlide(
                image = R.drawable.figma_onboarding_3,
                title = "Private by design",
                body = "Everything happens locally on your\ndevice. Your photos never leave your\nhand.",
            ),
        )
    }

    val current = slides[slide]



    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingHeader(onSkip = onContinue)

        Spacer(modifier = Modifier.weight(0.5f))

        OnboardingImage(
            image = current.image,
            showPrivacyBadge = slide == slides.lastIndex,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = current.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = current.body,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        OnboardingIndicator(
            selectedIndex = slide,
            totalSlides = slides.size
        )

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = if (slide == slides.lastIndex) "Get Started  ✓" else "Continue  →",
            onClick = {
                if (slide == slides.lastIndex) {
                    onContinue()
                } else {
                    slide += 1
                }
            },
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun OnboardingHeader(
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Compressor",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
        )

        TextButton(
            onClick = onSkip,
            colors = ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
        ) {
            Text(
                text = "Skip",
                color = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun OnboardingImage(
    image: Int,
    showPrivacyBadge: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp)
        ) {
            Image(
                painter = painterResource(image),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 320.dp)
                    .clip(RoundedCornerShape(32.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        if (showPrivacyBadge) {
            PrivacyBadge(
                text = "On-Device",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                compact = true,
            )
        }
    }
}

@Composable
private fun OnboardingIndicator(
    selectedIndex: Int,
    totalSlides: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSlides) { index ->
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (index == selectedIndex) 32.dp else 8.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(
                        if (index == selectedIndex) FigmaUi.Primary else FigmaUi.Border
                    )
            )
        }
    }
}

private data class OnboardingSlide(
    val image: Int,
    val title: String,
    val body: String,
)

@Preview
@Composable
fun PreviewOnBoardingScreen() {
    ImageCompressorTheme {
        OnboardingScreen() { }
    }

}
