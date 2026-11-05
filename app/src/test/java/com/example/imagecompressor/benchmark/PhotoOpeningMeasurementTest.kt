package com.example.imagecompressor.benchmark

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import coil.size.Precision
import com.example.imagecompressor.util.ImageCompressor
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.After
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Opt-in host measurements. Cold means empty Coil memory cache, not cold OS/provider storage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoOpeningMeasurementTest {
  // This probe does not render Compose; do not block on Robolectric's paused main Looper.
  @Before fun hostMainDispatcher() { Dispatchers.setMain(Dispatchers.Unconfined) }
  @After fun restoreMainDispatcher() { Dispatchers.resetMain() }
  @Test fun recordInspectionAndRealCoilLoads() = runBlocking {
    assumeTrue(System.getProperty("shrink.preview.measure") == "true")
    val context = RuntimeEnvironment.getApplication()
    val provider = checkNotNull(context.packageManager.resolveContentProvider(
      "${context.packageName}.fileprovider", PackageManager.GET_META_DATA))
    Robolectric.buildContentProvider(FileProvider::class.java).create(provider)
    val directory = File(context.cacheDir, "compressed/preview-measurement").apply { mkdirs() }
    fun fixture(name: String) = File(directory, name).apply {
      checkNotNull(PhotoOpeningMeasurementTest::class.java.classLoader?.getResourceAsStream("compression/$name")).use { source ->
        outputStream().use(source::copyTo)
      }
    }
    val png = fixture("astronaut.png")
    val large = fixture("large-12mp.png")
    val jpeg = File(directory, "large-12mp.jpg")
    checkNotNull(BitmapFactory.decodeFile(large.path)).let { bitmap ->
      try { jpeg.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) } }
      finally { bitmap.recycle() }
    }
    val files = listOf(png, large, jpeg, fixture("large-48mp.png"))
    val loader = ImageLoader.Builder(context).build()
    val processor = ImageCompressor(context)
    val rows = JSONArray()
    try {
      for (file in files) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val request = ImageRequest.Builder(context).data(uri).size(744, 480)
          .scale(Scale.FIT).precision(Precision.INEXACT).allowHardware(false).build()
        val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        // Warm native libraries/JIT once. Each measured cold request still clears Coil's bitmap cache.
        processor.inspect(uri)
        check(loader.execute(request) is SuccessResult)
        repeat(7) { iteration ->
          loader.memoryCache!!.clear()
          val started = System.nanoTime()
          val info = processor.inspect(uri)
          val inspected = System.nanoTime()
          val cold = loader.execute(request) as SuccessResult
          val loaded = System.nanoTime()
          val warm = loader.execute(request) as SuccessResult
          val finished = System.nanoTime()
          assertEquals(file.length(), info.sizeBytes)
          assertEquals(DataSource.DISK, cold.dataSource)
          assertEquals(DataSource.MEMORY_CACHE, warm.dataSource)
          val bitmap = (cold.drawable as BitmapDrawable).bitmap
          assertTrue("Preview must be sampled, not a full camera bitmap", bitmap.width.toLong() * bitmap.height <= 744L * 480 * 4)
          rows.put(JSONObject().put("file", file.name).put("sourceBytes", file.length()).put("sha256", sha)
            .put("sourceWidth", info.width).put("sourceHeight", info.height).put("iteration", iteration + 1)
            .put("inspectMs", (inspected - started) / 1_000_000.0)
            .put("coldCoilMs", (loaded - inspected) / 1_000_000.0)
            .put("warmCoilMs", (finished - loaded) / 1_000_000.0)
            .put("inspectAndColdCoilMs", (loaded - started) / 1_000_000.0)
            .put("decodedWidth", bitmap.width).put("decodedHeight", bitmap.height)
            .put("decodedBytes", bitmap.allocationByteCount).put("coldSource", cold.dataSource.name)
            .put("warmSource", warm.dataSource.name))
        }
      }
      val label = checkNotNull(System.getProperty("shrink.preview.label"))
      require(label.matches(Regex("[A-Za-z0-9_-]{1,60}")))
      val output = File(System.getProperty("shrink.preview.reports"), "$label.json").apply { parentFile!!.mkdirs() }
      val report = JSONObject().put("schemaVersion", 1).put("label", label)
        .put("environment", "Robolectric API35 native graphics; host JVM, not a phone")
        .put("clock", "System.nanoTime").put("complete", true).put("measurements", rows)
        .put("coldDefinition", "Coil memory cleared after one code warmup; OS/provider caches not cleared")
        .put("excludes", "picker, batch/Room initialization, Compose layout, first drawn frame, GPU, network provider download")
        .put("request", "744x480px, FIT/INEXACT, software bitmap, shared loader; local content URI")
      output.writeText(report.toString(2))
    } finally { loader.shutdown(); directory.deleteRecursively() }
  }
}
