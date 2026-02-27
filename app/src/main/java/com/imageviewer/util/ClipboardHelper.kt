package com.imageviewer.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.imageviewer.R

object ClipboardHelper {
    fun copyToClipboard(context: Context, text: String, label: String = "Image Path") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)

        // Show fading message
        Toast.makeText(context, context.getString(R.string.path_copied), Toast.LENGTH_SHORT).show()
    }
}
