package com.imageviewer.data.repository

import android.content.ContentResolver
import com.imageviewer.data.database.ImageDao
import com.imageviewer.data.model.ImageFile
import com.imageviewer.util.MediaStoreScanner
import kotlinx.coroutines.flow.Flow

class ImageRepository(
    private val imageDao: ImageDao,
    private val contentResolver: ContentResolver
) {
    private val scanner = MediaStoreScanner(contentResolver)

    fun getAllImages(): Flow<List<ImageFile>> {
        return imageDao.getAll()
    }

    fun searchImages(query: String, category: String = "All"): Flow<List<ImageFile>> {
        return when {
            category == "All" && query.isBlank() -> imageDao.getAll()
            category == "All" && query.isNotBlank() -> imageDao.searchByName(query)
            category != "All" && query.isBlank() -> imageDao.getByCategory(category)
            else -> imageDao.searchByNameAndCategory(query, category)
        }
    }

    suspend fun scanAndIndexImages() {
        val images = scanner.scanImages()
        imageDao.deleteAll()
        imageDao.insertAll(images)
    }
}
