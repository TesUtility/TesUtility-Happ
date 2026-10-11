package com.example.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.VlessConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface VlessDao {
    @Query("SELECT * FROM vless_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<VlessConfig>>

    @Query("SELECT * FROM vless_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: Long): VlessConfig?

    @Query("SELECT * FROM vless_configs WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedConfig(): VlessConfig?

    @Query("SELECT * FROM vless_configs WHERE isSelected = 1 LIMIT 1")
    fun getSelectedConfigFlow(): Flow<VlessConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: VlessConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<VlessConfig>)

    @Update
    suspend fun update(config: VlessConfig)

    @Delete
    suspend fun delete(config: VlessConfig)

    @Query("UPDATE vless_configs SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE vless_configs SET isSelected = 1 WHERE id = :id")
    suspend fun selectConfig(id: Long)

    @Query("UPDATE vless_configs SET pingMs = :pingMs WHERE id = :id")
    suspend fun updatePing(id: Long, pingMs: Long)

    @Query("DELETE FROM vless_configs")
    suspend fun deleteAll()
}
