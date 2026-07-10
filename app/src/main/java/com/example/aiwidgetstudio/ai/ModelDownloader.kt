package com.example.aiwidgetstudio.ai

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class DownloadProgress(
    val state: DownloadState = DownloadState.Idle,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0
)

enum class DownloadState { Idle, Downloading, Completed, Failed }

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var currentDownloadId: Long = -1

    fun downloadModel(url: String): Flow<DownloadProgress> = flow {
        emit(DownloadProgress(state = DownloadState.Downloading))

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("AI Widget Model")
            .setDescription("Downloading on-device AI model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "model.litertlm")

        val destFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "model.litertlm")
        if (destFile.exists()) destFile.delete()

        currentDownloadId = downloadManager.enqueue(request)

        while (true) {
            val query = DownloadManager.Query().setFilterById(currentDownloadId)
            val cursor: Cursor? = downloadManager.query(query)
            if (cursor == null) {
                emit(DownloadProgress(state = DownloadState.Failed))
                return@flow
            }

            cursor.use {
                if (!it.moveToFirst()) {
                    emit(DownloadProgress(state = DownloadState.Failed))
                    return@flow
                }

                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        emit(DownloadProgress(DownloadState.Downloading, 1f, total, total))
                        val importResult = modelManager.importModel(Uri.fromFile(destFile))
                        if (importResult.isSuccess) {
                            emit(DownloadProgress(DownloadState.Completed, 1f, total, total))
                        } else {
                            emit(DownloadProgress(DownloadState.Failed))
                        }
                        destFile.delete()
                        return@flow
                    }
                    DownloadManager.STATUS_FAILED -> {
                        emit(DownloadProgress(state = DownloadState.Failed))
                        return@flow
                    }
                    else -> {
                        val progress = if (total > 0) downloaded.toFloat() / total else 0f
                        emit(DownloadProgress(DownloadState.Downloading, progress, downloaded, total))
                    }
                }
            }
            delay(500)
        }
    }.flowOn(Dispatchers.IO)

    fun cancelDownload() {
        if (currentDownloadId != -1L) {
            downloadManager.remove(currentDownloadId)
            currentDownloadId = -1
        }
    }
}
