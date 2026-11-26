package com.pdfmakerstudio.app.data.pdf

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class DownloadedFileItem(
    val id: String,
    val fileName: String,
    val format: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localFilePath: String,
    val downloadedAtMillis: Long
)

class StudioDownloadedStore(private val context: Context) {

    private val storeFile: File
        get() = File(context.filesDir, "studio_downloaded_files_v1.json")

    private val filesDir: File
        get() = File(context.filesDir, "downloaded_files").apply { mkdirs() }

    fun listDownloadedFilesSync(): List<DownloadedFileItem> {
        return runCatching {
            if (!storeFile.exists()) return emptyList()
            val arr = JSONArray(storeFile.readText())
            val list = mutableListOf<DownloadedFileItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val path = obj.optString("localFilePath")
                if (path.isBlank() || !File(path).exists()) continue
                list.add(
                    DownloadedFileItem(
                        id = obj.optString("id"),
                        fileName = obj.optString("fileName", "PDF Maker Studio.pdf"),
                        format = obj.optString("format", "pdf"),
                        mimeType = obj.optString("mimeType", "application/pdf"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        localFilePath = path,
                        downloadedAtMillis = obj.optLong("downloadedAtMillis", System.currentTimeMillis())
                    )
                )
            }
            list.sortedByDescending { it.downloadedAtMillis }
        }.getOrDefault(emptyList())
    }

    suspend fun listDownloadedFiles(): List<DownloadedFileItem> = withContext(Dispatchers.IO) {
        listDownloadedFilesSync()
    }

    fun recordDownloadedFileSync(
        sourceFile: File,
        displayFileName: String,
        mimeType: String,
        extension: String
    ): DownloadedFileItem {
        val cleanExt = extension.trim().removePrefix(".").lowercase().ifBlank { "pdf" }
        val id = "dl_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val storedFile = File(filesDir, "$id.$cleanExt")
        sourceFile.inputStream().use { input ->
            storedFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val item = DownloadedFileItem(
            id = id,
            fileName = displayFileName,
            format = cleanExt,
            mimeType = mimeType,
            sizeBytes = storedFile.length(),
            localFilePath = storedFile.absolutePath,
            downloadedAtMillis = System.currentTimeMillis()
        )
        val current = listDownloadedFilesSync().toMutableList()
        current.add(0, item)
        writeAllSync(current.take(200))
        return item
    }

    suspend fun deleteDownloadedFile(id: String): List<DownloadedFileItem> = withContext(Dispatchers.IO) {
        val current = listDownloadedFilesSync()
        val target = current.firstOrNull { it.id == id }
        if (target != null) {
            runCatching { File(target.localFilePath).delete() }
        }
        val next = current.filterNot { it.id == id }
        writeAllSync(next)
        next
    }

    private fun writeAllSync(items: List<DownloadedFileItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("fileName", item.fileName)
                    put("format", item.format)
                    put("mimeType", item.mimeType)
                    put("sizeBytes", item.sizeBytes)
                    put("localFilePath", item.localFilePath)
                    put("downloadedAtMillis", item.downloadedAtMillis)
                }
            )
        }
        storeFile.writeText(arr.toString())
    }
}
