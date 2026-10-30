package com.example.imagecompressor.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.imagecompressor.data.local.ImageCompressorDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DatabaseMigrationTest {
  @Test fun versionOneRowsSurviveTheValidatedRoomMigration() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val name = "migration.db"
    val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
    val schema = javaClass.classLoader!!.getResourceAsStream("com.example.imagecompressor.data.local.ImageCompressorDatabase/1.json")!!
      .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
    SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
      val create = schema.getJSONArray("entities").getJSONObject(0).getString("createSql")
        .replace("\${TABLE_NAME}", "compression_history")
      db.execSQL(create)
      db.execSQL("INSERT INTO compression_history (id, createdAt, displayName, originalUri, compressedUri, originalSizeBytes, compressedSizeBytes, width, height, outputFormat, savedPath) VALUES (7, 123, 'preserved', 'content://source/7', 'content://cache/7', 1000, 500, 64, 32, 'JPEG', 'content://media/7')")
      db.version = 1
    }
    val database = Room.databaseBuilder(context, ImageCompressorDatabase::class.java, name)
      .addMigrations(ImageCompressorDatabase.MIGRATION_1_2).build()
    try {
      val row = database.historyDao().all().single()
      assertEquals(7L, row.id)
      assertEquals("preserved", row.displayName)
      assertEquals("content://media/7", row.savedPath)
      assertEquals(1000L, row.originalSizeBytes)
      assertEquals("UNAVAILABLE", row.availability)
      assertEquals(1, row.encodingAttempts)
      assertNull(row.localPath)
      assertNull(database.batchDao().current())
    } finally { database.close(); context.deleteDatabase(name) }
  }
}
