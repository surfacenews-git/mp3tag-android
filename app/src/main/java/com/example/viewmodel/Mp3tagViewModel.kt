package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.actions.ActionRunner
import com.example.audio.converter.FormatStringEngine
import com.example.data.local.AppSettings
import com.example.data.local.FolderBookmarkEntity
import com.example.data.local.FormatPresetEntity
import com.example.data.local.LocalConfigRepository
import com.example.data.local.Mp3tagDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.network.TagLookupRepository
import com.example.data.repository.AudioLibraryRepository
import com.example.model.ActionGroup
import com.example.model.ActionPreviewItem
import com.example.model.ActionStep
import com.example.model.AudioFormat
import com.example.model.AudioTags
import com.example.model.AudioTrackItem
import com.example.model.AutoNumberConfig
import com.example.model.AutoNumberPreviewItem
import com.example.model.ConverterMode
import com.example.model.CoverArt
import com.example.model.FilenameToTagPreviewItem
import com.example.model.OnlineReleaseResult
import com.example.model.RenamePreviewItem
import com.example.model.TagFieldKey
import com.example.model.TagSourceProvider
import com.example.model.TagToTagPreviewItem
import com.example.model.TrackFilter
import com.example.model.TrackSortOrder
import com.example.player.AudioPreviewPlayer
import com.example.player.PlaybackState
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BatchFieldValue(
    val value: String = "",
    val isMixed: Boolean = false,
    val isTouched: Boolean = false
)

sealed interface CoverDraftState {
    data object Unchanged : CoverDraftState
    data object KeepMixed : CoverDraftState
    data class Uniform(val coverArt: CoverArt?) : CoverDraftState
    data class Replaced(val coverArt: CoverArt) : CoverDraftState
    data object Removed : CoverDraftState
}

data class TagEditorDraft(
    val trackCount: Int = 0,
    val title: BatchFieldValue = BatchFieldValue(),
    val artist: BatchFieldValue = BatchFieldValue(),
    val album: BatchFieldValue = BatchFieldValue(),
    val albumArtist: BatchFieldValue = BatchFieldValue(),
    val year: BatchFieldValue = BatchFieldValue(),
    val track: BatchFieldValue = BatchFieldValue(),
    val discNumber: BatchFieldValue = BatchFieldValue(),
    val genre: BatchFieldValue = BatchFieldValue(),
    val comment: BatchFieldValue = BatchFieldValue(),
    val composer: BatchFieldValue = BatchFieldValue(),
    val publisher: BatchFieldValue = BatchFieldValue(),
    val copyright: BatchFieldValue = BatchFieldValue(),
    val bpm: BatchFieldValue = BatchFieldValue(),
    val lyrics: BatchFieldValue = BatchFieldValue(),
    val customFields: Map<String, String> = emptyMap(),
    val coverState: CoverDraftState = CoverDraftState.Unchanged
)

data class LookupUiState(
    val provider: TagSourceProvider = TagSourceProvider.ITUNES,
    val query: String = "",
    val isSearching: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val isApplying: Boolean = false,
    val results: List<OnlineReleaseResult> = emptyList(),
    val selectedRelease: OnlineReleaseResult? = null,
    val selectedCoverPreview: CoverArt? = null,
    val errorMessage: String? = null
)

class Mp3tagViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepo = AudioLibraryRepository(application)
    private val lookupRepo = TagLookupRepository()
    private val settingsStore = SettingsDataStore(application)
    private val db = Mp3tagDatabase.getInstance(application)
    private val configRepo = LocalConfigRepository(db.mp3tagDao())
    private val previewPlayer = AudioPreviewPlayer(application, viewModelScope)

    private val _tracks = MutableStateFlow<List<AudioTrackItem>>(emptyList())
    val tracks: StateFlow<List<AudioTrackItem>> = _tracks.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(TrackFilter.ALL)
    val filter: StateFlow<TrackFilter> = _filter.asStateFlow()

    private val _sortOrder = MutableStateFlow(TrackSortOrder.TRACK_NUMBER)
    val sortOrder: StateFlow<TrackSortOrder> = _sortOrder.asStateFlow()

    private val _currentSourceLabel = MutableStateFlow("No folder loaded")
    val currentSourceLabel: StateFlow<String> = _currentSourceLabel.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(false)
    val isLoadingLibrary: StateFlow<Boolean> = _isLoadingLibrary.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _editorDraft = MutableStateFlow(TagEditorDraft())
    val editorDraft: StateFlow<TagEditorDraft> = _editorDraft.asStateFlow()

    private val _isTagEditorOpen = MutableStateFlow(false)
    val isTagEditorOpen: StateFlow<Boolean> = _isTagEditorOpen.asStateFlow()

    private val _lookupState = MutableStateFlow(LookupUiState())
    val lookupState: StateFlow<LookupUiState> = _lookupState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    val playbackState: StateFlow<PlaybackState> = previewPlayer.state

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val actionGroups: StateFlow<List<ActionGroup>> = configRepo.actionGroupsFlow
        .map { list -> list.map { configRepo.toDomain(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val formatPresets: StateFlow<List<FormatPresetEntity>> = configRepo.formatPresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderBookmarks: StateFlow<List<FolderBookmarkEntity>> = configRepo.folderBookmarksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleTracks: StateFlow<List<AudioTrackItem>> = combine(
        _tracks, _searchQuery, _filter, _sortOrder
    ) { allTracks, query, activeFilter, sort ->
        val q = query.trim().lowercase()
        val filtered = allTracks.filter { track ->
            val matchesFilter = when (activeFilter) {
                TrackFilter.ALL -> true
                TrackFilter.UNTAGGED -> !track.tags.hasAnyBasicTag
                TrackFilter.NO_ART -> track.tags.coverArt == null
                TrackFilter.MODIFIED -> track.isModified
                TrackFilter.MP3 -> track.techInfo.format == AudioFormat.MP3
                TrackFilter.LOSSLESS -> track.techInfo.format == AudioFormat.FLAC || track.techInfo.format == AudioFormat.WAV
            }
            val matchesQuery = if (q.isEmpty()) true else {
                track.fileName.lowercase().contains(q) ||
                    track.tags.title.lowercase().contains(q) ||
                    track.tags.artist.lowercase().contains(q) ||
                    track.tags.album.lowercase().contains(q) ||
                    track.tags.genre.lowercase().contains(q)
            }
            matchesFilter && matchesQuery
        }

        when (sort) {
            TrackSortOrder.FILENAME -> filtered.sortedBy { it.fileName.lowercase() }
            TrackSortOrder.TRACK_NUMBER -> filtered.sortedWith(
                compareBy<AudioTrackItem>(
                    { it.tags.discNumber.substringBefore('/').toIntOrNull() ?: 999 },
                    { it.tags.track.substringBefore('/').toIntOrNull() ?: 9999 },
                    { it.fileName.lowercase() }
                )
            )
            TrackSortOrder.TITLE -> filtered.sortedBy { it.tags.title.ifBlank { it.fileName }.lowercase() }
            TrackSortOrder.ARTIST -> filtered.sortedBy { it.tags.artist.ifBlank { "zzz" }.lowercase() }
            TrackSortOrder.ALBUM -> filtered.sortedBy { it.tags.album.ifBlank { "zzz" }.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            configRepo.ensureDefaultsSeeded()
            // Check if sample workspace already exists from previous session
            val workspaceDir = libraryRepo.workspaceDirectory()
            if (workspaceDir.exists() && (workspaceDir.listFiles()?.isNotEmpty() == true)) {
                libraryRepo.loadLocalDirectory(workspaceDir, "Sample Workspace").onSuccess { loaded ->
                    if (loaded.isNotEmpty()) {
                        _tracks.value = loaded
                        _currentSourceLabel.value = "Sample Workspace"
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        previewPlayer.stop()
    }

    // --- Library Loading ---

    fun openFolderTree(treeUri: Uri) {
        viewModelScope.launch {
            _isLoadingLibrary.value = true
            libraryRepo.loadFolderTree(treeUri, settings.value.scanSubfolders)
                .onSuccess { (label, loaded) ->
                    _tracks.value = loaded
                    _selectedIds.value = emptySet()
                    _currentSourceLabel.value = label
                    configRepo.recordFolderBookmark(treeUri.toString(), label)
                    rebuildDraft()
                    notify("Loaded ${loaded.size} audio file(s) from $label")
                }
                .onFailure { err ->
                    notify("Failed to open folder: ${err.message ?: "Unknown error"}")
                }
            _isLoadingLibrary.value = false
        }
    }

    fun pickAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isLoadingLibrary.value = true
            libraryRepo.loadPickedUris(uris)
                .onSuccess { loaded ->
                    _tracks.value = loaded
                    _selectedIds.value = loaded.map { it.id }.toSet()
                    _currentSourceLabel.value = "${loaded.size} Picked File(s)"
                    rebuildDraft()
                    notify("Loaded ${loaded.size} audio file(s)")
                }
                .onFailure { err ->
                    notify("Failed to load files: ${err.message}")
                }
            _isLoadingLibrary.value = false
        }
    }

    fun createSampleWorkspace() {
        viewModelScope.launch {
            _isLoadingLibrary.value = true
            libraryRepo.createAndLoadSampleWorkspace()
                .onSuccess { loaded ->
                    _tracks.value = loaded
                    _selectedIds.value = setOf(loaded.first().id)
                    _currentSourceLabel.value = "Sample Workspace"
                    rebuildDraft()
                    notify("Created ${loaded.size} playable sample tracks in workspace")
                }
                .onFailure { err ->
                    notify("Failed creating sample tracks: ${err.message}")
                }
            _isLoadingLibrary.value = false
        }
    }

    // --- Search, Filter, Sort, Selection ---

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setFilter(f: TrackFilter) {
        _filter.value = f
    }

    fun setSortOrder(order: TrackSortOrder) {
        _sortOrder.value = order
    }

    fun toggleTrackSelection(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        _selectedIds.value = current
        rebuildDraft()
    }

    fun selectSingleAndOpenEditor(id: String) {
        _selectedIds.value = setOf(id)
        rebuildDraft()
        _isTagEditorOpen.value = true
    }

    fun openBatchEditorForSelection() {
        if (_selectedIds.value.isEmpty() && visibleTracks.value.isNotEmpty()) {
            _selectedIds.value = visibleTracks.value.map { it.id }.toSet()
        }
        rebuildDraft()
        _isTagEditorOpen.value = true
    }

    fun closeTagEditor() {
        _isTagEditorOpen.value = false
    }

    fun selectAllVisible() {
        _selectedIds.value = visibleTracks.value.map { it.id }.toSet()
        rebuildDraft()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        rebuildDraft()
    }

    fun invertSelection() {
        val visibleIds = visibleTracks.value.map { it.id }.toSet()
        _selectedIds.value = visibleIds - _selectedIds.value
        rebuildDraft()
    }

    fun selectedOrAllTracks(): List<AudioTrackItem> {
        val sel = _selectedIds.value
        return if (sel.isEmpty()) visibleTracks.value else _tracks.value.filter { it.id in sel }
    }

    // --- Tag Editor Draft & Save ---

    private fun rebuildDraft() {
        val selected = _tracks.value.filter { it.id in _selectedIds.value }
        if (selected.isEmpty()) {
            _editorDraft.value = TagEditorDraft()
            return
        }

        fun field(extractor: (AudioTags) -> String): BatchFieldValue {
            val values = selected.map { extractor(it.tags) }.distinct()
            return if (values.size == 1) {
                BatchFieldValue(value = values.first(), isMixed = false, isTouched = false)
            } else {
                BatchFieldValue(value = "", isMixed = true, isTouched = false)
            }
        }

        val firstCover = selected.first().tags.coverArt
        val allSameCover = selected.all { it.tags.coverArt == firstCover }
        val coverState = if (allSameCover) CoverDraftState.Uniform(firstCover) else CoverDraftState.KeepMixed

        val commonCustom = if (selected.size == 1) selected.first().tags.customFields else emptyMap()

        _editorDraft.value = TagEditorDraft(
            trackCount = selected.size,
            title = field { it.title },
            artist = field { it.artist },
            album = field { it.album },
            albumArtist = field { it.albumArtist },
            year = field { it.year },
            track = field { it.track },
            discNumber = field { it.discNumber },
            genre = field { it.genre },
            comment = field { it.comment },
            composer = field { it.composer },
            publisher = field { it.publisher },
            copyright = field { it.copyright },
            bpm = field { it.bpm },
            lyrics = field { it.lyrics },
            customFields = commonCustom,
            coverState = coverState
        )
    }

    fun updateDraftField(field: TagFieldKey, newValue: String) {
        val d = _editorDraft.value
        val updatedField = BatchFieldValue(value = newValue, isMixed = false, isTouched = true)
        _editorDraft.value = when (field) {
            TagFieldKey.TITLE -> d.copy(title = updatedField)
            TagFieldKey.ARTIST -> d.copy(artist = updatedField)
            TagFieldKey.ALBUM -> d.copy(album = updatedField)
            TagFieldKey.ALBUM_ARTIST -> d.copy(albumArtist = updatedField)
            TagFieldKey.YEAR -> d.copy(year = updatedField)
            TagFieldKey.TRACK -> d.copy(track = updatedField)
            TagFieldKey.DISC -> d.copy(discNumber = updatedField)
            TagFieldKey.GENRE -> d.copy(genre = updatedField)
            TagFieldKey.COMMENT -> d.copy(comment = updatedField)
            TagFieldKey.COMPOSER -> d.copy(composer = updatedField)
            TagFieldKey.PUBLISHER -> d.copy(publisher = updatedField)
            TagFieldKey.COPYRIGHT -> d.copy(copyright = updatedField)
            TagFieldKey.BPM -> d.copy(bpm = updatedField)
            TagFieldKey.LYRICS -> d.copy(lyrics = updatedField)
            TagFieldKey.ALL -> d
        }
    }

    fun setCustomField(key: String, value: String) {
        val d = _editorDraft.value
        val map = d.customFields.toMutableMap()
        if (value.isBlank()) map.remove(key) else map[key.uppercase()] = value
        _editorDraft.value = d.copy(customFields = map)
    }

    fun pickCoverArtFromUri(uri: Uri) {
        viewModelScope.launch {
            libraryRepo.loadCoverArtFromUri(uri)
                .onSuccess { art ->
                    _editorDraft.value = _editorDraft.value.copy(coverState = CoverDraftState.Replaced(art))
                    notify("Loaded cover art (${art.width}x${art.height}, ${art.sizeKb} KB)")
                }
                .onFailure { notify("Failed to load image: ${it.message}") }
        }
    }

    fun removeCoverArtInDraft() {
        _editorDraft.value = _editorDraft.value.copy(coverState = CoverDraftState.Removed)
    }

    fun exportCurrentCoverToUri(uri: Uri) {
        val art = when (val c = _editorDraft.value.coverState) {
            is CoverDraftState.Replaced -> c.coverArt
            is CoverDraftState.Uniform -> c.coverArt
            else -> _tracks.value.firstOrNull { it.id in _selectedIds.value }?.tags?.coverArt
        } ?: run {
            notify("No cover art to export")
            return
        }
        viewModelScope.launch {
            libraryRepo.writeBytesDocument(uri, art.bytes)
                .onSuccess { notify("Exported cover art (${art.sizeKb} KB)") }
                .onFailure { notify("Export failed: ${it.message}") }
        }
    }

    fun saveEditorDraftToFiles() {
        val d = _editorDraft.value
        val targetIds = _selectedIds.value
        if (targetIds.isEmpty()) return

        viewModelScope.launch {
            _isSaving.value = true
            val currentSettings = settings.value
            var savedCount = 0
            var failCount = 0
            val updatedList = _tracks.value.toMutableList()

            for (i in updatedList.indices) {
                val track = updatedList[i]
                if (track.id !in targetIds) continue

                fun resolve(bf: BatchFieldValue, existing: String): String {
                    return if (!bf.isMixed || bf.isTouched) bf.value else existing
                }

                val newCover = when (val cs = d.coverState) {
                    is CoverDraftState.Replaced -> cs.coverArt
                    CoverDraftState.Removed -> null
                    else -> track.tags.coverArt
                }

                val newCustom = if (targetIds.size == 1) d.customFields else track.tags.customFields

                val newTags = track.tags.copy(
                    title = resolve(d.title, track.tags.title),
                    artist = resolve(d.artist, track.tags.artist),
                    album = resolve(d.album, track.tags.album),
                    albumArtist = resolve(d.albumArtist, track.tags.albumArtist),
                    year = resolve(d.year, track.tags.year),
                    track = resolve(d.track, track.tags.track),
                    discNumber = resolve(d.discNumber, track.tags.discNumber),
                    genre = resolve(d.genre, track.tags.genre),
                    comment = resolve(d.comment, track.tags.comment),
                    composer = resolve(d.composer, track.tags.composer),
                    publisher = resolve(d.publisher, track.tags.publisher),
                    copyright = resolve(d.copyright, track.tags.copyright),
                    bpm = resolve(d.bpm, track.tags.bpm),
                    lyrics = resolve(d.lyrics, track.tags.lyrics),
                    customFields = newCustom,
                    coverArt = newCover
                )

                val candidate = track.copy(tags = newTags)
                libraryRepo.saveTrack(candidate, currentSettings.id3v2Version, currentSettings.writeId3v1)
                    .onSuccess { reloaded ->
                        updatedList[i] = reloaded
                        savedCount++
                    }
                    .onFailure {
                        failCount++
                    }
            }

            _tracks.value = updatedList
            rebuildDraft()
            _isSaving.value = false
            if (failCount == 0) {
                notify("Saved tags to $savedCount file(s)")
            } else {
                notify("Saved $savedCount file(s), $failCount failed (check write permissions)")
            }
        }
    }

    fun revertSelectedDraft() {
        rebuildDraft()
        notify("Reverted editor changes")
    }

    fun stripTagsFromSelected() {
        val targetIds = _selectedIds.value
        if (targetIds.isEmpty()) return
        viewModelScope.launch {
            _isSaving.value = true
            var count = 0
            val updatedList = _tracks.value.toMutableList()
            for (i in updatedList.indices) {
                val track = updatedList[i]
                if (track.id !in targetIds) continue
                libraryRepo.stripTrackTags(track).onSuccess { reloaded ->
                    updatedList[i] = reloaded
                    count++
                }
            }
            _tracks.value = updatedList
            rebuildDraft()
            _isSaving.value = false
            notify("Stripped all tags from $count file(s)")
        }
    }

    // --- Converters ---

    fun previewTagToFilename(pattern: String): List<RenamePreviewItem> {
        val targets = selectedOrAllTracks()
        val seenNames = mutableSetOf<String>()
        return targets.map { t ->
            val newName = FormatStringEngine.evaluateFilenameRename(pattern, t)
            val conflict = !seenNames.add(newName.lowercase())
            RenamePreviewItem(
                trackId = t.id,
                oldFilename = t.fileName,
                newFilename = newName,
                hasChanged = newName != t.fileName,
                conflict = conflict
            )
        }
    }

    fun applyTagToFilename(pattern: String) {
        val previews = previewTagToFilename(pattern).filter { it.hasChanged && !it.conflict }
        if (previews.isEmpty()) {
            notify("No filenames to rename")
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val list = _tracks.value.toMutableList()
            val selectedMutable = _selectedIds.value.toMutableSet()
            var renamed = 0
            previews.forEach { item ->
                val idx = list.indexOfFirst { it.id == item.trackId }
                if (idx >= 0) {
                    val oldTrack = list[idx]
                    libraryRepo.renameTrackFile(oldTrack, item.newFilename).onSuccess { updated ->
                        list[idx] = updated
                        if (selectedMutable.remove(oldTrack.id)) {
                            selectedMutable.add(updated.id)
                        }
                        renamed++
                    }
                }
            }
            _tracks.value = list
            _selectedIds.value = selectedMutable
            rebuildDraft()
            _isSaving.value = false
            notify("Renamed $renamed file(s)")
        }
    }

    fun previewFilenameToTag(pattern: String): List<FilenameToTagPreviewItem> {
        return selectedOrAllTracks().map { track ->
            val extracted = FormatStringEngine.parseFilenameToTags(track.baseName, pattern)
            FilenameToTagPreviewItem(
                trackId = track.id,
                filename = track.fileName,
                matched = extracted != null && extracted.isNotEmpty(),
                extractedFields = extracted ?: emptyMap()
            )
        }
    }

    fun applyFilenameToTag(pattern: String) {
        val previews = previewFilenameToTag(pattern).filter { it.matched }
        if (previews.isEmpty()) {
            notify("Pattern did not match any filenames")
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val s = settings.value
            val list = _tracks.value.toMutableList()
            var updatedCount = 0
            previews.forEach { item ->
                val idx = list.indexOfFirst { it.id == item.trackId }
                if (idx >= 0) {
                    val track = list[idx]
                    val newTags = FormatStringEngine.applyExtractedFields(track.tags, item.extractedFields)
                    libraryRepo.saveTrack(track.copy(tags = newTags), s.id3v2Version, s.writeId3v1)
                        .onSuccess { saved ->
                            list[idx] = saved
                            updatedCount++
                        }
                }
            }
            _tracks.value = list
            rebuildDraft()
            _isSaving.value = false
            notify("Extracted tags into $updatedCount file(s)")
        }
    }

    fun previewTagToTag(targetField: TagFieldKey, formatPattern: String): List<TagToTagPreviewItem> {
        return selectedOrAllTracks().map { track ->
            val oldVal = FormatStringEngine.getField(track.tags, targetField)
            val newVal = FormatStringEngine.evaluate(formatPattern, track)
            TagToTagPreviewItem(track.id, track.fileName, oldVal, newVal)
        }
    }

    fun applyTagToTag(targetField: TagFieldKey, formatPattern: String) {
        val targets = selectedOrAllTracks()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _isSaving.value = true
            val s = settings.value
            val list = _tracks.value.toMutableList()
            var count = 0
            targets.forEach { t ->
                val idx = list.indexOfFirst { it.id == t.id }
                if (idx >= 0) {
                    val newValue = FormatStringEngine.evaluate(formatPattern, t)
                    val newTags = FormatStringEngine.setField(t.tags, targetField, newValue)
                    libraryRepo.saveTrack(t.copy(tags = newTags), s.id3v2Version, s.writeId3v1)
                        .onSuccess { saved ->
                            list[idx] = saved
                            count++
                        }
                }
            }
            _tracks.value = list
            rebuildDraft()
            _isSaving.value = false
            notify("Formatted ${targetField.label} on $count file(s)")
        }
    }

    fun previewAutoNumber(config: AutoNumberConfig): List<AutoNumberPreviewItem> {
        val targets = selectedOrAllTracks()
        if (targets.isEmpty()) return emptyList()

        val albumCounts = targets.groupBy { it.tags.album.lowercase() }.mapValues { it.value.size }
        val albumCounters = mutableMapOf<String, Int>()
        var globalCounter = config.startNumber

        return targets.map { t ->
            val num = if (config.resetPerAlbum) {
                val key = t.tags.album.lowercase()
                val next = albumCounters[key] ?: config.startNumber
                albumCounters[key] = next + 1
                next
            } else {
                globalCounter++
            }
            val total = if (config.resetPerAlbum) {
                albumCounts[t.tags.album.lowercase()] ?: targets.size
            } else {
                targets.size
            }
            val numStr = if (config.zeroPadDigits > 1) num.toString().padStart(config.zeroPadDigits, '0') else num.toString()
            val totStr = if (config.zeroPadDigits > 1) total.toString().padStart(config.zeroPadDigits, '0') else total.toString()
            val newTrack = if (config.includeTotalTracks) "$numStr/$totStr" else numStr
            AutoNumberPreviewItem(t.id, t.fileName, t.tags.track, newTrack, config.discNumber)
        }
    }

    fun applyAutoNumber(config: AutoNumberConfig) {
        val previews = previewAutoNumber(config)
        if (previews.isEmpty()) return
        viewModelScope.launch {
            _isSaving.value = true
            val s = settings.value
            val list = _tracks.value.toMutableList()
            var count = 0
            previews.forEach { p ->
                val idx = list.indexOfFirst { it.id == p.trackId }
                if (idx >= 0) {
                    val t = list[idx]
                    val newTags = t.tags.copy(
                        track = p.newTrack,
                        discNumber = if (p.newDisc.isNotBlank()) p.newDisc else t.tags.discNumber
                    )
                    libraryRepo.saveTrack(t.copy(tags = newTags), s.id3v2Version, s.writeId3v1)
                        .onSuccess { saved ->
                            list[idx] = saved
                            count++
                        }
                }
            }
            _tracks.value = list
            rebuildDraft()
            _isSaving.value = false
            notify("Auto-numbered $count file(s)")
        }
    }

    fun exportM3u8Playlist(uri: Uri) {
        val targets = selectedOrAllTracks()
        if (targets.isEmpty()) {
            notify("No tracks to export")
            return
        }
        viewModelScope.launch {
            val content = libraryRepo.buildM3u8Playlist(targets)
            libraryRepo.writeTextDocument(uri, content)
                .onSuccess { notify("Exported M3U8 playlist (${targets.size} tracks)") }
                .onFailure { notify("Export failed: ${it.message}") }
        }
    }

    fun exportCsvMetadata(uri: Uri) {
        val targets = selectedOrAllTracks()
        if (targets.isEmpty()) {
            notify("No tracks to export")
            return
        }
        viewModelScope.launch {
            val content = libraryRepo.buildCsvExport(targets)
            libraryRepo.writeTextDocument(uri, content)
                .onSuccess { notify("Exported CSV metadata (${targets.size} tracks)") }
                .onFailure { notify("Export failed: ${it.message}") }
        }
    }

    fun importTagsFromTextFile(uri: Uri, pattern: String) {
        val targets = selectedOrAllTracks()
        if (targets.isEmpty()) {
            notify("Load and select tracks first")
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            libraryRepo.readTextDocument(uri).onSuccess { lines ->
                val nonEmptyLines = lines.filter { it.isNotBlank() && !it.startsWith("Filename;") }
                val s = settings.value
                val list = _tracks.value.toMutableList()
                var count = 0
                targets.forEachIndexed { i, track ->
                    val line = nonEmptyLines.getOrNull(i) ?: return@forEachIndexed
                    val extracted = FormatStringEngine.parseFilenameToTags(line.trim(), pattern) ?: return@forEachIndexed
                    val newTags = FormatStringEngine.applyExtractedFields(track.tags, extracted)
                    val idx = list.indexOfFirst { it.id == track.id }
                    if (idx >= 0) {
                        libraryRepo.saveTrack(track.copy(tags = newTags), s.id3v2Version, s.writeId3v1)
                            .onSuccess { saved ->
                                list[idx] = saved
                                count++
                            }
                    }
                }
                _tracks.value = list
                rebuildDraft()
                notify("Imported text tags into $count file(s)")
            }.onFailure {
                notify("Import failed: ${it.message}")
            }
            _isSaving.value = false
        }
    }

    fun saveFormatPreset(mode: ConverterMode, label: String, pattern: String) {
        viewModelScope.launch {
            configRepo.saveFormatPreset(mode, label, pattern)
            notify("Saved preset \"$label\"")
        }
    }

    // --- Actions & Action Groups ---

    fun previewActionSteps(steps: List<ActionStep>): List<ActionPreviewItem> {
        return ActionRunner.previewSteps(selectedOrAllTracks(), steps)
    }

    fun executeActionSteps(steps: List<ActionStep>, label: String) {
        val targets = selectedOrAllTracks()
        if (targets.isEmpty()) {
            notify("No tracks loaded")
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val s = settings.value
            val list = _tracks.value.toMutableList()
            var count = 0
            targets.forEach { track ->
                val newTags = ActionRunner.applySteps(track, steps)
                if (newTags != track.tags) {
                    val idx = list.indexOfFirst { it.id == track.id }
                    if (idx >= 0) {
                        libraryRepo.saveTrack(track.copy(tags = newTags), s.id3v2Version, s.writeId3v1)
                            .onSuccess { saved ->
                                list[idx] = saved
                                count++
                            }
                    }
                }
            }
            _tracks.value = list
            rebuildDraft()
            _isSaving.value = false
            notify("Applied \"$label\" to $count file(s)")
        }
    }

    fun saveCustomActionGroup(name: String, description: String, steps: List<ActionStep>) {
        viewModelScope.launch {
            configRepo.saveActionGroup(name, description, steps)
            notify("Saved Action Group \"$name\"")
        }
    }

    fun deleteActionGroup(id: Long) {
        viewModelScope.launch {
            configRepo.deleteActionGroup(id)
            notify("Deleted Action Group")
        }
    }

    // --- Online Tag Sources ---

    fun prepareLookupFromSelection() {
        if (_lookupState.value.query.isBlank()) {
            val first = selectedOrAllTracks().firstOrNull()
            if (first != null) {
                val q = listOf(first.tags.artist, first.tags.album.ifBlank { first.tags.title.ifBlank { first.baseName } })
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                _lookupState.value = _lookupState.value.copy(
                    query = q,
                    provider = settings.value.defaultTagSource
                )
            }
        }
    }

    fun setLookupProvider(provider: TagSourceProvider) {
        _lookupState.value = _lookupState.value.copy(provider = provider)
    }

    fun setLookupQuery(query: String) {
        _lookupState.value = _lookupState.value.copy(query = query)
    }

    fun searchOnlineReleases() {
        val state = _lookupState.value
        if (state.query.isBlank()) return
        viewModelScope.launch {
            _lookupState.value = state.copy(isSearching = true, errorMessage = null, selectedRelease = null)
            lookupRepo.searchReleases(state.query, state.provider)
                .onSuccess { results ->
                    _lookupState.value = _lookupState.value.copy(
                        isSearching = false,
                        results = results,
                        errorMessage = if (results.isEmpty()) "No matching releases found" else null
                    )
                }
                .onFailure { err ->
                    _lookupState.value = _lookupState.value.copy(
                        isSearching = false,
                        errorMessage = err.message ?: "Network error"
                    )
                }
        }
    }

    fun selectOnlineRelease(release: OnlineReleaseResult) {
        viewModelScope.launch {
            _lookupState.value = _lookupState.value.copy(
                selectedRelease = release,
                isLoadingDetails = true,
                selectedCoverPreview = null
            )
            lookupRepo.loadReleaseDetails(release)
                .onSuccess { detailed ->
                    _lookupState.value = _lookupState.value.copy(
                        selectedRelease = detailed,
                        isLoadingDetails = false
                    )
                    detailed.coverArtUrl?.let { url ->
                        lookupRepo.downloadCoverArt(url).onSuccess { art ->
                            _lookupState.value = _lookupState.value.copy(selectedCoverPreview = art)
                        }
                    }
                }
                .onFailure { err ->
                    _lookupState.value = _lookupState.value.copy(
                        isLoadingDetails = false,
                        errorMessage = err.message
                    )
                }
        }
    }

    fun clearSelectedRelease() {
        _lookupState.value = _lookupState.value.copy(selectedRelease = null, selectedCoverPreview = null)
    }

    fun applyOnlineReleaseToSelectedTracks(embedCover: Boolean) {
        val release = _lookupState.value.selectedRelease ?: return
        val targets = selectedOrAllTracks()
        if (targets.isEmpty()) {
            notify("Load or select tracks first")
            return
        }
        viewModelScope.launch {
            _lookupState.value = _lookupState.value.copy(isApplying = true)
            val coverArt = if (embedCover) _lookupState.value.selectedCoverPreview else null
            val s = settings.value
            val list = _tracks.value.toMutableList()
            var updatedCount = 0

            targets.forEachIndexed { idx, track ->
                val matchedOnline = release.tracks.getOrNull(idx)
                val newTags = track.tags.copy(
                    album = release.albumTitle.ifBlank { track.tags.album },
                    albumArtist = release.artist.ifBlank { track.tags.albumArtist },
                    year = release.year.ifBlank { track.tags.year },
                    genre = release.genre.ifBlank { track.tags.genre },
                    artist = matchedOnline?.artist?.ifBlank { release.artist } ?: release.artist.ifBlank { track.tags.artist },
                    title = matchedOnline?.title?.ifBlank { track.tags.title } ?: track.tags.title,
                    track = matchedOnline?.let {
                        val total = release.tracks.size.coerceAtLeast(release.trackCount)
                        if (total > 0) "${it.trackNumber}/$total" else it.trackNumber.toString()
                    } ?: track.tags.track,
                    discNumber = matchedOnline?.discNumber?.toString() ?: track.tags.discNumber,
                    coverArt = coverArt ?: track.tags.coverArt
                )
                val listIdx = list.indexOfFirst { it.id == track.id }
                if (listIdx >= 0) {
                    libraryRepo.saveTrack(track.copy(tags = newTags), s.id3v2Version, s.writeId3v1)
                        .onSuccess { saved ->
                            list[listIdx] = saved
                            updatedCount++
                        }
                }
            }
            _tracks.value = list
            rebuildDraft()
            _lookupState.value = _lookupState.value.copy(isApplying = false)
            notify("Applied \"${release.albumTitle}\" tags to $updatedCount file(s)")
        }
    }

    // --- Playback ---

    fun togglePlayTrack(track: AudioTrackItem) {
        previewPlayer.togglePlay(track)
    }

    fun seekPlayback(positionMs: Long) {
        previewPlayer.seekTo(positionMs)
    }

    fun stopPlayback() {
        previewPlayer.stop()
    }

    // --- Settings & Bookmarks ---

    fun setId3v2Version(version: Int) {
        viewModelScope.launch { settingsStore.setId3v2Version(version) }
    }

    fun setWriteId3v1(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setWriteId3v1(enabled) }
    }

    fun setScanSubfolders(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setScanSubfolders(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setDefaultTagSource(provider: TagSourceProvider) {
        viewModelScope.launch { settingsStore.setDefaultTagSource(provider) }
    }

    fun setAutoEmbedCover(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoEmbedCover(enabled) }
    }

    fun removeFolderBookmark(uriString: String) {
        viewModelScope.launch { configRepo.removeFolderBookmark(uriString) }
    }

    private fun notify(msg: String) {
        _snackbarMessages.tryEmit(msg)
    }
}
