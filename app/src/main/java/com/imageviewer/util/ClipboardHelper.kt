package com.imageviewer.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.imageviewer.R

object ClipboardHelper {
    /**
     * Copies [text] to the clipboard as plain text only.
     *
     * Why plain-text only: pasting into a terminal prompt (Claude Code / Gemini CLI / Codex
     * on Termux) is the entire workflow this app exists for. Those CLIs auto-detect
     * unquoted absolute paths and attach them as images. A `content://` URI primary clip
     * pastes the URI string instead, breaking auto-detection.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "Image Path") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, context.getString(R.string.path_copied), Toast.LENGTH_SHORT).show()
    }

    /**
     * Copies an image as content (not as a path) so paste targets that understand
     * image clipboard items receive an actual bitmap reference.
     *
     * The URI must be a `content://` URI readable by other apps — typically a
     * MediaStore.Images URI. Filesystem paths or unscoped FileProvider URIs do NOT
     * work here: the receiving app would lack the read grant and silently fail.
     *
     * Coexists with [copyToClipboard]: this is a separate user action ("Copy Image")
     * surfaced alongside the path-copy action. The terminal/CLI workflow keeps using
     * plain-text path copies; this is for image-aware destinations (chats, docs,
     * paint apps, etc.).
     */
    fun copyImageToClipboard(
        context: Context,
        uri: Uri,
        mimeType: String = "image/*",
        label: String = "Image"
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData(
            ClipDescription(label, arrayOf(mimeType)),
            ClipData.Item(uri)
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.image_copied), Toast.LENGTH_SHORT).show()
    }

    /**
     * Single-line, separator-joined absolute paths so pasting into a terminal prompt
     * doesn't trigger Enter. Newlines are intentionally never used here regardless of
     * [format] — they auto-submit in Termux.
     */
    fun formatPathsForConsole(
        paths: List<String>,
        format: MultiCopyFormat = MultiCopyFormat.DEFAULT
    ): String =
        paths.joinToString(separator = format.separator) { "${format.itemPrefix}$it" }
}
