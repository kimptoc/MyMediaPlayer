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
