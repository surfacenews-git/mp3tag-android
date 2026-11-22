package com.example.model

enum class TagSourceProvider(val label: String, val subtitle: String) {
    ITUNES("iTunes Search", "Fast release & high-res 600px cover art"),
    MUSICBRAINZ("MusicBrainz", "Open discography + Cover Art Archive")
}

data class OnlineTrackInfo(
    val trackNumber: Int,
    val discNumber: Int = 1,
    val title: String,
    val artist: String,
    val durationMs: Long = 0L
)

data class OnlineReleaseResult(
    val id: String,
    val provider: TagSourceProvider,
    val albumTitle: String,
    val artist: String,
    val year: String,
    val genre: String,
    val country: String = "",
    val trackCount: Int = 0,
    val coverArtUrl: String? = null,
    val tracks: List<OnlineTrackInfo> = emptyList(),
    val isDetailLoaded: Boolean = false
)
