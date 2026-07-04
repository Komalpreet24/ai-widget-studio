package com.example.aiwidgetstudio.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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
        if (!activeModelFile.exists()) {
            return@withContext ModelInfo(fileName = "", sizeBytes = 0, ready = false)
        }
        ModelInfo(
            fileName = activeModelFile.name,
            sizeBytes = activeModelFile.length(),
            ready = true
        )
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

                if (!displayName.endsWith(".litertlm", ignoreCase = true)) {
                    error("Select a .litertlm model file")
                }

                modelsDir.mkdirs()
                val tempFile = File(modelsDir, "importing-${System.currentTimeMillis()}.litertlm")
                val available = resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.statSize.takeIf { it > 0 }
                }
                if (available != null) {
                    val freeSpace = modelsDir.freeSpace
                    if (available > freeSpace) {
                        error("Not enough storage for this model")
                    }
                }

                resolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to read selected file")

                verifyModel(tempFile)

                if (activeModelFile.exists()) activeModelFile.delete()
                if (!tempFile.renameTo(activeModelFile)) {
                    tempFile.copyTo(activeModelFile, overwrite = true)
                    tempFile.delete()
                }
            }.onFailure {
                modelsDir.listFiles { file -> file.name.startsWith("importing-") }?.forEach { it.delete() }
            }
        }
    }

    suspend fun removeModel(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (activeModelFile.exists()) activeModelFile.delete()
        }
    }

    fun activeModelPath(): String? = activeModelFile.takeIf { it.exists() }?.absolutePath

    private fun verifyModel(file: File) {
        val config = EngineConfig(modelPath = file.absolutePath, backend = Backend.GPU())
        try {
            Engine(config).use { engine ->
                engine.initialize()
            }
        } catch (_: Exception) {
            val cpuConfig = EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU())
            Engine(cpuConfig).use { engine ->
                engine.initialize()
            }
        }
    }
}
