package com.example.audio.tagging

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BinaryUtils {

    fun decodeSynchsafeInt(b0: Int, b1: Int, b2: Int, b3: Int): Int {
        return ((b0 and 0x7F) shl 21) or
            ((b1 and 0x7F) shl 14) or
            ((b2 and 0x7F) shl 7) or
            (b3 and 0x7F)
    }

    fun encodeSynchsafeInt(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte()
        )
    }

    fun readIntBE(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    fun readUInt24BE(data: ByteArray, offset: Int): Int {
        if (offset + 3 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() and 0xFF)
    }

    fun readIntLE(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun writeIntBE(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    fun writeIntLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    fun writeShortBE(value: Int): ByteArray = byteArrayOf(
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    fun writeShortLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    fun readLongBE(data: ByteArray, offset: Int): Long {
        if (offset + 8 > data.size) return 0L
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.BIG_ENDIAN).long
    }

    fun writeLongBE(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array()

    fun detectImageMimeType(bytes: ByteArray): String {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()
        ) {
            return "image/webp"
        }
        return "image/jpeg"
    }

    data class Mp3FrameInfo(
        val bitrateKbps: Int,
        val sampleRateHz: Int,
        val channels: Int,
        val durationMs: Long
    )

    private val bitrateV1L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val bitrateV2L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
    private val sampleRatesV1 = intArrayOf(44100, 48000, 32000, 0)
    private val sampleRatesV2 = intArrayOf(22050, 24000, 16000, 0)
    private val sampleRatesV25 = intArrayOf(11025, 12000, 8000, 0)

    fun parseMp3AudioInfo(data: ByteArray, audioStartOffset: Int, audioEndOffset: Int): Mp3FrameInfo {
        val audioBytes = (audioEndOffset - audioStartOffset).coerceAtLeast(0)
        var pos = audioStartOffset
        val scanLimit = minOf(audioEndOffset - 4, audioStartOffset + 16384)
        while (pos <= scanLimit) {
            val b0 = data[pos].toInt() and 0xFF
            val b1 = data[pos + 1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val versionBits = (b1 ushr 3) and 0x03
                val layerBits = (b1 ushr 1) and 0x03
                val b2 = data[pos + 2].toInt() and 0xFF
                val bitrateIndex = (b2 ushr 4) and 0x0F
                val sampleRateIndex = (b2 ushr 2) and 0x03
                val b3 = data[pos + 3].toInt() and 0xFF
                val channelMode = (b3 ushr 6) and 0x03

                if (versionBits != 1 && layerBits == 1 && bitrateIndex in 1..14 && sampleRateIndex in 0..2) {
                    val sampleRate = when (versionBits) {
                        3 -> sampleRatesV1[sampleRateIndex]
                        2 -> sampleRatesV2[sampleRateIndex]
                        else -> sampleRatesV25[sampleRateIndex]
                    }
                    val bitrate = if (versionBits == 3) bitrateV1L3[bitrateIndex] else bitrateV2L3[bitrateIndex]
                    val channels = if (channelMode == 3) 1 else 2
                    val samplesPerFrame = if (versionBits == 3) 1152 else 576

                    // Check Xing / Info VBR header
                    val sideInfoSize = when {
                        versionBits == 3 && channels == 1 -> 17
                        versionBits == 3 -> 32
                        channels == 1 -> 9
                        else -> 17
                    }
                    val xingOffset = pos + 4 + sideInfoSize
                    if (xingOffset + 12 <= audioEndOffset) {
                        val tag = String(data, xingOffset, 4, Charsets.US_ASCII)
                        if (tag == "Xing" || tag == "Info") {
                            val flags = readIntBE(data, xingOffset + 4)
                            if ((flags and 0x01) != 0 && xingOffset + 12 <= audioEndOffset) {
                                val frames = readIntBE(data, xingOffset + 8).coerceAtLeast(1)
                                val durationMs = (frames.toLong() * samplesPerFrame * 1000L) / sampleRate.coerceAtLeast(1)
                                val avgBitrate = if (durationMs > 0) ((audioBytes * 8L) / durationMs).toInt() else bitrate
                                return Mp3FrameInfo(avgBitrate, sampleRate, channels, durationMs)
                            }
                        }
                    }

                    val durationMs = if (bitrate > 0) (audioBytes.toLong() * 8L) / bitrate else 0L
                    return Mp3FrameInfo(bitrate, sampleRate, channels, durationMs)
                }
            }
            pos++
        }
        return Mp3FrameInfo(0, 44100, 2, 0L)
    }

    // Ogg CRC32 lookup table (polynomial 0x04C11DB7, MSB-first, no reflection)
    private val oggCrcTable = IntArray(256).apply {
        for (i in 0 until 256) {
            var r = i shl 24
            for (j in 0 until 8) {
                r = if ((r and 0x80000000.toInt()) != 0) (r shl 1) xor 0x04C11DB7 else r shl 1
            }
            this[i] = r
        }
    }

    fun oggCrc32(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0
        for (i in offset until offset + length) {
            val idx = ((crc ushr 24) xor (data[i].toInt() and 0xFF)) and 0xFF
            crc = (crc shl 8) xor oggCrcTable[idx]
        }
        return crc
    }

    fun decodeImageDimensions(bytes: ByteArray): Pair<Int, Int> {
        // Lightweight header probe for PNG/JPEG without android.graphics dependency for unit tests
        if (bytes.size >= 24 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
            val w = readIntBE(bytes, 16)
            val h = readIntBE(bytes, 20)
            return w to h
        }
        if (bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var idx = 2
            while (idx + 9 < bytes.size) {
                if (bytes[idx] != 0xFF.toByte()) break
                val marker = bytes[idx + 1].toInt() and 0xFF
                if (marker == 0xD9 || marker == 0xDA) break
                val segLen = ((bytes[idx + 2].toInt() and 0xFF) shl 8) or (bytes[idx + 3].toInt() and 0xFF)
                if (segLen < 2) break
                if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3)) {
                    val h = ((bytes[idx + 5].toInt() and 0xFF) shl 8) or (bytes[idx + 6].toInt() and 0xFF)
                    val w = ((bytes[idx + 7].toInt() and 0xFF) shl 8) or (bytes[idx + 8].toInt() and 0xFF)
                    return w to h
                }
                idx += 2 + segLen
            }
        }
        return 0 to 0
    }

    val ID3V1_GENRES = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop",
        "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock",
        "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack",
        "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance",
        "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
        "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop",
        "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic",
        "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
        "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret",
        "New Wave", "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal",
        "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
        "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebob", "Latin",
        "Revival", "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock",
        "Psychedelic Rock", "Symphonic Rock", "Slow Rock", "Big Band", "Chorus",
        "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson", "Opera", "Chamber Music",
        "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam",
        "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul",
        "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A capella", "Euro-House", "Dance Hall",
        "Goa", "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie", "BritPop",
        "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap", "Heavy Metal", "Black Metal",
        "Crossover", "Contemporary Christian", "Christian Rock", "Merengue", "Salsa",
        "Thrash Metal", "Anime", "JPop", "Synthpop"
    )

    fun ByteArrayOutputStream.writeBytesSafe(bytes: ByteArray) {
        write(bytes, 0, bytes.size)
    }
}
