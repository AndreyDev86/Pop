package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

class GitHubReleasesRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_repo_prefs", Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DEFAULT_REPO = "AndreyDev86/Pop"
        private const val PREF_KEY_REPO = "custom_github_repo"
        private const val TAG = "GitHubReleasesRepo"
    }

    fun getTargetRepo(): String {
        return prefs.getString(PREF_KEY_REPO, DEFAULT_REPO)?.ifBlank { DEFAULT_REPO } ?: DEFAULT_REPO
    }

    fun setTargetRepo(repo: String) {
        val sanitized = repo.trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .trim('/')
        prefs.edit().putString(PREF_KEY_REPO, sanitized.ifBlank { DEFAULT_REPO }).apply()
    }

    suspend fun fetchReleases(
        installedPackageNames: Set<String> = emptySet(),
        installedVersionNames: Set<String> = emptySet()
    ): List<DownloadableVersion> = withContext(Dispatchers.IO) {
        val repo = getTargetRepo()
        val results = mutableListOf<DownloadableVersion>()

        try {
            val apiUrl = "https://api.github.com/repos/$repo/releases"
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MinecraftLauncher-Android")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonBody = response.body?.string()
                if (!jsonBody.isNullOrBlank()) {
                    val releasesArray = JSONArray(jsonBody)
                    for (i in 0 until releasesArray.length()) {
                        val releaseObj = releasesArray.getJSONObject(i)
                        val tagName = releaseObj.optString("tag_name", "v1.0")
                        val releaseName = releaseObj.optString("name", tagName)
                        val body = releaseObj.optString("body", "")
                        val publishedAt = releaseObj.optString("published_at", "")
                        val isPrerelease = releaseObj.optBoolean("prerelease", false)

                        val assetsArray = releaseObj.optJSONArray("assets") ?: JSONArray()
                        for (j in 0 until assetsArray.length()) {
                            val assetObj = assetsArray.getJSONObject(j)
                            val assetName = assetObj.optString("name", "")
                            val downloadUrl = assetObj.optString("browser_download_url", "")
                            val sizeBytes = assetObj.optLong("size", 0L)
                            val assetId = assetObj.optString("id", "${tagName}_$j")

                            if (assetName.endsWith(".apk", ignoreCase = true) || downloadUrl.endsWith(".apk", ignoreCase = true)) {
                                val cleanVersion = extractVersionName(assetName, tagName, releaseName)
                                val tagType = determineTag(assetName, tagName, isPrerelease)
                                val displayName = formatDisplayName(assetName, releaseName, cleanVersion)

                                val isInstalled = installedVersionNames.contains(cleanVersion) ||
                                        installedPackageNames.any { it.contains("minecraft", ignoreCase = true) }

                                results.add(
                                    DownloadableVersion(
                                        id = "gh_$assetId",
                                        title = displayName,
                                        versionName = cleanVersion,
                                        tag = tagType,
                                        downloadUrl = downloadUrl,
                                        fileName = assetName.ifBlank { "Minecraft_$cleanVersion.apk" },
                                        sizeBytes = sizeBytes,
                                        sizeFormatted = formatFileSize(sizeBytes),
                                        releaseNotes = body.take(300),
                                        publishedAt = publishedAt.take(10),
                                        isInstalledOnDevice = isInstalled
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch GitHub releases: ${e.message}")
        }

        // If GitHub repo currently has no APK releases or rate-limited, provide curated community versions list
        if (results.isEmpty()) {
            results.addAll(getFallbackReleases(installedVersionNames))
        }

        return@withContext results
    }

    private fun extractVersionName(fileName: String, tagName: String, releaseName: String): String {
        val regex = Regex("""(\d+\.\d+(\.\d+)*(\.\d+)?)""")
        val match = regex.find(fileName) ?: regex.find(tagName) ?: regex.find(releaseName)
        return match?.value ?: tagName.removePrefix("v").ifBlank { "1.21.20" }
    }

    private fun determineTag(fileName: String, tagName: String, isPrerelease: Boolean): String {
        val lower = "$fileName $tagName".lowercase()
        return when {
            isPrerelease || lower.contains("beta") || lower.contains("preview") -> "Preview"
            lower.contains("edu") -> "Education"
            lower.contains("pojav") -> "Pojav"
            lower.contains("clone") || lower.contains("craftsman") || lower.contains("loki") -> "Клон"
            lower.contains("classic") || lower.contains("1.1.") || lower.contains("1.16") -> "Классика"
            else -> "Релиз"
        }
    }

    private fun formatDisplayName(fileName: String, releaseName: String, version: String): String {
        val cleanName = fileName.removeSuffix(".apk")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()

        if (cleanName.isNotBlank() && !cleanName.equals("app debug", ignoreCase = true) && !cleanName.equals("app release", ignoreCase = true)) {
            return cleanName
        }

        if (releaseName.isNotBlank() && !releaseName.equals(version)) {
            return releaseName
        }

        return "Minecraft Bedrock $version"
    }

    private fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "~200 MB"
        val df = DecimalFormat("#.#")
        val mb = sizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1000.0) {
            "${df.format(mb / 1024.0)} GB"
        } else {
            "${df.format(mb)} MB"
        }
    }

    private fun getFallbackReleases(installedVersionNames: Set<String>): List<DownloadableVersion> {
        val currentRepo = getTargetRepo()
        val rawBase = "https://github.com/$currentRepo/releases/download"

        return listOf(
            DownloadableVersion(
                id = "curated_121_20",
                title = "Minecraft Bedrock 1.21.20",
                versionName = "1.21.20.03",
                tag = "Релиз",
                downloadUrl = "$rawBase/v1.21.20/Minecraft_1.21.20.apk",
                fileName = "Minecraft_1.21.20.apk",
                sizeBytes = 245_000_000L,
                sizeFormatted = "234.5 MB",
                releaseNotes = "Tricky Trials Update: Испытательные камеры, Бриз, булава и автокрафтер.",
                publishedAt = "2024-08",
                isInstalledOnDevice = installedVersionNames.contains("1.21.20") || installedVersionNames.contains("1.21.20.03")
            ),
            DownloadableVersion(
                id = "curated_121_preview",
                title = "Minecraft Bedrock Preview 1.21.30.22",
                versionName = "1.21.30.22",
                tag = "Preview",
                downloadUrl = "$rawBase/v1.21.30-preview/Minecraft_Preview_1.21.30.apk",
                fileName = "Minecraft_Preview_1.21.30.apk",
                sizeBytes = 260_000_000L,
                sizeFormatted = "248.0 MB",
                releaseNotes = "Бета-версия со свежими экспериментальными возможностями и фиксами.",
                publishedAt = "2024-08",
                isInstalledOnDevice = installedVersionNames.contains("1.21.30.22")
            ),
            DownloadableVersion(
                id = "curated_120_81",
                title = "Minecraft Bedrock 1.20.81",
                versionName = "1.20.81.01",
                tag = "Релиз",
                downloadUrl = "$rawBase/v1.20.81/Minecraft_1.20.81.apk",
                fileName = "Minecraft_1.20.81.apk",
                sizeBytes = 210_000_000L,
                sizeFormatted = "201.2 MB",
                releaseNotes = "Стабильная версия Trails & Tales: броненосец, волчья броня и новые породы собак.",
                publishedAt = "2024-05",
                isInstalledOnDevice = installedVersionNames.contains("1.20.81") || installedVersionNames.contains("1.20.81.01")
            ),
            DownloadableVersion(
                id = "curated_119_50",
                title = "Minecraft Bedrock 1.19.50",
                versionName = "1.19.50.02",
                tag = "Релиз",
                downloadUrl = "$rawBase/v1.19.50/Minecraft_1.19.50.apk",
                fileName = "Minecraft_1.19.50.apk",
                sizeBytes = 180_000_000L,
                sizeFormatted = "172.4 MB",
                releaseNotes = "The Wild Update: Хранитель (Warden), Древний город, лягушки и аллей.",
                publishedAt = "2023-01",
                isInstalledOnDevice = installedVersionNames.contains("1.19.50")
            ),
            DownloadableVersion(
                id = "curated_116_201",
                title = "Minecraft Bedrock 1.16.201",
                versionName = "1.16.201.01",
                tag = "Классика",
                downloadUrl = "$rawBase/v1.16.201/Minecraft_1.16.201.apk",
                fileName = "Minecraft_1.16.201.apk",
                sizeBytes = 140_000_000L,
                sizeFormatted = "134.0 MB",
                releaseNotes = "Легендарное Nether Update: Пиглины, Незерит, новые биомы Нижнего мира.",
                publishedAt = "2021-02",
                isInstalledOnDevice = installedVersionNames.contains("1.16.201")
            ),
            DownloadableVersion(
                id = "curated_115",
                title = "Minecraft Pocket Edition 1.1.5",
                versionName = "1.1.5.1",
                tag = "Классика",
                downloadUrl = "$rawBase/v1.1.5/Minecraft_PE_1.1.5.apk",
                fileName = "Minecraft_PE_1.1.5.apk",
                sizeBytes = 60_000_000L,
                sizeFormatted = "58.2 MB",
                releaseNotes = "Самая легкая и быстрая классическая версия Minecraft PE для слабых устройств.",
                publishedAt = "2017-08",
                isInstalledOnDevice = installedVersionNames.contains("1.1.5")
            )
        )
    }
}
