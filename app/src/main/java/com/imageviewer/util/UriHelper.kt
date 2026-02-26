package com.imageviewer.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns

object UriHelper {
    fun getPathFromUri(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> getPathFromContentUri(context, uri)
            "file" -> uri.path
            else -> null
        }
    }

    private fun getPathFromContentUri(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            // If we can't get the path, try to get the display name
            return getDisplayName(context.contentResolver, uri)
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun getDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return cursor.getString(displayNameIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return uri.toString()
    }
}
