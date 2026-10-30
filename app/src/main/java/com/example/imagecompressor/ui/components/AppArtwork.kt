package com.example.imagecompressor.ui.components

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision

/** Local artwork is decoded for its display size, without full-resolution UI bitmap allocation. */
@Composable
fun AppArtwork(@DrawableRes asset: Int, @DrawableRes fallback: Int, size: Dp,
  modifier: Modifier = Modifier, description: String? = null) {
  val context = LocalContext.current
  val pixels = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
  val request = remember(context, asset, pixels) { localArtworkRequest(context, asset, pixels) }
  var ready by remember(request) { mutableStateOf(false) }
  var failed by remember(request) { mutableStateOf(false) }
  Box(modifier.size(size).testTag(when { ready -> "artwork-ready"; failed -> "artwork-error"; else -> "artwork-loading" })) {
    if (!ready) Icon(painterResource(fallback), null, Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    AsyncImage(request, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
      onLoading = { ready = false; failed = false }, onSuccess = { ready = true; failed = false },
      onError = { failed = true })
  }
}

internal fun localArtworkRequest(context: Context, @DrawableRes asset: Int, pixels: Int): ImageRequest =
  ImageRequest.Builder(context).data(asset).size(pixels).precision(Precision.EXACT)
    .allowHardware(false).crossfade(false).diskCachePolicy(CachePolicy.DISABLED).build()
