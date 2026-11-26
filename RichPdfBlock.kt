package com.pdfmakerstudio.app.domain.model

import android.net.Uri
import java.util.UUID

enum class RichFontFamily(
    val displayName: String,
    val isBengaliOptimized: Boolean,
    val wordFontName: String
) {
    BENGALI_SANS("Noto Sans Bengali", true, "Nirmala UI"),
    BENGALI_SERIF("Bangla Serif", true, "Vrinda"),
    ENGLISH_MODERN("Modern Sans", false, "Calibri"),
    ENGLISH_SERIF("Editorial Serif", false, "Georgia"),
    MONOSPACE("Monospace", false, "Consolas"),
    NIRMALA_UI("Nirmala UI (Word)", true, "Nirmala UI"),
    VRINDA("Vrinda (Bangla Word)", true, "Vrinda"),
    KALPURUSH("Kalpurush (Bangla)", true, "Kalpurush"),
    CALIBRI("Calibri (Word)", false, "Calibri"),
    TIMES_NEW_ROMAN("Times New Roman", false, "Times New Roman"),
    ARIAL("Arial", false, "Arial"),
    GEORGIA("Georgia", false, "Georgia")
}

enum class RichTextAlign {
    LEFT,
    CENTER,
    RIGHT
}

sealed interface RichPdfBlock {
    val id: String

    data class TextBlock(
        override val id: String = UUID.randomUUID().toString(),
        val text: String = "",
        val fontFamily: RichFontFamily = RichFontFamily.BENGALI_SANS,
        val customFontName: String? = null,
        val fontSizeSp: Float = 14f,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val colorHex: Long = 0xFF1E293B,
        val align: RichTextAlign = RichTextAlign.LEFT,
        val leadingIcon: String? = null
    ) : RichPdfBlock

    data class ImageBlock(
        override val id: String = UUID.randomUUID().toString(),
        val imageUri: Uri,
        val caption: String = "",
        val widthFraction: Float = 0.85f,
        val align: RichTextAlign = RichTextAlign.CENTER
    ) : RichPdfBlock

    data class DividerBlock(
        override val id: String = UUID.randomUUID().toString(),
        val colorHex: Long = 0xFFCBD5E1
    ) : RichPdfBlock

    data class PageBreakBlock(
        override val id: String = UUID.randomUUID().toString()
    ) : RichPdfBlock
}

data class DocumentSignature(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int = 0,
    val imagePath: String,
    val xFraction: Float = 0.56f,
    val yFraction: Float = 0.72f,
    val widthFraction: Float = 0.28f,
    val aspectRatio: Float = 0.42f,
    val kind: String = "signature"
)
