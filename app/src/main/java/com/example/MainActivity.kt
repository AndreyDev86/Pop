package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MinecraftVersion
import com.example.ui.DownloadHubScreen
import com.example.ui.LauncherViewModel
import com.example.ui.theme.BlackBackground
import com.example.ui.theme.BlueDark
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextSubtle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LauncherScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val mcStatus by viewModel.mcStatus.collectAsState()
    val versions by viewModel.versions.collectAsState()
    val selectedVersion by viewModel.selectedVersion.collectAsState()
    val isVersionSheetOpen by viewModel.isVersionSheetOpen.collectAsState()
    val isDownloadHubOpen by viewModel.isDownloadHubOpen.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val refreshRotation = remember { Animatable(0f) }

    // Refresh status when screen resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isDownloadHubOpen) {
        DownloadHubScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeDownloadHub() }
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("launcher_screen"),
        color = BlackBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top System Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
                    .testTag("top_system_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.system_tag),
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    modifier = Modifier.testTag("system_tag_text")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download Versions Hub Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), CircleShape)
                            .clickable { viewModel.openDownloadHub() }
                            .testTag("top_download_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = stringResource(R.string.title_download_hub),
                            tint = CyanAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Share APK Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), CircleShape)
                            .clickable { viewModel.shareApp(context) }
                            .testTag("top_share_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.btn_share),
                            tint = CyanAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0x4DFFFFFF))
                    )
                }
            }

            // Middle Main Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App Icon and Title
                Column(
                    modifier = Modifier.padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0x0DFFFFFF))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
                            .testTag("app_icon_badge"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⛏",
                            fontSize = 32.sp,
                            modifier = Modifier.testTag("pickaxe_icon")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.title_minecraft).uppercase(),
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 7.sp,
                        modifier = Modifier.testTag("app_title")
                    )

                    Text(
                        text = stringResource(R.string.subtitle_bedrock).uppercase(),
                        color = TextSubtle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 5.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("app_subtitle")
                    )
                }

                // Circular Play Button with Glow
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val buttonScale = if (isPressed) 0.94f else 1.0f

                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .testTag("play_button_container"),
                    contentAlignment = Alignment.Center
                ) {
                    // Glow effect behind button
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .scale(1.15f)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0x4D3B82F6),
                                            Color(0x1A2563EB),
                                            Color.Transparent
                                        )
                                    )
                                )
                            }
                    )

                    // Gradient Outer Ring & Button Surface
                    Box(
                        modifier = Modifier
                            .size(206.dp)
                            .scale(buttonScale)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(BlueDark, BluePrimary, CyanAccent)
                                )
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                viewModel.launchGame(context)
                            }
                            .padding(2.dp)
                            .testTag("play_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xE6050B14)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "▶",
                                    color = CyanAccent,
                                    fontSize = 38.sp,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    text = stringResource(R.string.btn_play),
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Auto-Detected Version Selector Card
                val currentDisplayApp = selectedVersion?.appName ?: mcStatus.primaryAppName
                val currentDisplayVersion = selectedVersion?.versionName ?: mcStatus.versionName
                val currentDisplayTag = selectedVersion?.tag ?: mcStatus.tag

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x0AFFFFFF))
                        .border(1.dp, Color(0x2238BDF8), RoundedCornerShape(20.dp))
                        .clickable {
                            viewModel.openVersionSheet()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("version_selector_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = stringResource(R.string.btn_select_version),
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.label_selected_version),
                                    color = TextSubtle,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.2.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (currentDisplayVersion != null) "$currentDisplayApp $currentDisplayVersion" else stringResource(R.string.no_version_selected),
                                        color = if (currentDisplayVersion != null) TextPrimary else TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    currentDisplayTag?.let { tag ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x1A38BDF8))
                                                .border(0.5.dp, Color(0x4D38BDF8), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                color = CyanAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.btn_manage_versions),
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Download Versions Hub Button Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0x1F0284C7), Color(0x1F38BDF8))))
                        .border(1.dp, Color(0x4D38BDF8), RoundedCornerShape(18.dp))
                        .clickable { viewModel.openDownloadHub() }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("open_download_hub_card"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.btn_download_hub),
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Загрузка версий с сайта mcpehub.org",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // System Status Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .testTag("status_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Column: System Status
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.status_system),
                                color = TextSubtle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.5.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val dotColor = if (mcStatus.isInstalled) StatusGreen else StatusAmber
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(dotColor.copy(alpha = if (mcStatus.isInstalled) pulseAlpha else 0.9f))
                                )
                                Text(
                                    text = if (mcStatus.isInstalled) {
                                        stringResource(R.string.status_installed)
                                    } else {
                                        stringResource(R.string.status_not_installed)
                                    },
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Right Column: Version
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_version),
                                color = TextSubtle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = mcStatus.versionName ?: stringResource(R.string.version_none),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom Actions Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bottom Action Buttons (Refresh & Share)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Refresh Status Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x0DFFFFFF))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                            .clickable(enabled = !isScanning) {
                                coroutineScope.launch {
                                    refreshRotation.animateTo(
                                        targetValue = refreshRotation.value + 360f,
                                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                                    )
                                }
                                viewModel.refreshStatus()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .testTag("refresh_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CyanAccent
                            )
                        } else {
                            Text(
                                text = "↻",
                                color = TextSecondary,
                                fontSize = 18.sp,
                                modifier = Modifier.rotate(refreshRotation.value)
                            )
                        }
                        Text(
                            text = (if (isScanning) stringResource(R.string.status_scanning) else stringResource(R.string.btn_refresh)).uppercase(),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Share Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x0DFFFFFF))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                            .clickable { viewModel.shareApp(context) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("bottom_share_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.btn_share),
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.btn_share).uppercase(),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // Sleek Home Indicator Bar
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x33FFFFFF))
                )
            }
        }
    }

    // Version Manager Bottom Sheet (Auto-detected versions)
    if (isVersionSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { viewModel.closeVersionSheet() },
            sheetState = sheetState,
            containerColor = Color(0xFF090A10),
            contentColor = TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x33FFFFFF))
                )
            },
            modifier = Modifier.testTag("version_manager_sheet")
        ) {
            VersionManagerContent(
                versions = versions,
                selectedVersion = selectedVersion,
                isScanning = isScanning,
                onSelectVersion = { id ->
                    viewModel.selectVersion(id)
                },
                onRescan = {
                    viewModel.refreshStatus()
                },
                onInstallFromGooglePlay = {
                    viewModel.openGooglePlay(context)
                },
                onOpenDownloadHub = {
                    viewModel.closeVersionSheet()
                    viewModel.openDownloadHub()
                },
                onClose = {
                    viewModel.closeVersionSheet()
                }
            )
        }
    }
}

@Composable
fun VersionManagerContent(
    versions: List<MinecraftVersion>,
    selectedVersion: MinecraftVersion?,
    isScanning: Boolean,
    onSelectVersion: (Long) -> Unit,
    onRescan: () -> Unit,
    onInstallFromGooglePlay: () -> Unit,
    onOpenDownloadHub: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.title_version_manager),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = stringResource(R.string.subtitle_version_manager),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.btn_close),
                    tint = TextSecondary
                )
            }
        }

        // Action Buttons Row: Rescan & Download from GitHub
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Rescan / Auto-detect trigger
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                    .clickable(enabled = !isScanning) { onRescan() }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag("rescan_versions_button"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = CyanAccent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScanning) stringResource(R.string.status_scanning) else stringResource(R.string.btn_rescan),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Download Hub Button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1A38BDF8))
                    .border(1.dp, Color(0x4D38BDF8), RoundedCornerShape(14.dp))
                    .clickable { onOpenDownloadHub() }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag("sheet_open_download_hub_btn"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_download_hub),
                    color = CyanAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Version List or Empty State
        if (versions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x08FFFFFF))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📦",
                        fontSize = 32.sp
                    )
                    Text(
                        text = stringResource(R.string.empty_versions_title),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.empty_versions_desc),
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(BlueDark, BluePrimary)))
                                .clickable { onOpenDownloadHub() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.btn_download_hub),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
                                .clickable { onInstallFromGooglePlay() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Google Play",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(versions, key = { it.id }) { ver ->
                    val isCurrent = ver.id == selectedVersion?.id
                    VersionListItem(
                        version = ver,
                        isSelected = isCurrent,
                        onSelect = { onSelectVersion(ver.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun VersionListItem(
    version: MinecraftVersion,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) CyanAccent else Color(0x1AFFFFFF)
    val bgColor = if (isSelected) Color(0x1F2563EB) else Color(0x0DFFFFFF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("version_item_${version.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Radio Indicator
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(
                        1.5.dp,
                        if (isSelected) CyanAccent else TextMuted,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(CyanAccent)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = version.appName,
                        color = if (isSelected) Color.White else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                    Text(
                        text = "v${version.versionName}",
                        color = if (isSelected) CyanAccent else TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tagBgColor = when (version.tag) {
                        "Preview" -> Color(0x26F59E0B)
                        "Education" -> Color(0x268B5CF6)
                        "Pojav" -> Color(0x26EC4899)
                        "Клон", "Clone" -> Color(0x2638BDF8)
                        "Оригинал", "Bedrock" -> Color(0x2610B981)
                        else -> Color(0x2610B981)
                    }
                    val tagTextColor = when (version.tag) {
                        "Preview" -> StatusAmber
                        "Education" -> Color(0xFFA78BFA)
                        "Pojav" -> Color(0xFFF472B6)
                        "Клон", "Clone" -> CyanAccent
                        "Оригинал", "Bedrock" -> StatusGreen
                        else -> StatusGreen
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tagBgColor)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = version.tag,
                            fontSize = 9.sp,
                            color = tagTextColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x14FFFFFF))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = version.packageName,
                            fontSize = 8.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isSelected) {
                        Text(
                            text = stringResource(R.string.active_badge),
                            color = CyanAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x14FFFFFF))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = stringResource(R.string.auto_detected_badge),
                color = CyanAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LauncherScreenPreview() {
    MyApplicationTheme {
        LauncherScreen()
    }
}
