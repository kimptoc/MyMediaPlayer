# Remove [Play All] / [Shuffle] from Playlists Root — Design

**Issue:** [#245](https://github.com/kimptoc/MyMediaPlayer/issues/245)
**Date:** 2026-05-14

## Goal

Bring the Playlists browse root in Android Auto into line with the Albums / Genres / Artists / Decades roots, which already do not offer top-level `[Play All]` / `[Shuffle]` actions. Drop the misleading "shuffle across every track in every playlist" affordance, which (a) destroys the curation that motivates having playlists in the first place, and (b) was today's gateway into the stale-m3u-URI failure cascade now tracked in #244.

## Context

Tracing `[Play All]` / `[Shuffle]` across the full browse hierarchy:

| Browse page | Has `[Play All]` / `[Shuffle]`? | Sensible? |
|---|---|---|
| Songs root | Yes | Yes — flat list, "all my songs" |
| **Playlists root** | **Yes** | **No — cross-playlist shuffle destroys curation** |
| Albums / Genres / Artists / Decades roots | No | Yes — they are category browsers |
| Inside a specific Album / Genre / Artist / Decade | Yes | Yes — tracks of that thing |
| Inside a specific Playlist (via `buildSongListItems`) | Yes | Yes — tracks of that playlist |
| Letter buckets (Songs A / B / C …) | Yes | Yes — tracks of that letter |

Playlists root is the lone anomaly. Songs root is intentionally kept as the flat-list special case ("shuffle my whole library" is a common driver intent).

## Changes

All in `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`.

### 1. `buildPlaylistsItems()` — drop the action MediaItems

Current (lines 1060–1093):

```kotlin
private fun buildPlaylistsItems(): MutableList<MediaItem> {
    val items = mutableListOf<MediaItem>()
    if (mediaCacheService.discoveredPlaylists.isNotEmpty() ||
        mediaCacheService.cachedFiles.isNotEmpty()
    ) {
        items.add( /* [Play All] -> ACTION_PLAY_ALL_PREFIX + PLAYLISTS_ID */ )
        items.add( /* [Shuffle]  -> ACTION_SHUFFLE_PREFIX + PLAYLISTS_ID */ )
    }
    items += playlistEntriesForBrowse(mediaCacheService.discoveredPlaylists).map { ... }
    return items
}
```

Target:

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

The pre-empty `if` guard goes away because an empty `discoveredPlaylists` simply yields an empty list — the previous guard existed solely to suppress the action items when there was nothing to play.

### 2. `resolveTracksForListKey()` — drop the PLAYLISTS_ID branch

Delete this branch (lines 2043–2055):

```kotlin
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
```

This was the async fan-out that flattened every playlist's tracks into one list — only reachable from the two MediaItems being removed.

### 3. `resolvePlaylistNameForListKey()` — drop the "All Playlists" title

Delete this branch (line 2116):

```kotlin
listKey == PLAYLISTS_ID -> "All Playlists"
```

Same justification — only reachable from the removed action MediaItems.

## What stays unchanged

- `internal const val PLAYLISTS_ID = "playlists"` — still used as the browseable root identifier, by `notifyChildrenChanged(PLAYLISTS_ID)`, by `parentRequiresLoadedCache(PLAYLISTS_ID)`, and by `Home` page entries. Removing the constant would cascade into unrelated files.
- `ACTION_PLAY_ALL_PREFIX` / `ACTION_SHUFFLE_PREFIX` — still needed for Songs root and per-category actions.
- `buildSongsItems()` — Songs root keeps its `[Play All]` / `[Shuffle]` deliberately.
- `buildMediaItemsForPlaylist()` and `buildSongListItems()` — actions inside an opened playlist are unaffected.
- The dispatcher in `handlePlayFromMediaId` that routes `action:play_all:*` / `action:shuffle:*` mediaIds — unchanged; it just stops seeing `:playlists` suffix in practice.

## Testing

Add one test to `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt`:

```kotlin
@Test
fun buildMediaItems_playlistsRoot_hasNoPlayAllOrShuffleEntries() {
    val service = Robolectric.buildService(MyMusicService::class.java).get()
    val cache = service.mediaCacheService
    // Three fake discovered playlists
    cache.addPlaylist(PlaylistInfo(uriString = "content://p/1", displayName = "Mix 1"))
    cache.addPlaylist(PlaylistInfo(uriString = "content://p/2", displayName = "Mix 2"))
    cache.addPlaylist(PlaylistInfo(uriString = "content://p/3", displayName = "Mix 3"))

    val items = service.buildMediaItems("playlists")

    assertEquals(3, items.size)
    items.forEach { item ->
        val id = item.description.mediaId.orEmpty()
        assertFalse("No [Play All] entry expected at playlists root", id.startsWith("action:play_all:"))
        assertFalse("No [Shuffle] entry expected at playlists root", id.startsWith("action:shuffle:"))
    }
}
```

Verification beyond the unit test (manual, on device):
- Browse to Playlists in AA — list contains only the user's playlists, no `[Play All]` / `[Shuffle]` rows on top.
- Open a specific playlist — its internal `[Play All]` / `[Shuffle]` actions still appear and still play.
- Open Songs root — its `[Play All]` / `[Shuffle]` actions still appear and still play.

## Out of scope

- Stale-m3u-URI handling on individual playlist playback — tracked in #244.
- Any UX changes inside an opened playlist.
- Songs-root parity question — kept as deliberate exception (flat list vs. category browser).
- Smart Playlists — those live underneath the Playlists root as individual playlists, not as top-level actions; they pick up the same behaviour automatically.

## Risk

Minimal. The two removed MediaItems were the only paths into the deleted handler branches, and all callers of `PLAYLISTS_ID` outside of the deleted code are about the browseable root identity, not action handling.

Reversible via `git revert` if anything surfaces in AA review.
