package com.imageviewer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imageviewer.data.database.ImageDatabase
import com.imageviewer.data.model.ImageFile
import com.imageviewer.data.repository.ImageRepository
import kotlinx.coroutines.FlowPreview
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

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedImageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedImageIds: StateFlow<Set<Long>> = _selectedImageIds.asStateFlow()

    val images: StateFlow<List<ImageFile>>

    init {
        val database = ImageDatabase.getDatabase(application)
        repository = ImageRepository(database.imageDao(), application.contentResolver)

        // Set up search flow with debouncing and category filtering
        images = combine(
            _searchQuery.debounce(300).distinctUntilChanged(),
            _selectedCategory
        ) { query, category ->
            Pair(query, category)
        }
            .flatMapLatest { (query, category) ->
                repository.searchImages(query, category)
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
                repository.scanAndIndexImages()
            } catch (e: Exception) {
                android.util.Log.e("ImageViewModel", "Error loading images", e)
                // Re-throw to trigger crash handler
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

    fun refreshIndex() {
        loadImages()
    }
}
