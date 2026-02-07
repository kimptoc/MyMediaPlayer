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

---

# Task 4: Play Playlists (Play, Pause, Stop, Next Track)

## Overview

Enable playlist playback on both Mobile and Android Auto. During directory scanning, discover `.m3u` files alongside `.mp3` files. Display discovered playlists in the mobile Compose UI. Tapping a playlist starts sequential playback through its tracks with auto-advance. Add a "Next" button to skip tracks. Restructure the MediaBrowser browse tree into "Songs" and "Playlists" categories for Android Auto. Pause, resume, and stop continue to work as in Task 2.

## Current State (after Task 3)

| File | Description |
|---|---|
| `shared/.../MyMusicService.kt` | `MediaBrowserServiceCompat` with single-track `MediaPlayer` playback, flat browse tree under `root` |
| `shared/.../MediaCacheService.kt` | Scans SAF directories for `.mp3` files only, caches up to 20, `addFile()` |
| `shared/.../MediaFileInfo.kt` | Data class: `uriString`, `displayName`, `sizeBytes` |
| `shared/.../PlaylistService.kt` | `generateM3uContent()` + `writePlaylist()` — write-only, no reading |
| `mobile/.../MainActivity.kt` | `ComponentActivity` with Compose, SAF picker, `MediaBrowserCompat` connection, sends files to service, playback + playlist creation callbacks |
| `mobile/.../MainViewModel.kt` | `StateFlow<MainUiState>` with scanning + playback + playlist creation state |
| `mobile/.../MainScreen.kt` | Scaffold with TopAppBar, "Select Folder" + "Create Playlist" buttons, LazyColumn of FileCards, PlaybackBar (Play/Pause + Stop), Snackbar |

## Files to Create / Modify (in order)

### Step 1. `shared/.../PlaylistInfo.kt` — NEW data class

**New file:** `shared/src/main/java/com/example/mymediaplayer/shared/PlaylistInfo.kt`

```kotlin
package com.example.mymediaplayer.shared

data class PlaylistInfo(
    val uriString: String,      // Content URI of the .m3u file
    val displayName: String     // Filename (e.g., "playlist_20250601_120000.m3u")
)
```

**Why:** Separates playlist metadata from track metadata (`MediaFileInfo`). Used by the mobile UI to display discovered playlists and by the service for the browse tree and playback routing.

---

### Step 2. `shared/.../PlaylistService.kt` — Add M3U reading

**What changes:**

Add method to parse an M3U file from a SAF content URI:

```kotlin
fun readPlaylist(context: Context, playlistUri: Uri): List<MediaFileInfo> {
    val tracks = mutableListOf<MediaFileInfo>()
    val content = context.contentResolver.openInputStream(playlistUri)
        ?.bufferedReader()
        ?.readText() ?: return tracks

    val lines = content.lines()
    var pendingName: String? = null

    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() || trimmed == "#EXTM3U" -> continue
            trimmed.startsWith("#EXTINF:") -> {
                // Format: #EXTINF:-1,displayname
                pendingName = trimmed.substringAfter(",", "").ifEmpty { null }
            }
            !trimmed.startsWith("#") -> {
                // URI line
                tracks.add(MediaFileInfo(
                    uriString = trimmed,
                    displayName = pendingName ?: "Unknown",
                    sizeBytes = 0
                ))
                pendingName = null
            }
        }
    }
    return tracks
}
```

**Key design details:**
- Parses standard M3U format matching what `generateM3uContent()` writes
- Extracts display names from `#EXTINF` lines, URIs from non-comment lines
- `sizeBytes = 0` — M3U doesn't store file size; acceptable since playlist tracks display name only
- Returns empty list on read failure (graceful degradation)

**Why:** The service calls this when a playlist is selected for playback (from both Mobile tap and Android Auto browse).

---

### Step 3. `shared/.../MediaCacheService.kt` — Discover .m3u files + granular clear methods

**What changes:**

Add a second list for discovered playlists:

```kotlin
private val _discoveredPlaylists = mutableListOf<PlaylistInfo>()
val discoveredPlaylists: List<PlaylistInfo> get() = _discoveredPlaylists.toList()
```

Add granular clear methods (needed because `SET_MEDIA_FILES` and `SET_PLAYLISTS` custom actions arrive independently):

```kotlin
fun clearFiles() {
    _cachedFiles.clear()
}

fun clearPlaylists() {
    _discoveredPlaylists.clear()
}

fun clearCache() {
    clearFiles()
    clearPlaylists()
}
```

Add `addPlaylist()` method for service-side population:

```kotlin
fun addPlaylist(playlistInfo: PlaylistInfo) {
    _discoveredPlaylists.add(playlistInfo)
}
```

Update `walkTree()` to also collect `.m3u` files:

```kotlin
private fun walkTree(directory: DocumentFile) {
    if (_cachedFiles.size >= MAX_CACHE_SIZE) return
    for (file in directory.listFiles()) {
        if (file.isDirectory) {
            walkTree(file)
        } else if (file.isFile) {
            val name = file.name ?: continue
            when {
                name.endsWith(".mp3", ignoreCase = true) && _cachedFiles.size < MAX_CACHE_SIZE -> {
                    _cachedFiles.add(
                        MediaFileInfo(
                            uriString = file.uri.toString(),
                            displayName = name,
                            sizeBytes = file.length()
                        )
                    )
                }
                name.endsWith(".m3u", ignoreCase = true) -> {
                    _discoveredPlaylists.add(
                        PlaylistInfo(
                            uriString = file.uri.toString(),
                            displayName = name
                        )
                    )
                }
            }
        }
    }
}
```

**Why:** The scanner already traverses the SAF tree; collecting `.m3u` files costs negligible overhead. No cap on playlist count (lightweight metadata). Granular clear methods prevent `SET_MEDIA_FILES` from wiping playlist data and vice versa.

---

### Step 4. `shared/.../MyMusicService.kt` — Playlist queue, skip support, browse tree categories

This is the largest change, broken into sub-steps.

#### 4a. New constants and fields

```kotlin
companion object {
    private const val ROOT_ID = "root"
    private const val SONGS_ID = "songs"
    private const val PLAYLISTS_ID = "playlists"
    private const val PLAYLIST_PREFIX = "playlist:"
    private const val ACTION_SET_MEDIA_FILES = "SET_MEDIA_FILES"
    private const val ACTION_SET_PLAYLISTS = "SET_PLAYLISTS"
    private const val EXTRA_URIS = "uris"
    private const val EXTRA_NAMES = "names"
    private const val EXTRA_SIZES = "sizes"
    private const val EXTRA_PLAYLIST_URIS = "playlist_uris"
    private const val EXTRA_PLAYLIST_NAMES = "playlist_names"
}

// Queue state
private var playlistQueue: List<MediaFileInfo> = emptyList()
private var currentQueueIndex: Int = -1
private var currentPlaylistName: String? = null
private val playlistService = PlaylistService()
```

#### 4b. Extract `playTrack(fileInfo: MediaFileInfo)` method

Refactor the MediaPlayer setup code from the current `onPlayFromMediaId` into a reusable private method. The current body of `onPlayFromMediaId` (from `releaseMediaPlayer()` through the try/catch) moves here with one key change: the `setOnCompletionListener` now calls `onTrackCompleted()` instead of directly stopping.

```kotlin
private fun playTrack(fileInfo: MediaFileInfo) {
    releaseMediaPlayer()
    if (!requestAudioFocus()) {
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        return
    }
    currentFileInfo = fileInfo
    currentMediaId = fileInfo.uriString
    try {
        val uri = Uri.parse(fileInfo.uriString)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(this@MyMusicService, uri)
            setOnPreparedListener {
                start()
                updateMetadata(fileInfo)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }
            setOnCompletionListener { onTrackCompleted() }  // KEY CHANGE
            setOnErrorListener { _, _, _ ->
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                releaseMediaPlayer()
                abandonAudioFocus()
                true
            }
            prepareAsync()
        }
    } catch (e: Exception) {
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        releaseMediaPlayer()
        abandonAudioFocus()
    }
}
```

**Why:** Both single-track play and playlist-track play need the same MediaPlayer setup. Extracting avoids duplication.

#### 4c. New `onTrackCompleted()` — auto-advance logic

```kotlin
private fun onTrackCompleted() {
    if (playlistQueue.isNotEmpty() && currentQueueIndex < playlistQueue.size - 1) {
        // Auto-advance to next track in playlist
        currentQueueIndex++
        updateSessionQueue()
        playTrack(playlistQueue[currentQueueIndex])
    } else {
        // Single track finished, or end of playlist — stop
        playlistQueue = emptyList()
        currentQueueIndex = -1
        currentPlaylistName = null
        session.setQueue(null)
        session.setQueueTitle(null)
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        releaseMediaPlayer()
        abandonAudioFocus()
    }
}
```

**Why:** Central decision point: if there's a next track in the queue, advance; otherwise stop. Single-track playback has an empty queue, so it falls through to stop.

#### 4d. Modify `onPlayFromMediaId` — route playlists vs. songs

```kotlin
override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
    val resolvedMediaId = mediaId ?: return

    if (resolvedMediaId.startsWith(PLAYLIST_PREFIX)) {
        // --- Playlist playback ---
        val playlistUriStr = resolvedMediaId.removePrefix(PLAYLIST_PREFIX)
        val playlistUri = Uri.parse(playlistUriStr)
        val tracks = playlistService.readPlaylist(this@MyMusicService, playlistUri)
        if (tracks.isEmpty()) {
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            return
        }

        currentPlaylistName = mediaCacheService.discoveredPlaylists
            .firstOrNull { it.uriString == playlistUriStr }
            ?.displayName?.removeSuffix(".m3u") ?: "Playlist"

        playlistQueue = tracks
        currentQueueIndex = 0
        updateSessionQueue()
        playTrack(tracks[0])
    } else {
        // --- Single track playback (existing) --- clear any active playlist
        playlistQueue = emptyList()
        currentQueueIndex = -1
        currentPlaylistName = null
        session.setQueue(null)
        session.setQueueTitle(null)

        val fileInfo = mediaCacheService.cachedFiles.firstOrNull {
            it.uriString == resolvedMediaId
        } ?: run {
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            return
        }
        playTrack(fileInfo)
    }
}
```

**Key design detail:** Playlist mediaIds are prefixed with `playlist:` to distinguish from song URIs. Android Auto sends `onPlayFromMediaId` when the user taps a playable item in the browse tree, so both Mobile and Auto use the same code path.

#### 4e. Add `onSkipToNext()` and `onSkipToPrevious()` callbacks

```kotlin
override fun onSkipToNext() {
    if (playlistQueue.isEmpty() || currentQueueIndex >= playlistQueue.size - 1) return
    currentQueueIndex++
    updateSessionQueue()
    playTrack(playlistQueue[currentQueueIndex])
}

override fun onSkipToPrevious() {
    if (playlistQueue.isEmpty() || currentQueueIndex <= 0) return
    currentQueueIndex--
    updateSessionQueue()
    playTrack(playlistQueue[currentQueueIndex])
}
```

**Why:** `onSkipToNext` is the core Task 4 requirement. `onSkipToPrevious` is included for completeness — Android Auto shows both buttons when the actions are advertised, and the implementation is trivial.

#### 4f. New `updateSessionQueue()` helper

```kotlin
private fun updateSessionQueue() {
    if (playlistQueue.isEmpty()) {
        session.setQueue(null)
        session.setQueueTitle(null)
        return
    }
    val queueItems = playlistQueue.mapIndexed { index, track ->
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(track.uriString)
            .setTitle(track.displayName)
            .build()
        MediaSessionCompat.QueueItem(desc, index.toLong())
    }
    session.setQueue(queueItems)
    session.setQueueTitle(currentPlaylistName)
}
```

**Why:** Sets the MediaSession queue so Android Auto displays the track list and allows queue navigation. The active item is communicated via `playbackStateBuilder.setActiveQueueItemId()` in `updatePlaybackState()`.

#### 4g. Update `updatePlaybackState()` — skip actions + active queue item

```kotlin
private fun updatePlaybackState(state: Int) {
    val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
    val speed = if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f

    val hasQueue = playlistQueue.isNotEmpty()
    val canSkipNext = hasQueue && currentQueueIndex < playlistQueue.size - 1
    val canSkipPrev = hasQueue && currentQueueIndex > 0

    val actions = when (state) {
        PlaybackStateCompat.STATE_PLAYING -> {
            var a = PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP
            if (canSkipNext) a = a or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            if (canSkipPrev) a = a or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            a
        }
        PlaybackStateCompat.STATE_PAUSED -> {
            var a = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP
            if (canSkipNext) a = a or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            if (canSkipPrev) a = a or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            a
        }
        else -> {
            PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
        }
    }

    playbackStateBuilder
        .setActions(actions)
        .setState(state, position, speed, SystemClock.elapsedRealtime())

    if (hasQueue) {
        playbackStateBuilder.setActiveQueueItemId(currentQueueIndex.toLong())
    } else {
        playbackStateBuilder.setActiveQueueItemId(
            MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
        )
    }
    session.setPlaybackState(playbackStateBuilder.build())
}
```

**Why:** Advertises `ACTION_SKIP_TO_NEXT` / `ACTION_SKIP_TO_PREVIOUS` only when a playlist queue is active and there are tracks to skip to. Android Auto shows/hides skip buttons based on these actions. The `activeQueueItemId` highlights the current track in the queue display.

#### 4h. Restructure `onLoadChildren()` — "Songs" + "Playlists" categories

```kotlin
override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
    when (parentId) {
        ROOT_ID -> {
            val items = mutableListOf<MediaItem>()

            val songsDesc = MediaDescriptionCompat.Builder()
                .setMediaId(SONGS_ID)
                .setTitle("Songs")
                .build()
            items.add(MediaItem(songsDesc, MediaItem.FLAG_BROWSABLE))

            if (mediaCacheService.discoveredPlaylists.isNotEmpty()) {
                val playlistsDesc = MediaDescriptionCompat.Builder()
                    .setMediaId(PLAYLISTS_ID)
                    .setTitle("Playlists")
                    .build()
                items.add(MediaItem(playlistsDesc, MediaItem.FLAG_BROWSABLE))
            }

            result.sendResult(items)
        }
        SONGS_ID -> {
            val items = mediaCacheService.cachedFiles.map { fileInfo ->
                val description = MediaDescriptionCompat.Builder()
                    .setMediaId(fileInfo.uriString)
                    .setTitle(fileInfo.displayName)
                    .build()
                MediaItem(description, MediaItem.FLAG_PLAYABLE)
            }.toMutableList()
            result.sendResult(items)
        }
        PLAYLISTS_ID -> {
            val items = mediaCacheService.discoveredPlaylists.map { playlist ->
                val description = MediaDescriptionCompat.Builder()
                    .setMediaId(PLAYLIST_PREFIX + playlist.uriString)
                    .setTitle(playlist.displayName.removeSuffix(".m3u"))
                    .build()
                MediaItem(description, MediaItem.FLAG_PLAYABLE)
            }.toMutableList()
            result.sendResult(items)
        }
        else -> result.sendResult(ArrayList())
    }
}
```

**Why:** Android Auto users need to browse into "Songs" or "Playlists". Each playlist is `FLAG_PLAYABLE` — tapping it triggers `onPlayFromMediaId("playlist:uri")`. The "Playlists" category only appears when playlists have been discovered.

#### 4i. Add `SET_PLAYLISTS` custom action handler

Expand existing `onCustomAction`:

```kotlin
override fun onCustomAction(action: String?, extras: Bundle?) {
    when (action) {
        ACTION_SET_MEDIA_FILES -> {
            if (extras == null) return
            val uris = extras.getStringArrayList(EXTRA_URIS) ?: return
            val names = extras.getStringArrayList(EXTRA_NAMES) ?: return
            val sizes = extras.getLongArray(EXTRA_SIZES) ?: return

            mediaCacheService.clearFiles()   // WAS: clearCache()
            val count = minOf(uris.size, names.size, sizes.size)
            for (i in 0 until count) {
                mediaCacheService.addFile(
                    MediaFileInfo(
                        uriString = uris[i],
                        displayName = names[i],
                        sizeBytes = sizes[i]
                    )
                )
            }
            notifyChildrenChanged(ROOT_ID)
            notifyChildrenChanged(SONGS_ID)
        }
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
    }
}
```

**Important fix:** The existing `SET_MEDIA_FILES` handler changes from `mediaCacheService.clearCache()` to `mediaCacheService.clearFiles()` so it doesn't wipe playlist data. The two custom actions arrive independently.

#### 4j. Update `handleStop()` — clear playlist queue

```kotlin
private fun handleStop() {
    releaseMediaPlayer()
    abandonAudioFocus()
    playlistQueue = emptyList()
    currentQueueIndex = -1
    currentPlaylistName = null
    session.setQueue(null)
    session.setQueueTitle(null)
    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
}
```

**Why:** Stopping clears the playlist queue so the user returns to a clean state.

---

### Step 5. `mobile/.../MainViewModel.kt` — Playlist UI state

**What changes:**

Expand `MainUiState`:

```kotlin
data class MainUiState(
    val isScanning: Boolean = false,
    val scannedFiles: List<MediaFileInfo> = emptyList(),
    val discoveredPlaylists: List<PlaylistInfo> = emptyList(),
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentTrackName: String? = null,
    val currentMediaId: String? = null,
    val playlistMessage: String? = null,
    val isPlayingPlaylist: Boolean = false,
    val queuePosition: String? = null
)
```

Update `onDirectorySelected()` to include playlists:

```kotlin
fun onDirectorySelected(treeUri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.value = _uiState.value.copy(
            isScanning = true,
            scannedFiles = emptyList(),
            discoveredPlaylists = emptyList()
        )
        mediaCacheService.scanDirectory(getApplication(), treeUri)
        _uiState.value = _uiState.value.copy(
            isScanning = false,
            scannedFiles = mediaCacheService.cachedFiles,
            discoveredPlaylists = mediaCacheService.discoveredPlaylists
        )
    }
}
```

Add method to receive queue state from controller callback:

```kotlin
fun updateQueueState(queueTitle: String?, queueSize: Int, activeIndex: Int) {
    _uiState.value = _uiState.value.copy(
        isPlayingPlaylist = queueTitle != null,
        queuePosition = if (queueTitle != null && queueSize > 0) {
            "${activeIndex + 1}/$queueSize"
        } else null
    )
}
```

**Why:** `discoveredPlaylists` feeds the playlist section in the Compose UI. `isPlayingPlaylist` + `queuePosition` drive the "Next" button visibility and track position display in the PlaybackBar.

---

### Step 6. `mobile/.../MainScreen.kt` — Playlist section + Next button

**What changes:**

Update `MainScreen` signature — add `onNext` and `onPlaylistClick` callbacks:

```kotlin
fun MainScreen(
    uiState: MainUiState,
    onSelectFolder: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistMessageDismissed: () -> Unit,
    onPlaylistClick: (PlaylistInfo) -> Unit
)
```

Add playlists section in the content Column (between file count and LazyColumn):

```kotlin
if (uiState.discoveredPlaylists.isNotEmpty()) {
    Text(
        text = "${uiState.discoveredPlaylists.size} playlist(s) found",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(4.dp))
    uiState.discoveredPlaylists.forEach { playlist ->
        PlaylistCard(
            playlist = playlist,
            onClick = { onPlaylistClick(playlist) }
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}
```

New `PlaylistCard` composable (visually distinct from `FileCard` via `secondaryContainer` color):

```kotlin
@Composable
fun PlaylistCard(playlist: PlaylistInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playlist.displayName.removeSuffix(".m3u"),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Playlist",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
```

Update `PlaybackBar` — add `onNext` callback, queue position, and "Next" button:

```kotlin
@Composable
fun PlaybackBar(
    trackName: String,
    isPlaying: Boolean,
    isPlayingPlaylist: Boolean,
    queuePosition: String?,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (queuePosition != null) {
                    Text(
                        text = "Track $queuePosition",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            TextButton(onClick = onPlayPause) {
                Text(if (isPlaying) "Pause" else "Play")
            }
            if (isPlayingPlaylist) {
                TextButton(onClick = onNext) {
                    Text("Next")
                }
            }
            TextButton(onClick = onStop) {
                Text("Stop")
            }
        }
    }
}
```

Update the `bottomBar` lambda in `Scaffold` to pass the new parameters.

**Why:** Playlists are visually separated from songs (different card color + "Playlist" label). The "Next" button only appears during playlist playback. Track position (e.g., "Track 2/5") provides context.

---

### Step 7. `mobile/.../MainActivity.kt` — Wire playlist callbacks + queue state

**What changes:**

Add a field to track last-sent playlist URIs:

```kotlin
private var lastSentPlaylistUris: List<String>? = null
```

Update controller callback — add queue change handlers:

```kotlin
private val controllerCallback = object : MediaControllerCompat.Callback() {
    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
        lastPlaybackState = state
        pushPlaybackState()
        pushQueueState()
    }
    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
        lastMetadata = metadata
        pushPlaybackState()
    }
    override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
        pushQueueState()
    }
    override fun onQueueTitleChanged(title: CharSequence?) {
        pushQueueState()
    }
}
```

New `pushQueueState()` method:

```kotlin
private fun pushQueueState() {
    val controller = mediaController ?: return
    val queueTitle = controller.queueTitle?.toString()
    val queueSize = controller.queue?.size ?: 0
    val activeIndex = lastPlaybackState?.activeQueueItemId?.toInt() ?: -1
    viewModel.updateQueueState(queueTitle, queueSize, activeIndex)
}
```

Extend the `lifecycleScope.launch` collector to also send playlists:

```kotlin
lifecycleScope.launch {
    viewModel.uiState.collect { state ->
        if (state.scannedFiles.isNotEmpty()) {
            sendFilesToServiceIfNeeded(state.scannedFiles)
        }
        if (state.discoveredPlaylists.isNotEmpty()) {
            sendPlaylistsToServiceIfNeeded(state.discoveredPlaylists)
        }
    }
}
```

New `sendPlaylistsToServiceIfNeeded()`:

```kotlin
private fun sendPlaylistsToServiceIfNeeded(playlists: List<PlaylistInfo>) {
    val controller = mediaController ?: return
    val uris = playlists.map { it.uriString }
    if (uris == lastSentPlaylistUris) return
    val names = playlists.map { it.displayName }
    val bundle = Bundle().apply {
        putStringArrayList("playlist_uris", ArrayList(uris))
        putStringArrayList("playlist_names", ArrayList(names))
    }
    controller.transportControls.sendCustomAction("SET_PLAYLISTS", bundle)
    lastSentPlaylistUris = uris
}
```

Pass new callbacks to `MainScreen`:

```kotlin
MainScreen(
    // ... existing callbacks ...
    onNext = { mediaController?.transportControls?.skipToNext() },
    onPlaylistClick = { playlist ->
        sendFilesToServiceIfNeeded(uiState.value.scannedFiles)
        sendPlaylistsToServiceIfNeeded(uiState.value.discoveredPlaylists)
        mediaController?.transportControls?.playFromMediaId(
            "playlist:" + playlist.uriString, null
        )
    }
)
```

**Why:** `sendPlaylistsToServiceIfNeeded` follows the same deduplication pattern as `sendFilesToServiceIfNeeded`. Queue state changes from the controller callback flow through to the ViewModel for Compose observation. The `onPlaylistClick` ensures files and playlists are synced to the service before requesting playback.

---

## Architecture Diagram (after Task 4)

```
+-------------------------------------------------------------------+
|  mobile module                                                     |
|                                                                    |
|  MainActivity (ComponentActivity)                                  |
|    +-- MediaBrowserCompat -> MyMusicService                        |
|    +-- MediaControllerCompat -> transport controls                 |
|    +-- Controller callback:                                        |
|    |     onPlaybackStateChanged -> pushPlaybackState + pushQueue   |
|    |     onMetadataChanged -> pushPlaybackState                    |
|    |     onQueueChanged / onQueueTitleChanged -> pushQueueState    |
|    +-- sendFilesToServiceIfNeeded() via SET_MEDIA_FILES            |
|    +-- sendPlaylistsToServiceIfNeeded() via SET_PLAYLISTS          |
|                                                                    |
|  MainViewModel (AndroidViewModel)                                  |
|    +-- mediaCacheService -> scanDirectory (mp3 + m3u)              |
|    +-- uiState: StateFlow<MainUiState>                             |
|    |     scannedFiles, discoveredPlaylists,                        |
|    |     isPlaying, isPaused, currentTrackName,                    |
|    |     isPlayingPlaylist, queuePosition                          |
|    +-- onDirectorySelected(uri)                                    |
|    +-- updatePlaybackState(state, mediaId, trackName)              |
|    +-- updateQueueState(queueTitle, queueSize, activeIndex)        |
|    +-- createRandomPlaylist()                                      |
|                                                                    |
|  MainScreen (Composable)                                           |
|    +-- Scaffold + TopAppBar                                        |
|    +-- "Select Folder" + "Create Playlist" buttons                 |
|    +-- Discovered playlists: PlaylistCard (secondaryContainer)     |
|    +-- LazyColumn of tappable FileCards (highlight current)        |
|    +-- PlaybackBar: track name, queue position,                    |
|    |     Play/Pause, Next (playlist only), Stop                    |
|    +-- SnackbarHost for playlist creation feedback                 |
+-------------------------------------------------------------------+
|  shared module                                                     |
|                                                                    |
|  MyMusicService (MediaBrowserServiceCompat)                        |
|    +-- MediaPlayer for playback                                    |
|    +-- playTrack(fileInfo) — reusable MediaPlayer setup            |
|    +-- Playlist queue: playlistQueue, currentQueueIndex            |
|    +-- onTrackCompleted() — auto-advance or stop                   |
|    +-- onPlayFromMediaId() — routes "playlist:..." vs song URI     |
|    +-- onSkipToNext() / onSkipToPrevious()                         |
|    +-- updateSessionQueue() — sets session queue for Auto          |
|    +-- onLoadChildren():                                           |
|    |     root -> [Songs (browsable), Playlists (browsable)]        |
|    |     songs -> [track1, track2, ...] (playable)                 |
|    |     playlists -> [playlist1, playlist2, ...] (playable)       |
|    +-- SET_MEDIA_FILES custom action (clearFiles only)             |
|    +-- SET_PLAYLISTS custom action (clearPlaylists only)           |
|                                                                    |
|  MediaCacheService                                                 |
|    +-- scanDirectory() — scans .mp3 + .m3u files                   |
|    +-- cachedFiles, discoveredPlaylists                            |
|    +-- clearFiles(), clearPlaylists(), clearCache()                |
|    +-- addFile(), addPlaylist()                                    |
|                                                                    |
|  PlaylistService                                                   |
|    +-- generateM3uContent() — write M3U (Task 3)                  |
|    +-- writePlaylist() — save M3U to SAF (Task 3)                 |
|    +-- readPlaylist() — parse M3U from SAF URI (NEW)              |
|                                                                    |
|  PlaylistInfo (data class) — NEW                                   |
|    +-- uriString: String (content URI of .m3u file)               |
|    +-- displayName: String (filename)                              |
|                                                                    |
|  MediaFileInfo (data class) — unchanged                            |
+-------------------------------------------------------------------+
```

## Key Design Decisions

| Decision | Rationale |
|---|---|
| **`playlist:` prefix on mediaId** | Clean routing in `onPlayFromMediaId` — distinguishes playlist requests from song requests without extra APIs |
| **Playlist queue in service** | Standard MediaSession queue (`session.setQueue()`) — Android Auto automatically displays queue UI and skip buttons |
| **`playTrack()` extraction** | Avoids duplicating MediaPlayer setup between single-track and playlist playback code paths |
| **Auto-advance via `onTrackCompleted()`** | Central decision point — queue present = advance, queue empty = stop. Clean separation of concerns |
| **`onSkipToPrevious()` included** | Trivial to add alongside `onSkipToNext`; Android Auto expects both when either is advertised |
| **Granular `clearFiles()` / `clearPlaylists()`** | The two custom actions (`SET_MEDIA_FILES`, `SET_PLAYLISTS`) arrive independently; clearing all data on either would lose the other's state |
| **Browse tree: root → Songs / Playlists** | Standard Android Auto pattern for media apps with multiple content types; each category is browsable |
| **`PlaylistInfo` as separate data class** | Playlist metadata (URI + name) is structurally different from track metadata (URI + name + size); avoids confusion |
| **`readPlaylist()` returns `List<MediaFileInfo>`** | Parsed M3U tracks map directly to MediaFileInfo for playback; `sizeBytes = 0` is acceptable |
| **No build or manifest changes** | All new code uses existing dependencies (`DocumentFile`, `MediaBrowserServiceCompat`, Compose). No new permissions needed — SAF read grant covers M3U file reading |

## Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| M3U track URIs invalid (file moved/deleted) | Track fails to play, error state | `setOnErrorListener` catches it; could enhance `onTrackCompleted` to skip errored tracks in future |
| Large playlists (many tracks) | Queue UI slow on Android Auto | Unlikely in practice (Task 3 creates 3-track playlists); no artificial cap needed |
| Playlist created in Task 3 not visible until re-scan | Confusing UX | After "Create Playlist", user must re-scan folder to see new playlist. Acceptable for Task 4 scope; could auto-add to `discoveredPlaylists` as enhancement |
| `readPlaylist()` called on main thread in `onPlayFromMediaId` | ANR risk for large M3U files | M3U files are tiny (3 entries from Task 3); no risk in practice. Could wrap in coroutine if needed later |
| `SET_PLAYLISTS` arrives before `SET_MEDIA_FILES` | Playlists visible but songs not yet browsable | Both are sent in same `collect` block; timing is close. Browse tree handles empty songs gracefully |

## Verification Steps

1. **Build**: `./gradlew :shared:build && ./gradlew :mobile:build`
2. **Discover playlists**: Deploy, scan folder containing `.mp3` files and previously created `.m3u` playlists. Verify playlists appear in a separate section with distinct styling.
3. **Play playlist**: Tap a playlist card. First track starts playing. PlaybackBar shows track name, "Track 1/3", and "Next" button.
4. **Auto-advance**: Let first track finish. Second track starts automatically. PlaybackBar updates to "Track 2/3".
5. **Next**: Tap "Next" button. Skips to next track. Position updates.
6. **End of playlist**: Let last track finish (or skip to last then let it finish). Playback stops, PlaybackBar disappears, queue clears.
7. **Pause/Resume during playlist**: Tap Pause — pauses. Tap Play — resumes same track at same position. Queue position unchanged.
8. **Stop during playlist**: Tap Stop — stops, bar disappears, queue clears. Tapping playlist again starts from track 1.
9. **Single track still works**: Tap an individual song file. Plays without "Next" button or queue position. No auto-advance.
10. **Create then play**: Tap "Create Playlist", re-scan folder, new playlist appears, tap to play it.
11. **Android Auto** (if available): Browse tree shows "Songs" and "Playlists" categories. Navigate to Playlists, tap one — plays with queue. Skip forward/back buttons appear on playback screen.
