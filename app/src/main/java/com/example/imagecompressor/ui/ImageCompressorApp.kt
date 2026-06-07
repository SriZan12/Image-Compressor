package com.example.imagecompressor.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.ui.components.toMimeType
import com.example.imagecompressor.ui.state.AppScreen
import com.example.imagecompressor.ui.state.ImageCompressorUiState
import com.example.imagecompressor.util.ShareUtils
import kotlinx.coroutines.delay



@Composable
fun ImageCompressorRoot(viewModel: ImageCompressorViewModel = viewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showSplash by rememberSaveable { mutableStateOf(true) }
  val systemDarkTheme = isSystemInDarkTheme()
  val darkTheme =
    when (state.themePreference) {
      ThemePreference.SYSTEM -> systemDarkTheme
      ThemePreference.LIGHT -> false
      ThemePreference.DARK -> true
    }

  LaunchedEffect(Unit) {
    delay(1_400)
    showSplash = false
  }

  ImageCompressorTheme(darkTheme = darkTheme) {
    if (showSplash) {
      SplashScreen()
    } else {
      Surface(modifier = Modifier.fillMaxSize(), color = FigmaUi.Background) {
        ImageCompressorApp(state = state, viewModel = viewModel)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageCompressorApp(
  state: ImageCompressorUiState,
  viewModel: ImageCompressorViewModel,
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val multiPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
      viewModel.addSelectedImages(uris)
    }
  val singlePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      uri?.let { viewModel.addSelectedImages(listOf(it)) }
    }
  var pendingSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) pendingSaveAction?.invoke()
      else viewModel.showMessage("Storage permission was not granted. The image was not saved.")
      pendingSaveAction = null
    }
  val withSavePermission: (() -> Unit) -> Unit = { action ->
    val needsLegacyPermission =
      Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
          PackageManager.PERMISSION_GRANTED
    if (needsLegacyPermission) {
      pendingSaveAction = action
      permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
      action()
    }
  }
  val selectOne = {
    singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
  }
  val selectMany = {
    multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
  }

  LaunchedEffect(state.message) {
    state.message?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.consumeMessage()
    }
  }

  BackHandler(enabled = state.screen !in listOf(AppScreen.ONBOARDING, AppScreen.HOME)) {
    viewModel.goBack()
  }

  Scaffold(
    containerColor = FigmaUi.Background,
    topBar = {
      if (state.screen != AppScreen.ONBOARDING) {
        AppTopBar(
          screen = state.screen,
          onBack = viewModel::goBack,
          onClearSelection = viewModel::clearSelectedImages,
          hasSelection = state.selectedImages.isNotEmpty(),
        )
      }
    },
    bottomBar = {
      if (state.screen in listOf(AppScreen.HOME, AppScreen.HISTORY, AppScreen.SETTINGS)) {
        AppBottomBar(screen = state.screen, onNavigate = viewModel::navigateTo)
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    when (state.screen) {
      AppScreen.ONBOARDING ->
        OnboardingScreen(modifier = Modifier.padding(padding), onContinue = viewModel::completeOnboarding)
      AppScreen.HOME ->
        HomeScreen(
          state = state,
          modifier = Modifier.padding(padding),
          onSelectImages = selectMany,
          onReviewSelection = { viewModel.navigateTo(AppScreen.PREVIEW) },
          onOpenHistory = { viewModel.navigateTo(AppScreen.HISTORY) },
        )
      AppScreen.PREVIEW ->
        PreviewScreen(
          state = state,
          modifier = Modifier.padding(padding),
          onSelectOne = selectOne,
          onSelectMany = selectMany,
          onRemove = viewModel::removeSelectedImage,
          onContinue = { viewModel.navigateTo(AppScreen.COMPRESSION_SETTINGS) },
        )
      AppScreen.COMPRESSION_SETTINGS ->
        CompressionSettingsScreen(
          state = state,
          modifier = Modifier.padding(padding),
          viewModel = viewModel,
        )
      AppScreen.RESULTS ->
        ResultsScreen(
          state = state,
          modifier = Modifier.padding(padding),
          onSave = { image -> withSavePermission { viewModel.saveToGallery(image) } },
          onSaveAll = { withSavePermission(viewModel::saveAllToGallery) },
          onShare = { ShareUtils.share(context, listOf(it)) },
          onShareAll = { ShareUtils.share(context, state.compressedImages) },
          onCompare = viewModel::openImageCompare,
          onStartOver = viewModel::startOver,
        )
      AppScreen.IMAGE_COMPARE ->
        ImageCompareScreen(
          state = state,
          modifier = Modifier.padding(padding),
          onSave = { image -> withSavePermission { viewModel.saveToGallery(image) } },
          onShare = { ShareUtils.share(context, listOf(it)) },
        )
      AppScreen.HISTORY ->
        HistoryScreen(
          state = state,
          modifier = Modifier.padding(padding),
          onDelete = viewModel::deleteHistory,
          onShare = { item ->
            ShareUtils.shareUri(
              context,
              (item.savedPath ?: item.compressedUri).toUri(),
              item.outputFormat.toMimeType(),
            )
          },
        )
      AppScreen.SETTINGS ->
        SettingsScreen(
          state = state,
          modifier = Modifier.padding(padding),
          onSetTheme = viewModel::setThemePreference,
          onClearHistory = viewModel::clearHistory,
        )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
  screen: AppScreen,
  onBack: () -> Unit,
  onClearSelection: () -> Unit,
  hasSelection: Boolean,
) {
  val isRoot = screen in listOf(AppScreen.HOME, AppScreen.HISTORY, AppScreen.SETTINGS)
  Row(
    modifier = Modifier.fillMaxWidth().height(64.dp).background(FigmaUi.Background).padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = !isRoot) { onBack() },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = if (isRoot) "≡" else "←",
          color = FigmaUi.Primary,
          fontSize = 22.sp,
          lineHeight = 24.sp,
          fontWeight = FontWeight.Medium,
        )
      }
      Text(
        text = screen.title(),
        color = FigmaUi.Ink,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
      )
    }
    when {
      screen == AppScreen.PREVIEW && hasSelection ->
        TextButton(onClick = onClearSelection) {
          Text("Clear", color = FigmaUi.Body, fontWeight = FontWeight.Medium)
        }
      else ->
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
          Image(
            painter = painterResource(R.drawable.splash_privacy_icon),
            contentDescription = "Privacy",
            modifier = Modifier.width(16.dp).height(20.dp),
          )
        }
    }
  }
}

@Composable
private fun AppBottomBar(screen: AppScreen, onNavigate: (AppScreen) -> Unit) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(72.dp)
        .background(FigmaUi.Surface)
        .border(BorderStroke(1.dp, FigmaUi.Border.copy(alpha = 0.8f)))
        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    listOf(
        AppScreen.HOME to ("▣" to "Gallery"),
        AppScreen.HISTORY to ("↺" to "History"),
        AppScreen.SETTINGS to ("⚙" to "Settings"),
      )
      .forEach { (destination, pair) ->
        val selected = screen == destination
        Column(
          modifier =
            Modifier
              .clip(RoundedCornerShape(9999.dp))
              .background(if (selected) FigmaUi.Green else Color.Transparent)
              .clickable { onNavigate(destination) }
              .padding(horizontal = 18.dp, vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = pair.first,
            color = if (selected) FigmaUi.GreenText else FigmaUi.Body,
            fontSize = 18.sp,
            lineHeight = 20.sp,
          )
          Text(
            text = pair.second,
            color = if (selected) FigmaUi.GreenText else FigmaUi.Body,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
          )
        }
      }
  }
}

private fun AppScreen.title(): String =
  when (this) {
    AppScreen.ONBOARDING -> "Image Compressor"
    AppScreen.HOME -> "Image Compressor"
    AppScreen.PREVIEW -> "Selected Images"
    AppScreen.COMPRESSION_SETTINGS -> "Compression Settings"
    AppScreen.RESULTS -> "Compression Results"
    AppScreen.IMAGE_COMPARE -> "Image Compare"
    AppScreen.HISTORY -> "History"
    AppScreen.SETTINGS -> "Settings"
  }
