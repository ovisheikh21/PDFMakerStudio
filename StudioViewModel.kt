package com.pdfmakerstudio.app.ui.studio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdfmakerstudio.app.data.draft.StudioDraft
import com.pdfmakerstudio.app.data.draft.StudioDraftStore
import com.pdfmakerstudio.app.data.pdf.A4PdfGenerator
import com.pdfmakerstudio.app.data.pdf.DownloadedFileItem
import com.pdfmakerstudio.app.data.pdf.PdfDeviceSaver
import com.pdfmakerstudio.app.data.pdf.StudioDownloadedStore
import com.pdfmakerstudio.app.domain.model.AuthUser
import com.pdfmakerstudio.app.domain.model.DocumentSignature
import com.pdfmakerstudio.app.domain.model.GlobalImagePdfOptions
import com.pdfmakerstudio.app.domain.model.ImagePageItem
import com.pdfmakerstudio.app.domain.model.PdfFileItem
import com.pdfmakerstudio.app.domain.model.PdfSourceType
import com.pdfmakerstudio.app.domain.model.RichPdfBlock
import com.pdfmakerstudio.app.domain.repository.AuthRepository
import com.pdfmakerstudio.app.domain.repository.PdfCloudRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface FilesUiState {
    data object Loading : FilesUiState
    data class Error(val message: String) : FilesUiState
    data class Ready(val files: List<PdfFileItem>) : FilesUiState
}

data class StudioUiState(
    val user: AuthUser,
    val filesState: FilesUiState = FilesUiState.Loading,
    val drafts: List<StudioDraft> = emptyList(),
    val downloadedFiles: List<DownloadedFileItem> = emptyList(),
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val isCreatingOrUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val statusBannerText: String? = null,
    val openingFileId: String? = null
)

sealed interface StudioEvent {
    data class ShowMessage(val message: String) : StudioEvent
    data class OpenLocalPdf(val file: File) : StudioEvent
}

class StudioViewModel(
    private val user: AuthUser,
    private val authRepository: AuthRepository,
    private val pdfCloudRepository: PdfCloudRepository,
    val pdfGenerator: A4PdfGenerator,
    private val draftStore: StudioDraftStore,
    private val downloadedStore: StudioDownloadedStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudioUiState(user = user))
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StudioEvent>()
    val events: SharedFlow<StudioEvent> = _events.asSharedFlow()

    private var observeJob: Job? = null

    init {
        startObservingFiles()
        loadDrafts()
        refreshDownloadedFiles()
    }

    private fun loadDrafts() {
        viewModelScope.launch {
            val saved = draftStore.listDrafts()
            _uiState.update { it.copy(drafts = saved) }
        }
    }

    fun refreshDownloadedFiles() {
        viewModelScope.launch {
            val items = downloadedStore.listDownloadedFiles()
            _uiState.update { it.copy(downloadedFiles = items) }
        }
    }

    fun deleteDownloadedFile(id: String) {
        viewModelScope.launch {
            val next = downloadedStore.deleteDownloadedFile(id)
            _uiState.update { it.copy(downloadedFiles = next) }
            emitMessage("ফাইলটি ডাউনলোড তালিকা থেকে মুছে ফেলা হয়েছে।")
        }
    }

    fun openDownloadedFile(context: Context, item: DownloadedFileItem) {
        val file = File(item.localFilePath)
        if (!file.exists()) {
            emitMessage("ফাইলটি পাওয়া যায়নি।")
            refreshDownloadedFiles()
            return
        }
        val opened = PdfDeviceSaver.openFileWithChooser(
            context = context,
            file = file,
            mimeType = item.mimeType,
            chooserTitle = item.fileName
        )
        if (!opened) {
            emitMessage("এই ফাইলটি খোলার জন্য ফোনে কোনো অ্যাপ পাওয়া যায়নি।")
        }
    }

    fun shareDownloadedFile(context: Context, item: DownloadedFileItem) {
        val file = File(item.localFilePath)
        if (!file.exists()) {
            emitMessage("ফাইলটি পাওয়া যায়নি।")
            refreshDownloadedFiles()
            return
        }
        PdfDeviceSaver.shareFileWithChooser(
            context = context,
            file = file,
            mimeType = item.mimeType,
            displayName = item.fileName
        )
    }

    fun saveTextDraft(
        draftId: String?,
        fileName: String,
        docTitle: String,
        blocks: List<RichPdfBlock>,
        signatures: List<DocumentSignature> = emptyList(),
        silent: Boolean = false
    ) {
        viewModelScope.launch {
            val cleanName = fileName.trim().ifBlank { "PDF Maker Studio" }
            val draft = StudioDraft.TextDraft(
                id = draftId ?: "draft_${UUID.randomUUID().toString().take(12)}",
                fileName = cleanName,
                docTitle = docTitle,
                blocks = blocks,
                signatures = signatures,
                updatedAtMillis = System.currentTimeMillis()
            )
            val next = draftStore.saveDraft(draft)
            _uiState.update { it.copy(drafts = next) }
            if (!silent) {
                emitMessage("ড্রাফট সংরক্ষণ করা হয়েছে: \"$cleanName\"")
            }
        }
    }

    fun saveImagesDraft(
        draftId: String?,
        fileName: String,
        pages: List<ImagePageItem>,
        options: GlobalImagePdfOptions,
        silent: Boolean = false
    ) {
        viewModelScope.launch {
            val cleanName = fileName.trim().ifBlank { "PDF Maker Studio" }
            val draft = StudioDraft.ImagesDraft(
                id = draftId ?: "draft_${UUID.randomUUID().toString().take(12)}",
                fileName = cleanName,
                pages = pages,
                options = options,
                updatedAtMillis = System.currentTimeMillis()
            )
            val next = draftStore.saveDraft(draft)
            _uiState.update { it.copy(drafts = next) }
            if (!silent) {
                emitMessage("ছবির ড্রাফট সংরক্ষণ করা হয়েছে: \"$cleanName\"")
            }
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            val next = draftStore.deleteDraft(draftId)
            _uiState.update { it.copy(drafts = next) }
            emitMessage("ড্রাফট মুছে ফেলা হয়েছে।")
        }
    }

    fun startObservingFiles() {
        observeJob?.cancel()
        _uiState.update { it.copy(filesState = FilesUiState.Loading) }
        observeJob = viewModelScope.launch {
            pdfCloudRepository.observeUserPdfs(user.uid)
                .catch { err ->
                    _uiState.update {
                        it.copy(
                            filesState = FilesUiState.Error(
                                err.localizedMessage ?: "Failed to load cloud files."
                            ),
                            isRefreshing = false
                        )
                    }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(
                            filesState = FilesUiState.Ready(items),
                            isRefreshing = false
                        )
                    }
                }
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val dlItems = downloadedStore.listDownloadedFiles()
            pdfCloudRepository.fetchUserPdfsOnce(user.uid)
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            filesState = FilesUiState.Ready(items),
                            downloadedFiles = dlItems,
                            isRefreshing = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            downloadedFiles = dlItems,
                            isRefreshing = false
                        )
                    }
                    emitMessage(err.localizedMessage ?: "Refresh failed.")
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun createPdfFromImages(imageUris: List<Uri>, documentName: String = "PDF Maker Studio") {
        if (imageUris.isEmpty()) return
        val pages = imageUris.map { ImagePageItem(imageUri = it) }
        createPdfFromConfiguredImages(
            fileName = documentName,
            pages = pages,
            options = GlobalImagePdfOptions()
        )
    }

    fun createPdfFromConfiguredImages(
        fileName: String,
        pages: List<ImagePageItem>,
        options: GlobalImagePdfOptions,
        sourceDraftId: String? = null
    ) {
        if (pages.isEmpty()) return
        val cleanName = fileName.trim().ifBlank { "PDF Maker Studio" }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreatingOrUploading = true,
                    uploadProgress = 0f,
                    statusBannerText = "Generating custom A4 PDF (${pages.size} pages)..."
                )
            }
            val generated = pdfGenerator.createPdfFromConfiguredImages(
                pages = pages,
                globalOptions = options,
                fileNamePrefix = cleanName
            )
            generated.onSuccess { pdf ->
                uploadGeneratedPdf(
                    file = pdf.file,
                    name = cleanName,
                    sourceType = PdfSourceType.IMAGES,
                    pageCount = pdf.pageCount,
                    clearDraftId = sourceDraftId
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isCreatingOrUploading = false, statusBannerText = null)
                }
                emitMessage(err.localizedMessage ?: "Failed to generate PDF from images.")
            }
        }
    }

    fun createPdfFromText(title: String, body: String, fileName: String = "PDF Maker Studio") {
        if (body.trim().isEmpty()) {
            emitMessage("Please enter text content before creating the PDF.")
            return
        }
        createPdfFromRichBlocks(
            fileName = fileName,
            title = title,
            blocks = listOf(RichPdfBlock.TextBlock(text = body))
        )
    }

    fun createPdfFromRichBlocks(
        fileName: String = "PDF Maker Studio",
        title: String,
        blocks: List<RichPdfBlock>,
        sourceDraftId: String? = null,
        signatures: List<DocumentSignature> = emptyList()
    ) {
        val cleanFileName = fileName.trim().ifBlank { "PDF Maker Studio" }
        val cleanTitle = title.trim().ifBlank { cleanFileName }
        val hasContent = title.trim().isNotEmpty() || signatures.isNotEmpty() || blocks.any { block ->
            when (block) {
                is RichPdfBlock.TextBlock -> block.text.isNotBlank()
                is RichPdfBlock.ImageBlock -> true
                is RichPdfBlock.DividerBlock -> true
                is RichPdfBlock.PageBreakBlock -> false
            }
        }
        if (!hasContent) {
            emitMessage("Please enter text or add a photo before creating the PDF.")
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreatingOrUploading = true,
                    uploadProgress = 0f,
                    statusBannerText = "Rendering Rich A4 PDF (Unicode / Bengali)..."
                )
            }
            val generated = pdfGenerator.createPdfFromRichBlocks(
                title = cleanTitle,
                blocks = blocks,
                signatures = signatures
            )
            generated.onSuccess { pdf ->
                uploadGeneratedPdf(
                    file = pdf.file,
                    name = cleanFileName,
                    sourceType = PdfSourceType.TEXT,
                    pageCount = pdf.pageCount,
                    clearDraftId = sourceDraftId
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isCreatingOrUploading = false, statusBannerText = null)
                }
                emitMessage(err.localizedMessage ?: "Failed to generate rich text PDF.")
            }
        }
    }

    private suspend fun uploadGeneratedPdf(
        file: File,
        name: String,
        sourceType: PdfSourceType,
        pageCount: Int,
        clearDraftId: String? = null
    ) {
        val result = pdfCloudRepository.uploadPdf(
            uid = user.uid,
            localFile = file,
            displayName = name,
            sourceType = sourceType,
            pageCount = pageCount,
            onProgress = { progress ->
                _uiState.update {
                    it.copy(
                        uploadProgress = progress,
                        statusBannerText = "Uploading to Cloud Storage (${(progress * 100).toInt()}%)"
                    )
                }
            }
        )
        runCatching { file.delete() }
        _uiState.update {
            it.copy(isCreatingOrUploading = false, uploadProgress = 0f, statusBannerText = null)
        }
        result.onSuccess { uploaded ->
            emitMessage("Saved \"${uploaded.name}\" to cloud.")
        }.onFailure { err ->
            emitMessage(err.localizedMessage ?: "Cloud upload failed.")
        }
    }

    fun renamePdf(item: PdfFileItem, newName: String) {
        viewModelScope.launch {
            pdfCloudRepository.renamePdf(user.uid, item.id, newName)
                .onSuccess { emitMessage("Renamed successfully.") }
                .onFailure { err -> emitMessage(err.localizedMessage ?: "Rename failed.") }
        }
    }

    fun deletePdf(item: PdfFileItem) {
        viewModelScope.launch {
            pdfCloudRepository.deletePdf(user.uid, item.id, item.storagePath)
                .onSuccess { emitMessage("Deleted \"${item.name}\" from Storage and Firestore.") }
                .onFailure { err -> emitMessage(err.localizedMessage ?: "Delete failed.") }
        }
    }

    fun uploadReadyPdfToCloud(
        localFile: File,
        fileName: String,
        sourceType: PdfSourceType,
        pageCount: Int,
        sourceDraftId: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreatingOrUploading = true,
                    uploadProgress = 0f,
                    statusBannerText = "Backing up \"$fileName\" to Cloud Vault..."
                )
            }
            uploadGeneratedPdf(
                file = localFile,
                name = fileName.trim().ifBlank { "PDF Maker Studio" },
                sourceType = sourceType,
                pageCount = pageCount,
                clearDraftId = sourceDraftId
            )
        }
    }

    fun downloadPdfToPhone(context: Context, item: PdfFileItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(openingFileId = item.id) }
            val result = pdfCloudRepository.downloadPdfToCache(context, item)
            if (result.isSuccess) {
                val cached = result.getOrThrow()
                val saved = PdfDeviceSaver.savePdfToPhoneDownloads(context, cached, item.name)
                _uiState.update { it.copy(openingFileId = null) }
                saved.onSuccess { info ->
                    refreshDownloadedFiles()
                    emitMessage("সরাসরি ফোনে ডাউনলোড হয়েছে: ${info.savedFolderLabel}${info.displayFileName}")
                }.onFailure { err ->
                    emitMessage(err.localizedMessage ?: "Download failed.")
                }
            } else {
                _uiState.update { it.copy(openingFileId = null) }
                emitMessage(result.exceptionOrNull()?.localizedMessage ?: "Unable to download PDF.")
            }
        }
    }

    fun openPdf(context: Context, item: PdfFileItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(openingFileId = item.id) }
            val result = pdfCloudRepository.downloadPdfToCache(context, item)
            _uiState.update { it.copy(openingFileId = null) }
            result.onSuccess { file ->
                _events.emit(StudioEvent.OpenLocalPdf(file))
            }.onFailure { err ->
                emitMessage(err.localizedMessage ?: "Unable to open PDF.")
            }
        }
    }

    fun logout() {
        authRepository.signOut()
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(StudioEvent.ShowMessage(message))
        }
    }

    class Factory(
        private val user: AuthUser,
        private val authRepository: AuthRepository,
        private val pdfCloudRepository: PdfCloudRepository,
        private val pdfGenerator: A4PdfGenerator,
        private val draftStore: StudioDraftStore,
        private val downloadedStore: StudioDownloadedStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(
                user = user,
                authRepository = authRepository,
                pdfCloudRepository = pdfCloudRepository,
                pdfGenerator = pdfGenerator,
                draftStore = draftStore,
                downloadedStore = downloadedStore
            ) as T
        }
    }
}
