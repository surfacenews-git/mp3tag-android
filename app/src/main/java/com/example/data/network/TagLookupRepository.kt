package com.example.data.network

import com.example.audio.tagging.BinaryUtils
import com.example.model.CoverArt
import com.example.model.OnlineReleaseResult
import com.example.model.OnlineTrackInfo
import com.example.model.TagSourceProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class TagLookupRepository {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mp3tagAndroid/1.0 (https://mp3tag.de)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()

    private val itunesApi: ItunesApi = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ItunesApi::class.java)

    private val musicBrainzApi: MusicBrainzApi = Retrofit.Builder()
        .baseUrl("https://musicbrainz.org/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(MusicBrainzApi::class.java)

    suspend fun searchReleases(query: String, provider: TagSourceProvider): Result<List<OnlineReleaseResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) return@runCatching emptyList()

                when (provider) {
                    TagSourceProvider.ITUNES -> {
                        val response = itunesApi.searchAlbums(trimmed)
                        response.results.mapNotNull { dto ->
                            val id = dto.collectionId ?: return@mapNotNull null
                            val artUrl = dto.artworkUrl100?.replace("100x100bb", "600x600bb")
                            OnlineReleaseResult(
                                id = id.toString(),
                                provider = TagSourceProvider.ITUNES,
                                albumTitle = dto.collectionName.orEmpty(),
                                artist = dto.artistName.orEmpty(),
                                year = dto.releaseDate?.take(4).orEmpty(),
                                genre = dto.primaryGenreName.orEmpty(),
                                country = dto.country.orEmpty(),
                                trackCount = dto.trackCount ?: 0,
                                coverArtUrl = artUrl,
                                isDetailLoaded = false
                            )
                        }
                    }
                    TagSourceProvider.MUSICBRAINZ -> {
                        val response = musicBrainzApi.searchReleases(trimmed)
                        response.releases.map { dto ->
                            val artist = formatMbArtist(dto.artistCredit)
                            OnlineReleaseResult(
                                id = dto.id,
                                provider = TagSourceProvider.MUSICBRAINZ,
                                albumTitle = dto.title,
                                artist = artist,
                                year = dto.date?.take(4).orEmpty(),
                                genre = "",
                                country = dto.country.orEmpty(),
                                trackCount = dto.trackCount ?: 0,
                                coverArtUrl = "https://coverartarchive.org/release/${dto.id}/front-500",
                                isDetailLoaded = false
                            )
                        }
                    }
                }
            }
        }

    suspend fun loadReleaseDetails(release: OnlineReleaseResult): Result<OnlineReleaseResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (release.provider) {
                    TagSourceProvider.ITUNES -> {
                        val cid = release.id.toLongOrNull() ?: return@runCatching release
                        val resp = itunesApi.lookupAlbumTracks(cid)
                        val tracks = resp.results
                            .filter { it.wrapperType == "track" }
                            .mapIndexed { idx, dto ->
                                OnlineTrackInfo(
                                    trackNumber = dto.trackNumber ?: (idx + 1),
                                    discNumber = dto.discNumber ?: 1,
                                    title = dto.trackName.orEmpty(),
                                    artist = dto.artistName.orEmpty().ifBlank { release.artist },
                                    durationMs = dto.trackTimeMillis ?: 0L
                                )
                            }
                        release.copy(tracks = tracks, isDetailLoaded = true)
                    }
                    TagSourceProvider.MUSICBRAINZ -> {
                        val detail = musicBrainzApi.getReleaseDetails(release.id)
                        val releaseArtist = formatMbArtist(detail.artistCredit).ifBlank { release.artist }
                        val tracks = mutableListOf<OnlineTrackInfo>()
                        detail.media?.forEach { media ->
                            val discNo = media.position ?: 1
                            media.tracks?.forEachIndexed { idx, t ->
                                val tArtist = formatMbArtist(t.artistCredit).ifBlank { releaseArtist }
                                tracks.add(
                                    OnlineTrackInfo(
                                        trackNumber = t.position ?: t.number?.toIntOrNull() ?: (idx + 1),
                                        discNumber = discNo,
                                        title = t.title.orEmpty(),
                                        artist = tArtist,
                                        durationMs = t.length ?: 0L
                                    )
                                )
                            }
                        }
                        val genre = detail.genres?.firstOrNull()?.name?.replaceFirstChar { it.uppercase() }
                            ?: release.genre
                        release.copy(
                            artist = releaseArtist,
                            year = detail.date?.take(4)?.ifBlank { release.year } ?: release.year,
                            genre = genre,
                            tracks = tracks,
                            isDetailLoaded = true
                        )
                    }
                }
            }
        }

    suspend fun downloadCoverArt(url: String): Result<CoverArt> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Cover art unavailable (HTTP ${resp.code})")
                val bytes = resp.body?.bytes() ?: error("Empty cover art response")
                if (bytes.isEmpty()) error("Empty cover art bytes")
                val mime = BinaryUtils.detectImageMimeType(bytes)
                val (w, h) = BinaryUtils.decodeImageDimensions(bytes)
                CoverArt(
                    bytes = bytes,
                    mimeType = mime,
                    pictureType = 3,
                    description = "Front Cover",
                    width = w,
                    height = h
                )
            }
        }
    }

    private fun formatMbArtist(credits: List<MbArtistCreditDto>?): String {
        if (credits.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        credits.forEach { c ->
            sb.append(c.name.orEmpty())
            sb.append(c.joinphrase.orEmpty())
        }
        return sb.toString().trim()
    }
}
