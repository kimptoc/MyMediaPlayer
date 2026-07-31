package com.example.mymediaplayer

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow

internal fun clearPrefs(app: Application) {
    app.getSharedPreferences("mymediaplayer_prefs", Application.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
}

internal fun seedUiState(viewModel: MainViewModel, state: MainUiState) {
    val field = viewModel.javaClass.getDeclaredField("_uiState")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val flow = field.get(viewModel) as MutableStateFlow<MainUiState>
    flow.value = state
}
