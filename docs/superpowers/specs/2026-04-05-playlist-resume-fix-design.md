# Playlist Resume Fix — Design Spec

**Issue:** [#129](https://github.com/kimptoc/MyMediaPlayer/issues/129) — Playlists don't show in Android Auto when resuming, despite being visible on phone.

## Root Cause

Two bugs combine to cause empty playlists on service restart:

### Bug 1: Playlists never persisted to Room DB

When the phone sends playlists via `ACTION_SET_PLAYLISTS` (`MyMusicService.kt:480`), the handler adds them to `mediaCacheService` in memory and calls `notifyChildrenChanged`, but **does not call `persistCache`**. Compare with `ACTION_SET_MEDIA_FILES` (`MyMusicService.kt:437`) which does persist.

Result: On service restart, `loadPersistedCache` loads from Room DB, but the playlists table is empty (or stale from a prior scan that happened to persist before playlists arrived).

### Bug 2: Phone dedup prevents re-sending playlists

`MainActivity.sendPlaylistsToServiceIfNeeded` (`MainActivity.kt:658`) compares current playlist URIs against `lastSentPlaylistUris` and returns early if they match. After a service destroy/recreate, the service has lost its in-memory playlists, but the phone still holds the old `lastSentPlaylistUris` — so it never re-sends.

## Fix

### Fix 1: Persist playlists on receipt

In `MyMusicService`, after `ACTION_SET_PLAYLISTS` adds playlists to `mediaCacheService`, call `persistCache` in a coroutine (same pattern as `ACTION_SET_MEDIA_FILES`).

**File:** `MyMusicService.kt`, inside the `ACTION_SET_PLAYLISTS` handler (~line 494)

```kotlin
// After adding playlists, persist to Room DB
serviceScope.launch {
    val prefs = getPrefs(this@MyMusicService)
    val treeUriStr = prefs.getString(KEY_TREE_URI, null)
    if (treeUriStr != null) {
        val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
        mediaCacheService.persistCache(this@MyMusicService, Uri.parse(treeUriStr), limit)
    }
}
```

### Fix 2: Broadcast intent to reset phone's dedup state

**New constant:** `ACTION_REQUEST_PLAYLIST_SYNC` in `MyMusicService` companion object.

**Service side (MyMusicService.kt):** At the end of `onCreate`, after `loadCachedTreeIfAvailable()`, send a local broadcast:

```kotlin
LocalBroadcastManager.getInstance(this)
    .sendBroadcast(Intent(ACTION_REQUEST_PLAYLIST_SYNC))
```

**Phone side (MainActivity.kt):** Register a `BroadcastReceiver` in `onCreate`/`onDestroy` that clears `lastSentPlaylistUris` on receipt, triggering the next `StateFlow` emission to re-send:

```kotlin
private val playlistSyncReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        lastSentPlaylistUris = null
    }
}
```

Register in `onCreate`:
```kotlin
LocalBroadcastManager.getInstance(this)
    .registerReceiver(playlistSyncReceiver, IntentFilter(ACTION_REQUEST_PLAYLIST_SYNC))
```

Unregister in `onDestroy`:
```kotlin
LocalBroadcastManager.getInstance(this)
    .unregisterReceiver(playlistSyncReceiver)
```

## Why LocalBroadcastManager

Since the service runs in the same process as the activity, `LocalBroadcastManager` is appropriate — no need for system-wide broadcasts. It's simpler and more efficient.

**Note:** `LocalBroadcastManager` is deprecated in favor of other in-process patterns (LiveData, Flow, etc.). However, since the service and activity already communicate via custom actions on `MediaBrowserServiceCompat`, and the receiver pattern is minimal (one receiver, one intent), this is pragmatic and consistent with the existing architecture. If preferred, an alternative is to use a `SharedFlow` on a shared singleton, but that adds more structural change.

## Testing

- Existing unit tests should continue to pass (no behavior change for normal flow).
- Manual test: connect DHU, verify playlists show, disconnect/reconnect DHU, verify playlists still show.
- Consider adding a unit test that verifies `persistCache` is called after `ACTION_SET_PLAYLISTS`.

## Files Modified

1. `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt` — persist on playlist receipt + broadcast sync request
2. `mobile/src/main/java/com/example/mymediaplayer/MainActivity.kt` — register receiver to clear dedup state
