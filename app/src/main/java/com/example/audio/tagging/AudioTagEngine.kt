package com.example.audio.tagging

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.model.AudioFormat
import com.example.model.AudioTags
import com.example.model.AudioTechInfo
import com.example.model.AudioTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.PI
import kotlin.math.sin

object AudioTagEngine {

    data class ReadResult(
        val tags: AudioTags,
        val techInfo: AudioTechInfo
    )

    fun readBytes(data: ByteArray, format: AudioFormat): ReadResult {
        return when (format) {
            AudioFormat.MP3 -> {
                val res = Id3TagCodec.read(data)
                ReadResult(res.tags, res.techInfo)
            }
            AudioFormat.FLAC -> {
                val res = FlacAndOggCodec.readFlac(data)
                ReadResult(res.tags, res.techInfo)
            }
            AudioFormat.OGG -> {
                val res = FlacAndOggCodec.readOgg(data)
                ReadResult(res.tags, res.techInfo)
            }
            AudioFormat.M4A -> {
                val res = Mp4AndWavCodec.readMp4(data)
                ReadResult(res.tags, res.techInfo)
            }
            AudioFormat.WAV -> {
                val res = Mp4AndWavCodec.readWav(data)
                ReadResult(res.tags, res.techInfo)
            }
            AudioFormat.UNKNOWN -> ReadResult(
                AudioTags(),
                AudioTechInfo(AudioFormat.UNKNOWN, "Unsupported", fileSizeBytes = data.size.toLong())
            )
        }
    }

    fun writeBytes(
        originalData: ByteArray,
        format: AudioFormat,
        tags: AudioTags,
        id3v2MajorVersion: Int = 3,
        writeId3v1: Boolean = true
    ): ByteArray {
        return when (format) {
            AudioFormat.MP3 -> Id3TagCodec.write(originalData, tags, id3v2MajorVersion, writeId3v1)
            AudioFormat.FLAC -> FlacAndOggCodec.writeFlac(originalData, tags)
            AudioFormat.OGG -> FlacAndOggCodec.writeOgg(originalData, tags)
            AudioFormat.M4A -> Mp4AndWavCodec.writeMp4(originalData, tags)
            AudioFormat.WAV -> Mp4AndWavCodec.writeWav(originalData, tags, id3v2MajorVersion)
            AudioFormat.UNKNOWN -> originalData
        }
    }

    fun stripTags(originalData: ByteArray, format: AudioFormat): ByteArray {
        return when (format) {
            AudioFormat.MP3 -> Id3TagCodec.stripTags(originalData)
            else -> writeBytes(originalData, format, AudioTags())
        }
    }

    suspend fun readTrackFromFile(file: File, directoryLabel: String = file.parentFile?.name ?: "Workspace"): AudioTrackItem =
        withContext(Dispatchers.IO) {
            val format = AudioFormat.fromFilename(file.name)
            val bytes = file.readBytes()
            val parsed = readBytes(bytes, format)
            val tech = parsed.techInfo.copy(
                fileSizeBytes = file.length(),
                lastModifiedMs = file.lastModified()
            )
            AudioTrackItem(
                id = file.absolutePath,
                uri = Uri.fromFile(file),
                filePath = file.absolutePath,
                fileName = file.name,
                directoryName = directoryLabel,
                tags = parsed.tags,
                techInfo = tech,
                isModified = false,
                originalTags = parsed.tags
            )
        }

    suspend fun readTrackFromUri(
        context: Context,
        uri: Uri,
        fileName: String,
        directoryLabel: String,
        fileSize: Long = 0L,
        lastModified: Long = 0L
    ): AudioTrackItem = withContext(Dispatchers.IO) {
        val format = AudioFormat.fromFilename(fileName)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val parsed = readBytes(bytes, format)
        var tech = parsed.techInfo.copy(
            fileSizeBytes = if (fileSize > 0) fileSize else bytes.size.toLong(),
            lastModifiedMs = lastModified
        )
        if (tech.durationMs == 0L) {
            val mmrDuration = probeDurationWithMmr(context, uri)
            if (mmrDuration > 0L) {
                val estBitrate = if (tech.bitrateKbps > 0) tech.bitrateKbps else ((tech.fileSizeBytes * 8L) / mmrDuration).toInt()
                tech = tech.copy(durationMs = mmrDuration, bitrateKbps = estBitrate)
            }
        }
        AudioTrackItem(
            id = uri.toString(),
            uri = uri,
            filePath = null,
            fileName = fileName,
            directoryName = directoryLabel,
            tags = parsed.tags,
            techInfo = tech,
            isModified = false,
            originalTags = parsed.tags
        )
    }

    suspend fun writeTrack(
        context: Context,
        track: AudioTrackItem,
        id3v2MajorVersion: Int = 3,
        writeId3v1: Boolean = true
    ): Result<AudioTrackItem> = withContext(Dispatchers.IO) {
        runCatching {
            val format = track.techInfo.format
            if (track.filePath != null) {
                val file = File(track.filePath)
                val original = file.readBytes()
                val updatedBytes = writeBytes(original, format, track.tags, id3v2MajorVersion, writeId3v1)
                val tmp = File(file.parentFile, "${file.name}.mp3tag.tmp")
                tmp.writeBytes(updatedBytes)
                if (!tmp.renameTo(file)) {
                    file.writeBytes(updatedBytes)
                    tmp.delete()
                }
                readTrackFromFile(file, track.directoryName)
            } else if (track.uri != null) {
                val original = context.contentResolver.openInputStream(track.uri)?.use { it.readBytes() }
                    ?: error("Unable to read audio file stream")
                val updatedBytes = writeBytes(original, format, track.tags, id3v2MajorVersion, writeId3v1)
                val mode = "wt"
                val outStream = context.contentResolver.openOutputStream(track.uri, mode)
                    ?: context.contentResolver.openOutputStream(track.uri)
                    ?: error("File is read-only or cannot be opened for writing")
                outStream.use { it.write(updatedBytes) }
                readTrackFromUri(
                    context = context,
                    uri = track.uri,
                    fileName = track.fileName,
                    directoryLabel = track.directoryName,
                    fileSize = updatedBytes.size.toLong(),
                    lastModified = System.currentTimeMillis()
                )
            } else {
                error("No file path or URI associated with track")
            }
        }
    }

    private fun probeDurationWithMmr(context: Context, uri: Uri): Long {
        return runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uri)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                mmr.release()
            }
        }.getOrDefault(0L)
    }
}

/**
 * Generates real, valid, playable binary WAV and MP3 audio files on local storage
 * when a user on a fresh emulator wants immediate sample tracks to test tagging/renaming/playback.
 */
object SampleAudioGenerator {

    fun createSampleWorkspaceFiles(workspaceDir: File): List<File> {
        if (!workspaceDir.exists()) workspaceDir.mkdirs()

        val specs = listOf(
            SampleSpec(
                fileName = "01 - midnight_echoes.mp3",
                isMp3 = true,
                freqHz = 261.63,
                tags = AudioTags(
                    title = "midnight echoes",
                    artist = "the analog syndicate",
                    album = "late night sessions",
                    albumArtist = "the analog syndicate",
                    year = "2024",
                    track = "1/4",
                    genre = "Electronic",
                    comment = "Mastered at 44.1kHz"
                )
            ),
            SampleSpec(
                fileName = "02 - neon_horizon.mp3",
                isMp3 = true,
                freqHz = 329.63,
                tags = AudioTags(
                    title = "NEON HORIZON (FEAT. NOVA)",
                    artist = "the analog syndicate",
                    album = "late night sessions",
                    albumArtist = "the analog syndicate",
                    year = "2024",
                    track = "2",
                    genre = "Synthwave",
                    comment = "  extra whitespace  "
                )
            ),
            SampleSpec(
                fileName = "03 - Glass Cathedral.wav",
                isMp3 = false,
                freqHz = 392.00,
                tags = AudioTags(
                    title = "Glass Cathedral",
                    artist = "Sora Takahashi",
                    album = "Concrete Acoustics",
                    year = "2023",
                    track = "03",
                    genre = "Ambient",
                    bpm = "96"
                )
            ),
            SampleSpec(
                fileName = "04 - Velvet Static - Unsorted Demo.mp3",
                isMp3 = true,
                freqHz = 440.00,
                tags = AudioTags() // Intentionally untagged to demonstrate Filename -> Tag & Online Lookup
            )
        )

        return specs.map { spec ->
            val file = File(workspaceDir, spec.fileName)
            val rawAudio = if (spec.isMp3) buildValidPlayableMp3(durationSec = 4) else buildValidPlayableWav(spec.freqHz, durationSec = 4)
            val taggedBytes = if (spec.tags.hasAnyBasicTag) {
                AudioTagEngine.writeBytes(
                    originalData = rawAudio,
                    format = if (spec.isMp3) AudioFormat.MP3 else AudioFormat.WAV,
                    tags = spec.tags
                )
            } else {
                rawAudio
            }
            file.writeBytes(taggedBytes)
            file
        }
    }

    private data class SampleSpec(
        val fileName: String,
        val isMp3: Boolean,
        val freqHz: Double,
        val tags: AudioTags
    )

    private fun buildValidPlayableWav(freqHz: Double, durationSec: Int): ByteArray {
        val sampleRate = 22050
        val numSamples = sampleRate * durationSec
        val pcmBytes = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val env = when {
                i < 1000 -> i / 1000.0
                i > numSamples - 2000 -> (numSamples - i) / 2000.0
                else -> 1.0
            }
            val t = i.toDouble() / sampleRate
            val wave = (sin(2.0 * PI * freqHz * t) * 0.45 + sin(2.0 * PI * freqHz * 1.5 * t) * 0.25) * env
            val sample = (wave * 24000).toInt().coerceIn(-32768, 32767)
            pcmBytes[i * 2] = (sample and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        val out = ByteArrayOutputStream(44 + pcmBytes.size)
        out.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
        out.write(BinaryUtils.writeIntLE(36 + pcmBytes.size))
        out.write("WAVE".toByteArray(StandardCharsets.US_ASCII))
        out.write("fmt ".toByteArray(StandardCharsets.US_ASCII))
        out.write(BinaryUtils.writeIntLE(16))
        out.write(BinaryUtils.writeShortLE(1)) // PCM
        out.write(BinaryUtils.writeShortLE(1)) // Mono
        out.write(BinaryUtils.writeIntLE(sampleRate))
        out.write(BinaryUtils.writeIntLE(sampleRate * 2))
        out.write(BinaryUtils.writeShortLE(2))
        out.write(BinaryUtils.writeShortLE(16))
        out.write("data".toByteArray(StandardCharsets.US_ASCII))
        out.write(BinaryUtils.writeIntLE(pcmBytes.size))
        out.write(pcmBytes)
        return out.toByteArray()
    }

    /**
     * Generates valid MPEG-1 Layer III (128 kbps, 44100 Hz, Stereo) silent frames
     * with valid sync headers so MediaPlayer & ID3 parsers recognize duration and bitrate cleanly.
     */
    private fun buildValidPlayableMp3(durationSec: Int): ByteArray {
        // MPEG1 Layer III, 128 kbps (index 9), 44100 Hz (index 0), Stereo (mode 0)
        // Frame size = 144 * 128000 / 44100 = 417 bytes
        val frameSize = 417
        val framesPerSec = 38.28125
        val frameCount = (durationSec * framesPerSec).toInt().coerceAtLeast(10)
        val out = ByteArrayOutputStream(frameCount * frameSize)
        val frame = ByteArray(frameSize)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00.toByte()
        repeat(frameCount) {
            out.write(frame)
        }
        return out.toByteArray()
    }
}
