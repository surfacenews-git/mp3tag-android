package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.MiniAudioPlayerBar
import com.example.ui.screens.ActionsScreen
import com.example.ui.screens.ConvertScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.LookupScreen
import com.example.ui.screens.SettingsScreen
import com.example.viewmodel.Mp3tagViewModel

object AppRoutes {
    const val ROUTE_FILES = "files"
    const val ROUTE_CONVERT = "convert"
    const val ROUTE_ACTIONS = "actions"
    const val ROUTE_LOOKUP = "lookup"
    const val ROUTE_SETTINGS = "settings"
}

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NAV_ITEMS = listOf(
    NavDestinationItem(AppRoutes.ROUTE_FILES, R.string.nav_files, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, "nav_files"),
    NavDestinationItem(AppRoutes.ROUTE_CONVERT, R.string.nav_convert, Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz, "nav_convert"),
    NavDestinationItem(AppRoutes.ROUTE_ACTIONS, R.string.nav_actions, Icons.Filled.AutoFixHigh, Icons.Outlined.AutoFixHigh, "nav_actions"),
    NavDestinationItem(AppRoutes.ROUTE_LOOKUP, R.string.nav_lookup, Icons.Filled.TravelExplore, Icons.Outlined.TravelExplore, "nav_lookup")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Mp3tagApp(
    viewModel: Mp3tagViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val visibleTracks by viewModel.visibleTracks.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.filter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val currentSourceLabel by viewModel.currentSourceLabel.collectAsStateWithLifecycle()
    val isLoadingLibrary by viewModel.isLoadingLibrary.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val editorDraft by viewModel.editorDraft.collectAsStateWithLifecycle()
    val isTagEditorOpen by viewModel.isTagEditorOpen.collectAsStateWithLifecycle()
    val lookupState by viewModel.lookupState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val actionGroups by viewModel.actionGroups.collectAsStateWithLifecycle()
    val formatPresets by viewModel.formatPresets.collectAsStateWithLifecycle()
    val bookmarks by viewModel.folderBookmarks.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route ?: AppRoutes.ROUTE_FILES

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Mp3tag",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " STUDIO",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        val isSettings = currentRoute == AppRoutes.ROUTE_SETTINGS
                        IconButton(
                            onClick = {
                                if (isSettings) {
                                    navController.popBackStack()
                                } else {
                                    navigateToTab(AppRoutes.ROUTE_SETTINGS)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_settings_button")
                        ) {
                            Icon(
                                imageVector = if (isSettings) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.cd_settings),
                                tint = if (isSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.ROUTE_FILES,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(AppRoutes.ROUTE_FILES) {
                            FilesScreen(
                                tracks = visibleTracks,
                                allTracksCount = allTracks.size,
                                selectedIds = selectedIds,
                                searchQuery = searchQuery,
                                activeFilter = activeFilter,
                                sortOrder = sortOrder,
                                currentSourceLabel = currentSourceLabel,
                                bookmarks = bookmarks,
                                isLoading = isLoadingLibrary,
                                isSaving = isSaving,
                                isTagEditorOpen = isTagEditorOpen,
                                editorDraft = editorDraft,
                                playbackState = playbackState,
                                onOpenFolderUri = viewModel::openFolderTree,
                                onPickFilesUris = viewModel::pickAudioFiles,
                                onCreateSampleWorkspace = viewModel::createSampleWorkspace,
                                onSearchQueryChange = viewModel::setSearchQuery,
                                onFilterChange = viewModel::setFilter,
                                onSortChange = viewModel::setSortOrder,
                                onToggleSelect = viewModel::toggleTrackSelection,
                                onTrackClick = viewModel::selectSingleAndOpenEditor,
                                onOpenBatchEditor = viewModel::openBatchEditorForSelection,
                                onCloseEditor = viewModel::closeTagEditor,
                                onSelectAll = viewModel::selectAllVisible,
                                onInvertSelection = viewModel::invertSelection,
                                onClearSelection = viewModel::clearSelection,
                                onTogglePlay = viewModel::togglePlayTrack,
                                onUpdateDraftField = viewModel::updateDraftField,
                                onSetCustomField = viewModel::setCustomField,
                                onPickCoverUri = viewModel::pickCoverArtFromUri,
                                onRemoveCover = viewModel::removeCoverArtInDraft,
                                onExportCoverUri = viewModel::exportCurrentCoverToUri,
                                onOpenOnlineLookup = { navigateToTab(AppRoutes.ROUTE_LOOKUP) },
                                onSaveTags = viewModel::saveEditorDraftToFiles,
                                onRevertTags = viewModel::revertSelectedDraft,
                                onStripTags = viewModel::stripTagsFromSelected
                            )
                        }
                        composable(AppRoutes.ROUTE_CONVERT) {
                            val targetTracks = remember(allTracks, visibleTracks, selectedIds) {
                                viewModel.selectedOrAllTracks()
                            }
                            ConvertScreen(
                                targetTracks = targetTracks,
                                presets = formatPresets,
                                isSaving = isSaving,
                                previewTagToFilename = viewModel::previewTagToFilename,
                                onApplyTagToFilename = viewModel::applyTagToFilename,
                                previewFilenameToTag = viewModel::previewFilenameToTag,
                                onApplyFilenameToTag = viewModel::applyFilenameToTag,
                                previewTagToTag = viewModel::previewTagToTag,
                                onApplyTagToTag = viewModel::applyTagToTag,
                                previewAutoNumber = viewModel::previewAutoNumber,
                                onApplyAutoNumber = viewModel::applyAutoNumber,
                                onExportM3u8 = viewModel::exportM3u8Playlist,
                                onExportCsv = viewModel::exportCsvMetadata,
                                onImportTextTags = viewModel::importTagsFromTextFile,
                                onSavePreset = viewModel::saveFormatPreset
                            )
                        }
                        composable(AppRoutes.ROUTE_ACTIONS) {
                            val targetTracks = remember(allTracks, visibleTracks, selectedIds) {
                                viewModel.selectedOrAllTracks()
                            }
                            ActionsScreen(
                                targetTracks = targetTracks,
                                actionGroups = actionGroups,
                                isSaving = isSaving,
                                previewSteps = viewModel::previewActionSteps,
                                onExecuteSteps = viewModel::executeActionSteps,
                                onSaveActionGroup = viewModel::saveCustomActionGroup,
                                onDeleteActionGroup = viewModel::deleteActionGroup
                            )
                        }
                        composable(AppRoutes.ROUTE_LOOKUP) {
                            val targetTracks = remember(allTracks, visibleTracks, selectedIds) {
                                viewModel.selectedOrAllTracks()
                            }
                            LookupScreen(
                                state = lookupState,
                                targetTracks = targetTracks,
                                defaultEmbedCover = settings.autoEmbedFetchedCover,
                                onPrepare = viewModel::prepareLookupFromSelection,
                                onProviderChange = viewModel::setLookupProvider,
                                onQueryChange = viewModel::setLookupQuery,
                                onSearch = viewModel::searchOnlineReleases,
                                onSelectRelease = viewModel::selectOnlineRelease,
                                onClearRelease = viewModel::clearSelectedRelease,
                                onApplyRelease = viewModel::applyOnlineReleaseToSelectedTracks
                            )
                        }
                        composable(AppRoutes.ROUTE_SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                bookmarks = bookmarks,
                                onId3VersionChange = viewModel::setId3v2Version,
                                onWriteId3v1Change = viewModel::setWriteId3v1,
                                onScanSubfoldersChange = viewModel::setScanSubfolders,
                                onThemeModeChange = viewModel::setThemeMode,
                                onDefaultTagSourceChange = viewModel::setDefaultTagSource,
                                onAutoEmbedCoverChange = viewModel::setAutoEmbedCover,
                                onDeleteBookmark = viewModel::removeFolderBookmark
                            )
                        }
                    }

                    // Docked Mini Audio Player Bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        MiniAudioPlayerBar(
                            playbackState = playbackState,
                            onTogglePlayPause = {
                                val currentTrack = allTracks.firstOrNull { it.id == playbackState.trackId }
                                if (currentTrack != null) viewModel.togglePlayTrack(currentTrack)
                            },
                            onSeek = viewModel::seekPlayback,
                            onClose = viewModel::stopPlayback
                        )
                    }
                }
            }
        }
    }
}
