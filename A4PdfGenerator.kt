package com.pdfmakerstudio.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import com.pdfmakerstudio.app.domain.model.DocumentSignature
import com.pdfmakerstudio.app.domain.model.GlobalImagePdfOptions
import com.pdfmakerstudio.app.domain.model.ImagePageItem
import com.pdfmakerstudio.app.domain.model.ImageResolutionPreset
import com.pdfmakerstudio.app.domain.model.ImageSizeMode
import com.pdfmakerstudio.app.domain.model.PageTextPlacement
import com.pdfmakerstudio.app.domain.model.RichFontFamily
import com.pdfmakerstudio.app.domain.model.RichPdfBlock
import com.pdfmakerstudio.app.domain.model.RichTextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class GeneratedPdfResult(
    val file: File,
    val pageCount: Int,
    val sizeBytes: Long
)

data class ImageInspectionInfo(
    val originalBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val estimatedBytes: Long
)

class A4PdfGenerator(private val context: Context) {

    companion object {
        // Standard A4 size in PostScript points (72 dpi): 595 x 842
        const val A4_WIDTH = 595
        const val A4_HEIGHT = 842
        private const val MM_TO_PT = 72f / 25.4f
        private const val MARGIN = 44f
        private const val FOOTER_HEIGHT = 28f
    }

    suspend fun createPdfFromImages(
        imageUris: List<Uri>,
        fileNamePrefix: String = "PDF_Maker_Studio"
    ): Result<GeneratedPdfResult> {
        val items = imageUris.map { ImagePageItem(imageUri = it) }
        return createPdfFromConfiguredImages(items, GlobalImagePdfOptions(), fileNamePrefix)
    }

    suspend fun inspectImageSummary(
        uri: Uri,
        preset: ImageResolutionPreset,
        customMaxPx: Int,
        quality: Int
    ): ImageInspectionInfo = withContext(Dispatchers.IO) {
        val origBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.coerceAtLeast(0L) ?: 0L
        val bitmap = decodeAndResizeForPage(uri, 0, preset, customMaxPx, 1f)
            ?: return@withContext ImageInspectionInfo(origBytes, 0, 0, 0, 0, origBytes)
        val outW = bitmap.width
        val outH = bitmap.height
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(15, 100), baos)
        bitmap.recycle()
        ImageInspectionInfo(
            originalBytes = origBytes,
            originalWidth = outW,
            originalHeight = outH,
            outputWidth = outW,
            outputHeight = outH,
            estimatedBytes = baos.size().toLong()
        )
    }

    suspend fun createPdfFromConfiguredImages(
        pages: List<ImagePageItem>,
        globalOptions: GlobalImagePdfOptions,
        fileNamePrefix: String = "PDF_Maker_Studio"
    ): Result<GeneratedPdfResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(pages.isNotEmpty()) { "Select at least one image" }
            var result = renderConfiguredImagesPass(pages, globalOptions, fileNamePrefix, 1f, 1f)
            val targetMb = globalOptions.targetMaxMb
            if (targetMb != null && targetMb > 0f) {
                val targetBytes = (targetMb * 1024f * 1024f).toLong()
                val passes = listOf(0.72f to 0.90f, 0.52f to 0.78f, 0.36f to 0.65f, 0.24f to 0.50f)
                for ((qMult, rMult) in passes) {
                    if (result.sizeBytes <= targetBytes) break
                    runCatching { result.file.delete() }
                    result = renderConfiguredImagesPass(pages, globalOptions, fileNamePrefix, qMult, rMult)
                }
            }
            result
        }
    }

    private fun renderConfiguredImagesPass(
        pages: List<ImagePageItem>,
        globalOptions: GlobalImagePdfOptions,
        fileNamePrefix: String,
        qualityMultiplier: Float,
        resMultiplier: Float
    ): GeneratedPdfResult {
        val pdfDocument = PdfDocument()
        try {
            val margin = globalOptions.marginMode.marginPt
            val footerReserve = if (globalOptions.showPageNumbers) 24f else if (margin > 0f) 8f else 0f
            val bgPaint = Paint().apply { color = Color.WHITE }
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(110, 118, 129)
                textSize = 9.5f
                textAlign = Paint.Align.CENTER
                typeface = resolveTypeface(RichFontFamily.BENGALI_SANS, bold = false, italic = false)
            }

            var pageNumber = 0
            for (item in pages) {
                val rawBitmap = decodeAndResizeForPage(
                    uri = item.imageUri,
                    rotationDeg = item.rotationDegrees,
                    preset = item.resolutionPreset,
                    customMaxPx = item.customMaxDimPx,
                    resMultiplier = resMultiplier
                ) ?: continue

                // Compress via JPEG quality before embedding onto A4 canvas
                val q = (item.jpegQuality * qualityMultiplier).toInt().coerceIn(15, 100)
                val baos = ByteArrayOutputStream()
                rawBitmap.compress(Bitmap.CompressFormat.JPEG, q, baos)
                rawBitmap.recycle()
                val jpegBytes = baos.toByteArray()
                val compressedBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    ?: continue

                pageNumber += 1
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), A4_HEIGHT.toFloat(), bgPaint)

                val contentWidth = (A4_WIDTH - margin * 2).coerceAtLeast(60f)
                var topY = margin
                var bottomY = A4_HEIGHT - max(margin, footerReserve)

                val hasText = item.pageText.isNotBlank()
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = item.textColorHex.toInt()
                    textSize = item.textSizeSp.coerceIn(10f, 28f)
                    typeface = resolveTypeface(item.textFontFamily, item.isBold, item.isItalic)
                }
                val textLayout = if (hasText) {
                    StaticLayout.Builder
                        .obtain(item.pageText.trim(), 0, item.pageText.trim().length, textPaint, contentWidth.toInt())
                        .setAlignment(item.textAlign.toLayoutAlignment())
                        .setLineSpacing(3f, 1.3f)
                        .build()
                } else null

                if (textLayout != null && item.textPlacement == PageTextPlacement.TOP) {
                    canvas.save()
                    canvas.translate(margin, topY)
                    textLayout.draw(canvas)
                    canvas.restore()
                    topY += textLayout.height + 10f
                } else if (textLayout != null && item.textPlacement == PageTextPlacement.BOTTOM) {
                    val textY = bottomY - textLayout.height
                    canvas.save()
                    canvas.translate(margin, textY)
                    textLayout.draw(canvas)
                    canvas.restore()
                    bottomY = textY - 10f
                }

                val availW = contentWidth
                val availH = (bottomY - topY).coerceAtLeast(80f)

                val drawRect = when (item.sizeMode) {
                    ImageSizeMode.FILL_PAGE -> {
                        val fullTop = if (item.textPlacement == PageTextPlacement.TOP && textLayout != null) topY else 0f
                        val fullBottom = if (item.textPlacement == PageTextPlacement.BOTTOM && textLayout != null) {
                            bottomY
                        } else if (globalOptions.showPageNumbers) {
                            A4_HEIGHT - 24f
                        } else {
                            A4_HEIGHT.toFloat()
                        }
                        val fullH = (fullBottom - fullTop).coerceAtLeast(80f)
                        val s = min(A4_WIDTH / compressedBitmap.width.toFloat(), fullH / compressedBitmap.height.toFloat())
                        val w = compressedBitmap.width * s
                        val h = compressedBitmap.height * s
                        val l = (A4_WIDTH - w) / 2f
                        val t = fullTop + (fullH - h) / 2f
                        RectF(l, t, l + w, t + h)
                    }
                    ImageSizeMode.CUSTOM_SCALE -> {
                        val pct = item.customScalePercent.coerceIn(20, 100) / 100f
                        val s = min(availW / compressedBitmap.width.toFloat(), availH / compressedBitmap.height.toFloat())
                        val w = compressedBitmap.width * s * pct
                        val h = compressedBitmap.height * s * pct
                        val l = margin + (availW - w) / 2f
                        val t = topY + (availH - h) / 2f
                        RectF(l, t, l + w, t + h)
                    }
                    ImageSizeMode.CUSTOM_MM -> {
                        val reqW = min(availW, max(40f, item.customWidthMm * MM_TO_PT))
                        val reqH = min(availH, max(40f, item.customHeightMm * MM_TO_PT))
                        val s = min(reqW / compressedBitmap.width.toFloat(), reqH / compressedBitmap.height.toFloat())
                        val w = compressedBitmap.width * s
                        val h = compressedBitmap.height * s
                        val l = margin + (availW - w) / 2f
                        val t = topY + (availH - h) / 2f
                        RectF(l, t, l + w, t + h)
                    }
                    ImageSizeMode.AUTO_FIT -> {
                        val s = min(availW / compressedBitmap.width.toFloat(), availH / compressedBitmap.height.toFloat())
                        val w = compressedBitmap.width * s
                        val h = compressedBitmap.height * s
                        val l = margin + (availW - w) / 2f
                        val t = topY + (availH - h) / 2f
                        RectF(l, t, l + w, t + h)
                    }
                }

                val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(compressedBitmap, null, drawRect, imgPaint)
                compressedBitmap.recycle()

                if (globalOptions.showPageNumbers) {
                    canvas.drawText(
                        String.format(Locale.US, "%d / %d", pageNumber, pages.size),
                        A4_WIDTH / 2f,
                        A4_HEIGHT - 12f,
                        footerPaint
                    )
                }
                pdfDocument.finishPage(page)
            }

            require(pageNumber > 0) { "Could not decode any selected images" }
            val outFile = createOutputFile(fileNamePrefix)
            FileOutputStream(outFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            GeneratedPdfResult(
                file = outFile,
                pageCount = pageNumber,
                sizeBytes = outFile.length()
            )
        } finally {
            pdfDocument.close()
        }
    }

    private fun decodeAndResizeForPage(
        uri: Uri,
        rotationDeg: Int,
        preset: ImageResolutionPreset,
        customMaxPx: Int,
        resMultiplier: Float
    ): Bitmap? {
        val raw = decodeSampledRotatedBitmap(uri, 2800, 2800) ?: return null
        val oriented = if (rotationDeg % 360 != 0) {
            val m = Matrix().apply { postRotate((rotationDeg % 360).toFloat()) }
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            if (rotated != raw) raw.recycle()
            rotated
        } else raw

        val totalPx = max(1, oriented.width * oriented.height).toFloat()
        val baseScale = when (preset) {
            ImageResolutionPreset.ORIGINAL -> 1f
            ImageResolutionPreset.CUSTOM_PX -> {
                val maxAllowed = customMaxPx.coerceIn(240, 4000).toFloat()
                min(1f, maxAllowed / max(oriented.width, oriented.height).toFloat())
            }
            else -> {
                val mp = preset.targetMp ?: 2f
                min(1f, sqrt((mp * 1_000_000f) / totalPx))
            }
        }
        val finalScale = (baseScale * resMultiplier).coerceIn(0.1f, 1f)
        if (finalScale >= 0.98f) return oriented

        val targetW = max(1, (oriented.width * finalScale).toInt())
        val targetH = max(1, (oriented.height * finalScale).toInt())
        val scaled = Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
        if (scaled != oriented) oriented.recycle()
        return scaled
    }

    suspend fun createPdfFromText(
        title: String,
        body: String
    ): Result<GeneratedPdfResult> {
        return createPdfFromRichBlocks(
            title = title,
            blocks = listOf(RichPdfBlock.TextBlock(text = body))
        )
    }

    /**
     * Renders multi-block rich documents onto paginated A4 pages with full support for
     * Bengali & English fonts, bold, italic, underline, custom colors, alignment, icons,
     * dividers, and embedded photos.
     */
    suspend fun createPdfFromRichBlocks(
        title: String,
        blocks: List<RichPdfBlock>,
        titleColorHex: Long = 0xFF0F172A,
        titleFontFamily: RichFontFamily = RichFontFamily.BENGALI_SANS,
        titleAlign: RichTextAlign = RichTextAlign.LEFT,
        signatures: List<DocumentSignature> = emptyList()
    ): Result<GeneratedPdfResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = title.trim()
            val hasAnyContent = cleanTitle.isNotEmpty() || signatures.isNotEmpty() || blocks.any {
                when (it) {
                    is RichPdfBlock.TextBlock -> it.text.isNotBlank()
                    is RichPdfBlock.ImageBlock -> true
                    is RichPdfBlock.DividerBlock -> true
                    is RichPdfBlock.PageBreakBlock -> false
                }
            }
            require(hasAnyContent) { "Document content cannot be empty" }

            val pdfDocument = PdfDocument()
            try {
                val contentWidth = (A4_WIDTH - MARGIN * 2).toInt()
                val maxBodyBottomY = A4_HEIGHT - MARGIN - FOOTER_HEIGHT
                val bgPaint = Paint().apply { color = Color.WHITE }
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(100, 116, 139)
                    textSize = 9.5f
                    textAlign = Paint.Align.CENTER
                    typeface = resolveTypeface(RichFontFamily.BENGALI_SANS, bold = false, italic = false)
                }

                var pageNumber = 1
                var visualPageIdx = 0
                val drawnSigIds = mutableSetOf<String>()
                var currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
                )
                var canvas: Canvas = currentPage.canvas
                canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), A4_HEIGHT.toFloat(), bgPaint)
                var cursorY = MARGIN

                fun drawSignaturesForVisualPage(targetCanvas: Canvas, targetVisualIdx: Int, includeRemaining: Boolean = false) {
                    val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    val orderedSigs = signatures.sortedBy { if (it.widthFraction >= 0.94f) 0 else 1 }
                    for (sig in orderedSigs) {
                        if (sig.id in drawnSigIds) continue
                        if (!includeRemaining && sig.pageIndex != targetVisualIdx) continue
                        val sigFile = File(sig.imagePath)
                        if (!sigFile.exists()) continue
                        val bmp = decodeSampledFileBitmap(sigFile, 1600, 1600) ?: continue
                        val isFullSheet = sig.widthFraction >= 0.94f
                        var drawW = (sig.widthFraction.coerceIn(0.08f, 1.0f)) * A4_WIDTH
                        val ratio = if (sig.aspectRatio > 0f) sig.aspectRatio else bmp.height.toFloat() / max(1, bmp.width).toFloat()
                        var drawH = max(18f, drawW * ratio)
                        val maxAllowedH = if (isFullSheet) A4_HEIGHT.toFloat() else (A4_HEIGHT - 16f)
                        if (drawH > maxAllowedH) {
                            val shrink = maxAllowedH / drawH
                            drawH = maxAllowedH
                            drawW *= shrink
                        }
                        val minInset = if (isFullSheet) 0f else 8f
                        val drawX = (sig.xFraction * A4_WIDTH).coerceIn(minInset, (A4_WIDTH - drawW - minInset).coerceAtLeast(minInset))
                        val drawY = (sig.yFraction * A4_HEIGHT).coerceIn(minInset, (A4_HEIGHT - drawH - minInset).coerceAtLeast(minInset))
                        targetCanvas.drawBitmap(bmp, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), imgPaint)
                        bmp.recycle()
                        drawnSigIds.add(sig.id)
                    }
                }

                fun nextPage(isManualBreak: Boolean = false) {
                    val hasFullSheet = signatures.any { it.pageIndex == visualPageIdx && it.widthFraction >= 0.94f }
                    if (isManualBreak) {
                        drawSignaturesForVisualPage(canvas, visualPageIdx)
                    }
                    if (!hasFullSheet) {
                        canvas.drawText(
                            String.format(Locale.US, "Page %d", pageNumber),
                            A4_WIDTH / 2f,
                            A4_HEIGHT - 18f,
                            footerPaint
                        )
                    }
                    pdfDocument.finishPage(currentPage)
                    pageNumber += 1
                    if (isManualBreak) {
                        visualPageIdx += 1
                    }
                    currentPage = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
                    )
                    canvas = currentPage.canvas
                    canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), A4_HEIGHT.toFloat(), bgPaint)
                    cursorY = MARGIN
                }

                fun ensureSpace(neededHeight: Float) {
                    if (cursorY + neededHeight > maxBodyBottomY && cursorY > MARGIN + 4f) {
                        nextPage(isManualBreak = false)
                    }
                }

                // Draw Title
                if (cleanTitle.isNotEmpty()) {
                    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = titleColorHex.toInt()
                        textSize = 20f
                        typeface = resolveTypeface(titleFontFamily, bold = true, italic = false)
                    }
                    val titleLayout = StaticLayout.Builder
                        .obtain(cleanTitle, 0, cleanTitle.length, titlePaint, contentWidth)
                        .setAlignment(titleAlign.toLayoutAlignment())
                        .setLineSpacing(4f, 1.2f)
                        .setIncludePad(true)
                        .build()

                    ensureSpace(titleLayout.height + 18f)
                    canvas.save()
                    canvas.translate(MARGIN, cursorY)
                    titleLayout.draw(canvas)
                    canvas.restore()

                    cursorY += titleLayout.height + 8f
                    val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(203, 213, 225)
                        strokeWidth = 1f
                    }
                    canvas.drawLine(MARGIN, cursorY, A4_WIDTH - MARGIN, cursorY, rulePaint)
                    cursorY += 14f
                }

                // Draw Rich Blocks
                for (block in blocks) {
                    when (block) {
                        is RichPdfBlock.PageBreakBlock -> {
                            nextPage(isManualBreak = true)
                        }

                        is RichPdfBlock.DividerBlock -> {
                            ensureSpace(18f)
                            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = block.colorHex.toInt()
                                strokeWidth = 1.2f
                            }
                            canvas.drawLine(MARGIN, cursorY + 6f, A4_WIDTH - MARGIN, cursorY + 6f, linePaint)
                            cursorY += 16f
                        }

                        is RichPdfBlock.ImageBlock -> {
                            val bitmap = decodeSampledRotatedBitmap(block.imageUri, 1400, 1400)
                                ?: continue
                            val targetW = contentWidth * block.widthFraction.coerceIn(0.4f, 1f)
                            val maxH = (maxBodyBottomY - MARGIN - 40f).coerceAtLeast(120f)
                            val scale = min(targetW / bitmap.width.toFloat(), maxH / bitmap.height.toFloat())
                            val drawW = bitmap.width * scale
                            val drawH = bitmap.height * scale
                            val captionExtra = if (block.caption.isNotBlank()) 22f else 0f

                            ensureSpace(drawH + captionExtra + 12f)
                            val left = when (block.align) {
                                RichTextAlign.LEFT -> MARGIN
                                RichTextAlign.CENTER -> MARGIN + (contentWidth - drawW) / 2f
                                RichTextAlign.RIGHT -> A4_WIDTH - MARGIN - drawW
                            }

                            val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                            canvas.drawBitmap(bitmap, null, RectF(left, cursorY, left + drawW, cursorY + drawH), imgPaint)
                            bitmap.recycle()
                            cursorY += drawH + 6f

                            if (block.caption.isNotBlank()) {
                                val capPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.rgb(100, 116, 139)
                                    textSize = 10.5f
                                    typeface = resolveTypeface(RichFontFamily.BENGALI_SANS, bold = false, italic = true)
                                }
                                val capLayout = StaticLayout.Builder
                                    .obtain(block.caption, 0, block.caption.length, capPaint, contentWidth)
                                    .setAlignment(block.align.toLayoutAlignment())
                                    .build()
                                canvas.save()
                                canvas.translate(MARGIN, cursorY)
                                capLayout.draw(canvas)
                                canvas.restore()
                                cursorY += capLayout.height + 10f
                            } else {
                                cursorY += 8f
                            }
                        }

                        is RichPdfBlock.TextBlock -> {
                            val combined = if (!block.leadingIcon.isNullOrBlank()) {
                                "${block.leadingIcon}  ${block.text}"
                            } else {
                                block.text
                            }
                            if (combined.isBlank()) continue

                            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = block.colorHex.toInt()
                                textSize = block.fontSizeSp.coerceIn(10f, 32f)
                                isUnderlineText = block.isUnderline
                                typeface = resolveTypeface(block.fontFamily, block.isBold, block.isItalic, block.customFontName)
                            }
                            val layout = StaticLayout.Builder
                                .obtain(combined, 0, combined.length, paint, contentWidth)
                                .setAlignment(block.align.toLayoutAlignment())
                                .setLineSpacing(4f, 1.35f)
                                .setIncludePad(true)
                                .build()

                            var lineIdx = 0
                            while (lineIdx < layout.lineCount) {
                                val available = (maxBodyBottomY - cursorY)
                                val firstLineTop = layout.getLineTop(lineIdx)
                                val firstLineHeight = layout.getLineBottom(lineIdx) - firstLineTop
                                if (firstLineHeight > available && cursorY > MARGIN + 4f) {
                                    nextPage()
                                    continue
                                }

                                val currentAvail = (maxBodyBottomY - cursorY).coerceAtLeast(firstLineHeight.toFloat())
                                var endLine = lineIdx
                                while (endLine < layout.lineCount) {
                                    val sliceH = layout.getLineBottom(endLine) - firstLineTop
                                    if (sliceH > currentAvail && endLine > lineIdx) break
                                    endLine++
                                }

                                val lastInclusive = max(lineIdx, endLine - 1)
                                val sliceHeight = (layout.getLineBottom(lastInclusive) - firstLineTop).toFloat()

                                canvas.save()
                                canvas.translate(MARGIN, cursorY)
                                canvas.clipRect(0f, 0f, contentWidth.toFloat(), sliceHeight)
                                canvas.translate(0f, -firstLineTop.toFloat())
                                layout.draw(canvas)
                                canvas.restore()

                                cursorY += sliceHeight
                                lineIdx = endLine
                                if (lineIdx < layout.lineCount) {
                                    nextPage()
                                }
                            }
                            cursorY += 10f
                        }
                    }
                }

                // Draw signatures on final page + any remaining undrawn signatures
                val hasFullSheetFinal = signatures.any { it.pageIndex >= visualPageIdx && it.widthFraction >= 0.94f }
                drawSignaturesForVisualPage(canvas, visualPageIdx, includeRemaining = true)
                if (!hasFullSheetFinal) {
                    canvas.drawText(
                        String.format(Locale.US, "Page %d", pageNumber),
                        A4_WIDTH / 2f,
                        A4_HEIGHT - 18f,
                        footerPaint
                    )
                }
                pdfDocument.finishPage(currentPage)

                val outFile = createOutputFile("rich_pdf")
                FileOutputStream(outFile).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }

                GeneratedPdfResult(
                    file = outFile,
                    pageCount = pageNumber,
                    sizeBytes = outFile.length()
                )
            } finally {
                pdfDocument.close()
            }
        }
    }

    private fun RichTextAlign.toLayoutAlignment(): Layout.Alignment = when (this) {
        RichTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
        RichTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        RichTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }

    private fun resolveTypeface(
        fontFamily: RichFontFamily,
        bold: Boolean,
        italic: Boolean,
        customFontName: String? = null
    ): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        if (fontFamily == RichFontFamily.BENGALI_SANS ||
            fontFamily == RichFontFamily.NIRMALA_UI ||
            fontFamily == RichFontFamily.KALPURUSH
        ) {
            val fontResId = context.resources.getIdentifier(
                if (bold) "noto_sans_bengali_bold" else "noto_sans_bengali",
                "font",
                context.packageName
            )
            if (fontResId != 0) {
                runCatching {
                    val custom = ResourcesCompat.getFont(context, fontResId)
                    if (custom != null) return Typeface.create(custom, style)
                }
            }
        }

        val customLower = customFontName?.lowercase().orEmpty()
        val base = when {
            customLower.contains("mono") || customLower.contains("consolas") || customLower.contains("courier") -> Typeface.MONOSPACE
            customLower.contains("serif") || customLower.contains("times") || customLower.contains("georgia") || customLower.contains("vrinda") -> Typeface.SERIF
            fontFamily == RichFontFamily.BENGALI_SERIF ||
                fontFamily == RichFontFamily.VRINDA ||
                fontFamily == RichFontFamily.ENGLISH_SERIF ||
                fontFamily == RichFontFamily.TIMES_NEW_ROMAN ||
                fontFamily == RichFontFamily.GEORGIA -> Typeface.SERIF
            fontFamily == RichFontFamily.MONOSPACE -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        return Typeface.create(base, style)
    }

    private fun createOutputFile(prefix: String): File {
        val dir = File(context.cacheDir, "generated").apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}.pdf")
    }

    private fun decodeSampledFileBitmap(
        file: File,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    private fun decodeSampledRotatedBitmap(
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        return rotateBitmapIfRequired(bitmap, orientation)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}
