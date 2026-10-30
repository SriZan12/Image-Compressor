package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier, onContinue: () -> Unit, onSkip: () -> Unit) {
  ScreenColumn(modifier) {
    Spacer(Modifier.height(Space.lg))
    AppIcon(R.drawable.ic_shrink, size = 112.dp)
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
    PageTitle(stringResource(R.string.onboarding_title), stringResource(R.string.onboarding_body), large = true)
    Spacer(Modifier.height(Space.sm))
    ActionButton(stringResource(R.string.get_started), onContinue, Modifier.fillMaxWidth(), R.drawable.ic_add_photo_alternate)
    PrivacyNote()
    TextButton(onSkip, Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.skip_intro)) }
  }
}
