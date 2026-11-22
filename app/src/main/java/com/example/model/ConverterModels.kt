package com.example.model

enum class ConverterMode(val title: String, val subtitle: String) {
    TAG_TO_FILENAME("Tag → Filename", "Rename files from tag fields"),
    FILENAME_TO_TAG("Filename → Tag", "Extract metadata from filenames"),
    TAG_TO_TAG("Tag → Tag", "Format or copy between fields"),
    AUTO_NUMBER("Auto-Number", "Sequential track & disc numbering"),
    EXPORT_IMPORT("Export / Playlist", "M3U8 playlists & CSV tag import")
}

data class RenamePreviewItem(
    val trackId: String,
    val oldFilename: String,
    val newFilename: String,
    val hasChanged: Boolean,
    val conflict: Boolean = false
)

data class FilenameToTagPreviewItem(
    val trackId: String,
    val filename: String,
    val matched: Boolean,
    val extractedFields: Map<String, String>
)

data class TagToTagPreviewItem(
    val trackId: String,
    val filename: String,
    val oldValue: String,
    val newValue: String
)

data class AutoNumberConfig(
    val startNumber: Int = 1,
    val zeroPadDigits: Int = 2,
    val includeTotalTracks: Boolean = true,
    val resetPerAlbum: Boolean = false,
    val discNumber: String = ""
)

data class AutoNumberPreviewItem(
    val trackId: String,
    val filename: String,
    val oldTrack: String,
    val newTrack: String,
    val newDisc: String
)
