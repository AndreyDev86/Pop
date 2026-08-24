package com.example.data

import kotlinx.coroutines.flow.Flow

data class DetectedAppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val tag: String
)

class VersionRepository(private val versionDao: MinecraftVersionDao) {
    val allVersions: Flow<List<MinecraftVersion>> = versionDao.getAllVersions()

    suspend fun selectVersion(id: Long) {
        versionDao.selectVersion(id)
    }

    suspend fun syncDetectedApps(detectedApps: List<DetectedAppInfo>) {
        if (detectedApps.isEmpty()) {
            versionDao.clearAllVersions()
            return
        }

        val detectedPackages = detectedApps.map { it.packageName }
        versionDao.removeVersionsNotIn(detectedPackages)

        val currentlySelected = versionDao.getSelectedVersion()
        var hasSelected = currentlySelected != null && detectedPackages.contains(currentlySelected.packageName)

        for (app in detectedApps) {
            val existing = versionDao.getVersionByPackage(app.packageName)
            if (existing != null) {
                if (existing.versionName != app.versionName || existing.versionCode != app.versionCode || existing.tag != app.tag) {
                    versionDao.updateVersion(
                        existing.copy(
                            versionName = app.versionName,
                            versionCode = app.versionCode,
                            tag = app.tag,
                            isInstalled = true
                        )
                    )
                }
            } else {
                val shouldSelect = !hasSelected
                val newId = versionDao.insertVersion(
                    MinecraftVersion(
                        packageName = app.packageName,
                        versionName = app.versionName,
                        versionCode = app.versionCode,
                        tag = app.tag,
                        isSelected = shouldSelect,
                        isAutoDetected = true,
                        isInstalled = true
                    )
                )
                if (shouldSelect) {
                    versionDao.selectVersion(newId)
                    hasSelected = true
                }
            }
        }
    }
}
