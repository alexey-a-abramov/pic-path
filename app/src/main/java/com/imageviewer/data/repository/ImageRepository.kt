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
import com.imageviewer.util.FolderTree
import com.imageviewer.util.MediaStoreScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    /** Paginated images stream for Images and Files modes. */
    fun searchPaged(
        query: String,
        category: String,
        mode: BrowseMode
    ): Flow<PagingData<ImageFile>> =
        Pager(gridConfig) { sourceFor(query, category, mode) }.flow

    private fun sourceFor(query: String, category: String, mode: BrowseMode) =
        when (mode) {
            BrowseMode.Images -> when {
                category == "All" && query.isBlank() -> imageDao.pagedAllByType(TYPE_IMAGE)
                category == "All" && query.isNotBlank() -> imageDao.pagedSearchByName(query, TYPE_IMAGE)
                category != "All" && query.isBlank() -> imageDao.pagedByCategory(category, TYPE_IMAGE)
                else -> imageDao.pagedSearchByNameAndCategory(query, category, TYPE_IMAGE)
            }
            BrowseMode.AllFiles -> {
                if (query.isBlank()) imageDao.pagedAllByType(TYPE_FILE)
                else imageDao.pagedSearchByGlob(toGlob(query).lowercase(), TYPE_FILE)
            }
            BrowseMode.Folders -> imageDao.pagedAllByType(TYPE_IMAGE) // unused — folders use foldersPaged
        }

    /** Hierarchical folder tree, rebuilt whenever the underlying leaf-folder
     *  set changes (i.e. on rescan). Folders mode reads from this. */
    fun folderTree(): Flow<FolderTree> =
        imageDao.foldersSnapshot(TYPE_IMAGE).map { FolderTree.build(it) }

    /** All matching ids for the current filter. Used by Select All in image modes. */
    suspend fun matchingIds(
        query: String,
        category: String,
        mode: BrowseMode
    ): List<Long> = when (mode) {
        BrowseMode.Images -> when {
            category == "All" && query.isBlank() -> imageDao.listIdsAllByType(TYPE_IMAGE)
            category == "All" && query.isNotBlank() -> imageDao.listIdsSearchByName(query, TYPE_IMAGE)
            category != "All" && query.isBlank() -> imageDao.listIdsByCategory(category, TYPE_IMAGE)
            else -> imageDao.listIdsSearchByNameAndCategory(query, category, TYPE_IMAGE)
        }
        BrowseMode.AllFiles -> {
            if (query.isBlank()) imageDao.listIdsAllByType(TYPE_FILE)
            else imageDao.listIdsSearchByGlob(toGlob(query).lowercase(), TYPE_FILE)
        }
        BrowseMode.Folders -> emptyList() // folders use allFolders()
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
