package com.example.data

import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Float, // 0.0f .. 1.0f
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadState()
    data class Downloaded(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

data class DownloadableVersion(
    val id: String,
    val title: String,
    val versionName: String,
    val tag: String, // "Релиз", "Preview", "Бета", "Xbox Live", "Клон", "Классика"
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long = 0L,
    val sizeFormatted: String = "",
    val releaseNotes: String = "",
    val publishedAt: String = "",
    val articleUrl: String = "https://mcpehub.org/download-mcpe/",
    val isInstalledOnDevice: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle
)
