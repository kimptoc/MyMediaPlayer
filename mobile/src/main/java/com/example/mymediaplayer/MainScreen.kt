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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
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
    nowPlayingArt: Bitmap?,
    showPlaylistSaveFolderPrompt: Boolean,
    onDismissPlaylistSaveFolderPrompt: () -> Unit,
    onSetPlaylistSaveFolderNow: () -> Unit,
    onOpenSettings: () -> Unit,
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
    var songsFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var searchFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.search.searchQuery) {
        if (uiState.search.searchQuery.isNotBlank()) {
            isSearchExpanded = true
        }
    }

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
                                    isSearchExpanded = false
                                    onClearSearch()
                                }) { Text("Back") }
                            }
                        },
                        actions = {
                            if (!isSearchExpanded) {
                                TextButton(onClick = { isSearchExpanded = true }) { Text("Search") }
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
                                        text = { Text("Create Random Playlist") },
                                        onClick = {
                                            menuExpanded = false
                                            playlistCountText = uiState.playlist.lastPlaylistCount.toString()
                                            showPlaylistDialog = true
                                        },
                                        enabled = uiState.scan.scannedFiles.isNotEmpty()
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSettings()
                                        }
                                    )
                                }
                            } else if (uiState.search.searchQuery.isNotEmpty()) {
                                TextButton(onClick = onClearSearch) { Text("Clear") }
                            }
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
                    onOpenExpanded = { showExpandedNowPlayingDialog = true }
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
                    SearchResultsActionRow(
                        searchFavoritesOnly = searchFavoritesOnly,
                        onToggleSearchFavoritesOnly = { searchFavoritesOnly = !searchFavoritesOnly },
                        visibleSearchResults = visibleSearchResults,
                        onAddAll = {
                            pendingAddFiles = it
                            showAddToPlaylistDialog = true
                        },
                        isSearchSelectionMode = isSearchSelectionMode,
                        onToggleSearchSelectionMode = {
                            isSearchSelectionMode = !isSearchSelectionMode
                            if (!isSearchSelectionMode) selectedSearchUris = emptySet()
                        },
                        selectedSearchUris = selectedSearchUris,
                        onSelectAll = { selectedSearchUris = it },
                        onClearSelection = { selectedSearchUris = emptySet() },
                        onAddSelected = {
                            pendingAddFiles = it
                            showAddToPlaylistDialog = true
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
                    var selectedDecade by rememberSaveable { mutableStateOf<String?>(null) }
                    val availableDecades = remember(uiState.scan.scannedFiles) {
                        uiState.scan.scannedFiles
                            .map { decadeLabelForYear(it.year) }
                            .filter { it != "Unknown Decade" }
                            .distinct()
                            .sorted()
                    }
                    SongsTabContent(
                        songsFavoritesOnly = songsFavoritesOnly,
                        onToggleSongsFavoritesOnly = { songsFavoritesOnly = !songsFavoritesOnly },
                        selectedDecade = selectedDecade,
                        availableDecades = availableDecades,
                        onDecadeSelected = { selectedDecade = it },
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
            }
        }
    }

    if (showPlaylistDialog) {
        CreateRandomPlaylistDialog(
            maxCount = uiState.scan.scannedFiles.size,
            playlistCountText = playlistCountText,
            onPlaylistCountTextChange = { playlistCountText = it },
            onDismissRequest = { showPlaylistDialog = false },
            onCreatePlaylist = onCreatePlaylist
        )
    }

    if (showScanDialog) {
        ScanDialogContent(
            scanCountText = scanCountText,
            onScanCountTextChange = { scanCountText = it },
            scanWholeDriveMode = scanWholeDriveMode,
            onScanWholeDriveModeChange = { scanWholeDriveMode = it },
            scanDeepMode = scanDeepMode,
            onScanDeepModeChange = { scanDeepMode = it },
            onDismissRequest = { showScanDialog = false },
            onScanWholeDriveWithLimit = onScanWholeDriveWithLimit,
            onSelectFolderWithLimit = onSelectFolderWithLimit
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialogContent(
            pendingAddFiles = pendingAddFiles,
            localCreatedPlaylists = localCreatedPlaylists,
            discoveredPlaylists = uiState.scan.discoveredPlaylists,
            onDismissRequest = {
                showAddToPlaylistDialog = false
                pendingAddFiles = emptyList()
            },
            onCreateNewPlaylistClick = {
                createFromSelectionNameText = ""
                showCreateFromSelectionDialog = true
            },
            onAddToExistingPlaylist = { playlist, files ->
                onAddToExistingPlaylist(playlist, files)
                showAddToPlaylistDialog = false
                pendingAddFiles = emptyList()
            }
        )
    }

    if (showCreateFromSelectionDialog) {
        CreateFromSelectionDialogContent(
            createFromSelectionNameText = createFromSelectionNameText,
            onCreateFromSelectionNameTextChange = { createFromSelectionNameText = it },
            pendingAddFiles = pendingAddFiles,
            onCreatePlaylistFromSongs = onCreatePlaylistFromSongs,
            onChoosePlaylistSaveFolder = onChoosePlaylistSaveFolder,
            onPlaylistCreated = { created ->
                localCreatedPlaylists = (localCreatedPlaylists + created).distinctBy { it.uriString }
                showCreateFromSelectionDialog = false
                showAddToPlaylistDialog = false
                pendingAddFiles = emptyList()
                createFromSelectionNameText = ""
            },
            onDismissRequest = { showCreateFromSelectionDialog = false }
        )
    }

    if (showDeletePlaylistDialog) {
        DeletePlaylistDialogContent(
            pendingDeletePlaylist = pendingDeletePlaylist,
            onDismissRequest = { showDeletePlaylistDialog = false },
            onDeletePlaylist = { playlist ->
                onDeletePlaylist(playlist)
                showDeletePlaylistDialog = false
            }
        )
    }

    if (showRenamePlaylistDialog) {
        RenamePlaylistDialogContent(
            pendingRenamePlaylist = pendingRenamePlaylist,
            renamePlaylistNameText = renamePlaylistNameText,
            onRenamePlaylistNameTextChange = { renamePlaylistNameText = it },
            onDismissRequest = { showRenamePlaylistDialog = false },
            onRenamePlaylist = { playlist, newName ->
                onRenamePlaylist(playlist, newName)
                showRenamePlaylistDialog = false
                pendingRenamePlaylist = null
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
                    prefs.edit { putStringSet("flagged_uris", current) }
                    flaggedUris.value = current.toSet()
                }
            },
            onDismiss = { showExpandedNowPlayingDialog = false }
        )
    }

    if (showQueueDialog) {
        QueueDialogContent(
            queueTitle = uiState.playback.queueTitle,
            queueItems = uiState.playback.queueItems,
            activeQueueId = uiState.playback.activeQueueId,
            onDismissRequest = { showQueueDialog = false },
            onQueueItemSelected = { queueId ->
                onQueueItemSelected(queueId)
                showQueueDialog = false
            }
        )
    }

    if (showPlaylistSaveFolderPrompt) {
        PlaylistSaveFolderPromptDialogContent(
            onDismissRequest = onDismissPlaylistSaveFolderPrompt,
            onSetPlaylistSaveFolderNow = onSetPlaylistSaveFolderNow
        )
    }
}

@Composable
private fun DeletePlaylistDialogContent(
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
private fun RenamePlaylistDialogContent(
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
private fun QueueDialogContent(
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
private fun PlaylistSaveFolderPromptDialogContent(
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
private fun CreateFromSelectionDialogContent(
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
private fun AddToPlaylistDialogContent(
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
private fun ScanDialogContent(
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
private fun CreateRandomPlaylistDialog(
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
    var selectedLetter by rememberSaveable(title) { mutableStateOf<String?>(null) }

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
            onSelectedLetterChanged = { selectedLetter = it },
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
    val selectedSearchResults = visibleSearchResults.filter {
        it.uriString in selectedSearchUris
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
    var draggingOffsetY by remember(selectedPlaylist?.uriString) { mutableFloatStateOf(0f) }
    var dedupeOnSave by remember(selectedPlaylist?.uriString) { mutableStateOf(false) }
    var editSearchQuery by remember(selectedPlaylist?.uriString) { mutableStateOf("") }
    var showDiscardChangesDialog by remember(selectedPlaylist?.uriString) { mutableStateOf(false) }
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

    if (selectedPlaylist == null) {
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
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                TextButton(
                onClick = {
                    if (hasUnsavedChanges) {
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
                        val clearSelection = pendingClearSelection
                        pendingClearSelection = false
                        if (clearSelection) onClearPlaylistSelection()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardChangesDialog = false
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
                NowPlayingArtwork(artwork)
                NowPlayingTrackInfo(trackName, artistName, album, genre, year)
                NowPlayingSlider(
                    durationSafe = durationSafe,
                    seekValueMs = seekValueMs,
                    onSeekValueChange = { seekValueMs = it },
                    onIsSeekingChange = { isSeeking = it },
                    onSeekTo = onSeekTo
                )
                NowPlayingControls(isPlaying, hasPrev, hasNext, isPlayingPlaylist, isFlagged, onPlayPause, onPrev, onNext, onToggleFlag)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun NowPlayingArtwork(artwork: android.graphics.Bitmap?) {
    if (artwork != null) {
        Image(
            bitmap = artwork.asImageBitmap(),
            contentDescription = "Album art",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
    }
}

@Composable
private fun NowPlayingTrackInfo(
    trackName: String,
    artistName: String?,
    album: String?,
    genre: String?,
    year: Long
) {
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
}

@Composable
private fun NowPlayingSlider(
    durationSafe: Long,
    seekValueMs: Float,
    onSeekValueChange: (Float) -> Unit,
    onIsSeekingChange: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    if (durationSafe > 0L) {
        Slider(
            value = seekValueMs.coerceIn(0f, durationSafe.toFloat()),
            onValueChange = {
                onIsSeekingChange(true)
                onSeekValueChange(it)
            },
            valueRange = 0f..durationSafe.toFloat(),
            onValueChangeFinished = {
                onIsSeekingChange(false)
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
}

@Composable
private fun NowPlayingControls(
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    isPlayingPlaylist: Boolean,
    isFlagged: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleFlag: () -> Unit
) {
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
private fun formatPlaybackDuration(durationMs: Long): String {
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
    val filteredByDecade = if (selectedDecade != null) {
        scannedFiles.filter { decadeLabelForYear(it.year) == selectedDecade }
    } else {
        scannedFiles
    }
    val songsForTab = if (songsFavoritesOnly) {
        filteredByDecade.filter { it.uriString in favoriteUris }
    } else {
        filteredByDecade
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onToggleSongsFavoritesOnly) {
            Text(if (songsFavoritesOnly) "Show all songs" else "Favorites only")
        }

        var decadeMenuExpanded by remember { mutableStateOf(false) }
        TextButton(onClick = { decadeMenuExpanded = true }) {
            Text("Decade: ${selectedDecade ?: "All"}")
        }
        DropdownMenu(
            expanded = decadeMenuExpanded,
            onDismissRequest = { decadeMenuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All") },
                onClick = {
                    decadeMenuExpanded = false
                    onDecadeSelected(null)
                }
            )
            availableDecades.forEach { decade ->
                DropdownMenuItem(
                    text = { Text(decade) },
                    onClick = {
                        decadeMenuExpanded = false
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
