package com.example.mymediaplayer

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.example.mymediaplayer.shared.MediaFileInfo
import com.example.mymediaplayer.shared.PlaylistInfo
import com.example.mymediaplayer.PlaybackState

@Composable
fun PlaylistDialogs(
    maxScannedFilesCount: Int,
    discoveredPlaylists: List<PlaylistInfo>,
    playlistCountText: String,
    setPlaylistCountText: (String) -> Unit,
    showPlaylistDialog: Boolean,
    setShowPlaylistDialog: (Boolean) -> Unit,
    onCreatePlaylist: (Int) -> Unit,

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

    showRemovePlaylistSongDialog: Boolean,
    setShowRemovePlaylistSongDialog: (Boolean) -> Unit,
    pendingRemoveSong: MediaFileInfo?,
    onConfirmRemoveSong: () -> Unit
) {
    if (showPlaylistDialog) {
        CreateRandomPlaylistDialog(
            maxCount = maxScannedFilesCount,
            playlistCountText = playlistCountText,
            onPlaylistCountTextChange = { setPlaylistCountText(it) },
            onDismissRequest = { setShowPlaylistDialog(false) },
            onCreatePlaylist = onCreatePlaylist
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialogContent(
            pendingAddFiles = pendingAddFiles,
            localCreatedPlaylists = localCreatedPlaylists,
            discoveredPlaylists = discoveredPlaylists,
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

    if (showRemovePlaylistSongDialog) {
        RemovePlaylistSongDialogContent(
            pendingRemoveSong = pendingRemoveSong,
            onDismissRequest = { setShowRemovePlaylistSongDialog(false) },
            onConfirmRemove = {
                onConfirmRemoveSong()
                setShowRemovePlaylistSongDialog(false)
                setPendingRemoveSong(null)
            }
        )
    }
}

@Composable
fun ScanDialogs(
    scanCountText: String,
    setScanCountText: (String) -> Unit,
    scanWholeDriveMode: Boolean,
    setScanWholeDriveMode: (Boolean) -> Unit,
    scanDeepMode: Boolean,
    setScanDeepMode: (Boolean) -> Unit,
    showScanDialog: Boolean,
    setShowScanDialog: (Boolean) -> Unit,
    onScanWholeDriveWithLimit: (Int) -> Unit,
    onSelectFolderWithLimit: (Int, Boolean) -> Unit
) {
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
}

@Composable
fun PlaybackDialogs(
    playbackState: PlaybackState,
    flaggedUris: Set<String>,
    nowPlayingArt: Bitmap?,
    showExpandedNowPlayingDialog: Boolean,
    setShowExpandedNowPlayingDialog: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleFlag: (String) -> Unit,
    showQueueDialog: Boolean,
    setShowQueueDialog: (Boolean) -> Unit,
    onQueueItemSelected: (Long) -> Unit
) {
    if (showExpandedNowPlayingDialog && playbackState.currentTrackName != null) {
        val currentMediaId = playbackState.currentMediaId
        val isFlagged = currentMediaId != null && currentMediaId in flaggedUris
        ExpandedNowPlayingDialog(
            trackName = playbackState.currentTrackName,
            artistName = playbackState.currentArtistName,
            album = playbackState.currentAlbum,
            genre = playbackState.currentGenre,
            year = playbackState.currentYear,
            artwork = nowPlayingArt,
            currentPositionMs = playbackState.currentPositionMs,
            positionUpdatedAtElapsedMs = playbackState.positionUpdatedAtElapsedMs,
            durationMs = playbackState.durationMs,
            isPlaying = playbackState.isPlaying,
            playbackSpeed = playbackState.playbackSpeed,
            hasPrev = playbackState.hasPrev,
            hasNext = playbackState.hasNext,
            isPlayingPlaylist = playbackState.isPlayingPlaylist,
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
            queueTitle = playbackState.queueTitle,
            queueItems = playbackState.queueItems,
            activeQueueId = playbackState.activeQueueId,
            onDismissRequest = { setShowQueueDialog(false) },
            onQueueItemSelected = { queueId ->
                onQueueItemSelected(queueId)
                setShowQueueDialog(false)
            }
        )
    }
}

@Composable
fun SettingsDialogs(
    showPlaylistSaveFolderPrompt: Boolean,
    onDismissPlaylistSaveFolderPrompt: () -> Unit,
    onSetPlaylistSaveFolderNow: () -> Unit
) {
    if (showPlaylistSaveFolderPrompt) {
        PlaylistSaveFolderPromptDialogContent(
            onDismissRequest = onDismissPlaylistSaveFolderPrompt,
            onSetPlaylistSaveFolderNow = onSetPlaylistSaveFolderNow
        )
    }
}
