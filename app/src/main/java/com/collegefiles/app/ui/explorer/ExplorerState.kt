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
    
    // Future-proofing
    val selectionMode: Boolean = false,
    val selectedItems: Set<String> = emptySet()
)
