package com.example.mymediaplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mymediaplayer.shared.MediaFileInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onSelectFolder: () -> Unit,
    onFileClick: (MediaFileInfo) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("MyMediaPlayer") })
        },
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
                        FileCard(
                            file = file,
                            isCurrentTrack = file.uriString == uiState.currentMediaId,
                            onClick = { onFileClick(file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackBar(
    trackName: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
fun FileCard(file: MediaFileInfo, isCurrentTrack: Boolean, onClick: () -> Unit) {
    val colors = if (isCurrentTrack) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = colors
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
