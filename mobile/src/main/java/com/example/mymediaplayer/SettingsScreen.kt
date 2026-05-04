package com.example.mymediaplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
    debugCloudAnnouncements: Boolean,
    onSetDebugCloudAnnouncements: (Boolean) -> Unit,
    onSaveCloudAnnouncementKeys: (kiloKey: String, ttsKey: String, onValidated: () -> Unit) -> Unit,
    bluetoothAutoPlayEnabled: Boolean,
    onToggleBluetoothAutoPlay: () -> Unit,
    onAddCurrentBluetoothDevice: () -> Unit,
    trustedBluetoothDevices: List<TrustedBluetoothDevice>,
    bluetoothDiagnostics: String,
    onRemoveTrustedBluetoothDevice: (String) -> Unit,
    onClearTrustedBluetoothDevices: () -> Unit,
    onRefreshBluetoothDiagnostics: () -> Unit,
    onChoosePlaylistSaveFolder: () -> Unit,
    onBack: () -> Unit
) {
    var showCloudAnnouncementSettingsDialog by remember { mutableStateOf(false) }
    var showManageTrustedBluetoothDialog by remember { mutableStateOf(false) }
    var showBluetoothDiagnosticsDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = { SettingsTopAppBar(onBack = onBack) }
    ) { padding ->
        SettingsScreenContent(
            padding = padding,
            trackVoiceIntroEnabled = trackVoiceIntroEnabled,
            trackVoiceOutroEnabled = trackVoiceOutroEnabled,
            onToggleTrackVoiceIntro = onToggleTrackVoiceIntro,
            onToggleTrackVoiceOutro = onToggleTrackVoiceOutro,
            onShowCloudAnnouncementSettingsDialog = { showCloudAnnouncementSettingsDialog = true },
            bluetoothAutoPlayEnabled = bluetoothAutoPlayEnabled,
            onToggleBluetoothAutoPlay = onToggleBluetoothAutoPlay,
            onAddCurrentBluetoothDevice = onAddCurrentBluetoothDevice,
            onShowManageTrustedBluetoothDialog = { showManageTrustedBluetoothDialog = true },
            onShowBluetoothDiagnosticsDialog = {
                onRefreshBluetoothDiagnostics()
                showBluetoothDiagnosticsDialog = true
            },
            onChoosePlaylistSaveFolder = onChoosePlaylistSaveFolder
        )
    }

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
private fun VoiceAnnouncementsSection(
    trackVoiceIntroEnabled: Boolean,
    trackVoiceOutroEnabled: Boolean,
    onToggleTrackVoiceIntro: () -> Unit,
    onToggleTrackVoiceOutro: () -> Unit,
    onShowCloudAnnouncementSettingsDialog: () -> Unit
) {
    Text(
        text = "Voice Announcements",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )

    SettingsToggleRow(
        label = "Track Voice Intro",
        checked = trackVoiceIntroEnabled,
        onCheckedChange = { onToggleTrackVoiceIntro() }
    )

    SettingsToggleRow(
        label = "Track Voice Outro",
        checked = trackVoiceOutroEnabled,
        onCheckedChange = { onToggleTrackVoiceOutro() }
    )

    SettingsClickRow(
        label = "AI Announcement Settings",
        onClick = onShowCloudAnnouncementSettingsDialog
    )
}

@Composable
private fun BluetoothSection(
    bluetoothAutoPlayEnabled: Boolean,
    onToggleBluetoothAutoPlay: () -> Unit,
    onAddCurrentBluetoothDevice: () -> Unit,
    onShowManageTrustedBluetoothDialog: () -> Unit,
    onShowBluetoothDiagnosticsDialog: () -> Unit
) {
    Text(
        text = "Bluetooth",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    SettingsToggleRow(
        label = "Bluetooth Auto-Play",
        checked = bluetoothAutoPlayEnabled,
        onCheckedChange = { onToggleBluetoothAutoPlay() }
    )

    SettingsClickRow(
        label = "Trust Connected Bluetooth",
        onClick = onAddCurrentBluetoothDevice
    )

    SettingsClickRow(
        label = "Manage Trusted Bluetooth",
        onClick = onShowManageTrustedBluetoothDialog
    )

    SettingsClickRow(
        label = "Bluetooth Diagnostics",
        onClick = onShowBluetoothDiagnosticsDialog
    )
}

@Composable
private fun StorageSection(
    onChoosePlaylistSaveFolder: () -> Unit
) {
    Text(
        text = "Storage",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    SettingsClickRow(
        label = "Select Playlist Save Folder",
        onClick = onChoosePlaylistSaveFolder
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsClickRow(
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun CloudAnnouncementSettingsDialog(
    cloudAnnouncementKiloKey: String,
    cloudAnnouncementTtsKey: String,
    debugCloudAnnouncements: Boolean,
    onSetDebugCloudAnnouncements: (Boolean) -> Unit,
    onSaveCloudAnnouncementKeys: (String, String, () -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    var kiloKeyInput by remember { mutableStateOf(cloudAnnouncementKiloKey) }
    var ttsKeyInput by remember { mutableStateOf(cloudAnnouncementTtsKey) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("AI Announcement Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "API keys for cloud-generated announcements. Kilo works anonymously (no key needed). Google TTS key optional for higher quality.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextField(
                    value = kiloKeyInput,
                    onValueChange = { kiloKeyInput = it },
                    label = { Text("Kilo API key (optional)") },
                    placeholder = { Text("Leave blank for anonymous") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = ttsKeyInput,
                    onValueChange = { ttsKeyInput = it },
                    label = { Text("Google Cloud TTS key (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Debug mode", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Show toast when cloud TTS is used",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = debugCloudAnnouncements,
                        onCheckedChange = onSetDebugCloudAnnouncements
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveCloudAnnouncementKeys(kiloKeyInput.trim(), ttsKeyInput.trim(), onDismissRequest)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun ManageTrustedBluetoothDialog(
    trustedBluetoothDevices: List<TrustedBluetoothDevice>,
    onRemoveTrustedBluetoothDevice: (String) -> Unit,
    onClearTrustedBluetoothDevices: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Trusted Bluetooth Devices") },
        text = {
            Column {
                if (trustedBluetoothDevices.isEmpty()) {
                    Text("No trusted devices yet")
                } else {
                    LazyColumn {
                        items(trustedBluetoothDevices) { device ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name?.ifBlank { null } ?: "Unknown device",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { onRemoveTrustedBluetoothDevice(device.address) }) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onClearTrustedBluetoothDevices,
                enabled = trustedBluetoothDevices.isNotEmpty()
            ) {
                Text("Clear all", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
internal fun BluetoothDiagnosticsDialog(
    bluetoothDiagnostics: String,
    onRefreshBluetoothDiagnostics: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Bluetooth Diagnostics") },
        text = {
            Text(bluetoothDiagnostics)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRefreshBluetoothDiagnostics()
                }
            ) {
                Text("Refresh")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopAppBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Text("\u2190", style = MaterialTheme.typography.titleLarge)
            }
        }
    )
}

@Composable
private fun SettingsScreenContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    trackVoiceIntroEnabled: Boolean,
    trackVoiceOutroEnabled: Boolean,
    onToggleTrackVoiceIntro: () -> Unit,
    onToggleTrackVoiceOutro: () -> Unit,
    onShowCloudAnnouncementSettingsDialog: () -> Unit,
    bluetoothAutoPlayEnabled: Boolean,
    onToggleBluetoothAutoPlay: () -> Unit,
    onAddCurrentBluetoothDevice: () -> Unit,
    onShowManageTrustedBluetoothDialog: () -> Unit,
    onShowBluetoothDiagnosticsDialog: () -> Unit,
    onChoosePlaylistSaveFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VoiceAnnouncementsSection(
            trackVoiceIntroEnabled = trackVoiceIntroEnabled,
            trackVoiceOutroEnabled = trackVoiceOutroEnabled,
            onToggleTrackVoiceIntro = onToggleTrackVoiceIntro,
            onToggleTrackVoiceOutro = onToggleTrackVoiceOutro,
            onShowCloudAnnouncementSettingsDialog = onShowCloudAnnouncementSettingsDialog
        )

        Spacer(modifier = Modifier.height(16.dp))

        BluetoothSection(
            bluetoothAutoPlayEnabled = bluetoothAutoPlayEnabled,
            onToggleBluetoothAutoPlay = onToggleBluetoothAutoPlay,
            onAddCurrentBluetoothDevice = onAddCurrentBluetoothDevice,
            onShowManageTrustedBluetoothDialog = onShowManageTrustedBluetoothDialog,
            onShowBluetoothDiagnosticsDialog = onShowBluetoothDiagnosticsDialog
        )

        Spacer(modifier = Modifier.height(16.dp))

        StorageSection(
            onChoosePlaylistSaveFolder = onChoosePlaylistSaveFolder
        )
    }
}
