package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class McpeHubRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_mcpehub_prefs", Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        const val DEFAULT_SITE_URL = "https://mcpehub.org/download-mcpe/"
        private const val PREF_KEY_SITE_URL = "custom_mcpehub_url"
        private const val TAG = "McpeHubRepo"

        fun getDirectApkMirror(versionName: String, tag: String = ""): String {
            val v = versionName.trim()
            return when {
                v.startsWith("1.21.30") || v.startsWith("1.22") || v.startsWith("1.26") || v.startsWith("26.") || tag.contains("Preview", ignoreCase = true) || tag.contains("Бета", ignoreCase = true) ->
                    "https://github.com/AndreyDev86/Pop/releases/download/v1.21.30-preview/Minecraft_Preview_1.21.30.apk"
                v.startsWith("1.21") ->
                    "https://github.com/AndreyDev86/Pop/releases/download/v1.21.20/Minecraft_1.21.20.apk"
                v.startsWith("1.20") ->
                    "https://github.com/AndreyDev86/Pop/releases/download/v1.20.81/Minecraft_1.20.81.apk"
                v.startsWith("1.19") ->
                    "https://github.com/AndreyDev86/Pop/releases/download/v1.19.50/Minecraft_1.19.50.apk"
                v.startsWith("1.18") || v.startsWith("1.17") ->
                    "https://github.com/AndreyDev86/Pop/releases/download/v1.19.50/Minecraft_1.19.50.apk"
                v.startsWith("1.16") ->
                    "https://github.com/AndreyDev86/Pop/releases/download/v1.16.201/Minecraft_1.16.201.apk"
                v.startsWith("1.1.5") || v.startsWith("1.1.") ->
                    "https://github.com/AndreyDev86/Pop/releases/download/v1.1.5/Minecraft_PE_1.1.5.apk"
                else ->
                    "https://github.com/AndreyDev86/Pop/releases/download/v1.21.20/Minecraft_1.21.20.apk"
            }
        }
    }

    fun getSiteUrl(): String {
        return prefs.getString(PREF_KEY_SITE_URL, DEFAULT_SITE_URL)?.ifBlank { DEFAULT_SITE_URL } ?: DEFAULT_SITE_URL
    }

    fun setSiteUrl(url: String) {
        val sanitized = url.trim()
        prefs.edit().putString(PREF_KEY_SITE_URL, sanitized.ifBlank { DEFAULT_SITE_URL }).apply()
    }

    suspend fun fetchReleases(
        installedPackageNames: Set<String> = emptySet(),
        installedVersionNames: Set<String> = emptySet()
    ): List<DownloadableVersion> = withContext(Dispatchers.IO) {
        val siteUrl = getSiteUrl()
        val results = mutableListOf<DownloadableVersion>()

        try {
            val request = Request.Builder()
                .url(siteUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string().orEmpty()
                if (html.isNotBlank()) {
                    val parsed = parseMcpeHubHtml(html, siteUrl, installedVersionNames)
                    results.addAll(parsed)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load live data from $siteUrl: ${e.message}")
        }

        // Merge with curated items that might not be on the front page of mcpehub
        val parsedUrls = results.map { it.articleUrl }.toSet()
        val curated = getCuratedMcpeHubCatalog(installedVersionNames)
        for (item in curated) {
            if (!parsedUrls.contains(item.articleUrl)) {
                results.add(item)
            }
        }

        if (results.isEmpty()) {
            results.addAll(curated)
        }

        return@withContext results
    }

    private fun parseMcpeHubHtml(
        html: String,
        baseSiteUrl: String,
        installedVersionNames: Set<String>
    ): List<DownloadableVersion> {
        val items = mutableListOf<DownloadableVersion>()

        try {
            // Find news/article blocks in DLE structure
            val articlePattern = Pattern.compile(
                "<div[^>]*class=[\"'][^\"']*short-story[^\"']*[\"'][^>]*>(.*?)</div>\\s*</div>",
                Pattern.DOTALL
            )
            val linkPattern = Pattern.compile("href=[\"'](https://mcpehub\\.org/download-mcpe/\\d+-[^\"']+\\.html|/download-mcpe/\\d+-[^\"']+\\.html)[\"']")
            val titlePattern = Pattern.compile("<h2[^>]*class=[\"']title[^\"']*[\"'][^>]*>\\s*<a[^>]*>(.*?)</a>", Pattern.DOTALL)
            val versionPattern = Pattern.compile("(\\d+\\.\\d+(\\.\\d+)*(\\.\\d+)?)")
            val imgPattern = Pattern.compile("<img[^>]*src=[\"']([^\"']+)[\"']")

            val matcher = articlePattern.matcher(html)
            var index = 0

            while (matcher.find()) {
                val block = matcher.group(1) ?: continue
                val linkMatcher = linkPattern.matcher(block)
                if (!linkMatcher.find()) continue

                var articleUrl = linkMatcher.group(1) ?: continue
                if (!articleUrl.startsWith("http")) {
                    articleUrl = "https://mcpehub.org$articleUrl"
                }

                val titleMatcher = titlePattern.matcher(block)
                val rawTitle = if (titleMatcher.find()) {
                    titleMatcher.group(1)?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty()
                } else {
                    ""
                }

                val verMatcher = versionPattern.matcher(rawTitle.ifBlank { block })
                val versionName = if (verMatcher.find()) verMatcher.group(1) ?: "1.21" else "1.21"

                index++
                val isBeta = rawTitle.contains("бета", ignoreCase = true) ||
                        rawTitle.contains("preview", ignoreCase = true) ||
                        block.contains("preview", ignoreCase = true)

                val tag = when {
                    isBeta -> "Preview"
                    rawTitle.contains("xbox", ignoreCase = true) || block.contains("xbox", ignoreCase = true) -> "Xbox Live"
                    versionName.startsWith("1.1.") || versionName.startsWith("1.16") -> "Классика"
                    else -> "Релиз"
                }

                val displayTitle = if (rawTitle.isNotBlank()) rawTitle else "Minecraft PE $versionName"
                val cleanFileName = "Minecraft_${versionName.replace('.', '_')}_MCPEHub.apk"

                items.add(
                    DownloadableVersion(
                        id = "mcpehub_parsed_$index",
                        title = displayTitle,
                        versionName = versionName,
                        tag = tag,
                        downloadUrl = articleUrl, // Points to the article page which resolves or opens in-app browser
                        fileName = cleanFileName,
                        sizeBytes = 240_000_000L,
                        sizeFormatted = "~240 MB",
                        releaseNotes = "Официальная статья и загрузка с портала MCPEHub.org. Рабочий мультиплеер и скины.",
                        publishedAt = "MCPEHub",
                        articleUrl = articleUrl,
                        isInstalledOnDevice = installedVersionNames.contains(versionName)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MCPEHub HTML: ${e.message}")
        }

        return items
    }

    fun getDirectApkMirror(versionName: String, tag: String = ""): String {
        val v = versionName.trim()
        return when {
            v.startsWith("1.21.30") || v.startsWith("1.22") || v.startsWith("1.26") || v.startsWith("26.") || tag.contains("Preview", ignoreCase = true) || tag.contains("Бета", ignoreCase = true) ->
                "https://github.com/AndreyDev86/Pop/releases/download/v1.21.30-preview/Minecraft_Preview_1.21.30.apk"
            v.startsWith("1.21") ->
                "https://github.com/AndreyDev86/Pop/releases/download/v1.21.20/Minecraft_1.21.20.apk"
            v.startsWith("1.20") ->
                "https://github.com/AndreyDev86/Pop/releases/download/v1.20.81/Minecraft_1.20.81.apk"
            v.startsWith("1.19") ->
                "https://github.com/AndreyDev86/Pop/releases/download/v1.19.50/Minecraft_1.19.50.apk"
            v.startsWith("1.18") || v.startsWith("1.17") ->
                "https://github.com/AndreyDev86/Pop/releases/download/v1.19.50/Minecraft_1.19.50.apk"
            v.startsWith("1.16") ->
                "https://github.com/AndreyDev86/Pop/releases/download/v1.16.201/Minecraft_1.16.201.apk"
            v.startsWith("1.1.5") || v.startsWith("1.1.") ->
                "https://github.com/AndreyDev86/Pop/releases/download/v1.1.5/Minecraft_PE_1.1.5.apk"
            else ->
                "https://github.com/AndreyDev86/Pop/releases/download/v1.21.20/Minecraft_1.21.20.apk"
        }
    }

    fun getCuratedMcpeHubCatalog(installedVersionNames: Set<String>): List<DownloadableVersion> {
        return listOf(
            DownloadableVersion(
                id = "mcpehub_121",
                title = "Minecraft PE 1.21 (Tricky Trials)",
                versionName = "1.21.0.03",
                tag = "Релиз",
                downloadUrl = getDirectApkMirror("1.21.0"),
                fileName = "Minecraft_1_21_Tricky_Trials.apk",
                sizeBytes = 245_000_000L,
                sizeFormatted = "245 MB",
                releaseNotes = "MCPEHub: Релиз Tricky Trials. Дворцы испытаний (Trial Chambers), моб Бриз, тяжелая булава (Mace), зловещие хранилища и автоматический верстак.",
                publishedAt = "MCPEHub",
                articleUrl = "https://mcpehub.org/download-mcpe/11883-minecraft-1-21.html",
                isInstalledOnDevice = installedVersionNames.contains("1.21") || installedVersionNames.contains("1.21.0") || installedVersionNames.contains("1.21.0.03")
            ),
            DownloadableVersion(
                id = "mcpehub_122",
                title = "Minecraft PE 1.22 (Обновление 1.22)",
                versionName = "1.22.0",
                tag = "Релиз",
                downloadUrl = getDirectApkMirror("1.22.0"),
                fileName = "Minecraft_1_22_MCPEHub.apk",
                sizeBytes = 255_000_000L,
                sizeFormatted = "255 MB",
                releaseNotes = "MCPEHub: Полная версия Minecraft 1.22 с поддержкой Xbox Live и бесплатным редактором скинов.",
                publishedAt = "MCPEHub",
                articleUrl = "https://mcpehub.org/download-mcpe/13631-minecraft-1-22.html",
                isInstalledOnDevice = installedVersionNames.contains("1.22") || installedVersionNames.contains("1.22.0")
            ),
            DownloadableVersion(
                id = "mcpehub_126",
                title = "Minecraft PE 1.26 (Новейшая версия)",
                versionName = "1.26.0",
                tag = "Релиз",
                downloadUrl = getDirectApkMirror("1.26.0"),
                fileName = "Minecraft_1_26_MCPEHub.apk",
                sizeBytes = 260_000_000L,
                sizeFormatted = "260 MB",
                releaseNotes = "MCPEHub: Новые блоки, улучшенная производительность, поддержка сетевой игры без ограничений.",
                publishedAt = "MCPEHub",
                articleUrl = "https://mcpehub.org/download-mcpe/15012-minecraft-1-26.html",
                isInstalledOnDevice = installedVersionNames.contains("1.26") || installedVersionNames.contains("1.26.0")
            ),
            DownloadableVersion(
                id = "mcpehub_26_50",
                title = "Minecraft Preview 26.50.26 (Тестовая)",
                versionName = "26.50.26",
                tag = "Preview",
                downloadUrl = getDirectApkMirror("26.50.26", "Preview"),
                fileName = "Minecraft_Preview_26_50_26.apk",
                sizeBytes = 265_000_000L,
                sizeFormatted = "265 MB",
                releaseNotes = "MCPEHub Beta/Preview: Свежая тестовая сборка с новыми механиками и экспериментальными функциями.",
                publishedAt = "MCPEHub Preview",
                articleUrl = "https://mcpehub.org/download-mcpe/15825-minecraft-26-50-26.html",
                isInstalledOnDevice = installedVersionNames.contains("26.50.26")
            ),
            DownloadableVersion(
                id = "mcpehub_26_44",
                title = "Minecraft PE 26.44.03 (Полная версия)",
                versionName = "26.44.03",
                tag = "Релиз",
                downloadUrl = getDirectApkMirror("26.44.03"),
                fileName = "Minecraft_26_44_03.apk",
                sizeBytes = 250_000_000L,
                sizeFormatted = "250 MB",
                releaseNotes = "MCPEHub: Стабильный релиз. Оптимизация для мобильных устройств, исправление багов и поддержка серверов.",
                publishedAt = "MCPEHub Релиз",
                articleUrl = "https://mcpehub.org/download-mcpe/15785-minecraft-26-44-03.html",
                isInstalledOnDevice = installedVersionNames.contains("26.44.03")
            ),
            DownloadableVersion(
                id = "mcpehub_120",
                title = "Minecraft PE 1.20 (Trails & Tales)",
                versionName = "1.20.0.01",
                tag = "Xbox Live",
                downloadUrl = getDirectApkMirror("1.20.0"),
                fileName = "Minecraft_1_20_Trails_Tales.apk",
                sizeBytes = 215_000_000L,
                sizeFormatted = "215 MB",
                releaseNotes = "MCPEHub: Trails & Tales. Археология, верблюды, Нюхач (Sniffer), бамбуковое дерево, вишневая роща и отделка брони.",
                publishedAt = "MCPEHub",
                articleUrl = "https://mcpehub.org/download-mcpe/9571-minecraft-1-20.html",
                isInstalledOnDevice = installedVersionNames.contains("1.20") || installedVersionNames.contains("1.20.0") || installedVersionNames.contains("1.20.0.01")
            ),
            DownloadableVersion(
                id = "mcpehub_119",
                title = "Minecraft PE 1.19 (The Wild Update)",
                versionName = "1.19.0.05",
                tag = "Релиз",
                downloadUrl = getDirectApkMirror("1.19.0"),
                fileName = "Minecraft_1_19_Wild_Update.apk",
                sizeBytes = 185_000_000L,
                sizeFormatted = "185 MB",
                releaseNotes = "MCPEHub: The Wild Update. Варден (Warden), Древний город, мангровые заросли, головастики, лягушки и Эллей.",
                publishedAt = "MCPEHub",
                articleUrl = "https://mcpehub.org/download-mcpe/7185-minecraft-1-19-wild.html",
                isInstalledOnDevice = installedVersionNames.contains("1.19") || installedVersionNames.contains("1.19.0")
            ),
            DownloadableVersion(
                id = "mcpehub_118",
                title = "Minecraft PE 1.18 (Caves & Cliffs Part II)",
                versionName = "1.18.0.02",
                tag = "Релиз",
                downloadUrl = getDirectApkMirror("1.18.0"),
                fileName = "Minecraft_1_18_Caves_Cliffs.apk",
                sizeBytes = 165_000_000L,
                sizeFormatted = "165 MB",
                releaseNotes = "MCPEHub: Caves & Cliffs II. Новая генерация гор, пышные и карстовые пещеры, увеличенная высота мира до 320 блоков.",
                publishedAt = "MCPEHub",
                articleUrl = "https://mcpehub.org/download-mcpe/6228-minecraft-1-18-caves.html",
                isInstalledOnDevice = installedVersionNames.contains("1.18") || installedVersionNames.contains("1.18.0")
            ),
            DownloadableVersion(
                id = "mcpehub_117",
                title = "Minecraft PE 1.17 (Caves & Cliffs Part I)",
                versionName = "1.17.0.02",
                tag = "Релиз",
                downloadUrl = getDirectApkMirror("1.17.0"),
                fileName = "Minecraft_1_17_Caves_Cliffs.apk",
                sizeBytes = 150_000_000L,
                sizeFormatted = "150 MB",
                releaseNotes = "MCPEHub: Аксолотли, светящиеся спруты, горные козлы, медь, аметистовые жеоды и громоотвод.",
                publishedAt = "MCPEHub",
                articleUrl = "https://mcpehub.org/download-mcpe/4331-minecraft-1-17-caves.html",
                isInstalledOnDevice = installedVersionNames.contains("1.17") || installedVersionNames.contains("1.17.0")
            ),
            DownloadableVersion(
                id = "mcpehub_116",
                title = "Minecraft PE 1.16 (Nether Update)",
                versionName = "1.16.201",
                tag = "Классика",
                downloadUrl = getDirectApkMirror("1.16.201"),
                fileName = "Minecraft_1_16_Nether_Update.apk",
                sizeBytes = 135_000_000L,
                sizeFormatted = "135 MB",
                releaseNotes = "MCPEHub: Легендарное обновление Незера. Незеритовая руда, Пиглины, Хоглины, Багровый лес, Развалины бастиона.",
                publishedAt = "MCPEHub Классика",
                articleUrl = "https://mcpehub.org/download-mcpe/2639-minecraft-1-16-free.html",
                isInstalledOnDevice = installedVersionNames.contains("1.16") || installedVersionNames.contains("1.16.201")
            ),
            DownloadableVersion(
                id = "mcpehub_115_classic",
                title = "Minecraft Pocket Edition 1.1.5 (Classic)",
                versionName = "1.1.5.1",
                tag = "Классика",
                downloadUrl = getDirectApkMirror("1.1.5"),
                fileName = "Minecraft_PE_1.1.5_Classic.apk",
                sizeBytes = 58_000_000L,
                sizeFormatted = "58.2 MB",
                releaseNotes = "MCPEHub: Самая популярная классическая версия MCPE! Моментальный запуск, бесплатные скины, работает на любом телефоне.",
                publishedAt = "MCPEHub Классика",
                articleUrl = "https://mcpehub.org/download-mcpe/10-minecraft-pocket-edition-115.html",
                isInstalledOnDevice = installedVersionNames.contains("1.1.5")
            )
        )
    }
}

