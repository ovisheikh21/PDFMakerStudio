package com.pdfmakerstudio.app.domain.repository

import android.content.Context
import com.pdfmakerstudio.app.domain.model.PdfFileItem
import com.pdfmakerstudio.app.domain.model.PdfSourceType
import kotlinx.coroutines.flow.Flow
import java.io.File

interface PdfCloudRepository {
    fun observeUserPdfs(uid: String): Flow<List<PdfFileItem>>
    suspend fun fetchUserPdfsOnce(uid: String): Result<List<PdfFileItem>>
    suspend fun uploadPdf(
        uid: String,
        localFile: File,
        displayName: String,
        sourceType: PdfSourceType,
        pageCount: Int,
        onProgress: (Float) -> Unit
    ): Result<PdfFileItem>

    suspend fun renamePdf(uid: String, pdfId: String, newName: String): Result<Unit>
    suspend fun deletePdf(uid: String, pdfId: String, storagePath: String): Result<Unit>
    suspend fun downloadPdfToCache(context: Context, item: PdfFileItem): Result<File>
}
