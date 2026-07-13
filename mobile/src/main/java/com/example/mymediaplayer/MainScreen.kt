package com.example.mymediaplayer

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import com.example.mymediaplayer.shared.bucketGenre
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo


data class TrustedBluetoothDevice(
    val address: String,
    val name: String?
)

private data class AlbumSearchHit(
    val album: String,
    val matchedCount: Int,
    val songs: List<MediaFileInfo>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onSelectFolderWithLimit: (Int, Boolean) -> Unit,
    onChoosePlaylistSaveFolder: () -> Unit,
    onScanWholeDriveWithLimit: (Int) -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onQueueItemSelected: (Long) -> Unit,
    onCreatePlaylist: (Int) -> Unit,
    onPlaylistMessageDismissed: () -> Unit,
    onFolderMessageDismissed: () -> Unit,
    onScanMessageDismissed: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onAlbumSelected: (String) -> Unit,
    onAlbumSortModeChanged: (AlbumSortMode) -> Unit,
    onGenreSelected: (String) -> Unit,
    onArtistSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearCategorySelection: () -> Unit,
    onClearSearch: () -> Unit,
    onPlaylistSelected: (PlaylistInfo) -> Unit,
    onClearPlaylistSelection: () -> Unit,
    onDeletePlaylist: (PlaylistInfo) -> Unit,
    onRenamePlaylist: (PlaylistInfo, String) -> Unit,
    onSavePlaylistEdits: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onPlaySongs: (List<MediaFileInfo>) -> Unit,
    onShuffleSongs: (List<MediaFileInfo>) -> Unit,
    onPlaySearchResults: (List<MediaFileInfo>) -> Unit,
    onShuffleSearchResults: (List<MediaFileInfo>) -> Unit,
    onPlayPlaylist: (PlaylistInfo) -> Unit,
    onShufflePlaylistSongs: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onAddToExistingPlaylist: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onCreatePlaylistFromSongs: (String, List<MediaFileInfo>) -> PlaylistInfo?,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    onToggleFlag: (String) -> Unit,
    nowPlayingArt: Bitmap?,
    showPlaylistSaveFolderPrompt: Boolean,
    onDismissPlaylistSaveFolderPrompt: () -> Unit,
    onSetPlaylistSaveFolderNow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val (menuExpanded, setMenuExpanded) = remember { mutableStateOf(false) }
    val (showPlaylistDialog, setShowPlaylistDialog) = remember { mutableStateOf(false) }
    val (playlistCountText, setPlaylistCountText) = remember { mutableStateOf("3") }
    val (showScanDialog, setShowScanDialog) = remember { mutableStateOf(false) }
    val (scanCountText, setScanCountText) = remember { mutableStateOf(uiState.scan.lastScanLimit.toString()) }
    val (scanDeepMode, setScanDeepMode) = remember { mutableStateOf(uiState.scan.deepScanEnabled) }
    val (scanWholeDriveMode, setScanWholeDriveMode) = remember { mutableStateOf(false) }
    val (showAddToPlaylistDialog, setShowAddToPlaylistDialog) = remember { mutableStateOf(false) }
    val (showCreateFromSelectionDialog, setShowCreateFromSelectionDialog) = remember { mutableStateOf(false) }
    val (createFromSelectionNameText, setCreateFromSelectionNameText) = remember { mutableStateOf("") }
    val (pendingAddFiles, setPendingAddFiles) = remember { mutableStateOf<List<MediaFileInfo>>(emptyList()) }
    val (localCreatedPlaylists, setLocalCreatedPlaylists) = remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    val (isSearchSelectionMode, setIsSearchSelectionMode) = remember { mutableStateOf(false) }
    val (selectedSearchUris, setSelectedSearchUris) = remember { mutableStateOf<Set<String>>(emptySet()) }
    val (showDeletePlaylistDialog, setShowDeletePlaylistDialog) = remember { mutableStateOf(false) }
    val (pendingDeletePlaylist, setPendingDeletePlaylist) = remember { mutableStateOf<PlaylistInfo?>(null) }
    val (showRenamePlaylistDialog, setShowRenamePlaylistDialog) = remember { mutableStateOf(false) }
    val (pendingRenamePlaylist, setPendingRenamePlaylist) = remember { mutableStateOf<PlaylistInfo?>(null) }
    val (renamePlaylistNameText, setRenamePlaylistNameText) = remember { mutableStateOf("") }
    val (showRemovePlaylistSongDialog, setShowRemovePlaylistSongDialog) = remember { mutableStateOf(false) }
    val (pendingRemoveSong, setPendingRemoveSong) = remember { mutableStateOf<MediaFileInfo?>(null) }
    val (showQueueDialog, setShowQueueDialog) = remember { mutableStateOf(false) }
    val (showExpandedNowPlayingDialog, setShowExpandedNowPlayingDialog) = remember { mutableStateOf(false) }
    val (songsFavoritesOnly, setSongsFavoritesOnly) = rememberSaveable { mutableStateOf(false) }
    val (searchFavoritesOnly, setSearchFavoritesOnly) = rememberSaveable { mutableStateOf(false) }
    val (isSearchExpanded, setIsSearchExpanded) = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.search.searchQuery) {
        if (uiState.search.searchQuery.isNotBlank()) {
            setIsSearchExpanded(true)
        }
    }

    LaunchedEffect(uiState.search.searchQuery, uiState.search.searchResults) {
        val intersected = selectedSearchUris.intersect(uiState.search.searchResults.map { it.uriString }.toSet())
        setSelectedSearchUris(intersected)
        if (intersected.isEmpty()) {
            setIsSearchSelectionMode(false)
        }
    }

    LaunchedEffect(uiState.scan.discoveredPlaylists) {
        if (localCreatedPlaylists.isNotEmpty()) {
            val knownUris = uiState.scan.discoveredPlaylists.mapTo(HashSet()) { it.uriString }
            setLocalCreatedPlaylists(localCreatedPlaylists.filterNot { it.uriString in knownUris })
        }
    }

    LaunchedEffect(uiState.playlist.playlistMessage) {
        val message = uiState.playlist.playlistMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onPlaylistMessageDismissed()
        }
    }

    LaunchedEffect(uiState.scan.folderMessage) {
        val message = uiState.scan.folderMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onFolderMessageDismissed()
        }
    }

    LaunchedEffect(uiState.scan.scanMessage) {
        val message = uiState.scan.scanMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onScanMessageDismissed()
        }
    }

    LaunchedEffect(uiState.playback.playbackError) {
        val message = uiState.playback.playbackError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = run {
                    val lineColor = MaterialTheme.colorScheme.tertiary
                    Modifier.drawBehind {
                        drawLine(
                            color = lineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, size.height),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }
            ) {
                Column {
                    TopAppBar(
                        title = {
                            if (isSearchExpanded) {
                                TextField(
                                    value = uiState.search.searchQuery,
                                    onValueChange = onSearchQueryChanged,
                                    placeholder = { Text("Search title, artist, album, genre", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            } else {
                                Text("MyMediaPlayer")
                            }
                        },
                        navigationIcon = {
                            if (isSearchExpanded) {
                                TextButton(onClick = {
                                    setIsSearchExpanded(false)
                                    onClearSearch()
                                }) { Text("Back") }
                            }
                        },
                        actions = {
                            MainScreenTopBarActions(
                                isSearchExpanded = isSearchExpanded,
                                searchQuery = uiState.search.searchQuery,
                                menuExpanded = menuExpanded,
                                setMenuExpanded = setMenuExpanded,
                                setIsSearchExpanded = setIsSearchExpanded,
                                setScanCountText = setScanCountText,
                                setScanDeepMode = setScanDeepMode,
                                setShowScanDialog = setShowScanDialog,
                                setPlaylistCountText = setPlaylistCountText,
                                setShowPlaylistDialog = setShowPlaylistDialog,
                                onOpenSettings = onOpenSettings,
                                onClearSearch = onClearSearch,
                                lastScanLimit = uiState.scan.lastScanLimit,
                                deepScanEnabled = uiState.scan.deepScanEnabled,
                                lastPlaylistCount = uiState.playlist.lastPlaylistCount,
                                hasScannedFiles = uiState.scan.scannedFiles.isNotEmpty()
                            )
                        }
                    )
                    Box(
                        modifier = run {
                            val boxColor = MaterialTheme.colorScheme.primary
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .drawBehind {
                                    drawRoundRect(
                                        color = boxColor,
                                        cornerRadius = CornerRadius(2.dp.toPx())
                                    )
                                }
                        }
                    )
                }
            }
        },
        bottomBar = {
            if (uiState.playback.currentTrackName != null) {
                PlaybackBar(
                    trackName = uiState.playback.currentTrackName,
                    artistName = uiState.playback.currentArtistName,
                    isPlaying = uiState.playback.isPlaying,
                    onPlayPause = onPlayPause,
                    onOpenExpanded = { setShowExpandedNowPlayingDialog(true) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        MainScreenContent(
            padding = padding,
            uiState = uiState,
            onFileClick = onFileClick,
            onStop = onStop,
            onNext = onNext,
            onPrev = onPrev,
            onTabSelected = onTabSelected,
            onAlbumSelected = onAlbumSelected,
            onAlbumSortModeChanged = onAlbumSortModeChanged,
            onGenreSelected = onGenreSelected,
            onArtistSelected = onArtistSelected,
            onSearchQueryChanged = onSearchQueryChanged,
            onClearCategorySelection = onClearCategorySelection,
            onClearSearch = onClearSearch,
            onPlaylistSelected = onPlaylistSelected,
            onClearPlaylistSelection = onClearPlaylistSelection,
            onSavePlaylistEdits = onSavePlaylistEdits,
            onPlaySongs = onPlaySongs,
            onShuffleSongs = onShuffleSongs,
            onPlaySearchResults = onPlaySearchResults,
            onShuffleSearchResults = onShuffleSearchResults,
            onPlayPlaylist = onPlayPlaylist,
            onShufflePlaylistSongs = onShufflePlaylistSongs,
            onToggleFavorite = onToggleFavorite,
            setShowAddToPlaylistDialog = setShowAddToPlaylistDialog,
            setPendingAddFiles = setPendingAddFiles,
            isSearchSelectionMode = isSearchSelectionMode,
            setIsSearchSelectionMode = setIsSearchSelectionMode,
            selectedSearchUris = selectedSearchUris,
            setSelectedSearchUris = setSelectedSearchUris,
            setShowDeletePlaylistDialog = setShowDeletePlaylistDialog,
            setPendingDeletePlaylist = setPendingDeletePlaylist,
            setShowRenamePlaylistDialog = setShowRenamePlaylistDialog,
            setPendingRenamePlaylist = setPendingRenamePlaylist,
            setRenamePlaylistNameText = setRenamePlaylistNameText,
            setShowRemovePlaylistSongDialog = setShowRemovePlaylistSongDialog,
            setPendingRemoveSong = setPendingRemoveSong,
            songsFavoritesOnly = songsFavoritesOnly,
            setSongsFavoritesOnly = setSongsFavoritesOnly,
            searchFavoritesOnly = searchFavoritesOnly,
            setSearchFavoritesOnly = setSearchFavoritesOnly,
            isSearchExpanded = isSearchExpanded,
            setIsSearchExpanded = setIsSearchExpanded,
        )
    }

    PlaylistDialogs(
        maxScannedFilesCount = uiState.scan.scannedFiles.size,
        discoveredPlaylists = uiState.scan.discoveredPlaylists,
        playlistCountText = playlistCountText,
        setPlaylistCountText = setPlaylistCountText,
        showPlaylistDialog = showPlaylistDialog,
        setShowPlaylistDialog = setShowPlaylistDialog,
        onCreatePlaylist = onCreatePlaylist,
        pendingAddFiles = pendingAddFiles,
        setPendingAddFiles = setPendingAddFiles,
        localCreatedPlaylists = localCreatedPlaylists,
        showAddToPlaylistDialog = showAddToPlaylistDialog,
        setShowAddToPlaylistDialog = setShowAddToPlaylistDialog,
        setShowCreateFromSelectionDialog = setShowCreateFromSelectionDialog,
        setCreateFromSelectionNameText = setCreateFromSelectionNameText,
        onAddToExistingPlaylist = onAddToExistingPlaylist,
        showCreateFromSelectionDialog = showCreateFromSelectionDialog,
        createFromSelectionNameText = createFromSelectionNameText,
        onCreatePlaylistFromSongs = onCreatePlaylistFromSongs,
        onChoosePlaylistSaveFolder = onChoosePlaylistSaveFolder,
        setLocalCreatedPlaylists = setLocalCreatedPlaylists,
        showDeletePlaylistDialog = showDeletePlaylistDialog,
        setShowDeletePlaylistDialog = setShowDeletePlaylistDialog,
        pendingDeletePlaylist = pendingDeletePlaylist,
        onDeletePlaylist = onDeletePlaylist,
        showRenamePlaylistDialog = showRenamePlaylistDialog,
        setShowRenamePlaylistDialog = setShowRenamePlaylistDialog,
        pendingRenamePlaylist = pendingRenamePlaylist,
        setPendingRenamePlaylist = setPendingRenamePlaylist,
        renamePlaylistNameText = renamePlaylistNameText,
        setRenamePlaylistNameText = setRenamePlaylistNameText,
        onRenamePlaylist = onRenamePlaylist,
        showRemovePlaylistSongDialog = showRemovePlaylistSongDialog,
        setShowRemovePlaylistSongDialog = setShowRemovePlaylistSongDialog,
        pendingRemoveSong = pendingRemoveSong,
        onConfirmRemoveSong = {
            val song = pendingRemoveSong ?: return@onConfirmRemoveSong
            val playlist = uiState.playlist.selectedPlaylist ?: return@onConfirmRemoveSong
            val updated = uiState.playlist.playlistSongs.filterNot { it.uriString == song.uriString }
            if (updated.size == uiState.playlist.playlistSongs.size) return@onConfirmRemoveSong
            onSavePlaylistEdits(playlist, updated)
        }
    )

    ScanDialogs(
        scanCountText = scanCountText,
        setScanCountText = setScanCountText,
        scanWholeDriveMode = scanWholeDriveMode,
        setScanWholeDriveMode = setScanWholeDriveMode,
        scanDeepMode = scanDeepMode,
        setScanDeepMode = setScanDeepMode,
        showScanDialog = showScanDialog,
        setShowScanDialog = setShowScanDialog,
        onScanWholeDriveWithLimit = onScanWholeDriveWithLimit,
        onSelectFolderWithLimit = onSelectFolderWithLimit
    )

    PlaybackDialogs(
        playbackState = uiState.playback,
        flaggedUris = uiState.flaggedUris,
        nowPlayingArt = nowPlayingArt,
        showExpandedNowPlayingDialog = showExpandedNowPlayingDialog,
        setShowExpandedNowPlayingDialog = setShowExpandedNowPlayingDialog,
        onSeekTo = onSeekTo,
        onPlayPause = onPlayPause,
        onPrev = onPrev,
        onNext = onNext,
        onToggleFlag = onToggleFlag,
        showQueueDialog = showQueueDialog,
        setShowQueueDialog = setShowQueueDialog,
        onQueueItemSelected = onQueueItemSelected
    )

    SettingsDialogs(
        showPlaylistSaveFolderPrompt = showPlaylistSaveFolderPrompt,
        onDismissPlaylistSaveFolderPrompt = onDismissPlaylistSaveFolderPrompt,
        onSetPlaylistSaveFolderNow = onSetPlaylistSaveFolderNow
    )
}

@Composable
private fun RecentSearchesSection(
    queries: List<String>,
    onSearchSelected: (String) -> Unit
) {
    Text(
        text = "Recent searches",
        style = MaterialTheme.typography.titleSmall
    )
    Spacer(modifier = Modifier.height(4.dp))
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(queries) { query ->
            TextButton(onClick = { onSearchSelected(query) }) {
                Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun DeletePlaylistDialogContent(
    pendingDeletePlaylist: PlaylistInfo?,
    onDismissRequest: () -> Unit,
    onDeletePlaylist: (PlaylistInfo) -> Unit
) {
    val target = pendingDeletePlaylist
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Delete playlist") },
        text = {
            Text(
                "Delete ${target?.displayName?.removeSuffix(".m3u") ?: "this playlist"}?"
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (target != null) onDeletePlaylist(target)
                }
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RemovePlaylistSongDialogContent(
    pendingRemoveSong: MediaFileInfo?,
    onDismissRequest: () -> Unit,
    onConfirmRemove: () -> Unit
) {
    val target = pendingRemoveSong
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Remove song from playlist") },
        text = {
            Text(
                "Remove \"${target?.cleanTitle ?: target?.displayName ?: "this song"}\" from the playlist?"
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (target != null) onConfirmRemove()
                }
            ) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenamePlaylistDialogContent(
    pendingRenamePlaylist: PlaylistInfo?,
    renamePlaylistNameText: String,
    onRenamePlaylistNameTextChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onRenamePlaylist: (PlaylistInfo, String) -> Unit
) {
    val target = pendingRenamePlaylist
    val trimmedName = renamePlaylistNameText.trim()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Rename playlist") },
        text = {
            Column {
                Text("Enter a new name")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = renamePlaylistNameText,
                    onValueChange = onRenamePlaylistNameTextChange,
                    singleLine = true,
                    placeholder = { Text("e.g. Road Trip") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (target != null) onRenamePlaylist(target, trimmedName)
                },
                enabled = target != null && trimmedName.isNotEmpty()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun QueueItem(
    item: QueueEntry,
    activeQueueId: Long,
    onQueueItemSelected: (Long) -> Unit
) {
    val isActive = item.queueId == activeQueueId
    TextButton(
        onClick = {
            onQueueItemSelected(item.queueId)
        },
        enabled = !isActive
    ) {
        Text(
            text = if (isActive) "▶ ${item.title}" else item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun QueueDialogContent(
    queueTitle: String?,
    queueItems: List<QueueEntry>,
    activeQueueId: Long,
    onDismissRequest: () -> Unit,
    onQueueItemSelected: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(queueTitle ?: "Queue")
        },
        text = {
            if (queueItems.isEmpty()) {
                Text("Queue is empty")
            } else {
                LazyColumn {
                    items(queueItems) { item ->
                        QueueItem(
                            item = item,
                            activeQueueId = activeQueueId,
                            onQueueItemSelected = onQueueItemSelected
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
fun PlaylistSaveFolderPromptDialogContent(
    onDismissRequest: () -> Unit,
    onSetPlaylistSaveFolderNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Set Playlist Save Folder") },
        text = {
            Text("Choose a folder where new playlists (.m3u) will be saved.")
        },
        confirmButton = {
            TextButton(onClick = onSetPlaylistSaveFolderNow) {
                Text("Set now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Later")
            }
        }
    )
}

@Composable
fun CreateFromSelectionDialogContent(
    createFromSelectionNameText: String,
    onCreateFromSelectionNameTextChange: (String) -> Unit,
    pendingAddFiles: List<MediaFileInfo>,
    onCreatePlaylistFromSongs: (String, List<MediaFileInfo>) -> PlaylistInfo?,
    onChoosePlaylistSaveFolder: () -> Unit,
    onPlaylistCreated: (PlaylistInfo) -> Unit,
    onDismissRequest: () -> Unit
) {
    val nameTrimmed = createFromSelectionNameText.trim()
    val files = pendingAddFiles
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("New playlist") },
        text = {
            Column {
                Text("Playlist name (optional)")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = createFromSelectionNameText,
                    onValueChange = onCreateFromSelectionNameTextChange,
                    singleLine = true,
                    placeholder = { Text("Leave blank for auto name") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val created = onCreatePlaylistFromSongs(nameTrimmed, files)
                    if (created != null) {
                        onPlaylistCreated(created)
                    }
                },
                enabled = files.isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChoosePlaylistSaveFolder) {
                    Text("Choose save folder")
                }
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun AddToPlaylistDialogContent(
    pendingAddFiles: List<MediaFileInfo>,
    localCreatedPlaylists: List<PlaylistInfo>,
    discoveredPlaylists: List<PlaylistInfo>,
    onDismissRequest: () -> Unit,
    onCreateNewPlaylistClick: () -> Unit,
    onAddToExistingPlaylist: (PlaylistInfo, List<MediaFileInfo>) -> Unit
) {
    val files = pendingAddFiles
    val firstName = files.firstOrNull()?.cleanTitle ?: "songs"
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Add to playlist") },
        text = {
            Column {
                Text(
                    if (files.size <= 1) {
                        "Choose a playlist for $firstName"
                    } else {
                        "Choose a playlist for ${files.size} songs"
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        if (files.isNotEmpty()) {
                            onCreateNewPlaylistClick()
                        }
                    },
                    enabled = files.isNotEmpty()
                ) {
                    Text("Create new playlist")
                }
                val visiblePlaylists = (localCreatedPlaylists + discoveredPlaylists)
                    .distinctBy { it.uriString }
                if (visiblePlaylists.isEmpty()) {
                    Text(
                        text = "No existing playlists",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                    ) {
                        items(visiblePlaylists) { playlist ->
                            TextButton(
                                onClick = {
                                    if (files.isNotEmpty()) onAddToExistingPlaylist(playlist, files)
                                },
                                enabled = files.isNotEmpty()
                            ) {
                                Text(playlist.displayName.removeSuffix(".m3u"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ScanDialogContent(
    scanCountText: String,
    onScanCountTextChange: (String) -> Unit,
    scanWholeDriveMode: Boolean,
    onScanWholeDriveModeChange: (Boolean) -> Unit,
    scanDeepMode: Boolean,
    onScanDeepModeChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onScanWholeDriveWithLimit: (Int) -> Unit,
    onSelectFolderWithLimit: (Int, Boolean) -> Unit
) {
    val countValue = scanCountText.toIntOrNull()
    val isValid = countValue != null && countValue > 0
    val helperText = when {
        countValue == null -> "Enter a number greater than 0."
        countValue <= 0 -> "Enter a number greater than 0."
        else -> "OK"
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Scan Music") },
        text = {
            Column {
                Text("How many songs should be scanned?")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = scanCountText,
                    onValueChange = onScanCountTextChange,
                    singleLine = true,
                    placeholder = { Text("e.g. 50000") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = scanWholeDriveMode,
                        onCheckedChange = onScanWholeDriveModeChange
                    )
                    Text("Whole drive (uses device media index)")
                }
                if (!scanWholeDriveMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = scanDeepMode,
                            onCheckedChange = onScanDeepModeChange
                        )
                        Text("Deep scan (slower, tries unknown file types — result is cached for fast startups)")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isValid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        onDismissRequest()
                        if (scanWholeDriveMode) {
                            onScanWholeDriveWithLimit(countValue!!)
                        } else {
                            onSelectFolderWithLimit(countValue!!, scanDeepMode)
                        }
                    }
                },
                enabled = isValid
            ) {
                Text(if (scanWholeDriveMode) "Scan" else "Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateRandomPlaylistDialog(
    maxCount: Int,
    playlistCountText: String,
    onPlaylistCountTextChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onCreatePlaylist: (Int) -> Unit
) {
    val countValue = playlistCountText.toIntOrNull()
    val isValid = countValue != null && countValue in 1..maxCount
    val helperText = when {
        maxCount == 0 -> "Scan a folder to enable playlists."
        countValue == null -> "Enter a number between 1 and $maxCount."
        countValue < 1 || countValue > maxCount -> "Enter a number between 1 and $maxCount."
        else -> "OK"
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Create Random Playlist") },
        text = {
            Column {
                Text("How many songs should be added?")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = playlistCountText,
                    onValueChange = onPlaylistCountTextChange,
                    singleLine = true,
                    placeholder = { Text("e.g. 3") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isValid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        onDismissRequest()
                        onCreatePlaylist(countValue!!)
                    }
                },
                enabled = isValid
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CategoryTabContent(
    title: String,
    categories: List<String>,
    categoryCounts: Map<String, Int>,
    isLoading: Boolean,
    selectedLabel: String?,
    onCategorySelected: (String) -> Unit,
    onClearCategorySelection: () -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
    enableAlphaIndex: Boolean = false,
    songs: List<MediaFileInfo>,
    isPlaying: Boolean,
    isPlayingPlaylist: Boolean,
    hasNext: Boolean,
    hasPrev: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onAddToPlaylist: (MediaFileInfo) -> Unit,
    favoriteUris: Set<String>,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    currentMediaId: String?
) {
    if (isLoading) {
        CircularProgressIndicator()
        return
    }

    if (categories.isEmpty()) {
        Text("No $title found")
        return
    }

    val letters = if (enableAlphaIndex) {
        categories.mapNotNull {
            it.trim().firstOrNull()?.uppercaseChar()?.takeIf { c -> c in 'A'..'Z' }?.toString()
        }.distinct().sorted()
    } else {
        emptyList()
    }
    val (selectedLetter, setSelectedLetter) = rememberSaveable(title) { mutableStateOf<String?>(null) }

    Column {
        if (headerContent != null) {
            headerContent()
            Spacer(modifier = Modifier.height(8.dp))
        }

        CategoryListSection(
            categories = categories,
            categoryCounts = categoryCounts,
            selectedLabel = selectedLabel,
            selectedLetter = selectedLetter,
            enableAlphaIndex = enableAlphaIndex,
            letters = letters,
            onSelectedLetterChanged = { setSelectedLetter(it) },
            onCategorySelected = onCategorySelected
        )

        Spacer(modifier = Modifier.height(8.dp))

        CategorySongsSection(
            title = title,
            selectedLabel = selectedLabel,
            songs = songs,
            isPlaying = isPlaying,
            isPlayingPlaylist = isPlayingPlaylist,
            hasNext = hasNext,
            hasPrev = hasPrev,
            onClearCategorySelection = onClearCategorySelection,
            onPlay = onPlay,
            onShuffle = onShuffle,
            onStop = onStop,
            onNext = onNext,
            onPrev = onPrev,
            onFileClick = onFileClick,
            onAddToPlaylist = onAddToPlaylist,
            favoriteUris = favoriteUris,
            onToggleFavorite = onToggleFavorite,
            currentMediaId = currentMediaId
        )
    }
}

@Composable
private fun CategoryListSection(
    categories: List<String>,
    categoryCounts: Map<String, Int>,
    selectedLabel: String?,
    selectedLetter: String?,
    enableAlphaIndex: Boolean,
    letters: List<String>,
    onSelectedLetterChanged: (String?) -> Unit,
    onCategorySelected: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            val filteredCategories = if (selectedLetter == null) {
                categories
            } else {
                categories.filter {
                    it.trim().firstOrNull()?.uppercaseChar()?.toString() == selectedLetter
                }
            }
            val visibleCategories = if (selectedLabel == null) {
                filteredCategories
            } else {
                filteredCategories.filter { it == selectedLabel }
            }
            if (selectedLabel == null && enableAlphaIndex && letters.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            TextButton(
                                onClick = { onSelectedLetterChanged(null) },
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text("All")
                            }
                        }
                        items(letters) { letter ->
                            TextButton(
                                onClick = { onSelectedLetterChanged(letter) },
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text(letter)
                            }
                        }
                    }
                }
            }
            items(visibleCategories) { category ->
                val displayTitle = categoryCounts[category]?.let { count ->
                    "$category ($count)"
                } ?: category
                CategoryCard(
                    title = displayTitle,
                    isSelected = category == selectedLabel,
                    isCompact = selectedLabel != null,
                    onClick = { onCategorySelected(category) }
                )
            }
        }
    }
}

@Composable
private fun CategorySongsSection(
    title: String,
    selectedLabel: String?,
    songs: List<MediaFileInfo>,
    isPlaying: Boolean,
    isPlayingPlaylist: Boolean,
    hasNext: Boolean,
    hasPrev: Boolean,
    onClearCategorySelection: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onAddToPlaylist: (MediaFileInfo) -> Unit,
    favoriteUris: Set<String>,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    currentMediaId: String?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (selectedLabel != null) {
                TextButton(onClick = onClearCategorySelection) {
                    Text("Back to all $title")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Songs in $selectedLabel",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                PlaybackButtonsRow(
                    isPlaying = isPlaying,
                    isPlayingPlaylist = isPlayingPlaylist,
                    hasNext = hasNext,
                    hasPrev = hasPrev,
                    showNavForNonPlaylist = true,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    onStop = onStop,
                    onNext = onNext,
                    onPrev = onPrev
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (songs.isEmpty()) {
                    Text("No songs in $selectedLabel")
                } else {
                    LazyColumn {
                        items(songs) { file ->
                            FileCard(
                                file = file,
                                isCurrentTrack = file.uriString == currentMediaId,
                                onClick = { onFileClick(file) },
                                onAddToPlaylist = { onAddToPlaylist(file) },
                                isFavorite = file.uriString in favoriteUris,
                                onToggleFavorite = { onToggleFavorite(file) }
                            )
                        }
                    }
                }
            } else {
                Text("Select a $title to view songs")
            }
        }
    }
}

private inline fun buildCounts(
    files: List<MediaFileInfo>,
    crossinline selector: (MediaFileInfo) -> String
): Map<String, Int> = files.groupingBy(selector).eachCount()

private fun buildAlbumCounts(files: List<MediaFileInfo>): Map<String, Int> =
    buildCounts(files) { it.album?.ifBlank { null } ?: "Unknown Album" }

private fun buildArtistCounts(files: List<MediaFileInfo>): Map<String, Int> =
    buildCounts(files) { it.artist?.ifBlank { null } ?: "Unknown Artist" }

private fun buildGenreCounts(files: List<MediaFileInfo>): Map<String, Int> =
    buildCounts(files) { bucketGenre(it.genre) }

private fun decadeLabelForYear(year: Int?): String {
    if (year == null || year <= 0) return "Unknown Decade"
    val decade = (year / 10) * 10
    return "${decade}s"
}

private fun albumLabel(file: MediaFileInfo): String =
    file.album?.ifBlank { null } ?: "Unknown Album"

private fun toggleAlbumFavorite(
    songs: List<MediaFileInfo>,
    favoriteUris: Set<String>,
    allFavorited: Boolean,
    onToggleFavorite: (MediaFileInfo) -> Unit
) {
    val targetFavorite = !allFavorited
    songs.forEach { song ->
        val currentlyFavorite = song.uriString in favoriteUris
        if (currentlyFavorite != targetFavorite) {
            onToggleFavorite(song)
        }
    }
}

@Composable
private fun AlbumSearchResultsSection(
    query: String,
    albumHits: List<AlbumSearchHit>,
    favoriteUris: Set<String>,
    onOpenAlbum: (String) -> Unit,
    onAddAlbumToPlaylist: (List<MediaFileInfo>) -> Unit,
    onToggleFavorite: (MediaFileInfo) -> Unit
) {
    if (albumHits.isEmpty()) {
        Text("No albums for \"$query\"")
        return
    }
    Text(
        text = "Album Results (${albumHits.size})",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    LazyColumn {
        items(albumHits) { hit ->
            AlbumSearchHitCard(
                hit = hit,
                favoriteUris = favoriteUris,
                onOpenAlbum = onOpenAlbum,
                onAddAlbumToPlaylist = onAddAlbumToPlaylist,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}

@Composable
private fun AlbumSearchHitCard(
    hit: AlbumSearchHit,
    favoriteUris: Set<String>,
    onOpenAlbum: (String) -> Unit,
    onAddAlbumToPlaylist: (List<MediaFileInfo>) -> Unit,
    onToggleFavorite: (MediaFileInfo) -> Unit
) {
    val allFavorited = hit.songs.isNotEmpty() &&
        hit.songs.all { it.uriString in favoriteUris }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = hit.album,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${hit.matchedCount} matched • ${hit.songs.size} track(s)",
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onOpenAlbum(hit.album) }) {
                    Text("Open")
                }
                TextButton(
                    onClick = {
                        toggleAlbumFavorite(hit.songs, favoriteUris, allFavorited, onToggleFavorite)
                    },
                    enabled = hit.songs.isNotEmpty()
                ) {
                    Text(if (allFavorited) "Unfavorite album" else "Favorite album")
                }
                TextButton(
                    onClick = { onAddAlbumToPlaylist(hit.songs) },
                    enabled = hit.songs.isNotEmpty()
                ) {
                    Text("Add album")
                }
            }
        }
    }
}

@Composable
private fun SearchResultsActionRow(
    searchFavoritesOnly: Boolean,
    onToggleSearchFavoritesOnly: () -> Unit,
    visibleSearchResults: List<MediaFileInfo>,
    onAddAll: (List<MediaFileInfo>) -> Unit,
    isSearchSelectionMode: Boolean,
    onToggleSearchSelectionMode: () -> Unit,
    selectedSearchUris: Set<String>,
    onSelectAll: (Set<String>) -> Unit,
    onClearSelection: () -> Unit,
    onAddSelected: (List<MediaFileInfo>) -> Unit
) {
    val searchUrisSet = selectedSearchUris.toSet()
    val selectedSearchResults = visibleSearchResults.filter {
        it.uriString in searchUrisSet
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            TextButton(onClick = onToggleSearchFavoritesOnly) {
                Text(if (searchFavoritesOnly) "All results" else "Favorites only")
            }
        }
        item {
            TextButton(
                onClick = { onAddAll(visibleSearchResults) }
            ) {
                Text("Add all")
            }
        }
        item {
            TextButton(
                onClick = onToggleSearchSelectionMode
            ) {
                Text(if (isSearchSelectionMode) "Cancel selection" else "Select")
            }
        }
        if (isSearchSelectionMode) {
            item {
                TextButton(
                    onClick = {
                        onSelectAll(visibleSearchResults.map { it.uriString }.toSet())
                    },
                    enabled = selectedSearchResults.size < visibleSearchResults.size
                ) {
                    Text("Select all")
                }
            }
            item {
                TextButton(
                    onClick = onClearSelection,
                    enabled = selectedSearchResults.isNotEmpty()
                ) {
                    Text("Clear selection")
                }
            }
            item {
                TextButton(
                    onClick = { onAddSelected(selectedSearchResults) },
                    enabled = selectedSearchResults.isNotEmpty()
                ) {
                    Text("Add selected (${selectedSearchResults.size})")
                }
            }
        }
    }
}

@Composable
private fun SongsListSection(
    title: String,
    songs: List<MediaFileInfo>,
    isPlaying: Boolean,
    isPlayingPlaylist: Boolean,
    hasNext: Boolean,
    hasPrev: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onAddToPlaylist: (MediaFileInfo) -> Unit,
    favoriteUris: Set<String>,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    enableSelection: Boolean = false,
    selectedUris: Set<String> = emptySet(),
    onSelectionToggle: (MediaFileInfo) -> Unit = {},
    currentMediaId: String?
) {
    if (songs.isEmpty()) {
        Text("No songs found")
        return
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    PlaybackButtonsRow(
        isPlaying = isPlaying,
        isPlayingPlaylist = isPlayingPlaylist,
        hasNext = hasNext,
        hasPrev = hasPrev,
        showNavForNonPlaylist = true,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onStop = onStop,
        onNext = onNext,
        onPrev = onPrev
    )
    Spacer(modifier = Modifier.height(8.dp))
    LazyColumn {
        items(songs) { file ->
            FileCard(
                file = file,
                isCurrentTrack = file.uriString == currentMediaId,
                onClick = { onFileClick(file) },
                onAddToPlaylist = { onAddToPlaylist(file) },
                isFavorite = file.uriString in favoriteUris,
                onToggleFavorite = { onToggleFavorite(file) },
                isSelectionEnabled = enableSelection,
                isSelected = file.uriString in selectedUris,
                onSelectionToggle = { onSelectionToggle(file) }
            )
        }
    }
}

@Composable
private fun PlaylistsSection(
    playlists: List<PlaylistInfo>,
    selectedPlaylist: PlaylistInfo?,
    playlistSongs: List<MediaFileInfo>,
    isLoading: Boolean,
    isPlaying: Boolean,
    isPlayingPlaylist: Boolean,
    queueTitle: String?,
    hasNext: Boolean,
    hasPrev: Boolean,
    onPlaylistSelected: (PlaylistInfo) -> Unit,
    onClearPlaylistSelection: () -> Unit,
    onRequestDeletePlaylist: (PlaylistInfo) -> Unit,
    onRequestRenamePlaylist: (PlaylistInfo) -> Unit,
    onRequestRemoveSong: (MediaFileInfo) -> Unit,
    onSavePlaylistEdits: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onPlayPlaylist: (PlaylistInfo) -> Unit,
    onShufflePlaylistSongs: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onAddToPlaylist: (MediaFileInfo) -> Unit,
    favoriteUris: Set<String>,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    currentMediaId: String?
) {
    if (playlists.isEmpty()) {
        Text("No playlists found")
        return
    }

    if (selectedPlaylist == null) {
        PlaylistList(
            playlists = playlists,
            onRequestRenamePlaylist = onRequestRenamePlaylist,
            onRequestDeletePlaylist = onRequestDeletePlaylist,
            onPlaylistSelected = onPlaylistSelected
        )
    } else {
        PlaylistDetails(
            selectedPlaylist = selectedPlaylist,
            playlistSongs = playlistSongs,
            isLoading = isLoading,
            isPlaying = isPlaying,
            isPlayingPlaylist = isPlayingPlaylist,
            queueTitle = queueTitle,
            hasNext = hasNext,
            hasPrev = hasPrev,
            onClearPlaylistSelection = onClearPlaylistSelection,
            onSavePlaylistEdits = onSavePlaylistEdits,
            onRequestRemoveSong = onRequestRemoveSong,
            onPlayPlaylist = onPlayPlaylist,
            onShufflePlaylistSongs = onShufflePlaylistSongs,
            onStop = onStop,
            onNext = onNext,
            onPrev = onPrev,
            onFileClick = onFileClick,
            onAddToPlaylist = onAddToPlaylist,
            favoriteUris = favoriteUris,
            onToggleFavorite = onToggleFavorite,
            currentMediaId = currentMediaId
        )
    }
}

@Composable
private fun PlaylistList(
    playlists: List<PlaylistInfo>,
    onRequestRenamePlaylist: (PlaylistInfo) -> Unit,
    onRequestDeletePlaylist: (PlaylistInfo) -> Unit,
    onPlaylistSelected: (PlaylistInfo) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            items(playlists) { playlist ->
                val isSmart = playlist.uriString.startsWith(MainViewModel.SMART_PREFIX)
                PlaylistCard(
                    playlist = playlist,
                    isCompact = false,
                    isSmart = isSmart,
                    onRename = { if (!isSmart) onRequestRenamePlaylist(playlist) },
                    onDelete = { if (!isSmart) onRequestDeletePlaylist(playlist) },
                    onClick = { onPlaylistSelected(playlist) }
                )
            }
        }
    }
}


@Composable
private fun PlaylistSongsContent(
    isEditing: Boolean,
    editableSongs: List<MediaFileInfo>,
    playlistSongs: List<MediaFileInfo>,
    editSearchQuery: String,
    setEditSearchQuery: (String) -> Unit,
    dedupeOnSave: Boolean,
    setDedupeOnSave: (Boolean) -> Unit,
    currentMediaId: String?,
    favoriteUris: Set<String>,
    onFileClick: (MediaFileInfo) -> Unit,
    onAddToPlaylist: (MediaFileInfo) -> Unit,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    onRequestRemoveSong: ((MediaFileInfo) -> Unit)?,
    setEditableSongs: (List<MediaFileInfo>) -> Unit,
    draggingIndex: Int?,
    setDraggingIndex: (Int?) -> Unit,
    draggingOffsetY: Float,
    setDraggingOffsetY: (Float) -> Unit,
    dragSwapThresholdPx: Float
) {
    val displayedSongs = if (isEditing) editableSongs else playlistSongs
    if (isEditing) {
        PlaylistEditFilterRow(
            editSearchQuery = editSearchQuery,
            setEditSearchQuery = setEditSearchQuery,
            dedupeOnSave = dedupeOnSave,
            setDedupeOnSave = setDedupeOnSave
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (displayedSongs.isEmpty()) {
        Text("No songs in playlist")
    } else {
        if (!isEditing) {
            PlaylistViewList(
                displayedSongs = displayedSongs,
                currentMediaId = currentMediaId,
                favoriteUris = favoriteUris,
                onFileClick = onFileClick,
                onAddToPlaylist = onAddToPlaylist,
                onToggleFavorite = onToggleFavorite,
                onRequestRemoveSong = onRequestRemoveSong
            )
        } else {
            val filteredRows = if (editSearchQuery.isNotBlank()) {
                val needle = editSearchQuery.trim().lowercase()
                editableSongs.withIndex().filter { indexed ->
                    val file = indexed.value
                    val haystack = listOfNotNull(
                        file.cleanTitle,
                        file.artist,
                        file.album
                    ).joinToString(" ").lowercase()
                    haystack.contains(needle)
                }
            } else {
                editableSongs.withIndex().toList()
            }
            PlaylistEditList(
                filteredRows = filteredRows,
                editSearchQuery = editSearchQuery,
                editableSongs = editableSongs,
                setEditableSongs = setEditableSongs,
                draggingIndex = draggingIndex,
                setDraggingIndex = setDraggingIndex,
                draggingOffsetY = draggingOffsetY,
                setDraggingOffsetY = setDraggingOffsetY,
                dragSwapThresholdPx = dragSwapThresholdPx
            )
        }
    }
}

@Composable
private fun PlaylistDetails(
    selectedPlaylist: PlaylistInfo,
    playlistSongs: List<MediaFileInfo>,
    isLoading: Boolean,
    isPlaying: Boolean,
    isPlayingPlaylist: Boolean,
    queueTitle: String?,
    hasNext: Boolean,
    hasPrev: Boolean,
    onClearPlaylistSelection: () -> Unit,
    onSavePlaylistEdits: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onRequestRemoveSong: (MediaFileInfo) -> Unit,
    onPlayPlaylist: (PlaylistInfo) -> Unit,
    onShufflePlaylistSongs: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onAddToPlaylist: (MediaFileInfo) -> Unit,
    favoriteUris: Set<String>,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    currentMediaId: String?
) {
    val (isEditing, setIsEditing) = remember(selectedPlaylist.uriString) { mutableStateOf(false) }
    val (editableSongs, setEditableSongs) = remember(selectedPlaylist.uriString) { mutableStateOf<List<MediaFileInfo>>(emptyList()) }
    val (draggingIndex, setDraggingIndex) = remember(selectedPlaylist.uriString) { mutableStateOf<Int?>(null) }
    var draggingOffsetY by remember(selectedPlaylist.uriString) { mutableFloatStateOf(0f) }
    val (dedupeOnSave, setDedupeOnSave) = remember(selectedPlaylist.uriString) { mutableStateOf(false) }
    val (editSearchQuery, setEditSearchQuery) = remember(selectedPlaylist.uriString) { mutableStateOf("") }
    val (showDiscardChangesDialog, setShowDiscardChangesDialog) = remember(selectedPlaylist.uriString) { mutableStateOf(false) }
    val (pendingClearSelection, setPendingClearSelection) = remember(selectedPlaylist.uriString) { mutableStateOf(false) }
    val dragSwapThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }

    LaunchedEffect(selectedPlaylist.uriString, playlistSongs, isEditing) {
        if (!isEditing) {
            setEditableSongs(playlistSongs)
            setDedupeOnSave(false)
            setEditSearchQuery("")
        }
    }
    val hasUnsavedChanges = isEditing && (editableSongs != playlistSongs || dedupeOnSave)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            PlaylistHeader(
                selectedPlaylist = selectedPlaylist,
                playlistSongs = playlistSongs,
                isEditing = isEditing,
                editableSongs = editableSongs,
                dedupeOnSave = dedupeOnSave,
                hasUnsavedChanges = hasUnsavedChanges,
                onClearPlaylistSelection = onClearPlaylistSelection,
                setPendingClearSelection = setPendingClearSelection,
                setShowDiscardChangesDialog = setShowDiscardChangesDialog,
                setIsEditing = setIsEditing,
                setEditableSongs = setEditableSongs,
                setDraggingIndex = setDraggingIndex,
                setDraggingOffsetY = { draggingOffsetY = it },
                setDedupeOnSave = setDedupeOnSave,
                setEditSearchQuery = setEditSearchQuery,
                onSavePlaylistEdits = onSavePlaylistEdits
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            val selectedPlaylistTitle = selectedPlaylist.displayName.removeSuffix(".m3u")
            val isSelectedPlaylistActive = isPlayingPlaylist &&
                queueTitle?.trim()?.equals(selectedPlaylistTitle, ignoreCase = true) == true

            PlaybackButtonsRow(
                isPlaying = isPlaying && isSelectedPlaylistActive,
                isPlayingPlaylist = isSelectedPlaylistActive,
                hasNext = hasNext,
                hasPrev = hasPrev,
                showShuffleWhenPlaying = true,
                onPlay = { onPlayPlaylist(selectedPlaylist) },
                onShuffle = { onShufflePlaylistSongs(selectedPlaylist, playlistSongs) },
                onStop = onStop,
                onNext = onNext,
                onPrev = onPrev
            )

            Spacer(modifier = Modifier.height(8.dp))

            PlaylistSongsContent(
                isEditing = isEditing,
                editableSongs = editableSongs,
                playlistSongs = playlistSongs,
                editSearchQuery = editSearchQuery,
                setEditSearchQuery = setEditSearchQuery,
                dedupeOnSave = dedupeOnSave,
                setDedupeOnSave = setDedupeOnSave,
                currentMediaId = currentMediaId,
                favoriteUris = favoriteUris,
                onFileClick = onFileClick,
                onAddToPlaylist = onAddToPlaylist,
                onToggleFavorite = onToggleFavorite,
                onRequestRemoveSong = if (selectedPlaylist.uriString.startsWith(MainViewModel.SMART_PREFIX)) {
                    null
                } else {
                    onRequestRemoveSong
                },
                setEditableSongs = setEditableSongs,
                draggingIndex = draggingIndex,
                setDraggingIndex = setDraggingIndex,
                draggingOffsetY = draggingOffsetY,
                setDraggingOffsetY = { draggingOffsetY = it },
                dragSwapThresholdPx = dragSwapThresholdPx
            )
        }
    }

    if (showDiscardChangesDialog) {
        DiscardChangesDialog(
            setShowDiscardChangesDialog = setShowDiscardChangesDialog,
            setIsEditing = setIsEditing,
            setEditableSongs = setEditableSongs,
            playlistSongs = playlistSongs,
            setDedupeOnSave = setDedupeOnSave,
            setEditSearchQuery = setEditSearchQuery,
            pendingClearSelection = pendingClearSelection,
            setPendingClearSelection = setPendingClearSelection,
            onClearPlaylistSelection = onClearPlaylistSelection
        )
    }
}

@Composable
private fun PlaylistHeader(
    selectedPlaylist: PlaylistInfo,
    playlistSongs: List<MediaFileInfo>,
    isEditing: Boolean,
    editableSongs: List<MediaFileInfo>,
    dedupeOnSave: Boolean,
    hasUnsavedChanges: Boolean,
    onClearPlaylistSelection: () -> Unit,
    setPendingClearSelection: (Boolean) -> Unit,
    setShowDiscardChangesDialog: (Boolean) -> Unit,
    setIsEditing: (Boolean) -> Unit,
    setEditableSongs: (List<MediaFileInfo>) -> Unit,
    setDraggingIndex: (Int?) -> Unit,
    setDraggingOffsetY: (Float) -> Unit,
    setDedupeOnSave: (Boolean) -> Unit,
    setEditSearchQuery: (String) -> Unit,
    onSavePlaylistEdits: (PlaylistInfo, List<MediaFileInfo>) -> Unit
) {
    TextButton(
        onClick = {
            if (hasUnsavedChanges) {
                setPendingClearSelection(true)
                setShowDiscardChangesDialog(true)
            } else {
                onClearPlaylistSelection()
            }
        }
    ) {
        Text("Back to all playlists")
    }
    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = selectedPlaylist.displayName.removeSuffix(".m3u"),
        style = MaterialTheme.typography.titleSmall
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!isEditing) {
            TextButton(
                onClick = {
                    setEditableSongs(playlistSongs)
                    setIsEditing(true)
                },
                enabled = playlistSongs.isNotEmpty() &&
                    !selectedPlaylist.uriString.startsWith(MainViewModel.SMART_PREFIX)
            ) {
                Text("Edit")
            }
        } else {
            TextButton(
                onClick = {
                    val songsToSave = if (dedupeOnSave) {
                        editableSongs.distinctBy { it.uriString }
                    } else {
                        editableSongs
                    }
                    onSavePlaylistEdits(selectedPlaylist, songsToSave)
                    setEditableSongs(songsToSave)
                    setIsEditing(false)
                    setDraggingIndex(null)
                    setDraggingOffsetY(0f)
                    setDedupeOnSave(false)
                    setEditSearchQuery("")
                },
                enabled = editableSongs.isNotEmpty()
            ) {
                Text("Save")
            }
            TextButton(
                onClick = {
                    setEditableSongs(playlistSongs)
                    setIsEditing(false)
                    setDraggingIndex(null)
                    setDraggingOffsetY(0f)
                    setDedupeOnSave(false)
                    setEditSearchQuery("")
                }
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun PlaylistEditFilterRow(
    editSearchQuery: String,
    setEditSearchQuery: (String) -> Unit,
    dedupeOnSave: Boolean,
    setDedupeOnSave: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = editSearchQuery,
            onValueChange = { setEditSearchQuery(it) },
            singleLine = true,
            placeholder = { Text("Filter songs while editing") },
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { setDedupeOnSave(!dedupeOnSave) }) {
            Text(if (dedupeOnSave) "Dedup: On" else "Dedup: Off")
        }
    }
}

@Composable
private fun DiscardChangesDialog(
    setShowDiscardChangesDialog: (Boolean) -> Unit,
    setIsEditing: (Boolean) -> Unit,
    setEditableSongs: (List<MediaFileInfo>) -> Unit,
    playlistSongs: List<MediaFileInfo>,
    setDedupeOnSave: (Boolean) -> Unit,
    setEditSearchQuery: (String) -> Unit,
    pendingClearSelection: Boolean,
    setPendingClearSelection: (Boolean) -> Unit,
    onClearPlaylistSelection: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { setShowDiscardChangesDialog(false) },
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved playlist edits.") },
        confirmButton = {
            TextButton(
                onClick = {
                    setShowDiscardChangesDialog(false)
                    setIsEditing(false)
                    setEditableSongs(playlistSongs)
                    setDedupeOnSave(false)
                    setEditSearchQuery("")
                    val clearSelection = pendingClearSelection
                    setPendingClearSelection(false)
                    if (clearSelection) onClearPlaylistSelection()
                }
            ) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    setShowDiscardChangesDialog(false)
                    setPendingClearSelection(false)
                }
            ) {
                Text("Keep editing")
            }
        }
    )
}

@Composable
private fun PlaylistViewList(
    displayedSongs: List<MediaFileInfo>,
    currentMediaId: String?,
    favoriteUris: Set<String>,
    onFileClick: (MediaFileInfo) -> Unit,
    onAddToPlaylist: (MediaFileInfo) -> Unit,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    onRequestRemoveSong: ((MediaFileInfo) -> Unit)?
) {
    LazyColumn {
        items(displayedSongs) { file ->
            FileCard(
                file = file,
                isCurrentTrack = file.uriString == currentMediaId,
                onClick = { onFileClick(file) },
                onAddToPlaylist = { onAddToPlaylist(file) },
                isFavorite = file.uriString in favoriteUris,
                onToggleFavorite = { onToggleFavorite(file) },
                onRemoveFromPlaylist = onRequestRemoveSong?.let { callback -> { callback(file) } }
            )
        }
    }
}

@Composable
private fun PlaylistEditList(
    filteredRows: List<IndexedValue<MediaFileInfo>>,
    editSearchQuery: String,
    editableSongs: List<MediaFileInfo>,
    setEditableSongs: (List<MediaFileInfo>) -> Unit,
    draggingIndex: Int?,
    setDraggingIndex: (Int?) -> Unit,
    draggingOffsetY: Float,
    setDraggingOffsetY: (Float) -> Unit,
    dragSwapThresholdPx: Float
) {
    LazyColumn {
        items(filteredRows, key = { "${it.index}|${it.value.uriString}" }) { row ->
            val sourceIndex = row.index
            val file = row.value
            val canDrag = editSearchQuery.isBlank()
            val dragModifier = if (canDrag) {
                Modifier.pointerInput(sourceIndex, editableSongs) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            setDraggingIndex(sourceIndex)
                            setDraggingOffsetY(0f)
                        },
                        onDragEnd = {
                            setDraggingIndex(null)
                            setDraggingOffsetY(0f)
                        },
                        onDragCancel = {
                            setDraggingIndex(null)
                            setDraggingOffsetY(0f)
                        },
                        onDrag = { change, dragAmount ->
                            if (draggingIndex != sourceIndex) return@detectDragGesturesAfterLongPress
                            change.consume()
                            val newOffsetY = draggingOffsetY + dragAmount.y
                            setDraggingOffsetY(newOffsetY)
                            if (newOffsetY > dragSwapThresholdPx && sourceIndex < editableSongs.lastIndex) {
                                val list = editableSongs.toMutableList()
                                val tmp = list[sourceIndex + 1]
                                list[sourceIndex + 1] = list[sourceIndex]
                                list[sourceIndex] = tmp
                                setEditableSongs(list)
                                setDraggingIndex(sourceIndex + 1)
                                setDraggingOffsetY(newOffsetY - dragSwapThresholdPx)
                            } else if (newOffsetY < -dragSwapThresholdPx && sourceIndex > 0) {
                                val list = editableSongs.toMutableList()
                                val tmp = list[sourceIndex - 1]
                                list[sourceIndex - 1] = list[sourceIndex]
                                list[sourceIndex] = tmp
                                setEditableSongs(list)
                                setDraggingIndex(sourceIndex - 1)
                                setDraggingOffsetY(newOffsetY + dragSwapThresholdPx)
                            }
                        }
                    )
                }
            } else {
                Modifier
            }
            EditableSongCard(
                file = file,
                canDrag = canDrag,
                isDragging = draggingIndex == sourceIndex,
                draggingOffsetY = draggingOffsetY,
                dragModifier = dragModifier,
                onRemove = {
                    if (sourceIndex in editableSongs.indices) {
                        val list = editableSongs.toMutableList()
                        list.removeAt(sourceIndex)
                        setEditableSongs(list)
                    }
                }
            )
        }
    }
}

@Composable
private fun PlaybackButtonsRow(
    isPlaying: Boolean,
    isPlayingPlaylist: Boolean,
    hasNext: Boolean,
    hasPrev: Boolean,
    showNavForNonPlaylist: Boolean = false,
    showShuffleWhenPlaying: Boolean = false,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isPlaying) {
            if (isPlayingPlaylist || showNavForNonPlaylist) {
                TextButton(onClick = onPrev, enabled = hasPrev) { Text("Previous") }
                TextButton(onClick = onNext, enabled = hasNext) { Text("Next") }
            }
            if (showShuffleWhenPlaying) {
                TextButton(onClick = onShuffle) { Text("Shuffle") }
            }
            TextButton(onClick = onStop) { Text("Stop") }
        } else {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) { Text("Play") }
            Button(
                onClick = onShuffle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) { Text("Shuffle") }
        }
    }
}

@Composable
private fun CategoryCard(
    title: String,
    isSelected: Boolean,
    isCompact: Boolean = false,
    onClick: () -> Unit
) {
    val colors = if (isSelected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isCompact) 2.dp else 4.dp)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        colors = colors
    ) {
        Row(
            modifier = Modifier.padding(if (isCompact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = if (isCompact) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PlaybackBar(
    trackName: String,
    artistName: String?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onOpenExpanded: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenExpanded() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = run {
                    val barColor = MaterialTheme.colorScheme.primary
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = barColor,
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trackName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!artistName.isNullOrBlank()) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onPlayPause,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
            }
        }
    }
}

@Composable
fun ExpandedNowPlayingDialog(
    trackName: String,
    artistName: String?,
    album: String?,
    genre: String?,
    year: Long,
    artwork: Bitmap?,
    currentPositionMs: Long,
    positionUpdatedAtElapsedMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    hasPrev: Boolean,
    hasNext: Boolean,
    isPlayingPlaylist: Boolean,
    isFlagged: Boolean = false,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleFlag: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val now = SystemClock.elapsedRealtime()
    val projectedPositionMs = if (isPlaying && playbackSpeed > 0f) {
        val elapsed = (now - positionUpdatedAtElapsedMs).coerceAtLeast(0L)
        currentPositionMs + (elapsed * playbackSpeed).toLong()
    } else {
        currentPositionMs
    }
    val durationSafe = durationMs.coerceAtLeast(0L)
    val clampedProjectedMs = if (durationSafe > 0L) {
        projectedPositionMs.coerceIn(0L, durationSafe)
    } else {
        projectedPositionMs.coerceAtLeast(0L)
    }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValueMs by remember(
        trackName,
        artistName,
        currentPositionMs,
        positionUpdatedAtElapsedMs,
        durationMs,
        isPlaying
    ) {
        mutableFloatStateOf(clampedProjectedMs.toFloat())
    }

    // Continuously update progress while playing and user is not dragging the slider
    LaunchedEffect(isPlaying, currentPositionMs, positionUpdatedAtElapsedMs, playbackSpeed) {
        while (isPlaying && playbackSpeed > 0f) {
            kotlinx.coroutines.delay(250L)
            if (!isSeeking) {
                val elapsed = (SystemClock.elapsedRealtime() - positionUpdatedAtElapsedMs).coerceAtLeast(0L)
                val projected = currentPositionMs + (elapsed * playbackSpeed).toLong()
                seekValueMs = if (durationSafe > 0L) {
                    projected.coerceIn(0L, durationSafe).toFloat()
                } else {
                    projected.coerceAtLeast(0L).toFloat()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Now Playing") },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork.asImageBitmap(),
                        contentDescription = "Album art",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
                Text(trackName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!artistName.isNullOrBlank()) {
                    Text(artistName, style = MaterialTheme.typography.bodySmall)
                }
                val details = listOfNotNull(
                    album?.takeIf { it.isNotBlank() },
                    genre?.takeIf { it.isNotBlank() },
                    if (year > 0L) year.toString() else null
                ).joinToString(" • ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (durationSafe > 0L) {
                    Slider(
                        value = seekValueMs.coerceIn(0f, durationSafe.toFloat()),
                        onValueChange = {
                            isSeeking = true
                            seekValueMs = it
                        },
                        valueRange = 0f..durationSafe.toFloat(),
                        onValueChangeFinished = {
                            isSeeking = false
                            onSeekTo(seekValueMs.toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatPlaybackDuration(seekValueMs.toLong()),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            formatPlaybackDuration(durationSafe),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isPlayingPlaylist) {
                        TextButton(onClick = onPrev, enabled = hasPrev) { Text("Prev") }
                    }
                    TextButton(onClick = onPlayPause) { Text(if (isPlaying) "Pause" else "Play") }
                    if (isPlayingPlaylist) {
                        TextButton(onClick = onNext, enabled = hasNext) { Text("Next") }
                    }
                    TextButton(onClick = onToggleFlag) {
                        Text(
                            if (isFlagged) "Unflag" else "Flag",
                            color = if (isFlagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

internal fun formatPlaybackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}


@Composable
fun PlaylistCard(
    playlist: PlaylistInfo,
    isCompact: Boolean = false,
    isSmart: Boolean = false,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isCompact) 2.dp else 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(if (isCompact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playlist.displayName.removeSuffix(".m3u"),
                style = if (isCompact) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                modifier = Modifier.weight(1f)
            )
            if (!isSmart) {
                TextButton(onClick = onRename) {
                    Text("Rename")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
fun EditableSongCard(
    file: MediaFileInfo,
    canDrag: Boolean,
    isDragging: Boolean,
    draggingOffsetY: Float,
    dragModifier: Modifier,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .graphicsLayer {
                    translationY = if (isDragging) draggingOffsetY else 0f
                }
                .then(dragModifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = file.cleanTitle,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (canDrag) "Drag" else "Filtered",
                style = MaterialTheme.typography.labelMedium
            )
            TextButton(
                onClick = onRemove
            ) { Text("Remove") }
        }
    }
}

@Composable
fun FileCard(
    file: MediaFileInfo,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isSelectionEnabled: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    val colors = if (isCurrentTrack) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = colors
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = file.cleanTitle,
                style = MaterialTheme.typography.bodyLarge
            )
            val secondary = buildSongDetails(file)
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (onAddToPlaylist != null || onToggleFavorite != null || (isSelectionEnabled && onSelectionToggle != null) || onRemoveFromPlaylist != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onToggleFavorite != null) {
                        TextButton(onClick = onToggleFavorite) {
                            Text(if (isFavorite) "Unfavorite" else "Favorite")
                        }
                    }
                    if (isSelectionEnabled && onSelectionToggle != null) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectionToggle() }
                        )
                    }
                    if (onAddToPlaylist != null) {
                        TextButton(onClick = onAddToPlaylist) {
                            Text("Add")
                        }
                    }
                    if (onRemoveFromPlaylist != null) {
                        TextButton(onClick = onRemoveFromPlaylist) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private fun buildSongDetails(file: MediaFileInfo): String {
    val parts = mutableListOf<String>()
    file.artist?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    file.album?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    file.durationMs?.let { parts.add(formatDuration(it)) }
    file.year?.let { if (it > 0) parts.add(it.toString()) }
    return parts.joinToString(" • ")
}

@androidx.annotation.VisibleForTesting
internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun SongsTabContent(
    songsFavoritesOnly: Boolean,
    onToggleSongsFavoritesOnly: () -> Unit,
    selectedDecade: String?,
    availableDecades: List<String>,
    onDecadeSelected: (String?) -> Unit,
    scannedFiles: List<MediaFileInfo>,
    favoriteUris: Set<String>,
    isPlayingPlaylist: Boolean,
    hasNext: Boolean,
    hasPrev: Boolean,
    onPlaySongs: (List<MediaFileInfo>) -> Unit,
    onShuffleSongs: (List<MediaFileInfo>) -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onAddToPlaylist: (MediaFileInfo) -> Unit,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    currentMediaId: String?
) {
    val filteredByDecade = remember(scannedFiles, selectedDecade) {
        if (selectedDecade != null) {
            scannedFiles.filter { decadeLabelForYear(it.year) == selectedDecade }
        } else {
            scannedFiles
        }
    }
    val songsForTab = remember(filteredByDecade, songsFavoritesOnly, favoriteUris) {
        if (songsFavoritesOnly) {
            filteredByDecade.filter { it.uriString in favoriteUris }
        } else {
            filteredByDecade
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onToggleSongsFavoritesOnly) {
            Text(if (songsFavoritesOnly) "Show all songs" else "Favorites only")
        }

        val (decadeMenuExpanded, setDecadeMenuExpanded) = remember { mutableStateOf(false) }
        TextButton(onClick = { setDecadeMenuExpanded(true) }) {
            Text("Decade: ${selectedDecade ?: "All"}")
        }
        DropdownMenu(
            expanded = decadeMenuExpanded,
            onDismissRequest = { setDecadeMenuExpanded(false) }
        ) {
            DropdownMenuItem(
                text = { Text("All") },
                onClick = {
                    setDecadeMenuExpanded(false)
                    onDecadeSelected(null)
                }
            )
            availableDecades.forEach { decade ->
                DropdownMenuItem(
                    text = { Text(decade) },
                    onClick = {
                        setDecadeMenuExpanded(false)
                        onDecadeSelected(decade)
                    }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    SongsListSection(
        title = if (songsFavoritesOnly) {
            "Favorites (${songsForTab.size})"
        } else {
            "${songsForTab.size} file(s) found"
        },
        songs = songsForTab,
        favoriteUris = favoriteUris,
        isPlaying = false,
        isPlayingPlaylist = isPlayingPlaylist,
        hasNext = hasNext,
        hasPrev = hasPrev,
        onPlay = { onPlaySongs(songsForTab) },
        onShuffle = { onShuffleSongs(songsForTab) },
        onStop = onStop,
        onNext = onNext,
        onPrev = onPrev,
        onFileClick = onFileClick,
        onAddToPlaylist = onAddToPlaylist,
        onToggleFavorite = onToggleFavorite,
        currentMediaId = currentMediaId
    )
}

@Composable
private fun MainScreenTopBarActions(
    isSearchExpanded: Boolean,
    searchQuery: String,
    menuExpanded: Boolean,
    setMenuExpanded: (Boolean) -> Unit,
    setIsSearchExpanded: (Boolean) -> Unit,
    setScanCountText: (String) -> Unit,
    setScanDeepMode: (Boolean) -> Unit,
    setShowScanDialog: (Boolean) -> Unit,
    setPlaylistCountText: (String) -> Unit,
    setShowPlaylistDialog: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onClearSearch: () -> Unit,
    lastScanLimit: Int,
    deepScanEnabled: Boolean,
    lastPlaylistCount: Int,
    hasScannedFiles: Boolean
) {
    if (!isSearchExpanded) {
        TextButton(onClick = { setIsSearchExpanded(true) }) { Text("Search") }
        TextButton(onClick = { setMenuExpanded(true) }) {
            Text("Menu")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { setMenuExpanded(false) }
        ) {
            DropdownMenuItem(
                text = { Text("Select Folder") },
                onClick = {
                    setMenuExpanded(false)
                    setScanCountText(lastScanLimit.toString())
                    setScanDeepMode(deepScanEnabled)
                    setShowScanDialog(true)
                }
            )
            DropdownMenuItem(
                text = { Text("Create Random Playlist") },
                onClick = {
                    setMenuExpanded(false)
                    setPlaylistCountText(lastPlaylistCount.toString())
                    setShowPlaylistDialog(true)
                },
                enabled = hasScannedFiles
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    setMenuExpanded(false)
                    onOpenSettings()
                }
            )
        }
    } else if (searchQuery.isNotEmpty()) {
        TextButton(onClick = onClearSearch) { Text("Clear") }
    }
}

@Composable
private fun MainScreenContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    uiState: MainUiState,
    onFileClick: (MediaFileInfo) -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onAlbumSelected: (String) -> Unit,
    onAlbumSortModeChanged: (AlbumSortMode) -> Unit,
    onGenreSelected: (String) -> Unit,
    onArtistSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearCategorySelection: () -> Unit,
    onClearSearch: () -> Unit,
    onPlaylistSelected: (PlaylistInfo) -> Unit,
    onClearPlaylistSelection: () -> Unit,
    onSavePlaylistEdits: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onPlaySongs: (List<MediaFileInfo>) -> Unit,
    onShuffleSongs: (List<MediaFileInfo>) -> Unit,
    onPlaySearchResults: (List<MediaFileInfo>) -> Unit,
    onShuffleSearchResults: (List<MediaFileInfo>) -> Unit,
    onPlayPlaylist: (PlaylistInfo) -> Unit,
    onShufflePlaylistSongs: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    setShowAddToPlaylistDialog: (Boolean) -> Unit,
    setPendingAddFiles: (List<MediaFileInfo>) -> Unit,
    isSearchSelectionMode: Boolean,
    setIsSearchSelectionMode: (Boolean) -> Unit,
    selectedSearchUris: Set<String>,
    setSelectedSearchUris: (Set<String>) -> Unit,
    setShowDeletePlaylistDialog: (Boolean) -> Unit,
    setPendingDeletePlaylist: (PlaylistInfo?) -> Unit,
    setShowRenamePlaylistDialog: (Boolean) -> Unit,
    setPendingRenamePlaylist: (PlaylistInfo?) -> Unit,
    setRenamePlaylistNameText: (String) -> Unit,
    setShowRemovePlaylistSongDialog: (Boolean) -> Unit,
    setPendingRemoveSong: (MediaFileInfo?) -> Unit,
    songsFavoritesOnly: Boolean,
    setSongsFavoritesOnly: (Boolean) -> Unit,
    searchFavoritesOnly: Boolean,
    setSearchFavoritesOnly: (Boolean) -> Unit,
    isSearchExpanded: Boolean,
    setIsSearchExpanded: (Boolean) -> Unit,
) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (uiState.scan.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                uiState.scan.scanProgress?.let { progress ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            if (isSearchExpanded && uiState.search.searchQuery.isBlank() && uiState.search.previousSearchQueries.isNotEmpty()) {
                RecentSearchesSection(
                    queries = uiState.search.previousSearchQueries,
                    onSearchSelected = onSearchQueryChanged
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.search.searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val visibleSearchResults = if (searchFavoritesOnly) {
                    uiState.search.searchResults.filter { it.uriString in uiState.favoriteUris }
                } else {
                    uiState.search.searchResults
                }
                if (visibleSearchResults.isEmpty()) {
                    Text("No results for \"${uiState.search.searchQuery}\"")
                } else if (uiState.library.selectedTab == LibraryTab.Albums) {
                    val allByAlbum = uiState.scan.scannedFiles.groupBy { albumLabel(it) }
                    val albumHits = visibleSearchResults
                        .groupBy { albumLabel(it) }
                        .map { (album, matches) ->
                            AlbumSearchHit(
                                album = album,
                                matchedCount = matches.size,
                                songs = allByAlbum[album].orEmpty()
                            )
                        }
                        .sortedBy { it.album.lowercase() }
                    AlbumSearchResultsSection(
                        query = uiState.search.searchQuery,
                        albumHits = albumHits,
                        favoriteUris = uiState.favoriteUris,
                        onOpenAlbum = { album ->
                            setIsSearchExpanded(false)
                            onClearSearch()
                            onTabSelected(LibraryTab.Albums)
                            onAlbumSelected(album)
                        },
                        onAddAlbumToPlaylist = { songs ->
                            setPendingAddFiles(songs)
                            setShowAddToPlaylistDialog(true)
                        },
                        onToggleFavorite = onToggleFavorite
                    )
                } else {
                    SearchResultsActionRow(
                        searchFavoritesOnly = searchFavoritesOnly,
                        onToggleSearchFavoritesOnly = { setSearchFavoritesOnly(!searchFavoritesOnly) },
                        visibleSearchResults = visibleSearchResults,
                        onAddAll = {
                            setPendingAddFiles(it)
                            setShowAddToPlaylistDialog(true)
                        },
                        isSearchSelectionMode = isSearchSelectionMode,
                        onToggleSearchSelectionMode = {
                            setIsSearchSelectionMode(!isSearchSelectionMode)
                            if (!isSearchSelectionMode) setSelectedSearchUris(emptySet())
                        },
                        selectedSearchUris = selectedSearchUris,
                        onSelectAll = { setSelectedSearchUris(it) },
                        onClearSelection = { setSelectedSearchUris(emptySet()) },
                        onAddSelected = {
                            setPendingAddFiles(it)
                            setShowAddToPlaylistDialog(true)
                        }
                    )
                    SongsListSection(
                        title = "Search Results (${visibleSearchResults.size})",
                        songs = visibleSearchResults,
                        favoriteUris = uiState.favoriteUris,
                        isPlaying = false,
                        isPlayingPlaylist = uiState.playback.isPlayingPlaylist,
                        hasNext = uiState.playback.hasNext,
                        hasPrev = uiState.playback.hasPrev,
                        onPlay = { onPlaySearchResults(visibleSearchResults) },
                        onShuffle = { onShuffleSearchResults(visibleSearchResults) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        onAddToPlaylist = {
                            setPendingAddFiles(listOf(it))
                            setShowAddToPlaylistDialog(true)
                        },
                        onToggleFavorite = onToggleFavorite,
                        enableSelection = isSearchSelectionMode,
                        selectedUris = selectedSearchUris,
                        onSelectionToggle = { file ->
                            setSelectedSearchUris(if (file.uriString in selectedSearchUris) {
                                selectedSearchUris - file.uriString
                            } else {
                                selectedSearchUris + file.uriString
                            })
                        },
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val albumCounts = remember(uiState.scan.scannedFiles) { buildAlbumCounts(uiState.scan.scannedFiles) }
            val artistCounts = remember(uiState.scan.scannedFiles) { buildArtistCounts(uiState.scan.scannedFiles) }
            val genreCounts = remember(uiState.scan.scannedFiles) { buildGenreCounts(uiState.scan.scannedFiles) }
            val tabs = LibraryTab.values().toList()
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(uiState.library.selectedTab),
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 20.dp,
                indicator = { tabPositions ->
                    val index = tabs.indexOf(uiState.library.selectedTab)
                    if (index in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = uiState.library.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(tab.label, maxLines = 1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (uiState.library.selectedTab) {
                LibraryTab.Songs -> {
                    val (selectedDecade, setSelectedDecade) = rememberSaveable { mutableStateOf<String?>(null) }
                    val availableDecades = remember(uiState.scan.scannedFiles) {
                        uiState.scan.scannedFiles
                            .map { decadeLabelForYear(it.year) }
                            .filter { it != "Unknown Decade" }
                            .distinct()
                            .sorted()
                    }
                    SongsTabContent(
                        songsFavoritesOnly = songsFavoritesOnly,
                        onToggleSongsFavoritesOnly = { setSongsFavoritesOnly(!songsFavoritesOnly) },
                        selectedDecade = selectedDecade,
                        availableDecades = availableDecades,
                        onDecadeSelected = { setSelectedDecade(it) },
                        scannedFiles = uiState.scan.scannedFiles,
                        favoriteUris = uiState.favoriteUris,
                        isPlayingPlaylist = uiState.playback.isPlayingPlaylist,
                        hasNext = uiState.playback.hasNext,
                        hasPrev = uiState.playback.hasPrev,
                        onPlaySongs = onPlaySongs,
                        onShuffleSongs = onShuffleSongs,
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        onAddToPlaylist = {
                            setPendingAddFiles(listOf(it))
                            setShowAddToPlaylistDialog(true)
                        },
                        onToggleFavorite = onToggleFavorite,
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
                LibraryTab.Playlists -> {
                    val smartPlaylists = remember(
                        uiState.favoriteUris,
                        uiState.flaggedUris,
                        uiState.playCounts,
                        uiState.lastPlayedAt,
                        uiState.scan.scannedFiles
                    ) {
                        listOf(
                            PlaylistInfo(
                                uriString = MainViewModel.SMART_FAVORITES,
                                displayName = "Favorites.m3u"
                            ),
                            PlaylistInfo(
                                uriString = MainViewModel.SMART_FLAGGED,
                                displayName = "Flagged.m3u"
                            ),
                            PlaylistInfo(
                                uriString = MainViewModel.SMART_RECENTLY_ADDED,
                                displayName = "Recently Added.m3u"
                            ),
                            PlaylistInfo(
                                uriString = MainViewModel.SMART_MOST_PLAYED,
                                displayName = "Most Played.m3u"
                            ),
                            PlaylistInfo(
                                uriString = MainViewModel.SMART_NOT_HEARD_RECENTLY,
                                displayName = "Haven't Heard In A While.m3u"
                            )
                        )
                    }
                    PlaylistsSection(
                        playlists = uiState.scan.discoveredPlaylists + smartPlaylists,
                        selectedPlaylist = uiState.playlist.selectedPlaylist,
                        playlistSongs = uiState.playlist.playlistSongs,
                        isLoading = uiState.playlist.isPlaylistLoading,
                        isPlaying = false,
                        isPlayingPlaylist = uiState.playback.isPlayingPlaylist,
                        queueTitle = uiState.playback.queueTitle,
                        hasNext = uiState.playback.hasNext,
                        hasPrev = uiState.playback.hasPrev,
                        onPlaylistSelected = onPlaylistSelected,
                        onClearPlaylistSelection = onClearPlaylistSelection,
                        onRequestDeletePlaylist = {
                            setPendingDeletePlaylist(it)
                            setShowDeletePlaylistDialog(true)
                        },
                        onRequestRenamePlaylist = {
                            setPendingRenamePlaylist(it)
                            setRenamePlaylistNameText(it.displayName.removeSuffix(".m3u"))
                            setShowRenamePlaylistDialog(true)
                        },
                        onRequestRemoveSong = { song ->
                            setPendingRemoveSong(song)
                            setShowRemovePlaylistSongDialog(true)
                        },
                        onSavePlaylistEdits = onSavePlaylistEdits,
                        onPlayPlaylist = onPlayPlaylist,
                        onShufflePlaylistSongs = onShufflePlaylistSongs,
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        onAddToPlaylist = {
                            setPendingAddFiles(listOf(it))
                            setShowAddToPlaylistDialog(true)
                        },
                        favoriteUris = uiState.favoriteUris,
                        onToggleFavorite = onToggleFavorite,
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
                LibraryTab.Albums -> {
                    CategoryTabContent(
                        title = "Albums",
                        categories = uiState.library.albums,
                        categoryCounts = albumCounts,
                        isLoading = uiState.library.isMetadataLoading,
                        selectedLabel = uiState.library.selectedAlbum,
                        onCategorySelected = onAlbumSelected,
                        onClearCategorySelection = onClearCategorySelection,
                        headerContent = {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    TextButton(
                                        onClick = { onAlbumSortModeChanged(AlbumSortMode.Name) },
                                        enabled = uiState.library.albumSortMode != AlbumSortMode.Name
                                    ) {
                                        Text("Sort: Name")
                                    }
                                }
                                item {
                                    TextButton(
                                        onClick = { onAlbumSortModeChanged(AlbumSortMode.DateAddedDesc) },
                                        enabled = uiState.library.albumSortMode != AlbumSortMode.DateAddedDesc
                                    ) {
                                        Text("Sort: Date added")
                                    }
                                }
                            }
                        },
                        songs = uiState.library.filteredSongs,
                        isPlaying = false,
                        isPlayingPlaylist = uiState.playback.isPlayingPlaylist,
                        hasNext = uiState.playback.hasNext,
                        hasPrev = uiState.playback.hasPrev,
                        onPlay = { onPlaySongs(uiState.library.filteredSongs) },
                        onShuffle = { onShuffleSongs(uiState.library.filteredSongs) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        onAddToPlaylist = {
                            setPendingAddFiles(listOf(it))
                            setShowAddToPlaylistDialog(true)
                        },
                        favoriteUris = uiState.favoriteUris,
                        onToggleFavorite = onToggleFavorite,
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
                LibraryTab.Genres -> {
                    CategoryTabContent(
                        title = "Genres",
                        categories = uiState.library.genres,
                        categoryCounts = genreCounts,
                        isLoading = uiState.library.isMetadataLoading,
                        selectedLabel = uiState.library.selectedGenre,
                        onCategorySelected = onGenreSelected,
                        onClearCategorySelection = onClearCategorySelection,
                        enableAlphaIndex = false,
                        songs = uiState.library.filteredSongs,
                        isPlaying = false,
                        isPlayingPlaylist = uiState.playback.isPlayingPlaylist,
                        hasNext = uiState.playback.hasNext,
                        hasPrev = uiState.playback.hasPrev,
                        onPlay = { onPlaySongs(uiState.library.filteredSongs) },
                        onShuffle = { onShuffleSongs(uiState.library.filteredSongs) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        onAddToPlaylist = {
                            setPendingAddFiles(listOf(it))
                            setShowAddToPlaylistDialog(true)
                        },
                        favoriteUris = uiState.favoriteUris,
                        onToggleFavorite = onToggleFavorite,
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
                LibraryTab.Artists -> {
                    CategoryTabContent(
                        title = "Artists",
                        categories = uiState.library.artists,
                        categoryCounts = artistCounts,
                        isLoading = uiState.library.isMetadataLoading,
                        selectedLabel = uiState.library.selectedArtist,
                        onCategorySelected = onArtistSelected,
                        onClearCategorySelection = onClearCategorySelection,
                        enableAlphaIndex = true,
                        songs = uiState.library.filteredSongs,
                        isPlaying = false,
                        isPlayingPlaylist = uiState.playback.isPlayingPlaylist,
                        hasNext = uiState.playback.hasNext,
                        hasPrev = uiState.playback.hasPrev,
                        onPlay = { onPlaySongs(uiState.library.filteredSongs) },
                        onShuffle = { onShuffleSongs(uiState.library.filteredSongs) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        onAddToPlaylist = {
                            setPendingAddFiles(listOf(it))
                            setShowAddToPlaylistDialog(true)
                        },
                        favoriteUris = uiState.favoriteUris,
                        onToggleFavorite = onToggleFavorite,
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
            }
        }
}
