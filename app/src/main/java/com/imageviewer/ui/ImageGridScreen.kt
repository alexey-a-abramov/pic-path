package com.imageviewer.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.imageviewer.data.repository.BrowseMode
import com.imageviewer.ui.components.ImageGridItem
import com.imageviewer.ui.components.SearchBar
import com.imageviewer.util.ClipboardHelper
import com.imageviewer.util.MultiCopyFormat
import com.imageviewer.util.SettingsManager
import com.imageviewer.viewmodel.ImageViewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGridScreen(
    viewModel: ImageViewModel,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val browseMode by viewModel.browseMode.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedImageIds by viewModel.selectedImageIds.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val multiCopyFormat by SettingsManager.getMultiCopyFormat(context)
        .collectAsState(initial = MultiCopyFormat.DEFAULT)

    var showFullscreen by remember { mutableStateOf(false) }
    var fullscreenIndex by remember { mutableStateOf(0) }
    var targetPath by remember { mutableStateOf<String?>(null) }

    val categories = listOf("All", "Screenshots", "Camera", "Downloads", "Other")
    val categoryLabels = mapOf(
        "All" to stringResource(R.string.cat_all),
        "Screenshots" to stringResource(R.string.cat_screenshots),
        "Camera" to stringResource(R.string.cat_camera),
        "Downloads" to stringResource(R.string.cat_downloads),
        "Other" to stringResource(R.string.cat_other)
    )

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)

    val permissionPromptText = stringResource(R.string.all_files_permission_required)
    val grantText = stringResource(R.string.grant)

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    // After an edit-and-save, the new file's path is queued; jump back into
    // fullscreen on it as soon as the rescan picks it up.
    LaunchedEffect(images, targetPath) {
        targetPath?.let { path ->
            val index = images.indexOfFirst { it.path == path }
            if (index != -1) {
                fullscreenIndex = index
                showFullscreen = true
                targetPath = null
            } else {
                viewModel.refreshIndex()
            }
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
                targetPath = path
            },
            onRefresh = { viewModel.refreshIndex() }
        )
    } else {

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text(stringResource(R.string.selection_mode, selectedImageIds.size))
                        } else {
                            BrowseModeSelector(
                                mode = browseMode,
                                onSelect = { newMode ->
                                    if (newMode == BrowseMode.AllFiles && !hasAllFilesAccess()) {
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = permissionPromptText,
                                                actionLabel = grantText
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                openAllFilesAccessSettings(context)
                                            }
                                        }
                                        return@BrowseModeSelector
                                    }
                                    viewModel.selectBrowseMode(newMode)
                                }
                            )
                        }
                    },
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.exit_selection)
                                )
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            if (images.size <= SELECT_ALL_THRESHOLD) {
                                IconButton(onClick = { viewModel.selectAll() }) {
                                    Icon(
                                        imageVector = Icons.Default.SelectAll,
                                        contentDescription = stringResource(R.string.select_all)
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleSelectionMode(true) }) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = stringResource(R.string.select_mode)
                                )
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings)
                                )
                            }
                            IconButton(onClick = onNavigateToAbout) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(R.string.about)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (isSelectionMode) {
                    BottomAppBar {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (images.size <= SELECT_ALL_THRESHOLD) {
                                OutlinedButton(
                                    onClick = { viewModel.selectAll() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.SelectAll, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.select_all))
                                }
                            }
                            Button(
                                onClick = {
                                    val paths = viewModel.getSelectedPaths()
                                    val combined = ClipboardHelper.formatPathsForConsole(paths, multiCopyFormat)
                                    ClipboardHelper.copyToClipboard(context, combined)
                                    val count = paths.size
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.multiple_paths_copied, count)
                                        )
                                    }
                                    viewModel.clearSelection()
                                                    },
                                enabled = selectedImageIds.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                val fmtTag = stringResource(
                                    when (multiCopyFormat) {
                                        MultiCopyFormat.SPACE -> R.string.fmt_tag_space
                                        MultiCopyFormat.COMMA -> R.string.fmt_tag_comma
                                        MultiCopyFormat.SEMICOLON -> R.string.fmt_tag_semicolon
                                        MultiCopyFormat.AT_PREFIX -> R.string.fmt_tag_at
                                    }
                                )
                                Text(stringResource(R.string.copy_selected_fmt, selectedImageIds.size, fmtTag))
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
                if (browseMode == BrowseMode.Images) {
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
                }

                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.searchImages(it) },
                    placeholderRes = if (browseMode == BrowseMode.AllFiles)
                        R.string.search_hint_files
                    else
                        R.string.search_hint
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
                                        text = stringResource(
                                            if (browseMode == BrowseMode.AllFiles)
                                                R.string.no_files_found
                                            else
                                                R.string.no_images_found
                                        ),
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
                                    itemsIndexed(images, key = { _, image -> "${image.type}:${image.id}" }) { index, image ->
                                        ImageGridItem(
                                            image = image,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    viewModel.toggleImageSelection(image.id)
                                                } else if (image.mimeType.startsWith("image/")) {
                                                    fullscreenIndex = index
                                                    showFullscreen = true
                                                } else {
                                                    ClipboardHelper.copyToClipboard(context, image.path)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            message = context.getString(R.string.path_copied)
                                                        )
                                                    }
                                                                                    }
                                            },
                                            onLongClick = {
                                                // First long-press also copies the path; subsequent
                                                // long-presses just toggle membership.
                                                val wasEmpty = !isSelectionMode
                                                if (wasEmpty) {
                                                    viewModel.toggleSelectionMode(true)
                                                }
                                                viewModel.toggleImageSelection(image.id)
                                                if (wasEmpty) {
                                                    ClipboardHelper.copyToClipboard(context, image.path)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            message = context.getString(R.string.path_copied)
                                                        )
                                                    }
                                                                                    }
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

private const val SELECT_ALL_THRESHOLD = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseModeSelector(
    mode: BrowseMode,
    onSelect: (BrowseMode) -> Unit
) {
    val options = listOf(BrowseMode.Images, BrowseMode.AllFiles)
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(
                    text = stringResource(
                        when (option) {
                            BrowseMode.Images -> R.string.mode_images
                            BrowseMode.AllFiles -> R.string.mode_all_files
                        }
                    )
                )
            }
        }
    }
}

private fun hasAllFilesAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

private fun openAllFilesAccessSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }
}
