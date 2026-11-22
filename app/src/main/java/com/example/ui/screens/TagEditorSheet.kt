package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioTrackItem
import com.example.model.TagFieldKey
import com.example.ui.components.BatchTagTextField
import com.example.ui.components.CoverArtThumbnail
import com.example.ui.components.FormatBadgePill
import com.example.viewmodel.CoverDraftState
import com.example.viewmodel.TagEditorDraft

private val QUICK_GENRES = listOf(
    "Electronic", "Rock", "Pop", "Jazz", "Classical", "Hip-Hop",
    "Ambient", "Synthwave", "Indie", "Soundtrack", "Metal", "Folk"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorPanel(
    selectedTracks: List<AudioTrackItem>,
    draft: TagEditorDraft,
    isSaving: Boolean,
    showCloseButton: Boolean,
    onClose: () -> Unit,
    onUpdateField: (TagFieldKey, String) -> Unit,
    onSetCustomField: (String, String) -> Unit,
    onPickCoverUri: (android.net.Uri) -> Unit,
    onRemoveCover: () -> Unit,
    onExportCoverUri: (android.net.Uri) -> Unit,
    onOpenOnlineLookup: () -> Unit,
    onSave: () -> Unit,
    onRevert: () -> Unit,
    onStripTags: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var showStripConfirm by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(onPickCoverUri)
    }

    val exportCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        uri?.let(onExportCoverUri)
    }

    if (selectedTracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Tag Inspector",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap any track to edit its tags, or select multiple files to batch-edit shared metadata.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val singleTrack = selectedTracks.singleOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val previewCover = when (val cs = draft.coverState) {
                is CoverDraftState.Replaced -> cs.coverArt
                CoverDraftState.Removed -> null
                is CoverDraftState.Uniform -> cs.coverArt
                else -> selectedTracks.firstOrNull()?.tags?.coverArt
            }
            CoverArtThumbnail(coverArt = previewCover, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = singleTrack?.fileName ?: "Batch Edit (${selectedTracks.size} files)",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (singleTrack != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormatBadgePill(
                            format = singleTrack.techInfo.format,
                            bitrateKbps = singleTrack.techInfo.bitrateKbps
                        )
                        Text(
                            text = "${singleTrack.techInfo.formattedDuration} · ${singleTrack.techInfo.tagSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "Unmodified <keep> fields preserve per-file tags",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (showCloseButton) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tag_editor_close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close tag editor")
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = activeTab) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Tags") },
                modifier = Modifier.testTag("editor_tab_tags")
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Cover Art") },
                modifier = Modifier.testTag("editor_tab_cover")
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Extended") },
                modifier = Modifier.testTag("editor_tab_extended")
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                0 -> {
                    BatchTagTextField(
                        label = "Title",
                        field = draft.title,
                        onValueChange = { onUpdateField(TagFieldKey.TITLE, it) },
                        testTag = "input_title"
                    )
                    BatchTagTextField(
                        label = "Artist",
                        field = draft.artist,
                        onValueChange = { onUpdateField(TagFieldKey.ARTIST, it) },
                        testTag = "input_artist"
                    )
                    BatchTagTextField(
                        label = "Album",
                        field = draft.album,
                        onValueChange = { onUpdateField(TagFieldKey.ALBUM, it) },
                        testTag = "input_album"
                    )
                    BatchTagTextField(
                        label = "Album Artist",
                        field = draft.albumArtist,
                        onValueChange = { onUpdateField(TagFieldKey.ALBUM_ARTIST, it) },
                        testTag = "input_album_artist"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BatchTagTextField(
                            label = "Track (e.g. 01/12)",
                            field = draft.track,
                            onValueChange = { onUpdateField(TagFieldKey.TRACK, it) },
                            testTag = "input_track",
                            modifier = Modifier.weight(1f)
                        )
                        BatchTagTextField(
                            label = "Disc",
                            field = draft.discNumber,
                            onValueChange = { onUpdateField(TagFieldKey.DISC, it) },
                            testTag = "input_disc",
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BatchTagTextField(
                            label = "Year",
                            field = draft.year,
                            onValueChange = { onUpdateField(TagFieldKey.YEAR, it) },
                            testTag = "input_year",
                            modifier = Modifier.weight(1f)
                        )
                        BatchTagTextField(
                            label = "BPM",
                            field = draft.bpm,
                            onValueChange = { onUpdateField(TagFieldKey.BPM, it) },
                            testTag = "input_bpm",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    BatchTagTextField(
                        label = "Genre",
                        field = draft.genre,
                        onValueChange = { onUpdateField(TagFieldKey.GENRE, it) },
                        testTag = "input_genre"
                    )
                    // Quick Genre Suggestions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QUICK_GENRES.forEach { g ->
                            AssistChip(
                                onClick = { onUpdateField(TagFieldKey.GENRE, g) },
                                label = { Text(g, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    BatchTagTextField(
                        label = "Comment",
                        field = draft.comment,
                        onValueChange = { onUpdateField(TagFieldKey.COMMENT, it) },
                        testTag = "input_comment",
                        singleLine = false,
                        minLines = 2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BatchTagTextField(
                            label = "Composer",
                            field = draft.composer,
                            onValueChange = { onUpdateField(TagFieldKey.COMPOSER, it) },
                            testTag = "input_composer",
                            modifier = Modifier.weight(1f)
                        )
                        BatchTagTextField(
                            label = "Publisher",
                            field = draft.publisher,
                            onValueChange = { onUpdateField(TagFieldKey.PUBLISHER, it) },
                            testTag = "input_publisher",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    BatchTagTextField(
                        label = "Copyright",
                        field = draft.copyright,
                        onValueChange = { onUpdateField(TagFieldKey.COPYRIGHT, it) },
                        testTag = "input_copyright"
                    )
                }

                1 -> {
                    val displayedArt = when (val cs = draft.coverState) {
                        is CoverDraftState.Replaced -> cs.coverArt
                        CoverDraftState.Removed -> null
                        is CoverDraftState.Uniform -> cs.coverArt
                        else -> null
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CoverArtThumbnail(
                                coverArt = displayedArt,
                                size = 196.dp,
                                cornerRadius = 14.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            val artDesc = when {
                                draft.coverState is CoverDraftState.KeepMixed -> "Mixed cover art across selected files (<keep>)"
                                draft.coverState is CoverDraftState.Removed -> "Cover art marked for removal"
                                displayedArt != null -> {
                                    val dims = if (displayedArt.width > 0) "${displayedArt.width}×${displayedArt.height} · " else ""
                                    "$dims${displayedArt.mimeType} · ${displayedArt.sizeKb} KB"
                                }
                                else -> "No embedded cover art"
                            }
                            Text(
                                text = artDesc,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pick_cover_button")
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Choose Image")
                                }
                                FilledTonalButton(
                                    onClick = onOpenOnlineLookup,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("fetch_cover_online_button")
                                ) {
                                    Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Fetch Online")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val base = singleTrack?.tags?.album?.ifBlank { singleTrack.baseName } ?: "cover"
                                        exportCoverLauncher.launch("${base}_cover.jpg")
                                    },
                                    enabled = displayedArt != null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("export_cover_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export Cover")
                                }
                                OutlinedButton(
                                    onClick = onRemoveCover,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("remove_cover_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }

                2 -> {
                    BatchTagTextField(
                        label = "Unsynced Lyrics (USLT)",
                        field = draft.lyrics,
                        onValueChange = { onUpdateField(TagFieldKey.LYRICS, it) },
                        testTag = "input_lyrics",
                        singleLine = false,
                        minLines = 4
                    )

                    HorizontalDivider()
                    Text(
                        text = "Custom Extended Tags (TXXX / Vorbis)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (selectedTracks.size > 1) {
                        Text(
                            text = "Custom key-value editing is available when a single track is selected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        var newKey by remember { mutableStateOf("") }
                        var newVal by remember { mutableStateOf("") }

                        draft.customFields.forEach { (k, v) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = k,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(0.4f)
                                )
                                Text(
                                    text = v,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(0.5f)
                                )
                                IconButton(
                                    onClick = { onSetCustomField(k, "") },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete $k")
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newKey,
                                onValueChange = { newKey = it },
                                label = { Text("Field") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.42f)
                                    .testTag("custom_field_key_input")
                            )
                            OutlinedTextField(
                                value = newVal,
                                onValueChange = { newVal = it },
                                label = { Text("Value") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.42f)
                                    .testTag("custom_field_value_input")
                            )
                            IconButton(
                                onClick = {
                                    if (newKey.isNotBlank() && newVal.isNotBlank()) {
                                        onSetCustomField(newKey.trim(), newVal.trim())
                                        newKey = ""
                                        newVal = ""
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("add_custom_field_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add custom tag field")
                            }
                        }
                    }

                    if (singleTrack != null) {
                        HorizontalDivider()
                        Text(
                            text = "Technical Audio Information",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TechInfoRow("Format", singleTrack.techInfo.format.displayName)
                                TechInfoRow("Tag Types", singleTrack.techInfo.tagSummary)
                                TechInfoRow("Bitrate", "${singleTrack.techInfo.bitrateKbps} kbps")
                                TechInfoRow("Sample Rate", "${singleTrack.techInfo.sampleRateHz} Hz")
                                TechInfoRow("Channels", if (singleTrack.techInfo.channels == 1) "Mono" else "Stereo (${singleTrack.techInfo.channels}ch)")
                                TechInfoRow("Duration", singleTrack.techInfo.formattedDuration)
                                TechInfoRow("File Size", singleTrack.techInfo.formattedSize)
                                TechInfoRow("Directory", singleTrack.directoryName)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sticky Save / Revert / Strip Bar
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            IconButton(
                onClick = { showStripConfirm = true },
                enabled = !isSaving,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("strip_tags_button")
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Strip all tags",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            OutlinedButton(
                onClick = onRevert,
                enabled = !isSaving,
                modifier = Modifier.testTag("revert_tags_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_revert))
            }
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_tags_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_save_tags))
            }
        }
    }

    if (showStripConfirm) {
        AlertDialog(
            onDismissRequest = { showStripConfirm = false },
            title = { Text("Strip All Tags?") },
            text = {
                Text("This will permanently remove all ID3/Vorbis/MP4 metadata and embedded cover art from ${selectedTracks.size} file(s), keeping only audio.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStripConfirm = false
                        onStripTags()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Strip Tags")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStripConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TechInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
