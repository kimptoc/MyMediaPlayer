# Android Auto Home Page Plan

## Summary
Add a dedicated Android Auto home page in the MediaBrowser browse tree with button-like entries for Search, Songs, Playlists, Albums, Genres, Artists, and Decades. This new Home list becomes the root. The previous root list remains accessible via a “Browse Library” entry. Empty sections are hidden.

## Current State
- Android Auto browsing uses `MyMusicService.onLoadChildren()` in `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`.
- Root (`ROOT_ID`) currently surfaces Search, Songs, Playlists, and categories when data exists.
- No explicit home node exists.

## Goals
- Introduce an Android Auto home page list with button-like entries.
- Keep Search on the home page.
- Preserve access to the previous root list via a “Browse Library” entry.

## Non-Goals
- No mobile UI changes.
- No playback logic changes.
- No changes to existing media item IDs for categories.

## Public API / Interface Changes
- Add browse IDs:
  - `HOME_ID = "home"` (optional helper if needed)
  - `BROWSE_LIBRARY_ID = "browse_library"`
- Root now returns Home entries and a link to “Browse Library”.
- Existing IDs (`SONGS_ID`, `PLAYLISTS_ID`, `ALBUMS_ID`, `GENRES_ID`, `ARTISTS_ID`, `DECADES_ID`, `SEARCH_ID`) remain unchanged.

## Behavior Specification

### Root (Home) list
Root (`ROOT_ID`) returns:
1. Search (always shown)
2. Songs (shown if songs exist)
3. Playlists (shown if playlists exist)
4. Albums (shown if songs exist)
5. Genres (shown if songs exist)
6. Artists (shown if songs exist)
7. Decades (shown if songs exist)
8. Browse Library (always shown; navigates to legacy root list)

All entries are `MediaItem.FLAG_BROWSABLE`.

### Browse Library list (legacy root)
`BROWSE_LIBRARY_ID` returns the **previous** root contents unchanged.

### Empty State
- Hide empty sections (songs/categories/playlists) as in current behavior.
- Search and Browse Library always visible.

## Implementation Plan

1. Add IDs in `MyMusicService.kt`:
   - `BROWSE_LIBRARY_ID` (and `HOME_ID` if helpful for clarity).
2. Refactor root logic:
   - Extract current root list into `buildLegacyRootItems()`.
   - Update `ROOT_ID` branch to return new Home list.
   - Add `BROWSE_LIBRARY_ID` branch to return `buildLegacyRootItems()`.
3. Add “Browse Library” item to the Home list.
4. Keep all other browse branches unchanged.

## Testing

Add unit tests under `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt`:
1. Root home includes Search and Browse Library, and hides empty categories.
2. Browse Library returns legacy root items for the same cache state.

## Acceptance Criteria
- Auto home shows Search + category buttons.
- Browse Library navigates to old root list.
- Empty categories are hidden.
- Existing category IDs remain unchanged.

## Assumptions
- Home is delivered via MediaBrowser browse list (not a separate Car App UI).
- Search remains on home (confirmed).
- Hide empty entries (confirmed).
