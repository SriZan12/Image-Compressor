package com.example.imagecompressor.util.compression

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageContainerTest {
  @get:Rule val folder = TemporaryFolder()
  private fun file(bytes: ByteArray): File = folder.newFile().apply { writeBytes(bytes) }
  private fun fixture(name: String) = checkNotNull(javaClass.classLoader?.getResourceAsStream("compression/$name")).use { it.readBytes() }
  private fun pngChunk(type: String, data: ByteArray) = ByteArrayOutputStream().apply {
    DataOutputStream(this).apply {
      writeInt(data.size)
      val tag = type.toByteArray(Charsets.US_ASCII)
      write(tag); write(data)
      writeInt(CRC32().apply { update(tag); update(data) }.value.toInt())
    }
  }.toByteArray()
  private fun webp(type: String, data: ByteArray): ByteArray {
    val chunk = type.toByteArray() + littleInt(data.size) + data + if (data.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
    return "RIFF".toByteArray() + littleInt(4 + chunk.size) + "WEBP".toByteArray() + chunk
  }
  private fun littleInt(value: Int) = ByteArray(4) { (value shr (8 * it)).toByte() }

  @Test fun validatesRealPngFixturesAndRejectsDamage() {
    for (name in listOf("astronaut.png", "chart.png", "alpha.png", "noise.png", "large-48mp.png")) {
      assertEquals(ImageContainer.PNG, ImageContainer.validate(file(fixture(name))))
    }
    for (name in listOf("corrupt.png", "truncated.png")) {
      assertThrows(IOException::class.java) { ImageContainer.validate(file(fixture(name))) }
    }
    val damaged = fixture("chart.png").apply { this[45] = (this[45].toInt() xor 1).toByte() }
    assertThrows(IOException::class.java) { ImageContainer.validate(file(damaged)) }
  }

  @Test fun rejectsAnimatedPngAndWebpInsteadOfDroppingFrames() {
    val png = fixture("alpha.png")
    val animated = png.copyOfRange(0, 33) + pngChunk("acTL", ByteArray(8)) + png.copyOfRange(33, png.size)
    assertTrue(assertThrows(IOException::class.java) { ImageContainer.validate(file(animated)) }.message!!.contains("Animated"))
    for (bytes in listOf(webp("ANIM", ByteArray(6)), webp("VP8X", byteArrayOf(2) + ByteArray(9)))) {
      assertTrue(assertThrows(IOException::class.java) { ImageContainer.validate(file(bytes)) }.message!!.contains("Animated"))
    }
  }

  @Test fun rejectsOversizedMetadataBeforeReadingItsPayload() {
    val length = (CompressionPolicy.MAX_METADATA_BYTES + 1).toInt()
    // Only headers are supplied. A payload read would fail as truncated, not report the cap.
    for (type in listOf("eXIf", "iTXt", "iCCP", "tEXt", "zTXt")) {
      val prefix = fixture("chart.png").copyOfRange(0, 33) + ByteArrayOutputStream().apply {
        DataOutputStream(this).apply { writeInt(length); write(type.toByteArray()) }
      }.toByteArray()
      val failure = assertThrows(IOException::class.java) {
        ImageContainer.validate(prefix.inputStream(), prefix.size.toLong() + length + 4)
      }
      assertTrue("PNG $type must reject its declared size before allocation", failure.message!!.contains("embedded information"))
    }
    for (type in listOf("EXIF", "XMP ", "ICCP")) {
      val total = 20 + length + (length and 1)
      val prefix = "RIFF".toByteArray() + littleInt(total - 8) + "WEBP".toByteArray() + type.toByteArray() + littleInt(length)
      val failure = assertThrows(IOException::class.java) { ImageContainer.validate(prefix.inputStream(), total.toLong()) }
      assertTrue("WebP $type must reject its declared size before allocation", failure.message!!.contains("embedded information"))
    }
  }

  @Test fun repeatedMetadataSharesOneBudgetAndPixelPayloadDoesNotConsumeIt() {
    val png = fixture("chart.png")
    val half = ByteArray((CompressionPolicy.MAX_METADATA_BYTES / 2).toInt())
    val metadata = pngChunk("eXIf", half) + pngChunk("iTXt", half)
    val atLimit = png.copyOfRange(0, 33) + metadata + png.copyOfRange(33, png.size)
    assertEquals(ImageContainer.PNG, ImageContainer.validate(atLimit.inputStream(), atLimit.size.toLong()))
    val tooMuch = png.copyOfRange(0, 33) + metadata + pngChunk("tEXt", byteArrayOf(1)) + png.copyOfRange(33, png.size)
    assertTrue(assertThrows(IOException::class.java) {
      ImageContainer.validate(tooMuch.inputStream(), tooMuch.size.toLong())
    }.message!!.contains("embedded information"))
    // Container validation is independent of decoding: a large IDAT remains bounded by the
    // separate input-byte limit, not by the metadata budget.
    val largePixels = png.copyOfRange(0, 33) + pngChunk("IDAT", ByteArray(half.size * 2 + 1)) + pngChunk("IEND", byteArrayOf())
    assertEquals(ImageContainer.PNG, ImageContainer.validate(largePixels.inputStream(), largePixels.size.toLong()))
  }

  @Test fun manySmallJpegMetadataSegmentsCannotBypassTheAggregateCap() {
    val bytes = ByteArrayOutputStream().apply {
      val output = DataOutputStream(this)
      output.writeShort(0xffd8)
      repeat(65) {
        output.writeShort(0xffe1)
        output.writeShort(0xffff)
        output.write(ByteArray(0xfffd))
      }
      output.writeShort(0xffda); output.writeShort(2); output.writeByte(1); output.writeShort(0xffd9)
    }.toByteArray()
    assertTrue(assertThrows(IOException::class.java) {
      ImageContainer.validate(bytes.inputStream(), bytes.size.toLong())
    }.message!!.contains("embedded information"))
  }

  @Test fun rejectsUnboundedLengthsAndUnsupportedFormats() {
    for (bytes in listOf("GIF89a".toByteArray(), "untrusted".toByteArray(),
      "RIFF".toByteArray() + littleInt(Int.MAX_VALUE) + "WEBP".toByteArray())) {
      assertThrows(IOException::class.java) { ImageContainer.validate(file(bytes)) }
    }
    val png = fixture("chart.png").apply { for (i in 8..11) this[i] = 0xff.toByte() }
    assertThrows(IOException::class.java) { ImageContainer.validate(file(png)) }
  }

  @Test fun jpegHandlesEscapesRestartsAndMultipleScansButRequiresEndMarker() {
    // Container-only checks; a native decoder separately validates the encoded pixel payload.
    val bytes = intArrayOf(0xff,0xd8, 0xff,0xda,0,2, 1,0xff,0,2,0xff,0xd0,3,
      0xff,0xc4,0,2, 0xff,0xda,0,2,4, 0xff,0xd9).map { it.toByte() }.toByteArray()
    assertEquals(ImageContainer.JPEG, ImageContainer.validate(file(bytes)))
    assertThrows(IOException::class.java) { ImageContainer.validate(file(bytes.copyOf(bytes.size - 2))) }
    assertThrows(IOException::class.java) { ImageContainer.validate(file(byteArrayOf(-1, -40, -1, -39))) }
  }
}
