package com.imageviewer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.imageviewer.data.model.ImageFile
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<ImageFile>)

    @Query("SELECT * FROM images WHERE type = :type ORDER BY dateAdded DESC")
    fun getAllByType(type: String): Flow<List<ImageFile>>

    @Query("SELECT * FROM images WHERE type = :type AND displayName LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun searchByName(query: String, type: String): Flow<List<ImageFile>>

    @Query("SELECT * FROM images WHERE type = :type AND category = :category ORDER BY dateAdded DESC")
    fun getByCategory(category: String, type: String): Flow<List<ImageFile>>

    @Query("SELECT * FROM images WHERE type = :type AND category = :category AND displayName LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun searchByNameAndCategory(query: String, category: String, type: String): Flow<List<ImageFile>>

    @Query("SELECT * FROM images WHERE type = :type AND LOWER(displayName) GLOB :pattern ORDER BY dateAdded DESC")
    fun searchByGlob(pattern: String, type: String): Flow<List<ImageFile>>

    @Query("DELETE FROM images WHERE type = :type")
    suspend fun deleteAllByType(type: String)
}
