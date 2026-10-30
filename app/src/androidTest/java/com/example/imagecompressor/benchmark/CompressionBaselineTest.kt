package com.example.imagecompressor.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.OutputFormat
import com.example.imagecompressor.data.model.ResizeMode
import com.example.imagecompressor.util.ImageCompressor
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Characterizes the real Android encoder; quality findings are data, not passing assertions. */
@RunWith(AndroidJUnit4::class)
class CompressionBaselineTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val arguments = InstrumentationRegistry.getArguments()

  private data class Case(
    val id: String,
    val asset: String,
    val settings: CompressionSettings = CompressionSettings(),
    val orientation: Int? = null,
    val expectRejection: Boolean = false,
  )

  @Test
  fun captureCompressionBaseline() {
    val profile = arguments.getString("baselineProfile", "smoke")!!
    require(profile in setOf("smoke", "full", "performance"))
    val runId = arguments.getString("baselineRunId", "local-${System.currentTimeMillis()}")!!
    require(runId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
    val iterations = arguments.getString("baselineIterations", "1")!!.toInt()
    require(iterations in 1..100)
    val inputs = File(context.cacheDir, "compressed/baseline-$runId")
    val reportFile = File(context.filesDir, "compression-benchmark/$runId.json")
    check(!reportFile.exists()) { "Never overwrite an earlier report" }
    check(!inputs.exists() && inputs.mkdirs()) { "Cannot create fresh fixture directory; use a new run ID" }
    reportFile.parentFile!!.mkdirs()
    val results = JSONArray()
    val failures = mutableListOf<String>()
    val cases = cases(profile)
    val report = JSONObject()
      .put("schemaVersion", 1)
      .put("runId", runId)
      .put("profile", profile)
      .put("iterations", iterations)
      .put("expectedMeasurements", cases.size * iterations)
      .put("sourceRevision", arguments.getString("baselineRevision", "unspecified"))
      .put("compressorSha256", arguments.getString("baselineCompressorSha256", "unspecified"))
      .put("startedAtEpochMs", System.currentTimeMillis())
      .put("complete", false)
      .put("device", JSONObject()
        .put("sdk", Build.VERSION.SDK_INT)
        .put("model", Build.MODEL)
        .put("manufacturer", Build.MANUFACTURER)
        .put("fingerprint", Build.FINGERPRINT)
        .put("hardware", Build.HARDWARE)
        .put("isEmulator", Build.HARDWARE in setOf("ranchu", "goldfish") ||
          Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk", ignoreCase = true) ||
          Build.PRODUCT.contains("emulator", ignoreCase = true))
        .put("vmHeapLimitBytes", Runtime.getRuntime().maxMemory()))
      .put("measurement", JSONObject()
        .put("buildType", "debug")
        .put("includes", "inspect, decode, resize, encode, metadata, output file write")
        .put("excludes", "fixture preparation, UI, gallery save, quality analysis, Room history")
        .put("memory", "Whole-process PSS sampled every 100ms; short peaks can be missed")
        .put("quality", "RGB PSNR after white compositing at at most 256px; not perceptual sign-off")
        .put("warmups", 1))
      .put("results", results)

    try {
      reportFile.writeText(report.toString(2))
      val compressor = ImageCompressor(context)
      val warmup = Case("warmup", "chart.png", CompressionSettings(resizeMode = ResizeMode.PERCENTAGE, resizePercent = 10))
      val warmupInput = prepareInput(warmup, inputs)
      val warmed = runBlocking { compressor.compress(compressor.inspect(uri(warmupInput)), warmup.settings) }
      File(warmed.outputFilePath).delete()
      warmupInput.delete()
      reportFile.writeText(report.toString(2))
      repeat(iterations) { iteration ->
        for (case in cases) {
          val result = measure(case, inputs, compressor).put("iteration", iteration + 1)
          results.put(result)
          if (result.getBoolean("unexpectedFailure")) failures += case.id
          reportFile.writeText(report.toString(2))
        }
      }
      report.put("complete", true)
        .put("finishedAtEpochMs", System.currentTimeMillis())
        .put("unexpectedFailures", JSONArray(failures))
      reportFile.writeText(report.toString(2))
      assertEquals("Every requested measurement must run", cases.size * iterations, results.length())
      assertTrue("Harness/encoding failures: $failures; inspect $runId.json", failures.isEmpty())
    } finally {
      inputs.deleteRecursively()
    }
  }

  private fun cases(profile: String): List<Case> {
    if (profile == "performance") return listOf(
      Case("large-12mp-jpeg", "large-12mp.png"),
      Case("large-12mp-target-1mb", "large-12mp.png", CompressionSettings(targetBytes = 1_000_000)),
    )
    val basic = listOf(
      Case("photo-jpeg-q80", "astronaut.png"),
      Case("photo-webp-q80", "astronaut.png", CompressionSettings(outputFormat = OutputFormat.WEBP)),
      Case("chart-png", "chart.png", CompressionSettings(outputFormat = OutputFormat.PNG)),
      Case("alpha-png", "alpha.png", CompressionSettings(outputFormat = OutputFormat.PNG)),
      Case("alpha-jpeg-white", "alpha.png"),
      Case("photo-target-50kib", "astronaut.png", CompressionSettings(targetBytes = 50L * 1024)),
      Case("noise-target-10kib", "noise.png", CompressionSettings(targetBytes = 10L * 1024)),
      Case("impossible-png-target", "chart.png", CompressionSettings(targetBytes = 1, outputFormat = OutputFormat.PNG)),
      Case("corrupt-input", "corrupt.png", expectRejection = true),
      Case("truncated-input", "truncated.png", expectRejection = true),
    )
    val orientations = (1..8).map { orientation ->
      Case("exif-$orientation", "chart.png", CompressionSettings(outputFormat = OutputFormat.PNG), orientation)
    }
    val large = if (profile == "full") listOf(
      Case("large-12mp-jpeg", "large-12mp.png"),
      Case("large-48mp-jpeg", "large-48mp.png"),
    ) else emptyList()
    return basic + orientations + large
  }

  private fun prepareInput(case: Case, directory: File): File {
    val file = File(directory, "${case.id}.${if (case.orientation != null) "jpg" else "png"}")
    instrumentation.context.assets.open("compression/${case.asset}").use { input ->
      if (case.orientation == null) {
        file.outputStream().use { input.copyTo(it) }
      } else {
        val bitmap = checkNotNull(BitmapFactory.decodeStream(input))
        try {
          file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
          bitmap.recycle()
        }
        ExifInterface(file).apply {
          setAttribute(ExifInterface.TAG_ORIENTATION, case.orientation.toString())
          saveAttributes()
        }
      }
    }
    return file
  }

  private fun measure(case: Case, directory: File, compressor: ImageCompressor): JSONObject {
    val input = prepareInput(case, directory)
    val originalHash = sha256(input)
    val memory = PssSampler()
    var compressed: CompressedImage? = null
    var error: Exception? = null
    memory.start()
    val start = SystemClock.elapsedRealtimeNanos()
    var elapsedMs: Double
    try {
      compressed = runBlocking { compressor.compress(compressor.inspect(uri(input)), case.settings) }
    } catch (exception: Exception) {
      error = exception
    } finally {
      elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
      memory.stop()
    }
    val sourceUnchanged = sha256(input) == originalHash
    val row = JSONObject()
      .put("id", case.id)
      .put("fixture", case.asset)
      .put("inputSha256", originalHash)
      .put("inputBytes", input.length())
      .put("sourceUnchanged", sourceUnchanged)
      .put("format", case.settings.outputFormat.name)
      .put("qualitySetting", case.settings.quality)
      .put("targetBytes", case.settings.targetBytes ?: JSONObject.NULL)
      .put("orientation", case.orientation ?: JSONObject.NULL)
      .put("elapsedMs", elapsedMs)
      .put("baselinePssKiB", memory.baseline)
      .put("sampledPeakPssKiB", memory.peak.get())
      .put("expectedRejection", case.expectRejection)
      .put("unexpectedFailure", !sourceUnchanged || (error != null) != case.expectRejection)

    try {
      if (error != null) {
        row.put("status", if (case.expectRejection) "expected_rejection" else "error")
          .put("errorType", error.javaClass.simpleName)
          .put("error", error.message ?: "No message")
      } else {
        val output = checkNotNull(compressed)
        val outputFile = File(output.outputFilePath)
        val bounds = bounds(outputFile)
        val decodable = bounds.outWidth > 0 && bounds.outHeight > 0
        val metadataMatches = output.width == bounds.outWidth && output.height == bounds.outHeight &&
          output.compressedSizeBytes == outputFile.length()
        row.put("status", "encoded")
          .put("outputBytes", outputFile.length())
          .put("outputWidth", bounds.outWidth)
          .put("outputHeight", bounds.outHeight)
          .put("outputMime", bounds.outMimeType)
          .put("outputSha256", sha256(outputFile))
          .put("decodable", decodable)
          .put("metadataMatches", metadataMatches)
          .put("actualReductionPercent", (1.0 - outputFile.length().toDouble() / input.length()) * 100)
          .put("targetMet", case.settings.targetBytes?.let { outputFile.length() <= it } ?: JSONObject.NULL)
          .put("reportedTargetStatus", output.targetSizeStatus.name)
          .put("encodingAttempts", output.encodingAttempts)
          .put("encodingQuality", output.encodingQuality ?: JSONObject.NULL)
          .put("adjustments", JSONArray(output.adjustments.map { it.name }))
          .put("quality", quality(input, outputFile, case))
        if (!decodable || !metadataMatches || bounds.outMimeType != case.settings.outputFormat.mimeType) {
          row.put("unexpectedFailure", true)
        }
      }
      return row
    } finally {
      compressed?.let { File(it.outputFilePath).delete() }
      input.delete()
    }
  }

  private fun quality(input: File, output: File, case: Case): JSONObject {
    val inputBounds = bounds(input)
    val expectedWidth = if (case.orientation in 5..8) inputBounds.outHeight else inputBounds.outWidth
    val expectedHeight = if (case.orientation in 5..8) inputBounds.outWidth else inputBounds.outHeight
    val outputBounds = bounds(output)
    val logicalDimensionsMatch = expectedWidth == outputBounds.outWidth && expectedHeight == outputBounds.outHeight
    val referenceRaw = sampled(input)
    val matrix = Matrix().apply {
      when (case.orientation) {
        2 -> setScale(-1f, 1f)
        3 -> setRotate(180f)
        4 -> setScale(1f, -1f)
        5 -> { setRotate(90f); postScale(-1f, 1f) }
        6 -> setRotate(90f)
        7 -> { setRotate(-90f); postScale(-1f, 1f) }
        8 -> setRotate(-90f)
      }
    }
    val oriented = Bitmap.createBitmap(referenceRaw, 0, 0, referenceRaw.width, referenceRaw.height, matrix, false)
    val outputRaw = sampled(output)
    val width = minOf(256, outputBounds.outWidth)
    val height = max(1, (outputBounds.outHeight.toDouble() * width / outputBounds.outWidth).roundToInt())
    val reference = Bitmap.createScaledBitmap(oriented, width, height, true)
    val actual = Bitmap.createScaledBitmap(outputRaw, width, height, true)
    try {
      val left = IntArray(width * height)
      val right = IntArray(width * height)
      reference.getPixels(left, 0, width, 0, 0, width, height)
      actual.getPixels(right, 0, width, 0, 0, width, height)
      var squaredError = 0.0
      var alphaError = 0L
      val channels = intArrayOf(16, 8, 0)
      for (index in left.indices) {
        val a = Color.alpha(left[index])
        val b = Color.alpha(right[index])
        alphaError += kotlin.math.abs(a - b)
        for (shift in channels) {
          val x = ((left[index] shr shift and 255) * a + 255 * (255 - a)) / 255.0
          val y = ((right[index] shr shift and 255) * b + 255 * (255 - b)) / 255.0
          squaredError += (x - y) * (x - y)
        }
      }
      val mse = squaredError / (left.size * 3)
      return JSONObject()
        .put("logicalDimensionsPreserved", logicalDimensionsMatch)
        .put("comparisonWidth", width)
        .put("comparisonHeight", height)
        .put("rgbPsnrDb", if (mse == 0.0) JSONObject.NULL else 10 * log10(255.0 * 255 / mse))
        .put("compositedPixelsExact", mse == 0.0)
        .put("meanAlphaError", alphaError.toDouble() / left.size)
        .put("orientationPixelsCorrect", if (case.orientation == null) JSONObject.NULL else
          logicalDimensionsMatch && left.contentEquals(right))
    } finally {
      listOf(referenceRaw, oriented, outputRaw, reference, actual).distinct().forEach { it.recycle() }
    }
  }

  private fun bounds(file: File) = BitmapFactory.Options().apply {
    inJustDecodeBounds = true
    BitmapFactory.decodeFile(file.absolutePath, this)
  }

  private fun sampled(file: File): Bitmap {
    val info = bounds(file)
    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
    while (max(info.outWidth, info.outHeight) / options.inSampleSize.coerceAtLeast(1) > 512) {
      options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
    }
    return checkNotNull(BitmapFactory.decodeFile(file.absolutePath, options))
  }

  private fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(8192)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private class PssSampler {
    val baseline = Debug.getPss()
    val peak = AtomicLong(baseline)
    private val running = AtomicBoolean(true)
    private val thread = Thread({
      while (running.get()) {
        peak.accumulateAndGet(Debug.getPss(), ::maxOf)
        try { Thread.sleep(100) } catch (_: InterruptedException) { break }
      }
    }, "baseline-pss").apply { isDaemon = true }

    fun start() = thread.start()
    fun stop() {
      peak.accumulateAndGet(Debug.getPss(), ::maxOf)
      running.set(false)
      thread.interrupt()
      thread.join(1000)
    }
  }
}
