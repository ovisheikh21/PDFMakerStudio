package com.pdfmakerstudio.app.domain.model

import android.net.Uri
import java.util.UUID

enum class ImageSizeMode(val labelEn: String, val labelBn: String) {
    AUTO_FIT("Auto Fit", "অটো ফিট"),
    FILL_PAGE("Fill A4", "ফুল A4"),
    CUSTOM_SCALE("Custom %", "কাস্টম %"),
    CUSTOM_MM("Custom mm", "কাস্টম mm")
}

enum class ImageResolutionPreset(val labelEn: String, val labelBn: String, val targetMp: Float?) {
    ORIGINAL("Original MP", "অরিজিনাল", null),
    MP_8("8 MP", "8 MP (হাই)", 8f),
    MP_5("5 MP", "5 MP", 5f),
    MP_2("2 MP (HD)", "2 MP (ব্যালেন্সড)", 2f),
    MP_1("1 MP (Light)", "1 MP (কম এমবি)", 1f),
    MP_0_5("0.5 MP (Mini)", "0.5 MP (সর্বনিম্ন)", 0.5f),
    CUSTOM_PX("Custom px", "কাস্টম px", null)
}

enum class PageTextPlacement(val labelEn: String, val labelBn: String) {
    TOP("Above Photo", "ছবির উপরে"),
    BOTTOM("Below Photo", "ছবির নিচে")
}

enum class ImageMarginMode(val marginPt: Float, val labelEn: String, val labelBn: String) {
    STANDARD(36f, "Normal", "স্বাভাবিক"),
    NARROW(18f, "Narrow", "চিকন"),
    NONE(0f, "None", "নাই")
}

data class ImagePageItem(
    val id: String = UUID.randomUUID().toString(),
    val imageUri: Uri,
    val originalBytes: Long = 0L,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val sizeMode: ImageSizeMode = ImageSizeMode.AUTO_FIT,
    val customScalePercent: Int = 85,
    val customWidthMm: Int = 160,
    val customHeightMm: Int = 220,
    val rotationDegrees: Int = 0,
    val resolutionPreset: ImageResolutionPreset = ImageResolutionPreset.MP_2,
    val customMaxDimPx: Int = 1600,
    val jpegQuality: Int = 80,
    val pageText: String = "",
    val textPlacement: PageTextPlacement = PageTextPlacement.BOTTOM,
    val textFontFamily: RichFontFamily = RichFontFamily.BENGALI_SANS,
    val textSizeSp: Float = 13f,
    val textColorHex: Long = 0xFF0F172AL,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val textAlign: RichTextAlign = RichTextAlign.CENTER
)

data class GlobalImagePdfOptions(
    val marginMode: ImageMarginMode = ImageMarginMode.STANDARD,
    val targetMaxMb: Float? = null,
    val showPageNumbers: Boolean = true
)
