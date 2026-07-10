package com.collegefiles.app.ui.viewer.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collegefiles.app.di.AppModule
import com.smbcore.io.inputStream
import com.smbcore.model.FileItem
import com.smbcore.model.SmbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PdfViewerState(
    val pages: List<ImageBitmap> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val title: String = ""
)

class PdfViewerViewModel(
    private val file: FileItem,
    private val cacheDir: File
) : ViewModel() {
    private val _state = MutableStateFlow(PdfViewerState(title = file.name))
    val state: StateFlow<PdfViewerState> = _state.asStateFlow()

    private var tempFile: File? = null

    init { loadPdf() }

    private fun loadPdf() {
        viewModelScope.launch {
            val shareName = file.path.substringBefore("\\")
            val result = withContext(Dispatchers.IO) {
                AppModule.smbClient.openFile(shareName, file.path)
            }
            when (result) {
                is SmbResult.Success -> {
                    val stream = result.data
                    try {
                        // Write to app-private cache (not encrypted, but private to app)
                        val cacheFile = withContext(Dispatchers.IO) {
                            val f = File.createTempFile("viewer_", ".pdf", cacheDir)
                            f.outputStream().use { out -> stream.inputStream().copyTo(out) }
                            f
                        }
                        tempFile = cacheFile

                        val pages = withContext(Dispatchers.IO) { renderPdfPages(cacheFile) }
                        _state.update { it.copy(pages = pages, isLoading = false) }
                    } finally {
                        withContext(Dispatchers.IO) { stream.close() }
                    }
                }
                is SmbResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = "Unable to load PDF.") }
                }
            }
        }
    }

    private fun renderPdfPages(file: File): List<ImageBitmap> {
        val pages = mutableListOf<ImageBitmap>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bmp = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pages.add(bmp.asImageBitmap())
            page.close()
        }
        renderer.close()
        pfd.close()
        return pages
    }

    override fun onCleared() {
        super.onCleared()
        tempFile?.delete() // Delete cache when viewer is closed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(viewModel: PdfViewerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(state.title, maxLines = 1) }) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading PDF...", style = MaterialTheme.typography.bodySmall)
                }
                state.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(state.pages.size) { i ->
                        Image(
                            bitmap = state.pages[i],
                            contentDescription = "Page ${i + 1}",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
