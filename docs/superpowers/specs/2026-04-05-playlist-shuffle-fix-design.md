# Playlist Play All / Shuffle Fix — Design Spec

**Issue:** [#131](https://github.com/kimptoc/MyMediaPlayer/issues/131) — [Play All] and [Shuffle] rows don't appear in Android Auto when browsing into a discovered playlist.

## Root Cause

Android Auto silently drops MediaItems with very long mediaIds. Discovered playlists embed the full content:// URI in the listKey, producing mediaIds like:

```
action:play_all:playlist_uri:content%3A%2F%2Fcom.android.externalstorage.documents%2Ftree%2F...
```

This can exceed 150+ characters. Smart playlists (short IDs like `smart_playlist:flagged`) and genres (`genre:Classical`) work fine because their mediaIds are short.

## Fix

Replace the full URI in playlist listKeys with a short deterministic hash derived from the URI.

### Short ID Generation

```kotlin
private val playlistShortIds = mutableMapOf<String, String>() // shortId -> uriString

private fun rebuildPlaylistShortIds() {
    playlistShortIds.clear()
    for (playlist in mediaCacheService.discoveredPlaylists) {
        val shortId = playlist.uriString.hashCode().toUInt().toString(36)
        playlistShortIds[shortId] = playlist.uriString
    }
}
```

Called when playlists change: after `ACTION_SET_PLAYLISTS` handling and after `loadCachedTreeIfAvailable` completes.

### New Constant

```kotlin
private const val PLAYLIST_SHORT_PREFIX = "pl:"
```

### buildMediaItemsForPrefix Change

In the `PLAYLIST_PREFIX` handler, change the listKey from:
```kotlin
PLAYLIST_URI_PREFIX + Uri.encode(playlistUri)
```
to:
```kotlin
val shortId = playlistShortIds.entries.firstOrNull { it.value == playlistUri }?.key ?: ""
PLAYLIST_SHORT_PREFIX + shortId
```

MediaId becomes ~30 chars: `action:play_all:pl:1a2b3c`

### handlePlayAllOrShuffle Change

Add a new case in the listKey resolution:
```kotlin
listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
    val shortId = listKey.removePrefix(PLAYLIST_SHORT_PREFIX)
    val uri = playlistShortIds[shortId] ?: return
    enrichFromCache(playlistService.readPlaylist(this, Uri.parse(uri)))
}
```

### Queue Title Resolver Change

In `resolveQueueTitleForListKey`, add:
```kotlin
listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
    val shortId = listKey.removePrefix(PLAYLIST_SHORT_PREFIX)
    val uri = playlistShortIds[shortId]
    mediaCacheService.discoveredPlaylists.firstOrNull { it.uriString == uri }?.displayName ?: "Playlist"
}
```

## Files Modified

1. `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt` — all changes in one file

## Testing

- Unit test: verify `buildMediaItems` for a `PLAYLIST_PREFIX` parent returns items with short mediaIds
- Unit test: verify `handlePlayAllOrShuffle` resolves `PLAYLIST_SHORT_PREFIX` listKeys correctly
- Manual: browse into a discovered playlist in Android Auto, verify [Play All] and [Shuffle] appear and work
