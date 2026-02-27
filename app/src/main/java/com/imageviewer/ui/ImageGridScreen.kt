package com.imageviewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
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
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedImageIds by viewModel.selectedImageIds.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showFullscreen by remember { mutableStateOf(false) }
    var fullscreenIndex by remember { mutableStateOf(0) }
    var shouldRefresh by remember { mutableStateOf(false) }

    val categories = listOf("All", "Screenshots", "Camera", "Downloads", "Other")
    val categoryLabels = mapOf(
        "All" to stringResource(R.string.cat_all),
        "Screenshots" to stringResource(R.string.cat_screenshots),
        "Camera" to stringResource(R.string.cat_camera),
        "Downloads" to stringResource(R.string.cat_downloads),
        "Other" to stringResource(R.string.cat_other)
    )
    
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)

    // Auto-refresh after copy with delay for smooth UX
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            kotlinx.coroutines.delay(300) // Small delay to avoid jarring refresh
            viewModel.refreshIndex()
            shouldRefresh = false
        }
    }

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
                shouldRefresh = true
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text(stringResource(R.string.selection_mode))
                        } else {
                            Text(stringResource(R.string.app_name))
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToAbout) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.about)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (isSelectionMode && selectedImageIds.isNotEmpty()) {
                    BottomAppBar {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.clearSelection() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.clear))
                            }
                            Button(
                                onClick = {
                                    val paths = viewModel.getSelectedPaths()
                                    val combinedPath = paths.joinToString("\n")
                                    ClipboardHelper.copyToClipboard(context, combinedPath)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.multiple_paths_copied)
                                        )
                                    }
                                    viewModel.clearSelection()
                                    shouldRefresh = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.copy_selected, selectedImageIds.size))
                            }
                        }
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Category Tabs
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            text = { Text(categoryLabels[category] ?: category) }
                        )
                    }
                }

                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.searchImages(it) }
                )

                SwipeRefresh(
                    state = swipeRefreshState,
                    onRefresh = { viewModel.refreshIndex() },
                    modifier = Modifier.fillMaxSize()
                ) {
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
                                                if (isSelectionMode) {
                                                    viewModel.toggleImageSelection(image.id)
                                                } else {
                                                    fullscreenIndex = index
                                                    showFullscreen = true
                                                }
                                            },
                                            onCopyClick = {
                                                ClipboardHelper.copyToClipboard(context, image.path)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = context.getString(R.string.path_copied)
                                                    )
                                                }
                                                shouldRefresh = true
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) {
                                                    viewModel.toggleSelectionMode(true)
                                                }
                                                viewModel.toggleImageSelection(image.id)
                                            },
                                            isSelected = selectedImageIds.contains(image.id)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
