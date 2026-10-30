package com.example.imagecompressor.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.theme.Space
import com.example.imagecompressor.ui.components.*
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.example.imagecompressor.util.ShareUtils

@Composable
fun ImageCompressorRoot(viewModel: ImageCompressorViewModel = viewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val dark = when (state.themePreference) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme(); ThemePreference.LIGHT -> false; ThemePreference.DARK -> true
  }
  val view = LocalView.current
  val context = LocalContext.current
  SideEffect {
    context.activity()?.let { activity ->
      WindowCompat.getInsetsController(activity.window, view).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
      }
    }
  }
  ImageCompressorTheme(darkTheme = dark, dynamicColor = state.dynamicColor) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      when {
        state.isInitializing -> SplashScreen()
        state.initializationError != null -> ScreenColumn(Modifier.safeDrawingPadding()) {
          EmptyState(stringResource(R.string.startup_error), checkNotNull(state.initializationError), icon = R.drawable.ic_error)
          ActionButton(stringResource(R.string.retry), viewModel::refreshAvailability, Modifier.fillMaxWidth(), R.drawable.ic_restart_alt)
        }
        else -> ImageCompressorApp(state, viewModel)
      }
    }
  }
}

private tailrec fun Context.activity(): Activity? = when (this) {
  is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null
}

@Composable
private fun ImageCompressorApp(state: ImageCompressorUiState, viewModel: ImageCompressorViewModel) {
  val context = LocalContext.current
  val snackbar = remember { SnackbarHostState() }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { viewModel.addSelectedImages(it) }
  val selectMany: () -> Unit = {
    try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    catch (_: ActivityNotFoundException) { viewModel.showMessage("No photo picker is available on this device.") }
    catch (_: SecurityException) { viewModel.showMessage("Unable to open the photo picker. Please try again.") }
  }
  val newPhotos: () -> Unit = { viewModel.startOver(selectMany) }
  val share: (List<Long>) -> Unit = { ids -> viewModel.shareResults(ids) { ShareUtils.shareUris(context, it, viewModel::showMessage) } }
  LifecycleResumeEffect(Unit) { viewModel.refreshAvailability(); onPauseOrDispose { } }
  LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }
  BackHandler(state.screen !in listOf(AppScreen.ONBOARDING, AppScreen.HOME)) { viewModel.goBack() }
  AppChrome(state.screen, snackbar, viewModel::goBack, viewModel::navigateTo,
    navigationEnabled = !state.isLoadingImages && !state.isSaving && !state.isCompressing) { modifier ->
    when (state.screen) {
      AppScreen.ONBOARDING -> OnboardingScreen(modifier, { viewModel.completeOnboarding(); selectMany() }, viewModel::completeOnboarding)
      AppScreen.HOME -> HomeScreen(state, modifier, selectMany,
        { viewModel.navigateTo(AppScreen.COMPRESSION_SETTINGS) }, { viewModel.navigateTo(AppScreen.HISTORY) })
      AppScreen.PREVIEW -> PreviewScreen(state, modifier, selectMany, viewModel::removeSelectedImage,
        { viewModel.navigateTo(AppScreen.COMPRESSION_SETTINGS) })
      AppScreen.COMPRESSION_SETTINGS -> CompressionSettingsScreen(state, modifier, viewModel::updateCompressionSettings,
        viewModel::compressSelectedImages, { viewModel.navigateTo(AppScreen.PREVIEW) })
      AppScreen.RESULTS -> ResultsScreen(state, modifier, viewModel::saveAllToGallery,
        { share(state.compressedImages.map { it.historyId }) }, viewModel::openImageCompare, newPhotos,
        viewModel::cancelCompression, viewModel::retryRemaining)
      AppScreen.IMAGE_COMPARE -> ImageCompareScreen(state, modifier, viewModel::saveToGallery, { share(listOf(it.historyId)) })
      AppScreen.HISTORY -> HistoryScreen(state, modifier, viewModel::deleteHistory, { share(listOf(it.id)) }, viewModel::openHistory, newPhotos)
      AppScreen.SETTINGS -> SettingsScreen(state, modifier, viewModel::setThemePreference, viewModel::setDynamicColor, viewModel::clearHistory)
    }
  }
}

/** Shared shell also used by native rendering tests, so tests include insets and navigation. */
@Composable
internal fun AppChrome(screen: AppScreen, snackbar: SnackbarHostState = remember { SnackbarHostState() },
  onBack: () -> Unit = {}, onNavigate: (AppScreen) -> Unit = {}, navigationEnabled: Boolean = true,
  content: @Composable (Modifier) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val root = screen in listOf(AppScreen.HOME, AppScreen.HISTORY, AppScreen.SETTINGS)
    val rail = root && maxWidth >= 840.dp && LocalDensity.current.fontScale <= 1.5f
    Row(Modifier.fillMaxSize()) {
      if (rail) NavigationRail(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Spacer(Modifier.height(20.dp))
        destinations.forEach { (destination, icon, label) ->
          NavigationRailItem(screen == destination, { onNavigate(destination) }, { AppIcon(icon) },
            label = { Text(stringResource(label)) }, enabled = navigationEnabled)
          Spacer(Modifier.height(Space.lg))
        }
      }
      Scaffold(Modifier.weight(1f).imePadding(), containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
          if (screen != AppScreen.ONBOARDING) Row(Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .heightIn(min = 64.dp).padding(horizontal = Space.md, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (root) AppIcon(R.drawable.ic_shrink, modifier = Modifier.padding(Space.md))
            else IconButton(onBack, enabled = navigationEnabled) { AppIcon(R.drawable.ic_arrow_back, description = stringResource(R.string.back)) }
            Text(stringResource(screen.titleResource()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
          }
        }, bottomBar = {
          if (root && !rail) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            destinations.forEach { (destination, icon, label) ->
              NavigationBarItem(screen == destination, { onNavigate(destination) }, { AppIcon(icon) },
                label = { Text(stringResource(label)) }, enabled = navigationEnabled)
            }
          }
        }, snackbarHost = { SnackbarHost(snackbar) }) { padding -> content(Modifier.padding(padding)) }
    }
  }
}

private val destinations = listOf(Triple(AppScreen.HOME, R.drawable.ic_photo_library, R.string.photos),
  Triple(AppScreen.HISTORY, R.drawable.ic_history, R.string.history), Triple(AppScreen.SETTINGS, R.drawable.ic_settings, R.string.settings))
private fun AppScreen.titleResource(): Int = when (this) {
  AppScreen.ONBOARDING, AppScreen.HOME -> R.string.app_name
  AppScreen.PREVIEW -> R.string.selected_title
  AppScreen.COMPRESSION_SETTINGS -> R.string.choose_size
  AppScreen.RESULTS -> R.string.results
  AppScreen.IMAGE_COMPARE -> R.string.compare
  AppScreen.HISTORY -> R.string.history
  AppScreen.SETTINGS -> R.string.settings
}
