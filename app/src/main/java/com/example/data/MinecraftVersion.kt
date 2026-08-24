package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "minecraft_versions")
data class MinecraftVersion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val versionName: String,
    val tag: String = "Release",
    val isSelected: Boolean = false,
    val isAutoDetected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
