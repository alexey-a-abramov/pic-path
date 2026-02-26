package com.imageviewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imageviewer.R
import com.imageviewer.ui.components.ImageGridItem
import com.imageviewer.ui.components.SearchBar
import com.imageviewer.util.ClipboardHelper
import com.imageviewer.viewmodel.ImageViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGridScreen(
    viewModel: ImageViewModel,
    modifier: Modifier = Modifier
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showFullscreen by remember { mutableStateOf(false) }
    var fullscreenIndex by remember { mutableStateOf(0) }

    val categories = listOf("All", "Screenshots", "Camera", "Downloads", "Other")

    if (showFullscreen) {
        FullscreenImageViewer(
            images = images,
            initialIndex = fullscreenIndex,
            onClose = { showFullscreen = false },
            onCopyPath = { path ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.path_copied)
                    )
                }
            }
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Category Tabs
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategory),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            text = { Text(category) }
                        )
                    }
                }

                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.searchImages(it) }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading && images.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.loading),
                                    modifier = Modifier.padding(top = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    images.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_images_found),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
                                ImageGridItem(
                                    image = image,
                                    onClick = {
                                        fullscreenIndex = index
                                        showFullscreen = true
                                    },
                                    onCopyClick = {
                                        ClipboardHelper.copyToClipboard(context, image.path)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.path_copied)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Refresh FAB
        FloatingActionButton(
            onClick = { viewModel.refreshIndex() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { data ->
            Snackbar(
                snackbarData = data,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
    }
}
