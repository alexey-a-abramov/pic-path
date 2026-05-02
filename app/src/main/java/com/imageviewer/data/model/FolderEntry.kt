package com.imageviewer.data.model

/** One row per filesystem folder containing images, as returned by the DAO. */
data class FolderEntry(
    val folder: String,
    val count: Int,
    val sampleUri: String,
    val sampleMimeType: String,
    val lastModified: Long
) {
    /** Display name for the folder = its last path segment. */
    val name: String get() = folder.substringAfterLast('/').ifBlank { folder }
}

/**
 * A single tile in the hierarchical Folders view. May represent an interior
 * directory (one that contains sub-directories) or a leaf one (only images).
 */
data class NavigableFolderEntry(
    val path: String,
    val name: String,
    val hasChildren: Boolean,
    val directImageCount: Int,
    val totalImageCount: Int,
    val sampleUri: String?,
    val sampleMimeType: String?
)
