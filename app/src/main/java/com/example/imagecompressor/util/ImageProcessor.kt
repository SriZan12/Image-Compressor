package com.example.imagecompressor.util

import android.net.Uri
import com.example.imagecompressor.data.model.CompressedImage
import com.example.imagecompressor.data.model.CompressionSettings
import com.example.imagecompressor.data.model.SelectedImage

interface ImageProcessor {
  suspend fun inspect(uri: Uri): SelectedImage
  suspend fun compress(image: SelectedImage, settings: CompressionSettings): CompressedImage
}
