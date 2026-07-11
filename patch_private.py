with open("mobile/src/main/java/com/example/mymediaplayer/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("    fun launchOpenPlaylistDocumentTree", "    private fun launchOpenPlaylistDocumentTree")
content = content.replace("    fun handleFileClick", "    private fun handleFileClick")
content = content.replace("    fun handlePlayPause", "    private fun handlePlayPause")
content = content.replace("    fun handleToggleRepeat", "    private fun handleToggleRepeat")
content = content.replace("    fun handlePlaySongs", "    private fun handlePlaySongs")
content = content.replace("    fun handleShuffleSongs", "    private fun handleShuffleSongs")
content = content.replace("    fun handlePlaySearchResults", "    private fun handlePlaySearchResults")
content = content.replace("    fun handleShuffleSearchResults", "    private fun handleShuffleSearchResults")
content = content.replace("    fun handleToggleFavorite", "    private fun handleToggleFavorite")
content = content.replace("    fun handleDismissPlaylistSaveFolderPrompt", "    private fun handleDismissPlaylistSaveFolderPrompt")
content = content.replace("    fun handleSetPlaylistSaveFolderNow", "    private fun handleSetPlaylistSaveFolderNow")
content = content.replace("    fun handleOpenSettings", "    private fun handleOpenSettings")
content = content.replace("    fun handlePlayPlaylist", "    private fun handlePlayPlaylist")
content = content.replace("    fun handleShufflePlaylistSongs", "    private fun handleShufflePlaylistSongs")

with open("mobile/src/main/java/com/example/mymediaplayer/MainActivity.kt", "w") as f:
    f.write(content)
