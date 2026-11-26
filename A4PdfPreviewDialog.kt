package com.pdfmakerstudio.app.ui.editor

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pdfmakerstudio.app.ui.strings.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A4PdfPreviewDialog(
    pdfFile: File,
    title: String,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onUploadCloud: (() -> Unit)? = null
) {
    var renderedPages by remember(pdfFile.absolutePath) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember(pdfFile.absolutePath) { mutableStateOf(true) }
    var errorMsg by remember(pdfFile.absolutePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfFile.absolutePath) {
        isLoading = true
        errorMsg = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val pages = mutableListOf<Bitmap>()
                val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val targetWidth = 1080
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                                val bmp = Bitmap.createBitmap(
                                    targetWidth,
                                    (targetWidth * aspect).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pages.add(bmp)
                            }
                        }
                    }
                }
                pages
            }
        }
        result.onSuccess {
            renderedPages = it
        }.onFailure {
            errorMsg = it.message ?: "Preview failed"
        }
        isLoading = false
    }

    DisposableEffect(renderedPages) {
        onDispose {
            renderedPages.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (language == AppLanguage.BENGALI) "A4 PDF লাইভ প্রিভিউ" else "Live A4 PDF Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$title (${renderedPages.size}p)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (onUploadCloud != null) {
                            IconButton(onClick = onUploadCloud) {
                                Icon(
                                    Icons.Outlined.CloudUpload,
                                    contentDescription = if (language == AppLanguage.BENGALI) "আপলোড" else "Upload"
                                )
                            }
                        }
                        if (onDownload != null) {
                            IconButton(onClick = onDownload) {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = if (language == AppLanguage.BENGALI) "ডাউনলোড" else "Download"
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    errorMsg != null -> Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(renderedPages) { index, bmp ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI)
                                                "পৃষ্ঠা ${index + 1} / ${renderedPages.size}"
                                            else
                                                "Page ${index + 1} of ${renderedPages.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF475569),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "A4 Portrait",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(210f / 297f)
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
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
