package com.imageviewer.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.imageviewer.R
import java.io.File

object ClipboardHelper {
    fun copyToClipboard(context: Context, text: String, label: String = "Image Path") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // Create ClipData with both plain text (the path) and URI (the image file)
        val file = File(text)
        if (file.exists()) {
            val contentUri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: Exception) {
                Uri.fromFile(file)
            }
            
            val clip = ClipData.newUri(context.contentResolver, label, contentUri)
            // Add the plain text path as well
            clip.addItem(ClipData.Item(text))
            clipboard.setPrimaryClip(clip)
        } else {
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
        }

        // Show fading message
        Toast.makeText(context, context.getString(R.string.path_copied), Toast.LENGTH_SHORT).show()
    }
}
