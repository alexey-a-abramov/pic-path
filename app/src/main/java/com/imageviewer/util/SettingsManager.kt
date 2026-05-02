package com.imageviewer.util

import android.content.Context
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

    fun getMultiCopyFormat(context: Context): Flow<MultiCopyFormat> =
        context.dataStore.data.map { prefs ->
            MultiCopyFormat.fromId(prefs[MULTI_COPY_FORMAT_KEY])
        }

    suspend fun setMultiCopyFormat(context: Context, format: MultiCopyFormat) {
        context.dataStore.edit { prefs ->
            prefs[MULTI_COPY_FORMAT_KEY] = format.id
        }
    }
}
