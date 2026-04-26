# UI Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize the phone and Android Auto UI to follow Android media player conventions, improve space efficiency, and move settings out of the overflow menu.

**Architecture:** Six independent PRs, each on its own branch off `main`. Changes are purely in the phone UI layer (`mobile/`) except Task 6 which touches `shared/`. The core approach is: extract settings to a dedicated screen, simplify the playback bar to a mini-player, move search to the top bar, convert playlists from stacked panels to list-detail navigation, merge decades into songs as a filter, and clean up Auto browse text.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), MediaBrowserServiceCompat

---

## File Map

| Task | Files Created/Modified | Purpose |
|------|----------------------|---------|
| 1 | Create: `mobile/.../SettingsScreen.kt` | Dedicated settings screen |
| 1 | Modify: `MainScreen.kt` (lines 225-347, menu items) | Remove settings from overflow menu |
| 1 | Modify: `MainActivity.kt` | Add settings navigation state + wire callbacks |
| 2 | Modify: `MainScreen.kt` (lines 2385-2490, PlaybackBar) | Mini-player pattern |
| 3 | Modify: `MainScreen.kt` (lines 225-347, TopAppBar + lines 392-416, search) | Move search to TopAppBar |
| 4 | Modify: `MainScreen.kt` (lines 1924-2293, PlaylistsSection) | List-to-detail navigation |
| 5 | Modify: `MainScreen.kt` (tabs + SongsTabContent) | Merge Decades into Songs as filter |
| 5 | Modify: `MainViewModel.kt` (LibraryTab enum, state) | Remove Decades tab, add decade filter |
| 6 | Modify: `shared/.../MyMusicService.kt` | Clean up category count text in Auto browse |

---

## Task 1: Extract Settings to Dedicated Screen

**Branch:** `ui/settings-screen`

**Files:**
- Create: `mobile/src/main/java/com/example/mymediaplayer/SettingsScreen.kt`
- Modify: `mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt`
- Modify: `mobile/src/main/java/com/example/mymediaplayer/MainActivity.kt`

### What moves to SettingsScreen

These menu items move OUT of the dropdown and INTO a new `SettingsScreen` composable:
- Track Voice Intro toggle (line 278-284)
- Track Voice Outro toggle (line 285-291)
- AI Announcement Settings (line 292-298)
- Bluetooth Auto-Play toggle (line 299-305)
- Trust Connected Bluetooth (line 306-312)
- Manage Trusted Bluetooth (line 313-319)
- Bluetooth Diagnostics (line 320-327)
- Select Playlist Save Folder (line 262-267)

These stay in the overflow menu (they are actions, not settings):
- Select Folder (scan action)
- Create Random Playlist (action)

The overflow menu gets a new "Settings" item that navigates to the settings screen.

- [ ] **Step 1: Create SettingsScreen.kt with grouped settings**

```kotlin
// mobile/src/main/java/com/example/mymediaplayer/SettingsScreen.kt
package com.example.mymediaplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    trackVoiceIntroEnabled: Boolean,
    trackVoiceOutroEnabled: Boolean,
    onToggleTrackVoiceIntro: () -> Unit,
    onToggleTrackVoiceOutro: () -> Unit,
    cloudAnnouncementKiloKey: String,
    cloudAnnouncementTtsKey: String,
    onSaveCloudAnnouncementKeys: (kiloKey: String, ttsKey: String, onValidated: () -> Unit) -> Unit,
    debugCloudAnnouncements: Boolean,
    onSetDebugCloudAnnouncements: (Boolean) -> Unit,
    bluetoothAutoPlayEnabled: Boolean,
    onToggleBluetoothAutoPlay: () -> Unit,
    onAddCurrentBluetoothDevice: () -> Unit,
    trustedBluetoothDevices: List<TrustedBluetoothDevice>,
    onRemoveTrustedBluetoothDevice: (String) -> Unit,
    onClearTrustedBluetoothDevices: () -> Unit,
    bluetoothDiagnostics: String,
    onRefreshBluetoothDiagnostics: () -> Unit,
    onChoosePlaylistSaveFolder: () -> Unit,
    onBack: () -> Unit
) {
    var showCloudAnnouncementSettingsDialog by remember { mutableStateOf(false) }
    var showManageTrustedBluetoothDialog by remember { mutableStateOf(false) }
    var showBluetoothDiagnosticsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // -- Voice Announcements --
            Text("Voice Announcements", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))

            SettingsToggleRow(
                label = "Track Voice Intro",
                checked = trackVoiceIntroEnabled,
                onToggle = onToggleTrackVoiceIntro
            )
            SettingsToggleRow(
                label = "Track Voice Outro",
                checked = trackVoiceOutroEnabled,
                onToggle = onToggleTrackVoiceOutro
            )
            SettingsClickRow(
                label = "AI Announcement Settings",
                onClick = { showCloudAnnouncementSettingsDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // -- Bluetooth --
            Text("Bluetooth", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))

            SettingsToggleRow(
                label = "Bluetooth Auto-Play",
                checked = bluetoothAutoPlayEnabled,
                onToggle = onToggleBluetoothAutoPlay
            )
            SettingsClickRow(
                label = "Trust Connected Bluetooth",
                onClick = onAddCurrentBluetoothDevice
            )
            SettingsClickRow(
                label = "Manage Trusted Bluetooth",
                onClick = { showManageTrustedBluetoothDialog = true }
            )
            SettingsClickRow(
                label = "Bluetooth Diagnostics",
                onClick = {
                    onRefreshBluetoothDiagnostics()
                    showBluetoothDiagnosticsDialog = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // -- Storage --
            Text("Storage", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))

            SettingsClickRow(
                label = "Select Playlist Save Folder",
                onClick = onChoosePlaylistSaveFolder
            )
        }
    }

    // Reuse dialog composables moved from MainScreen
    if (showCloudAnnouncementSettingsDialog) {
        CloudAnnouncementSettingsDialog(
            cloudAnnouncementKiloKey = cloudAnnouncementKiloKey,
            cloudAnnouncementTtsKey = cloudAnnouncementTtsKey,
            debugCloudAnnouncements = debugCloudAnnouncements,
            onSetDebugCloudAnnouncements = onSetDebugCloudAnnouncements,
            onSaveCloudAnnouncementKeys = onSaveCloudAnnouncementKeys,
            onDismissRequest = { showCloudAnnouncementSettingsDialog = false }
        )
    }

    if (showManageTrustedBluetoothDialog) {
        ManageTrustedBluetoothDialog(
            trustedBluetoothDevices = trustedBluetoothDevices,
            onRemoveTrustedBluetoothDevice = onRemoveTrustedBluetoothDevice,
            onClearTrustedBluetoothDevices = onClearTrustedBluetoothDevices,
            onDismissRequest = { showManageTrustedBluetoothDialog = false }
        )
    }

    if (showBluetoothDiagnosticsDialog) {
        BluetoothDiagnosticsDialog(
            bluetoothDiagnostics = bluetoothDiagnostics,
            onRefreshBluetoothDiagnostics = onRefreshBluetoothDiagnostics,
            onDismissRequest = { showBluetoothDiagnosticsDialog = false }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SettingsClickRow(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
```

The dialog composables (`CloudAnnouncementSettingsDialog`, `ManageTrustedBluetoothDialog`, `BluetoothDiagnosticsDialog`) are extracted from `MainScreen.kt` and made `internal` so both files can use them during migration. They keep the exact same implementation as the existing `CloudAnnouncementSettingsDialogContent` (line 1048), `ManageTrustedBluetoothDialogContent` (line 1120), and the bluetooth diagnostics AlertDialog (line 992).

- [ ] **Step 2: Extract dialog composables from MainScreen.kt**

Move these three composables from `MainScreen.kt` into `SettingsScreen.kt`, renaming them:
- `CloudAnnouncementSettingsDialogContent` (lines 1048-1118) -> `CloudAnnouncementSettingsDialog` (internal)
- `ManageTrustedBluetoothDialogContent` (lines 1120-1178) -> `ManageTrustedBluetoothDialog` (internal)
- Bluetooth diagnostics AlertDialog (lines 992-1013) -> `BluetoothDiagnosticsDialog` (internal)

Remove the private versions from `MainScreen.kt` and their dialog state variables (`showCloudAnnouncementSettingsDialog`, `showManageTrustedBluetoothDialog`, `showBluetoothDiagnosticsDialog`) and the corresponding `if` blocks at lines 983-1025.

- [ ] **Step 3: Add navigation state to MainActivity**

In `MainActivity.kt`, add a `showSettingsScreen` mutableStateOf(false) alongside the other state variables (around line 118). In the `setContent` block, conditionally show either `SettingsScreen` or `MainScreen` based on this flag.

```kotlin
// In MainActivity, add state
private val showSettingsScreen = mutableStateOf(false)

// In setContent block, wrap the existing MainScreen call:
if (showSettingsScreen.value) {
    LcarsTheme {
        SettingsScreen(
            trackVoiceIntroEnabled = trackVoiceIntroEnabled.value,
            trackVoiceOutroEnabled = trackVoiceOutroEnabled.value,
            onToggleTrackVoiceIntro = { toggleTrackVoiceIntro() },
            onToggleTrackVoiceOutro = { toggleTrackVoiceOutro() },
            cloudAnnouncementKiloKey = cloudAnnouncementKiloKey.value,
            cloudAnnouncementTtsKey = cloudAnnouncementTtsKey.value,
            onSaveCloudAnnouncementKeys = ::saveCloudAnnouncementKeys,
            debugCloudAnnouncements = debugCloudAnnouncements.value,
            onSetDebugCloudAnnouncements = ::setDebugCloudAnnouncements,
            bluetoothAutoPlayEnabled = bluetoothAutoPlayEnabled.value,
            onToggleBluetoothAutoPlay = { toggleBluetoothAutoPlay() },
            onAddCurrentBluetoothDevice = { addCurrentBluetoothDevice() },
            trustedBluetoothDevices = trustedBluetoothDevices.value,
            onRemoveTrustedBluetoothDevice = ::removeTrustedBluetoothDevice,
            onClearTrustedBluetoothDevices = ::clearTrustedBluetoothDevices,
            bluetoothDiagnostics = bluetoothDiagnostics.value,
            onRefreshBluetoothDiagnostics = { refreshBluetoothDiagnostics() },
            onChoosePlaylistSaveFolder = { openPlaylistDocumentTree.launch(null) },
            onBack = { showSettingsScreen.value = false }
        )
    }
} else {
    LcarsTheme {
        MainScreen(/* existing params, plus: */
            onOpenSettings = { showSettingsScreen.value = true }
        )
    }
}
```

- [ ] **Step 4: Update MainScreen to remove settings items and add Settings menu item**

In `MainScreen.kt`:
1. Remove the 8 settings-related DropdownMenuItems (lines 278-327)
2. Remove the settings-related parameters from the `MainScreen` function signature (the bluetooth/voice/cloud params that are no longer needed in MainScreen)
3. Add `onOpenSettings: () -> Unit` parameter
4. Add a new DropdownMenuItem for "Settings" that calls `onOpenSettings()`
5. Remove the `if` blocks for the moved dialogs (lines 983-1025)
6. Remove the state variables for the moved dialogs (lines 176-178)

The resulting overflow menu should contain only:
- Select Folder
- Create Random Playlist
- Settings

- [ ] **Step 5: Build and verify**

```bash
cd /Users/kimptoc/AndroidStudioProjects/MyMediaPlayer && ./gradlew :mobile:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run tests**

```bash
./gradlew :mobile:testDebugUnitTest
```

Expected: All tests pass

- [ ] **Step 7: Commit and create PR**

```bash
git checkout -b ui/settings-screen
git add mobile/src/main/java/com/example/mymediaplayer/SettingsScreen.kt
git add mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt
git add mobile/src/main/java/com/example/mymediaplayer/MainActivity.kt
git commit -m "refactor: extract settings to dedicated SettingsScreen

Move bluetooth, voice announcement, and storage settings from the
overflow menu into a proper settings screen with grouped sections.
Overflow menu now only shows actions (Scan, Create Playlist) and
a Settings entry."
```

---

## Task 2: Simplify PlaybackBar to Mini-Player Pattern

**Branch:** `ui/mini-player`

**Files:**
- Modify: `mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt` (PlaybackBar composable, lines 2385-2490)

### Design

Replace the current multi-line PlaybackBar (title + artist + queue info + scrollable button row) with a compact mini-player:
- Single row: track info (title + artist stacked) | play/pause button
- Entire bar is already clickable to open ExpandedNowPlayingDialog (line 2408)
- Remove the horizontally scrollable buttons (repeat, queue, prev, next, stop) - these are all available in the ExpandedNowPlayingDialog already
- This saves ~60dp of vertical space

- [ ] **Step 1: Replace PlaybackBar implementation**

Replace the `PlaybackBar` composable (lines 2385-2490) with:

```kotlin
@Composable
fun PlaybackBar(
    trackName: String,
    artistName: String?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onOpenExpanded: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenExpanded() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = run {
                    val barColor = MaterialTheme.colorScheme.primary
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = barColor,
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trackName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!artistName.isNullOrBlank()) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onPlayPause,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Update PlaybackBar call site in MainScreen**

Update the `bottomBar` block (lines 348-369) to pass only the reduced parameters:

```kotlin
bottomBar = {
    if (uiState.playback.currentTrackName != null) {
        PlaybackBar(
            trackName = uiState.playback.currentTrackName,
            artistName = uiState.playback.currentArtistName,
            isPlaying = uiState.playback.isPlaying,
            onPlayPause = onPlayPause,
            onOpenExpanded = { showExpandedNowPlayingDialog = true }
        )
    }
},
```

Remove the now-unused parameters from the call: `isPlayingPlaylist`, `repeatMode`, `queueTitle`, `queuePosition`, `onStop`, `onNext`, `onPrev`, `onToggleRepeat`, `onShowQueue`, `hasNext`, `hasPrev`.

- [ ] **Step 3: Build and verify**

```bash
./gradlew :mobile:assembleDebug
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :mobile:testDebugUnitTest
```

- [ ] **Step 5: Commit and create PR**

```bash
git checkout -b ui/mini-player
git add mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt
git commit -m "refactor: simplify PlaybackBar to compact mini-player

Replace the multi-line playback bar (title, artist, queue info, 6 buttons)
with a compact mini-player showing just track info and play/pause.
All other controls remain accessible in the expanded now-playing dialog.
Saves ~60dp of vertical screen space."
```

---

## Task 3: Move Search to TopAppBar

**Branch:** `ui/search-in-appbar`

**Files:**
- Modify: `mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt`

### Design

Replace the inline search TextField (lines 392-416) with a collapsible search in the TopAppBar. When the search icon is tapped, the TopAppBar transforms into a search field. This keeps search always accessible without scrolling.

- [ ] **Step 1: Add search state and search field to TopAppBar**

Add a `var isSearchExpanded by remember { mutableStateOf(false) }` state variable alongside the existing ones (line 155 area).

Replace the TopAppBar (lines 243-330) with a conditional: when `isSearchExpanded`, show a search TextField in the TopAppBar; otherwise show the normal title + menu.

```kotlin
TopAppBar(
    title = {
        if (isSearchExpanded) {
            TextField(
                value = uiState.search.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = {
                    Text(
                        "Search title, artist, album, genre",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        } else {
            Text("MyMediaPlayer")
        }
    },
    navigationIcon = {
        if (isSearchExpanded) {
            TextButton(onClick = {
                isSearchExpanded = false
                onClearSearch()
            }) {
                Text("Back")
            }
        }
    },
    actions = {
        if (!isSearchExpanded) {
            TextButton(onClick = { isSearchExpanded = true }) {
                Text("Search")
            }
            TextButton(onClick = { menuExpanded = true }) {
                Text("Menu")
            }
            // DropdownMenu unchanged
        } else if (uiState.search.searchQuery.isNotEmpty()) {
            TextButton(onClick = onClearSearch) {
                Text("Clear")
            }
        }
    }
)
```

- [ ] **Step 2: Remove inline search from content area**

Remove the search Row (lines 392-416) that currently contains the TextField and Clear button inside the main content Column.

Keep the search results section (lines 418-507) as-is - it still renders below the tabs when `searchQuery.isNotBlank()`.

- [ ] **Step 3: Auto-expand search when query is non-empty on recomposition**

Add a `LaunchedEffect` to sync the `isSearchExpanded` state:

```kotlin
LaunchedEffect(uiState.search.searchQuery) {
    if (uiState.search.searchQuery.isNotBlank()) {
        isSearchExpanded = true
    }
}
```

- [ ] **Step 4: Build and verify**

```bash
./gradlew :mobile:assembleDebug
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :mobile:testDebugUnitTest
```

- [ ] **Step 6: Commit and create PR**

```bash
git checkout -b ui/search-in-appbar
git add mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt
git commit -m "refactor: move search bar into TopAppBar

Move search from inline content area to a collapsible search field
in the TopAppBar. Tapping 'Search' transforms the app bar into a
search input. This keeps search always accessible regardless of
scroll position."
```

---

## Task 4: Convert Playlists to List-Detail Navigation

**Branch:** `ui/playlist-navigation`

**Files:**
- Modify: `mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt` (PlaylistsSection, lines 1924-2293)

### Design

Replace the stacked two-surface layout (playlist list on top, detail below) with a single-view navigation pattern:
- When no playlist is selected: show full-height playlist list
- When a playlist is selected: show full-height playlist detail with a back button

This gives both views the full screen height instead of splitting it.

- [ ] **Step 1: Restructure PlaylistsSection to use conditional full-screen views**

Replace the current implementation (lines 1970-2006 for list + 2008-2253 for detail shown together) with mutually exclusive views:

```kotlin
// Inside PlaylistsSection, after the early return for empty playlists:

if (selectedPlaylist == null) {
    // Full-height playlist list
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            items(playlists) { playlist ->
                val isSmart = playlist.uriString.startsWith(MainViewModel.SMART_PREFIX)
                PlaylistCard(
                    playlist = playlist,
                    isCompact = false,
                    isSmart = isSmart,
                    onRename = { if (!isSmart) onRequestRenamePlaylist(playlist) },
                    onDelete = { if (!isSmart) onRequestDeletePlaylist(playlist) },
                    onClick = { onPlaylistSelected(playlist) }
                )
            }
        }
    }
} else {
    // Full-height playlist detail (same content as current lines 2014-2253)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TextButton(
                onClick = {
                    if (hasUnsavedChanges) {
                        pendingSelectPlaylist = null
                        pendingClearSelection = true
                        showDiscardChangesDialog = true
                    } else {
                        onClearPlaylistSelection()
                    }
                }
            ) {
                Text("Back to all playlists")
            }
            // ... rest of detail content unchanged from current lines 2033-2253
        }
    }
}
```

The key change: remove the `Spacer(modifier = Modifier.height(8.dp))` between the two surfaces and make them mutually exclusive via `if/else`. Remove the compact/filtered playlist list that currently shows when a playlist is selected.

- [ ] **Step 2: Build and verify**

```bash
./gradlew :mobile:assembleDebug
```

- [ ] **Step 3: Run tests**

```bash
./gradlew :mobile:testDebugUnitTest
```

- [ ] **Step 4: Commit and create PR**

```bash
git checkout -b ui/playlist-navigation
git add mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt
git commit -m "refactor: convert playlists to list-detail navigation

Replace stacked two-surface playlist layout with mutually exclusive
list/detail views. Selecting a playlist now shows the detail view
at full height instead of splitting the screen. Back button returns
to the full playlist list."
```

---

## Task 5: Merge Decades Tab into Songs as Filter

**Branch:** `ui/decades-filter`

**Files:**
- Modify: `mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt`
- Modify: `mobile/src/main/java/com/example/mymediaplayer/MainViewModel.kt`

### Design

Remove the Decades tab and add a decade filter dropdown to the Songs tab, reducing from 6 tabs to 5. The Songs tab gets a filter row: "Favorites only" | "Decade: All/1970s/1980s/..."

- [ ] **Step 1: Remove Decades from LibraryTab enum**

In `MainViewModel.kt`, remove `Decades("Decades")` from the `LibraryTab` enum (line 107).

Remove `selectedDecade` from `LibraryState` (line 67).
Remove `decades` from `LibraryState` (line 72).

- [ ] **Step 2: Update MainViewModel to remove Decades-related logic**

Search for and remove/update all references to `LibraryTab.Decades`, `selectedDecade`, and `decades` in `MainViewModel.kt`. This includes:
- The `selectDecade()` function
- The `clearCategorySelection()` decade branch
- Any decade list building in metadata refresh

- [ ] **Step 3: Add decade filter to SongsTabContent**

In `MainScreen.kt`, update `SongsTabContent` to accept a decade filter:

```kotlin
@Composable
private fun SongsTabContent(
    songsFavoritesOnly: Boolean,
    onToggleSongsFavoritesOnly: () -> Unit,
    selectedDecade: String?,
    availableDecades: List<String>,
    onDecadeSelected: (String?) -> Unit,
    scannedFiles: List<MediaFileInfo>,
    favoriteUris: Set<String>,
    // ... rest of existing params
) {
    val filteredByDecade = if (selectedDecade != null) {
        scannedFiles.filter { decadeLabelForYear(it.year) == selectedDecade }
    } else {
        scannedFiles
    }
    val songsForTab = if (songsFavoritesOnly) {
        filteredByDecade.filter { it.uriString in favoriteUris }
    } else {
        filteredByDecade
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onToggleSongsFavoritesOnly) {
            Text(if (songsFavoritesOnly) "Show all songs" else "Favorites only")
        }

        var decadeMenuExpanded by remember { mutableStateOf(false) }
        TextButton(onClick = { decadeMenuExpanded = true }) {
            Text("Decade: ${selectedDecade ?: "All"}")
        }
        DropdownMenu(
            expanded = decadeMenuExpanded,
            onDismissRequest = { decadeMenuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All") },
                onClick = {
                    decadeMenuExpanded = false
                    onDecadeSelected(null)
                }
            )
            availableDecades.forEach { decade ->
                DropdownMenuItem(
                    text = { Text(decade) },
                    onClick = {
                        decadeMenuExpanded = false
                        onDecadeSelected(decade)
                    }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    SongsListSection(
        title = if (songsFavoritesOnly) {
            "Favorites (${songsForTab.size})"
        } else {
            "${songsForTab.size} file(s) found"
        },
        songs = songsForTab,
        // ... rest unchanged
    )
}
```

- [ ] **Step 4: Update the Songs tab call site and remove Decades tab**

Remove the `LibraryTab.Decades -> { ... }` case (lines 740-768).

Remove the `onDecadeSelected` parameter from `MainScreen` if it's no longer used for the tab.

Update the call site for `SongsTabContent` (around line 542) to pass the new decade filter params:

```kotlin
LibraryTab.Songs -> {
    var selectedDecade by rememberSaveable { mutableStateOf<String?>(null) }
    val availableDecades = remember(uiState.scan.scannedFiles) {
        uiState.scan.scannedFiles
            .map { decadeLabelForYear(it.year) }
            .filter { it != "Unknown Decade" }
            .distinct()
            .sorted()
    }
    SongsTabContent(
        songsFavoritesOnly = songsFavoritesOnly,
        onToggleSongsFavoritesOnly = { songsFavoritesOnly = !songsFavoritesOnly },
        selectedDecade = selectedDecade,
        availableDecades = availableDecades,
        onDecadeSelected = { selectedDecade = it },
        scannedFiles = uiState.scan.scannedFiles,
        // ... rest unchanged
    )
}
```

- [ ] **Step 5: Remove decadeCounts from MainScreen**

Remove the `val decadeCounts = remember(...)` line (line 512) since it's no longer needed.

- [ ] **Step 6: Build and verify**

```bash
./gradlew :mobile:assembleDebug
```

- [ ] **Step 7: Run tests**

```bash
./gradlew :mobile:testDebugUnitTest
```

- [ ] **Step 8: Commit and create PR**

```bash
git checkout -b ui/decades-filter
git add mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt
git add mobile/src/main/java/com/example/mymediaplayer/MainViewModel.kt
git commit -m "refactor: merge Decades tab into Songs as dropdown filter

Remove the Decades tab and add a decade filter dropdown to the Songs
tab. Reduces tab count from 6 to 5. Decade selection is now a
dropdown in the Songs filter row alongside the favorites toggle."
```

---

## Task 6: Clean Up Android Auto Category Count Text

**Branch:** `ui/auto-category-text`

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`
- Modify: `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt` (if tests assert old format)

### Design

The home-level categories already use `.setSubtitle("$count genre(s)")` correctly. But within category lists (e.g., listing all genres), individual items use `"$name ($count)"` as the title. Change these to use title for just the name and subtitle for the count.

- [ ] **Step 1: Find and update buildCategoryBrowsableItems**

Search `MyMusicService.kt` for the helper that builds category browsable items. Look for patterns like `"$name ($count)"` in title construction. Change to:

```kotlin
// Before:
.setTitle("$name ($count)")

// After:
.setTitle(name)
.setSubtitle("$count song${if (count != 1) "s" else ""}")
```

- [ ] **Step 2: Run existing tests**

```bash
./gradlew :shared:testDebugUnitTest
```

Fix any tests that assert the old title format.

- [ ] **Step 3: Build and verify**

```bash
./gradlew :mobile:assembleDebug :shared:assembleDebug
```

- [ ] **Step 4: Commit and create PR**

```bash
git checkout -b ui/auto-category-text
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt
git add shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt
git commit -m "fix: clean up Android Auto category item text formatting

Move song counts from category titles to subtitles in the Android Auto
browse tree for consistency. Titles now show just the category name."
```

---

## Execution Order

Tasks are independent and can be executed in any order since they're on separate branches. However, if merging sequentially, the recommended order is:

1. **Task 1** (Settings Screen) - biggest menu change, reduces MainScreen complexity
2. **Task 2** (Mini-Player) - independent of menu changes
3. **Task 3** (Search in AppBar) - touches TopAppBar, easier after menu simplification
4. **Task 4** (Playlist Navigation) - independent section of MainScreen
5. **Task 5** (Decades Filter) - touches tabs and ViewModel
6. **Task 6** (Auto Category Text) - separate module entirely

Each PR should be reviewed and merged independently. Merge conflicts between branches should be minimal since each task touches different sections of `MainScreen.kt`.
