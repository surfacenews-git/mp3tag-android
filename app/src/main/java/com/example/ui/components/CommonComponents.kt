package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioFormat
import com.example.model.CoverArt
import com.example.player.PlaybackState
import com.example.viewmodel.BatchFieldValue

@Composable
fun CoverArtThumbnail(
    coverArt: CoverArt?,
    size: Dp = 52.dp,
    cornerRadius: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(coverArt?.bytes) {
        coverArt?.bytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = stringResource(R.string.cd_album_art),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_default_cover),
                contentDescription = stringResource(R.string.cd_album_art),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun FormatBadgePill(
    format: AudioFormat,
    bitrateKbps: Int = 0,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (format) {
        AudioFormat.MP3 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        AudioFormat.FLAC, AudioFormat.WAV -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        AudioFormat.M4A, AudioFormat.OGG -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        AudioFormat.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val text = if (bitrateKbps > 0) "${format.displayName} · ${bitrateKbps}k" else format.displayName
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun BatchTagTextField(
    label: String,
    field: BatchFieldValue,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val displayText = if (field.isMixed && !field.isTouched) "" else field.value
    OutlinedTextField(
        value = displayText,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        placeholder = {
            if (field.isMixed && !field.isTouched) {
                Text(
                    text = stringResource(R.string.keep_existing),
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        singleLine = singleLine,
        minLines = minLines,
        trailingIcon = trailingContent,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = if (field.isMixed && !field.isTouched) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
fun FormatPlaceholderChips(
    tokens: List<String>,
    onInsertToken: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tokens.forEach { token ->
            AssistChip(
                onClick = { onInsertToken(token) },
                label = {
                    Text(
                        text = token,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.primary
                ),
                border = null
            )
        }
    }
}

@Composable
fun MiniAudioPlayerBar(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (playbackState.trackId == null) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playbackState.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playbackState.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${formatMs(playbackState.positionMs)} / ${formatMs(playbackState.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_toggle")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) stringResource(R.string.cd_pause_track) else stringResource(R.string.cd_play_track),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop playback",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val duration = playbackState.durationMs.coerceAtLeast(1L)
            Slider(
                value = (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac -> onSeek((frac * duration).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(sec / 60L, sec % 60L)
}
