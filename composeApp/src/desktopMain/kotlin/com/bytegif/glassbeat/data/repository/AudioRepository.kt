package com.bytegif.glassbeat.data.repository

import com.bytegif.glassbeat.data.local.AppStorage
import com.bytegif.glassbeat.data.model.Album
import com.bytegif.glassbeat.data.model.Artist
import com.bytegif.glassbeat.data.model.AudioItem
import com.bytegif.glassbeat.data.model.Folder
import com.bytegif.glassbeat.util.AudioArtCache
import com.bytegif.glassbeat.util.OpusTagHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import kotlin.math.abs

object AudioRepository {

    private val SUPPORTED_EXTENSIONS = setOf(
        "mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "aiff", "aif"
    )

    @Volatile
    private var cachedAudioFiles: List<AudioItem>? = null

    @Volatile
    private var lastCacheTimeMs: Long = 0L
    private const val CACHE_VALIDITY_MS = 300_000L // 5 minutes cache

    private val _libraryUpdateEvent = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val libraryUpdateEvent: SharedFlow<Unit> = _libraryUpdateEvent.asSharedFlow()

    fun invalidateCache() {
        cachedAudioFiles = null
        lastCacheTimeMs = 0L
        AudioArtCache.clear()
        _libraryUpdateEvent.tryEmit(Unit)
    }

    private fun isUnknownString(str: String?): Boolean {
        if (str.isNullOrBlank()) return true
        val lower = str.trim().lowercase()
        return lower == "<unknown>" || lower == "unknown" || lower == "unknown artist" || lower == "unknown album" || lower == "unknown title"
    }

    suspend fun getAllAudioFiles(
        forceRefresh: Boolean = false,
        ignoreExclusions: Boolean = false,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<AudioItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val currentCache = cachedAudioFiles
        if (!forceRefresh && !ignoreExclusions && currentCache != null && (now - lastCacheTimeMs) < CACHE_VALIDITY_MS) {
            return@withContext currentCache
        }

        val prefs = AppStorage.state.value
        val excluded = if (ignoreExclusions) emptySet() else prefs.excludedFolders
        val roots = prefs.musicDirectories
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
            .ifEmpty {
                listOf(File(System.getProperty("user.home") ?: ".", "Music"))
            }

        val discoveredFiles = LinkedHashSet<File>()
        for (root in roots) {
            collectAudioFiles(root, excluded, discoveredFiles)
        }

        val filesList = discoveredFiles.toList()
        val total = filesList.size
        val result = ArrayList<AudioItem>(total)

        filesList.forEachIndexed { index, file ->
            onProgress?.invoke(index + 1, total)
            parseAudioItem(file)?.let { item ->
                if (item.duration >= 1000L) {
                    result.add(item)
                }
            }
        }

        val sorted = result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        if (!ignoreExclusions) {
            cachedAudioFiles = sorted
            lastCacheTimeMs = now
        }
        sorted
    }

    private fun collectAudioFiles(dir: File, excluded: Set<String>, out: MutableSet<File>) {
        try {
            val absPath = dir.absolutePath
            if (excluded.any { absPath == it || absPath.startsWith("$it/") }) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.name.startsWith(".")) continue
                if (child.isDirectory) {
                    // Skip Android/Gradle build output directories if scanning inside a project folder
                    if (child.name in setOf("build", ".gradle", ".git", "node_modules")) continue
                    collectAudioFiles(child, excluded, out)
                } else if (child.isFile) {
                    val ext = child.extension.lowercase()
                    if (ext in SUPPORTED_EXTENSIONS) {
                        out.add(child)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun parseAudioItem(file: File): AudioItem? {
        if (!file.exists() || !file.canRead()) return null
        val path = file.absolutePath
        val id = abs(path.hashCode().toLong()).let { if (it == 0L) 1L else it }

        var title = ""
        var artist = ""
        var album = ""
        var durationMs = 0L

        try {
            if (OpusTagHandler.isOpus(file)) {
                val tag = OpusTagHandler.readTags(file)
                if (tag != null) {
                    title = tag.getFirst(FieldKey.TITLE) ?: ""
                    artist = tag.getFirst(FieldKey.ARTIST).orEmpty().ifBlank {
                        tag.getFirst(FieldKey.ALBUM_ARTIST) ?: ""
                    }
                    album = tag.getFirst(FieldKey.ALBUM) ?: ""
                }
                // Estimate duration from file size if header duration not directly read
                durationMs = maxOf(5000L, (file.length() / 16000L) * 1000L)
            } else {
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tag
                if (tag != null) {
                    title = tag.getFirst(FieldKey.TITLE) ?: ""
                    artist = tag.getFirst(FieldKey.ARTIST).orEmpty().ifBlank {
                        tag.getFirst(FieldKey.ALBUM_ARTIST) ?: ""
                    }
                    album = tag.getFirst(FieldKey.ALBUM) ?: ""
                }
                val trackLengthSec = audioFile.audioHeader?.trackLength ?: 0
                if (trackLengthSec > 0) {
                    durationMs = trackLengthSec * 1000L
                }
            }
        } catch (_: Exception) {
        }

        if (durationMs <= 0L) {
            durationMs = maxOf(5000L, (file.length() / 16000L) * 1000L)
        }

        val resolvedTitle = if (!isUnknownString(title)) title.trim() else file.nameWithoutExtension
        val resolvedArtist = if (!isUnknownString(artist)) artist.trim() else "Unknown Artist"
        val resolvedAlbum = if (!isUnknownString(album)) album.trim() else (file.parentFile?.name ?: "Unknown Album")
        val albumId = abs("$resolvedAlbum::$resolvedArtist".hashCode().toLong())

        return AudioItem(
            id = id,
            title = resolvedTitle,
            artist = resolvedArtist,
            album = resolvedAlbum,
            albumId = albumId,
            duration = durationMs,
            data = path,
            uri = file.toURI().toString(),
            dateModified = file.lastModified()
        )
    }

    fun isSupportedAudioFile(file: File): Boolean {
        return file.isFile && file.extension.lowercase() in SUPPORTED_EXTENSIONS
    }

    suspend fun resolveExternalFilesToQueue(inputFiles: List<File>): Pair<List<AudioItem>, Int> = withContext(Dispatchers.IO) {
        val explicitFiles = ArrayList<File>()
        for (item in inputFiles) {
            if (!item.exists()) continue
            if (item.isDirectory) {
                val collected = LinkedHashSet<File>()
                collectAudioFiles(item, emptySet(), collected)
                explicitFiles.addAll(collected)
            } else if (isSupportedAudioFile(item)) {
                explicitFiles.add(item)
            }
        }
        if (explicitFiles.isEmpty()) return@withContext emptyList<AudioItem>() to 0

        // If the user opened a single audio file from Finder, load sibling audio files in the same folder
        // so Previous/Next navigation works seamlessly while starting playback at the clicked file.
        if (explicitFiles.size == 1) {
            val targetFile = explicitFiles.first().canonicalFile
            val targetItem = parseAudioItem(targetFile) ?: return@withContext emptyList<AudioItem>() to 0
            val parentDir = targetFile.parentFile
            val siblingItems = if (parentDir != null && parentDir.isDirectory) {
                parentDir.listFiles()
                    ?.filter { !it.name.startsWith(".") && isSupportedAudioFile(it) }
                    ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    ?.mapNotNull { file ->
                        if (file.canonicalFile == targetFile) targetItem else parseAudioItem(file)
                    }
                    .orEmpty()
            } else {
                emptyList()
            }
            if (siblingItems.isNotEmpty()) {
                val startIdx = siblingItems.indexOfFirst {
                    File(it.data).canonicalPath == targetFile.canonicalPath
                }.coerceAtLeast(0)
                return@withContext siblingItems to startIdx
            }
            return@withContext listOf(targetItem) to 0
        }

        val parsedItems = explicitFiles.mapNotNull { parseAudioItem(it) }
        parsedItems to 0
    }

    suspend fun getAudioFileById(id: Long): AudioItem? = withContext(Dispatchers.IO) {
        getAllAudioFiles().firstOrNull { it.id == id }
    }

    suspend fun getAudioFilesByIds(ids: List<Long>): List<AudioItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val map = getAllAudioFiles().associateBy { it.id }
        ids.mapNotNull { map[it] }
    }

    suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val songs = getAllAudioFiles()
        songs.groupBy { it.albumId }
            .mapNotNull { (albumId, albumSongs) ->
                val firstSong = albumSongs.firstOrNull() ?: return@mapNotNull null
                Album(
                    id = albumId,
                    name = firstSong.album,
                    artist = firstSong.artist,
                    songCount = albumSongs.size,
                    songPath = firstSong.data,
                    songUri = firstSong.uri
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun getArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val songs = getAllAudioFiles()
        songs.groupBy { it.artist }
            .map { (artistName, artistSongs) ->
                val uniqueAlbums = artistSongs.map { it.albumId }.distinct().size
                Artist(
                    id = abs(artistName.hashCode().toLong()),
                    name = artistName,
                    songCount = artistSongs.size,
                    albumCount = uniqueAlbums
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun getFolders(ignoreExclusions: Boolean = false): List<Folder> = withContext(Dispatchers.IO) {
        getAllAudioFiles(ignoreExclusions = ignoreExclusions)
            .groupBy { File(it.data).parent ?: "/" }
            .map { (path, songs) ->
                Folder(
                    path = path,
                    name = File(path).name.ifBlank { path },
                    songCount = songs.size
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun getSongsByAlbum(albumId: Long): List<AudioItem> = withContext(Dispatchers.IO) {
        getAllAudioFiles().filter { it.albumId == albumId }
    }

    suspend fun getSongsByArtist(artistName: String): List<AudioItem> = withContext(Dispatchers.IO) {
        getAllAudioFiles().filter { it.artist == artistName }
    }

    suspend fun getSongsByFolder(folderPath: String): List<AudioItem> = withContext(Dispatchers.IO) {
        getAllAudioFiles().filter { File(it.data).parent == folderPath }
    }

    suspend fun getPodcastAudioFiles(): List<AudioItem> = withContext(Dispatchers.IO) {
        getAllAudioFiles().filter { it.duration > 12 * 60 * 1000L }
    }

    suspend fun getFavoriteAudioFiles(): List<AudioItem> = withContext(Dispatchers.IO) {
        val favIds = AppStorage.favorites.value
        if (favIds.isEmpty()) return@withContext emptyList()
        getAllAudioFiles().filter { it.id in favIds }
    }

    suspend fun getPlaylistSongs(playlistId: Long): List<AudioItem> = withContext(Dispatchers.IO) {
        val ids = AppStorage.getSongIdsForPlaylist(playlistId)
        getAudioFilesByIds(ids)
    }

    suspend fun deleteAudioFiles(songs: List<AudioItem>): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        for (song in songs) {
            try {
                val f = File(song.data)
                if (f.exists() && f.delete()) {
                    deletedCount++
                }
            } catch (_: Exception) {
            }
        }
        if (deletedCount > 0) {
            invalidateCache()
        }
        deletedCount
    }
}
