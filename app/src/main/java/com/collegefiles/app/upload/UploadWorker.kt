package com.collegefiles.app.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.collegefiles.app.R
import com.collegefiles.app.di.AppModule
import com.smbcore.model.UploadState
import kotlinx.coroutines.CancellationException

class UploadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "upload_channel"
        const val NOTIFICATION_ID_BASE = 1000
    }

    override suspend fun doWork(): Result {
        val uriStr = inputData.getString("uri") ?: return Result.failure()
        val shareName = inputData.getString("shareName") ?: return Result.failure()
        val remotePath = inputData.getString("remotePath") ?: return Result.failure()
        
        val uri = Uri.parse(uriStr)
        val fileName = remotePath.substringAfterLast('\\', remotePath)
        val overwrite = inputData.getBoolean("overwrite", false)

        createNotificationChannel()

        var expectedSize = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    expectedSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Create initial foreground info
        val notificationId = NOTIFICATION_ID_BASE + id.hashCode()
        setForeground(createForegroundInfo(fileName, 0, notificationId))

        var result: Result = Result.success()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return Result.failure()

        // Isolate the SMBClient so background bulk uploads don't flood the UI's socket
        val uploadSmbClient = com.smbcore.SmbClient.create(AppModule.smbConfig, AppModule.credentialStore)

        try {
            uploadSmbClient.upload(
                input = inputStream,
                shareName = shareName,
                remotePath = remotePath,
                expectedSize = expectedSize,
                overwrite = overwrite,
                onProgress = { progress ->
                    // Update notification and WorkManager progress
                    setProgress(workDataOf(
                        "state" to "Uploading",
                        "percentage" to progress.percentage,
                        "bytesTransferred" to progress.bytesTransferred,
                        "totalBytes" to progress.totalBytes,
                        "speedBytesPerSecond" to progress.speedBytesPerSecond,
                        "estimatedRemainingSeconds" to progress.estimatedRemainingSeconds,
                        "fileName" to fileName,
                        "remotePath" to remotePath
                    ))
                    
                    val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setContentTitle("Uploading $fileName")
                        .setContentText("${progress.percentage.toInt()}% (${progress.speedBytesPerSecond / 1024 / 1024} MB/s)")
                        .setSmallIcon(android.R.drawable.ic_menu_upload)
                        .setProgress(100, progress.percentage.toInt(), false)
                        .setOngoing(true)
                        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", androidx.work.WorkManager.getInstance(context).createCancelPendingIntent(id))
                        .build()
                    notificationManager.notify(notificationId, notif)
                },
                onStateChange = { state ->
                    val stateStr = when(state) {
                        is UploadState.Waiting -> "Waiting"
                        is UploadState.Uploading -> "Uploading"
                        is UploadState.Verifying -> "Verifying"
                        is UploadState.Renaming -> "Finishing"
                        is UploadState.Completed -> "Completed"
                        is UploadState.Cancelled -> "Cancelled"
                        is UploadState.Failed -> "Failed: ${state.error}"
                        is UploadState.AlreadyExists -> "Already Exists"
                    }
                    setProgress(workDataOf(
                        "state" to stateStr,
                        "fileName" to fileName,
                        "remotePath" to remotePath
                    ))
                    
                    if (state is UploadState.Failed || state is UploadState.AlreadyExists) {
                        result = Result.failure(workDataOf("error" to stateStr))
                    } else if (state is UploadState.Cancelled) {
                        result = Result.failure(workDataOf("error" to "Cancelled"))
                    }
                }
            )
        } catch (e: CancellationException) {
            // Handled by onStateChange -> Cancelled
            result = Result.failure(workDataOf("error" to "Cancelled"))
        } catch (e: Exception) {
            result = Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
            // Remove notification
            notificationManager.cancel(notificationId)
        }

        return result
    }

    private fun createForegroundInfo(fileName: String, progress: Int, notificationId: Int): ForegroundInfo {
        val cancelIntent = androidx.work.WorkManager.getInstance(context).createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Preparing to upload $fileName")
            .setTicker("Uploading $fileName")
            .setContentText("Calculating...")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setProgress(100, progress, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Uploads"
            val descriptionText = "File upload notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
