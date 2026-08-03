package com.example.aiwidgetstudio.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwidgetstudio.ai.AiOutputExtractor
import com.example.aiwidgetstudio.ai.CapabilityChecker
import com.example.aiwidgetstudio.ai.GeminiWidgetGenerator
import com.example.aiwidgetstudio.data.PermissionManager
import com.example.aiwidgetstudio.data.PermissionRequest
import com.example.aiwidgetstudio.data.datasource.DataSourceResolver
import com.example.aiwidgetstudio.ai.GeneratorMode
import com.example.aiwidgetstudio.ai.GeneratorPreference
import com.example.aiwidgetstudio.ai.WidgetTheme
import com.example.aiwidgetstudio.ai.LocalWidgetDslGenerator
import com.example.aiwidgetstudio.ai.ModelManager
import com.example.aiwidgetstudio.data.local.dao.WidgetListEntry
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.domain.model.WidgetSize
import com.example.aiwidgetstudio.engine.runtime.RuntimeWidget
import com.example.aiwidgetstudio.engine.runtime.WidgetRuntime
import com.example.aiwidgetstudio.engine.state.WidgetStateCodec
import com.example.aiwidgetstudio.engine.state.WidgetStateEngine
import com.example.aiwidgetstudio.worker.RefreshWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen { LIST, EDITOR, SETTINGS }

data class ModelSettingsState(
    val fileName: String = "",
    val sizeBytes: Long = 0,
    val ready: Boolean = false,
    val importStatus: OperationStatus = OperationStatus.Idle,
    val downloadProgress: Float? = null
)

data class MainUiState(
    val screen: AppScreen = AppScreen.LIST,
    val prompt: String = "",
    val editorJson: String = "",
    val showAdvancedEditor: Boolean = false,
    val editingWidgetId: String? = null,
    val originalPrompt: String = "",
    val previewWidget: RuntimeWidget? = null,
    val selectedSize: WidgetSize = WidgetSize.MEDIUM,
    val warnings: List<String> = emptyList(),
    val validateStatus: OperationStatus = OperationStatus.Idle,
    val saveStatus: OperationStatus = OperationStatus.Idle,
    val generateStatus: OperationStatus = OperationStatus.Idle,
    val generatorMode: GeneratorMode = GeneratorMode.GEMINI,
    val widgetTheme: WidgetTheme = WidgetTheme.SYSTEM,
    val geminiApiKey: String = "",
    val capabilityWarning: String? = null,
    val capabilityHint: String? = null,
    val missingPermissions: List<PermissionRequest> = emptyList(),
    val modelSettings: ModelSettingsState = ModelSettingsState(),
    val error: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: WidgetRuntime,
    private val repository: WidgetRepository,
    private val geminiGenerator: GeminiWidgetGenerator,
    private val localGenerator: LocalWidgetDslGenerator,
    private val modelManager: ModelManager,
    private val generatorPreference: GeneratorPreference,
    private val capabilityChecker: CapabilityChecker,
    private val permissionManager: PermissionManager,
    private val dataSourceResolver: DataSourceResolver,
    private val stateEngine: WidgetStateEngine,
    private val stateCodec: WidgetStateCodec,
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
            generatorMode = generatorPreference.mode,
            widgetTheme = generatorPreference.widgetTheme,
            geminiApiKey = generatorPreference.geminiApiKey
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var generateJob: Job? = null

    init {
        viewModelScope.launch { refreshModelInfo() }
    }

    fun openCreate() {
        savedStateHandle[KEY_PROMPT] = ""
        savedStateHandle[KEY_EDITOR_JSON] = ""
        _uiState.value = MainUiState(
            screen = AppScreen.EDITOR,
            generatorMode = _uiState.value.generatorMode,
            modelSettings = _uiState.value.modelSettings
        )
    }

    fun openEditor(widgetId: String) {
        viewModelScope.launch {
            val stored = repository.getWidgetWithState(widgetId) ?: return@launch
            val processed = runtime.processDsl(stored.widget.dslJson).getOrNull()
            val preview = processed?.let {
                val state = stateCodec.decode(stored.state.stateJson, it.definition)
                RuntimeWidget(it.definition, state)
            }
            _uiState.update {
                it.copy(
                    screen = AppScreen.EDITOR,
                    prompt = "",
                    editorJson = stored.widget.dslJson,
                    editingWidgetId = widgetId,
                    originalPrompt = stored.widget.originalPrompt,
                    showAdvancedEditor = false,
                    previewWidget = preview,
                    error = null
                )
            }
            persistEditorState()
            validateDsl()
        }
    }

fun setWidgetSize(size: WidgetSize) {
        _uiState.update { it.copy(selectedSize = size) }
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

    fun setGeminiApiKey(key: String) {
        generatorPreference.geminiApiKey = key
        _uiState.update { it.copy(geminiApiKey = key) }
    }

    fun setGeneratorMode(mode: GeneratorMode) {
        generatorPreference.mode = mode
        _uiState.update { it.copy(generatorMode = mode) }
    }

    fun setWidgetTheme(theme: WidgetTheme) {
        generatorPreference.widgetTheme = theme
        _uiState.update { it.copy(widgetTheme = theme) }
    }

    fun validateDsl() {
        if (_uiState.value.validateStatus == OperationStatus.Loading) return
        _uiState.update { it.copy(validateStatus = OperationStatus.Loading, error = null) }

        val result = runtime.processDsl(_uiState.value.editorJson)
        val processed = result.getOrNull()
        if (processed == null) {
            _uiState.update {
                it.copy(
                    warnings = emptyList(),
                    validateStatus = OperationStatus.Error(result.exceptionOrNull()?.message ?: "Invalid widget DSL"),
                    error = result.exceptionOrNull()?.message ?: "Invalid widget DSL"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                warnings = processed.warnings.map { w -> w.message },
                validateStatus = OperationStatus.Success,
                error = null
            )
        }
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        _uiState.update { it.copy(generateStatus = OperationStatus.Idle) }
    }

    fun dismissCapabilityWarning() {
        _uiState.update { it.copy(capabilityWarning = null, capabilityHint = null) }
    }

    fun dismissPermissionRequest() {
        _uiState.update { it.copy(missingPermissions = emptyList()) }
        RefreshWorkScheduler.runNow(context)
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
            // On-device check first (instant)
            val onDeviceResult = capabilityChecker.checkOnDevice(prompt)
            if (!onDeviceResult.feasible) {
                _uiState.update {
                    it.copy(
                        capabilityWarning = onDeviceResult.warning,
                        capabilityHint = onDeviceResult.suggestion,
                        generateStatus = OperationStatus.Idle
                    )
                }
                return@launch
            }
            // Gemini mode: async feasibility check (non-blocking — runs alongside generation)
            if (_uiState.value.generatorMode == GeneratorMode.GEMINI) {
                launch {
                    val result = capabilityChecker.checkWithGemini(prompt) ?: return@launch
                    if (!result.feasible) {
                        _uiState.update {
                            it.copy(
                                capabilityWarning = result.warning,
                                capabilityHint = result.suggestion
                            )
                        }
                    }
                }
            }
            _uiState.update { it.copy(generateStatus = OperationStatus.Loading, error = null) }
            val existingDsl = if (_uiState.value.editingWidgetId != null) _uiState.value.editorJson.ifBlank { null } else null
            val sizeHint = if (_uiState.value.editingWidgetId == null) " Target size: ${_uiState.value.selectedSize.name} (${_uiState.value.selectedSize.label})." else ""
            generator.generate(prompt + sizeHint, existingDsl).collect { progress ->
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
                        _uiState.update { it.copy(editorJson = json, prompt = "") }
                        savedStateHandle[KEY_EDITOR_JSON] = json
                        validateDsl()
                        val processed = runtime.processDsl(json).getOrNull()
                        val preview = processed?.let {
                            val initial = stateEngine.createInitialState(it.definition)
                            val resolved = dataSourceResolver.resolveAll(it.definition.data.variables)
                            RuntimeWidget(it.definition, initial.copy(values = initial.values + resolved))
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

    fun saveWidget(onSaved: ((String) -> Unit)? = null) {
        if (_uiState.value.saveStatus == OperationStatus.Loading) return
        validateDsl()
        if (_uiState.value.validateStatus is OperationStatus.Error) return

        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(saveStatus = OperationStatus.Loading, error = null) }

            // Check permissions for data sources before saving
            val processed = runtime.processDsl(state.editorJson).getOrNull()
            if (processed == null) {
                _uiState.update { it.copy(saveStatus = OperationStatus.Error("Invalid widget DSL"), error = "Invalid widget DSL") }
                return@launch
            }
            val missing = permissionManager.missingPermissions(processed.definition)
            if (missing.isNotEmpty()) {
                _uiState.update { it.copy(saveStatus = OperationStatus.Idle, missingPermissions = missing) }
                return@launch
            }

            val result = if (state.editingWidgetId == null) {
                runtime.createWidget(state.editorJson, state.prompt).map { it.widgetId }
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
                onSaved?.invoke(widgetId)
                showList()
            }
        }
    }

    fun deleteWidget(widgetId: String) {
        viewModelScope.launch {
            val deleted = runtime.deleteWidget(widgetId)
            if (!deleted) _uiState.update { it.copy(error = "Remove this widget from the home screen before deleting it") }
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

    fun downloadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(modelSettings = it.modelSettings.copy(downloadProgress = 0f)) }
            val result = modelManager.downloadModel { progress ->
                _uiState.update { it.copy(modelSettings = it.modelSettings.copy(downloadProgress = progress)) }
            }
            localGenerator.invalidateEngine()
            viewModelScope.launch { localGenerator.warmUp() }
            refreshModelInfo()
            _uiState.update {
                it.copy(
                    modelSettings = it.modelSettings.copy(
                        downloadProgress = null,
                        importStatus = result.fold(
                            onSuccess = { OperationStatus.Success },
                            onFailure = { e -> OperationStatus.Error(e.message ?: "Download failed") }
                        )
                    ),
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(modelSettings = it.modelSettings.copy(importStatus = OperationStatus.Loading)) }
            val result = modelManager.importModel(uri)
            localGenerator.invalidateEngine()
            viewModelScope.launch { localGenerator.warmUp() }
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

    private suspend fun refreshModelInfo() {
        val info = modelManager.getModelInfo()
        _uiState.update {
            it.copy(modelSettings = it.modelSettings.copy(fileName = info.fileName, sizeBytes = info.sizeBytes, ready = info.ready))
        }
    }

    private fun persistEditorState() {
        savedStateHandle[KEY_PROMPT] = _uiState.value.prompt
        savedStateHandle[KEY_EDITOR_JSON] = _uiState.value.editorJson
    }

    companion object {
        private const val KEY_PROMPT = "prompt"
        private const val KEY_EDITOR_JSON = "editor_json"
    }
}
