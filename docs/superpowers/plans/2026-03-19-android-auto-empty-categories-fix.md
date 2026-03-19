# Android Auto Empty Categories Fix

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Android Auto showing no playlists/genres/categories even though content exists on the phone.

**Architecture:** Three bugs found in `MyMusicService`. (1) Race condition: `isScanning` is set inside the loading coroutine instead of before it, so `onLoadChildren` calls during startup return empty synchronously and are never queued for delivery. Fix: move `isScanning = true` before `serviceScope.launch`. (2) Playlists category hidden when only smart playlists exist: `buildHomeItems()` checks `discoveredPlaylists.size > 0` but smart playlists are always available when songs exist. Fix: change condition to `playlistCount > 0 || songCount > 0`. (3) `ACTION_SET_MEDIA_FILES` handler omits `notifyChildrenChanged(PLAYLISTS_ID)`. Fix: add the missing notify call.

**Tech Stack:** Kotlin, Android MediaBrowserServiceCompat, Coroutines, Robolectric (unit tests)

---

### Task 0: Create feature branch from main

- [ ] **Step 1: Create branch**

```bash
git checkout main && git pull
git checkout -b fix/android-auto-empty-categories-56
```

---

### Task 1: Expose internals and write failing tests for Playlists visibility bug

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt:202` (mediaCacheService visibility)
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt:2684` (buildHomeItems visibility)
- Test: `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt`

- [ ] **Step 1: Change `mediaCacheService` from `private` to `internal`**

In `MyMusicService.kt` line 202, change:
```kotlin
private val mediaCacheService = MediaCacheService()
```
to:
```kotlin
internal val mediaCacheService = MediaCacheService()
```

- [ ] **Step 2: Change `buildHomeItems` from `private` to `internal`**

In `MyMusicService.kt` around line 2684, change:
```kotlin
private fun buildHomeItems(): MutableList<MediaItem> {
```
to:
```kotlin
internal fun buildHomeItems(): MutableList<MediaItem> {
```

- [ ] **Step 3: Write the failing tests**

Add to `MyMusicServiceTest.kt`:
```kotlin
@Test
fun buildHomeItems_showsPlaylistsTileWhenOnlySmartPlaylistsAvailable() {
    val service = MyMusicService()
    service.mediaCacheService.addFile(
        MediaFileInfo(
            uriString = "content://test/song1",
            displayName = "Song One.mp3",
            sizeBytes = 1L,
            title = "Song One"
        )
    )
    // No discovered playlists — only smart playlists should be available

    val items = service.buildHomeItems()

    val playlistsTile = items.find { it.description.mediaId == "playlists" }
    assertNotNull("Playlists tile should show when songs exist (smart playlists available)", playlistsTile)
    assertEquals("Smart Playlists", playlistsTile!!.description.subtitle)
}

@Test
fun buildHomeItems_showsPlaylistsTileWithCountWhenUserPlaylistsExist() {
    val service = MyMusicService()
    service.mediaCacheService.addFile(
        MediaFileInfo(
            uriString = "content://test/song1",
            displayName = "Song One.mp3",
            sizeBytes = 1L,
            title = "Song One"
        )
    )
    service.mediaCacheService.addPlaylist(
        PlaylistInfo(uriString = "content://test/playlist.m3u", displayName = "My Playlist.m3u")
    )

    val items = service.buildHomeItems()

    val playlistsTile = items.find { it.description.mediaId == "playlists" }
    assertNotNull(playlistsTile)
    assertEquals("1 playlist", playlistsTile!!.description.subtitle)
}

@Test
fun buildHomeItems_hidesAllCategoriesWhenCacheEmpty() {
    val service = MyMusicService()
    // No files added

    val items = service.buildHomeItems()

    assertTrue("Home should be empty when no songs loaded", items.isEmpty())
}
```

- [ ] **Step 4: Run tests to verify they fail**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.example.mymediaplayer.shared.MyMusicServiceTest.buildHomeItems*" -q 2>&1 | tail -20
```

Expected: `buildHomeItems_showsPlaylistsTileWhenOnlySmartPlaylistsAvailable` and `buildHomeItems_showsPlaylistsTileWithCountWhenUserPlaylistsExist` FAIL at the `assertNotNull` check (the Playlists tile is absent entirely — the subtitle assertion is unreachable in the failing state). `buildHomeItems_hidesAllCategoriesWhenCacheEmpty` should PASS already.

Note: `buildHomeItems` calls `resourceIconUri(R.drawable.ic_auto_playlists)` which accesses Android drawables. Robolectric provides a shadow application context that resolves resources, so this should work. If it crashes with a resource-not-found error instead of an assertion failure, the test runner may need `@Config(manifest = Config.NONE)` or ensure `testApplicationId` is configured — check the existing Robolectric tests for any such annotations and mirror them.

---

### Task 2: Fix `buildHomeItems` Playlists visibility condition

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt:2684`

- [ ] **Step 1: Move `songCount` before the Playlists check and fix the condition**

Replace the current start of `buildHomeItems()`:
```kotlin
val playlistCount = mediaCacheService.discoveredPlaylists.size
if (playlistCount > 0) {
    items.add(
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(PLAYLISTS_ID)
                .setTitle("Playlists")
                .setSubtitle("$playlistCount playlist${if (playlistCount != 1) "s" else ""}")
                .setIconUri(resourceIconUri(R.drawable.ic_auto_playlists))
                .setExtras(childListExtras)
                .build(),
            MediaItem.FLAG_BROWSABLE
        )
    )
}

val songCount = mediaCacheService.cachedFiles.size
```

with:
```kotlin
val playlistCount = mediaCacheService.discoveredPlaylists.size
val songCount = mediaCacheService.cachedFiles.size
if (playlistCount > 0 || songCount > 0) {
    val playlistSubtitle = if (playlistCount > 0)
        "$playlistCount playlist${if (playlistCount != 1) "s" else ""}"
    else
        "Smart Playlists"
    items.add(
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(PLAYLISTS_ID)
                .setTitle("Playlists")
                .setSubtitle(playlistSubtitle)
                .setIconUri(resourceIconUri(R.drawable.ic_auto_playlists))
                .setExtras(childListExtras)
                .build(),
            MediaItem.FLAG_BROWSABLE
        )
    )
}
```

Also delete the now-duplicate `val songCount = mediaCacheService.cachedFiles.size` line that appears further down immediately before the `if (songCount > 0)` block (around what was line 2712). Leaving it will cause a compile error ("val cannot be reassigned").

- [ ] **Step 2: Run tests to verify they pass**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.example.mymediaplayer.shared.MyMusicServiceTest.buildHomeItems*" -q 2>&1 | tail -20
```

Expected: All three `buildHomeItems_*` tests PASS.

- [ ] **Step 3: Run full test suite to check for regressions**

```bash
./gradlew :shared:testDebugUnitTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt \
        shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt
git commit -m "fix: show Playlists category in Android Auto home when only smart playlists exist

Previously buildHomeItems() only showed the Playlists tile when discovered
.m3u playlist files existed. Smart playlists (Favorites, Most Played, etc.)
are always available when songs are loaded but were invisible in the home
view. Now shows Playlists tile whenever songs exist, with subtitle 'Smart
Playlists' when no user playlist files are present."
```

---

### Task 3: Fix `loadCachedTreeIfAvailable` race condition

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt:1202`

The current code sets `isScanning = true` only inside the coroutine, and only in the scan (not cache-load) branch. This means `onLoadChildren` calls during the gap between `serviceScope.launch` and the coroutine actually executing return empty synchronously. Since `HOME_ID` is not in `needsMetadataIndexes`, it goes through the synchronous path and delivers empty results that Android Auto may cache.

- [ ] **Step 1: Restructure `loadCachedTreeIfAvailable` to set `isScanning = true` before the coroutine**

**Delete lines 1202–1270 in their entirety** (the complete `loadCachedTreeIfAvailable` function including its closing brace) and replace with the following:

```kotlin
private fun loadCachedTreeIfAvailable() {
    if (isScanning) return
    if (mediaCacheService.cachedFiles.isNotEmpty()) return
    val prefs = getPrefs(this@MyMusicService)
    val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
    val wholeDriveMode = prefs.getBoolean(KEY_SCAN_WHOLE_DRIVE, false)
    val uri = if (wholeDriveMode) {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    } else {
        val uriString = prefs.getString(KEY_TREE_URI, null) ?: return
        val parsed = Uri.parse(uriString)
        val hasPermission = contentResolver.persistedUriPermissions.any {
            it.uri == parsed && it.isReadPermission
        }
        if (!hasPermission) return
        parsed
    }

    // Set isScanning BEFORE launching the coroutine so that any onLoadChildren
    // calls during the loading window are queued in pendingResults and delivered
    // properly once data is ready — rather than returning empty results immediately.
    isScanning = true
    serviceScope.launch {
        try {
            val persisted = mediaCacheService.loadPersistedCache(this@MyMusicService, uri, limit)
            if (persisted != null) {
                mediaCacheService.buildAlbumArtistIndexesFromCache()
            } else {
                var lastNotify = 0
                val progress: (Int, Int) -> Unit = { songsFound, _ ->
                    if (songsFound - lastNotify >= 200) {
                        lastNotify = songsFound
                        notifyChildrenChanged(SONGS_ALL_ID)
                    }
                }
                if (wholeDriveMode) {
                    mediaCacheService.scanWholeDevice(this@MyMusicService, limit, progress)
                } else {
                    mediaCacheService.scanDirectory(
                        this@MyMusicService,
                        uri,
                        limit,
                        onProgress = progress
                    )
                    mediaCacheService.enrichGenresFromMediaStore(this@MyMusicService)
                }
                mediaCacheService.buildAlbumArtistIndexesFromCache()
                mediaCacheService.persistCache(this@MyMusicService, uri, limit)
            }
        } finally {
            isScanning = false
            deliverPendingResults()
            notifyChildrenChanged(ROOT_ID)
            notifyChildrenChanged(HOME_ID)
            notifyChildrenChanged(SONGS_ID)
            notifyChildrenChanged(PLAYLISTS_ID)
            notifyChildrenChanged(ALBUMS_ID)
            notifyChildrenChanged(GENRES_ID)
            notifyChildrenChanged(ARTISTS_ID)
            notifyChildrenChanged(DECADES_ID)
        }
    }
}
```

Key changes:
- `isScanning = true` moved before `serviceScope.launch`
- Both the cache-load and scan paths consolidated into a single `try/finally` block
- `finally` block handles `isScanning = false` + `deliverPendingResults()` + all `notifyChildrenChanged` calls for both paths — no duplication

- [ ] **Step 2: Run the full test suite**

```bash
./gradlew :shared:testDebugUnitTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt
git commit -m "fix: prevent Android Auto from receiving empty browse results during startup

loadCachedTreeIfAvailable() previously set isScanning=true only inside the
coroutine (and only in the scan path, not the cache-load path). Any
onLoadChildren() call during the window between launching the coroutine and
it actually running would execute synchronously, find empty cachedFiles, and
return an empty result that Android Auto could cache permanently.

Now sets isScanning=true synchronously before launching the coroutine.
All onLoadChildren calls during loading are queued in pendingResults and
delivered correctly once data is available. Consolidated the try/finally
block to handle both cache-load and scan paths identically."
```

---

### Task 4: Add missing `PLAYLISTS_ID` notification in `ACTION_SET_MEDIA_FILES`

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt:442`

When the phone sends media files to the service via `ACTION_SET_MEDIA_FILES`, it notifies `GENRES_ID`, `ALBUMS_ID`, `ARTISTS_ID`, `DECADES_ID` — but not `PLAYLISTS_ID`. If Android Auto has already navigated into the Playlists view, it won't receive an update when the smart playlist contents change (e.g. after new songs are loaded into the service).

- [ ] **Step 1: Add `notifyChildrenChanged(PLAYLISTS_ID)` to the `ACTION_SET_MEDIA_FILES` handler**

Find the block around line 442:
```kotlin
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(HOME_ID)
                    notifyChildrenChanged(SONGS_ID)
                    notifyChildrenChanged(ALBUMS_ID)
                    notifyChildrenChanged(GENRES_ID)
                    notifyChildrenChanged(ARTISTS_ID)
                    notifyChildrenChanged(DECADES_ID)
```

Change to:
```kotlin
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(HOME_ID)
                    notifyChildrenChanged(SONGS_ID)
                    notifyChildrenChanged(PLAYLISTS_ID)
                    notifyChildrenChanged(ALBUMS_ID)
                    notifyChildrenChanged(GENRES_ID)
                    notifyChildrenChanged(ARTISTS_ID)
                    notifyChildrenChanged(DECADES_ID)
```

- [ ] **Step 2: Run full test suite**

```bash
./gradlew :shared:testDebugUnitTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt
git commit -m "fix: notify PLAYLISTS_ID when phone sends media files to service

ACTION_SET_MEDIA_FILES handler was missing notifyChildrenChanged(PLAYLISTS_ID).
Android Auto subscribers to the playlists view would not receive updates
when the phone sent new track data (which affects smart playlist contents)."
```

---

### Task 5: Push branch and open PR

- [ ] **Step 1: Push branch**

```bash
git push -u origin fix/android-auto-empty-categories-56
```

- [ ] **Step 2: Open PR referencing issue #56**

```bash
gh pr create \
  --title "Fix Android Auto showing empty categories (playlists/genres)" \
  --body "$(cat <<'EOF'
## Summary

- **Race condition fix**: `loadCachedTreeIfAvailable()` now sets `isScanning = true` synchronously before launching the loading coroutine, so `onLoadChildren` calls during startup are queued and delivered correctly rather than returning empty results that Android Auto may cache
- **Playlists category visibility**: `buildHomeItems()` now shows the Playlists tile whenever songs exist, not only when `.m3u` playlist files are present — smart playlists (Favorites, Most Played, etc.) are always available when songs are loaded
- **Missing notification**: Added `notifyChildrenChanged(PLAYLISTS_ID)` to the `ACTION_SET_MEDIA_FILES` handler which was notifying all other categories but omitting playlists

Closes #56

## Test plan

- [ ] Connect Android Auto (DHU or real car) with phone app closed — categories should appear after service loads cache
- [ ] Open phone app first, then connect Android Auto — categories should appear immediately
- [ ] Navigate to Playlists in Android Auto with no `.m3u` files — Smart Playlists tile should be visible with subtitle "Smart Playlists"
- [ ] Unit tests: `./gradlew :shared:testDebugUnitTest`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
