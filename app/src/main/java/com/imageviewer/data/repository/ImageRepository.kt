package com.imageviewer.data.repository

import android.content.ContentResolver
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.imageviewer.data.database.ImageDao
import com.imageviewer.data.model.FolderEntry
import com.imageviewer.data.model.ImageFile
import com.imageviewer.data.model.ImageFile.Companion.TYPE_FILE
import com.imageviewer.data.model.ImageFile.Companion.TYPE_IMAGE
import com.imageviewer.util.MediaStoreScanner
import kotlinx.coroutines.flow.Flow

enum class BrowseMode { Images, AllFiles, Folders }

class ImageRepository(
    private val imageDao: ImageDao,
    contentResolver: ContentResolver
) {
    private val scanner = MediaStoreScanner(contentResolver)

    private val gridConfig = PagingConfig(
        pageSize = 60,
        prefetchDistance = 60,
        enablePlaceholders = false,
        initialLoadSize = 120
    )

    /**
     * Paginated images stream. When [folder] is non-null, results are restricted
     * to that folder (used by Folders mode after the user drills into one).
     */
    fun searchPaged(
        query: String,
        category: String,
        mode: BrowseMode,
        folder: String? = null
    ): Flow<PagingData<ImageFile>> =
        Pager(gridConfig) { sourceFor(query, category, mode, folder) }.flow

    private fun sourceFor(query: String, category: String, mode: BrowseMode, folder: String?) =
        when {
            folder != null -> {
                val type = if (mode == BrowseMode.AllFiles) TYPE_FILE else TYPE_IMAGE
                if (query.isBlank()) imageDao.pagedByFolder(folder, type)
                else imageDao.pagedByFolderAndName(folder, query, type)
            }
            mode == BrowseMode.Images -> when {
                category == "All" && query.isBlank() -> imageDao.pagedAllByType(TYPE_IMAGE)
                category == "All" && query.isNotBlank() -> imageDao.pagedSearchByName(query, TYPE_IMAGE)
                category != "All" && query.isBlank() -> imageDao.pagedByCategory(category, TYPE_IMAGE)
                else -> imageDao.pagedSearchByNameAndCategory(query, category, TYPE_IMAGE)
            }
            mode == BrowseMode.AllFiles -> {
                if (query.isBlank()) imageDao.pagedAllByType(TYPE_FILE)
                else imageDao.pagedSearchByGlob(toGlob(query).lowercase(), TYPE_FILE)
            }
            else -> imageDao.pagedAllByType(TYPE_IMAGE) // Folders without folder = handled separately
        }

    /** Folder list for Folders mode (one row per directory containing images). */
    fun foldersPaged(): Flow<PagingData<FolderEntry>> =
        Pager(gridConfig) { imageDao.pagedFolders(TYPE_IMAGE) }.flow

    /** Distinct folder paths — used by Folders-mode Select All. */
    suspend fun allFolders(): List<String> = imageDao.listAllFolders(TYPE_IMAGE)

    /** All matching ids for the current filter. Used by Select All. */
    suspend fun matchingIds(
        query: String,
        category: String,
        mode: BrowseMode,
        folder: String? = null
    ): List<Long> = when {
        folder != null -> {
            val type = if (mode == BrowseMode.AllFiles) TYPE_FILE else TYPE_IMAGE
            if (query.isBlank()) imageDao.listIdsByFolder(folder, type)
            else imageDao.listIdsByFolderAndName(folder, query, type)
        }
        mode == BrowseMode.Images -> when {
            category == "All" && query.isBlank() -> imageDao.listIdsAllByType(TYPE_IMAGE)
            category == "All" && query.isNotBlank() -> imageDao.listIdsSearchByName(query, TYPE_IMAGE)
            category != "All" && query.isBlank() -> imageDao.listIdsByCategory(category, TYPE_IMAGE)
            else -> imageDao.listIdsSearchByNameAndCategory(query, category, TYPE_IMAGE)
        }
        mode == BrowseMode.AllFiles -> {
            if (query.isBlank()) imageDao.listIdsAllByType(TYPE_FILE)
            else imageDao.listIdsSearchByGlob(toGlob(query).lowercase(), TYPE_FILE)
        }
        else -> emptyList()
    }

    /** Resolve a selection (set of ids) back to absolute filesystem paths. */
    suspend fun pathsForIds(ids: Collection<Long>, mode: BrowseMode): List<String> {
        if (ids.isEmpty()) return emptyList()
        val type = if (mode == BrowseMode.AllFiles) TYPE_FILE else TYPE_IMAGE
        return imageDao.listPathsForIds(ids.toList(), type)
    }

    /** Look up a single ImageFile by absolute path within the current mode. */
    suspend fun findByPath(path: String, mode: BrowseMode): ImageFile? {
        val type = if (mode == BrowseMode.AllFiles) TYPE_FILE else TYPE_IMAGE
        return imageDao.findByPath(path, type)
    }

    suspend fun scanAndIndex(mode: BrowseMode) {
        // Folders mode reuses the image index; nothing to scan separately.
        val (items, type) = when (mode) {
            BrowseMode.Images, BrowseMode.Folders -> scanner.scanImages() to TYPE_IMAGE
            BrowseMode.AllFiles -> scanner.scanAllFiles() to TYPE_FILE
        }
        imageDao.insertAll(items)
        imageDao.deleteStale(type, items.map { it.id })
    }

    /**
     * Convert a user-typed wildcard query into a SQLite GLOB pattern.
     * Bare term "report" -> "*report*" (substring match);
     * a query that already contains * or ? is passed through verbatim.
     */
    private fun toGlob(query: String): String {
        val trimmed = query.trim()
        return if (trimmed.contains('*') || trimmed.contains('?')) trimmed else "*$trimmed*"
    }
}
