package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "minecraft_versions")
data class MinecraftVersion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String = "com.mojang.minecraftpe",
    val versionName: String,
    val versionCode: Long = 0,
    val tag: String = "Bedrock",
    val isSelected: Boolean = false,
    val isAutoDetected: Boolean = true,
    val isInstalled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
