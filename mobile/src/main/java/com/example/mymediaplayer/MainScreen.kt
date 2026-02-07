package com.example.mymediaplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    onSelectFolder: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onCreatePlaylist: (Int) -> Unit,
    onPlaylistMessageDismissed: () -> Unit,
    onFolderMessageDismissed: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onAlbumSelected: (String) -> Unit,
    onGenreSelected: (String) -> Unit,
    onArtistSelected: (String) -> Unit,
    onPlaylistClick: (PlaylistInfo) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistCountText by remember { mutableStateOf("3") }

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
                                onSelectFolder()
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
                    onNext = onNext
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
                    if (uiState.scannedFiles.isNotEmpty()) {
                        Text(
                            text = "${uiState.scannedFiles.size} file(s) found",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            items(uiState.scannedFiles) { file ->
                                FileCard(
                                    file = file,
                                    isCurrentTrack = file.uriString == uiState.currentMediaId,
                                    onClick = { onFileClick(file) }
                                )
                            }
                        }
                    } else {
                        Text("No songs found")
                    }
                }
                LibraryTab.Playlists -> {
                    if (uiState.discoveredPlaylists.isNotEmpty()) {
                        Text(
                            text = "${uiState.discoveredPlaylists.size} playlist(s) found",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            items(uiState.discoveredPlaylists) { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    onClick = { onPlaylistClick(playlist) }
                                )
                            }
                        }
                    } else {
                        Text("No playlists found")
                    }
                }
                LibraryTab.Albums -> {
                    CategoryTabContent(
                        title = "Albums",
                        categories = uiState.albums,
                        isLoading = uiState.isMetadataLoading,
                        selectedLabel = uiState.selectedAlbum,
                        onCategorySelected = onAlbumSelected,
                        songs = uiState.filteredSongs,
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
                        songs = uiState.filteredSongs,
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
                        songs = uiState.filteredSongs,
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
                        if (isValid && countValue != null) {
                            showPlaylistDialog = false
                            onCreatePlaylist(countValue)
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
}

@Composable
private fun CategoryTabContent(
    title: String,
    categories: List<String>,
    isLoading: Boolean,
    selectedLabel: String?,
    onCategorySelected: (String) -> Unit,
    songs: List<MediaFileInfo>,
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

    LazyColumn {
        items(categories) { category ->
            CategoryCard(
                title = category,
                isSelected = category == selectedLabel,
                onClick = { onCategorySelected(category) }
            )
        }

        if (selectedLabel != null) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Songs in $selectedLabel",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(songs) { file ->
                FileCard(
                    file = file,
                    isCurrentTrack = file.uriString == currentMediaId,
                    onClick = { onFileClick(file) }
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(title: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = if (isSelected) {
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
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
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
    queuePosition: String?,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit
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
                TextButton(onClick = onNext) {
                    Text("Next")
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
