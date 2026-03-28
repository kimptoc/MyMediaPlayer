1. **Understand & Analyze**: The `MainScreen` function in `MainScreen.kt` is over 1100 lines long, violating the complexity threshold. This is because it contains all the UI layout and nested conditionals for multiple states (scan, playlist, various dialogs, search, library tabs, etc.).
2. **Design**: To solve this, I need to extract several complex pieces into separate `@Composable` functions. Specifically, I'll extract the massive top-level `Scaffold`'s `TopAppBar` dropdown menu into its own component. I will also extract the contents of the main `Scaffold` area (specifically the layout logic to handle Search, Scanning Progress, and Library tabs) into smaller composables or helper functions. And I'll extract some of the large dialogs at the bottom (like `ScanDialog`, `AddToPlaylistDialog`) into separate files or just separate functions within `MainScreen.kt`. Given the task scope, extracting them into separate functions within `MainScreen.kt` (or closely related files if they grow too large) is typically preferred unless the prompt asks to create new files. In this case, I will create private helper composables within `MainScreen.kt`.

*Wait, extracting all of these might be risky if I break state propagation. I need to be careful. Let's look at `MainScreen` again. The main block has many states.*

Let's extract the `DropdownMenu` inside the `TopAppBar` into a `MainDropdownMenu` composable.
Let's extract the various `AlertDialog` blocks into separate functions:
  - `ScanDialog`
  - `AddToPlaylistDialog`
  - `CreateFromSelectionDialog`
  - `DeletePlaylistDialog`
  - `RenamePlaylistDialog`
  - `QueueDialog`
  - `ManageTrustedBluetoothDialog`
  - `BluetoothDiagnosticsDialog`
  - `CloudAnnouncementSettingsDialog`

By moving these dialogs out of `MainScreen` into their own private composable functions, we can significantly reduce the line count of `MainScreen`.

3. **Plan Details**:
- Extract `ScanDialog` to a private composable `ScanDialogContent`.
- Extract `AddToPlaylistDialog` to a private composable `AddToPlaylistDialogContent`.
- Extract `CreateFromSelectionDialog` to `CreateFromSelectionDialogContent`.
- Extract `DeletePlaylistDialog` to `DeletePlaylistDialogContent`.
- Extract `RenamePlaylistDialog` to `RenamePlaylistDialogContent`.
- Extract `QueueDialog` to `QueueDialogContent`.
- Extract `ManageTrustedBluetoothDialog` to `ManageTrustedBluetoothDialogContent`.
- Extract `BluetoothDiagnosticsDialog` to `BluetoothDiagnosticsDialogContent`.
- Extract `CloudAnnouncementSettingsDialog` to `CloudAnnouncementSettingsDialogContent`.

Wait, these dialogs capture local states like `scanCountText`, `scanDeepMode`, `scanWholeDriveMode`, etc. These states are defined at the top of `MainScreen`:

```kotlin
    var scanCountText by remember { mutableStateOf(uiState.scan.lastScanLimit.toString()) }
    var scanDeepMode by remember { mutableStateOf(uiState.scan.deepScanEnabled) }
    var scanWholeDriveMode by remember { mutableStateOf(false) }
```
If we extract the dialogs, we can either:
1) Keep the states in `MainScreen` and pass them as parameters (along with an `on...Change` callback).
2) Move the states into the dialog composables if they are purely local to the dialog (except maybe the initial values). However, some initial values depend on `uiState`. And the submit callbacks need to read them.

Actually, it might be cleaner to pass the current values and callbacks, or just extract the dialogs entirely and pass the required state. For `ScanDialog`, we need `scanCountText`, `onScanCountTextChange`, `scanWholeDriveMode`, `onScanWholeDriveModeChange`, etc. This might create functions with many parameters.

Another option is to group the UI sections.
- Extract `LibraryContent` (the `ScrollableTabRow` and the `when` block for the selected tab).
- Extract `SearchContent` (the `TextField` and search results logic).
- Extract `TopAppBarActions` (the `DropdownMenu`).

Let's check the size of the dialogs block at the end of `MainScreen`. From line 673 to 1238 are almost entirely dialogs! That's ~550 lines of dialogs. Extracting those into private composable functions is exactly the right approach.

Let's write a small shell script to analyze the exact lengths of these dialog blocks.
