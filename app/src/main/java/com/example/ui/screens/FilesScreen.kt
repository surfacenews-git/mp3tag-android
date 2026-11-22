package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.FolderBookmarkEntity
import com.example.model.AudioTrackItem
import com.example.model.TagFieldKey
import com.example.model.TrackFilter
import com.example.model.TrackSortOrder
import com.example.player.PlaybackState
import com.example.ui.components.CoverArtThumbnail
import com.example.ui.components.FormatBadgePill
import com.example.viewmodel.TagEditorDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    tracks: List<AudioTrackItem>,
    allTracksCount: Int,
    selectedIds: Set<String>,
    searchQuery: String,
    activeFilter: TrackFilter,
    sortOrder: TrackSortOrder,
    currentSourceLabel: String,
    bookmarks: List<FolderBookmarkEntity>,
    isLoading: Boolean,
    isSaving: Boolean,
    isTagEditorOpen: Boolean,
    editorDraft: TagEditorDraft,
    playbackState: PlaybackState,
    onOpenFolderUri: (Uri) -> Unit,
    onPickFilesUris: (List<Uri>) -> Unit,
    onCreateSampleWorkspace: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (TrackFilter) -> Unit,
    onSortChange: (TrackSortOrder) -> Unit,
    onToggleSelect: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onOpenBatchEditor: () -> Unit,
    onCloseEditor: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onTogglePlay: (AudioTrackItem) -> Unit,
    onUpdateDraftField: (TagFieldKey, String) -> Unit,
    onSetCustomField: (String, String) -> Unit,
    onPickCoverUri: (Uri) -> Unit,
    onRemoveCover: () -> Unit,
    onExportCoverUri: (Uri) -> Unit,
    onOpenOnlineLookup: () -> Unit,
    onSaveTags: () -> Unit,
    onRevertTags: () -> Unit,
    onStripTags: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(onOpenFolderUri) }

    val filesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onPickFilesUris(uris) }

    val selectedTracks = remember(tracks, selectedIds) {
        tracks.filter { it.id in selectedIds }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpandedSplit = maxWidth >= 840.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Left / Main Track List Pane
            Column(
                modifier = Modifier
                    .weight(if (isExpandedSplit) 0.54f else 1f)
                    .fillMaxHeight()
            ) {
                // Workspace Source & Actions Header
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var showBookmarksMenu by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    onClick = { showBookmarksMenu = true },
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.testTag("folder_switcher_chip")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = currentSourceLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$allTracksCount file(s) loaded",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                DropdownMenu(
                                    expanded = showBookmarksMenu,
                                    onDismissRequest = { showBookmarksMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open Music Folder (SAF)") },
                                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                                        onClick = {
                                            showBookmarksMenu = false
                                            folderLauncher.launch(null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Pick Individual Audio Files") },
                                        leadingIcon = { Icon(Icons.Default.AudioFile, null) },
                                        onClick = {
                                            showBookmarksMenu = false
                                            filesLauncher.launch(arrayOf("audio/*"))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Create Sample Audio Workspace") },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                        onClick = {
                                            showBookmarksMenu = false
                                            onCreateSampleWorkspace()
                                        }
                                    )
                                    if (bookmarks.isNotEmpty()) {
                                        HorizontalDivider()
                                        bookmarks.forEach { bm ->
                                            DropdownMenuItem(
                                                text = { Text("Recent: ${bm.displayName}") },
                                                onClick = {
                                                    showBookmarksMenu = false
                                                    onOpenFolderUri(Uri.parse(bm.uriString))
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = { folderLauncher.launch(null) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("open_folder_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Folder")
                            }

                            OutlinedButton(
                                onClick = { filesLauncher.launch(arrayOf("audio/*")) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("pick_files_button")
                            ) {
                                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Files")
                            }
                        }

                        if (allTracksCount > 0) {
                            // Search + Sort row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    placeholder = { Text("Filter title, artist, album, filename...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { onSearchQueryChange("") }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("track_search_input")
                                )

                                var showSortMenu by remember { mutableStateOf(false) }
                                Box {
                                    FilledTonalButton(
                                        onClick = { showSortMenu = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("sort_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort tracks", modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(sortOrder.label, style = MaterialTheme.typography.labelMedium)
                                    }
                                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                        TrackSortOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = { Text(order.label) },
                                                onClick = {
                                                    onSortChange(order)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Filter Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TrackFilter.entries.forEach { f ->
                                    FilterChip(
                                        selected = activeFilter == f,
                                        onClick = { onFilterChange(f) },
                                        label = { Text(f.label, style = MaterialTheme.typography.labelMedium) },
                                        modifier = Modifier.testTag("filter_chip_${f.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Multi-select Sticky Action Bar
                AnimatedVisibility(visible = selectedIds.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${selectedIds.size} selected",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onSelectAll,
                                modifier = Modifier.testTag("select_all_button")
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("All")
                            }
                            TextButton(
                                onClick = onInvertSelection,
                                modifier = Modifier.testTag("invert_selection_button")
                            ) {
                                Text("Invert")
                            }
                            TextButton(
                                onClick = onClearSelection,
                                modifier = Modifier.testTag("clear_selection_button")
                            ) {
                                Text("Clear")
                            }
                            if (!isExpandedSplit) {
                                Button(
                                    onClick = onOpenBatchEditor,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("batch_edit_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit")
                                }
                            }
                        }
                    }
                }

                // Loading / Empty / Track List
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text("Scanning audio tags...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    allTracksCount == 0 -> {
                        EmptyLibraryState(
                            onOpenFolder = { folderLauncher.launch(null) },
                            onPickFiles = { filesLauncher.launch(arrayOf("audio/*")) },
                            onCreateSamples = onCreateSampleWorkspace
                        )
                    }
                    tracks.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(
                                    Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("No tracks match your filter", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = {
                                    onSearchQueryChange("")
                                    onFilterChange(TrackFilter.ALL)
                                }) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("track_list")
                        ) {
                            items(tracks, key = { it.id }) { track ->
                                val isSelected = track.id in selectedIds
                                val isPlaying = playbackState.trackId == track.id && playbackState.isPlaying
                                TrackRowCard(
                                    track = track,
                                    isSelected = isSelected,
                                    isPlaying = isPlaying,
                                    onToggleSelect = { onToggleSelect(track.id) },
                                    onClick = { onTrackClick(track.id) },
                                    onTogglePlay = { onTogglePlay(track) }
                                )
                            }
                            item { Spacer(Modifier.height(72.dp)) }
                        }
                    }
                }
            }

            // Expanded Tablet Right-Hand Tag Inspector Pane
            if (isExpandedSplit) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TagEditorPanel(
                    selectedTracks = selectedTracks,
                    draft = editorDraft,
                    isSaving = isSaving,
                    showCloseButton = false,
                    onClose = onCloseEditor,
                    onUpdateField = onUpdateDraftField,
                    onSetCustomField = onSetCustomField,
                    onPickCoverUri = onPickCoverUri,
                    onRemoveCover = onRemoveCover,
                    onExportCoverUri = onExportCoverUri,
                    onOpenOnlineLookup = onOpenOnlineLookup,
                    onSave = onSaveTags,
                    onRevert = onRevertTags,
                    onStripTags = onStripTags,
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                )
            }
        }

        // Compact Modal Bottom Sheet Tag Editor
        if (!isExpandedSplit && isTagEditorOpen && selectedTracks.isNotEmpty()) {
            BackHandler(onBack = onCloseEditor)
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = onCloseEditor,
                sheetState = sheetState,
                dragHandle = null,
                modifier = Modifier.fillMaxHeight(0.94f)
            ) {
                TagEditorPanel(
                    selectedTracks = selectedTracks,
                    draft = editorDraft,
                    isSaving = isSaving,
                    showCloseButton = true,
                    onClose = onCloseEditor,
                    onUpdateField = onUpdateDraftField,
                    onSetCustomField = onSetCustomField,
                    onPickCoverUri = onPickCoverUri,
                    onRemoveCover = onRemoveCover,
                    onExportCoverUri = onExportCoverUri,
                    onOpenOnlineLookup = {
                        onCloseEditor()
                        onOpenOnlineLookup()
                    },
                    onSave = onSaveTags,
                    onRevert = onRevertTags,
                    onStripTags = onStripTags
                )
            }
        }
    }
}

@Composable
private fun TrackRowCard(
    track: AudioTrackItem,
    isSelected: Boolean,
    isPlaying: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("track_card_${track.fileName}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("track_checkbox_${track.fileName}")
            )
            CoverArtThumbnail(coverArt = track.tags.coverArt, size = 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (track.tags.track.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                text = track.tags.track.substringBefore('/').padStart(2, '0'),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = track.tags.title.ifBlank { track.baseName },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (track.tags.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
                val artistAlbum = listOf(
                    track.tags.artist.ifBlank { "Unknown Artist" },
                    track.tags.album.ifBlank { "No Album" }
                ).joinToString(" · ")
                Text(
                    text = artistAlbum,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FormatBadgePill(track.techInfo.format, track.techInfo.bitrateKbps)
                    Text(
                        text = track.techInfo.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = track.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("track_play_${track.fileName}")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.cd_pause_track) else stringResource(R.string.cd_play_track),
                    tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    onOpenFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onCreateSamples: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_studio_deck),
                contentDescription = stringResource(R.string.cd_empty_studio),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Load Your Audio Library",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Edit ID3v1, ID3v2.3/2.4, FLAC Vorbis, M4A/MP4, OGG, and WAV metadata directly using Android's secure Storage Access Framework.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenFolder,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_open_folder_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_open_folder))
                }
                FilledTonalButton(
                    onClick = onPickFiles,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_pick_files_button")
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_pick_files))
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onCreateSamples,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_create_samples_button")
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_create_samples))
            }
        }
    }
}
