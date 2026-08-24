package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MinecraftVersionDao {
    @Query("SELECT * FROM minecraft_versions ORDER BY createdAt DESC")
    fun getAllVersions(): Flow<List<MinecraftVersion>>

    @Query("SELECT * FROM minecraft_versions WHERE isAutoDetected = 1 LIMIT 1")
    suspend fun getAutoDetectedVersion(): MinecraftVersion?

    @Query("SELECT * FROM minecraft_versions WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedVersion(): MinecraftVersion?

    @Query("SELECT * FROM minecraft_versions WHERE versionName = :name LIMIT 1")
    suspend fun getVersionByName(name: String): MinecraftVersion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: MinecraftVersion): Long

    @Update
    suspend fun updateVersion(version: MinecraftVersion)

    @Query("DELETE FROM minecraft_versions WHERE id = :id")
    suspend fun deleteVersionById(id: Long)

    @Query("SELECT COUNT(*) FROM minecraft_versions")
    suspend fun getVersionCount(): Int

    @Query("UPDATE minecraft_versions SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE minecraft_versions SET isSelected = 1 WHERE id = :id")
    suspend fun setVersionSelected(id: Long)

    @Transaction
    suspend fun selectVersion(id: Long) {
        clearSelection()
        setVersionSelected(id)
    }
}
