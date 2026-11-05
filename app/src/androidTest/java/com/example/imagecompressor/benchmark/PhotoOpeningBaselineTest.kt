package com.example.imagecompressor.benchmark

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import coil.size.Precision
import com.example.imagecompressor.util.ImageCompressor
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device timings for inspection and bitmap availability, not picker-to-first-frame latency. */
@RunWith(AndroidJUnit4::class)
class PhotoOpeningBaselineTest {
  @Test fun recordInspectionAndRealCoilLoads() = runBlocking {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val arguments = InstrumentationRegistry.getArguments()
    val runId = arguments.getString("previewRunId", "opening-${UUID.randomUUID()}")!!
    require(runId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
    val iterations = arguments.getString("previewIterations", "1")!!.toInt()
    require(iterations in 1..30)
    val inputs = File(context.cacheDir, "compressed/preview-$runId")
    val output = File(context.filesDir, "preview-performance/$runId.json")
    check(!inputs.exists()) { "Use a fresh run ID; fixture directory already exists" }
    check(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs())
    check(output.createNewFile()) { "Never overwrite an earlier report; use a fresh run ID" }
    val rows = JSONArray()
    val report = JSONObject().put("schemaVersion", 1).put("runId", runId)
      .put("iterations", iterations).put("expectedMeasurements", 4 * iterations)
      .put("sourceRevision", arguments.getString("previewRevision", "unspecified"))
      .put("compressorSha256", arguments.getString("previewCompressorSha256", "unspecified"))
      .put("startedAtEpochMs", System.currentTimeMillis()).put("complete", false)
      .put("clock", "SystemClock.elapsedRealtimeNanos")
      .put("device", JSONObject().put("sdk", Build.VERSION.SDK_INT)
        .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
        .put("fingerprint", Build.FINGERPRINT).put("hardware", Build.HARDWARE)
        .put("isEmulator", Build.HARDWARE in setOf("ranchu", "goldfish") ||
          Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk", ignoreCase = true) ||
          Build.PRODUCT.contains("emulator", ignoreCase = true))
        .put("vmHeapLimitBytes", Runtime.getRuntime().maxMemory()))
      .put("buildType", if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) "debug" else "release")
      .put("coldDefinition", "Coil memory cleared after one code warmup; OS/provider caches not cleared")
      .put("request", "744x480px, FIT/INEXACT, software bitmap, shared loader; local content URI")
      .put("excludes", "fixture preparation, picker, batch/Room initialization, Compose layout, first drawn frame, GPU, network provider download")
      .put("measurements", rows)
    output.writeText(report.toString(2))
    var loader: ImageLoader? = null
    var ownsInputs = false
    try {
      check(inputs.mkdirs()) { "Unable to create fixture directory" }
      ownsInputs = true
      fun fixture(name: String) = File(inputs, name).apply {
        instrumentation.context.assets.open("compression/$name").use { source ->
          outputStream().use(source::copyTo)
        }
      }
      val png = fixture("astronaut.png")
      val large = fixture("large-12mp.png")
      val jpeg = File(inputs, "large-12mp.jpg")
      checkNotNull(BitmapFactory.decodeFile(large.path)).let { bitmap ->
        try { jpeg.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) } }
        finally { bitmap.recycle() }
      }
      val files = listOf(png, large, jpeg, fixture("large-48mp.png"))
      val imageLoader = ImageLoader.Builder(context).build().also { loader = it }
      val processor = ImageCompressor(context)
      for (file in files) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val request = ImageRequest.Builder(context).data(uri).size(744, 480)
          .scale(Scale.FIT).precision(Precision.INEXACT).allowHardware(false).build()
        val hash = sha256(file)
        processor.inspect(uri)
        check(imageLoader.execute(request) is SuccessResult) { "Warmup failed for ${file.name}" }
        repeat(iterations) { iteration ->
          checkNotNull(imageLoader.memoryCache).clear()
          val started = SystemClock.elapsedRealtimeNanos()
          val info = processor.inspect(uri)
          val inspected = SystemClock.elapsedRealtimeNanos()
          val cold = imageLoader.execute(request) as SuccessResult
          val loaded = SystemClock.elapsedRealtimeNanos()
          val warm = imageLoader.execute(request) as SuccessResult
          val finished = SystemClock.elapsedRealtimeNanos()
          val bitmap = (cold.drawable as BitmapDrawable).bitmap
          assertEquals(file.length(), info.sizeBytes)
          assertEquals(DataSource.DISK, cold.dataSource)
          assertEquals(DataSource.MEMORY_CACHE, warm.dataSource)
          assertTrue("Preview must be sampled, not a full camera bitmap", bitmap.width.toLong() * bitmap.height <= 744L * 480 * 4)
          rows.put(JSONObject().put("file", file.name).put("sourceBytes", file.length()).put("sha256", hash)
            .put("sourceWidth", info.width).put("sourceHeight", info.height).put("iteration", iteration + 1)
            .put("inspectMs", (inspected - started) / 1_000_000.0)
            .put("coldCoilMs", (loaded - inspected) / 1_000_000.0)
            .put("warmCoilMs", (finished - loaded) / 1_000_000.0)
            .put("inspectAndColdCoilMs", (loaded - started) / 1_000_000.0)
            .put("decodedWidth", bitmap.width).put("decodedHeight", bitmap.height)
            .put("decodedBytes", bitmap.allocationByteCount).put("coldSource", cold.dataSource.name)
            .put("warmSource", warm.dataSource.name))
          output.writeText(report.toString(2))
        }
        assertEquals("Inspection/loading must not change the fixture", hash, sha256(file))
      }
      assertEquals(4 * iterations, rows.length())
      report.put("complete", true).put("finishedAtEpochMs", System.currentTimeMillis())
      output.writeText(report.toString(2))
    } catch (failure: Throwable) {
      report.put("complete", false).put("failure", failure.javaClass.simpleName)
        .put("failureMessage", failure.message).put("finishedAtEpochMs", System.currentTimeMillis())
      runCatching { output.writeText(report.toString(2)) }.exceptionOrNull()?.let(failure::addSuppressed)
      throw failure
    } finally {
      loader?.shutdown()
      if (ownsInputs) inputs.deleteRecursively()
    }
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(65536)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}
