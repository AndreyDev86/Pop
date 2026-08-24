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
    val primaryAppName: String = "Minecraft",
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
        val myPackageName = context.packageName
        val detectedMap = mutableMapOf<String, DetectedAppInfo>()

        // 1. Direct candidate package checks
        data class PresetCandidate(val pkg: String, val name: String, val tag: String)
        val knownCandidates = listOf(
            PresetCandidate(MINECRAFT_PACKAGE_BEDROCK, "Minecraft", "Оригинал"),
            PresetCandidate(MINECRAFT_PACKAGE_PREVIEW, "Minecraft Preview", "Preview"),
            PresetCandidate(MINECRAFT_PACKAGE_EDUCATION, "Minecraft Education", "Education"),
            PresetCandidate("net.kdt.pojavlaunch", "PojavLauncher", "Pojav"),
            PresetCandidate("net.kdt.pojavlaunch.debug", "PojavLauncher Debug", "Pojav")
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

        // 2. Scan all installed packages on device for Minecraft clones and versions
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_ACTIVITIES
            }

            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            }

            for (pkgInfo in installedPackages) {
                val pkgName = pkgInfo.packageName ?: continue
                if (pkgName == myPackageName) continue

                val appLabel = try {
                    pkgInfo.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: ""
                } catch (_: Exception) {
                    ""
                }

                val lowerPkg = pkgName.lowercase()
                val lowerLabel = appLabel.lowercase()

                // Check if this package is a Minecraft app/clone
                val hasMojangActivity = pkgInfo.activities?.any { act ->
                    act.name.contains("mojang", ignoreCase = true) ||
                    act.name.contains("minecraft", ignoreCase = true)
                } == true

                val isMinecraftClone = lowerLabel == "minecraft" ||
                        lowerLabel.contains("minecraft") ||
                        lowerLabel.contains("майнкрафт") ||
                        lowerPkg.contains("minecraft") ||
                        lowerPkg.contains("mojang") ||
                        lowerPkg.contains("mcpe") ||
                        hasMojangActivity

                if (isMinecraftClone) {
                    val vName = pkgInfo.versionName ?: "1.0"
                    val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }

                    val tag = when {
                        pkgName == MINECRAFT_PACKAGE_BEDROCK -> "Оригинал"
                        lowerPkg.contains("beta") || lowerPkg.contains("preview") || lowerLabel.contains("preview") || lowerLabel.contains("beta") -> "Preview"
                        lowerPkg.contains("edu") || lowerLabel.contains("education") -> "Education"
                        lowerPkg.contains("pojav") || lowerLabel.contains("pojav") -> "Pojav"
                        else -> "Клон"
                    }

                    val displayName = if (appLabel.isNotBlank()) appLabel else "Minecraft"

                    // If existing entry has placeholder tag, update with better metadata
                    if (!detectedMap.containsKey(pkgName) || detectedMap[pkgName]?.tag == "Bedrock") {
                        detectedMap[pkgName] = DetectedAppInfo(
                            appName = displayName,
                            packageName = pkgName,
                            versionName = vName,
                            versionCode = vCode,
                            tag = tag
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore package scan errors
        }

        // 3. Dynamic scan of all launchable launcher activities (catch dual apps, parallel space, app cloner shortcuts)
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchableApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            for (resolveInfo in launchableApps) {
                val pkg = resolveInfo.activityInfo?.packageName ?: continue
                if (pkg == myPackageName || detectedMap.containsKey(pkg)) continue

                val label = try {
                    resolveInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    ""
                }

                val lowerPkg = pkg.lowercase()
                val lowerLabel = label.lowercase()
                val targetActivity = resolveInfo.activityInfo?.name ?: ""

                val isMatch = lowerLabel == "minecraft" ||
                        lowerLabel.contains("minecraft") ||
                        lowerLabel.contains("майнкрафт") ||
                        lowerPkg.contains("minecraft") ||
                        lowerPkg.contains("mojang") ||
                        lowerPkg.contains("mcpe") ||
                        targetActivity.contains("mojang", ignoreCase = true) ||
                        targetActivity.contains("minecraft", ignoreCase = true)

                if (isMatch) {
                    try {
                        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(pkg, 0)
                        }

                        val vName = pInfo.versionName ?: "1.0"
                        val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pInfo.versionCode.toLong()
                        }

                        val tag = when {
                            pkg == MINECRAFT_PACKAGE_BEDROCK -> "Оригинал"
                            lowerPkg.contains("beta") || lowerLabel.contains("preview") || lowerLabel.contains("beta") -> "Preview"
                            lowerPkg.contains("edu") || lowerLabel.contains("education") -> "Education"
                            lowerPkg.contains("pojav") || lowerLabel.contains("pojav") -> "Pojav"
                            else -> "Клон"
                        }

                        detectedMap[pkg] = DetectedAppInfo(
                            appName = label.ifBlank { "Minecraft" },
                            packageName = pkg,
                            versionName = vName,
                            versionCode = vCode,
                            tag = tag
                        )
                    } catch (_: Exception) {
                        // Skip if unable to get PackageInfo
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore dynamic query errors
        }

        // Return sorted list: Original first, then sorted by version descending
        return detectedMap.values.sortedWith(
            compareByDescending<DetectedAppInfo> { it.tag == "Оригинал" }
                .thenByDescending { it.versionCode }
                .thenByDescending { it.versionName }
        )
    }
}
