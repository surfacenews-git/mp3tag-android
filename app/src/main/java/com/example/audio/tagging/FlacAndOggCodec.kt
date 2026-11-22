package com.example.audio.tagging

import android.util.Base64
import com.example.audio.tagging.BinaryUtils.writeBytesSafe
import com.example.model.AudioFormat
import com.example.model.AudioTags
import com.example.model.AudioTechInfo
import com.example.model.CoverArt
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin FLAC & OGG Vorbis Comment codec.
 */
object FlacAndOggCodec {

    data class ParseResult(
        val tags: AudioTags,
        val techInfo: AudioTechInfo
    )

    // --- FLAC ---

    fun readFlac(data: ByteArray): ParseResult {
        if (data.size < 4 || String(data, 0, 4, StandardCharsets.US_ASCII) != "fLaC") {
            return ParseResult(AudioTags(), AudioTechInfo(AudioFormat.FLAC, "Invalid FLAC", fileSizeBytes = data.size.toLong()))
        }

        var pos = 4
        var isLast = false
        var sampleRate = 44100
        var channels = 2
        var totalSamples = 0L
        val comments = linkedMapOf<String, String>()
        var coverArt: CoverArt? = null
        var audioStart = 4

        while (!isLast && pos + 4 <= data.size) {
            val header = data[pos].toInt() and 0xFF
            isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val blockSize = BinaryUtils.readUInt24BE(data, pos + 1)
            pos += 4
            if (pos + blockSize > data.size) break
            val block = data.copyOfRange(pos, pos + blockSize)
            pos += blockSize
            audioStart = pos

            when (blockType) {
                0 -> if (block.size >= 18) { // STREAMINFO
                    val b10 = block[10].toInt() and 0xFF
                    val b11 = block[11].toInt() and 0xFF
                    val b12 = block[12].toInt() and 0xFF
                    val b13 = block[13].toInt() and 0xFF
                    sampleRate = (b10 shl 12) or (b11 shl 4) or (b12 ushr 4)
                    channels = ((b12 ushr 1) and 0x07) + 1
                    totalSamples = (((b13 and 0x0F).toLong() shl 32) or
                        ((block[14].toInt() and 0xFF).toLong() shl 24) or
                        ((block[15].toInt() and 0xFF).toLong() shl 16) or
                        ((block[16].toInt() and 0xFF).toLong() shl 8) or
                        (block[17].toInt() and 0xFF).toLong())
                }
                4 -> parseVorbisCommentBlock(block, comments)
                6 -> parseFlacPictureBlock(block)?.let { art ->
                    if (coverArt == null || art.pictureType == 3) coverArt = art
                }
            }
        }

        val durationMs = if (sampleRate > 0) (totalSamples * 1000L) / sampleRate else 0L
        val audioBytes = (data.size - audioStart).coerceAtLeast(0)
        val bitrate = if (durationMs > 0) ((audioBytes * 8L) / durationMs).toInt() else 0

        val tags = vorbisMapToAudioTags(comments, coverArt)
        val tech = AudioTechInfo(
            format = AudioFormat.FLAC,
            tagSummary = "FLAC (Vorbis)",
            bitrateKbps = bitrate,
            sampleRateHz = sampleRate,
            channels = channels,
            durationMs = durationMs,
            fileSizeBytes = data.size.toLong()
        )
        return ParseResult(tags, tech)
    }

    fun writeFlac(originalData: ByteArray, tags: AudioTags): ByteArray {
        if (originalData.size < 4 || String(originalData, 0, 4, StandardCharsets.US_ASCII) != "fLaC") {
            return originalData
        }

        val preservedBlocks = mutableListOf<Pair<Int, ByteArray>>()
        var pos = 4
        var isLast = false
        while (!isLast && pos + 4 <= originalData.size) {
            val header = originalData[pos].toInt() and 0xFF
            isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val blockSize = BinaryUtils.readUInt24BE(originalData, pos + 1)
            pos += 4
            if (pos + blockSize > originalData.size) break
            if (blockType != 4 && blockType != 6 && blockType != 1) {
                preservedBlocks.add(blockType to originalData.copyOfRange(pos, pos + blockSize))
            }
            pos += blockSize
        }
        val audioFramesOffset = pos

        val vorbisBytes = buildVorbisCommentBlock(tags)
        val newBlocks = mutableListOf<Pair<Int, ByteArray>>()
        newBlocks.addAll(preservedBlocks)
        newBlocks.add(4 to vorbisBytes)
        tags.coverArt?.let { art ->
            newBlocks.add(6 to buildFlacPictureBlock(art))
        }

        val out = ByteArrayOutputStream(originalData.size + 4096)
        out.writeBytesSafe("fLaC".toByteArray(StandardCharsets.US_ASCII))
        newBlocks.forEachIndexed { index, (type, payload) ->
            val lastFlag = if (index == newBlocks.lastIndex) 0x80 else 0x00
            out.write(lastFlag or (type and 0x7F))
            out.write((payload.size ushr 16) and 0xFF)
            out.write((payload.size ushr 8) and 0xFF)
            out.write(payload.size and 0xFF)
            out.writeBytesSafe(payload)
        }
        out.write(originalData, audioFramesOffset, originalData.size - audioFramesOffset)
        return out.toByteArray()
    }

    // --- OGG VORBIS ---

    private data class OggPage(
        val version: Int,
        val headerType: Int,
        val granulePos: Long,
        val serial: Int,
        val seqNo: Int,
        val segments: ByteArray,
        val payload: ByteArray
    )

    fun readOgg(data: ByteArray): ParseResult {
        val pages = parseOggPages(data)
        if (pages.isEmpty()) {
            return ParseResult(AudioTags(), AudioTechInfo(AudioFormat.OGG, "Invalid OGG", fileSizeBytes = data.size.toLong()))
        }

        var sampleRate = 44100
        var channels = 2
        var nominalBitrate = 0
        var maxGranule = 0L
        val comments = linkedMapOf<String, String>()
        var coverArt: CoverArt? = null

        pages.forEach { page ->
            if (page.granulePos > maxGranule) maxGranule = page.granulePos
            val p = page.payload
            if (p.size >= 30 && p[0] == 1.toByte() && String(p, 1, 6, StandardCharsets.US_ASCII) == "vorbis") {
                channels = p[11].toInt() and 0xFF
                sampleRate = BinaryUtils.readIntLE(p, 12)
                nominalBitrate = BinaryUtils.readIntLE(p, 20) / 1000
            } else {
                // Find vorbis comment packet (0x03 + "vorbis")
                val idx = findSubarray(p, byteArrayOf(3, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte()))
                if (idx >= 0) {
                    val commentSlice = p.copyOfRange(idx + 7, p.size)
                    parseVorbisCommentBlock(commentSlice, comments)
                    comments.remove("METADATA_BLOCK_PICTURE")?.let { b64 ->
                        runCatching {
                            val picBytes = Base64.decode(b64, Base64.DEFAULT)
                            coverArt = parseFlacPictureBlock(picBytes)
                        }
                    }
                }
            }
        }

        val durationMs = if (sampleRate > 0 && maxGranule > 0) (maxGranule * 1000L) / sampleRate else 0L
        val bitrate = if (nominalBitrate > 0) nominalBitrate else if (durationMs > 0) ((data.size * 8L) / durationMs).toInt() else 0

        return ParseResult(
            tags = vorbisMapToAudioTags(comments, coverArt),
            techInfo = AudioTechInfo(
                format = AudioFormat.OGG,
                tagSummary = "Ogg Vorbis",
                bitrateKbps = bitrate,
                sampleRateHz = sampleRate,
                channels = channels,
                durationMs = durationMs,
                fileSizeBytes = data.size.toLong()
            )
        )
    }

    fun writeOgg(originalData: ByteArray, tags: AudioTags): ByteArray {
        val pages = parseOggPages(originalData)
        if (pages.size < 2) return originalData

        // Reconstruct packet 2 (comment header)
        val vorbisCommentHeader = ByteArrayOutputStream()
        vorbisCommentHeader.write(3)
        vorbisCommentHeader.writeBytesSafe("vorbis".toByteArray(StandardCharsets.US_ASCII))
        vorbisCommentHeader.writeBytesSafe(buildVorbisCommentBlock(tags, includeCoverBase64 = true))
        vorbisCommentHeader.write(1) // framing bit
        val commentPacket = vorbisCommentHeader.toByteArray()

        // Find the page containing the setup header (packet type 5 + "vorbis")
        val setupTag = byteArrayOf(5, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())
        val setupPageIdx = pages.indexOfFirst { findSubarray(it.payload, setupTag) >= 0 }
        if (setupPageIdx <= 0) return originalData

        val setupPage = pages[setupPageIdx]
        val setupOffset = findSubarray(setupPage.payload, setupTag)
        val setupPacket = setupPage.payload.copyOfRange(setupOffset, setupPage.payload.size)

        val out = ByteArrayOutputStream(originalData.size + commentPacket.size)
        // Page 0: Identification header (unchanged)
        out.writeBytesSafe(encodeOggPage(pages[0]))

        // Write updated comment + setup packets into clean OGG pages
        val serial = pages[0].serial
        var seq = 1
        val combinedPackets = listOf(commentPacket, setupPacket)
        combinedPackets.forEach { pkt ->
            val chunks = chunkPacketToOggPages(pkt, serial, seq)
            chunks.forEach { pageBytes ->
                out.writeBytesSafe(pageBytes)
                seq++
            }
        }

        // Write remaining audio pages with updated sequence numbers and recalculated CRC32
        for (i in (setupPageIdx + 1) until pages.size) {
            val updated = pages[i].copy(seqNo = seq++)
            out.writeBytesSafe(encodeOggPage(updated))
        }
        return out.toByteArray()
    }

    private fun parseOggPages(data: ByteArray): List<OggPage> {
        val pages = mutableListOf<OggPage>()
        var pos = 0
        while (pos + 27 <= data.size) {
            if (data[pos] != 'O'.code.toByte() || data[pos + 1] != 'g'.code.toByte() ||
                data[pos + 2] != 'g'.code.toByte() || data[pos + 3] != 'S'.code.toByte()
            ) {
                break
            }
            val version = data[pos + 4].toInt() and 0xFF
            val headerType = data[pos + 5].toInt() and 0xFF
            val granule = java.nio.ByteBuffer.wrap(data, pos + 6, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).long
            val serial = BinaryUtils.readIntLE(data, pos + 14)
            val seqNo = BinaryUtils.readIntLE(data, pos + 18)
            val segCount = data[pos + 26].toInt() and 0xFF
            if (pos + 27 + segCount > data.size) break
            val segments = data.copyOfRange(pos + 27, pos + 27 + segCount)
            var payloadLen = 0
            for (s in segments) payloadLen += (s.toInt() and 0xFF)
            val payloadStart = pos + 27 + segCount
            if (payloadStart + payloadLen > data.size) break
            val payload = data.copyOfRange(payloadStart, payloadStart + payloadLen)
            pages.add(OggPage(version, headerType, granule, serial, seqNo, segments, payload))
            pos = payloadStart + payloadLen
        }
        return pages
    }

    private fun chunkPacketToOggPages(packet: ByteArray, serial: Int, startSeq: Int): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var offset = 0
        var seq = startSeq
        var continued = false
        while (offset < packet.size || result.isEmpty()) {
            val maxPagePayload = 255 * 255
            val sliceLen = minOf(maxPagePayload, packet.size - offset)
            val slice = packet.copyOfRange(offset, offset + sliceLen)
            val segs = ByteArrayOutputStream()
            var rem = sliceLen
            while (rem >= 255) {
                segs.write(255)
                rem -= 255
            }
            if (offset + sliceLen == packet.size) {
                segs.write(rem) // terminates packet (< 255)
            }
            val headerType = if (continued) 0x01 else 0x00
            result.add(encodeOggPage(OggPage(0, headerType, 0L, serial, seq++, segs.toByteArray(), slice)))
            offset += sliceLen
            continued = true
            if (offset >= packet.size) break
        }
        return result
    }

    private fun encodeOggPage(page: OggPage): ByteArray {
        val totalSize = 27 + page.segments.size + page.payload.size
        val buf = ByteArray(totalSize)
        buf[0] = 'O'.code.toByte()
        buf[1] = 'g'.code.toByte()
        buf[2] = 'g'.code.toByte()
        buf[3] = 'S'.code.toByte()
        buf[4] = page.version.toByte()
        buf[5] = page.headerType.toByte()
        val granuleBytes = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(page.granulePos).array()
        System.arraycopy(granuleBytes, 0, buf, 6, 8)
        System.arraycopy(BinaryUtils.writeIntLE(page.serial), 0, buf, 14, 4)
        System.arraycopy(BinaryUtils.writeIntLE(page.seqNo), 0, buf, 18, 4)
        // CRC bytes [22..25] zeroed before calculation
        buf[26] = page.segments.size.toByte()
        System.arraycopy(page.segments, 0, buf, 27, page.segments.size)
        System.arraycopy(page.payload, 0, buf, 27 + page.segments.size, page.payload.size)
        val crc = BinaryUtils.oggCrc32(buf)
        System.arraycopy(BinaryUtils.writeIntLE(crc), 0, buf, 22, 4)
        return buf
    }

    // --- Shared Vorbis Comment & FLAC Picture helpers ---

    private fun parseVorbisCommentBlock(block: ByteArray, dest: MutableMap<String, String>) {
        if (block.size < 8) return
        var pos = 0
        val vendorLen = BinaryUtils.readIntLE(block, pos)
        pos += 4
        if (vendorLen < 0 || pos + vendorLen + 4 > block.size) return
        pos += vendorLen
        val count = BinaryUtils.readIntLE(block, pos)
        pos += 4
        for (i in 0 until count.coerceAtMost(500)) {
            if (pos + 4 > block.size) break
            val len = BinaryUtils.readIntLE(block, pos)
            pos += 4
            if (len < 0 || pos + len > block.size) break
            val entry = String(block, pos, len, StandardCharsets.UTF_8)
            pos += len
            val eq = entry.indexOf('=')
            if (eq > 0) {
                val key = entry.substring(0, eq).uppercase()
                val value = entry.substring(eq + 1)
                dest[key] = value
            }
        }
    }

    private fun buildVorbisCommentBlock(tags: AudioTags, includeCoverBase64: Boolean = false): ByteArray {
        val entries = mutableListOf<String>()
        fun add(k: String, v: String) {
            if (v.isNotEmpty()) entries.add("$k=$v")
        }
        add("TITLE", tags.title)
        add("ARTIST", tags.artist)
        add("ALBUM", tags.album)
        add("ALBUMARTIST", tags.albumArtist)
        add("DATE", tags.year)
        if (tags.track.contains('/')) {
            add("TRACKNUMBER", tags.track.substringBefore('/'))
            add("TRACKTOTAL", tags.track.substringAfter('/'))
        } else {
            add("TRACKNUMBER", tags.track)
        }
        if (tags.discNumber.contains('/')) {
            add("DISCNUMBER", tags.discNumber.substringBefore('/'))
            add("DISCTOTAL", tags.discNumber.substringAfter('/'))
        } else {
            add("DISCNUMBER", tags.discNumber)
        }
        add("GENRE", tags.genre)
        add("COMMENT", tags.comment)
        add("COMPOSER", tags.composer)
        add("PUBLISHER", tags.publisher)
        add("COPYRIGHT", tags.copyright)
        add("LYRICS", tags.lyrics)
        add("BPM", tags.bpm)
        add("ENCODED-BY", tags.encodedBy)
        if (tags.compilation) add("COMPILATION", "1")
        tags.customFields.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotEmpty()) add(k.uppercase(), v)
        }
        if (includeCoverBase64 && tags.coverArt != null) {
            val picBlock = buildFlacPictureBlock(tags.coverArt)
            val b64 = Base64.encodeToString(picBlock, Base64.NO_WRAP)
            add("METADATA_BLOCK_PICTURE", b64)
        }

        val vendor = "Mp3tag Android".toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.writeBytesSafe(BinaryUtils.writeIntLE(vendor.size))
        out.writeBytesSafe(vendor)
        out.writeBytesSafe(BinaryUtils.writeIntLE(entries.size))
        entries.forEach { e ->
            val b = e.toByteArray(StandardCharsets.UTF_8)
            out.writeBytesSafe(BinaryUtils.writeIntLE(b.size))
            out.writeBytesSafe(b)
        }
        return out.toByteArray()
    }

    private fun parseFlacPictureBlock(block: ByteArray): CoverArt? {
        if (block.size < 32) return null
        var pos = 0
        val picType = BinaryUtils.readIntBE(block, pos); pos += 4
        val mimeLen = BinaryUtils.readIntBE(block, pos); pos += 4
        if (mimeLen < 0 || pos + mimeLen + 4 > block.size) return null
        val mime = String(block, pos, mimeLen, StandardCharsets.US_ASCII); pos += mimeLen
        val descLen = BinaryUtils.readIntBE(block, pos); pos += 4
        if (descLen < 0 || pos + descLen + 20 > block.size) return null
        val desc = String(block, pos, descLen, StandardCharsets.UTF_8); pos += descLen
        val width = BinaryUtils.readIntBE(block, pos); pos += 4
        val height = BinaryUtils.readIntBE(block, pos); pos += 4
        pos += 8 // colorDepth + indexedColors
        val dataLen = BinaryUtils.readIntBE(block, pos); pos += 4
        if (dataLen <= 0 || pos + dataLen > block.size) return null
        val imgBytes = block.copyOfRange(pos, pos + dataLen)
        return CoverArt(
            bytes = imgBytes,
            mimeType = mime.ifBlank { BinaryUtils.detectImageMimeType(imgBytes) },
            pictureType = picType,
            description = desc,
            width = width,
            height = height
        )
    }

    private fun buildFlacPictureBlock(art: CoverArt): ByteArray {
        val out = ByteArrayOutputStream(art.bytes.size + 64)
        val mimeBytes = art.mimeType.toByteArray(StandardCharsets.US_ASCII)
        val descBytes = art.description.toByteArray(StandardCharsets.UTF_8)
        out.writeBytesSafe(BinaryUtils.writeIntBE(art.pictureType))
        out.writeBytesSafe(BinaryUtils.writeIntBE(mimeBytes.size))
        out.writeBytesSafe(mimeBytes)
        out.writeBytesSafe(BinaryUtils.writeIntBE(descBytes.size))
        out.writeBytesSafe(descBytes)
        out.writeBytesSafe(BinaryUtils.writeIntBE(art.width))
        out.writeBytesSafe(BinaryUtils.writeIntBE(art.height))
        out.writeBytesSafe(BinaryUtils.writeIntBE(24))
        out.writeBytesSafe(BinaryUtils.writeIntBE(0))
        out.writeBytesSafe(BinaryUtils.writeIntBE(art.bytes.size))
        out.writeBytesSafe(art.bytes)
        return out.toByteArray()
    }

    private fun vorbisMapToAudioTags(map: MutableMap<String, String>, coverArt: CoverArt?): AudioTags {
        val known = setOf(
            "TITLE", "ARTIST", "ALBUM", "ALBUMARTIST", "ALBUM ARTIST", "DATE", "YEAR",
            "TRACKNUMBER", "TRACKTOTAL", "TOTALTRACKS", "DISCNUMBER", "DISCTOTAL", "TOTALDISCS",
            "GENRE", "COMMENT", "DESCRIPTION", "COMPOSER", "PUBLISHER", "ORGANIZATION", "LABEL",
            "COPYRIGHT", "LYRICS", "UNSYNCEDLYRICS", "BPM", "TEMPO", "ENCODED-BY", "ENCODER",
            "COMPILATION", "METADATA_BLOCK_PICTURE"
        )
        val trackNum = map["TRACKNUMBER"] ?: ""
        val trackTot = map["TRACKTOTAL"] ?: map["TOTALTRACKS"] ?: ""
        val track = if (trackTot.isNotEmpty() && trackNum.isNotEmpty()) "$trackNum/$trackTot" else trackNum

        val discNum = map["DISCNUMBER"] ?: ""
        val discTot = map["DISCTOTAL"] ?: map["TOTALDISCS"] ?: ""
        val disc = if (discTot.isNotEmpty() && discNum.isNotEmpty()) "$discNum/$discTot" else discNum

        val custom = linkedMapOf<String, String>()
        map.forEach { (k, v) ->
            if (k !in known) custom[k] = v
        }

        return AudioTags(
            title = map["TITLE"] ?: "",
            artist = map["ARTIST"] ?: "",
            album = map["ALBUM"] ?: "",
            albumArtist = map["ALBUMARTIST"] ?: map["ALBUM ARTIST"] ?: "",
            year = (map["DATE"] ?: map["YEAR"] ?: "").take(10),
            track = track,
            discNumber = disc,
            genre = map["GENRE"] ?: "",
            comment = map["COMMENT"] ?: map["DESCRIPTION"] ?: "",
            composer = map["COMPOSER"] ?: "",
            publisher = map["PUBLISHER"] ?: map["ORGANIZATION"] ?: map["LABEL"] ?: "",
            copyright = map["COPYRIGHT"] ?: "",
            lyrics = map["LYRICS"] ?: map["UNSYNCEDLYRICS"] ?: "",
            bpm = map["BPM"] ?: map["TEMPO"] ?: "",
            encodedBy = map["ENCODED-BY"] ?: map["ENCODER"] ?: "",
            compilation = map["COMPILATION"] == "1" || map["COMPILATION"].equals("true", true),
            customFields = custom,
            coverArt = coverArt
        )
    }

    private fun findSubarray(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
