package com.pdfmakerstudio.app.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.pdfmakerstudio.app.domain.model.PdfFileItem
import com.pdfmakerstudio.app.domain.model.PdfSourceType
import com.pdfmakerstudio.app.domain.repository.PdfCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FirebasePdfCloudRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : PdfCloudRepository {

    override fun observeUserPdfs(uid: String): Flow<List<PdfFileItem>> = callbackFlow {
        val query = firestore.collection("users")
            .document(uid)
            .collection("pdfs")
            .whereEqualTo("ownerId", uid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents
                ?.mapNotNull { it.toPdfFileItem() }
                ?.sortedByDescending { it.createdAtMillis }
                ?: emptyList()
            trySend(items)
        }

        awaitClose { registration.remove() }
    }

    override suspend fun fetchUserPdfsOnce(uid: String): Result<List<PdfFileItem>> =
        runCatching {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("pdfs")
                .whereEqualTo("ownerId", uid)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { it.toPdfFileItem() }
                .sortedByDescending { it.createdAtMillis }
        }

    override suspend fun uploadPdf(
        uid: String,
        localFile: File,
        displayName: String,
        sourceType: PdfSourceType,
        pageCount: Int,
        onProgress: (Float) -> Unit
    ): Result<PdfFileItem> = runCatching {
        val pdfId = "pdf_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val sanitizedDisplayName = ensurePdfExtension(displayName.trim().ifBlank { "PDF Maker Studio" }).take(140)
        val storageFileName = "${pdfId}.pdf"
        val storagePath = "users/$uid/pdfs/$storageFileName"
        val storageRef = storage.reference.child(storagePath)

        val metadata = StorageMetadata.Builder()
            .setContentType("application/pdf")
            .build()

        val uploadTask = storageRef.putFile(Uri.fromFile(localFile), metadata)
        uploadTask.addOnProgressListener { snapshot ->
            val total = snapshot.totalByteCount
            if (total > 0L) {
                onProgress((snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
        }
        uploadTask.await()
        onProgress(1f)

        val downloadUrl = storageRef.downloadUrl.await().toString()
        val sizeBytes = localFile.length().coerceAtLeast(1L)

        val docData = hashMapOf<String, Any>(
            "id" to pdfId,
            "ownerId" to uid,
            "name" to sanitizedDisplayName,
            "url" to downloadUrl,
            "storagePath" to storagePath,
            "sourceType" to sourceType.value,
            "pageCount" to pageCount.coerceAtLeast(1),
            "sizeBytes" to sizeBytes,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val docRef = firestore.collection("users")
            .document(uid)
            .collection("pdfs")
            .document(pdfId)

        docRef.set(docData).await()

        val now = System.currentTimeMillis()
        PdfFileItem(
            id = pdfId,
            ownerId = uid,
            name = sanitizedDisplayName,
            url = downloadUrl,
            storagePath = storagePath,
            sourceType = sourceType,
            pageCount = pageCount,
            sizeBytes = sizeBytes,
            createdAtMillis = now,
            updatedAtMillis = now
        )
    }

    override suspend fun renamePdf(uid: String, pdfId: String, newName: String): Result<Unit> =
        runCatching {
            val sanitized = ensurePdfExtension(newName.trim()).take(140)
            require(sanitized.isNotBlank()) { "File name cannot be empty" }

            firestore.collection("users")
                .document(uid)
                .collection("pdfs")
                .document(pdfId)
                .update(
                    mapOf(
                        "name" to sanitized,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        }

    override suspend fun deletePdf(uid: String, pdfId: String, storagePath: String): Result<Unit> =
        runCatching {
            try {
                storage.reference.child(storagePath).delete().await()
            } catch (e: StorageException) {
                if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                    throw e
                }
            }

            firestore.collection("users")
                .document(uid)
                .collection("pdfs")
                .document(pdfId)
                .delete()
                .await()
        }

    override suspend fun downloadPdfToCache(context: Context, item: PdfFileItem): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val downloadsDir = File(context.cacheDir, "downloads").apply { mkdirs() }
                val safeName = item.name.replace(Regex("[^a-zA-Z0-9._\\-\\u0980-\\u09FF ]"), "_")
                val targetFile = File(downloadsDir, "${item.id}_$safeName")
                storage.reference.child(item.storagePath).getFile(targetFile).await()
                targetFile
            }
        }

    private fun DocumentSnapshot.toPdfFileItem(): PdfFileItem? {
        val id = getString("id") ?: id
        val ownerId = getString("ownerId") ?: return null
        val name = getString("name") ?: return null
        val url = getString("url") ?: return null
        val storagePath = getString("storagePath") ?: return null
        val sourceType = PdfSourceType.fromValue(getString("sourceType"))
        val pageCount = (getLong("pageCount") ?: 1L).toInt()
        val sizeBytes = getLong("sizeBytes") ?: 0L
        val createdAt = (get("createdAt") as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
        val updatedAt = (get("updatedAt") as? Timestamp)?.toDate()?.time ?: createdAt

        return PdfFileItem(
            id = id,
            ownerId = ownerId,
            name = name,
            url = url,
            storagePath = storagePath,
            sourceType = sourceType,
            pageCount = pageCount,
            sizeBytes = sizeBytes,
            createdAtMillis = createdAt,
            updatedAtMillis = updatedAt
        )
    }

    private fun ensurePdfExtension(name: String): String {
        return if (name.lowercase().endsWith(".pdf")) name else "$name.pdf"
    }
}
