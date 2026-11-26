package com.pdfmakerstudio.app.ui.studio

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfmakerstudio.app.R
import com.pdfmakerstudio.app.data.draft.StudioDraft
import com.pdfmakerstudio.app.data.pdf.DownloadedFileItem
import com.pdfmakerstudio.app.data.templates.DocumentTemplate
import com.pdfmakerstudio.app.data.templates.DocumentTemplates
import com.pdfmakerstudio.app.domain.model.DocumentSignature
import com.pdfmakerstudio.app.domain.model.GlobalImagePdfOptions
import com.pdfmakerstudio.app.domain.model.ImagePageItem
import com.pdfmakerstudio.app.domain.model.PdfFileItem
import com.pdfmakerstudio.app.domain.model.PdfSourceType
import com.pdfmakerstudio.app.domain.model.RichPdfBlock
import com.pdfmakerstudio.app.ui.editor.DocumentScannerScreen
import com.pdfmakerstudio.app.ui.editor.ImagesToPdfScreen
import com.pdfmakerstudio.app.ui.editor.TextEditorScreen
import com.pdfmakerstudio.app.ui.strings.AppLanguage
import com.pdfmakerstudio.app.ui.strings.stringsFor
import com.pdfmakerstudio.app.ui.theme.DangerRed
import com.pdfmakerstudio.app.ui.theme.OutlineHairline
import com.pdfmakerstudio.app.ui.theme.StudioAmber
import com.pdfmakerstudio.app.ui.theme.StudioCobalt
import com.pdfmakerstudio.app.ui.theme.StudioCrimson
import com.pdfmakerstudio.app.ui.theme.StudioCrimsonDark
import com.pdfmakerstudio.app.ui.theme.StudioEmerald
import java.io.File
import java.util.Date
import java.util.UUID

private enum class StudioNavTab {
    HOME,
    FILES
}

private enum class DownloadFormatFilter {
    ALL,
    PDF,
    WORD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    language: AppLanguage,
    onToggleLanguage: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = stringsFor(language)
    val context = LocalContext.current
    val isOnline = rememberIsOnlineState(context)

    var activeTab by rememberSaveable { mutableStateOf(StudioNavTab.HOME) }
    var downloadFormatFilter by rememberSaveable { mutableStateOf(DownloadFormatFilter.ALL) }
    var isGridView by rememberSaveable { mutableStateOf(true) }

    val filteredDownloadedFiles = remember(uiState.downloadedFiles, uiState.searchQuery, downloadFormatFilter) {
        val q = uiState.searchQuery.trim()
        uiState.downloadedFiles.filter { dl ->
            val matchesSearch = q.isBlank() || dl.fileName.contains(q, ignoreCase = true)
            val matchesFormat = when (downloadFormatFilter) {
                DownloadFormatFilter.ALL -> true
                DownloadFormatFilter.PDF -> dl.format.equals("pdf", ignoreCase = true)
                DownloadFormatFilter.WORD -> !dl.format.equals("pdf", ignoreCase = true)
            }
            matchesSearch && matchesFormat
        }
    }

    var showScannerScreen by rememberSaveable { mutableStateOf(false) }
    var activeScannerDraftId by rememberSaveable { mutableStateOf<String?>(null) }

    var showImagesStudio by rememberSaveable { mutableStateOf(false) }
    var imagesInitialFileName by rememberSaveable { mutableStateOf("PDF Maker Studio") }
    var imagesInitialPages by remember { mutableStateOf<List<ImagePageItem>>(emptyList()) }
    var imagesInitialOptions by remember { mutableStateOf(GlobalImagePdfOptions()) }
    var activeImagesDraftId by rememberSaveable { mutableStateOf<String?>(null) }

    var showRichEditor by rememberSaveable { mutableStateOf(false) }
    var editorInitialFileName by rememberSaveable { mutableStateOf("PDF Maker Studio") }
    var editorInitialTitle by rememberSaveable { mutableStateOf("") }
    var editorInitialBlocks by remember { mutableStateOf<List<RichPdfBlock>>(emptyList()) }
    var editorInitialSignatures by remember { mutableStateOf<List<DocumentSignature>>(emptyList()) }
    var activeTextDraftId by rememberSaveable { mutableStateOf<String?>(null) }

    var templatePickerKind by remember { mutableStateOf<String?>(null) }
    var showCreatePopupSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var activeMenuDialog by remember { mutableStateOf<String?>(null) }

    var renameTarget by remember { mutableStateOf<PdfFileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<PdfFileItem?>(null) }

    fun newDraftId(): String = "draft_${UUID.randomUUID().toString().take(12)}"

    fun openScannerNew() {
        activeScannerDraftId = newDraftId()
        showScannerScreen = true
    }

    fun openTextEditorNew(
        title: String = "",
        blocks: List<RichPdfBlock> = listOf(RichPdfBlock.TextBlock()),
        fileName: String = "PDF Maker Studio",
        draftId: String? = null,
        signatures: List<DocumentSignature> = emptyList()
    ) {
        editorInitialFileName = fileName.ifBlank { "PDF Maker Studio" }
        editorInitialTitle = title
        editorInitialBlocks = blocks
        editorInitialSignatures = signatures
        activeTextDraftId = draftId ?: newDraftId()
        showRichEditor = true
    }

    fun openImagesStudioNew(
        fileName: String = "PDF Maker Studio",
        pages: List<ImagePageItem> = emptyList(),
        options: GlobalImagePdfOptions = GlobalImagePdfOptions(),
        draftId: String? = null
    ) {
        imagesInitialFileName = fileName.ifBlank { "PDF Maker Studio" }
        imagesInitialPages = pages
        imagesInitialOptions = options
        activeImagesDraftId = draftId ?: newDraftId()
        showImagesStudio = true
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StudioEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is StudioEvent.OpenLocalPdf -> openPdfIntent(context, event.file) { msg ->
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    if (showScannerScreen) {
        val curScanDraftId = activeScannerDraftId ?: newDraftId().also { activeScannerDraftId = it }
        DocumentScannerScreen(
            language = language,
            pdfGenerator = viewModel.pdfGenerator,
            isCloudBusy = uiState.isCreatingOrUploading,
            onBack = {
                showScannerScreen = false
                viewModel.refreshDownloadedFiles()
            },
            onSaveDraft = { fileName, pages, options ->
                showScannerScreen = false
                viewModel.saveImagesDraft(curScanDraftId, fileName, pages, options)
                viewModel.refreshDownloadedFiles()
            },
            onAutoSaveDraft = { fileName, pages, options ->
                viewModel.saveImagesDraft(curScanDraftId, fileName, pages, options, silent = true)
            },
            onUploadPdfToCloud = { file, fileName, pageCount ->
                viewModel.uploadReadyPdfToCloud(file, fileName, PdfSourceType.IMAGES, pageCount, curScanDraftId)
            }
        )
        return
    }

    if (showImagesStudio) {
        val curImgDraftId = activeImagesDraftId ?: newDraftId().also { activeImagesDraftId = it }
        ImagesToPdfScreen(
            language = language,
            pdfGenerator = viewModel.pdfGenerator,
            initialFileName = imagesInitialFileName,
            initialPages = imagesInitialPages,
            initialOptions = imagesInitialOptions,
            isBusy = uiState.isCreatingOrUploading,
            onBack = {
                showImagesStudio = false
                viewModel.refreshDownloadedFiles()
            },
            onSaveDraft = { fileName, pages, options ->
                showImagesStudio = false
                viewModel.saveImagesDraft(curImgDraftId, fileName, pages, options)
                viewModel.refreshDownloadedFiles()
            },
            onAutoSaveDraft = { fileName, pages, options ->
                viewModel.saveImagesDraft(curImgDraftId, fileName, pages, options, silent = true)
            },
            onUploadPdfToCloud = { file, fileName, pageCount ->
                viewModel.uploadReadyPdfToCloud(file, fileName, PdfSourceType.IMAGES, pageCount, curImgDraftId)
            }
        )
        return
    }

    if (showRichEditor) {
        val curTextDraftId = activeTextDraftId ?: newDraftId().also { activeTextDraftId = it }
        TextEditorScreen(
            language = language,
            pdfGenerator = viewModel.pdfGenerator,
            initialFileName = editorInitialFileName,
            initialTitle = editorInitialTitle,
            initialBlocks = editorInitialBlocks,
            initialSignatures = editorInitialSignatures,
            isBusy = uiState.isCreatingOrUploading,
            onBack = {
                showRichEditor = false
                viewModel.refreshDownloadedFiles()
            },
            onSaveDraft = { fileName, docTitle, docBlocks, docSigs ->
                showRichEditor = false
                viewModel.saveTextDraft(curTextDraftId, fileName, docTitle, docBlocks, docSigs)
                viewModel.refreshDownloadedFiles()
            },
            onAutoSaveDraft = { fileName, docTitle, docBlocks, docSigs ->
                viewModel.saveTextDraft(curTextDraftId, fileName, docTitle, docBlocks, docSigs, silent = true)
            },
            onUploadPdfToCloud = { file, fileName, pageCount ->
                viewModel.uploadReadyPdfToCloud(file, fileName, PdfSourceType.TEXT, pageCount, curTextDraftId)
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_studio_brand),
                            contentDescription = strings.appTitle,
                            modifier = Modifier.size(36.dp)
                        )
                        Column {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.user.email.ifBlank { uiState.user.uid },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onToggleLanguage) {
                        Text(
                            text = if (language == AppLanguage.ENGLISH) "বাংলা" else "EN",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                text = {
                                    Text(if (language == AppLanguage.BENGALI) "প্রোফাইল" else "Profile")
                                },
                                onClick = {
                                    showMoreMenu = false
                                    activeMenuDialog = "profile"
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                text = {
                                    Text(
                                        if (language == AppLanguage.BENGALI) "লগইন / অ্যাকাউন্ট পরিবর্তন"
                                        else "Switch Account / Login"
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.logout()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                text = {
                                    Text(if (language == AppLanguage.BENGALI) "অ্যাপ সম্পর্কে" else "About App")
                                },
                                onClick = {
                                    showMoreMenu = false
                                    activeMenuDialog = "about"
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Outlined.Policy, contentDescription = null) },
                                text = {
                                    Text(if (language == AppLanguage.BENGALI) "প্রাইভেসি পলিসি" else "Privacy Policy")
                                },
                                onClick = {
                                    showMoreMenu = false
                                    activeMenuDialog = "privacy"
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                text = {
                                    Text(if (language == AppLanguage.BENGALI) "সহায়তা ও শর্তাবলী" else "Help & Terms")
                                },
                                onClick = {
                                    showMoreMenu = false
                                    activeMenuDialog = "help"
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                if (activeTab == StudioNavTab.HOME && isOnline) {
                    BottomTestAdBannerSlot(language = language)
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = activeTab == StudioNavTab.HOME,
                        onClick = { activeTab = StudioNavTab.HOME },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        label = { Text(strings.navHome) }
                    )
                    NavigationBarItem(
                        selected = activeTab == StudioNavTab.FILES,
                        onClick = {
                            activeTab = StudioNavTab.FILES
                            viewModel.refreshDownloadedFiles()
                        },
                        icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        label = { Text(strings.navFiles) }
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreatePopupSheet = true },
                containerColor = StudioCrimson,
                contentColor = Color.White,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(strings.fabCreate, fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refreshFiles,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.isCreatingOrUploading) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = uiState.statusBannerText
                                        ?: strings.uploadingProgress((uiState.uploadProgress * 100).toInt()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (uiState.uploadProgress > 0f) {
                                    LinearProgressIndicator(
                                        progress = { uiState.uploadProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }

                if (activeTab == StudioNavTab.HOME) {
                    // Compact Editorial Banner
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(StudioCrimsonDark, StudioCrimson, Color(0xFFEA580C))
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = strings.heroTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = strings.heroSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    // 4-Tile Modular Tools Grid (iLovePDF & Smallpdf style)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = strings.quickToolsHeading,
                                style = MaterialTheme.typography.titleMedium
                            )

                            QuickToolTile(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Outlined.DocumentScanner,
                                title = if (language == AppLanguage.BENGALI) "ডকুমেন্ট স্ক্যান" else "Scan Document",
                                subtitle = if (language == AppLanguage.BENGALI)
                                    "সরাসরি স্ক্যান, অটো মার্জিন ও সোজা A4 কপি"
                                else "Auto edge crop, straighten & sequential pages",
                                accentColor = Color(0xFF4F46E5),
                                enabled = !uiState.isCreatingOrUploading,
                                onClick = { openScannerNew() }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                QuickToolTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Outlined.Collections,
                                    title = strings.imagesToPdfTitle,
                                    subtitle = strings.imagesToPdfDesc,
                                    accentColor = StudioCobalt,
                                    enabled = !uiState.isCreatingOrUploading,
                                    onClick = { openImagesStudioNew() }
                                )
                                QuickToolTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Outlined.TextFields,
                                    title = strings.textToPdfTitle,
                                    subtitle = strings.textToPdfDesc,
                                    accentColor = StudioEmerald,
                                    enabled = !uiState.isCreatingOrUploading,
                                    onClick = { openTextEditorNew() }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                QuickToolTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Outlined.Description,
                                    title = strings.templateLetterTitle,
                                    subtitle = if (language == AppLanguage.BENGALI) "৮টি আবেদনপত্র ও ৫টি ইংরেজি CV" else "8 letters & 5 English CVs",
                                    accentColor = StudioCrimson,
                                    enabled = !uiState.isCreatingOrUploading,
                                    onClick = { templatePickerKind = "letter" }
                                )
                                QuickToolTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Outlined.NoteAlt,
                                    title = strings.templateNotesTitle,
                                    subtitle = if (language == AppLanguage.BENGALI) "৪টি আধুনিক নোটস লেআউট" else "4 structured note sheets",
                                    accentColor = StudioAmber,
                                    enabled = !uiState.isCreatingOrUploading,
                                    onClick = { templatePickerKind = "notes" }
                                )
                            }
                        }
                    }

                    // HOME TAB: Show ONLY Drafts (no downloaded or cloud files)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (language == AppLanguage.BENGALI)
                                        "সংরক্ষিত ড্রাফটসমূহ (${uiState.drafts.size})"
                                    else "Saved Drafts (${uiState.drafts.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            if (uiState.drafts.isNotEmpty()) {
                                uiState.drafts.forEach { draft ->
                                    DraftResumeCard(
                                        draft = draft,
                                        language = language,
                                        onResume = {
                                            when (draft) {
                                                is StudioDraft.TextDraft -> openTextEditorNew(
                                                    title = draft.docTitle,
                                                    blocks = draft.blocks,
                                                    fileName = draft.fileName,
                                                    draftId = draft.id,
                                                    signatures = draft.signatures
                                                )
                                                is StudioDraft.ImagesDraft -> openImagesStudioNew(
                                                    fileName = draft.fileName,
                                                    pages = draft.pages,
                                                    options = draft.options,
                                                    draftId = draft.id
                                                )
                                            }
                                        },
                                        onDelete = { viewModel.deleteDraft(draft.id) }
                                    )
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, OutlineHairline),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.BookmarkAdded,
                                            contentDescription = null,
                                            tint = StudioAmber,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) "এখনো কোনো ড্রাফট নেই" else "No drafts saved yet",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (language == AppLanguage.BENGALI)
                                                "উপরের যেকোনো টুল দিয়ে কাজ শুরু করলে বা ব্যাক করলে অটোমেটিক ড্রাফটে যোগ হবে।"
                                            else "Start any project above and your progress auto-saves here as a draft.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // FILES TAB: Show ONLY Downloaded Files (no drafts mixed in)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (language == AppLanguage.BENGALI)
                                        "ডাউনলোড করা ফাইলসমূহ (${filteredDownloadedFiles.size})"
                                    else "Downloaded Files (${filteredDownloadedFiles.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { isGridView = !isGridView }) {
                                        Icon(
                                            imageVector = if (isGridView) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                            contentDescription = "Toggle Layout"
                                        )
                                    }
                                    IconButton(onClick = viewModel::refreshDownloadedFiles) {
                                        Icon(
                                            imageVector = Icons.Outlined.Refresh,
                                            contentDescription = "Refresh"
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = { Text(strings.searchHint) },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = downloadFormatFilter == DownloadFormatFilter.ALL,
                                    onClick = { downloadFormatFilter = DownloadFormatFilter.ALL },
                                    label = { Text(strings.filterAll) }
                                )
                                FilterChip(
                                    selected = downloadFormatFilter == DownloadFormatFilter.PDF,
                                    onClick = { downloadFormatFilter = DownloadFormatFilter.PDF },
                                    label = { Text("PDF") }
                                )
                                FilterChip(
                                    selected = downloadFormatFilter == DownloadFormatFilter.WORD,
                                    onClick = { downloadFormatFilter = DownloadFormatFilter.WORD },
                                    label = { Text("Word") }
                                )
                            }
                        }
                    }

                    if (filteredDownloadedFiles.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, OutlineHairline),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = null,
                                        tint = StudioEmerald,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = if (language == AppLanguage.BENGALI)
                                            "এখনো কোনো ডাউনলোড করা ফাইল নেই"
                                        else "No downloaded files yet",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (language == AppLanguage.BENGALI)
                                            "যেকোনো প্রজেক্ট থেকে PDF বা Word (.docx) ডাউনলোড করলে শুধু সেই ফাইলগুলো এখানে সেভ থাকবে।"
                                        else "Only files downloaded from any tool (PDF or Word .docx) appear here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (isGridView) {
                        items(filteredDownloadedFiles.chunked(2)) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { dl ->
                                    DownloadedFileGridCard(
                                        modifier = Modifier.weight(1f),
                                        item = dl,
                                        language = language,
                                        onOpen = { viewModel.openDownloadedFile(context, dl) },
                                        onShare = { viewModel.shareDownloadedFile(context, dl) },
                                        onDelete = { viewModel.deleteDownloadedFile(dl.id) }
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(filteredDownloadedFiles, key = { it.id }) { dl ->
                            DownloadedFileRowCard(
                                item = dl,
                                language = language,
                                onOpen = { viewModel.openDownloadedFile(context, dl) },
                                onShare = { viewModel.shareDownloadedFile(context, dl) },
                                onDelete = { viewModel.deleteDownloadedFile(dl.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // In-Place + তৈরি (Create) Feature List Pop-up Dialog (stays on current screen)
    if (showCreatePopupSheet) {
        Dialog(onDismissRequest = { showCreatePopupSheet = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, OutlineHairline),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == AppLanguage.BENGALI) "নতুন তৈরি করুন" else "Create Document",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "চালু থাকা ফিচারগুলো থেকে যেকোনো একটি বেছে নিন"
                                else "Pick an active studio feature",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showCreatePopupSheet = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close")
                        }
                    }
                    HorizontalDivider()

                    CreateFeaturePopupRow(
                        icon = Icons.Outlined.DocumentScanner,
                        title = if (language == AppLanguage.BENGALI) "ডকুমেন্ট স্ক্যান" else "Scan Document",
                        subtitle = if (language == AppLanguage.BENGALI) "অটো মার্জিন ক্যামেরা ও সোজা A4" else "Auto margin crop & straighten",
                        accentColor = Color(0xFF4F46E5),
                        onClick = {
                            showCreatePopupSheet = false
                            openScannerNew()
                        }
                    )
                    HorizontalDivider(color = OutlineHairline.copy(alpha = 0.5f))
                    CreateFeaturePopupRow(
                        icon = Icons.Outlined.Collections,
                        title = strings.imagesToPdfTitle,
                        subtitle = strings.imagesToPdfDesc,
                        accentColor = StudioCobalt,
                        onClick = {
                            showCreatePopupSheet = false
                            openImagesStudioNew()
                        }
                    )
                    HorizontalDivider(color = OutlineHairline.copy(alpha = 0.5f))
                    CreateFeaturePopupRow(
                        icon = Icons.Outlined.TextFields,
                        title = strings.textToPdfTitle,
                        subtitle = if (language == AppLanguage.BENGALI) "Word আপলোড, লেখা ও .docx/.pdf ডাউনলোড" else "Word upload, compose & .docx/.pdf",
                        accentColor = StudioEmerald,
                        onClick = {
                            showCreatePopupSheet = false
                            openTextEditorNew()
                        }
                    )
                    HorizontalDivider(color = OutlineHairline.copy(alpha = 0.5f))
                    CreateFeaturePopupRow(
                        icon = Icons.Outlined.Description,
                        title = strings.templateLetterTitle,
                        subtitle = if (language == AppLanguage.BENGALI) "৮টি আবেদনপত্র ও ৫টি ইংরেজি CV" else "8 letters & 5 English CVs",
                        accentColor = StudioCrimson,
                        onClick = {
                            showCreatePopupSheet = false
                            templatePickerKind = "letter"
                        }
                    )
                    HorizontalDivider(color = OutlineHairline.copy(alpha = 0.5f))
                    CreateFeaturePopupRow(
                        icon = Icons.Outlined.NoteAlt,
                        title = strings.templateNotesTitle,
                        subtitle = if (language == AppLanguage.BENGALI) "৪টি আধুনিক স্টাডি নোটস লেআউট" else "4 structured study note sheets",
                        accentColor = StudioAmber,
                        onClick = {
                            showCreatePopupSheet = false
                            templatePickerKind = "notes"
                        }
                    )
                }
            }
        }
    }

    templatePickerKind?.let { kind ->
        val templates: List<DocumentTemplate> = if (kind == "letter") {
            DocumentTemplates.letterTemplates
        } else {
            DocumentTemplates.notesTemplates
        }
        AlertDialog(
            onDismissRequest = { templatePickerKind = null },
            title = {
                Text(
                    if (kind == "letter") {
                        if (language == AppLanguage.BENGALI) "আবেদনপত্র ও ইংরেজি সিভি (১৩টি)" else "Letters & English CVs (13)"
                    } else {
                        if (language == AppLanguage.BENGALI) "আধুনিক নোটস টেমপ্লেট (৪টি)" else "Notes Templates (4)"
                    }
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { tpl ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    templatePickerKind = null
                                    val passportSlot = if (tpl.includePassportPhotoSlot) {
                                        DocumentTemplates.createPassportPhotoPlaceholderFile(context)?.let { (path, ratio) ->
                                            listOf(
                                                DocumentSignature(
                                                    pageIndex = 0,
                                                    imagePath = path,
                                                    xFraction = 0.745f,
                                                    yFraction = 0.045f,
                                                    widthFraction = 0.175f,
                                                    aspectRatio = ratio,
                                                    kind = "image"
                                                )
                                            )
                                        } ?: emptyList()
                                    } else emptyList()
                                    openTextEditorNew(
                                        title = tpl.docTitle,
                                        blocks = tpl.blocks,
                                        fileName = if (tpl.includePassportPhotoSlot) "Curriculum_Vitae_CV" else "PDF Maker Studio",
                                        signatures = passportSlot
                                    )
                                },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, OutlineHairline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) tpl.titleBn else tpl.titleEn,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (language == AppLanguage.BENGALI) tpl.subtitleBn else tpl.subtitleEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { templatePickerKind = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }


    renameTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name.removeSuffix(".pdf")) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(strings.renameDialogTitle) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(strings.fileNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        renameTarget = null
                        viewModel.renamePdf(target, newName)
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(strings.deleteDialogTitle) },
            text = { Text("${target.name}\n\n${strings.deleteDialogBody}") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        viewModel.deletePdf(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text(strings.deleteConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    activeMenuDialog?.let { dialogType ->
        AlertDialog(
            onDismissRequest = { activeMenuDialog = null },
            title = {
                Text(
                    when (dialogType) {
                        "profile" -> if (language == AppLanguage.BENGALI) "ইউজার প্রোফাইল" else "User Profile"
                        "about" -> if (language == AppLanguage.BENGALI) "PDF Maker Studio সম্পর্কে" else "About PDF Maker Studio"
                        "privacy" -> if (language == AppLanguage.BENGALI) "প্রাইভেসি পলিসি" else "Privacy Policy"
                        else -> if (language == AppLanguage.BENGALI) "সহায়তা ও শর্তাবলী" else "Help & Terms"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (dialogType) {
                        "profile" -> {
                            Text(
                                text = uiState.user.displayName?.ifBlank { "PDF Maker Member" } ?: "PDF Maker Member",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.user.email.ifBlank { uiState.user.uid },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "সংরক্ষিত ড্রাফট: ${uiState.drafts.size} টি"
                                else "Saved Drafts: ${uiState.drafts.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        "about" -> {
                            Text(
                                text = "PDF Maker Studio · v1.0.0",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "ছবি থেকে কাস্টম A4 PDF, প্রফেশনাল বাংলা ও ইংরেজি এডিটর, ফুল-স্ক্রিন ডিজিটাল সিগনেচার এবং Firebase ক্লাউড ব্যাকআপ সুবিধা।"
                                else "Create A4 PDFs from photos with Megapixel/MB optimization, compose rich Bengali & English docs with draggable signatures, and sync to Firebase Cloud.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        "privacy" -> {
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "আপনার তৈরি করা PDF ফাইলগুলো Firebase Authentication ও Cloud Storage-এ আপনার নিজস্ব অ্যাকাউন্টের অধীনে নিরাপদভাবে সংরক্ষিত থাকে। ড্রাফট ফাইল লোকাল ডিভাইসে সংরক্ষিত হয়।"
                                else "All generated PDFs are stored privately under your authenticated Firebase user UID. Drafts stay saved locally on your device.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (language == AppLanguage.BENGALI) {
                                    Text("এই অ্যাপে আপনি যেসব কাজ করতে পারবেন:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Text("• ডকুমেন্ট স্ক্যান: সরাসরি ক্যামেরা বা ছবি থেকে পেজ স্ক্যান — অটো মার্জিন ধরে বাঁকা ছবি সোজা, ক্লিয়ার এবং একের পর এক পেজ যোগ করা।", style = MaterialTheme.typography.bodySmall)
                                    Text("• ছবি থেকে পিডিএফ: একাধিক ছবি সাজিয়ে ও রোটেট করে A4 PDF তৈরি, কোয়ালিটি/মেগাপিক্সেল নিয়ন্ত্রণ এবং ক্যাপশন যোগ করা।", style = MaterialTheme.typography.bodySmall)
                                    Text("• টেক্সট ও একাধিক পেজ: বাংলা ও ইংরেজিতে ডকুমেন্ট লেখা, রঙ ও ফন্ট বদলানো, '+' বাটনে নতুন পেজ নেওয়া ও ছবি বসানো।", style = MaterialTheme.typography.bodySmall)
                                    Text("• ডিজিটাল সিগনেচার: অটো-রোটেট ল্যান্ডস্কেপ প্যাডে স্বাক্ষর করে পেজের যেকোনো জায়গায় বসানো ও ছোট-বড় করা।", style = MaterialTheme.typography.bodySmall)
                                    Text("• চাকরির আবেদনপত্র ও নোটস: চাকরি, ছুটি, ব্যাংক আবেদনপত্র ও স্টাডি নোটসের রেডি টেমপ্লেট এডিট করে PDF বানানো।", style = MaterialTheme.typography.bodySmall)
                                    Text("• সরাসরি ফোনে ডাউনলোড ও ড্রাফট: প্রজেক্ট থেকেই সরাসরি Downloads-এ সেভ, প্রিভিউ দেখা, অটো-ড্রাফট ও ক্লাউড সিঙ্ক।", style = MaterialTheme.typography.bodySmall)

                                    Text("শর্তাবলী:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Text("আপনার তৈরি সব ডকুমেন্ট, ছবি ও স্বাক্ষরের মালিকানা ও দায়ভার আপনার নিজস্ব। ব্যক্তিগত, পড়াশোনা ও চাকরিসংক্রান্ত বৈধ কাজের জন্য অ্যাপটি ব্যবহার করতে পারবেন।", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("What you can do in this app:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Text("• Document Scanner: Scan copies directly from camera or photos with automatic margin cropping, straightening, and sequential page capture.", style = MaterialTheme.typography.bodySmall)
                                    Text("• Images to PDF: Arrange & rotate photos into A4 PDFs with quality/MP control and captions above or below photos.", style = MaterialTheme.typography.bodySmall)
                                    Text("• Text & Multi-Page Docs: Write in Bengali & English, style fonts/colors, add unlimited '+' pages, and insert inline photos.", style = MaterialTheme.typography.bodySmall)
                                    Text("• Digital Signature: Sign on the auto-rotating landscape pad and drag or resize signatures anywhere on any page.", style = MaterialTheme.typography.bodySmall)
                                    Text("• Job Letters & Notes: Edit ready templates for job applications, leave/bank letters, and study notes into PDFs.", style = MaterialTheme.typography.bodySmall)
                                    Text("• Direct Phone Download & Drafts: Save PDFs straight to Downloads inside your project, auto-save drafts on Back, and sync to Cloud.", style = MaterialTheme.typography.bodySmall)

                                    Text("Terms:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Text("You retain full ownership and responsibility for all documents, photos, and signatures you create. Use the app responsibly for personal, study, and job or office work.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (dialogType == "profile") {
                    Button(
                        onClick = {
                            activeMenuDialog = null
                            viewModel.logout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCrimson)
                    ) {
                        Text(strings.logout)
                    }
                } else {
                    TextButton(onClick = { activeMenuDialog = null }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (dialogType == "profile") {
                    TextButton(onClick = { activeMenuDialog = null }) {
                        Text(strings.cancel)
                    }
                }
            }
        )
    }
}

@Composable
private fun rememberIsOnlineState(context: Context): Boolean {
    val cm = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    fun checkConnected(): Boolean {
        val network = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    var isOnline by remember { mutableStateOf(checkConnected()) }

    DisposableEffect(cm) {
        if (cm == null) {
            onDispose {}
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isOnline = true
                }

                override fun onLost(network: Network) {
                    isOnline = checkConnected()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    isOnline = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching { cm.registerNetworkCallback(request, callback) }
            onDispose {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }
    }

    return isOnline
}

@Composable
private fun BottomTestAdBannerSlot(language: AppLanguage) {
    // Standard 320x50 dp AdMob Banner placeholder — replace Test Ad Unit ID when publishing on Play Store:
    // ca-app-pub-3940256099942544/6300978111
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(0.5.dp, OutlineHairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(StudioAmber.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = StudioAmber
                    )
                }
                Text(
                    text = if (language == AppLanguage.BENGALI)
                        "টেস্ট ব্যানার অ্যাড স্লট (৩২০×৫০)"
                    else "Test Banner Ad Slot (320×50)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "AdMob Ready",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DraftResumeCard(
    draft: StudioDraft,
    language: AppLanguage,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val dateText = remember(draft.updatedAtMillis) {
        DateFormat.format("dd MMM, hh:mm a", Date(draft.updatedAtMillis)).toString()
    }
    val subtitle = when (draft) {
        is StudioDraft.TextDraft ->
            if (language == AppLanguage.BENGALI) "টেক্সট ড্রাফট · ${draft.blocks.size} ব্লক · $dateText"
            else "Text Draft · ${draft.blocks.size} blocks · $dateText"
        is StudioDraft.ImagesDraft ->
            if (language == AppLanguage.BENGALI) "ছবির ড্রাফট · ${draft.pages.size} ছবি · $dateText"
            else "Images Draft · ${draft.pages.size} pages · $dateText"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onResume),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, OutlineHairline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(StudioAmber.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkAdded,
                    contentDescription = null,
                    tint = StudioAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete draft",
                    tint = DangerRed
                )
            }
        }
    }
}

@Composable
private fun QuickToolTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineHairline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CreateFeaturePopupRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DownloadedFileGridCard(
    modifier: Modifier = Modifier,
    item: DownloadedFileItem,
    language: AppLanguage,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isPdf = item.format.equals("pdf", ignoreCase = true)
    val accent = if (isPdf) StudioCrimson else StudioCobalt
    val formattedSize = remember(item.sizeBytes) {
        Formatter.formatShortFileSize(context, item.sizeBytes)
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineHairline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = item.format.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(4.dp)
                                .background(Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0xFFE2E8F0), RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(3.dp)
                                .background(Color(0xFFE2E8F0), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.format.uppercase()} · $formattedSize",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (language == AppLanguage.BENGALI) "খুলুন" else "Open") },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (language == AppLanguage.BENGALI) "শেয়ার" else "Share") },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (language == AppLanguage.BENGALI) "মুছুন" else "Delete", color = DangerRed) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedFileRowCard(
    item: DownloadedFileItem,
    language: AppLanguage,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isPdf = item.format.equals("pdf", ignoreCase = true)
    val accent = if (isPdf) StudioCrimson else StudioCobalt
    val formattedSize = remember(item.sizeBytes) {
        Formatter.formatShortFileSize(context, item.sizeBytes)
    }
    val formattedDate = remember(item.downloadedAtMillis) {
        DateFormat.format("dd MMM yyyy, hh:mm a", Date(item.downloadedAtMillis)).toString()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineHairline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = accent
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.format.uppercase()} · $formattedSize · $formattedDate",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOpen) {
                Icon(
                    Icons.Outlined.OpenInNew,
                    contentDescription = if (language == AppLanguage.BENGALI) "খুলুন" else "Open",
                    tint = StudioEmerald
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = if (language == AppLanguage.BENGALI) "শেয়ার" else "Share"
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = if (language == AppLanguage.BENGALI) "মুছুন" else "Delete",
                    tint = DangerRed
                )
            }
        }
    }
}

private suspend fun openPdfIntent(
    context: Context,
    file: File,
    onError: suspend (String) -> Unit
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    }.onFailure {
        onError("No PDF viewer app found on this device.")
    }
}
