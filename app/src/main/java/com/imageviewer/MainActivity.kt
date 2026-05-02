package com.imageviewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.imageviewer.data.model.ImageFile
import com.imageviewer.ui.FullscreenImageViewer
import com.imageviewer.ui.ImageViewerApp
import com.imageviewer.util.ClipboardHelper
import com.imageviewer.util.LanguageManager
import com.imageviewer.util.SettingsManager
import com.imageviewer.util.UriHelper
import com.imageviewer.viewmodel.ImageViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val viewModel: ImageViewModel by viewModels()
    private var hasPermission by mutableStateOf(false)
    private var permissionDenied by mutableStateOf(false)
    private var sharedImageUri by mutableStateOf<Uri?>(null)
    private var sharedImagePath by mutableStateOf<String?>(null)
    private var sharedImageMime by mutableStateOf<String?>(null)
    private var hasResumedOnce = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasPermission = true
            permissionDenied = false
            viewModel.loadImages()
        } else {
            permissionDenied = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Apply saved language
            lifecycleScope.launch {
                try {
                    val language = LanguageManager.getSelectedLanguage(this@MainActivity).first()
                    LanguageManager.applyLanguage(language)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error applying language", e)
                }
            }

            handleIntent(intent)
            checkAndRequestPermission()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onCreate", e)
            throw e
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val imageUri = sharedImageUri
                    if (imageUri != null) {
                        // Setting "Share opens detailed view" was on. Reuse the
                        // grid's fullscreen viewer so the user gets edit / share /
                        // copy / path display in one place.
                        val mime = sharedImageMime ?: "image/*"
                        val path = sharedImagePath ?: imageUri.toString()
                        val sharedImage = ImageFile(
                            id = 0L,
                            displayName = path.substringAfterLast('/').ifBlank { "shared" },
                            uri = imageUri.toString(),
                            path = path,
                            dateAdded = 0L,
                            size = 0L,
                            mimeType = mime,
                            category = "All",
                            type = ImageFile.TYPE_IMAGE,
                            folder = path.substringBeforeLast('/', "")
                        )
                        FullscreenImageViewer(
                            images = listOf(sharedImage),
                            initialIndex = 0,
                            onClose = {
                                sharedImageUri = null
                                sharedImagePath = null
                                sharedImageMime = null
                                finish()
                            },
                            onCopyPath = { /* Toast already shown by ClipboardHelper */ },
                            onRefresh = { /* no-op when shown standalone */ },
                            onEditSaved = { newPath ->
                                ClipboardHelper.copyToClipboard(this@MainActivity, newPath)
                                finish()
                            }
                        )
                    } else {
                        when {
                            hasPermission -> {
                                ImageViewerApp(viewModel = viewModel)
                            }
                            permissionDenied -> {
                                PermissionDeniedScreen(
                                    onOpenSettings = { openAppSettings() }
                                )
                            }
                            else -> {
                                PermissionRequestScreen(
                                    onRequestPermission = { checkAndRequestPermission() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Skip the very first onResume: onCreate already kicked off loadImages()
        // after the permission check, and scanning twice back-to-back is wasted work.
        if (hasResumedOnce && hasPermission && sharedImageUri == null) {
            viewModel.refreshIndex()
        }
        hasResumedOnce = true
    }

    private fun handleIntent(intent: Intent?) {
        try {
            if (intent?.action != Intent.ACTION_SEND) return
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
            val path = UriHelper.getPathFromUri(this, uri)
            val isImage = intent.type?.startsWith("image/") == true

            // Single short blocking read of the boolean preference; cached after
            // first access. Default false on any failure so the share never hangs.
            val openViewer = isImage && runCatching {
                runBlocking {
                    SettingsManager.getShareOpensViewer(this@MainActivity).first()
                }
            }.getOrDefault(false)

            if (openViewer) {
                sharedImageUri = uri
                sharedImagePath = path
                sharedImageMime = intent.type
                path?.let { ClipboardHelper.copyToClipboard(this, it) }
            } else {
                // Default: copy + Toast + finish for any shared item (image or other).
                if (path != null) {
                    ClipboardHelper.copyToClipboard(this, path)
                } else {
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.no_path_for_shared_file),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                finish()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error handling intent", e)
        }
    }

    private fun checkAndRequestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasPermission = true
                permissionDenied = false
                viewModel.loadImages()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.permission_required),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
fun PermissionDeniedScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.permission_denied),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.open_settings))
        }
    }
}
