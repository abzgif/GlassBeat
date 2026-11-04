package com.bytegif.glassbeat.data.local

import com.bytegif.glassbeat.data.model.ListenHistoryEntity
import com.bytegif.glassbeat.data.model.PlaylistEntity
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
import java.sql.Connection
import java.sql.DriverManager

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

object AppStorage {

    private val json = Json {
        prettyPrint = true
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
    private val dbFile: File by lazy { File(appSupportDir, "glassbeat.db") }

    private val _state = MutableStateFlow(loadPrefsInternal())
    val state: StateFlow<PersistedAppState> = _state.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    private val _history = MutableStateFlow<List<ListenHistoryEntity>>(emptyList())
    val history: StateFlow<List<ListenHistoryEntity>> = _history.asStateFlow()

    init {
        initDatabase()
        reloadAllTables()
    }

    private fun getConnection(): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }

    private fun initDatabase() {
        try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS favorites (
                            mediaId INTEGER PRIMARY KEY
                        );
                        """.trimIndent()
                    )
                    stmt.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS playlists (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL,
                            createdAt INTEGER NOT NULL
                        );
                        """.trimIndent()
                    )
                    stmt.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS playlist_songs (
                            playlistId INTEGER NOT NULL,
                            songId INTEGER NOT NULL,
                            addedAt INTEGER NOT NULL,
                            PRIMARY KEY (playlistId, songId)
                        );
                        """.trimIndent()
                    )
                    stmt.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS listen_history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            songId INTEGER NOT NULL,
                            title TEXT NOT NULL,
                            artist TEXT NOT NULL,
                            albumId INTEGER NOT NULL,
                            songPath TEXT NOT NULL DEFAULT '',
                            playedAt INTEGER NOT NULL,
                            lastPosition INTEGER NOT NULL DEFAULT 0,
                            duration INTEGER NOT NULL DEFAULT 0
                        );
                        """.trimIndent()
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun reloadAllTables() {
        try {
            getConnection().use { conn ->
                // Favorites
                val favSet = mutableSetOf<Long>()
                conn.createStatement().executeQuery("SELECT mediaId FROM favorites").use { rs ->
                    while (rs.next()) {
                        favSet.add(rs.getLong("mediaId"))
                    }
                }
                _favorites.value = favSet

                // Playlists
                val plList = mutableListOf<PlaylistEntity>()
                conn.createStatement().executeQuery("SELECT id, name, createdAt FROM playlists ORDER BY createdAt DESC").use { rs ->
                    while (rs.next()) {
                        plList.add(
                            PlaylistEntity(
                                id = rs.getLong("id"),
                                name = rs.getString("name"),
                                createdAt = rs.getLong("createdAt")
                            )
                        )
                    }
                }
                _playlists.value = plList

                // Listen History (one per songId, ordered by playedAt DESC, limit 200)
                val histList = mutableListOf<ListenHistoryEntity>()
                val query = """
                    SELECT id, songId, title, artist, albumId, songPath, playedAt, lastPosition, duration
                    FROM listen_history
                    WHERE id IN (
                        SELECT MAX(id) FROM listen_history GROUP BY songId
                    )
                    ORDER BY playedAt DESC
                    LIMIT 200
                """.trimIndent()
                conn.createStatement().executeQuery(query).use { rs ->
                    while (rs.next()) {
                        histList.add(
                            ListenHistoryEntity(
                                id = rs.getLong("id"),
                                songId = rs.getLong("songId"),
                                title = rs.getString("title"),
                                artist = rs.getString("artist"),
                                albumId = rs.getLong("albumId"),
                                songPath = rs.getString("songPath") ?: "",
                                playedAt = rs.getLong("playedAt"),
                                lastPosition = rs.getLong("lastPosition"),
                                duration = rs.getLong("duration")
                            )
                        )
                    }
                }
                _history.value = histList
            }
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
        } catch (e: Exception) {
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
        val isFav: Boolean
        getConnection().use { conn ->
            val exists = conn.prepareStatement("SELECT 1 FROM favorites WHERE mediaId = ?").use { ps ->
                ps.setLong(1, mediaId)
                ps.executeQuery().use { it.next() }
            }
            if (exists) {
                conn.prepareStatement("DELETE FROM favorites WHERE mediaId = ?").use { ps ->
                    ps.setLong(1, mediaId)
                    ps.executeUpdate()
                }
                isFav = false
            } else {
                conn.prepareStatement("INSERT OR REPLACE INTO favorites(mediaId) VALUES (?)").use { ps ->
                    ps.setLong(1, mediaId)
                    ps.executeUpdate()
                }
                isFav = true
            }
        }
        reloadAllTables()
        isFav
    }

    suspend fun removeFavorite(mediaId: Long) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM favorites WHERE mediaId = ?").use { ps ->
                ps.setLong(1, mediaId)
                ps.executeUpdate()
            }
        }
        reloadAllTables()
    }

    // ── Playlists ────────────────────────────────────────────────────────────

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        var newId = 0L
        getConnection().use { conn ->
            conn.prepareStatement(
                "INSERT INTO playlists(name, createdAt) VALUES (?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS
            ).use { ps ->
                ps.setString(1, name)
                ps.setLong(2, System.currentTimeMillis())
                ps.executeUpdate()
                ps.generatedKeys.use { keys ->
                    if (keys.next()) newId = keys.getLong(1)
                }
            }
        }
        reloadAllTables()
        newId
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement("UPDATE playlists SET name = ? WHERE id = ?").use { ps ->
                ps.setString(1, newName)
                ps.setLong(2, playlistId)
                ps.executeUpdate()
            }
        }
        reloadAllTables()
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM playlist_songs WHERE playlistId = ?").use { ps ->
                ps.setLong(1, playlistId)
                ps.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM playlists WHERE id = ?").use { ps ->
                ps.setLong(1, playlistId)
                ps.executeUpdate()
            }
        }
        reloadAllTables()
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement(
                "INSERT OR IGNORE INTO playlist_songs(playlistId, songId, addedAt) VALUES (?, ?, ?)"
            ).use { ps ->
                ps.setLong(1, playlistId)
                ps.setLong(2, songId)
                ps.setLong(3, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
        reloadAllTables()
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement(
                "DELETE FROM playlist_songs WHERE playlistId = ? AND songId = ?"
            ).use { ps ->
                ps.setLong(1, playlistId)
                ps.setLong(2, songId)
                ps.executeUpdate()
            }
        }
        reloadAllTables()
    }

    suspend fun getSongIdsForPlaylist(playlistId: Long): List<Long> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT songId FROM playlist_songs WHERE playlistId = ? ORDER BY addedAt ASC"
            ).use { ps ->
                ps.setLong(1, playlistId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        ids.add(rs.getLong("songId"))
                    }
                }
            }
        }
        ids
    }

    // ── Listen History ───────────────────────────────────────────────────────

    suspend fun upsertHistory(entry: ListenHistoryEntity) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            var existingPos = 0L
            var existingDur = 0L
            conn.prepareStatement(
                "SELECT lastPosition, duration FROM listen_history WHERE songId = ? ORDER BY id DESC LIMIT 1"
            ).use { ps ->
                ps.setLong(1, entry.songId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        existingPos = rs.getLong("lastPosition")
                        existingDur = rs.getLong("duration")
                    }
                }
            }
            val pos = if (entry.lastPosition > 0L) entry.lastPosition else existingPos
            val dur = if (entry.duration > 0L) entry.duration else existingDur

            conn.prepareStatement("DELETE FROM listen_history WHERE songId = ?").use { ps ->
                ps.setLong(1, entry.songId)
                ps.executeUpdate()
            }
            conn.prepareStatement(
                """
                INSERT INTO listen_history(songId, title, artist, albumId, songPath, playedAt, lastPosition, duration)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setLong(1, entry.songId)
                ps.setString(2, entry.title)
                ps.setString(3, entry.artist)
                ps.setLong(4, entry.albumId)
                ps.setString(5, entry.songPath)
                ps.setLong(6, entry.playedAt)
                ps.setLong(7, pos)
                ps.setLong(8, dur)
                ps.executeUpdate()
            }
        }
        reloadAllTables()
    }

    suspend fun updateHistoryPosition(songId: Long, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement(
                "UPDATE listen_history SET lastPosition = ?, duration = ? WHERE songId = ?"
            ).use { ps ->
                ps.setLong(1, positionMs)
                ps.setLong(2, durationMs)
                ps.setLong(3, songId)
                ps.executeUpdate()
            }
        }
        reloadAllTables()
    }

    suspend fun deleteHistoryForSong(songId: Long) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM listen_history WHERE songId = ?").use { ps ->
                ps.setLong(1, songId)
                ps.executeUpdate()
            }
        }
        reloadAllTables()
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.createStatement().executeUpdate("DELETE FROM listen_history")
        }
        reloadAllTables()
    }
}
