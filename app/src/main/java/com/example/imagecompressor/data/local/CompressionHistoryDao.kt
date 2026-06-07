package com.example.imagecompressor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompressionHistoryDao {
  @Query("SELECT * FROM compression_history ORDER BY createdAt DESC")
  fun observeAll(): Flow<List<CompressionHistoryEntity>>

  @Insert suspend fun insert(item: CompressionHistoryEntity): Long

  @Query("UPDATE compression_history SET savedPath = :savedPath WHERE id = :id")
  suspend fun updateSavedPath(id: Long, savedPath: String)

  @Delete suspend fun delete(item: CompressionHistoryEntity)

  @Query("DELETE FROM compression_history")
  suspend fun deleteAll()
}
