package com.bytegif.glassbeat.service

import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.data.model.AudioItem
import com.bytegif.glassbeat.data.model.ListenHistoryEntity
import com.bytegif.glassbeat.data.model.NowPlayingInfo
import com.bytegif.glassbeat.data.model.RepeatMode
import com.bytegif.glassbeat.data.repository.AudioRepository
import com.bytegif.glassbeat.ui.equalizer.EqualizerAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.random.Random

/**
 * Full macOS port of MusicServiceConnection + MusicService.
 * Uses the native macOS CoreAudio helper (AVAudioEngine + AVAudioUnitEQ + AVAudioUnitTimePitch)
 * as the primary engine, with automatic fallback to Java Sound + EqualizerAudioProcessor (DF2T Biquad DSP).
 */
object DesktopAudioPlayer {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _queue = MutableStateFlow<List<AudioItem>>(emptyList())
    val queue: StateFlow<List<AudioItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _nowPlaying = MutableStateFlow(NowPlayingInfo())
    val nowPlaying: StateFlow<NowPlayingInfo> = _nowPlaying.asStateFlow()

    private val _hasActiveSession = MutableStateFlow(false)
    val hasActiveSession: StateFlow<Boolean> = _hasActiveSession.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(AppStorage.state.value.playbackSpeed)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _volume = MutableStateFlow(AppStorage.state.value.volume)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _shuffleMode = MutableStateFlow(AppStorage.state.value.savedShuffleMode)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(
        runCatching { RepeatMode.valueOf(AppStorage.state.value.savedRepeatMode) }.getOrDefault(RepeatMode.OFF)
    )
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _historyEvent = MutableSharedFlow<NowPlayingInfo>(extraBufferCapacity = 8)
    val historyEvent: SharedFlow<NowPlayingInfo> = _historyEvent.asSharedFlow()

    // Native macOS CoreAudio helper process
    private var helperProcess: Process? = null
    private var helperWriter: BufferedWriter? = null
    private var helperReaderJob: Job? = null
    private var positionPollJob: Job? = null

    // Fallback JVM Audio Processor
    private val eqProcessor = EqualizerAudioProcessor()
    private var jvmPlaybackJob: Job? = null
    @Volatile
    private var useJvmFallback = false

    init {
        SleepTimerManager.onTimerExpired = {
            pause()
        }
        startNativeHelperIfAvailable()
        startPositionPoller()
    }

    private fun startNativeHelperIfAvailable() {
        if (!com.bytegif.glassbeat.util.PlatformUtils.isMacOs) {
            useJvmFallback = true
            return
        }
        try {
            val helperFile = ensureHelperBinary()
            if (helperFile != null && helperFile.exists() && helperFile.canExecute()) {
                val proc = ProcessBuilder(helperFile.absolutePath)
                    .redirectErrorStream(false)
                    .start()
                helperProcess = proc
                helperWriter = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))

                // Apply initial EQ, speed, and volume
                val prefs = AppStorage.state.value
                sendHelperCommand("SPEED\t${prefs.playbackSpeed}")
                sendHelperCommand("VOLUME\t${prefs.volume}")
                sendEqualizerToHelper(prefs.eqEnabled, prefs.eqBands)

                helperReaderJob = scope.launch(Dispatchers.IO) {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        handleHelperOutput(line)
                    }
                }
            } else {
                useJvmFallback = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            useJvmFallback = true
        }
    }

    private fun ensureHelperBinary(): File? {
        val targetBin = File(AppStorage.appSupportDir, "glassbeat-audio-helper")
        try {
            val stream = DesktopAudioPlayer::class.java.getResourceAsStream("/macos/glassbeat-audio-helper")
            if (stream != null) {
                stream.use { input ->
                    targetBin.outputStream().use { output -> input.copyTo(output) }
                }
                targetBin.setExecutable(true)
                return targetBin
            }
        } catch (_: Exception) {
        }

        // Also check local resource path when running directly from source tree
        val localBin = File("composeApp/src/desktopMain/resources/macos/glassbeat-audio-helper")
        if (localBin.exists()) {
            localBin.setExecutable(true)
            return localBin
        }
        return if (targetBin.exists()) targetBin else null
    }

    @Synchronized
    private fun sendHelperCommand(cmd: String) {
        try {
            val writer = helperWriter ?: return
            writer.write(cmd)
            writer.newLine()
            writer.flush()
        } catch (_: Exception) {
            useJvmFallback = true
        }
    }

    private fun handleHelperOutput(line: String) {
        val parts = line.split('\t')
        when (parts.firstOrNull()) {
            "LOADED" -> {
                val dur = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                if (dur > 0L) {
                    _durationMs.value = dur
                    _nowPlaying.value = _nowPlaying.value.copy(duration = dur)
                }
            }
            "POS" -> {
                val pos = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val dur = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                val playing = parts.getOrNull(3) == "1"
                _currentPositionMs.value = pos
                if (dur > 0L) _durationMs.value = dur
                _isPlaying.value = playing
            }
            "STATE" -> {
                val playing = parts.getOrNull(1) == "1"
                _isPlaying.value = playing
            }
            "ENDED" -> {
                onTrackEndedNaturally()
            }
            "ERROR" -> {
                // Fallback to JVM decoder if AVAudioFile cannot read container (e.g. Ogg Opus)
                val currentSong = _queue.value.getOrNull(_currentIndex.value)
                if (currentSong != null) {
                    startJvmFallbackPlayback(currentSong, _currentPositionMs.value)
                }
            }
        }
    }

    private fun startPositionPoller() {
        positionPollJob?.cancel()
        positionPollJob = scope.launch {
            while (isActive) {
                if (_isPlaying.value && !useJvmFallback && helperProcess?.isAlive == true) {
                    sendHelperCommand("POS")
                }
                delay(250L)
            }
        }
    }

    private fun onTrackEndedNaturally() {
        if (SleepTimerManager.isEndOfSong) {
            SleepTimerManager.cancel()
            pause()
            return
        }
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0L)
                play()
            }
            RepeatMode.ALL -> {
                skipToNext(wrapAround = true)
            }
            RepeatMode.OFF -> {
                val q = _queue.value
                val idx = _currentIndex.value
                if (_shuffleMode.value && q.size > 1) {
                    val nextIdx = Random.nextInt(q.size)
                    playAtIndex(nextIdx, 0L, autoPlay = true)
                } else if (idx + 1 < q.size) {
                    playAtIndex(idx + 1, 0L, autoPlay = true)
                } else {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                    savePlaybackState()
                }
            }
        }
    }

    fun restorePlaybackState() {
        if (_queue.value.isNotEmpty()) return
        scope.launch {
            val prefs = AppStorage.state.value
            if (prefs.savedQueueIds.isEmpty()) return@launch
            val songs = AudioRepository.getAudioFilesByIds(prefs.savedQueueIds)
            if (songs.isNotEmpty()) {
                val idx = prefs.savedCurrentIndex.coerceIn(0, songs.size - 1)
                val pos = prefs.savedCurrentPositionMs.coerceAtLeast(0L)
                _queue.value = songs
                _shuffleMode.value = prefs.savedShuffleMode
                _repeatMode.value = runCatching { RepeatMode.valueOf(prefs.savedRepeatMode) }.getOrDefault(RepeatMode.OFF)
                playAtIndex(idx, pos, autoPlay = false)
            }
        }
    }

    fun savePlaybackState() {
        val q = _queue.value
        if (q.isEmpty()) return
        val ids = q.map { it.id }
        val idx = _currentIndex.value.coerceAtLeast(0)
        val pos = _currentPositionMs.value
        val shuf = _shuffleMode.value
        val rep = _repeatMode.value.name
        AppStorage.updatePrefs {
            it.copy(
                savedQueueIds = ids,
                savedCurrentIndex = idx,
                savedCurrentPositionMs = pos,
                savedShuffleMode = shuf,
                savedRepeatMode = rep
            )
        }
    }

    fun clearPlaybackState() {
        AppStorage.updatePrefs {
            it.copy(
                savedQueueIds = emptyList(),
                savedCurrentIndex = 0,
                savedCurrentPositionMs = 0L
            )
        }
    }

    fun playSongs(songs: List<AudioItem>, startIndex: Int, startPositionMs: Long = 0L) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        _queue.value = songs
        playAtIndex(startIndex, startPositionMs, autoPlay = true)
    }

    fun playHistorySongs(historyItems: List<ListenHistoryEntity>, startIndex: Int) {
        if (startIndex !in historyItems.indices) return
        scope.launch {
            val ids = historyItems.map { it.songId }
            val songs = AudioRepository.getAudioFilesByIds(ids)
            if (songs.isEmpty()) return@launch
            val startHistory = historyItems[startIndex]
            val targetSongIdx = songs.indexOfFirst { it.id == startHistory.songId }.coerceAtLeast(0)
            val dur = startHistory.duration
            val lastPos = startHistory.lastPosition
            val isCompleted = (dur > 0L && lastPos >= dur - 3000L) || (dur > 0L && lastPos >= (dur * 0.95f))
            val startPos = if (isCompleted || lastPos <= 0L) 0L else lastPos
            playSongs(songs, targetSongIdx, startPos)
        }
    }

    fun playAtIndex(index: Int, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        val q = _queue.value
        val song = q.getOrNull(index) ?: return
        jvmPlaybackJob?.cancel()
        useJvmFallback = false

        _currentIndex.value = index
        _currentPositionMs.value = startPositionMs
        _durationMs.value = song.duration
        _hasActiveSession.value = true

        val info = NowPlayingInfo(
            id = song.id.toString(),
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            lastPosition = startPositionMs,
            songUri = song.uri,
            filePath = song.data,
            dateModified = song.dateModified,
            albumId = song.albumId
        )
        _nowPlaying.value = info

        if (autoPlay) {
            _historyEvent.tryEmit(info)
        }

        if (helperProcess?.isAlive == true) {
            sendHelperCommand("LOAD\t${song.data}\t$startPositionMs")
            val prefs = AppStorage.state.value
            sendEqualizerToHelper(prefs.eqEnabled, prefs.eqBands)
            sendHelperCommand("SPEED\t${_playbackSpeed.value}")
            sendHelperCommand("VOLUME\t${_volume.value}")
            if (autoPlay) {
                sendHelperCommand("PLAY")
                _isPlaying.value = true
            } else {
                sendHelperCommand("PAUSE")
                _isPlaying.value = false
            }
        } else {
            if (autoPlay) {
                startJvmFallbackPlayback(song, startPositionMs)
            } else {
                _isPlaying.value = false
            }
        }
        savePlaybackState()
    }

    private fun startJvmFallbackPlayback(song: AudioItem, startPositionMs: Long) {
        useJvmFallback = true
        jvmPlaybackJob?.cancel()
        jvmPlaybackJob = scope.launch(Dispatchers.IO) {
            val file = File(song.data)
            if (!file.exists()) return@launch
            try {
                val rawIn = AudioSystem.getAudioInputStream(file)
                val baseFormat = rawIn.format
                val decodedFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels.coerceIn(1, 2),
                    baseFormat.channels.coerceIn(1, 2) * 2,
                    baseFormat.sampleRate,
                    false
                )
                val pcmIn = AudioSystem.getAudioInputStream(decodedFormat, rawIn)
                val info = DataLine.Info(SourceDataLine::class.java, decodedFormat)
                val line = AudioSystem.getLine(info) as SourceDataLine
                line.open(decodedFormat)
                line.start()

                eqProcessor.configure(decodedFormat.sampleRate.toInt())
                val prefs = AppStorage.state.value
                eqProcessor.updateSettings(prefs.eqEnabled, prefs.eqBands)

                val bytesPerMs = ((decodedFormat.sampleRate * decodedFormat.frameSize) / 1000f).toLong().coerceAtLeast(1L)
                if (startPositionMs > 0L) {
                    val toSkip = startPositionMs * bytesPerMs
                    var skipped = 0L
                    while (skipped < toSkip) {
                        val s = pcmIn.skip(toSkip - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                }

                _isPlaying.value = true
                val buffer = ByteArray(4096)
                var playedBytes = startPositionMs * bytesPerMs

                while (isActive) {
                    if (!_isPlaying.value) {
                        delay(50L)
                        continue
                    }
                    val read = pcmIn.read(buffer, 0, buffer.size)
                    if (read == -1) {
                        line.drain()
                        line.close()
                        pcmIn.close()
                        onTrackEndedNaturally()
                        break
                    }
                    if (read > 0) {
                        eqProcessor.processPcm16Le(
                            buffer = buffer,
                            bytesRead = read,
                            channelCount = decodedFormat.channels.coerceIn(1, 2),
                            volume = _volume.value
                        )
                        line.write(buffer, 0, read)
                        playedBytes += read
                        _currentPositionMs.value = (playedBytes / bytesPerMs).coerceAtMost(_durationMs.value)
                    }
                }
                if (line.isOpen) {
                    line.stop()
                    line.close()
                }
                pcmIn.close()
            } catch (e: Exception) {
                e.printStackTrace()
                _isPlaying.value = false
            }
        }
    }

    fun playPause() {
        if (_queue.value.isEmpty()) return
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        if (_queue.value.isEmpty()) return
        if (useJvmFallback) {
            val currentSong = _queue.value.getOrNull(_currentIndex.value) ?: return
            if (jvmPlaybackJob?.isActive == true) {
                _isPlaying.value = true
            } else {
                startJvmFallbackPlayback(currentSong, _currentPositionMs.value)
            }
        } else {
            sendHelperCommand("PLAY")
            _isPlaying.value = true
        }
        savePlaybackState()
    }

    fun pause() {
        if (useJvmFallback) {
            _isPlaying.value = false
        } else {
            sendHelperCommand("PAUSE")
            _isPlaying.value = false
        }
        savePlaybackState()
    }

    fun stop() {
        jvmPlaybackJob?.cancel()
        sendHelperCommand("STOP")
        _isPlaying.value = false
        _hasActiveSession.value = false
        _queue.value = emptyList()
        _currentIndex.value = -1
        _currentPositionMs.value = 0L
        clearPlaybackState()
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        _currentPositionMs.value = clamped
        if (useJvmFallback) {
            val song = _queue.value.getOrNull(_currentIndex.value) ?: return
            startJvmFallbackPlayback(song, clamped)
        } else {
            sendHelperCommand("SEEK\t$clamped")
        }
        savePlaybackState()
    }

    fun skipForward10s() {
        val target = (_currentPositionMs.value + 10_000L).coerceAtMost(_durationMs.value)
        seekTo(target)
    }

    fun replay10s() {
        val target = (_currentPositionMs.value - 10_000L).coerceAtLeast(0L)
        seekTo(target)
    }

    fun skipToNext(wrapAround: Boolean = true) {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = if (_shuffleMode.value && q.size > 1) {
            var candidate = Random.nextInt(q.size)
            if (candidate == _currentIndex.value) candidate = (candidate + 1) % q.size
            candidate
        } else {
            val next = _currentIndex.value + 1
            if (next < q.size) next else if (wrapAround) 0 else return
        }
        playAtIndex(nextIdx, 0L, autoPlay = true)
    }

    fun skipToPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (_currentPositionMs.value > 3500L) {
            seekTo(0L)
            return
        }
        val prevIdx = if (_currentIndex.value - 1 >= 0) _currentIndex.value - 1 else q.size - 1
        playAtIndex(prevIdx, 0L, autoPlay = true)
    }

    fun toggleShuffle() {
        val next = !_shuffleMode.value
        _shuffleMode.value = next
        savePlaybackState()
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = next
        savePlaybackState()
    }

    fun playNext(song: AudioItem) {
        val currentQ = _queue.value.toMutableList()
        if (currentQ.isEmpty()) {
            playSongs(listOf(song), 0)
            return
        }
        val insertIdx = (_currentIndex.value + 1).coerceIn(0, currentQ.size)
        currentQ.add(insertIdx, song)
        _queue.value = currentQ
        savePlaybackState()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQ = _queue.value.toMutableList()
        if (fromIndex !in currentQ.indices || toIndex !in currentQ.indices || fromIndex == toIndex) return
        val item = currentQ.removeAt(fromIndex)
        currentQ.add(toIndex, item)
        val cur = _currentIndex.value
        val newCurrent = when {
            cur == fromIndex -> toIndex
            fromIndex < cur && toIndex >= cur -> cur - 1
            fromIndex > cur && toIndex <= cur -> cur + 1
            else -> cur
        }
        _queue.value = currentQ
        _currentIndex.value = newCurrent
        savePlaybackState()
    }

    fun removeFromQueue(index: Int) {
        val currentQ = _queue.value.toMutableList()
        if (index !in currentQ.indices) return
        if (currentQ.size == 1) {
            stop()
            return
        }
        currentQ.removeAt(index)
        val cur = _currentIndex.value
        _queue.value = currentQ
        if (index == cur) {
            val nextIdx = cur.coerceAtMost(currentQ.size - 1)
            playAtIndex(nextIdx, 0L, autoPlay = _isPlaying.value)
        } else if (index < cur) {
            _currentIndex.value = cur - 1
        }
        savePlaybackState()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        _playbackSpeed.value = clamped
        sendHelperCommand("SPEED\t$clamped")
        AppStorage.updatePrefs { it.copy(playbackSpeed = clamped) }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        sendHelperCommand("VOLUME\t$clamped")
        AppStorage.updatePrefs { it.copy(volume = clamped) }
    }

    fun updateEqualizer(enabled: Boolean, bandsMilliBels: List<Int>) {
        eqProcessor.updateSettings(enabled, bandsMilliBels)
        sendEqualizerToHelper(enabled, bandsMilliBels)
    }

    private fun sendEqualizerToHelper(enabled: Boolean, bandsMilliBels: List<Int>) {
        val b0 = bandsMilliBels.getOrElse(0) { 0 }
        val b1 = bandsMilliBels.getOrElse(1) { 0 }
        val b2 = bandsMilliBels.getOrElse(2) { 0 }
        val b3 = bandsMilliBels.getOrElse(3) { 0 }
        val b4 = bandsMilliBels.getOrElse(4) { 0 }
        val flag = if (enabled) "1" else "0"
        sendHelperCommand("EQ\t$flag\t$b0\t$b1\t$b2\t$b3\t$b4")
    }
}
