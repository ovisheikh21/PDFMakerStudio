package com.pdfmakerstudio.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.pdfmakerstudio.app.domain.model.DocumentSignature
import com.pdfmakerstudio.app.domain.model.RichFontFamily
import com.pdfmakerstudio.app.domain.model.RichPdfBlock
import com.pdfmakerstudio.app.domain.model.RichTextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToLong

data class ImportedPdfAlternateMode(
    val currentMode: String,
    val blocks: List<RichPdfBlock>,
    val overlays: List<DocumentSignature>
)

data class ImportedWordDocResult(
    val suggestedFileName: String,
    val docTitle: String,
    val blocks: List<RichPdfBlock>,
    val overlays: List<DocumentSignature> = emptyList(),
    val detectedFonts: List<String>,
    val detectedScripts: List<String>,
    val sourceFormat: String,
    val alternateMode: ImportedPdfAlternateMode? = null
)

enum class WordExportFormat(
    val extension: String,
    val mimeType: String
) {
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    DOC("doc", "application/msword"),
    RTF("rtf", "application/rtf"),
    TXT("txt", "text/plain")
}

object WordDocumentCodec {

    private data class ParsedStyle(
        val fontName: String? = null,
        val fontSizeSp: Float? = null,
        val isBold: Boolean? = null,
        val isItalic: Boolean? = null,
        val isUnderline: Boolean? = null,
        val colorHex: Long? = null,
        val align: RichTextAlign? = null
    )

    private data class EmbeddedMediaItem(
        val rId: String,
        val fileNameInZip: String,
        val contentType: String,
        val bytes: ByteArray,
        val widthEmu: Long,
        val heightEmu: Long
    )

    fun mapWordFontToStudio(rawFont: String?): Pair<RichFontFamily, String?> {
        val clean = rawFont?.trim()?.replace("\"", "")?.replace("'", "").orEmpty()
        if (clean.isBlank()) return RichFontFamily.BENGALI_SANS to null
        val lower = clean.lowercase(Locale.US)
        if (lower.matches(Regex("""^g_d\d+_f\d+$""")) ||
            lower.matches(Regex("""^f\d+$""")) ||
            lower == "sans-serif" ||
            lower == "serif" ||
            lower == "monospace" ||
            lower == "system-ui"
        ) {
            return RichFontFamily.BENGALI_SANS to null
        }
        return when {
            lower.contains("noto sans bengali") -> RichFontFamily.BENGALI_SANS to null
            lower.contains("tiro bangla") -> RichFontFamily.BENGALI_SERIF to null
            lower.contains("nirmala") -> RichFontFamily.NIRMALA_UI to "Nirmala UI"
            lower.contains("vrinda") || lower.contains("shonar") -> RichFontFamily.VRINDA to clean
            lower.contains("kalpurush") || lower.contains("solaiman") ||
                lower.contains("nikosh") || lower.contains("siyam") || lower.contains("sutonny") ->
                RichFontFamily.KALPURUSH to clean
            lower.contains("calibri") -> RichFontFamily.CALIBRI to clean
            lower.contains("times new roman") -> RichFontFamily.TIMES_NEW_ROMAN to clean
            lower.contains("arial") || lower.contains("helvetica") -> RichFontFamily.ARIAL to clean
            lower.contains("georgia") || lower.contains("cambria") || lower.contains("garamond") ->
                RichFontFamily.GEORGIA to clean
            lower.contains("consolas") || lower.contains("courier") ||
                lower.contains("jetbrains") || lower.contains("mono") ->
                RichFontFamily.MONOSPACE to clean
            lower.contains("serif") || lower.contains("bangla") || lower.contains("arabic") ->
                RichFontFamily.BENGALI_SERIF to clean
            else -> RichFontFamily.BENGALI_SANS to clean
        }
    }

    private data class WordFontTriple(
        val ascii: String,
        val cs: String,
        val eastAsia: String
    )

    private fun resolveDesktopWordFonts(
        fontFamily: RichFontFamily,
        customFontName: String?
    ): WordFontTriple {
        val cleanCustom = customFontName?.trim()?.replace("\"", "")?.replace("'", "").orEmpty()
        if (cleanCustom.isNotBlank()) {
            val lower = cleanCustom.lowercase(Locale.US)
            val isIndicCapable = lower.contains("nirmala") ||
                lower.contains("vrinda") ||
                lower.contains("shonar") ||
                lower.contains("kalpurush") ||
                lower.contains("nikosh") ||
                lower.contains("solaiman") ||
                lower.contains("noto") ||
                lower.contains("mangal") ||
                lower.contains("arial unicode") ||
                lower.contains("tahoma") ||
                lower.contains("amiri")
            return WordFontTriple(
                ascii = cleanCustom,
                cs = if (isIndicCapable) cleanCustom else "Nirmala UI",
                eastAsia = cleanCustom
            )
        }
        val wordName = fontFamily.wordFontName
        val isIndic = fontFamily.isBengaliOptimized
        return WordFontTriple(
            ascii = wordName,
            cs = if (isIndic) wordName else "Nirmala UI",
            eastAsia = wordName
        )
    }

    const val MAX_UPLOAD_FILE_BYTES: Long = 10L * 1024L * 1024L // 10 MB strict limit

    internal data class BidiLangInfo(
        val bidiLang: String,
        val isRtl: Boolean,
        val scripts: List<String>
    )

    internal fun detectTextScripts(text: String): BidiLangInfo {
        var hasBn = false
        var hasEn = false
        var hasAr = false
        var hasHi = false
        var hasCjk = false
        var rtlCount = 0
        var ltrCount = 0

        for (ch in text) {
            val cp = ch.code
            when {
                cp in 0x0980..0x09FF -> {
                    hasBn = true
                    ltrCount++
                }
                cp in 0x0600..0x06FF -> {
                    hasAr = true
                    rtlCount++
                }
                cp in 0x0900..0x097F && cp != 0x0964 && cp != 0x0965 -> {
                    hasHi = true
                    ltrCount++
                }
                cp in 0x4E00..0x9FFF || cp in 0x3040..0x30FF -> {
                    hasCjk = true
                    ltrCount++
                }
                ch in 'A'..'Z' || ch in 'a'..'z' -> {
                    hasEn = true
                    ltrCount++
                }
            }
        }
        val scripts = buildList {
            if (hasBn) add("বাংলা")
            if (hasEn) add("English")
            if (hasAr) add("العربية")
            if (hasHi) add("हिन्दी")
            if (hasCjk) add("CJK")
        }
        val bidiLang = when {
            hasAr -> "ar-SA"
            hasHi -> "hi-IN"
            else -> "bn-BD"
        }
        return BidiLangInfo(
            bidiLang = bidiLang,
            isRtl = hasAr && rtlCount > ltrCount,
            scripts = scripts
        )
    }

    internal fun queryDeclaredFileSize(context: Context, uri: Uri): Long? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx)
                }
            }
        }
        return null
    }

    internal fun readUriBytesWithLimit(
        context: Context,
        uri: Uri,
        maxBytes: Long = MAX_UPLOAD_FILE_BYTES
    ): ByteArray {
        val declaredSize = queryDeclaredFileSize(context, uri)
        if (declaredSize != null && declaredSize > maxBytes) {
            val sizeMb = String.format(Locale.US, "%.2f", declaredSize / (1024.0 * 1024.0))
            throw IllegalArgumentException(
                "ফাইলের সাইজ $sizeMb MB — সর্বোচ্চ ১০ MB পর্যন্ত PDF বা Word ফাইল আপলোড করা যাবে।"
            )
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to read selected document")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    throw IllegalArgumentException(
                        "ফাইলের সাইজ ১০ MB-এর বেশি হতে পারবে না (সর্বোচ্চ ১০ MB অনুমোদিত)।"
                    )
                }
                out.write(buf, 0, read)
            }
            val result = out.toByteArray()
            if (result.isEmpty()) {
                throw IllegalArgumentException("নির্বাচিত ফাইলটি খালি (0 bytes)।")
            }
            result
        }
    }

    // =========================================================================
    // 1. IMPORT WORD FILES (.docx, .doc, .rtf, .html, .txt)
    // =========================================================================

    suspend fun importWordFile(
        context: Context,
        uri: Uri
    ): Result<ImportedWordDocResult> = withContext(Dispatchers.IO) {
        runCatching {
            val rawFileName = queryDisplayName(context, uri) ?: "Imported_Document"
            val stemName = rawFileName.replace(
                Regex("""\.(docx|doc|rtf|html?|txt)$""", RegexOption.IGNORE_CASE),
                ""
            ).ifBlank { "Imported Document" }
            val ext = rawFileName.substringAfterLast('.', "").lowercase(Locale.US)

            val fileBytes = readUriBytesWithLimit(context, uri)

            val isZip = fileBytes.size >= 4 && fileBytes[0] == 0x50.toByte() && fileBytes[1] == 0x4B.toByte()
            val headText = String(
                fileBytes.copyOfRange(0, minOf(fileBytes.size, 600)),
                StandardCharsets.UTF_8
            ).trimStart()

            val result = when {
                isZip || ext == "docx" -> parseDocxBytes(context, fileBytes, stemName)
                headText.startsWith("{\\rtf", ignoreCase = true) || ext == "rtf" ->
                    parseRtfBytes(fileBytes, stemName)
                headText.contains("<html", ignoreCase = true) ||
                    headText.contains("MIME-Version:", ignoreCase = true) ||
                    ext == "html" || ext == "htm" ->
                    parseHtmlOrWordHtml(fileBytes, stemName, if (ext == "doc") "doc" else "html")
                ext == "doc" -> parseBinaryOrTextDoc(fileBytes, stemName)
                else -> parsePlainText(fileBytes, stemName)
            }

            if (result.blocks.isEmpty()) {
                result.copy(
                    blocks = listOf(RichPdfBlock.TextBlock(text = "")),
                    detectedScripts = listOf("UTF-8")
                )
            } else {
                result
            }
        }
    }

    internal fun queryDisplayName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun parseDocxBytes(
        context: Context,
        bytes: ByteArray,
        stemName: String
    ): ImportedWordDocResult {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val documentXmlBytes = entries["word/document.xml"]
            ?: error("Invalid .docx archive (missing word/document.xml)")

        // Save extracted media files to app private dir
        val relIdToImageUri = mutableMapOf<String, Uri>()
        entries["word/_rels/document.xml.rels"]?.let { relsBytes ->
            val mediaDir = File(context.filesDir, "imported_word_media").apply { mkdirs() }
            val parser = newPullParser(relsBytes)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && localNameOf(parser) == "Relationship") {
                    val rId = parser.getAttributeValue(null, "Id") ?: ""
                    val target = parser.getAttributeValue(null, "Target") ?: ""
                    val normalizedTarget = target.removePrefix("/").let { t ->
                        if (t.startsWith("word/")) t else "word/${t.removePrefix("../")}"
                    }
                    val mediaBytes = entries[normalizedTarget]
                    if (rId.isNotBlank() && mediaBytes != null) {
                        val safeMediaName = normalizedTarget.substringAfterLast('/')
                            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
                        val outFile = File(mediaDir, "${System.currentTimeMillis()}_${rId}_$safeMediaName")
                        FileOutputStream(outFile).use { it.write(mediaBytes) }
                        relIdToImageUri[rId] = Uri.fromFile(outFile)
                    }
                }
                event = parser.next()
            }
        }

        // Parse word/styles.xml
        val stylesMap = mutableMapOf<String, ParsedStyle>()
        var docDefaultStyle = ParsedStyle()
        entries["word/styles.xml"]?.let { stylesBytes ->
            docDefaultStyle = parseStylesXml(stylesBytes, stylesMap)
        }

        val blocks = mutableListOf<RichPdfBlock>()
        val detectedFonts = linkedSetOf<String>()
        val allTextBuilder = StringBuilder()

        // Stream word/document.xml
        val parser = newPullParser(documentXmlBytes)
        var inParagraph = false
        var inParagraphProps = false
        var inRun = false
        var inRunProps = false
        var inText = false

        var pStyleId: String? = null
        var pAlign: RichTextAlign? = null
        var pHasBottomBorder = false
        var pHasPageBreak = false
        val pText = StringBuilder()
        var pPrimaryFontRaw: String? = null
        var pFontSizeSp: Float? = null
        var pBold: Boolean? = null
        var pItalic: Boolean? = null
        var pUnderline: Boolean? = null
        var pColorHex: Long? = null

        var rFontRaw: String? = null
        var rSizeSp: Float? = null
        var rBold: Boolean? = null
        var rItalic: Boolean? = null
        var rUnderline: Boolean? = null
        var rColorHex: Long? = null

        fun flushParagraph() {
            val baseStyle = pStyleId?.let { stylesMap[it] }
            val textVal = pText.toString()
            if (textVal.isNotEmpty()) {
                val chosenRawFont = pPrimaryFontRaw ?: baseStyle?.fontName ?: docDefaultStyle.fontName
                if (!chosenRawFont.isNullOrBlank()) detectedFonts.add(chosenRawFont)
                val (fontFamily, customFont) = mapWordFontToStudio(chosenRawFont)
                val headingFallbackSize = when (pStyleId?.lowercase(Locale.US)) {
                    "title" -> 22f
                    "heading1" -> 19f
                    "heading2" -> 16.5f
                    "heading3" -> 15f
                    else -> null
                }
                val finalSize = (pFontSizeSp ?: baseStyle?.fontSizeSp ?: headingFallbackSize ?: docDefaultStyle.fontSizeSp ?: 14f)
                    .coerceIn(10f, 32f)
                val finalBold = pBold ?: baseStyle?.isBold ?: (pStyleId?.lowercase(Locale.US)?.let { it == "title" || it.startsWith("heading") } == true)
                val finalItalic = pItalic ?: baseStyle?.isItalic ?: false
                val finalUnderline = pUnderline ?: baseStyle?.isUnderline ?: false
                val finalColor = pColorHex ?: baseStyle?.colorHex ?: docDefaultStyle.colorHex ?: 0xFF1E293BL
                val finalAlign = pAlign ?: baseStyle?.align ?: RichTextAlign.LEFT

                allTextBuilder.append(textVal).append('\n')
                blocks.add(
                    RichPdfBlock.TextBlock(
                        text = textVal,
                        fontFamily = fontFamily,
                        customFontName = customFont,
                        fontSizeSp = finalSize,
                        isBold = finalBold,
                        isItalic = finalItalic,
                        isUnderline = finalUnderline,
                        colorHex = finalColor,
                        align = finalAlign
                    )
                )
            } else if (pHasBottomBorder) {
                blocks.add(RichPdfBlock.DividerBlock())
            }
            if (pHasPageBreak) {
                blocks.add(RichPdfBlock.PageBreakBlock())
            }
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = localNameOf(parser)
                    when {
                        tag == "p" -> {
                            inParagraph = true
                            pStyleId = null
                            pAlign = null
                            pHasBottomBorder = false
                            pHasPageBreak = false
                            pText.setLength(0)
                            pPrimaryFontRaw = null
                            pFontSizeSp = null
                            pBold = null
                            pItalic = null
                            pUnderline = null
                            pColorHex = null
                        }
                        inParagraph && tag == "pPr" -> inParagraphProps = true
                        inParagraphProps && tag == "pStyle" -> {
                            pStyleId = attrVal(parser, "val")
                        }
                        inParagraphProps && tag == "jc" -> {
                            pAlign = when (attrVal(parser, "val")?.lowercase(Locale.US)) {
                                "center" -> RichTextAlign.CENTER
                                "right", "end" -> RichTextAlign.RIGHT
                                else -> RichTextAlign.LEFT
                            }
                        }
                        inParagraphProps && tag == "bottom" -> {
                            pHasBottomBorder = true
                        }
                        inParagraphProps && tag == "pageBreakBefore" -> {
                            val v = attrVal(parser, "val")
                            if (v != "0" && v != "false") {
                                if (blocks.isNotEmpty()) blocks.add(RichPdfBlock.PageBreakBlock())
                            }
                        }
                        inParagraph && tag == "r" -> {
                            inRun = true
                            rFontRaw = null
                            rSizeSp = null
                            rBold = null
                            rItalic = null
                            rUnderline = null
                            rColorHex = null
                        }
                        inRun && tag == "rPr" -> inRunProps = true
                        inRunProps && tag == "rFonts" -> {
                            rFontRaw = attrVal(parser, "cs")
                                ?: attrVal(parser, "ascii")
                                ?: attrVal(parser, "hAnsi")
                                ?: attrVal(parser, "eastAsia")
                        }
                        inRunProps && (tag == "sz" || tag == "szCs") -> {
                            attrVal(parser, "val")?.toFloatOrNull()?.let { halfPt ->
                                if (rSizeSp == null || tag == "sz") rSizeSp = halfPt / 2f
                            }
                        }
                        inRunProps && (tag == "b" || tag == "bCs") -> {
                            val v = attrVal(parser, "val")
                            rBold = v != "0" && v != "false"
                        }
                        inRunProps && (tag == "i" || tag == "iCs") -> {
                            val v = attrVal(parser, "val")
                            rItalic = v != "0" && v != "false"
                        }
                        inRunProps && tag == "u" -> {
                            val v = attrVal(parser, "val")
                            rUnderline = v != null && v != "none" && v != "0" && v != "false"
                        }
                        inRunProps && tag == "color" -> {
                            parseWordHexColor(attrVal(parser, "val"))?.let { rColorHex = it }
                        }
                        inRun && tag == "t" -> inText = true
                        inRun && tag == "tab" -> pText.append('\t')
                        inRun && tag == "br" -> {
                            if (attrVal(parser, "type").equals("page", ignoreCase = true)) {
                                pHasPageBreak = true
                            } else {
                                pText.append('\n')
                            }
                        }
                        inRun && (tag == "blip" || tag == "imagedata") -> {
                            val rId = attrVal(parser, "embed") ?: attrVal(parser, "id")
                            val imageUri = rId?.let { relIdToImageUri[it] }
                            if (imageUri != null) {
                                blocks.add(
                                    RichPdfBlock.ImageBlock(
                                        imageUri = imageUri,
                                        widthFraction = 0.85f,
                                        align = pAlign ?: RichTextAlign.CENTER
                                    )
                                )
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText && inRun) {
                        val txt = parser.text ?: ""
                        if (txt.isNotBlank() && pPrimaryFontRaw == null && rFontRaw != null) {
                            pPrimaryFontRaw = rFontRaw
                        }
                        if (txt.isNotBlank() && pFontSizeSp == null && rSizeSp != null) pFontSizeSp = rSizeSp
                        if (txt.isNotBlank() && pBold == null && rBold != null) pBold = rBold
                        if (txt.isNotBlank() && pItalic == null && rItalic != null) pItalic = rItalic
                        if (txt.isNotBlank() && pUnderline == null && rUnderline != null) pUnderline = rUnderline
                        if (txt.isNotBlank() && pColorHex == null && rColorHex != null) pColorHex = rColorHex
                        pText.append(txt)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (localNameOf(parser)) {
                        "t" -> inText = false
                        "rPr" -> inRunProps = false
                        "r" -> {
                            inRun = false
                            inRunProps = false
                            inText = false
                        }
                        "pPr" -> inParagraphProps = false
                        "p" -> {
                            if (inParagraph) flushParagraph()
                            inParagraph = false
                            inParagraphProps = false
                        }
                    }
                }
            }
            event = parser.next()
        }

        // Promote clean opening Title block if non-image first block is clearly a document title
        var docTitle = ""
        val firstBlock = blocks.firstOrNull()
        if (firstBlock is RichPdfBlock.TextBlock && firstBlock.text.length in 1..140 &&
            (firstBlock.fontSizeSp >= 17f || (firstBlock.isBold && blocks.size > 1))
        ) {
            docTitle = firstBlock.text.trim()
            blocks.removeAt(0)
        }

        val scripts = detectTextScripts(allTextBuilder.toString()).scripts.ifEmpty { listOf("Unicode") }
        return ImportedWordDocResult(
            suggestedFileName = stemName,
            docTitle = docTitle,
            blocks = blocks,
            detectedFonts = detectedFonts.toList(),
            detectedScripts = scripts,
            sourceFormat = "docx"
        )
    }

    private fun parseStylesXml(bytes: ByteArray, stylesOut: MutableMap<String, ParsedStyle>): ParsedStyle {
        val parser = newPullParser(bytes)
        var defaultFont: String? = null
        var defaultSize: Float? = null
        var defaultColor: Long? = null

        var inDocDefaults = false
        var currentStyleId: String? = null
        var curFont: String? = null
        var curSize: Float? = null
        var curBold: Boolean? = null
        var curItalic: Boolean? = null
        var curUnderline: Boolean? = null
        var curColor: Long? = null
        var curAlign: RichTextAlign? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = localNameOf(parser)
                    when {
                        tag == "docDefaults" -> inDocDefaults = true
                        tag == "style" -> {
                            currentStyleId = attrVal(parser, "styleId")
                            curFont = null
                            curSize = null
                            curBold = null
                            curItalic = null
                            curUnderline = null
                            curColor = null
                            curAlign = null
                        }
                        tag == "rFonts" -> {
                            val fn = attrVal(parser, "cs")
                                ?: attrVal(parser, "ascii")
                                ?: attrVal(parser, "hAnsi")
                                ?: attrVal(parser, "eastAsia")
                            if (inDocDefaults && defaultFont == null) defaultFont = fn
                            else if (currentStyleId != null && curFont == null) curFont = fn
                        }
                        tag == "sz" || tag == "szCs" -> {
                            val halfPt = attrVal(parser, "val")?.toFloatOrNull()
                            if (halfPt != null) {
                                if (inDocDefaults && defaultSize == null) defaultSize = halfPt / 2f
                                else if (currentStyleId != null && curSize == null) curSize = halfPt / 2f
                            }
                        }
                        tag == "color" -> {
                            val c = parseWordHexColor(attrVal(parser, "val"))
                            if (c != null) {
                                if (inDocDefaults && defaultColor == null) defaultColor = c
                                else if (currentStyleId != null && curColor == null) curColor = c
                            }
                        }
                        tag == "b" || tag == "bCs" -> {
                            if (currentStyleId != null) {
                                val v = attrVal(parser, "val")
                                curBold = v != "0" && v != "false"
                            }
                        }
                        tag == "i" || tag == "iCs" -> {
                            if (currentStyleId != null) {
                                val v = attrVal(parser, "val")
                                curItalic = v != "0" && v != "false"
                            }
                        }
                        tag == "u" -> {
                            if (currentStyleId != null) {
                                val v = attrVal(parser, "val")
                                curUnderline = v != null && v != "none" && v != "0" && v != "false"
                            }
                        }
                        tag == "jc" -> {
                            if (currentStyleId != null) {
                                curAlign = when (attrVal(parser, "val")?.lowercase(Locale.US)) {
                                    "center" -> RichTextAlign.CENTER
                                    "right", "end" -> RichTextAlign.RIGHT
                                    else -> RichTextAlign.LEFT
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (localNameOf(parser)) {
                        "docDefaults" -> inDocDefaults = false
                        "style" -> {
                            currentStyleId?.let { id ->
                                stylesOut[id] = ParsedStyle(
                                    fontName = curFont,
                                    fontSizeSp = curSize,
                                    isBold = curBold,
                                    isItalic = curItalic,
                                    isUnderline = curUnderline,
                                    colorHex = curColor,
                                    align = curAlign
                                )
                            }
                            currentStyleId = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return ParsedStyle(fontName = defaultFont, fontSizeSp = defaultSize, colorHex = defaultColor)
    }

    private fun parseRtfBytes(bytes: ByteArray, stemName: String): ImportedWordDocResult {
        val raw = String(bytes, StandardCharsets.ISO_8859_1)
        val fontNames = mutableMapOf<Int, String>()
        Regex("""\{\\f(\d+)[^}]*?\s([^;{}]+);\}""").findAll(raw).forEach { m ->
            val idx = m.groupValues[1].toIntOrNull()
            val name = m.groupValues[2].trim()
            if (idx != null && name.isNotBlank()) fontNames[idx] = name
        }

        val colors = mutableListOf<Long>(0xFF1E293BL)
        Regex("""\{\\colortbl\s*;([^}]*)\}""").find(raw)?.let { m ->
            m.groupValues[1].split(";").forEach { entry ->
                val r = Regex("""\\red(\d+)""").find(entry)?.groupValues?.get(1)?.toIntOrNull()
                val g = Regex("""\\green(\d+)""").find(entry)?.groupValues?.get(1)?.toIntOrNull()
                val b = Regex("""\\blue(\d+)""").find(entry)?.groupValues?.get(1)?.toIntOrNull()
                if (r != null && g != null && b != null) {
                    val hex = (0xFFL shl 24) or ((r and 0xFF).toLong() shl 16) or ((g and 0xFF).toLong() shl 8) or (b and 0xFF).toLong()
                    colors.add(hex)
                }
            }
        }

        val skipDest = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "footer", "themedata", "colorschememapping")
        val blocks = mutableListOf<RichPdfBlock>()
        val detectedFonts = linkedSetOf<String>()
        val allText = StringBuilder()

        data class RtfState(
            var bold: Boolean = false,
            var italic: Boolean = false,
            var underline: Boolean = false,
            var sizeSp: Float = 14f,
            var align: RichTextAlign = RichTextAlign.LEFT,
            var fontIdx: Int = 0,
            var colorIdx: Int = 0,
            var ucSkip: Int = 1,
            var ignorable: Boolean = false
        )

        val stack = ArrayDeque<RtfState>()
        var state = RtfState()
        val curPara = StringBuilder()
        var paraState = state.copy()

        fun flushPara(addBreakAfter: Boolean = false) {
            val txt = curPara.toString().trimEnd()
            if (txt.isNotEmpty()) {
                val fn = fontNames[paraState.fontIdx]
                if (!fn.isNullOrBlank()) detectedFonts.add(fn)
                val (family, custom) = mapWordFontToStudio(fn)
                val cHex = colors.getOrElse(paraState.colorIdx) { 0xFF1E293BL }
                allText.append(txt).append('\n')
                blocks.add(
                    RichPdfBlock.TextBlock(
                        text = txt,
                        fontFamily = family,
                        customFontName = custom,
                        fontSizeSp = paraState.sizeSp.coerceIn(10f, 30f),
                        isBold = paraState.bold,
                        isItalic = paraState.italic,
                        isUnderline = paraState.underline,
                        colorHex = cHex,
                        align = paraState.align
                    )
                )
            }
            curPara.setLength(0)
            paraState = state.copy()
            if (addBreakAfter && blocks.isNotEmpty()) {
                blocks.add(RichPdfBlock.PageBreakBlock())
            }
        }

        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when (ch) {
                '{' -> {
                    stack.addLast(state.copy())
                    i++
                }
                '}' -> {
                    if (stack.isNotEmpty()) state = stack.removeLast()
                    i++
                }
                '\\' -> {
                    i++
                    if (i >= raw.length) break
                    val next = raw[i]
                    when {
                        next == '\\' || next == '{' || next == '}' -> {
                            if (!state.ignorable) curPara.append(next)
                            i++
                        }
                        next == '*' -> {
                            state.ignorable = true
                            i++
                        }
                        next == '~' -> {
                            if (!state.ignorable) curPara.append(' ')
                            i++
                        }
                        next == '\'' && i + 2 < raw.length -> {
                            val hex = raw.substring(i + 1, i + 3).toIntOrNull(16)
                            if (hex != null && !state.ignorable) {
                                curPara.append(hex.toChar())
                            }
                            i += 3
                        }
                        next in 'a'..'z' || next in 'A'..'Z' -> {
                            val wordStart = i
                            while (i < raw.length && (raw[i] in 'a'..'z' || raw[i] in 'A'..'Z')) i++
                            val word = raw.substring(wordStart, i)
                            val paramStart = i
                            if (i < raw.length && (raw[i] == '-' || raw[i] in '0'..'9')) {
                                if (raw[i] == '-') i++
                                while (i < raw.length && raw[i] in '0'..'9') i++
                            }
                            val param = if (i > paramStart) raw.substring(paramStart, i).toIntOrNull() else null
                            if (i < raw.length && raw[i] == ' ') i++

                            if (word in skipDest) state.ignorable = true
                            if (!state.ignorable) {
                                when (word) {
                                    "par", "line" -> flushPara(false)
                                    "page" -> flushPara(true)
                                    "tab" -> curPara.append('\t')
                                    "b" -> {
                                        state.bold = param != 0
                                        if (curPara.isEmpty()) paraState.bold = state.bold
                                    }
                                    "i" -> {
                                        state.italic = param != 0
                                        if (curPara.isEmpty()) paraState.italic = state.italic
                                    }
                                    "ul" -> {
                                        state.underline = true
                                        if (curPara.isEmpty()) paraState.underline = true
                                    }
                                    "ulnone" -> {
                                        state.underline = false
                                        if (curPara.isEmpty()) paraState.underline = false
                                    }
                                    "fs" -> if (param != null) {
                                        state.sizeSp = param / 2f
                                        if (curPara.isEmpty()) paraState.sizeSp = state.sizeSp
                                    }
                                    "f" -> if (param != null) {
                                        state.fontIdx = param
                                        if (curPara.isEmpty()) paraState.fontIdx = param
                                    }
                                    "cf" -> if (param != null) {
                                        state.colorIdx = param
                                        if (curPara.isEmpty()) paraState.colorIdx = param
                                    }
                                    "qc" -> {
                                        state.align = RichTextAlign.CENTER
                                        paraState.align = RichTextAlign.CENTER
                                    }
                                    "qr" -> {
                                        state.align = RichTextAlign.RIGHT
                                        paraState.align = RichTextAlign.RIGHT
                                    }
                                    "ql" -> {
                                        state.align = RichTextAlign.LEFT
                                        paraState.align = RichTextAlign.LEFT
                                    }
                                    "uc" -> if (param != null) state.ucSkip = param.coerceAtLeast(0)
                                    "u" -> if (param != null) {
                                        val code = if (param < 0) param + 65536 else param
                                        if (code > 0) curPara.append(code.toChar())
                                        var skipped = 0
                                        while (skipped < state.ucSkip && i < raw.length && raw[i] !in setOf('{', '}', '\\')) {
                                            i++
                                            skipped++
                                        }
                                    }
                                }
                            }
                        }
                        else -> i++
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (!state.ignorable) {
                        if (curPara.isEmpty()) paraState = state.copy()
                        curPara.append(ch)
                    }
                    i++
                }
            }
        }
        flushPara(false)

        val info = detectTextScripts(allText.toString())
        return ImportedWordDocResult(
            suggestedFileName = stemName,
            docTitle = "",
            blocks = blocks,
            detectedFonts = detectedFonts.toList(),
            detectedScripts = info.scripts.ifEmpty { listOf("RTF Unicode") },
            sourceFormat = "rtf"
        )
    }

    private fun parseHtmlOrWordHtml(
        bytes: ByteArray,
        stemName: String,
        sourceFormat: String
    ): ImportedWordDocResult {
        val raw = String(bytes, StandardCharsets.UTF_8)
        val bodyHtml = raw
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<xml[\s\S]*?</xml>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</(p|div|h[1-6]|li|tr)>""", RegexOption.IGNORE_CASE), "\n\n")

        val stripped = bodyHtml
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        val fontMatch = Regex("""font-family\s*:\s*['"]?([^'";}]+)""", RegexOption.IGNORE_CASE).find(raw)
        val rawFont = fontMatch?.groupValues?.get(1)?.split(',')?.firstOrNull()?.trim()
        val (fontFamily, customFont) = mapWordFontToStudio(rawFont)

        val paragraphs = stripped.split(Regex("""\n{2,}""")).map { it.trim() }.filter { it.isNotBlank() }
        val blocks = paragraphs.mapIndexed { idx, para ->
            RichPdfBlock.TextBlock(
                text = para,
                fontFamily = fontFamily,
                customFontName = customFont,
                fontSizeSp = if (idx == 0 && para.length < 90) 18f else 14f,
                isBold = idx == 0 && para.length < 90
            )
        }
        val info = detectTextScripts(stripped)
        return ImportedWordDocResult(
            suggestedFileName = stemName,
            docTitle = "",
            blocks = blocks,
            detectedFonts = listOfNotNull(customFont ?: rawFont),
            detectedScripts = info.scripts.ifEmpty { listOf("UTF-8") },
            sourceFormat = sourceFormat
        )
    }

    private fun parseBinaryOrTextDoc(bytes: ByteArray, stemName: String): ImportedWordDocResult {
        // Check if UTF-16LE OLE text runs contain readable Unicode runs
        val utf16Runs = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i + 1 < bytes.size) {
            val code = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            val ch = code.toChar()
            if (isPrintableDocChar(ch)) {
                cur.append(ch)
            } else {
                val s = cur.toString().trim()
                if (s.length >= 4 && !looksBinaryGarbage(s)) utf16Runs.add(s)
                cur.setLength(0)
            }
            i += 2
        }
        val tail = cur.toString().trim()
        if (tail.length >= 4 && !looksBinaryGarbage(tail)) utf16Runs.add(tail)

        val blocks = if (utf16Runs.isNotEmpty()) {
            utf16Runs
                .flatMap { it.split('\r', '\n') }
                .map { it.trim() }
                .filter { it.length >= 2 }
                .distinct()
                .map { line ->
                    RichPdfBlock.TextBlock(
                        text = line,
                        fontFamily = RichFontFamily.NIRMALA_UI,
                        customFontName = "Nirmala UI"
                    )
                }
        } else {
            return parsePlainText(bytes, stemName, "doc")
        }

        val combined = blocks.joinToString("\n") { it.text }
        val info = detectTextScripts(combined)
        return ImportedWordDocResult(
            suggestedFileName = stemName,
            docTitle = "",
            blocks = blocks,
            detectedFonts = listOf("Nirmala UI"),
            detectedScripts = info.scripts.ifEmpty { listOf("Word Classic") },
            sourceFormat = "doc"
        )
    }

    private fun parsePlainText(
        bytes: ByteArray,
        stemName: String,
        sourceFormat: String = "txt"
    ): ImportedWordDocResult {
        val text = String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        val paragraphs = text.replace("\r\n", "\n").split(Regex("""\n{2,}""")).map { it.trimEnd() }.filter { it.isNotBlank() }
        val blocks = paragraphs.map { p ->
            RichPdfBlock.TextBlock(
                text = p,
                fontFamily = RichFontFamily.BENGALI_SANS
            )
        }
        val info = detectTextScripts(text)
        return ImportedWordDocResult(
            suggestedFileName = stemName,
            docTitle = "",
            blocks = blocks,
            detectedFonts = emptyList(),
            detectedScripts = info.scripts.ifEmpty { listOf("UTF-8") },
            sourceFormat = sourceFormat
        )
    }

    private fun isPrintableDocChar(ch: Char): Boolean {
        val cp = ch.code
        return ch == '\n' || ch == '\r' || ch == '\t' ||
            cp in 0x0020..0x007E ||
            cp in 0x0980..0x09FF || // Bengali
            cp in 0x0600..0x06FF || // Arabic
            cp in 0x0900..0x097F || // Devanagari
            cp in 0x4E00..0x9FFF || // CJK
            cp in 0x00A0..0x024F
    }

    private fun looksBinaryGarbage(s: String): Boolean {
        if (s.contains("Microsoft Word") || s.contains("Root Entry") || s.contains("WordDocument")) return true
        val weirdCount = s.count { it == '\uFFFD' || it == '\u0000' }
        return weirdCount > s.length / 4
    }

    // =========================================================================
    // 2. DESKTOP-EXACT MICROSOFT WORD EXPORT (.docx, .doc, .rtf, .txt)
    // =========================================================================

    suspend fun exportDocumentToWordCacheFile(
        context: Context,
        format: WordExportFormat,
        fileName: String,
        docTitle: String,
        blocks: List<RichPdfBlock>,
        signatures: List<DocumentSignature> = emptyList()
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outDir = File(context.cacheDir, "generated").apply { mkdirs() }
            val safeBase = PdfDeviceSaver.ensureFileExtension(fileName, format.extension)
                .removeSuffix(".${format.extension}")
            val outFile = File(outDir, "${safeBase}_${System.currentTimeMillis()}.${format.extension}")

            when (format) {
                WordExportFormat.DOCX -> writeDocxFile(context, outFile, docTitle, blocks, signatures)
                WordExportFormat.DOC -> writeLegacyWordDocHtmlFile(context, outFile, docTitle, blocks, signatures)
                WordExportFormat.RTF -> writeRtfFile(outFile, docTitle, blocks)
                WordExportFormat.TXT -> writeTxtFile(outFile, docTitle, blocks)
            }
            outFile
        }
    }

    private fun writeDocxFile(
        context: Context,
        outFile: File,
        docTitle: String,
        blocks: List<RichPdfBlock>,
        signatures: List<DocumentSignature>
    ) {
        val mediaItems = mutableListOf<EmbeddedMediaItem>()
        val usedFontNames = linkedSetOf(
            "Nirmala UI",
            "Vrinda",
            "Kalpurush",
            "Calibri",
            "Times New Roman",
            "Georgia",
            "Arial",
            "Consolas"
        )

        val bodyXml = StringBuilder()

        if (docTitle.isNotBlank()) {
            val info = detectTextScripts(docTitle)
            bodyXml.append("<w:p><w:pPr>")
            bodyXml.append("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"6\" w:color=\"CBD5E1\"/></w:pBdr>")
            bodyXml.append("<w:spacing w:before=\"0\" w:after=\"200\"/>")
            if (info.isRtl) bodyXml.append("<w:bidi/>")
            bodyXml.append("</w:pPr>")
            bodyXml.append(
                buildWordRunXml(
                    text = docTitle.trim(),
                    triple = WordFontTriple("Nirmala UI", "Nirmala UI", "Nirmala UI"),
                    sizeHalfPt = 40,
                    bold = true,
                    italic = false,
                    underline = false,
                    colorHex = "0F172A",
                    bidiLang = info.bidiLang
                )
            )
            bodyXml.append("</w:p>")
        }

        var currentPageIndex = 0
        val appendedSigIds = mutableSetOf<String>()
        fun appendSignaturesForPage(targetPageIdx: Int, includeRemaining: Boolean = false) {
            signatures.filter {
                it.id !in appendedSigIds && (includeRemaining || it.pageIndex == targetPageIdx)
            }.forEach { sig ->
                val sigFile = File(sig.imagePath)
                if (sigFile.exists()) {
                    val fileBytes = sigFile.readBytes()
                    val isJpeg = fileBytes.size >= 2 &&
                        fileBytes[0] == 0xFF.toByte() &&
                        fileBytes[1] == 0xD8.toByte()
                    val ext = if (isJpeg) "jpg" else "png"
                    val mime = if (isJpeg) "image/jpeg" else "image/png"
                    val prefix = if (sig.kind == "image") "overlay" else "sig"
                    val targetEmu = (5_400_000.0 * sig.widthFraction.coerceIn(0.12f, 0.90f)).roundToLong()
                    val item = loadMediaForDocx(fileBytes, "${prefix}_${mediaItems.size + 1}.$ext", mime, targetEmu)
                    if (item != null) {
                        mediaItems.add(item)
                        appendedSigIds.add(sig.id)
                        val centerX = sig.xFraction + sig.widthFraction * 0.5f
                        val alignVal = when {
                            sig.xFraction < 0.22f && centerX < 0.45f -> "left"
                            centerX > 0.58f -> "right"
                            else -> "center"
                        }
                        bodyXml.append("<w:p><w:pPr><w:jc w:val=\"$alignVal\"/><w:spacing w:before=\"160\" w:after=\"120\"/></w:pPr>")
                        bodyXml.append(buildInlineDrawingXml(item, mediaItems.size))
                        bodyXml.append("</w:p>")
                    }
                }
            }
        }

        for (block in blocks) {
            when (block) {
                is RichPdfBlock.PageBreakBlock -> {
                    appendSignaturesForPage(currentPageIndex)
                    currentPageIndex++
                    bodyXml.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
                is RichPdfBlock.DividerBlock -> {
                    val hex = String.format(Locale.US, "%06X", block.colorHex and 0xFFFFFFL)
                    bodyXml.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"$hex\"/></w:pBdr><w:spacing w:before=\"120\" w:after=\"120\"/></w:pPr></w:p>")
                }
                is RichPdfBlock.ImageBlock -> {
                    val imgBytes = readImageBytesNormalized(context, block.imageUri) ?: continue
                    val targetEmu = (5_400_000.0 * block.widthFraction.coerceIn(0.35f, 1f)).roundToLong()
                    val item = loadMediaForDocx(imgBytes, "img_${mediaItems.size + 1}.jpg", "image/jpeg", targetEmu) ?: continue
                    mediaItems.add(item)
                    val alignVal = block.align.toWordAlign()
                    bodyXml.append("<w:p><w:pPr><w:jc w:val=\"$alignVal\"/><w:spacing w:before=\"120\" w:after=\"80\"/></w:pPr>")
                    bodyXml.append(buildInlineDrawingXml(item, mediaItems.size))
                    bodyXml.append("</w:p>")
                    if (block.caption.isNotBlank()) {
                        val info = detectTextScripts(block.caption)
                        bodyXml.append("<w:p><w:pPr><w:jc w:val=\"$alignVal\"/><w:spacing w:after=\"140\"/></w:pPr>")
                        bodyXml.append(
                            buildWordRunXml(
                                text = block.caption,
                                triple = WordFontTriple("Nirmala UI", "Nirmala UI", "Nirmala UI"),
                                sizeHalfPt = 21,
                                bold = false,
                                italic = true,
                                underline = false,
                                colorHex = "64748B",
                                bidiLang = info.bidiLang
                            )
                        )
                        bodyXml.append("</w:p>")
                    }
                }
                is RichPdfBlock.TextBlock -> {
                    val fullText = if (!block.leadingIcon.isNullOrBlank()) {
                        "${block.leadingIcon}  ${block.text}"
                    } else {
                        block.text
                    }
                    val triple = resolveDesktopWordFonts(block.fontFamily, block.customFontName)
                    usedFontNames.add(triple.ascii)
                    usedFontNames.add(triple.cs)
                    val sizeHalfPt = (block.fontSizeSp.coerceIn(8f, 48f) * 2f).toInt()
                    val colorHex = String.format(Locale.US, "%06X", block.colorHex and 0xFFFFFFL)
                    val alignVal = block.align.toWordAlign()

                    val lines = fullText.replace("\r\n", "\n").split('\n')
                    for (line in lines) {
                        val info = detectTextScripts(line)
                        bodyXml.append("<w:p><w:pPr>")
                        bodyXml.append("<w:jc w:val=\"$alignVal\"/>")
                        if (info.isRtl) bodyXml.append("<w:bidi/>")
                        bodyXml.append("<w:spacing w:before=\"0\" w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/>")
                        bodyXml.append("</w:pPr>")
                        if (line.isNotEmpty()) {
                            bodyXml.append(
                                buildWordRunXml(
                                    text = line,
                                    triple = triple,
                                    sizeHalfPt = sizeHalfPt,
                                    bold = block.isBold,
                                    italic = block.isItalic,
                                    underline = block.isUnderline,
                                    colorHex = colorHex,
                                    bidiLang = info.bidiLang
                                )
                            )
                        }
                        bodyXml.append("</w:p>")
                    }
                }
            }
        }
        appendSignaturesForPage(currentPageIndex, includeRemaining = true)

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>
    $bodyXml
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>"""

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/fontTable.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"/>
  <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val mediaRels = mediaItems.joinToString("\n") { m ->
            """  <Relationship Id="${m.rId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${m.fileNameInZip}"/>"""
        }
        val docRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rIdFonts" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable" Target="fontTable.xml"/>
  <Relationship Id="rIdSettings" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>
$mediaRels
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Nirmala UI" w:eastAsia="Nirmala UI"/>
        <w:sz w:val="28"/>
        <w:szCs w:val="28"/>
        <w:lang w:val="en-US" w:eastAsia="zh-CN" w:bidi="bn-BD"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
  </w:style>
</w:styles>"""

        val fontEntriesXml = usedFontNames.joinToString("\n") { fn ->
            val safe = escapeXml(fn)
            """  <w:font w:name="$safe"><w:charset w:val="00"/><w:family w:val="auto"/><w:pitch w:val="variable"/></w:font>"""
        }
        val fontTableXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:fonts xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
$fontEntriesXml
</w:fonts>"""

        val settingsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:themeFontLang w:val="en-US" w:bidi="bn-BD"/>
  <w:compat>
    <w:compatSetting w:name="compatibilityMode" w:uri="http://schemas.microsoft.com/office/word" w:val="15"/>
  </w:compat>
</w:settings>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            fun putText(name: String, xml: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(xml.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
            putText("[Content_Types].xml", contentTypesXml)
            putText("_rels/.rels", rootRelsXml)
            putText("word/_rels/document.xml.rels", docRelsXml)
            putText("word/styles.xml", stylesXml)
            putText("word/fontTable.xml", fontTableXml)
            putText("word/settings.xml", settingsXml)
            putText("word/document.xml", documentXml)
            mediaItems.forEach { m ->
                zos.putNextEntry(ZipEntry("word/media/${m.fileNameInZip}"))
                zos.write(m.bytes)
                zos.closeEntry()
            }
        }
    }

    private fun buildWordRunXml(
        text: String,
        triple: WordFontTriple,
        sizeHalfPt: Int,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        colorHex: String,
        bidiLang: String
    ): String {
        val sb = StringBuilder()
        sb.append("<w:r><w:rPr>")
        sb.append(
            "<w:rFonts w:ascii=\"${escapeXml(triple.ascii)}\" w:hAnsi=\"${escapeXml(triple.ascii)}\" w:cs=\"${escapeXml(triple.cs)}\" w:eastAsia=\"${escapeXml(triple.eastAsia)}\"/>"
        )
        if (bold) sb.append("<w:b/><w:bCs/>")
        if (italic) sb.append("<w:i/><w:iCs/>")
        if (underline) sb.append("<w:u w:val=\"single\"/>")
        sb.append("<w:color w:val=\"$colorHex\"/>")
        sb.append("<w:sz w:val=\"$sizeHalfPt\"/><w:szCs w:val=\"$sizeHalfPt\"/>")
        sb.append("<w:lang w:val=\"en-US\" w:bidi=\"$bidiLang\"/>")
        sb.append("</w:rPr>")

        val tabParts = text.split('\t')
        tabParts.forEachIndexed { idx, part ->
            if (idx > 0) sb.append("<w:tab/>")
            if (part.isNotEmpty() || tabParts.size == 1) {
                sb.append("<w:t xml:space=\"preserve\">${escapeXml(part)}</w:t>")
            }
        }
        sb.append("</w:r>")
        return sb.toString()
    }

    private fun loadMediaForDocx(
        bytes: ByteArray,
        fileNameInZip: String,
        contentType: String,
        targetWidthEmu: Long
    ): EmbeddedMediaItem? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val wPx = opts.outWidth.coerceAtLeast(1)
        val hPx = opts.outHeight.coerceAtLeast(1)
        val heightEmu = (targetWidthEmu.toDouble() * hPx.toDouble() / wPx.toDouble()).roundToLong()
            .coerceAtLeast(100_000L)
        val rId = "rImg${UUID.randomUUID().toString().replace("-", "").take(8)}"
        return EmbeddedMediaItem(
            rId = rId,
            fileNameInZip = fileNameInZip,
            contentType = contentType,
            bytes = bytes,
            widthEmu = targetWidthEmu,
            heightEmu = heightEmu
        )
    }

    private fun buildInlineDrawingXml(item: EmbeddedMediaItem, docPrId: Int): String {
        return """<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="${item.widthEmu}" cy="${item.heightEmu}"/><wp:docPr id="$docPrId" name="Picture $docPrId"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$docPrId" name="${escapeXml(item.fileNameInZip)}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="${item.rId}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${item.widthEmu}" cy="${item.heightEmu}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"""
    }

    private fun readImageBytesNormalized(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
            val maxDim = 1600
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height).toFloat())
            val finalBmp = if (scale < 0.98f) {
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            } else bmp
            val out = ByteArrayOutputStream()
            finalBmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
            if (finalBmp != bmp) finalBmp.recycle()
            bmp.recycle()
            out.toByteArray()
        }.getOrNull()
    }

    private fun writeLegacyWordDocHtmlFile(
        context: Context,
        outFile: File,
        docTitle: String,
        blocks: List<RichPdfBlock>,
        signatures: List<DocumentSignature>
    ) {
        val sb = StringBuilder()
        sb.append("<html xmlns:o=\"urn:schemas-microsoft-com:office:office\" xmlns:w=\"urn:schemas-microsoft-com:office:word\" xmlns=\"http://www.w3.org/TR/REC-html40\">")
        sb.append("<head><meta charset=\"utf-8\"/><style>@page WordSection1{size:595.3pt 841.9pt;margin:72pt 72pt 72pt 72pt;}div.WordSection1{page:WordSection1;}body{font-family:'Nirmala UI','Calibri',sans-serif;}</style></head>")
        sb.append("<body><div class=\"WordSection1\">")

        if (docTitle.isNotBlank()) {
            sb.append("<h1 style=\"font-family:'Nirmala UI','Calibri',sans-serif;font-size:20pt;border-bottom:1pt solid #cbd5e1;padding-bottom:6pt;\">${escapeHtml(docTitle)}</h1>")
        }

        var pageIdx = 0
        for (block in blocks) {
            when (block) {
                is RichPdfBlock.PageBreakBlock -> {
                    pageIdx++
                    sb.append("<br clear=\"all\" style=\"page-break-before:always\"/>")
                }
                is RichPdfBlock.DividerBlock -> {
                    val hex = String.format(Locale.US, "#%06X", block.colorHex and 0xFFFFFFL)
                    sb.append("<hr style=\"border:none;border-top:1pt solid $hex;margin:10pt 0;\"/>")
                }
                is RichPdfBlock.ImageBlock -> {
                    val bytes = readImageBytesNormalized(context, block.imageUri) ?: continue
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val pct = (block.widthFraction.coerceIn(0.35f, 1f) * 100).toInt()
                    val alignCss = block.align.toWordAlign()
                    sb.append("<p style=\"text-align:$alignCss;\"><img src=\"data:image/jpeg;base64,$b64\" style=\"width:$pct%;max-width:100%;\"/></p>")
                    if (block.caption.isNotBlank()) {
                        sb.append("<p style=\"text-align:$alignCss;color:#64748b;font-style:italic;font-size:10pt;\">${escapeHtml(block.caption)}</p>")
                    }
                }
                is RichPdfBlock.TextBlock -> {
                    val triple = resolveDesktopWordFonts(block.fontFamily, block.customFontName)
                    val hex = String.format(Locale.US, "#%06X", block.colorHex and 0xFFFFFFL)
                    val alignCss = block.align.toWordAlign()
                    val full = if (!block.leadingIcon.isNullOrBlank()) "${block.leadingIcon}  ${block.text}" else block.text
                    val info = detectTextScripts(full)
                    val dir = if (info.isRtl) "dir=\"rtl\"" else ""
                    val style = buildString {
                        append("font-family:'${escapeHtml(triple.ascii)}','${escapeHtml(triple.cs)}','Nirmala UI',sans-serif;")
                        append("font-size:${block.fontSizeSp.toInt()}pt;")
                        append("color:$hex;")
                        append("text-align:$alignCss;")
                        append("font-weight:${if (block.isBold) "bold" else "normal"};")
                        append("font-style:${if (block.isItalic) "italic" else "normal"};")
                        if (block.isUnderline) append("text-decoration:underline;")
                        append("white-space:pre-wrap;")
                    }
                    sb.append("<p $dir style=\"$style\">${escapeHtml(full)}</p>")
                }
            }
        }

        signatures.forEach { sig ->
            val f = File(sig.imagePath)
            if (f.exists()) {
                val fileBytes = f.readBytes()
                val isJpeg = fileBytes.size >= 2 &&
                    fileBytes[0] == 0xFF.toByte() &&
                    fileBytes[1] == 0xD8.toByte()
                val mime = if (isJpeg) "image/jpeg" else "image/png"
                val b64 = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
                val centerX = sig.xFraction + sig.widthFraction * 0.5f
                val alignCss = when {
                    sig.xFraction < 0.22f && centerX < 0.45f -> "left"
                    centerX > 0.58f -> "right"
                    else -> "center"
                }
                val pct = (sig.widthFraction.coerceIn(0.10f, 0.90f) * 100).toInt()
                sb.append("<p style=\"text-align:$alignCss;\"><img src=\"data:$mime;base64,$b64\" style=\"width:$pct%;max-width:100%;\"/></p>")
            }
        }

        sb.append("</div></body></html>")
        outFile.writeText("\uFEFF$sb", StandardCharsets.UTF_8)
    }

    private fun writeRtfFile(
        outFile: File,
        docTitle: String,
        blocks: List<RichPdfBlock>
    ) {
        val sb = StringBuilder()
        sb.append("{\\rtf1\\ansi\\deff0\\uc1\n")
        sb.append("{\\fonttbl{\\f0\\fnil\\fcharset0 Nirmala UI;}{\\f1\\froman\\fcharset0 Vrinda;}{\\f2\\fswiss\\fcharset0 Calibri;}{\\f3\\froman\\fcharset0 Times New Roman;}{\\f4\\fmodern\\fcharset0 Consolas;}}\n")
        sb.append("{\\colortbl ;\\red15\\green23\\blue42;\\red30\\green41\\blue59;\\red100\\green116\\blue139;}\n")
        sb.append("\\paperw11906\\paperh16838\\margl1440\\margr1440\\margt1440\\margb1440\n")

        if (docTitle.isNotBlank()) {
            sb.append("\\pard\\ql\\f0\\fs40\\b ").append(escapeRtfUnicode(docTitle)).append("\\b0\\par\\par\n")
        }
        for (block in blocks) {
            when (block) {
                is RichPdfBlock.PageBreakBlock -> sb.append("\\page\n")
                is RichPdfBlock.DividerBlock -> sb.append("\\pard\\qc____________________________\\par\n")
                is RichPdfBlock.ImageBlock -> {
                    if (block.caption.isNotBlank()) {
                        sb.append("\\pard\\qc\\i ").append(escapeRtfUnicode(block.caption)).append("\\i0\\par\n")
                    }
                }
                is RichPdfBlock.TextBlock -> {
                    val fIdx = when (block.fontFamily) {
                        RichFontFamily.BENGALI_SERIF, RichFontFamily.VRINDA -> 1
                        RichFontFamily.ENGLISH_MODERN, RichFontFamily.CALIBRI, RichFontFamily.ARIAL -> 2
                        RichFontFamily.ENGLISH_SERIF, RichFontFamily.TIMES_NEW_ROMAN, RichFontFamily.GEORGIA -> 3
                        RichFontFamily.MONOSPACE -> 4
                        else -> 0
                    }
                    val jc = when (block.align) {
                        RichTextAlign.CENTER -> "\\qc"
                        RichTextAlign.RIGHT -> "\\qr"
                        RichTextAlign.LEFT -> "\\ql"
                    }
                    val sz = (block.fontSizeSp.coerceIn(8f, 48f) * 2f).toInt()
                    sb.append("\\pard$jc\\f$fIdx\\fs$sz")
                    if (block.isBold) sb.append("\\b")
                    if (block.isItalic) sb.append("\\i")
                    if (block.isUnderline) sb.append("\\ul")
                    sb.append(' ')
                    val full = if (!block.leadingIcon.isNullOrBlank()) "${block.leadingIcon}  ${block.text}" else block.text
                    sb.append(escapeRtfUnicode(full))
                    if (block.isUnderline) sb.append("\\ulnone")
                    if (block.isItalic) sb.append("\\i0")
                    if (block.isBold) sb.append("\\b0")
                    sb.append("\\par\n")
                }
            }
        }
        sb.append("}")
        outFile.writeText(sb.toString(), StandardCharsets.US_ASCII)
    }

    private fun writeTxtFile(
        outFile: File,
        docTitle: String,
        blocks: List<RichPdfBlock>
    ) {
        val sb = StringBuilder("\uFEFF")
        if (docTitle.isNotBlank()) {
            sb.append(docTitle.trim()).append("\r\n").append("=".repeat(minOf(40, docTitle.trim().length))).append("\r\n\r\n")
        }
        for (block in blocks) {
            when (block) {
                is RichPdfBlock.TextBlock -> {
                    val full = if (!block.leadingIcon.isNullOrBlank()) "${block.leadingIcon}  ${block.text}" else block.text
                    sb.append(full).append("\r\n\r\n")
                }
                is RichPdfBlock.DividerBlock -> sb.append("----------------------------------------\r\n\r\n")
                is RichPdfBlock.PageBreakBlock -> sb.append("\r\n--- Page Break ---\r\n\r\n")
                is RichPdfBlock.ImageBlock -> if (block.caption.isNotBlank()) sb.append("[Image: ${block.caption}]\r\n\r\n")
            }
        }
        outFile.writeText(sb.toString(), StandardCharsets.UTF_8)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun RichTextAlign.toWordAlign(): String = when (this) {
        RichTextAlign.LEFT -> "left"
        RichTextAlign.CENTER -> "center"
        RichTextAlign.RIGHT -> "right"
    }

    private fun newPullParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        val xmlStr = String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        parser.setInput(StringReader(xmlStr))
        return parser
    }

    private fun localNameOf(parser: XmlPullParser): String {
        val name = parser.name ?: return ""
        return name.substringAfter(':')
    }

    private fun attrVal(parser: XmlPullParser, localAttr: String): String? {
        for (i in 0 until parser.attributeCount) {
            val n = parser.getAttributeName(i) ?: continue
            if (n == localAttr || n.substringAfter(':') == localAttr) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun parseWordHexColor(raw: String?): Long? {
        val cleaned = raw?.trim()?.removePrefix("#") ?: return null
        if (cleaned.equals("auto", ignoreCase = true) || cleaned.length != 6) return null
        val rgb = cleaned.toLongOrNull(16) ?: return null
        return 0xFF000000L or rgb
    }

    private fun escapeXml(str: String): String = str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun escapeHtml(str: String): String = str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun escapeRtfUnicode(str: String): String {
        val sb = StringBuilder()
        for (ch in str) {
            val code = ch.code
            when {
                ch == '\n' -> sb.append("\\line ")
                ch == '\r' -> {}
                ch == '\t' -> sb.append("\\tab ")
                ch == '\\' || ch == '{' || ch == '}' -> sb.append('\\').append(ch)
                code in 32..126 -> sb.append(ch)
                else -> {
                    val signed = if (code > 32767) code - 65536 else code
                    sb.append("\\u").append(signed).append('?')
                }
            }
        }
        return sb.toString()
    }
}
