# Podcasts as a separate category — design

GitHub issue: [#252](https://github.com/kimptoc/MyMediaPlayer/issues/252)

## Goal

Treat podcast-like media files as a distinct category. They appear in **Genres → Podcasts** and in **search results**, but are excluded from **All Songs**, **Albums**, **Artists**, **Decades**, and all non-Podcasts genre buckets.

## Detection

A file is classified as a podcast when **any** of the following match:

1. Its raw genre tag contains (case-insensitive) `podcast`, `audiobook`, or `spoken`.
2. Its folder path or URI contains (case-insensitive) `podcast`.

Implementation: new top-level function in `shared/`:

```kotlin
// shared/src/main/java/com/example/mymediaplayer/shared/PodcastDetection.kt
fun isPodcastMedia(rawGenre: String?, pathOrUri: String?): Boolean {
    val g = rawGenre.orEmpty().lowercase(Locale.US)
    if (g.contains("podcast") || g.contains("audiobook") || g.contains("spoken")) return true
    val p = pathOrUri.orEmpty().lowercase(Locale.US)
    return p.contains("podcast")
}
```

A shared constant `const val PODCAST_GENRE = "Podcasts"` lives in `GenreBuckets.kt` next to `bucketGenre`.

## Data model

`MediaFileInfo` gains:

```kotlin
val isPodcast: Boolean = false
```

The field is **not persisted** to Room. It is derived from `genre` + `uriString` at every entry point that creates or revives a `MediaFileInfo`. No DB migration.

Entry points that compute `isPodcast`:

- `MediaCacheService.extractCandidateMetadata()` — uses `metadata?.genre` and `candidate.parentFolderName`.
- The MediaStore scan branch around `MediaCacheService.kt:173` — uses `osGenre` and `relativePath`.
- `MediaCacheService.loadPersistedCache()` — uses `it.genre` and `Uri.decode(it.uriString)`.

## Index building

`MediaCacheService.buildAlbumArtistIndexesFromCache()` routes podcasts away from album/artist/decade indexes and into a single `Podcasts` genre bucket:

```kotlin
for (file in snapshot) {
    if (file.isPodcast) {
        genreIndex.getOrPut(PODCAST_GENRE) { mutableListOf() }.add(file)
    } else {
        val album  = file.album?.ifBlank { null } ?: "Unknown Album"
        val artist = file.artist?.ifBlank { null } ?: "Unknown Artist"
        val genre  = bucketGenre(file.genre)
        val decade = decadeLabel(file.year)
        albumIndex.getOrPut(album)  { mutableListOf() }.add(file)
        artistIndex.getOrPut(artist){ mutableListOf() }.add(file)
        genreIndex.getOrPut(genre)  { mutableListOf() }.add(file)
        decadeIndex.getOrPut(decade){ mutableListOf() }.add(file)
    }
}
```

## New accessor

```kotlin
val cachedMusicFiles: List<MediaFileInfo>
    get() = synchronized(cacheLock) { _cachedFiles.filter { !it.isPodcast } }
```

`cachedFiles` is unchanged (still all files; required by search, persistence, and URI lookup).

## Callsite audit — `MyMusicService`

**Switch from `cachedFiles` to `cachedMusicFiles`** (exclude podcasts):

| Line | Use |
|------|-----|
| 918  | `buildArtistCounts` argument |
| 962  | letter-filter for songs browse |
| 1016 | empty check before adding the All-Songs row |
| 1050 | `buildSongsAllItems` titles → letter buckets |
| 1074 | `buildAlbumCounts` argument |
| 1103 | `buildDecadeCounts` argument |
| 2016 | songs list for `SONGS_ID` |
| 2054 | letter filter on the music side |
| 2188 | fallback queue source |
| 2215, 2216 | queue building for `SONGS_ID` / `SONGS_ALL_ID` |
| 2239 | fallback letter filter |
| 2471 | empty check |
| 2514 | smart-playlist source (favorites / recently-added / most-played / flagged) |
| 2923 | home-screen `songCount` |

**Keep `cachedFiles`** (need every file, including podcasts):

| Line | Use |
|------|-----|
| 663  | URI lookup for `playProvidedUriList` |
| 2168 | first-by-URI lookup |
| 2323 | free-text search — podcasts ARE findable in search |
| 2410, 2445, 2448, 2451, 2454 | voice-search filters — voice search is a query, same logic as text search |
| 635, 775 | log/diagnostic counters only |

**`buildGenreCounts` (L1256) — small special-case:** route `isPodcast` files to the `PODCAST_GENRE` key instead of `bucketGenre(file.genre)`. Continue to pass `cachedFiles` (all) so podcasts contribute to the Podcasts count.

```kotlin
private fun buildGenreCounts(files: List<MediaFileInfo>): Map<String, Int> =
    files.groupingBy { file ->
        if (file.isPodcast) PODCAST_GENRE else bucketGenre(file.genre)
    }.eachCount()
```

## Search behaviour

`searchFiles(query)` in `MediaCacheService` is unchanged — it iterates `_cachedFiles`, so podcasts remain searchable. Voice search filters in `MyMusicService` also keep using `cachedFiles`.

## Tests

1. **`PodcastDetectionTest.kt`** (new, JVM unit) — table-driven cases for `isPodcastMedia`:
   - Tagged genres: `"Podcast"`, `"podcast"`, `"Audiobook"`, `"Spoken Word"`, `"Podcast;News"` → true.
   - Path matches: `"/storage/Podcasts/show.mp3"`, `"file://.../podcast-feeds/ep.mp3"` → true.
   - Plain music: `"Rock"` + `"/Music/AC-DC/..."` → false.
   - Null and empty inputs → false.

2. **`MediaCacheServicePodcastIndexTest.kt`** (new, JVM unit) — populate `_cachedFiles` with a mix of music + podcast files, call `buildAlbumArtistIndexesFromCache()`, assert:
   - `genres()` contains `"Podcasts"`.
   - `songsForGenre("Podcasts")` contains exactly the podcast files.
   - Podcast files are absent from `albums()`, `artists()`, `decades()`, and from every non-Podcasts `songsForGenre(...)`.
   - Music files are indexed normally.

3. **`MyMusicServiceGenreCountsTest.kt`** (new, JVM unit) — focused test for the updated `buildGenreCounts`, asserting podcasts count toward `"Podcasts"` and not `"Other"`.

No automated coverage planned for the wide `cachedFiles` → `cachedMusicFiles` swap in `MyMusicService` — those are mechanical refactors. Verification is via build + manual smoke:

- Genres list shows a `Podcasts` entry; tapping it lists the podcast files.
- All Songs, Albums, Artists, Decades omit podcast files.
- Search finds podcasts.

## Out of scope

- Podcast-specific UI affordances (episode ordering, playback resume per episode).
- A top-level `Podcasts` browse node alongside Songs/Albums/Artists.
- Migration of the persisted Room cache (no schema change needed).
