package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ApkDownloadManager(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Map of versionId to current DownloadState flow
    private val _downloadStates = ConcurrentHashMap<String, MutableStateFlow<DownloadState>>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val TAG = "ApkDownloadManager"
        private const val DOWNLOADS_SUBDIR = "minecraft_apks"
    }

    private fun getDownloadsDir(): File {
        val dir = File(context.filesDir, DOWNLOADS_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getDownloadFile(fileName: String): File {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(getDownloadsDir(), safeName)
    }

    fun isApkDownloaded(fileName: String, expectedSizeBytes: Long = 0L): Boolean {
        val file = getDownloadFile(fileName)
        return if (file.exists() && file.length() > 0L) {
            if (expectedSizeBytes > 0L) {
                file.length() == expectedSizeBytes || file.length() > 50_000_000L
            } else {
                file.length() > 10_000_000L
            }
        } else {
            false
        }
    }

    fun getStateFlow(versionId: String, fileName: String, sizeBytes: Long = 0L): StateFlow<DownloadState> {
        val flow = _downloadStates.computeIfAbsent(versionId) {
            val file = getDownloadFile(fileName)
            val initialState = if (isApkDownloaded(fileName, sizeBytes)) {
                DownloadState.Downloaded(file)
            } else {
                DownloadState.Idle
            }
            MutableStateFlow(initialState)
        }
        return flow.asStateFlow()
    }

    suspend fun downloadApk(
        version: DownloadableVersion,
        job: Job
    ) = withContext(Dispatchers.IO) {
        val stateFlow = _downloadStates.computeIfAbsent(version.id) {
            MutableStateFlow(DownloadState.Idle)
        }
        downloadJobs[version.id] = job

        val targetFile = getDownloadFile(version.fileName)
        val tempFile = File(getDownloadsDir(), "${targetFile.name}.downloading")

        try {
            stateFlow.value = DownloadState.Downloading(
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = version.sizeBytes
            )

            val request = Request.Builder()
                .url(version.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) MinecraftLauncher")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else version.sizeBytes

            if (tempFile.exists()) {
                tempFile.delete()
            }

            var bytesReadTotal = 0L
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var lastUpdatePercent = -1

                    while (input.read(buffer).also { read = it } != -1) {
                        if (job.isCancelled) {
                            tempFile.delete()
                            throw CancellationException("Download cancelled by user")
                        }
                        output.write(buffer, 0, read)
                        bytesReadTotal += read

                        val progress = if (totalBytes > 0) {
                            (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        val currentPercent = (progress * 100).toInt()
                        if (currentPercent != lastUpdatePercent) {
                            lastUpdatePercent = currentPercent
                            stateFlow.value = DownloadState.Downloading(
                                progress = progress,
                                downloadedBytes = bytesReadTotal,
                                totalBytes = totalBytes
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.renameTo(targetFile)) {
                stateFlow.value = DownloadState.Downloaded(targetFile)
            } else {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                stateFlow.value = DownloadState.Downloaded(targetFile)
            }

        } catch (e: CancellationException) {
            Log.d(TAG, "Download cancelled for ${version.title}")
            tempFile.delete()
            stateFlow.value = DownloadState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Download error for ${version.title}: ${e.message}", e)
            tempFile.delete()
            stateFlow.value = DownloadState.Error(e.message ?: "Ошибка скачивания")
        } finally {
            downloadJobs.remove(version.id)
        }
    }

    fun cancelDownload(versionId: String) {
        val job = downloadJobs.remove(versionId)
        job?.cancel()
        _downloadStates[versionId]?.value = DownloadState.Idle
    }

    fun installApk(context: Context, file: File) {
        try {
            if (!file.exists()) {
                Log.e(TAG, "APK file does not exist: ${file.absolutePath}")
                return
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
        }
    }
}
