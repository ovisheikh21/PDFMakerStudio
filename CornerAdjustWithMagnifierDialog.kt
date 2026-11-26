package com.pdfmakerstudio.app.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pdfmakerstudio.app.data.pdf.DocumentScannerUtils
import com.pdfmakerstudio.app.data.pdf.NormalizedPoint
import com.pdfmakerstudio.app.data.pdf.ScanQuad
import com.pdfmakerstudio.app.ui.strings.AppLanguage
import com.pdfmakerstudio.app.ui.theme.StudioCrimson
import com.pdfmakerstudio.app.ui.theme.StudioEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CornerAdjustWithMagnifierDialog(
    imageUri: Uri,
    initialQuad: ScanQuad,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onApply: (ScanQuad) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var quad by remember(imageUri.toString(), initialQuad) { mutableStateOf(initialQuad) }
    var activeCorner by remember { mutableStateOf<String?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var previewBitmap by remember(imageUri.toString()) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri.toString()) {
        val loaded = withContext(Dispatchers.IO) {
            DocumentScannerUtils.decodeUprightBitmap(context, imageUri, 1500)
        }
        previewBitmap = loaded
    }

    DisposableEffect(previewBitmap) {
        val bmp = previewBitmap
        onDispose {
            if (bmp != null && !bmp.isRecycled) {
                bmp.recycle()
            }
        }
    }

    val aspectRatio = remember(previewBitmap) {
        val b = previewBitmap
        if (b != null && b.width > 0 && b.height > 0) {
            (b.width.toFloat() / b.height.toFloat()).coerceIn(0.42f, 2.2f)
        } else {
            3f / 4f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "পেজের ৪ কোণা ঠিক করুন"
                                else "Adjust 4 Corners",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "কোণা ধরে টানলে উপরে জুম লেন্স দেখাবে"
                                else "Drag any corner to preview zoomed detail above",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            scope.launch {
                                quad = DocumentScannerUtils.detectDocumentQuad(context, imageUri)
                            }
                        }) {
                            Text(if (language == AppLanguage.BENGALI) "অটো" else "Auto")
                        }
                        TextButton(onClick = {
                            quad = ScanQuad(
                                NormalizedPoint(0.02f, 0.02f),
                                NormalizedPoint(0.98f, 0.02f),
                                NormalizedPoint(0.98f, 0.98f),
                                NormalizedPoint(0.02f, 0.98f)
                            )
                        }) {
                            Text(if (language == AppLanguage.BENGALI) "ফুল" else "Full")
                        }
                        TextButton(onClick = onDismiss) {
                            Text(if (language == AppLanguage.BENGALI) "বাতিল" else "Cancel")
                        }
                        Button(
                            onClick = { onApply(quad) },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioCrimson),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (language == AppLanguage.BENGALI) "সোজা" else "Apply")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0F172A))
                    .padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    if (boxSize.width <= 0 || boxSize.height <= 0) return@detectDragGestures
                                    val nx = start.x / boxSize.width
                                    val ny = start.y / boxSize.height
                                    val candidates = listOf(
                                        "tl" to hypot(nx - quad.tl.x, ny - quad.tl.y),
                                        "tr" to hypot(nx - quad.tr.x, ny - quad.tr.y),
                                        "br" to hypot(nx - quad.br.x, ny - quad.br.y),
                                        "bl" to hypot(nx - quad.bl.x, ny - quad.bl.y)
                                    )
                                    activeCorner = candidates.minByOrNull { it.second }?.first
                                },
                                onDragEnd = { activeCorner = null },
                                onDragCancel = { activeCorner = null },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (boxSize.width <= 0 || boxSize.height <= 0) return@detectDragGestures
                                    val dx = dragAmount.x / boxSize.width
                                    val dy = dragAmount.y / boxSize.height
                                    quad = when (activeCorner) {
                                        "tl" -> quad.copy(
                                            tl = NormalizedPoint(
                                                (quad.tl.x + dx).coerceIn(0.01f, 0.99f),
                                                (quad.tl.y + dy).coerceIn(0.01f, 0.99f)
                                            )
                                        )
                                        "tr" -> quad.copy(
                                            tr = NormalizedPoint(
                                                (quad.tr.x + dx).coerceIn(0.01f, 0.99f),
                                                (quad.tr.y + dy).coerceIn(0.01f, 0.99f)
                                            )
                                        )
                                        "br" -> quad.copy(
                                            br = NormalizedPoint(
                                                (quad.br.x + dx).coerceIn(0.01f, 0.99f),
                                                (quad.br.y + dy).coerceIn(0.01f, 0.99f)
                                            )
                                        )
                                        "bl" -> quad.copy(
                                            bl = NormalizedPoint(
                                                (quad.bl.x + dx).coerceIn(0.01f, 0.99f),
                                                (quad.bl.y + dy).coerceIn(0.01f, 0.99f)
                                            )
                                        )
                                        else -> quad
                                    }
                                }
                            )
                        }
                ) {
                    val bmp = previewBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Adjust document corners",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val tl = Offset(quad.tl.x * size.width, quad.tl.y * size.height)
                        val tr = Offset(quad.tr.x * size.width, quad.tr.y * size.height)
                        val br = Offset(quad.br.x * size.width, quad.br.y * size.height)
                        val bl = Offset(quad.bl.x * size.width, quad.bl.y * size.height)

                        val path = Path().apply {
                            moveTo(tl.x, tl.y)
                            lineTo(tr.x, tr.y)
                            lineTo(br.x, br.y)
                            lineTo(bl.x, bl.y)
                            close()
                        }
                        drawPath(path, color = Color(0x2810B981))
                        drawPath(
                            path,
                            color = StudioEmerald,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        listOf("tl" to tl, "tr" to tr, "br" to br, "bl" to bl).forEach { (key, pt) ->
                            val isDragging = activeCorner == key
                            drawCircle(
                                color = Color.White,
                                radius = if (isDragging) 14.dp.toPx() else 12.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = if (isDragging) StudioCrimson else StudioEmerald,
                                radius = if (isDragging) 10.dp.toPx() else 8.dp.toPx(),
                                center = pt
                            )
                        }
                    }

                    // Zoomed-In Corner Magnifier Loupe positioned directly ABOVE the active corner
                    val currentCorner = activeCorner
                    if (currentCorner != null && bmp != null && !bmp.isRecycled && boxSize.width > 0 && boxSize.height > 0) {
                        val activePt = when (currentCorner) {
                            "tl" -> quad.tl
                            "tr" -> quad.tr
                            "br" -> quad.br
                            else -> quad.bl
                        }
                        val cornerLabel = when (currentCorner) {
                            "tl" -> if (language == AppLanguage.BENGALI) "উপরে-বামে" else "Top-Left"
                            "tr" -> if (language == AppLanguage.BENGALI) "উপরে-ডানে" else "Top-Right"
                            "br" -> if (language == AppLanguage.BENGALI) "নিচে-ডানে" else "Bottom-Right"
                            else -> if (language == AppLanguage.BENGALI) "নিচে-বামে" else "Bottom-Left"
                        }

                        val loupeDp = 114.dp
                        val loupePx = with(density) { loupeDp.toPx() }
                        val gapPx = with(density) { 26.dp.toPx() }

                        val cornerPxX = activePt.x * boxSize.width
                        val cornerPxY = activePt.y * boxSize.height

                        val clampedX = (cornerPxX - loupePx / 2f)
                            .coerceIn(4f, (boxSize.width - loupePx - 4f).coerceAtLeast(4f))
                            .roundToInt()

                        // Prefer placing ABOVE the dragged corner so thumb never covers it
                        val preferY = cornerPxY - loupePx - gapPx
                        val resolvedY = (if (preferY >= -with(density) { 42.dp.toPx() }) {
                            preferY
                        } else {
                            cornerPxY + gapPx
                        }).roundToInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(clampedX, resolvedY) }
                                .size(loupeDp)
                                .shadow(12.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A))
                                .border(3.dp, Color.White, CircleShape)
                                .border(1.5.dp, StudioCrimson, CircleShape)
                        ) {
                            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val zoom = 2.6f
                                val srcPerDispX = bmp.width.toFloat() / boxSize.width.toFloat().coerceAtLeast(1f)
                                val srcPerDispY = bmp.height.toFloat() / boxSize.height.toFloat().coerceAtLeast(1f)

                                val sampleW = ((size.width / zoom) * srcPerDispX).roundToInt().coerceIn(24, bmp.width)
                                val sampleH = ((size.height / zoom) * srcPerDispY).roundToInt().coerceIn(24, bmp.height)

                                val centerSrcX = (activePt.x * bmp.width).roundToInt()
                                val centerSrcY = (activePt.y * bmp.height).roundToInt()

                                val maxOffsetX = (bmp.width - sampleW).coerceAtLeast(0)
                                val maxOffsetY = (bmp.height - sampleH).coerceAtLeast(0)
                                val srcLeft = (centerSrcX - sampleW / 2).coerceIn(0, maxOffsetX)
                                val srcTop = (centerSrcY - sampleH / 2).coerceIn(0, maxOffsetY)

                                drawImage(
                                    image = imageBitmap,
                                    srcOffset = IntOffset(srcLeft, srcTop),
                                    srcSize = IntSize(sampleW, sampleH),
                                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                                )

                                // Map active corner exact position inside the zoomed crop window
                                val cx = ((centerSrcX - srcLeft).toFloat() / sampleW.toFloat()) * size.width
                                val cy = ((centerSrcY - srcTop).toFloat() / sampleH.toFloat()) * size.height

                                val arm = 15.dp.toPx()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.92f),
                                    start = Offset(cx - arm, cy),
                                    end = Offset(cx + arm, cy),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.92f),
                                    start = Offset(cx, cy - arm),
                                    end = Offset(cx, cy + arm),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawCircle(
                                    color = StudioCrimson,
                                    radius = 4.5.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 4.5.dp.toPx(),
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xCC020617))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$cornerLabel · 2.6×",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
