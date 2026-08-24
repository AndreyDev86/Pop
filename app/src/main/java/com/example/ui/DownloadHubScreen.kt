package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.DownloadState
import com.example.data.DownloadableVersion
import com.example.ui.theme.BlackBackground
import com.example.ui.theme.BlueDark
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextSubtle

@Composable
fun DownloadHubScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val releases by viewModel.downloadableVersions.collectAsState()
    val isLoading by viewModel.isLoadingReleases.collectAsState()
    val siteUrl by viewModel.siteUrl.collectAsState()
    val activeFilter by viewModel.downloadFilter.collectAsState()
    val searchQuery by viewModel.downloadSearchQuery.collectAsState()
    val activeTab by viewModel.activeHubTab.collectAsState()
    val activeWebUrl by viewModel.activeWebUrl.collectAsState()

    var isSiteDialogOpen by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webProgress by remember { mutableStateOf(0) }
    var isWebLoading by remember { mutableStateOf(false) }

    // Intercept hardware back button when on web tab
    BackHandler {
        if (activeTab == 1 && webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else if (activeTab == 1) {
            viewModel.setActiveHubTab(0)
        } else {
            onBack()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("download_hub_screen"),
        color = BlackBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), CircleShape)
                            .clickable {
                                if (activeTab == 1) {
                                    viewModel.setActiveHubTab(0)
                                } else {
                                    onBack()
                                }
                            }
                            .testTag("download_hub_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_close),
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.title_download_hub),
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (activeTab == 0) stringResource(R.string.subtitle_download_hub) else activeWebUrl,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeTab == 1) {
                        // Open in external browser
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, Color(0x26FFFFFF), CircleShape)
                                .clickable {
                                    viewModel.openMcpeHubArticle(context, activeWebUrl, openExternal = true)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.btn_open_in_browser),
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), CircleShape)
                            .clickable(enabled = !isLoading && !isWebLoading) {
                                if (activeTab == 0) {
                                    viewModel.refreshDownloadableVersions()
                                } else {
                                    webViewInstance?.reload()
                                }
                            }
                            .testTag("download_hub_refresh_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading || isWebLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CyanAccent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.btn_refresh),
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Dual Tab Switcher: Catalog vs Live MCPEHub Site
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0EFFFFFF))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 0: Catalog
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (activeTab == 0) Modifier.background(Brush.linearGradient(listOf(BlueDark, BluePrimary)))
                            else Modifier.background(Color.Transparent)
                        )
                        .clickable { viewModel.setActiveHubTab(0) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.tab_catalog),
                        color = if (activeTab == 0) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Tab 1: Live Site
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (activeTab == 1) Modifier.background(Brush.linearGradient(listOf(BlueDark, BluePrimary)))
                            else Modifier.background(Color.Transparent)
                        )
                        .clickable { viewModel.setActiveHubTab(1) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = if (activeTab == 1) Color.White else CyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.tab_mcpehub_site),
                            color = if (activeTab == 1) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            if (activeTab == 0) {
                // CATALOG VIEW
                // Source Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0F38BDF8))
                        .border(1.dp, Color(0x2E38BDF8), RoundedCornerShape(12.dp))
                        .clickable { isSiteDialogOpen = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("site_source_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = siteUrl,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_change_repo),
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Search Bar
                val keyboardController = LocalSoftwareKeyboardController.current
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setDownloadSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("download_search_field"),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_versions_hint),
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setDownloadSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = Color(0x26FFFFFF),
                        focusedContainerColor = Color(0x0DFFFFFF),
                        unfocusedContainerColor = Color(0x08FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                )

                // Category Filter Chips
                val filters = listOf(
                    "Все" to stringResource(R.string.filter_all),
                    "Релиз" to stringResource(R.string.filter_releases),
                    "Preview" to stringResource(R.string.filter_betas),
                    "Xbox Live" to stringResource(R.string.filter_xbox),
                    "Классика" to stringResource(R.string.filter_classics)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEach { (filterKey, filterLabel) ->
                        val isSelected = activeFilter == filterKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0x3338BDF8) else Color(0x0DFFFFFF))
                                .border(
                                    1.dp,
                                    if (isSelected) CyanAccent else Color(0x1FFFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.setDownloadFilter(filterKey) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("filter_chip_$filterKey")
                        ) {
                            Text(
                                text = filterLabel,
                                color = if (isSelected) CyanAccent else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Versions List
                val filteredList = releases.filter { version ->
                    val matchesFilter = when (activeFilter) {
                        "Все" -> true
                        "Preview" -> version.tag.contains("Preview", ignoreCase = true) || version.tag.contains("Бета", ignoreCase = true)
                        "Xbox Live" -> version.tag.contains("Xbox", ignoreCase = true) || version.title.contains("Xbox", ignoreCase = true)
                        "Классика" -> version.tag.contains("Классика", ignoreCase = true) || version.versionName.startsWith("1.1.") || version.versionName.startsWith("1.16")
                        "Релиз" -> version.tag.contains("Релиз", ignoreCase = true)
                        else -> version.tag.equals(activeFilter, ignoreCase = true)
                    }

                    val matchesSearch = searchQuery.isBlank() ||
                            version.title.contains(searchQuery, ignoreCase = true) ||
                            version.versionName.contains(searchQuery, ignoreCase = true) ||
                            version.tag.contains(searchQuery, ignoreCase = true) ||
                            version.releaseNotes.contains(searchQuery, ignoreCase = true)

                    matchesFilter && matchesSearch
                }

                if (filteredList.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = "📥", fontSize = 40.sp)
                            Text(
                                text = stringResource(R.string.empty_downloads_title),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.empty_downloads_desc),
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(listOf(BlueDark, BluePrimary)))
                                    .clickable { viewModel.refreshDownloadableVersions() }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.btn_refresh),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredList, key = { it.id }) { version ->
                            DownloadVersionCard(
                                version = version,
                                viewModel = viewModel,
                                onDownloadClick = {
                                    viewModel.startDownload(version)
                                },
                                onCancelClick = {
                                    viewModel.cancelDownload(version.id)
                                },
                                onInstallClick = {
                                    viewModel.installDownloadedApk(context, version)
                                },
                                onOpenArticleClick = {
                                    viewModel.openMcpeHubArticle(context, version.articleUrl, openExternal = false)
                                },
                                onOpenExternalClick = {
                                    viewModel.openMcpeHubArticle(context, version.articleUrl, openExternal = true)
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            } else {
                // IN-APP LIVE MCPEHUB WEBVIEW
                Column(modifier = Modifier.fillMaxSize()) {
                    // Browser control bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x18000000))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack() },
                                enabled = canGoBack
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (canGoBack) CyanAccent else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { if (webViewInstance?.canGoForward() == true) webViewInstance?.goForward() },
                                enabled = canGoForward
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (canGoForward) CyanAccent else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = activeWebUrl,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        TextButton(
                            onClick = { viewModel.setActiveHubTab(0) }
                        ) {
                            Text("В каталог", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isWebLoading) {
                        LinearProgressIndicator(
                            progress = { webProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = CyanAccent,
                            trackColor = Color.Transparent
                        )
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebLoading = false
                                        canGoBack = view?.canGoBack() ?: false
                                        canGoForward = view?.canGoForward() ?: false
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        url: String?
                                    ): Boolean {
                                        if (url == null) return false
                                        if (url.endsWith(".apk", ignoreCase = true) || url.contains("/engine/dlfile.php")) {
                                            val name = "Minecraft_${System.currentTimeMillis()}.apk"
                                            Toast.makeText(ctx, "Загрузка начата через Лаунчер…", Toast.LENGTH_SHORT).show()
                                            viewModel.startDirectDownload(
                                                id = "web_dl_${System.currentTimeMillis()}",
                                                url = url,
                                                fileName = name,
                                                referer = view?.url ?: "https://mcpehub.org/"
                                            )
                                            return true
                                        }
                                        return false
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        webProgress = newProgress
                                        isWebLoading = newProgress < 100
                                        canGoBack = view?.canGoBack() ?: false
                                        canGoForward = view?.canGoForward() ?: false
                                    }
                                }

                                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                    val name = "Minecraft_Download.apk"
                                    Toast.makeText(ctx, "Загрузка началась через лаунчер…", Toast.LENGTH_SHORT).show()
                                    viewModel.startDirectDownload(
                                        id = "web_dl_${System.currentTimeMillis()}",
                                        url = url,
                                        fileName = name,
                                        referer = getUrl() ?: "https://mcpehub.org/"
                                    )
                                }

                                loadUrl(activeWebUrl)
                                webViewInstance = this
                            }
                        },
                        update = { webView ->
                            if (webView.url != activeWebUrl && activeWebUrl.isNotBlank()) {
                                webView.loadUrl(activeWebUrl)
                            }
                            webViewInstance = webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Change MCPEHub URL Dialog
    if (isSiteDialogOpen) {
        var inputUrl by remember { mutableStateOf(siteUrl) }

        AlertDialog(
            onDismissRequest = { isSiteDialogOpen = false },
            containerColor = Color(0xFF0F1420),
            title = {
                Text(
                    text = stringResource(R.string.dialog_repo_title),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_repo_desc),
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        singleLine = true,
                        placeholder = { Text("https://mcpehub.org/download-mcpe/", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            viewModel.setCustomSiteUrl(inputUrl)
                            isSiteDialogOpen = false
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.btn_save),
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { isSiteDialogOpen = false }) {
                    Text(text = stringResource(R.string.btn_cancel), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun DownloadVersionCard(
    version: DownloadableVersion,
    viewModel: LauncherViewModel,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onInstallClick: () -> Unit,
    onOpenArticleClick: () -> Unit,
    onOpenExternalClick: () -> Unit
) {
    val downloadStateFlow = remember(version.id) {
        viewModel.getDownloadStateFlow(version)
    }
    val downloadState by downloadStateFlow.collectAsState()

    val tagBgColor = when (version.tag) {
        "Preview", "Бета" -> Color(0x26F59E0B)
        "Xbox Live" -> Color(0x2610B981)
        "Education" -> Color(0x268B5CF6)
        "Классика" -> Color(0x26F97316)
        "Клон" -> Color(0x2638BDF8)
        else -> Color(0x260284C7)
    }

    val tagTextColor = when (version.tag) {
        "Preview", "Бета" -> Color(0xFFFBBF24)
        "Xbox Live" -> StatusGreen
        "Education" -> Color(0xFFA78BFA)
        "Классика" -> Color(0xFFFB923C)
        "Клон" -> CyanAccent
        else -> CyanAccent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0CFFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("download_card_${version.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Title and Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = version.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "v${version.versionName}",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(text = "•", color = TextSubtle, fontSize = 12.sp)
                        Text(text = version.sizeFormatted, color = TextSecondary, fontSize = 12.sp)
                        if (version.publishedAt.isNotBlank()) {
                            Text(text = "•", color = TextSubtle, fontSize = 12.sp)
                            Text(text = version.publishedAt, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (version.isInstalledOnDevice) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x2610B981))
                                .border(0.5.dp, StatusGreen, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.status_installed_on_device),
                                color = StatusGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tagBgColor)
                            .border(0.5.dp, tagTextColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = version.tag,
                            color = tagTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Release Notes Description
            if (version.releaseNotes.isNotBlank()) {
                Text(
                    text = version.releaseNotes,
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Progress Bar & Action Buttons
            when (val state = downloadState) {
                is DownloadState.Downloading -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = state.progress,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "progress"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = CyanAccent
                                )
                                Text(
                                    text = if (state.totalBytes > 0) {
                                        "${stringResource(R.string.btn_downloading)}: ${(state.progress * 100).toInt()}%"
                                    } else {
                                        "${stringResource(R.string.btn_downloading)}…"
                                    },
                                    color = CyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val mbDownloaded = state.downloadedBytes / (1024.0 * 1024.0)
                            val mbTotal = state.totalBytes / (1024.0 * 1024.0)
                            Text(
                                text = if (state.totalBytes > 0) {
                                    String.format("%.1f / %.1f MB", mbDownloaded, mbTotal)
                                } else {
                                    String.format("%.1f MB", mbDownloaded)
                                },
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CyanAccent,
                            trackColor = Color(0x26FFFFFF)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = stringResource(R.string.btn_cancel),
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { onCancelClick() }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }

                is DownloadState.Downloaded -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.status_ready_to_install),
                                color = StatusGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Article in webview
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x14FFFFFF))
                                    .clickable { onOpenArticleClick() }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "Статья", color = TextSecondary, fontSize = 11.sp)
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            // Re-download button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x14FFFFFF))
                                    .clickable { onDownloadClick() }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "↻", color = TextSecondary, fontSize = 14.sp)
                            }

                            // Install Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF10B981)))
                                    )
                                    .clickable { onInstallClick() }
                                    .padding(horizontal = 16.dp, vertical = 7.dp)
                                    .testTag("install_btn_${version.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InstallMobile,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.btn_install),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                is DownloadState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.status_download_failed)}: ${state.message}",
                            color = StatusAmber,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Direct 1-tap open in built-in webview
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x2238BDF8))
                                    .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .clickable { onOpenArticleClick() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Скачать с сайта MCPEHub",
                                    color = CyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x14FFFFFF))
                                    .clickable { onDownloadClick() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Повторить",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                is DownloadState.Idle -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // MCPEHub article preview button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x12FFFFFF))
                                .clickable { onOpenArticleClick() }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(R.string.btn_open_site),
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // External browser icon
                            IconButton(
                                onClick = { onOpenExternalClick() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.btn_open_in_browser),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Download Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(listOf(BlueDark, BluePrimary, CyanAccent.copy(alpha = 0.8f)))
                                    )
                                    .clickable { onDownloadClick() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("download_btn_${version.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.btn_download),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
