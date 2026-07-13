package com.example.aiwidgetstudio.presentation

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwidgetstudio.ai.AiOutputExtractor
import com.example.aiwidgetstudio.ai.GeminiWidgetGenerator
import com.example.aiwidgetstudio.ai.GeneratorMode
import com.example.aiwidgetstudio.ai.GeneratorPreference
import com.example.aiwidgetstudio.ai.LocalWidgetDslGenerator
import com.example.aiwidgetstudio.ai.ModelManager
import com.example.aiwidgetstudio.data.local.dao.WidgetListEntry
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.domain.model.UiNode
import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.engine.runtime.RuntimeWidget
import com.example.aiwidgetstudio.engine.runtime.WidgetRuntime
import com.example.aiwidgetstudio.engine.state.WidgetStateEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen { LIST, EDITOR, DETAIL, SETTINGS }

data class ParsedSummary(
    val name: String = "",
    val variableCount: Int = 0,
    val actionCount: Int = 0,
    val uiNodeCount: Int = 0
)

data class ModelSettingsState(
    val fileName: String = "",
    val sizeBytes: Long = 0,
    val ready: Boolean = false,
    val importStatus: OperationStatus = OperationStatus.Idle
)

data class MainUiState(
    val screen: AppScreen = AppScreen.LIST,
    val prompt: String = "",
    val editorJson: String = "",
    val showAdvancedEditor: Boolean = false,
    val editingWidgetId: String? = null,
    val selectedWidgetId: String? = null,
    val runtimeWidget: RuntimeWidget? = null,
    val previewWidget: RuntimeWidget? = null,
    val warnings: List<String> = emptyList(),
    val parsedSummary: ParsedSummary? = null,
    val placementCount: Int = 0,
    val validateStatus: OperationStatus = OperationStatus.Idle,
    val saveStatus: OperationStatus = OperationStatus.Idle,
    val generateStatus: OperationStatus = OperationStatus.Idle,
    val generatorMode: GeneratorMode = GeneratorMode.GEMINI,
    val modelSettings: ModelSettingsState = ModelSettingsState(),
    val error: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val runtime: WidgetRuntime,
    private val repository: WidgetRepository,
    private val geminiGenerator: GeminiWidgetGenerator,
    private val localGenerator: LocalWidgetDslGenerator,
    private val modelManager: ModelManager,
    private val generatorPreference: GeneratorPreference,
    private val stateEngine: WidgetStateEngine,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val widgets: StateFlow<List<WidgetListEntry>> = repository.observeWidgetListEntries().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _uiState = MutableStateFlow(
        MainUiState(
            prompt = savedStateHandle.get<String>(KEY_PROMPT).orEmpty(),
            editorJson = savedStateHandle.get<String>(KEY_EDITOR_JSON).orEmpty(),
            generatorMode = generatorPreference.mode
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var generateJob: Job? = null
    private var downloadsObserver: FileObserver? = null

    init {
        viewModelScope.launch { refreshModelInfo() }
    }

    fun openCreate() {
        _uiState.value = MainUiState(
            screen = AppScreen.EDITOR,
            prompt = _uiState.value.prompt,
            generatorMode = _uiState.value.generatorMode,
            modelSettings = _uiState.value.modelSettings,
            previewWidget = null
        )
        persistEditorState()
    }

    fun openEditor(widgetId: String) {
        viewModelScope.launch {
            val stored = repository.getWidgetWithState(widgetId) ?: return@launch
            _uiState.update {
                it.copy(
                    screen = AppScreen.EDITOR,
                    editorJson = stored.widget.dslJson,
                    editingWidgetId = widgetId,
                    showAdvancedEditor = true,
                    previewWidget = null,
                    error = null
                )
            }
            persistEditorState()
            validateDsl()
        }
    }

    fun updatePrompt(value: String) {
        _uiState.update { it.copy(prompt = value) }
        savedStateHandle[KEY_PROMPT] = value
    }

    fun updateEditorJson(value: String) {
        _uiState.update { it.copy(editorJson = value, error = null) }
        savedStateHandle[KEY_EDITOR_JSON] = value
    }

    fun toggleAdvancedEditor() {
        _uiState.update { it.copy(showAdvancedEditor = !it.showAdvancedEditor) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setGeneratorMode(mode: GeneratorMode) {
        generatorPreference.mode = mode
        _uiState.update { it.copy(generatorMode = mode) }
    }

    fun updateGeminiApiKey(key: String) { }

    fun validateDsl() {
        if (_uiState.value.validateStatus == OperationStatus.Loading) return
        _uiState.update { it.copy(validateStatus = OperationStatus.Loading, error = null) }

        val result = runtime.processDsl(_uiState.value.editorJson)
        val processed = result.getOrNull()
        if (processed == null) {
            _uiState.update {
                it.copy(
                    warnings = emptyList(),
                    parsedSummary = null,
                    validateStatus = OperationStatus.Error(result.exceptionOrNull()?.message ?: "Invalid widget DSL"),
                    error = result.exceptionOrNull()?.message ?: "Invalid widget DSL"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                warnings = processed.warnings.map { w -> w.message },
                parsedSummary = ParsedSummary(
                    name = processed.definition.metadata.name,
                    variableCount = processed.definition.data.variables.size,
                    actionCount = processed.definition.actions.size,
                    uiNodeCount = countUiNodes(processed.definition.ui)
                ),
                validateStatus = OperationStatus.Success,
                error = null
            )
        }
    }

    fun generateDsl() {
        if (_uiState.value.generateStatus == OperationStatus.Loading) return
        val prompt = _uiState.value.prompt.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = "Enter a description for your widget") }
            return
        }

        val generator = when (_uiState.value.generatorMode) {
            GeneratorMode.GEMINI -> geminiGenerator
            GeneratorMode.ON_DEVICE -> localGenerator
        }

        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            _uiState.update { it.copy(generateStatus = OperationStatus.Loading, error = null) }
            val existingDsl = if (_uiState.value.editingWidgetId != null) _uiState.value.editorJson.ifBlank { null } else null
            generator.generate(prompt, existingDsl).collect { progress ->
                when (val status = progress.status) {
                    OperationStatus.Loading -> {
                        val json = AiOutputExtractor.extractWidgetJson(progress.partialText)
                        if (json != null) {
                            _uiState.update { it.copy(editorJson = json) }
                            savedStateHandle[KEY_EDITOR_JSON] = json
                        }
                    }
                    OperationStatus.Success -> {
                        val json = AiOutputExtractor.extractWidgetJson(progress.partialText) ?: progress.partialText
                        _uiState.update { it.copy(editorJson = json) }
                        savedStateHandle[KEY_EDITOR_JSON] = json
                        validateDsl()
                        val processed = runtime.processDsl(json).getOrNull()
                        val preview = processed?.let {
                            val state = stateEngine.createInitialState(it.definition)
                            RuntimeWidget(it.definition, state)
                        }
                        _uiState.update { it.copy(generateStatus = OperationStatus.Success, previewWidget = preview) }
                    }
                    is OperationStatus.Error -> {
                        _uiState.update { it.copy(generateStatus = status, error = status.message) }
                    }
                    OperationStatus.Idle -> Unit
                }
            }
        }
    }

    fun saveWidget() {
        if (_uiState.value.saveStatus == OperationStatus.Loading) return
        validateDsl()
        if (_uiState.value.validateStatus is OperationStatus.Error) return

        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(saveStatus = OperationStatus.Loading, error = null) }
            val result = if (state.editingWidgetId == null) {
                runtime.createWidget(state.editorJson).map { it.widgetId }
            } else {
                runtime.updateWidgetDefinition(state.editingWidgetId, state.editorJson).map { state.editingWidgetId }
            }

            val widgetId = result.getOrNull()
            if (widgetId == null) {
                _uiState.update {
                    it.copy(
                        saveStatus = OperationStatus.Error(result.exceptionOrNull()?.message ?: "Unable to save widget"),
                        error = result.exceptionOrNull()?.message ?: "Unable to save widget"
                    )
                }
            } else {
                _uiState.update { it.copy(saveStatus = OperationStatus.Success) }
                openDetail(widgetId)
            }
        }
    }

    fun openDetail(widgetId: String) {
        viewModelScope.launch {
            _uiState.update {
                MainUiState(
                    screen = AppScreen.DETAIL,
                    selectedWidgetId = widgetId,
                    validateStatus = OperationStatus.Loading,
                    generatorMode = it.generatorMode,
                    modelSettings = it.modelSettings
                )
            }
            reloadDetail(widgetId)
        }
    }

    fun applyAction(actionId: String) {
        val widgetId = _uiState.value.selectedWidgetId ?: return
        viewModelScope.launch {
            runtime.applyAction(widgetId, actionId)
            reloadDetail(widgetId)
        }
    }

    fun deleteSelectedWidget() {
        val widgetId = _uiState.value.selectedWidgetId ?: return
        viewModelScope.launch {
            val deleted = runtime.deleteWidget(widgetId)
            if (deleted) showList()
            else _uiState.update { it.copy(error = "Remove this widget from the home screen before deleting it") }
        }
    }

    fun showList() {
        _uiState.update {
            MainUiState(
                prompt = savedStateHandle.get<String>(KEY_PROMPT).orEmpty(),
                editorJson = savedStateHandle.get<String>(KEY_EDITOR_JSON).orEmpty(),
                generatorMode = it.generatorMode,
                modelSettings = it.modelSettings
            )
        }
    }

    fun showSettings() {
        viewModelScope.launch {
            refreshModelInfo()
            _uiState.update {
                it.copy(screen = AppScreen.SETTINGS, modelSettings = it.modelSettings.copy(importStatus = OperationStatus.Idle))
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(modelSettings = it.modelSettings.copy(importStatus = OperationStatus.Loading)) }
            val result = modelManager.importModel(uri)
            localGenerator.invalidateEngine()
            refreshModelInfo()
            _uiState.update {
                it.copy(
                    modelSettings = it.modelSettings.copy(
                        importStatus = result.fold(
                            onSuccess = { OperationStatus.Success },
                            onFailure = { e -> OperationStatus.Error(e.message ?: "Import failed") }
                        )
                    ),
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun removeModel() {
        viewModelScope.launch {
            modelManager.removeModel()
            localGenerator.invalidateEngine()
            refreshModelInfo()
        }
    }

    fun startWatchingDownloads() {
        if (downloadsObserver != null) return
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        downloadsObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(downloadsDir, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path?.endsWith(".litertlm", ignoreCase = true) == true) {
                        viewModelScope.launch { importModel(Uri.fromFile(File(downloadsDir, path))) }
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(downloadsDir.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path?.endsWith(".litertlm", ignoreCase = true) == true) {
                        viewModelScope.launch { importModel(Uri.fromFile(File(downloadsDir, path))) }
                    }
                }
            }
        }
        downloadsObserver?.startWatching()
    }

    fun stopWatchingDownloads() {
        downloadsObserver?.stopWatching()
        downloadsObserver = null
    }

    private suspend fun refreshModelInfo() {
        val info = modelManager.getModelInfo()
        _uiState.update {
            it.copy(modelSettings = it.modelSettings.copy(fileName = info.fileName, sizeBytes = info.sizeBytes, ready = info.ready))
        }
    }

    private suspend fun reloadDetail(widgetId: String) {
        val widget = runtime.loadWidget(widgetId)
        val placements = repository.getPlacementCount(widgetId)
        _uiState.update {
            it.copy(
                runtimeWidget = widget,
                placementCount = placements,
                validateStatus = OperationStatus.Idle,
                error = if (widget == null) "Unable to load widget" else null
            )
        }
    }

    private fun persistEditorState() {
        savedStateHandle[KEY_PROMPT] = _uiState.value.prompt
        savedStateHandle[KEY_EDITOR_JSON] = _uiState.value.editorJson
    }

    private fun countUiNodes(node: UiNode): Int = when (node) {
        is UiNode.Box -> 1 + countUiNodes(node.child)
        is UiNode.Card -> 1 + countUiNodes(node.child)
        is UiNode.Column -> 1 + node.children.sumOf { countUiNodes(it) }
        is UiNode.Row -> 1 + node.children.sumOf { countUiNodes(it) }
        else -> 1
    }

    companion object {
        private const val KEY_PROMPT = "prompt"
        private const val KEY_EDITOR_JSON = "editor_json"
    }
}
