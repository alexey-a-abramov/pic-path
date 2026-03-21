package com.imageviewer.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.NativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imageviewer.R
import com.imageviewer.data.model.ImageFile
import com.imageviewer.util.ClipboardHelper
import com.imageviewer.util.ImageEditorUtil
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

import androidx.compose.ui.graphics.PathFillType

sealed class Annotation {
    data class Arrow(val start: Offset, val end: Offset, val color: Color = Color.Red) : Annotation()
    data class Text(val position: Offset, val text: String, val color: Color = Color.Red) : Annotation()
}

enum class EditMode {
    None, Crop, Arrow, Text
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditor(
    imageUri: String,
    imagePath: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var mode by remember { mutableStateOf(EditMode.None) }
    val annotations = remember { mutableStateListOf<Annotation>() }
    var currentArrowStart by remember { mutableStateOf<Offset?>(null) }
    var currentArrowEnd by remember { mutableStateOf<Offset?>(null) }
    
    // Improved Crop State
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var draggingHandle by remember { mutableStateOf<Handle?>(null) }

    var textEntryPosition by remember { mutableStateOf<Offset?>(null) }
    var textEntryValue by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val parsedUri = remember(imageUri) { Uri.parse(imageUri) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Base Image
        AsyncImage(
            model = parsedUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    viewSize = coordinates.size
                    // Initialize crop rect to 80% of view if in crop mode and not set
                    if (mode == EditMode.Crop && cropRect == null && viewSize != IntSize.Zero) {
                        val w = viewSize.width.toFloat()
                        val h = viewSize.height.toFloat()
                        cropRect = Rect(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f)
                    }
                }
                .pointerInput(mode, cropRect) {
                    when (mode) {
                        EditMode.Arrow -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentArrowStart = offset
                                    currentArrowEnd = offset
                                },
                                onDrag = { change, _ ->
                                    currentArrowEnd = change.position
                                },
                                onDragEnd = {
                                    if (currentArrowStart != null && currentArrowEnd != null) {
                                        annotations.add(Annotation.Arrow(currentArrowStart!!, currentArrowEnd!!))
                                    }
                                    currentArrowStart = null
                                    currentArrowEnd = null
                                }
                            )
                        }
                        EditMode.Text -> {
                            detectTapGestures { offset ->
                                textEntryPosition = offset
                                textEntryValue = ""
                            }
                        }
                        EditMode.Crop -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    draggingHandle = cropRect?.let { getHandleAt(offset, it) }
                                },
                                onDrag = { change, _ ->
                                    cropRect?.let { rect ->
                                        cropRect = when (draggingHandle) {
                                            Handle.TopLeft -> rect.copy(left = change.position.x, top = change.position.y)
                                            Handle.TopRight -> rect.copy(right = change.position.x, top = change.position.y)
                                            Handle.BottomLeft -> rect.copy(left = change.position.x, bottom = change.position.y)
                                            Handle.BottomRight -> rect.copy(right = change.position.x, bottom = change.position.y)
                                            null -> {
                                                // If not dragging a handle, maybe move the whole rect?
                                                if (rect.contains(change.position)) {
                                                    rect.translate(change.scrollDelta.x, change.scrollDelta.y)
                                                } else rect
                                            }
                                        }
                                    }
                                },
                                onDragEnd = { draggingHandle = null }
                            )
                        }
                        else -> {}
                    }
                },
            contentScale = ContentScale.Fit
        )

        // Drawing Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw existing annotations
            annotations.forEach { annotation ->
                when (annotation) {
                    is Annotation.Arrow -> {
                        drawArrow(annotation.start, annotation.end, annotation.color)
                    }
                    is Annotation.Text -> {
                        drawContext.canvas.nativeCanvas.drawText(
                            annotation.text,
                            annotation.position.x,
                            annotation.position.y,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                textSize = 60f
                                isFakeBoldText = true
                            }
                        )
                    }
                }
            }

            // Draw current arrow being dragged
            if (currentArrowStart != null && currentArrowEnd != null) {
                drawArrow(currentArrowStart!!, currentArrowEnd!!, Color.Red.copy(alpha = 0.5f))
            }

            // Draw crop rect and handles
            cropRect?.let { rect ->
                // Scrim with hole
                val path = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRect(rect)
                    fillType = PathFillType.EvenOdd
                }
                drawPath(path, Color.Black.copy(alpha = 0.7f))

                // Border
                drawRect(
                    color = Color.White,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Handles
                val handleSize = 12.dp.toPx()
                drawCircle(Color.White, radius = handleSize, center = rect.topLeft)
                drawCircle(Color.White, radius = handleSize, center = rect.topRight)
                drawCircle(Color.White, radius = handleSize, center = rect.bottomLeft)
                drawCircle(Color.White, radius = handleSize, center = rect.bottomRight)
            }
        }

        // Text Input Overlay
        textEntryPosition?.let { position ->
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        TextField(
                            value = textEntryValue,
                            onValueChange = { textEntryValue = it },
                            placeholder = { Text(stringResource(R.string.enter_text_placeholder)) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                        IconButton(onClick = {
                            if (textEntryValue.isNotBlank()) {
                                annotations.add(Annotation.Text(position, textEntryValue))
                            }
                            textEntryPosition = null
                        }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.add_text), tint = Color.White)
                        }
                    }
                }
            }
        }

        // Top Toolbar
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.5f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
            }
            if (annotations.isNotEmpty() || cropRect != null) {
                IconButton(onClick = {
                    if (mode == EditMode.Crop) cropRect = null
                    else if (annotations.isNotEmpty()) annotations.removeAt(annotations.size - 1)
                }) {
                    Icon(Icons.Default.Undo, contentDescription = stringResource(R.string.undo), tint = Color.White)
                }
            }
        }

        // Bottom Toolbar
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Color.Black.copy(alpha = 0.5f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { 
                mode = EditMode.Crop 
                if (cropRect == null && viewSize != IntSize.Zero) {
                    val w = viewSize.width.toFloat()
                    val h = viewSize.height.toFloat()
                    cropRect = Rect(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f)
                }
            }, modifier = Modifier.background(if (mode == EditMode.Crop) Color.White.copy(alpha = 0.2f) else Color.Transparent)) {
                Icon(Icons.Default.Crop, contentDescription = stringResource(R.string.crop), tint = Color.White)
            }
            IconButton(onClick = { mode = EditMode.Arrow }, modifier = Modifier.background(if (mode == EditMode.Arrow) Color.White.copy(alpha = 0.2f) else Color.Transparent)) {
                Icon(Icons.Default.ArrowOutward, contentDescription = stringResource(R.string.arrow), tint = Color.White)
            }
            IconButton(onClick = { mode = EditMode.Text }, modifier = Modifier.background(if (mode == EditMode.Text) Color.White.copy(alpha = 0.2f) else Color.Transparent)) {
                Icon(Icons.Default.FormatSize, contentDescription = stringResource(R.string.text), tint = Color.White)
            }
            IconButton(onClick = {
                val savedPath = ImageEditorUtil.saveEditedImage(context, parsedUri, cropRect, annotations, viewSize)
                if (savedPath != null) {
                    // Copy to clipboard as path AND as URI (for file paste)
                    ClipboardHelper.copyToClipboard(context, savedPath)
                    onSave(savedPath)
                }
            }) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.done), tint = Color.Green)
            }
        }
    }
}

private enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight }

private fun getHandleAt(offset: Offset, rect: Rect): Handle? {
    val threshold = 40f
    return when {
        (offset - rect.topLeft).getDistance() < threshold -> Handle.TopLeft
        (offset - rect.topRight).getDistance() < threshold -> Handle.TopRight
        (offset - rect.bottomLeft).getDistance() < threshold -> Handle.BottomLeft
        (offset - rect.bottomRight).getDistance() < threshold -> Handle.BottomRight
        else -> null
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(
    start: Offset,
    end: Offset,
    color: Color
) {
    val strokeWidth = 10f
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth
    )
    
    val angle = atan2(end.y - start.y, end.x - start.x)
    val arrowSize = 40f
    
    val p1 = Offset(
        end.x - arrowSize * cos(angle - 0.5f),
        end.y - arrowSize * sin(angle - 0.5f)
    )
    val p2 = Offset(
        end.x - arrowSize * cos(angle + 0.5f),
        end.y - arrowSize * sin(angle + 0.5f)
    )
    
    drawLine(color = color, start = end, end = p1, strokeWidth = strokeWidth)
    drawLine(color = color, start = end, end = p2, strokeWidth = strokeWidth)
}
