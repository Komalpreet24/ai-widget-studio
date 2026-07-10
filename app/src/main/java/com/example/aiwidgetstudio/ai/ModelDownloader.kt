package com.example.aiwidgetstudio.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class DownloadProgress(
    val state: DownloadState = DownloadState.Idle,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0
)

enum class DownloadState { Idle, Downloading, Completed, Failed }

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var currentDownloadId: Long = -1

    fun downloadModel(url: String): Flow<DownloadProgress> = flow {
        emit(DownloadProgress(state = DownloadState.Downloading))

        val destFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "model-download.litertlm"
        )
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("AI Widget Model")
            .setDescription("Downloading on-device AI model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(destFile))

        currentDownloadId = downloadManager.enqueue(request)

        while (true) {
            val query = DownloadManager.Query().setFilterById(currentDownloadId)
            val cursor = downloadManager.query(query) ?: run {
                emit(DownloadProgress(state = DownloadState.Failed))
                return@flow
            }

            var keepPolling = true
            cursor.use {
                if (!it.moveToFirst()) {
                    emit(DownloadProgress(state = DownloadState.Failed))
                    keepPolling = false
                    return@use
                }

                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        keepPolling = false
                        installFile(destFile)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        keepPolling = false
                        destFile.delete()
                        emit(DownloadProgress(state = DownloadState.Failed))
                    }
                    else -> {
                        val progress = if (total > 0) downloaded.toFloat() / total else 0f
                        emit(DownloadProgress(DownloadState.Downloading, progress, downloaded.coerceAtLeast(0), total.coerceAtLeast(0)))
                    }
                }
            }

            if (!keepPolling) return@flow
            delay(300)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<DownloadProgress>.installFile(srcFile: File) {
        val modelsDir = File(context.filesDir, "models")
        modelsDir.mkdirs()
        val destModel = File(modelsDir, "widget-generator.litertlm")
        runCatching {
            srcFile.copyTo(destModel, overwrite = true)
            srcFile.delete()
            emit(DownloadProgress(DownloadState.Completed, 1f, destModel.length(), destModel.length()))
        }.onFailure {
            srcFile.delete()
            destModel.delete()
            emit(DownloadProgress(state = DownloadState.Failed))
        }
    }

    fun cancelDownload() {
        if (currentDownloadId != -1L) {
            downloadManager.remove(currentDownloadId)
            currentDownloadId = -1
        }
    }
}
