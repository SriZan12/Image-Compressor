package com.example.imagecompressor.util.compression

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32

/** Validate containers before native allocation. MIME/extension supplied by a provider is not trusted. */
internal enum class ImageContainer(val mimeType: String, val label: String) {
  JPEG("image/jpeg", "JPEG"), PNG("image/png", "PNG"), WEBP("image/webp", "WEBP");

  companion object {
    fun validate(file: File, checkpoint: () -> Unit = {}): ImageContainer =
      file.inputStream().use { validate(it, file.length(), checkpoint) }

    /** Validates the entire container without taking ownership of the supplied source stream. */
    fun validate(source: InputStream, length: Long, checkpoint: () -> Unit = {}): ImageContainer {
      val input = BufferedInputStream(source)
      val header = ByteArray(12)
      input.mark(header.size)
      var count = 0
      while (count < header.size) {
        checkpoint()
        val read = input.read(header, count, header.size - count)
        if (read < 0) break
        count += read
      }
      input.reset()
      return try {
        when {
          count >= 3 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte() -> {
            jpeg(DataInputStream(input), checkpoint); JPEG
          }
          count >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) -> {
            png(DataInputStream(input), length, checkpoint); PNG
          }
          count == 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> {
            webp(DataInputStream(input), length, checkpoint); WEBP
          }
          else -> throw IOException("Choose a still JPEG, PNG or WebP image. This format is not supported.")
        }
      } catch (exception: EOFException) {
        throw IOException("The image is incomplete or damaged.", exception)
      }
    }

    private fun jpeg(input: DataInputStream, checkpoint: () -> Unit) {
      check(input.readUnsignedShort() == 0xffd8)
      var inScan = false
      var sawScan = false
      var reads = 0
      fun byte(): Int {
        if (++reads % 65536 == 0) checkpoint()
        return input.readUnsignedByte()
      }
      while (true) {
        checkpoint()
        var marker: Int
        if (inScan) {
          do {
            while (byte() != 0xff) { /* Entropy-coded bytes; 0xff00 is escaped. */ }
            do { marker = byte() } while (marker == 0xff)
          } while (marker == 0 || marker in 0xd0..0xd7)
          inScan = false
        } else {
          if (byte() != 0xff) throw IOException("The JPEG structure is damaged.")
          do { marker = byte() } while (marker == 0xff)
        }
        if (marker == 0xd9) {
          if (!sawScan) throw IOException("The JPEG has no image data.")
          return // JPEG permits trailing data, including auxiliary images in an MPF container.
        }
        if (marker == 0xd8 || marker == 0 || marker in 0xd0..0xd7) throw IOException("The JPEG structure is damaged.")
        if (marker == 1) continue // Standalone TEM marker.
        val length = input.readUnsignedShort() - 2
        if (length < 0) throw IOException("The JPEG structure is damaged.")
        var remaining = length
        while (remaining > 0) {
          val skipped = input.skipBytes(remaining)
          if (skipped == 0) throw EOFException()
          remaining -= skipped
        }
        if (marker == 0xda) { inScan = true; sawScan = true }
      }
    }

    private fun png(input: DataInputStream, totalLength: Long, checkpoint: () -> Unit) {
      input.readLong() // Signature was checked above.
      var remaining = totalLength - 8
      var first = true
      var sawData = false
      val buffer = ByteArray(65536)
      while (remaining >= 12) {
        checkpoint()
        val length = input.readInt().toLong() and 0xffffffffL
        val typeBytes = ByteArray(4).also(input::readFully)
        val type = String(typeBytes, Charsets.US_ASCII)
        if (length > remaining - 12 || (first && (type != "IHDR" || length != 13L))) throw EOFException()
        if (type == "acTL" || type == "fcTL" || type == "fdAT") throw IOException("Animated PNG images are not supported. Choose a still image.")
        first = false
        val crc = CRC32().apply { update(typeBytes) }
        var unread = length
        while (unread > 0) {
          checkpoint()
          val size = minOf(unread, buffer.size.toLong()).toInt()
          input.readFully(buffer, 0, size)
          crc.update(buffer, 0, size)
          unread -= size
        }
        val expected = input.readInt().toLong() and 0xffffffffL
        if (crc.value != expected) throw IOException("The PNG checksum is invalid; the image is damaged.")
        remaining -= length + 12
        if (type == "IDAT") sawData = true
        if (type == "IEND") {
          if (length != 0L || !sawData || remaining != 0L) throw IOException("The PNG structure is damaged.")
          return
        }
      }
      throw EOFException()
    }

    private fun webp(input: DataInputStream, length: Long, checkpoint: () -> Unit) {
      input.readInt() // RIFF signature.
      fun unsignedLittleInt() = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
      val end = unsignedLittleInt() + 8
      if (end != length) throw EOFException()
      input.readInt() // WEBP signature.
      var position = 12L
      var sawPixels = false
      while (position < end) {
        checkpoint()
        if (end - position < 8) throw EOFException()
        val type = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
        val chunkLength = unsignedLittleInt()
        val next = position + 8 + chunkLength + (chunkLength and 1)
        if (next > end) throw EOFException()
        val flags = if (type == "VP8X" && chunkLength > 0) input.readUnsignedByte() else null
        if (type == "ANIM" || type == "ANMF" ||
          (flags != null && flags and 2 != 0)) {
          throw IOException("Animated WebP images are not supported. Choose a still image.")
        }
        if (type == "VP8 " || type == "VP8L") sawPixels = true
        var unread = chunkLength + (chunkLength and 1) - if (flags != null) 1 else 0
        while (unread > 0) {
          checkpoint()
          val skipped = input.skip(unread)
          if (skipped > 0) unread -= skipped
          else { if (input.read() < 0) throw EOFException(); unread-- }
        }
        position = next
      }
      if (!sawPixels) throw IOException("The WebP has no image data.")
    }
  }
}
