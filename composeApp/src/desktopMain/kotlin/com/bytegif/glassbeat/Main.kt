package com.bytegif.glassbeat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.data.model.AppTab
import com.bytegif.glassbeat.service.DesktopAudioPlayer
import com.bytegif.glassbeat.service.SleepTimerManager
import com.bytegif.glassbeat.ui.MainViewModel
import com.bytegif.glassbeat.ui.screens.EditMetadataDialog
import com.bytegif.glassbeat.ui.screens.EqualizerScreen
import com.bytegif.glassbeat.ui.screens.LibraryScreen
import com.bytegif.glassbeat.ui.screens.ListenHistoryScreen
import com.bytegif.glassbeat.ui.screens.MiniPlayerBar
import com.bytegif.glassbeat.ui.screens.NowPlayingFullOverlay
import com.bytegif.glassbeat.ui.screens.SettingsScreen
import com.bytegif.glassbeat.ui.screens.SongsScreen
import com.bytegif.glassbeat.ui.theme.GlassBeatTheme
import com.bytegif.glassbeat.ui.theme.GlassTheme
import com.bytegif.glassbeat.ui.theme.glassCard
import com.bytegif.glassbeat.ui.theme.glassPill
import com.bytegif.glassbeat.ui.theme.loadGlassBeatDockIconBitmap
import com.bytegif.glassbeat.ui.theme.loadGlassBeatLogoBitmap
import com.bytegif.glassbeat.ui.theme.rememberMainBlurRadius
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Taskbar
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

private val externalOpenFilesFlow = MutableSharedFlow<List<File>>(
    replay = 1,
    extraBufferCapacity = 8,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)

private fun dispatchExternalFiles(files: List<File>) {
    val valid = files.filter { it.exists() }
    if (valid.isNotEmpty()) {
        DesktopAudioPlayer.markExternalPlaybackTriggered()
        externalOpenFilesFlow.tryEmit(valid)
    }
}

fun main(args: Array<String>) {
    // 1. Register macOS Finder "Open With" / Dock drop handler immediately at process startup
    // so cold-launch kAEOpenDocuments AppleEvents are captured before the Compose window finishes initializing.
    try {
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
                desktop.setOpenFileHandler { event ->
                    val files = event.files.orEmpty()
                    dispatchExternalFiles(files)
                }
            }
        }
    } catch (_: Exception) {
    }

    // 2. Parse command-line file arguments (Windows, Linux, or macOS CLI `open -a GlassBeat --args <file>`)
    if (args.isNotEmpty()) {
        val cliFiles = args.mapNotNull { rawArg ->
            val trimmed = rawArg.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("-psn_") || trimmed.startsWith("--")) {
                null
            } else {
                runCatching {
                    if (trimmed.startsWith("file://")) File(URI(trimmed)) else File(trimmed)
                }.getOrNull()?.takeIf { it.exists() }
            }
        }
        dispatchExternalFiles(cliFiles)
    }

    // 3. Set macOS Dock Icon to the Apple HIG-proportioned GlassBeat Dock icon (80.5% squircle with standard margin)
    try {
        if (Taskbar.isTaskbarSupported()) {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                val iconStream = MainViewModel::class.java.getResourceAsStream("/glassbeat_dock_icon.png")
                    ?: MainViewModel::class.java.getResourceAsStream("/glassbeat_logo.png")
                if (iconStream != null) {
                    val awtImg = iconStream.use { ImageIO.read(it) }
                    if (awtImg != null) {
                        taskbar.iconImage = awtImg
                    }
                }
            }
        }
    } catch (_: Exception) {
    }

    application {
        val viewModel = remember { MainViewModel() }
        val prefs by AppStorage.state.collectAsState()
        val dockIconBitmap = remember { loadGlassBeatDockIconBitmap() }
        val windowState = rememberWindowState(
            size = DpSize(1180.dp, 780.dp),
            position = WindowPosition.Aligned(Alignment.Center)
        )

        Window(
            onCloseRequest = {
                DesktopAudioPlayer.savePlaybackState()
                exitApplication()
            },
            state = windowState,
            title = "GlassBeat",
            icon = dockIconBitmap?.let { BitmapPainter(it) },
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        event.key == Key.Escape -> {
                            if (viewModel.editingSong.value != null) {
                                viewModel.closeMetadataEditor()
                                true
                            } else if (viewModel.isNowPlayingExpanded.value) {
                                viewModel.setNowPlayingExpanded(false)
                                true
                            } else if (viewModel.selectedSongIds.value.isNotEmpty()) {
                                viewModel.clearSongSelection()
                                true
                            } else false
                        }
                        event.isMetaPressed && event.key == Key.F -> {
                            viewModel.switchTab(AppTab.SONGS)
                            viewModel.toggleSearchVisibility()
                            true
                        }
                        else -> false
                    }
                } else false
            }
        ) {
            LaunchedEffect(prefs.isDarkMode) {
                window.minimumSize = Dimension(900, 620)
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty(
                    "apple.awt.windowAppearance",
                    if (prefs.isDarkMode) "NSAppearanceNameVibrantDark" else "NSAppearanceNameVibrantLight"
                )
            }

            // Handle Finder "Open With", CLI args, and Drag-and-Drop onto the window
            LaunchedEffect(viewModel) {
                // Attach AWT DropTarget so dragging audio files from Finder into the window plays them immediately
                try {
                    window.dropTarget = DropTarget(
                        window,
                        DnDConstants.ACTION_COPY,
                        object : DropTargetAdapter() {
                            override fun drop(dtde: DropTargetDropEvent) {
                                try {
                                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                                        @Suppress("UNCHECKED_CAST")
                                        val dropped = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                                        if (!dropped.isNullOrEmpty()) {
                                            dispatchExternalFiles(dropped)
                                        }
                                        dtde.dropComplete(true)
                                    } else {
                                        dtde.rejectDrop()
                                    }
                                } catch (_: Exception) {
                                    dtde.dropComplete(false)
                                }
                            }
                        },
                        true
                    )
                } catch (_: Exception) {
                }

                externalOpenFilesFlow.collect { files ->
                    if (files.isNotEmpty()) {
                        viewModel.openExternalFiles(files)
                        try {
                            window.toFront()
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                                Desktop.getDesktop().requestForeground(true)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            GlassBeatTheme(isDarkMode = prefs.isDarkMode) {
                GlassBeatDesktopApp(viewModel)
            }
        }
    }
}

@Composable
fun GlassBeatDesktopApp(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val currentTab by viewModel.currentTab.collectAsState()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsState()
    val editingSong by viewModel.editingSong.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    val mainBlurRadius = rememberMainBlurRadius(extraCondition = editingSong != null)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.background,
                        colors.backgroundGradientEnd
                    )
                )
            )
    ) {
        // Main Layout: Glassmorphic Sidebar + Active Screen (blurs when any modal is active)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .blur(mainBlurRadius)
                .padding(top = if (com.bytegif.glassbeat.util.PlatformUtils.isMacOs) 28.dp else 8.dp)
        ) {
            GlassSidebar(
                currentTab = currentTab,
                onSelectTab = { viewModel.switchTab(it) },
                onOpenNowPlaying = { viewModel.setNowPlayingExpanded(true) }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                    when (tab) {
                        AppTab.SONGS -> SongsScreen(viewModel)
                        AppTab.EQUALIZER -> EqualizerScreen(viewModel)
                        AppTab.LIBRARY -> LibraryScreen(viewModel)
                        AppTab.HISTORY -> ListenHistoryScreen(viewModel)
                        AppTab.SETTINGS -> SettingsScreen(viewModel)
                    }
                }

                // Floating Mini Player Bar with blurred glass backdrop at the bottom of the content pane
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    MiniPlayerBar(viewModel = viewModel)
                }
            }
        }

        // Full-Screen Expanded Now Playing Overlay
        AnimatedVisibility(
            visible = isNowPlayingExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            NowPlayingFullOverlay(viewModel = viewModel)
        }

        // Metadata Tag Editor Modal (with blurred background)
        editingSong?.let { targetSong ->
            EditMetadataDialog(
                song = targetSong,
                viewModel = viewModel,
                isOverNowPlaying = isNowPlayingExpanded,
                onDismiss = { viewModel.closeMetadataEditor() }
            )
        }

        // Floating Glass Toast Notification
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .glassPill()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = toastMessage ?: "",
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun GlassSidebar(
    currentTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val colors = GlassTheme.colors
    val sleepActive by SleepTimerManager.isActive.collectAsState()
    val sleepDisplay by SleepTimerManager.display.collectAsState()
    val hasActiveSession by DesktopAudioPlayer.hasActiveSession.collectAsState()
    val logoBitmap = remember { loadGlassBeatLogoBitmap() }

    val navItems = listOf(
        Triple(AppTab.SONGS, "Songs", Icons.Default.MusicNote),
        Triple(AppTab.EQUALIZER, "Equalizer", Icons.Default.Equalizer),
        Triple(AppTab.LIBRARY, "Library", Icons.Default.LibraryMusic),
        Triple(AppTab.HISTORY, "History", Icons.Default.History),
        Triple(AppTab.SETTINGS, "Settings", Icons.Default.Settings)
    )

    Column(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight()
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
            .glassCard(cornerRadius = 24.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Brand Header with Official GlassBeat Logo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap,
                        contentDescription = "GlassBeat Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(percent = 23))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "GlassBeat",
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = com.bytegif.glassbeat.util.PlatformUtils.editionTitle,
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Navigation Items
            navItems.forEach { (tab, label, icon) ->
                val selected = tab == currentTab
                SidebarNavRow(
                    icon = icon,
                    label = label,
                    selected = selected,
                    onClick = { onSelectTab(tab) }
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sleepActive && sleepDisplay.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(cornerRadius = 14.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Sleep Timer", color = colors.onSurfaceVariant, fontSize = 10.sp)
                        Text(
                            text = sleepDisplay,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (hasActiveSession) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accent.copy(alpha = 0.15f))
                        .clickable { onOpenNowPlaying() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open Now Playing",
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarNavRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) colors.accent else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
