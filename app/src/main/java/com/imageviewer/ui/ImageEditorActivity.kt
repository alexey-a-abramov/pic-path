package com.imageviewer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imageviewer.R
import com.imageviewer.ui.components.ImageEditor
import com.imageviewer.util.ClipboardHelper
import com.imageviewer.util.EditorSaveResult
import com.imageviewer.util.UriHelper

/**
 * Standalone editor entry point for ACTION_EDIT intents on image MIME types from
 * other apps. Owns its own back-stack (noHistory=true in the manifest) so dismissing
 * returns the user to the calling app, not Pic Path's gallery. Post-save the user
 * picks Copy Path / Copy Image / Done; on cancel we propagate RESULT_CANCELED so
 * callers like Google Photos know nothing happened.
 */
class ImageEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceUri: Uri? = intent?.data ?: intent?.clipData?.getItemAt(0)?.uri
        if (sourceUri == null) {
            Toast.makeText(this, R.string.no_path_for_shared_file, Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Best-effort path resolution. Empty string is fine — ImageEditorUtil falls
        // back to Pictures/PicPath when it can't mirror the original directory.
        val sourcePath = UriHelper.getPathFromUri(this, sourceUri).orEmpty()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    var saved by remember { mutableStateOf<EditorSaveResult?>(null) }
                    val current = saved
                    if (current == null) {
                        ImageEditor(
                            imageUri = sourceUri.toString(),
                            imagePath = sourcePath,
                            onSave = { result -> saved = result },
                            onCancel = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }
                        )
                    } else {
                        PostSaveChooser(
                            result = current,
                            onCopyPath = {
                                ClipboardHelper.copyToClipboard(this, current.absolutePath)
                                finishWithResult(current)
                            },
                            onCopyImage = {
                                if (current.contentUri != null) {
                                    ClipboardHelper.copyImageToClipboard(
                                        context = this,
                                        uri = current.contentUri
                                    )
                                }
                                finishWithResult(current)
                            },
                            onDone = { finishWithResult(current) }
                        )
                    }
                }
            }
        }
    }

    private fun finishWithResult(result: EditorSaveResult) {
        // Hand the saved URI back to the caller so apps that listen for the result
        // (e.g., Google Photos) can refresh and show the edited copy.
        val data = Intent().apply {
            result.contentUri?.let { setData(it) }
            putExtra("absolutePath", result.absolutePath)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}

@Composable
private fun PostSaveChooser(
    result: EditorSaveResult,
    onCopyPath: () -> Unit,
    onCopyImage: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.edit_image_chooser_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = result.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onCopyPath,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy_path))
                }
                Spacer(Modifier.height(8.dp))
                // Copy-image is disabled when the save fell through MediaStore
                // and we have no content URI to grant the receiver.
                Button(
                    onClick = onCopyImage,
                    enabled = result.contentUri != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy_image))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.done_action))
                }
            }
        }
    }
}
