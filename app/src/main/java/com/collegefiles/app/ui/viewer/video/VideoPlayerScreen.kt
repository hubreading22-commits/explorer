package com.collegefiles.app.ui.viewer.video

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smbcore.model.FileItem

// Phase 6 Stub — Full ExoPlayer + SmbDataSource integration in next iteration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(file: FileItem?, onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(file?.name ?: "Video", maxLines = 1) }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Video Player", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Media3 + SmbDataSource integration coming in next phase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Go Back") }
            }
        }
    }
}
