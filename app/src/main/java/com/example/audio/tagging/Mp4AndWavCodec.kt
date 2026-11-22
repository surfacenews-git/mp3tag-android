package com.example.audio.tagging

import com.example.audio.tagging.BinaryUtils.writeBytesSafe
import com.example.model.AudioFormat
import com.example.model.AudioTags
import com.example.model.AudioTechInfo
import com.example.model.CoverArt
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin MP4/M4A (iTunes ilst atoms) & WAV (RIFF LIST/INFO + id3 chunk) codec.
 */
object Mp4AndWavCodec {

    data class ParseResult(
        val tags: AudioTags,
        val techInfo: AudioTechInfo
    )

    // ==================== MP4 / M4A ====================

    private data class Mp4Box(
        val type: String,
        val start: Int,
        val end: Int,
        val headerSize: Int
    )

    fun readMp4(data: ByteArray): ParseResult {
        val topBoxes = scanBoxes(data, 0, data.size)
        val moov = topBoxes.firstOrNull { it.type == "moov" }
            ?: return ParseResult(AudioTags(), AudioTechInfo(AudioFormat.M4A, "MP4 (No moov)", fileSizeBytes = data.size.toLong()))

        var durationMs = 0L
        var sampleRate = 44100
        var channels = 2

        val moovChildren = scanBoxes(data, moov.start + moov.headerSize, moov.end)
        moovChildren.firstOrNull { it.type == "mvhd" }?.let { mvhd ->
            val p = mvhd.start + mvhd.headerSize
            if (p + 20 <= data.size) {
                val version = data[p].toInt() and 0xFF
                if (version == 0) {
                    val scale = BinaryUtils.readIntBE(data, p + 12)
                    val dur = BinaryUtils.readIntBE(data, p + 16).toLong() and 0xFFFFFFFFL
                    if (scale > 0) durationMs = (dur * 1000L) / scale
                } else if (p + 32 <= data.size) {
                    val scale = BinaryUtils.readIntBE(data, p + 20)
                    val dur = BinaryUtils.readLongBE(data, p + 24)
                    if (scale > 0) durationMs = (dur * 1000L) / scale
                }
            }
        }

        // Probe mdhd in first trak for sampleRate
        moovChildren.firstOrNull { it.type == "trak" }?.let { trak ->
            val mdia = scanBoxes(data, trak.start + trak.headerSize, trak.end).firstOrNull { it.type == "mdia" }
            val mdhd = mdia?.let { scanBoxes(data, it.start + it.headerSize, it.end).firstOrNull { b -> b.type == "mdhd" } }
            mdhd?.let { box ->
                val p = box.start + box.headerSize
                if (p + 16 <= data.size) {
                    val ver = data[p].toInt() and 0xFF
                    val scale = if (ver == 0) BinaryUtils.readIntBE(data, p + 12) else BinaryUtils.readIntBE(data, p + 20)
                    if (scale in 8000..192000) sampleRate = scale
                }
            }
        }

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
        var copyright = ""
        var lyrics = ""
        var encodedBy = ""
        var bpm = ""
        var compilation = false
        val customFields = linkedMapOf<String, String>()
        var coverArt: CoverArt? = null

        val udta = moovChildren.firstOrNull { it.type == "udta" }
        val meta = udta?.let { scanBoxes(data, it.start + it.headerSize, it.end).firstOrNull { b -> b.type == "meta" } }
        val ilst = meta?.let {
            // meta box is a FullBox (4 bytes version+flags before child boxes)
            scanBoxes(data, it.start + it.headerSize + 4, it.end).firstOrNull { b -> b.type == "ilst" }
        }

        if (ilst != null) {
            val items = scanBoxes(data, ilst.start + ilst.headerSize, ilst.end)
            for (item in items) {
                val children = scanBoxes(data, item.start + item.headerSize, item.end)
                val dataBox = children.firstOrNull { it.type == "data" } ?: continue
                val dStart = dataBox.start + dataBox.headerSize
                if (dStart + 8 > dataBox.end) continue
                val typeIndicator = BinaryUtils.readIntBE(data, dStart)
                val valStart = dStart + 8
                val valBytes = data.copyOfRange(valStart, dataBox.end)

                when (item.type) {
                    "\u00A9nam" -> title = String(valBytes, StandardCharsets.UTF_8)
                    "\u00A9ART" -> artist = String(valBytes, StandardCharsets.UTF_8)
                    "\u00A9alb" -> album = String(valBytes, StandardCharsets.UTF_8)
                    "aART" -> albumArtist = String(valBytes, StandardCharsets.UTF_8)
                    "\u00A9day" -> year = String(valBytes, StandardCharsets.UTF_8).take(10)
                    "\u00A9gen" -> genre = String(valBytes, StandardCharsets.UTF_8)
                    "gnre" -> if (valBytes.size >= 2 && genre.isEmpty()) {
                        val idx = ((valBytes[0].toInt() and 0xFF) shl 8) or (valBytes[1].toInt() and 0xFF) - 1
                        if (idx in BinaryUtils.ID3V1_GENRES.indices) genre = BinaryUtils.ID3V1_GENRES[idx]
                    }
                    "\u00A9cmt" -> comment = String(valBytes, StandardCharsets.UTF_8)
                    "\u00A9wrt" -> composer = String(valBytes, StandardCharsets.UTF_8)
                    "cprt" -> copyright = String(valBytes, StandardCharsets.UTF_8)
                    "\u00A9lyr" -> lyrics = String(valBytes, StandardCharsets.UTF_8)
                    "\u00A9too" -> encodedBy = String(valBytes, StandardCharsets.UTF_8)
                    "tmpo" -> if (valBytes.size >= 2) {
                        val tempo = ((valBytes[0].toInt() and 0xFF) shl 8) or (valBytes[1].toInt() and 0xFF)
                        if (tempo > 0) bpm = tempo.toString()
                    }
                    "cpil" -> compilation = valBytes.isNotEmpty() && valBytes[0].toInt() != 0
                    "trkn" -> if (valBytes.size >= 6) {
                        val num = ((valBytes[2].toInt() and 0xFF) shl 8) or (valBytes[3].toInt() and 0xFF)
                        val tot = ((valBytes[4].toInt() and 0xFF) shl 8) or (valBytes[5].toInt() and 0xFF)
                        if (num > 0) track = if (tot > 0) "$num/$tot" else num.toString()
                    }
                    "disk" -> if (valBytes.size >= 6) {
                        val num = ((valBytes[2].toInt() and 0xFF) shl 8) or (valBytes[3].toInt() and 0xFF)
                        val tot = ((valBytes[4].toInt() and 0xFF) shl 8) or (valBytes[5].toInt() and 0xFF)
                        if (num > 0) disc = if (tot > 0) "$num/$tot" else num.toString()
                    }
                    "covr" -> if (valBytes.isNotEmpty() && coverArt == null) {
                        val mime = when (typeIndicator) {
                            14 -> "image/png"
                            13 -> "image/jpeg"
                            else -> BinaryUtils.detectImageMimeType(valBytes)
                        }
                        val (w, h) = BinaryUtils.decodeImageDimensions(valBytes)
                        coverArt = CoverArt(valBytes, mime, 3, "Front Cover", w, h)
                    }
                    "----" -> {
                        val nameBox = children.firstOrNull { it.type == "name" }
                        if (nameBox != null && nameBox.start + nameBox.headerSize + 4 < nameBox.end) {
                            val key = String(data, nameBox.start + nameBox.headerSize + 4, nameBox.end - (nameBox.start + nameBox.headerSize + 4), StandardCharsets.UTF_8)
                            if (key.isNotBlank()) customFields[key] = String(valBytes, StandardCharsets.UTF_8)
                        }
                    }
                }
            }
        }

        val mdat = topBoxes.firstOrNull { it.type == "mdat" }
        val audioBytes = mdat?.let { it.end - it.start } ?: data.size
        val bitrate = if (durationMs > 0) ((audioBytes * 8L) / durationMs).toInt() else 0

        return ParseResult(
            tags = AudioTags(
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
                copyright = copyright,
                lyrics = lyrics,
                bpm = bpm,
                encodedBy = encodedBy,
                compilation = compilation,
                customFields = customFields,
                coverArt = coverArt
            ),
            techInfo = AudioTechInfo(
                format = AudioFormat.M4A,
                tagSummary = "MP4 (iTunes)",
                bitrateKbps = bitrate,
                sampleRateHz = sampleRate,
                channels = channels,
                durationMs = durationMs,
                fileSizeBytes = data.size.toLong()
            )
        )
    }

    fun writeMp4(originalData: ByteArray, tags: AudioTags): ByteArray {
        val topBoxes = scanBoxes(originalData, 0, originalData.size)
        val moov = topBoxes.firstOrNull { it.type == "moov" } ?: return originalData
        val mdat = topBoxes.firstOrNull { it.type == "mdat" }

        val newIlst = buildMp4Ilst(tags)
        val newMeta = buildMp4Box("meta", byteArrayOf(0, 0, 0, 0) + buildMp4Hdlr() + newIlst)

        val moovChildren = scanBoxes(originalData, moov.start + moov.headerSize, moov.end)
        val newMoovInner = ByteArrayOutputStream()
        var wroteUdta = false
        for (child in moovChildren) {
            if (child.type == "udta") {
                val udtaChildren = scanBoxes(originalData, child.start + child.headerSize, child.end)
                val udtaInner = ByteArrayOutputStream()
                var wroteMeta = false
                for (uChild in udtaChildren) {
                    if (uChild.type == "meta") {
                        udtaInner.writeBytesSafe(newMeta)
                        wroteMeta = true
                    } else {
                        udtaInner.write(originalData, uChild.start, uChild.end - uChild.start)
                    }
                }
                if (!wroteMeta) udtaInner.writeBytesSafe(newMeta)
                newMoovInner.writeBytesSafe(buildMp4Box("udta", udtaInner.toByteArray()))
                wroteUdta = true
            } else {
                newMoovInner.write(originalData, child.start, child.end - child.start)
            }
        }
        if (!wroteUdta) {
            newMoovInner.writeBytesSafe(buildMp4Box("udta", newMeta))
        }

        var newMoovBytes = buildMp4Box("moov", newMoovInner.toByteArray())
        val delta = newMoovBytes.size - (moov.end - moov.start)

        // If moov appears before mdat, adjust stco / co64 chunk offsets by delta so audio remains playable
        if (mdat != null && moov.start < mdat.start && delta != 0) {
            newMoovBytes = shiftMp4ChunkOffsets(newMoovBytes, delta)
        }

        val out = ByteArrayOutputStream(originalData.size + delta + 64)
        for (box in topBoxes) {
            if (box.type == "moov") {
                out.writeBytesSafe(newMoovBytes)
            } else {
                out.write(originalData, box.start, box.end - box.start)
            }
        }
        return out.toByteArray()
    }

    private fun shiftMp4ChunkOffsets(moovBytes: ByteArray, delta: Int): ByteArray {
        val copy = moovBytes.copyOf()
        fun walk(start: Int, end: Int) {
            val boxes = scanBoxes(copy, start, end)
            for (b in boxes) {
                when (b.type) {
                    "stco" -> {
                        val p = b.start + b.headerSize
                        if (p + 8 <= b.end) {
                            val count = BinaryUtils.readIntBE(copy, p + 4)
                            for (i in 0 until count) {
                                val offPos = p + 8 + i * 4
                                if (offPos + 4 <= b.end) {
                                    val oldOff = BinaryUtils.readIntBE(copy, offPos)
                                    val newOff = oldOff + delta
                                    System.arraycopy(BinaryUtils.writeIntBE(newOff), 0, copy, offPos, 4)
                                }
                            }
                        }
                    }
                    "co64" -> {
                        val p = b.start + b.headerSize
                        if (p + 8 <= b.end) {
                            val count = BinaryUtils.readIntBE(copy, p + 4)
                            for (i in 0 until count) {
                                val offPos = p + 8 + i * 8
                                if (offPos + 8 <= b.end) {
                                    val oldOff = BinaryUtils.readLongBE(copy, offPos)
                                    val newOff = oldOff + delta
                                    System.arraycopy(BinaryUtils.writeLongBE(newOff), 0, copy, offPos, 8)
                                }
                            }
                        }
                    }
                    "trak", "mdia", "minf", "stbl" -> walk(b.start + b.headerSize, b.end)
                }
            }
        }
        walk(8, copy.size)
        return copy
    }

    private fun buildMp4Ilst(tags: AudioTags): ByteArray {
        val out = ByteArrayOutputStream()
        fun addUtf8(atom: String, value: String) {
            if (value.isNotEmpty()) {
                val payload = BinaryUtils.writeIntBE(1) + ByteArray(4) + value.toByteArray(StandardCharsets.UTF_8)
                out.writeBytesSafe(buildMp4Box(atom, buildMp4Box("data", payload)))
            }
        }
        fun addPair(atom: String, raw: String) {
            if (raw.isBlank()) return
            val num = raw.substringBefore('/').trim().toIntOrNull() ?: 0
            val tot = if (raw.contains('/')) raw.substringAfter('/').trim().toIntOrNull() ?: 0 else 0
            if (num > 0 || tot > 0) {
                val data = byteArrayOf(0, 0) +
                    BinaryUtils.writeShortBE(num) +
                    BinaryUtils.writeShortBE(tot) +
                    byteArrayOf(0, 0)
                val payload = BinaryUtils.writeIntBE(0) + ByteArray(4) + data
                out.writeBytesSafe(buildMp4Box(atom, buildMp4Box("data", payload)))
            }
        }

        addUtf8("\u00A9nam", tags.title)
        addUtf8("\u00A9ART", tags.artist)
        addUtf8("\u00A9alb", tags.album)
        addUtf8("aART", tags.albumArtist)
        addUtf8("\u00A9day", tags.year)
        addPair("trkn", tags.track)
        addPair("disk", tags.discNumber)
        addUtf8("\u00A9gen", tags.genre)
        addUtf8("\u00A9cmt", tags.comment)
        addUtf8("\u00A9wrt", tags.composer)
        addUtf8("cprt", tags.copyright)
        addUtf8("\u00A9lyr", tags.lyrics)
        addUtf8("\u00A9too", tags.encodedBy)
        tags.bpm.toIntOrNull()?.let { bpm ->
            if (bpm > 0) {
                val payload = BinaryUtils.writeIntBE(21) + ByteArray(4) + BinaryUtils.writeShortBE(bpm)
                out.writeBytesSafe(buildMp4Box("tmpo", buildMp4Box("data", payload)))
            }
        }
        if (tags.compilation) {
            val payload = BinaryUtils.writeIntBE(21) + ByteArray(4) + byteArrayOf(1)
            out.writeBytesSafe(buildMp4Box("cpil", buildMp4Box("data", payload)))
        }
        tags.customFields.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotEmpty()) {
                val mean = buildMp4Box("mean", ByteArray(4) + "com.apple.iTunes".toByteArray(StandardCharsets.UTF_8))
                val name = buildMp4Box("name", ByteArray(4) + k.toByteArray(StandardCharsets.UTF_8))
                val data = buildMp4Box("data", BinaryUtils.writeIntBE(1) + ByteArray(4) + v.toByteArray(StandardCharsets.UTF_8))
                out.writeBytesSafe(buildMp4Box("----", mean + name + data))
            }
        }
        tags.coverArt?.let { art ->
            val typeInd = if (art.mimeType.contains("png", true)) 14 else 13
            val payload = BinaryUtils.writeIntBE(typeInd) + ByteArray(4) + art.bytes
            out.writeBytesSafe(buildMp4Box("covr", buildMp4Box("data", payload)))
        }
        return buildMp4Box("ilst", out.toByteArray())
    }

    private fun buildMp4Hdlr(): ByteArray {
        val body = ByteArrayOutputStream()
        body.writeBytesSafe(ByteArray(4)) // version + flags
        body.writeBytesSafe(ByteArray(4)) // pre_defined
        body.writeBytesSafe("mdir".toByteArray(StandardCharsets.US_ASCII))
        body.writeBytesSafe("appl".toByteArray(StandardCharsets.US_ASCII))
        body.writeBytesSafe(ByteArray(12))
        body.write(0)
        return buildMp4Box("hdlr", body.toByteArray())
    }

    private fun buildMp4Box(type: String, content: ByteArray): ByteArray {
        val total = 8 + content.size
        val out = ByteArrayOutputStream(total)
        out.writeBytesSafe(BinaryUtils.writeIntBE(total))
        out.writeBytesSafe(type.toByteArray(StandardCharsets.ISO_8859_1).copyOf(4))
        out.writeBytesSafe(content)
        return out.toByteArray()
    }

    private fun scanBoxes(data: ByteArray, start: Int, end: Int): List<Mp4Box> {
        val result = mutableListOf<Mp4Box>()
        var pos = start
        while (pos + 8 <= end) {
            val size32 = BinaryUtils.readIntBE(data, pos).toLong() and 0xFFFFFFFFL
            val type = String(data, pos + 4, 4, StandardCharsets.ISO_8859_1)
            val headerSize: Int
            val totalSize: Long
            if (size32 == 1L && pos + 16 <= end) {
                totalSize = BinaryUtils.readLongBE(data, pos + 8)
                headerSize = 16
            } else if (size32 == 0L) {
                totalSize = (end - pos).toLong()
                headerSize = 8
            } else {
                totalSize = size32
                headerSize = 8
            }
            if (totalSize < headerSize || pos + totalSize > end) break
            result.add(Mp4Box(type, pos, pos + totalSize.toInt(), headerSize))
            pos += totalSize.toInt()
        }
        return result
    }

    // ==================== WAV (RIFF / WAVE) ====================

    fun readWav(data: ByteArray): ParseResult {
        if (data.size < 12 ||
            String(data, 0, 4, StandardCharsets.US_ASCII) != "RIFF" ||
            String(data, 8, 4, StandardCharsets.US_ASCII) != "WAVE"
        ) {
            return ParseResult(AudioTags(), AudioTechInfo(AudioFormat.WAV, "Invalid WAV", fileSizeBytes = data.size.toLong()))
        }

        var channels = 2
        var sampleRate = 44100
        var byteRate = 176400
        var dataSize = 0
        var infoTags = AudioTags()
        var id3Tags: AudioTags? = null
        val summary = mutableListOf<String>()

        var pos = 12
        while (pos + 8 <= data.size) {
            val chunkId = String(data, pos, 4, StandardCharsets.US_ASCII)
            val chunkSize = BinaryUtils.readIntLE(data, pos + 4)
            pos += 8
            if (chunkSize < 0 || pos + chunkSize > data.size) break
            val paddedEnd = pos + chunkSize + (chunkSize and 1)

            when (chunkId) {
                "fmt " -> if (chunkSize >= 16) {
                    channels = (data[pos + 2].toInt() and 0xFF) or ((data[pos + 3].toInt() and 0xFF) shl 8)
                    sampleRate = BinaryUtils.readIntLE(data, pos + 4)
                    byteRate = BinaryUtils.readIntLE(data, pos + 8)
                }
                "data" -> dataSize = chunkSize
                "id3 ", "ID3 " -> {
                    summary.add("ID3v2")
                    id3Tags = Id3TagCodec.read(data.copyOfRange(pos, pos + chunkSize)).tags
                }
                "LIST" -> if (chunkSize >= 4 && String(data, pos, 4, StandardCharsets.US_ASCII) == "INFO") {
                    summary.add("RIFF INFO")
                    infoTags = parseRiffInfo(data, pos + 4, pos + chunkSize)
                }
            }
            pos = minOf(paddedEnd, data.size)
        }

        val durationMs = if (byteRate > 0) (dataSize.toLong() * 1000L) / byteRate else 0L
        val bitrateKbps = (byteRate * 8) / 1000
        val merged = id3Tags ?: infoTags

        return ParseResult(
            tags = merged,
            techInfo = AudioTechInfo(
                format = AudioFormat.WAV,
                tagSummary = if (summary.isEmpty()) "WAV (Untagged)" else "WAV (${summary.joinToString(" + ")})",
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRate,
                channels = channels,
                durationMs = durationMs,
                fileSizeBytes = data.size.toLong()
            )
        )
    }

    fun writeWav(originalData: ByteArray, tags: AudioTags, id3v2MajorVersion: Int = 3): ByteArray {
        if (originalData.size < 12 ||
            String(originalData, 0, 4, StandardCharsets.US_ASCII) != "RIFF" ||
            String(originalData, 8, 4, StandardCharsets.US_ASCII) != "WAVE"
        ) {
            return originalData
        }

        val chunksOut = ByteArrayOutputStream()
        chunksOut.writeBytesSafe("WAVE".toByteArray(StandardCharsets.US_ASCII))

        var pos = 12
        while (pos + 8 <= originalData.size) {
            val chunkId = String(originalData, pos, 4, StandardCharsets.US_ASCII)
            val chunkSize = BinaryUtils.readIntLE(originalData, pos + 4)
            pos += 8
            if (chunkSize < 0 || pos + chunkSize > originalData.size) break
            val isInfoList = chunkId == "LIST" && chunkSize >= 4 &&
                String(originalData, pos, 4, StandardCharsets.US_ASCII) == "INFO"

            if (chunkId != "id3 " && chunkId != "ID3 " && !isInfoList) {
                chunksOut.writeBytesSafe(chunkId.toByteArray(StandardCharsets.US_ASCII))
                chunksOut.writeBytesSafe(BinaryUtils.writeIntLE(chunkSize))
                chunksOut.write(originalData, pos, chunkSize)
                if ((chunkSize and 1) != 0) chunksOut.write(0)
            }
            pos = minOf(pos + chunkSize + (chunkSize and 1), originalData.size)
        }

        // Write RIFF LIST/INFO chunk
        val infoBytes = buildRiffInfoChunk(tags)
        if (infoBytes.isNotEmpty()) {
            chunksOut.writeBytesSafe("LIST".toByteArray(StandardCharsets.US_ASCII))
            chunksOut.writeBytesSafe(BinaryUtils.writeIntLE(infoBytes.size))
            chunksOut.writeBytesSafe(infoBytes)
            if ((infoBytes.size and 1) != 0) chunksOut.write(0)
        }

        // Write embedded id3 chunk (using Id3TagCodec with dummy empty audio)
        val id3ChunkPayload = Id3TagCodec.write(ByteArray(0), tags, id3v2MajorVersion, writeId3v1 = false)
        if (id3ChunkPayload.isNotEmpty()) {
            chunksOut.writeBytesSafe("id3 ".toByteArray(StandardCharsets.US_ASCII))
            chunksOut.writeBytesSafe(BinaryUtils.writeIntLE(id3ChunkPayload.size))
            chunksOut.writeBytesSafe(id3ChunkPayload)
            if ((id3ChunkPayload.size and 1) != 0) chunksOut.write(0)
        }

        val body = chunksOut.toByteArray()
        val finalOut = ByteArrayOutputStream(8 + body.size)
        finalOut.writeBytesSafe("RIFF".toByteArray(StandardCharsets.US_ASCII))
        finalOut.writeBytesSafe(BinaryUtils.writeIntLE(body.size))
        finalOut.writeBytesSafe(body)
        return finalOut.toByteArray()
    }

    private fun parseRiffInfo(data: ByteArray, start: Int, end: Int): AudioTags {
        var title = ""
        var artist = ""
        var album = ""
        var year = ""
        var genre = ""
        var comment = ""
        var track = ""
        var copyright = ""

        var pos = start
        while (pos + 8 <= end) {
            val id = String(data, pos, 4, StandardCharsets.US_ASCII)
            val len = BinaryUtils.readIntLE(data, pos + 4)
            pos += 8
            if (len < 0 || pos + len > end) break
            val value = String(data, pos, len, StandardCharsets.UTF_8).trimEnd('\u0000').trim()
            when (id) {
                "INAM" -> title = value
                "IART" -> artist = value
                "IPRD" -> album = value
                "ICRD" -> year = value.take(10)
                "IGNR" -> genre = value
                "ICMT" -> comment = value
                "ITRK" -> track = value
                "ICOP" -> copyright = value
            }
            pos += len + (len and 1)
        }
        return AudioTags(
            title = title, artist = artist, album = album, year = year,
            genre = genre, comment = comment, track = track, copyright = copyright
        )
    }

    private fun buildRiffInfoChunk(tags: AudioTags): ByteArray {
        val out = ByteArrayOutputStream()
        fun add(id: String, value: String) {
            if (value.isNotEmpty()) {
                val strBytes = value.toByteArray(StandardCharsets.UTF_8) + byteArrayOf(0)
                out.writeBytesSafe(id.toByteArray(StandardCharsets.US_ASCII))
                out.writeBytesSafe(BinaryUtils.writeIntLE(strBytes.size))
                out.writeBytesSafe(strBytes)
                if ((strBytes.size and 1) != 0) out.write(0)
            }
        }
        add("INAM", tags.title)
        add("IART", tags.artist)
        add("IPRD", tags.album)
        add("ICRD", tags.year)
        add("IGNR", tags.genre)
        add("ICMT", tags.comment)
        add("ITRK", tags.track)
        add("ICOP", tags.copyright)
        val inner = out.toByteArray()
        if (inner.isEmpty()) return ByteArray(0)
        return "INFO".toByteArray(StandardCharsets.US_ASCII) + inner
    }
}
