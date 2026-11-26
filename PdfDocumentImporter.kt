package com.pdfmakerstudio.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.pdfmakerstudio.app.domain.model.DocumentSignature
import com.pdfmakerstudio.app.domain.model.RichFontFamily
import com.pdfmakerstudio.app.domain.model.RichPdfBlock
import com.pdfmakerstudio.app.domain.model.RichTextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.zip.Inflater

object PdfDocumentImporter {

    private data class PdfObj(
        val id: Int,
        val dict: String,
        val streamBytes: ByteArray?
    )

    private data class PageFontInfo(
        val cmap: Map<Int, String>,
        val isBijoy: Boolean,
        val fontName: String
    )

    private val PUA_ICON_MAP: Map<Int, String> = mapOf(
        0xF020 to " ",
        0xF028 to "📞",
        0xF029 to "📱",
        0xF02A to "★",
        0xF02B to "✦",
        0xF02D to "−",
        0xF043 to "✓",
        0xF044 to "❖",
        0xF048 to "★",
        0xF04A to "😊",
        0xF061 to "✓",
        0xF06C to "●",
        0xF06E to "■",
        0xF070 to "◆",
        0xF071 to "◆",
        0xF075 to "◆",
        0xF076 to "❖",
        0xF077 to "❖",
        0xF0A1 to "•",
        0xF0A7 to "■",
        0xF0A8 to "◻",
        0xF0B0 to "◦",
        0xF0B7 to "•",
        0xF0D8 to "➢",
        0xF0DC to "➜",
        0xF0DF to "➜",
        0xF0E0 to "✉",
        0xF0E8 to "➜",
        0xF0FB to "✗",
        0xF0FC to "✓",
        0xF0FD to "☒",
        0xF0FE to "☑"
    )

    private val BIJOY_WORD_CONVERSIONS: List<Pair<String, String>> = listOf(
        "Avwg" to "আমি",
        "evsjv" to "বাংলা",
        "evsjv‡`k" to "বাংলাদেশ",
        "Avgv‡`i" to "আমাদের",
        "‡Zvgv‡`i" to "তোমাদের",
        "wkÿv" to "শিক্ষা",
        "cixÿv" to "পরীক্ষা",
        "Kg©" to "কর্ম",
        "cÖwZ" to "প্রতি",
        "Rb¨" to "জন্য",
        "Kiv" to "করা",
        "n‡q‡Q" to "হয়েছে",
        "ZvwiL" to "তারিখ"
    )

    private val BIJOY_CHAR_MAP: Map<String, String> = mapOf(
        "k" to "ক", "L" to "খ", "M" to "গ", "N" to "ঘ", "O" to "ঙ",
        "P" to "চ", "Q" to "ছ", "R" to "জ", "S" to "ঝ", "T" to "ঞ",
        "U" to "ট", "V" to "ঠ", "W" to "ড", "X" to "ঢ", "Y" to "ণ",
        "Z" to "ত", "_" to "থ", "`" to "দ", "a" to "ধ", "b" to "ন",
        "c" to "প", "d" to "ফ", "e" to "ব", "f" to "ভ", "g" to "ম",
        "h" to "য", "i" to "র", "j" to "ল", "k¡" to "শ্ব", "l" to "শ",
        "m" to "ষ", "n" to "স", "o" to "হ", "p" to "ড়", "q" to "ঢ়",
        "r" to "য়", "s" to "ৎ", "t" to "ং", "u" to "ঃ", "v" to "ঁ",
        "Av" to "আ", "A" to "অ", "B" to "ই", "C" to "ঈ", "D" to "উ",
        "E" to "ঊ", "F" to "ঋ", "G" to "এ", "H" to "ঐ", "I" to "ও",
        "J" to "ঔ",
        "v" to "া", "w" to "ি", "x" to "ী", "y" to "ু", "z" to "ূ",
        "„" to "ৃ", "‡" to "ে", "‰" to "ৈ", "Š" to "ৌ",
        "&" to "্", "Ö" to "্র", "¨" to "্য", "©" to "র্",
        "0" to "০", "1" to "১", "2" to "২", "3" to "৩", "4" to "৪",
        "5" to "৫", "6" to "৬", "7" to "৭", "8" to "৮", "9" to "৯",
        "|" to "।"
    )

    private val BANGLA_LEGACY_GLYPH_MAP: Map<Char, String> = mapOf(
        '\u00CB' to "\u09CD\u09AF",
        '\u00CE' to "\uE0FE",
        '\u00CF' to "\u09C7",
        '\u00D7' to "\u0995\u09CD\u09A4",
        '\u00D9' to "\u0995\u09CD\u09B7",
        '\u00E5' to "\u0997\u09CD\u09B0",
        '\u00EA' to "\u0999\u09CD\u0997",
        '\u0113' to "\u09A4\u09CD\u09A4",
        '\u0114' to "\u09A4\u09CD\u09B0",
        '\u0117' to "\u09A6\u09CD\u09A6",
        '\u0119' to "\u09A6\u09CD\u09B0",
        '\u0125' to "\u09A8\u09CD\u09AE",
        '\u012E' to "\u09A8\u09CD\u09A4\u09CD\u09B0",
        '\u012F' to "\u09A8\u09CD\u09AF",
        '\u0134' to "\u09AA\u09CD\u09B0",
        '\u0142' to "\u09AE\u09CD\u09AA",
        '\u014F' to "\u09B2\u09CD\u09A1",
        '\u0168' to "\u09B8\u09CD\u099F",
        '\u0169' to "\u09B8\u09CD\u09A4",
        '\u016A' to "\u09B8\u09CD\u09A5",
        '\u016E' to "\u09B8\u09CD\u09AC",
        '\u017F' to "\u09A8\u09C1",
        '\u0187' to "\u0997\u09CD\u09B0\u09C1"
    )

    private val BANGLA_LEGACY_KEYS_REGEX = Regex(
        """[\u00CB\u00CE\u00CF\u00D7\u00D9\u00E5\u00EA\u0113\u0114\u0117\u0119\u0125\u012E\u012F\u0134\u0142\u014F\u0168\u0169\u016A\u016E\u017F\u0187]"""
    )

    suspend fun importEditableDocument(
        context: Context,
        uri: Uri
    ): Result<ImportedWordDocResult> = withContext(Dispatchers.IO) {
        val rawName = WordDocumentCodec.queryDisplayName(context, uri) ?: "Imported_Document"
        val ext = rawName.substringAfterLast('.', "").lowercase(Locale.US)
        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()

        val bytesResult = runCatching {
            WordDocumentCodec.readUriBytesWithLimit(context, uri, WordDocumentCodec.MAX_UPLOAD_FILE_BYTES)
        }
        val bytes = bytesResult.getOrElse { return@withContext Result.failure(it) }

        val headAscii = String(
            bytes.copyOfRange(0, minOf(bytes.size, 32)),
            StandardCharsets.ISO_8859_1
        )
        val isPdf = headAscii.contains("%PDF-") || ext == "pdf" || mime == "application/pdf"
        if (isPdf) {
            val stem = rawName.replace(Regex("""\.pdf$""", RegexOption.IGNORE_CASE), "")
                .ifBlank { "PDF Document" }
            return@withContext runCatching { importPdfBytes(context, bytes, stem) }
        }
        WordDocumentCodec.importWordFile(context, uri)
    }

    private fun importPdfBytes(
        context: Context,
        pdfBytes: ByteArray,
        stemName: String
    ): ImportedWordDocResult {
        val objects = parsePdfObjects(pdfBytes)

        // 1. Lossless restore if this PDF was exported by PDF Maker Studio
        tryRestoreEmbeddedStudioDocument(context, objects, stemName)?.let { restored ->
            return restored
        }

        val pageObjects = collectOrderedPageObjects(objects)
        val exactBlocks = mutableListOf<RichPdfBlock>()
        val exactOverlays = mutableListOf<DocumentSignature>()
        val editableBlocks = mutableListOf<RichPdfBlock>()
        val editableOverlays = mutableListOf<DocumentSignature>()
        val allText = StringBuilder()
        val detectedFonts = linkedSetOf<String>()
        var complexPagesCount = 0

        val tempPdf = File(context.cacheDir, "import_src_${System.currentTimeMillis()}.pdf")
        FileOutputStream(tempPdf).use { it.write(pdfBytes) }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val rendererCount = renderer.pageCount.coerceAtMost(60)
            val totalPages = maxOf(rendererCount, pageObjects.size).coerceIn(1, 60)

            for (pageIdx in 0 until totalPages) {
                if (pageIdx > 0) {
                    exactBlocks.add(RichPdfBlock.PageBreakBlock())
                    editableBlocks.add(RichPdfBlock.PageBreakBlock())
                }
                val pageObj = pageObjects.getOrNull(pageIdx)
                val fontInfos = if (pageObj != null) buildFontInfosForPage(pageObj, objects) else emptyMap()
                fontInfos.values.forEach { info ->
                    if (info.fontName.isNotBlank() && isUsefulFontName(info.fontName)) {
                        detectedFonts.add(info.fontName)
                    }
                }

                val combinedStream = if (pageObj != null) combinePageContentStream(pageObj, objects) else ""
                val extractedRaw = if (combinedStream.isNotEmpty()) {
                    parseContentStreamText(combinedStream, fontInfos)
                } else {
                    ""
                }
                val vectorOps = countGraphicOperators(combinedStream)
                val normalizedText = normalizeExtractedBengali(mapPuaIconsToUnicode(extractedRaw))
                    .replace(Regex("""(?im)^(?:Page\s+\d+(?:\s+of\s+\d+)?|পৃষ্ঠা\s*[০-৯0-9]+(?:\s*/\s*[০-৯0-9]+)?)$"""), "")
                    .trim()

                val pageEmbeddedOverlays = if (pageObj != null) {
                    extractEmbeddedJpegOverlays(context, pageObj, objects, pageIdx)
                } else {
                    emptyList()
                }
                editableOverlays.addAll(pageEmbeddedOverlays)

                val fullPageOverlay = if (pageIdx < rendererCount) {
                    renderPageToOverlayFile(context, renderer, pageIdx, fullSheet = true)
                } else {
                    null
                }
                if (fullPageOverlay != null) {
                    exactOverlays.add(fullPageOverlay)
                }
                exactBlocks.add(
                    RichPdfBlock.TextBlock(
                        text = "",
                        fontFamily = RichFontFamily.BENGALI_SANS,
                        fontSizeSp = 14f
                    )
                )

                val cleanTextOk = isCleanReadableText(normalizedText)
                val hasComplexVisualLayout = !cleanTextOk || vectorOps >= 4 || pageEmbeddedOverlays.size >= 2
                if (hasComplexVisualLayout) {
                    complexPagesCount++
                }

                if (cleanTextOk) {
                    val paragraphs = splitIntoParagraphs(normalizedText)
                    paragraphs.forEachIndexed { idx, para ->
                        allText.append(para).append('\n')
                        val looksHeading = idx == 0 && para.length in 3..90 && !para.contains('\n')
                        editableBlocks.add(
                            RichPdfBlock.TextBlock(
                                text = para,
                                fontFamily = RichFontFamily.BENGALI_SANS,
                                fontSizeSp = if (looksHeading) 17f else 14f,
                                isBold = looksHeading
                            )
                        )
                    }
                } else {
                    if (pageEmbeddedOverlays.isEmpty() && fullPageOverlay != null) {
                        editableOverlays.add(fullPageOverlay.copy(id = UUID.randomUUID().toString()))
                    }
                    editableBlocks.add(
                        RichPdfBlock.TextBlock(
                            text = if (normalizedText.length >= 12) normalizedText else "",
                            fontFamily = RichFontFamily.BENGALI_SANS,
                            fontSizeSp = 14f
                        )
                    )
                }
            }
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
            runCatching { tempPdf.delete() }
        }

        if (exactBlocks.isEmpty()) exactBlocks.add(RichPdfBlock.TextBlock(text = ""))
        if (editableBlocks.isEmpty()) editableBlocks.add(RichPdfBlock.TextBlock(text = ""))

        var docTitle = ""
        val firstEditable = editableBlocks.firstOrNull()
        if (firstEditable is RichPdfBlock.TextBlock && firstEditable.isBold && firstEditable.text.length in 2..110 && editableBlocks.size > 1) {
            docTitle = firstEditable.text.trim()
            editableBlocks.removeAt(0)
        }

        val preferExact = complexPagesCount > 0 && exactOverlays.isNotEmpty()
        val primaryBlocks = if (preferExact) exactBlocks else editableBlocks
        val primaryOverlays = if (preferExact) exactOverlays else editableOverlays
        val alternate = if (exactOverlays.isNotEmpty()) {
            ImportedPdfAlternateMode(
                currentMode = if (preferExact) "exact" else "editable",
                blocks = if (preferExact) editableBlocks else exactBlocks,
                overlays = if (preferExact) editableOverlays else exactOverlays
            )
        } else {
            null
        }

        val info = WordDocumentCodec.detectTextScripts(allText.toString())
        return ImportedWordDocResult(
            suggestedFileName = stemName,
            docTitle = if (preferExact) "" else docTitle,
            blocks = primaryBlocks,
            overlays = primaryOverlays,
            detectedFonts = detectedFonts.toList().ifEmpty { listOf("PDF Unicode") },
            detectedScripts = info.scripts.ifEmpty { listOf("PDF") },
            sourceFormat = "pdf",
            alternateMode = alternate
        )
    }

    private fun tryRestoreEmbeddedStudioDocument(
        context: Context,
        objects: Map<Int, PdfObj>,
        stemName: String
    ): ImportedWordDocResult? {
        for (obj in objects.values) {
            if (!obj.dict.contains("/Keywords")) continue
            val keywords = extractKeywordsValue(obj.dict) ?: continue
            val marker = "pms_doc_v1:"
            val idx = keywords.indexOf(marker)
            if (idx < 0) continue

            val b64 = keywords.substring(idx + marker.length).trim().split(Regex("""\s+"""))[0]
            return runCatching {
                val decodedBytes = Base64.decode(b64, Base64.DEFAULT)
                val jsonText = String(decodedBytes, StandardCharsets.UTF_8)
                val root = JSONObject(jsonText)
                val blocksArr = root.optJSONArray("b")
                val blocks = mutableListOf<RichPdfBlock>()
                val overlayDir = File(context.filesDir, "editor_overlays").apply { mkdirs() }
                val allText = StringBuilder()

                if (blocksArr != null) {
                    for (i in 0 until blocksArr.length()) {
                        val item = blocksArr.optJSONObject(i) ?: continue
                        when (item.optString("k")) {
                            "pb" -> blocks.add(RichPdfBlock.PageBreakBlock())
                            "div" -> {
                                val cHex = parseCssColorToLong(item.optString("c"), 0xFFCBD5E1L)
                                blocks.add(RichPdfBlock.DividerBlock(colorHex = cHex))
                            }
                            "img" -> {
                                val savedFile = saveDataUrlToFile(item.optString("d"), overlayDir, "imp_blk")
                                if (savedFile != null) {
                                    blocks.add(
                                        RichPdfBlock.ImageBlock(
                                            imageUri = Uri.fromFile(savedFile),
                                            caption = item.optString("cp", ""),
                                            widthFraction = item.optDouble("wp", 0.85).toFloat().coerceIn(0.3f, 1f),
                                            align = parseAlign(item.optString("a"))
                                        )
                                    )
                                }
                            }
                            "txt" -> {
                                val txt = item.optString("x", "")
                                val customFont = item.optString("cf").takeIf { it.isNotBlank() }
                                val fk = item.optString("fk", "")
                                val (family, mappedCustom) = mapStudioFontKey(fk, customFont)
                                allText.append(txt).append('\n')
                                blocks.add(
                                    RichPdfBlock.TextBlock(
                                        text = txt,
                                        fontFamily = family,
                                        customFontName = mappedCustom,
                                        fontSizeSp = item.optDouble("fs", 14.0).toFloat().coerceIn(10f, 32f),
                                        isBold = item.optInt("b", 0) == 1,
                                        isItalic = item.optInt("i", 0) == 1,
                                        isUnderline = item.optInt("u", 0) == 1,
                                        colorHex = parseCssColorToLong(item.optString("c"), 0xFF1E293BL),
                                        align = parseAlign(item.optString("a")),
                                        leadingIcon = item.optString("ic").takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        }
                    }
                }

                val overlays = mutableListOf<DocumentSignature>()
                val sigsArr = root.optJSONArray("s")
                if (sigsArr != null) {
                    for (i in 0 until sigsArr.length()) {
                        val s = sigsArr.optJSONObject(i) ?: continue
                        val savedFile = saveDataUrlToFile(s.optString("d"), overlayDir, "imp_ovl") ?: continue
                        overlays.add(
                            DocumentSignature(
                                pageIndex = s.optInt("pi", 0).coerceAtLeast(0),
                                imagePath = savedFile.absolutePath,
                                xFraction = s.optDouble("x", 0.1).toFloat().coerceIn(0.01f, 0.90f),
                                yFraction = s.optDouble("y", 0.15).toFloat().coerceIn(0.01f, 0.94f),
                                widthFraction = s.optDouble("w", 0.42).toFloat().coerceIn(0.08f, 0.90f),
                                aspectRatio = s.optDouble("r", 0.65).toFloat().coerceIn(0.15f, 2.5f),
                                kind = s.optString("kd", "image").ifBlank { "image" }
                            )
                        )
                    }
                }

                val info = WordDocumentCodec.detectTextScripts(allText.toString())
                ImportedWordDocResult(
                    suggestedFileName = stemName,
                    docTitle = root.optString("t", ""),
                    blocks = blocks.ifEmpty { listOf(RichPdfBlock.TextBlock(text = "")) },
                    overlays = overlays,
                    detectedFonts = listOf("PDF Maker Studio Restored"),
                    detectedScripts = info.scripts.ifEmpty { listOf("Unicode") },
                    sourceFormat = "pdf"
                )
            }.getOrNull()
        }
        return null
    }

    private fun extractKeywordsValue(dict: String): String? {
        val parenMatch = Regex("""/Keywords\s*\(((?:\\.|[^\\()])*)\)""").find(dict)
        if (parenMatch != null) {
            return parenMatch.groupValues[1]
                .replace("\\(", "(")
                .replace("\\)", ")")
                .replace("\\\\", "\\")
        }
        val hexMatch = Regex("""/Keywords\s*<([0-9A-Fa-f\s]+)>""").find(dict)
        if (hexMatch != null) {
            val hex = hexMatch.groupValues[1].replace(Regex("""\s+"""), "")
            return if (hex.startsWith("FEFF", ignoreCase = true) && hex.length > 4) {
                hexToUtf16String(hex.substring(4))
            } else {
                hexToUtf16String(hex)
            }
        }
        return null
    }

    private fun saveDataUrlToFile(dataUrl: String, outDir: File, prefix: String): File? {
        if (!dataUrl.startsWith("data:image/")) return null
        val commaIdx = dataUrl.indexOf(',')
        if (commaIdx <= 0 || commaIdx + 1 >= dataUrl.length) return null
        val header = dataUrl.substring(0, commaIdx).lowercase(Locale.US)
        val b64 = dataUrl.substring(commaIdx + 1)
        return runCatching {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val ext = if (header.contains("png")) "png" else "jpg"
            val outFile = File(outDir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")
            FileOutputStream(outFile).use { it.write(bytes) }
            outFile
        }.getOrNull()
    }

    private fun extractEmbeddedJpegOverlays(
        context: Context,
        pageObj: PdfObj,
        objects: Map<Int, PdfObj>,
        pageIndex: Int
    ): List<DocumentSignature> {
        val resObjId = Regex("""/Resources\s+(\d+)\s+\d+\s+R""").find(pageObj.dict)?.groupValues?.get(1)?.toIntOrNull()
        val resourceSource = resObjId?.let { objects[it]?.dict } ?: pageObj.dict
        val xObjDictId = Regex("""/XObject\s+(\d+)\s+\d+\s+R""").find(resourceSource)?.groupValues?.get(1)?.toIntOrNull()
        val xObjSource = xObjDictId?.let { objects[it]?.dict } ?: resourceSource

        val outDir = File(context.filesDir, "editor_overlays").apply { mkdirs() }
        val result = mutableListOf<DocumentSignature>()
        val seenObjIds = mutableSetOf<Int>()

        Regex("""/([A-Za-z0-9_.+-]+)\s+(\d+)\s+\d+\s+R""").findAll(xObjSource).forEach { m ->
            if (result.size >= 8) return@forEach
            val objId = m.groupValues[2].toIntOrNull() ?: return@forEach
            if (!seenObjIds.add(objId)) return@forEach
            val xObj = objects[objId] ?: return@forEach
            if (!xObj.dict.contains("/Subtype") || !xObj.dict.contains("/Image")) return@forEach
            val bytes = xObj.streamBytes ?: return@forEach
            if (bytes.size < 400) return@forEach

            // Check if JPEG (/DCTDecode) or decodable bitmap
            val isJpeg = xObj.dict.contains("/DCTDecode") ||
                (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte())
            if (!isJpeg) return@forEach

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth < 24 || opts.outHeight < 24) return@forEach

            val outFile = File(outDir, "pdf_img_${System.currentTimeMillis()}_${pageIndex}_${result.size}.jpg")
            FileOutputStream(outFile).use { it.write(bytes) }
            val ratio = (opts.outHeight.toFloat() / opts.outWidth.coerceAtLeast(1).toFloat()).coerceIn(0.15f, 2.5f)
            val widthFrac = if (ratio > 1.2f) 0.36f else 0.48f
            val slot = result.size
            val xFrac = if (slot % 2 == 0) 0.08f else 0.46f
            val yFrac = (0.14f + (slot / 2) * 0.28f).coerceAtMost(0.68f)

            result.add(
                DocumentSignature(
                    pageIndex = pageIndex,
                    imagePath = outFile.absolutePath,
                    xFraction = xFrac,
                    yFraction = yFrac,
                    widthFraction = widthFrac,
                    aspectRatio = ratio,
                    kind = "image"
                )
            )
        }
        return result
    }

    private fun renderPageToOverlayFile(
        context: Context,
        renderer: PdfRenderer,
        pageIndex: Int,
        fullSheet: Boolean = false
    ): DocumentSignature? {
        return runCatching {
            val outDir = File(context.filesDir, "imported_pdf_pages").apply { mkdirs() }
            renderer.openPage(pageIndex).use { page ->
                val targetWidth = if (fullSheet) 1440 else 1120
                val scale = (targetWidth.toFloat() / page.width.coerceAtLeast(1)).coerceIn(1f, 3.0f)
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val outFile = File(outDir, "pdf_p_${System.currentTimeMillis()}_$pageIndex.jpg")
                FileOutputStream(outFile).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                }
                bmp.recycle()
                val ratio = (h.toFloat() / w.coerceAtLeast(1).toFloat()).coerceIn(0.15f, 2.5f)
                DocumentSignature(
                    pageIndex = pageIndex,
                    imagePath = outFile.absolutePath,
                    xFraction = if (fullSheet) 0f else 0.08f,
                    yFraction = if (fullSheet) 0f else 0.08f,
                    widthFraction = if (fullSheet) 1.0f else 0.84f,
                    aspectRatio = ratio,
                    kind = "image"
                )
            }
        }.getOrNull()
    }

    private fun parsePdfObjects(pdfBytes: ByteArray): Map<Int, PdfObj> {
        val raw = String(pdfBytes, StandardCharsets.ISO_8859_1)
        val result = linkedMapOf<Int, PdfObj>()
        val objRegex = Regex("""(\d+)\s+\d+\s+obj\b([\s\S]*?)endobj""")

        objRegex.findAll(raw).forEach { match ->
            val objId = match.groupValues[1].toIntOrNull() ?: return@forEach
            val bodyStart = match.groups[2]?.range?.first ?: return@forEach
            val body = match.groupValues[2]

            val streamKeywordIdx = body.indexOf("stream")
            if (streamKeywordIdx >= 0) {
                val dictPart = body.substring(0, streamKeywordIdx).trim()
                val absStreamStart = findStreamDataOffset(pdfBytes, bodyStart + streamKeywordIdx)
                val endStreamIdx = raw.indexOf("endstream", absStreamStart.coerceAtMost(raw.length))
                if (absStreamStart in 0..endStreamIdx && endStreamIdx <= pdfBytes.size) {
                    var streamEnd = endStreamIdx
                    while (streamEnd > absStreamStart && (pdfBytes[streamEnd - 1] == 0x0A.toByte() || pdfBytes[streamEnd - 1] == 0x0D.toByte())) {
                        streamEnd--
                    }
                    val rawStream = pdfBytes.copyOfRange(absStreamStart, streamEnd)
                    val decodedStream = if (dictPart.contains("/FlateDecode")) {
                        inflateStream(rawStream) ?: rawStream
                    } else {
                        rawStream
                    }
                    result[objId] = PdfObj(objId, dictPart, decodedStream)

                    if (dictPart.contains("/ObjStm")) {
                        unpackObjStm(dictPart, decodedStream, result)
                    }
                } else {
                    result[objId] = PdfObj(objId, body.trim(), null)
                }
            } else {
                result[objId] = PdfObj(objId, body.trim(), null)
            }
        }
        return result
    }

    private fun unpackObjStm(
        dict: String,
        streamBytes: ByteArray,
        out: MutableMap<Int, PdfObj>
    ) {
        val nCount = Regex("""/N\s+(\d+)""").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: return
        val firstOffset = Regex("""/First\s+(\d+)""").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: return
        if (firstOffset <= 0 || firstOffset >= streamBytes.size) return

        val headerStr = String(streamBytes, 0, firstOffset, StandardCharsets.ISO_8859_1).trim()
        val tokens = headerStr.split(Regex("""\s+""")).mapNotNull { it.toIntOrNull() }
        val pairs = mutableListOf<Pair<Int, Int>>()
        var idx = 0
        while (idx + 1 < tokens.size && pairs.size < nCount) {
            pairs.add(tokens[idx] to (firstOffset + tokens[idx + 1]))
            idx += 2
        }
        for (pIdx in pairs.indices) {
            val objId = pairs[pIdx].first
            val start = pairs[pIdx].second
            val end = if (pIdx + 1 < pairs.size) pairs[pIdx + 1].second else streamBytes.size
            if (start in 0 until end && end <= streamBytes.size) {
                val slice = String(streamBytes, start, end - start, StandardCharsets.ISO_8859_1).trim()
                if (!out.containsKey(objId)) {
                    out[objId] = PdfObj(objId, slice, null)
                }
            }
        }
    }

    private fun findStreamDataOffset(bytes: ByteArray, keywordOffset: Int): Int {
        var pos = keywordOffset + "stream".length
        if (pos < bytes.size && bytes[pos] == 0x0D.toByte()) pos++
        if (pos < bytes.size && bytes[pos] == 0x0A.toByte()) pos++
        return pos
    }

    private fun inflateStream(compressed: ByteArray): ByteArray? {
        return runCatching {
            val inflater = Inflater()
            try {
                inflater.setInput(compressed)
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var guard = 0
                while (!inflater.finished() && guard < 4096) {
                    guard++
                    val count = inflater.inflate(buf)
                    if (count == 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) break
                    } else {
                        out.write(buf, 0, count)
                    }
                }
                out.toByteArray()
            } finally {
                inflater.end()
            }
        }.getOrNull()
    }

    private fun collectOrderedPageObjects(objects: Map<Int, PdfObj>): List<PdfObj> {
        val pagesNodes = objects.values.filter {
            Regex("""/Type\s*/Pages\b""").containsMatchIn(it.dict)
        }
        val rootPages = pagesNodes.firstOrNull { !it.dict.contains("/Parent") } ?: pagesNodes.firstOrNull()
        val ordered = mutableListOf<PdfObj>()
        val visited = mutableSetOf<Int>()

        fun walkKids(obj: PdfObj) {
            if (!visited.add(obj.id)) return
            val kidsMatch = Regex("""/Kids\s*\[([^\]]+)]""").find(obj.dict)
            if (kidsMatch != null) {
                val refIds = Regex("""(\d+)\s+\d+\s+R""").findAll(kidsMatch.groupValues[1])
                    .mapNotNull { it.groupValues[1].toIntOrNull() }
                refIds.forEach { kidId ->
                    val kid = objects[kidId] ?: return@forEach
                    if (Regex("""/Type\s*/Pages\b""").containsMatchIn(kid.dict)) {
                        walkKids(kid)
                    } else {
                        ordered.add(kid)
                    }
                }
            }
        }

        if (rootPages != null) walkKids(rootPages)
        if (ordered.isNotEmpty()) return ordered

        return objects.values.filter {
            Regex("""/Type\s*/Page\b(?!s)""").containsMatchIn(it.dict)
        }.sortedBy { it.id }
    }

    private fun combinePageContentStream(
        pageObj: PdfObj,
        objects: Map<Int, PdfObj>
    ): String {
        val contentIds = mutableListOf<Int>()

        val arrayMatch = Regex("""/Contents\s*\[([^\]]+)]""").find(pageObj.dict)
        if (arrayMatch != null) {
            Regex("""(\d+)\s+\d+\s+R""").findAll(arrayMatch.groupValues[1]).forEach { m ->
                m.groupValues[1].toIntOrNull()?.let { contentIds.add(it) }
            }
        } else {
            Regex("""/Contents\s+(\d+)\s+\d+\s+R""").find(pageObj.dict)?.groupValues?.get(1)
                ?.toIntOrNull()?.let { contentIds.add(it) }
        }

        val combinedContent = StringBuilder()
        contentIds.forEach { cid ->
            objects[cid]?.streamBytes?.let { bytes ->
                combinedContent.append(String(bytes, StandardCharsets.ISO_8859_1)).append('\n')
            }
        }
        return combinedContent.toString()
    }

    private fun countGraphicOperators(stream: String): Int {
        if (stream.isBlank()) return 0
        val noText = stream.replace(Regex("""BT[\s\S]*?ET"""), " ")
        return Regex("""(?:^|\s)(?:re|S|B|f\*?|Do)(?=\s|$)""").findAll(noText).count()
    }

    private fun buildFontInfosForPage(
        pageObj: PdfObj,
        objects: Map<Int, PdfObj>
    ): Map<String, PageFontInfo> {
        val infos = mutableMapOf<String, PageFontInfo>()
        val resObjId = Regex("""/Resources\s+(\d+)\s+\d+\s+R""").find(pageObj.dict)?.groupValues?.get(1)?.toIntOrNull()
        val resourceSource = resObjId?.let { objects[it]?.dict } ?: pageObj.dict

        val fontDictId = Regex("""/Font\s+(\d+)\s+\d+\s+R""").find(resourceSource)?.groupValues?.get(1)?.toIntOrNull()
        val fontSource = fontDictId?.let { objects[it]?.dict } ?: resourceSource

        Regex("""/([A-Za-z0-9_.+-]+)\s+(\d+)\s+\d+\s+R""").findAll(fontSource).forEach { m ->
            val fontAlias = m.groupValues[1]
            val fontObjId = m.groupValues[2].toIntOrNull() ?: return@forEach
            val fontObj = objects[fontObjId] ?: return@forEach

            val rawBaseFont = Regex("""/BaseFont\s*/([^\s/>\]\[]+)""").find(fontObj.dict)?.groupValues?.get(1).orEmpty()
            val cleanedName = rawBaseFont.replace(Regex("""^[A-Z]{6}\+"""), "")
            val isBijoy = Regex("""sutonny|bijoy|boishakhi|chandrabati|mj\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanedName)

            val cmap = mutableMapOf<Int, String>()
            val toUniId = Regex("""/ToUnicode\s+(\d+)\s+\d+\s+R""").find(fontObj.dict)?.groupValues?.get(1)?.toIntOrNull()
            val cmapBytes = toUniId?.let { objects[it]?.streamBytes }
            if (cmapBytes != null) {
                cmap.putAll(parseToUnicodeCmap(String(cmapBytes, StandardCharsets.ISO_8859_1)))
            }
            infos[fontAlias] = PageFontInfo(
                cmap = cmap,
                isBijoy = isBijoy,
                fontName = cleanedName
            )
        }
        return infos
    }

    private fun parseToUnicodeCmap(rawCmap: String): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        Regex("""beginbfchar([\s\S]*?)endbfchar""").findAll(rawCmap).forEach { block ->
            Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""").findAll(block.groupValues[1]).forEach { m ->
                val src = m.groupValues[1].toIntOrNull(16) ?: return@forEach
                val dst = hexToUtf16String(m.groupValues[2])
                if (dst.isNotEmpty()) map[src] = dst
            }
        }
        Regex("""beginbfrange([\s\S]*?)endbfrange""").findAll(rawCmap).forEach { block ->
            Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""").findAll(block.groupValues[1]).forEach { m ->
                val start = m.groupValues[1].toIntOrNull(16) ?: return@forEach
                val end = m.groupValues[2].toIntOrNull(16) ?: return@forEach
                val dstStart = m.groupValues[3].toIntOrNull(16) ?: return@forEach
                if (end - start in 0..1024) {
                    for (offset in 0..(end - start)) {
                        map[start + offset] = String(Character.toChars(dstStart + offset))
                    }
                }
            }
        }
        return map
    }

    private fun hexToUtf16String(hex: String): String {
        val clean = hex.trim()
        if (clean.length < 2) return ""
        val sb = StringBuilder()
        if (clean.length % 4 == 0) {
            var i = 0
            while (i + 3 < clean.length) {
                val unit = clean.substring(i, i + 4).toIntOrNull(16) ?: 0
                if (unit > 0) sb.append(unit.toChar())
                i += 4
            }
        } else {
            clean.toIntOrNull(16)?.let { code ->
                if (code > 0) sb.append(code.toChar())
            }
        }
        return sb.toString()
    }

    private fun parseContentStreamText(
        stream: String,
        fontInfos: Map<String, PageFontInfo>
    ): String {
        val out = StringBuilder()
        var currentFontAlias = ""
        val btBlocks = Regex("""BT([\s\S]*?)ET""").findAll(stream)

        for (bt in btBlocks) {
            val content = bt.groupValues[1]
            var idx = 0
            val len = content.length
            val tokens = mutableListOf<String>()

            while (idx < len) {
                val ch = content[idx]
                when {
                    ch.isWhitespace() -> idx++
                    ch == '(' -> {
                        val sb = StringBuilder("(")
                        idx++
                        var depth = 1
                        while (idx < len && depth > 0) {
                            val c = content[idx]
                            if (c == '\\' && idx + 1 < len) {
                                sb.append(c).append(content[idx + 1])
                                idx += 2
                            } else {
                                if (c == '(') depth++
                                else if (c == ')') depth--
                                sb.append(c)
                                idx++
                            }
                        }
                        tokens.add(sb.toString())
                    }
                    ch == '<' && idx + 1 < len && content[idx + 1] != '<' -> {
                        val end = content.indexOf('>', idx + 1)
                        if (end > idx) {
                            tokens.add(content.substring(idx, end + 1))
                            idx = end + 1
                        } else {
                            idx++
                        }
                    }
                    ch == '[' -> {
                        val sb = StringBuilder("[")
                        idx++
                        var inParen = false
                        while (idx < len) {
                            val c = content[idx]
                            if (inParen && c == '\\' && idx + 1 < len) {
                                sb.append(c).append(content[idx + 1])
                                idx += 2
                                continue
                            }
                            if (c == '(') inParen = true
                            else if (c == ')') inParen = false
                            sb.append(c)
                            idx++
                            if (!inParen && c == ']') break
                        }
                        tokens.add(sb.toString())
                    }
                    else -> {
                        val start = idx
                        while (idx < len && !content[idx].isWhitespace() && content[idx] !in setOf('(', '<', '[')) {
                            idx++
                        }
                        val tok = content.substring(start, idx)
                        tokens.add(tok)

                        val curInfo = fontInfos[currentFontAlias]
                        when (tok) {
                            "Tf" -> {
                                val fontTok = tokens.getOrNull(tokens.size - 3).orEmpty()
                                if (fontTok.startsWith("/")) currentFontAlias = fontTok.drop(1)
                            }
                            "T*", "'" -> out.append('\n')
                            "Td", "TD" -> {
                                val dy = tokens.getOrNull(tokens.size - 2)?.toFloatOrNull() ?: 0f
                                if (kotlin.math.abs(dy) > 2f) out.append('\n')
                                else if (out.isNotEmpty() && !out.endsWith('\n') && !out.endsWith(' ')) out.append(' ')
                            }
                            "Tm" -> {
                                if (out.isNotEmpty() && !out.endsWith('\n') && !out.endsWith(' ')) out.append('\n')
                            }
                            "Tj" -> {
                                val strTok = tokens.getOrNull(tokens.size - 2).orEmpty()
                                val decoded = decodePdfStringToken(strTok, curInfo?.cmap)
                                out.append(if (curInfo?.isBijoy == true) convertBijoyToUnicode(decoded) else decoded)
                            }
                            "TJ" -> {
                                val arrTok = tokens.getOrNull(tokens.size - 2).orEmpty()
                                val decoded = decodeTjArray(arrTok, curInfo?.cmap)
                                out.append(if (curInfo?.isBijoy == true) convertBijoyToUnicode(decoded) else decoded)
                            }
                        }
                    }
                }
            }
            if (out.isNotEmpty() && !out.endsWith('\n')) out.append('\n')
        }
        return out.toString().trim()
    }

    private fun decodeTjArray(arr: String, cmap: Map<Int, String>?): String {
        val sb = StringBuilder()
        var i = 0
        while (i < arr.length) {
            when (arr[i]) {
                '(' -> {
                    val start = i
                    i++
                    var depth = 1
                    while (i < arr.length && depth > 0) {
                        if (arr[i] == '\\' && i + 1 < arr.length) i += 2
                        else {
                            if (arr[i] == '(') depth++
                            else if (arr[i] == ')') depth--
                            i++
                        }
                    }
                    sb.append(decodePdfStringToken(arr.substring(start, i), cmap))
                }
                '<' -> {
                    val end = arr.indexOf('>', i + 1)
                    if (end > i) {
                        sb.append(decodePdfStringToken(arr.substring(i, end + 1), cmap))
                        i = end + 1
                    } else i++
                }
                '-' -> {
                    val numStart = i
                    i++
                    while (i < arr.length && (arr[i].isDigit() || arr[i] == '.')) i++
                    val kern = arr.substring(numStart, i).toFloatOrNull() ?: 0f
                    if (kern < -180f && sb.isNotEmpty() && !sb.endsWith(' ')) sb.append(' ')
                }
                else -> i++
            }
        }
        return sb.toString()
    }

    private fun decodePdfStringToken(token: String, cmap: Map<Int, String>?): String {
        if (token.startsWith("<") && token.endsWith(">")) {
            val hex = token.substring(1, token.length - 1).replace(Regex("""\s+"""), "")
            if (hex.isEmpty()) return ""
            if (cmap != null && cmap.isNotEmpty()) {
                val sb = StringBuilder()
                val step = if (hex.length % 4 == 0) 4 else 2
                var i = 0
                while (i + step <= hex.length) {
                    val code = hex.substring(i, i + step).toIntOrNull(16) ?: 0
                    sb.append(cmap[code] ?: if (code in 32..126) code.toChar().toString() else "")
                    i += step
                }
                return sb.toString()
            }
            if (hex.startsWith("FEFF", ignoreCase = true) && hex.length > 4) {
                return hexToUtf16String(hex.substring(4))
            }
            val sb = StringBuilder()
            var i = 0
            while (i + 1 < hex.length) {
                val b = hex.substring(i, i + 2).toIntOrNull(16) ?: 0
                if (b in 32..126) sb.append(b.toChar())
                i += 2
            }
            return sb.toString()
        }

        if (token.startsWith("(") && token.endsWith(")")) {
            val inner = token.substring(1, token.length - 1)
            val sb = StringBuilder()
            var i = 0
            while (i < inner.length) {
                val c = inner[i]
                if (c == '\\' && i + 1 < inner.length) {
                    val next = inner[i + 1]
                    when (next) {
                        'n' -> { sb.append('\n'); i += 2 }
                        'r' -> { sb.append('\r'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        'b', 'f' -> i += 2
                        '(', ')', '\\' -> { sb.append(next); i += 2 }
                        in '0'..'7' -> {
                            var octEnd = i + 2
                            while (octEnd < inner.length && octEnd < i + 4 && inner[octEnd] in '0'..'7') octEnd++
                            val code = inner.substring(i + 1, octEnd).toIntOrNull(8) ?: 32
                            sb.append(cmap?.get(code) ?: code.toChar().toString())
                            i = octEnd
                        }
                        else -> { sb.append(next); i += 2 }
                    }
                } else {
                    val mapped = cmap?.get(c.code)
                    sb.append(mapped ?: c.toString())
                    i++
                }
            }
            return sb.toString()
        }
        return ""
    }

    private fun mapPuaIconsToUnicode(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val mapped = PUA_ICON_MAP[ch.code]
            if (mapped != null) sb.append(mapped) else sb.append(ch)
        }
        return sb.toString()
    }

    private fun convertBijoyToUnicode(input: String): String {
        var out = input
        for ((from, to) in BIJOY_WORD_CONVERSIONS) {
            out = out.replace(from, to)
        }
        val orderedKeys = BIJOY_CHAR_MAP.keys.sortedByDescending { it.length }
        val sb = StringBuilder()
        var idx = 0
        while (idx < out.length) {
            var matched: String? = null
            for (k in orderedKeys) {
                if (out.startsWith(k, idx)) {
                    sb.append(BIJOY_CHAR_MAP[k])
                    idx += k.length
                    matched = k
                    break
                }
            }
            if (matched == null) {
                sb.append(out[idx])
                idx++
            }
        }
        return normalizeExtractedBengali(sb.toString(), forceVisualOrder = true)
    }

    private fun looksLikeVisualOrderBengali(text: String): Boolean {
        if (!hasBengaliChars(text)) return false
        if (BANGLA_LEGACY_KEYS_REGEX.containsMatchIn(text)) return true
        return Regex("""(?:^|[\s\u0985-\u0994\u09BE-\u09CD\u09D7\u0981-\u0983:/\-.,(])[\u09BF\u09C7\u09C8]""")
            .containsMatchIn(text)
    }

    private fun decodeBanglaLegacyGlyphs(text: String): String {
        if (!hasBengaliChars(text) || !BANGLA_LEGACY_KEYS_REGEX.containsMatchIn(text)) {
            return text
        }
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(BANGLA_LEGACY_GLYPH_MAP[ch] ?: ch.toString())
        }
        return sb.toString()
    }

    private fun normalizeExtractedBengali(input: String, forceVisualOrder: Boolean = false): String {
        var text = input.replace("\u200B", "").replace("\uFEFF", "")
        val hadLegacy = hasBengaliChars(text) && BANGLA_LEGACY_KEYS_REGEX.containsMatchIn(text)
        text = decodeBanglaLegacyGlyphs(text)
        text = text
            .replace("\u09A1\u09BC", "\u09DC")
            .replace("\u09A2\u09BC", "\u09DD")
            .replace("\u09AF\u09BC", "\u09DF")

        text = text.replace(Regex("""[ \t]+([\u0981-\u0983\u09BC\u09BE-\u09CD\u09D7\u09E2-\u09E3])"""), "$1")
        text = text.replace(Regex("""\u09CD[ \t]+([\u0995-\u09B9\u09DC-\u09DF])"""), "\u09CD$1")

        if (text.contains('\uE0FE')) {
            val refRegex = Regex("""([\u0995-\u09B9\u09DC-\u09DF](?:\u09CD[\u0995-\u09B9\u09DC-\u09DF])*[\u09C1-\u09C4]?)\uE0FE""")
            text = refRegex.replace(text, "\u09B0\u09CD$1").replace("\uE0FE", "\u09B0\u09CD")
        }

        if (forceVisualOrder || hadLegacy || looksLikeVisualOrderBengali(text)) {
            val clusterRegex = Regex("""^([\u0995-\u09B9\u09DC-\u09DF](?:\u09CD[\u0995-\u09B9\u09DC-\u09DF])*(?:\u09CD[\u09AF\u09B0\u09AC\u09AE\u09A8\u09B2])?)""")
            val sb = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                if (ch == '\u09BF' || ch == '\u09C7' || ch == '\u09C8') {
                    val m = clusterRegex.find(text.substring(i + 1))
                    if (m != null) {
                        val cluster = m.groupValues[1]
                        val afterIdx = i + 1 + cluster.length
                        val nextCh = text.getOrNull(afterIdx)
                        if (ch == '\u09C7' && nextCh == '\u09BE') {
                            sb.append(cluster).append('\u09CB')
                            i = afterIdx + 1
                            continue
                        }
                        if (ch == '\u09C7' && nextCh == '\u09D7') {
                            sb.append(cluster).append('\u09CC')
                            i = afterIdx + 1
                            continue
                        }
                        sb.append(cluster).append(ch)
                        i = afterIdx
                        continue
                    }
                }
                sb.append(ch)
                i++
            }
            text = sb.toString()
        }

        text = text.replace(Regex("""([\u0981])([\u09BE-\u09CC\u09D7]+)"""), "$2$1")
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        return normalized
            .replace("\u09A1\u09BC", "\u09DC")
            .replace("\u09A2\u09BC", "\u09DD")
            .replace("\u09AF\u09BC", "\u09DF")
    }

    private fun hasBengaliChars(text: String): Boolean {
        return text.any { it.code in 0x0980..0x09FF }
    }

    private fun isUsefulFontName(name: String): Boolean {
        val lower = name.trim().lowercase(Locale.US)
        if (lower.isEmpty()) return false
        if (lower.matches(Regex("""^g_d\d+_f\d+$""")) || lower.matches(Regex("""^f\d+$"""))) return false
        if (lower in setOf("sans-serif", "serif", "monospace", "system-ui")) return false
        return true
    }

    private fun isCleanReadableText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 4) return false
        var brokenCount = 0
        val readableCount = trimmed.count { ch ->
            val cp = ch.code
            val isBroken = ch == '\uFFFD' || cp in 0xE000..0xF8FF
            if (isBroken) brokenCount++
            ch.isWhitespace() ||
                cp in 0x0020..0x007E ||
                cp in 0x0980..0x09FF ||
                cp in 0x0600..0x06FF ||
                cp in 0x0900..0x097F ||
                cp in 0x00A0..0x024F ||
                cp in 0x2000..0x27BF ||
                cp >= 0x1F300
        }
        if (brokenCount.toFloat() / trimmed.length > 0.12f) return false
        return readableCount.toFloat() / trimmed.length >= 0.78f
    }

    private fun splitIntoParagraphs(rawText: String): List<String> {
        val normalized = rawText.replace("\r\n", "\n").replace("\r", "\n")
        return normalized
            .split(Regex("""\n{2,}"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun mapStudioFontKey(fk: String, customFont: String?): Pair<RichFontFamily, String?> {
        if (!customFont.isNullOrBlank()) {
            return WordDocumentCodec.mapWordFontToStudio(customFont)
        }
        return when (fk.lowercase(Locale.US)) {
            "noto_serif_bengali", "tiro_bangla" -> RichFontFamily.BENGALI_SERIF to null
            "english_sans", "inter" -> RichFontFamily.ENGLISH_MODERN to null
            "english_serif", "georgia" -> RichFontFamily.ENGLISH_SERIF to null
            "monospace", "consolas" -> RichFontFamily.MONOSPACE to null
            "nirmala" -> RichFontFamily.NIRMALA_UI to "Nirmala UI"
            "vrinda" -> RichFontFamily.VRINDA to "Vrinda"
            "kalpurush" -> RichFontFamily.KALPURUSH to "Kalpurush"
            "calibri" -> RichFontFamily.CALIBRI to "Calibri"
            "times_new_roman" -> RichFontFamily.TIMES_NEW_ROMAN to "Times New Roman"
            "arial" -> RichFontFamily.ARIAL to "Arial"
            else -> RichFontFamily.BENGALI_SANS to null
        }
    }

    private fun parseCssColorToLong(css: String?, fallback: Long): Long {
        val clean = css?.trim()?.removePrefix("#") ?: return fallback
        if (clean.length != 6) return fallback
        val rgb = clean.toLongOrNull(16) ?: return fallback
        return 0xFF000000L or rgb
    }

    private fun parseAlign(raw: String?): RichTextAlign {
        return when (raw?.lowercase(Locale.US)) {
            "center" -> RichTextAlign.CENTER
            "right" -> RichTextAlign.RIGHT
            else -> RichTextAlign.LEFT
        }
    }
}
