# Whole-Device Scan Caching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Room DB caching to `MainViewModel.scanWholeDevice()` so whole-device mode doesn't rescan on every app open.

**Architecture:** Mirror the existing cache pattern from `onDirectorySelected()` (lines 205-219 of MainViewModel.kt). Check Room DB via `loadPersistedCache()` before scanning, persist via `persistCache()` after scanning. Use `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` as the cache key URI.

**Tech Stack:** Kotlin, Android Room, Coroutines, Robolectric (unit tests)

---

### Task 0: Create feature branch from main

- [ ] **Step 1: Create branch**

```bash
git checkout main && git pull
git checkout -b fix/whole-device-scan-caching-55
```

---

### Task 1: Add Room DB cache check and persist to `scanWholeDevice()`

**Files:**
- Modify: `mobile/src/main/java/com/example/mymediaplayer/MainViewModel.kt:296-370`

- [ ] **Step 1: Add MediaStore import**

Add to the imports section of `MainViewModel.kt`:
```kotlin
import android.provider.MediaStore
```

- [ ] **Step 2: Add Room DB cache check inside the coroutine, before the scan**

In `scanWholeDevice()`, immediately after `viewModelScope.launch(Dispatchers.IO) {` (line 317) and before the `val current = _uiState.value` block (line 318), insert:

```kotlin
            if (!forceRescan) {
                val wholeDeviceUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val persisted =
                    mediaCacheService.loadPersistedCache(getApplication(), wholeDeviceUri, maxFiles)
                if (persisted != null) {
                    scanCache[key] = persisted.files to persisted.playlists
                    _uiState.value = resetAfterScan(
                        persisted.files,
                        persisted.playlists,
                        maxFiles,
                        deepScan = false,
                        scanMessage = "Whole-drive scan loaded from cache"
                    )
                    reimportPlaylistsFromSaveFolderIfNeeded()
                    metadataKey = null
                    return@launch
                }
            }
```

This mirrors the pattern at lines 205-219 of `onDirectorySelected()`.

- [ ] **Step 3: Add Room DB persist after scan completes**

After the existing `scanCache[key] = files to playlists` line (around line 350), add:

```kotlin
            mediaCacheService.persistCache(
                getApplication(),
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                maxFiles
            )
```

- [ ] **Step 4: Run existing tests to verify no regressions**

```bash
./gradlew :mobile:testDebugUnitTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all existing tests pass.

- [ ] **Step 5: Run shared tests too**

```bash
./gradlew :shared:testDebugUnitTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add mobile/src/main/java/com/example/mymediaplayer/MainViewModel.kt
git commit -m "$(cat <<'EOF'
fix: add Room DB caching to whole-device scan mode

scanWholeDevice() previously had no Room DB caching, causing a full
MediaStore query on every app open. Now checks loadPersistedCache()
before scanning and calls persistCache() after, mirroring the existing
pattern in onDirectorySelected(). Uses EXTERNAL_CONTENT_URI as the
cache key, matching the service-side loadCachedTreeIfAvailable().

Closes #55

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Push branch and open PR

- [ ] **Step 1: Push branch**

```bash
git push -u origin fix/whole-device-scan-caching-55
```

- [ ] **Step 2: Open PR referencing issue #55**

```bash
gh pr create \
  --title "Fix unnecessary media rescan on every app open (whole-device mode)" \
  --body "$(cat <<'EOF'
## Summary

- `scanWholeDevice()` now checks Room DB cache before scanning, and persists results after scanning
- Mirrors the existing cache pattern in `onDirectorySelected()` (lines 205-219)
- Uses `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` as the cache key, consistent with the service-side `loadCachedTreeIfAvailable()`
- First open after install: full scan (populates cache). Subsequent opens: instant load from cache.

Closes #55

## Test plan

- [ ] With whole-device mode enabled, open the app — should see full scan with progress
- [ ] Close and reopen the app — should load instantly from cache (no "Scanning..." progress)
- [ ] Change scan limit in settings, reopen — should rescan (cache invalidated by limit mismatch)
- [ ] Unit tests: `./gradlew :mobile:testDebugUnitTest`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
