package com.collegefiles.app.ui.viewer

import androidx.lifecycle.ViewModel
import com.collegefiles.app.config.Capability
import com.collegefiles.app.di.AppModule
import com.smbcore.model.FileItem
import com.smbcore.model.FileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ViewerViewModel : ViewModel() {

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    fun selectFile(file: FileItem) {
        val policy = AppModule.contentPolicy
        val requiredCapability = file.type.toCapability()

        if (requiredCapability != null && !policy.hasCapability(requiredCapability)) {
            _state.update { it.copy(file = file, isRestricted = true, title = file.name) }
            return
        }

        val destination = when (file.type) {
            FileType.PDF -> ViewerDestination.Pdf
            FileType.IMAGE -> ViewerDestination.Image
            FileType.VIDEO -> ViewerDestination.Video
            FileType.AUDIO -> ViewerDestination.Audio
            FileType.TEXT -> ViewerDestination.Text
            else -> ViewerDestination.Unsupported
        }

        _state.update {
            it.copy(
                file = file,
                destination = destination,
                isRestricted = false,
                title = file.name,
                fileInfo = formatFileInfo(file)
            )
        }
    }

    fun clearFile() {
        _state.update { ViewerState() }
    }

    private fun formatFileInfo(file: FileItem): String {
        val sizeStr = formatSize(file.size)
        return "${file.type.name} • $sizeStr"
    }
}

enum class ViewerDestination {
    Pdf, Image, Video, Audio, Text, Unsupported, Restricted
}

private fun FileType.toCapability(): Capability? = when (this) {
    FileType.PDF -> Capability.VIEW_PDF
    FileType.IMAGE -> Capability.VIEW_IMAGE
    FileType.VIDEO -> Capability.VIEW_VIDEO
    FileType.AUDIO -> Capability.VIEW_AUDIO
    FileType.TEXT -> Capability.VIEW_TEXT
    FileType.OFFICE -> Capability.VIEW_OFFICE
    else -> null
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
