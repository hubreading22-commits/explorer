package com.collegefiles.app.ui.viewer.image

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collegefiles.app.di.AppModule
import com.smbcore.model.FileItem
import com.smbcore.model.SmbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImageViewerState(
    val bitmap: ImageBitmap? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val title: String = ""
)

class ImageViewerViewModel(private val file: FileItem) : ViewModel() {
    private val _state = MutableStateFlow(ImageViewerState(title = file.name))
    val state: StateFlow<ImageViewerState> = _state.asStateFlow()

    init { loadImage() }

    private fun loadImage() {
        viewModelScope.launch {
            val shareName = file.path.substringBefore("\\")
            val result = withContext(Dispatchers.IO) {
                AppModule.smbClient.openFile(shareName, file.path)
            }
            when (result) {
                is SmbResult.Success -> {
                    val stream = result.data
                    try {
                        val bytes = withContext(Dispatchers.IO) { stream.inputStream().readBytes() }
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        _state.update { it.copy(bitmap = bmp?.asImageBitmap(), isLoading = false) }
                    } finally {
                        withContext(Dispatchers.IO) { stream.close() }
                    }
                }
                is SmbResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = "Unable to load image.") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(viewModel: ImageViewerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    // Pinch-to-zoom and pan state
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(state.title, maxLines = 1) }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                }
                state.bitmap != null -> Image(
                    bitmap = state.bitmap!!,
                    contentDescription = state.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offsetX, translationY = offsetY
                        )
                        .transformable(transformState)
                )
            }
        }
    }
}
