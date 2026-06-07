package com.example.imagecompressor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.imagecompressor.R
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.theme.FigmaUi

@Composable
fun FigmaCard(
  modifier: Modifier = Modifier,
  shape: RoundedCornerShape = RoundedCornerShape(12.dp),
  padding: Dp = 16.dp,
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
fun FigmaChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
fun FigmaTextField(
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
    textStyle = TextStyle(color = FigmaUi.Body, fontSize = 16.sp),
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
fun BoxScope.BottomActionBar(content: @Composable () -> Unit) {
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
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
  FigmaCard(content = content)
}

@Composable
fun LoadingCard(message: String, progress: Float? = null) {
  SectionCard {
    Text(message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Text("This stays on your device.", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(body)
    }
  }
}

fun Float.roundToStep(): Int = ((this / 10f).toInt() * 10).coerceIn(10, 100)

fun String.toMimeType(): String =
  when (this) {
    OutputFormat.JPEG.name -> OutputFormat.JPEG.mimeType
    OutputFormat.PNG.name -> OutputFormat.PNG.mimeType
    OutputFormat.WEBP.name -> OutputFormat.WEBP.mimeType
    else -> "image/*"
  }
