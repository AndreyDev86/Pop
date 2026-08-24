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

const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"

data class MinecraftStatus(
    val isInstalled: Boolean = false,
    val versionName: String? = null,
    val versionCode: Long? = null
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: VersionRepository

    val versions: StateFlow<List<MinecraftVersion>>

    val selectedVersion: StateFlow<MinecraftVersion?>

    private val _mcStatus = MutableStateFlow(MinecraftStatus())
    val mcStatus: StateFlow<MinecraftStatus> = _mcStatus.asStateFlow()

    private val _isVersionSheetOpen = MutableStateFlow(false)
    val isVersionSheetOpen: StateFlow<Boolean> = _isVersionSheetOpen.asStateFlow()

    private val _isAddDialogOpen = MutableStateFlow(false)
    val isAddDialogOpen: StateFlow<Boolean> = _isAddDialogOpen.asStateFlow()

    private val _isLoadingApk = MutableStateFlow(false)
    val isLoadingApk: StateFlow<Boolean> = _isLoadingApk.asStateFlow()

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
        val status = checkMinecraftInstalled(context)
        _mcStatus.value = status

        viewModelScope.launch {
            if (status.isInstalled && !status.versionName.isNullOrBlank()) {
                repository.syncDetectedVersion(status.versionName)
            }
        }
    }

    fun openVersionSheet() {
        _isVersionSheetOpen.value = true
    }

    fun closeVersionSheet() {
        _isVersionSheetOpen.value = false
    }

    fun openAddDialog() {
        _isAddDialogOpen.value = true
    }

    fun closeAddDialog() {
        _isAddDialogOpen.value = false
    }

    fun selectVersion(id: Long) {
        viewModelScope.launch {
            repository.selectVersion(id)
        }
    }

    fun addCustomVersion(versionName: String, tag: String) {
        val trimmed = versionName.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                repository.addVersion(trimmed, tag.ifEmpty { "Custom" }, selectNow = true)
                _isAddDialogOpen.value = false
            }
        }
    }

    fun importApkUri(uri: Uri) {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApk.value = true
            try {
                val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val pm = context.packageManager
                val pkgInfo = pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
                if (pkgInfo != null) {
                    val versionName = pkgInfo.versionName ?: "Unknown"
                    val isMojang = pkgInfo.packageName == MINECRAFT_PACKAGE
                    val tag = if (isMojang) "Bedrock APK" else "Custom APK"

                    repository.addVersion(versionName, tag, selectNow = true)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_apk_imported, versionName),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.err_apk_invalid), Toast.LENGTH_SHORT).show()
                    }
                }
                tempFile.delete()
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.err_apk_read), Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isLoadingApk.value = false
            }
        }
    }

    fun deleteVersion(id: Long) {
        viewModelScope.launch {
            repository.deleteVersion(id)
        }
    }

    fun launchGame(context: Context) {
        val isInstalled = _mcStatus.value.isInstalled
        if (isInstalled) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(MINECRAFT_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Toast.makeText(context, context.getString(R.string.toast_launching), Toast.LENGTH_SHORT).show()
                context.startActivity(launchIntent)
            } else {
                openGooglePlay(context)
            }
        } else {
            Toast.makeText(context, context.getString(R.string.toast_not_installed), Toast.LENGTH_SHORT).show()
            openGooglePlay(context)
        }
    }

    fun openGooglePlay(context: Context) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$MINECRAFT_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$MINECRAFT_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    private fun checkMinecraftInstalled(context: Context): MinecraftStatus {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(MINECRAFT_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(MINECRAFT_PACKAGE, 0)
            }
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            MinecraftStatus(
                isInstalled = true,
                versionName = info.versionName,
                versionCode = vCode
            )
        } catch (_: PackageManager.NameNotFoundException) {
            MinecraftStatus(isInstalled = false)
        } catch (_: Exception) {
            MinecraftStatus(isInstalled = false)
        }
    }
}
