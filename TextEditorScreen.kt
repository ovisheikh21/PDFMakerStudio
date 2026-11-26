package com.pdfmakerstudio.app.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import android.text.format.Formatter
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.style.TextOverflow
import com.pdfmakerstudio.app.data.pdf.A4PdfGenerator
import com.pdfmakerstudio.app.data.pdf.ImportedWordDocResult
import com.pdfmakerstudio.app.data.pdf.PdfDeviceSaver
import com.pdfmakerstudio.app.data.pdf.PdfDocumentImporter
import com.pdfmakerstudio.app.data.pdf.SavedDevicePdf
import com.pdfmakerstudio.app.data.pdf.WordDocumentCodec
import com.pdfmakerstudio.app.data.pdf.WordExportFormat
import com.pdfmakerstudio.app.domain.model.DocumentSignature
import com.pdfmakerstudio.app.domain.model.RichFontFamily
import com.pdfmakerstudio.app.domain.model.RichPdfBlock
import com.pdfmakerstudio.app.domain.model.RichTextAlign
import com.pdfmakerstudio.app.ui.strings.AppLanguage
import com.pdfmakerstudio.app.ui.theme.OutlineHairline
import com.pdfmakerstudio.app.ui.theme.StudioCobalt
import com.pdfmakerstudio.app.ui.theme.StudioCrimson
import com.pdfmakerstudio.app.ui.theme.StudioEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

private data class SavedWordExportState(
    val savedFile: SavedDevicePdf,
    val format: WordExportFormat,
    val sizeBytes: Long
)

private val EDITOR_COLORS = listOf(
    0xFF0F172AL,
    0xFF475569L,
    0xFFD92D20L,
    0xFF1D4ED8L,
    0xFF047857L,
    0xFFB45309L,
    0xFF6D28D9L,
    0xFFBE185DL
)

private val EDITOR_ICONS = listOf(
    "✓", "★", "✦", "●", "◆", "➜", "📌", "📞", "✉", "📍", "📅", "🎓", "💼", "📄"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    language: AppLanguage,
    pdfGenerator: A4PdfGenerator,
    initialFileName: String = "PDF Maker Studio",
    initialTitle: String,
    initialBlocks: List<RichPdfBlock>,
    initialSignatures: List<DocumentSignature> = emptyList(),
    isBusy: Boolean,
    onBack: () -> Unit,
    onSaveDraft: (fileName: String, title: String, blocks: List<RichPdfBlock>, signatures: List<DocumentSignature>) -> Unit,
    onAutoSaveDraft: ((fileName: String, title: String, blocks: List<RichPdfBlock>, signatures: List<DocumentSignature>) -> Unit)? = null,
    onUploadPdfToCloud: (pdfFile: File, fileName: String, pageCount: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var readyExportState by remember { mutableStateOf<LocalReadyPdfState?>(null) }
    var isBuildingPreview by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf(initialFileName.ifBlank { "PDF Maker Studio" }) }
    var title by remember { mutableStateOf(initialTitle) }
    val blocks = remember {
        mutableStateListOf<RichPdfBlock>().apply {
            if (initialBlocks.isNotEmpty()) {
                addAll(initialBlocks)
            } else {
                add(RichPdfBlock.TextBlock())
            }
        }
    }
    val signatures = remember {
        mutableStateListOf<DocumentSignature>().apply {
            addAll(initialSignatures)
        }
    }
    var selectedBlockId by remember { mutableStateOf(blocks.firstOrNull()?.id) }
    var selectedSigId by remember { mutableStateOf<String?>(null) }
    var signaturePadPageIndex by remember { mutableStateOf<Int?>(null) }
    var showFontMenu by remember { mutableStateOf(false) }
    var showIconMenu by remember { mutableStateOf(false) }
    var showDownloadFormatDialog by remember { mutableStateOf(false) }
    var showCustomFontDialog by remember { mutableStateOf(false) }
    var customFontDraftInput by remember { mutableStateOf("") }
    var pendingWordImport by remember { mutableStateOf<ImportedWordDocResult?>(null) }
    var importNoticeText by remember { mutableStateOf<String?>(null) }
    var importNoticeIsError by remember { mutableStateOf(false) }
    var readyWordExportState by remember { mutableStateOf<SavedWordExportState?>(null) }
    var pdfModeSwitch by remember { mutableStateOf<com.pdfmakerstudio.app.data.pdf.ImportedPdfAlternateMode?>(null) }

    val selectedBlockIdx = blocks.indexOfFirst { it.id == selectedBlockId }.coerceAtLeast(0)
    val activePageIndex = blocks.take(selectedBlockIdx + 1).count { it is RichPdfBlock.PageBreakBlock }
    val selectedTextBlock = blocks.firstOrNull { it.id == selectedBlockId } as? RichPdfBlock.TextBlock

    val customFontsInDoc = remember(blocks.toList()) {
        blocks.filterIsInstance<RichPdfBlock.TextBlock>()
            .mapNotNull { it.customFontName?.trim()?.takeIf { fn -> fn.isNotEmpty() } }
            .distinct()
    }

    fun hasMeaningfulEditorContent(): Boolean {
        return fileName.trim() != "PDF Maker Studio" || title.isNotBlank() || signatures.isNotEmpty() || blocks.any {
            when (it) {
                is RichPdfBlock.TextBlock -> it.text.isNotBlank()
                is RichPdfBlock.ImageBlock -> true
                is RichPdfBlock.DividerBlock -> true
                is RichPdfBlock.PageBreakBlock -> false
            }
        }
    }

    LaunchedEffect(fileName, title, blocks.toList(), signatures.toList()) {
        if (hasMeaningfulEditorContent()) {
            delay(1500L)
            onAutoSaveDraft?.invoke(
                fileName.ifBlank { "PDF Maker Studio" },
                title,
                blocks.toList(),
                signatures.toList()
            )
        }
    }

    fun applyImportedWordDoc(imported: ImportedWordDocResult, mode: String) {
        if (mode == "replace") {
            fileName = imported.suggestedFileName.ifBlank { fileName }
            title = imported.docTitle
            blocks.clear()
            blocks.addAll(imported.blocks.ifEmpty { listOf(RichPdfBlock.TextBlock()) })
            signatures.clear()
            signatures.addAll(imported.overlays)
            selectedSigId = if (imported.alternateMode?.currentMode == "exact") null else imported.overlays.firstOrNull()?.id
            pdfModeSwitch = imported.alternateMode
        } else {
            pdfModeSwitch = null
            val cleanedExisting = blocks.filterNot { it is RichPdfBlock.TextBlock && it.text.isBlank() }
            val pageOffset = cleanedExisting.count { it is RichPdfBlock.PageBreakBlock }
            blocks.clear()
            blocks.addAll(cleanedExisting)
            if (imported.docTitle.isNotBlank()) {
                if (title.isBlank()) {
                    title = imported.docTitle
                } else {
                    blocks.add(
                        RichPdfBlock.TextBlock(
                            text = imported.docTitle,
                            fontSizeSp = 18f,
                            isBold = true
                        )
                    )
                }
            }
            blocks.addAll(imported.blocks)
            val shiftedOverlays = imported.overlays.map { ov ->
                ov.copy(
                    id = UUID.randomUUID().toString(),
                    pageIndex = ov.pageIndex + pageOffset
                )
            }
            signatures.addAll(shiftedOverlays)
            if (shiftedOverlays.isNotEmpty()) {
                selectedSigId = shiftedOverlays.first().id
            }
        }
        selectedBlockId = blocks.firstOrNull()?.id
        val scriptsLabel = imported.detectedScripts.joinToString(", ")
        val fontsLabel = imported.detectedFonts.take(3).joinToString(", ")
        importNoticeIsError = false
        val docLabelBn = if (imported.sourceFormat == "pdf") "পিডিএফ" else "ওয়ার্ড"
        val docLabelEn = if (imported.sourceFormat == "pdf") "PDF" else "Word"
        val overlaySuffixBn = when {
            imported.alternateMode?.currentMode == "exact" -> " · অরিজিনাল ডিজাইন হুবহু চালু"
            imported.overlays.isNotEmpty() -> " · ${imported.overlays.size}টি ছবি মুভ/রিসাইজযোগ্য"
            else -> ""
        }
        val overlaySuffixEn = when {
            imported.alternateMode?.currentMode == "exact" -> " · Exact 1:1 Original Layout Active"
            imported.overlays.isNotEmpty() -> " · ${imported.overlays.size} movable image(s)"
            else -> ""
        }
        importNoticeText = if (language == AppLanguage.BENGALI) {
            "$docLabelBn ফাইল লোড হয়েছে (${imported.sourceFormat.uppercase()} · $scriptsLabel${if (fontsLabel.isNotEmpty()) " · $fontsLabel" else ""}$overlaySuffixBn)"
        } else {
            "$docLabelEn file loaded (${imported.sourceFormat.uppercase()} · $scriptsLabel${if (fontsLabel.isNotEmpty()) " · $fontsLabel" else ""}$overlaySuffixEn)"
        }
    }

    val pickWordFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isBuildingPreview = true
                val res = PdfDocumentImporter.importEditableDocument(context, uri)
                res.onSuccess { imported ->
                    if (hasMeaningfulEditorContent()) {
                        pendingWordImport = imported
                    } else {
                        applyImportedWordDoc(imported, "replace")
                    }
                }.onFailure { err ->
                    importNoticeIsError = true
                    importNoticeText = err.localizedMessage ?: if (language == AppLanguage.BENGALI) {
                        "PDF বা Word ফাইলটি পড়া যায়নি (সর্বোচ্চ ১০ MB অনুমোদিত)"
                    } else "Could not read selected PDF or Word file (max 10 MB)"
                }
                isBuildingPreview = false
            }
        }
    }

    var pendingOverlayTargetPage by remember { mutableStateOf<Int?>(null) }
    var pendingReplaceSigId by remember { mutableStateOf<String?>(null) }

    val replacePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val targetId = pendingReplaceSigId
        pendingReplaceSigId = null
        if (uri != null && targetId != null) {
            coroutineScope.launch {
                isBuildingPreview = true
                val saved = withContext(Dispatchers.IO) {
                    prepareOverlayImageFile(context, uri)
                }
                if (saved != null) {
                    val (path, ratio) = saved
                    val idx = signatures.indexOfFirst { it.id == targetId }
                    if (idx >= 0) {
                        val existing = signatures[idx]
                        signatures[idx] = existing.copy(
                            imagePath = path,
                            aspectRatio = ratio,
                            kind = "image"
                        )
                        selectedSigId = targetId
                    }
                }
                isBuildingPreview = false
            }
        }
    }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        val targetPage = pendingOverlayTargetPage ?: activePageIndex
        pendingOverlayTargetPage = null
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isBuildingPreview = true
                val existingOnPage = signatures.count { it.pageIndex == targetPage }
                val prepared = withContext(Dispatchers.IO) {
                    uris.mapIndexedNotNull { idx, uri ->
                        val saved = prepareOverlayImageFile(context, uri) ?: return@mapIndexedNotNull null
                        val (path, ratio) = saved
                        val slot = existingOnPage + idx
                        val widthFrac = if (ratio > 1.2f) 0.36f else 0.46f
                        val xFrac = ((0.08f + (slot % 3) * 0.14f)).coerceIn(0.04f, 0.96f - widthFrac)
                        val yFrac = ((0.18f + (slot % 4) * 0.16f)).coerceIn(0.04f, 0.72f)
                        DocumentSignature(
                            pageIndex = targetPage,
                            imagePath = path,
                            xFraction = xFrac,
                            yFraction = yFrac,
                            widthFraction = widthFrac,
                            aspectRatio = ratio,
                            kind = "image"
                        )
                    }
                }
                if (prepared.isNotEmpty()) {
                    signatures.addAll(prepared)
                    selectedSigId = prepared.last().id
                }
                isBuildingPreview = false
            }
        }
    }

    fun addNewPageAfter(insertAfterIndex: Int = blocks.lastIndex) {
        val pb = RichPdfBlock.PageBreakBlock()
        val nextText = RichPdfBlock.TextBlock(
            fontFamily = selectedTextBlock?.fontFamily ?: RichFontFamily.BENGALI_SANS,
            fontSizeSp = selectedTextBlock?.fontSizeSp ?: 14f,
            colorHex = selectedTextBlock?.colorHex ?: 0xFF1E293BL
        )
        val safePos = (insertAfterIndex + 1).coerceIn(0, blocks.size)
        blocks.add(safePos, pb)
        blocks.add(safePos + 1, nextText)
        selectedBlockId = nextText.id
    }

    fun updateSelectedText(transform: (RichPdfBlock.TextBlock) -> RichPdfBlock.TextBlock) {
        val idx = blocks.indexOfFirst { it.id == selectedBlockId }
        if (idx >= 0) {
            val item = blocks[idx] as? RichPdfBlock.TextBlock ?: return
            blocks[idx] = transform(item)
        }
    }

    fun handleBackWithAutoSave() {
        val hasContent = fileName.trim() != "PDF Maker Studio" ||
            title.isNotBlank() ||
            signatures.isNotEmpty() ||
            blocks.any {
                when (it) {
                    is RichPdfBlock.TextBlock -> it.text.isNotBlank()
                    is RichPdfBlock.ImageBlock -> true
                    is RichPdfBlock.DividerBlock -> true
                    is RichPdfBlock.PageBreakBlock -> false
                }
            }
        if (hasContent) {
            onSaveDraft(
                fileName.ifBlank { "PDF Maker Studio" },
                title,
                blocks.toList(),
                signatures.toList()
            )
        } else {
            onBack()
        }
    }

    BackHandler(enabled = !isBusy && signaturePadPageIndex == null) {
        handleBackWithAutoSave()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { handleBackWithAutoSave() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = if (language == AppLanguage.BENGALI) "প্রফেশনাল A4 এডিটর" else "Rich A4 Editor",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ProjectTopControlPanel(
                language = language,
                fileName = fileName,
                onFileNameChange = { fileName = it },
                hasContent = true,
                isBusy = isBusy || isBuildingPreview,
                onUploadFile = {
                    pickWordFileLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/msword",
                            "application/rtf",
                            "text/rtf",
                            "text/plain",
                            "text/html",
                            "*/*"
                        )
                    )
                },
                uploadFileLabel = if (language == AppLanguage.BENGALI) "আপলোড" else "Upload",
                onPreview = {
                    coroutineScope.launch {
                        isBuildingPreview = true
                        val res = pdfGenerator.createPdfFromRichBlocks(
                            title = title.ifBlank { fileName.ifBlank { "PDF Maker Studio" } },
                            blocks = blocks.toList(),
                            signatures = signatures.toList()
                        )
                        res.onSuccess { generated -> previewPdfFile = generated.file }
                        isBuildingPreview = false
                    }
                },
                onDownloadPdf = { showDownloadFormatDialog = true },
                downloadButtonLabel = if (language == AppLanguage.BENGALI) "ডাউনলোড ▾" else "Download ▾"
            )
            // Word-Processor Formatting Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Font Picker (Studio + Word Desktop + Custom Fonts)
                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { showFontMenu = true },
                                label = {
                                    Text(
                                        text = selectedTextBlock?.customFontName?.takeIf { it.isNotBlank() }
                                            ?: selectedTextBlock?.fontFamily?.displayName
                                            ?: "Font",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = showFontMenu,
                                onDismissRequest = { showFontMenu = false }
                            ) {
                                if (customFontsInDoc.isNotEmpty()) {
                                    customFontsInDoc.forEach { customName ->
                                        DropdownMenuItem(
                                            text = { Text("$customName (Custom/Word)") },
                                            onClick = {
                                                showFontMenu = false
                                                val (mapped, _) = WordDocumentCodec.mapWordFontToStudio(customName)
                                                updateSelectedText {
                                                    it.copy(fontFamily = mapped, customFontName = customName)
                                                }
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                }
                                RichFontFamily.entries.forEach { family ->
                                    DropdownMenuItem(
                                        text = { Text(family.displayName) },
                                        onClick = {
                                            showFontMenu = false
                                            updateSelectedText {
                                                it.copy(fontFamily = family, customFontName = null)
                                            }
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI)
                                                "+ কাস্টম ওয়ার্ড ফন্ট লিখুন..."
                                            else "+ Custom Word Font...",
                                            color = StudioCobalt,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        showFontMenu = false
                                        customFontDraftInput = selectedTextBlock?.customFontName ?: ""
                                        showCustomFontDialog = true
                                    }
                                )
                            }
                        }

                        // Bold / Italic / Underline
                        FilterChip(
                            selected = selectedTextBlock?.isBold == true,
                            onClick = { updateSelectedText { it.copy(isBold = !it.isBold) } },
                            label = { Icon(Icons.Outlined.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = selectedTextBlock?.isItalic == true,
                            onClick = { updateSelectedText { it.copy(isItalic = !it.isItalic) } },
                            label = { Icon(Icons.Outlined.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = selectedTextBlock?.isUnderline == true,
                            onClick = { updateSelectedText { it.copy(isUnderline = !it.isUnderline) } },
                            label = { Icon(Icons.Outlined.FormatUnderlined, contentDescription = "Underline", modifier = Modifier.size(18.dp)) }
                        )

                        // Alignment
                        IconButton(onClick = { updateSelectedText { it.copy(align = RichTextAlign.LEFT) } }) {
                            Icon(Icons.AutoMirrored.Outlined.FormatAlignLeft, contentDescription = "Align Left")
                        }
                        IconButton(onClick = { updateSelectedText { it.copy(align = RichTextAlign.CENTER) } }) {
                            Icon(Icons.Outlined.FormatAlignCenter, contentDescription = "Align Center")
                        }
                        IconButton(onClick = { updateSelectedText { it.copy(align = RichTextAlign.RIGHT) } }) {
                            Icon(Icons.AutoMirrored.Outlined.FormatAlignRight, contentDescription = "Align Right")
                        }

                        // Icon Picker (optional — inserts directly into text)
                        Box {
                            IconButton(onClick = { showIconMenu = true }) {
                                Icon(Icons.Outlined.EmojiEmotions, contentDescription = "Insert Icon")
                            }
                            DropdownMenu(
                                expanded = showIconMenu,
                                onDismissRequest = { showIconMenu = false }
                            ) {
                                EDITOR_ICONS.chunked(7).forEach { rowIcons ->
                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        rowIcons.forEach { glyph ->
                                            TextButton(
                                                onClick = {
                                                    showIconMenu = false
                                                    updateSelectedText {
                                                        it.copy(text = it.text + "$glyph ")
                                                    }
                                                },
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Text(glyph, fontSize = 18.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Add Photo
                        IconButton(
                            onClick = {
                                pickImagesLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Add Photo")
                        }

                        // Optional Heading / Section / Divider
                        IconButton(
                            onClick = {
                                val h = RichPdfBlock.TextBlock(
                                    text = "",
                                    fontSizeSp = 19f,
                                    isBold = true
                                )
                                blocks.add(h)
                                selectedBlockId = h.id
                            }
                        ) {
                            Icon(Icons.Outlined.Title, contentDescription = "Add Heading")
                        }
                        IconButton(
                            onClick = {
                                val p = RichPdfBlock.TextBlock()
                                blocks.add(p)
                                selectedBlockId = p.id
                            }
                        ) {
                            Icon(Icons.Outlined.PostAdd, contentDescription = "Add Paragraph")
                        }
                        IconButton(
                            onClick = {
                                val d = RichPdfBlock.DividerBlock()
                                blocks.add(d)
                                selectedBlockId = d.id
                            }
                        ) {
                            Icon(Icons.Outlined.HorizontalRule, contentDescription = "Add Divider")
                        }
                        FilterChip(
                            selected = false,
                            onClick = { signaturePadPageIndex = activePageIndex },
                            leadingIcon = {
                                Icon(Icons.Outlined.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(if (language == AppLanguage.BENGALI) "সিগনেচার" else "Sign")
                            }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { addNewPageAfter() },
                            leadingIcon = {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(if (language == AppLanguage.BENGALI) "নতুন পেজ" else "New Page")
                            }
                        )
                    }

                    // Color Swatches + Font Size Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EDITOR_COLORS.forEach { hex ->
                            val isChosen = selectedTextBlock?.colorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (isChosen) 2.5.dp else 1.dp,
                                        color = if (isChosen) StudioCrimson else OutlineHairline,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        updateSelectedText { it.copy(colorHex = hex) }
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(
                            onClick = {
                                updateSelectedText {
                                    it.copy(fontSizeSp = (it.fontSizeSp - 1f).coerceAtLeast(10f))
                                }
                            }
                        ) {
                            Text("A-")
                        }
                        Text(
                            text = "${(selectedTextBlock?.fontSizeSp ?: 14f).toInt()}pt",
                            style = MaterialTheme.typography.labelMedium
                        )
                        TextButton(
                            onClick = {
                                updateSelectedText {
                                    it.copy(fontSizeSp = (it.fontSizeSp + 1f).coerceAtMost(30f))
                                }
                            }
                        ) {
                            Text("A+")
                        }
                    }
                    HorizontalDivider()
                }
            }

            importNoticeText?.let { msg ->
                val bgColor = if (importNoticeIsError) Color(0xFFFEF2F2) else Color(0xFFECFDF5)
                val fgColor = if (importNoticeIsError) Color(0xFFB91C1C) else Color(0xFF047857)
                Surface(
                    color = bgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = fgColor,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { importNoticeText = null },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp), tint = fgColor)
                        }
                    }
                }
            }

            pdfModeSwitch?.let { switchState ->
                val isExact = switchState.currentMode == "exact"
                Surface(
                    color = if (isExact) Color(0xFFECFDF5) else Color(0xFFEFF6FF),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isExact) {
                                    if (language == AppLanguage.BENGALI) "অরিজিনাল ডিজাইন হুবহু চালু" else "Original 1:1 Layout Active"
                                } else {
                                    if (language == AppLanguage.BENGALI) "লেখা ও ছবি আলাদা এডিট মোড" else "Editable Text & Images Mode"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isExact) Color(0xFF065F46) else Color(0xFF1E3A8A)
                            )
                        }
                        Button(
                            onClick = {
                                val nextMode = if (isExact) "editable" else "exact"
                                val curBlocks = blocks.toList()
                                val curOverlays = signatures.toList()
                                blocks.clear()
                                blocks.addAll(switchState.blocks.ifEmpty { listOf(RichPdfBlock.TextBlock()) })
                                signatures.clear()
                                signatures.addAll(switchState.overlays)
                                selectedBlockId = blocks.firstOrNull()?.id
                                selectedSigId = if (nextMode == "editable") signatures.firstOrNull()?.id else null
                                pdfModeSwitch = com.pdfmakerstudio.app.data.pdf.ImportedPdfAlternateMode(
                                    currentMode = nextMode,
                                    blocks = curBlocks,
                                    overlays = curOverlays
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isExact) {
                                    if (language == AppLanguage.BENGALI) "লেখা ও ছবি আলাদা এডিট করুন" else "Edit Text & Images"
                                } else {
                                    if (language == AppLanguage.BENGALI) "অরিজিনাল ডিজাইনে ফিরুন" else "Original 1:1 Design"
                                },
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // A4 Document Paper Canvas
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = {
                            Text(if (language == AppLanguage.BENGALI) "ডকুমেন্টের শিরোনাম" else "Document Title")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                itemsIndexed(blocks, key = { _, b -> b.id }) { index, block ->
                    val pageNum = 1 + blocks.take(index + 1).count { it is RichPdfBlock.PageBreakBlock }
                    if (block is RichPdfBlock.PageBreakBlock) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioCrimson.copy(alpha = 0.35f)),
                            colors = CardDefaults.cardColors(containerColor = StudioCrimson.copy(alpha = 0.06f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (language == AppLanguage.BENGALI)
                                        "নতুন A4 পেজ শুরু · পৃষ্ঠা $pageNum"
                                    else "New A4 Page · Page $pageNum",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StudioCrimson
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { addNewPageAfter(index) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (language == AppLanguage.BENGALI) "আরেকটি পেজ" else "Add Page")
                                    }
                                    IconButton(
                                        onClick = { blocks.removeAt(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "Remove Page Break",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        return@itemsIndexed
                    }

                    val isSelected = block.id == selectedBlockId
                    val blocksOnSamePage = blocks
                        .drop(blocks.take(index).lastIndexOfFirst { it is RichPdfBlock.PageBreakBlock }.let { if (it < 0) 0 else it + 1 })
                        .takeWhile { it !is RichPdfBlock.PageBreakBlock }
                    val isSingleFreshText = blocksOnSamePage.size == 1 && block is RichPdfBlock.TextBlock
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedBlockId = block.id },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = if (!isSingleFreshText && isSelected) 1.5.dp else 1.dp,
                            color = if (!isSingleFreshText && isSelected) StudioCrimson else OutlineHairline
                        ),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (!isSingleFreshText) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (block) {
                                            is RichPdfBlock.TextBlock -> "পৃষ্ঠা $pageNum · ${block.customFontName ?: block.fontFamily.displayName}"
                                            is RichPdfBlock.ImageBlock -> "পৃষ্ঠা $pageNum · Photo"
                                            is RichPdfBlock.DividerBlock -> "পৃষ্ঠা $pageNum · Divider"
                                            is RichPdfBlock.PageBreakBlock -> "Page Break"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { blocks.removeAt(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            when (block) {
                                is RichPdfBlock.PageBreakBlock -> Unit
                                is RichPdfBlock.DividerBlock -> {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = Color(block.colorHex)
                                    )
                                }

                                is RichPdfBlock.ImageBlock -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val saved = withContext(Dispatchers.IO) {
                                                        prepareOverlayImageFile(context, block.imageUri)
                                                    } ?: return@launch
                                                    val (path, ratio) = saved
                                                    val overlay = DocumentSignature(
                                                        pageIndex = (pageNum - 1).coerceAtLeast(0),
                                                        imagePath = path,
                                                        xFraction = 0.08f,
                                                        yFraction = 0.22f,
                                                        widthFraction = block.widthFraction.coerceIn(0.20f, 0.85f),
                                                        aspectRatio = ratio,
                                                        kind = "image"
                                                    )
                                                    signatures.add(overlay)
                                                    selectedSigId = overlay.id
                                                    blocks.removeAt(index)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (language == AppLanguage.BENGALI) "ইচ্ছামতো সরান ও সাইজ করুন" else "Move & Resize Freely",
                                                color = StudioCobalt,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    AsyncImage(
                                        model = block.imageUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = block.caption,
                                        onValueChange = { cap ->
                                            blocks[index] = block.copy(caption = cap)
                                        },
                                        placeholder = {
                                            Text(if (language == AppLanguage.BENGALI) "ছবির ক্যাপশন..." else "Caption...")
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                is RichPdfBlock.TextBlock -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        if (!block.leadingIcon.isNullOrBlank()) {
                                            Text(
                                                text = block.leadingIcon,
                                                fontSize = block.fontSizeSp.sp,
                                                color = Color(block.colorHex),
                                                modifier = Modifier.padding(top = 14.dp, end = 8.dp)
                                            )
                                        }
                                        OutlinedTextField(
                                            value = block.text,
                                            onValueChange = { updated ->
                                                selectedBlockId = block.id
                                                blocks[index] = block.copy(text = updated)
                                            },
                                            placeholder = {
                                                Text(
                                                    if (language == AppLanguage.BENGALI)
                                                        "এখানে ইচ্ছামতো লিখুন — স্পেস দিয়ে সরান, এন্টার দিয়ে নিচে নামুন..."
                                                    else "Write freely anywhere — use Space to indent, Enter for new lines..."
                                                )
                                            },
                                            textStyle = TextStyle(
                                                fontFamily = when (block.fontFamily) {
                                                    RichFontFamily.BENGALI_SERIF, RichFontFamily.VRINDA, RichFontFamily.ENGLISH_SERIF, RichFontFamily.TIMES_NEW_ROMAN, RichFontFamily.GEORGIA -> FontFamily.Serif
                                                    RichFontFamily.MONOSPACE -> FontFamily.Monospace
                                                    else -> FontFamily.SansSerif
                                                },
                                                fontSize = block.fontSizeSp.sp,
                                                fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                                                fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                textDecoration = if (block.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                                color = Color(block.colorHex),
                                                textAlign = when (block.align) {
                                                    RichTextAlign.LEFT -> TextAlign.Start
                                                    RichTextAlign.CENTER -> TextAlign.Center
                                                    RichTextAlign.RIGHT -> TextAlign.End
                                                }
                                            ),
                                            minLines = if (isSingleFreshText) 12 else 3,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (signatures.isNotEmpty()) {
                    item {
                        SignaturePlacementBoard(
                            language = language,
                            docTitle = title,
                            blocks = blocks.toList(),
                            signatures = signatures,
                            selectedSigId = selectedSigId,
                            onSelectSig = { selectedSigId = it },
                            onUpdateSig = { updated ->
                                val sIdx = signatures.indexOfFirst { it.id == updated.id }
                                if (sIdx >= 0) signatures[sIdx] = updated
                            },
                            onDeleteSig = { id ->
                                signatures.removeAll { it.id == id }
                                if (selectedSigId == id) selectedSigId = null
                            },
                            onAddSignatureForPage = { pageIdx ->
                                signaturePadPageIndex = pageIdx
                            },
                            onAddPhotoForPage = { pageIdx ->
                                pendingOverlayTargetPage = pageIdx
                                pickImagesLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onReplacePhoto = { sigId ->
                                pendingReplaceSigId = sigId
                                replacePhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                }

                item {
                    val totalPages = 1 + blocks.count { it is RichPdfBlock.PageBreakBlock }
                    OutlinedButton(
                        onClick = { addNewPageAfter() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (language == AppLanguage.BENGALI)
                                "নিচে নতুন পেজ যোগ করুন (মোট পৃষ্ঠা: $totalPages)"
                            else "Add New Page Below (Total: $totalPages pages)"
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    signaturePadPageIndex?.let { pageIdx ->
        SignaturePadDialog(
            language = language,
            pageNumber = pageIdx + 1,
            onDismiss = { signaturePadPageIndex = null },
            onSignatureSaved = { path, ratio ->
                val newSig = DocumentSignature(
                    pageIndex = pageIdx,
                    imagePath = path,
                    xFraction = 0.56f,
                    yFraction = 0.72f,
                    widthFraction = 0.28f,
                    aspectRatio = ratio
                )
                signatures.add(newSig)
                selectedSigId = newSig.id
                signaturePadPageIndex = null
            }
        )
    }

    previewPdfFile?.let { file ->
        A4PdfPreviewDialog(
            pdfFile = file,
            title = fileName.ifBlank { "PDF Maker Studio" },
            language = language,
            onDownload = {
                previewPdfFile = null
                showDownloadFormatDialog = true
            },
            onDismiss = { previewPdfFile = null }
        )
    }

    readyExportState?.let { state ->
        PdfExportReadyDialog(
            state = state,
            language = language,
            isCloudBusy = isBusy,
            onSaveToCloud = {
                onUploadPdfToCloud(
                    state.savedDevicePdf.localCopyFile,
                    fileName.ifBlank { "PDF Maker Studio" },
                    state.pageCount
                )
            },
            onDismissKeepEditing = { readyExportState = null }
        )
    }

    if (showDownloadFormatDialog) {
        val targetName = fileName.ifBlank { "PDF Maker Studio" }
        AlertDialog(
            onDismissRequest = { showDownloadFormatDialog = false },
            title = {
                Text(
                    if (language == AppLanguage.BENGALI)
                        "ফরম্যাট বাছাই করে ডাউনলোড করুন"
                    else "Choose Export Format"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (language == AppLanguage.BENGALI)
                            "যে ভাষা ও ফন্ট দিয়েই লিখুন না কেন, কম্পিউটারে Microsoft Word-এ হুবহু ঠিক থাকবে।"
                        else "Preserves all languages, fonts, colors, spacing & layout for Desktop Microsoft Word.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val formats = listOf(
                        Triple("docx", if (language == AppLanguage.BENGALI) "Microsoft Word (.docx)" else "Microsoft Word (.docx)", if (language == AppLanguage.BENGALI) "কম্পিউটার Word-এ বাংলা/সব ভাষা ও ফন্ট হুবহু ঠিক থাকবে" else "Exact desktop Word fonts & Unicode layout"),
                        Triple("pdf", if (language == AppLanguage.BENGALI) "A4 PDF ফাইল (.pdf)" else "A4 PDF Document (.pdf)", if (language == AppLanguage.BENGALI) "প্রিন্ট ও শেয়ার করার জন্য সেরা A4 PDF" else "Universal print & share A4 PDF")
                    )

                    formats.forEach { (fmtKey, label, desc) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDownloadFormatDialog = false
                                    coroutineScope.launch {
                                        isBuildingPreview = true
                                        if (fmtKey == "pdf") {
                                            val res = pdfGenerator.createPdfFromRichBlocks(
                                                title = title.ifBlank { targetName },
                                                blocks = blocks.toList(),
                                                signatures = signatures.toList()
                                            )
                                            res.onSuccess { generated ->
                                                val saved = PdfDeviceSaver.savePdfToPhoneDownloads(
                                                    context = context,
                                                    sourceFile = generated.file,
                                                    requestedName = targetName
                                                )
                                                saved.onSuccess { savedPdf ->
                                                    onAutoSaveDraft?.invoke(
                                                        targetName,
                                                        title,
                                                        blocks.toList(),
                                                        signatures.toList()
                                                    )
                                                    readyExportState = LocalReadyPdfState(
                                                        sourceFile = generated.file,
                                                        savedDevicePdf = savedPdf,
                                                        pageCount = generated.pageCount,
                                                        sizeBytes = generated.sizeBytes
                                                    )
                                                }
                                            }
                                        } else {
                                            val exportFmt = WordExportFormat.DOCX
                                            val wordRes = WordDocumentCodec.exportDocumentToWordCacheFile(
                                                context = context,
                                                format = exportFmt,
                                                fileName = targetName,
                                                docTitle = title,
                                                blocks = blocks.toList(),
                                                signatures = signatures.toList()
                                            )
                                            wordRes.onSuccess { cacheFile ->
                                                val saved = PdfDeviceSaver.saveDocumentToPhoneDownloads(
                                                    context = context,
                                                    sourceFile = cacheFile,
                                                    requestedName = targetName,
                                                    mimeType = exportFmt.mimeType,
                                                    extension = exportFmt.extension
                                                )
                                                saved.onSuccess { savedDoc ->
                                                    onAutoSaveDraft?.invoke(
                                                        targetName,
                                                        title,
                                                        blocks.toList(),
                                                        signatures.toList()
                                                    )
                                                    readyWordExportState = SavedWordExportState(
                                                        savedFile = savedDoc,
                                                        format = exportFmt,
                                                        sizeBytes = cacheFile.length()
                                                    )
                                                }
                                            }
                                        }
                                        isBuildingPreview = false
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, OutlineHairline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (fmtKey == "pdf") Icons.Outlined.PictureAsPdf else Icons.Outlined.Article,
                                    contentDescription = null,
                                    tint = if (fmtKey == "pdf") StudioCrimson else StudioCobalt,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadFormatDialog = false }) {
                    Text(if (language == AppLanguage.BENGALI) "বন্ধ করুন" else "Close")
                }
            }
        )
    }

    readyWordExportState?.let { wordState ->
        AlertDialog(
            onDismissRequest = { readyWordExportState = null },
            icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = StudioEmerald) },
            title = {
                Text(
                    if (language == AppLanguage.BENGALI)
                        "ওয়ার্ড ফাইল ফোনে সেভ হয়েছে!"
                    else "Word File Saved to Phone!"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = wordState.savedFile.displayFileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val sz = Formatter.formatShortFileSize(context, wordState.sizeBytes)
                    Text(
                        text = "${wordState.savedFile.savedFolderLabel} · $sz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (language == AppLanguage.BENGALI)
                            "এই ফাইলটি কম্পিউটারের Microsoft Word-এ খুললে ফন্ট, ভাষা ও স্টাইল হুবহু ঠিক থাকবে।"
                        else "Designed to open cleanly on Desktop Microsoft Word with exact fonts and scripts.",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioEmerald
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        PdfDeviceSaver.openFileWithChooser(
                            context = context,
                            file = wordState.savedFile.localCopyFile,
                            mimeType = wordState.format.mimeType,
                            chooserTitle = wordState.savedFile.displayFileName
                        )
                    }
                ) {
                    Text(if (language == AppLanguage.BENGALI) "ফাইল খুলুন" else "Open")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            PdfDeviceSaver.shareFileWithChooser(
                                context = context,
                                file = wordState.savedFile.localCopyFile,
                                mimeType = wordState.format.mimeType,
                                displayName = wordState.savedFile.displayFileName
                            )
                        }
                    ) {
                        Text(if (language == AppLanguage.BENGALI) "শেয়ার" else "Share")
                    }
                    TextButton(onClick = { readyWordExportState = null }) {
                        Text(if (language == AppLanguage.BENGALI) "ঠিক আছে" else "Done")
                    }
                }
            }
        )
    }

    pendingWordImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { pendingWordImport = null },
            title = {
                Text(
                    if (language == AppLanguage.BENGALI)
                        "আপলোড করা ফাইল কীভাবে যোগ করবেন?"
                    else "How would you like to add this file?"
                )
            },
            text = {
                Text(
                    if (language == AppLanguage.BENGALI)
                        "\"${imported.suggestedFileName}\" (${imported.detectedScripts.joinToString(", ")}) এডিটরে খোলা হবে।"
                    else "Load \"${imported.suggestedFileName}\" (${imported.detectedScripts.joinToString(", ")}) into the editor."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        applyImportedWordDoc(imported, "append")
                        pendingWordImport = null
                    }
                ) {
                    Text(if (language == AppLanguage.BENGALI) "বর্তমান লেখার সাথে যোগ" else "Append")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            applyImportedWordDoc(imported, "replace")
                            pendingWordImport = null
                        }
                    ) {
                        Text(if (language == AppLanguage.BENGALI) "নতুন করে খুলুন" else "Replace")
                    }
                    TextButton(onClick = { pendingWordImport = null }) {
                        Text(if (language == AppLanguage.BENGALI) "বাতিল" else "Cancel")
                    }
                }
            }
        )
    }

    if (showCustomFontDialog) {
        AlertDialog(
            onDismissRequest = { showCustomFontDialog = false },
            title = {
                Text(if (language == AppLanguage.BENGALI) "কাস্টম Word ফন্ট নাম" else "Custom Word Font Name")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (language == AppLanguage.BENGALI)
                            "যেকোনো ফন্টের নাম লিখুন (যেমন: SutonnyMJ, SolaimanLipi, Nikosh, Calibri, Amiri):"
                        else "Enter any font name (e.g., SutonnyMJ, SolaimanLipi, Nikosh, Calibri, Amiri):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customFontDraftInput,
                        onValueChange = { customFontDraftInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = customFontDraftInput.trim()
                        if (cleaned.isNotEmpty()) {
                            val (mapped, _) = WordDocumentCodec.mapWordFontToStudio(cleaned)
                            updateSelectedText { it.copy(fontFamily = mapped, customFontName = cleaned) }
                        }
                        showCustomFontDialog = false
                    }
                ) {
                    Text(if (language == AppLanguage.BENGALI) "সেট করুন" else "Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomFontDialog = false }) {
                    Text(if (language == AppLanguage.BENGALI) "বাতিল" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun SignaturePlacementBoard(
    language: AppLanguage,
    docTitle: String,
    blocks: List<RichPdfBlock>,
    signatures: List<DocumentSignature>,
    selectedSigId: String?,
    onSelectSig: (String?) -> Unit,
    onUpdateSig: (DocumentSignature) -> Unit,
    onDeleteSig: (String) -> Unit,
    onAddSignatureForPage: (Int) -> Unit,
    onAddPhotoForPage: (Int) -> Unit,
    onReplacePhoto: (String) -> Unit
) {
    val pagesWithSigs = signatures.map { it.pageIndex }.distinct().sorted()
    val density = LocalDensity.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        pagesWithSigs.forEach { pageIdx ->
            val pageSigs = signatures.filter { it.pageIndex == pageIdx }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, OutlineHairline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "ছবি ও স্বাক্ষর বসান ও সাইজ ঠিক করুন · পৃষ্ঠা ${pageIdx + 1}"
                                else "Position & Resize Photos & Signatures · Page ${pageIdx + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "আঙুল দিয়ে টেনে পেজের যেকোনো স্থানে বসান, কোণায় ধরে ছোট-বড় করুন"
                                else "Drag anywhere on the A4 sheet; drag corner handle or pinch to resize",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(
                                onClick = { onAddPhotoForPage(pageIdx) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == AppLanguage.BENGALI) "+ ছবি" else "+ Photo")
                            }
                            TextButton(
                                onClick = { onAddSignatureForPage(pageIdx) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Outlined.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == AppLanguage.BENGALI) "+ স্বাক্ষর" else "+ Sign")
                            }
                        }
                    }

                    // True A4 Paper Interactive Drag & Resize Surface
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(210f / 297f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(1.dp, OutlineHairline, RoundedCornerShape(10.dp))
                    ) {
                        val sheetWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                        val sheetHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

                        // Lightweight background text guide so user sees where text sits on this page
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (pageIdx == 0 && docTitle.isNotBlank()) {
                                Text(
                                    text = docTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155).copy(alpha = 0.65f)
                                )
                                HorizontalDivider(color = Color(0xFFE2E8F0))
                            }
                            val pageTextPreview = remember(blocks, pageIdx) {
                                var cur = 0
                                buildString {
                                    blocks.forEach { b ->
                                        if (b is RichPdfBlock.PageBreakBlock) cur++
                                        else if (cur == pageIdx && b is RichPdfBlock.TextBlock && b.text.isNotBlank()) {
                                            appendLine(b.text)
                                        }
                                    }
                                }
                            }
                            if (pageTextPreview.isNotBlank()) {
                                Text(
                                    text = pageTextPreview.take(500),
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp,
                                    color = Color(0xFF475569).copy(alpha = 0.55f)
                                )
                            }
                        }

                        val orderedSigs = pageSigs.sortedBy { if (it.widthFraction >= 0.94f) 0 else 1 }
                        orderedSigs.forEach { sig ->
                            val isSelected = selectedSigId == sig.id
                            val isPhoto = sig.kind == "image"
                            val isFullSheet = sig.widthFraction >= 0.94f
                            val accentColor = if (isPhoto) Color(0xFF0284C7) else Color(0xFF4F46E5)
                            val mutedBorder = if (isPhoto) Color(0xFF38BDF8) else Color(0xFF818CF8)

                            val sigBmp = remember(sig.imagePath) {
                                decodeSampledOverlayBitmap(sig.imagePath, 1280, 1280)?.asImageBitmap()
                            }
                            val clampedW = sig.widthFraction.coerceIn(0.08f, 1.0f)
                            val clampedRatio = sig.aspectRatio.coerceIn(0.15f, 2.5f)
                            val boxWidthDp = maxWidth * clampedW
                            val xPx = (sig.xFraction * sheetWidthPx).roundToInt()
                            val yPx = (sig.yFraction * sheetHeightPx).roundToInt()

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(xPx, yPx) }
                                    .width(boxWidthDp)
                                    .aspectRatio(1f / clampedRatio)
                                    .border(
                                        width = if (isSelected) 2.dp else if (isFullSheet) 0.dp else 1.2.dp,
                                        color = if (isSelected) accentColor else if (isFullSheet) Color.Transparent else mutedBorder,
                                        shape = RoundedCornerShape(if (isFullSheet) 2.dp else 6.dp)
                                    )
                                    .background(
                                        if (isSelected) accentColor.copy(alpha = 0.06f) else Color.Transparent,
                                        RoundedCornerShape(if (isFullSheet) 2.dp else 6.dp)
                                    )
                                    .pointerInput(sig.id, sheetWidthPx, sheetHeightPx) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            onSelectSig(sig.id)
                                            val nextW = (sig.widthFraction * zoom).coerceIn(0.08f, 1.0f)
                                            val full = nextW >= 0.94f
                                            val minBound = if (full) 0f else 0.01f
                                            val estHFrac = minOf(1.0f, nextW * clampedRatio * (210f / 297f))
                                            val maxX = (1.0f - nextW).coerceAtLeast(0f)
                                            val maxY = (1.0f - estHFrac).coerceAtLeast(0f)
                                            val nextX = (sig.xFraction + pan.x / sheetWidthPx).coerceIn(minBound, maxX.coerceAtLeast(minBound))
                                            val nextY = (sig.yFraction + pan.y / sheetHeightPx).coerceIn(minBound, maxY.coerceAtLeast(minBound))
                                            onUpdateSig(sig.copy(xFraction = nextX, yFraction = nextY, widthFraction = nextW))
                                        }
                                    }
                                    .clickable { onSelectSig(sig.id) }
                            ) {
                                if (sigBmp != null) {
                                    Image(
                                        bitmap = sigBmp,
                                        contentDescription = if (isPhoto) "Photo" else "Signature",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                if (isSelected) {
                                    Surface(
                                        color = accentColor,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(3.dp)
                                    ) {
                                        Text(
                                            text = if (isFullSheet) {
                                                if (language == AppLanguage.BENGALI) "অরিজিনাল শিট" else "1:1 Sheet"
                                            } else if (isPhoto) {
                                                if (language == AppLanguage.BENGALI) "ছবি" else "Photo"
                                            } else {
                                                if (language == AppLanguage.BENGALI) "স্বাক্ষর" else "Sign"
                                            },
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                // Corner Resize Drag Handle
                                if (!isFullSheet || isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(accentColor)
                                            .pointerInput(sig.id, sheetWidthPx) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    onSelectSig(sig.id)
                                                    val deltaW = dragAmount.x / sheetWidthPx
                                                    val nextW = (sig.widthFraction + deltaW).coerceIn(0.08f, 1.0f)
                                                    val nextX = sig.xFraction.coerceAtMost((1.0f - nextW).coerceAtLeast(0f))
                                                    onUpdateSig(sig.copy(widthFraction = nextW, xFraction = nextX))
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.OpenInFull,
                                            contentDescription = "Resize",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Selected overlay fine size & delete row
                    val activeSig = pageSigs.firstOrNull { it.id == selectedSigId } ?: pageSigs.firstOrNull()
                    if (activeSig != null) {
                        val activeIsPhoto = activeSig.kind == "image"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        onUpdateSig(activeSig.copy(widthFraction = (activeSig.widthFraction - 0.05f).coerceAtLeast(0.08f)))
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(if (language == AppLanguage.BENGALI) "ছোট -" else "Smaller -")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val nextW = (activeSig.widthFraction + 0.05f).coerceAtMost(1.0f)
                                        val nextX = activeSig.xFraction.coerceAtMost((1.0f - nextW).coerceAtLeast(0f))
                                        onUpdateSig(activeSig.copy(widthFraction = nextW, xFraction = nextX))
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(if (language == AppLanguage.BENGALI) "বড় +" else "Larger +")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (activeIsPhoto) {
                                    TextButton(
                                        onClick = { onReplacePhoto(activeSig.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = StudioCobalt, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) "বদলান" else "Replace",
                                            color = StudioCobalt
                                        )
                                    }
                                }
                                TextButton(onClick = { onDeleteSig(activeSig.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = StudioCrimson, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (activeIsPhoto) {
                                            if (language == AppLanguage.BENGALI) "ছবি সরান" else "Remove Photo"
                                        } else {
                                            if (language == AppLanguage.BENGALI) "স্বাক্ষর সরান" else "Remove Sign"
                                        },
                                        color = StudioCrimson
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun prepareOverlayImageFile(context: Context, uri: Uri): Pair<String, Float>? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1600 && bounds.outHeight / (sample * 2) >= 1600) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        } ?: return@runCatching null

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        var rotatedNeeded = true
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> rotatedNeeded = false
        }
        if (rotatedNeeded) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated != bmp) bmp.recycle()
            bmp = rotated
        }

        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide > 1600) {
            val scale = 1600f / maxSide.toFloat()
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled != bmp) bmp.recycle()
            bmp = scaled
        }

        val ratio = (bmp.height.toFloat() / bmp.width.coerceAtLeast(1).toFloat()).coerceIn(0.15f, 2.5f)
        val outDir = File(context.filesDir, "editor_overlays").apply { mkdirs() }
        val usePng = bmp.hasAlpha()
        val ext = if (usePng) "png" else "jpg"
        val outFile = File(outDir, "ovl_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")
        FileOutputStream(outFile).use { fos ->
            if (usePng) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            } else {
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
        }
        bmp.recycle()
        outFile.absolutePath to ratio
    }.getOrNull()
}

private fun decodeSampledOverlayBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqWidth && bounds.outHeight / (sample * 2) >= reqHeight) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}
