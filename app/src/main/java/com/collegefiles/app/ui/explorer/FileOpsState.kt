package com.collegefiles.app.ui.explorer

import com.smbcore.model.FileItem

data class FileOpsState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,

    // Dialog visibility
    val showRenameDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val showActionSheet: Boolean = false,

    // Currently targeted item
    val targetItem: FileItem? = null,
    
    // Batch targets
    val batchTargetItems: Set<com.smbcore.model.SmbPath> = emptySet(),
    val showBatchDeleteDialog: Boolean = false
)
