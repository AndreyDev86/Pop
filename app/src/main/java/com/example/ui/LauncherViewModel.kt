package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ApkDownloadManager
import com.example.data.AppDatabase
import com.example.data.DetectedAppInfo
import com.example.data.DownloadState
import com.example.data.DownloadableVersion
import com.example.data.McpeHubRepository
import com.example.data.MinecraftVersion
import com.example.data.VersionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val MINECRAFT_PACKAGE_BEDROCK = "com.mojang.minecraftpe"
const val MINECRAFT_PACKAGE_PREVIEW = "com.mojang.minecraftpe.beta"
const val MINECRAFT_PACKAGE_EDUCATION = "com.mojang.minecraftedu"

data class MinecraftStatus(
    val isInstalled: Boolean = false,
    val primaryPackageName: String = MINECRAFT_PACKAGE_BEDROCK,
    val primaryAppName: String = "Minecraft",
    val versionName: String? = null,
    val versionCode: Long? = null,
    val tag: String? = null,
    val detectedCount: Int = 0
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: VersionRepository
    private val mcpeHubRepository: McpeHubRepository
    private val downloadManager: ApkDownloadManager

    val versions: StateFlow<List<MinecraftVersion>>
    val selectedVersion: StateFlow<MinecraftVersion?>

    private val _mcStatus = MutableStateFlow(MinecraftStatus())
    val mcStatus: StateFlow<MinecraftStatus> = _mcStatus.asStateFlow()

    private val _isVersionSheetOpen = MutableStateFlow(false)
    val isVersionSheetOpen: StateFlow<Boolean> = _isVersionSheetOpen.asStateFlow()

    private val _isDownloadHubOpen = MutableStateFlow(false)
    val isDownloadHubOpen: StateFlow<Boolean> = _isDownloadHubOpen.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _downloadableVersions = MutableStateFlow<List<DownloadableVersion>>(emptyList())
    val downloadableVersions: StateFlow<List<DownloadableVersion>> = _downloadableVersions.asStateFlow()

    private val _isLoadingReleases = MutableStateFlow(false)
    val isLoadingReleases: StateFlow<Boolean> = _isLoadingReleases.asStateFlow()

    private val _siteUrl = MutableStateFlow("https://mcpehub.org/download-mcpe/")
    val siteUrl: StateFlow<String> = _siteUrl.asStateFlow()

    private val _downloadFilter = MutableStateFlow("Все")
    val downloadFilter: StateFlow<String> = _downloadFilter.asStateFlow()

    private val _downloadSearchQuery = MutableStateFlow("")
    val downloadSearchQuery: StateFlow<String> = _downloadSearchQuery.asStateFlow()

    private val _activeHubTab = MutableStateFlow(0)
    val activeHubTab: StateFlow<Int> = _activeHubTab.asStateFlow()

    private val _activeWebUrl = MutableStateFlow("https://mcpehub.org/download-mcpe/")
    val activeWebUrl: StateFlow<String> = _activeWebUrl.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = VersionRepository(db.versionDao())
        mcpeHubRepository = McpeHubRepository(application)
        downloadManager = ApkDownloadManager(application)
        _siteUrl.value = mcpeHubRepository.getSiteUrl()
        _activeWebUrl.value = mcpeHubRepository.getSiteUrl()

        versions = repository.allVersions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        selectedVersion = versions.map { list ->
            list.find { it.isSelected } ?: list.firstOrNull()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        refreshStatus()
        refreshDownloadableVersions()
    }

    fun refreshStatus() {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            val detected = scanAllInstalledMinecraftPackages(context)
            repository.syncDetectedApps(detected)

            val isInstalled = detected.isNotEmpty()
            val primary = detected.firstOrNull()

            _mcStatus.value = MinecraftStatus(
                isInstalled = isInstalled,
                primaryPackageName = primary?.packageName ?: MINECRAFT_PACKAGE_BEDROCK,
                primaryAppName = primary?.appName ?: "Minecraft",
                versionName = primary?.versionName,
                versionCode = primary?.versionCode,
                tag = primary?.tag,
                detectedCount = detected.size
            )
            _isScanning.value = false
        }
    }

    fun openVersionSheet() {
        _isVersionSheetOpen.value = true
    }

    fun closeVersionSheet() {
        _isVersionSheetOpen.value = false
    }

    fun openDownloadHub() {
        _isDownloadHubOpen.value = true
        refreshDownloadableVersions()
    }

    fun closeDownloadHub() {
        _isDownloadHubOpen.value = false
    }

    fun setDownloadFilter(filter: String) {
        _downloadFilter.value = filter
    }

    fun setDownloadSearchQuery(query: String) {
        _downloadSearchQuery.value = query
    }

    fun setCustomSiteUrl(url: String) {
        mcpeHubRepository.setSiteUrl(url)
        _siteUrl.value = mcpeHubRepository.getSiteUrl()
        refreshDownloadableVersions()
    }

    fun refreshDownloadableVersions() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingReleases.value = true
            val currentVersions = repository.getAllVersionsDirect()
            val installedPackages = currentVersions.map { it.packageName }.toSet()
            val installedVersionNames = currentVersions.mapNotNull { it.versionName }.toSet()

            val fetched = mcpeHubRepository.fetchReleases(installedPackages, installedVersionNames)
            _downloadableVersions.value = fetched
            _isLoadingReleases.value = false
        }
    }

    fun setActiveHubTab(index: Int) {
        _activeHubTab.value = index
    }

    fun openInWebView(url: String) {
        val target = url.ifBlank { mcpeHubRepository.getSiteUrl() }
        _activeWebUrl.value = target
        _activeHubTab.value = 1
    }

    fun openMcpeHubArticle(context: Context, articleUrl: String, openExternal: Boolean = false) {
        val target = if (articleUrl.isNotBlank()) articleUrl else "https://mcpehub.org/download-mcpe/"
        if (openExternal) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось открыть браузер: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            openInWebView(target)
        }
    }

    fun getDownloadStateFlow(version: DownloadableVersion): StateFlow<DownloadState> {
        return downloadManager.getStateFlow(version.id, version.fileName, version.sizeBytes)
    }

    fun startDownload(version: DownloadableVersion) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadManager.downloadApk(version, coroutineContext[kotlinx.coroutines.Job]!!)
        }
    }

    fun startDirectDownload(id: String, url: String, fileName: String, referer: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadManager.downloadDirectUrl(id, url, fileName, referer, coroutineContext[kotlinx.coroutines.Job]!!)
        }
    }

    fun cancelDownload(versionId: String) {
        downloadManager.cancelDownload(versionId)
    }

    fun installDownloadedApk(context: Context, version: DownloadableVersion) {
        val file = downloadManager.getDownloadFile(version.fileName)
        if (file.exists()) {
            downloadManager.installApk(context, file)
        } else {
            Toast.makeText(context, "Файл не найден. Скачайте заново.", Toast.LENGTH_SHORT).show()
        }
    }

    fun selectVersion(id: Long) {
        viewModelScope.launch {
            repository.selectVersion(id)
        }
    }

    fun launchGame(context: Context) {
        val currentSelected = selectedVersion.value
        val targetPackage = currentSelected?.packageName ?: _mcStatus.value.primaryPackageName

        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Toast.makeText(context, context.getString(R.string.toast_launching), Toast.LENGTH_SHORT).show()
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_not_installed), Toast.LENGTH_SHORT).show()
            openGooglePlay(context, targetPackage)
        }
    }

    fun openGooglePlay(context: Context, packageName: String = MINECRAFT_PACKAGE_BEDROCK) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun shareApp(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceDir = context.applicationInfo.sourceDir
                if (sourceDir != null) {
                    val sourceFile = File(sourceDir)
                    if (sourceFile.exists()) {
                        val cacheDir = File(context.cacheDir, "shared_apk").apply { mkdirs() }
                        val destFile = File(cacheDir, "MinecraftLauncher.apk")
                        
                        sourceFile.inputStream().use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val apkUri: Uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            destFile
                        )

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "application/vnd.android.package-archive"
                            putExtra(Intent.EXTRA_STREAM, apkUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Minecraft Launcher APK")
                            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        val chooserIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_title)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        withContext(Dispatchers.Main) {
                            context.startActivity(chooserIntent)
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                // Fallback to text link if APK export fails
            }

            withContext(Dispatchers.Main) {
                try {
                    val shareText = context.getString(R.string.share_text)
                    val shareLink = context.getString(R.string.share_link)
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "$shareText\n\n$shareLink")
                        type = "text/plain"
                    }
                    val chooserIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_title)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooserIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Не удалось отправить", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun scanAllInstalledMinecraftPackages(context: Context): List<DetectedAppInfo> {
        val pm = context.packageManager
        val detectedMap = mutableMapOf<String, DetectedAppInfo>()

        data class PresetCandidate(val pkg: String, val name: String, val tag: String)
        val knownCandidates = listOf(
            PresetCandidate(MINECRAFT_PACKAGE_BEDROCK, "Minecraft", "Оригинал"),
            PresetCandidate(MINECRAFT_PACKAGE_PREVIEW, "Minecraft Preview", "Preview"),
            PresetCandidate(MINECRAFT_PACKAGE_EDUCATION, "Minecraft Education", "Education"),
            PresetCandidate("com.mojang.minecraft", "Minecraft", "Оригинал")
        )

        for (candidate in knownCandidates) {
            try {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(candidate.pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(candidate.pkg, 0)
                }

                val appLabel = try {
                    info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: candidate.name
                } catch (_: Exception) {
                    candidate.name
                }

                val vName = info.versionName ?: "1.0"
                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }

                detectedMap[candidate.pkg] = DetectedAppInfo(
                    appName = if (appLabel.isNotBlank()) appLabel else candidate.name,
                    packageName = candidate.pkg,
                    versionName = vName,
                    versionCode = vCode,
                    tag = candidate.tag
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            } catch (_: Exception) {
                // Ignore error
            }
        }

        // Return sorted list: Original first, then sorted by version descending
        return detectedMap.values.sortedWith(
            compareByDescending<DetectedAppInfo> { it.tag == "Оригинал" }
                .thenByDescending { it.versionCode }
                .thenByDescending { it.versionName }
        )
    }
}
