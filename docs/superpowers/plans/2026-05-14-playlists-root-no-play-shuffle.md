# Remove [Play All] / [Shuffle] from Playlists Root Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the Playlists browse root in Android Auto into line with the Albums / Genres / Artists / Decades roots — no `[Play All]` / `[Shuffle]` action rows at the top of the playlists list.

**Architecture:** Two-change refactor in `MyMusicService.kt`. Task 1 removes the two action `MediaItem`s from `buildPlaylistsItems()` (the user-visible fix, covered by a new test). Task 2 deletes the now-unreachable handler code that flattened all playlists into one track list, plus its two unused coroutine imports.

**Tech Stack:** Kotlin, AndroidX `MediaBrowserServiceCompat`, Robolectric for unit tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-05-14-playlists-root-no-play-shuffle-design.md`

---

## File Structure

- **Modify:** `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`
  - `buildPlaylistsItems()` (1013–1046) — remove the two action `MediaItem`s.
  - `resolveTracksForListKey()` (1984–1996) — remove the `listKey == PLAYLISTS_ID` branch.
  - `resolvePlaylistNameForListKey()` (2057) — remove the `listKey == PLAYLISTS_ID -> "All Playlists"` branch.
  - Imports — remove `import kotlinx.coroutines.async` (line 46) and `import kotlinx.coroutines.awaitAll` (line 47), which become unused.
- **Modify:** `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt`
  - Add one new `@Test` asserting the playlists root has no `action:play_all:*` / `action:shuffle:*` entries.

No new files. No changes elsewhere — `PLAYLISTS_ID`, `ACTION_PLAY_ALL_PREFIX`, `ACTION_SHUFFLE_PREFIX` all stay in use by other callers.

---

## Task 1: Remove `[Play All]` / `[Shuffle]` from playlists root (user-visible fix + test)

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt:1013-1046`
- Test: `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt` (append one `@Test`)

- [ ] **Step 1: Write the failing test**

Append this method anywhere inside the `MyMusicServiceTest` class body (a natural spot is after `playlistEntriesForBrowse_putsUserPlaylistsBeforeSmartPlaylists` around line 305):

```kotlin
@Test
fun buildMediaItems_playlistsRoot_hasNoPlayAllOrShuffleEntries() {
    val service = Robolectric.buildService(MyMusicService::class.java).get()
    service.mediaCacheService.addPlaylist(
        PlaylistInfo(uriString = "content://playlists/mix1.m3u", displayName = "Mix 1")
    )
    service.mediaCacheService.addPlaylist(
        PlaylistInfo(uriString = "content://playlists/mix2.m3u", displayName = "Mix 2")
    )

    val items = service.buildMediaItems(MyMusicService.PLAYLISTS_ID)

    val actionItems = items.filter { item ->
        val id = item.description.mediaId.orEmpty()
        id.startsWith("action:play_all:") || id.startsWith("action:shuffle:")
    }
    assertTrue(
        "Expected no [Play All] / [Shuffle] entries at playlists root, found titles: " +
            actionItems.map { it.description.title.toString() },
        actionItems.isEmpty()
    )
}
```

The file already imports `Robolectric`, `RobolectricTestRunner`, `assertTrue`, `assertFalse`, `PlaylistInfo`, `MediaFileInfo`, and `MyMusicService` — no new imports needed.

Note: we deliberately do **not** assert exact `items.size`, because `buildPlaylistsItems()` also appends smart playlist entries via `playlistEntriesForBrowse`; locking that count in would couple this test to unrelated behaviour.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.example.mymediaplayer.shared.MyMusicServiceTest.buildMediaItems_playlistsRoot_hasNoPlayAllOrShuffleEntries" -q 2>&1 | tail -20
```

Expected: test runs, fails with an assertion error reading something like `Expected no [Play All] / [Shuffle] entries at playlists root, found titles: [[Play All], [Shuffle]]`.

If you instead get a compile error (e.g. unresolved reference), fix the test source first before continuing.

- [ ] **Step 3: Remove the action MediaItems from `buildPlaylistsItems`**

In `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`, replace the entire body of `buildPlaylistsItems()` (lines 1013–1046).

Use the Edit tool with:

`old_string`:

```kotlin
    private fun buildPlaylistsItems(): MutableList<MediaItem> {
        val items = mutableListOf<MediaItem>()
        if (mediaCacheService.discoveredPlaylists.isNotEmpty() ||
            mediaCacheService.cachedFiles.isNotEmpty()
        ) {
            items.add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(ACTION_PLAY_ALL_PREFIX + PLAYLISTS_ID)
                        .setTitle("[Play All]")
                        .build(),
                    MediaItem.FLAG_PLAYABLE
                )
            )
            items.add(
                MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(ACTION_SHUFFLE_PREFIX + PLAYLISTS_ID)
                        .setTitle("[Shuffle]")
                        .build(),
                    MediaItem.FLAG_PLAYABLE
                )
            )
        }
        items += playlistEntriesForBrowse(mediaCacheService.discoveredPlaylists).map { entry ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(entry.mediaId)
                .setTitle(entry.title)
                .setIconUri(resourceIconUri(R.drawable.ic_auto_playlists))
                .build()
            MediaItem(description, MediaItem.FLAG_BROWSABLE)
        }
        return items
    }
```

`new_string`:

```kotlin
    private fun buildPlaylistsItems(): MutableList<MediaItem> {
        return playlistEntriesForBrowse(mediaCacheService.discoveredPlaylists).map { entry ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(entry.mediaId)
                .setTitle(entry.title)
                .setIconUri(resourceIconUri(R.drawable.ic_auto_playlists))
                .build()
            MediaItem(description, MediaItem.FLAG_BROWSABLE)
        }.toMutableList()
    }
```

The pre-empty `if` guard is gone — an empty `discoveredPlaylists` simply yields an empty list, which is the only thing the guard had been protecting against once the action items go away.

- [ ] **Step 4: Run the test to verify it passes**

Run the same command as Step 2:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.example.mymediaplayer.shared.MyMusicServiceTest.buildMediaItems_playlistsRoot_hasNoPlayAllOrShuffleEntries" -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` and the test passes.

- [ ] **Step 5: Run the full shared module test suite to confirm no regressions**

Run:

```bash
./gradlew :shared:testDebugUnitTest -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all existing tests still pass.

- [ ] **Step 6: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt \
        shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt
git commit -m "$(cat <<'EOF'
Remove [Play All] / [Shuffle] from Playlists root in AA browse

Brings the Playlists browse root in line with Albums / Genres /
Artists / Decades roots, which already have no top-level action
rows. The previous "shuffle across every track in every playlist"
affordance destroys the curation that motivates having playlists,
and was today's gateway into the stale-m3u-URI failure cascade
tracked in #244.

Songs root keeps its [Play All] / [Shuffle] deliberately — it is
a flat list, not a category browser. Per-playlist actions inside
an opened playlist are unchanged.

Fixes #245.
EOF
)"
```

---

## Task 2: Clean up the now-unreachable handler code

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`
  - Branch in `resolveTracksForListKey()` at lines 1984–1996.
  - Branch in `resolvePlaylistNameForListKey()` at line 2057.
  - Imports at lines 46 and 47.

After Task 1, no UI path produces an `action:play_all:playlists` or `action:shuffle:playlists` mediaId, so the handler branches that resolve them are dead. Deleting them keeps the dispatcher tight and removes a misleading API surface for future readers.

- [ ] **Step 1: Remove the `PLAYLISTS_ID` branch from `resolveTracksForListKey`**

Use the Edit tool with:

`old_string`:

```kotlin
            listKey == SONGS_ID -> mediaCacheService.cachedFiles
            listKey == PLAYLISTS_ID -> {
                val all = kotlinx.coroutines.coroutineScope {
                    mediaCacheService.discoveredPlaylists.map { playlist ->
                        async(Dispatchers.IO) {
                            playlistService.readPlaylist(
                                this@MyMusicService,
                                Uri.parse(playlist.uriString)
                            )
                        }
                    }.awaitAll().flatten()
                }
                enrichFromCache(all)
            }
            listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
```

`new_string`:

```kotlin
            listKey == SONGS_ID -> mediaCacheService.cachedFiles
            listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
```

- [ ] **Step 2: Remove the `PLAYLISTS_ID` branch from `resolvePlaylistNameForListKey`**

Use the Edit tool with:

`old_string`:

```kotlin
            listKey == SONGS_ID -> "All Songs"
            listKey == PLAYLISTS_ID -> "All Playlists"
            listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
```

`new_string`:

```kotlin
            listKey == SONGS_ID -> "All Songs"
            listKey.startsWith(PLAYLIST_SHORT_PREFIX) -> {
```

- [ ] **Step 3: Remove the two now-unused coroutine imports**

Use the Edit tool with:

`old_string`:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
```

`new_string`:

```kotlin
import kotlinx.coroutines.launch
```

If you get an "unique match" error on this Edit, verify the imports block by reading lines 40–55 of `MyMusicService.kt` first. The expected current state is:

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
```

If the order differs, do two separate single-line removals instead of the three-line replace.

- [ ] **Step 4: Run the full shared module test suite**

Run:

```bash
./gradlew :shared:testDebugUnitTest -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests still pass (including the one added in Task 1).

- [ ] **Step 5: Build the shared and mobile modules to verify the unused-import removal compiles cleanly**

Run:

```bash
./gradlew :shared:assembleDebug :mobile:assembleDebug -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`. (`async`, `Dispatchers`, and `awaitAll` are otherwise grepped to be untouched — only `async` and `awaitAll` are exclusive to the deleted branch.)

- [ ] **Step 6: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt
git commit -m "$(cat <<'EOF'
Drop dead PLAYLISTS_ID handler branches after removing root actions

Following the removal of [Play All] / [Shuffle] from the Playlists
browse root, the handler code that flattened every playlist's tracks
into one queue is no longer reachable from any UI path. Remove:

  - the listKey == PLAYLISTS_ID branch in resolveTracksForListKey
  - the listKey == PLAYLISTS_ID -> "All Playlists" branch in
    resolvePlaylistNameForListKey
  - the now-unused kotlinx.coroutines.async and ...awaitAll imports

PLAYLISTS_ID itself stays (used as the browseable root identifier),
as do ACTION_PLAY_ALL_PREFIX / ACTION_SHUFFLE_PREFIX (used by Songs
root and per-category actions).

Part of #245.
EOF
)"
```

---

## Manual verification on device (after both tasks)

These are not part of the merge criteria but worth running once before opening the PR:

1. `./gradlew :mobile:installDebug` to push the new build to the connected phone.
2. `adb shell am force-stop com.example.mymediaplayer` so the service cold-starts off the new code.
3. Open Android Auto / DHU → MyMediaPlayer → Playlists.
   - **Expected:** the list contains only your saved playlists and smart playlists. No `[Play All]` / `[Shuffle]` rows at the top.
4. Tap into a specific playlist.
   - **Expected:** `[Play All]` and `[Shuffle]` rows appear at the top of *that* playlist's tracks (unchanged behaviour).
5. Back out → Songs.
   - **Expected:** `[Play All]` and `[Shuffle]` rows are still present at the top (unchanged behaviour — Songs root is the deliberate exception).

---

## Self-Review Notes

- **Spec coverage:** All four "Changes" listed in the spec map to concrete steps — `buildPlaylistsItems()` rewrite (Task 1 step 3), `resolveTracksForListKey()` branch removal (Task 2 step 1), `resolvePlaylistNameForListKey()` branch removal (Task 2 step 2), plus the added test (Task 1 step 1).
- **Spec out-of-scope items** (stale-m3u URIs, Songs-root parity, smart playlists, per-playlist internals) are not touched by any task in this plan.
- **Placeholder scan:** no TBD / TODO / "appropriate handling" / "similar to above" / vague gestures. Every step has the actual code or command.
- **Type consistency:** `PlaylistInfo(uriString, displayName)` matches `shared/src/main/java/com/example/mymediaplayer/shared/PlaylistInfo.kt`. `service.mediaCacheService.addPlaylist(...)` matches `MediaCacheService.addPlaylist(playlistInfo: PlaylistInfo)`. `service.buildMediaItems(parentId)` matches `internal fun buildMediaItems(parentId: String): MutableList<MediaItem>`. `MyMusicService.PLAYLISTS_ID` matches `internal const val PLAYLISTS_ID = "playlists"`.
- **Import-removal robustness:** Task 2 Step 3 has a fallback path documented if the import block doesn't match the expected adjacent ordering, so the engineer isn't blocked.
