package com.bytegif.glassbeat.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytegif.glassbeat.data.model.AudioItem
import com.bytegif.glassbeat.data.model.SongMetadataDetails
import com.bytegif.glassbeat.ui.MainViewModel
import com.bytegif.glassbeat.ui.theme.GlassModalOverlay
import com.bytegif.glassbeat.ui.theme.GlassTheme
import com.bytegif.glassbeat.ui.theme.glassCard
import com.bytegif.glassbeat.util.MetadataEditor
import org.jetbrains.skia.Image
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Full port of EditMetadataActivity for macOS.
 */
@Composable
fun EditMetadataDialog(
    song: AudioItem,
    viewModel: MainViewModel,
    isOverNowPlaying: Boolean = false,
    onDismiss: () -> Unit
) {
    val colors = GlassTheme.colors

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var albumArtist by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var composer by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    var currentArtworkBytes by remember { mutableStateOf<ByteArray?>(null) }
    var newArtworkBytes by remember { mutableStateOf<ByteArray?>(null) }
    var newArtworkMimeType by remember { mutableStateOf<String?>(null) }
    var isArtworkDeleted by remember { mutableStateOf(false) }

    LaunchedEffect(song.id) {
        isLoading = true
        val loaded = MetadataEditor.loadFullMetadata(song)
        title = loaded.title
        artist = loaded.artist
        album = loaded.album
        albumArtist = loaded.albumArtist
        genre = loaded.genre
        composer = loaded.composer
        year = loaded.year
        lyrics = loaded.lyrics
        comment = loaded.comment
        currentArtworkBytes = loaded.artworkBytes
        isLoading = false
    }

    val previewBitmap: ImageBitmap? = remember(currentArtworkBytes, newArtworkBytes, isArtworkDeleted) {
        if (isArtworkDeleted) null
        else {
            val bytes = newArtworkBytes ?: currentArtworkBytes
            if (bytes != null && bytes.isNotEmpty()) {
                runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
            } else null
        }
    }

    GlassModalOverlay(
        onDismissRequest = { if (!isSaving) onDismiss() },
        isOverNowPlaying = isOverNowPlaying
    ) {
        Column(
            modifier = Modifier
                .width(620.dp)
                .height(680.dp)
                .glassCard(cornerRadius = 28.dp, elevated = true)
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Edit Metadata Tags",
                        color = colors.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = File(song.data).name,
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { if (!isSaving) onDismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.onSurface)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 6.dp)
                ) {
                    // Cover Art Picker Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surfaceVariant)
                                .clickable {
                                    pickCoverArtImage { bytes, mime ->
                                        newArtworkBytes = bytes
                                        newArtworkMimeType = mime
                                        isArtworkDeleted = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "Cover Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(18.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Embedded Cover Artwork",
                                color = colors.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        pickCoverArtImage { bytes, mime ->
                                            newArtworkBytes = bytes
                                            newArtworkMimeType = mime
                                            isArtworkDeleted = false
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Choose Image", fontSize = 12.sp)
                                }

                                if (previewBitmap != null) {
                                    OutlinedButton(
                                        onClick = {
                                            isArtworkDeleted = true
                                            newArtworkBytes = null
                                            newArtworkMimeType = null
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = colors.danger, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Remove Cover", color = colors.danger, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = artist,
                            onValueChange = { artist = it },
                            label = { Text("Artist") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = album,
                            onValueChange = { album = it },
                            label = { Text("Album") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = albumArtist,
                            onValueChange = { albumArtist = it },
                            label = { Text("Album Artist") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = composer,
                            onValueChange = { composer = it },
                            label = { Text("Composer") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text("Genre") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Comment") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = lyrics,
                        onValueChange = { lyrics = it },
                        label = { Text("Lyrics") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        isSaving = true
                        val updated = SongMetadataDetails(
                            title = title,
                            artist = artist,
                            album = album,
                            albumArtist = albumArtist,
                            genre = genre,
                            composer = composer,
                            year = year,
                            lyrics = lyrics,
                            comment = comment
                        )
                        viewModel.saveSongMetadata(
                            song = song,
                            details = updated,
                            isArtworkDeleted = isArtworkDeleted,
                            newArtworkBytes = newArtworkBytes,
                            newArtworkMimeType = newArtworkMimeType
                        ) {
                            isSaving = false
                        }
                    },
                    enabled = !isLoading && !isSaving,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isSaving) "Saving..." else "Save Tags")
                }
            }
        }
    }
}

private fun pickCoverArtImage(onPicked: (ByteArray, String) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select Cover Art Image"
        fileFilter = FileNameExtensionFilter("Images (JPG, PNG, WEBP)", "jpg", "jpeg", "png", "webp")
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile ?: return
        if (file.exists() && file.canRead()) {
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            onPicked(file.readBytes(), mime)
        }
    }
}
