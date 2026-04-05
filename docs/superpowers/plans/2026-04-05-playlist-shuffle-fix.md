# Playlist Play All / Shuffle Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix [Play All] and [Shuffle] not appearing in Android Auto when browsing into a discovered playlist (issue #131).

**Architecture:** Android Auto silently drops MediaItems with very long mediaIds. Discovered playlists embed full content:// URIs producing 150+ char IDs. Fix by replacing long URIs with short deterministic hashes in a lookup map maintained by the service. Three sites reference `PLAYLIST_URI_PREFIX` and all need updating to use the new `PLAYLIST_SHORT_PREFIX` with the short ID map.

**Tech Stack:** Kotlin, Android MediaBrowserServiceCompat, Robolectric (tests)

---

### File Structure

All changes are in one file:
- **Modify:** `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`
  - Add `PLAYLIST_SHORT_PREFIX` constant and `playlistShortIds` map
  - Add `rebuildPlaylistShortIds()` method
  - Update `buildMediaItemsForPrefix` PLAYLIST_PREFIX handler to use short IDs
  - Update `handlePlayAllOrShuffle` to resolve short IDs
  - Update `currentPlaylistName` assignment to resolve short IDs
  - Call `rebuildPlaylistShortIds()` after playlists change
- **Modify:** `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt`
  - Add test for short ID generation and lookup

---

### Task 1: Add short ID infrastructure and update browse path

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`
- Modify: `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt`

- [ ] **Step 1: Add constant, map, and rebuild method**

In `MyMusicService.kt`, add the new constant in the companion object (after line 69 where `PLAYLIST_URI_PREFIX` is defined):

```kotlin
        private const val PLAYLIST_SHORT_PREFIX = "pl:"
```

Add the map as an instance field (near the other instance fields, e.g. after the `mediaCacheService` field):

```kotlin
    private val playlistShortIds = mutableMapOf<String, String>() // shortId -> uriString
```

Add the rebuild method (near the `playlistEntriesForBrowse` method around line 1074):

```kotlin
    private fun rebuildPlaylistShortIds() {
        playlistShortIds.clear()
        for (playlist in mediaCacheService.discoveredPlaylists) {
            val shortId = playlist.uriString.hashCode().toUInt().toString(36)
            playlistShortIds[shortId] = playlist.uriString
        }
    }
```

- [ ] **Step 2: Call rebuildPlaylistShortIds after playlists change**

There are two places playlists can change:

**a) After `ACTION_SET_PLAYLISTS` handler (around line 497):** Add `rebuildPlaylistShortIds()` call after the persist launch and before `notifyChildrenChanged`:

Find:
```kotlin
                    serviceScope.launch {
                        mediaCacheService.persistPlaylists(this@MyMusicService)
                    }
                    notifyChildrenChanged(ROOT_ID)
```

Replace with:
```kotlin
                    serviceScope.launch {
                        mediaCacheService.persistPlaylists(this@MyMusicService)
                    }
                    rebuildPlaylistShortIds()
                    notifyChildrenChanged(ROOT_ID)
```

**b) After `loadCachedTreeIfAvailable` completes (in the `finally` block around line 1296):** Add after `deliverPendingResults()`:

Find:
```kotlin
            } finally {
                isScanning = false
                refreshQueueMetadata()
                deliverPendingResults()
                notifyChildrenChanged(ROOT_ID)
```

Replace with:
```kotlin
            } finally {
                isScanning = false
                refreshQueueMetadata()
                rebuildPlaylistShortIds()
                deliverPendingResults()
                notifyChildrenChanged(ROOT_ID)
```

- [ ] **Step 3: Update buildMediaItemsForPrefix to use short IDs**

In `buildMediaItemsForPrefix`, find the `PLAYLIST_PREFIX` handler (around line 759):

Find:
```kotlin
        if (parentId.startsWith(PLAYLIST_PREFIX)) {
            val playlistUri = Uri.decode(parentId.removePrefix(PLAYLIST_PREFIX))
            val songs = enrichFromCache(
                playlistService.readPlaylist(this, Uri.parse(playlistUri))
            )
            val songIconUri = resourceIconUri(R.drawable.ic_album_placeholder)
            return buildSongListItems(songs, PLAYLIST_URI_PREFIX + Uri.encode(playlistUri), songIconUri)
        }
```

Replace with:
```kotlin
        if (parentId.startsWith(PLAYLIST_PREFIX)) {
            val playlistUri = Uri.decode(parentId.removePrefix(PLAYLIST_PREFIX))
            val songs = enrichFromCache(
                playlistService.readPlaylist(this, Uri.parse(playlistUri))
            )
            val songIconUri = resourceIconUri(R.drawable.ic_album_placeholder)
            val shortId = playlistShortIds.entries.firstOrNull { it.value == playlistUri }?.key ?: ""
            return buildSongListItems(songs, PLAYLIST_SHORT_PREFIX + shortId, songIconUri)
        }
```

- [ ] **Step 4: Update handlePlayAllOrShuffle to resolve short IDs**

In `handlePlayAllOrShuffle`, find the `PLAYLIST_URI_PREFIX` case (around line 1906):

Find:
```kotlin
            listKey.startsWith(PLAYLIST_URI_PREFIX) -> {
                val playlistUri =
                    Uri.decode(listKey.removePrefix(PLAYLIST_URI_PREFIX))
                enrichFromCache(
                    playlistService.readPlaylist(
                        this,
                        Uri.parse(playlistUri)
                    )
                )
            }
```

Replace with:
```kotlin
            listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
                val shortId = listKey.removePrefix(PLAYLIST_SHORT_PREFIX)
                val playlistUri = playlistShortIds[shortId] ?: ""
                if (playlistUri.isEmpty()) emptyList()
                else enrichFromCache(
                    playlistService.readPlaylist(
                        this,
                        Uri.parse(playlistUri)
                    )
                )
            }
```

- [ ] **Step 5: Update currentPlaylistName to resolve short IDs**

In `handlePlayAllOrShuffle`, find the `PLAYLIST_URI_PREFIX` case in the `currentPlaylistName` assignment (around line 1972):

Find:
```kotlin
            listKey.startsWith(PLAYLIST_URI_PREFIX) -> {
                Uri.decode(listKey.removePrefix(PLAYLIST_URI_PREFIX))
            }
```

Replace with:
```kotlin
            listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
                val shortId = listKey.removePrefix(PLAYLIST_SHORT_PREFIX)
                val uri = playlistShortIds[shortId]
                mediaCacheService.discoveredPlaylists
                    .firstOrNull { it.uriString == uri }
                    ?.displayName ?: "Playlist"
            }
```

- [ ] **Step 6: Build and verify no compile errors**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run existing tests to verify no regressions**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 8: Add test for short ID generation**

In `MyMusicServiceTest.kt`, add this test:

```kotlin
    @Test
    fun playlistShortId_isDeterministicAndShort() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fplaylist.m3u"
        val shortId = uri.hashCode().toUInt().toString(36)
        // Short ID should be 6-7 chars, not 100+
        assertTrue("Short ID '$shortId' should be under 10 chars", shortId.length < 10)
        // Deterministic: same input produces same output
        assertEquals(shortId, uri.hashCode().toUInt().toString(36))
    }
```

- [ ] **Step 9: Run tests**

Run: `./gradlew test`
Expected: All tests pass including new test

- [ ] **Step 10: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt
git commit -m "fix: use short IDs for playlist play/shuffle mediaIds

Android Auto silently drops MediaItems with long mediaIds. Discovered
playlists embedded full content:// URIs producing 150+ char IDs.
Now uses deterministic short hashes (~7 chars) via a lookup map.
Fixes #131."
```

---

### Task 2: Deploy and verify on Android Auto

- [ ] **Step 1: Install on device**

Run: `./gradlew :mobile:installDebug`

- [ ] **Step 2: Force-stop and reopen app**

```bash
adb shell am force-stop com.example.mymediaplayer
```

Open the app on the phone, wait for scan to complete.

- [ ] **Step 3: Verify in Android Auto**

Browse into a discovered playlist (e.g., "Olivia d") in the DHU. Verify:
- [Play All] and [Shuffle] appear at the top of the song list
- Tapping [Play All] plays all songs in order
- Tapping [Shuffle] plays all songs shuffled
- Smart playlists (Flagged, Most Played) still work as before
