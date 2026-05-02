package com.imageviewer.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
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
import com.imageviewer.util.ImageEditorUtil
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

sealed class Annotation {
    data class Arrow(val start: Offset, val end: Offset, val color: Color = Color.Red) : Annotation()
    data class Text(val position: Offset, val text: String, val color: Color = Color.Red) : Annotation()
}

private enum class EditMode { None, Crop, Arrow, Text }
private enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Inside }

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

    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var draggingHandle by remember { mutableStateOf<Handle?>(null) }

    var textEntryPosition by remember { mutableStateOf<Offset?>(null) }
    var textEntryValue by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val parsedUri = remember(imageUri) { Uri.parse(imageUri) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = parsedUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    viewSize = coords.size
                    if (mode == EditMode.Crop && cropRect == null && viewSize != IntSize.Zero) {
                        cropRect = defaultCropRect(viewSize)
                    }
                }
                .pointerInput(mode) {
                    when (mode) {
                        EditMode.Arrow -> detectDragGestures(
                            onDragStart = { offset ->
                                currentArrowStart = offset
                                currentArrowEnd = offset
                            },
                            onDrag = { change, _ -> currentArrowEnd = change.position },
                            onDragEnd = {
                                val s = currentArrowStart
                                val e = currentArrowEnd
                                if (s != null && e != null) annotations.add(Annotation.Arrow(s, e))
                                currentArrowStart = null
                                currentArrowEnd = null
                            }
                        )
                        EditMode.Text -> detectTapGestures { offset ->
                            textEntryPosition = offset
                            textEntryValue = ""
                        }
                        EditMode.Crop -> detectDragGestures(
                            onDragStart = { offset ->
                                draggingHandle = handleAt(offset, cropRect)
                            },
                            onDrag = { _, dragAmount ->
                                val rect = cropRect ?: return@detectDragGestures
                                val updated = when (draggingHandle) {
                                    Handle.TopLeft -> rect.copy(
                                        left = rect.left + dragAmount.x,
                                        top = rect.top + dragAmount.y
                                    )
                                    Handle.TopRight -> rect.copy(
                                        right = rect.right + dragAmount.x,
                                        top = rect.top + dragAmount.y
                                    )
                                    Handle.BottomLeft -> rect.copy(
                                        left = rect.left + dragAmount.x,
                                        bottom = rect.bottom + dragAmount.y
                                    )
                                    Handle.BottomRight -> rect.copy(
                                        right = rect.right + dragAmount.x,
                                        bottom = rect.bottom + dragAmount.y
                                    )
                                    Handle.Inside -> rect.translate(dragAmount.x, dragAmount.y)
                                    null -> return@detectDragGestures
                                }
                                cropRect = clampRectToView(updated, viewSize)
                            },
                            onDragEnd = { draggingHandle = null }
                        )
                        EditMode.None -> {}
                    }
                },
            contentScale = ContentScale.Fit
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            annotations.forEach { ann ->
                when (ann) {
                    is Annotation.Arrow -> drawArrow(ann.start, ann.end, ann.color)
                    is Annotation.Text -> drawContext.canvas.nativeCanvas.drawText(
                        ann.text,
                        ann.position.x,
                        ann.position.y,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.RED
                            textSize = 60f
                            isFakeBoldText = true
                        }
                    )
                }
            }
            if (currentArrowStart != null && currentArrowEnd != null) {
                drawArrow(currentArrowStart!!, currentArrowEnd!!, Color.Red.copy(alpha = 0.5f))
            }
            cropRect?.let { rect ->
                val normalized = normalize(rect)
                val path = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRect(normalized)
                    fillType = PathFillType.EvenOdd
                }
                drawPath(path, Color.Black.copy(alpha = 0.7f))
                drawRect(
                    color = Color.White,
                    topLeft = normalized.topLeft,
                    size = normalized.size,
                    style = Stroke(width = 2.dp.toPx())
                )
                val handleSize = 12.dp.toPx()
                drawCircle(Color.White, radius = handleSize, center = normalized.topLeft)
                drawCircle(Color.White, radius = handleSize, center = normalized.topRight)
                drawCircle(Color.White, radius = handleSize, center = normalized.bottomLeft)
                drawCircle(Color.White, radius = handleSize, center = normalized.bottomRight)
            }
        }

        textEntryPosition?.let { position ->
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

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.5f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel, enabled = !saving) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
            }
            if ((annotations.isNotEmpty() || cropRect != null) && !saving) {
                IconButton(onClick = {
                    if (mode == EditMode.Crop) cropRect = null
                    else if (annotations.isNotEmpty()) annotations.removeAt(annotations.size - 1)
                }) {
                    Icon(Icons.Default.Undo, contentDescription = stringResource(R.string.undo), tint = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Color.Black.copy(alpha = 0.5f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarToggle(
                icon = Icons.Default.Crop,
                label = stringResource(R.string.crop),
                selected = mode == EditMode.Crop,
                enabled = !saving,
                onClick = {
                    mode = EditMode.Crop
                    if (cropRect == null && viewSize != IntSize.Zero) {
                        cropRect = defaultCropRect(viewSize)
                    }
                }
            )
            ToolbarToggle(
                icon = Icons.Default.ArrowOutward,
                label = stringResource(R.string.arrow),
                selected = mode == EditMode.Arrow,
                enabled = !saving,
                onClick = { mode = EditMode.Arrow }
            )
            ToolbarToggle(
                icon = Icons.Default.FormatSize,
                label = stringResource(R.string.text),
                selected = mode == EditMode.Text,
                enabled = !saving,
                onClick = { mode = EditMode.Text }
            )
            IconButton(
                enabled = !saving,
                onClick = {
                    if (saving || viewSize == IntSize.Zero) return@IconButton
                    saving = true
                    val finalCrop = cropRect?.let { normalize(it) }
                    val snapshotAnnotations = annotations.toList()
                    scope.launch {
                        val result = ImageEditorUtil.saveEditedCopy(
                            context = context,
                            originalUri = parsedUri,
                            originalPath = imagePath,
                            cropRect = finalCrop,
                            annotations = snapshotAnnotations,
                            viewSize = viewSize
                        )
                        saving = false
                        if (result != null) onSave(result.absolutePath)
                    }
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.done), tint = Color.Green)
            }
        }

        if (saving) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun ToolbarToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

private fun defaultCropRect(viewSize: IntSize): Rect {
    val w = viewSize.width.toFloat()
    val h = viewSize.height.toFloat()
    return Rect(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f)
}

private fun normalize(rect: Rect): Rect = Rect(
    left = min(rect.left, rect.right),
    top = min(rect.top, rect.bottom),
    right = max(rect.left, rect.right),
    bottom = max(rect.top, rect.bottom)
)

private fun clampRectToView(rect: Rect, viewSize: IntSize): Rect {
    if (viewSize == IntSize.Zero) return rect
    val w = viewSize.width.toFloat()
    val h = viewSize.height.toFloat()
    return Rect(
        left = rect.left.coerceIn(0f, w),
        top = rect.top.coerceIn(0f, h),
        right = rect.right.coerceIn(0f, w),
        bottom = rect.bottom.coerceIn(0f, h)
    )
}

private fun handleAt(offset: Offset, rect: Rect?): Handle? {
    rect ?: return null
    val n = normalize(rect)
    val threshold = 56f
    return when {
        (offset - n.topLeft).getDistance() < threshold -> Handle.TopLeft
        (offset - n.topRight).getDistance() < threshold -> Handle.TopRight
        (offset - n.bottomLeft).getDistance() < threshold -> Handle.BottomLeft
        (offset - n.bottomRight).getDistance() < threshold -> Handle.BottomRight
        n.contains(offset) -> Handle.Inside
        else -> null
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(
    start: Offset,
    end: Offset,
    color: Color
) {
    val strokeWidth = 10f
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth)
    val angle = atan2(end.y - start.y, end.x - start.x)
    val arrowSize = 40f
    val p1 = Offset(end.x - arrowSize * cos(angle - 0.5f), end.y - arrowSize * sin(angle - 0.5f))
    val p2 = Offset(end.x - arrowSize * cos(angle + 0.5f), end.y - arrowSize * sin(angle + 0.5f))
    drawLine(color = color, start = end, end = p1, strokeWidth = strokeWidth)
    drawLine(color = color, start = end, end = p2, strokeWidth = strokeWidth)
}
