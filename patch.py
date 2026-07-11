with open("mobile/src/main/java/com/example/mymediaplayer/MainActivity.kt", "r") as f:
    content = f.read()

search_str = """    @androidx.compose.runtime.Composable
    private fun MainAppContent(uiState: MainUiState) {
        MainScreen(
            uiState = uiState,
            onSelectFolderWithLimit = ::handleSelectFolderWithLimit,
            onChoosePlaylistSaveFolder = { openPlaylistDocumentTree.launch(null) },
            onScanWholeDriveWithLimit = ::handleScanWholeDriveWithLimit,
            onFileClick = { file -> playFile(file, uiState.scan.scannedFiles) },
            onPlayPause = { togglePlayPause(uiState.playback.isPlaying) },
            onStop = ::stopPlayback,
            onNext = ::skipToNext,
            onPrev = ::skipToPrevious,
            onToggleRepeat = { toggleRepeatMode(uiState.playback.repeatMode) },
            onQueueItemSelected = ::skipToQueueItem,
            onSeekTo = ::seekTo,
            onCreatePlaylist = viewModel::createRandomPlaylist,
            onPlaylistMessageDismissed = viewModel::clearPlaylistMessage,
            onFolderMessageDismissed = viewModel::clearFolderMessage,
            onScanMessageDismissed = viewModel::clearScanMessage,
            onTabSelected = viewModel::selectTab,
            onAlbumSelected = viewModel::selectAlbum,
            onAlbumSortModeChanged = viewModel::setAlbumSortMode,
            onGenreSelected = viewModel::selectGenre,
            onArtistSelected = viewModel::selectArtist,
            onSearchQueryChanged = viewModel::updateSearchQuery,
            onClearSearch = viewModel::clearSearch,
            onClearCategorySelection = viewModel::clearCategorySelection,
            onPlaylistSelected = viewModel::selectPlaylist,
            onClearPlaylistSelection = viewModel::clearSelectedPlaylist,
            onDeletePlaylist = viewModel::deletePlaylist,
            onRenamePlaylist = viewModel::renamePlaylist,
            onSavePlaylistEdits = viewModel::savePlaylistEdits,
            onPlaySongs = { songs ->
                playUiList(
                    songs = songs,
                    shuffle = false,
                    queueTitle = queueTitleForCurrentUiList(uiState)
                )
            },
            onShuffleSongs = { songs ->
                playUiList(
                    songs = songs,
                    shuffle = true,
                    queueTitle = queueTitleForCurrentUiList(uiState)
                )
            },
            onPlaySearchResults = { songs ->
                playSearchResults(songs, shuffle = false)
            },
            onShuffleSearchResults = { songs ->
                playSearchResults(songs, shuffle = true)
            },
            onAddToExistingPlaylist = viewModel::addManyToExistingPlaylist,
            onCreatePlaylistFromSongs = viewModel::createPlaylistFromSongs,
            onToggleFavorite = { file ->
                viewModel.toggleFavorite(file.uriString)
            },
            onToggleFlag = viewModel::toggleFlaggedUri,
            nowPlayingArt = nowPlayingArt.value,
            showPlaylistSaveFolderPrompt = showPlaylistSaveFolderPrompt.value,
            onDismissPlaylistSaveFolderPrompt = {
                showPlaylistSaveFolderPrompt.value = false
            },
            onSetPlaylistSaveFolderNow = {
                showPlaylistSaveFolderPrompt.value = false
                openPlaylistDocumentTree.launch(null)
            },
            onOpenSettings = { showSettings.value = true },
            onPlayPlaylist = { playlist -> playPlaylist(playlist, uiState.scan.scannedFiles, uiState.scan.discoveredPlaylists) },
            onShufflePlaylistSongs = { playlist, songs -> shufflePlaylistSongs(playlist, songs, uiState.scan.scannedFiles, uiState.scan.discoveredPlaylists) }
        )
    }"""

replace_str = """    private fun launchOpenPlaylistDocumentTree() {
        openPlaylistDocumentTree.launch(null)
    }

    private fun handleFileClick(file: com.example.mymediaplayer.shared.MediaFileInfo) {
        playFile(file, viewModel.uiState.value.scan.scannedFiles)
    }

    private fun handlePlayPause() {
        togglePlayPause(viewModel.uiState.value.playback.isPlaying)
    }

    private fun handleToggleRepeat() {
        toggleRepeatMode(viewModel.uiState.value.playback.repeatMode)
    }

    private fun handlePlaySongs(songs: List<com.example.mymediaplayer.shared.MediaFileInfo>) {
        playUiList(
            songs = songs,
            shuffle = false,
            queueTitle = queueTitleForCurrentUiList(viewModel.uiState.value)
        )
    }

    private fun handleShuffleSongs(songs: List<com.example.mymediaplayer.shared.MediaFileInfo>) {
        playUiList(
            songs = songs,
            shuffle = true,
            queueTitle = queueTitleForCurrentUiList(viewModel.uiState.value)
        )
    }

    private fun handlePlaySearchResults(songs: List<com.example.mymediaplayer.shared.MediaFileInfo>) {
        playSearchResults(songs, shuffle = false)
    }

    private fun handleShuffleSearchResults(songs: List<com.example.mymediaplayer.shared.MediaFileInfo>) {
        playSearchResults(songs, shuffle = true)
    }

    private fun handleToggleFavorite(file: com.example.mymediaplayer.shared.MediaFileInfo) {
        viewModel.toggleFavorite(file.uriString)
    }

    private fun handleDismissPlaylistSaveFolderPrompt() {
        showPlaylistSaveFolderPrompt.value = false
    }

    private fun handleSetPlaylistSaveFolderNow() {
        showPlaylistSaveFolderPrompt.value = false
        openPlaylistDocumentTree.launch(null)
    }

    private fun handleOpenSettings() {
        showSettings.value = true
    }

    private fun handlePlayPlaylist(playlist: com.example.mymediaplayer.shared.PlaylistInfo) {
        playPlaylist(playlist, viewModel.uiState.value.scan.scannedFiles, viewModel.uiState.value.scan.discoveredPlaylists)
    }

    private fun handleShufflePlaylistSongs(playlist: com.example.mymediaplayer.shared.PlaylistInfo, songs: List<com.example.mymediaplayer.shared.MediaFileInfo>) {
        shufflePlaylistSongs(playlist, songs, viewModel.uiState.value.scan.scannedFiles, viewModel.uiState.value.scan.discoveredPlaylists)
    }

    @androidx.compose.runtime.Composable
    private fun MainAppContent(uiState: MainUiState) {
        MainScreen(
            uiState = uiState,
            onSelectFolderWithLimit = ::handleSelectFolderWithLimit,
            onChoosePlaylistSaveFolder = ::launchOpenPlaylistDocumentTree,
            onScanWholeDriveWithLimit = ::handleScanWholeDriveWithLimit,
            onFileClick = ::handleFileClick,
            onPlayPause = ::handlePlayPause,
            onStop = ::stopPlayback,
            onNext = ::skipToNext,
            onPrev = ::skipToPrevious,
            onToggleRepeat = ::handleToggleRepeat,
            onQueueItemSelected = ::skipToQueueItem,
            onSeekTo = ::seekTo,
            onCreatePlaylist = viewModel::createRandomPlaylist,
            onPlaylistMessageDismissed = viewModel::clearPlaylistMessage,
            onFolderMessageDismissed = viewModel::clearFolderMessage,
            onScanMessageDismissed = viewModel::clearScanMessage,
            onTabSelected = viewModel::selectTab,
            onAlbumSelected = viewModel::selectAlbum,
            onAlbumSortModeChanged = viewModel::setAlbumSortMode,
            onGenreSelected = viewModel::selectGenre,
            onArtistSelected = viewModel::selectArtist,
            onSearchQueryChanged = viewModel::updateSearchQuery,
            onClearSearch = viewModel::clearSearch,
            onClearCategorySelection = viewModel::clearCategorySelection,
            onPlaylistSelected = viewModel::selectPlaylist,
            onClearPlaylistSelection = viewModel::clearSelectedPlaylist,
            onDeletePlaylist = viewModel::deletePlaylist,
            onRenamePlaylist = viewModel::renamePlaylist,
            onSavePlaylistEdits = viewModel::savePlaylistEdits,
            onPlaySongs = ::handlePlaySongs,
            onShuffleSongs = ::handleShuffleSongs,
            onPlaySearchResults = ::handlePlaySearchResults,
            onShuffleSearchResults = ::handleShuffleSearchResults,
            onAddToExistingPlaylist = viewModel::addManyToExistingPlaylist,
            onCreatePlaylistFromSongs = viewModel::createPlaylistFromSongs,
            onToggleFavorite = ::handleToggleFavorite,
            onToggleFlag = viewModel::toggleFlaggedUri,
            nowPlayingArt = nowPlayingArt.value,
            showPlaylistSaveFolderPrompt = showPlaylistSaveFolderPrompt.value,
            onDismissPlaylistSaveFolderPrompt = ::handleDismissPlaylistSaveFolderPrompt,
            onSetPlaylistSaveFolderNow = ::handleSetPlaylistSaveFolderNow,
            onOpenSettings = ::handleOpenSettings,
            onPlayPlaylist = ::handlePlayPlaylist,
            onShufflePlaylistSongs = ::handleShufflePlaylistSongs
        )
    }"""

if search_str in content:
    new_content = content.replace(search_str, replace_str)
    with open("mobile/src/main/java/com/example/mymediaplayer/MainActivity.kt", "w") as f:
        f.write(new_content)
    print("Success")
else:
    print("Search string not found")
