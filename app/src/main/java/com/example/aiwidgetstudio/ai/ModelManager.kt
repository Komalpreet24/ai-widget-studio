package com.example.aiwidgetstudio.ai

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

    suspend fun getModelInfo(): ModelInfo = withContext(Dispatchers.IO) {
        if (!activeModelFile.exists()) return@withContext ModelInfo("", 0, false)
        ModelInfo(activeModelFile.name, activeModelFile.length(), true)
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

                if (!displayName.endsWith(".litertlm", ignoreCase = true)) error("Select a .litertlm model file")

                modelsDir.mkdirs()
                val tempFile = File(modelsDir, "importing-${System.currentTimeMillis()}.litertlm")
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
                if (!tempFile.renameTo(activeModelFile)) {
                    tempFile.copyTo(activeModelFile, overwrite = true)
                    tempFile.delete()
                }
            }.onFailure {
                modelsDir.listFiles { f -> f.name.startsWith("importing-") }?.forEach { it.delete() }
            }
        }
    }

    suspend fun removeModel(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) { if (activeModelFile.exists()) activeModelFile.delete() }
    }

    fun activeModelPath(): String? = activeModelFile.takeIf { it.exists() }?.absolutePath

    private fun verifyModel(file: File) {
        if (file.length() < 1024) error("File is too small to be a valid model")
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        val isLiteRt = header.take(4).map { it.toInt() and 0xFF } == listOf(0x20, 0x00, 0x00, 0x00) ||
            String(header).contains("TFL3") || String(header).contains("FLAT")
        if (!isLiteRt && file.length() < 10 * 1024 * 1024) error("File does not appear to be a valid .litertlm model")
    }
}
