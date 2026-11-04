package com.bytegif.glassbeat.util

import org.jaudiotagger.audio.ogg.util.OggCRCFactory
import org.jaudiotagger.tag.TagField
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTagField
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight, pure-Kotlin reader and writer for Ogg Opus metadata tags (RFC 7845 & RFC 3533).
 * Directly ported from GlassBeat Android and compatible with JAudioTagger 2.x and 3.x.
 */
object OpusTagHandler {

    private val OPUS_HEAD_MAGIC = "OpusHead".toByteArray(Charsets.US_ASCII)
    private val OPUS_TAGS_MAGIC = "OpusTags".toByteArray(Charsets.US_ASCII)
    private val OGG_MAGIC = "OggS".toByteArray(Charsets.US_ASCII)

    fun isOpus(file: File): Boolean {
        if (!file.exists() || file.length() < 36) return false
        if (file.extension.equals("opus", ignoreCase = true)) return true

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (!magic.contentEquals(OGG_MAGIC)) return false

                raf.seek(0)
                val page0 = readOggPage(raf) ?: return false
                page0.payload.size >= 8 &&
                    page0.payload.copyOfRange(0, 8).contentEquals(OPUS_HEAD_MAGIC)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun readTags(file: File): VorbisCommentTag? {
        if (!file.exists()) return null

        RandomAccessFile(file, "r").use { raf ->
            val page0 = readOggPage(raf) ?: return null
            if (page0.payload.size < 8 || !page0.payload.copyOfRange(0, 8).contentEquals(OPUS_HEAD_MAGIC)) {
                return null
            }

            val tagsPacketData = extractOpusTagsPacket(raf) ?: return VorbisCommentTag.createNewTag()
            if (tagsPacketData.size < 8 || !tagsPacketData.copyOfRange(0, 8).contentEquals(OPUS_TAGS_MAGIC)) {
                return VorbisCommentTag.createNewTag()
            }

            val vorbisCommentRaw = tagsPacketData.copyOfRange(8, tagsPacketData.size)
            return try {
                parseVorbisCommentBlock(vorbisCommentRaw)
            } catch (e: Exception) {
                e.printStackTrace()
                VorbisCommentTag.createNewTag()
            }
        }
    }

    fun getArtworkBytes(file: File): ByteArray? {
        val tag = readTags(file) ?: return null
        return tag.firstArtwork?.binaryData
    }

    fun writeTags(file: File, tag: VorbisCommentTag) {
        val tempOut = File(file.parentFile, "${file.name}.opustmp_${System.currentTimeMillis()}")
        try {
            RandomAccessFile(file, "r").use { rafIn ->
                val page0 = readOggPage(rafIn) ?: throw IllegalStateException("Invalid Opus file: missing Page 0")
                if (page0.payload.size < 8 || !page0.payload.copyOfRange(0, 8).contentEquals(OPUS_HEAD_MAGIC)) {
                    throw IllegalStateException("Invalid Opus file: missing OpusHead")
                }
                val serialNumber = page0.serialNumber

                var oldTagsPagesCount = 0
                while (true) {
                    val pagePos = rafIn.filePointer
                    val page = readOggPage(rafIn) ?: break
                    if (page.serialNumber != serialNumber) {
                        rafIn.seek(pagePos)
                        break
                    }
                    oldTagsPagesCount++
                    var packetComplete = false
                    for (seg in page.segments) {
                        if (seg < 255) {
                            packetComplete = true
                            break
                        }
                    }
                    if (packetComplete) break
                }
                val audioPagesStartOffset = rafIn.filePointer

                if (tag.vendor.isNullOrBlank()) {
                    tag.vendor = "libopus (GlassBeat)"
                }
                val commentBytes = serializeVorbisCommentBlock(tag)

                val newOpusTagsPacket = ByteArray(OPUS_TAGS_MAGIC.size + commentBytes.size)
                System.arraycopy(OPUS_TAGS_MAGIC, 0, newOpusTagsPacket, 0, OPUS_TAGS_MAGIC.size)
                System.arraycopy(commentBytes, 0, newOpusTagsPacket, OPUS_TAGS_MAGIC.size, commentBytes.size)

                val newTagsPages = packetizeToOggPages(
                    payload = newOpusTagsPacket,
                    serialNumber = serialNumber,
                    startSequenceNumber = 1
                )

                RandomAccessFile(tempOut, "rw").use { rafOut ->
                    rafOut.setLength(0)
                    rafOut.write(page0.rawBytes)
                    for (page in newTagsPages) {
                        rafOut.write(page)
                    }

                    val deltaPages = newTagsPages.size - oldTagsPagesCount
                    rafIn.seek(audioPagesStartOffset)

                    if (deltaPages == 0) {
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (rafIn.read(buffer).also { bytesRead = it } != -1) {
                            rafOut.write(buffer, 0, bytesRead)
                        }
                    } else {
                        while (true) {
                            val audioPage = readOggPage(rafIn) ?: break
                            val updatedPageBytes = audioPage.rawBytes.clone()

                            val newSeq = audioPage.pageSequence + deltaPages
                            ByteBuffer.wrap(updatedPageBytes, 18, 4)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(newSeq)

                            updatedPageBytes[22] = 0
                            updatedPageBytes[23] = 0
                            updatedPageBytes[24] = 0
                            updatedPageBytes[25] = 0

                            val crc = OggCRCFactory.computeCRC(updatedPageBytes)
                            System.arraycopy(crc, 0, updatedPageBytes, 22, 4)

                            rafOut.write(updatedPageBytes)
                        }
                    }
                }
            }
            tempOut.copyTo(file, overwrite = true)
        } finally {
            tempOut.delete()
        }
    }

    /**
     * Parses RFC 7845 / Vorbis Comment binary payload into a JAudioTagger VorbisCommentTag.
     */
    private fun parseVorbisCommentBlock(raw: ByteArray): VorbisCommentTag {
        val tag = VorbisCommentTag.createNewTag()
        if (raw.size < 8) return tag

        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val vendorLen = bb.int
        if (vendorLen < 0 || vendorLen > bb.remaining()) return tag
        val vendorBytes = ByteArray(vendorLen)
        bb.get(vendorBytes)
        tag.vendor = String(vendorBytes, Charsets.UTF_8)

        if (bb.remaining() < 4) return tag
        val commentCount = bb.int
        for (i in 0 until commentCount) {
            if (bb.remaining() < 4) break
            val len = bb.int
            if (len < 0 || len > bb.remaining()) break
            val commentBytes = ByteArray(len)
            bb.get(commentBytes)
            val str = String(commentBytes, Charsets.UTF_8)
            val eqIdx = str.indexOf('=')
            if (eqIdx > 0) {
                val key = str.substring(0, eqIdx).uppercase()
                val value = str.substring(eqIdx + 1)
                tag.addField(VorbisCommentTagField(key, value))
            }
        }
        return tag
    }

    /**
     * Serializes a JAudioTagger VorbisCommentTag into RFC 7845 / Vorbis Comment binary layout.
     */
    private fun serializeVorbisCommentBlock(tag: VorbisCommentTag): ByteArray {
        val vendorStr = tag.vendor.orEmpty().ifBlank { "libopus (GlassBeat)" }
        val vendorBytes = vendorStr.toByteArray(Charsets.UTF_8)

        val fieldByteArrays = mutableListOf<ByteArray>()
        val fieldsIterator = tag.fields
        while (fieldsIterator.hasNext()) {
            val field: TagField = fieldsIterator.next()
            if (field.id.equals("VENDOR", ignoreCase = true)) continue
            if (field is VorbisCommentTagField) {
                val entry = "${field.id.uppercase()}=${field.content}"
                fieldByteArrays.add(entry.toByteArray(Charsets.UTF_8))
            }
        }

        var totalSize = 4 + vendorBytes.size + 4
        for (arr in fieldByteArrays) {
            totalSize += 4 + arr.size
        }

        val out = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(vendorBytes.size)
        out.put(vendorBytes)
        out.putInt(fieldByteArrays.size)
        for (arr in fieldByteArrays) {
            out.putInt(arr.size)
            out.put(arr)
        }
        return out.array()
    }

    private data class RawOggPage(
        val headerType: Int,
        val granulePosition: Long,
        val serialNumber: Int,
        val pageSequence: Int,
        val checksum: Int,
        val segments: IntArray,
        val payload: ByteArray,
        val rawBytes: ByteArray
    )

    private fun readOggPage(raf: RandomAccessFile): RawOggPage? {
        val startOffset = raf.filePointer
        if (startOffset + 27 > raf.length()) return null

        val headerFixed = ByteArray(27)
        raf.readFully(headerFixed)

        if (headerFixed[0] != 0x4F.toByte() || headerFixed[1] != 0x67.toByte() ||
            headerFixed[2] != 0x67.toByte() || headerFixed[3] != 0x53.toByte()
        ) {
            return null
        }

        val bb = ByteBuffer.wrap(headerFixed).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(5)
        val headerType = bb.get().toInt() and 0xFF
        val granule = bb.long
        val serialNumber = bb.int
        val pageSequence = bb.int
        val checksum = bb.int
        val numSegments = bb.get().toInt() and 0xFF

        val segmentTable = ByteArray(numSegments)
        raf.readFully(segmentTable)

        var totalPayloadLen = 0
        val segments = IntArray(numSegments)
        for (i in 0 until numSegments) {
            val len = segmentTable[i].toInt() and 0xFF
            segments[i] = len
            totalPayloadLen += len
        }

        val payload = ByteArray(totalPayloadLen)
        raf.readFully(payload)

        val totalPageSize = 27 + numSegments + totalPayloadLen
        val rawBytes = ByteArray(totalPageSize)
        System.arraycopy(headerFixed, 0, rawBytes, 0, 27)
        System.arraycopy(segmentTable, 0, rawBytes, 27, numSegments)
        System.arraycopy(payload, 0, rawBytes, 27 + numSegments, totalPayloadLen)

        return RawOggPage(
            headerType = headerType,
            granulePosition = granule,
            serialNumber = serialNumber,
            pageSequence = pageSequence,
            checksum = checksum,
            segments = segments,
            payload = payload,
            rawBytes = rawBytes
        )
    }

    private fun extractOpusTagsPacket(raf: RandomAccessFile): ByteArray? {
        val packetStream = ByteArrayOutputStream()
        var packetComplete = false

        while (!packetComplete) {
            val page = readOggPage(raf) ?: break
            var offset = 0
            for (segLen in page.segments) {
                if (offset + segLen <= page.payload.size) {
                    packetStream.write(page.payload, offset, segLen)
                    offset += segLen
                }
                if (segLen < 255) {
                    packetComplete = true
                    break
                }
            }
        }

        return if (packetStream.size() > 0) packetStream.toByteArray() else null
    }

    private fun packetizeToOggPages(
        payload: ByteArray,
        serialNumber: Int,
        startSequenceNumber: Int
    ): List<ByteArray> {
        val pages = mutableListOf<ByteArray>()

        val segmentSizes = mutableListOf<Int>()
        var remaining = payload.size
        while (remaining >= 255) {
            segmentSizes.add(255)
            remaining -= 255
        }
        segmentSizes.add(remaining)

        var segmentIndex = 0
        var payloadOffset = 0
        var currentSeq = startSequenceNumber
        var isContinued = false

        while (segmentIndex < segmentSizes.size) {
            val segCountOnThisPage = minOf(255, segmentSizes.size - segmentIndex)
            var pagePayloadLen = 0
            for (i in 0 until segCountOnThisPage) {
                pagePayloadLen += segmentSizes[segmentIndex + i]
            }

            val totalPageLen = 27 + segCountOnThisPage + pagePayloadLen
            val pageBytes = ByteArray(totalPageLen)
            val bb = ByteBuffer.wrap(pageBytes).order(ByteOrder.LITTLE_ENDIAN)

            bb.put(OGG_MAGIC)
            bb.put(0.toByte())
            val headerType = if (isContinued) 0x01 else 0x00
            bb.put(headerType.toByte())
            bb.putLong(0L)
            bb.putInt(serialNumber)
            bb.putInt(currentSeq)
            bb.putInt(0)
            bb.put(segCountOnThisPage.toByte())

            for (i in 0 until segCountOnThisPage) {
                val seg = segmentSizes[segmentIndex + i].toByte()
                bb.put(seg)
            }

            bb.put(payload, payloadOffset, pagePayloadLen)

            val crc = OggCRCFactory.computeCRC(pageBytes)
            System.arraycopy(crc, 0, pageBytes, 22, 4)

            pages.add(pageBytes)

            payloadOffset += pagePayloadLen
            segmentIndex += segCountOnThisPage
            currentSeq++
            isContinued = true
        }

        return pages
    }
}
