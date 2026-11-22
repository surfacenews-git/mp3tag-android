package com.example.model

import android.net.Uri

enum class AudioFormat(val extension: String, val displayName: String, val mimeType: String) {
    MP3("mp3", "MP3", "audio/mpeg"),
    FLAC("flac", "FLAC", "audio/flac"),
    M4A("m4a", "M4A", "audio/mp4"),
    OGG("ogg", "OGG", "audio/ogg"),
    WAV("wav", "WAV", "audio/wav"),
    UNKNOWN("audio", "AUDIO", "audio/*");

    companion object {
        fun fromFilename(name: String): AudioFormat {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp3" -> MP3
                "flac" -> FLAC
                "m4a", "mp4", "aac" -> M4A
                "ogg", "oga" -> OGG
                "wav", "wave" -> WAV
                else -> UNKNOWN
            }
        }
    }
}

data class CoverArt(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg",
    val pictureType: Int = 3, // 3 = Front Cover
    val description: String = "Front Cover",
    val width: Int = 0,
    val height: Int = 0
) {
    val sizeKb: Int get() = (bytes.size + 1023) / 1024

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoverArt) return false
        return mimeType == other.mimeType &&
            pictureType == other.pictureType &&
            description == other.description &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + pictureType
        result = 31 * result + description.hashCode()
        return result
    }
}

data class AudioTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val track: String = "",
    val discNumber: String = "",
    val genre: String = "",
    val comment: String = "",
    val composer: String = "",
    val publisher: String = "",
    val copyright: String = "",
    val lyrics: String = "",
    val bpm: String = "",
    val encodedBy: String = "",
    val compilation: Boolean = false,
    val customFields: Map<String, String> = emptyMap(),
    val coverArt: CoverArt? = null
) {
    val hasAnyBasicTag: Boolean
        get() = title.isNotBlank() || artist.isNotBlank() || album.isNotBlank()
}

data class AudioTechInfo(
    val format: AudioFormat,
    val tagSummary: String = "",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val channels: Int = 2,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val lastModifiedMs: Long = 0L
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "%d:%02d".format(minutes, seconds)
        }

    val formattedSize: String
        get() = when {
            fileSizeBytes >= 1024 * 1024 -> "%.1f MB".format(fileSizeBytes / (1024f * 1024f))
            fileSizeBytes >= 1024 -> "%.0f KB".format(fileSizeBytes / 1024f)
            else -> "$fileSizeBytes B"
        }
}

data class AudioTrackItem(
    val id: String,
    val uri: Uri? = null,
    val filePath: String? = null,
    val fileName: String,
    val directoryName: String,
    val tags: AudioTags,
    val techInfo: AudioTechInfo,
    val isModified: Boolean = false,
    val originalTags: AudioTags = tags
) {
    val baseName: String
        get() = fileName.substringBeforeLast('.')

    val extension: String
        get() = fileName.substringAfterLast('.', "")
}

enum class TagFieldKey(val label: String, val placeholder: String) {
    TITLE("Title", "%title%"),
    ARTIST("Artist", "%artist%"),
    ALBUM("Album", "%album%"),
    ALBUM_ARTIST("Album Artist", "%albumartist%"),
    YEAR("Year", "%year%"),
    TRACK("Track", "%track%"),
    DISC("Discnumber", "%discnumber%"),
    GENRE("Genre", "%genre%"),
    COMMENT("Comment", "%comment%"),
    COMPOSER("Composer", "%composer%"),
    PUBLISHER("Publisher", "%publisher%"),
    COPYRIGHT("Copyright", "%copyright%"),
    BPM("BPM", "%bpm%"),
    LYRICS("Lyrics", "%unsyncedlyrics%"),
    ALL("All Fields", "_ALL")
}

/**
 * Represents a batch edit patch where null means "<keep>" (preserve per-file value).
 */
data class TagPatch(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: String? = null,
    val track: String? = null,
    val discNumber: String? = null,
    val genre: String? = null,
    val comment: String? = null,
    val composer: String? = null,
    val publisher: String? = null,
    val copyright: String? = null,
    val lyrics: String? = null,
    val bpm: String? = null,
    val coverArtAction: CoverArtPatch = CoverArtPatch.Keep
)

sealed interface CoverArtPatch {
    data object Keep : CoverArtPatch
    data object Remove : CoverArtPatch
    data class Replace(val coverArt: CoverArt) : CoverArtPatch
}

enum class TrackFilter(val label: String) {
    ALL("All"),
    UNTAGGED("Untagged"),
    NO_ART("Missing Art"),
    MODIFIED("Unsaved"),
    MP3("MP3"),
    LOSSLESS("FLAC / WAV")
}

enum class TrackSortOrder(val label: String) {
    FILENAME("Filename"),
    TRACK_NUMBER("Track #"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album")
}
