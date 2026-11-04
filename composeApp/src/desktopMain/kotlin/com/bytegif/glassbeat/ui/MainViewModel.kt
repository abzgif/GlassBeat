package com.bytegif.glassbeat.ui

import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.data.model.Album
import com.bytegif.glassbeat.data.model.AppTab
import com.bytegif.glassbeat.data.model.Artist
import com.bytegif.glassbeat.data.model.AudioItem
import com.bytegif.glassbeat.data.model.AudioTechSpecs
import com.bytegif.glassbeat.data.model.Folder
import com.bytegif.glassbeat.data.model.LibrarySection
import com.bytegif.glassbeat.data.model.ListenHistoryEntity
import com.bytegif.glassbeat.data.model.PlaylistEntity
import com.bytegif.glassbeat.data.model.SongMetadataDetails
import com.bytegif.glassbeat.data.model.SongSortOrder
import com.bytegif.glassbeat.data.repository.AudioRepository
import com.bytegif.glassbeat.service.DesktopAudioPlayer
import com.bytegif.glassbeat.ui.equalizer.EqualizerSpecs
import com.bytegif.glassbeat.util.MetadataEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import kotlin.math.abs

class MainViewModel {

    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Navigation & Overlays
    private val _currentTab = MutableStateFlow(AppTab.SONGS)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Library & Songs state
    private val _allSongs = MutableStateFlow<List<AudioItem>>(emptyList())
    val allSongs: StateFlow<List<AudioItem>> = _allSongs.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0 to 0)
    val scanProgress: StateFlow<Pair<Int, Int>> = _scanProgress.asStateFlow()

    // Search, Sort & Multi-Select in Songs tab
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        runCatching { SongSortOrder.valueOf(AppStorage.state.value.sortOrder) }
            .getOrDefault(SongSortOrder.NAME_ASC)
    )
    val sortOrder: StateFlow<SongSortOrder> = _sortOrder.asStateFlow()

    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds: StateFlow<Set<Long>> = _selectedSongIds.asStateFlow()

    val sortedSongs: StateFlow<List<AudioItem>> = combine(_allSongs, _sortOrder) { songs, order ->
        sortList(songs, order)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val filteredSongs: StateFlow<List<AudioItem>> = combine(sortedSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Library section & drill-down song list
    private val _librarySection = MutableStateFlow(LibrarySection.HOME)
    val librarySection: StateFlow<LibrarySection> = _librarySection.asStateFlow()

    private val _drillDownType = MutableStateFlow("")
    val drillDownType: StateFlow<String> = _drillDownType.asStateFlow()

    private val _drillDownId = MutableStateFlow("")
    val drillDownId: StateFlow<String> = _drillDownId.asStateFlow()

    private val _drillDownTitle = MutableStateFlow("")
    val drillDownTitle: StateFlow<String> = _drillDownTitle.asStateFlow()

    private val _drillDownSongs = MutableStateFlow<List<AudioItem>>(emptyList())
    val drillDownSongs: StateFlow<List<AudioItem>> = _drillDownSongs.asStateFlow()

    private val _drillDownSelectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val drillDownSelectedIds: StateFlow<Set<Long>> = _drillDownSelectedIds.asStateFlow()

    // Listen History multi-select
    private val _historySelectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val historySelectedSongIds: StateFlow<Set<Long>> = _historySelectedSongIds.asStateFlow()

    // Current song tech specs
    private val _nowPlayingTechSpecs = MutableStateFlow(AudioTechSpecs())
    val nowPlayingTechSpecs: StateFlow<AudioTechSpecs> = _nowPlayingTechSpecs.asStateFlow()

    // Metadata Editor Dialog target
    private val _editingSong = MutableStateFlow<AudioItem?>(null)
    val editingSong: StateFlow<AudioItem?> = _editingSong.asStateFlow()

    init {
        loadLibrary(forceRefresh = true)
        DesktopAudioPlayer.restorePlaybackState()

        // React to library updates
        scope.launch {
            AudioRepository.libraryUpdateEvent.collect {
                loadLibrary(forceRefresh = true)
            }
        }

        // Record new song starts in Listen History & extract tech specs
        scope.launch {
            DesktopAudioPlayer.historyEvent.collect { info ->
                if (info.title.isNotEmpty()) {
                    val songId = info.id.toLongOrNull() ?: return@collect
                    val entry = ListenHistoryEntity(
                        songId = songId,
                        title = info.title,
                        artist = info.artist,
                        albumId = info.albumId,
                        songPath = info.filePath,
                        playedAt = System.currentTimeMillis(),
                        lastPosition = 0L,
                        duration = info.duration
                    )
                    AppStorage.upsertHistory(entry)
                }
            }
        }

        scope.launch {
            DesktopAudioPlayer.nowPlaying.collect { info ->
                if (info.filePath.isNotEmpty()) {
                    val specs = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        MetadataEditor.extractTechSpecs(info.filePath)
                    }
                    _nowPlayingTechSpecs.value = specs
                }
            }
        }

        // Poll every 1s to update listen history position (matches Android MainViewModel)
        scope.launch {
            var lastSavedPos = -1L
            var lastSongId = -1L
            while (isActive) {
                delay(1_000L)
                if (DesktopAudioPlayer.isPlaying.value) {
                    val info = DesktopAudioPlayer.nowPlaying.value
                    val songId = info.id.toLongOrNull() ?: continue
                    val pos = DesktopAudioPlayer.currentPositionMs.value
                    val dur = DesktopAudioPlayer.durationMs.value
                    if (songId != lastSongId) {
                        lastSongId = songId
                        lastSavedPos = pos
                    }
                    if (pos > 0L && dur > 0L && abs(pos - lastSavedPos) >= 500L) {
                        lastSavedPos = pos
                        val isNearEnd = pos >= dur - 2000L || (pos >= dur * 0.98f)
                        val posToSave = if (isNearEnd) 0L else pos
                        AppStorage.updateHistoryPosition(songId, posToSave, dur)
                    }
                }
            }
        }
    }

    fun showToast(message: String) {
        scope.launch {
            _toastMessage.value = message
            delay(2800L)
            if (_toastMessage.value == message) {
                _toastMessage.value = null
            }
        }
    }

    fun switchTab(tab: AppTab) {
        if (tab == AppTab.LIBRARY && _currentTab.value == AppTab.LIBRARY) {
            resetLibraryToHome()
        }
        _currentTab.value = tab
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    fun loadLibrary(forceRefresh: Boolean = false) {
        scope.launch {
            _isLoading.value = true
            val songs = AudioRepository.getAllAudioFiles(forceRefresh = forceRefresh)
            _allSongs.value = songs
            _albums.value = AudioRepository.getAlbums()
            _artists.value = AudioRepository.getArtists()
            _folders.value = AudioRepository.getFolders()
            if (_librarySection.value == LibrarySection.SONG_LIST && _drillDownType.value.isNotEmpty()) {
                loadDrillDownSongs(_drillDownType.value, _drillDownId.value, _drillDownTitle.value)
            }
            _isLoading.value = false
        }
    }

    fun scanMusicLibrary() {
        if (_isScanning.value) return
        scope.launch {
            _isScanning.value = true
            _scanProgress.value = 0 to 0
            val songs = AudioRepository.getAllAudioFiles(
                forceRefresh = true,
                onProgress = { scanned, total ->
                    _scanProgress.value = scanned to total
                }
            )
            _allSongs.value = songs
            _albums.value = AudioRepository.getAlbums()
            _artists.value = AudioRepository.getArtists()
            _folders.value = AudioRepository.getFolders()
            _isScanning.value = false
            showToast("Scan complete: ${songs.size} songs refreshed")
        }
    }

    // ── Sort & Search ────────────────────────────────────────────────────────

    fun setSortOrder(order: SongSortOrder) {
        _sortOrder.value = order
        AppStorage.updatePrefs { it.copy(sortOrder = order.name) }
    }

    private fun sortList(list: List<AudioItem>, order: SongSortOrder): List<AudioItem> {
        return when (order) {
            SongSortOrder.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SongSortOrder.NAME_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            SongSortOrder.DATE_DESC -> list.sortedByDescending { it.dateModified }
            SongSortOrder.DATE_ASC -> list.sortedBy { it.dateModified }
            SongSortOrder.DURATION_DESC -> list.sortedByDescending { it.duration }
            SongSortOrder.DURATION_ASC -> list.sortedBy { it.duration }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearchVisibility() {
        val next = !_isSearchVisible.value
        _isSearchVisible.value = next
        if (!next) _searchQuery.value = ""
    }

    // ── Songs Selection Mode ─────────────────────────────────────────────────

    fun toggleSongSelection(songId: Long) {
        val current = _selectedSongIds.value.toMutableSet()
        if (!current.add(songId)) current.remove(songId)
        _selectedSongIds.value = current
    }

    fun selectAllSongs() {
        _selectedSongIds.value = filteredSongs.value.map { it.id }.toSet()
    }

    fun clearSongSelection() {
        _selectedSongIds.value = emptySet()
    }

    fun getSelectedSongs(): List<AudioItem> {
        val ids = _selectedSongIds.value
        return filteredSongs.value.filter { it.id in ids }
    }

    // ── Library Navigation & Drill-Down ──────────────────────────────────────

    fun resetLibraryToHome() {
        _librarySection.value = LibrarySection.HOME
        _drillDownSelectedIds.value = emptySet()
    }

    fun openLibrarySection(section: LibrarySection) {
        _drillDownSelectedIds.value = emptySet()
        when (section) {
            LibrarySection.FAVORITES -> {
                openDrillDown("favorites", "favorites", "Favorites")
            }
            LibrarySection.PODCASTS -> {
                openDrillDown("podcasts", "podcasts", "Podcasts")
            }
            else -> {
                _librarySection.value = section
            }
        }
    }

    fun openDrillDown(type: String, id: String, title: String) {
        _drillDownType.value = type
        _drillDownId.value = id
        _drillDownTitle.value = title
        _drillDownSelectedIds.value = emptySet()
        _librarySection.value = LibrarySection.SONG_LIST
        loadDrillDownSongs(type, id, title)
    }

    private fun loadDrillDownSongs(type: String, id: String, title: String) {
        scope.launch {
            _drillDownTitle.value = title
            _drillDownSongs.value = when (type) {
                "album" -> AudioRepository.getSongsByAlbum(id.toLongOrNull() ?: 0L)
                "artist" -> AudioRepository.getSongsByArtist(id)
                "folder" -> AudioRepository.getSongsByFolder(id)
                "podcasts" -> AudioRepository.getPodcastAudioFiles()
                "favorites" -> AudioRepository.getFavoriteAudioFiles()
                "playlist" -> AudioRepository.getPlaylistSongs(id.toLongOrNull() ?: 0L)
                else -> emptyList()
            }
        }
    }

    fun navigateBackInLibrary() {
        _drillDownSelectedIds.value = emptySet()
        if (_librarySection.value == LibrarySection.SONG_LIST) {
            _librarySection.value = when (_drillDownType.value) {
                "album" -> LibrarySection.ALBUMS
                "artist" -> LibrarySection.ARTISTS
                "folder" -> LibrarySection.FOLDERS
                "playlist" -> LibrarySection.PLAYLISTS
                else -> LibrarySection.HOME
            }
        } else {
            _librarySection.value = LibrarySection.HOME
        }
    }

    fun toggleDrillDownSelection(songId: Long) {
        val set = _drillDownSelectedIds.value.toMutableSet()
        if (!set.add(songId)) set.remove(songId)
        _drillDownSelectedIds.value = set
    }

    fun selectAllDrillDown() {
        _drillDownSelectedIds.value = _drillDownSongs.value.map { it.id }.toSet()
    }

    fun clearDrillDownSelection() {
        _drillDownSelectedIds.value = emptySet()
    }

    // ── History Selection ────────────────────────────────────────────────────

    fun toggleHistorySelection(songId: Long) {
        val set = _historySelectedSongIds.value.toMutableSet()
        if (!set.add(songId)) set.remove(songId)
        _historySelectedSongIds.value = set
    }

    fun selectAllHistory() {
        _historySelectedSongIds.value = AppStorage.history.value.map { it.songId }.toSet()
    }

    fun clearHistorySelection() {
        _historySelectedSongIds.value = emptySet()
    }

    // ── Favorites & Playlists ────────────────────────────────────────────────

    fun toggleFavorite(songId: Long) {
        scope.launch {
            val isNowFav = AppStorage.toggleFavorite(songId)
            if (_librarySection.value == LibrarySection.SONG_LIST && _drillDownType.value == "favorites") {
                loadDrillDownSongs("favorites", "favorites", "Favorites")
            }
            showToast(if (isNowFav) "Added to Favorites" else "Removed from Favorites")
        }
    }

    fun createPlaylist(name: String, initialSongs: List<AudioItem> = emptyList()) {
        scope.launch {
            val id = AppStorage.createPlaylist(name)
            initialSongs.forEach { song ->
                AppStorage.addSongToPlaylist(id, song.id)
            }
            if (initialSongs.isNotEmpty()) {
                showToast("${initialSongs.size} songs added to $name")
            } else {
                showToast("Playlist \"$name\" created")
            }
        }
    }

    fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        scope.launch {
            AppStorage.renamePlaylist(playlist.id, newName)
            showToast("Playlist renamed to \"$newName\"")
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        scope.launch {
            AppStorage.deletePlaylist(playlist.id)
            showToast("Deleted playlist \"${playlist.name}\"")
        }
    }

    fun addSongsToPlaylist(playlist: PlaylistEntity, songs: List<AudioItem>) {
        scope.launch {
            songs.forEach { song ->
                AppStorage.addSongToPlaylist(playlist.id, song.id)
            }
            if (_librarySection.value == LibrarySection.SONG_LIST &&
                _drillDownType.value == "playlist" &&
                _drillDownId.value == playlist.id.toString()
            ) {
                loadDrillDownSongs("playlist", playlist.id.toString(), playlist.name)
            }
            showToast("Added ${songs.size} song(s) to ${playlist.name}")
        }
    }

    fun removeSongsFromCurrentPlaylist(songs: List<AudioItem>) {
        val playlistId = _drillDownId.value.toLongOrNull() ?: return
        scope.launch {
            songs.forEach { song ->
                AppStorage.removeSongFromPlaylist(playlistId, song.id)
            }
            loadDrillDownSongs("playlist", _drillDownId.value, _drillDownTitle.value)
            _drillDownSelectedIds.value = emptySet()
            showToast("Removed ${songs.size} song(s) from playlist")
        }
    }

    // ── Equalizer ────────────────────────────────────────────────────────────

    fun setEqualizerEnabled(enabled: Boolean) {
        AppStorage.updatePrefs { it.copy(eqEnabled = enabled) }
        DesktopAudioPlayer.updateEqualizer(enabled, AppStorage.state.value.eqBands)
    }

    fun selectEqualizerPreset(presetIndex: Int) {
        val preset = EqualizerSpecs.presets.getOrNull(presetIndex) ?: return
        val customIdx = EqualizerSpecs.presets.lastIndex
        val newBands = if (presetIndex != customIdx) {
            preset.levelsMilliBels.toList()
        } else {
            AppStorage.state.value.eqBands
        }
        AppStorage.updatePrefs {
            it.copy(
                eqPresetIndex = presetIndex,
                eqBands = newBands
            )
        }
        DesktopAudioPlayer.updateEqualizer(AppStorage.state.value.eqEnabled, newBands)
    }

    fun updateEqualizerBand(bandIndex: Int, levelMilliBels: Int) {
        val clamped = levelMilliBels.coerceIn(EqualizerSpecs.MIN_LEVEL_MB, EqualizerSpecs.MAX_LEVEL_MB)
        val currentBands = AppStorage.state.value.eqBands.toMutableList()
        while (currentBands.size < 5) currentBands.add(0)
        currentBands[bandIndex] = clamped
        val customIndex = EqualizerSpecs.presets.lastIndex
        AppStorage.updatePrefs {
            it.copy(
                eqPresetIndex = customIndex,
                eqBands = currentBands
            )
        }
        DesktopAudioPlayer.updateEqualizer(AppStorage.state.value.eqEnabled, currentBands)
    }

    // ── Metadata Editor ──────────────────────────────────────────────────────

    fun openMetadataEditor(song: AudioItem) {
        _editingSong.value = song
    }

    fun closeMetadataEditor() {
        _editingSong.value = null
    }

    fun saveSongMetadata(
        song: AudioItem,
        details: SongMetadataDetails,
        isArtworkDeleted: Boolean,
        newArtworkBytes: ByteArray?,
        newArtworkMimeType: String?,
        onComplete: (Boolean) -> Unit
    ) {
        scope.launch {
            val res = MetadataEditor.saveMetadata(
                song = song,
                details = details,
                isArtworkDeleted = isArtworkDeleted,
                newArtworkBytes = newArtworkBytes,
                newArtworkMimeType = newArtworkMimeType
            )
            if (res.isSuccess) {
                AudioRepository.invalidateCache()
                showToast("Tags updated for \"${details.title.ifBlank { song.title }}\"")
                _editingSong.value = null
                onComplete(true)
            } else {
                showToast("Failed to save tags: ${res.exceptionOrNull()?.localizedMessage ?: "Error"}")
                onComplete(false)
            }
        }
    }

    // ── File System Actions & Settings ───────────────────────────────────────

    fun revealInFinder(song: AudioItem) {
        try {
            val file = File(song.data)
            if (file.exists()) {
                ProcessBuilder("open", "-R", file.absolutePath).start()
            } else if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.parentFile)
            }
        } catch (_: Exception) {
        }
    }

    fun deleteSongsFromDisk(songs: List<AudioItem>) {
        scope.launch {
            val count = AudioRepository.deleteAudioFiles(songs)
            _selectedSongIds.value = emptySet()
            _drillDownSelectedIds.value = emptySet()
            showToast(if (count > 0) "Deleted $count song(s)" else "Unable to delete file(s)")
        }
    }

    fun setDarkMode(enabled: Boolean) {
        AppStorage.updatePrefs { it.copy(isDarkMode = enabled) }
    }

    fun setSimultaneousAudio(enabled: Boolean) {
        AppStorage.updatePrefs { it.copy(simultaneousAudio = enabled) }
    }

    fun setExcludedFolders(excluded: Set<String>) {
        AppStorage.updatePrefs { it.copy(excludedFolders = excluded) }
        AudioRepository.invalidateCache()
        val msg = if (excluded.isEmpty()) "No folders excluded" else "${excluded.size} folder(s) excluded from library"
        showToast(msg)
    }

    fun addMusicDirectory(path: String) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return
        val current = AppStorage.state.value.musicDirectories.toMutableList()
        if (dir.absolutePath !in current) {
            current.add(dir.absolutePath)
            AppStorage.updatePrefs { it.copy(musicDirectories = current) }
            AudioRepository.invalidateCache()
            showToast("Added folder: ${dir.name}")
        }
    }

    fun removeMusicDirectory(path: String) {
        val current = AppStorage.state.value.musicDirectories.toMutableList()
        if (current.size <= 1) {
            showToast("At least one music directory must remain")
            return
        }
        if (current.remove(path)) {
            AppStorage.updatePrefs { it.copy(musicDirectories = current) }
            AudioRepository.invalidateCache()
            showToast("Removed music directory")
        }
    }

    fun clearListenHistory() {
        scope.launch {
            AppStorage.clearAllHistory()
            _historySelectedSongIds.value = emptySet()
            showToast("Listen history cleared")
        }
    }
}
