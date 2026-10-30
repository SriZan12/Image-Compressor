package com.example.imagecompressor.ui.design

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.Coil
import coil.EventListener
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.imagecompressor.R
import com.example.imagecompressor.ui.components.AppArtwork
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIconResourceTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  @Test fun adaptiveForegroundAndThemedMarkFitTheLauncherSafeZone() {
    val context = RuntimeEnvironment.getApplication()
    val launcher = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
    val round = context.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable
    val mono = checkNotNull(launcher.monochrome)
    for (layer in listOf(launcher.foreground, round.foreground, mono)) {
      val bitmap = render(layer, 108)
      var visible = 0
      for (y in 0 until 108) for (x in 0 until 108) {
        val pixel = bitmap.getPixel(x, y)
        if (Color.alpha(pixel) > 32) {
          visible++
          val dx = x + .5 - 54; val dy = y + .5 - 54
          assertTrue("Visible logo pixel ($x, $y) leaves the 66dp safe circle", dx * dx + dy * dy <= 33.5 * 33.5)
        }
      }
      assertTrue("Launcher artwork must have a visible silhouette", visible > 350)
      assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
      bitmap.recycle()
    }
    val themed = AdaptiveIconDrawable(ColorDrawable(Color.rgb(211, 243, 235)),
      mono.mutate().apply { setTint(Color.rgb(0, 80, 71)) })
    captureLauncher(launcher, round, themed)
  }

  @Test fun bundledArtworkDecodesToTheRequestedSizeAndReusesMemoryCache() {
    val context = RuntimeEnvironment.getApplication()
    val successes = ConcurrentLinkedQueue<SuccessResult>()
    val loader = ImageLoader.Builder(context).eventListenerFactory {
      object : EventListener {
        override fun onSuccess(request: ImageRequest, result: SuccessResult) { successes.add(result) }
      }
    }.build()
    Coil.setImageLoader(loader)
    var asset by mutableIntStateOf(R.drawable.art_shrink)
    var visible by mutableStateOf(true)
    compose.setContent { if (visible) AppArtwork(asset, R.drawable.ic_shrink, 96.dp) }
    fun awaitArtwork() {
      compose.waitUntil(10_000) { compose.onAllNodesWithTag("artwork-ready").fetchSemanticsNodes().size == 1 }
    }
    try {
      for (nextAsset in listOf(R.drawable.art_shrink, R.drawable.art_recent, R.drawable.art_settings)) {
        compose.runOnIdle { asset = nextAsset }
        awaitArtwork()
        val result = successes.last()
        val bitmap = (result.drawable as BitmapDrawable).bitmap
        assertTrue("Navigation must not decode the full 1254px master", bitmap.width <= 96 && bitmap.height <= 96)
        assertTrue(bitmap.hasAlpha())
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("artwork-ready").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        awaitArtwork()
        val cached = successes.last()
        assertEquals(DataSource.MEMORY_CACHE, cached.dataSource)
      }
      assertEquals(6, successes.size)
    } finally { loader.shutdown(); Coil.reset() }
  }

  private fun render(drawable: Drawable, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(Canvas(bitmap))
    return bitmap
  }

  /** Actual Android adaptive layers, drawn through native Canvas at launcher and review sizes. */
  private fun captureLauncher(launcher: AdaptiveIconDrawable, round: AdaptiveIconDrawable, themed: AdaptiveIconDrawable) {
    val bitmap = Bitmap.createBitmap(768, 380, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(250, 250, 247))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(27, 33, 31); textSize = 19f; textAlign = Paint.Align.CENTER }
    listOf(launcher, round, themed).forEachIndexed { index, drawable ->
      val x = index * 256
      canvas.save()
      if (index == 1) canvas.clipPath(android.graphics.Path().apply { addCircle(x + 128f, 128f, 96f, android.graphics.Path.Direction.CW) })
      drawable.setBounds(x + 32, 32, x + 224, 224)
      drawable.draw(canvas)
      canvas.restore()
      canvas.drawText(listOf("Adaptive", "Round mask", "Themed")[index], x + 128f, 260f, paint)
      canvas.save()
      if (index == 1) canvas.clipPath(android.graphics.Path().apply { addCircle(x + 128f, 309f, 24f, android.graphics.Path.Direction.CW) })
      drawable.setBounds(x + 104, 285, x + 152, 333)
      drawable.draw(canvas)
      canvas.restore()
      canvas.drawText("48 px", x + 128f, 364f, paint)
    }
    val file = File(System.getProperty("shrink.screenshots"), "icons-launcher.png").apply { parentFile?.mkdirs() }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
  }
}
