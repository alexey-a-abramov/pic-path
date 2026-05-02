package com.imageviewer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imageviewer.data.database.ImageDatabase
import com.imageviewer.data.model.ImageFile
import com.imageviewer.data.repository.BrowseMode
import com.imageviewer.data.repository.ImageRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
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

    val images: StateFlow<List<ImageFile>>

    init {
        val database = ImageDatabase.getDatabase(application)
        repository = ImageRepository(database.imageDao(), application.contentResolver)

        images = combine(
            _searchQuery.debounce(300).distinctUntilChanged(),
            _selectedCategory,
            _browseMode
        ) { query, category, mode -> Triple(query, category, mode) }
            .flatMapLatest { (query, category, mode) ->
                repository.search(query, category, mode)
                    // Read the cursor on IO so the UI thread is never the
                    // one walking the CursorWindow.
                    .flowOn(Dispatchers.IO)
                    // Room's underlying cursor races with flatMapLatest cancellation
                    // (and with deleteStale running concurrently with scanAndIndex):
                    // it can throw IllegalStateException("Couldn't read row N, col 0")
                    // mid-iteration. The next emission (debounced search, mode flip,
                    // or the post-scan flow refresh) will repopulate, so swallow
                    // the race and emit empty rather than crash.
                    .catch { e ->
                        android.util.Log.w("ImageViewModel", "search flow failed; emitting empty", e)
                        emit(emptyList())
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
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
        clearSelection()
        loadImages()
    }

    fun toggleSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) {
            clearSelection()
        }
    }

    fun toggleImageSelection(imageId: Long) {
        val current = _selectedImageIds.value.toMutableSet()
        if (current.contains(imageId)) {
            current.remove(imageId)
        } else {
            current.add(imageId)
        }
        _selectedImageIds.value = current

        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun clearSelection() {
        _selectedImageIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun getSelectedPaths(): List<String> {
        val selectedIds = _selectedImageIds.value
        return images.value.filter { it.id in selectedIds }.map { it.path }
    }

    fun selectAll() {
        _isSelectionMode.value = true
        _selectedImageIds.value = images.value.map { it.id }.toSet()
    }

    fun refreshIndex() {
        loadImages()
    }
}
