package com.example.data

import kotlinx.coroutines.flow.Flow

class VersionRepository(private val versionDao: MinecraftVersionDao) {
    val allVersions: Flow<List<MinecraftVersion>> = versionDao.getAllVersions()

    suspend fun addVersion(versionName: String, tag: String, selectNow: Boolean = true): Long {
        val existing = versionDao.getVersionByName(versionName)
        val id = if (existing != null) {
            versionDao.updateVersion(existing.copy(tag = tag))
            existing.id
        } else {
            versionDao.insertVersion(
                MinecraftVersion(
                    versionName = versionName,
                    tag = tag,
                    isSelected = false,
                    isAutoDetected = false
                )
            )
        }
        if (selectNow) {
            versionDao.selectVersion(id)
        }
        return id
    }

    suspend fun selectVersion(id: Long) {
        versionDao.selectVersion(id)
    }

    suspend fun deleteVersion(id: Long) {
        versionDao.deleteVersionById(id)
    }

    suspend fun syncDetectedVersion(detectedVersion: String?) {
        if (detectedVersion.isNullOrBlank()) return

        val existingAuto = versionDao.getAutoDetectedVersion()
        val currentlySelected = versionDao.getSelectedVersion()

        if (existingAuto != null) {
            if (existingAuto.versionName != detectedVersion) {
                versionDao.updateVersion(existingAuto.copy(versionName = detectedVersion))
            }
        } else {
            val shouldSelect = currentlySelected == null
            val newId = versionDao.insertVersion(
                MinecraftVersion(
                    versionName = detectedVersion,
                    tag = "Installed",
                    isSelected = shouldSelect,
                    isAutoDetected = true
                )
            )
            if (shouldSelect) {
                versionDao.selectVersion(newId)
            }
        }
    }
}
