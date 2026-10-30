package com.example.imagecompressor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CompressionHistoryDao {
  @Query("SELECT * FROM compression_history ORDER BY createdAt DESC")
  fun observeAll(): Flow<List<CompressionHistoryEntity>>

  @Insert suspend fun insert(item: CompressionHistoryEntity): Long

  @Update suspend fun update(item: CompressionHistoryEntity)
  @Query("SELECT * FROM compression_history ORDER BY createdAt DESC")
  suspend fun all(): List<CompressionHistoryEntity>
  @Query("SELECT * FROM compression_history WHERE id = :id")
  suspend fun find(id: Long): CompressionHistoryEntity?

  @Query("UPDATE compression_history SET pendingSaveUri = :uri WHERE id = :id")
  suspend fun setPendingSave(id: Long, uri: String?)

  @Query("UPDATE compression_history SET savedPath = :savedPath, pendingSaveUri = NULL, availability = 'EXPORTED' WHERE id = :id")
  suspend fun updateSavedPath(id: Long, savedPath: String)

  @Delete suspend fun delete(item: CompressionHistoryEntity)

  @Query("DELETE FROM compression_history")
  suspend fun deleteAll()
}
