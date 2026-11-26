package com.pdfmakerstudio.app.data.draft

import android.content.Context
import android.net.Uri
import com.pdfmakerstudio.app.domain.model.DocumentSignature
import com.pdfmakerstudio.app.domain.model.GlobalImagePdfOptions
import com.pdfmakerstudio.app.domain.model.ImageMarginMode
import com.pdfmakerstudio.app.domain.model.ImagePageItem
import com.pdfmakerstudio.app.domain.model.ImageResolutionPreset
import com.pdfmakerstudio.app.domain.model.ImageSizeMode
import com.pdfmakerstudio.app.domain.model.PageTextPlacement
import com.pdfmakerstudio.app.domain.model.RichFontFamily
import com.pdfmakerstudio.app.domain.model.RichPdfBlock
import com.pdfmakerstudio.app.domain.model.RichTextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

sealed interface StudioDraft {
    val id: String
    val fileName: String
    val updatedAtMillis: Long

    data class TextDraft(
        override val id: String,
        override val fileName: String,
        val docTitle: String,
        val blocks: List<RichPdfBlock>,
        val signatures: List<DocumentSignature> = emptyList(),
        override val updatedAtMillis: Long
    ) : StudioDraft

    data class ImagesDraft(
        override val id: String,
        override val fileName: String,
        val pages: List<ImagePageItem>,
        val options: GlobalImagePdfOptions,
        override val updatedAtMillis: Long
    ) : StudioDraft
}

class StudioDraftStore(private val context: Context) {

    private val storeFile: File
        get() = File(context.filesDir, "studio_drafts_v1.json")

    suspend fun listDrafts(): List<StudioDraft> = withContext(Dispatchers.IO) {
        runCatching {
            if (!storeFile.exists()) return@runCatching emptyList()
            val raw = storeFile.readText()
            val arr = JSONArray(raw)
            val result = mutableListOf<StudioDraft>()
            for (i in 0 until arr.length()) {
                parseDraft(arr.getJSONObject(i))?.let { result.add(it) }
            }
            result.sortedByDescending { it.updatedAtMillis }
        }.getOrDefault(emptyList())
    }

    suspend fun saveDraft(draft: StudioDraft): List<StudioDraft> = withContext(Dispatchers.IO) {
        val current = listDrafts().filterNot { it.id == draft.id }.toMutableList()
        current.add(0, draft)
        writeAll(current)
        current
    }

    suspend fun deleteDraft(id: String): List<StudioDraft> = withContext(Dispatchers.IO) {
        val next = listDrafts().filterNot { it.id == id }
        writeAll(next)
        next
    }

    private fun writeAll(drafts: List<StudioDraft>) {
        val arr = JSONArray()
        drafts.forEach { arr.put(serializeDraft(it)) }
        storeFile.writeText(arr.toString())
    }

    private fun serializeDraft(draft: StudioDraft): JSONObject = JSONObject().apply {
        put("id", draft.id)
        put("fileName", draft.fileName)
        put("updatedAtMillis", draft.updatedAtMillis)
        when (draft) {
            is StudioDraft.TextDraft -> {
                put("kind", "text")
                put("docTitle", draft.docTitle)
                val blocksArr = JSONArray()
                draft.blocks.forEach { b ->
                    val bObj = JSONObject().put("id", b.id)
                    when (b) {
                        is RichPdfBlock.TextBlock -> {
                            bObj.put("type", "text")
                            bObj.put("text", b.text)
                            bObj.put("fontFamily", b.fontFamily.name)
                            b.customFontName?.let { bObj.put("customFontName", it) }
                            bObj.put("fontSizeSp", b.fontSizeSp.toDouble())
                            bObj.put("isBold", b.isBold)
                            bObj.put("isItalic", b.isItalic)
                            bObj.put("isUnderline", b.isUnderline)
                            bObj.put("colorHex", b.colorHex)
                            bObj.put("align", b.align.name)
                            b.leadingIcon?.let { bObj.put("leadingIcon", it) }
                        }
                        is RichPdfBlock.ImageBlock -> {
                            bObj.put("type", "image")
                            bObj.put("imageUri", b.imageUri.toString())
                            bObj.put("caption", b.caption)
                            bObj.put("widthFraction", b.widthFraction.toDouble())
                            bObj.put("align", b.align.name)
                        }
                        is RichPdfBlock.DividerBlock -> {
                            bObj.put("type", "divider")
                            bObj.put("colorHex", b.colorHex)
                        }
                        is RichPdfBlock.PageBreakBlock -> {
                            bObj.put("type", "page_break")
                        }
                    }
                    blocksArr.put(bObj)
                }
                put("blocks", blocksArr)
                val sigArr = JSONArray()
                draft.signatures.forEach { s ->
                    sigArr.put(JSONObject().apply {
                        put("id", s.id)
                        put("pageIndex", s.pageIndex)
                        put("imagePath", s.imagePath)
                        put("xFraction", s.xFraction.toDouble())
                        put("yFraction", s.yFraction.toDouble())
                        put("widthFraction", s.widthFraction.toDouble())
                        put("aspectRatio", s.aspectRatio.toDouble())
                        put("kind", s.kind)
                    })
                }
                put("signatures", sigArr)
            }

            is StudioDraft.ImagesDraft -> {
                put("kind", "images")
                put("marginMode", draft.options.marginMode.name)
                draft.options.targetMaxMb?.let { put("targetMaxMb", it.toDouble()) }
                put("showPageNumbers", draft.options.showPageNumbers)
                val pagesArr = JSONArray()
                draft.pages.forEach { p ->
                    pagesArr.put(JSONObject().apply {
                        put("id", p.id)
                        put("imageUri", p.imageUri.toString())
                        put("originalBytes", p.originalBytes)
                        put("originalWidth", p.originalWidth)
                        put("originalHeight", p.originalHeight)
                        put("sizeMode", p.sizeMode.name)
                        put("customScalePercent", p.customScalePercent)
                        put("customWidthMm", p.customWidthMm)
                        put("customHeightMm", p.customHeightMm)
                        put("rotationDegrees", p.rotationDegrees)
                        put("resolutionPreset", p.resolutionPreset.name)
                        put("customMaxDimPx", p.customMaxDimPx)
                        put("jpegQuality", p.jpegQuality)
                        put("pageText", p.pageText)
                        put("textPlacement", p.textPlacement.name)
                        put("textFontFamily", p.textFontFamily.name)
                        put("textSizeSp", p.textSizeSp.toDouble())
                        put("textColorHex", p.textColorHex)
                        put("isBold", p.isBold)
                        put("isItalic", p.isItalic)
                        put("textAlign", p.textAlign.name)
                    })
                }
                put("pages", pagesArr)
            }
        }
    }

    private fun parseDraft(obj: JSONObject): StudioDraft? {
        val id = obj.optString("id")
        val fileName = obj.optString("fileName", "PDF Maker Studio")
        val updated = obj.optLong("updatedAtMillis", System.currentTimeMillis())
        return when (obj.optString("kind")) {
            "text" -> {
                val docTitle = obj.optString("docTitle")
                val arr = obj.optJSONArray("blocks") ?: JSONArray()
                val blocks = mutableListOf<RichPdfBlock>()
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    val bId = b.optString("id")
                    when (b.optString("type")) {
                        "text" -> blocks.add(
                            RichPdfBlock.TextBlock(
                                id = bId,
                                text = b.optString("text"),
                                fontFamily = enumValueOr(b.optString("fontFamily"), RichFontFamily.BENGALI_SANS),
                                customFontName = b.optString("customFontName").takeIf { it.isNotBlank() },
                                fontSizeSp = b.optDouble("fontSizeSp", 14.0).toFloat(),
                                isBold = b.optBoolean("isBold", false),
                                isItalic = b.optBoolean("isItalic", false),
                                isUnderline = b.optBoolean("isUnderline", false),
                                colorHex = b.optLong("colorHex", 0xFF1E293BL),
                                align = enumValueOr(b.optString("align"), RichTextAlign.LEFT),
                                leadingIcon = b.optString("leadingIcon").takeIf { it.isNotBlank() }
                            )
                        )
                        "image" -> blocks.add(
                            RichPdfBlock.ImageBlock(
                                id = bId,
                                imageUri = Uri.parse(b.optString("imageUri")),
                                caption = b.optString("caption"),
                                widthFraction = b.optDouble("widthFraction", 0.8).toFloat(),
                                align = enumValueOr(b.optString("align"), RichTextAlign.CENTER)
                            )
                        )
                        "divider" -> blocks.add(
                            RichPdfBlock.DividerBlock(
                                id = bId,
                                colorHex = b.optLong("colorHex", 0xFFCBD5E1L)
                            )
                        )
                        "page_break" -> blocks.add(
                            RichPdfBlock.PageBreakBlock(id = bId)
                        )
                    }
                }
                val sigArr = obj.optJSONArray("signatures") ?: JSONArray()
                val signatures = mutableListOf<DocumentSignature>()
                for (i in 0 until sigArr.length()) {
                    val s = sigArr.getJSONObject(i)
                    signatures.add(
                        DocumentSignature(
                            id = s.optString("id"),
                            pageIndex = s.optInt("pageIndex", 0),
                            imagePath = s.optString("imagePath"),
                            xFraction = s.optDouble("xFraction", 0.56).toFloat(),
                            yFraction = s.optDouble("yFraction", 0.72).toFloat(),
                            widthFraction = s.optDouble("widthFraction", 0.28).toFloat(),
                            aspectRatio = s.optDouble("aspectRatio", 0.42).toFloat(),
                            kind = s.optString("kind", "signature").ifBlank { "signature" }
                        )
                    )
                }
                StudioDraft.TextDraft(id, fileName, docTitle, blocks, signatures, updated)
            }

            "images" -> {
                val marginMode = enumValueOr(obj.optString("marginMode"), ImageMarginMode.STANDARD)
                val targetMb = if (obj.has("targetMaxMb")) obj.optDouble("targetMaxMb").toFloat() else null
                val showPageNums = obj.optBoolean("showPageNumbers", true)
                val arr = obj.optJSONArray("pages") ?: JSONArray()
                val pages = mutableListOf<ImagePageItem>()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    pages.add(
                        ImagePageItem(
                            id = p.optString("id"),
                            imageUri = Uri.parse(p.optString("imageUri")),
                            originalBytes = p.optLong("originalBytes", 0L),
                            originalWidth = p.optInt("originalWidth", 0),
                            originalHeight = p.optInt("originalHeight", 0),
                            sizeMode = enumValueOr(p.optString("sizeMode"), ImageSizeMode.AUTO_FIT),
                            customScalePercent = p.optInt("customScalePercent", 85),
                            customWidthMm = p.optInt("customWidthMm", 160),
                            customHeightMm = p.optInt("customHeightMm", 220),
                            rotationDegrees = p.optInt("rotationDegrees", 0),
                            resolutionPreset = enumValueOr(p.optString("resolutionPreset"), ImageResolutionPreset.MP_2),
                            customMaxDimPx = p.optInt("customMaxDimPx", 1600),
                            jpegQuality = p.optInt("jpegQuality", 80),
                            pageText = p.optString("pageText"),
                            textPlacement = enumValueOr(p.optString("textPlacement"), PageTextPlacement.BOTTOM),
                            textFontFamily = enumValueOr(p.optString("textFontFamily"), RichFontFamily.BENGALI_SANS),
                            textSizeSp = p.optDouble("textSizeSp", 13.0).toFloat(),
                            textColorHex = p.optLong("textColorHex", 0xFF0F172AL),
                            isBold = p.optBoolean("isBold", false),
                            isItalic = p.optBoolean("isItalic", false),
                            textAlign = enumValueOr(p.optString("textAlign"), RichTextAlign.CENTER)
                        )
                    )
                }
                StudioDraft.ImagesDraft(
                    id = id,
                    fileName = fileName,
                    pages = pages,
                    options = GlobalImagePdfOptions(marginMode, targetMb, showPageNums),
                    updatedAtMillis = updated
                )
            }

            else -> null
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOr(name: String, fallback: T): T {
        return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    }
}
