package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.FormatPresetEntity
import com.example.model.AudioTrackItem
import com.example.model.AutoNumberConfig
import com.example.model.AutoNumberPreviewItem
import com.example.model.ConverterMode
import com.example.model.FilenameToTagPreviewItem
import com.example.model.RenamePreviewItem
import com.example.model.TagFieldKey
import com.example.model.TagToTagPreviewItem
import com.example.ui.components.FormatPlaceholderChips

@Composable
fun ConvertScreen(
    targetTracks: List<AudioTrackItem>,
    presets: List<FormatPresetEntity>,
    isSaving: Boolean,
    previewTagToFilename: (String) -> List<RenamePreviewItem>,
    onApplyTagToFilename: (String) -> Unit,
    previewFilenameToTag: (String) -> List<FilenameToTagPreviewItem>,
    onApplyFilenameToTag: (String) -> Unit,
    previewTagToTag: (TagFieldKey, String) -> List<TagToTagPreviewItem>,
    onApplyTagToTag: (TagFieldKey, String) -> Unit,
    previewAutoNumber: (AutoNumberConfig) -> List<AutoNumberPreviewItem>,
    onApplyAutoNumber: (AutoNumberConfig) -> Unit,
    onExportM3u8: (Uri) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onImportTextTags: (Uri, String) -> Unit,
    onSavePreset: (ConverterMode, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(ConverterMode.TAG_TO_FILENAME) }
    var tagToFilePattern by remember { mutableStateOf("\$num(%track%,2) - %artist% - %title%") }
    var fileToTagPattern by remember { mutableStateOf("%track% - %artist% - %title%") }
    var tagToTagField by remember { mutableStateOf(TagFieldKey.ALBUM_ARTIST) }
    var tagToTagPattern by remember { mutableStateOf("%artist%") }
    var autoNumStart by remember { mutableIntStateOf(1) }
    var autoNumPad by remember { mutableIntStateOf(2) }
    var autoNumTotal by remember { mutableStateOf(true) }
    var autoNumResetAlbum by remember { mutableStateOf(false) }
    var autoNumDisc by remember { mutableStateOf("") }
    var textImportPattern by remember { mutableStateOf("%artist%;%title%;%album%;%track%") }

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetLabel by remember { mutableStateOf("") }

    val m3uLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri -> uri?.let(onExportM3u8) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExportCsv) }

    val textImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onImportTextTags(it, textImportPattern) } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            // Header & Mode Chips
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Converters", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                text = "Operating on ${targetTracks.size} track(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConverterMode.entries.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { mode = m },
                                label = { Text(m.title) },
                                modifier = Modifier.testTag("converter_mode_${m.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (targetTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Load audio files in the Files tab to use converters.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (mode) {
                    ConverterMode.TAG_TO_FILENAME -> {
                        item {
                            PatternConfigCard(
                                title = "Format String for Filename",
                                pattern = tagToFilePattern,
                                onPatternChange = { tagToFilePattern = it },
                                tokens = listOf("\$num(%track%,2)", "%artist%", "%title%", "%album%", "%year%", "%genre%"),
                                presets = presets.filter { it.modeName == mode.name },
                                onSavePresetClick = { showSavePresetDialog = true },
                                actionLabel = "Rename Files",
                                isSaving = isSaving,
                                onApply = { onApplyTagToFilename(tagToFilePattern) },
                                testTagPrefix = "t2f"
                            )
                        }
                        val previews = previewTagToFilename(tagToFilePattern)
                        items(previews, key = { it.trackId }) { item ->
                            PreviewDiffCard(
                                title = item.oldFilename,
                                before = item.oldFilename,
                                after = item.newFilename,
                                highlight = item.hasChanged && !item.conflict,
                                warning = if (item.conflict) "Duplicate filename conflict" else null
                            )
                        }
                    }

                    ConverterMode.FILENAME_TO_TAG -> {
                        item {
                            PatternConfigCard(
                                title = "Pattern to Parse Filenames",
                                pattern = fileToTagPattern,
                                onPatternChange = { fileToTagPattern = it },
                                tokens = listOf("%track%", "%artist%", "%title%", "%album%", "%year%", "%dummy%"),
                                presets = presets.filter { it.modeName == mode.name },
                                onSavePresetClick = { showSavePresetDialog = true },
                                actionLabel = "Extract Tags",
                                isSaving = isSaving,
                                onApply = { onApplyFilenameToTag(fileToTagPattern) },
                                testTagPrefix = "f2t"
                            )
                        }
                        val previews = previewFilenameToTag(fileToTagPattern)
                        items(previews, key = { it.trackId }) { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (item.matched) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = item.filename,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!item.matched) {
                                        Text(
                                            text = "No match for pattern",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Text(
                                            text = item.extractedFields.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ConverterMode.TAG_TO_TAG -> {
                        item {
                            var showFieldMenu by remember { mutableStateOf(false) }
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Target Tag Field", style = MaterialTheme.typography.titleSmall)
                                        Box {
                                            FilledTonalButton(onClick = { showFieldMenu = true }) {
                                                Text(tagToTagField.label)
                                            }
                                            DropdownMenu(expanded = showFieldMenu, onDismissRequest = { showFieldMenu = false }) {
                                                TagFieldKey.entries.filter { it != TagFieldKey.ALL }.forEach { f ->
                                                    DropdownMenuItem(
                                                        text = { Text(f.label) },
                                                        onClick = {
                                                            tagToTagField = f
                                                            showFieldMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    PatternConfigCard(
                                        title = "Format Value Expression",
                                        pattern = tagToTagPattern,
                                        onPatternChange = { tagToTagPattern = it },
                                        tokens = listOf("%artist%", "%title%", "%album%", "\$caps(%title%)", "\$upper(%genre%)", "%year%"),
                                        presets = presets.filter { it.modeName == mode.name },
                                        onSavePresetClick = { showSavePresetDialog = true },
                                        actionLabel = "Apply to ${tagToTagField.label}",
                                        isSaving = isSaving,
                                        onApply = { onApplyTagToTag(tagToTagField, tagToTagPattern) },
                                        testTagPrefix = "t2t"
                                    )
                                }
                            }
                        }
                        val previews = previewTagToTag(tagToTagField, tagToTagPattern)
                        items(previews, key = { it.trackId }) { item ->
                            PreviewDiffCard(
                                title = item.filename,
                                before = item.oldValue.ifBlank { "(empty)" },
                                after = item.newValue.ifBlank { "(empty)" },
                                highlight = item.oldValue != item.newValue
                            )
                        }
                    }

                    ConverterMode.AUTO_NUMBER -> {
                        val config = AutoNumberConfig(
                            startNumber = autoNumStart,
                            zeroPadDigits = autoNumPad,
                            includeTotalTracks = autoNumTotal,
                            resetPerAlbum = autoNumResetAlbum,
                            discNumber = autoNumDisc
                        )
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Auto-Numbering Wizard", style = MaterialTheme.typography.titleMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = autoNumStart.toString(),
                                            onValueChange = { autoNumStart = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                                            label = { Text("Begin at") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = autoNumPad.toString(),
                                            onValueChange = { autoNumPad = it.toIntOrNull()?.coerceIn(1, 4) ?: 2 },
                                            label = { Text("Pad digits") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = autoNumDisc,
                                            onValueChange = { autoNumDisc = it },
                                            label = { Text("Disc (opt)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Save total count (e.g. 01/12)", style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = autoNumTotal, onCheckedChange = { autoNumTotal = it })
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Reset counter for each album", style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = autoNumResetAlbum, onCheckedChange = { autoNumResetAlbum = it })
                                    }
                                    Button(
                                        onClick = { onApplyAutoNumber(config) },
                                        enabled = !isSaving,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("apply_autonumber_button")
                                    ) {
                                        Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Apply Track Numbers")
                                    }
                                }
                            }
                        }
                        val previews = previewAutoNumber(config)
                        items(previews, key = { it.trackId }) { item ->
                            PreviewDiffCard(
                                title = item.filename,
                                before = item.oldTrack.ifBlank { "(none)" },
                                after = item.newTrack,
                                highlight = item.oldTrack != item.newTrack
                            )
                        }
                    }

                    ConverterMode.EXPORT_IMPORT -> {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Export Playlists & Metadata", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Generate M3U8 playlists or semicolon-delimited CSV tag sheets for ${targetTracks.size} selected track(s).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = { m3uLauncher.launch("playlist.m3u8") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("export_m3u8_button")
                                        ) {
                                            Icon(Icons.Default.PlaylistAddCheck, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Export M3U8")
                                        }
                                        FilledTonalButton(
                                            onClick = { csvExportLauncher.launch("mp3tag_export.csv") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("export_csv_button")
                                        ) {
                                            Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Export CSV")
                                        }
                                    }
                                    HorizontalDivider()
                                    Text("Text File → Tags Import", style = MaterialTheme.typography.titleMedium)
                                    OutlinedTextField(
                                        value = textImportPattern,
                                        onValueChange = { textImportPattern = it },
                                        label = { Text("Line Format Pattern") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedButton(
                                        onClick = { textImportLauncher.launch(arrayOf("text/*")) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("import_text_tags_button")
                                    ) {
                                        Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Pick Text / CSV File to Import")
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showSavePresetDialog) {
        val currentPattern = when (mode) {
            ConverterMode.TAG_TO_FILENAME -> tagToFilePattern
            ConverterMode.FILENAME_TO_TAG -> fileToTagPattern
            ConverterMode.TAG_TO_TAG -> tagToTagPattern
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Format Preset") },
            text = {
                OutlinedTextField(
                    value = presetLabel,
                    onValueChange = { presetLabel = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetLabel.isNotBlank() && currentPattern.isNotBlank()) {
                            onSavePreset(mode, presetLabel.trim(), currentPattern)
                            presetLabel = ""
                            showSavePresetDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PatternConfigCard(
    title: String,
    pattern: String,
    onPatternChange: (String) -> Unit,
    tokens: List<String>,
    presets: List<FormatPresetEntity>,
    onSavePresetClick: () -> Unit,
    actionLabel: String,
    isSaving: Boolean,
    onApply: () -> Unit,
    testTagPrefix: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                IconButton(
                    onClick = onSavePresetClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = "Save format preset")
                }
            }
            OutlinedTextField(
                value = pattern,
                onValueChange = onPatternChange,
                textStyle = MaterialTheme.typography.labelMedium,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_pattern_input")
            )
            FormatPlaceholderChips(
                tokens = tokens,
                onInsertToken = { tok ->
                    val sep = if (pattern.isEmpty() || pattern.endsWith(" ") || pattern.endsWith("-")) "" else " "
                    onPatternChange("$pattern$sep$tok")
                }
            )
            if (presets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        AssistChip(
                            onClick = { onPatternChange(preset.pattern) },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            Button(
                onClick = onApply,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_apply_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun PreviewDiffCard(
    title: String,
    before: String,
    after: String,
    highlight: Boolean,
    warning: String? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (warning != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = before,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = after,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        warning != null -> MaterialTheme.colorScheme.error
                        highlight -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (warning != null) {
                Text(text = warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
