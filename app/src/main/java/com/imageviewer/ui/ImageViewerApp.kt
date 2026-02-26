package com.imageviewer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.imageviewer.viewmodel.ImageViewModel

@Composable
fun ImageViewerApp(viewModel: ImageViewModel) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ImageGridScreen(viewModel = viewModel)
        }
    }
}
