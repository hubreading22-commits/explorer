package com.collegefiles.app.ui.explorer

import com.smbcore.model.FileItem

data class ExplorerState(
    val currentShare: String = "",
    val breadcrumbs: List<String> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val selectedFile: FileItem? = null,
    val isLoading: Boolean = true,
    val connectionState: ConnectionState = ConnectionState.Connecting,
    val error: String? = null,
    
    val isDownloading: Boolean = false,
    val downloadingFileName: String? = null,
    val downloadProgress: Float? = null,
    
    // Future-proofing
    val selectionMode: Boolean = false,
    val selectedItems: Set<String> = emptySet()
)
