package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.R
import com.example.imagecompressor.theme.Space

/** Shown only while real initialization runs; Android provides the cold-start splash. */
@Composable
fun SplashScreen() {
  Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
  Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.section), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Space.section, Alignment.CenterVertically)) {
    Icon(painterResource(R.drawable.ic_shrink), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
    Text(stringResource(R.string.preparing), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
  }
  }
}
