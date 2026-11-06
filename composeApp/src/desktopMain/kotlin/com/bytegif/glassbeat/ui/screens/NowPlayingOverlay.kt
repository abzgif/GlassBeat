package com.bytegif.glassbeat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.data.model.RepeatMode
import com.bytegif.glassbeat.service.DesktopAudioPlayer
import com.bytegif.glassbeat.service.SleepTimerManager
import com.bytegif.glassbeat.ui.MainViewModel
import com.bytegif.glassbeat.ui.components.ArtworkThumbnail
import com.bytegif.glassbeat.ui.theme.GlassModalOverlay
import com.bytegif.glassbeat.ui.theme.GlassTheme
import com.bytegif.glassbeat.ui.theme.formatDurationMs
import com.bytegif.glassbeat.ui.theme.glassCard
import com.bytegif.glassbeat.ui.theme.glassPill
import com.bytegif.glassbeat.ui.theme.rememberNowPlayingBlurRadius
import com.bytegif.glassbeat.util.AudioArtCache
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MiniPlayerBar(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    val hasActiveSession by DesktopAudioPlayer.hasActiveSession.collectAsState()
    val isPlaying by DesktopAudioPlayer.isPlaying.collectAsState()
    val nowPlaying by DesktopAudioPlayer.nowPlaying.collectAsState()
    val positionMs by DesktopAudioPlayer.currentPositionMs.collectAsState()
    val durationMs by DesktopAudioPlayer.durationMs.collectAsState()

    if (!hasActiveSession || nowPlaying.title.isEmpty()) return

    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    var artworkBitmap by remember(nowPlaying.filePath) {
        mutableStateOf<ImageBitmap?>(AudioArtCache.getCachedBitmap(nowPlaying.filePath))
    }
    LaunchedEffect(nowPlaying.filePath) {
        if (artworkBitmap == null && nowPlaying.filePath.isNotEmpty()) {
            artworkBitmap = AudioArtCache.loadArtworkBitmap(nowPlaying.filePath)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 22.dp, elevated = true)
            .clickable { viewModel.setNowPlayingExpanded(true) }
    ) {
        // Blurred dynamic album art glass layer inside the Mini Player
        artworkBitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(36.dp)
                    .alpha(if (colors.isDark) 0.42f else 0.28f)
            )
        }

        // Frosted glass tint overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(colors.miniPlayerBg)
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            // Interactive Seekable Bar at the top of Mini Player (click or drag to seek)
            MiniPlayerSeekBar(
                progress = progress,
                accentColor = colors.accent,
                trackColor = colors.divider,
                onSeekFraction = { fraction ->
                    if (durationMs > 0L) {
                        DesktopAudioPlayer.seekTo((fraction * durationMs).toLong())
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkThumbnail(filePath = nowPlaying.filePath, size = 44.dp, cornerRadius = 10.dp)

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nowPlaying.title,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = nowPlaying.artist,
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${formatDurationMs(positionMs)} / ${formatDurationMs(durationMs)}",
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontFeatureSettings = "tnum",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                IconButton(onClick = { DesktopAudioPlayer.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = colors.onSurface)
                }

                IconButton(
                    onClick = { DesktopAudioPlayer.playPause() },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = colors.onPrimary
                    )
                }

                IconButton(onClick = { DesktopAudioPlayer.skipToNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = colors.onSurface)
                }

                IconButton(
                    onClick = {
                        DesktopAudioPlayer.stop()
                        SleepTimerManager.cancel()
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Stop & Close", tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerSeekBar(
    progress: Float,
    accentColor: Color,
    trackColor: Color,
    onSeekFraction: (Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progress) }
    val displayFraction = if (isDragging) dragFraction else progress

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .padding(horizontal = 12.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekFraction(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekFraction(dragFraction)
                    },
                    onDragEnd = {
                        onSeekFraction(dragFraction)
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            }
    ) {
        val barHeight = 4.dp.toPx()
        val centerY = size.height / 2f
        val topY = centerY - barHeight / 2f

        // Background track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, topY),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
        )

        // Active fill
        val activeWidth = (size.width * displayFraction.coerceIn(0f, 1f))
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(0f, topY),
            size = Size(activeWidth, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
        )

        // Seek thumb handle
        drawCircle(
            color = accentColor,
            radius = if (isDragging) 6.dp.toPx() else 4.5.dp.toPx(),
            center = Offset(activeWidth.coerceIn(4.dp.toPx(), size.width - 4.dp.toPx()), centerY)
        )
    }
}

@Composable
fun NowPlayingFullOverlay(viewModel: MainViewModel) {
    val colors = GlassTheme.colors
    val nowPlaying by DesktopAudioPlayer.nowPlaying.collectAsState()
    val isPlaying by DesktopAudioPlayer.isPlaying.collectAsState()
    val positionMs by DesktopAudioPlayer.currentPositionMs.collectAsState()
    val durationMs by DesktopAudioPlayer.durationMs.collectAsState()
    val shuffleMode by DesktopAudioPlayer.shuffleMode.collectAsState()
    val repeatMode by DesktopAudioPlayer.repeatMode.collectAsState()
    val playbackSpeed by DesktopAudioPlayer.playbackSpeed.collectAsState()
    val volume by DesktopAudioPlayer.volume.collectAsState()
    val queue by DesktopAudioPlayer.queue.collectAsState()
    val currentIndex by DesktopAudioPlayer.currentIndex.collectAsState()
    val favorites by AppStorage.favorites.collectAsState()
    val techSpecs by viewModel.nowPlayingTechSpecs.collectAsState()
    val sleepTimerDisplay by SleepTimerManager.display.collectAsState()
    val sleepTimerActive by SleepTimerManager.isActive.collectAsState()
    val editingSong by viewModel.editingSong.collectAsState()

    val currentSongId = nowPlaying.id.toLongOrNull() ?: -1L
    val isFavorite = currentSongId in favorites

    var showSpeedModal by remember { mutableStateOf(false) }
    var showSleepTimerModal by remember { mutableStateOf(false) }
    var showQueueDrawer by remember { mutableStateOf(false) }

    val nowPlayingBlurRadius = rememberNowPlayingBlurRadius(extraCondition = editingSong != null)

    var artworkBitmap by remember(nowPlaying.filePath) {
        mutableStateOf<ImageBitmap?>(AudioArtCache.getCachedBitmap(nowPlaying.filePath))
    }
    LaunchedEffect(nowPlaying.filePath) {
        if (artworkBitmap == null && nowPlaying.filePath.isNotEmpty()) {
            artworkBitmap = AudioArtCache.loadArtworkBitmap(nowPlaying.filePath)
        }
    }

    // Root container consumes all clicks so clicks never pass through to the main screen underneath
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        // Content container that blurs when Sleep Timer, Playback Speed, or Edit Metadata modal is open
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(nowPlayingBlurRadius)
        ) {
            // Dynamic Blurred Album Art Background (matching NowPlayingActivity)
            artworkBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(45.dp)
                        .alpha(if (colors.isDark) 0.55f else 0.35f)
                )
            }

            // Gradient Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.background.copy(alpha = 0.65f),
                                colors.background.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 36.dp, end = 36.dp, top = 32.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar: True-centered "NOW PLAYING" & non-clipping, non-jittering Sleep Timer pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.setNowPlayingExpanded(false) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .glassPill()
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize",
                            tint = colors.onSurface
                        )
                    }

                    // Dead-center header + fixed-height timer slot so nothing ever shifts or clips
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "NOW PLAYING",
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            letterSpacing = 1.8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(6.dp))

                        Box(
                            modifier = Modifier.height(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sleepTimerActive && sleepTimerDisplay.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .widthIn(min = 96.dp)
                                        .glassPill()
                                        .clickable { showSleepTimerModal = true }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = sleepTimerDisplay,
                                        style = TextStyle(
                                            color = colors.accent,
                                            fontSize = 12.sp,
                                            lineHeight = 12.sp,
                                            fontFeatureSettings = "tnum",
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showSleepTimerModal = true },
                            modifier = Modifier.glassPill()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimerActive) colors.accent else colors.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showQueueDrawer = !showQueueDrawer },
                            modifier = Modifier.glassPill()
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Queue",
                                tint = if (showQueueDrawer) colors.accent else colors.onSurface
                            )
                        }
                    }
                }

                // Center Content: Album Art + Queue Drawer side-by-side if open
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ArtworkThumbnail(
                            filePath = nowPlaying.filePath,
                            size = 270.dp,
                            cornerRadius = 28.dp
                        )

                        Spacer(Modifier.height(14.dp))

                        // Audio Tech Specs Pill (e.g., FLAC • 1042 kb/s • 44.1 kHz)
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .glassPill()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = techSpecs.formattedString(),
                                style = TextStyle(
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 11.sp,
                                    fontFeatureSettings = "tnum",
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }

                    if (showQueueDrawer) {
                        Spacer(Modifier.width(24.dp))
                        QueueSidePanel(
                            queue = queue,
                            currentIndex = currentIndex,
                            onSelectTrack = { idx -> DesktopAudioPlayer.playAtIndex(idx, 0L, autoPlay = true) },
                            onMoveTrack = { from, to -> DesktopAudioPlayer.moveQueueItem(from, to) },
                            onRemoveTrack = { idx -> DesktopAudioPlayer.removeFromQueue(idx) },
                            onClose = { showQueueDrawer = false },
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                        )
                    }
                }

                // Bottom Controls Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(cornerRadius = 28.dp, elevated = true)
                        .padding(horizontal = 28.dp, vertical = 20.dp)
                ) {
                    // Title, Artist, Favorite & Speed & Edit Tag buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nowPlaying.title.ifBlank { "No Track Selected" },
                                color = colors.onSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${nowPlaying.artist} · ${nowPlaying.album}",
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Playback Speed Chip
                            val formattedSpeed = if (playbackSpeed == playbackSpeed.toInt().toFloat()) {
                                String.format(Locale.US, "%.1fx", playbackSpeed)
                            } else {
                                String.format(Locale.US, "%.2fx", playbackSpeed)
                            }
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .glassPill()
                                    .clickable { showSpeedModal = true }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formattedSpeed,
                                    style = TextStyle(
                                        color = if (abs(playbackSpeed - 1.0f) < 0.03f) colors.onSurfaceVariant else colors.accent,
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum",
                                        fontSize = 12.sp,
                                        lineHeight = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }

                            // Edit Metadata Button
                            IconButton(
                                onClick = {
                                    queue.getOrNull(currentIndex)?.let { song ->
                                        viewModel.openMetadataEditor(song)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Tags",
                                    tint = colors.onSurfaceVariant
                                )
                            }

                            // Favorite Heart Button
                            IconButton(
                                onClick = {
                                    if (currentSongId > 0) viewModel.toggleFavorite(currentSongId)
                                }
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color(0xFFFF375F) else colors.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Seekbar + Elapsed / Total Time
                    Slider(
                        value = if (durationMs > 0L) positionMs.toFloat().coerceIn(0f, durationMs.toFloat()) else 0f,
                        onValueChange = { DesktopAudioPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..(durationMs.coerceAtLeast(1L).toFloat()),
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primary,
                            activeTrackColor = colors.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMs(positionMs),
                            style = TextStyle(
                                color = colors.onSurfaceVariant,
                                fontFeatureSettings = "tnum",
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = formatDurationMs(durationMs),
                            style = TextStyle(
                                color = colors.onSurfaceVariant,
                                fontFeatureSettings = "tnum",
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Transport Controls + Volume Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Shuffle & Repeat
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.width(180.dp)
                        ) {
                            IconButton(onClick = { DesktopAudioPlayer.toggleShuffle() }) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (shuffleMode) colors.accent else colors.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            IconButton(onClick = { DesktopAudioPlayer.cycleRepeatMode() }) {
                                Icon(
                                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (repeatMode != RepeatMode.OFF) colors.accent else colors.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Center: -10s, Prev, Play/Pause, Next, +10s
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            IconButton(onClick = { DesktopAudioPlayer.replay10s() }) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Replay 10s",
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            IconButton(onClick = { DesktopAudioPlayer.skipToPrevious() }) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = { DesktopAudioPlayer.playPause() },
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = colors.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(onClick = { DesktopAudioPlayer.skipToNext() }) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(onClick = { DesktopAudioPlayer.skipForward10s() }) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Right: Volume Slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(180.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Volume",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = volume,
                                onValueChange = { DesktopAudioPlayer.setVolume(it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSpeedModal) {
        PlaybackSpeedModal(
            currentSpeed = playbackSpeed,
            onSpeedSelected = { DesktopAudioPlayer.setPlaybackSpeed(it) },
            onDismiss = { showSpeedModal = false }
        )
    }

    if (showSleepTimerModal) {
        SleepTimerModal(
            onDismiss = { showSleepTimerModal = false }
        )
    }
}

@Composable
private fun QueueSidePanel(
    queue: List<com.bytegif.glassbeat.data.model.AudioItem>,
    currentIndex: Int,
    onSelectTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors

    Column(
        modifier = modifier
            .glassCard(cornerRadius = 24.dp, elevated = true)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Playing Queue", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${queue.size} tracks", color = colors.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Queue", tint = colors.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = colors.divider)
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue, key = { idx, item -> "${idx}_${item.id}" }) { index, song ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) colors.accent.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelectTrack(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkThumbnail(filePath = song.data, size = 36.dp, cornerRadius = 8.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = if (isCurrent) colors.accent else colors.onSurface,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index > 0) {
                        IconButton(onClick = { onMoveTrack(index, index - 1) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                    if (index < queue.lastIndex) {
                        IconButton(onClick = { onMoveTrack(index, index + 1) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                    IconButton(onClick = { onRemoveTrack(index) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackSpeedModal(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GlassTheme.colors
    var speed by remember { mutableStateOf(currentSpeed) }
    val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    GlassModalOverlay(
        onDismissRequest = onDismiss,
        isOverNowPlaying = true
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .glassCard(cornerRadius = 26.dp, elevated = true)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Playback Speed", color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = {
                        speed = 1.0f
                        onSpeedSelected(1.0f)
                    }
                ) {
                    Text("Reset (1.0x)")
                }
            }

            Spacer(Modifier.height(12.dp))

            val formatted = if (speed == speed.toInt().toFloat()) {
                String.format(Locale.US, "%.1fx", speed)
            } else {
                String.format(Locale.US, "%.2fx", speed)
            }
            Text(
                text = formatted,
                style = TextStyle(
                    color = colors.accent,
                    fontSize = 32.sp,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(Modifier.height(10.dp))

            Slider(
                value = speed,
                onValueChange = { raw ->
                    val snapped = if (raw in 0.96f..1.04f) 1.0f else (raw * 20f).roundToInt() / 20f
                    speed = snapped
                    onSpeedSelected(snapped)
                },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                presets.forEach { preset ->
                    val active = abs(speed - preset) < 0.03f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) colors.accent else colors.surfaceVariant)
                            .clickable {
                                speed = preset
                                onSpeedSelected(preset)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${preset}x",
                            color = if (active) Color.Black else colors.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SleepTimerModal(onDismiss: () -> Unit) {
    val colors = GlassTheme.colors
    val isActive by SleepTimerManager.isActive.collectAsState()
    val display by SleepTimerManager.display.collectAsState()

    val options = listOf(
        15L to "15 minutes",
        30L to "30 minutes",
        45L to "45 minutes",
        60L to "60 minutes",
        90L to "90 minutes"
    )

    GlassModalOverlay(
        onDismissRequest = onDismiss,
        isOverNowPlaying = true
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .glassCard(cornerRadius = 26.dp, elevated = true)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = colors.accent)
                Spacer(Modifier.width(10.dp))
                Text("Sleep Timer", color = colors.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }

            if (isActive) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(cornerRadius = 14.dp)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Timer", color = colors.onSurfaceVariant, fontSize = 11.sp)
                        Text(
                            text = display,
                            style = TextStyle(
                                color = colors.accent,
                                fontFeatureSettings = "tnum",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            SleepTimerManager.cancel()
                            onDismiss()
                        }
                    ) {
                        Text("Cancel Timer", color = colors.danger)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            options.forEach { (mins, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            SleepTimerManager.start(mins)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        SleepTimerManager.setEndOfSong()
                        onDismiss()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("End of current song", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
