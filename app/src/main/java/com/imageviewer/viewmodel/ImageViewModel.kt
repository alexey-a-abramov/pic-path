package com.imageviewer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.imageviewer.data.database.ImageDatabase
import com.imageviewer.data.model.FolderEntry
import com.imageviewer.data.model.ImageFile
import com.imageviewer.data.repository.BrowseMode
import com.imageviewer.data.repository.ImageRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ImageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ImageRepository

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Screenshots")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _browseMode = MutableStateFlow(BrowseMode.Images)
    val browseMode: StateFlow<BrowseMode> = _browseMode.asStateFlow()

    /** When non-null in Folders mode, the grid shows images inside this folder. */
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedImageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedImageIds: StateFlow<Set<Long>> = _selectedImageIds.asStateFlow()

    /** Paginated grid stream for image/file rows (used in all modes except
     *  Folders-without-selection, which uses pagedFolders below). */
    val pagedImages: Flow<PagingData<ImageFile>>

    /** Paginated stream of folder entries for Folders mode top level. */
    val pagedFolders: Flow<PagingData<FolderEntry>>

    init {
        val database = ImageDatabase.getDatabase(application)
        repository = ImageRepository(database.imageDao(), application.contentResolver)

        pagedImages = combine(
            _searchQuery.debounce(300).distinctUntilChanged(),
            _selectedCategory,
            _browseMode,
            _selectedFolder
        ) { query, category, mode, folder ->
            QueryArgs(query, category, mode, folder)
        }
            .flatMapLatest { (query, category, mode, folder) ->
                repository.searchPaged(query, category, mode, folder)
            }
            .cachedIn(viewModelScope)

        pagedFolders = repository.foldersPaged().cachedIn(viewModelScope)
    }

    private data class QueryArgs(
        val query: String,
        val category: String,
        val mode: BrowseMode,
        val folder: String?
    )

    fun loadImages() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.scanAndIndex(_browseMode.value)
            } catch (e: Exception) {
                android.util.Log.e("ImageViewModel", "Error loading images", e)
                throw e
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchImages(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        clearSelection()
    }

    fun selectBrowseMode(mode: BrowseMode) {
        if (_browseMode.value == mode) return
        _browseMode.value = mode
        _searchQuery.value = ""
        _selectedFolder.value = null
        clearSelection()
        loadImages()
    }

    fun selectFolder(folder: String?) {
        _selectedFolder.value = folder
        _searchQuery.value = ""
        clearSelection()
    }

    fun toggleSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) {
            clearSelection()
        }
    }

    fun toggleImageSelection(imageId: Long) {
        val current = _selectedImageIds.value.toMutableSet()
        if (current.contains(imageId)) current.remove(imageId) else current.add(imageId)
        _selectedImageIds.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun clearSelection() {
        _selectedImageIds.value = emptySet()
        _isSelectionMode.value = false
    }

    /** Fetches paths for the current selection. The query/category/mode/folder
     *  used for resolution is captured at call-time so a category change mid-call
     *  doesn't change the row set under us. */
    suspend fun selectedPaths(): List<String> {
        val ids = _selectedImageIds.value
        return repository.pathsForIds(ids, _browseMode.value)
    }

    /** Selects every row matching the *current* filter. Captures the filter args
     *  at call-time for the same reason as selectedPaths(). */
    suspend fun selectAll() {
        val mode = _browseMode.value
        val category = _selectedCategory.value
        val query = _searchQuery.value
        val folder = _selectedFolder.value
        val ids = repository.matchingIds(query, category, mode, folder)
        _isSelectionMode.value = true
        _selectedImageIds.value = ids.toSet()
    }

    /** Look up the [ImageFile] for a given absolute path in the current mode. */
    suspend fun findByPath(path: String): ImageFile? =
        repository.findByPath(path, _browseMode.value)

    fun refreshIndex() {
        loadImages()
    }
}
