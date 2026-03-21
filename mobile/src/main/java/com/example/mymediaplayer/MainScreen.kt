package com.example.mymediaplayer

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mymediaplayer.shared.bucketGenre
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
import android.support.v4.media.session.PlaybackStateCompat

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
    onDecadeSelected: (String) -> Unit,
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
    bluetoothAutoPlayEnabled: Boolean,
    trackVoiceIntroEnabled: Boolean,
    trackVoiceOutroEnabled: Boolean,
    onToggleBluetoothAutoPlay: () -> Unit,
    onToggleTrackVoiceIntro: () -> Unit,
    onToggleTrackVoiceOutro: () -> Unit,
    onAddCurrentBluetoothDevice: () -> Unit,
    trustedBluetoothDevices: List<TrustedBluetoothDevice>,
    bluetoothDiagnostics: String,
    onRemoveTrustedBluetoothDevice: (String) -> Unit,
    onClearTrustedBluetoothDevices: () -> Unit,
    onRefreshBluetoothDiagnostics: () -> Unit,
    nowPlayingArt: Bitmap?,
    showPlaylistSaveFolderPrompt: Boolean,
    onDismissPlaylistSaveFolderPrompt: () -> Unit,
    onSetPlaylistSaveFolderNow: () -> Unit,
    cloudAnnouncementKiloKey: String,
    cloudAnnouncementTtsKey: String,
    onSaveCloudAnnouncementKeys: (kiloKey: String, ttsKey: String, onValidated: () -> Unit) -> Unit,
    debugCloudAnnouncements: Boolean,
    onSetDebugCloudAnnouncements: (Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistCountText by remember { mutableStateOf("3") }
    var showScanDialog by remember { mutableStateOf(false) }
    var scanCountText by remember { mutableStateOf(uiState.scan.lastScanLimit.toString()) }
    var scanDeepMode by remember { mutableStateOf(uiState.scan.deepScanEnabled) }
    var scanWholeDriveMode by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreateFromSelectionDialog by remember { mutableStateOf(false) }
    var createFromSelectionNameText by remember { mutableStateOf("") }
    var pendingAddFiles by remember { mutableStateOf<List<MediaFileInfo>>(emptyList()) }
    var localCreatedPlaylists by remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    var isSearchSelectionMode by remember { mutableStateOf(false) }
    var selectedSearchUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    var pendingDeletePlaylist by remember { mutableStateOf<PlaylistInfo?>(null) }
    var showRenamePlaylistDialog by remember { mutableStateOf(false) }
    var pendingRenamePlaylist by remember { mutableStateOf<PlaylistInfo?>(null) }
    var renamePlaylistNameText by remember { mutableStateOf("") }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showExpandedNowPlayingDialog by remember { mutableStateOf(false) }
    var showBluetoothDiagnosticsDialog by remember { mutableStateOf(false) }
    var showManageTrustedBluetoothDialog by remember { mutableStateOf(false) }
    var showCloudAnnouncementSettingsDialog by remember { mutableStateOf(false) }
    var songsFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var searchFavoritesOnly by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.search.searchQuery, uiState.search.searchResults) {
        selectedSearchUris = selectedSearchUris.intersect(uiState.search.searchResults.map { it.uriString }.toSet())
        if (selectedSearchUris.isEmpty()) {
            isSearchSelectionMode = false
        }
    }

    LaunchedEffect(uiState.scan.discoveredPlaylists) {
        val knownUris = uiState.scan.discoveredPlaylists.map { it.uriString }.toSet()
        localCreatedPlaylists = localCreatedPlaylists.filterNot { it.uriString in knownUris }
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
            TopAppBar(
                title = { Text("MyMediaPlayer") },
                actions = {
                    TextButton(onClick = { menuExpanded = true }) {
                        Text("Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Select Folder") },
                            onClick = {
                                menuExpanded = false
                                scanCountText = uiState.scan.lastScanLimit.toString()
                                scanDeepMode = uiState.scan.deepScanEnabled
                                showScanDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select Playlist Save Folder") },
                            onClick = {
                                menuExpanded = false
                                onChoosePlaylistSaveFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Create Random Playlist") },
                            onClick = {
                                menuExpanded = false
                                playlistCountText = uiState.playlist.lastPlaylistCount.toString()
                                showPlaylistDialog = true
                            },
                            enabled = uiState.scan.scannedFiles.isNotEmpty()
                        )
                        DropdownMenuItem(
                            text = { Text(if (trackVoiceIntroEnabled) "Track Voice Intro: On" else "Track Voice Intro: Off") },
                            onClick = {
                                menuExpanded = false
                                onToggleTrackVoiceIntro()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (trackVoiceOutroEnabled) "Track Voice Outro: On" else "Track Voice Outro: Off") },
                            onClick = {
                                menuExpanded = false
                                onToggleTrackVoiceOutro()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("AI Announcement Settings") },
                            onClick = {
                                menuExpanded = false
                                showCloudAnnouncementSettingsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (bluetoothAutoPlayEnabled) "Bluetooth Auto-Play: On" else "Bluetooth Auto-Play: Off") },
                            onClick = {
                                menuExpanded = false
                                onToggleBluetoothAutoPlay()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Trust Connected Bluetooth") },
                            onClick = {
                                menuExpanded = false
                                onAddCurrentBluetoothDevice()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage Trusted Bluetooth") },
                            onClick = {
                                menuExpanded = false
                                showManageTrustedBluetoothDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bluetooth Diagnostics") },
                            onClick = {
                                menuExpanded = false
                                onRefreshBluetoothDiagnostics()
                                showBluetoothDiagnosticsDialog = true
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.playback.currentTrackName != null) {
                PlaybackBar(
                    trackName = uiState.playback.currentTrackName,
                    artistName = uiState.playback.currentArtistName,
                    isPlaying = uiState.playback.isPlaying,
                    isPlayingPlaylist = uiState.playback.isPlayingPlaylist,
                    repeatMode = uiState.playback.repeatMode,
                    queueTitle = uiState.playback.queueTitle,
                    queuePosition = uiState.playback.queuePosition,
                    onPlayPause = onPlayPause,
                    onStop = onStop,
                    onNext = onNext,
                    onPrev = onPrev,
                    onToggleRepeat = onToggleRepeat,
                    onShowQueue = { showQueueDialog = true },
                    onOpenExpanded = { showExpandedNowPlayingDialog = true },
                    hasNext = uiState.playback.hasNext,
                    hasPrev = uiState.playback.hasPrev
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = uiState.search.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search title, artist, album, genre") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.search.searchQuery.isNotEmpty()) {
                    TextButton(onClick = onClearSearch) {
                        Text("Clear")
                    }
                }
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
                            onTabSelected(LibraryTab.Albums)
                            onAlbumSelected(album)
                        },
                        onAddAlbumToPlaylist = { songs ->
                            pendingAddFiles = songs
                            showAddToPlaylistDialog = true
                        },
                        onToggleFavorite = onToggleFavorite
                    )
                } else {
                    val selectedSearchResults = visibleSearchResults.filter {
                        it.uriString in selectedSearchUris
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            TextButton(onClick = { searchFavoritesOnly = !searchFavoritesOnly }) {
                                Text(if (searchFavoritesOnly) "All results" else "Favorites only")
                            }
                        }
                        item {
                            TextButton(
                                onClick = {
                                    pendingAddFiles = visibleSearchResults
                                    showAddToPlaylistDialog = true
                                }
                            ) {
                                Text("Add all")
                            }
                        }
                        item {
                            TextButton(
                                onClick = {
                                    isSearchSelectionMode = !isSearchSelectionMode
                                    if (!isSearchSelectionMode) selectedSearchUris = emptySet()
                                }
                            ) {
                                Text(if (isSearchSelectionMode) "Cancel selection" else "Select")
                            }
                        }
                        if (isSearchSelectionMode) {
                            item {
                                TextButton(
                                    onClick = {
                                        selectedSearchUris = visibleSearchResults.map { it.uriString }.toSet()
                                    },
                                    enabled = selectedSearchResults.size < visibleSearchResults.size
                                ) {
                                    Text("Select all")
                                }
                            }
                            item {
                                TextButton(
                                    onClick = { selectedSearchUris = emptySet() },
                                    enabled = selectedSearchResults.isNotEmpty()
                                ) {
                                    Text("Clear selection")
                                }
                            }
                            item {
                                TextButton(
                                    onClick = {
                                        pendingAddFiles = selectedSearchResults
                                        showAddToPlaylistDialog = true
                                    },
                                    enabled = selectedSearchResults.isNotEmpty()
                                ) {
                                    Text("Add selected (${selectedSearchResults.size})")
                                }
                            }
                        }
                    }
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
                            pendingAddFiles = listOf(it)
                            showAddToPlaylistDialog = true
                        },
                        onToggleFavorite = onToggleFavorite,
                        enableSelection = isSearchSelectionMode,
                        selectedUris = selectedSearchUris,
                        onSelectionToggle = { file ->
                            selectedSearchUris = if (file.uriString in selectedSearchUris) {
                                selectedSearchUris - file.uriString
                            } else {
                                selectedSearchUris + file.uriString
                            }
                        },
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val albumCounts = remember(uiState.scan.scannedFiles) { buildAlbumCounts(uiState.scan.scannedFiles) }
            val artistCounts = remember(uiState.scan.scannedFiles) { buildArtistCounts(uiState.scan.scannedFiles) }
            val genreCounts = remember(uiState.scan.scannedFiles) { buildGenreCounts(uiState.scan.scannedFiles) }
            val decadeCounts = remember(uiState.scan.scannedFiles) { buildDecadeCounts(uiState.scan.scannedFiles) }

            val tabs = LibraryTab.values().toList()
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(uiState.library.selectedTab)
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
                    val songsForTab = if (songsFavoritesOnly) {
                        uiState.scan.scannedFiles.filter { it.uriString in uiState.favoriteUris }
                    } else {
                        uiState.scan.scannedFiles
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { songsFavoritesOnly = !songsFavoritesOnly }) {
                            Text(if (songsFavoritesOnly) "Show all songs" else "Favorites only")
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
                        favoriteUris = uiState.favoriteUris,
                        isPlaying = false,
                        isPlayingPlaylist = uiState.playback.isPlayingPlaylist,
                        hasNext = uiState.playback.hasNext,
                        hasPrev = uiState.playback.hasPrev,
                        onPlay = { onPlaySongs(songsForTab) },
                        onShuffle = { onShuffleSongs(songsForTab) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        onAddToPlaylist = {
                            pendingAddFiles = listOf(it)
                            showAddToPlaylistDialog = true
                        },
                        onToggleFavorite = onToggleFavorite,
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
                LibraryTab.Playlists -> {
                    val smartPlaylists = remember(
                        uiState.favoriteUris,
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
                            pendingDeletePlaylist = it
                            showDeletePlaylistDialog = true
                        },
                        onRequestRenamePlaylist = {
                            pendingRenamePlaylist = it
                            renamePlaylistNameText = it.displayName.removeSuffix(".m3u")
                            showRenamePlaylistDialog = true
                        },
                        onSavePlaylistEdits = onSavePlaylistEdits,
                        onPlayPlaylist = onPlayPlaylist,
                        onShufflePlaylistSongs = onShufflePlaylistSongs,
                        onPlaySongs = onPlaySongs,
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        onAddToPlaylist = {
                            pendingAddFiles = listOf(it)
                            showAddToPlaylistDialog = true
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
                            pendingAddFiles = listOf(it)
                            showAddToPlaylistDialog = true
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
                            pendingAddFiles = listOf(it)
                            showAddToPlaylistDialog = true
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
                            pendingAddFiles = listOf(it)
                            showAddToPlaylistDialog = true
                        },
                        favoriteUris = uiState.favoriteUris,
                        onToggleFavorite = onToggleFavorite,
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
                LibraryTab.Decades -> {
                    CategoryTabContent(
                        title = "Decades",
                        categories = uiState.library.decades,
                        categoryCounts = decadeCounts,
                        isLoading = uiState.library.isMetadataLoading,
                        selectedLabel = uiState.library.selectedDecade,
                        onCategorySelected = onDecadeSelected,
                        onClearCategorySelection = onClearCategorySelection,
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
                            pendingAddFiles = listOf(it)
                            showAddToPlaylistDialog = true
                        },
                        favoriteUris = uiState.favoriteUris,
                        onToggleFavorite = onToggleFavorite,
                        currentMediaId = uiState.playback.currentMediaId
                    )
                }
            }
        }
    }

    if (showPlaylistDialog) {
        val maxCount = uiState.scan.scannedFiles.size
        val countValue = playlistCountText.toIntOrNull()
        val isValid = countValue != null && countValue in 1..maxCount
        val helperText = when {
            maxCount == 0 -> "Scan a folder to enable playlists."
            countValue == null -> "Enter a number between 1 and $maxCount."
            countValue < 1 || countValue > maxCount -> "Enter a number between 1 and $maxCount."
            else -> "OK"
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Create Random Playlist") },
            text = {
                Column {
                    Text("How many songs should be added?")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = playlistCountText,
                        onValueChange = { playlistCountText = it },
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
                            showPlaylistDialog = false
                            onCreatePlaylist(countValue!!)
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showScanDialog) {
        val countValue = scanCountText.toIntOrNull()
        val isValid = countValue != null && countValue > 0
        val helperText = when {
            countValue == null -> "Enter a number greater than 0."
            countValue <= 0 -> "Enter a number greater than 0."
            else -> "OK"
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Scan Music") },
            text = {
                Column {
                    Text("How many songs should be scanned?")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = scanCountText,
                        onValueChange = { scanCountText = it },
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
                            onCheckedChange = { scanWholeDriveMode = it }
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
                            onCheckedChange = { scanDeepMode = it }
                        )
                            Text("Deep scan (slower, tries unknown file types)")
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
                            showScanDialog = false
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
                TextButton(onClick = { showScanDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddToPlaylistDialog) {
        val files = pendingAddFiles
        val firstName = files.firstOrNull()?.cleanTitle ?: "songs"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
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
                                createFromSelectionNameText = ""
                                showCreateFromSelectionDialog = true
                            }
                        },
                        enabled = files.isNotEmpty()
                    ) {
                        Text("Create new playlist")
                    }
                    val visiblePlaylists = (localCreatedPlaylists + uiState.scan.discoveredPlaylists)
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
                                        showAddToPlaylistDialog = false
                                        pendingAddFiles = emptyList()
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
                TextButton(onClick = {
                    showAddToPlaylistDialog = false
                    pendingAddFiles = emptyList()
                }) {
                    Text("Close")
                }
            }
        )
    }

    if (showCreateFromSelectionDialog) {
        val nameTrimmed = createFromSelectionNameText.trim()
        val files = pendingAddFiles
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateFromSelectionDialog = false },
            title = { Text("New playlist") },
            text = {
                Column {
                    Text("Playlist name (optional)")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = createFromSelectionNameText,
                        onValueChange = { createFromSelectionNameText = it },
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
                            localCreatedPlaylists = (localCreatedPlaylists + created)
                                .distinctBy { it.uriString }
                            showCreateFromSelectionDialog = false
                            showAddToPlaylistDialog = false
                            pendingAddFiles = emptyList()
                            createFromSelectionNameText = ""
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
                    TextButton(onClick = { showCreateFromSelectionDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showDeletePlaylistDialog) {
        val target = pendingDeletePlaylist
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeletePlaylistDialog = false },
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
                        showDeletePlaylistDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenamePlaylistDialog) {
        val target = pendingRenamePlaylist
        val trimmedName = renamePlaylistNameText.trim()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRenamePlaylistDialog = false },
            title = { Text("Rename playlist") },
            text = {
                Column {
                    Text("Enter a new name")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = renamePlaylistNameText,
                        onValueChange = { renamePlaylistNameText = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Road Trip") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (target != null) onRenamePlaylist(target, trimmedName)
                        showRenamePlaylistDialog = false
                        pendingRenamePlaylist = null
                    },
                    enabled = target != null && trimmedName.isNotEmpty()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenamePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExpandedNowPlayingDialog && uiState.playback.currentTrackName != null) {
        val currentMediaId = uiState.playback.currentMediaId
        val context = LocalContext.current
        val flaggedUris = remember { mutableStateOf(emptySet<String>()) }
        LaunchedEffect(currentMediaId) {
            val prefs = context.getSharedPreferences("mymediaplayer_prefs", android.content.Context.MODE_PRIVATE)
            flaggedUris.value = prefs.getStringSet("flagged_uris", emptySet())?.toSet() ?: emptySet()
        }
        val isFlagged = currentMediaId != null && currentMediaId in flaggedUris.value
        ExpandedNowPlayingDialog(
            trackName = uiState.playback.currentTrackName,
            artistName = uiState.playback.currentArtistName,
            album = uiState.playback.currentAlbum,
            genre = uiState.playback.currentGenre,
            year = uiState.playback.currentYear,
            artwork = nowPlayingArt,
            currentPositionMs = uiState.playback.currentPositionMs,
            positionUpdatedAtElapsedMs = uiState.playback.positionUpdatedAtElapsedMs,
            durationMs = uiState.playback.durationMs,
            isPlaying = uiState.playback.isPlaying,
            playbackSpeed = uiState.playback.playbackSpeed,
            hasPrev = uiState.playback.hasPrev,
            hasNext = uiState.playback.hasNext,
            isPlayingPlaylist = uiState.playback.isPlayingPlaylist,
            isFlagged = isFlagged,
            onSeekTo = onSeekTo,
            onPlayPause = onPlayPause,
            onPrev = onPrev,
            onNext = onNext,
            onToggleFlag = {
                if (currentMediaId != null) {
                    val prefs = context.getSharedPreferences("mymediaplayer_prefs", android.content.Context.MODE_PRIVATE)
                    val current = prefs.getStringSet("flagged_uris", emptySet())?.toMutableSet() ?: mutableSetOf()
                    if (currentMediaId in current) current.remove(currentMediaId) else current.add(currentMediaId)
                    prefs.edit().putStringSet("flagged_uris", current).apply()
                    flaggedUris.value = current.toSet()
                }
            },
            onDismiss = { showExpandedNowPlayingDialog = false }
        )
    }

    if (showQueueDialog) {
        AlertDialog(
            onDismissRequest = { showQueueDialog = false },
            title = {
                Text(uiState.playback.queueTitle ?: "Queue")
            },
            text = {
                if (uiState.playback.queueItems.isEmpty()) {
                    Text("Queue is empty")
                } else {
                    LazyColumn {
                        items(uiState.playback.queueItems) { item ->
                            val isActive = item.queueId == uiState.playback.activeQueueId
                            TextButton(
                                onClick = {
                                    onQueueItemSelected(item.queueId)
                                    showQueueDialog = false
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
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQueueDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showManageTrustedBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showManageTrustedBluetoothDialog = false },
            title = { Text("Trusted Bluetooth Devices") },
            text = {
                Column {
                    if (trustedBluetoothDevices.isEmpty()) {
                        Text("No trusted devices yet")
                    } else {
                        LazyColumn {
                            items(trustedBluetoothDevices) { device ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.name?.ifBlank { null } ?: "Unknown device",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = device.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(onClick = { onRemoveTrustedBluetoothDevice(device.address) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onClearTrustedBluetoothDevices() },
                    enabled = trustedBluetoothDevices.isNotEmpty()
                ) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManageTrustedBluetoothDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showBluetoothDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDiagnosticsDialog = false },
            title = { Text("Bluetooth Diagnostics") },
            text = {
                Text(bluetoothDiagnostics)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRefreshBluetoothDiagnostics()
                    }
                ) {
                    Text("Refresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBluetoothDiagnosticsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showCloudAnnouncementSettingsDialog) {
        var kiloKeyInput by remember { mutableStateOf(cloudAnnouncementKiloKey) }
        var ttsKeyInput by remember { mutableStateOf(cloudAnnouncementTtsKey) }
        AlertDialog(
            onDismissRequest = { showCloudAnnouncementSettingsDialog = false },
            title = { Text("AI Announcement Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "API keys for cloud-generated announcements. Kilo works anonymously (no key needed). Google TTS key optional for higher quality.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextField(
                        value = kiloKeyInput,
                        onValueChange = { kiloKeyInput = it },
                        label = { Text("Kilo API key (optional)") },
                        placeholder = { Text("Leave blank for anonymous") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = ttsKeyInput,
                        onValueChange = { ttsKeyInput = it },
                        label = { Text("Google Cloud TTS key (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Debug mode", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Show toast when cloud TTS is used",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = debugCloudAnnouncements,
                            onCheckedChange = onSetDebugCloudAnnouncements
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                        onClick = {
                            onSaveCloudAnnouncementKeys(kiloKeyInput.trim(), ttsKeyInput.trim()) {
                                showCloudAnnouncementSettingsDialog = false
                            }
                        }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCloudAnnouncementSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPlaylistSaveFolderPrompt) {
        AlertDialog(
            onDismissRequest = onDismissPlaylistSaveFolderPrompt,
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
                TextButton(onClick = onDismissPlaylistSaveFolderPrompt) {
                    Text("Later")
                }
            }
        )
    }
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
    var selectedLetter by rememberSaveable(title) { mutableStateOf<String?>(null) }

    Column {
        if (headerContent != null) {
            headerContent()
            Spacer(modifier = Modifier.height(8.dp))
        }
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
                                    onClick = { selectedLetter = null },
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    Text("All")
                                }
                            }
                            items(letters) { letter ->
                                TextButton(
                                    onClick = { selectedLetter = letter },
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

        Spacer(modifier = Modifier.height(8.dp))

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
}

private fun buildAlbumCounts(files: List<MediaFileInfo>): Map<String, Int> =
    files.groupingBy { file ->
        file.album?.ifBlank { null } ?: "Unknown Album"
    }.eachCount()

private fun buildArtistCounts(files: List<MediaFileInfo>): Map<String, Int> =
    files.groupingBy { file ->
        file.artist?.ifBlank { null } ?: "Unknown Artist"
    }.eachCount()

private fun buildGenreCounts(files: List<MediaFileInfo>): Map<String, Int> =
    files.groupingBy { file ->
        bucketGenre(file.genre)
    }.eachCount()

private fun buildDecadeCounts(files: List<MediaFileInfo>): Map<String, Int> =
    files.groupingBy { file ->
        decadeLabelForYear(file.year)
    }.eachCount()

private fun decadeLabelForYear(year: Int?): String {
    if (year == null || year <= 0) return "Unknown Decade"
    val decade = (year / 10) * 10
    return "${decade}s"
}

private fun albumLabel(file: MediaFileInfo): String =
    file.album?.ifBlank { null } ?: "Unknown Album"

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
                                val targetFavorite = !allFavorited
                                hit.songs.forEach { song ->
                                    val currentlyFavorite = song.uriString in favoriteUris
                                    if (currentlyFavorite != targetFavorite) {
                                        onToggleFavorite(song)
                                    }
                                }
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
    onSavePlaylistEdits: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onPlayPlaylist: (PlaylistInfo) -> Unit,
    onShufflePlaylistSongs: (PlaylistInfo, List<MediaFileInfo>) -> Unit,
    onPlaySongs: (List<MediaFileInfo>) -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onAddToPlaylist: (MediaFileInfo) -> Unit,
    favoriteUris: Set<String>,
    onToggleFavorite: (MediaFileInfo) -> Unit,
    currentMediaId: String?
) {
    var isEditing by remember(selectedPlaylist?.uriString) { mutableStateOf(false) }
    var editableSongs by remember(selectedPlaylist?.uriString) { mutableStateOf<List<MediaFileInfo>>(emptyList()) }
    var draggingIndex by remember(selectedPlaylist?.uriString) { mutableStateOf<Int?>(null) }
    var draggingOffsetY by remember(selectedPlaylist?.uriString) { mutableStateOf(0f) }
    var dedupeOnSave by remember(selectedPlaylist?.uriString) { mutableStateOf(false) }
    var editSearchQuery by remember(selectedPlaylist?.uriString) { mutableStateOf("") }
    var showDiscardChangesDialog by remember(selectedPlaylist?.uriString) { mutableStateOf(false) }
    var pendingSelectPlaylist by remember(selectedPlaylist?.uriString) { mutableStateOf<PlaylistInfo?>(null) }
    var pendingClearSelection by remember(selectedPlaylist?.uriString) { mutableStateOf(false) }
    val dragSwapThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    LaunchedEffect(selectedPlaylist?.uriString, playlistSongs, isEditing) {
        if (!isEditing) {
            editableSongs = playlistSongs
            dedupeOnSave = false
            editSearchQuery = ""
        }
    }
    val hasUnsavedChanges = isEditing && (editableSongs != playlistSongs || dedupeOnSave)

    if (playlists.isEmpty()) {
        Text("No playlists found")
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            val visiblePlaylists = if (selectedPlaylist == null) {
                playlists
            } else {
                playlists.filter { it.uriString == selectedPlaylist.uriString }
            }
            items(visiblePlaylists) { playlist ->
                val isSmart = playlist.uriString.startsWith(MainViewModel.SMART_PREFIX)
                PlaylistCard(
                    playlist = playlist,
                    isCompact = selectedPlaylist != null,
                    isSmart = isSmart,
                    onRename = { if (!isSmart) onRequestRenamePlaylist(playlist) },
                    onDelete = { if (!isSmart) onRequestDeletePlaylist(playlist) },
                    onClick = {
                        if (hasUnsavedChanges && selectedPlaylist?.uriString != playlist.uriString) {
                            pendingSelectPlaylist = playlist
                            pendingClearSelection = false
                            showDiscardChangesDialog = true
                        } else {
                            onPlaylistSelected(playlist)
                        }
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (selectedPlaylist == null) {
                Text("Select a playlist to view songs")
                return@Column
            }

            TextButton(
                onClick = {
                    if (hasUnsavedChanges) {
                        pendingSelectPlaylist = null
                        pendingClearSelection = true
                        showDiscardChangesDialog = true
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
                            editableSongs = playlistSongs
                            isEditing = true
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
                            editableSongs = songsToSave
                            isEditing = false
                            draggingIndex = null
                            draggingOffsetY = 0f
                            dedupeOnSave = false
                            editSearchQuery = ""
                        },
                        enabled = editableSongs.isNotEmpty()
                    ) {
                        Text("Save")
                    }
                    TextButton(
                        onClick = {
                            editableSongs = playlistSongs
                            isEditing = false
                            draggingIndex = null
                            draggingOffsetY = 0f
                            dedupeOnSave = false
                            editSearchQuery = ""
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
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

            val displayedSongs = if (isEditing) editableSongs else playlistSongs
            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = editSearchQuery,
                        onValueChange = { editSearchQuery = it },
                        singleLine = true,
                        placeholder = { Text("Filter songs while editing") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { dedupeOnSave = !dedupeOnSave }) {
                        Text(if (dedupeOnSave) "Dedup: On" else "Dedup: Off")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (displayedSongs.isEmpty()) {
                Text("No songs in playlist")
            } else {
                val filteredRows = if (isEditing && editSearchQuery.isNotBlank()) {
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
                } else if (isEditing) {
                    editableSongs.withIndex().toList()
                } else {
                    emptyList()
                }
                LazyColumn {
                    if (!isEditing) {
                        items(displayedSongs) { file ->
                            FileCard(
                                file = file,
                                isCurrentTrack = file.uriString == currentMediaId,
                                onClick = { onFileClick(file) },
                                onAddToPlaylist = { onAddToPlaylist(file) },
                                isFavorite = file.uriString in favoriteUris,
                                onToggleFavorite = { onToggleFavorite(file) }
                            )
                        }
                    } else {
                        items(filteredRows, key = { "${it.index}|${it.value.uriString}" }) { row ->
                            val sourceIndex = row.index
                            val file = row.value
                            val canDrag = editSearchQuery.isBlank()
                            val dragModifier = if (canDrag) {
                                Modifier.pointerInput(sourceIndex, editableSongs) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = sourceIndex
                                            draggingOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            draggingOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            draggingOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (draggingIndex != sourceIndex) return@detectDragGesturesAfterLongPress
                                            change.consume()
                                            draggingOffsetY += dragAmount.y
                                            if (draggingOffsetY > dragSwapThresholdPx && sourceIndex < editableSongs.lastIndex) {
                                                val list = editableSongs.toMutableList()
                                                val tmp = list[sourceIndex + 1]
                                                list[sourceIndex + 1] = list[sourceIndex]
                                                list[sourceIndex] = tmp
                                                editableSongs = list
                                                draggingIndex = sourceIndex + 1
                                                draggingOffsetY -= dragSwapThresholdPx
                                            } else if (draggingOffsetY < -dragSwapThresholdPx && sourceIndex > 0) {
                                                val list = editableSongs.toMutableList()
                                                val tmp = list[sourceIndex - 1]
                                                list[sourceIndex - 1] = list[sourceIndex]
                                                list[sourceIndex] = tmp
                                                editableSongs = list
                                                draggingIndex = sourceIndex - 1
                                                draggingOffsetY += dragSwapThresholdPx
                                            }
                                        }
                                    )
                                }
                            } else {
                                Modifier
                            }
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
                                            translationY = if (draggingIndex == sourceIndex) draggingOffsetY else 0f
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
                                        onClick = {
                                            if (sourceIndex in editableSongs.indices) {
                                                val list = editableSongs.toMutableList()
                                                list.removeAt(sourceIndex)
                                                editableSongs = list
                                            }
                                        }
                                    ) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardChangesDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardChangesDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved playlist edits.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardChangesDialog = false
                        isEditing = false
                        editableSongs = playlistSongs
                        dedupeOnSave = false
                        editSearchQuery = ""
                        val nextSelection = pendingSelectPlaylist
                        val clearSelection = pendingClearSelection
                        pendingSelectPlaylist = null
                        pendingClearSelection = false
                        if (clearSelection) onClearPlaylistSelection()
                        if (nextSelection != null) onPlaylistSelected(nextSelection)
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardChangesDialog = false
                        pendingSelectPlaylist = null
                        pendingClearSelection = false
                    }
                ) {
                    Text("Keep editing")
                }
            }
        )
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
    isPlayingPlaylist: Boolean,
    repeatMode: Int,
    queueTitle: String?,
    hasNext: Boolean,
    hasPrev: Boolean,
    queuePosition: String?,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleRepeat: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenExpanded: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenExpanded() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
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
            if (queuePosition != null) {
                Text(
                    text = "${queueTitle ?: "Queue"} • Track $queuePosition",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onPlayPause) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
                TextButton(onClick = onToggleRepeat) {
                    Text("Repeat: ${repeatModeShortLabel(repeatMode)}")
                }
                TextButton(onClick = onShowQueue) {
                    Text("Queue")
                }
                if (isPlayingPlaylist) {
                    TextButton(onClick = onPrev, enabled = hasPrev) {
                        Text("Prev")
                    }
                    TextButton(onClick = onNext, enabled = hasNext) {
                        Text("Next")
                    }
                }
                TextButton(onClick = onStop) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun ExpandedNowPlayingDialog(
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
        mutableStateOf(clampedProjectedMs.toFloat())
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
                        }
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
                        Text(if (isFlagged) "Unflag" else "Flag")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun formatPlaybackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun repeatModeShortLabel(mode: Int): String {
    return when (mode) {
        PlaybackStateCompat.REPEAT_MODE_ALL -> "All"
        PlaybackStateCompat.REPEAT_MODE_ONE -> "One"
        else -> "Off"
    }
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
fun FileCard(
    file: MediaFileInfo,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isSelectionEnabled: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null
) {
    val colors = if (isCurrentTrack) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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
            if (onAddToPlaylist != null || onToggleFavorite != null || (isSelectionEnabled && onSelectionToggle != null)) {
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
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
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

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
