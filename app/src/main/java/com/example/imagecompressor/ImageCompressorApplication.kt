package com.example.imagecompressor

import android.app.Application
import androidx.room.Room
import com.example.imagecompressor.data.DefaultImageRepository
import com.example.imagecompressor.data.ImageRepository
import com.example.imagecompressor.data.local.ImageCompressorDatabase
import com.example.imagecompressor.data.preferences.AppPreferencesRepository
import com.example.imagecompressor.data.preferences.DefaultAppPreferencesRepository
import com.example.imagecompressor.util.GallerySaver
import com.example.imagecompressor.util.ImageCompressor

class ImageCompressorApplication : Application() {
  val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
  private val database =
    Room.databaseBuilder(
        application,
        ImageCompressorDatabase::class.java,
        "image-compressor.db",
      )
      .fallbackToDestructiveMigration(false)
      .build()

  val preferencesRepository: AppPreferencesRepository = DefaultAppPreferencesRepository(application)

  val imageRepository: ImageRepository =
    DefaultImageRepository(
      context = application,
      historyDao = database.historyDao(),
      imageCompressor = ImageCompressor(application),
      gallerySaver = GallerySaver(application),
    )
}
