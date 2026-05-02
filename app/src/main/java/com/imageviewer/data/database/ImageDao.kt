package com.imageviewer.data.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.imageviewer.data.model.FolderEntry
import com.imageviewer.data.model.ImageFile

@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<ImageFile>)

    // ---- Paging variants for the grid. Room generates the PagingSource impls;
    // these never load the whole result set into a single List, so they don't
    // trip the CursorWindow race when the previous query is being cancelled. ----

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

    /**
     * One row per folder containing images of [type]. The sample URI/mimeType
     * come from the folder's most recently added file (used as a thumbnail).
     */
    @Query("""
        SELECT
            folder AS folder,
            COUNT(*) AS count,
            (SELECT uri      FROM images i2 WHERE i2.type = images.type AND i2.folder = images.folder ORDER BY dateAdded DESC LIMIT 1) AS sampleUri,
            (SELECT mimeType FROM images i2 WHERE i2.type = images.type AND i2.folder = images.folder ORDER BY dateAdded DESC LIMIT 1) AS sampleMimeType,
            MAX(dateAdded) AS lastModified
        FROM images
        WHERE type = :type AND folder != ''
        GROUP BY folder
        ORDER BY lastModified DESC
    """)
    fun pagedFolders(type: String): PagingSource<Int, FolderEntry>

    /**
     * Folders whose path matches the given LIKE pattern (case-insensitive).
     * Used by the search bar in Folders mode.
     */
    @Query("""
        SELECT
            folder AS folder,
            COUNT(*) AS count,
            (SELECT uri      FROM images i2 WHERE i2.type = images.type AND i2.folder = images.folder ORDER BY dateAdded DESC LIMIT 1) AS sampleUri,
            (SELECT mimeType FROM images i2 WHERE i2.type = images.type AND i2.folder = images.folder ORDER BY dateAdded DESC LIMIT 1) AS sampleMimeType,
            MAX(dateAdded) AS lastModified
        FROM images
        WHERE type = :type AND folder != '' AND LOWER(folder) LIKE '%' || :query || '%'
        GROUP BY folder
        ORDER BY lastModified DESC
    """)
    fun pagedFoldersSearch(query: String, type: String): PagingSource<Int, FolderEntry>

    /** Distinct folder paths for [type], used by Folders-mode Select All. */
    @Query("SELECT DISTINCT folder FROM images WHERE type = :type AND folder != '' ORDER BY folder")
    suspend fun listAllFolders(type: String): List<String>

    @Query("SELECT DISTINCT folder FROM images WHERE type = :type AND folder != '' AND LOWER(folder) LIKE '%' || :query || '%' ORDER BY folder")
    suspend fun listAllFoldersMatching(query: String, type: String): List<String>

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
