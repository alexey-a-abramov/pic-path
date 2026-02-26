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

    @Query("SELECT * FROM images WHERE displayName LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun searchByName(query: String): Flow<List<ImageFile>>

    @Query("SELECT * FROM images WHERE category = :category AND displayName LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun searchByNameAndCategory(query: String, category: String): Flow<List<ImageFile>>

    @Query("SELECT * FROM images ORDER BY dateAdded DESC")
    fun getAll(): Flow<List<ImageFile>>

    @Query("SELECT * FROM images WHERE category = :category ORDER BY dateAdded DESC")
    fun getByCategory(category: String): Flow<List<ImageFile>>

    @Query("DELETE FROM images")
    suspend fun deleteAll()
}
