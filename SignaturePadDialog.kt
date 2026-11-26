package com.pdfmakerstudio.app.ui.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pdfmakerstudio.app.ui.strings.AppLanguage
import com.pdfmakerstudio.app.ui.theme.StudioCrimson
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private data class DrawnStroke(
    val points: List<Offset>,
    val colorHex: Long,
    val strokeWidthPx: Float
)

private val SIGNATURE_COLORS = listOf(
    0xFF0F172AL,
    0xFF1D4ED8L,
    0xFFBE123CL
)

@Composable
fun SignaturePadDialog(
    language: AppLanguage,
    pageNumber: Int,
    onDismiss: () -> Unit,
    onSignatureSaved: (imagePath: String, aspectRatio: Float) -> Unit
) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    var activePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedColorHex by remember { mutableLongStateOf(0xFF0F172AL) }
    var selectedWidthPx by remember { mutableFloatStateOf(10f) }
    var canvasSize by remember { mutableStateOf(IntSize(1200, 500)) }

    // Automatically rotate screen to Landscape for comfortable wide signing, then restore
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Landscape Header & Pen Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Text(
                            text = if (language == AppLanguage.BENGALI)
                                "সিগনেচার প্যাড · পৃষ্ঠা $pageNumber"
                            else "Signature Pad · Page $pageNumber",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SIGNATURE_COLORS.forEach { hex ->
                            val isSelected = selectedColorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = hex }
                            )
                        }

                        listOf(
                            6f to (if (language == AppLanguage.BENGALI) "সরু" else "Fine"),
                            10f to (if (language == AppLanguage.BENGALI) "মাঝারি" else "Med"),
                            16f to (if (language == AppLanguage.BENGALI) "মোটা" else "Bold")
                        ).forEach { (widthVal, label) ->
                            FilterChip(
                                selected = selectedWidthPx == widthVal,
                                onClick = { selectedWidthPx = widthVal },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StudioCrimson,
                                    selectedLabelColor = Color.White,
                                    labelColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }
                }

                // Wide White Signature Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .onSizeChanged { canvasSize = it }
                ) {
                    if (strokes.isEmpty() && activePoints.isEmpty()) {
                        Text(
                            text = if (language == AppLanguage.BENGALI)
                                "এখানে হাত দিয়ে আপনার স্বাক্ষর (Signature) আঁকুন"
                            else "Sign smoothly here with your finger",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selectedColorHex, selectedWidthPx) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        activePoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        activePoints = activePoints + change.position
                                    },
                                    onDragEnd = {
                                        if (activePoints.isNotEmpty()) {
                                            strokes.add(
                                                DrawnStroke(
                                                    points = activePoints,
                                                    colorHex = selectedColorHex,
                                                    strokeWidthPx = selectedWidthPx
                                                )
                                            )
                                            activePoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        activePoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        val baselineY = size.height * 0.76f
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(size.width * 0.06f, baselineY),
                            end = Offset(size.width * 0.94f, baselineY),
                            strokeWidth = 2f
                        )

                        val all = if (activePoints.isNotEmpty()) {
                            strokes + DrawnStroke(activePoints, selectedColorHex, selectedWidthPx)
                        } else {
                            strokes.toList()
                        }

                        all.forEach { stroke ->
                            if (stroke.points.size == 1) {
                                drawCircle(
                                    color = Color(stroke.colorHex),
                                    radius = stroke.strokeWidthPx / 2f,
                                    center = stroke.points.first()
                                )
                            } else if (stroke.points.size > 1) {
                                val path = Path().apply {
                                    moveTo(stroke.points.first().x, stroke.points.first().y)
                                    for (i in 1 until stroke.points.size - 1) {
                                        val midX = (stroke.points[i].x + stroke.points[i + 1].x) / 2f
                                        val midY = (stroke.points[i].y + stroke.points[i + 1].y) / 2f
                                        quadraticTo(
                                            stroke.points[i].x,
                                            stroke.points[i].y,
                                            midX,
                                            midY
                                        )
                                    }
                                    lineTo(stroke.points.last().x, stroke.points.last().y)
                                }
                                drawPath(
                                    path = path,
                                    color = Color(stroke.colorHex),
                                    style = Stroke(
                                        width = stroke.strokeWidthPx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }

                // Bottom Landscape Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                            enabled = strokes.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == AppLanguage.BENGALI) "আগের ধাপ" else "Undo", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = {
                                strokes.clear()
                                activePoints = emptyList()
                            },
                            enabled = strokes.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == AppLanguage.BENGALI) "সব মুছুন" else "Clear", color = Color(0xFFFBBF24))
                        }
                    }

                    Button(
                        onClick = {
                            exportTightCroppedSignature(
                                context = context,
                                strokes = strokes.toList(),
                                widthPx = max(400, canvasSize.width),
                                heightPx = max(200, canvasSize.height)
                            )?.let { (path, ratio) ->
                                onSignatureSaved(path, ratio)
                            }
                        },
                        enabled = strokes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCrimson),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == AppLanguage.BENGALI) "পেজে বসান" else "Apply Signature",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun exportTightCroppedSignature(
    context: Context,
    strokes: List<DrawnStroke>,
    widthPx: Int,
    heightPx: Int
): Pair<String, Float>? {
    if (strokes.isEmpty()) return null

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    var maxStroke = 10f

    strokes.forEach { s ->
        maxStroke = max(maxStroke, s.strokeWidthPx)
        s.points.forEach { pt ->
            minX = min(minX, pt.x)
            minY = min(minY, pt.y)
            maxX = max(maxX, pt.x)
            maxY = max(maxY, pt.y)
        }
    }

    if (minX == Float.MAX_VALUE) return null

    val pad = maxStroke + 18f
    val cropLeft = max(0f, minX - pad).toInt()
    val cropTop = max(0f, minY - pad).toInt()
    val cropRight = min(widthPx.toFloat(), maxX + pad).toInt()
    val cropBottom = min(heightPx.toFloat(), maxY + pad).toInt()
    val outW = max(60, cropRight - cropLeft)
    val outH = max(40, cropBottom - cropTop)

    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.translate(-cropLeft.toFloat(), -cropTop.toFloat())

    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }

    strokes.forEach { stroke ->
        paint.color = stroke.colorHex.toInt()
        paint.strokeWidth = stroke.strokeWidthPx
        if (stroke.points.size == 1) {
            paint.style = AndroidPaint.Style.FILL
            canvas.drawCircle(stroke.points.first().x, stroke.points.first().y, stroke.strokeWidthPx / 2f, paint)
            paint.style = AndroidPaint.Style.STROKE
        } else if (stroke.points.size > 1) {
            val path = AndroidPath().apply {
                moveTo(stroke.points.first().x, stroke.points.first().y)
                for (i in 1 until stroke.points.size - 1) {
                    val midX = (stroke.points[i].x + stroke.points[i + 1].x) / 2f
                    val midY = (stroke.points[i].y + stroke.points[i + 1].y) / 2f
                    quadTo(stroke.points[i].x, stroke.points[i].y, midX, midY)
                }
                lineTo(stroke.points.last().x, stroke.points.last().y)
            }
            canvas.drawPath(path, paint)
        }
    }

    val dir = File(context.filesDir, "signatures").apply { mkdirs() }
    val outFile = File(dir, "sig_${UUID.randomUUID().toString().take(12)}.png")
    FileOutputStream(outFile).use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    bitmap.recycle()

    val aspectRatio = (outH.toFloat() / outW.toFloat()).coerceIn(0.18f, 1.6f)
    return outFile.absolutePath to aspectRatio
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
