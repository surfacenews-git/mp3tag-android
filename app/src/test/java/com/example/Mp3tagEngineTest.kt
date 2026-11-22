package com.example

import com.example.audio.actions.ActionRunner
import com.example.audio.converter.FormatStringEngine
import com.example.audio.tagging.AudioTagEngine
import com.example.audio.tagging.BinaryUtils
import com.example.model.ActionStep
import com.example.model.ActionType
import com.example.model.AudioFormat
import com.example.model.AudioTags
import com.example.model.AudioTechInfo
import com.example.model.AudioTrackItem
import com.example.model.CaseMode
import com.example.model.CoverArt
import com.example.model.TagFieldKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class Mp3tagEngineTest {

    private fun makeDummyMp3Audio(): ByteArray {
        val frame = ByteArray(417)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00.toByte()
        val out = ByteArrayOutputStream()
        repeat(20) { out.write(frame) }
        return out.toByteArray()
    }

    private fun makeDummyWavAudio(): ByteArray {
        val pcm = ByteArray(1000)
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
        out.write(BinaryUtils.writeIntLE(36 + pcm.size))
        out.write("WAVE".toByteArray(StandardCharsets.US_ASCII))
        out.write("fmt ".toByteArray(StandardCharsets.US_ASCII))
        out.write(BinaryUtils.writeIntLE(16))
        out.write(BinaryUtils.writeShortLE(1))
        out.write(BinaryUtils.writeShortLE(2))
        out.write(BinaryUtils.writeIntLE(44100))
        out.write(BinaryUtils.writeIntLE(176400))
        out.write(BinaryUtils.writeShortLE(4))
        out.write(BinaryUtils.writeShortLE(16))
        out.write("data".toByteArray(StandardCharsets.US_ASCII))
        out.write(BinaryUtils.writeIntLE(pcm.size))
        out.write(pcm)
        return out.toByteArray()
    }

    @Test
    fun id3v23AndId3v24RoundTripPreservesMetadataAndCoverArt() {
        val rawMp3 = makeDummyMp3Audio()
        val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
        val tags = AudioTags(
            title = "Midnight Echoes (Rémi Mix)",
            artist = "The Analog Syndicate",
            album = "Late Night Sessions",
            albumArtist = "Analog Syndicate",
            year = "2024",
            track = "03/12",
            discNumber = "1/2",
            genre = "Electronic",
            comment = "Mastered cleanly",
            composer = "K. Sato",
            bpm = "124",
            customFields = mapOf("CATALOGNO" to "AS-009"),
            coverArt = CoverArt(fakePng, "image/png", 3, "Front Cover")
        )

        for (ver in listOf(3, 4)) {
            val tagged = AudioTagEngine.writeBytes(rawMp3, AudioFormat.MP3, tags, id3v2MajorVersion = ver, writeId3v1 = true)
            val parsed = AudioTagEngine.readBytes(tagged, AudioFormat.MP3)
            assertEquals(tags.title, parsed.tags.title)
            assertEquals(tags.artist, parsed.tags.artist)
            assertEquals(tags.album, parsed.tags.album)
            assertEquals(tags.albumArtist, parsed.tags.albumArtist)
            assertEquals(tags.year, parsed.tags.year)
            assertEquals(tags.track, parsed.tags.track)
            assertEquals(tags.discNumber, parsed.tags.discNumber)
            assertEquals(tags.genre, parsed.tags.genre)
            assertEquals(tags.comment, parsed.tags.comment)
            assertEquals(tags.composer, parsed.tags.composer)
            assertEquals(tags.bpm, parsed.tags.bpm)
            assertEquals("AS-009", parsed.tags.customFields["CATALOGNO"])
            assertNotNull(parsed.tags.coverArt)
            assertArrayEquals(fakePng, parsed.tags.coverArt!!.bytes)
            assertEquals(128, parsed.techInfo.bitrateKbps)
        }
    }

    @Test
    fun wavTagRoundTripPreservesRiffAndId3Metadata() {
        val rawWav = makeDummyWavAudio()
        val tags = AudioTags(
            title = "Glass Cathedral",
            artist = "Sora Takahashi",
            album = "Concrete Acoustics",
            year = "2023",
            track = "01",
            genre = "Ambient"
        )
        val tagged = AudioTagEngine.writeBytes(rawWav, AudioFormat.WAV, tags)
        val parsed = AudioTagEngine.readBytes(tagged, AudioFormat.WAV)
        assertEquals("Glass Cathedral", parsed.tags.title)
        assertEquals("Sora Takahashi", parsed.tags.artist)
        assertEquals("Concrete Acoustics", parsed.tags.album)
        assertEquals(44100, parsed.techInfo.sampleRateHz)
    }

    @Test
    fun formatStringAndFilenameToTagWorkCorrectly() {
        val track = AudioTrackItem(
            id = "1",
            fileName = "04 - Velvet Static - Unsorted Demo.mp3",
            directoryName = "Music",
            tags = AudioTags(
                title = "Midnight Echoes",
                artist = "Analog Syndicate",
                album = "Late Night",
                track = "1/8",
                year = "2024"
            ),
            techInfo = AudioTechInfo(AudioFormat.MP3, bitrateKbps = 320, durationMs = 215_000)
        )

        val formatted = FormatStringEngine.evaluateFilenameRename("\$num(%track%,2) - %artist% - %title%", track)
        assertEquals("01 - Analog Syndicate - Midnight Echoes.mp3", formatted)

        val extracted = FormatStringEngine.parseFilenameToTags("04 - Velvet Static - Unsorted Demo", "%track% - %artist% - %title%")
        assertNotNull(extracted)
        assertEquals("04", extracted!!["track"])
        assertEquals("Velvet Static", extracted["artist"])
        assertEquals("Unsorted Demo", extracted["title"])
    }

    @Test
    fun actionRunnerCaseConversionAndRegexReplaceWork() {
        val track = AudioTrackItem(
            id = "1",
            fileName = "01.mp3",
            directoryName = "Music",
            tags = AudioTags(
                title = "  NEON HORIZON (FEAT. NOVA) ",
                artist = "the analog syndicate",
                comment = "Downloaded from web"
            ),
            techInfo = AudioTechInfo(AudioFormat.MP3)
        )

        val steps = listOf(
            ActionStep("1", ActionType.TRIM, TagFieldKey.ALL),
            ActionStep("2", ActionType.CASE_CONVERSION, TagFieldKey.ALL, caseMode = CaseMode.TITLE_CASE),
            ActionStep("3", ActionType.REMOVE_FIELDS, TagFieldKey.COMMENT)
        )
        val updated = ActionRunner.applySteps(track, steps)
        assertEquals("Neon Horizon (feat. Nova)", updated.title)
        assertEquals("The Analog Syndicate", updated.artist)
        assertTrue(updated.comment.isEmpty())
    }

    @Test
    fun flacVorbisCommentAndPictureRoundTrip() {
        // Minimal valid fLaC stream with STREAMINFO (type 0, last block flag 0x80, 34 bytes) + 4 dummy audio bytes
        val streamInfo = ByteArray(34)
        // Sample rate 44100 (0xAC44), 2 channels, 16 bps, 44100 total samples
        streamInfo[10] = 0x0A
        streamInfo[11] = 0xC4.toByte()
        streamInfo[12] = 0x42
        streamInfo[13] = 0xF0.toByte()
        streamInfo[16] = 0xAC.toByte()
        streamInfo[17] = 0x44

        val flacHeader = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(StandardCharsets.US_ASCII))
            write(0x80) // last block, type 0 STREAMINFO
            write(0); write(0); write(34)
            write(streamInfo)
            write(byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x01, 0x02))
        }.toByteArray()

        val fakeJpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        val tags = AudioTags(
            title = "Concrete Acoustics",
            artist = "Sora Takahashi",
            album = "Tokyo Field",
            track = "2/10",
            genre = "Ambient",
            coverArt = CoverArt(fakeJpg, "image/jpeg", 3, "Front Cover", 500, 500)
        )

        val tagged = AudioTagEngine.writeBytes(flacHeader, AudioFormat.FLAC, tags)
        val parsed = AudioTagEngine.readBytes(tagged, AudioFormat.FLAC)
        assertEquals("Concrete Acoustics", parsed.tags.title)
        assertEquals("Sora Takahashi", parsed.tags.artist)
        assertEquals("2/10", parsed.tags.track)
        assertEquals("Ambient", parsed.tags.genre)
        assertNotNull(parsed.tags.coverArt)
        assertArrayEquals(fakeJpg, parsed.tags.coverArt!!.bytes)
    }
}
