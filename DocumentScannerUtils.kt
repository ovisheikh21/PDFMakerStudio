package com.pdfmakerstudio.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

data class ScanQuad(
    val tl: NormalizedPoint = NormalizedPoint(0.05f, 0.05f),
    val tr: NormalizedPoint = NormalizedPoint(0.95f, 0.05f),
    val br: NormalizedPoint = NormalizedPoint(0.95f, 0.95f),
    val bl: NormalizedPoint = NormalizedPoint(0.05f, 0.95f)
)

data class LiveDetectedQuad(
    val quad: ScanQuad,
    val confidence: Float,
    val isLocked: Boolean
)

enum class ScanFilterMode(val labelEn: String, val labelBn: String) {
    MAGIC("Crisp Scan", "ক্রিস্প স্ক্যান"),
    BW("B&W Clear", "সাদা-কালো"),
    ORIGINAL("Original", "অরিজিনাল")
}

data class ProcessedScanResult(
    val outputUri: Uri,
    val width: Int,
    val height: Int
)

object DocumentScannerUtils {

    private const val MAX_SOURCE_DIM = 2200
    private const val A4_RATIO = 210f / 297f

    fun createTempCameraScanUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_scans").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun importPreStraightenedScanUri(
        context: Context,
        sourceUri: Uri
    ): ProcessedScanResult = withContext(Dispatchers.IO) {
        val upright = decodeUprightBitmap(context, sourceUri, MAX_SOURCE_DIM)
            ?: error("Unable to read scanned image")
        try {
            val outDir = File(context.filesDir, "scanned_pages").apply { mkdirs() }
            val outFile = File(outDir, "mlkit_scan_${UUID.randomUUID().toString().take(10)}.jpg")
            FileOutputStream(outFile).use { fos ->
                upright.compress(Bitmap.CompressFormat.JPEG, 94, fos)
            }
            ProcessedScanResult(
                outputUri = Uri.fromFile(outFile),
                width = upright.width,
                height = upright.height
            )
        } finally {
            upright.recycle()
        }
    }

    fun smoothQuad(prev: ScanQuad?, next: ScanQuad, alpha: Float = 0.38f): ScanQuad {
        if (prev == null) return next
        fun lerp(a: Float, b: Float) = a + (b - a) * alpha
        return ScanQuad(
            tl = NormalizedPoint(lerp(prev.tl.x, next.tl.x), lerp(prev.tl.y, next.tl.y)),
            tr = NormalizedPoint(lerp(prev.tr.x, next.tr.x), lerp(prev.tr.y, next.tr.y)),
            br = NormalizedPoint(lerp(prev.br.x, next.br.x), lerp(prev.br.y, next.br.y)),
            bl = NormalizedPoint(lerp(prev.bl.x, next.bl.x), lerp(prev.bl.y, next.bl.y))
        )
    }

    fun quadDistance(a: ScanQuad, b: ScanQuad): Float {
        return (
            hypot(a.tl.x - b.tl.x, a.tl.y - b.tl.y) +
                hypot(a.tr.x - b.tr.x, a.tr.y - b.tr.y) +
                hypot(a.br.x - b.br.x, a.br.y - b.br.y) +
                hypot(a.bl.x - b.bl.x, a.bl.y - b.bl.y)
            ) / 4f
    }

    fun detectDocumentQuadFromYPlane(
        yBuffer: ByteBuffer,
        bufW: Int,
        bufH: Int,
        rowStride: Int,
        rotationDeg: Int
    ): LiveDetectedQuad {
        if (bufW < 64 || bufH < 64) return LiveDetectedQuad(ScanQuad(), 0f, false)
        val normRot = ((rotationDeg % 360) + 360) % 360
        val rotatedW = if (normRot == 90 || normRot == 270) bufH else bufW
        val rotatedH = if (normRot == 90 || normRot == 270) bufW else bufH

        val targetMax = 150
        val scale = min(1f, targetMax.toFloat() / max(rotatedW, rotatedH).toFloat())
        val w = (rotatedW * scale).roundToInt().coerceAtLeast(48)
        val h = (rotatedH * scale).roundToInt().coerceAtLeast(48)

        val luma = FloatArray(w * h)
        val totalCap = yBuffer.capacity()
        for (dy in 0 until h) {
            val ry = (dy.toFloat() / h * rotatedH).toInt().coerceIn(0, rotatedH - 1)
            for (dx in 0 until w) {
                val rx = (dx.toFloat() / w * rotatedW).toInt().coerceIn(0, rotatedW - 1)
                val sx: Int
                val sy: Int
                when (normRot) {
                    90 -> {
                        sx = ry
                        sy = bufH - 1 - rx
                    }
                    180 -> {
                        sx = bufW - 1 - rx
                        sy = bufH - 1 - ry
                    }
                    270 -> {
                        sx = bufW - 1 - ry
                        sy = rx
                    }
                    else -> {
                        sx = rx
                        sy = ry
                    }
                }
                val offset = (sy * rowStride + sx).coerceIn(0, totalCap - 1)
                luma[dy * w + dx] = (yBuffer.get(offset).toInt() and 0xFF).toFloat()
            }
        }
        return detectDocumentQuadFromLuma(luma, w, h)
    }

    suspend fun detectDocumentQuad(context: Context, sourceUri: Uri): ScanQuad = withContext(Dispatchers.Default) {
        val sample = decodeUprightBitmap(context, sourceUri, 240) ?: return@withContext ScanQuad()
        try {
            val w = sample.width
            val h = sample.height
            if (w < 48 || h < 48) return@withContext ScanQuad()

            val pixels = IntArray(w * h)
            sample.getPixels(pixels, 0, w, 0, 0, w, h)

            val rawLum = FloatArray(w * h)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                rawLum[i] = 0.299f * r + 0.587f * g + 0.114f * b
            }
            detectDocumentQuadFromLuma(rawLum, w, h).quad
        } finally {
            sample.recycle()
        }
    }

    fun detectDocumentQuadFromLuma(rawLum: FloatArray, w: Int, h: Int): LiveDetectedQuad {
        if (w < 48 || h < 48 || rawLum.size < w * h) {
            return LiveDetectedQuad(ScanQuad(), 0f, false)
        }

        // 3x3 box blur suppresses inner text strokes while keeping outer document edges sharp
        val lum = FloatArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - 1)
            val y1 = min(h - 1, y + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - 1)
                val x1 = min(w - 1, x + 1)
                var sum = 0f
                var cnt = 0
                for (yy in y0..y1) {
                    val row = yy * w
                    for (xx in x0..x1) {
                        sum += rawLum[row + xx]
                        cnt++
                    }
                }
                lum[y * w + x] = sum / cnt
            }
        }

        // Center paper vs border desk luminance difference
        var centerSum = 0f
        var centerCnt = 0
        for (y in (h * 0.34f).toInt()..(h * 0.66f).toInt()) {
            for (x in (w * 0.34f).toInt()..(w * 0.66f).toInt()) {
                centerSum += lum[y * w + x]
                centerCnt++
            }
        }
        val centerLum = centerSum / max(1, centerCnt)

        var borderSum = 0f
        var borderCnt = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (y < 4 || y >= h - 4 || x < 4 || x >= w - 4) {
                    borderSum += lum[y * w + x]
                    borderCnt++
                }
            }
        }
        val borderLum = borderSum / max(1, borderCnt)
        val paperBrighter = centerLum >= borderLum

        val gxArr = FloatArray(w * h)
        val gyArr = FloatArray(w * h)
        val grad = FloatArray(w * h)
        var maxGrad = 1f
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val gx =
                    -lum[(y - 1) * w + (x - 1)] + lum[(y - 1) * w + (x + 1)] -
                        2f * lum[row + (x - 1)] + 2f * lum[row + (x + 1)] -
                        lum[(y + 1) * w + (x - 1)] + lum[(y + 1) * w + (x + 1)]
                val gy =
                    -lum[(y - 1) * w + (x - 1)] - 2f * lum[(y - 1) * w + x] - lum[(y - 1) * w + (x + 1)] +
                        lum[(y + 1) * w + (x - 1)] + 2f * lum[(y + 1) * w + x] + lum[(y + 1) * w + (x + 1)]
                val mag = hypot(gx, gy)
                gxArr[row + x] = gx
                gyArr[row + x] = gy
                grad[row + x] = mag
                if (mag > maxGrad) maxGrad = mag
            }
        }

        val minEdge = max(22f, maxGrad * 0.14f)

        fun edgeContrastScore(x: Int, y: Int, inDx: Int, inDy: Int, isHorizontalEdge: Boolean): Float {
            val idx = y * w + x
            val mag = grad[idx]
            if (mag < minEdge) return -1f
            val agx = abs(gxArr[idx])
            val agy = abs(gyArr[idx])
            if (isHorizontalEdge && agy < agx * 0.55f) return -1f
            if (!isHorizontalEdge && agx < agy * 0.55f) return -1f

            val inX = (x + inDx * 5).coerceIn(0, w - 1)
            val inY = (y + inDy * 5).coerceIn(0, h - 1)
            val outX = (x - inDx * 5).coerceIn(0, w - 1)
            val outY = (y - inDy * 5).coerceIn(0, h - 1)
            val diff = lum[inY * w + inX] - lum[outY * w + outX]
            val polarityBonus = if (paperBrighter) max(0f, diff) else abs(diff)
            return mag + polarityBonus * 1.15f
        }

        val topPts = ArrayList<Pair<Float, Float>>()
        val botPts = ArrayList<Pair<Float, Float>>()
        val leftPts = ArrayList<Pair<Float, Float>>()
        val rightPts = ArrayList<Pair<Float, Float>>()

        val xStep = max(2, w / 42)
        val yStep = max(2, h / 42)
        var totalSweeps = 0

        for (x in (w * 0.14f).toInt() until (w * 0.86f).toInt() step xStep) {
            totalSweeps += 2
            var bestTopScore = -1f
            var bestTopY = -1
            for (y in 4 until (h * 0.48f).toInt()) {
                val s = edgeContrastScore(x, y, 0, 1, true)
                if (s > bestTopScore) {
                    bestTopScore = s
                    bestTopY = y
                }
                if (bestTopScore > minEdge * 2.8f && s < bestTopScore * 0.4f) break
            }
            if (bestTopY >= 0) topPts.add(x.toFloat() to bestTopY.toFloat())

            var bestBotScore = -1f
            var bestBotY = -1
            for (y in h - 5 downTo (h * 0.52f).toInt()) {
                val s = edgeContrastScore(x, y, 0, -1, true)
                if (s > bestBotScore) {
                    bestBotScore = s
                    bestBotY = y
                }
                if (bestBotScore > minEdge * 2.8f && s < bestBotScore * 0.4f) break
            }
            if (bestBotY >= 0) botPts.add(x.toFloat() to bestBotY.toFloat())
        }

        for (y in (h * 0.14f).toInt() until (h * 0.86f).toInt() step yStep) {
            totalSweeps += 2
            var bestLeftScore = -1f
            var bestLeftX = -1
            for (x in 4 until (w * 0.48f).toInt()) {
                val s = edgeContrastScore(x, y, 1, 0, false)
                if (s > bestLeftScore) {
                    bestLeftScore = s
                    bestLeftX = x
                }
                if (bestLeftScore > minEdge * 2.8f && s < bestLeftScore * 0.4f) break
            }
            if (bestLeftX >= 0) leftPts.add(bestLeftX.toFloat() to y.toFloat())

            var bestRightScore = -1f
            var bestRightX = -1
            for (x in w - 5 downTo (w * 0.52f).toInt()) {
                val s = edgeContrastScore(x, y, -1, 0, false)
                if (s > bestRightScore) {
                    bestRightScore = s
                    bestRightX = x
                }
                if (bestRightScore > minEdge * 2.8f && s < bestRightScore * 0.4f) break
            }
            if (bestRightX >= 0) rightPts.add(bestRightX.toFloat() to y.toFloat())
        }

        data class Line2D(val a: Float, val b: Float, val c: Float, val strong: Boolean)

        fun fitHorizontalLine(pts: List<Pair<Float, Float>>, defaultY: Float): Line2D {
            if (pts.size < 4) return Line2D(0f, -1f, defaultY, false)
            fun ols(subset: List<Pair<Float, Float>>): Pair<Float, Float> {
                var sx = 0f; var sy = 0f; var sxx = 0f; var sxy = 0f
                for ((px, py) in subset) {
                    sx += px; sy += py; sxx += px * px; sxy += px * py
                }
                val n = subset.size.toFloat()
                val denom = n * sxx - sx * sx
                val m = if (abs(denom) > 1e-4f) (n * sxy - sx * sy) / denom else 0f
                val b = (sy - m * sx) / n
                return m.coerceIn(-1.2f, 1.2f) to b
            }
            val (m1, b1) = ols(pts)
            val residuals = pts.map { (px, py) -> abs(py - (m1 * px + b1)) }.sorted()
            val cutoff = max(3.5f, residuals[(residuals.size * 0.65f).toInt().coerceIn(0, residuals.lastIndex)])
            val inliers = pts.filter { (px, py) -> abs(py - (m1 * px + b1)) <= cutoff }
            val (m2, b2) = if (inliers.size >= 3) ols(inliers) else (m1 to b1)
            return Line2D(m2, -1f, b2, inliers.size >= 5)
        }

        fun fitVerticalLine(pts: List<Pair<Float, Float>>, defaultX: Float): Line2D {
            if (pts.size < 4) return Line2D(-1f, 0f, defaultX, false)
            fun ols(subset: List<Pair<Float, Float>>): Pair<Float, Float> {
                var sx = 0f; var sy = 0f; var syy = 0f; var sxy = 0f
                for ((px, py) in subset) {
                    sx += px; sy += py; syy += py * py; sxy += px * py
                }
                val n = subset.size.toFloat()
                val denom = n * syy - sy * sy
                val m = if (abs(denom) > 1e-4f) (n * sxy - sy * sx) / denom else 0f
                val b = (sx - m * sy) / n
                return m.coerceIn(-1.2f, 1.2f) to b
            }
            val (m1, b1) = ols(pts)
            val residuals = pts.map { (px, py) -> abs(px - (m1 * py + b1)) }.sorted()
            val cutoff = max(3.5f, residuals[(residuals.size * 0.65f).toInt().coerceIn(0, residuals.lastIndex)])
            val inliers = pts.filter { (px, py) -> abs(px - (m1 * py + b1)) <= cutoff }
            val (m2, b2) = if (inliers.size >= 3) ols(inliers) else (m1 to b1)
            return Line2D(-1f, m2, b2, inliers.size >= 5)
        }

        fun intersect(l1: Line2D, l2: Line2D): NormalizedPoint? {
            val det = l1.a * l2.b - l2.a * l1.b
            if (abs(det) < 1e-5f) return null
            val x = (l1.b * l2.c - l2.b * l1.c) / det
            val y = (l2.a * l1.c - l1.a * l2.c) / det
            return NormalizedPoint(
                x = (x / w).coerceIn(0.01f, 0.99f),
                y = (y / h).coerceIn(0.01f, 0.99f)
            )
        }

        val topLine = fitHorizontalLine(topPts, h * 0.05f)
        val botLine = fitHorizontalLine(botPts, h * 0.95f)
        val leftLine = fitVerticalLine(leftPts, w * 0.05f)
        val rightLine = fitVerticalLine(rightPts, w * 0.95f)

        val tl = intersect(topLine, leftLine) ?: return LiveDetectedQuad(ScanQuad(), 0f, false)
        val tr = intersect(topLine, rightLine) ?: return LiveDetectedQuad(ScanQuad(), 0f, false)
        val br = intersect(botLine, rightLine) ?: return LiveDetectedQuad(ScanQuad(), 0f, false)
        val bl = intersect(botLine, leftLine) ?: return LiveDetectedQuad(ScanQuad(), 0f, false)

        val minWidthTop = tr.x - tl.x
        val minWidthBot = br.x - bl.x
        val minHeightLeft = bl.y - tl.y
        val minHeightRight = br.y - tr.y

        if (minWidthTop < 0.25f || minWidthBot < 0.25f || minHeightLeft < 0.25f || minHeightRight < 0.25f) {
            return LiveDetectedQuad(ScanQuad(), 0.1f, false)
        }

        val areaApprox = ((minWidthTop + minWidthBot) * 0.5f) * ((minHeightLeft + minHeightRight) * 0.5f)
        if (areaApprox < 0.18f) {
            return LiveDetectedQuad(ScanQuad(), 0.1f, false)
        }

        val sideSupport = (if (topLine.strong) 1 else 0) +
            (if (botLine.strong) 1 else 0) +
            (if (leftLine.strong) 1 else 0) +
            (if (rightLine.strong) 1 else 0)
        val hitRatio = (topPts.size + botPts.size + leftPts.size + rightPts.size).toFloat() / max(1, totalSweeps)
        val contrastNorm = min(1f, abs(centerLum - borderLum) / 55f)
        val confidence = min(1f, sideSupport * 0.18f + hitRatio * 0.35f + contrastNorm * 0.25f)

        return LiveDetectedQuad(
            quad = ScanQuad(tl = tl, tr = tr, br = br, bl = bl),
            confidence = confidence,
            isLocked = sideSupport >= 3 && confidence >= 0.45f
        )
    }

    suspend fun warpAndEnhanceScan(
        context: Context,
        sourceUri: Uri,
        quad: ScanQuad,
        filterMode: ScanFilterMode,
        rotationDeg: Int
    ): ProcessedScanResult = withContext(Dispatchers.Default) {
        val srcBitmap = decodeUprightBitmap(context, sourceUri, MAX_SOURCE_DIM)
            ?: error("Unable to decode image for scanning")

        val sw = srcBitmap.width.toFloat()
        val sh = srcBitmap.height.toFloat()

        val pTLx = quad.tl.x.coerceIn(0f, 1f) * sw
        val pTLy = quad.tl.y.coerceIn(0f, 1f) * sh
        val pTRx = quad.tr.x.coerceIn(0f, 1f) * sw
        val pTRy = quad.tr.y.coerceIn(0f, 1f) * sh
        val pBRx = quad.br.x.coerceIn(0f, 1f) * sw
        val pBRy = quad.br.y.coerceIn(0f, 1f) * sh
        val pBLx = quad.bl.x.coerceIn(0f, 1f) * sw
        val pBLy = quad.bl.y.coerceIn(0f, 1f) * sh

        val topW = hypot(pTRx - pTLx, pTRy - pTLy)
        val botW = hypot(pBRx - pBLx, pBRy - pBLy)
        val leftH = hypot(pBLx - pTLx, pBLy - pTLy)
        val rightH = hypot(pBRx - pTRx, pBRy - pTRy)

        val estW = max(topW, botW).coerceAtLeast(240f)
        val estH = max(leftH, rightH).coerceAtLeast(300f)

        // Snap slightly close ratios toward crisp A4 portrait or landscape proportions while keeping fidelity
        val rawAspect = estW / estH
        val targetAspect = when {
            abs(rawAspect - A4_RATIO) < 0.14f -> A4_RATIO
            abs(rawAspect - (1f / A4_RATIO)) < 0.14f -> 1f / A4_RATIO
            else -> rawAspect.coerceIn(0.45f, 2.2f)
        }

        val dstH = 1650
        val dstW = (dstH * targetAspect).roundToInt().coerceIn(600, 2000)

        val warped = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warped)
        canvas.drawColor(Color.WHITE)

        val matrix = Matrix()
        val srcPts = floatArrayOf(
            pTLx, pTLy,
            pTRx, pTRy,
            pBRx, pBRy,
            pBLx, pBLy
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            dstW.toFloat(), 0f,
            dstW.toFloat(), dstH.toFloat(),
            0f, dstH.toFloat()
        )
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(srcBitmap, matrix, paint)
        srcBitmap.recycle()

        val normRot = ((rotationDeg % 360) + 360) % 360
        val rotated = if (normRot != 0) {
            val rotMatrix = Matrix().apply { postRotate(normRot.toFloat()) }
            val out = Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, rotMatrix, true)
            warped.recycle()
            out
        } else {
            warped
        }

        applyScanClarityFilter(rotated, filterMode)

        val outDir = File(context.filesDir, "scanned_pages").apply { mkdirs() }
        val outFile = File(outDir, "scan_${UUID.randomUUID().toString().take(10)}.jpg")
        FileOutputStream(outFile).use { fos ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 92, fos)
        }
        val outW = rotated.width
        val outH = rotated.height
        rotated.recycle()

        ProcessedScanResult(
            outputUri = Uri.fromFile(outFile),
            width = outW,
            height = outH
        )
    }

    private fun applyScanClarityFilter(bitmap: Bitmap, filterMode: ScanFilterMode) {
        if (filterMode == ScanFilterMode.ORIGINAL) return

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute luminance histogram to adaptively pull paper background to clean white
        val hist = IntArray(256)
        for (i in pixels.indices step 2) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val l = ((77 * r + 150 * g + 29 * b) shr 8).coerceIn(0, 255)
            hist[l]++
        }

        val totalSamples = (pixels.size + 1) / 2
        var acc = 0
        var blackPt = 18
        var whitePt = 220

        for (i in 0..255) {
            acc += hist[i]
            if (acc >= totalSamples * 0.02f) {
                blackPt = i
                break
            }
        }
        acc = 0
        for (i in 255 downTo 0) {
            acc += hist[i]
            if (acc >= totalSamples * 0.16f) {
                whitePt = i
                break
            }
        }

        val lo = blackPt.coerceIn(5, 85).toFloat()
        val hi = max(lo + 55f, whitePt.coerceIn(145, 248).toFloat())
        val invRange = 255f / (hi - lo)

        if (filterMode == ScanFilterMode.MAGIC) {
            for (i in pixels.indices) {
                val c = pixels[i]
                var r = (((c shr 16) and 0xFF) - lo) * invRange
                var g = (((c shr 8) and 0xFF) - lo) * invRange
                var b = ((c and 0xFF) - lo) * invRange

                // Lift bright paper regions gently to pure clean white while keeping ink sharp
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum > 188f) {
                    val push = ((lum - 188f) / 67f).coerceIn(0f, 1f)
                    r += (255f - r) * push * 0.85f
                    g += (255f - g) * push * 0.85f
                    b += (255f - b) * push * 0.85f
                } else if (lum < 115f) {
                    // Deepen ink strokes
                    r *= 0.86f
                    g *= 0.86f
                    b *= 0.86f
                }

                val ir = r.roundToInt().coerceIn(0, 255)
                val ig = g.roundToInt().coerceIn(0, 255)
                val ib = b.roundToInt().coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        } else {
            // ScanFilterMode.BW -> High-contrast crisp grayscale / ink-on-white
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val rawLum = 0.299f * r + 0.587f * g + 0.114f * b
                var v = (rawLum - lo) * invRange
                v = when {
                    v >= 176f -> 255f
                    v <= 72f -> v * 0.55f
                    else -> ((v - 72f) / 104f) * 215f + 40f
                }
                val iv = v.roundToInt().coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (iv shl 16) or (iv shl 8) or iv
            }
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    fun decodeUprightBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        var longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sampleSize > maxDim * 1.4f) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> {}
        }

        val oriented = if (!matrix.isIdentity) {
            val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            raw.recycle()
            out
        } else {
            raw
        }

        val curMax = max(oriented.width, oriented.height)
        if (curMax > maxDim) {
            val scale = maxDim.toFloat() / curMax.toFloat()
            val scaled = Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).roundToInt().coerceAtLeast(1),
                (oriented.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
            oriented.recycle()
            return scaled
        }
        return oriented
    }
}
