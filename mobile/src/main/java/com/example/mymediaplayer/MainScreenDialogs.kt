package com.example.mymediaplayer

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo

@Composable
fun MainScreenDialogs(
    uiState: MainUiState,
    nowPlayingArt: Bitmap?,
    playlistCountText: String,
    setPlaylistCountText: (String) -> Unit,
    showPlaylistDialog: Boolean,
    setShowPlaylistDialog: (Boolean) -> Unit,
    onCreatePlaylist: (Int) -> Unit,

    scanCountText: String,
    setScanCountText: (String) -> Unit,
    scanWholeDriveMode: Boolean,
    setScanWholeDriveMode: (Boolean) -> Unit,
    scanDeepMode: Boolean,
    setScanDeepMode: (Boolean) -> Unit,
    showScanDialog: Boolean,
    setShowScanDialog: (Boolean) -> Unit,
    onScanWholeDriveWithLimit: (Int) -> Unit,
    onSelectFolderWithLimit: (Int, Boolean) -> Unit,

    pendingAddFiles: List<MediaFileInfo>,
    setPendingAddFiles: (List<MediaFileInfo>) -> Unit,
    localCreatedPlaylists: List<PlaylistInfo>,
    showAddToPlaylistDialog: Boolean,
    setShowAddToPlaylistDialog: (Boolean) -> Unit,
    setShowCreateFromSelectionDialog: (Boolean) -> Unit,
    setCreateFromSelectionNameText: (String) -> Unit,
    onAddToExistingPlaylist: (PlaylistInfo, List<MediaFileInfo>) -> Unit,

    showCreateFromSelectionDialog: Boolean,
    createFromSelectionNameText: String,
    onCreatePlaylistFromSongs: (String, List<MediaFileInfo>) -> PlaylistInfo?,
    onChoosePlaylistSaveFolder: () -> Unit,
    setLocalCreatedPlaylists: (List<PlaylistInfo>) -> Unit,

    showDeletePlaylistDialog: Boolean,
    setShowDeletePlaylistDialog: (Boolean) -> Unit,
    pendingDeletePlaylist: PlaylistInfo?,
    onDeletePlaylist: (PlaylistInfo) -> Unit,

    showRenamePlaylistDialog: Boolean,
    setShowRenamePlaylistDialog: (Boolean) -> Unit,
    pendingRenamePlaylist: PlaylistInfo?,
    setPendingRenamePlaylist: (PlaylistInfo?) -> Unit,
    renamePlaylistNameText: String,
    setRenamePlaylistNameText: (String) -> Unit,
    onRenamePlaylist: (PlaylistInfo, String) -> Unit,

    showExpandedNowPlayingDialog: Boolean,
    setShowExpandedNowPlayingDialog: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleFlag: (String) -> Unit,

    showQueueDialog: Boolean,
    setShowQueueDialog: (Boolean) -> Unit,
    onQueueItemSelected: (Long) -> Unit,

    showPlaylistSaveFolderPrompt: Boolean,
    onDismissPlaylistSaveFolderPrompt: () -> Unit,
    onSetPlaylistSaveFolderNow: () -> Unit
) {
    if (showPlaylistDialog) {
        CreateRandomPlaylistDialog(
            maxCount = uiState.scan.scannedFiles.size,
            playlistCountText = playlistCountText,
            onPlaylistCountTextChange = { setPlaylistCountText(it) },
            onDismissRequest = { setShowPlaylistDialog(false) },
            onCreatePlaylist = onCreatePlaylist
        )
    }

    if (showScanDialog) {
        ScanDialogContent(
            scanCountText = scanCountText,
            onScanCountTextChange = { setScanCountText(it) },
            scanWholeDriveMode = scanWholeDriveMode,
            onScanWholeDriveModeChange = { setScanWholeDriveMode(it) },
            scanDeepMode = scanDeepMode,
            onScanDeepModeChange = { setScanDeepMode(it) },
            onDismissRequest = { setShowScanDialog(false) },
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
                setShowAddToPlaylistDialog(false)
                setPendingAddFiles(emptyList())
            },
            onCreateNewPlaylistClick = {
                setCreateFromSelectionNameText("")
                setShowCreateFromSelectionDialog(true)
            },
            onAddToExistingPlaylist = { playlist, files ->
                onAddToExistingPlaylist(playlist, files)
                setShowAddToPlaylistDialog(false)
                setPendingAddFiles(emptyList())
            }
        )
    }

    if (showCreateFromSelectionDialog) {
        CreateFromSelectionDialogContent(
            createFromSelectionNameText = createFromSelectionNameText,
            onCreateFromSelectionNameTextChange = { setCreateFromSelectionNameText(it) },
            pendingAddFiles = pendingAddFiles,
            onCreatePlaylistFromSongs = onCreatePlaylistFromSongs,
            onChoosePlaylistSaveFolder = onChoosePlaylistSaveFolder,
            onPlaylistCreated = { created ->
                setLocalCreatedPlaylists((localCreatedPlaylists + created).distinctBy { it.uriString })
                setShowCreateFromSelectionDialog(false)
                setShowAddToPlaylistDialog(false)
                setPendingAddFiles(emptyList())
                setCreateFromSelectionNameText("")
            },
            onDismissRequest = { setShowCreateFromSelectionDialog(false) }
        )
    }

    if (showDeletePlaylistDialog) {
        DeletePlaylistDialogContent(
            pendingDeletePlaylist = pendingDeletePlaylist,
            onDismissRequest = { setShowDeletePlaylistDialog(false) },
            onDeletePlaylist = { playlist ->
                onDeletePlaylist(playlist)
                setShowDeletePlaylistDialog(false)
            }
        )
    }

    if (showRenamePlaylistDialog) {
        RenamePlaylistDialogContent(
            pendingRenamePlaylist = pendingRenamePlaylist,
            renamePlaylistNameText = renamePlaylistNameText,
            onRenamePlaylistNameTextChange = { setRenamePlaylistNameText(it) },
            onDismissRequest = { setShowRenamePlaylistDialog(false) },
            onRenamePlaylist = { playlist, newName ->
                onRenamePlaylist(playlist, newName)
                setShowRenamePlaylistDialog(false)
                setPendingRenamePlaylist(null)
            }
        )
    }

    if (showExpandedNowPlayingDialog && uiState.playback.currentTrackName != null) {
        val currentMediaId = uiState.playback.currentMediaId
        val isFlagged = currentMediaId != null && currentMediaId in uiState.flaggedUris
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
                    onToggleFlag(currentMediaId)
                }
            },
            onDismiss = { setShowExpandedNowPlayingDialog(false) }
        )
    }

    if (showQueueDialog) {
        QueueDialogContent(
            queueTitle = uiState.playback.queueTitle,
            queueItems = uiState.playback.queueItems,
            activeQueueId = uiState.playback.activeQueueId,
            onDismissRequest = { setShowQueueDialog(false) },
            onQueueItemSelected = { queueId ->
                onQueueItemSelected(queueId)
                setShowQueueDialog(false)
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
