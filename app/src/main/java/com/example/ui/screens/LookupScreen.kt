package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AudioTrackItem
import com.example.model.OnlineReleaseResult
import com.example.model.TagSourceProvider
import com.example.ui.components.CoverArtThumbnail
import com.example.viewmodel.LookupUiState

@Composable
fun LookupScreen(
    state: LookupUiState,
    targetTracks: List<AudioTrackItem>,
    defaultEmbedCover: Boolean,
    onPrepare: () -> Unit,
    onProviderChange: (TagSourceProvider) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectRelease: (OnlineReleaseResult) -> Unit,
    onClearRelease: () -> Unit,
    onApplyRelease: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var embedCover by remember(defaultEmbedCover) { mutableStateOf(defaultEmbedCover) }

    LaunchedEffect(Unit) {
        onPrepare()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            // Search Header
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Online Tag Sources", style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagSourceProvider.entries.forEach { p ->
                            FilterChip(
                                selected = state.provider == p,
                                onClick = { onProviderChange(p) },
                                label = { Text(p.label) },
                                modifier = Modifier.testTag("provider_${p.name.lowercase()}")
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Artist, album, or track title...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lookup_query_input")
                        )
                        Button(
                            onClick = onSearch,
                            enabled = state.query.isNotBlank() && !state.isSearching,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("lookup_search_button")
                        ) {
                            if (state.isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Search releases")
                            }
                        }
                    }
                }
            }

            val selected = state.selectedRelease
            if (selected != null) {
                // Release Detail + Track Matching View
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        IconButton(
                            onClick = onClearRelease,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to search results")
                        }
                        if (state.selectedCoverPreview != null) {
                            CoverArtThumbnail(coverArt = state.selectedCoverPreview, size = 56.dp)
                        } else {
                            AsyncImage(
                                model = selected.coverArtUrl,
                                contentDescription = "Album cover preview",
                                placeholder = painterResource(R.drawable.img_default_cover),
                                error = painterResource(R.drawable.img_default_cover),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selected.albumTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = listOf(selected.artist, selected.year, selected.genre).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Embed downloaded cover art", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = embedCover, onCheckedChange = { embedCover = it })
                    }

                    if (state.isLoadingDetails) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                Text(
                                    text = "Track Mapping (${targetTracks.size} local file(s) ↔ ${selected.tracks.size} online track(s))",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(selected.tracks) { onlineTrack ->
                                val idx = selected.tracks.indexOf(onlineTrack)
                                val localTarget = targetTracks.getOrNull(idx)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Text(
                                            text = onlineTrack.trackNumber.toString().padStart(2, '0'),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(onlineTrack.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = localTarget?.let { "→ ${it.fileName}" } ?: "No local track at position ${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (localTarget != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Button(
                        onClick = { onApplyRelease(embedCover) },
                        enabled = targetTracks.isNotEmpty() && !state.isApplying && !state.isLoadingDetails,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("apply_online_tags_button")
                    ) {
                        if (state.isApplying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Apply Tags to ${targetTracks.size} Track(s)")
                    }
                }
            } else {
                // Search Results List
                when {
                    state.errorMessage != null -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(state.errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    state.results.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(52.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("Search MusicBrainz or iTunes", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Fetch album metadata, tracklists, release dates, and high-res cover art directly into your audio files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.results, key = { "${it.provider}_${it.id}" }) { release ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSelectRelease(release) }
                                        .testTag("release_card_${release.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        AsyncImage(
                                            model = release.coverArtUrl,
                                            contentDescription = "Release cover art",
                                            placeholder = painterResource(R.drawable.img_default_cover),
                                            error = painterResource(R.drawable.img_default_cover),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(release.albumTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(release.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val meta = listOfNotNull(
                                                release.year.takeIf { it.isNotBlank() },
                                                release.country.takeIf { it.isNotBlank() },
                                                release.trackCount.takeIf { it > 0 }?.let { "$it tracks" },
                                                release.genre.takeIf { it.isNotBlank() }
                                            ).joinToString(" · ")
                                            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(72.dp)) }
                        }
                    }
                }
            }
        }
    }
}
