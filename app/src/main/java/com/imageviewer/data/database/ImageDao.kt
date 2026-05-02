package com.imageviewer.data.database

import androidx.paging.PagingSource
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

    // ---- Legacy Flow<List<>> queries (kept until callers migrate) ----

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

    // ---- Paging variants (Room generates the PagingSource impls; these never
    // load the whole result set into a single List, so they don't trip the
    // CursorWindow race when the previous query is being cancelled.) ----

    @Query("SELECT * FROM images WHERE type = :type ORDER BY dateAdded DESC")
    fun pagedAllByType(type: String): PagingSource<Int, ImageFile>

    @Query("SELECT * FROM images WHERE type = :type AND displayName LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun pagedSearchByName(query: String, type: String): PagingSource<Int, ImageFile>

    @Query("SELECT * FROM images WHERE type = :type AND category = :category ORDER BY dateAdded DESC")
    fun pagedByCategory(category: String, type: String): PagingSource<Int, ImageFile>

    @Query("SELECT * FROM images WHERE type = :type AND category = :category AND displayName LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun pagedSearchByNameAndCategory(query: String, category: String, type: String): PagingSource<Int, ImageFile>

    @Query("SELECT * FROM images WHERE type = :type AND LOWER(displayName) GLOB :pattern ORDER BY dateAdded DESC")
    fun pagedSearchByGlob(pattern: String, type: String): PagingSource<Int, ImageFile>

    // ---- Bulk-selection helpers (used by Select All and the multi-copy button).
    // These return one row per match but only the columns we need, so the
    // result set is small enough to materialize in a single suspend call. ----

    @Query("SELECT id FROM images WHERE type = :type ORDER BY dateAdded DESC")
    suspend fun listIdsAllByType(type: String): List<Long>

    @Query("SELECT id FROM images WHERE type = :type AND displayName LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    suspend fun listIdsSearchByName(query: String, type: String): List<Long>

    @Query("SELECT id FROM images WHERE type = :type AND category = :category ORDER BY dateAdded DESC")
    suspend fun listIdsByCategory(category: String, type: String): List<Long>

    @Query("SELECT id FROM images WHERE type = :type AND category = :category AND displayName LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    suspend fun listIdsSearchByNameAndCategory(query: String, category: String, type: String): List<Long>

    @Query("SELECT id FROM images WHERE type = :type AND LOWER(displayName) GLOB :pattern ORDER BY dateAdded DESC")
    suspend fun listIdsSearchByGlob(pattern: String, type: String): List<Long>

    @Query("SELECT path FROM images WHERE type = :type AND id IN (:ids) ORDER BY dateAdded DESC")
    suspend fun listPathsForIds(ids: List<Long>, type: String): List<String>

    @Query("SELECT * FROM images WHERE type = :type AND id = :id LIMIT 1")
    suspend fun findById(id: Long, type: String): ImageFile?

    @Query("SELECT * FROM images WHERE type = :type AND path = :path LIMIT 1")
    suspend fun findByPath(path: String, type: String): ImageFile?

    @Query("DELETE FROM images WHERE type = :type")
    suspend fun deleteAllByType(type: String)

    @Query("DELETE FROM images WHERE type = :type AND id NOT IN (:keepIds)")
    suspend fun deleteStale(type: String, keepIds: List<Long>)
}
