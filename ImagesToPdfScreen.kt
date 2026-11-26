package com.pdfmakerstudio.app.ui.editor

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.RotateRight
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pdfmakerstudio.app.data.pdf.A4PdfGenerator
import com.pdfmakerstudio.app.data.pdf.DocumentScannerUtils
import com.pdfmakerstudio.app.data.pdf.ImageInspectionInfo
import com.pdfmakerstudio.app.data.pdf.NormalizedPoint
import com.pdfmakerstudio.app.data.pdf.PdfDeviceSaver
import com.pdfmakerstudio.app.data.pdf.ScanFilterMode
import com.pdfmakerstudio.app.data.pdf.ScanQuad
import com.pdfmakerstudio.app.domain.model.GlobalImagePdfOptions
import com.pdfmakerstudio.app.domain.model.ImageMarginMode
import com.pdfmakerstudio.app.domain.model.ImagePageItem
import com.pdfmakerstudio.app.domain.model.ImageResolutionPreset
import com.pdfmakerstudio.app.domain.model.ImageSizeMode
import com.pdfmakerstudio.app.domain.model.PageTextPlacement
import com.pdfmakerstudio.app.domain.model.RichFontFamily
import com.pdfmakerstudio.app.domain.model.RichTextAlign
import com.pdfmakerstudio.app.ui.strings.AppLanguage
import com.pdfmakerstudio.app.ui.theme.OutlineHairline
import com.pdfmakerstudio.app.ui.theme.StudioCobalt
import com.pdfmakerstudio.app.ui.theme.StudioCrimson
import kotlinx.coroutines.launch
import java.io.File

private val PAGE_TEXT_COLORS = listOf(
    0xFF0F172AL,
    0xFF475569L,
    0xFFD92D20L,
    0xFF1D4ED8L,
    0xFF047857L,
    0xFFB45309L,
    0xFF6D28D9L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(
    language: AppLanguage,
    pdfGenerator: A4PdfGenerator,
    initialFileName: String = "PDF Maker Studio",
    initialPages: List<ImagePageItem> = emptyList(),
    initialOptions: GlobalImagePdfOptions = GlobalImagePdfOptions(),
    isBusy: Boolean,
    onBack: () -> Unit,
    onSaveDraft: (fileName: String, pages: List<ImagePageItem>, options: GlobalImagePdfOptions) -> Unit,
    onAutoSaveDraft: ((fileName: String, pages: List<ImagePageItem>, options: GlobalImagePdfOptions) -> Unit)? = null,
    onUploadPdfToCloud: (pdfFile: File, fileName: String, pageCount: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var readyExportState by remember { mutableStateOf<LocalReadyPdfState?>(null) }
    var isBuildingPreview by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf(initialFileName.ifBlank { "PDF Maker Studio" }) }
    val pages = remember { mutableStateListOf<ImagePageItem>().apply { addAll(initialPages) } }
    val inspections = remember { mutableStateMapOf<String, ImageInspectionInfo>() }
    val rawOriginalUriByPageId = remember { mutableStateMapOf<String, Uri>() }
    val quadByPageId = remember { mutableStateMapOf<String, ScanQuad>() }
    var cropTargetPage by remember { mutableStateOf<ImagePageItem?>(null) }

    var globalSizeMode by remember { mutableStateOf(ImageSizeMode.AUTO_FIT) }
    var globalScalePct by remember { mutableIntStateOf(85) }
    var globalResolution by remember { mutableStateOf(ImageResolutionPreset.MP_2) }
    var globalCustomMaxPx by remember { mutableIntStateOf(1600) }
    var globalQuality by remember { mutableFloatStateOf(80f) }
    var marginMode by remember { mutableStateOf(initialOptions.marginMode) }
    var targetMaxMb by remember { mutableStateOf(initialOptions.targetMaxMb) }

    var showResMenu by remember { mutableStateOf(false) }
    var showTargetMbMenu by remember { mutableStateOf(false) }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newItems = uris.map { uri ->
                ImagePageItem(
                    imageUri = uri,
                    sizeMode = globalSizeMode,
                    customScalePercent = globalScalePct,
                    resolutionPreset = globalResolution,
                    customMaxDimPx = globalCustomMaxPx,
                    jpegQuality = globalQuality.toInt()
                )
            }
            pages.addAll(newItems)
        }
    }

    LaunchedEffect(pages.toList()) {
        pages.forEach { item ->
            val info = pdfGenerator.inspectImageSummary(
                uri = item.imageUri,
                preset = item.resolutionPreset,
                customMaxPx = item.customMaxDimPx,
                quality = item.jpegQuality
            )
            inspections[item.id] = info
        }
    }

    fun currentOptions() = GlobalImagePdfOptions(
        marginMode = marginMode,
        targetMaxMb = targetMaxMb,
        showPageNumbers = true
    )

    LaunchedEffect(fileName, pages.toList(), marginMode, targetMaxMb) {
        if (pages.isNotEmpty()) {
            kotlinx.coroutines.delay(1500L)
            onAutoSaveDraft?.invoke(
                fileName.ifBlank { "PDF Maker Studio" },
                pages.toList(),
                currentOptions()
            )
        }
    }

    fun handleBackWithAutoSave() {
        if (pages.isNotEmpty()) {
            onSaveDraft(
                fileName.ifBlank { "PDF Maker Studio" },
                pages.toList(),
                currentOptions()
            )
        } else {
            onBack()
        }
    }

    BackHandler(enabled = !isBusy) {
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
                        text = if (language == AppLanguage.BENGALI) "ছবি → A4 পিডিএফ স্টুডিও" else "Images to A4 Studio",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ProjectTopControlPanel(
                language = language,
                fileName = fileName,
                onFileNameChange = { fileName = it },
                hasContent = pages.isNotEmpty(),
                isBusy = isBusy || isBuildingPreview,
                onPreview = {
                    coroutineScope.launch {
                        isBuildingPreview = true
                        val res = pdfGenerator.createPdfFromConfiguredImages(
                            pages = pages.toList(),
                            globalOptions = currentOptions(),
                            fileNamePrefix = fileName.ifBlank { "PDF_Maker_Studio" }
                        )
                        res.onSuccess { generated -> previewPdfFile = generated.file }
                        isBuildingPreview = false
                    }
                },
                onDownloadPdf = {
                    coroutineScope.launch {
                        isBuildingPreview = true
                        val targetName = fileName.ifBlank { "PDF Maker Studio" }
                        val res = pdfGenerator.createPdfFromConfiguredImages(
                            pages = pages.toList(),
                            globalOptions = currentOptions(),
                            fileNamePrefix = targetName
                        )
                        res.onSuccess { generated ->
                            val saved = PdfDeviceSaver.savePdfToPhoneDownloads(
                                context = context,
                                sourceFile = generated.file,
                                requestedName = targetName
                            )
                            saved.onSuccess { savedPdf ->
                                onAutoSaveDraft?.invoke(targetName, pages.toList(), currentOptions())
                                readyExportState = LocalReadyPdfState(
                                    sourceFile = generated.file,
                                    savedDevicePdf = savedPdf,
                                    pageCount = generated.pageCount,
                                    sizeBytes = generated.sizeBytes
                                )
                            }
                        }
                        isBuildingPreview = false
                    }
                }
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

            // Global Sizing, Megapixel & MB Compression Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, OutlineHairline),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI)
                                "প্রফেশনাল কোয়ালিটি ও মেগাপিক্সেল প্রিসেট"
                            else "Pro Export Quality & Megapixels",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple(if (language == AppLanguage.BENGALI) "প্রিন্ট HD" else "Print HD", ImageResolutionPreset.ORIGINAL, 95),
                                Triple(if (language == AppLanguage.BENGALI) "ডকুমেন্ট ৫ MP" else "Doc 5 MP", ImageResolutionPreset.MP_5, 85),
                                Triple(if (language == AppLanguage.BENGALI) "ব্যালেন্সড ২ MP" else "Balanced 2 MP", ImageResolutionPreset.MP_2, 80),
                                Triple(if (language == AppLanguage.BENGALI) "কম্প্যাক্ট ১ MP" else "Compact 1 MP", ImageResolutionPreset.MP_1, 60)
                            ).forEach { (label, presetRes, presetQ) ->
                                val selected = globalResolution == presetRes && globalQuality.toInt() == presetQ
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        globalResolution = presetRes
                                        globalQuality = presetQ.toFloat()
                                        for (i in pages.indices) {
                                            pages[i] = pages[i].copy(
                                                resolutionPreset = presetRes,
                                                jpegQuality = presetQ
                                            )
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        // Auto / Fill / Custom Size Mode
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ImageSizeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = globalSizeMode == mode,
                                    onClick = {
                                        globalSizeMode = mode
                                        for (i in pages.indices) {
                                            pages[i] = pages[i].copy(sizeMode = mode)
                                        }
                                    },
                                    label = {
                                        Text(if (language == AppLanguage.BENGALI) mode.labelBn else mode.labelEn)
                                    }
                                )
                            }
                        }

                        if (globalSizeMode == ImageSizeMode.CUSTOM_SCALE) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${globalScalePct}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(48.dp)
                                )
                                Slider(
                                    value = globalScalePct.toFloat(),
                                    onValueChange = { v ->
                                        globalScalePct = v.toInt()
                                        for (i in pages.indices) {
                                            pages[i] = pages[i].copy(customScalePercent = globalScalePct)
                                        }
                                    },
                                    valueRange = 20f..100f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Resolution / Megapixel Dropdown + Max PDF MB Cap
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showResMenu = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (language == AppLanguage.BENGALI) globalResolution.labelBn
                                        else globalResolution.labelEn
                                    )
                                }
                                DropdownMenu(expanded = showResMenu, onDismissRequest = { showResMenu = false }) {
                                    ImageResolutionPreset.entries.forEach { preset ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(if (language == AppLanguage.BENGALI) preset.labelBn else preset.labelEn)
                                            },
                                            onClick = {
                                                showResMenu = false
                                                globalResolution = preset
                                                for (i in pages.indices) {
                                                    pages[i] = pages[i].copy(resolutionPreset = preset)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showTargetMbMenu = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        targetMaxMb?.let { "Cap: ${it} MB" }
                                            ?: if (language == AppLanguage.BENGALI) "টার্গেট MB: অটো" else "Max MB: Auto"
                                    )
                                }
                                DropdownMenu(expanded = showTargetMbMenu, onDismissRequest = { showTargetMbMenu = false }) {
                                    listOf(null, 0.5f, 1f, 2f, 5f).forEach { cap ->
                                        DropdownMenuItem(
                                            text = { Text(cap?.let { "< $it MB" } ?: "No Cap (Auto)") },
                                            onClick = {
                                                showTargetMbMenu = false
                                                targetMaxMb = cap
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (globalResolution == ImageResolutionPreset.CUSTOM_PX) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${globalCustomMaxPx}px",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(60.dp)
                                )
                                Slider(
                                    value = globalCustomMaxPx.toFloat(),
                                    onValueChange = { v ->
                                        globalCustomMaxPx = v.toInt()
                                        for (i in pages.indices) {
                                            pages[i] = pages[i].copy(customMaxDimPx = globalCustomMaxPx)
                                        }
                                    },
                                    valueRange = 360f..3840f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Quality / MB Slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "কোয়ালিটি: ${globalQuality.toInt()}%"
                                else "Quality: ${globalQuality.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(105.dp)
                            )
                            Slider(
                                value = globalQuality,
                                onValueChange = { v ->
                                    globalQuality = v
                                    for (i in pages.indices) {
                                        pages[i] = pages[i].copy(jpegQuality = v.toInt())
                                    }
                                },
                                valueRange = 15f..100f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Margins
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ImageMarginMode.entries.forEach { m ->
                                FilterChip(
                                    selected = marginMode == m,
                                    onClick = { marginMode = m },
                                    label = { Text(if (language == AppLanguage.BENGALI) m.labelBn else m.labelEn) }
                                )
                            }
                        }
                    }
                }
            }

            // Add Photos Button
            item {
                OutlinedButton(
                    onClick = {
                        pickImagesLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (language == AppLanguage.BENGALI)
                            "+ ছবি নির্বাচন করুন (${pages.size} টি পেজ)"
                        else "+ Select Photos (${pages.size} pages)"
                    )
                }
            }

            // Per-Page Cards with Write Text on Paper
            itemsIndexed(pages, key = { _, p -> p.id }) { idx, page ->
                val info = inspections[page.id]
                var fontMenuOpen by remember(page.id) { mutableStateOf(false) }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, OutlineHairline),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
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
                            Column {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) "পেজ ${idx + 1}" else "Page ${idx + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (info != null) {
                                    Text(
                                        text = "${info.outputWidth}×${info.outputHeight}px · ${Formatter.formatShortFileSize(context, info.estimatedBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        val rawUri = rawOriginalUriByPageId.getOrPut(page.id) { page.imageUri }
                                        cropTargetPage = page.copy(imageUri = rawUri)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Crop,
                                        contentDescription = "Adjust corners",
                                        tint = StudioCobalt,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) "কোণা" else "Corners",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StudioCobalt
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        pages[idx] = page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360)
                                    }
                                ) {
                                    Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate")
                                }
                                IconButton(onClick = { pages.removeAt(idx) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove")
                                }
                            }
                        }

                        AsyncImage(
                            model = page.imageUri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF8FAFC))
                                .rotate(page.rotationDegrees.toFloat())
                        )

                        HorizontalDivider()

                        Text(
                            text = if (language == AppLanguage.BENGALI)
                                "এই PDF পেপারে লিখুন (ছবির উপরে / নিচে)"
                            else "Write Text on This A4 Page",
                            style = MaterialTheme.typography.labelLarge
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PageTextPlacement.entries.forEach { place ->
                                FilterChip(
                                    selected = page.textPlacement == place,
                                    onClick = { pages[idx] = page.copy(textPlacement = place) },
                                    label = {
                                        Text(if (language == AppLanguage.BENGALI) place.labelBn else place.labelEn)
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = page.pageText,
                            onValueChange = { pages[idx] = page.copy(pageText = it) },
                            placeholder = {
                                Text(
                                    if (language == AppLanguage.BENGALI)
                                        "এখানে বাংলা বা ইংরেজি ক্যাপশন / নোট লিখুন..."
                                    else "Write caption or note on this sheet..."
                                )
                            },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Per-page Text Style Row (Font, Bold, Italic, Align, Colors)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                FilterChip(
                                    selected = false,
                                    onClick = { fontMenuOpen = true },
                                    label = { Text(page.textFontFamily.displayName) }
                                )
                                DropdownMenu(expanded = fontMenuOpen, onDismissRequest = { fontMenuOpen = false }) {
                                    RichFontFamily.entries.forEach { ff ->
                                        DropdownMenuItem(
                                            text = { Text(ff.displayName) },
                                            onClick = {
                                                fontMenuOpen = false
                                                pages[idx] = page.copy(textFontFamily = ff)
                                            }
                                        )
                                    }
                                }
                            }

                            FilterChip(
                                selected = page.isBold,
                                onClick = { pages[idx] = page.copy(isBold = !page.isBold) },
                                label = { Icon(Icons.Outlined.FormatBold, contentDescription = "Bold", modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = page.isItalic,
                                onClick = { pages[idx] = page.copy(isItalic = !page.isItalic) },
                                label = { Icon(Icons.Outlined.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(16.dp)) }
                            )

                            IconButton(onClick = { pages[idx] = page.copy(textAlign = RichTextAlign.LEFT) }) {
                                Icon(Icons.AutoMirrored.Outlined.FormatAlignLeft, contentDescription = "Left")
                            }
                            IconButton(onClick = { pages[idx] = page.copy(textAlign = RichTextAlign.CENTER) }) {
                                Icon(Icons.Outlined.FormatAlignCenter, contentDescription = "Center")
                            }
                            IconButton(onClick = { pages[idx] = page.copy(textAlign = RichTextAlign.RIGHT) }) {
                                Icon(Icons.AutoMirrored.Outlined.FormatAlignRight, contentDescription = "Right")
                            }

                            PAGE_TEXT_COLORS.forEach { hex ->
                                val chosen = page.textColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            width = if (chosen) 2.5.dp else 1.dp,
                                            color = if (chosen) StudioCrimson else OutlineHairline,
                                            shape = CircleShape
                                        )
                                        .clickable { pages[idx] = page.copy(textColorHex = hex) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewPdfFile?.let { file ->
        A4PdfPreviewDialog(
            pdfFile = file,
            title = fileName.ifBlank { "PDF Maker Studio" },
            language = language,
            onDismiss = { previewPdfFile = null }
        )
    }

    cropTargetPage?.let { target ->
        val rawUri = rawOriginalUriByPageId[target.id] ?: target.imageUri
        val initialQuad = quadByPageId[target.id] ?: ScanQuad(
            NormalizedPoint(0.03f, 0.03f),
            NormalizedPoint(0.97f, 0.03f),
            NormalizedPoint(0.97f, 0.97f),
            NormalizedPoint(0.03f, 0.97f)
        )
        CornerAdjustWithMagnifierDialog(
            imageUri = rawUri,
            initialQuad = initialQuad,
            language = language,
            onDismiss = { cropTargetPage = null },
            onApply = { updatedQuad ->
                cropTargetPage = null
                quadByPageId[target.id] = updatedQuad
                coroutineScope.launch {
                    isBuildingPreview = true
                    runCatching {
                        DocumentScannerUtils.warpAndEnhanceScan(
                            context = context,
                            sourceUri = rawUri,
                            quad = updatedQuad,
                            mode = ScanFilterMode.ORIGINAL,
                            rotationDeg = 0
                        )
                    }.onSuccess { res ->
                        val pIdx = pages.indexOfFirst { it.id == target.id }
                        if (pIdx >= 0) {
                            pages[pIdx] = pages[pIdx].copy(
                                imageUri = res.outputUri,
                                originalWidth = res.width,
                                originalHeight = res.height,
                                rotationDegrees = 0
                            )
                        }
                    }
                    isBuildingPreview = false
                }
            }
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
}
