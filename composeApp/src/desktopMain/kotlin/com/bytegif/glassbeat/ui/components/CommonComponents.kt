package com.bytegif.glassbeat.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytegif.glassbeat.data.model.AudioItem
import com.bytegif.glassbeat.data.model.PlaylistEntity
import com.bytegif.glassbeat.data.model.SongSortOrder
import com.bytegif.glassbeat.ui.theme.GlassModalOverlay
import com.bytegif.glassbeat.ui.theme.GlassTheme
import com.bytegif.glassbeat.ui.theme.formatDurationMs
import com.bytegif.glassbeat.ui.theme.glassCard
import com.bytegif.glassbeat.util.AudioArtCache
import com.bytegif.glassbeat.util.MetadataEditor

@Composable
fun ArtworkThumbnail(
    filePath: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 12.dp
) {
    val colors = GlassTheme.colors
    var bitmap by remember(filePath) { mutableStateOf<ImageBitmap?>(AudioArtCache.getCachedBitmap(filePath)) }

    LaunchedEffect(filePath) {
        if (bitmap == null && filePath.isNotEmpty() && !AudioArtCache.hasNoArt(filePath)) {
            bitmap = AudioArtCache.loadArtworkBitmap(filePath)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.surfaceVariant,
                        colors.glassSurfaceElevated
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(size * 0.48f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRowItem(
    song: AudioItem,
    isNowPlaying: Boolean,
    isFavorite: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    val rowBg = when {
        isSelected -> colors.primary.copy(alpha = 0.14f)
        isNowPlaying -> colors.accent.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onLongClick() else onClick()
                },
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = "Select",
                tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onLongClick() }
            )
            Spacer(Modifier.width(12.dp))
        }

        Box(contentAlignment = Alignment.Center) {
            ArtworkThumbnail(filePath = song.data, size = 48.dp, cornerRadius = 12.dp)
            if (isNowPlaying) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Now Playing",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isNowPlaying) colors.accent else colors.onSurface,
                fontSize = 15.sp,
                fontWeight = if (isNowPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${song.artist} · ${song.album}",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = formatDurationMs(song.duration),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFF375F) else colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SelectionActionBar(
    selectedCount: Int,
    showRemoveFromPlaylist: Boolean = false,
    removeLabel: String = "Remove",
    onSelectAll: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val colors = GlassTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 18.dp, elevated = true)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Exit Selection", tint = colors.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$selectedCount selected",
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select All", fontSize = 12.sp)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChipButton(icon = Icons.Default.QueueMusic, label = "Play Next", onClick = onPlayNext)
            if (showRemoveFromPlaylist && onRemoveFromPlaylist != null) {
                ActionChipButton(icon = Icons.Default.Close, label = removeLabel, onClick = onRemoveFromPlaylist)
            } else {
                ActionChipButton(icon = Icons.Default.PlaylistAdd, label = "Add to Playlist", onClick = onAddToPlaylist)
            }
            ActionChipButton(
                icon = Icons.Default.Delete,
                label = "Delete",
                tint = colors.danger,
                onClick = onDelete
            )
        }
    }
}

@Composable
fun ActionChipButton(
    icon: ImageVector,
    label: String,
    tint: Color = GlassTheme.colors.onSurface,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SongContextMenuDialog(
    song: AudioItem,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowInfo: () -> Unit,
    onEditMetadata: () -> Unit,
    onRevealInFinder: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = GlassTheme.colors

    GlassModalOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .glassCard(cornerRadius = 24.dp, elevated = true)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkThumbnail(filePath = song.data, size = 52.dp, cornerRadius = 12.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} · ${song.album}",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.divider)
            Spacer(Modifier.height(8.dp))

            MenuOptionRow(Icons.Default.PlayArrow, "Play") { onPlay(); onDismiss() }
            MenuOptionRow(Icons.Default.QueueMusic, "Play Next") { onPlayNext(); onDismiss() }
            MenuOptionRow(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
            ) { onToggleFavorite(); onDismiss() }
            MenuOptionRow(Icons.Default.PlaylistAdd, "Add to Playlist") { onDismiss(); onAddToPlaylist() }
            MenuOptionRow(Icons.Default.Edit, "Edit Metadata Tags") { onDismiss(); onEditMetadata() }
            MenuOptionRow(Icons.Default.Info, "Song Info") { onDismiss(); onShowInfo() }
            MenuOptionRow(
                Icons.Default.FolderOpen,
                com.bytegif.glassbeat.util.PlatformUtils.revealInFileManagerLabel
            ) { onRevealInFinder(); onDismiss() }
            MenuOptionRow(
                icon = Icons.Default.Delete,
                label = "Delete from Device",
                tint = colors.danger
            ) { onDelete(); onDismiss() }
        }
    }
}

@Composable
private fun MenuOptionRow(
    icon: ImageVector,
    label: String,
    tint: Color = GlassTheme.colors.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SongInfoDialog(
    song: AudioItem,
    onDismiss: () -> Unit
) {
    val colors = GlassTheme.colors
    val specs = remember(song.data) { MetadataEditor.extractTechSpecs(song.data) }

    GlassModalOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .glassCard(cornerRadius = 24.dp, elevated = true)
                .padding(24.dp)
        ) {
            Text(
                text = "Song Information",
                color = colors.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            InfoRow("Title", song.title)
            InfoRow("Artist", song.artist)
            InfoRow("Album", song.album)
            InfoRow("Duration", formatDurationMs(song.duration))
            InfoRow("Audio Specs", specs.formattedString())
            InfoRow("File Path", song.data)

            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = GlassTheme.colors
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = colors.onSurface, fontSize = 13.sp)
    }
}

@Composable
fun SortOrderDialog(
    currentOrder: SongSortOrder,
    onSelectOrder: (SongSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GlassTheme.colors

    GlassModalOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .glassCard(cornerRadius = 24.dp, elevated = true)
                .padding(22.dp)
        ) {
            Text(
                text = "Sort Songs By",
                color = colors.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            SongSortOrder.entries.forEach { order ->
                val selected = order == currentOrder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable {
                            onSelectOrder(order)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = order.label,
                            color = if (selected) colors.primary else colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            text = order.subtitle,
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onCreateNewPlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GlassTheme.colors
    var showCreateInput by remember { mutableStateOf(playlists.isEmpty()) }
    var newPlaylistName by remember { mutableStateOf("") }

    GlassModalOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .glassCard(cornerRadius = 24.dp, elevated = true)
                .padding(22.dp)
        ) {
            Text(
                text = if (showCreateInput) "New Playlist" else "Add to Playlist",
                color = colors.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))

            if (showCreateInput) {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreateNewPlaylist(newPlaylistName.trim())
                                onDismiss()
                            }
                        }
                    ) {
                        Text("Create & Add")
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCreateInput = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "+ Create new playlist",
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectPlaylist(playlist)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = playlist.name,
                                color = colors.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
