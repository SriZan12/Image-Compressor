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
import com.example.imagecompressor.play.review.ReviewTracker
import com.example.imagecompressor.play.review.SharedPreferencesReviewStore
import com.example.imagecompressor.play.update.UpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.UUID

class ImageCompressorApplication : Application() {
  val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
  // A session lasts for this app process, including rotations and activity recreation.
  val playSessionId: String = UUID.randomUUID().toString()
  val updateCoordinator = UpdateCoordinator()
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  val reviewTracker = applicationScope.async {
    ReviewTracker(SharedPreferencesReviewStore(application))
  }
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
