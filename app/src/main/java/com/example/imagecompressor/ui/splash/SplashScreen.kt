package com.example.imagecompressor.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.R
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.theme.greenBackground
import com.example.imagecompressor.theme.greenText

@Composable
fun SplashScreen(
    modifier: Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val iconShape = RoundedCornerShape(28.dp)

                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(shape = iconShape)
                        .background(MaterialTheme.colorScheme.inverseOnSurface)
                        .border(width = 1.dp, color = Color.Gray, shape = iconShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.splash_main_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Image Compressor",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Fast, high-fidelity optimization for all\nyour professional media needs.",
                    style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.25.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Image(
                    painter = painterResource(R.drawable.splash_workstation),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(horizontal = 16.dp)
                        .clip(shape = RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(150.dp))

            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(color = greenBackground)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF4F6354).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(99.dp),
                            )
                            .padding(horizontal = 17.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.splash_privacy_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .width(13.33.dp)
                            .height(16.67.dp),
                    )
                    Text(
                        text = "Privacy First • On-Device Processing",
                        color = greenText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.sp,
                    )
                }

            }
        }
    }
}


@Preview
@Composable
fun PreviewSplashScreen() {
    ImageCompressorTheme() {
        SplashScreen(modifier = Modifier)
    }
}