package com.imageviewer.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.imageviewer.ui.components.Annotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Result of a save attempt. Always carries the absolute filesystem path so the rest of
 * the app can copy it to clipboard and navigate to it after MediaStore re-indexes.
 */
data class EditorSaveResult(val absolutePath: String)

object ImageEditorUtil {

    /**
     * Save an edited copy. Runs the heavy work on IO; the result lands on the caller's
     * coroutine context. The original file is never touched. The new file lands in the
     * same directory as the original (when its path is known and writable); otherwise it
     * falls back to Pictures/PicPath. MediaStore is updated synchronously via insert,
     * so a subsequent MediaStore scan immediately sees the new row.
     */
    suspend fun saveEditedCopy(
        context: Context,
        originalUri: Uri,
        originalPath: String,
        cropRect: Rect?,
        annotations: List<Annotation>,
        viewSize: IntSize
    ): EditorSaveResult? = withContext(Dispatchers.IO) {
        val rendered = renderEditedBitmap(context, originalUri, cropRect, annotations, viewSize)
            ?: return@withContext null

        try {
            val isPng = originalPath.substringAfterLast('.', "").equals("png", ignoreCase = true)
            val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val mime = if (isPng) "image/png" else "image/jpeg"
            val ext = if (isPng) "png" else "jpg"

            val baseName = File(originalPath).nameWithoutExtension.ifBlank { "image" }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${baseName}_edited_$timeStamp.$ext"

            val savedAbsolutePath = saveBitmap(
                context = context,
                bitmap = rendered,
                originalPath = originalPath,
                fileName = fileName,
                mime = mime,
                format = format
            ) ?: return@withContext null

            EditorSaveResult(savedAbsolutePath)
        } finally {
            rendered.recycle()
        }
    }

    /**
     * Render the bitmap with annotations applied and an optional crop. Sample-decoded
     * to fit comfortably above the view size so we don't OOM on 12MP photos.
     */
    private fun renderEditedBitmap(
        context: Context,
        originalUri: Uri,
        cropRect: Rect?,
        annotations: List<Annotation>,
        viewSize: IntSize
    ): Bitmap? {
        val bounds = readBounds(context, originalUri) ?: return null
        val targetMax = max(viewSize.width, viewSize.height).coerceAtLeast(1024) * 2
        val sample = computeInSampleSize(bounds.first, bounds.second, targetMax, targetMax)

        val original = decodeSampled(context, originalUri, sample) ?: return null
        return try {
            val mutable = original.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            try {
                drawAnnotations(mutable, cropRect, annotations, viewSize)
                applyCrop(mutable, cropRect, viewSize) ?: mutable
            } catch (oom: OutOfMemoryError) {
                mutable.recycle()
                null
            }
        } finally {
            if (!original.isRecycled) original.recycle()
        }
    }

    private fun readBounds(context: Context, uri: Uri): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        while (srcW / (sample * 2) >= reqW && srcH / (sample * 2) >= reqH) sample *= 2
        return sample
    }

    private fun decodeSampled(context: Context, uri: Uri, sample: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (oom: OutOfMemoryError) {
            null
        }
    }

    private fun drawAnnotations(
        bitmap: Bitmap,
        cropRect: Rect?,
        annotations: List<Annotation>,
        viewSize: IntSize
    ) {
        if (annotations.isEmpty()) return
        val canvas = Canvas(bitmap)
        val viewW = viewSize.width.toFloat()
        val viewH = viewSize.height.toFloat()
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        val scale = min(viewW / bmpW, viewH / bmpH).coerceAtLeast(1e-6f)
        val offsetX = (viewW - bmpW * scale) / 2f
        val offsetY = (viewH - bmpH * scale) / 2f

        val basePaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 10f / scale
            textSize = 60f / scale
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        annotations.forEach { ann ->
            when (ann) {
                is Annotation.Arrow -> {
                    val p = Paint(basePaint).apply { color = ann.color.toArgb() }
                    drawArrowOnCanvas(
                        canvas,
                        (ann.start.x - offsetX) / scale,
                        (ann.start.y - offsetY) / scale,
                        (ann.end.x - offsetX) / scale,
                        (ann.end.y - offsetY) / scale,
                        p
                    )
                }
                is Annotation.Stroke -> {
                    if (ann.points.size < 2) return@forEach
                    val path = AndroidPath()
                    val first = ann.points[0]
                    path.moveTo((first.x - offsetX) / scale, (first.y - offsetY) / scale)
                    for (i in 1 until ann.points.size) {
                        val p = ann.points[i]
                        path.lineTo((p.x - offsetX) / scale, (p.y - offsetY) / scale)
                    }
                    val strokePaint = Paint(basePaint).apply {
                        color = ann.color.toArgb()
                        style = Paint.Style.STROKE
                    }
                    canvas.drawPath(path, strokePaint)
                }
                is Annotation.Text -> {
                    val textPaint = Paint(basePaint).apply {
                        color = ann.color.toArgb()
                        style = Paint.Style.FILL
                        isFakeBoldText = true
                    }
                    canvas.drawText(
                        ann.text,
                        (ann.position.x - offsetX) / scale,
                        (ann.position.y - offsetY) / scale,
                        textPaint
                    )
                }
            }
        }
    }

    private fun applyCrop(
        source: Bitmap,
        cropRect: Rect?,
        viewSize: IntSize
    ): Bitmap? {
        if (cropRect == null) return null
        val viewW = viewSize.width.toFloat()
        val viewH = viewSize.height.toFloat()
        val bmpW = source.width.toFloat()
        val bmpH = source.height.toFloat()
        val scale = min(viewW / bmpW, viewH / bmpH).coerceAtLeast(1e-6f)
        val offsetX = (viewW - bmpW * scale) / 2f
        val offsetY = (viewH - bmpH * scale) / 2f

        // Normalize: user may have dragged bottom-right above top-left.
        val l0 = min(cropRect.left, cropRect.right)
        val r0 = max(cropRect.left, cropRect.right)
        val t0 = min(cropRect.top, cropRect.bottom)
        val b0 = max(cropRect.top, cropRect.bottom)

        val left = ((l0 - offsetX) / scale).toInt().coerceIn(0, source.width)
        val top = ((t0 - offsetY) / scale).toInt().coerceIn(0, source.height)
        val right = ((r0 - offsetX) / scale).toInt().coerceIn(0, source.width)
        val bottom = ((b0 - offsetY) / scale).toInt().coerceIn(0, source.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    private fun drawArrowOnCanvas(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        paint: Paint
    ) {
        canvas.drawLine(startX, startY, endX, endY, paint)
        val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
        val arrowSize = 40f * (paint.strokeWidth * 0.1f).coerceAtLeast(1f)
        val p1x = endX - arrowSize * cos(angle - 0.5f).toFloat()
        val p1y = endY - arrowSize * sin(angle - 0.5f).toFloat()
        val p2x = endX - arrowSize * cos(angle + 0.5f).toFloat()
        val p2y = endY - arrowSize * sin(angle + 0.5f).toFloat()
        canvas.drawLine(endX, endY, p1x, p1y, paint)
        canvas.drawLine(endX, endY, p2x, p2y, paint)
    }

    /**
     * Save [bitmap] beside the original when possible, falling back to Pictures/PicPath.
     * Uses MediaStore so the row is queryable immediately (no MediaScanner round-trip).
     * Returns the absolute filesystem path of the saved file, or null on failure.
     */
    private fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        originalPath: String,
        fileName: String,
        mime: String,
        format: Bitmap.CompressFormat
    ): String? {
        val originalParent = File(originalPath).parentFile
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

        // Decide the "relative path" (Pictures/Screenshots, DCIM/Camera, …) used by MediaStore on Q+.
        // If the original is on external storage, mirror its directory; otherwise use Pictures/PicPath.
        val (relativePath, fallbackParent) = if (
            originalParent != null &&
            originalParent.absolutePath.startsWith(externalRoot)
        ) {
            val rel = originalParent.absolutePath.removePrefix(externalRoot).trim('/')
            (if (rel.isEmpty()) "Pictures/PicPath" else rel) to originalParent
        } else {
            "Pictures/PicPath" to File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "PicPath"
            )
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreQ(context, bitmap, fileName, mime, format, relativePath)
                ?: saveViaFile(bitmap, fallbackParent, fileName, format, context)
        } else {
            saveViaFile(bitmap, fallbackParent, fileName, format, context)
        }
    }

    private fun saveViaMediaStoreQ(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        mime: String,
        format: Bitmap.CompressFormat,
        relativePath: String
    ): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(format, 95, out)) {
                    resolver.delete(uri, null, null)
                    return null
                }
            } ?: return null

            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)

            // Resolve absolute path back from MediaStore for clipboard + navigation.
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (t: Throwable) {
            try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
            null
        }
    }

    private fun saveViaFile(
        bitmap: Bitmap,
        parentDir: File,
        fileName: String,
        format: Bitmap.CompressFormat,
        context: Context
    ): String? {
        return try {
            if (!parentDir.exists()) parentDir.mkdirs()
            val file = File(parentDir, fileName)
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(format, 95, out)) return null
            }
            // Pre-Q: insert into MediaStore so it's immediately queryable.
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, file.absolutePath)
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
                )
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            file.absolutePath
        } catch (t: Throwable) {
            null
        }
    }
}
