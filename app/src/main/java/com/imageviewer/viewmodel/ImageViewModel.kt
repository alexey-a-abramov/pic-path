package com.imageviewer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.imageviewer.data.database.ImageDatabase
import com.imageviewer.data.model.ImageFile
import com.imageviewer.data.model.NavigableFolderEntry
import com.imageviewer.data.repository.BrowseMode
import com.imageviewer.data.repository.ImageRepository
import com.imageviewer.util.FolderTree
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedImageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedImageIds: StateFlow<Set<Long>> = _selectedImageIds.asStateFlow()

    private val _selectedFolderPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolderPaths: StateFlow<Set<String>> = _selectedFolderPaths.asStateFlow()

    /** Where the user is in the folder tree (null = the tree's anchor root). */
    private val _currentFolderPath = MutableStateFlow<String?>(null)
    val currentFolderPath: StateFlow<String?> = _currentFolderPath.asStateFlow()

    /** Paginated image/file rows for Images and Files modes. */
    val pagedImages: Flow<PagingData<ImageFile>>

    /**
     * Hot snapshot of the folder tree. Folders mode UI derives breadcrumbs and
     * the visible tile list from this; the snapshot is small enough to fit in
     * memory comfortably (typical libraries: tens of leaf folders).
     */
    val folderTree: StateFlow<FolderTree?>

    init {
        val database = ImageDatabase.getDatabase(application)
        repository = ImageRepository(database.imageDao(), application.contentResolver)

        pagedImages = combine(
            _searchQuery.debounce(300).distinctUntilChanged(),
            _selectedCategory,
            _browseMode
        ) { query, category, mode -> Triple(query, category, mode) }
            .flatMapLatest { (query, category, mode) ->
                repository.searchPaged(query, category, mode)
            }
            .cachedIn(viewModelScope)

        folderTree = repository.folderTree()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )
    }

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
        _currentFolderPath.value = null
        clearSelection()
        loadImages()
    }

    /** Drill into a sub-folder (or jump to a breadcrumb segment). */
    fun navigateToFolder(path: String?) {
        _currentFolderPath.value = path
        _searchQuery.value = ""
        clearSelection()
    }

    fun toggleSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) clearSelection()
    }

    fun toggleImageSelection(imageId: Long) {
        val current = _selectedImageIds.value.toMutableSet()
        if (current.contains(imageId)) current.remove(imageId) else current.add(imageId)
        _selectedImageIds.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun toggleFolderSelection(folderPath: String) {
        val current = _selectedFolderPaths.value.toMutableSet()
        if (current.contains(folderPath)) current.remove(folderPath) else current.add(folderPath)
        _selectedFolderPaths.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun clearSelection() {
        _selectedImageIds.value = emptySet()
        _selectedFolderPaths.value = emptySet()
        _isSelectionMode.value = false
    }

    suspend fun selectedPaths(): List<String> {
        val ids = _selectedImageIds.value
        return repository.pathsForIds(ids, _browseMode.value)
    }

    suspend fun selectAll() {
        val mode = _browseMode.value
        val category = _selectedCategory.value
        val query = _searchQuery.value
        val ids = repository.matchingIds(query, category, mode)
        _isSelectionMode.value = true
        _selectedImageIds.value = ids.toSet()
    }

    /** Folders-mode Select All — every visible entry at the current location
     *  (or every search match when search is active). */
    fun selectAllFolders(visiblePaths: List<String>) {
        if (visiblePaths.isEmpty()) return
        _isSelectionMode.value = true
        _selectedFolderPaths.value = visiblePaths.toSet()
    }

    suspend fun findByPath(path: String): ImageFile? =
        repository.findByPath(path, _browseMode.value)

    fun refreshIndex() {
        loadImages()
    }
}
