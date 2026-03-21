package com.imageviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object ImageEditorUtil {
    fun saveEditedImage(
        context: Context,
        originalUri: Uri,
        cropRect: androidx.compose.ui.geometry.Rect?,
        annotations: List<com.imageviewer.ui.components.Annotation>,
        viewSize: androidx.compose.ui.unit.IntSize
    ): String? {
        val inputStream = context.contentResolver.openInputStream(originalUri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        
        // Create a mutable copy to draw on
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        // Calculate scaling
        // IMPORTANT: We need to handle how AsyncImage with ContentScale.Fit places the image
        val viewWidth = viewSize.width.toFloat()
        val viewHeight = viewSize.height.toFloat()
        val bitmapWidth = originalBitmap.width.toFloat()
        val bitmapHeight = originalBitmap.height.toFloat()

        val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val displayedWidth = bitmapWidth * scale
        val displayedHeight = bitmapHeight * scale
        
        val offsetX = (viewWidth - displayedWidth) / 2
        val offsetY = (viewHeight - displayedHeight) / 2

        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 10f / scale // Scale the stroke so it looks consistent on high res
            textSize = 60f / scale
            isAntiAlias = true
            style = Paint.Style.STROKE
        }

        annotations.forEach { annotation ->
            when (annotation) {
                is com.imageviewer.ui.components.Annotation.Arrow -> {
                    // Convert view coordinates back to bitmap coordinates
                    val startX = (annotation.start.x - offsetX) / scale
                    val startY = (annotation.start.y - offsetY) / scale
                    val endX = (annotation.end.x - offsetX) / scale
                    val endY = (annotation.end.y - offsetY) / scale
                    drawArrowOnCanvas(canvas, startX, startY, endX, endY, paint)
                }
                is com.imageviewer.ui.components.Annotation.Text -> {
                    val x = (annotation.position.x - offsetX) / scale
                    val y = (annotation.position.y - offsetY) / scale
                    val textPaint = Paint(paint).apply {
                        style = Paint.Style.FILL
                        isFakeBoldText = true
                    }
                    canvas.drawText(annotation.text, x, y, textPaint)
                }
            }
        }

        var finalBitmap = mutableBitmap
        if (cropRect != null) {
            val left = ((cropRect.left - offsetX) / scale).toInt().coerceIn(0, originalBitmap.width)
            val top = ((cropRect.top - offsetY) / scale).toInt().coerceIn(0, originalBitmap.height)
            val right = ((cropRect.right - offsetX) / scale).toInt().coerceIn(0, originalBitmap.width)
            val bottom = ((cropRect.bottom - offsetY) / scale).toInt().coerceIn(0, originalBitmap.height)
            
            if (right > left && bottom > top) {
                finalBitmap = Bitmap.createBitmap(mutableBitmap, left, top, right - left, bottom - top)
            }
        }

        // Save to public Pictures/PicPath so MediaStore can see it
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PICPATH_$timeStamp.jpg"
        val storageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PicPath")
        if (!storageDir.exists()) storageDir.mkdirs()
        
        val file = File(storageDir, fileName)
        return try {
            FileOutputStream(file).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            // Trigger media scan so it shows up in the app immediately
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawArrowOnCanvas(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, paint: Paint) {
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
}
