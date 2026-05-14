# Head-Unit Root No-Block Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Android Auto from showing "MyMediaPlayer doesn't seem to be working at the moment" by ensuring `onLoadChildren` for the root, search, and home tabs always responds immediately, even while the service is loading its cache.

**Architecture:** Add a small `internal fun parentRequiresLoadedCache(parentId: String): Boolean` predicate on `MyMusicService`. Change the unconditional `if (isScanning)` queue-and-detach branch in `onLoadChildren` to `if (isScanning && parentRequiresLoadedCache(parentId))`. Cache-dependent screens (Songs/Albums/Artists/Genres/Decades/Playlists/all prefix children) keep their existing pending-queue path; only ROOT, SEARCH, and HOME bypass it.

**Tech Stack:** Kotlin, AndroidX MediaBrowserServiceCompat, Robolectric for unit tests, Gradle.

---

## File Structure

- **Modify:** `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt`
  - Add: `internal fun parentRequiresLoadedCache(parentId: String): Boolean` (new method on the class — same style as the existing `internal fun shouldLoadChildrenAsync` at line 1439).
  - Modify: the `if (isScanning)` block in `onLoadChildren` at line 735–743 to also check the predicate.
- **Modify:** `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt`
  - Add: one Robolectric-driven `@Test` that exercises the predicate across cache-independent and cache-dependent IDs.

No new files. No changes to scan, persist, or queue/delivery logic.

---

## Task 1: Add `parentRequiresLoadedCache` predicate with unit test

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt` (add a new `internal fun` near line 1439, next to `shouldLoadChildrenAsync`)
- Test: `shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt` (add a single new `@Test`)

- [ ] **Step 1: Write the failing test**

Append the following test method to `MyMusicServiceTest` (anywhere inside the class body, e.g. after the existing `shouldLoadChildrenAsync_requiresIndexes` test). The test uses Robolectric the same way other service-method tests in this file do (see lines 309, 329, 351 for the pattern).

```kotlin
@Test
fun parentRequiresLoadedCache_falseForRootSearchHome_trueForDataIds() {
    val service = Robolectric.buildService(MyMusicService::class.java).get()

    // Cache-independent: must respond immediately even while scanning.
    assertFalse(service.parentRequiresLoadedCache("root"))
    assertFalse(service.parentRequiresLoadedCache("search"))
    assertFalse(service.parentRequiresLoadedCache("home"))

    // Cache-dependent top-level tabs.
    assertTrue(service.parentRequiresLoadedCache("songs"))
    assertTrue(service.parentRequiresLoadedCache("songs_all"))
    assertTrue(service.parentRequiresLoadedCache("playlists"))
    assertTrue(service.parentRequiresLoadedCache("albums"))
    assertTrue(service.parentRequiresLoadedCache("genres"))
    assertTrue(service.parentRequiresLoadedCache("artists"))
    assertTrue(service.parentRequiresLoadedCache("decades"))

    // Cache-dependent prefix-based children (conservative default).
    assertTrue(service.parentRequiresLoadedCache("album:Rock"))
    assertTrue(service.parentRequiresLoadedCache("artist:Beatles"))
    assertTrue(service.parentRequiresLoadedCache("genre:Jazz"))
    assertTrue(service.parentRequiresLoadedCache("playlist:abc"))
    assertTrue(service.parentRequiresLoadedCache("smart_playlist:flagged"))

    // Unknown IDs default to true (safer to defer than to over-respond).
    assertTrue(service.parentRequiresLoadedCache("something_unexpected"))
}
```

The existing file already imports `Robolectric`, `RobolectricTestRunner`, `assertTrue`, and `assertFalse` (lines 6–13), so no new imports are needed.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.example.mymediaplayer.shared.MyMusicServiceTest.parentRequiresLoadedCache_falseForRootSearchHome_trueForDataIds" -q 2>&1 | tail -30
```

Expected: compilation failure with something like `unresolved reference: parentRequiresLoadedCache`.

- [ ] **Step 3: Implement the predicate**

Open `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt` and, immediately above `internal fun shouldLoadChildrenAsync(...)` at line 1439, insert:

```kotlin
    internal fun parentRequiresLoadedCache(parentId: String): Boolean {
        // Cache-independent: these screens render purely from static data,
        // so they MUST respond even while the service is loading its cache.
        // Returning a Result.detach() for the root tree past Android Auto's
        // onLoadChildren timeout causes "MyMediaPlayer doesn't seem to be
        // working at the moment."
        return when (parentId) {
            ROOT_ID, SEARCH_ID, HOME_ID -> false
            else -> true
        }
    }

```

(Keep the existing `shouldLoadChildrenAsync` declaration right after — do not delete it.)

- [ ] **Step 4: Run the test to verify it passes**

Run the same command as Step 2:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.example.mymediaplayer.shared.MyMusicServiceTest.parentRequiresLoadedCache_falseForRootSearchHome_trueForDataIds" -q 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL` and the test passes.

- [ ] **Step 5: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt \
        shared/src/test/java/com/example/mymediaplayer/shared/MyMusicServiceTest.kt
git commit -m "$(cat <<'EOF'
Add parentRequiresLoadedCache predicate for onLoadChildren

Classifies parent IDs as cache-independent (ROOT, SEARCH, HOME) vs.
cache-dependent. Unknown IDs default to cache-dependent, which keeps
the existing queue-and-detach behaviour as a safe default.
EOF
)"
```

---

## Task 2: Apply the predicate in `onLoadChildren`

**Files:**
- Modify: `shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt:735-743`

- [ ] **Step 1: Inspect the current `onLoadChildren` opening block**

The current code at line 735–743 looks like:

```kotlin
override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
    if (isScanning) {
        synchronized(pendingResults) {
            val list = pendingResults.getOrPut(parentId) { mutableListOf() }
            list.add(result)
        }
        result.detach()
        return
    }

    lastBrowseParentId = parentId
    // ...rest of method unchanged
```

The gate is **unconditional on `isScanning`** — every parent gets queued, including ROOT.

- [ ] **Step 2: Tighten the gate**

Change the `if (isScanning) {` line so that the queue-and-detach path only fires when the parent actually needs the loaded cache. Use the Edit tool with:

`old_string`:

```kotlin
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        if (isScanning) {
            synchronized(pendingResults) {
                val list = pendingResults.getOrPut(parentId) { mutableListOf() }
                list.add(result)
            }
            result.detach()
            return
        }
```

`new_string`:

```kotlin
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        if (isScanning && parentRequiresLoadedCache(parentId)) {
            synchronized(pendingResults) {
                val list = pendingResults.getOrPut(parentId) { mutableListOf() }
                list.add(result)
            }
            result.detach()
            return
        }
```

Nothing else in the method changes. Cache-independent IDs (`ROOT_ID`, `SEARCH_ID`, `HOME_ID`) now fall through to the existing dispatch logic below (which goes through `buildMediaItems(parentId)` and returns the static items immediately).

- [ ] **Step 3: Run the predicate test plus the broader service test class**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.example.mymediaplayer.shared.MyMusicServiceTest" -q 2>&1 | tail -40
```

Expected: `BUILD SUCCESSFUL`. All existing `MyMusicServiceTest` tests still pass, plus the new `parentRequiresLoadedCache_…` test from Task 1.

- [ ] **Step 4: Build the debug APK to confirm the full module compiles**

```bash
./gradlew :shared:assembleDebug :mobile:assembleDebug -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual DHU verification**

Run these steps on a connected device with the Desktop Head Unit emulator running, or on the actual car:

1. Force-stop the phone-side process so the service must re-create on next browse:
   ```bash
   adb shell am force-stop com.example.mymediaplayer
   ```
2. In Android Auto / DHU, open MyMediaPlayer.
3. **Expected:** the root menu shows `[Home, Search]` within ~1 s. No "MyMediaPlayer doesn't seem to be working at the moment." error appears. (Spinner may flash briefly — that's fine, as long as the root resolves before Android Auto's timeout.)
4. Tap **Home**. Expected: renders quickly. If the cache is still loading, Home initially shows an empty body, then populates within seconds. (This is the existing `notifyChildrenChanged(HOME_ID)` path firing at MyMusicService.kt:1404.)
5. Tap **Songs** (or any cache-dependent tab) while the scan is in flight. Expected: spinner until cache+indexes finish loading (existing queue behaviour), then the list appears. No "doesn't seem to be working" error.

If the root tab still fails to load: capture `adb logcat | grep MyMusicService` during the reproduction and confirm whether the failure is happening before or after `onLoadChildren` is invoked. Either of those is a separate bug, not a regression of this change.

- [ ] **Step 6: Commit**

```bash
git add shared/src/main/java/com/example/mymediaplayer/shared/MyMusicService.kt
git commit -m "$(cat <<'EOF'
Don't block ROOT/SEARCH/HOME on isScanning in onLoadChildren

Android Auto times out onLoadChildren for the root tree and shows
"MyMediaPlayer doesn't seem to be working at the moment" when the
service is still loading its cache or rebuilding metadata indexes.
Gate the queue-and-detach path on parentRequiresLoadedCache so only
cache-dependent IDs wait; ROOT, SEARCH, and HOME respond immediately
with their static menu items.
EOF
)"
```

---

## Self-Review Notes

- **Spec coverage:** Spec sections "The change", classification table, and testing are all covered by Task 1 (predicate + unit test) and Task 2 (gate change + DHU verification + module build).
- **Placeholder scan:** No TBD / TODO / "appropriate error handling" / "similar to above" patterns. Every step has the actual code or command.
- **Type consistency:** Predicate is named `parentRequiresLoadedCache` in Tasks 1, 2, and the test. The constants `ROOT_ID`, `SEARCH_ID`, `HOME_ID` referenced in the predicate match the existing companion-object declarations at MyMusicService.kt:57–66.
- **Out-of-scope items from the spec (persisting metadata indexes; diagnosing cache-miss-on-cold-start) are intentionally not included as tasks.** They are tracked in the spec's "Out of scope" section.
