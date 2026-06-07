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
fun SplashScreen(modifier: Modifier = Modifier) {
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
