package com.collegefiles.app.upload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.asFlow
import androidx.work.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class UploadManager(private val context: Context) {
    
    private val workManager = WorkManager.getInstance(context)

    fun enqueueUpload(uri: Uri, shareName: String, remotePath: String, overwrite: Boolean = false): UUID {
        val inputData = workDataOf(
            "uri" to uri.toString(),
            "shareName" to shareName,
            "remotePath" to remotePath,
            "overwrite" to overwrite
        )

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .addTag("upload")
            .build()

        workManager.enqueue(request)
        return request.id
    }

    fun observeUploads(): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosByTagLiveData("upload").asFlow()
    }
    
    fun cancelUpload(id: UUID) {
        workManager.cancelWorkById(id)
    }
}
