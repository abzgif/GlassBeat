package com.bytegif.glassbeat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.data.model.AudioItem
import com.bytegif.glassbeat.data.model.ListenHistoryEntity
import com.bytegif.glassbeat.data.repository.AudioRepository
import com.bytegif.glassbeat.service.DesktopAudioPlayer
import com.bytegif.glassbeat.ui.MainViewModel
import com.bytegif.glassbeat.ui.components.ActionChipButton
import com.bytegif.glassbeat.ui.components.AddToPlaylistDialog
import com.bytegif.glassbeat.ui.components.ArtworkThumbnail
import com.bytegif.glassbeat.ui.theme.GlassTheme
import com.bytegif.glassbeat.ui.theme.formatDurationMs
import com.bytegif.glassbeat.ui.theme.glassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListenHistoryScreen(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val history by AppStorage.history.collectAsState()
    val selectedSongIds by viewModel.historySelectedSongIds.collectAsState()
    val playlists by AppStorage.playlists.collectAsState()
    val isSelectionMode = selectedSongIds.isNotEmpty()

    var playlistTargetSongs by remember { mutableStateOf<List<AudioItem>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 18.dp, elevated = true)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.clearHistorySelection() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = colors.onSurface)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${selectedSongIds.size} selected", color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { viewModel.selectAllHistory() }) {
                        Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Select All", fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChipButton(
                        icon = Icons.Default.QueueMusic,
                        label = "Play Next",
                        onClick = {
                            viewModel.scope.launch {
                                val items = AudioRepository.getAudioFilesByIds(selectedSongIds.toList())
                                items.reversed().forEach { DesktopAudioPlayer.playNext(it) }
                                viewModel.showToast("${items.size} songs added to play next")
                                viewModel.clearHistorySelection()
                            }
                        }
                    )
                    ActionChipButton(
                        icon = Icons.Default.PlaylistAdd,
                        label = "Add to Playlist",
                        onClick = {
                            viewModel.scope.launch {
                                val items = AudioRepository.getAudioFilesByIds(selectedSongIds.toList())
                                if (items.isNotEmpty()) {
                                    playlistTargetSongs = items
                                } else {
                                    viewModel.showToast("No matching songs found on device")
                                }
                            }
                        }
                    )
                    ActionChipButton(
                        icon = Icons.Default.Delete,
                        label = "Remove from History",
                        tint = colors.danger,
                        onClick = {
                            viewModel.scope.launch {
                                val count = selectedSongIds.size
                                selectedSongIds.forEach { AppStorage.deleteHistoryForSong(it) }
                                viewModel.clearHistorySelection()
                                viewModel.showToast("Removed $count songs from history")
                            }
                        }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 20.dp)
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Listen History",
                        color = colors.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (history.isEmpty()) "No history yet" else "${history.size} songs played · Click any track to resume where you left off",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                if (history.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            history.firstOrNull()?.let { viewModel.toggleHistorySelection(it.songId) }
                        }
                    ) {
                        Icon(Icons.Default.CheckBox, contentDescription = "Select", tint = colors.onSurface)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassCard(cornerRadius = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No Listen History",
                        color = colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Songs you play will appear here with saved playback progress",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .glassCard(cornerRadius = 22.dp)
                    .padding(8.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                itemsIndexed(history, key = { _, item -> item.id }) { index, item ->
                    HistoryRowItem(
                        item = item,
                        isSelectionMode = isSelectionMode,
                        isSelected = item.songId in selectedSongIds,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleHistorySelection(item.songId)
                            } else {
                                DesktopAudioPlayer.playHistorySongs(history, index)
                            }
                        },
                        onLongClick = { viewModel.toggleHistorySelection(item.songId) }
                    )
                }
            }
        }
    }

    playlistTargetSongs?.let { targets ->
        AddToPlaylistDialog(
            playlists = playlists,
            onSelectPlaylist = { pl ->
                viewModel.addSongsToPlaylist(pl, targets)
                viewModel.clearHistorySelection()
            },
            onCreateNewPlaylist = { name ->
                viewModel.createPlaylist(name, targets)
                viewModel.clearHistorySelection()
            },
            onDismiss = { playlistTargetSongs = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRowItem(
    item: ListenHistoryEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val progress = if (item.duration > 0L && item.lastPosition > 0L) {
        (item.lastPosition.toFloat() / item.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = "Select",
                tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
        }

        ArtworkThumbnail(filePath = item.songPath, size = 48.dp, cornerRadius = 12.dp)

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.artist,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress > 0.01f) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = colors.accent,
                        trackColor = colors.divider,
                        modifier = Modifier
                            .width(140.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${formatDurationMs(item.lastPosition)} / ${formatDurationMs(item.duration)}",
                        color = colors.accent,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = formatRelativeTime(item.playedAt),
            color = colors.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val diffSec = ((System.currentTimeMillis() - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        else -> "${diffSec / 86400}d ago"
    }
}
