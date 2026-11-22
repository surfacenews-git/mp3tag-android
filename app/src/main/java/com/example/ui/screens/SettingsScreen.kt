package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AppSettings
import com.example.data.local.FolderBookmarkEntity
import com.example.model.TagSourceProvider
import com.example.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
    bookmarks: List<FolderBookmarkEntity>,
    onId3VersionChange: (Int) -> Unit,
    onWriteId3v1Change: (Boolean) -> Unit,
    onScanSubfoldersChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDefaultTagSourceChange: (TagSourceProvider) -> Unit,
    onAutoEmbedCoverChange: (Boolean) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            item {
                Text("Tagging & Studio Settings", style = MaterialTheme.typography.headlineSmall)
            }

            // ID3 Tag Format Card
            item {
                SettingsCard(title = "MP3 ID3 Tag Encoding") {
                    Text(
                        text = "ID3v2.3 UTF-16 offers the widest hardware/car stereo compatibility. ID3v2.4 uses UTF-8.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.id3v2Version == 3,
                            onClick = { onId3VersionChange(3) },
                            label = { Text("ID3v2.3 (UTF-16)") },
                            modifier = Modifier.testTag("setting_id3_v23")
                        )
                        FilterChip(
                            selected = settings.id3v2Version == 4,
                            onClick = { onId3VersionChange(4) },
                            label = { Text("ID3v2.4 (UTF-8)") },
                            modifier = Modifier.testTag("setting_id3_v24")
                        )
                    }
                    SettingSwitchRow(
                        title = "Write ID3v1.1 compatibility tag",
                        subtitle = "Appends legacy 128-byte ID3v1 block at file end",
                        checked = settings.writeId3v1,
                        onCheckedChange = onWriteId3v1Change
                    )
                }
            }

            // Library & Lookup Card
            item {
                SettingsCard(title = "Library & Online Tag Sources") {
                    SettingSwitchRow(
                        title = "Scan subfolders recursively",
                        subtitle = "Include nested album directories when opening a folder",
                        checked = settings.scanSubfolders,
                        onCheckedChange = onScanSubfoldersChange
                    )
                    SettingSwitchRow(
                        title = "Auto-embed online cover art",
                        subtitle = "Embed downloaded artwork when applying MusicBrainz / iTunes tags",
                        checked = settings.autoEmbedFetchedCover,
                        onCheckedChange = onAutoEmbedCoverChange
                    )
                    Text("Default Lookup Source", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagSourceProvider.entries.forEach { p ->
                            FilterChip(
                                selected = settings.defaultTagSource == p,
                                onClick = { onDefaultTagSourceChange(p) },
                                label = { Text(p.label) }
                            )
                        }
                    }
                }
            }

            // Appearance Card
            item {
                SettingsCard(title = "Appearance") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Saved Bookmarks
            if (bookmarks.isNotEmpty()) {
                item {
                    Text("Recent SAF Folder Bookmarks", style = MaterialTheme.typography.titleMedium)
                }
                items(bookmarks, key = { it.uriString }) { bm ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bm.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(bm.uriString, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            IconButton(
                                onClick = { onDeleteBookmark(bm.uriString) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove bookmark")
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard(title = "Supported Audio Formats") {
                    Text(
                        text = "• MP3 (ID3v2.2 / ID3v2.3 / ID3v2.4 / ID3v1.1 + APIC)\n" +
                            "• FLAC (Vorbis Comments + METADATA_BLOCK_PICTURE)\n" +
                            "• M4A / MP4 / AAC (iTunes ilst atoms + covr + stco adjustment)\n" +
                            "• OGG Vorbis (Vorbis Comment pages + CRC32)\n" +
                            "• WAV (RIFF LIST/INFO + embedded ID3v2 chunk)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
