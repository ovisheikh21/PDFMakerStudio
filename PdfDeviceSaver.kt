package com.pdfmakerstudio.app.data.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SavedDevicePdf(
    val displayFileName: String,
    val savedFolderLabel: String,
    val contentUri: Uri,
    val localCopyFile: File
)

object PdfDeviceSaver {

    private const val DOWNLOAD_SUBFOLDER = "PDF Maker Studio"

    fun ensureFileExtension(rawName: String, ext: String): String {
        val cleanExt = ext.trim().removePrefix(".").lowercase()
        val cleaned = rawName
            .trim()
            .replace(Regex("""[\\/:*?"<>|]+"""), "_")
            .take(120)
            .ifBlank { "PDF Maker Studio" }
        val withoutKnownExt = cleaned.replace(Regex("""\.(pdf|docx|doc|rtf|txt)$""", RegexOption.IGNORE_CASE), "")
        val base = withoutKnownExt.ifBlank { "PDF Maker Studio" }
        return "$base.$cleanExt"
    }

    fun ensurePdfFileName(rawName: String): String = ensureFileExtension(rawName, "pdf")

    suspend fun savePdfToPhoneDownloads(
        context: Context,
        sourceFile: File,
        requestedName: String
    ): Result<SavedDevicePdf> = saveDocumentToPhoneDownloads(
        context = context,
        sourceFile = sourceFile,
        requestedName = requestedName,
        mimeType = "application/pdf",
        extension = "pdf"
    )

    suspend fun saveDocumentToPhoneDownloads(
        context: Context,
        sourceFile: File,
        requestedName: String,
        mimeType: String,
        extension: String
    ): Result<SavedDevicePdf> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanExt = extension.trim().removePrefix(".").lowercase()
            val finalFileName = ensureFileExtension(requestedName, cleanExt)
            val baseName = finalFileName.removeSuffix(".$cleanExt")
            val stamp = System.currentTimeMillis().toString().takeLast(4)

            // Always keep a readable FileProvider copy under cache/downloads for instant Open & Share
            val previewDir = File(context.cacheDir, "downloads").apply { mkdirs() }
            val localPreviewCopy = File(previewDir, "${baseName}_$stamp.$cleanExt")
            sourceFile.inputStream().use { input ->
                localPreviewCopy.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_SUBFOLDER"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create file in Downloads")

                try {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: error("Unable to open Downloads output stream")

                    val publish = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, publish, null, null)

                    runCatching {
                        StudioDownloadedStore(context).recordDownloadedFileSync(
                            sourceFile = localPreviewCopy,
                            displayFileName = finalFileName,
                            mimeType = mimeType,
                            extension = cleanExt
                        )
                    }

                    SavedDevicePdf(
                        displayFileName = finalFileName,
                        savedFolderLabel = "Downloads/$DOWNLOAD_SUBFOLDER/",
                        contentUri = itemUri,
                        localCopyFile = localPreviewCopy
                    )
                } catch (err: Throwable) {
                    runCatching { resolver.delete(itemUri, null, null) }
                    throw err
                }
            } else {
                @Suppress("DEPRECATION")
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(publicDownloads, DOWNLOAD_SUBFOLDER)
                val writtenFile = runCatching {
                    targetDir.mkdirs()
                    val dest = uniqueFile(targetDir, finalFileName, cleanExt)
                    sourceFile.inputStream().use { input ->
                        dest.outputStream().use { out -> input.copyTo(out) }
                    }
                    dest
                }.getOrElse {
                    val extDir = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                        DOWNLOAD_SUBFOLDER
                    ).apply { mkdirs() }
                    val dest = uniqueFile(extDir, finalFileName, cleanExt)
                    sourceFile.inputStream().use { input ->
                        dest.outputStream().use { out -> input.copyTo(out) }
                    }
                    dest
                }
                val providerUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localPreviewCopy
                )
                runCatching {
                    StudioDownloadedStore(context).recordDownloadedFileSync(
                        sourceFile = localPreviewCopy,
                        displayFileName = writtenFile.name,
                        mimeType = mimeType,
                        extension = cleanExt
                    )
                }
                SavedDevicePdf(
                    displayFileName = writtenFile.name,
                    savedFolderLabel = "Downloads/$DOWNLOAD_SUBFOLDER/",
                    contentUri = providerUri,
                    localCopyFile = localPreviewCopy
                )
            }
        }
    }

    private fun uniqueFile(dir: File, fileName: String, ext: String = "pdf"): File {
        val candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val stem = fileName.removeSuffix(".$ext")
        return File(dir, "${stem}_${System.currentTimeMillis().toString().takeLast(4)}.$ext")
    }

    fun openPdfFile(context: Context, file: File): Boolean =
        openFileWithChooser(context, file, "application/pdf", "Open PDF")

    fun sharePdfFile(context: Context, file: File, displayName: String): Boolean =
        shareFileWithChooser(context, file, "application/pdf", displayName)

    fun openFileWithChooser(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String = "Open File"
    ): Boolean {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            true
        }.getOrDefault(false)
    }

    fun shareFileWithChooser(
        context: Context,
        file: File,
        mimeType: String,
        displayName: String
    ): Boolean {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, displayName))
            true
        }.getOrDefault(false)
    }
}
