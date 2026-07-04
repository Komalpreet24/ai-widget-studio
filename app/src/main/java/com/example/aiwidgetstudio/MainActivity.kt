package com.example.aiwidgetstudio

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aiwidgetstudio.data.local.dao.WidgetListEntry
import com.example.aiwidgetstudio.domain.model.VariableValue
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.glance.WidgetGlanceReceiver
import com.example.aiwidgetstudio.presentation.AppScreen
import com.example.aiwidgetstudio.presentation.MainUiState
import com.example.aiwidgetstudio.presentation.MainViewModel
import com.example.aiwidgetstudio.presentation.OperationStatus
import com.example.aiwidgetstudio.presentation.ParsedSummary
import com.example.aiwidgetstudio.ui.theme.AIWidgetStudioTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormat
import java.util.Date

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLinkWidgetId = intent?.getStringExtra(EXTRA_WIDGET_ID)
        setContent {
            AIWidgetStudioTheme {
                WidgetStudioApp(viewModel = viewModel, deepLinkWidgetId = deepLinkWidgetId)
            }
        }
    }

    companion object {
        const val EXTRA_WIDGET_ID = "widgetId"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WidgetStudioApp(viewModel: MainViewModel, deepLinkWidgetId: String?) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsState()
    val widgets by viewModel.widgets.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(deepLinkWidgetId) {
        deepLinkWidgetId?.let {
            viewModel.openDetail(it)
            navController.navigate("detail/$it") { launchSingleTop = true }
        }
    }

    LaunchedEffect(state.screen) {
        val route = when (state.screen) {
            AppScreen.LIST -> "list"
            AppScreen.EDITOR -> "editor"
            AppScreen.DETAIL -> state.selectedWidgetId?.let { "detail/$it" } ?: "list"
            AppScreen.SETTINGS -> "settings"
        }
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                launchSingleTop = true
                popUpTo("list") { inclusive = route == "list" }
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle(state), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (state.screen != AppScreen.LIST) {
                        IconButton(onClick = {
                            viewModel.showList()
                            navController.popBackStack("list", false)
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (state.screen == AppScreen.LIST) {
                        TextButton(onClick = {
                            viewModel.showSettings()
                            navController.navigate("settings")
                        }) {
                            Text("AI Model")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "list",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable("list") {
                WidgetListScreen(
                    widgets = widgets,
                    onCreate = {
                        viewModel.openCreate()
                        navController.navigate("editor")
                    },
                    onOpen = { widgetId ->
                        viewModel.openDetail(widgetId)
                        navController.navigate("detail/$widgetId")
                    },
                    onEdit = { widgetId ->
                        viewModel.openEditor(widgetId)
                        navController.navigate("editor")
                    },
                    onDelete = { widgetId ->
                        viewModel.openDetail(widgetId)
                        viewModel.deleteSelectedWidget()
                    }
                )
            }
            composable("editor") {
                EditorScreen(
                    state = state,
                    onPromptChanged = viewModel::updatePrompt,
                    onJsonChanged = viewModel::updateEditorJson,
                    onGenerate = viewModel::generateDsl,
                    onValidate = viewModel::validateDsl,
                    onSave = viewModel::saveWidget
                )
            }
            composable("detail/{widgetId}") {
                DetailScreen(
                    state = state,
                    onAction = viewModel::applyAction,
                    onEdit = {
                        state.selectedWidgetId?.let { widgetId ->
                            viewModel.openEditor(widgetId)
                            navController.navigate("editor")
                        }
                    },
                    onDelete = viewModel::deleteSelectedWidget,
                    onPin = { requestPinWidget(context) }
                )
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    onImport = viewModel::importModel,
                    onRemove = viewModel::removeModel
                )
            }
        }
    }
}

@Composable
private fun WidgetListScreen(
    widgets: List<WidgetListEntry>,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (widgets.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No widgets yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap the button below to create your first AI-powered home screen widget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(widgets, key = { it.widget.widgetId }) { entry ->
                    WidgetCard(
                        entry = entry,
                        onOpen = { onOpen(entry.widget.widgetId) },
                        onEdit = { onEdit(entry.widget.widgetId) },
                        onDelete = { onDelete(entry.widget.widgetId) }
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreate,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Create widget") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }
}

@Composable
private fun WidgetCard(
    entry: WidgetListEntry,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(entry.widget.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    if (entry.placementCount > 0) append("On home screen · ") else append("Not placed · ")
                    append("Updated ${formatDate(entry.widget.updatedAt)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                if (entry.placementCount == 0) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(
    state: MainUiState,
    onPromptChanged: (String) -> Unit,
    onJsonChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!state.modelSettings.ready) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚠ No AI model loaded. Go to AI Model settings to import a .litertlm file, or write DSL JSON manually below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Prompt section
        Text("Generate from prompt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChanged,
            label = { Text("Describe your widget") },
            placeholder = { Text("e.g. A water tracker with daily goal") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Button(
            onClick = onGenerate,
            enabled = state.generateStatus != OperationStatus.Loading && state.modelSettings.ready,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.generateStatus == OperationStatus.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Generating…")
            } else {
                Text("Generate DSL")
            }
        }

        // DSL editor section
        Text("Widget DSL", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.editorJson,
            onValueChange = onJsonChanged,
            label = { Text("JSON") },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        )

        state.parsedSummary?.let { SummaryCard(it) }

        if (state.warnings.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.warnings.forEach { warning ->
                    Text(
                        "• $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onValidate, modifier = Modifier.weight(1f)) { Text("Validate") }
            Button(
                onClick = onSave,
                enabled = state.saveStatus != OperationStatus.Loading &&
                    state.validateStatus !is OperationStatus.Error,
                modifier = Modifier.weight(1f)
            ) {
                if (state.saveStatus == OperationStatus.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.saveStatus == OperationStatus.Loading) "Saving…" else "Save")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryCard(summary: ParsedSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(summary.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${summary.variableCount} variables · ${summary.actionCount} actions · ${summary.uiNodeCount} UI nodes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun DetailScreen(
    state: MainUiState,
    onAction: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit
) {
    if (state.validateStatus == OperationStatus.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val widget = state.runtimeWidget ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(widget.definition.metadata.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (state.placementCount > 0) "On home screen (${state.placementCount} placement${if (state.placementCount > 1) "s" else ""})"
                else "Not placed on home screen yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // State values
        if (widget.state.values.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Current state", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    widget.state.values.forEach { (name, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            Text(displayValue(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Actions
        val interactiveActions = widget.definition.actions.filter {
            it !is WidgetAction.OpenApp && it !is WidgetAction.OpenUrl
        }
        if (interactiveActions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Actions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                interactiveActions.forEach { action ->
                    FilledTonalButton(
                        onClick = { onAction(action.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(action.id)
                    }
                }
            }
        }

        // Pin
        Button(onClick = onPin, modifier = Modifier.fillMaxWidth()) {
            Text("Add to home screen")
        }

        // Edit / Delete
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
            OutlinedButton(
                onClick = onDelete,
                enabled = state.placementCount == 0,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        }

        if (state.placementCount > 0) {
            Text(
                "Remove this widget from your home screen before deleting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsScreen(
    state: MainUiState,
    onImport: (android.net.Uri) -> Unit,
    onRemove: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    val settings = state.modelSettings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (settings.ready) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (settings.ready) "Model loaded" else "No model loaded",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (settings.ready) {
                    Text(settings.fileName, style = MaterialTheme.typography.bodySmall)
                    Text("${settings.sizeBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "Import a .litertlm model file to enable AI-powered widget generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // How to get a model
        if (!settings.ready) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How to get a model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "This app uses Google's LiteRT-LM runtime and requires a .litertlm model file.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Recommended model:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Gemma 3n E2B (int4) — ~2 GB, runs on-device with GPU acceleration.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Steps:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Text("1. Tap \"Download model\" below to open the download page.", style = MaterialTheme.typography.bodySmall)
                    Text("2. Download the .litertlm file to your device.", style = MaterialTheme.typography.bodySmall)
                    Text("3. Once downloaded, tap \"Import model\" and select the file.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Without a model you can still create widgets by writing or pasting DSL JSON manually in the editor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            OutlinedButton(
                onClick = { uriHandler.openUri("https://ai.google.dev/edge/litert/models") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download model from Google AI Edge")
            }
        }

        if (settings.status == OperationStatus.Loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Importing model…", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = { launcher.launch(arrayOf("*/*")) },
            enabled = settings.status != OperationStatus.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (settings.ready) "Replace model" else "Import model")
        }

        if (settings.ready) {
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Remove model")
            }
        }
    }
}

private fun requestPinWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, WidgetGlanceReceiver::class.java)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(provider, null, null)
    }
}

private fun displayValue(value: VariableValue): String = when (value) {
    is VariableValue.BooleanValue -> value.value.toString()
    is VariableValue.DoubleValue -> value.value.toString()
    is VariableValue.IntValue -> value.value.toString()
    is VariableValue.StringValue -> value.value
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

private fun screenTitle(state: MainUiState): String = when (state.screen) {
    AppScreen.LIST -> "AI Widget Studio"
    AppScreen.EDITOR -> if (state.editingWidgetId == null) "Create widget" else "Edit widget"
    AppScreen.DETAIL -> "Widget"
    AppScreen.SETTINGS -> "AI Model"
}
