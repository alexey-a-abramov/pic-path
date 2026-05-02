package com.imageviewer.util

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.imageviewer.data.model.ImageFile
import com.imageviewer.data.model.ImageFile.Companion.TYPE_FILE
import com.imageviewer.data.model.ImageFile.Companion.TYPE_IMAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(private val contentResolver: ContentResolver) {

    private fun getCategoryFromPath(path: String): String {
        return when {
            path.contains("/Screenshots", ignoreCase = true) -> "Screenshots"
            path.contains("/Camera", ignoreCase = true) || path.contains("/DCIM", ignoreCase = true) -> "Camera"
            path.contains("/Download", ignoreCase = true) -> "Downloads"
            else -> "Other"
        }
    }

    suspend fun scanImages(): List<ImageFile> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageFile>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )

        val selection = null
        val selectionArgs = null
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn) ?: ""
                val path = cursor.getString(dataColumn) ?: ""
                val dateAdded = cursor.getLong(dateAddedColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: ""

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val category = getCategoryFromPath(path)

                images.add(
                    ImageFile(
                        id = id,
                        displayName = displayName,
                        uri = contentUri.toString(),
                        path = path,
                        dateAdded = dateAdded,
                        size = size,
                        mimeType = mimeType,
                        category = category,
                        type = TYPE_IMAGE,
                        folder = path.substringBeforeLast('/', "")
                    )
                )
            }
        }

        images
    }

    suspend fun scanAllFiles(): List<ImageFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<ImageFile>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        // No MEDIA_TYPE constant for directories; filter zero-byte rows below to skip dir entries.
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val path = cursor.getString(dataColumn) ?: continue
                val size = cursor.getLong(sizeColumn)
                if (size <= 0L) continue
                val date = cursor.getLong(dateColumn)
                val mime = cursor.getString(mimeColumn) ?: ""

                val contentUri = ContentUris.withAppendedId(collection, id)

                files.add(
                    ImageFile(
                        id = id,
                        displayName = name,
                        uri = contentUri.toString(),
                        path = path,
                        dateAdded = date,
                        size = size,
                        mimeType = mime,
                        category = "All",
                        type = TYPE_FILE,
                        folder = path.substringBeforeLast('/', "")
                    )
                )
            }
        }

        files
    }
}
