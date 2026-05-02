package com.imageviewer.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class MultiCopyFormat(val id: String, val separator: String, val itemPrefix: String = "") {
    /** path1 path2 path3 — Claude/Gemini/Codex auto-detection (default, terminal-safe). */
    SPACE("space", " "),

    /** path1, path2, path3 — readable but Claude Code may not auto-attach as images. */
    COMMA("comma", ", "),

    /** path1; path2; path3 — same idea, different separator. */
    SEMICOLON("semicolon", "; "),

    /** @path1 @path2 @path3 — Claude Code only; breaks Gemini CLI / Codex auto-detection. */
    AT_PREFIX("at", " ", itemPrefix = "@");

    companion object {
        val DEFAULT = SPACE
        fun fromId(id: String?): MultiCopyFormat =
            values().firstOrNull { it.id == id } ?: DEFAULT
    }
}

object SettingsManager {
    private val MULTI_COPY_FORMAT_KEY = stringPreferencesKey("multi_copy_format")
    private val FOLDER_TRAILING_SLASH_KEY = booleanPreferencesKey("folder_trailing_slash")
    private val SHARE_OPENS_VIEWER_KEY = booleanPreferencesKey("share_opens_viewer")

    fun getMultiCopyFormat(context: Context): Flow<MultiCopyFormat> =
        context.dataStore.data.map { prefs ->
            MultiCopyFormat.fromId(prefs[MULTI_COPY_FORMAT_KEY])
        }

    suspend fun setMultiCopyFormat(context: Context, format: MultiCopyFormat) {
        context.dataStore.edit { prefs ->
            prefs[MULTI_COPY_FORMAT_KEY] = format.id
        }
    }

    /** When true (default), copied folder paths end with `/`. */
    fun getFolderTrailingSlash(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[FOLDER_TRAILING_SLASH_KEY] ?: true
        }

    suspend fun setFolderTrailingSlash(context: Context, value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[FOLDER_TRAILING_SLASH_KEY] = value
        }
    }

    /** Apply the user's trailing-slash preference to a raw folder path. */
    fun applyFolderTrailingSlash(path: String, trailingSlash: Boolean): String {
        val trimmed = path.trimEnd('/')
        return if (trailingSlash) "$trimmed/" else trimmed
    }

    /**
     * When true, sharing an image into the app opens the detailed view (so the
     * user can crop/annotate/share-on); when false (default), the app just
     * copies the path to the clipboard, shows a toast, and finishes.
     */
    fun getShareOpensViewer(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[SHARE_OPENS_VIEWER_KEY] ?: false
        }

    suspend fun setShareOpensViewer(context: Context, value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHARE_OPENS_VIEWER_KEY] = value
        }
    }
}
