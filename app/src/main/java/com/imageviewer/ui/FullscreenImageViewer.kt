package com.imageviewer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imageviewer.R
import com.imageviewer.data.model.ImageFile
import com.imageviewer.ui.components.ImageEditor
import com.imageviewer.util.ClipboardHelper
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SWIPE_DISMISS_THRESHOLD_PX = 200f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenImageViewer(
    images: List<ImageFile>,
    initialIndex: Int,
    onClose: () -> Unit,
    onCopyPath: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { images.size }
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var editingImage by remember { mutableStateOf<ImageFile?>(null) }

    // After an edit-and-save the parent updates initialIndex to point at the new
    // file. Pager state was captured at first composition and won't move on its
    // own — sync it here so the user lands on the edited copy.
    LaunchedEffect(initialIndex, images.size) {
        if (initialIndex in 0 until images.size && initialIndex != pagerState.currentPage) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (editingImage != null) {
            ImageEditor(
                imageUri = editingImage!!.uri,
                imagePath = editingImage!!.path,
                onSave = { newPath ->
                    onRefresh()
                    onCopyPath(newPath)
                    editingImage = null
                },
                onCancel = { editingImage = null }
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val image = images[page]
                var verticalDrag = 0f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { verticalDrag = 0f },
                                onDragEnd = {
                                    if (abs(verticalDrag) > SWIPE_DISMISS_THRESHOLD_PX) onClose()
                                },
                                onVerticalDrag = { _, dy -> verticalDrag += dy }
                            )
                        }
                        .combinedClickable(
                            onClick = { showControls = !showControls },
                            onLongClick = {
                                ClipboardHelper.copyToClipboard(context, image.path)
                                onCopyPath(image.path)
                            }
                        )
                ) {
                    AsyncImage(
                        model = image.uri,
                        contentDescription = image.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            if (showControls) {
                val current = images.getOrNull(pagerState.currentPage)

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (pagerState.currentPage > 0) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                if (pagerState.currentPage < images.size - 1) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                if (current != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = current.path,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { editingImage = current }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit image",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                ClipboardHelper.copyToClipboard(context, current.path)
                                onCopyPath(current.path)
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_copy),
                                contentDescription = "Copy path",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
