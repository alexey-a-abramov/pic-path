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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.imageviewer.R
import com.imageviewer.data.model.ImageFile
import com.imageviewer.data.repository.BrowseMode
import com.imageviewer.ui.components.FolderGridItem
import com.imageviewer.ui.components.ImageGridItem
import com.imageviewer.ui.components.SearchBar
import com.imageviewer.util.ClipboardHelper
import com.imageviewer.util.MultiCopyFormat
import com.imageviewer.util.SettingsManager
import com.imageviewer.viewmodel.ImageViewModel
import kotlinx.coroutines.launch

private const val SELECT_ALL_THRESHOLD = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGridScreen(
    viewModel: ImageViewModel,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lazyItems = viewModel.pagedImages.collectAsLazyPagingItems()
    val lazyFolders = viewModel.pagedFolders.collectAsLazyPagingItems()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val browseMode by viewModel.browseMode.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedImageIds by viewModel.selectedImageIds.collectAsStateWithLifecycle()
    val selectedFolderPaths by viewModel.selectedFolderPaths.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val multiCopyFormat by SettingsManager.getMultiCopyFormat(context)
        .collectAsState(initial = MultiCopyFormat.DEFAULT)
    val folderTrailingSlash by SettingsManager.getFolderTrailingSlash(context)
        .collectAsState(initial = true)

    // Fullscreen viewer state. The viewer takes a fixed snapshot list — for the
    // grid-tap path this is the currently-loaded paging window; for the
    // post-edit path it's a single-element list with the freshly saved image.
    var fullscreenImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    var fullscreenIndex by remember { mutableStateOf(0) }
    var pendingEditedPath by remember { mutableStateOf<String?>(null) }

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
    val pathCopiedText = stringResource(R.string.path_copied)

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }
    // Inside a folder → first back press exits to the folder list.
    BackHandler(enabled = !isSelectionMode && browseMode == BrowseMode.Folders && selectedFolder != null) {
        viewModel.selectFolder(null)
    }

    // After an edit-and-save, look up the saved file and open fullscreen on it
    // as a single-image preview. We don't try to maintain pager state across
    // a paginated refresh — too easy to loop. The grid will pick up the new
    // file on its next refresh independently.
    LaunchedEffect(pendingEditedPath) {
        val path = pendingEditedPath ?: return@LaunchedEffect
        val image = viewModel.findByPath(path)
        if (image != null) {
            fullscreenImages = listOf(image)
            fullscreenIndex = 0
            pendingEditedPath = null
        }
    }

    if (fullscreenImages.isNotEmpty()) {
        FullscreenImageViewer(
            images = fullscreenImages,
            initialIndex = fullscreenIndex,
            onClose = { fullscreenImages = emptyList() },
            onCopyPath = {
                scope.launch {
                    snackbarHostState.showSnackbar(message = pathCopiedText)
                }
            },
            onRefresh = {
                viewModel.refreshIndex()
                lazyItems.refresh()
            },
            onEditSaved = { newPath ->
                // Drop current viewer; the LaunchedEffect on pendingEditedPath
                // will re-open with the saved file as a single-image preview.
                fullscreenImages = emptyList()
                pendingEditedPath = newPath
            }
        )
        return
    }

    // When in Folders mode without a selected folder, the grid renders folder
    // tiles from `lazyFolders`. Otherwise it renders image tiles from `lazyItems`.
    val showingFolders = browseMode == BrowseMode.Folders && selectedFolder == null
    val pagedTotal = if (showingFolders) lazyFolders.itemCount else lazyItems.itemCount
    val selectionCount = if (showingFolders) selectedFolderPaths.size else selectedImageIds.size
    val refreshing = if (showingFolders) {
        lazyFolders.loadState.refresh is LoadState.Loading
    } else {
        lazyItems.loadState.refresh is LoadState.Loading
    }
    val isEmpty = pagedTotal == 0 && (
        if (showingFolders) lazyFolders.loadState.refresh is LoadState.NotLoading
        else lazyItems.loadState.refresh is LoadState.NotLoading
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when {
                        isSelectionMode -> Text(
                            stringResource(R.string.selection_mode, selectionCount)
                        )
                        // Drilled into a folder — show its name in the title.
                        browseMode == BrowseMode.Folders && selectedFolder != null -> Text(
                            text = selectedFolder!!.substringAfterLast('/').ifBlank { selectedFolder!! },
                            maxLines = 1
                        )
                        else -> BrowseModeSelector(
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
                    when {
                        isSelectionMode -> IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.exit_selection)
                            )
                        }
                        browseMode == BrowseMode.Folders && selectedFolder != null -> IconButton(
                            onClick = { viewModel.selectFolder(null) }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        if (pagedTotal in 1..SELECT_ALL_THRESHOLD) {
                            IconButton(onClick = {
                                scope.launch {
                                    if (showingFolders) viewModel.selectAllFolders()
                                    else viewModel.selectAll()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = stringResource(R.string.select_all)
                                )
                            }
                        }
                    } else {
                        // Long-press a tile to enter selection mode — no separate
                        // toolbar button. Keeps the bar uncluttered.
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings)
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
                        if (pagedTotal in 1..SELECT_ALL_THRESHOLD) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        if (showingFolders) viewModel.selectAllFolders()
                                        else viewModel.selectAll()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.select_all))
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val paths = if (showingFolders) {
                                        selectedFolderPaths.map {
                                            SettingsManager.applyFolderTrailingSlash(it, folderTrailingSlash)
                                        }
                                    } else {
                                        viewModel.selectedPaths()
                                    }
                                    val combined = ClipboardHelper
                                        .formatPathsForConsole(paths, multiCopyFormat)
                                    ClipboardHelper.copyToClipboard(context, combined)
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.multiple_paths_copied,
                                            paths.size
                                        )
                                    )
                                    viewModel.clearSelection()
                                }
                            },
                            enabled = selectionCount > 0,
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
                            Text(
                                stringResource(
                                    R.string.copy_selected_fmt,
                                    selectionCount,
                                    fmtTag
                                )
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data, modifier = Modifier.padding(16.dp))
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

            // The folder list is browsed by tap, not by typing — hide search there.
            if (!showingFolders) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.searchImages(it) },
                    placeholderRes = if (browseMode == BrowseMode.AllFiles)
                        R.string.search_hint_files
                    else
                        R.string.search_hint
                )
            }

            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = {
                    viewModel.refreshIndex()
                    lazyItems.refresh()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        (refreshing || isLoading) && pagedTotal == 0 -> {
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
                        isEmpty -> {
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
                        showingFolders -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    count = lazyFolders.itemCount,
                                    key = { index ->
                                        runCatching { lazyFolders.peek(index) }
                                            .getOrNull()
                                            ?.folder
                                            ?: "i:$index"
                                    }
                                ) { index ->
                                    val entry = lazyFolders[index] ?: return@items
                                    FolderGridItem(
                                        entry = entry,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleFolderSelection(entry.folder)
                                            } else {
                                                viewModel.selectFolder(entry.folder)
                                            }
                                        },
                                        onLongClick = {
                                            // Mirror the image long-press: enter selection mode,
                                            // add this folder, and copy its (settings-formatted)
                                            // path on the very first long-press.
                                            val wasEmpty = !isSelectionMode
                                            if (wasEmpty) viewModel.toggleSelectionMode(true)
                                            viewModel.toggleFolderSelection(entry.folder)
                                            if (wasEmpty) {
                                                val formatted = SettingsManager
                                                    .applyFolderTrailingSlash(entry.folder, folderTrailingSlash)
                                                ClipboardHelper.copyToClipboard(context, formatted)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(message = pathCopiedText)
                                                }
                                            }
                                        },
                                        isSelected = selectedFolderPaths.contains(entry.folder)
                                    )
                                }
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
                                items(
                                    count = pagedTotal,
                                    // Wrap in runCatching: when paging shrinks (refresh,
                                    // mode flip), Compose can call this lambda with an
                                    // index from the previous frame's count while the
                                    // ItemSnapshotList has already shrunk — peek then
                                    // throws IndexOutOfBoundsException. Falling back to
                                    // an index-derived key is safe because the lambda
                                    // will be re-keyed once the next frame settles.
                                    key = { index ->
                                        runCatching { lazyItems.peek(index) }
                                            .getOrNull()
                                            ?.let { "${it.type}:${it.id}" }
                                            ?: "i:$index"
                                    }
                                ) { index ->
                                    val image = lazyItems[index] ?: return@items
                                    ImageGridItem(
                                        image = image,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleImageSelection(image.id)
                                            } else if (image.mimeType.startsWith("image/")) {
                                                fullscreenImages = lazyItems
                                                    .itemSnapshotList.items
                                                fullscreenIndex = fullscreenImages
                                                    .indexOfFirst { it.id == image.id && it.type == image.type }
                                                    .coerceAtLeast(0)
                                            } else {
                                                ClipboardHelper.copyToClipboard(context, image.path)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = pathCopiedText
                                                    )
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            // First long-press also copies the path; subsequent
                                            // long-presses just toggle membership.
                                            val wasEmpty = !isSelectionMode
                                            if (wasEmpty) viewModel.toggleSelectionMode(true)
                                            viewModel.toggleImageSelection(image.id)
                                            if (wasEmpty) {
                                                ClipboardHelper.copyToClipboard(context, image.path)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = pathCopiedText
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseModeSelector(
    mode: BrowseMode,
    onSelect: (BrowseMode) -> Unit
) {
    val options = listOf(BrowseMode.Images, BrowseMode.AllFiles, BrowseMode.Folders)
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
                            BrowseMode.Folders -> R.string.mode_folders
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
