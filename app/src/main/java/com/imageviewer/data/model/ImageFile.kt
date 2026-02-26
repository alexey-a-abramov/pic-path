package com.imageviewer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageFile(
    @PrimaryKey
    val id: Long,
    val displayName: String,
    val uri: String,
    val path: String,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String,
    val category: String = "All"
)
