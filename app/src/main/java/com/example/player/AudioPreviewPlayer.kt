package com.example.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.model.AudioTrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val trackId: String? = null,
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class AudioPreviewPlayer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun togglePlay(track: AudioTrackItem) {
        val current = _state.value
        if (current.trackId == track.id && mediaPlayer != null) {
            val mp = mediaPlayer ?: return
            if (mp.isPlaying) {
                mp.pause()
                _state.value = current.copy(isPlaying = false)
            } else {
                mp.start()
                _state.value = current.copy(isPlaying = true)
                startProgressLoop()
            }
            return
        }

        stop()
        val uri = track.uri ?: track.filePath?.let { Uri.parse("file://$it") } ?: return
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnPreparedListener { player ->
                    player.start()
                    _state.value = PlaybackState(
                        trackId = track.id,
                        title = track.tags.title.ifBlank { track.baseName },
                        subtitle = track.tags.artist.ifBlank { track.fileName },
                        isPlaying = true,
                        positionMs = 0L,
                        durationMs = player.duration.toLong().coerceAtLeast(track.techInfo.durationMs)
                    )
                    startProgressLoop()
                }
                setOnCompletionListener {
                    _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
                    progressJob?.cancel()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        }.onFailure {
            stop()
        }
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        runCatching {
            mp.seekTo(positionMs.toInt())
            _state.value = _state.value.copy(positionMs = positionMs)
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _state.value = PlaybackState()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = mp.currentPosition.toLong(),
                        durationMs = mp.duration.toLong().coerceAtLeast(1L)
                    )
                }
                delay(250L)
            }
        }
    }
}
