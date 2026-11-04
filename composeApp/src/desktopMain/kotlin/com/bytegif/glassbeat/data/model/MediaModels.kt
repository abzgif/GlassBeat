package com.bytegif.glassbeat.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val data: String, // Absolute file path on macOS
    val uri: String,  // file:// URI string
    val dateModified: Long = 0L
)

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val songPath: String,
    val songUri: String = ""
)

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int
)

@Serializable
data class PlaylistEntity(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ListenHistoryEntity(
    val id: Long = 0,
    val songId: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val songPath: String = "",
    val playedAt: Long = System.currentTimeMillis(),
    val lastPosition: Long = 0L,   // ms — where the user stopped
    val duration: Long = 0L        // ms — full track duration
)

data class NowPlayingInfo(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
    val lastPosition: Long = 0L,
    val songUri: String = "",
    val filePath: String = "",
    val dateModified: Long = 0L,
    val albumId: Long = -1L
)

data class AudioTechSpecs(
    val format: String = "AUDIO",
    val bitrateKbps: Int? = null,
    val sampleRateKhz: Float? = null
) {
    fun formattedString(): String = buildString {
        append(format)
        if (bitrateKbps != null && bitrateKbps > 0) {
            append(" • $bitrateKbps kb/s")
        }
        if (sampleRateKhz != null && sampleRateKhz > 0f) {
            val formattedHz = if (sampleRateKhz % 1f == 0f) {
                sampleRateKhz.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", sampleRateKhz)
            }
            append(" • $formattedHz kHz")
        }
    }
}

enum class SongSortOrder(val label: String, val subtitle: String) {
    NAME_ASC("Title (A–Z)", "Alphabetical ascending"),
    NAME_DESC("Title (Z–A)", "Alphabetical descending"),
    DATE_DESC("Date Added (Newest)", "Most recently modified first"),
    DATE_ASC("Date Added (Oldest)", "Oldest modified first"),
    DURATION_DESC("Duration (Longest)", "Longest tracks first"),
    DURATION_ASC("Duration (Shortest)", "Shortest tracks first")
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class AppTab(val title: String) {
    SONGS("Songs"),
    EQUALIZER("Equalizer"),
    LIBRARY("Library"),
    HISTORY("History"),
    SETTINGS("Settings")
}

enum class LibrarySection(val title: String) {
    HOME("Library"),
    FAVORITES("Favorites"),
    PLAYLISTS("Playlists"),
    PODCASTS("Podcasts"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
    SONG_LIST("Songs")
}

data class SongMetadataDetails(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val composer: String = "",
    val year: String = "",
    val lyrics: String = "",
    val comment: String = "",
    val artworkBytes: ByteArray? = null,
    val artworkMimeType: String = "image/jpeg"
)
