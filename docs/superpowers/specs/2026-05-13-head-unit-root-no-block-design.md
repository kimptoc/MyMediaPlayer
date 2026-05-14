# Don't block ROOT/SEARCH/HOME on isScanning

## Problem

When Android Auto (head unit / DHU) opens MyMediaPlayer on a cold service start, it shows a spinner and then a hard error: **"MyMediaPlayer doesn't seem to be working at the moment."**

The service is not actually dead — playback continues if a track was already in the session — but `MediaBrowserService.onLoadChildren` for the root tree fails to respond within Android Auto's per-call timeout, so Android Auto declares the app unhealthy.

### Why it happens

In `MyMusicService.onCreate()` the service calls `loadCachedTreeIfAvailable()`, which:

1. Sets `isScanning = true` synchronously.
2. Launches a coroutine that loads the persisted Room cache, rebuilds album/artist/genre/decade indexes (and, on cache miss, runs a full scan).
3. Sets `isScanning = false` only after all of that finishes, then delivers the queued results.

While `isScanning == true`, `onLoadChildren` (shared/.../MyMusicService.kt:735) queues **every** parentId into `pendingResults` and detaches the result — including `ROOT_ID` and `SEARCH_ID`, which return purely static items ("Home", "Search") that need zero cached song data.

For libraries large enough that index rebuild (or, worse, a full re-scan) takes longer than Android Auto's tolerance, the root call never resolves in time. This is amplified by the documented DHU-reconnect → service-destroy/recreate cycle (MEMORY.md), which means in-memory state is empty on every fresh DHU session.

## Goal

The root menu must always render quickly enough for Android Auto to consider the app healthy, regardless of cache state.

Out of scope:

- Persisting metadata indexes to skip the rebuild step.
- Investigating why `loadPersistedCache` may return null on cold start (a separate "rescans every time" bug, only relevant if the spinner duration is in the tens of seconds).
- Changing the scan logic itself.

## Design

Classify parent IDs by whether they require the cached song list to render meaningful content:

| Category | Parent IDs | Behaviour while `isScanning` |
|---|---|---|
| Cache-independent | `ROOT_ID`, `SEARCH_ID` | Respond immediately with static items. |
| Cache-tolerant | `HOME_ID` | Respond immediately. `buildHomeItems()` already returns an empty list when no songs/playlists are loaded, and `notifyChildrenChanged(HOME_ID)` is already called when the load finishes (line 1404). |
| Cache-dependent | `SONGS_ID`, `SONGS_ALL_ID`, `ALBUMS_ID`, `ARTISTS_ID`, `GENRES_ID`, `DECADES_ID`, `PLAYLISTS_ID`, and all prefix-based children | Queue into `pendingResults` and detach (existing behaviour). |

Implementation:

1. Add an internal predicate `parentRequiresLoadedCache(parentId: String): Boolean` that returns `false` for `ROOT_ID`, `SEARCH_ID`, `HOME_ID`, and `true` otherwise (conservative default — anything unknown is treated as cache-dependent).
2. In `onLoadChildren`, change the gate from `if (isScanning)` to `if (isScanning && parentRequiresLoadedCache(parentId))`. Everything else in the method is unchanged.

That is the entire production change.

## Why this fix specifically

- The DHU error is *the root tree timing out*, not user impatience. The minimum sufficient fix is to make the root tree respond fast. Anything more is scope creep.
- Cache-dependent screens still queue while scanning. This is fine because the user has to navigate into them deliberately; by the time they do, either the cache has loaded (instant response) or the queue resolves shortly after.
- We deliberately keep `HOME_ID` cache-tolerant rather than cache-dependent because `buildHomeItems()` already handles the empty case gracefully, and Android Auto's "Home" tab needs to be reachable as soon as the user picks it from the root.

## Testing

**Unit:**

- Test `parentRequiresLoadedCache` directly: `false` for `ROOT_ID`, `SEARCH_ID`, `HOME_ID`; `true` for `SONGS_ID`, `ALBUMS_ID`, `ARTIST_PREFIX + "Foo"`, etc.

**Manual on DHU:**

1. Force-stop the phone process: `adb shell am force-stop com.example.mymediaplayer`.
2. Connect/relaunch DHU.
3. Confirm the app loads to the root with [Home, Search] visible within ~1 s — no spinner, no "doesn't seem to be working" error.
4. Tap Home → confirm it renders (empty body initially is acceptable; should populate as soon as cache+indexes load).
5. Tap into Songs/Albums → confirm it eventually populates (queued during scan, delivered when scan completes).
6. Verify playback initiated before the test continues uninterrupted.

## Risk

Very low. The predicate is a pure function, the change is one conditional, and the cache-dependent path is unchanged. The only behavioural change is that ROOT/SEARCH/HOME no longer wait — and they have nothing to wait for.
