package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier, onContinue: () -> Unit, onSkip: () -> Unit) {
  ScreenColumn(modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.align(Alignment.Start)) {
      Icon(painterResource(R.drawable.ic_shrink), null, Modifier.padding(20.dp).size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    PageTitle(stringResource(R.string.onboarding_title), stringResource(R.string.onboarding_body), large = true)
    listOf(Triple(R.drawable.ic_add_photo_alternate, R.string.onboarding_step_one, R.string.onboarding_step_one_body),
      Triple(R.drawable.ic_tune, R.string.onboarding_step_two, R.string.onboarding_step_two_body),
      Triple(R.drawable.ic_compare, R.string.onboarding_step_three, R.string.onboarding_step_three_body)).forEach { (icon, title, body) ->
      Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
          AppIcon(icon, modifier = Modifier.padding(Space.md))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
          Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
          Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    ActionButton(stringResource(R.string.get_started), onContinue, Modifier.fillMaxWidth(), R.drawable.ic_arrow_forward)
    TextButton(onSkip, Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.skip_intro)) }
    PrivacyNote()
  }
}
