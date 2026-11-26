package com.pdfmakerstudio.app.domain.model

enum class PdfSourceType(val value: String) {
    IMAGES("images"),
    TEXT("text");

    companion object {
        fun fromValue(raw: String?): PdfSourceType {
            return entries.firstOrNull { it.value == raw } ?: TEXT
        }
    }
}

data class PdfFileItem(
    val id: String,
    val ownerId: String,
    val name: String,
    val url: String,
    val storagePath: String,
    val sourceType: PdfSourceType,
    val pageCount: Int,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
