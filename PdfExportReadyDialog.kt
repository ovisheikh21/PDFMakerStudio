package com.pdfmakerstudio.app.ui.editor

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pdfmakerstudio.app.data.pdf.PdfDeviceSaver
import com.pdfmakerstudio.app.data.pdf.SavedDevicePdf
import com.pdfmakerstudio.app.ui.strings.AppLanguage
import com.pdfmakerstudio.app.ui.theme.OutlineHairline
import com.pdfmakerstudio.app.ui.theme.StudioCobalt
import com.pdfmakerstudio.app.ui.theme.StudioCrimson
import com.pdfmakerstudio.app.ui.theme.StudioEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LocalReadyPdfState(
    val sourceFile: File,
    val savedDevicePdf: SavedDevicePdf,
    val pageCount: Int,
    val sizeBytes: Long
)

@Composable
fun PdfExportReadyDialog(
    state: LocalReadyPdfState,
    language: AppLanguage,
    isCloudBusy: Boolean = false,
    onSaveToCloud: (() -> Unit)? = null,
    onDismissKeepEditing: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var firstPageBmp by remember(state.savedDevicePdf.localCopyFile.absolutePath) {
        mutableStateOf<Bitmap?>(null)
    }
    var showPreviewPages by remember { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.savedDevicePdf.localCopyFile.absolutePath) {
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(
                    state.savedDevicePdf.localCopyFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                ).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount <= 0) return@runCatching null
                        renderer.openPage(0).use { page ->
                            val targetW = 420
                            val ratio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                            val bitmap = Bitmap.createBitmap(
                                targetW,
                                (targetW * ratio).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(AndroidColor.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
        firstPageBmp = bmp
    }

    DisposableEffect(firstPageBmp) {
        onDispose {
            firstPageBmp?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    if (showPreviewPages) {
        A4PdfPreviewDialog(
            pdfFile = state.savedDevicePdf.localCopyFile,
            title = state.savedDevicePdf.displayFileName,
            language = language,
            onDismiss = { showPreviewPages = false }
        )
    }

    Dialog(
        onDismissRequest = onDismissKeepEditing,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Emerald verified header banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF047857), Color(0xFF0D9488))
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                                Column {
                                    Text(
                                        text = if (language == AppLanguage.BENGALI)
                                            "সরাসরি ফোনে ডাউনলোড হয়েছে!"
                                        else "Saved Directly to Phone!",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = state.savedDevicePdf.savedFolderLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismissKeepEditing,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Crisp Page-1 Thumbnail + Metadata
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, OutlineHairline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .width(56.dp)
                                        .aspectRatio(210f / 297f)
                                ) {
                                    val bmp = firstPageBmp
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.PictureAsPdf,
                                                contentDescription = null,
                                                tint = StudioCrimson
                                            )
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = state.savedDevicePdf.displayFileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeLabel = remember(state.sizeBytes) {
                                        Formatter.formatShortFileSize(context, state.sizeBytes)
                                    }
                                    Text(
                                        text = if (language == AppLanguage.BENGALI)
                                            "A4 ফরম্যাট · ${state.pageCount} পৃষ্ঠা · $sizeLabel"
                                        else "A4 Portrait · ${state.pageCount} page(s) · $sizeLabel",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        statusNotice?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioEmerald,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Direct Open & Re-Download Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val ok = PdfDeviceSaver.openPdfFile(
                                        context,
                                        state.savedDevicePdf.localCopyFile
                                    )
                                    if (!ok) showPreviewPages = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioCrimson),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (language == AppLanguage.BENGALI) "PDF খুলুন" else "Open PDF",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val res = PdfDeviceSaver.savePdfToPhoneDownloads(
                                            context = context,
                                            sourceFile = state.savedDevicePdf.localCopyFile,
                                            requestedName = state.savedDevicePdf.displayFileName
                                        )
                                        statusNotice = res.fold(
                                            onSuccess = {
                                                if (language == AppLanguage.BENGALI)
                                                    "✓ Downloads ফোল্ডারে নতুন কপি সেভ হয়েছে"
                                                else "✓ Saved another copy to Downloads"
                                            },
                                            onFailure = { it.localizedMessage ?: "Download failed" }
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (language == AppLanguage.BENGALI) "আবার সেভ" else "Download")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showPreviewPages = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (language == AppLanguage.BENGALI) "সব পেজ" else "Preview")
                            }

                            OutlinedButton(
                                onClick = {
                                    PdfDeviceSaver.sharePdfFile(
                                        context,
                                        state.savedDevicePdf.localCopyFile,
                                        state.savedDevicePdf.displayFileName
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (language == AppLanguage.BENGALI) "শেয়ার" else "Share")
                            }
                        }

                        if (onSaveToCloud != null) {
                            OutlinedButton(
                                enabled = !isCloudBusy,
                                onClick = onSaveToCloud,
                                border = BorderStroke(1.dp, StudioCobalt.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Outlined.CloudUpload,
                                    contentDescription = null,
                                    tint = StudioCobalt,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isCloudBusy) {
                                        if (language == AppLanguage.BENGALI) "ক্লাউডে সেভ হচ্ছে..." else "Saving to Cloud..."
                                    } else {
                                        if (language == AppLanguage.BENGALI) "ক্লাউড ভল্টেও ব্যাকআপ রাখুন" else "Also Backup to Cloud Vault"
                                    },
                                    color = StudioCobalt
                                )
                            }
                        }

                        TextButton(
                            onClick = onDismissKeepEditing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (language == AppLanguage.BENGALI)
                                    "এই প্রজেক্টেই কাজ চালিয়ে যান"
                                else "Continue Editing This Project"
                            )
                        }
                    }
                }
            }
        }
    }
}
