package com.smbcore.internal.mapper

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.smbcore.model.FileItem
import com.smbcore.model.FileType
import java.time.Instant

internal object ModelMapper {

    fun mapToFileItem(path: String, info: FileIdBothDirectoryInformation): FileItem {
        val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
        val type = if (isDir) FileType.FOLDER else detectFileType(info.fileName)
        
        return FileItem(
            path = if (path.isEmpty()) info.fileName else "$path\\${info.fileName}",
            name = info.fileName,
            type = type,
            size = info.endOfFile,
            modified = Instant.ofEpochMilli(info.lastWriteTime.toEpochMillis()),
            isDirectory = isDir
        )
    }

    private fun detectFileType(fileName: String): FileType {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        return when (ext) {
            "PDF" -> FileType.PDF
            "JPG", "JPEG", "PNG", "GIF", "BMP" -> FileType.IMAGE
            "MP4", "MKV", "MOV", "AVI" -> FileType.VIDEO
            "MP3", "WAV", "OGG", "FLAC" -> FileType.AUDIO
            "DOC", "DOCX" -> FileType.WORD
            "XLS", "XLSX" -> FileType.EXCEL
            "PPT", "PPTX" -> FileType.POWERPOINT
            "ZIP", "RAR", "7Z" -> FileType.ZIP
            else -> FileType.UNKNOWN
        }
    }
}
