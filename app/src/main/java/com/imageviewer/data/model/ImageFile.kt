package com.imageviewer.data.model

import androidx.room.Entity

@Entity(tableName = "images", primaryKeys = ["id", "type"])
data class ImageFile(
    val id: Long,
    val displayName: String,
    val uri: String,
    val path: String,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String,
    val category: String = "All",
    val type: String = TYPE_IMAGE
) {
    companion object {
        const val TYPE_IMAGE = "image"
        const val TYPE_FILE = "file"
    }
}
