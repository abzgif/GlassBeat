package com.bytegif.glassbeat.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.data.model.AppTab
import com.bytegif.glassbeat.data.model.Folder
import com.bytegif.glassbeat.data.repository.AudioRepository
import com.bytegif.glassbeat.ui.MainViewModel
import com.bytegif.glassbeat.ui.theme.GlassModalOverlay
import com.bytegif.glassbeat.ui.theme.GlassTheme
import com.bytegif.glassbeat.ui.theme.glassCard
import com.bytegif.glassbeat.ui.theme.loadGlassBeatLogoBitmap
import com.bytegif.glassbeat.util.AppUpdater
import com.bytegif.glassbeat.util.UpdateCheckState
import java.io.File
import javax.swing.JFileChooser

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val prefs by AppStorage.state.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val updateState by AppUpdater.state.collectAsState()
    val logoBitmap = remember { loadGlassBeatLogoBitmap() }

    var showExcludedDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showUpdateModal by remember { mutableStateOf(false) }

    val excludedCount = prefs.excludedFolders.size
    val excludedSubtitle = when {
        excludedCount == 0 -> "No folders excluded"
        excludedCount == 1 -> "1 folder excluded"
        else -> "$excludedCount folders excluded"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerRadius = 20.dp)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    color = colors.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure library scanning, folders, audio behavior, updates, and appearance",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Library & Folders Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerRadius = 22.dp)
                .padding(20.dp)
        ) {
            Text(
                text = "MUSIC LIBRARY & FOLDERS",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            SettingsClickableRow(
                icon = Icons.Default.Refresh,
                title = "Scan for Music",
                subtitle = "Run recursive background scan of your music directories",
                onClick = { viewModel.scanMusicLibrary() }
            )

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

            SettingsClickableRow(
                icon = Icons.Default.FolderOff,
                title = "Excluded Folders",
                subtitle = excludedSubtitle,
                onClick = { showExcludedDialog = true }
            )

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Monitored Music Directories",
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Folders scanned for local MP3, FLAC, M4A, WAV, OGG, and OPUS files",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = {
                        val chooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = "Select Music Folder to Add"
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            chooser.selectedFile?.absolutePath?.let { viewModel.addMusicDirectory(it) }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Folder", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            prefs.musicDirectories.forEach { dirPath ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = dirPath,
                        color = colors.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (prefs.musicDirectories.size > 1) {
                        IconButton(
                            onClick = { viewModel.removeMusicDirectory(dirPath) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Folder",
                                tint = colors.danger,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Audio & Playback Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerRadius = 22.dp)
                .padding(20.dp)
        ) {
            Text(
                text = "AUDIO & PLAYBACK",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            SettingsClickableRow(
                icon = Icons.Default.Equalizer,
                title = "Equalizer",
                subtitle = "Open 5-band parametric equalizer and acoustic presets",
                onClick = { viewModel.switchTab(AppTab.EQUALIZER) }
            )

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

            SettingsSwitchRow(
                icon = Icons.Default.VolumeUp,
                title = "Simultaneous Audio Playback",
                subtitle = "Play alongside other macOS applications without exclusive audio focus",
                checked = prefs.simultaneousAudio,
                onCheckedChange = { viewModel.setSimultaneousAudio(it) }
            )

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

            SettingsClickableRow(
                icon = Icons.Default.DeleteSweep,
                title = "Clear Listen History",
                subtitle = "Remove all recorded playback history and resume positions",
                onClick = { showClearHistoryDialog = true }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Appearance, Updates & Open Source Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerRadius = 22.dp)
                .padding(20.dp)
        ) {
            Text(
                text = "APPEARANCE, UPDATES & OPEN SOURCE",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            SettingsSwitchRow(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                subtitle = "Use deep obsidian glass palette (#050505)",
                checked = prefs.isDarkMode,
                onCheckedChange = { viewModel.setDarkMode(it) }
            )

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

            SettingsClickableRow(
                icon = Icons.Default.SystemUpdateAlt,
                title = "Check for Updates",
                subtitle = "Download new releases from GitHub and automatically reopen GlassBeat",
                onClick = {
                    showUpdateModal = true
                    AppUpdater.checkForUpdates()
                }
            )

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { AppUpdater.openGitHubRepo() }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "GitHub Repository",
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open Source on GitHub",
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = AppUpdater.GITHUB_REPO_URL,
                        color = colors.accent,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open in Browser",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap,
                        contentDescription = "GlassBeat App Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(percent = 23))
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GlassBeat for macOS",
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Kotlin Multiplatform / Compose for Desktop",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "v${AppUpdater.CURRENT_VERSION} (18)",
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    // Scanning Progress Dialog
    if (isScanning) {
        val (scanned, total) = scanProgress
        GlassModalOverlay(onDismissRequest = {}) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .glassCard(cornerRadius = 24.dp, elevated = true)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scanning Library...",
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (total > 0) "Registering $scanned of $total files..." else "Discovering audio files...",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (scanned.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        color = colors.accent,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(color = colors.accent, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // Excluded Folders Dialog
    if (showExcludedDialog) {
        ExcludedFoldersModal(
            currentExclusions = prefs.excludedFolders,
            onApply = { newSet ->
                viewModel.setExcludedFolders(newSet)
                showExcludedDialog = false
            },
            onDismiss = { showExcludedDialog = false }
        )
    }

    // Clear History Confirmation Dialog
    if (showClearHistoryDialog) {
        GlassModalOverlay(onDismissRequest = { showClearHistoryDialog = false }) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .glassCard(cornerRadius = 24.dp, elevated = true)
                    .padding(24.dp)
            ) {
                Text(
                    text = "Clear Listen History?",
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "This will permanently remove all recently played songs and saved playback positions.",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.clearListenHistory()
                            showClearHistoryDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                    ) {
                        Text("Clear", color = colors.onPrimary)
                    }
                }
            }
        }
    }

    // Check for Updates Modal
    if (showUpdateModal) {
        SoftwareUpdateModal(
            state = updateState,
            onDismiss = {
                if (updateState !is UpdateCheckState.Downloading &&
                    updateState !is UpdateCheckState.InstallingAndRestarting
                ) {
                    showUpdateModal = false
                    AppUpdater.resetState()
                }
            }
        )
    }
}

@Composable
private fun SoftwareUpdateModal(
    state: UpdateCheckState,
    onDismiss: () -> Unit
) {
    val colors = GlassTheme.colors
    val logoBitmap = remember { loadGlassBeatLogoBitmap() }

    GlassModalOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .glassCard(cornerRadius = 26.dp, elevated = true)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = "GlassBeat",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(percent = 23))
                )
                Spacer(Modifier.height(12.dp))
            }

            when (state) {
                is UpdateCheckState.Idle,
                is UpdateCheckState.Checking -> {
                    Text(
                        text = "Checking for Updates...",
                        color = colors.onSurface,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Connecting to github.com/abzgif/mac-glassbeat",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    CircularProgressIndicator(color = colors.accent)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }

                is UpdateCheckState.UpToDate -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "GlassBeat is Up to Date",
                        color = colors.onSurface,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "You are running the latest version (v${state.currentVersion}).",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { AppUpdater.openGitHubRepo() }) {
                            Text("View on GitHub")
                        }
                        Button(onClick = onDismiss) {
                            Text("Done")
                        }
                    }
                }

                is UpdateCheckState.UpdateAvailable -> {
                    Text(
                        text = "Update Available: v${state.latestVersion}",
                        color = colors.onSurface,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Current version: v${state.currentVersion}",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 14.dp)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = state.releaseTitle,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.releaseNotes,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Later")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { AppUpdater.downloadAndApplyUpdate(state) }
                        ) {
                            Text("Update & Reopen Now")
                        }
                    }
                }

                is UpdateCheckState.Downloading -> {
                    Text(
                        text = "Downloading GlassBeat v${state.latestVersion}...",
                        color = colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    val progress = if (state.totalBytes > 0L) {
                        (state.downloadedBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val dlMb = String.format("%.1f", state.downloadedBytes / (1024f * 1024f))
                    val totalMb = if (state.totalBytes > 0L) {
                        String.format("%.1f MB", state.totalBytes / (1024f * 1024f))
                    } else "..."
                    LinearProgressIndicator(
                        progress = { progress },
                        color = colors.accent,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$dlMb MB / $totalMb (${(progress * 100).toInt()}%)",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                is UpdateCheckState.InstallingAndRestarting -> {
                    Text(
                        text = "Installing & Reopening...",
                        color = colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Applying v${state.latestVersion}. GlassBeat will reopen automatically in a moment.",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(color = colors.accent)
                }

                is UpdateCheckState.Error -> {
                    Text(
                        text = "Update Check Failed",
                        color = colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { AppUpdater.openUrl("${AppUpdater.GITHUB_REPO_URL}/releases") }) {
                            Text("Open GitHub Releases")
                        }
                        Button(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ExcludedFoldersModal(
    currentExclusions: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GlassTheme.colors
    var allFolders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    val selectedPaths = remember { mutableStateListOf<String>().apply { addAll(currentExclusions) } }

    LaunchedEffect(Unit) {
        allFolders = AudioRepository.getFolders(ignoreExclusions = true)
    }

    GlassModalOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .glassCard(cornerRadius = 26.dp, elevated = true)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Exclude Folders",
                        color = colors.onSurface,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Checked folders will be hidden from your library",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = { selectedPaths.clear() }) {
                    Text("Clear All", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (allFolders.isEmpty()) {
                Text(
                    text = "No folders found in monitored directories.",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(allFolders, key = { it.path }) { folder ->
                        val isChecked = folder.path in selectedPaths
                        val songLabel = if (folder.songCount == 1) "1 song" else "${folder.songCount} songs"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isChecked) selectedPaths.remove(folder.path)
                                    else selectedPaths.add(folder.path)
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (folder.path !in selectedPaths) selectedPaths.add(folder.path)
                                    } else {
                                        selectedPaths.remove(folder.path)
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${File(folder.path).name} ($songLabel)",
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = folder.path,
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(selectedPaths.toSet()) }) {
                    Text("Apply")
                }
            }
        }
    }
}
