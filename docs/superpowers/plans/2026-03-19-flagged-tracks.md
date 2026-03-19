# Flagged Tracks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ability to flag/unflag tracks for later review, with a smart playlist and an Android Auto custom action button.

**Architecture:** Follow the existing Favorites pattern exactly — `KEY_FLAGGED_URIS` StringSet in SharedPreferences, `SMART_PLAYLIST_FLAGGED` constant, custom action on the now-playing PlaybackState for Android Auto. Add a vector drawable for the flag icon.

**Tech Stack:** Kotlin, Android MediaBrowserServiceCompat, PlaybackStateCompat custom actions, Compose (phone unflag UI)

---

### Task 0: Create feature branch from main

- [ ] **Step 1: Create branch**

```bash
git checkout main && git pull
git checkout -b feat/flagged-tracks-61
```

---

### Task 1: Add flag icon drawable and constants

**Files:**
- Create: `shared/src/main/res/drawable/ic_flag.xml`
- Create: `shared/src/main/res/drawable/ic_flag_filled.xml`
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt` (constants only)

- [ ] **Step 1: Create unflagged icon `ic_flag.xml`**

Create `shared/src/main/res/drawable/ic_flag.xml` — a simple outlined flag icon (24dp Material-style):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6h-5.6zM19,13h-4.6l-0.4,-2H7V6h5.6l0.4,2H19v5z"/>
</vector>
```

- [ ] **Step 2: Create flagged icon `ic_flag_filled.xml`**

Create `shared/src/main/res/drawable/ic_flag_filled.xml` — a filled flag icon:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z"/>
</vector>
```

- [ ] **Step 3: Add constants to MyMusicService companion object**

In `MyMusicService.kt`, add these constants near the existing `KEY_FAVORITE_URIS` and `SMART_PLAYLIST_FAVORITES`:

```kotlin
private const val KEY_FLAGGED_URIS = "flagged_uris"
private const val SMART_PLAYLIST_FLAGGED = "flagged"
private const val CUSTOM_ACTION_FLAG = "FLAG_TRACK"
```

- [ ] **Step 4: Commit**

```bash
git add shared/src/main/res/drawable/ic_flag.xml \
        shared/src/main/res/drawable/ic_flag_filled.xml \
        shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt
git commit -m "$(cat <<'EOF'
feat: add flag icon drawables and constants for flagged tracks

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Add "Flagged Tracks" smart playlist

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`
- Test: `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt`

This mirrors how Favorites works — add to the playlist browse entries, resolution, voice query mapping, and title mapping.

- [ ] **Step 1: Add "Flagged" to `playlistEntriesForBrowse`**

Find the `smartEntries` list in `playlistEntriesForBrowse` (around line 1040):
```kotlin
        val smartEntries = listOf(
            SMART_PLAYLIST_FAVORITES to "Favorites",
            SMART_PLAYLIST_RECENTLY_ADDED to "Recently Added",
            SMART_PLAYLIST_MOST_PLAYED to "Most Played",
            SMART_PLAYLIST_NOT_HEARD_RECENTLY to "Haven't Heard In A While"
        ).map { smart ->
```

Add `SMART_PLAYLIST_FLAGGED to "Flagged"` to the list:
```kotlin
        val smartEntries = listOf(
            SMART_PLAYLIST_FAVORITES to "Favorites",
            SMART_PLAYLIST_FLAGGED to "Flagged",
            SMART_PLAYLIST_RECENTLY_ADDED to "Recently Added",
            SMART_PLAYLIST_MOST_PLAYED to "Most Played",
            SMART_PLAYLIST_NOT_HEARD_RECENTLY to "Haven't Heard In A While"
        ).map { smart ->
```

- [ ] **Step 2: Add resolution in `resolveSmartPlaylistTracksById`**

Find the `when` block in `resolveSmartPlaylistTracksById` (around line 2336). Add a new branch after the `SMART_PLAYLIST_FAVORITES` case:

```kotlin
            SMART_PLAYLIST_FLAGGED -> {
                val flagged = getPrefs(this@MyMusicService)
                    .getStringSet(KEY_FLAGGED_URIS, emptySet())
                    ?.toSet()
                    ?: emptySet()
                all.filter { it.uriString in flagged }
            }
```

- [ ] **Step 3: Add voice query mapping in `smartPlaylistIdFromQuery`**

In the `when` block in `smartPlaylistIdFromQuery` (around line 2309), add before the `else`:

```kotlin
            needle.contains("flag") -> SMART_PLAYLIST_FLAGGED
```

- [ ] **Step 4: Add title mapping in `smartPlaylistTitleFromId`**

In the `when` block in `smartPlaylistTitleFromId` (around line 2323), add:

```kotlin
            SMART_PLAYLIST_FLAGGED -> "Flagged"
```

- [ ] **Step 5: Update test for `playlistEntriesForBrowse`**

In `MyMusicServiceTest.kt`, the existing test `playlistEntriesForBrowse_includesSmartPlaylistsWhenNoUserPlaylists` asserts 4 smart playlists. Update it to expect 5:

Find:
```kotlin
        assertEquals(4, entries.size)
        assertEquals("Favorites", entries[0].title)
        assertEquals("Recently Added", entries[1].title)
```

Change to:
```kotlin
        assertEquals(5, entries.size)
        assertEquals("Favorites", entries[0].title)
        assertEquals("Flagged", entries[1].title)
        assertEquals("Recently Added", entries[2].title)
```

Also update `playlistEntriesForBrowse_putsUserPlaylistsBeforeSmartPlaylists` — it asserts `assertEquals(6, entries.size)` (2 user + 4 smart). Change to 7 (2 user + 5 smart):

```kotlin
        assertEquals(7, entries.size)
```

And update the smart playlist assertions (entries[2] through entries[6] instead of entries[2] through entries[5]):
```kotlin
        assertEquals("Favorites", entries[2].title)
        assertEquals("Flagged", entries[3].title)
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :shared:testDebugUnitTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt \
        shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt
git commit -m "$(cat <<'EOF'
feat: add Flagged Tracks smart playlist

Mirrors the Favorites pattern — stores flagged URIs in SharedPreferences,
adds Flagged to smart playlist browse entries, voice query resolution,
and title mapping.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Add Android Auto custom action button to flag/unflag current track

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`

The custom action button appears on Android Auto's now-playing screen. It uses `PlaybackStateCompat.Builder.addCustomAction()` and is handled in the `onCustomAction` callback.

- [ ] **Step 1: Add the custom action to `updatePlaybackState`**

In `updatePlaybackState()`, just before `session.setPlaybackState(playbackStateBuilder.build())` (around line 2468), add:

```kotlin
        // Add flag/unflag custom action for Android Auto
        val currentUri = currentFileInfo?.uriString ?: currentMediaId
        if (currentUri != null) {
            val isFlagged = getPrefs(this@MyMusicService)
                .getStringSet(KEY_FLAGGED_URIS, emptySet())
                ?.contains(currentUri) == true
            val flagIcon = if (isFlagged) R.drawable.ic_flag_filled else R.drawable.ic_flag
            val flagLabel = if (isFlagged) "Unflag" else "Flag"
            playbackStateBuilder.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_ACTION_FLAG, flagLabel, flagIcon
                ).build()
            )
        }
```

**Important:** `addCustomAction` accumulates — the builder must be cleared of old custom actions first. Since `PlaybackStateCompat.Builder` doesn't have a `clearCustomActions()`, you need to create a fresh builder each time OR call `addCustomAction` before building. Check if the builder is reused — it is (line 208: `private val playbackStateBuilder`). The `setState` call resets state but NOT custom actions.

To fix this, add at the start of `updatePlaybackState()`, after `val speed = ...`:
```kotlin
        // Reset custom actions (builder accumulates them)
        playbackStateBuilder.setExtras(null)
```

Actually, `PlaybackStateCompat.Builder` doesn't have a method to clear custom actions. The cleanest approach: create a new builder each call. Replace line 2446:

```kotlin
        playbackStateBuilder
            .setActions(playbackActions)
            .setState(state, position, speed, SystemClock.elapsedRealtime())
```

with:

```kotlin
        val builder = PlaybackStateCompat.Builder()
            .setActions(playbackActions)
            .setState(state, position, speed, SystemClock.elapsedRealtime())
```

And replace all subsequent `playbackStateBuilder` references in the function with `builder`, ending with `session.setPlaybackState(builder.build())`.

- [ ] **Step 2: Handle the flag action in `onCustomAction`**

In the `onCustomAction` callback (around line 394), add a new `when` branch:

```kotlin
                CUSTOM_ACTION_FLAG -> {
                    val uri = currentFileInfo?.uriString ?: currentMediaId ?: return
                    val prefs = getPrefs(this@MyMusicService)
                    val flagged = prefs.getStringSet(KEY_FLAGGED_URIS, emptySet())
                        ?.toMutableSet() ?: mutableSetOf()
                    if (uri in flagged) {
                        flagged.remove(uri)
                    } else {
                        flagged.add(uri)
                    }
                    prefs.edit().putStringSet(KEY_FLAGGED_URIS, flagged).apply()
                    // Refresh playback state to toggle the icon
                    val currentState = lastPlaybackState()?.state
                        ?: PlaybackStateCompat.STATE_NONE
                    updatePlaybackState(currentState)
                }
```

- [ ] **Step 3: Check `lastPlaybackState` exists**

Verify there's a helper to get the current playback state. Search for `lastPlaybackState`:

```bash
grep -n "lastPlaybackState" shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt
```

If it doesn't exist, use `session.controller?.playbackState?.state` instead.

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest -q 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt
git commit -m "$(cat <<'EOF'
feat: add Flag/Unflag custom action on Android Auto now-playing screen

Adds a flag icon button to the Android Auto now-playing controls.
Tapping toggles the current track's flagged state in SharedPreferences.
The icon switches between outlined (unflagged) and filled (flagged).

Closes #61

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Push branch and open PR

- [ ] **Step 1: Push branch**

```bash
git push -u origin feat/flagged-tracks-61
```

- [ ] **Step 2: Open PR referencing issue #61**

```bash
gh pr create \
  --title "Add ability to flag tracks for later review" \
  --body "$(cat <<'EOF'
## Summary

- **Flagged Tracks smart playlist**: appears in Android Auto and phone alongside Favorites, Most Played, etc.
- **Android Auto custom action**: flag/unflag button on the now-playing screen — icon toggles between outlined and filled flag
- **Storage**: SharedPreferences StringSet (`flagged_uris`), mirroring the existing Favorites pattern
- **Voice support**: "play flagged" voice command resolves to the Flagged smart playlist

Closes #61

## Test plan

- [ ] In Android Auto, play a track — flag icon should appear in now-playing controls
- [ ] Tap flag — icon should change to filled, track added to Flagged playlist
- [ ] Tap again — icon should change to outlined, track removed
- [ ] Navigate to Playlists > Flagged — should show flagged tracks
- [ ] Unit tests: `./gradlew :shared:testDebugUnitTest`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
