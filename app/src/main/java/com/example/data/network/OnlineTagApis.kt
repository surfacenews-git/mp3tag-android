package com.example.data.network

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// --- iTunes Search API ---

data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesItemDto> = emptyList()
)

data class ItunesItemDto(
    val wrapperType: String? = null,
    val collectionId: Long? = null,
    val trackId: Long? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val trackName: String? = null,
    val primaryGenreName: String? = null,
    val releaseDate: String? = null,
    val country: String? = null,
    val artworkUrl100: String? = null,
    val trackCount: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val trackTimeMillis: Long? = null
)

interface ItunesApi {
    @GET("search")
    suspend fun searchAlbums(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 20
    ): ItunesSearchResponse

    @GET("lookup")
    suspend fun lookupAlbumTracks(
        @Query("id") collectionId: Long,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 100
    ): ItunesSearchResponse
}

// --- MusicBrainz WS/2 API ---

data class MbReleaseSearchResponse(
    val releases: List<MbReleaseSummaryDto> = emptyList()
)

data class MbReleaseSummaryDto(
    val id: String = "",
    val title: String = "",
    val date: String? = null,
    val country: String? = null,
    @Json(name = "track-count") val trackCount: Int? = null,
    @Json(name = "artist-credit") val artistCredit: List<MbArtistCreditDto>? = null
)

data class MbArtistCreditDto(
    val name: String? = null,
    val joinphrase: String? = null
)

data class MbReleaseDetailDto(
    val id: String = "",
    val title: String = "",
    val date: String? = null,
    val country: String? = null,
    @Json(name = "artist-credit") val artistCredit: List<MbArtistCreditDto>? = null,
    val media: List<MbMediaDto>? = null,
    val genres: List<MbTagDto>? = null
)

data class MbMediaDto(
    val position: Int? = null,
    val tracks: List<MbTrackDto>? = null
)

data class MbTrackDto(
    val number: String? = null,
    val position: Int? = null,
    val title: String? = null,
    val length: Long? = null,
    @Json(name = "artist-credit") val artistCredit: List<MbArtistCreditDto>? = null
)

data class MbTagDto(
    val name: String? = null
)

interface MusicBrainzApi {
    @GET("ws/2/release/")
    suspend fun searchReleases(
        @Query("query") query: String,
        @Query("fmt") fmt: String = "json",
        @Query("limit") limit: Int = 20
    ): MbReleaseSearchResponse

    @GET("ws/2/release/{id}")
    suspend fun getReleaseDetails(
        @Path("id") releaseId: String,
        @Query("inc") inc: String = "recordings+artist-credits+genres",
        @Query("fmt") fmt: String = "json"
    ): MbReleaseDetailDto
}
