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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.imagecompressor.R
import com.example.imagecompressor.data.local.CompressionHistoryEntity
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.ResizeMode
import com.example.imagecompressor.data.model.SelectedImage
import com.example.imagecompressor.data.model.ThemePreference
import com.example.imagecompressor.data.model.calculateReductionPercent
import com.example.imagecompressor.data.model.toReadableSize
import com.example.imagecompressor.theme.FigmaUi
import com.example.imagecompressor.theme.ImageCompressorTheme
import com.example.imagecompressor.util.ShareUtils
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
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

@Composable
private fun SplashScreen(modifier: Modifier = Modifier) {
  val background = Color(0xFFF8F9FF)
  val outline = Color(0xFFC1C7D2)
  val titleColor = Color(0xFF191C20)
  val subtitleColor = Color(0xFF414750).copy(alpha = 0.8f)
  val blue = Color(0xFF00497D)
  val greenSurface = Color(0xFFCFE5D2)
  val greenText = Color(0xFF536758)
  val mutedText = Color(0xFF717782)

  BoxWithConstraints(modifier = modifier.fillMaxSize().background(background)) {
    val compact = maxHeight < 760.dp
    val topPadding = if (compact) 72.dp else 150.dp
    val imageHeight = if (compact) 170.dp else 199.38.dp
    val footerPadding = if (compact) 20.dp else 32.dp
    val iconShape = RoundedCornerShape(28.dp)
    val imageShape = RoundedCornerShape(12.dp)

    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = topPadding, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier =
            Modifier
              .size(128.dp)
              .drawBehind { drawCircle(color = Color(0x1A0061A4), radius = 96.dp.toPx()) }
              .shadow(elevation = 8.dp, shape = iconShape, clip = false)
              .clip(iconShape)
              .background(background)
              .border(width = 1.dp, color = outline, shape = iconShape),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            painter = painterResource(R.drawable.splash_main_icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
          )
        }

        Spacer(Modifier.height(32.dp))
        Text(
          text = "Image Compressor",
          color = titleColor,
          fontSize = 32.sp,
          lineHeight = 40.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = (-0.8f).sp,
          textAlign = TextAlign.Center,
          maxLines = 1,
        )
        Spacer(Modifier.height(7.5.dp))
        Text(
          text = "Fast, high-fidelity optimization for all\nyour professional media needs.",
          color = subtitleColor,
          fontSize = 14.sp,
          lineHeight = 20.sp,
          letterSpacing = 0.25.sp,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .widthIn(max = 384.dp)
              .height(imageHeight)
              .clip(imageShape)
              .background(Color(0xFFE6E8EE))
              .border(width = 1.dp, color = outline, shape = imageShape)
        ) {
          Image(
            painter = painterResource(R.drawable.splash_workstation),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
          Box(
            modifier =
              Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFFECEEF4))
          )
          Box(
            modifier =
              Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(blue)
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = footerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          modifier =
            Modifier
              .clip(RoundedCornerShape(9999.dp))
              .background(greenSurface)
              .border(
                width = 1.dp,
                color = Color(0xFF4F6354).copy(alpha = 0.1f),
                shape = RoundedCornerShape(9999.dp),
              )
              .padding(horizontal = 17.dp, vertical = 9.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(
            painter = painterResource(R.drawable.splash_privacy_icon),
            contentDescription = null,
            modifier = Modifier.width(13.33.dp).height(16.67.dp),
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
        Text(
          text = "BY DESIGN",
          color = mutedText,
          fontSize = 11.sp,
          lineHeight = 16.sp,
          fontWeight = FontWeight.Medium,
          letterSpacing = 1.1.sp,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun OnboardingScreen(modifier: Modifier = Modifier, onContinue: () -> Unit) {
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



@Composable
private fun PreviewScreen(
  state: ImageCompressorUiState,
  modifier: Modifier = Modifier,
  onSelectOne: () -> Unit,
  onSelectMany: () -> Unit,
  onRemove: (SelectedImage) -> Unit,
  onContinue: () -> Unit,
) {
  Column(modifier = modifier.fillMaxSize().background(FigmaUi.Background).padding(horizontal = 16.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedPillButton(text = "Add One", leading = "+", onClick = onSelectOne, modifier = Modifier.weight(1f).height(48.dp))
      OutlinedPillButton(text = "Add Multiple", leading = "▣", onClick = onSelectMany, modifier = Modifier.weight(1f).height(48.dp))
    }
    if (state.isLoadingImages) {
      LoadingCard("Reading image details...")
    } else if (state.selectedImages.isEmpty()) {
      EmptyState(
        title = "No images selected",
        body = "Pick one image or choose several at once.",
        modifier = Modifier.weight(1f),
      )
    } else {
      Text(
        "${state.selectedImages.size} image${if (state.selectedImages.size == 1) "" else "s"} ready",
        color = FigmaUi.Ink,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
      )
      LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(state.selectedImages, key = { it.id }) { image ->
          SelectedImageCard(image = image, onRemove = { onRemove(image) })
        }
      }
      FigmaPrimaryButton(
        text = "Choose Compression Settings",
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      )
    }
  }
}

@Composable
private fun SelectedImageCard(image: SelectedImage, onRemove: () -> Unit) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaUi.SurfaceSoft),
    border = BorderStroke(1.dp, FigmaUi.Border.copy(alpha = 0.35f)),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      AsyncImage(
        model = image.uri,
        contentDescription = image.displayName,
        modifier =
          Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(FigmaUi.Surface),
        contentScale = ContentScale.Crop,
      )
      Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(image.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = FigmaUi.Ink, fontWeight = FontWeight.Bold)
        Text(image.sizeBytes.toReadableSize(), color = FigmaUi.Body, fontSize = 12.sp, lineHeight = 16.sp)
        Text("${image.width} x ${image.height} • ${image.format}", color = FigmaUi.Body, fontSize = 12.sp, lineHeight = 16.sp)
        TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) {
          Text("Remove", color = FigmaUi.Primary)
        }
      }
    }
  }
}

@Composable
private fun CompressionSettingsScreen(
  state: ImageCompressorUiState,
  modifier: Modifier = Modifier,
  viewModel: ImageCompressorViewModel,
) {
  val settings = state.compressionSettings
  Box(modifier = modifier.fillMaxSize().background(FigmaUi.Background)) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 128.dp),
      verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
      item {
        PrivacyBadge(text = "Processed locally on your device")
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          state.selectedImages.take(3).forEach { image ->
            AsyncImage(
              model = image.uri,
              contentDescription = image.displayName,
              modifier =
                Modifier
                  .weight(1f)
                  .height(84.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(FigmaUi.Surface)
                  .border(1.dp, FigmaUi.Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
              contentScale = ContentScale.Crop,
            )
          }
          val remaining = (state.selectedImages.size - 3).coerceAtLeast(0)
          if (state.selectedImages.size < 3) {
            repeat(3 - state.selectedImages.size) {
              Box(modifier = Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(12.dp)).background(FigmaUi.Surface))
            }
          }
          Box(
            modifier =
              Modifier
                .weight(1f)
                .height(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE6E8EE))
                .border(1.dp, FigmaUi.Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Text("+$remaining", color = FigmaUi.Body, fontSize = 16.sp, lineHeight = 24.sp)
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Quality", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
            Text("${settings.quality}%", color = FigmaUi.Primary, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
          }
          Slider(
            value = settings.quality.toFloat(),
            onValueChange = { viewModel.updateQuality(it.roundToStep()) },
            valueRange = 10f..100f,
            steps = 8,
            colors =
              SliderDefaults.colors(
                thumbColor = FigmaUi.Primary,
                activeTrackColor = FigmaUi.Primary,
                inactiveTrackColor = Color(0xFFE1E2E8),
              ),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FigmaChip("Small Size", selected = settings.quality <= 50, onClick = { viewModel.updateQuality(50) })
            FigmaChip("Balanced", selected = settings.quality in 51..85, onClick = { viewModel.updateQuality(80) })
            FigmaChip("High Quality", selected = settings.quality > 85, onClick = { viewModel.updateQuality(95) })
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Target Size", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(null to "Off", 100L * 1024 to "100 KB", 500L * 1024 to "500 KB", 1024L * 1024 to "1 MB")
              .forEach { (bytes, label) ->
                FigmaChip(label, selected = settings.targetBytes == bytes, onClick = { viewModel.updateTargetBytes(bytes) })
              }
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("Resize (Optional)", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            FigmaTextField(
              value = settings.customWidth.ifBlank { if (settings.resizeMode == ResizeMode.CUSTOM) "" else "Original" },
              label = "Width (px)",
              onValueChange = {
                viewModel.updateResizeMode(ResizeMode.CUSTOM)
                viewModel.updateCustomWidth(it)
              },
              modifier = Modifier.weight(1f),
            )
            FigmaTextField(
              value = settings.customHeight.ifBlank { if (settings.resizeMode == ResizeMode.CUSTOM) "" else "Original" },
              label = "Height (px)",
              onValueChange = {
                viewModel.updateResizeMode(ResizeMode.CUSTOM)
                viewModel.updateCustomHeight(it)
              },
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 75, 100).forEach { percent ->
              FigmaChip(
                "$percent%",
                selected = settings.resizeMode == ResizeMode.PERCENTAGE && settings.resizePercent == percent,
                onClick = {
                  viewModel.updateResizeMode(ResizeMode.PERCENTAGE)
                  viewModel.updateResizePercent(percent)
                },
              )
            }
            FigmaChip("Original", selected = settings.resizeMode == ResizeMode.ORIGINAL, onClick = { viewModel.updateResizeMode(ResizeMode.ORIGINAL) })
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("Output Format", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
          Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FigmaUi.Surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
          ) {
            OutputFormat.entries.forEach { format ->
              Box(
                modifier =
                  Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (settings.outputFormat == format) Color.White else Color.Transparent)
                    .clickable { viewModel.updateOutputFormat(format) },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  format.label,
                  color = if (settings.outputFormat == format) FigmaUi.Ink else FigmaUi.Body,
                  fontSize = 14.sp,
                  lineHeight = 20.sp,
                  fontWeight = FontWeight.Medium,
                )
              }
            }
          }
        }
      }

      item {
        FigmaCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("Preserve Aspect Ratio", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
              Text("Avoid stretching custom dimensions.", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Switch(
              checked = settings.preserveAspectRatio,
              onCheckedChange = viewModel::updatePreserveAspectRatio,
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = FigmaUi.Primary,
                  uncheckedTrackColor = FigmaUi.Border,
                ),
            )
          }
        }
      }
    }

    BottomActionBar {
      FigmaPrimaryButton(
        text = "Compress ${state.selectedImages.size} Image${if (state.selectedImages.size == 1) "" else "s"}",
        onClick = viewModel::compressSelectedImages,
      )
    }
  }
}

@Composable
private fun SettingsQualityCard(settings: CompressionSettings, onQualityChange: (Int) -> Unit) {
  SectionCard {
    Text("Quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("${settings.quality}% quality")
    Slider(
      value = settings.quality.toFloat(),
      onValueChange = { onQualityChange(it.roundToStep()) },
      valueRange = 10f..100f,
      steps = 8,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Smaller File", style = MaterialTheme.typography.labelMedium)
      Text("Better Quality", style = MaterialTheme.typography.labelMedium)
    }
  }
}

@Composable
private fun SettingsTargetCard(settings: CompressionSettings, onTargetChange: (Long?) -> Unit) {
  SectionCard {
    Text("Target file size", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("Optional. The app will try to get close to your chosen size.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(null to "Off", 100L * 1024 to "100 KB", 500L * 1024 to "500 KB", 1024L * 1024 to "1 MB")
        .forEach { (bytes, label) ->
          FilterChip(selected = settings.targetBytes == bytes, onClick = { onTargetChange(bytes) }, label = { Text(label) })
        }
    }
  }
}

@Composable
private fun SettingsResizeCard(
  settings: CompressionSettings,
  onResizeModeChange: (ResizeMode) -> Unit,
  onResizePercentChange: (Int) -> Unit,
  onCustomWidthChange: (String) -> Unit,
  onCustomHeightChange: (String) -> Unit,
  onPreserveChange: (Boolean) -> Unit,
) {
  SectionCard {
    Text("Resize", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    ResizeMode.entries.forEach { mode ->
      FilterChip(
        selected = settings.resizeMode == mode,
        onClick = { onResizeModeChange(mode) },
        label = { Text(mode.label) },
      )
    }
    when (settings.resizeMode) {
      ResizeMode.ORIGINAL -> Text("Keeps the original dimensions when memory allows.")
      ResizeMode.PERCENTAGE -> {
        Text("${settings.resizePercent}% of original width and height")
        Slider(
          value = settings.resizePercent.toFloat(),
          onValueChange = { onResizePercentChange(it.roundToStep()) },
          valueRange = 10f..100f,
          steps = 8,
        )
      }
      ResizeMode.CUSTOM -> {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = settings.customWidth,
            onValueChange = onCustomWidthChange,
            modifier = Modifier.weight(1f),
            label = { Text("Width") },
            singleLine = true,
          )
          OutlinedTextField(
            value = settings.customHeight,
            onValueChange = onCustomHeightChange,
            modifier = Modifier.weight(1f),
            label = { Text("Height") },
            singleLine = true,
          )
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text("Preserve aspect ratio")
      Switch(checked = settings.preserveAspectRatio, onCheckedChange = onPreserveChange)
    }
  }
}

@Composable
private fun SettingsFormatCard(settings: CompressionSettings, onFormatChange: (OutputFormat) -> Unit) {
  SectionCard {
    Text("Convert format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutputFormat.entries.forEach { format ->
        FilterChip(
          selected = settings.outputFormat == format,
          onClick = { onFormatChange(format) },
          label = { Text(format.label) },
        )
      }
    }
    if (settings.outputFormat == OutputFormat.PNG) {
      Text("PNG keeps sharp edges and transparency. Its quality slider has less effect on file size.")
    }
  }
}

@Composable
private fun ResultsScreen(
  state: ImageCompressorUiState,
  modifier: Modifier = Modifier,
  onSave: (CompressedImage) -> Unit,
  onSaveAll: () -> Unit,
  onShare: (CompressedImage) -> Unit,
  onShareAll: () -> Unit,
  onCompare: (CompressedImage) -> Unit,
  onStartOver: () -> Unit,
) {
  if (state.isCompressing) {
    CompressionProgressScreen(state = state, modifier = modifier)
    return
  }

  val totalBefore = state.compressedImages.sumOf { it.original.sizeBytes }
  val totalAfter = state.compressedImages.sumOf { it.compressedSizeBytes }
  val reduction = calculateReductionPercent(totalBefore, totalAfter)

  Column(modifier = modifier.fillMaxSize().background(FigmaUi.Background)) {
    if (state.compressedImages.isEmpty()) {
      EmptyState(
        title = "No results yet",
        body = "Compression did not produce any files. Go back and try a different image.",
        modifier = Modifier.weight(1f),
      )
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        item {
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
              modifier = Modifier.size(64.dp).clip(CircleShape).background(FigmaUi.Green),
              contentAlignment = Alignment.Center,
            ) {
              Text("✓", color = FigmaUi.GreenDark, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text("Compression Complete!", color = FigmaUi.Ink, fontSize = 24.sp, lineHeight = 32.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            PrivacyBadge(text = "On-Device Processing", compact = true)
          }
        }

        item {
          FigmaCard(shape = RoundedCornerShape(12.dp), padding = 24.dp) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Column {
                Text("Total Before", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                Text(totalBefore.toReadableSize(), color = FigmaUi.Ink, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
              }
              Box(modifier = Modifier.height(40.dp).width(1.dp).background(FigmaUi.Border))
              Column(horizontalAlignment = Alignment.End) {
                Text("Total After", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                Text(totalAfter.toReadableSize(), color = FigmaUi.Primary, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
              }
            }
            Box(
              modifier =
                Modifier
                  .align(Alignment.CenterHorizontally)
                  .clip(RoundedCornerShape(9999.dp))
                  .background(FigmaUi.Primary)
                  .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
              Text("$reduction% Saved", color = Color.White, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
            }
          }
        }

        item {
          Text("Summary", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
        }

        items(state.compressedImages, key = { it.outputFilePath }) { image ->
          ResultCard(
            image = image,
            onSave = { onSave(image) },
            onShare = { onShare(image) },
            onCompare = { onCompare(image) },
          )
        }

        item {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            FigmaPrimaryButton(text = if (state.isSaving) "Saving..." else "Save to Gallery", onClick = onSaveAll, enabled = !state.isSaving)
            OutlinedPillButton(text = "Share All", leading = "↗", onClick = onShareAll, modifier = Modifier.fillMaxWidth().height(56.dp))
            TextButton(onClick = onStartOver, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
              Text("Compress More", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            }
          }
        }

        item {
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Text("Images were never uploaded.", color = FigmaUi.Muted, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun ResultCard(image: CompressedImage, onSave: () -> Unit, onShare: () -> Unit, onCompare: () -> Unit) {
  FigmaCard(shape = RoundedCornerShape(12.dp), padding = 12.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FigmaUi.Surface)
            .clickable(onClick = onCompare),
      ) {
        AsyncImage(
          model = image.outputUri,
          contentDescription = image.original.displayName,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
        Box(
          modifier =
            Modifier
              .align(Alignment.TopEnd)
              .padding(8.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(FigmaUi.Ink.copy(alpha = 0.82f))
              .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
          Text(
            "${image.original.sizeBytes.toReadableSize()} → ${image.compressedSizeBytes.toReadableSize()}",
            color = FigmaUi.Background,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(image.original.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = FigmaUi.Ink, fontWeight = FontWeight.Bold)
        Text("${image.reductionPercent}% smaller • ${image.width} x ${image.height} • ${image.format.label}", color = FigmaUi.Body, fontSize = 12.sp, lineHeight = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = onCompare) { Text("Compare", color = FigmaUi.Primary) }
          TextButton(onClick = onShare) { Text("Share", color = FigmaUi.Primary) }
          TextButton(onClick = onSave, enabled = image.savedUri == null) {
            Text(if (image.savedUri == null) "Save" else "Saved", color = if (image.savedUri == null) FigmaUi.Primary else FigmaUi.Muted)
          }
        }
      }
    }
  }
}

@Composable
private fun CompressionProgressScreen(state: ImageCompressorUiState, modifier: Modifier = Modifier) {
  val fraction = state.progress.fraction.coerceIn(0f, 1f)
  val percent = (fraction * 100).roundToInt()
  val maxImageIndex = (state.selectedImages.size - 1).coerceAtLeast(0)
  val currentIndex = state.progress.completed.coerceIn(0, maxImageIndex)
  val currentCount = state.progress.completed.coerceIn(0, state.progress.total)
  val current = state.selectedImages.getOrNull(currentIndex)

  Box(modifier = modifier.fillMaxSize().background(FigmaUi.Background)) {
    Box(
      modifier =
        Modifier
          .align(Alignment.Center)
          .size(500.dp)
          .clip(CircleShape)
          .background(FigmaUi.Primary.copy(alpha = 0.08f))
    )
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 56.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(256.dp)) {
        CircularProgressIndicator(
          progress = { fraction },
          modifier = Modifier.size(256.dp),
          color = FigmaUi.Primary,
          trackColor = Color(0xFFD7E2EA),
          strokeWidth = 12.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("$percent%", color = FigmaUi.Primary, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-1.425f).sp)
          Text("COMPLETE", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
        }
      }
      Spacer(Modifier.height(48.dp))
      Text(
        "Compressing your images\nlocally...",
        color = FigmaUi.Ink,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        "Processing ${current?.displayName ?: "image"} ($currentCount of ${state.progress.total})",
        color = FigmaUi.Body,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(32.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(
          label = "Completed",
          value = "${state.progress.completed}/${state.progress.total}",
          tone = FigmaUi.Green,
          modifier = Modifier.weight(1f),
        )
        StatCard(
          label = "Status",
          value = "~working",
          tone = FigmaUi.SurfaceSoft,
          modifier = Modifier.weight(1f),
        )
      }
    }
    Box(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF2E3135))
          .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Text("Your images never leave your device.", color = Color(0xFFEFF0F7), fontSize = 14.sp, lineHeight = 20.sp)
    }
  }
}

@Composable
private fun ImageCompareScreen(
  state: ImageCompressorUiState,
  modifier: Modifier = Modifier,
  onSave: (CompressedImage) -> Unit,
  onShare: (CompressedImage) -> Unit,
) {
  val image = state.compareImage
  if (image == null) {
    EmptyState(
      title = "No image selected",
      body = "Open a compressed result to compare it.",
      modifier = modifier.fillMaxSize(),
    )
    return
  }

  LazyColumn(
    modifier = modifier.fillMaxSize().background(FigmaUi.Background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      FigmaCard(shape = RoundedCornerShape(28.dp), padding = 20.dp) {
        Text(
          image.original.displayName,
          color = FigmaUi.Ink,
          fontSize = 22.sp,
          lineHeight = 28.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          "${image.reductionPercent}% smaller • ${image.format.label}",
          color = FigmaUi.Primary,
          fontSize = 16.sp,
          lineHeight = 24.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        ComparisonPanel(
          label = "Original",
          model = image.original.uri,
          size = image.original.sizeBytes.toReadableSize(),
          modifier = Modifier.weight(1f),
        )
        ComparisonPanel(
          label = "Compressed",
          model = image.outputUri,
          size = image.compressedSizeBytes.toReadableSize(),
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard("Before", image.original.sizeBytes.toReadableSize(), FigmaUi.SurfaceSoft, Modifier.weight(1f))
        StatCard("After", image.compressedSizeBytes.toReadableSize(), FigmaUi.Green, Modifier.weight(1f))
      }
    }

    item {
      FigmaCard {
        Text("Output Details", color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
        Text("${image.width} x ${image.height}", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp)
        Text(image.outputFilePath, color = FigmaUi.Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FigmaPrimaryButton(
          text = if (image.savedUri == null) "Save to Gallery" else "Saved",
          onClick = { onSave(image) },
          enabled = image.savedUri == null,
        )
        OutlinedPillButton(text = "Share", leading = "↗", onClick = { onShare(image) }, modifier = Modifier.fillMaxWidth().height(56.dp))
      }
    }
  }
}

@Composable
private fun ComparisonPanel(label: String, model: Any, size: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .aspectRatio(0.78f)
          .clip(RoundedCornerShape(16.dp))
          .background(FigmaUi.Surface)
          .border(1.dp, FigmaUi.Border.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
    ) {
      AsyncImage(model = model, contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      Box(
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(9999.dp))
            .background(FigmaUi.Ink.copy(alpha = 0.82f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Text(label, color = FigmaUi.Background, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
      }
    }
    Text(size, color = FigmaUi.Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun ImagePreview(model: Any, label: String, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    AsyncImage(
      model = model,
      contentDescription = label,
      modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
      contentScale = ContentScale.Crop,
    )
    Text(label, style = MaterialTheme.typography.labelMedium)
  }
}

@Composable
private fun HistoryScreen(
  state: ImageCompressorUiState,
  modifier: Modifier = Modifier,
  onDelete: (CompressionHistoryEntity) -> Unit,
  onShare: (CompressionHistoryEntity) -> Unit,
) {
  if (state.history.isEmpty()) {
    EmptyState(
      title = "No compression history",
      body = "Compressed images will appear here with their saved location.",
      modifier = modifier.fillMaxSize().background(FigmaUi.Background),
    )
    return
  }
  LazyColumn(
    modifier = modifier.fillMaxSize().background(FigmaUi.Background),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      PrivacyBadge(text = "Images were compressed locally")
    }
    items(state.history, key = { it.id }) { item ->
      HistoryCard(item = item, onDelete = { onDelete(item) }, onShare = { onShare(item) })
    }
  }
}

@Composable
private fun HistoryCard(item: CompressionHistoryEntity, onDelete: () -> Unit, onShare: () -> Unit) {
  val reduction = calculateReductionPercent(item.originalSizeBytes, item.compressedSizeBytes)
  FigmaCard(shape = RoundedCornerShape(12.dp), padding = 16.dp) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = FigmaUi.Ink, fontWeight = FontWeight.Bold)
        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.createdAt)), color = FigmaUi.Body, fontSize = 12.sp, lineHeight = 16.sp)
      }
      Box(modifier = Modifier.clip(RoundedCornerShape(9999.dp)).background(FigmaUi.Green).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text("$reduction%", color = FigmaUi.GreenText, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
      StatCard("Before", item.originalSizeBytes.toReadableSize(), FigmaUi.SurfaceSoft, Modifier.weight(1f))
      StatCard("After", item.compressedSizeBytes.toReadableSize(), FigmaUi.Green, Modifier.weight(1f))
    }
    Text("${item.width} x ${item.height} • ${item.outputFormat}", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp)
    Text(
      item.savedPath ?: "Not saved to gallery",
      color = FigmaUi.Muted,
      fontSize = 12.sp,
      lineHeight = 16.sp,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedPillButton(text = "Share", leading = "↗", onClick = onShare, modifier = Modifier.weight(1f).height(44.dp))
      TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete", color = FigmaUi.Body) }
    }
  }
}

@Composable
private fun SettingsScreen(
  state: ImageCompressorUiState,
  modifier: Modifier = Modifier,
  onSetTheme: (ThemePreference) -> Unit,
  onClearHistory: () -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().background(FigmaUi.Background),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      FigmaCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        Text("Appearance", color = FigmaUi.Ink, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        Text("Choose how the interface should look.", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          ThemePreference.entries.forEach { preference ->
            FigmaChip(preference.label, selected = state.themePreference == preference, onClick = { onSetTheme(preference) })
          }
        }
      }
    }
    item {
      FigmaCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        PrivacyBadge(text = "Privacy First", compact = true)
        Text("Images are compressed locally on your device.", color = FigmaUi.Ink, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
        Text("The app does not upload your photos to a server.", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp)
      }
    }
    item {
      FigmaCard(shape = RoundedCornerShape(28.dp), padding = 24.dp) {
        Text("History", color = FigmaUi.Ink, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        Text("${state.history.size} compression record${if (state.history.size == 1) "" else "s"}", color = FigmaUi.Body, fontSize = 14.sp, lineHeight = 20.sp)
        OutlinedPillButton(text = "Clear History", leading = "×", onClick = onClearHistory, modifier = Modifier.fillMaxWidth().height(52.dp))
      }
    }
  }
}

@Composable
fun FigmaCard(
  modifier: Modifier = Modifier,
  shape: RoundedCornerShape = RoundedCornerShape(12.dp),
  padding: androidx.compose.ui.unit.Dp = 16.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = shape,
    colors = CardDefaults.cardColors(containerColor = FigmaUi.SurfaceSoft),
    border = BorderStroke(1.dp, FigmaUi.Border.copy(alpha = 0.25f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(padding),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

@Composable
fun FigmaPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.fillMaxWidth().height(56.dp),
    shape = RoundedCornerShape(9999.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = FigmaUi.Primary,
        contentColor = Color.White,
        disabledContainerColor = FigmaUi.Border,
        disabledContentColor = Color.White.copy(alpha = 0.8f),
      ),
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp, pressedElevation = 2.dp),
  ) {
    Text(text, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp)
  }
}

@Composable
fun OutlinedPillButton(
  text: String,
  leading: String? = null,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(9999.dp))
        .background(FigmaUi.Background)
        .border(1.dp, if (enabled) FigmaUi.Muted else FigmaUi.Border, RoundedCornerShape(9999.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (leading != null) {
      Text(leading, color = if (enabled) FigmaUi.Primary else FigmaUi.Muted, fontSize = 18.sp, lineHeight = 20.sp)
      Spacer(Modifier.width(8.dp))
    }
    Text(
      text,
      color = if (enabled) FigmaUi.Primary else FigmaUi.Muted,
      fontSize = 14.sp,
      lineHeight = 20.sp,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun FigmaChip(text: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier
        .clip(RoundedCornerShape(9999.dp))
        .background(if (selected) FigmaUi.Green else FigmaUi.Background)
        .border(1.dp, if (selected) Color.Transparent else FigmaUi.Border, RoundedCornerShape(9999.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 17.dp, vertical = 9.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text,
      color = if (selected) FigmaUi.GreenText else FigmaUi.Body,
      fontSize = 14.sp,
      lineHeight = 20.sp,
      fontWeight = FontWeight.Medium,
      letterSpacing = 0.1.sp,
    )
  }
}

@Composable
private fun FigmaTextField(
  value: String,
  label: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.height(56.dp),
    label = { Text(label, color = FigmaUi.Primary, fontSize = 11.sp) },
    singleLine = true,
    shape = RoundedCornerShape(8.dp),
    textStyle = androidx.compose.ui.text.TextStyle(color = FigmaUi.Body, fontSize = 16.sp),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedBorderColor = FigmaUi.Muted,
        unfocusedBorderColor = FigmaUi.Muted,
        cursorColor = FigmaUi.Primary,
        focusedContainerColor = FigmaUi.Background,
        unfocusedContainerColor = FigmaUi.Background,
      ),
  )
}

@Composable
fun PrivacyBadge(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Row(
      modifier =
        Modifier
          .clip(RoundedCornerShape(9999.dp))
          .background(if (compact) FigmaUi.Green else FigmaUi.Green.copy(alpha = 0.65f))
          .border(1.dp, FigmaUi.GreenText.copy(alpha = 0.1f), RoundedCornerShape(9999.dp))
          .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 4.dp else 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(
        painter = painterResource(R.drawable.splash_privacy_icon),
        contentDescription = null,
        modifier = Modifier.width(if (compact) 12.dp else 13.dp).height(if (compact) 15.dp else 16.dp),
      )
      Text(
        text,
        color = if (compact) FigmaUi.GreenDark else FigmaUi.GreenText,
        fontSize = if (compact) 14.sp else 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
      )
    }
  }
}

@Composable
fun StatCard(label: String, value: String, tone: Color, modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(12.dp))
        .background(FigmaUi.Surface)
        .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tone), contentAlignment = Alignment.Center) {
      Text("•", color = if (tone == FigmaUi.Green) FigmaUi.GreenText else FigmaUi.Primary, fontSize = 22.sp)
    }
    Column {
      Text(label, color = FigmaUi.Body, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
      Text(value, color = FigmaUi.Ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
  }
}

@Composable
private fun BoxScope.BottomActionBar(content: @Composable () -> Unit) {
  Box(
    modifier =
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .background(Color.White.copy(alpha = 0.72f))
        .border(1.dp, FigmaUi.Border.copy(alpha = 0.3f))
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
  ) {
    content()
  }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
  FigmaCard(content = content)
}

@Composable
private fun LoadingCard(message: String, progress: Float? = null) {
  SectionCard {
    Text(message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Text("This stays on your device.", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(body)
    }
  }
}

private fun Float.roundToStep(): Int = ((this / 10f).toInt() * 10).coerceIn(10, 100)

private fun String.toMimeType(): String =
  when (this) {
    OutputFormat.JPEG.name -> OutputFormat.JPEG.mimeType
    OutputFormat.PNG.name -> OutputFormat.PNG.mimeType
    OutputFormat.WEBP.name -> OutputFormat.WEBP.mimeType
    else -> "image/*"
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
