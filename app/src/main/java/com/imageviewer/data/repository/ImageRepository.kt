package com.imageviewer.data.repository

import android.content.ContentResolver
import com.imageviewer.data.database.ImageDao
import com.imageviewer.data.model.ImageFile
import com.imageviewer.data.model.ImageFile.Companion.TYPE_FILE
import com.imageviewer.data.model.ImageFile.Companion.TYPE_IMAGE
import com.imageviewer.util.MediaStoreScanner
import kotlinx.coroutines.flow.Flow

enum class BrowseMode { Images, AllFiles }

class ImageRepository(
    private val imageDao: ImageDao,
    contentResolver: ContentResolver
) {
    private val scanner = MediaStoreScanner(contentResolver)

    fun search(query: String, category: String, mode: BrowseMode): Flow<List<ImageFile>> {
        return when (mode) {
            BrowseMode.Images -> when {
                category == "All" && query.isBlank() -> imageDao.getAllByType(TYPE_IMAGE)
                category == "All" && query.isNotBlank() -> imageDao.searchByName(query, TYPE_IMAGE)
                category != "All" && query.isBlank() -> imageDao.getByCategory(category, TYPE_IMAGE)
                else -> imageDao.searchByNameAndCategory(query, category, TYPE_IMAGE)
            }
            BrowseMode.AllFiles -> {
                if (query.isBlank()) {
                    imageDao.getAllByType(TYPE_FILE)
                } else {
                    // Lowercase for case-insensitive GLOB; DAO lowercases column on read.
                    imageDao.searchByGlob(toGlob(query).lowercase(), TYPE_FILE)
                }
            }
        }
    }

    suspend fun scanAndIndex(mode: BrowseMode) {
        val (items, type) = when (mode) {
            BrowseMode.Images -> scanner.scanImages() to TYPE_IMAGE
            BrowseMode.AllFiles -> scanner.scanAllFiles() to TYPE_FILE
        }
        // Upsert first, then drop stale rows. With the composite (id, type) PK,
        // INSERT OR REPLACE acts as upsert. This avoids the brief empty-grid
        // flash that delete-all-then-insert produced on every refresh.
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
