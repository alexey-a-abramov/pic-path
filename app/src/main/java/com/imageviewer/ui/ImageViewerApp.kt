package com.imageviewer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.imageviewer.viewmodel.ImageViewModel

@Composable
fun ImageViewerApp(viewModel: ImageViewModel) {
    var showAboutScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                showAboutScreen -> AboutScreen(
                    onNavigateBack = { showAboutScreen = false }
                )
                showSettingsScreen -> SettingsScreen(
                    onNavigateBack = { showSettingsScreen = false },
                    onNavigateToAbout = { showAboutScreen = true }
                )
                else -> ImageGridScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { showSettingsScreen = true }
                )
            }
        }
    }
}
