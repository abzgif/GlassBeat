package com.bytegif.glassbeat.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.data.model.AudioItem
import com.bytegif.glassbeat.service.DesktopAudioPlayer
import com.bytegif.glassbeat.ui.MainViewModel
import com.bytegif.glassbeat.ui.components.AddToPlaylistDialog
import com.bytegif.glassbeat.ui.components.SelectionActionBar
import com.bytegif.glassbeat.ui.components.SongContextMenuDialog
import com.bytegif.glassbeat.ui.components.SongInfoDialog
import com.bytegif.glassbeat.ui.components.SongRowItem
import com.bytegif.glassbeat.ui.components.SortOrderDialog
import com.bytegif.glassbeat.ui.theme.GlassTheme
import com.bytegif.glassbeat.ui.theme.glassCard
import kotlin.random.Random

@Composable
fun SongsScreen(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val songs by viewModel.filteredSongs.collectAsState()
    val allSortedSongs by viewModel.sortedSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedIds by viewModel.selectedSongIds.collectAsState()
    val favorites by AppStorage.favorites.collectAsState()
    val playlists by AppStorage.playlists.collectAsState()
    val nowPlaying by DesktopAudioPlayer.nowPlaying.collectAsState()

    var showSortDialog by remember { mutableStateOf(false) }
    var contextMenuSong by remember { mutableStateOf<AudioItem?>(null) }
    var infoSong by remember { mutableStateOf<AudioItem?>(null) }
    var playlistTargetSongs by remember { mutableStateOf<List<AudioItem>?>(null) }

    val isSelectionMode = selectedIds.isNotEmpty()
    val currentPlayingId = nowPlaying.id.toLongOrNull() ?: -1L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Glassmorphic Header
        if (isSelectionMode) {
            SelectionActionBar(
                selectedCount = selectedIds.size,
                onSelectAll = { viewModel.selectAllSongs() },
                onPlayNext = {
                    val selected = viewModel.getSelectedSongs()
                    selected.reversed().forEach { DesktopAudioPlayer.playNext(it) }
                    viewModel.showToast("${selected.size} songs added to play next")
                    viewModel.clearSongSelection()
                },
                onAddToPlaylist = {
                    playlistTargetSongs = viewModel.getSelectedSongs()
                },
                onDelete = {
                    viewModel.deleteSongsFromDisk(viewModel.getSelectedSongs())
                },
                onClose = { viewModel.clearSongSelection() }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 20.dp)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Songs",
                        color = colors.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (songs.isEmpty()) "No songs" else "${songs.size} songs · ${sortOrder.label}",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (songs.isNotEmpty()) {
                        Button(
                            onClick = { DesktopAudioPlayer.playSongs(songs, 0) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play All", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val start = Random.nextInt(songs.size)
                                DesktopAudioPlayer.playSongs(songs, start)
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle", fontSize = 13.sp)
                        }
                    }

                    IconButton(onClick = { viewModel.toggleSearchVisibility() }) {
                        Icon(
                            imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.onSurface
                        )
                    }

                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = colors.onSurface
                        )
                    }

                    if (songs.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                songs.firstOrNull()?.let { viewModel.toggleSongSelection(it.id) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckBox,
                                contentDescription = "Select Songs",
                                tint = colors.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Expandable Search Bar
        AnimatedVisibility(visible = isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search songs, artists, or albums...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Main List / Empty State
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassCard(cornerRadius = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicOff,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No Audio Files Found",
                        color = colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "No audio files match your search query"
                        } else {
                            "Add a music folder in Settings or place audio files in ~/Music"
                        },
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
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                itemsIndexed(songs, key = { _, item -> item.id }) { index, song ->
                    SongRowItem(
                        song = song,
                        isNowPlaying = song.id == currentPlayingId,
                        isFavorite = song.id in favorites,
                        isSelectionMode = isSelectionMode,
                        isSelected = song.id in selectedIds,
                        onClick = {
                            val fullIdx = allSortedSongs.indexOfFirst { it.id == song.id }
                            if (fullIdx >= 0) {
                                DesktopAudioPlayer.playSongs(allSortedSongs, fullIdx)
                            } else {
                                DesktopAudioPlayer.playSongs(songs, index)
                            }
                        },
                        onLongClick = { viewModel.toggleSongSelection(song.id) },
                        onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                        onMoreClick = { contextMenuSong = song }
                    )
                }
            }
        }
    }

    if (showSortDialog) {
        SortOrderDialog(
            currentOrder = sortOrder,
            onSelectOrder = { viewModel.setSortOrder(it) },
            onDismiss = { showSortDialog = false }
        )
    }

    contextMenuSong?.let { target ->
        SongContextMenuDialog(
            song = target,
            isFavorite = target.id in favorites,
            onDismiss = { contextMenuSong = null },
            onPlay = {
                val idx = allSortedSongs.indexOfFirst { it.id == target.id }
                if (idx >= 0) DesktopAudioPlayer.playSongs(allSortedSongs, idx)
                else DesktopAudioPlayer.playSongs(listOf(target), 0)
            },
            onPlayNext = {
                DesktopAudioPlayer.playNext(target)
                viewModel.showToast("Added to Play Next")
            },
            onToggleFavorite = { viewModel.toggleFavorite(target.id) },
            onAddToPlaylist = { playlistTargetSongs = listOf(target) },
            onShowInfo = { infoSong = target },
            onEditMetadata = { viewModel.openMetadataEditor(target) },
            onRevealInFinder = { viewModel.revealInFinder(target) },
            onDelete = { viewModel.deleteSongsFromDisk(listOf(target)) }
        )
    }

    infoSong?.let { target ->
        SongInfoDialog(song = target, onDismiss = { infoSong = null })
    }

    playlistTargetSongs?.let { targets ->
        AddToPlaylistDialog(
            playlists = playlists,
            onSelectPlaylist = { pl ->
                viewModel.addSongsToPlaylist(pl, targets)
                viewModel.clearSongSelection()
            },
            onCreateNewPlaylist = { name ->
                viewModel.createPlaylist(name, targets)
                viewModel.clearSongSelection()
            },
            onDismiss = { playlistTargetSongs = null }
        )
    }
}
