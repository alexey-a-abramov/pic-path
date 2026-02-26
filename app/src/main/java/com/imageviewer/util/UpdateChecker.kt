package com.imageviewer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.imageviewer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val html_url: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long
)

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String?,
    val releaseNotes: String?,
    val releaseUrl: String?
)

object UpdateChecker {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val currentVersion = BuildConfig.VERSION_NAME
            val repo = BuildConfig.GITHUB_REPO
            val url = "https://api.github.com/repos/$repo/releases/latest"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateInfo(
                    isUpdateAvailable = false,
                    latestVersion = currentVersion,
                    currentVersion = currentVersion,
                    downloadUrl = null,
                    releaseNotes = null,
                    releaseUrl = null
                )
            }

            val body = response.body?.string() ?: return@withContext UpdateInfo(
                isUpdateAvailable = false,
                latestVersion = currentVersion,
                currentVersion = currentVersion,
                downloadUrl = null,
                releaseNotes = null,
                releaseUrl = null
            )

            val release = json.decodeFromString<GitHubRelease>(body)
            val latestVersion = release.tag_name.removePrefix("v")

            // Find APK asset
            val apkAsset = release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true)
            }

            val isNewer = compareVersions(latestVersion, currentVersion) > 0

            UpdateInfo(
                isUpdateAvailable = isNewer,
                latestVersion = latestVersion,
                currentVersion = currentVersion,
                downloadUrl = apkAsset?.browser_download_url,
                releaseNotes = release.body,
                releaseUrl = release.html_url
            )
        } catch (e: Exception) {
            e.printStackTrace()
            UpdateInfo(
                isUpdateAvailable = false,
                latestVersion = BuildConfig.VERSION_NAME,
                currentVersion = BuildConfig.VERSION_NAME,
                downloadUrl = null,
                releaseNotes = null,
                releaseUrl = null
            )
        }
    }

    suspend fun downloadUpdate(context: Context, downloadUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val fileName = "pic-path-update.apk"
            val downloadDir = context.getExternalFilesDir(null)
            val file = File(downloadDir, fileName)

            response.body?.byteStream()?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun installApk(context: Context, file: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val v1 = parts1.getOrNull(i) ?: 0
            val v2 = parts2.getOrNull(i) ?: 0

            if (v1 > v2) return 1
            if (v1 < v2) return -1
        }

        return 0
    }
}
