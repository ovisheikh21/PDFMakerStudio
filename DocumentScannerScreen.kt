package com.pdfmakerstudio.app.ui.editor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.pdfmakerstudio.app.data.pdf.A4PdfGenerator
import com.pdfmakerstudio.app.data.pdf.DocumentScannerUtils
import com.pdfmakerstudio.app.data.pdf.NormalizedPoint
import com.pdfmakerstudio.app.data.pdf.PdfDeviceSaver
import com.pdfmakerstudio.app.data.pdf.ScanFilterMode
import com.pdfmakerstudio.app.data.pdf.ScanQuad
import com.pdfmakerstudio.app.domain.model.GlobalImagePdfOptions
import com.pdfmakerstudio.app.domain.model.ImageMarginMode
import com.pdfmakerstudio.app.domain.model.ImagePageItem
import com.pdfmakerstudio.app.domain.model.ImageResolutionPreset
import com.pdfmakerstudio.app.domain.model.ImageSizeMode
import com.pdfmakerstudio.app.ui.strings.AppLanguage
import com.pdfmakerstudio.app.ui.theme.DangerRed
import com.pdfmakerstudio.app.ui.theme.OutlineHairline
import com.pdfmakerstudio.app.ui.theme.StudioCobalt
import com.pdfmakerstudio.app.ui.theme.StudioCrimson
import com.pdfmakerstudio.app.ui.theme.StudioEmerald
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

data class ScannedPageEntry(
    val id: String = UUID.randomUUID().toString(),
    val originalUri: Uri,
    val processedUri: Uri,
    val quad: ScanQuad,
    val filterMode: ScanFilterMode = ScanFilterMode.MAGIC,
    val rotationDeg: Int = 0
)

private val ScannedPageListSaver = listSaver<SnapshotStateList<ScannedPageEntry>, String>(
    save = { list ->
        list.map { e ->
            listOf(
                e.id,
                e.originalUri.toString(),
                e.processedUri.toString(),
                e.quad.tl.x.toString(),
                e.quad.tl.y.toString(),
                e.quad.tr.x.toString(),
                e.quad.tr.y.toString(),
                e.quad.br.x.toString(),
                e.quad.br.y.toString(),
                e.quad.bl.x.toString(),
                e.quad.bl.y.toString(),
                e.filterMode.name,
                e.rotationDeg.toString()
            ).joinToString("|")
        }
    },
    restore = { saved ->
        val out = mutableStateListOf<ScannedPageEntry>()
        saved.forEach { row ->
            val p = row.split("|")
            if (p.size >= 13) {
                runCatching {
                    ScannedPageEntry(
                        id = p[0],
                        originalUri = Uri.parse(p[1]),
                        processedUri = Uri.parse(p[2]),
                        quad = ScanQuad(
                            tl = NormalizedPoint(p[3].toFloat(), p[4].toFloat()),
                            tr = NormalizedPoint(p[5].toFloat(), p[6].toFloat()),
                            br = NormalizedPoint(p[7].toFloat(), p[8].toFloat()),
                            bl = NormalizedPoint(p[9].toFloat(), p[10].toFloat())
                        ),
                        filterMode = runCatching { ScanFilterMode.valueOf(p[11]) }
                            .getOrDefault(ScanFilterMode.MAGIC),
                        rotationDeg = p[12].toIntOrNull() ?: 0
                    )
                }.getOrNull()?.let { out.add(it) }
            }
        }
        out
    }
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    language: AppLanguage,
    pdfGenerator: A4PdfGenerator,
    isCloudBusy: Boolean,
    onBack: () -> Unit,
    onSaveDraft: (fileName: String, pages: List<ImagePageItem>, options: GlobalImagePdfOptions) -> Unit,
    onAutoSaveDraft: ((fileName: String, pages: List<ImagePageItem>, options: GlobalImagePdfOptions) -> Unit)? = null,
    onUploadPdfToCloud: (pdfFile: File, fileName: String, pageCount: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var docName by rememberSaveable { mutableStateOf("PDF Maker Studio") }
    val scans = rememberSaveable(saver = ScannedPageListSaver) { mutableStateListOf<ScannedPageEntry>() }
    var isProcessingScan by remember { mutableStateOf(false) }
    var isBuildingPdf by remember { mutableStateOf(false) }
    var pendingCaptureUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    var showInAppCameraX by rememberSaveable { mutableStateOf(false) }

    var cropTarget by remember { mutableStateOf<ScannedPageEntry?>(null) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var readyExportState by remember { mutableStateOf<LocalReadyPdfState?>(null) }

    fun buildConfiguredPages(): List<ImagePageItem> = scans.map { entry ->
        ImagePageItem(
            id = entry.id,
            imageUri = entry.processedUri,
            sizeMode = ImageSizeMode.AUTO_FIT,
            customScalePercent = 96,
            resolutionPreset = ImageResolutionPreset.MP_5,
            customMaxDimPx = 1900,
            jpegQuality = 90
        )
    }

    fun scanOptions() = GlobalImagePdfOptions(
        marginMode = ImageMarginMode.NARROW,
        targetMaxMb = null,
        showPageNumbers = scans.size > 1
    )

    LaunchedEffect(docName, scans.toList()) {
        if (scans.isNotEmpty()) {
            delay(1500L)
            onAutoSaveDraft?.invoke(
                docName.ifBlank { "PDF Maker Studio" },
                buildConfiguredPages(),
                scanOptions()
            )
        }
    }

    suspend fun processNewSourceUris(uris: List<Uri>, quadOverrides: Map<Uri, ScanQuad> = emptyMap()) {
        if (uris.isEmpty()) return
        isProcessingScan = true
        try {
            for (uri in uris) {
                val detected = quadOverrides[uri] ?: runCatching {
                    DocumentScannerUtils.detectDocumentQuad(context, uri)
                }.getOrDefault(ScanQuad())

                val processed = runCatching {
                    DocumentScannerUtils.warpAndEnhanceScan(
                        context = context,
                        sourceUri = uri,
                        quad = detected,
                        filterMode = ScanFilterMode.MAGIC,
                        rotationDeg = 0
                    )
                }
                processed.onSuccess { res ->
                    scans.add(
                        ScannedPageEntry(
                            originalUri = uri,
                            processedUri = res.outputUri,
                            quad = detected,
                            filterMode = ScanFilterMode.MAGIC,
                            rotationDeg = 0
                        )
                    )
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Scan failed")
                }
            }
        } finally {
            isProcessingScan = false
        }
    }

    suspend fun reprocessEntry(
        entry: ScannedPageEntry,
        newQuad: ScanQuad = entry.quad,
        newFilter: ScanFilterMode = entry.filterMode,
        newRotation: Int = entry.rotationDeg
    ) {
        isProcessingScan = true
        try {
            val res = DocumentScannerUtils.warpAndEnhanceScan(
                context = context,
                sourceUri = entry.originalUri,
                quad = newQuad,
                filterMode = newFilter,
                rotationDeg = newRotation
            )
            val idx = scans.indexOfFirst { it.id == entry.id }
            if (idx >= 0) {
                scans[idx] = entry.copy(
                    processedUri = res.outputUri,
                    quad = newQuad,
                    filterMode = newFilter,
                    rotationDeg = ((newRotation % 360) + 360) % 360
                )
            }
        } catch (err: Throwable) {
            snackbarHostState.showSnackbar(err.localizedMessage ?: "Processing failed")
        } finally {
            isProcessingScan = false
        }
    }

    suspend fun addPreStraightenedMlKitUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        isProcessingScan = true
        try {
            val edgeMarginQuad = ScanQuad(
                NormalizedPoint(0.01f, 0.01f),
                NormalizedPoint(0.99f, 0.01f),
                NormalizedPoint(0.99f, 0.99f),
                NormalizedPoint(0.01f, 0.99f)
            )
            for (uri in uris) {
                val imported = runCatching {
                    DocumentScannerUtils.importPreStraightenedScanUri(context, uri)
                }
                imported.onSuccess { res ->
                    scans.add(
                        ScannedPageEntry(
                            originalUri = res.outputUri,
                            processedUri = res.outputUri,
                            quad = edgeMarginQuad,
                            filterMode = ScanFilterMode.ORIGINAL,
                            rotationDeg = 0
                        )
                    )
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Scan import failed")
                }
            }
        } finally {
            isProcessingScan = false
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uriStr = pendingCaptureUriStr
        pendingCaptureUriStr = null
        if (success && !uriStr.isNullOrBlank()) {
            scope.launch { processNewSourceUris(listOf(Uri.parse(uriStr))) }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showInAppCameraX = true
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (language == AppLanguage.BENGALI)
                        "ক্যামেরা পারমিশন না থাকায় গ্যালারি থেকে ছবি নির্বাচন করুন।"
                    else "Camera permission denied. Please choose from gallery."
                )
            }
        }
    }

    fun launchSystemCameraFallback() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val captureUri = DocumentScannerUtils.createTempCameraScanUri(context)
            pendingCaptureUriStr = captureUri.toString()
            takePictureLauncher.launch(captureUri)
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val mlKitScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pageUris = scanResult?.pages?.map { it.imageUri }.orEmpty()
            if (pageUris.isNotEmpty()) {
                scope.launch { addPreStraightenedMlKitUris(pageUris) }
            }
        }
    }

    fun launchMlKitScanner() {
        val activity = context.findActivity() ?: return launchSystemCameraFallback()
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                mlKitScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { launchSystemCameraFallback() }
    }

    // Primary camera launcher opens IN-APP CameraX viewfinder directly (never kicks user out!)
    fun launchCameraScan() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            showInAppCameraX = true
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch { processNewSourceUris(uris) }
        }
    }

    fun handleBackWithAutoDraft() {
        if (scans.isNotEmpty()) {
            onSaveDraft(
                docName.ifBlank { "PDF Maker Studio" },
                buildConfiguredPages(),
                scanOptions()
            )
        } else {
            onBack()
        }
    }

    BackHandler(
        enabled = !showInAppCameraX &&
            !isProcessingScan &&
            !isBuildingPdf &&
            cropTarget == null &&
            previewPdfFile == null &&
            readyExportState == null
    ) {
        handleBackWithAutoDraft()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { handleBackWithAutoDraft() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (language == AppLanguage.BENGALI) "ডকুমেন্ট স্ক্যানার" else "Document Scanner",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (language == AppLanguage.BENGALI)
                                "${scans.size} পেজ · অটো A4 মার্জিন"
                            else "${scans.size} page(s) · Auto A4 margins",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF1F5F9))
        ) {
            ProjectTopControlPanel(
                language = language,
                fileName = docName,
                onFileNameChange = { docName = it },
                hasContent = scans.isNotEmpty(),
                isBusy = isProcessingScan || isBuildingPdf,
                onPreview = {
                    scope.launch {
                        isBuildingPdf = true
                        val res = pdfGenerator.createPdfFromConfiguredImages(
                            pages = buildConfiguredPages(),
                            globalOptions = scanOptions(),
                            fileNamePrefix = docName.ifBlank { "PDF_Maker_Studio" }
                        )
                        res.onSuccess { generated -> previewPdfFile = generated.file }
                        isBuildingPdf = false
                    }
                },
                onDownloadPdf = {
                    scope.launch {
                        isBuildingPdf = true
                        val targetName = docName.ifBlank { "PDF Maker Studio" }
                        val res = pdfGenerator.createPdfFromConfiguredImages(
                            pages = buildConfiguredPages(),
                            globalOptions = scanOptions(),
                            fileNamePrefix = targetName
                        )
                        res.onSuccess { generated ->
                            val savedRes = PdfDeviceSaver.savePdfToPhoneDownloads(
                                context = context,
                                sourceFile = generated.file,
                                requestedName = targetName
                            )
                            savedRes.onSuccess { savedDevicePdf ->
                                onAutoSaveDraft?.invoke(targetName, buildConfiguredPages(), scanOptions())
                                readyExportState = LocalReadyPdfState(
                                    sourceFile = generated.file,
                                    savedDevicePdf = savedDevicePdf,
                                    pageCount = generated.pageCount,
                                    sizeBytes = generated.sizeBytes
                                )
                            }.onFailure { err ->
                                snackbarHostState.showSnackbar(err.localizedMessage ?: "Download failed")
                            }
                        }.onFailure { err ->
                            snackbarHostState.showSnackbar(err.localizedMessage ?: "PDF failed")
                        }
                        isBuildingPdf = false
                    }
                }
            )
            if (isProcessingScan || isBuildingPdf) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (scans.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, OutlineHairline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(StudioCobalt.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.DocumentScanner,
                                        contentDescription = null,
                                        tint = StudioCobalt,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = if (language == AppLanguage.BENGALI)
                                        "লাইভ ক্যামেরায় অটো মার্জিন স্ক্যান"
                                    else "Live Camera Auto-Margin Scanner",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (language == AppLanguage.BENGALI)
                                        "ক্যামেরায় ডকুমেন্ট ধরলেই অটোমেটিক পেজের ৪টি কোণা ও মার্জিন দেখাবে — ছবি তুললেই বাঁকা অংশ সোজা ও ক্লিয়ার হয়ে A4 পেজে সেট হয়ে যাবে।"
                                    else "Shows live document margins on camera, auto-straightens tilted shots, and formats cleanly onto crisp A4 sheets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { launchCameraScan() },
                                        colors = ButtonDefaults.buttonColors(containerColor = StudioCrimson),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (language == AppLanguage.BENGALI) "ক্যামেরা স্ক্যান" else "Camera Scan")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            galleryPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (language == AppLanguage.BENGALI) "গ্যালারি" else "Gallery")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(scans, key = { _, item -> item.id }) { idx, entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, OutlineHairline),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI)
                                                "স্ক্যান পেজ ${idx + 1} · A4 সোজা ফিট"
                                            else "Scanned Page ${idx + 1} · A4 Auto-Fit",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { cropTarget = entry }) {
                                                Icon(
                                                    Icons.Outlined.Crop,
                                                    contentDescription = "Adjust margins",
                                                    tint = StudioCobalt
                                                )
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    reprocessEntry(entry, newRotation = entry.rotationDeg + 90)
                                                }
                                            }) {
                                                Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate")
                                            }
                                            IconButton(onClick = { scans.removeAt(idx) }) {
                                                Icon(
                                                    Icons.Outlined.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = DangerRed
                                                )
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                            .aspectRatio(210f / 297f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White)
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = entry.processedUri,
                                            contentDescription = "Scanned page ${idx + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ScanFilterMode.entries.forEach { mode ->
                                            FilterChip(
                                                selected = entry.filterMode == mode,
                                                onClick = {
                                                    scope.launch {
                                                        reprocessEntry(entry, newFilter = mode)
                                                    }
                                                },
                                                label = {
                                                    Text(if (language == AppLanguage.BENGALI) mode.labelBn else mode.labelEn)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, OutlineHairline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (language == AppLanguage.BENGALI)
                                            "পরের পেজ যোগ করবেন?"
                                        else "Add next copy?",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { launchCameraScan() },
                                            colors = ButtonDefaults.buttonColors(containerColor = StudioCrimson),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (language == AppLanguage.BENGALI) "পরের পেজ স্ক্যান" else "Scan Next",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                galleryPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (language == AppLanguage.BENGALI) "ছবি যোগ" else "Photo",
                                                style = MaterialTheme.typography.labelMedium
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
    }

    if (showInAppCameraX) {
        InAppCameraXScannerDialog(
            language = language,
            onDismiss = { showInAppCameraX = false },
            onCaptured = { capturedUri, lockedQuadHint ->
                showInAppCameraX = false
                scope.launch {
                    val overrides = if (lockedQuadHint != null) mapOf(capturedUri to lockedQuadHint) else emptyMap()
                    processNewSourceUris(listOf(capturedUri), overrides)
                }
            },
            onOpenGallery = {
                showInAppCameraX = false
                galleryPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onOpenMlKitFallback = {
                showInAppCameraX = false
                launchMlKitScanner()
            }
        )
    }

    cropTarget?.let { target ->
        CornerAdjustWithMagnifierDialog(
            imageUri = target.originalUri,
            initialQuad = target.quad,
            language = language,
            onDismiss = { cropTarget = null },
            onApply = { updatedQuad ->
                cropTarget = null
                scope.launch {
                    reprocessEntry(target, newQuad = updatedQuad)
                }
            }
        )
    }

    previewPdfFile?.let { file ->
        A4PdfPreviewDialog(
            pdfFile = file,
            title = docName.ifBlank { "PDF Maker Studio" },
            language = language,
            onDismiss = { previewPdfFile = null }
        )
    }

    readyExportState?.let { state ->
        PdfExportReadyDialog(
            state = state,
            language = language,
            isCloudBusy = isCloudBusy,
            onSaveToCloud = {
                onUploadPdfToCloud(
                    state.savedDevicePdf.localCopyFile,
                    docName.ifBlank { "PDF Maker Studio" },
                    state.pageCount
                )
            },
            onDismissKeepEditing = { readyExportState = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppCameraXScannerDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onCaptured: (Uri, ScanQuad?) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenMlKitFallback: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    var liveQuad by remember { mutableStateOf<ScanQuad?>(null) }
    var liveLocked by remember { mutableStateOf(false) }
    var autoCaptureEnabled by remember { mutableStateOf(false) }
    var steadyProgress by remember { mutableStateOf(0f) }
    var steadySinceMs by remember { mutableLongStateOf(0L) }
    var smoothedQuad by remember { mutableStateOf<ScanQuad?>(null) }
    var prevQuad by remember { mutableStateOf<ScanQuad?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraErrorMsg by remember { mutableStateOf<String?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    fun triggerCapture(lockedHint: ScanQuad?) {
        if (isCapturing) return
        isCapturing = true
        val targetUri = DocumentScannerUtils.createTempCameraScanUri(context)
        val tempFile = File(context.cacheDir, "camera_scans/${targetUri.lastPathSegment ?: "scan_${System.currentTimeMillis()}.jpg"}")
        tempFile.parentFile?.mkdirs()
        val outputOpts = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        imageCapture.takePicture(
            outputOpts,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onCaptured(Uri.fromFile(tempFile), lockedHint)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    cameraErrorMsg = exception.localizedMessage ?: "Capture failed"
                }
            }
        )
    }

    DisposableEffect(lifecycleOwner, autoCaptureEnabled) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            runCatching {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val analyzer = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val plane = imageProxy.planes.firstOrNull()
                                if (plane != null) {
                                    val detected = DocumentScannerUtils.detectDocumentQuadFromYPlane(
                                        yBuffer = plane.buffer,
                                        bufW = imageProxy.width,
                                        bufH = imageProxy.height,
                                        rowStride = plane.rowStride,
                                        rotationDeg = imageProxy.imageInfo.rotationDegrees
                                    )
                                    mainExecutor.execute {
                                        if (isCapturing) return@execute
                                        val nextSmooth = DocumentScannerUtils.smoothQuad(smoothedQuad, detected.quad, 0.36f)
                                        smoothedQuad = nextSmooth
                                        liveQuad = nextSmooth
                                        liveLocked = detected.isLocked

                                        val pq = prevQuad
                                        prevQuad = nextSmooth
                                        if (autoCaptureEnabled && detected.isLocked && pq != null) {
                                            if (DocumentScannerUtils.quadDistance(pq, nextSmooth) < 0.026f) {
                                                val now = System.currentTimeMillis()
                                                if (steadySinceMs == 0L) steadySinceMs = now
                                                val elapsed = now - steadySinceMs
                                                steadyProgress = (elapsed / 1200f).coerceIn(0f, 1f)
                                                if (elapsed >= 1200L) {
                                                    triggerCapture(nextSmooth)
                                                }
                                            } else {
                                                steadySinceMs = 0L
                                                steadyProgress = 0f
                                            }
                                        } else {
                                            steadySinceMs = 0L
                                            steadyProgress = 0f
                                        }
                                    }
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    analyzer
                )
            }.onFailure { err ->
                cameraErrorMsg = err.localizedMessage ?: "Camera unavailable"
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Scaffold(
            containerColor = Color(0xFF020617),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close")
                        }
                    },
                    title = {
                        Text(
                            text = if (language == AppLanguage.BENGALI) "অটো মার্জিন ক্যামেরা" else "Auto-Margin Camera",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        FilterChip(
                            selected = autoCaptureEnabled,
                            onClick = {
                                autoCaptureEnabled = !autoCaptureEnabled
                                steadySinceMs = 0L
                                steadyProgress = 0f
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(
                                    text = if (language == AppLanguage.BENGALI)
                                        if (autoCaptureEnabled) "অটো স্ক্যান: চালু" else "অটো স্ক্যান: বন্ধ"
                                    else if (autoCaptureEnabled) "Auto: ON" else "Auto: OFF"
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF020617)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (cameraErrorMsg != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = cameraErrorMsg ?: "",
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onOpenMlKitFallback, colors = ButtonDefaults.buttonColors(containerColor = StudioCrimson)) {
                            Text(if (language == AppLanguage.BENGALI) "বিকল্প স্ক্যানার খুলুন" else "Open Alternate Scanner")
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )

                            liveQuad?.let { quad ->
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val tl = Offset(quad.tl.x * size.width, quad.tl.y * size.height)
                                    val tr = Offset(quad.tr.x * size.width, quad.tr.y * size.height)
                                    val br = Offset(quad.br.x * size.width, quad.br.y * size.height)
                                    val bl = Offset(quad.bl.x * size.width, quad.bl.y * size.height)

                                    val quadPath = Path().apply {
                                        moveTo(tl.x, tl.y)
                                        lineTo(tr.x, tr.y)
                                        lineTo(br.x, br.y)
                                        lineTo(bl.x, bl.y)
                                        close()
                                    }

                                    clipPath(quadPath, clipOp = ClipOp.Difference) {
                                        drawRect(Color(0x78020617))
                                    }

                                    drawPath(
                                        path = quadPath,
                                        color = if (liveLocked) Color(0x1810B981) else Color(0x0BFFFFFF)
                                    )
                                    drawPath(
                                        path = quadPath,
                                        color = if (liveLocked) StudioEmerald else Color(0xFFFBBF24),
                                        style = Stroke(
                                            width = (if (liveLocked) 3.5.dp else 2.5.dp).toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                    listOf(tl, tr, br, bl).forEach { pt ->
                                        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = pt)
                                        drawCircle(
                                            color = if (liveLocked) StudioEmerald else Color(0xFFF59E0B),
                                            radius = 5.5.dp.toPx(),
                                            center = pt
                                        )
                                    }
                                }
                            }

                            if (autoCaptureEnabled && steadyProgress > 0f) {
                                LinearProgressIndicator(
                                    progress = { steadyProgress },
                                    color = StudioEmerald,
                                    trackColor = Color(0x660F172A),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(5.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = when {
                            isCapturing -> if (language == AppLanguage.BENGALI) "ছবি প্রসেস হচ্ছে..." else "Capturing scan..."
                            liveLocked -> if (language == AppLanguage.BENGALI)
                                "পেজের মার্জিন লক হয়েছে — নিচের গোল বাটনে চাপুন"
                            else "Page margins locked — tap shutter to capture & straighten"
                            else -> if (language == AppLanguage.BENGALI)
                                "ডকুমেন্টের ওপর ক্যামেরা ধরুন — অটোমেটিক মার্জিন দেখাচ্ছে"
                            else "Aim camera at document — showing paper margins live"
                        },
                        color = if (liveLocked) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onOpenGallery) {
                            Text(if (language == AppLanguage.BENGALI) "গ্যালারি" else "Gallery", color = Color.White)
                        }

                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(4.dp, StudioCrimson, CircleShape)
                                .clickable(enabled = !isCapturing) {
                                    triggerCapture(if (liveLocked) liveQuad else null)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(if (isCapturing) Color.Gray else StudioCrimson)
                            )
                        }

                        OutlinedButton(onClick = onOpenMlKitFallback) {
                            Text("ML Kit", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
