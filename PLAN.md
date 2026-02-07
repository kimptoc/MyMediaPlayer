# MyMediaPlayer — Implementation Plan

## Roadmap

- **Task 1** — Mobile: Directory scanner with Compose UI (find files)
- **Task 2** — Mobile + Android Auto: Play media files (stop/pause/resume, volume)
- **Task 3** — Mobile: Create playlists (random 3 files, m3u format)
- **Task 4** — Mobile + Android Auto: Play playlists (play, pause, stop, next track)

---

# Task 1: Directory Scanner with Compose UI

## Overview

Replace the bare "Hello World" XML layout in the mobile module with a Jetpack Compose UI that allows the user to select a folder via the Storage Access Framework (SAF), scan it for `.mp3` files, cache metadata (max 20 files), and display results in a scrollable list.

## Current State

| File | Description |
|---|---|
| `gradle/libs.versions.toml` | Version catalog with AGP 9.0.0, core-ktx, appcompat, material, activity, constraintlayout, media |
| `build.gradle.kts` (root) | Declares `android-application` plugin only |
| `shared/build.gradle` | Android library (`com.android.library`), Groovy DSL, depends on `androidx.media` |
| `shared/.../MyMusicService.kt` | `MediaBrowserServiceCompat` stub with empty callbacks |
| `mobile/build.gradle.kts` | Android app, depends on core-ktx, appcompat, material, activity, constraintlayout, `:shared` |
| `mobile/.../MainActivity.kt` | `AppCompatActivity` using XML layout, edge-to-edge, `setContentView(R.layout.activity_main)` |
| `mobile/.../res/layout/activity_main.xml` | ConstraintLayout with a single centered "Hello World!" TextView |
| `settings.gradle.kts` | Includes `:mobile`, `:automotive`, `:shared` |

## Files to Create / Modify (in order)

### Step 1. `gradle/libs.versions.toml` — Add Compose & DocumentFile entries

**What changes:**

Add new version entries:

```toml
[versions]
# ... existing entries ...
composeBom = "2025.05.01"          # Compose BOM — verify compatibility with AGP 9.0.0
activityCompose = "1.10.1"
lifecycleViewmodelCompose = "2.9.0"
documentfile = "1.0.1"
kotlin = "2.1.20"                  # Needed for compose-compiler plugin
```

Add new library entries:

```toml
[libraries]
# ... existing entries ...
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-documentfile = { group = "androidx.documentfile", name = "documentfile", version.ref = "documentfile" }
```

Add new plugin entry:

```toml
[plugins]
# ... existing entries ...
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

**Why:** Compose BOM manages consistent versions of all Compose libraries. The `compose-compiler` plugin is required by Kotlin 2.x for Compose support. DocumentFile is needed for SAF tree traversal.

**Risk:** Version numbers (especially Compose BOM) may need adjustment during Gradle sync for AGP 9.0.0 compatibility.

---

### Step 2. `build.gradle.kts` (root) — Declare compose-compiler plugin

**What changes:**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false   // NEW
}
```

**Why:** The Compose compiler plugin must be declared at root level so modules can apply it.

---

### Step 3. `shared/build.gradle` — Add documentfile dependency

**What changes:**

```groovy
dependencies {
    implementation libs.androidx.media
    implementation libs.androidx.documentfile   // NEW
}
```

**Why:** `DocumentFile.fromTreeUri()` is needed by `MediaCacheService` to traverse SAF directory trees.

---

### Step 4. `shared/.../shared/MediaFileInfo.kt` — NEW data class

**New file:** `shared/src/main/java/com/example/mymediaplayer/shared/MediaFileInfo.kt`

```kotlin
package com.example.mymediaplayer.shared

data class MediaFileInfo(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long
)
```

**Why:** Plain data class to hold scanned file metadata. No Android framework dependencies, easily testable. Lives in `shared` so both `mobile` and `automotive` modules can use it.

---

### Step 5. `shared/.../shared/MediaCacheService.kt` — NEW cache service

**New file:** `shared/src/main/java/com/example/mymediaplayer/shared/MediaCacheService.kt`

```kotlin
package com.example.mymediaplayer.shared

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class MediaCacheService {

    companion object {
        const val MAX_CACHE_SIZE = 20
    }

    private val _cachedFiles = mutableListOf<MediaFileInfo>()
    val cachedFiles: List<MediaFileInfo> get() = _cachedFiles.toList()

    fun scanDirectory(context: Context, treeUri: Uri) {
        clearCache()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        walkTree(root)
    }

    private fun walkTree(directory: DocumentFile) {
        if (_cachedFiles.size >= MAX_CACHE_SIZE) return
        for (file in directory.listFiles()) {
            if (_cachedFiles.size >= MAX_CACHE_SIZE) return
            if (file.isDirectory) {
                walkTree(file)
            } else if (file.isFile && file.name?.endsWith(".mp3", ignoreCase = true) == true) {
                _cachedFiles.add(
                    MediaFileInfo(
                        uriString = file.uri.toString(),
                        displayName = file.name ?: "Unknown",
                        sizeBytes = file.length()
                    )
                )
            }
        }
    }

    fun clearCache() {
        _cachedFiles.clear()
    }
}
```

**Key design details:**
- `scanDirectory()` takes a `Context` because `DocumentFile.fromTreeUri()` requires one
- `walkTree()` recursively descends into subdirectories
- Stops collecting after `MAX_CACHE_SIZE` (20) files to bound SAF traversal time
- `cachedFiles` returns an immutable snapshot via `toList()`
- Only collects files ending in `.mp3` (case-insensitive)

**Why:** Encapsulates scanning logic in the shared module so it can be reused by automotive.

---

### Step 6. `mobile/build.gradle.kts` — Enable Compose

**What changes:**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)       // NEW
}

android {
    // ... existing config ...

    buildFeatures {
        compose = true                          // NEW
    }
}

dependencies {
    // Compose BOM — manages versions of all Compose libraries
    implementation(platform(libs.compose.bom))  // NEW
    implementation(libs.compose.ui)             // NEW
    implementation(libs.compose.material3)      // NEW
    implementation(libs.compose.ui.tooling.preview) // NEW
    debugImplementation(libs.compose.ui.tooling)    // NEW

    implementation(libs.androidx.activity.compose)             // NEW (replaces plain activity)
    implementation(libs.androidx.lifecycle.viewmodel.compose)   // NEW

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // REMOVED: implementation(libs.androidx.activity)         — replaced by activity-compose
    // REMOVED: implementation(libs.androidx.constraintlayout) — no longer needed
    implementation(project(":shared"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

**Why:** Compose replaces the XML layout system. `activity-compose` provides `setContent {}`. `lifecycle-viewmodel-compose` provides `viewModel()` in composables. The BOM ensures all Compose artifact versions are aligned.

---

### Step 7. `mobile/.../MainViewModel.kt` — NEW ViewModel

**New file:** `mobile/src/main/java/com/example/mymediaplayer/MainViewModel.kt`

```kotlin
package com.example.mymediaplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymediaplayer.shared.MediaCacheService
import com.example.mymediaplayer.shared.MediaFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val isScanning: Boolean = false,
    val scannedFiles: List<MediaFileInfo> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaCacheService = MediaCacheService()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun onDirectorySelected(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = MainUiState(isScanning = true)
            mediaCacheService.scanDirectory(getApplication(), treeUri)
            _uiState.value = MainUiState(
                isScanning = false,
                scannedFiles = mediaCacheService.cachedFiles
            )
        }
    }
}
```

**Key design details:**
- `AndroidViewModel` provides `getApplication()` for the Context needed by `MediaCacheService`
- Scanning runs on `Dispatchers.IO` to avoid blocking the main thread
- `StateFlow<MainUiState>` is observed by the Compose UI via `collectAsState()`
- `MainUiState` is a simple data class with scanning state and file list

---

### Step 8. `mobile/.../MainScreen.kt` — NEW Compose UI

**New file:** `mobile/src/main/java/com/example/mymediaplayer/MainScreen.kt`

```kotlin
package com.example.mymediaplayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mymediaplayer.shared.MediaFileInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onSelectFolder: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("MyMediaPlayer") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Button(
                onClick = onSelectFolder,
                enabled = !uiState.isScanning
            ) {
                Text("Select Folder")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            if (uiState.scannedFiles.isNotEmpty()) {
                Text(
                    text = "${uiState.scannedFiles.size} file(s) found",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(uiState.scannedFiles) { file ->
                        FileCard(file)
                    }
                }
            }
        }
    }
}

@Composable
fun FileCard(file: MediaFileInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = formatFileSize(file.sizeBytes),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
```

**Key design details:**
- `MainScreen` is stateless — receives `uiState` and a callback, making it testable
- `Scaffold` + `TopAppBar` for standard Material 3 layout
- "Select Folder" button is disabled while scanning to prevent concurrent scans
- `LazyColumn` for efficient scrolling of file list
- `FileCard` shows filename and human-readable file size

---

### Step 9. `mobile/.../MainActivity.kt` — Rewrite for Compose

**Replace entire file:**

```kotlin
package com.example.mymediaplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val openDocumentTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.onDirectorySelected(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val uiState = viewModel.uiState.collectAsState()
                MainScreen(
                    uiState = uiState.value,
                    onSelectFolder = { openDocumentTree.launch(null) }
                )
            }
        }
    }
}
```

**Key design details:**
- Changes from `AppCompatActivity` to `ComponentActivity` (Compose base class)
- `by viewModels()` delegate creates/retains the ViewModel
- `registerForActivityResult(OpenDocumentTree())` launches the SAF directory picker
- `takePersistableUriPermission` ensures the URI grant survives app restarts
- `setContent { MaterialTheme { ... } }` replaces `setContentView(R.layout.activity_main)`
- `collectAsState()` converts the `StateFlow` into Compose state

---

### Step 10. Delete `mobile/src/main/res/layout/activity_main.xml`

**Why:** No longer referenced. The Compose UI replaces all XML layouts.

---

## Architecture Diagram

```
+------------------------------------------------------+
|  mobile module                                        |
|                                                       |
|  MainActivity (ComponentActivity)                     |
|    +-- registerForActivityResult(OpenDocumentTree)     |
|    +-- viewModel: MainViewModel                       |
|    +-- setContent { MainScreen(uiState, onSelect) }   |
|                                                       |
|  MainViewModel (AndroidViewModel)                     |
|    +-- mediaCacheService: MediaCacheService            |
|    +-- uiState: StateFlow<MainUiState>                |
|    +-- onDirectorySelected(uri) -> IO coroutine       |
|                                                       |
|  MainScreen (Composable)                              |
|    +-- Scaffold + TopAppBar                           |
|    +-- "Select Folder" Button                         |
|    +-- CircularProgressIndicator (while scanning)     |
|    +-- LazyColumn of FileCard items                   |
+-------------------------------------------------------+
|  shared module                                        |
|                                                       |
|  MediaFileInfo (data class)                           |
|    +-- uriString: String                              |
|    +-- displayName: String                            |
|    +-- sizeBytes: Long                                |
|                                                       |
|  MediaCacheService                                    |
|    +-- scanDirectory(context, treeUri)                |
|    +-- walkTree(directory) -- recursive, max 20       |
|    +-- cachedFiles: List<MediaFileInfo>                |
|    +-- clearCache()                                   |
+-------------------------------------------------------+
```

## Key Design Decisions

| Decision | Rationale |
|---|---|
| **SAF (Storage Access Framework)** | No manifest permissions needed; the system picker handles grants. `takePersistableUriPermission` persists access across restarts. |
| **Context via AndroidViewModel** | `DocumentFile.fromTreeUri()` requires a Context. `AndroidViewModel` provides `getApplication()` safely (no Activity leak). |
| **Scanning on Dispatchers.IO** | SAF tree traversal involves IPC calls and is slow. Running on IO keeps the UI responsive. |
| **StateFlow + collectAsState()** | Standard Compose pattern for observing ViewModel state. No LiveData dependency needed. |
| **Max 20 files** | Bounds traversal time for large directories. Sufficient for task demonstration. |
| **Shared module for scan logic** | Both `mobile` and `automotive` modules will eventually need file scanning. |
| **No manifest changes** | SAF handles permissions via the system picker intent. |

## Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| AGP 9.0.0 + Compose BOM compatibility | Build failure | Verify versions during Gradle sync; adjust BOM version if needed |
| Kotlin version mismatch | Compile error | The compose-compiler plugin version must match the Kotlin version used by AGP |
| DocumentFile SAF performance | Slow scan on large trees | 20-file cap bounds worst case; scanning runs on IO thread |
| Compose BOM version approximate | May not resolve | Use latest stable BOM from Maven; adjust if sync fails |

## Verification Steps

1. **Shared module compiles:** `./gradlew :shared:build`
2. **Mobile module compiles:** `./gradlew :mobile:build`
3. **Manual test:** Deploy to device/emulator, tap "Select Folder", navigate to a directory with `.mp3` files, verify the list appears with filenames and sizes
4. **Edge cases to test:**
   - Empty directory (no files shown, no crash)
   - Directory with > 20 `.mp3` files (only 20 shown)
   - Directory with no `.mp3` files (empty list)
   - Cancel the folder picker (nothing happens)

---

# Task 2: Play Media Files (Stop/Pause/Resume, Volume)

## Overview

Add playback capability using `android.media.MediaPlayer`. The mobile app gets tappable file cards and a bottom playback bar (track name + play/pause/stop). Android Auto works automatically through the existing `MediaBrowserServiceCompat`. Volume is handled by the system via hardware buttons.

## Current State (after Task 1)

| File | Description |
|---|---|
| `shared/.../MyMusicService.kt` | `MediaBrowserServiceCompat` with empty callbacks |
| `shared/.../MediaCacheService.kt` | Scans SAF directories for `.mp3` files, caches up to 20 |
| `shared/.../MediaFileInfo.kt` | Data class: `uriString`, `displayName`, `sizeBytes` |
| `mobile/.../MainActivity.kt` | `ComponentActivity` with Compose, SAF folder picker |
| `mobile/.../MainViewModel.kt` | `AndroidViewModel` with `StateFlow<MainUiState>` (scanning state + file list) |
| `mobile/.../MainScreen.kt` | Compose UI: Scaffold, "Select Folder" button, LazyColumn of FileCards |

## Files to Modify (in order)

### Step 1. `shared/.../MediaCacheService.kt` — Add `addFile()` method

**What changes:**

```kotlin
fun addFile(fileInfo: MediaFileInfo) {
    if (_cachedFiles.size < MAX_CACHE_SIZE) {
        _cachedFiles.add(fileInfo)
    }
}
```

**Why:** The mobile app scans files via the ViewModel, then pushes them to `MyMusicService` via a custom action. The service needs a way to populate its cache without re-scanning.

---

### Step 2. `shared/.../MyMusicService.kt` — Full playback implementation

**What changes (replace empty stub with working service):**

New instance fields:
- `private var mediaPlayer: MediaPlayer? = null`
- `private val mediaCacheService = MediaCacheService()`
- `private var currentFileInfo: MediaFileInfo? = null`
- `private var currentMediaId: String? = null`
- `private lateinit var audioManager: AudioManager`
- `private var audioFocusRequest: AudioFocusRequest? = null`
- `private val playbackStateBuilder = PlaybackStateCompat.Builder()`

**`onCreate()` additions:**
- Get `AudioManager` via `getSystemService()`
- Set initial `PlaybackStateCompat` with `STATE_NONE` and available actions (`PLAY`, `PLAY_FROM_MEDIA_ID`, `PAUSE`, `STOP`)
- Set `session.isActive = true`

**Callback implementations:**

- **`onPlayFromMediaId(mediaId, extras)`** — Primary entry point. Looks up file in cache by `uriString`, releases any existing player, requests audio focus, creates `MediaPlayer` with SAF content URI via `setDataSource(context, uri)`, calls `prepareAsync()`. On prepared: `start()`, update playback state + metadata. On completion: update state to `STOPPED`. On error: update state to `ERROR`.

- **`onPlay()`** — Resume paused playback: `mediaPlayer.start()`, request audio focus, update state to `PLAYING`.

- **`onPause()`** — Pause: `mediaPlayer.pause()`, update state to `PAUSED`.

- **`onStop()`** — Release player, abandon audio focus, update state to `STOPPED`.

- **`onCustomAction("SET_MEDIA_FILES", extras)`** — Receive scanned file list from mobile app as parallel `StringArrayList`/`LongArray` in `Bundle`. Populate `MediaCacheService` via `addFile()`. Call `notifyChildrenChanged("root")` so Android Auto refreshes.

**`onLoadChildren()` implementation:**
```kotlin
override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
    if (parentId == "root") {
        val items = mediaCacheService.cachedFiles.map { fileInfo ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(fileInfo.uriString)
                .setTitle(fileInfo.displayName)
                .build()
            MediaItem(description, MediaItem.FLAG_PLAYABLE)
        }.toMutableList()
        result.sendResult(items)
    } else {
        result.sendResult(ArrayList())
    }
}
```

**Helper methods:**

- **`requestAudioFocus(): Boolean`** — `AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)` with music attributes. Focus change listener: `AUDIOFOCUS_LOSS` -> stop, `AUDIOFOCUS_LOSS_TRANSIENT` -> pause.

- **`abandonAudioFocus()`** — Calls `audioManager.abandonAudioFocusRequest()`.

- **`releaseMediaPlayer()`** — Stop + release `MediaPlayer`, set to `null`.

- **`updatePlaybackState(state: Int)`** — Build `PlaybackStateCompat` with current position + appropriate actions based on state, set on session.

- **`updateMetadata(fileInfo: MediaFileInfo)`** — Build `MediaMetadataCompat` with title + mediaId, set on session.

**`onDestroy()`:**
- Release player, abandon focus, deactivate + release session, call `super.onDestroy()`.

**Why:** This is the core playback engine shared by both mobile and Android Auto. Using `uriString` as `mediaId` provides a direct mapping for `MediaPlayer.setDataSource()`.

---

### Step 3. `mobile/build.gradle.kts` — Add media dependency

**What changes:**

```kotlin
dependencies {
    // ... existing ...
    implementation(libs.androidx.media)  // NEW — for MediaBrowserCompat, MediaControllerCompat
}
```

**Why:** `shared/build.gradle` uses `implementation` (not `api`), so `MediaBrowserCompat`/`MediaControllerCompat` aren't available transitively in the mobile module.

---

### Step 4. `mobile/.../MainViewModel.kt` — Add playback state to UI state

**What changes:**

Expand `MainUiState`:
```kotlin
data class MainUiState(
    val isScanning: Boolean = false,
    val scannedFiles: List<MediaFileInfo> = emptyList(),
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentTrackName: String? = null,
    val currentMediaId: String? = null
)
```

Add method called by Activity's controller callback:
```kotlin
fun updatePlaybackState(state: Int, mediaId: String?, trackName: String?) {
    val current = _uiState.value
    _uiState.value = current.copy(
        isPlaying = (state == PlaybackStateCompat.STATE_PLAYING),
        isPaused = (state == PlaybackStateCompat.STATE_PAUSED),
        currentTrackName = if (state == PlaybackStateCompat.STATE_STOPPED) null else trackName,
        currentMediaId = if (state == PlaybackStateCompat.STATE_STOPPED) null else mediaId
    )
}
```

**Why:** The ViewModel carries playback state so Compose can observe it. The Activity receives controller callbacks and pushes state updates here.

---

### Step 5. `mobile/.../MainScreen.kt` — Bottom bar + tappable cards

**What changes:**

Update `MainScreen` signature:
```kotlin
fun MainScreen(
    uiState: MainUiState,
    onSelectFolder: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
)
```

Add `bottomBar` to `Scaffold`:
```kotlin
bottomBar = {
    if (uiState.currentTrackName != null) {
        PlaybackBar(
            trackName = uiState.currentTrackName,
            isPlaying = uiState.isPlaying,
            onPlayPause = onPlayPause,
            onStop = onStop
        )
    }
}
```

New `PlaybackBar` composable:
```kotlin
@Composable
fun PlaybackBar(
    trackName: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = trackName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onPlayPause) {
                Text(if (isPlaying) "Pause" else "Play")
            }
            TextButton(onClick = onStop) {
                Text("Stop")
            }
        }
    }
}
```

Update `FileCard` to be tappable with highlight:
```kotlin
@Composable
fun FileCard(file: MediaFileInfo, isCurrentTrack: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = if (isCurrentTrack) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) { /* ... existing content ... */ }
}
```

Update LazyColumn:
```kotlin
items(uiState.scannedFiles) { file ->
    FileCard(
        file = file,
        isCurrentTrack = file.uriString == uiState.currentMediaId,
        onClick = { onFileClick(file) }
    )
}
```

**Why:** Uses `TextButton` instead of `IconButton` to avoid the `material-icons-extended` dependency. Bottom bar appears only when a track is active.

---

### Step 6. `mobile/.../MainActivity.kt` — Wire everything together

**What changes:**

- **Volume**: `volumeControlStream = AudioManager.STREAM_MUSIC` in `onCreate()` so hardware buttons control music.

- **MediaBrowserCompat connection**: Connect to `MyMusicService` in `onCreate()`, disconnect in `onDestroy()`.

- **Connection callback**: On connected, create `MediaControllerCompat`, register controller callback.

- **Controller callback**: `onPlaybackStateChanged` + `onMetadataChanged` -> call `viewModel.updatePlaybackState(...)`.

- **Send files to service**: `lifecycleScope.launch` collector on `viewModel.uiState` — when `scannedFiles` is non-empty and controller is connected, send via `transportControls.sendCustomAction("SET_MEDIA_FILES", bundle)` with parallel arrays (uris, names, sizes).

- **Compose callbacks**:
  - `onFileClick` -> `sendFilesToService()` + `transportControls.playFromMediaId(uri, null)`
  - `onPlayPause` -> toggle pause/play based on current state
  - `onStop` -> `transportControls.stop()`

- **Cleanup in `onDestroy()`**: Unregister controller callback + disconnect browser.

**Why:** `MediaBrowserCompat` is the standard way to connect an Activity to a `MediaBrowserServiceCompat`. The controller callback bridges service state changes to the ViewModel/Compose UI.

---

## Architecture Diagram (after Task 2)

```
+------------------------------------------------------------------+
|  mobile module                                                    |
|                                                                   |
|  MainActivity (ComponentActivity)                                 |
|    +-- volumeControlStream = STREAM_MUSIC                         |
|    +-- MediaBrowserCompat -> MyMusicService                       |
|    +-- MediaControllerCompat -> transport controls                |
|    +-- Controller callback -> viewModel.updatePlaybackState()     |
|    +-- sendFilesToService() via sendCustomAction                  |
|                                                                   |
|  MainViewModel (AndroidViewModel)                                 |
|    +-- mediaCacheService: MediaCacheService                       |
|    +-- uiState: StateFlow<MainUiState>                            |
|    +-- onDirectorySelected(uri)                                   |
|    +-- updatePlaybackState(state, mediaId, trackName)             |
|                                                                   |
|  MainScreen (Composable)                                          |
|    +-- Scaffold + TopAppBar + bottomBar: PlaybackBar              |
|    +-- "Select Folder" Button                                     |
|    +-- LazyColumn of tappable FileCards (highlight current)       |
|    +-- PlaybackBar: track name, Play/Pause, Stop                  |
+------------------------------------------------------------------+
|  shared module                                                    |
|                                                                   |
|  MyMusicService (MediaBrowserServiceCompat)                       |
|    +-- MediaPlayer for playback                                   |
|    +-- AudioManager for audio focus                               |
|    +-- MediaSessionCompat with working callbacks                  |
|    +-- onLoadChildren() -> browsable MediaItems                   |
|    +-- onPlayFromMediaId() -> play specific track                 |
|    +-- Updates PlaybackStateCompat + MediaMetadataCompat          |
|                                                                   |
|  MediaCacheService                                                |
|    +-- scanDirectory(), addFile(), clearCache()                   |
|    +-- cachedFiles: List<MediaFileInfo>                            |
|                                                                   |
|  MediaFileInfo (data class) — unchanged                           |
+------------------------------------------------------------------+
```

## Key Design Decisions

| Decision | Rationale |
|---|---|
| **`uriString` as `mediaId`** | Direct mapping — `onPlayFromMediaId` can `Uri.parse()` it for `MediaPlayer.setDataSource()` |
| **Files pushed to service via `sendCustomAction`** | Avoids duplicate scanning; works with MediaBrowser/Controller pattern |
| **Audio focus with `AUDIOFOCUS_GAIN`** | Standard for music apps; pauses on transient loss, stops on permanent loss |
| **`TextButton` for playback controls** | Avoids `material-icons-extended` dependency (~2.4MB) |
| **No foreground service notification** | Acceptable for Task 2 scope; can be added later |
| **`MediaPlayer` (not ExoPlayer)** | Built-in, simpler, no extra dependencies; sufficient for basic `.mp3` playback |

## Verification Steps

1. **Build**: `./gradlew :shared:build && ./gradlew :mobile:build`
2. **Play**: Deploy, scan folder, tap file — bottom bar appears, music plays
3. **Pause**: Tap Pause — pauses, button shows "Play"
4. **Resume**: Tap Play — resumes
5. **Stop**: Tap Stop — music stops, bottom bar disappears
6. **Switch tracks**: Tap different file — switches, bar updates
7. **Volume**: Hardware buttons control music volume
8. **Highlight**: Currently playing file highlighted in list
9. **Android Auto** (if available): browse shows tracks, tap plays, car controls work

---

# Task 3: Create Playlists (Random 3 Files, M3U Format)

## Overview

Add a "Create Playlist" button that picks 3 random files from the scanned list and writes an M3U playlist file to the same SAF directory. Uses timestamped filenames for uniqueness. If fewer than 3 files are scanned, uses all available files. Button is disabled when no files have been scanned.

## Current State (after Task 2)

| File | Description |
|---|---|
| `shared/.../MyMusicService.kt` | Fully implemented `MediaBrowserServiceCompat` with `MediaPlayer` playback |
| `shared/.../MediaCacheService.kt` | Scans SAF directories, `addFile()`, caches up to 20 |
| `shared/.../MediaFileInfo.kt` | Data class: `uriString`, `displayName`, `sizeBytes` |
| `mobile/.../MainActivity.kt` | `ComponentActivity` with Compose, SAF picker, `MediaBrowserCompat` connection |
| `mobile/.../MainViewModel.kt` | `StateFlow<MainUiState>` with scanning + playback state |
| `mobile/.../MainScreen.kt` | Scaffold with TopAppBar, "Select Folder" button, LazyColumn, PlaybackBar |

## Files to Create / Modify (in order)

### Step 1. `shared/.../PlaylistService.kt` — NEW playlist generation + writing

**New file:** `shared/src/main/java/com/example/mymediaplayer/shared/PlaylistService.kt`

Two responsibilities:
- **Generate M3U content** from a list of `MediaFileInfo`:
  ```
  #EXTM3U
  #EXTINF:-1,song1.mp3
  content://...uri1...
  #EXTINF:-1,song2.mp3
  content://...uri2...
  ```
- **Write to SAF directory** using `DocumentFile.fromTreeUri(context, treeUri).createFile("audio/x-mpegurl", filename)` + `contentResolver.openOutputStream()`

```kotlin
class PlaylistService {
    fun generateM3uContent(files: List<MediaFileInfo>): String
    fun writePlaylist(context: Context, treeUri: Uri, files: List<MediaFileInfo>): String?
    // returns the created filename on success, null on failure
}
```

`writePlaylist` generates a timestamped filename (`playlist_yyyyMMdd_HHmmss.m3u`), calls `generateM3uContent`, writes via SAF, returns filename.

**Why:** Lives in `shared` so automotive module could reuse it later. Keeps ViewModel thin.

---

### Step 2. `mobile/.../MainViewModel.kt` — Add playlist creation

**What changes:**

Extend `MainUiState`:
```kotlin
data class MainUiState(
    // ... existing fields ...
    val playlistMessage: String? = null   // transient success/error message
)
```

Add fields + method:
```kotlin
private val playlistService = PlaylistService()
private var treeUri: Uri? = null

fun setTreeUri(uri: Uri) { treeUri = uri }

fun createRandomPlaylist() {
    val uri = treeUri ?: return
    val files = _uiState.value.scannedFiles
    if (files.isEmpty()) return
    val selected = files.shuffled().take(3)
    viewModelScope.launch(Dispatchers.IO) {
        val result = playlistService.writePlaylist(getApplication(), uri, selected)
        _uiState.value = _uiState.value.copy(
            playlistMessage = if (result != null) "Created $result" else "Failed to create playlist"
        )
    }
}

fun clearPlaylistMessage() {
    _uiState.value = _uiState.value.copy(playlistMessage = null)
}
```

**Why:** `shuffled().take(3)` naturally handles < 3 files. IO dispatcher avoids blocking the UI during SAF write. `playlistMessage` is a transient field shown via Snackbar then cleared.

---

### Step 3. `mobile/.../MainScreen.kt` — Add "Create Playlist" button + Snackbar

**What changes:**

- Add `onCreatePlaylist: () -> Unit` and `onPlaylistMessageDismissed: () -> Unit` callback parameters to `MainScreen`
- Add a "Create Playlist" `Button` next to "Select Folder", enabled when `scannedFiles.isNotEmpty()`
- Add `SnackbarHost` to `Scaffold` and a `LaunchedEffect` that shows a Snackbar when `playlistMessage` changes

```kotlin
// In the Column, after "Select Folder" button:
Button(
    onClick = onCreatePlaylist,
    enabled = uiState.scannedFiles.isNotEmpty()
) {
    Text("Create Playlist")
}
```

**Why:** Snackbar is the standard Material 3 pattern for transient feedback messages.

---

### Step 4. `mobile/.../MainActivity.kt` — Wire treeUri + callback

**What changes:**

- Store `treeUri` when SAF picker returns: call `viewModel.setTreeUri(it)` in the `openDocumentTree` result handler
- Pass `onCreatePlaylist = { viewModel.createRandomPlaylist() }` to `MainScreen`
- Pass `onPlaylistMessageDismissed = { viewModel.clearPlaylistMessage() }` to `MainScreen`

**Why:** The `treeUri` from the SAF picker is needed by `PlaylistService` to write the M3U file to the same directory.

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| **Save to SAF directory** | Playlist alongside MP3s; uses existing SAF grant; no extra permissions |
| **Timestamped filename** | Unique, no user input needed, avoids collisions |
| **`PlaylistService` in shared** | Reusable by automotive module; keeps ViewModel thin |
| **`playlistMessage` as transient state** | Simple Snackbar pattern; cleared after display |
| **`take(3)` with `shuffled()`** | If < 3 files, `take(3)` returns all available — no crash |
| **No build/manifest changes** | SAF write uses existing tree URI grant; `DocumentFile` already in shared deps |

## Verification Steps

1. **Build**: `./gradlew :shared:build && ./gradlew :mobile:build`
2. **Create**: Deploy, scan folder, tap "Create Playlist" — Snackbar shows "Created playlist_XXXXXXXX_XXXXXX.m3u"
3. **File check**: Verify .m3u file exists in scanned directory with correct M3U content (`#EXTM3U`, 3 entries)
4. **Repeat**: Tap again — new file with different timestamp, potentially different random files
5. **Few files**: With 1-2 files scanned, playlist created with 1-2 entries
6. **No files**: With 0 files scanned, button is disabled
