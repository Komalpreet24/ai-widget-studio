package com.example.aiwidgetstudio.ai

import android.util.Log
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ModelInfo(
    val fileName: String,
    val sizeBytes: Long,
    val ready: Boolean
)

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val modelsDir = File(context.filesDir, "models")
    private val activeModelFile = File(modelsDir, "widget-generator.litertlm")
    private val activeTaskFile = File(modelsDir, "widget-generator.task")
    private fun activeFile() = listOf(activeModelFile, activeTaskFile).firstOrNull { it.exists() }

    init {
        modelsDir.listFiles { f -> f.name.startsWith("downloading-") || f.name.startsWith("importing-") }?.forEach { it.delete() }
    }

    companion object {
        private const val MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    }

    suspend fun getModelInfo(): ModelInfo = withContext(Dispatchers.IO) {
        val f = activeFile() ?: return@withContext ModelInfo("", 0, false)
        ModelInfo(f.name, f.length(), true)
    }

    suspend fun downloadModel(onProgress: (Float) -> Unit): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                modelsDir.mkdirs()
                val tempFile = File(modelsDir, "downloading-${System.currentTimeMillis()}.task")
                try {
                    var url = MODEL_URL
                    var connection: HttpURLConnection
                    var redirects = 0
                    while (true) {
                        connection = (URL(url).openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = false
                            connectTimeout = 15_000
                            readTimeout = 60_000
                        }
                        val code = connection.responseCode
                        if (code in 300..399) {
                            url = connection.getHeaderField("Location") ?: error("Redirect with no location")
                            connection.disconnect()
                            if (++redirects > 5) error("Too many redirects")
                        } else {
                            if (code != 200) error("Download failed: HTTP $code")
                            break
                        }
                    }
                    val total = connection.contentLengthLong
                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) onProgress(downloaded.toFloat() / total)
                            }
                        }
                    }
                    connection.disconnect()
                    activeFile()?.delete()
                    if (!tempFile.renameTo(activeTaskFile)) {
                        tempFile.copyTo(activeTaskFile, overwrite = true)
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    throw e
                }
            }
        }
    }

    suspend fun importModel(uri: Uri): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    if (index >= 0) cursor.getString(index) else null
                } ?: "model.litertlm"

                if (!displayName.endsWith(".litertlm", ignoreCase = true) &&
                    !displayName.endsWith(".task", ignoreCase = true)) error("Select a .litertlm or .task model file")

                modelsDir.mkdirs()
                val tempFile = File(modelsDir, "importing-${System.currentTimeMillis()}.task")
                val available = resolver.openFileDescriptor(uri, "r")?.use { it.statSize.takeIf { s -> s > 0 } }
                if (available != null && available > modelsDir.freeSpace) error("Not enough storage for this model")

                resolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: if (uri.scheme == "file") {
                    File(uri.path!!).inputStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } else error("Unable to read selected file")

                verifyModel(tempFile)
                if (activeModelFile.exists()) activeModelFile.delete()
                if (activeTaskFile.exists()) activeTaskFile.delete()
                val dest = if (displayName.endsWith(".task", ignoreCase = true)) activeTaskFile else activeModelFile
                if (!tempFile.renameTo(dest)) {
                    tempFile.copyTo(dest, overwrite = true)
                    tempFile.delete()
                }
            }.onFailure {
                modelsDir.listFiles { f -> f.name.startsWith("importing-") }?.forEach { it.delete() }
            }
        }
    }

    suspend fun removeModel(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) { activeFile()?.delete() }
    }

    fun activeModelPath(): String? {
        val f = activeFile()
        Log.d("ModelManager", "activeModelPath: litertlm=${activeModelFile.exists()} task=${activeTaskFile.exists()} resolved=${f?.absolutePath}")
        return f?.absolutePath
    }

    private fun verifyModel(file: File) {
        if (file.length() < 1024) error("File is too small to be a valid model")
    }
}
