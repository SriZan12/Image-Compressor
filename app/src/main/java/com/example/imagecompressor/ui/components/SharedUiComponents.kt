package com.example.imagecompressor.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import com.example.imagecompressor.R
import com.example.imagecompressor.theme.LocalStatusColors
import com.example.imagecompressor.theme.Space

@Composable
fun AppIcon(@DrawableRes resource: Int, modifier: Modifier = Modifier, description: String? = null, size: Dp = 24.dp) {
  val artwork = when (resource) {
    R.drawable.ic_shrink, R.drawable.ic_photo_library -> R.drawable.art_shrink
    R.drawable.ic_history -> R.drawable.art_recent
    R.drawable.ic_settings -> R.drawable.art_settings
    else -> null
  }
  val sculpted = when (resource) {
    R.drawable.ic_add_photo_alternate -> R.drawable.ic_soft_add_photo
    R.drawable.ic_download -> R.drawable.ic_soft_save
    R.drawable.ic_share -> R.drawable.ic_soft_share
    R.drawable.ic_compare -> R.drawable.ic_soft_compare
    R.drawable.ic_lock -> R.drawable.ic_soft_lock
    else -> null
  }
  when {
    artwork != null -> AppArtwork(artwork, resource, size, modifier.alpha(LocalContentColor.current.alpha), description)
    sculpted != null -> Icon(painterResource(sculpted), description,
      modifier.size(size).alpha(LocalContentColor.current.alpha), tint = Color.Unspecified)
    else -> Icon(painterResource(resource), description, modifier.size(size))
  }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(Modifier.padding(Space.page), verticalArrangement = Arrangement.spacedBy(Space.lg), content = content)
  }
}

@Composable
fun PageTitle(title: String, body: String? = null, large: Boolean = false) {
  Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
    Text(title, style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
      modifier = Modifier.semantics { heading() })
    if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
  @DrawableRes icon: Int? = null, enabled: Boolean = true, secondary: Boolean = false) {
  val contents: @Composable RowScope.() -> Unit = {
    if (icon != null) { AppIcon(icon); Spacer(Modifier.width(Space.sm)) }
    Text(text, modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
  }
  val sized = modifier.heightIn(min = 56.dp)
  if (secondary) OutlinedButton(onClick, sized, enabled = enabled,
    shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = Space.page, vertical = Space.lg), content = contents)
  else Button(onClick, sized, enabled = enabled,
    shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = Space.page, vertical = Space.lg), content = contents)
}

@Composable
fun ResponsiveActions(modifier: Modifier = Modifier,
  primary: @Composable (Modifier) -> Unit, secondary: @Composable (Modifier) -> Unit) {
  BoxWithConstraints(modifier.fillMaxWidth()) {
    if (maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f) {
      Column(verticalArrangement = Arrangement.spacedBy(Space.md)) { primary(Modifier.fillMaxWidth()); secondary(Modifier.fillMaxWidth()) }
    } else Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) { primary(Modifier.weight(1f)); secondary(Modifier.weight(1f)) }
  }
}

/** Two panes only when each pane has usable space; narrow/short windows use one scrollable column. */
@Composable
fun AdaptiveScreen(modifier: Modifier = Modifier, primary: @Composable (Boolean) -> Unit, detail: @Composable (Boolean) -> Unit) {
  // Move the existing composition between panes rather than recreating its drafts and gestures.
  val latestPrimary by rememberUpdatedState(primary)
  val latestDetail by rememberUpdatedState(detail)
  val primaryPane = remember { movableContentOf<Boolean> { wide -> latestPrimary(wide) } }
  val detailPane = remember { movableContentOf<Boolean> { wide -> latestDetail(wide) } }
  val narrowScroll = rememberScrollState()
  val detailScroll = rememberScrollState()
  BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val wide = maxWidth >= (if (LocalDensity.current.fontScale > 1.5f) 1080.dp else 840.dp) && maxHeight >= 480.dp
    if (wide) Row(Modifier.widthIn(max = 1320.dp).fillMaxSize().padding(Space.section), horizontalArrangement = Arrangement.spacedBy(Space.section)) {
      Box(Modifier.weight(1.35f).fillMaxHeight()) { primaryPane(true) }
      Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(detailScroll), verticalArrangement = Arrangement.spacedBy(Space.section)) { detailPane(true) }
    } else Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(narrowScroll).padding(Space.page),
      verticalArrangement = Arrangement.spacedBy(Space.section)) { primaryPane(false); detailPane(false) }
  }
}

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.page),
      verticalArrangement = Arrangement.spacedBy(Space.section), content = content)
  }
}

@Composable
fun Photo(model: Any?, description: String?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Fit,
  shape: Shape = MaterialTheme.shapes.large) {
  var failed by remember(model) { mutableStateOf(false) }
  var loading by remember(model) { mutableStateOf(true) }
  var cachedPlaceholder by remember(model) { mutableStateOf(false) }
  var previousPreviewKey by remember(model) { mutableStateOf<MemoryCache.Key?>(null) }
  var viewport by remember { mutableStateOf(IntSize.Zero) }
  val request = rememberPreviewImageRequest(model, viewport, previousPreviewKey)
  val status = stringResource(if (loading) R.string.loading_preview else if (failed) R.string.preview_unavailable else R.string.preview_ready)
  Box(modifier.onSizeChanged { viewport = it }.semantics(mergeDescendants = true) { stateDescription = status }
    .clip(shape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
    if (request != null) AsyncImage(request, description, modifier = Modifier.fillMaxSize(), contentScale = scale,
      onLoading = { failed = false; loading = true; cachedPlaceholder = it.painter != null },
      onError = { failed = true; loading = false; cachedPlaceholder = false },
      onSuccess = { failed = false; loading = false; previousPreviewKey = it.result.memoryCacheKey })
    if (loading && !cachedPlaceholder) EditorPlaceholder(Modifier.matchParentSize())
    if (failed) Column(Modifier.padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Space.sm)) {
      AppIcon(R.drawable.ic_image)
      Text(stringResource(R.string.preview_unavailable), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
  }
}

@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, success: Boolean = false, @DrawableRes icon: Int? = null) {
  val status = LocalStatusColors.current
  Surface(modifier, shape = MaterialTheme.shapes.small,
    color = if (success) status.successContainer else MaterialTheme.colorScheme.surfaceContainer,
    contentColor = if (success) status.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
      if (icon != null) Icon(painterResource(icon), null, Modifier.size(18.dp))
      Text(text, style = MaterialTheme.typography.labelMedium)
    }
  }
}

@Composable
fun Notice(message: String, modifier: Modifier = Modifier, error: Boolean = false, success: Boolean = false) {
  val status = LocalStatusColors.current
  Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
    color = when { error -> MaterialTheme.colorScheme.errorContainer; success -> status.successContainer; else -> MaterialTheme.colorScheme.surfaceContainer },
    contentColor = when { error -> MaterialTheme.colorScheme.onErrorContainer; success -> status.onSuccessContainer; else -> MaterialTheme.colorScheme.onSurfaceVariant }) {
    Row(Modifier.padding(Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
      AppIcon(if (error) R.drawable.ic_error else if (success) R.drawable.ic_check_circle else R.drawable.ic_info)
      Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
  }
}

@Composable
fun PrivacyNote(modifier: Modifier = Modifier) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
    AppIcon(R.drawable.ic_lock, size = 20.dp)
    Text(stringResource(R.string.private_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, @DrawableRes icon: Int = R.drawable.ic_photo_library) {
  Column(modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Space.lg)) {
    if (icon == R.drawable.ic_photo_library || icon == R.drawable.ic_history)
      AppIcon(icon, size = 96.dp)
    else Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
      Icon(painterResource(icon), null, Modifier.padding(24.dp).size(40.dp), tint = MaterialTheme.colorScheme.primary)
    }
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
  }
}

@Composable
fun PhotoOpeningStatus() {
  // Indeterminate work has no truthful percentage. Text also respects reduced motion.
  Text(stringResource(R.string.editor_loading), style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

/** Keep the next actions in reach on phones, with inline actions in short windows and wide panes. */
@Composable
fun AdaptiveActionScreen(modifier: Modifier = Modifier, hasActions: Boolean = true, primary: @Composable (Boolean) -> Unit,
  detail: @Composable (Boolean, @Composable () -> Unit) -> Unit, actions: @Composable () -> Unit) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    val wide = maxWidth >= (if (LocalDensity.current.fontScale > 1.5f) 1080.dp else 840.dp) && maxHeight >= 480.dp
    val pinned = hasActions && !wide && maxHeight >= 480.dp && LocalDensity.current.fontScale <= 1.3f
    Column(Modifier.fillMaxSize()) {
      AdaptiveScreen(Modifier.weight(1f), primary, detail = { twoPanes ->
        detail(twoPanes) { if (!pinned) actions() }
      })
      if (pinned) Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Box(Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.md), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { actions() }
          }
        }
      }
    }
  }
}
