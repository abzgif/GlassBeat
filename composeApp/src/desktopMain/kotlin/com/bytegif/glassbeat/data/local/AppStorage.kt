package com.bytegif.glassbeat.data.local

import com.bytegif.glassbeat.data.model.ListenHistoryEntity
import com.bytegif.glassbeat.data.model.PlaylistEntity
import com.bytegif.glassbeat.data.model.PlaylistSongEntity
import com.bytegif.glassbeat.data.model.RepeatMode
import com.bytegif.glassbeat.data.model.SongSortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PersistedAppState(
    val isDarkMode: Boolean = true,
    val simultaneousAudio: Boolean = false,
    val sortOrder: String = SongSortOrder.NAME_ASC.name,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 0.85f,
    val eqEnabled: Boolean = false,
    val eqPresetIndex: Int = 0,
    val eqBands: List<Int> = listOf(0, 0, 0, 0, 0),
    val musicDirectories: List<String> = emptyList(),
    val excludedFolders: Set<String> = emptySet(),
    val savedQueueIds: List<Long> = emptyList(),
    val savedCurrentIndex: Int = 0,
    val savedCurrentPositionMs: Long = 0L,
    val savedShuffleMode: Boolean = false,
    val savedRepeatMode: String = RepeatMode.OFF.name
)

@Serializable
data class PersistedLibraryDatabase(
    val nextPlaylistId: Long = 1L,
    val nextHistoryId: Long = 1L,
    val favorites: Set<Long> = emptySet(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val playlistSongs: List<PlaylistSongEntity> = emptyList(),
    val listenHistory: List<ListenHistoryEntity> = emptyList()
)

object AppStorage {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val appSupportDir: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        val dir = File(home, "Library/Application Support/GlassBeat")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val prefsFile: File by lazy { File(appSupportDir, "preferences.json") }
    private val dbJsonFile: File by lazy { File(appSupportDir, "library_store.json") }

    @Volatile
    private var dbData: PersistedLibraryDatabase = loadDbInternal()

    private val _state = MutableStateFlow(loadPrefsInternal())
    val state: StateFlow<PersistedAppState> = _state.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(dbData.favorites)
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(
        dbData.playlists.sortedByDescending { it.createdAt }
    )
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    private val _history = MutableStateFlow<List<ListenHistoryEntity>>(
        dbData.listenHistory.sortedByDescending { it.playedAt }.take(200)
    )
    val history: StateFlow<List<ListenHistoryEntity>> = _history.asStateFlow()

    private fun loadDbInternal(): PersistedLibraryDatabase {
        if (!dbJsonFile.exists()) return PersistedLibraryDatabase()
        return try {
            json.decodeFromString<PersistedLibraryDatabase>(dbJsonFile.readText())
        } catch (_: Exception) {
            PersistedLibraryDatabase()
        }
    }

    @Synchronized
    private fun updateDb(transform: (PersistedLibraryDatabase) -> PersistedLibraryDatabase) {
        val next = transform(dbData)
        dbData = next
        _favorites.value = next.favorites
        _playlists.value = next.playlists.sortedByDescending { it.createdAt }
        _history.value = next.listenHistory.sortedByDescending { it.playedAt }.take(200)
        try {
            dbJsonFile.writeText(json.encodeToString(next))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPrefsInternal(): PersistedAppState {
        val defaultMusicDir = File(System.getProperty("user.home") ?: ".", "Music").absolutePath
        if (!prefsFile.exists()) {
            return PersistedAppState(musicDirectories = listOf(defaultMusicDir))
        }
        return try {
            val loaded = json.decodeFromString<PersistedAppState>(prefsFile.readText())
            if (loaded.musicDirectories.isEmpty()) {
                loaded.copy(musicDirectories = listOf(defaultMusicDir))
            } else {
                loaded
            }
        } catch (_: Exception) {
            PersistedAppState(musicDirectories = listOf(defaultMusicDir))
        }
    }

    @Synchronized
    fun updatePrefs(transform: (PersistedAppState) -> PersistedAppState) {
        val next = transform(_state.value)
        _state.value = next
        try {
            prefsFile.writeText(json.encodeToString(next))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Favorites ────────────────────────────────────────────────────────────

    suspend fun toggleFavorite(mediaId: Long): Boolean = withContext(Dispatchers.IO) {
        var isNowFav = false
        updateDb { current ->
            val mutable = current.favorites.toMutableSet()
            isNowFav = if (mediaId in mutable) {
                mutable.remove(mediaId)
                false
            } else {
                mutable.add(mediaId)
                true
            }
            current.copy(favorites = mutable)
        }
        isNowFav
    }

    suspend fun removeFavorite(mediaId: Long) = withContext(Dispatchers.IO) {
        updateDb { current ->
            current.copy(favorites = current.favorites - mediaId)
        }
    }

    // ── Playlists ────────────────────────────────────────────────────────────

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        var createdId = 0L
        updateDb { current ->
            createdId = current.nextPlaylistId
            val newPl = PlaylistEntity(
                id = createdId,
                name = name,
                createdAt = System.currentTimeMillis()
            )
            current.copy(
                nextPlaylistId = createdId + 1L,
                playlists = current.playlists + newPl
            )
        }
        createdId
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        updateDb { current ->
            current.copy(
                playlists = current.playlists.map {
                    if (it.id == playlistId) it.copy(name = newName) else it
                }
            )
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        updateDb { current ->
            current.copy(
                playlists = current.playlists.filterNot { it.id == playlistId },
                playlistSongs = current.playlistSongs.filterNot { it.playlistId == playlistId }
            )
        }
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        updateDb { current ->
            val exists = current.playlistSongs.any { it.playlistId == playlistId && it.songId == songId }
            if (exists) current
            else current.copy(
                playlistSongs = current.playlistSongs + PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = songId,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        updateDb { current ->
            current.copy(
                playlistSongs = current.playlistSongs.filterNot {
                    it.playlistId == playlistId && it.songId == songId
                }
            )
        }
    }

    suspend fun getSongIdsForPlaylist(playlistId: Long): List<Long> = withContext(Dispatchers.IO) {
        dbData.playlistSongs
            .filter { it.playlistId == playlistId }
            .sortedBy { it.addedAt }
            .map { it.songId }
    }

    // ── Listen History ───────────────────────────────────────────────────────

    suspend fun upsertHistory(entry: ListenHistoryEntity) = withContext(Dispatchers.IO) {
        updateDb { current ->
            val existing = current.listenHistory.firstOrNull { it.songId == entry.songId }
            val pos = if (entry.lastPosition > 0L) entry.lastPosition else (existing?.lastPosition ?: 0L)
            val dur = if (entry.duration > 0L) entry.duration else (existing?.duration ?: 0L)
            val newId = current.nextHistoryId
            val updatedList = current.listenHistory
                .filterNot { it.songId == entry.songId } +
                entry.copy(id = newId, lastPosition = pos, duration = dur)
            current.copy(
                nextHistoryId = newId + 1L,
                listenHistory = updatedList.sortedByDescending { it.playedAt }.take(200)
            )
        }
    }

    suspend fun updateHistoryPosition(songId: Long, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        updateDb { current ->
            current.copy(
                listenHistory = current.listenHistory.map {
                    if (it.songId == songId) it.copy(lastPosition = positionMs, duration = durationMs)
                    else it
                }
            )
        }
    }

    suspend fun deleteHistoryForSong(songId: Long) = withContext(Dispatchers.IO) {
        updateDb { current ->
            current.copy(
                listenHistory = current.listenHistory.filterNot { it.songId == songId }
            )
        }
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        updateDb { current ->
            current.copy(listenHistory = emptyList())
        }
    }
}
