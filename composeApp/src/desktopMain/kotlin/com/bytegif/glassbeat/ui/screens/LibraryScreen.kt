package com.bytegif.glassbeat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.data.model.AudioItem
import com.bytegif.glassbeat.data.model.LibrarySection
import com.bytegif.glassbeat.data.model.PlaylistEntity
import com.bytegif.glassbeat.service.DesktopAudioPlayer
import com.bytegif.glassbeat.ui.MainViewModel
import com.bytegif.glassbeat.ui.components.AddToPlaylistDialog
import com.bytegif.glassbeat.ui.components.ArtworkThumbnail
import com.bytegif.glassbeat.ui.components.SelectionActionBar
import com.bytegif.glassbeat.ui.components.SongContextMenuDialog
import com.bytegif.glassbeat.ui.components.SongInfoDialog
import com.bytegif.glassbeat.ui.components.SongRowItem
import com.bytegif.glassbeat.ui.theme.GlassModalOverlay
import com.bytegif.glassbeat.ui.theme.GlassTheme
import com.bytegif.glassbeat.ui.theme.glassCard
import kotlin.random.Random

@Composable
fun LibraryScreen(viewModel: MainViewModel) {
    val section by viewModel.librarySection.collectAsState()

    when (section) {
        LibrarySection.HOME -> LibraryHomeMenu(viewModel)
        LibrarySection.ALBUMS -> AlbumsSubScreen(viewModel)
        LibrarySection.ARTISTS -> ArtistsSubScreen(viewModel)
        LibrarySection.FOLDERS -> FoldersSubScreen(viewModel)
        LibrarySection.PLAYLISTS -> PlaylistsSubScreen(viewModel)
        LibrarySection.SONG_LIST,
        LibrarySection.FAVORITES,
        LibrarySection.PODCASTS -> DrillDownSongListScreen(viewModel)
    }
}

@Composable
private fun LibraryHomeMenu(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val favorites by AppStorage.favorites.collectAsState()
    val playlists by AppStorage.playlists.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val podcastsCount = remember(allSongs) { allSongs.count { it.duration > 12 * 60 * 1000L } }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    text = "Library",
                    color = colors.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Browse your music by category, albums, artists, playlists, or folders",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        val items = listOf(
            LibraryCardData(LibrarySection.FAVORITES, "Favorites", "${favorites.size} liked songs", Icons.Default.Favorite),
            LibraryCardData(LibrarySection.PLAYLISTS, "Playlists", "${playlists.size} custom playlists", Icons.Default.QueueMusic),
            LibraryCardData(LibrarySection.PODCASTS, "Podcasts", "$podcastsCount long-form tracks", Icons.Default.Podcasts),
            LibraryCardData(LibrarySection.ALBUMS, "Albums", "${albums.size} albums", Icons.Default.Album),
            LibraryCardData(LibrarySection.ARTISTS, "Artists", "${artists.size} artists", Icons.Default.Person),
            LibraryCardData(LibrarySection.FOLDERS, "Folders", "${folders.size} local folders", Icons.Default.Folder)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(items) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(cornerRadius = 22.dp, elevated = true)
                        .clickable { viewModel.openLibrarySection(item.section) }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = colors.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = item.title,
                            color = colors.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private data class LibraryCardData(
    val section: LibrarySection,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
private fun AlbumsSubScreen(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val albums by viewModel.albums.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SubScreenHeader(
            title = "Albums",
            subtitle = "${albums.size} albums",
            onBack = { viewModel.navigateBackInLibrary() }
        )

        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(cornerRadius = 20.dp)
                        .clickable {
                            viewModel.openDrillDown("album", album.id.toString(), album.name)
                        }
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ArtworkThumbnail(
                        filePath = album.songPath,
                        size = 140.dp,
                        cornerRadius = 16.dp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = album.name,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.artist} · ${album.songCount} songs",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsSubScreen(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val artists by viewModel.artists.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SubScreenHeader(
            title = "Artists",
            subtitle = "${artists.size} artists",
            onBack = { viewModel.navigateBackInLibrary() }
        )

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassCard(cornerRadius = 22.dp)
                .padding(8.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(artists, key = { it.id }) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            viewModel.openDrillDown("artist", artist.name, artist.name)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = artist.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = colors.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = artist.name,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${artist.albumCount} albums · ${artist.songCount} songs",
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersSubScreen(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val folders by viewModel.folders.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SubScreenHeader(
            title = "Folders",
            subtitle = "${folders.size} folders",
            onBack = { viewModel.navigateBackInLibrary() }
        )

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassCard(cornerRadius = 22.dp)
                .padding(8.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(folders, key = { it.path }) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            viewModel.openDrillDown("folder", folder.path, folder.name)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${folder.songCount} songs · ${folder.path}",
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsSubScreen(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val playlists by AppStorage.playlists.collectAsState()
    var showCreateModal by remember { mutableStateOf(false) }
    var renamingPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerRadius = 20.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigateBackInLibrary() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onSurface)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Playlists", color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${playlists.size} playlists", color = colors.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            Button(
                onClick = { showCreateModal = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Playlist")
            }
        }

        Spacer(Modifier.height(14.dp))

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassCard(cornerRadius = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("No Playlists Yet", color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Click 'New Playlist' to create your first collection", color = colors.onSurfaceVariant, fontSize = 13.sp)
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
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.openDrillDown("playlist", playlist.id.toString(), playlist.name)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = colors.accent)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Click to view songs", color = colors.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton(onClick = { renamingPlaylist = playlist }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = colors.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.danger)
                        }
                    }
                }
            }
        }
    }

    if (showCreateModal) {
        PlaylistNameModal(
            title = "New Playlist",
            initialName = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateModal = false
            },
            onDismiss = { showCreateModal = false }
        )
    }

    renamingPlaylist?.let { target ->
        PlaylistNameModal(
            title = "Rename Playlist",
            initialName = target.name,
            confirmLabel = "Rename",
            onConfirm = { newName ->
                viewModel.renamePlaylist(target, newName)
                renamingPlaylist = null
            },
            onDismiss = { renamingPlaylist = null }
        )
    }
}

@Composable
private fun PlaylistNameModal(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GlassTheme.colors
    var name by remember { mutableStateOf(initialName) }

    GlassModalOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .glassCard(cornerRadius = 24.dp, elevated = true)
                .padding(22.dp)
        ) {
            Text(title, color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank()) onConfirm(name.trim())
                    }
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

@Composable
private fun DrillDownSongListScreen(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val title by viewModel.drillDownTitle.collectAsState()
    val type by viewModel.drillDownType.collectAsState()
    val songs by viewModel.drillDownSongs.collectAsState()
    val selectedIds by viewModel.drillDownSelectedIds.collectAsState()
    val favorites by AppStorage.favorites.collectAsState()
    val playlists by AppStorage.playlists.collectAsState()
    val nowPlaying by DesktopAudioPlayer.nowPlaying.collectAsState()

    var contextMenuSong by remember { mutableStateOf<AudioItem?>(null) }
    var infoSong by remember { mutableStateOf<AudioItem?>(null) }
    var playlistTargetSongs by remember { mutableStateOf<List<AudioItem>?>(null) }

    val isSelectionMode = selectedIds.isNotEmpty()
    val currentPlayingId = nowPlaying.id.toLongOrNull() ?: -1L
    val selectedSongsList = remember(songs, selectedIds) { songs.filter { it.id in selectedIds } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (isSelectionMode) {
            SelectionActionBar(
                selectedCount = selectedIds.size,
                showRemoveFromPlaylist = type == "playlist",
                removeLabel = "Remove from Playlist",
                onSelectAll = { viewModel.selectAllDrillDown() },
                onPlayNext = {
                    selectedSongsList.reversed().forEach { DesktopAudioPlayer.playNext(it) }
                    viewModel.showToast("${selectedSongsList.size} songs added to play next")
                    viewModel.clearDrillDownSelection()
                },
                onAddToPlaylist = { playlistTargetSongs = selectedSongsList },
                onRemoveFromPlaylist = {
                    viewModel.removeSongsFromCurrentPlaylist(selectedSongsList)
                },
                onDelete = {
                    if (type == "favorites") {
                        selectedSongsList.forEach { viewModel.toggleFavorite(it.id) }
                        viewModel.clearDrillDownSelection()
                    } else {
                        viewModel.deleteSongsFromDisk(selectedSongsList)
                    }
                },
                onClose = { viewModel.clearDrillDownSelection() }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 20.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.navigateBackInLibrary() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onSurface)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${songs.size} songs", color = colors.onSurfaceVariant, fontSize = 12.sp)
                    }
                }

                if (songs.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { DesktopAudioPlayer.playSongs(songs, 0) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play All")
                        }
                        OutlinedButton(
                            onClick = {
                                DesktopAudioPlayer.playSongs(songs, Random.nextInt(songs.size))
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                        IconButton(
                            onClick = { songs.firstOrNull()?.let { viewModel.toggleDrillDownSelection(it.id) } }
                        ) {
                            Icon(Icons.Default.CheckBox, contentDescription = "Select", tint = colors.onSurface)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassCard(cornerRadius = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicOff, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No Audio Files Found", color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("No audio files were found in this section", color = colors.onSurfaceVariant, fontSize = 13.sp)
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
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    SongRowItem(
                        song = song,
                        isNowPlaying = song.id == currentPlayingId,
                        isFavorite = song.id in favorites,
                        isSelectionMode = isSelectionMode,
                        isSelected = song.id in selectedIds,
                        onClick = {
                            DesktopAudioPlayer.playSongs(songs, index)
                        },
                        onLongClick = { viewModel.toggleDrillDownSelection(song.id) },
                        onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                        onMoreClick = { contextMenuSong = song }
                    )
                }
            }
        }
    }

    contextMenuSong?.let { target ->
        SongContextMenuDialog(
            song = target,
            isFavorite = target.id in favorites,
            onDismiss = { contextMenuSong = null },
            onPlay = {
                val idx = songs.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
                DesktopAudioPlayer.playSongs(songs, idx)
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
                viewModel.clearDrillDownSelection()
            },
            onCreateNewPlaylist = { name ->
                viewModel.createPlaylist(name, targets)
                viewModel.clearDrillDownSelection()
            },
            onDismiss = { playlistTargetSongs = null }
        )
    }
}

@Composable
private fun SubScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 20.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onSurface)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
