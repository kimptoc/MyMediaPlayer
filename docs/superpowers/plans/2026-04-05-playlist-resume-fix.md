# Playlist Resume Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix playlists not showing in Android Auto when resuming after DHU reconnect (issue #129).

**Architecture:** Two bugs combine to cause empty playlists: (1) `ACTION_SET_PLAYLISTS` handler doesn't persist playlists to Room DB, so they're lost on restart, and (2) the phone's dedup logic prevents re-sending playlists after service recreate. Fix both: persist on receipt, and clear dedup state on MediaBrowser reconnection.

**Tech Stack:** Kotlin, Android MediaBrowserServiceCompat, Room DB

---

### Task 1: Persist playlists to Room DB on receipt

When `ACTION_SET_PLAYLISTS` is handled in `MyMusicService`, playlists are added to `mediaCacheService` in memory but never persisted. This means on service restart, the Room DB has no playlists.

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt:480-497`

- [ ] **Step 1: Add persistCache call after playlist receipt**

In `MyMusicService.kt`, in the `ACTION_SET_PLAYLISTS` handler (line 480-497), add a `persistCache` call after the playlists are added — same pattern as the `ACTION_SET_MEDIA_FILES` handler at line 437-443.

Find this block:

```kotlin
                ACTION_SET_PLAYLISTS -> {
                    if (extras == null) return
                    val playlistUris = extras.getStringArrayList(EXTRA_PLAYLIST_URIS) ?: return
                    val playlistNames = extras.getStringArrayList(EXTRA_PLAYLIST_NAMES) ?: return

                    mediaCacheService.clearPlaylists()
                    val count = minOf(playlistUris.size, playlistNames.size)
                    for (i in 0 until count) {
                        mediaCacheService.addPlaylist(
                            PlaylistInfo(
                                uriString = playlistUris[i],
                                displayName = playlistNames[i]
                            )
                        )
                    }
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(PLAYLISTS_ID)
                }
```

Replace with:

```kotlin
                ACTION_SET_PLAYLISTS -> {
                    if (extras == null) return
                    val playlistUris = extras.getStringArrayList(EXTRA_PLAYLIST_URIS) ?: return
                    val playlistNames = extras.getStringArrayList(EXTRA_PLAYLIST_NAMES) ?: return

                    mediaCacheService.clearPlaylists()
                    val count = minOf(playlistUris.size, playlistNames.size)
                    for (i in 0 until count) {
                        mediaCacheService.addPlaylist(
                            PlaylistInfo(
                                uriString = playlistUris[i],
                                displayName = playlistNames[i]
                            )
                        )
                    }
                    serviceScope.launch {
                        val prefs = getPrefs(this@MyMusicService)
                        val treeUriStr = prefs.getString(KEY_TREE_URI, null)
                        if (treeUriStr != null) {
                            val limit = prefs.getInt(KEY_SCAN_LIMIT, MediaCacheService.MAX_CACHE_SIZE)
                            mediaCacheService.persistCache(this@MyMusicService, Uri.parse(treeUriStr), limit)
                        }
                    }
                    notifyChildrenChanged(ROOT_ID)
                    notifyChildrenChanged(PLAYLISTS_ID)
                }
```

- [ ] **Step 2: Build and verify no compile errors**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run existing tests to verify no regressions**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt
git commit -m "fix: persist playlists to Room DB when received from phone

ACTION_SET_PLAYLISTS handler now calls persistCache so playlists
survive service restart. Fixes part of #129."
```

---

### Task 2: Clear dedup state on MediaBrowser reconnection

When the service is destroyed and recreated (e.g., DHU reconnect), `connectionCallback.onConnected()` fires in `MainActivity`. At this point, `lastSentPlaylistUris` still holds the old values, preventing re-send. Clear all dedup state in `onConnected` so the next `StateFlow` emission triggers a fresh sync.

**Files:**
- Modify: `mobile/src/main/java/com/example/mymediaplayer/MainActivity.kt:168-185`

- [ ] **Step 1: Clear dedup state in onConnected**

In `MainActivity.kt`, in the `connectionCallback`'s `onConnected()` method (line 168-185), clear the dedup variables so playlists and files get re-sent to the potentially-new service instance.

Find this block:

```kotlin
    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val browser = mediaBrowser ?: return
            val controller = MediaControllerCompat(this@MainActivity, browser.sessionToken)
            mediaController = controller
            MediaControllerCompat.setMediaController(this@MainActivity, controller)
            controller.registerCallback(controllerCallback)
            lastPlaybackState = controller.playbackState
            lastMetadata = controller.metadata
            lastRepeatMode = controller.repeatMode
            pushPlaybackState()
            pushQueueState()
            viewModel.updateRepeatMode(lastRepeatMode)
            sendTrackVoiceIntroSettingToService()
            sendTrackVoiceOutroSettingToService()
            dispatchPendingVoiceSearchIfNeeded()
        }
    }
```

Replace with:

```kotlin
    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val browser = mediaBrowser ?: return
            val controller = MediaControllerCompat(this@MainActivity, browser.sessionToken)
            mediaController = controller
            MediaControllerCompat.setMediaController(this@MainActivity, controller)
            controller.registerCallback(controllerCallback)
            lastPlaybackState = controller.playbackState
            lastMetadata = controller.metadata
            lastRepeatMode = controller.repeatMode
            pushPlaybackState()
            pushQueueState()
            viewModel.updateRepeatMode(lastRepeatMode)
            sendTrackVoiceIntroSettingToService()
            sendTrackVoiceOutroSettingToService()
            dispatchPendingVoiceSearchIfNeeded()

            // Clear dedup state so playlists and files are re-sent to the
            // (possibly recreated) service instance.
            lastSentPlaylistUris = null
            lastSentUris = null
            lastSentLargeLibraryCount = null
        }
    }
```

- [ ] **Step 2: Build and verify no compile errors**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run existing tests to verify no regressions**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add mobile/src/main/java/com/example/mymediaplayer/MainActivity.kt
git commit -m "fix: clear dedup state on MediaBrowser reconnect

Clears lastSentPlaylistUris, lastSentUris, and lastSentLargeLibraryCount
in onConnected so playlists and files are re-sent after service recreate.
Fixes #129."
```

---

### Task 3: Manual verification

- [ ] **Step 1: Connect phone via USB and start DHU**

```bash
cd $HOME/Library/Android/sdk/extras/google/auto
./desktop-head-unit --usb
```

- [ ] **Step 2: Verify playlists appear in Android Auto**

Navigate to the media app in the DHU. Confirm playlists are listed.

- [ ] **Step 3: Simulate service recreate**

Disconnect and reconnect the DHU (or kill the app process from Android Studio and let it restart). Verify playlists still appear after reconnection.
