package com.imageviewer.data.model

/** One row per filesystem folder containing images, used by the Folders browse mode. */
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
