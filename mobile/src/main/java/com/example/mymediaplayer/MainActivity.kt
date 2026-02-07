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
