package com.example.audio.tagging

import com.example.audio.tagging.BinaryUtils.writeBytesSafe
import com.example.model.AudioFormat
import com.example.model.AudioTags
import com.example.model.AudioTechInfo
import com.example.model.CoverArt
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin ID3v2.2 / ID3v2.3 / ID3v2.4 + ID3v1.1 codec.
 * Preserves raw MPEG audio frames bit-for-bit when updating or stripping tags.
 */
object Id3TagCodec {

    data class ParseResult(
        val tags: AudioTags,
        val techInfo: AudioTechInfo,
        val audioStartOffset: Int,
        val audioEndOffset: Int
    )

    fun read(data: ByteArray): ParseResult {
        var audioStart = 0
        var audioEnd = data.size
        val tagSummaryParts = mutableListOf<String>()

        var title = ""
        var artist = ""
        var album = ""
        var albumArtist = ""
        var year = ""
        var track = ""
        var disc = ""
        var genre = ""
        var comment = ""
        var composer = ""
        var publisher = ""
        var copyright = ""
        var lyrics = ""
        var bpm = ""
        var encodedBy = ""
        var compilation = false
        val customFields = linkedMapOf<String, String>()
        var coverArt: CoverArt? = null

        // Check ID3v1 at end (128 bytes starting with "TAG")
        if (data.size >= 128 &&
            data[data.size - 128] == 'T'.code.toByte() &&
            data[data.size - 127] == 'A'.code.toByte() &&
            data[data.size - 126] == 'G'.code.toByte()
        ) {
            tagSummaryParts.add("ID3v1")
            audioEnd = data.size - 128
            val base = data.size - 128
            title = decodeLatin1Trimmed(data, base + 3, 30)
            artist = decodeLatin1Trimmed(data, base + 33, 30)
            album = decodeLatin1Trimmed(data, base + 63, 30)
            year = decodeLatin1Trimmed(data, base + 93, 4)
            val zeroByte = data[base + 125].toInt() and 0xFF
            val trkByte = data[base + 126].toInt() and 0xFF
            if (zeroByte == 0 && trkByte > 0) {
                comment = decodeLatin1Trimmed(data, base + 97, 28)
                track = trkByte.toString()
            } else {
                comment = decodeLatin1Trimmed(data, base + 97, 30)
            }
            val genreIdx = data[base + 127].toInt() and 0xFF
            if (genreIdx in BinaryUtils.ID3V1_GENRES.indices) {
                genre = BinaryUtils.ID3V1_GENRES[genreIdx]
            }
        }

        // Check ID3v2 header at offset 0
        if (data.size >= 10 &&
            data[0] == 'I'.code.toByte() &&
            data[1] == 'D'.code.toByte() &&
            data[2] == '3'.code.toByte()
        ) {
            val majorVersion = data[3].toInt() and 0xFF
            val flags = data[5].toInt() and 0xFF
            val tagSize = BinaryUtils.decodeSynchsafeInt(
                data[6].toInt(), data[7].toInt(), data[8].toInt(), data[9].toInt()
            )
            tagSummaryParts.add("ID3v2.$majorVersion")
            val tagEnd = minOf(10 + tagSize, data.size)
            audioStart = tagEnd

            val unsyncAll = (flags and 0x80) != 0
            var body = data.copyOfRange(10, tagEnd)
            if (unsyncAll && majorVersion < 4) {
                body = removeUnsynchronization(body)
            }

            var pos = 0
            // Skip extended header if present
            if ((flags and 0x40) != 0 && body.size >= 4) {
                val extSize = if (majorVersion >= 4) {
                    BinaryUtils.decodeSynchsafeInt(body[0].toInt(), body[1].toInt(), body[2].toInt(), body[3].toInt())
                } else {
                    4 + BinaryUtils.readIntBE(body, 0)
                }
                pos = extSize.coerceIn(0, body.size)
            }

            while (pos < body.size) {
                if (body[pos].toInt() == 0) break // padding reached

                if (majorVersion == 2) {
                    if (pos + 6 > body.size) break
                    val frameId = String(body, pos, 3, StandardCharsets.US_ASCII)
                    val frameSize = BinaryUtils.readUInt24BE(body, pos + 3)
                    pos += 6
                    if (frameSize <= 0 || pos + frameSize > body.size) break
                    val payload = body.copyOfRange(pos, pos + frameSize)
                    pos += frameSize

                    when (frameId) {
                        "TT2" -> decodeTextFrame(payload).takeIf { it.isNotEmpty() }?.let { title = it }
                        "TP1" -> decodeTextFrame(payload).takeIf { it.isNotEmpty() }?.let { artist = it }
                        "TAL" -> decodeTextFrame(payload).takeIf { it.isNotEmpty() }?.let { album = it }
                        "TP2" -> decodeTextFrame(payload).takeIf { it.isNotEmpty() }?.let { albumArtist = it }
                        "TYE" -> decodeTextFrame(payload).takeIf { it.isNotEmpty() }?.let { year = it }
                        "TRK" -> decodeTextFrame(payload).takeIf { it.isNotEmpty() }?.let { track = it }
                        "TPA" -> decodeTextFrame(payload).takeIf { it.isNotEmpty() }?.let { disc = it }
                        "TCO" -> normalizeGenre(decodeTextFrame(payload)).takeIf { it.isNotEmpty() }?.let { genre = it }
                        "TCM" -> decodeTextFrame(payload).takeIf { it.isNotEmpty() }?.let { composer = it }
                        "COM" -> decodeCommentOrLyricsFrame(payload).takeIf { it.isNotEmpty() }?.let { comment = it }
                        "PIC" -> decodePicV22(payload)?.let { if (coverArt == null) coverArt = it }
                    }
                } else {
                    if (pos + 10 > body.size) break
                    val frameId = String(body, pos, 4, StandardCharsets.US_ASCII)
                    val frameSize = if (majorVersion >= 4) {
                        BinaryUtils.decodeSynchsafeInt(
                            body[pos + 4].toInt(), body[pos + 5].toInt(),
                            body[pos + 6].toInt(), body[pos + 7].toInt()
                        )
                    } else {
                        BinaryUtils.readIntBE(body, pos + 4)
                    }
                    val frameFlags2 = body[pos + 9].toInt() and 0xFF
                    pos += 10
                    if (frameSize <= 0 || pos + frameSize > body.size) break
                    var payload = body.copyOfRange(pos, pos + frameSize)
                    pos += frameSize

                    if (majorVersion >= 4 && (frameFlags2 and 0x02) != 0) {
                        payload = removeUnsynchronization(payload)
                    }

                    when (frameId) {
                        "TIT2" -> title = decodeTextFrame(payload)
                        "TPE1" -> artist = decodeTextFrame(payload)
                        "TALB" -> album = decodeTextFrame(payload)
                        "TPE2" -> albumArtist = decodeTextFrame(payload)
                        "TYER", "TDRC" -> if (year.isEmpty() || frameId == "TDRC") {
                            year = decodeTextFrame(payload).take(10)
                        }
                        "TRCK" -> track = decodeTextFrame(payload)
                        "TPOS" -> disc = decodeTextFrame(payload)
                        "TCON" -> genre = normalizeGenre(decodeTextFrame(payload))
                        "TCOM" -> composer = decodeTextFrame(payload)
                        "TPUB" -> publisher = decodeTextFrame(payload)
                        "TCOP" -> copyright = decodeTextFrame(payload)
                        "TENC" -> encodedBy = decodeTextFrame(payload)
                        "TBPM" -> bpm = decodeTextFrame(payload)
                        "TCMP" -> compilation = decodeTextFrame(payload) == "1"
                        "COMM" -> comment = decodeCommentOrLyricsFrame(payload)
                        "USLT" -> lyrics = decodeCommentOrLyricsFrame(payload)
                        "TXXX" -> decodeTxxxFrame(payload)?.let { (k, v) ->
                            if (k.isNotBlank()) customFields[k] = v
                        }
                        "APIC" -> decodeApicFrame(payload)?.let { art ->
                            if (coverArt == null || art.pictureType == 3) coverArt = art
                        }
                    }
                }
            }
        }

        val mp3Info = BinaryUtils.parseMp3AudioInfo(data, audioStart, audioEnd)
        val tagSummary = if (tagSummaryParts.isEmpty()) "No Tags" else tagSummaryParts.joinToString(" + ")

        val tags = AudioTags(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            year = year,
            track = track,
            discNumber = disc,
            genre = genre,
            comment = comment,
            composer = composer,
            publisher = publisher,
            copyright = copyright,
            lyrics = lyrics,
            bpm = bpm,
            encodedBy = encodedBy,
            compilation = compilation,
            customFields = customFields,
            coverArt = coverArt
        )

        val tech = AudioTechInfo(
            format = AudioFormat.MP3,
            tagSummary = tagSummary,
            bitrateKbps = mp3Info.bitrateKbps,
            sampleRateHz = mp3Info.sampleRateHz,
            channels = mp3Info.channels,
            durationMs = mp3Info.durationMs,
            fileSizeBytes = data.size.toLong()
        )
        return ParseResult(tags, tech, audioStart, audioEnd)
    }

    /**
     * Writes ID3v2.3 or ID3v2.4 (plus optional ID3v1.1) while keeping raw MPEG audio intact.
     */
    fun write(
        originalData: ByteArray,
        tags: AudioTags,
        id3v2MajorVersion: Int = 3,
        writeId3v1: Boolean = true
    ): ByteArray {
        val parsed = read(originalData)
        val audioBytes = originalData.copyOfRange(
            parsed.audioStartOffset.coerceIn(0, originalData.size),
            parsed.audioEndOffset.coerceIn(parsed.audioStartOffset, originalData.size)
        )

        val out = ByteArrayOutputStream(audioBytes.size + 4096)

        // Build ID3v2 frames
        val framesOut = ByteArrayOutputStream()
        val useV24 = id3v2MajorVersion >= 4

        fun addText(id: String, value: String) {
            if (value.isNotEmpty()) {
                val payload = encodeTextFrame(value, useV24)
                writeFrame(framesOut, id, payload, useV24)
            }
        }

        addText("TIT2", tags.title)
        addText("TPE1", tags.artist)
        addText("TALB", tags.album)
        addText("TPE2", tags.albumArtist)
        if (tags.year.isNotEmpty()) {
            addText(if (useV24) "TDRC" else "TYER", tags.year)
        }
        addText("TRCK", tags.track)
        addText("TPOS", tags.discNumber)
        addText("TCON", tags.genre)
        addText("TCOM", tags.composer)
        addText("TPUB", tags.publisher)
        addText("TCOP", tags.copyright)
        addText("TENC", tags.encodedBy)
        addText("TBPM", tags.bpm)
        if (tags.compilation) addText("TCMP", "1")

        if (tags.comment.isNotEmpty()) {
            writeFrame(framesOut, "COMM", encodeCommentFrame(tags.comment, useV24), useV24)
        }
        if (tags.lyrics.isNotEmpty()) {
            writeFrame(framesOut, "USLT", encodeCommentFrame(tags.lyrics, useV24), useV24)
        }
        tags.customFields.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotEmpty()) {
                writeFrame(framesOut, "TXXX", encodeTxxxFrame(key, value, useV24), useV24)
            }
        }
        tags.coverArt?.let { art ->
            writeFrame(framesOut, "APIC", encodeApicFrame(art), useV24)
        }

        val frameBytes = framesOut.toByteArray()
        if (frameBytes.isNotEmpty()) {
            val paddingSize = 256
            val totalTagSize = frameBytes.size + paddingSize
            out.writeBytesSafe(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
            out.write(if (useV24) 4 else 3)
            out.write(0) // minor version
            out.write(0) // flags
            out.writeBytesSafe(BinaryUtils.encodeSynchsafeInt(totalTagSize))
            out.writeBytesSafe(frameBytes)
            out.writeBytesSafe(ByteArray(paddingSize))
        }

        out.writeBytesSafe(audioBytes)

        if (writeId3v1 && tags.hasAnyBasicTag) {
            out.writeBytesSafe(encodeId3v1(tags))
        }

        return out.toByteArray()
    }

    /**
     * Strips all ID3v1 and ID3v2 tags, leaving pure MPEG audio frames.
     */
    fun stripTags(originalData: ByteArray): ByteArray {
        val parsed = read(originalData)
        return originalData.copyOfRange(
            parsed.audioStartOffset.coerceIn(0, originalData.size),
            parsed.audioEndOffset.coerceIn(parsed.audioStartOffset, originalData.size)
        )
    }

    private fun writeFrame(out: ByteArrayOutputStream, frameId: String, payload: ByteArray, useV24: Boolean) {
        out.writeBytesSafe(frameId.toByteArray(StandardCharsets.US_ASCII).copyOf(4))
        val sizeBytes = if (useV24) {
            BinaryUtils.encodeSynchsafeInt(payload.size)
        } else {
            BinaryUtils.writeIntBE(payload.size)
        }
        out.writeBytesSafe(sizeBytes)
        out.write(0)
        out.write(0)
        out.writeBytesSafe(payload)
    }

    private fun encodeTextFrame(text: String, useUtf8: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        if (useUtf8) {
            out.write(3) // UTF-8
            out.writeBytesSafe(text.toByteArray(StandardCharsets.UTF_8))
        } else {
            out.write(1) // UTF-16 with BOM
            out.writeBytesSafe(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            out.writeBytesSafe(text.toByteArray(StandardCharsets.UTF_16LE))
        }
        return out.toByteArray()
    }

    private fun encodeCommentFrame(text: String, useUtf8: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        if (useUtf8) {
            out.write(3)
            out.writeBytesSafe("eng".toByteArray(StandardCharsets.US_ASCII))
            out.write(0) // empty short description
            out.writeBytesSafe(text.toByteArray(StandardCharsets.UTF_8))
        } else {
            out.write(1)
            out.writeBytesSafe("eng".toByteArray(StandardCharsets.US_ASCII))
            out.writeBytesSafe(byteArrayOf(0x00, 0x00)) // empty UTF-16 short desc
            out.writeBytesSafe(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            out.writeBytesSafe(text.toByteArray(StandardCharsets.UTF_16LE))
        }
        return out.toByteArray()
    }

    private fun encodeTxxxFrame(key: String, value: String, useUtf8: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        if (useUtf8) {
            out.write(3)
            out.writeBytesSafe(key.toByteArray(StandardCharsets.UTF_8))
            out.write(0)
            out.writeBytesSafe(value.toByteArray(StandardCharsets.UTF_8))
        } else {
            out.write(1)
            out.writeBytesSafe(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            out.writeBytesSafe(key.toByteArray(StandardCharsets.UTF_16LE))
            out.writeBytesSafe(byteArrayOf(0x00, 0x00))
            out.writeBytesSafe(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            out.writeBytesSafe(value.toByteArray(StandardCharsets.UTF_16LE))
        }
        return out.toByteArray()
    }

    private fun encodeApicFrame(art: CoverArt): ByteArray {
        val out = ByteArrayOutputStream(art.bytes.size + 64)
        out.write(0) // ISO-8859-1 mime & description
        out.writeBytesSafe(art.mimeType.toByteArray(StandardCharsets.ISO_8859_1))
        out.write(0)
        out.write(art.pictureType and 0xFF)
        out.write(0) // empty description
        out.writeBytesSafe(art.bytes)
        return out.toByteArray()
    }

    private fun encodeId3v1(tags: AudioTags): ByteArray {
        val buf = ByteArray(128)
        buf[0] = 'T'.code.toByte()
        buf[1] = 'A'.code.toByte()
        buf[2] = 'G'.code.toByte()
        writeFixedLatin1(buf, 3, 30, tags.title)
        writeFixedLatin1(buf, 33, 30, tags.artist)
        writeFixedLatin1(buf, 63, 30, tags.album)
        writeFixedLatin1(buf, 93, 4, tags.year.take(4))
        val trackNum = tags.track.substringBefore('/').trim().toIntOrNull()
        if (trackNum != null && trackNum in 1..255) {
            writeFixedLatin1(buf, 97, 28, tags.comment)
            buf[125] = 0
            buf[126] = trackNum.toByte()
        } else {
            writeFixedLatin1(buf, 97, 30, tags.comment)
        }
        val genreIndex = BinaryUtils.ID3V1_GENRES.indexOfFirst { it.equals(tags.genre, ignoreCase = true) }
        buf[127] = if (genreIndex >= 0) genreIndex.toByte() else 0xFF.toByte()
        return buf
    }

    private fun writeFixedLatin1(dest: ByteArray, offset: Int, maxLen: Int, value: String) {
        val bytes = value.toByteArray(StandardCharsets.ISO_8859_1)
        val count = minOf(bytes.size, maxLen)
        System.arraycopy(bytes, 0, dest, offset, count)
    }

    private fun decodeLatin1Trimmed(data: ByteArray, offset: Int, len: Int): String {
        val end = minOf(offset + len, data.size)
        var actualEnd = offset
        while (actualEnd < end && data[actualEnd] != 0.toByte()) actualEnd++
        return String(data, offset, actualEnd - offset, StandardCharsets.ISO_8859_1).trim()
    }

    private fun decodeTextFrame(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val encoding = payload[0].toInt() and 0xFF
        return decodeEncodedString(payload, 1, payload.size - 1, encoding)
            .replace("\u0000", " / ")
            .trim()
    }

    private fun decodeCommentOrLyricsFrame(payload: ByteArray): String {
        if (payload.size < 4) return ""
        val encoding = payload[0].toInt() and 0xFF
        val startAfterLang = 4
        val descEnd = findNullTerminator(payload, startAfterLang, encoding)
        val textStart = advancePastNull(descEnd, encoding, payload.size)
        return decodeEncodedString(payload, textStart, payload.size - textStart, encoding).trimEnd('\u0000')
    }

    private fun decodeTxxxFrame(payload: ByteArray): Pair<String, String>? {
        if (payload.size < 2) return null
        val encoding = payload[0].toInt() and 0xFF
        val keyEnd = findNullTerminator(payload, 1, encoding)
        val key = decodeEncodedString(payload, 1, keyEnd - 1, encoding).trim()
        val valStart = advancePastNull(keyEnd, encoding, payload.size)
        val value = decodeEncodedString(payload, valStart, payload.size - valStart, encoding).trimEnd('\u0000')
        return key to value
    }

    private fun decodeApicFrame(payload: ByteArray): CoverArt? {
        if (payload.size < 6) return null
        val encoding = payload[0].toInt() and 0xFF
        val mimeEnd = findNullTerminator(payload, 1, 0)
        val mimeRaw = String(payload, 1, mimeEnd - 1, StandardCharsets.ISO_8859_1).trim()
        val picTypeIdx = mimeEnd + 1
        if (picTypeIdx >= payload.size) return null
        val picType = payload[picTypeIdx].toInt() and 0xFF
        val descStart = picTypeIdx + 1
        val descEnd = findNullTerminator(payload, descStart, encoding)
        val desc = decodeEncodedString(payload, descStart, descEnd - descStart, encoding)
        val imgStart = advancePastNull(descEnd, encoding, payload.size)
        if (imgStart >= payload.size) return null
        val imgBytes = payload.copyOfRange(imgStart, payload.size)
        if (imgBytes.isEmpty()) return null
        val mime = if (mimeRaw.contains('/')) mimeRaw else BinaryUtils.detectImageMimeType(imgBytes)
        val (w, h) = BinaryUtils.decodeImageDimensions(imgBytes)
        return CoverArt(imgBytes, mime, picType, desc, w, h)
    }

    private fun decodePicV22(payload: ByteArray): CoverArt? {
        if (payload.size < 7) return null
        val encoding = payload[0].toInt() and 0xFF
        val format = String(payload, 1, 3, StandardCharsets.US_ASCII).uppercase()
        val picType = payload[4].toInt() and 0xFF
        val descEnd = findNullTerminator(payload, 5, encoding)
        val imgStart = advancePastNull(descEnd, encoding, payload.size)
        if (imgStart >= payload.size) return null
        val imgBytes = payload.copyOfRange(imgStart, payload.size)
        val mime = when (format) {
            "PNG" -> "image/png"
            "JPG", "JPE" -> "image/jpeg"
            else -> BinaryUtils.detectImageMimeType(imgBytes)
        }
        val (w, h) = BinaryUtils.decodeImageDimensions(imgBytes)
        return CoverArt(imgBytes, mime, picType, "", w, h)
    }

    private fun findNullTerminator(data: ByteArray, start: Int, encoding: Int): Int {
        var i = start
        if (encoding == 1 || encoding == 2) {
            while (i + 1 < data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i
                i += 2
            }
            return data.size
        } else {
            while (i < data.size) {
                if (data[i].toInt() == 0) return i
                i++
            }
            return data.size
        }
    }

    private fun advancePastNull(nullPos: Int, encoding: Int, limit: Int): Int {
        return if (encoding == 1 || encoding == 2) {
            minOf(nullPos + 2, limit)
        } else {
            minOf(nullPos + 1, limit)
        }
    }

    private fun decodeEncodedString(data: ByteArray, offset: Int, length: Int, encoding: Int): String {
        if (length <= 0 || offset >= data.size) return ""
        val safeLen = minOf(length, data.size - offset)
        return when (encoding) {
            0 -> String(data, offset, safeLen, StandardCharsets.ISO_8859_1)
            1 -> {
                if (safeLen >= 2 && data[offset] == 0xFF.toByte() && data[offset + 1] == 0xFE.toByte()) {
                    String(data, offset + 2, safeLen - 2, StandardCharsets.UTF_16LE)
                } else if (safeLen >= 2 && data[offset] == 0xFE.toByte() && data[offset + 1] == 0xFF.toByte()) {
                    String(data, offset + 2, safeLen - 2, StandardCharsets.UTF_16BE)
                } else {
                    String(data, offset, safeLen, StandardCharsets.UTF_16LE)
                }
            }
            2 -> String(data, offset, safeLen, StandardCharsets.UTF_16BE)
            else -> String(data, offset, safeLen, StandardCharsets.UTF_8)
        }
    }

    private fun normalizeGenre(raw: String): String {
        val trimmed = raw.trim()
        val match = Regex("^\\((\\d+)\\)(.*)$").find(trimmed)
        if (match != null) {
            val idx = match.groupValues[1].toIntOrNull()
            val rest = match.groupValues[2].trim()
            if (rest.isNotEmpty()) return rest
            if (idx != null && idx in BinaryUtils.ID3V1_GENRES.indices) {
                return BinaryUtils.ID3V1_GENRES[idx]
            }
        }
        if (trimmed.all { it.isDigit() } && trimmed.isNotEmpty()) {
            val idx = trimmed.toIntOrNull()
            if (idx != null && idx in BinaryUtils.ID3V1_GENRES.indices) {
                return BinaryUtils.ID3V1_GENRES[idx]
            }
        }
        return trimmed
    }

    private fun removeUnsynchronization(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size)
        var i = 0
        while (i < input.size) {
            out.write(input[i].toInt() and 0xFF)
            if (input[i] == 0xFF.toByte() && i + 1 < input.size && input[i + 1] == 0x00.toByte()) {
                i += 2
            } else {
                i++
            }
        }
        return out.toByteArray()
    }
}
