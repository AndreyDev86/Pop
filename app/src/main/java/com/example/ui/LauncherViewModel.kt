package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppDatabase
import com.example.data.DetectedAppInfo
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

const val MINECRAFT_PACKAGE_BEDROCK = "com.mojang.minecraftpe"
const val MINECRAFT_PACKAGE_PREVIEW = "com.mojang.minecraftpe.beta"
const val MINECRAFT_PACKAGE_EDUCATION = "com.mojang.minecraftedu"

data class MinecraftStatus(
    val isInstalled: Boolean = false,
    val primaryPackageName: String = MINECRAFT_PACKAGE_BEDROCK,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val tag: String? = null,
    val detectedCount: Int = 0
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: VersionRepository

    val versions: StateFlow<List<MinecraftVersion>>
    val selectedVersion: StateFlow<MinecraftVersion?>

    private val _mcStatus = MutableStateFlow(MinecraftStatus())
    val mcStatus: StateFlow<MinecraftStatus> = _mcStatus.asStateFlow()

    private val _isVersionSheetOpen = MutableStateFlow(false)
    val isVersionSheetOpen: StateFlow<Boolean> = _isVersionSheetOpen.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = VersionRepository(db.versionDao())

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
        try {
            val shareText = context.getString(R.string.share_text)
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "$shareText\n\nhttps://github.com/AndreyDev86/Qwe")
                type = "text/plain"
            }
            val chooserIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (_: Exception) {
            // Ignore error
        }
    }

    private fun scanAllInstalledMinecraftPackages(context: Context): List<DetectedAppInfo> {
        val pm = context.packageManager
        val candidatePackages = listOf(
            MINECRAFT_PACKAGE_BEDROCK to "Bedrock",
            MINECRAFT_PACKAGE_PREVIEW to "Preview",
            MINECRAFT_PACKAGE_EDUCATION to "Education"
        )

        val detectedList = mutableListOf<DetectedAppInfo>()

        for ((pkgName, tag) in candidatePackages) {
            try {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkgName, 0)
                }

                val vName = info.versionName ?: "Unknown"
                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }

                detectedList.add(
                    DetectedAppInfo(
                        packageName = pkgName,
                        versionName = vName,
                        versionCode = vCode,
                        tag = tag
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Package not installed
            } catch (_: Exception) {
                // Ignore error
            }
        }

        return detectedList
    }
}
