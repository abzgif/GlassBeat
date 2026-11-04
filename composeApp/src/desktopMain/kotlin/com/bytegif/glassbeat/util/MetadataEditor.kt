package com.bytegif.glassbeat.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.bytegif.glassbeat.data.model.AudioItem
import com.bytegif.glassbeat.data.model.AudioTechSpecs
import com.bytegif.glassbeat.data.model.SongMetadataDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.jetbrains.skia.Image
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

object AudioArtCache {
    private val bitmapCache = ConcurrentHashMap<String, ImageBitmap>()
    private val noArtPaths: MutableSet<String> = Collections.synchronizedSet(HashSet())

    init {
        // Silence noisy JAudioTagger console logs
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun getCachedBitmap(filePath: String): ImageBitmap? = bitmapCache[filePath]

    fun hasNoArt(filePath: String): Boolean = noArtPaths.contains(filePath)

    suspend fun loadArtworkBitmap(filePath: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (filePath.isBlank() || noArtPaths.contains(filePath)) return@withContext null
        bitmapCache[filePath]?.let { return@withContext it }

        val bytes = MetadataEditor.extractArtworkBytes(filePath)
        if (bytes != null && bytes.isNotEmpty()) {
            try {
                val bmp = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                bitmapCache[filePath] = bmp
                return@withContext bmp
            } catch (_: Exception) {
            }
        }
        noArtPaths.add(filePath)
        null
    }

    fun invalidate(filePath: String) {
        bitmapCache.remove(filePath)
        noArtPaths.remove(filePath)
    }

    fun clear() {
        bitmapCache.clear()
        noArtPaths.clear()
    }
}

/**
 * Full port of EditMetadataActivity and NowPlayingActivity tech-spec/tag reading & writing.
 */
object MetadataEditor {

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun extractArtworkBytes(filePath: String): ByteArray? {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null
        return try {
            if (OpusTagHandler.isOpus(file)) {
                OpusTagHandler.getArtworkBytes(file)
            } else {
                val audioFile = AudioFileIO.read(file)
                audioFile.tag?.firstArtwork?.binaryData
            }
        } catch (_: Exception) {
            // Also check for cover.jpg / folder.jpg in parent folder as desktop convenience
            val parent = file.parentFile
            if (parent != null && parent.exists()) {
                val coverFile = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "artwork.jpg")
                    .map { File(parent, it) }
                    .firstOrNull { it.exists() && it.canRead() }
                coverFile?.readBytes()
            } else null
        }
    }

    fun extractTechSpecs(filePath: String): AudioTechSpecs {
        val file = File(filePath)
        val ext = file.extension.uppercase()
        val format = when (ext) {
            "MP3" -> "MP3"
            "M4A", "AAC" -> "M4A"
            "FLAC" -> "FLAC"
            "WAV" -> "WAV"
            "OGG" -> "OGG"
            "OPUS" -> "OPUS"
            else -> ext.ifBlank { "AUDIO" }
        }
        if (!file.exists() || !file.canRead()) {
            return AudioTechSpecs(format = format)
        }
        return try {
            if (OpusTagHandler.isOpus(file)) {
                AudioTechSpecs(format = "OPUS", bitrateKbps = null, sampleRateKhz = 48.0f)
            } else {
                val audioFile = AudioFileIO.read(file)
                val header = audioFile.audioHeader
                val bitrate = header?.bitRateAsNumber?.toInt()
                val sampleRateHz = header?.sampleRateAsNumber ?: 0
                val sampleRateKhz = if (sampleRateHz > 0) sampleRateHz / 1000f else null
                AudioTechSpecs(
                    format = format,
                    bitrateKbps = if (bitrate != null && bitrate > 0) bitrate else null,
                    sampleRateKhz = sampleRateKhz
                )
            }
        } catch (_: Exception) {
            AudioTechSpecs(format = format)
        }
    }

    suspend fun loadFullMetadata(song: AudioItem): SongMetadataDetails = withContext(Dispatchers.IO) {
        val file = File(song.data)
        var tagTitle = song.title
        var tagArtist = song.artist
        var tagAlbum = song.album
        var tagAlbumArtist = ""
        var tagGenre = ""
        var tagComposer = ""
        var tagYear = ""
        var tagLyrics = ""
        var tagComment = ""
        var embeddedArtBytes: ByteArray? = null
        var mimeType = "image/jpeg"

        try {
            if (file.exists() && file.canRead()) {
                val isOpus = OpusTagHandler.isOpus(file)
                val tag: Tag? = if (isOpus) {
                    OpusTagHandler.readTags(file)
                } else {
                    AudioFileIO.read(file).tag
                }

                if (tag != null) {
                    val t = tag.getFirst(FieldKey.TITLE)
                    if (!t.isNullOrBlank()) tagTitle = t.trim()

                    val a = tag.getFirst(FieldKey.ARTIST)
                    if (!a.isNullOrBlank()) tagArtist = a.trim()

                    val al = tag.getFirst(FieldKey.ALBUM)
                    if (!al.isNullOrBlank()) tagAlbum = al.trim()

                    tagAlbumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST) ?: ""
                    tagGenre = tag.getFirst(FieldKey.GENRE) ?: ""
                    tagComposer = tag.getFirst(FieldKey.COMPOSER) ?: ""
                    tagYear = readYear(tag)
                    tagLyrics = tag.getFirst(FieldKey.LYRICS) ?: ""
                    tagComment = tag.getFirst(FieldKey.COMMENT) ?: ""

                    val artwork = tag.firstArtwork
                    if (artwork != null && artwork.binaryData != null) {
                        embeddedArtBytes = artwork.binaryData
                        mimeType = artwork.mimeType ?: "image/jpeg"
                    }
                }

                if (embeddedArtBytes == null && isOpus) {
                    embeddedArtBytes = OpusTagHandler.getArtworkBytes(file)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        SongMetadataDetails(
            title = tagTitle.ifBlank { file.nameWithoutExtension },
            artist = tagArtist,
            album = tagAlbum,
            albumArtist = tagAlbumArtist,
            genre = tagGenre,
            composer = tagComposer,
            year = tagYear,
            lyrics = tagLyrics,
            comment = tagComment,
            artworkBytes = embeddedArtBytes,
            artworkMimeType = mimeType
        )
    }

    suspend fun saveMetadata(
        song: AudioItem,
        details: SongMetadataDetails,
        isArtworkDeleted: Boolean,
        newArtworkBytes: ByteArray?,
        newArtworkMimeType: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(song.data)
        if (!file.exists() || !file.canWrite()) {
            return@withContext Result.failure(IllegalStateException("File is not writable: ${file.absolutePath}"))
        }

        val tempFile = File(file.parentFile, ".glassbeat_edit_${System.currentTimeMillis()}_${file.name}")
        try {
            file.copyTo(tempFile, overwrite = true)

            val isOpus = OpusTagHandler.isOpus(tempFile)
            val audioObj = if (!isOpus) AudioFileIO.read(tempFile) else null
            val tag: Tag = if (isOpus) {
                OpusTagHandler.readTags(tempFile) ?: VorbisCommentTag.createNewTag()
            } else {
                audioObj!!.tagOrCreateAndSetDefault ?: audioObj.tag ?: ID3v23Tag().also { audioObj.tag = it }
            }

            safeSetOrDeleteField(tag, FieldKey.TITLE, details.title)
            safeSetOrDeleteField(tag, FieldKey.ARTIST, details.artist)
            safeSetOrDeleteField(tag, FieldKey.ALBUM, details.album)
            safeSetOrDeleteField(tag, FieldKey.ALBUM_ARTIST, details.albumArtist)
            if (isOpus) {
                safeSetOrDeleteField(tag, FieldKey.YEAR, details.year)
            } else {
                writeYear(audioObj!!, tag, details.year)
            }
            safeSetOrDeleteField(tag, FieldKey.GENRE, details.genre)
            safeSetOrDeleteField(tag, FieldKey.COMPOSER, details.composer)
            safeSetOrDeleteField(tag, FieldKey.LYRICS, details.lyrics)
            safeSetOrDeleteField(tag, FieldKey.COMMENT, details.comment)

            if (isArtworkDeleted) {
                try { tag.deleteArtworkField() } catch (_: Exception) {}
            } else if (newArtworkBytes != null) {
                try { tag.deleteArtworkField() } catch (_: Exception) {}
                safeSetArtwork(tag, newArtworkBytes, newArtworkMimeType ?: "image/jpeg")
            }

            if (isOpus) {
                OpusTagHandler.writeTags(tempFile, tag as VorbisCommentTag)
            } else {
                AudioFileIO.write(audioObj!!)
            }

            tempFile.copyTo(file, overwrite = true)
            AudioArtCache.invalidate(file.absolutePath)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    private fun readYear(tag: Tag): String {
        return try {
            val raw = tag.getFirst(FieldKey.YEAR) ?: ""
            if (raw.isBlank()) return ""
            val trimmed = raw.trim()
            if (trimmed.contains("--") || trimmed.startsWith("-") || trimmed.startsWith("0-") || trimmed == "0000" || trimmed == "0") {
                val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(trimmed)
                return match?.value ?: ""
            }
            trimmed
        } catch (_: Exception) {
            ""
        }
    }

    private fun writeYear(audioObj: AudioFile, tag: Tag, yearValue: String?) {
        val trimmed = yearValue?.trim() ?: ""

        if (tag is AbstractID3v2Tag) {
            val dateFrames = listOf("TYER", "TDAT", "TIME", "TRDA", "TYERTDAT", "TYE", "TDA", "TIM", "TRD", "TDRC", "TDRL", "TDEN", "TDTG")
            for (frame in dateFrames) {
                try { tag.removeFrame(frame) } catch (_: Exception) {}
            }
            try { tag.deleteField(FieldKey.YEAR) } catch (_: Exception) {}

            if (trimmed.isBlank()) {
                if (audioObj is MP3File) {
                    try { audioObj.iD3v1Tag?.deleteField(FieldKey.YEAR) } catch (_: Exception) {}
                }
            } else {
                val isFullDate = Regex("^(1\\d{3}|2\\d{3})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$").matches(trimmed)
                if (isFullDate) {
                    safeSetOrDeleteField(tag, FieldKey.YEAR, trimmed)
                } else {
                    try {
                        val frameId = if (tag is ID3v23Tag) "TYER" else "TDRC"
                        val frame = tag.createFrame(frameId)
                        val body = frame.body
                        if (body is org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyTextInfo) {
                            body.text = trimmed
                        }
                        tag.setFrame(frame)
                    } catch (_: Exception) {
                        safeSetOrDeleteField(tag, FieldKey.YEAR, trimmed)
                    }
                }

                if (audioObj is MP3File) {
                    val y4 = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(trimmed)?.value ?: ""
                    try {
                        val v1 = audioObj.iD3v1Tag
                        if (v1 != null) {
                            if (y4.isNotEmpty()) v1.setField(FieldKey.YEAR, y4)
                            else v1.deleteField(FieldKey.YEAR)
                        }
                    } catch (_: Exception) {}
                }
            }
        } else {
            safeSetOrDeleteField(tag, FieldKey.YEAR, trimmed.ifBlank { null })
        }
    }

    private fun safeSetOrDeleteField(tag: Tag, key: FieldKey, value: String?) {
        try {
            if (value.isNullOrBlank()) {
                try { tag.deleteField(key) } catch (_: Exception) {}
            } else {
                val trimmed = value.trim()
                try {
                    val field = tag.createField(key, trimmed)
                    if (field != null) tag.setField(field) else tag.deleteField(key)
                } catch (_: Exception) {
                    try { tag.setField(key, trimmed) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun safeSetArtwork(tag: Tag, artworkBytes: ByteArray, mimeType: String) {
        try {
            val artwork = ArtworkFactory.getNew()
            artwork.binaryData = artworkBytes
            artwork.mimeType = mimeType.ifBlank { "image/jpeg" }
            artwork.description = ""
            artwork.isLinked = false

            val artField = tag.createField(artwork)
            if (artField != null) tag.setField(artField) else tag.setField(artwork)
        } catch (_: Exception) {
            try {
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = artworkBytes
                artwork.mimeType = "image/jpeg"
                tag.setField(artwork)
            } catch (_: Exception) {}
        }
    }
}
