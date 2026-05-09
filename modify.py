import re

with open("mobile/src/main/java/com/example/mymediaplayer/SettingsScreen.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.compose.runtime.getValue\n", "")
content = content.replace("import androidx.compose.runtime.setValue\n", "")

content = content.replace("var kiloKeyInput by remember { mutableStateOf(cloudAnnouncementKiloKey) }", "val (kiloKeyInput, setKiloKeyInput) = remember { mutableStateOf(cloudAnnouncementKiloKey) }")
content = content.replace("var ttsKeyInput by remember { mutableStateOf(cloudAnnouncementTtsKey) }", "val (ttsKeyInput, setTtsKeyInput) = remember { mutableStateOf(cloudAnnouncementTtsKey) }")

content = content.replace("var showCloudAnnouncementSettingsDialog by remember { mutableStateOf(false) }", "val (showCloudAnnouncementSettingsDialog, setShowCloudAnnouncementSettingsDialog) = remember { mutableStateOf(false) }")
content = content.replace("var showManageTrustedBluetoothDialog by remember { mutableStateOf(false) }", "val (showManageTrustedBluetoothDialog, setShowManageTrustedBluetoothDialog) = remember { mutableStateOf(false) }")
content = content.replace("var showBluetoothDiagnosticsDialog by remember { mutableStateOf(false) }", "val (showBluetoothDiagnosticsDialog, setShowBluetoothDiagnosticsDialog) = remember { mutableStateOf(false) }")

content = content.replace("onValueChange = { kiloKeyInput = it }", "onValueChange = setKiloKeyInput")
content = content.replace("onValueChange = { ttsKeyInput = it }", "onValueChange = setTtsKeyInput")

content = content.replace("showCloudAnnouncementSettingsDialog = true", "setShowCloudAnnouncementSettingsDialog(true)")
content = content.replace("showCloudAnnouncementSettingsDialog = false", "setShowCloudAnnouncementSettingsDialog(false)")

content = content.replace("showManageTrustedBluetoothDialog = true", "setShowManageTrustedBluetoothDialog(true)")
content = content.replace("showManageTrustedBluetoothDialog = false", "setShowManageTrustedBluetoothDialog(false)")

content = content.replace("showBluetoothDiagnosticsDialog = true", "setShowBluetoothDiagnosticsDialog(true)")
content = content.replace("showBluetoothDiagnosticsDialog = false", "setShowBluetoothDiagnosticsDialog(false)")

with open("mobile/src/main/java/com/example/mymediaplayer/SettingsScreen.kt", "w") as f:
    f.write(content)
