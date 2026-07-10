package com.collegefiles.app.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.collegefiles.app.config.Capability
import com.collegefiles.app.di.AppModule
import com.smbcore.model.FileItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel,
    fileOpsViewModel: FileOpsViewModel,
    onNavigateBackToShares: () -> Unit,
    onSessionExpired: () -> Unit,
    onFileClick: (FileItem) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val opsState by fileOpsViewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val policy = AppModule.contentPolicy

    val canRename = policy.hasCapability(Capability.RENAME)
    val canDelete = policy.hasCapability(Capability.DELETE)
    val canUpload = policy.hasCapability(Capability.UPLOAD)
    val canCreateFolder = policy.hasCapability(Capability.CREATE_FOLDER)

    // File picker launcher for uploads
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = getFileName(context, uri)
            fileOpsViewModel.upload(
                uri = uri,
                shareName = state.currentShare,
                remotePath = state.breadcrumbs.joinToString("\\"),
                fileName = fileName,
                onSuccess = { viewModel.refresh() }
            )
        }
    }
    
    val uploads by AppModule.uploadManager.observeUploads().collectAsState(initial = emptyList())

    LaunchedEffect(opsState.successMessage) {
        opsState.successMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            fileOpsViewModel.clearMessages()
        }
    }
    LaunchedEffect(opsState.error) {
        opsState.error?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            fileOpsViewModel.clearMessages()
        }
    }

    LaunchedEffect(state.connectionState) {
        if (state.connectionState == ConnectionState.Expired) onSessionExpired()
    }

    BackHandler {
        val handled = viewModel.navigateUp()
        if (!handled) onNavigateBackToShares()
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────
    if (opsState.showActionSheet && opsState.targetItem != null) {
        FileActionSheet(
            item = opsState.targetItem!!,
            canRename = canRename,
            canDelete = canDelete,
            onOpen = {
                fileOpsViewModel.dismissActionSheet()
                val item = opsState.targetItem!!
                if (item.isDirectory) viewModel.onFolderClick(item) else onFileClick(item)
            },
            onCopy = { fileOpsViewModel.copyItem(state.currentShare) },
            onMove = { fileOpsViewModel.cutItem(state.currentShare) },
            onRename = { fileOpsViewModel.requestRename() },
            onDelete = { fileOpsViewModel.requestDelete() },
            onProperties = { fileOpsViewModel.dismissActionSheet() },
            onDismiss = { fileOpsViewModel.dismissActionSheet() }
        )
    }
    if (opsState.showRenameDialog && opsState.targetItem != null) {
        RenameDialog(
            item = opsState.targetItem!!,
            onConfirm = { newName ->
                fileOpsViewModel.rename(state.currentShare, newName) { viewModel.refresh() }
            },
            onDismiss = { fileOpsViewModel.dismissAllDialogs() }
        )
    }
    if (opsState.showDeleteDialog && opsState.targetItem != null) {
        DeleteConfirmationDialog(
            item = opsState.targetItem!!,
            onConfirm = { fileOpsViewModel.delete(state.currentShare) { viewModel.refresh() } },
            onDismiss = { fileOpsViewModel.dismissAllDialogs() }
        )
    }
    if (opsState.showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirm = { name ->
                fileOpsViewModel.createFolder(
                    state.currentShare,
                    state.breadcrumbs.joinToString("\\"),
                    name
                ) { viewModel.refresh() }
            },
            onDismiss = { fileOpsViewModel.dismissAllDialogs() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Files") },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Text(
                                text = when(state.connectionState) {
                                    ConnectionState.Connected -> "🟢 Connected"
                                    ConnectionState.Expired, ConnectionState.Disconnected -> "🔴 Offline"
                                    else -> "🟡 Reconnecting"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                )
                BreadcrumbRow(
                    currentShare = state.currentShare,
                    breadcrumbs = state.breadcrumbs,
                    onHomeClick = { onNavigateBackToShares() },
                    onBreadcrumbClick = viewModel::onBreadcrumbClick
                )
                Divider()
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canUpload) {
                    SmallFloatingActionButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Default.Upload, "Upload File")
                    }
                }
                if (canCreateFolder) {
                    FloatingActionButton(onClick = { fileOpsViewModel.requestCreateFolder() }) {
                        Icon(Icons.Default.CreateNewFolder, "New Folder")
                    }
                }
                if (fileOpsViewModel.hasClipboardItem) {
                    ExtendedFloatingActionButton(
                        onClick = { fileOpsViewModel.paste(state.currentShare, state.breadcrumbs.joinToString("\\")) { viewModel.refresh() } },
                        icon = { Icon(Icons.Default.ContentPaste, "Paste") },
                        text = { Text("Paste") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            UploadProgressBanner(uploads)
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> {
                        SkeletonFileLoadingList()
                    }
                    state.error != null -> {
                        ExplorerErrorState(error = state.error!!, onRetry = { viewModel.refresh() })
                    }
                    state.files.isEmpty() -> {
                        ExplorerEmptyState()
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.files, key = { it.path }) { file ->
                                FileItemRow(
                                    item = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            viewModel.onFolderClick(file)
                                        } else {
                                            onFileClick(file) // Delegate to router in AppNavigation
                                        }
                                    },
                                    onLongClick = {
                                        fileOpsViewModel.onLongPress(file)
                                    }
                                )
                                Divider(modifier = Modifier.padding(start = 72.dp)) // ChromeOS style offset divider
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadProgressBanner(workInfos: List<androidx.work.WorkInfo>) {
    val activeUploads = workInfos.filter { !it.state.isFinished }
    if (activeUploads.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        activeUploads.forEachIndexed { index, workInfo ->
            if (index > 0) Spacer(modifier = Modifier.height(16.dp))
            val progress = workInfo.progress
            val stateStr = progress.getString("state") ?: "Preparing"
            val pct = progress.getFloat("percentage", 0f)
            val speed = progress.getLong("speedBytesPerSecond", 0L)
            
            val speedMb = speed / 1024f / 1024f
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Uploading... $stateStr", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${pct.toInt()}%", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (speed > 0) {
                Text(
                    text = String.format("%.2f MB/s", speedMb), 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = pct / 100f, 
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun BreadcrumbRow(
    currentShare: String,
    breadcrumbs: List<String>,
    onHomeClick: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Text(
                text = "Home",
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onHomeClick() }
                    .padding(4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp).padding(horizontal = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = currentShare.replaceFirstChar { it.uppercase() },
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onHomeClick() } // Clicking share root acts like Home
                    .padding(4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        items(breadcrumbs.size) { index ->
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp).padding(horizontal = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = breadcrumbs[index],
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onBreadcrumbClick(index) }
                    .padding(4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (index == breadcrumbs.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    item: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (item.isDirectory) Color(0xFFFFCA28) else MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (item.isDirectory) {
                // Future: We could optionally fetch items count, but standard SMB usually just shows folder date
                Text(
                    text = "Folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val sizeStr = formatSize(item.size)
                Text(
                    text = "$sizeStr • ${formatRelativeDate(item.modified.toEpochMilli())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SkeletonFileLoadingList() {
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                }
            }
            Divider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
fun ExplorerErrorState(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = error, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun ExplorerEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("This folder is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Simple helpers - in a real app put these in a Utils file
fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatRelativeDate(millis: Long): String {
    // Simplified relative date for the MVP MVP
    return "Recently"
}

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "upload"
}
