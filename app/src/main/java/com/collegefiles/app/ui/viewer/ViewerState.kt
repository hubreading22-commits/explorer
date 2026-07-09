package com.collegefiles.app.ui.viewer

import com.smbcore.model.FileItem

data class ViewerState(
    val file: FileItem? = null,
    val destination: ViewerDestination = ViewerDestination.Unsupported,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRestricted: Boolean = false,
    val title: String = "",
    val fileInfo: String = ""
)
