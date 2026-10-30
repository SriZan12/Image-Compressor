package com.example.imagecompressor.ui.design

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import com.example.imagecompressor.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIconResourceTest {
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

  @Test @Config(sdk = [31, 35])
  fun shadedIconsRenderAsVectorsAtButtonAndTabletSizes() {
    val context = RuntimeEnvironment.getApplication()
    for ((id, label) in icons) {
      val drawable = checkNotNull(context.getDrawable(id))
      assertTrue("$label must remain a density-independent vector", drawable is VectorDrawable)
      for (size in listOf(24, 48, 192)) {
        val bitmap = render(drawable, size)
        val colors = IntArray(size * size)
        bitmap.getPixels(colors, 0, size, 0, 0, size, size)
        assertTrue("$label must retain its shading at $size px", colors.filter { Color.alpha(it) > 128 }.toSet().size > 8)
        assertEquals("$label has a transparent background", 0, Color.alpha(bitmap.getPixel(0, 0)))
        bitmap.recycle()
      }
    }
    if (RuntimeEnvironment.getApiLevel() == 35) captureFamily()
  }

  private val icons = listOf(
    R.drawable.ic_soft_shrink to "Shrink", R.drawable.ic_soft_recent to "Recent",
    R.drawable.ic_soft_settings to "Settings", R.drawable.ic_soft_system to "System",
    R.drawable.ic_soft_light to "Light", R.drawable.ic_soft_dark to "Dark",
    R.drawable.ic_soft_add_photo to "Add photo", R.drawable.ic_soft_delete to "Remove",
    R.drawable.ic_soft_save to "Save", R.drawable.ic_soft_share to "Share",
    R.drawable.ic_soft_compare to "Compare", R.drawable.ic_soft_lock to "Privacy",
  )

  private fun captureFamily() {
    val bitmap = Bitmap.createBitmap(768, 684, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(250, 250, 247))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(27, 33, 31); textSize = 17f; textAlign = Paint.Align.CENTER }
    icons.forEachIndexed { index, (id, label) ->
      val x = (index % 4) * 192; val y = (index / 4) * 228
      val drawable = checkNotNull(RuntimeEnvironment.getApplication().getDrawable(id))
      drawable.setBounds(x + 32, y + 8, x + 160, y + 136)
      drawable.draw(canvas)
      drawable.setBounds(x + 84, y + 150, x + 108, y + 174)
      drawable.draw(canvas)
      canvas.drawText(label, x + 96f, y + 205f, paint)
    }
    val file = File(System.getProperty("shrink.screenshots"), "icons-vector-family.png").apply { parentFile?.mkdirs() }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
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
