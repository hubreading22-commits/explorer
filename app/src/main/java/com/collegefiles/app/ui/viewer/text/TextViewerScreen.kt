package com.collegefiles.app.ui.viewer.text

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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

data class TextViewerState(
    val content: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val title: String = ""
)

class TextViewerViewModel(private val shareName: String, private val file: FileItem) : ViewModel() {
    private val _state = MutableStateFlow(TextViewerState(title = file.name))
    val state: StateFlow<TextViewerState> = _state.asStateFlow()

    init {
        loadFile()
    }

    private fun loadFile() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                AppModule.smbClient.openFile(shareName, file.path)
            }

            when (result) {
                is SmbResult.Success -> {
                    val stream = result.data
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            stream.inputStream().readBytes()
                        }
                        val content = String(bytes, Charsets.UTF_8)
                        _state.update { it.copy(content = content, isLoading = false) }
                    } finally {
                        withContext(Dispatchers.IO) { stream.close() }
                    }
                }
                is SmbResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = "Unable to load file.") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(
    viewModel: TextViewerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                }
                else -> Text(
                    text = state.content,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
