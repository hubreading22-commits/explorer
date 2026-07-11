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
    onLogout: () -> Unit,
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
    
    var showLogoutDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(uploads) {
        val newlyFinished = uploads.filter { it.state.isFinished && !AppModule.acknowledgedWorkIds.contains(it.id) }
        for (work in newlyFinished) {
            AppModule.acknowledgedWorkIds.add(work.id)
            if (work.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                viewModel.refresh()
            } else if (work.state == androidx.work.WorkInfo.State.FAILED) {
                val errorMsg = work.outputData.getString("error") ?: "Upload failed"
                snackbarHostState.showSnackbar(errorMsg)
            }
        }
    }

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
            onCopy = { 
                fileOpsViewModel.dismissActionSheet()
                fileOpsViewModel.requestBatchCopy(setOf(com.smbcore.model.SmbPath(state.currentShare, opsState.targetItem!!.path)))
            },
            onMove = { 
                fileOpsViewModel.dismissActionSheet()
                fileOpsViewModel.requestBatchMove(setOf(com.smbcore.model.SmbPath(state.currentShare, opsState.targetItem!!.path)))
            },
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
    if (opsState.showBatchDeleteDialog) {
        BatchDeleteConfirmationDialog(
            count = opsState.batchTargetItems.size,
            onConfirm = {
                fileOpsViewModel.executeBatchDelete {
                    viewModel.refresh()
                    viewModel.clearSelection()
                }
            },
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

    if (showLogoutDialog) {
        val activeUploads = uploads.filter { !it.state.isFinished }
        val hasActiveUploads = activeUploads.isNotEmpty()
        
        val activeSessions by AppModule.documentSessionRepository.sessions.collectAsState(initial = emptyMap())
        val hasActiveSessions = activeSessions.isNotEmpty()
        
        var isCancelling by remember { mutableStateOf(false) }

        if (isCancelling) {
            if (activeUploads.isEmpty()) {
                LaunchedEffect(Unit) {
                    showLogoutDialog = false
                    isCancelling = false
                    onLogout()
                }
            }
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Logging out...") },
                text = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Cancelling active uploads before logout...")
                    }
                },
                confirmButton = {}
            )
        } else {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout") },
                text = { 
                    if (hasActiveSessions) {
                        Text("You have active document editing sessions. Unsaved changes may be lost if you logout now. Are you sure?")
                    } else if (hasActiveUploads) {
                        Text("You have active uploads running. Logging out will cancel them immediately. Are you sure you want to logout?") 
                    } else {
                        Text("Are you sure you want to logout?")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (hasActiveUploads) {
                                isCancelling = true
                                androidx.work.WorkManager.getInstance(context).cancelAllWork()
                            } else {
                                showLogoutDialog = false
                                onLogout()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Logout") }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                if (state.selectionMode) {
                    TopAppBar(
                        title = { Text("${state.selectedItems.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                            }
                        },
                        actions = {
                            if (canDelete) {
                                IconButton(onClick = { fileOpsViewModel.requestBatchDelete(state.selectedItems) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                            IconButton(onClick = { fileOpsViewModel.requestBatchCopy(state.selectedItems) }) {
                                Icon(Icons.Default.FileCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = { fileOpsViewModel.requestBatchMove(state.selectedItems) }) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = "Move")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                } else {
                    TopAppBar(
                        title = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Files")
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = when(state.connectionState) {
                                        ConnectionState.Connected -> "🟢 Connected"
                                        ConnectionState.Expired, ConnectionState.Disconnected -> "🔴 Offline"
                                        else -> "🟡 Reconnecting"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                IconButton(onClick = { viewModel.refresh() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { showLogoutDialog = true },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.ExitToApp, contentDescription = "Logout", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Logout")
                                }
                            }
                        }
                    )
                }
                BreadcrumbRow(
                    currentShare = state.currentShare,
                    breadcrumbs = state.breadcrumbs,
                    onHomeClick = { onNavigateBackToShares() },
                    onShareRootClick = { viewModel.onHomeClick() },
                    onBreadcrumbClick = viewModel::onBreadcrumbClick
                )
                Divider()
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End, 
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                if (canUpload) {
                    ExtendedFloatingActionButton(
                        onClick = { filePicker.launch("*/*") },
                        icon = { Icon(Icons.Default.Upload, "Upload File") },
                        text = { Text("Upload") }
                    )
                }
                if (canCreateFolder) {
                    ExtendedFloatingActionButton(
                        onClick = { fileOpsViewModel.requestCreateFolder() },
                        icon = { Icon(Icons.Default.CreateNewFolder, "New Folder") },
                        text = { Text("New Folder") }
                    )
                }
            }
        }
    ) { paddingValues ->
        val pendingUpload by com.collegefiles.app.di.AppModule.pendingUploads.collectAsState()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isDownloading && state.downloadingFileName != null) {
                DownloadProgressBanner(
                    fileName = state.downloadingFileName!!,
                    progress = state.downloadProgress,
                    onCancel = { viewModel.cancelDownload() }
                )
            }
            UploadProgressBanner(uploads)
            
            pendingUpload?.let { (uri, fileName) ->
                PendingUploadBanner(
                    fileName = fileName,
                    onSaveHere = {
                        val currentPath = state.breadcrumbs.joinToString("\\")
                        val fullRemotePath = if (currentPath.isEmpty()) fileName else "$currentPath\\$fileName"
                        com.collegefiles.app.di.AppModule.uploadManager.enqueueUpload(uri, state.currentShare, fullRemotePath)
                        com.collegefiles.app.di.AppModule.pendingUploads.value = null
                    },
                    onCancel = {
                        com.collegefiles.app.di.AppModule.pendingUploads.value = null
                    }
                )
            }
            
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
                                    selectionMode = state.selectionMode,
                                    isSelected = state.selectedItems.contains(com.smbcore.model.SmbPath(state.currentShare, file.path)),
                                    onClick = {
                                        if (state.selectionMode) {
                                            viewModel.toggleSelection(file)
                                        } else {
                                            if (file.isDirectory) {
                                                viewModel.onFolderClick(file)
                                            } else {
                                                onFileClick(file) // Delegate to router in AppNavigation
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.onLongClick(file)
                                        // Also trigger fileOps if needed, but for now we enter selection mode
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
fun PendingUploadBanner(fileName: String, onSaveHere: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp)
    ) {
        Text(
            text = "Upload: $fileName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSaveHere) {
                Text("Save Here")
            }
        }
    }
}

@Composable
fun DownloadProgressBanner(fileName: String, progress: Float?, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Opening $fileName...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress != null) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun UploadProgressBanner(workInfos: List<androidx.work.WorkInfo>) {
    val activeUploads = workInfos.filter { !it.state.isFinished }
    if (activeUploads.isEmpty()) return

    val context = LocalContext.current

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
            
            val fileName = progress.getString("fileName") ?: "file"
            val remotePath = progress.getString("remotePath") ?: ""
            val displayPath = if (remotePath.contains("\\")) remotePath.substringBeforeLast('\\') else "root"
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$stateStr: $fileName", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "To: $displayPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${pct.toInt()}%", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (speed > 0) {
                        Text(
                            text = String.format("%.2f MB/s", speedMb), 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { androidx.work.WorkManager.getInstance(context).cancelWorkById(workInfo.id) }, 
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    onShareRootClick: () -> Unit,
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
                    .clickable { onShareRootClick() }
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
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 16.dp)
            )
        }
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
    if (millis <= 0) return "Unknown date"
    val now = System.currentTimeMillis()
    return android.text.format.DateUtils.getRelativeTimeSpanString(
        millis, 
        now, 
        android.text.format.DateUtils.MINUTE_IN_MILLIS, 
        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
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
