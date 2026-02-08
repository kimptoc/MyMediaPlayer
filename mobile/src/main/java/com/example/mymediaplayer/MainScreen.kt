package com.example.mymediaplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onSelectFolderWithLimit: (Int) -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onCreatePlaylist: (Int) -> Unit,
    onPlaylistMessageDismissed: () -> Unit,
    onFolderMessageDismissed: () -> Unit,
    onScanMessageDismissed: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onAlbumSelected: (String) -> Unit,
    onGenreSelected: (String) -> Unit,
    onArtistSelected: (String) -> Unit,
    onDecadeSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearCategorySelection: () -> Unit,
    onPlaylistSelected: (PlaylistInfo) -> Unit,
    onPlaySongs: (List<MediaFileInfo>) -> Unit,
    onShuffleSongs: (List<MediaFileInfo>) -> Unit,
    onPlaySearchResults: (List<MediaFileInfo>) -> Unit,
    onShuffleSearchResults: (List<MediaFileInfo>) -> Unit,
    onPlayPlaylist: (PlaylistInfo) -> Unit,
    onShufflePlaylistSongs: (List<MediaFileInfo>) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistCountText by remember { mutableStateOf("3") }
    var showScanDialog by remember { mutableStateOf(false) }
    var scanCountText by remember { mutableStateOf(uiState.lastScanLimit.toString()) }

    LaunchedEffect(uiState.playlistMessage) {
        val message = uiState.playlistMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onPlaylistMessageDismissed()
        }
    }

    LaunchedEffect(uiState.folderMessage) {
        val message = uiState.folderMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onFolderMessageDismissed()
        }
    }

    LaunchedEffect(uiState.scanMessage) {
        val message = uiState.scanMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onScanMessageDismissed()
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
                            scanCountText = uiState.lastScanLimit.toString()
                            showScanDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Create Playlist") },
                        onClick = {
                            menuExpanded = false
                            playlistCountText = uiState.lastPlaylistCount.toString()
                            showPlaylistDialog = true
                        },
                        enabled = uiState.scannedFiles.isNotEmpty()
                    )
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.currentTrackName != null) {
                PlaybackBar(
                    trackName = uiState.currentTrackName,
                    isPlaying = uiState.isPlaying,
                    isPlayingPlaylist = uiState.isPlayingPlaylist,
                    queuePosition = uiState.queuePosition,
                    onPlayPause = onPlayPause,
                    onStop = onStop,
                    onNext = onNext,
                    onPrev = onPrev,
                    hasNext = uiState.hasNext,
                    hasPrev = uiState.hasPrev
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
            if (uiState.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                uiState.scanProgress?.let { progress ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            TextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Search title, artist, album, genre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.searchResults.isEmpty()) {
                    Text("No results for \"${uiState.searchQuery}\"")
                } else {
                    SongsListSection(
                        title = "Search Results (${uiState.searchResults.size})",
                        songs = uiState.searchResults,
                        isPlaying = uiState.isPlaying || uiState.isPlayingPlaylist,
                        isPlayingPlaylist = uiState.isPlayingPlaylist,
                        hasNext = uiState.hasNext,
                        hasPrev = uiState.hasPrev,
                        onPlay = { onPlaySearchResults(uiState.searchResults) },
                        onShuffle = { onShuffleSearchResults(uiState.searchResults) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        currentMediaId = uiState.currentMediaId
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val tabs = LibraryTab.values().toList()
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(uiState.selectedTab)
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(tab.label, maxLines = 1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (uiState.selectedTab) {
                LibraryTab.Songs -> {
                    SongsListSection(
                        title = "${uiState.scannedFiles.size} file(s) found",
                        songs = uiState.scannedFiles,
                        isPlaying = uiState.isPlaying || uiState.isPlayingPlaylist,
                        isPlayingPlaylist = uiState.isPlayingPlaylist,
                        hasNext = uiState.hasNext,
                        hasPrev = uiState.hasPrev,
                        onPlay = { onPlaySongs(uiState.scannedFiles) },
                        onShuffle = { onShuffleSongs(uiState.scannedFiles) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        currentMediaId = uiState.currentMediaId
                    )
                }
                LibraryTab.Playlists -> {
                    PlaylistsSection(
                        playlists = uiState.discoveredPlaylists,
                        selectedPlaylist = uiState.selectedPlaylist,
                        playlistSongs = uiState.playlistSongs,
                        isLoading = uiState.isPlaylistLoading,
                        isPlaying = uiState.isPlaying || uiState.isPlayingPlaylist,
                        isPlayingPlaylist = uiState.isPlayingPlaylist,
                        hasNext = uiState.hasNext,
                        hasPrev = uiState.hasPrev,
                        onPlaylistSelected = onPlaylistSelected,
                        onPlayPlaylist = onPlayPlaylist,
                        onShufflePlaylistSongs = onShufflePlaylistSongs,
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        currentMediaId = uiState.currentMediaId
                    )
                }
                LibraryTab.Albums -> {
                    CategoryTabContent(
                        title = "Albums",
                        categories = uiState.albums,
                        isLoading = uiState.isMetadataLoading,
                        selectedLabel = uiState.selectedAlbum,
                        onCategorySelected = onAlbumSelected,
                        onClearCategorySelection = onClearCategorySelection,
                        songs = uiState.filteredSongs,
                        isPlaying = uiState.isPlaying || uiState.isPlayingPlaylist,
                        isPlayingPlaylist = uiState.isPlayingPlaylist,
                        hasNext = uiState.hasNext,
                        hasPrev = uiState.hasPrev,
                        onPlay = { onPlaySongs(uiState.filteredSongs) },
                        onShuffle = { onShuffleSongs(uiState.filteredSongs) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        currentMediaId = uiState.currentMediaId
                    )
                }
                LibraryTab.Genres -> {
                    CategoryTabContent(
                        title = "Genres",
                        categories = uiState.genres,
                        isLoading = uiState.isMetadataLoading,
                        selectedLabel = uiState.selectedGenre,
                        onCategorySelected = onGenreSelected,
                        onClearCategorySelection = onClearCategorySelection,
                        enableAlphaIndex = true,
                        songs = uiState.filteredSongs,
                        isPlaying = uiState.isPlaying || uiState.isPlayingPlaylist,
                        isPlayingPlaylist = uiState.isPlayingPlaylist,
                        hasNext = uiState.hasNext,
                        hasPrev = uiState.hasPrev,
                        onPlay = { onPlaySongs(uiState.filteredSongs) },
                        onShuffle = { onShuffleSongs(uiState.filteredSongs) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        currentMediaId = uiState.currentMediaId
                    )
                }
                LibraryTab.Artists -> {
                    CategoryTabContent(
                        title = "Artists",
                        categories = uiState.artists,
                        isLoading = uiState.isMetadataLoading,
                        selectedLabel = uiState.selectedArtist,
                        onCategorySelected = onArtistSelected,
                        onClearCategorySelection = onClearCategorySelection,
                        enableAlphaIndex = true,
                        songs = uiState.filteredSongs,
                        isPlaying = uiState.isPlaying || uiState.isPlayingPlaylist,
                        isPlayingPlaylist = uiState.isPlayingPlaylist,
                        hasNext = uiState.hasNext,
                        hasPrev = uiState.hasPrev,
                        onPlay = { onPlaySongs(uiState.filteredSongs) },
                        onShuffle = { onShuffleSongs(uiState.filteredSongs) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        currentMediaId = uiState.currentMediaId
                    )
                }
                LibraryTab.Decades -> {
                    CategoryTabContent(
                        title = "Decades",
                        categories = uiState.decades,
                        isLoading = uiState.isMetadataLoading,
                        selectedLabel = uiState.selectedDecade,
                        onCategorySelected = onDecadeSelected,
                        onClearCategorySelection = onClearCategorySelection,
                        songs = uiState.filteredSongs,
                        isPlaying = uiState.isPlaying || uiState.isPlayingPlaylist,
                        isPlayingPlaylist = uiState.isPlayingPlaylist,
                        hasNext = uiState.hasNext,
                        hasPrev = uiState.hasPrev,
                        onPlay = { onPlaySongs(uiState.filteredSongs) },
                        onShuffle = { onShuffleSongs(uiState.filteredSongs) },
                        onStop = onStop,
                        onNext = onNext,
                        onPrev = onPrev,
                        onFileClick = onFileClick,
                        currentMediaId = uiState.currentMediaId
                    )
                }
            }
        }
    }

    if (showPlaylistDialog) {
        val maxCount = uiState.scannedFiles.size
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
            title = { Text("Create Playlist") },
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
            title = { Text("Select Folder") },
            text = {
                Column {
                    Text("How many songs should be scanned?")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = scanCountText,
                        onValueChange = { scanCountText = it },
                        singleLine = true,
                        placeholder = { Text("e.g. 20") }
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
                            showScanDialog = false
                            onSelectFolderWithLimit(countValue!!)
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScanDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CategoryTabContent(
    title: String,
    categories: List<String>,
    isLoading: Boolean,
    selectedLabel: String?,
    onCategorySelected: (String) -> Unit,
    onClearCategorySelection: () -> Unit,
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
                    CategoryCard(
                        title = category,
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
                                    onClick = { onFileClick(file) }
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
                onClick = { onFileClick(file) }
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
    hasNext: Boolean,
    hasPrev: Boolean,
    onPlaylistSelected: (PlaylistInfo) -> Unit,
    onPlayPlaylist: (PlaylistInfo) -> Unit,
    onShufflePlaylistSongs: (List<MediaFileInfo>) -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    currentMediaId: String?
) {
    if (playlists.isEmpty()) {
        Text("No playlists found")
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            items(playlists) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistSelected(playlist) }
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

            Text(
                text = selectedPlaylist.displayName.removeSuffix(".m3u"),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            PlaybackButtonsRow(
                isPlaying = isPlaying,
                isPlayingPlaylist = isPlayingPlaylist,
                hasNext = hasNext,
                hasPrev = hasPrev,
                onPlay = { onPlayPlaylist(selectedPlaylist) },
                onShuffle = { onShufflePlaylistSongs(playlistSongs) },
                onStop = onStop,
                onNext = onNext,
                onPrev = onPrev
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (playlistSongs.isEmpty()) {
                Text("No songs in playlist")
            } else {
                LazyColumn {
                    items(playlistSongs) { file ->
                        FileCard(
                            file = file,
                            isCurrentTrack = file.uriString == currentMediaId,
                            onClick = { onFileClick(file) }
                        )
                    }
                }
            }
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
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isPlaying) {
            if (isPlayingPlaylist || showNavForNonPlaylist) {
                if (hasPrev) {
                    TextButton(onClick = onPrev) { Text("Prev") }
                }
                if (hasNext) {
                    TextButton(onClick = onNext) { Text("Next") }
                }
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
    isPlaying: Boolean,
    isPlayingPlaylist: Boolean,
    hasNext: Boolean,
    hasPrev: Boolean,
    queuePosition: String?,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (queuePosition != null) {
                    Text(
                        text = "Track $queuePosition",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            TextButton(onClick = onPlayPause) {
                Text(if (isPlaying) "Pause" else "Play")
            }
            if (isPlayingPlaylist) {
                if (hasPrev) {
                    TextButton(onClick = onPrev) {
                        Text("Prev")
                    }
                }
                if (hasNext) {
                    TextButton(onClick = onNext) {
                        Text("Next")
                    }
                }
            }
            TextButton(onClick = onStop) {
                Text("Stop")
            }
        }
    }
}

@Composable
fun PlaylistCard(playlist: PlaylistInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playlist.displayName.removeSuffix(".m3u"),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Playlist",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun FileCard(file: MediaFileInfo, isCurrentTrack: Boolean, onClick: () -> Unit) {
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
                text = file.title ?: file.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            val secondary = buildSongDetails(file)
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall
                )
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
