package com.example.imagecompressor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
  @Transaction @Query("SELECT * FROM compression_batch LIMIT 1")
  fun observeCurrent(): Flow<List<BatchWithItems>>
  @Transaction @Query("SELECT * FROM compression_batch LIMIT 1")
  suspend fun current(): BatchWithItems?
  @Insert suspend fun insert(batch: BatchEntity)
  @Insert suspend fun insertItems(items: List<BatchItemEntity>)
  @Query("UPDATE compression_batch SET status = :status") suspend fun setStatus(status: String)
  @Query("UPDATE compression_batch_item SET status = :status, historyId = :historyId, error = :error WHERE id = :id")
  suspend fun updateItem(id: String, status: String, historyId: Long? = null, error: String? = null)
  @Query("UPDATE compression_batch_item SET status = :status, error = :error WHERE status IN ('PENDING', 'RUNNING')")
  suspend fun interruptItems(status: String, error: String)
  @Query("DELETE FROM compression_batch_item") suspend fun deleteItems()
  @Query("DELETE FROM compression_batch") suspend fun deleteBatch()
}
