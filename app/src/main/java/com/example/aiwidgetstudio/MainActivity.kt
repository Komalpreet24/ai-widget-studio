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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                WidgetStudioApp(
                    viewModel = viewModel,
                    deepLinkWidgetId = deepLinkWidgetId
                )
            }
        }
    }

    companion object {
        const val EXTRA_WIDGET_ID = "widgetId"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WidgetStudioApp(
    viewModel: MainViewModel,
    deepLinkWidgetId: String?
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsState()
    val widgets by viewModel.widgets.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(deepLinkWidgetId) {
        deepLinkWidgetId?.let {
            viewModel.openDetail(it)
            navController.navigate("detail/$it") {
                launchSingleTop = true
            }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle(state)) },
                navigationIcon = {
                    if (state.screen != AppScreen.LIST) {
                        TextButton(onClick = {
                            viewModel.showList()
                            navController.popBackStack("list", false)
                        }) {
                            Text("Back")
                        }
                    }
                },
                actions = {
                    if (state.screen == AppScreen.LIST) {
                        TextButton(onClick = {
                            viewModel.showSettings()
                            navController.navigate("settings")
                        }) {
                            Text("Model")
                        }
                    }
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "list",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
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

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
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
    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
        Text("Create widget")
    }
    Spacer(Modifier.height(16.dp))

    if (widgets.isEmpty()) {
        Text("No widgets yet")
        return
    }

    LazyColumn {
        items(widgets, key = { it.widget.widgetId }) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(entry.widget.widgetId) }
                    .padding(vertical = 14.dp)
            ) {
                Text(entry.widget.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Placed ${entry.placementCount} times · Updated ${formatDate(entry.widget.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEdit(entry.widget.widgetId) }) { Text("Edit") }
                    if (entry.placementCount == 0) {
                        TextButton(onClick = { onDelete(entry.widget.widgetId) }) { Text("Delete") }
                    }
                }
            }
            HorizontalDivider()
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
    OutlinedTextField(
        value = state.prompt,
        onValueChange = onPromptChanged,
        label = { Text("Prompt") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onGenerate,
            enabled = state.generateStatus != OperationStatus.Loading,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (state.generateStatus == OperationStatus.Loading) "Generating" else "Generate")
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.editorJson,
        onValueChange = onJsonChanged,
        label = { Text("Widget DSL") },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    )
    Spacer(Modifier.height(12.dp))
    state.parsedSummary?.let { SummaryCard(it) }
    LazyColumn(modifier = Modifier.height(100.dp)) {
        items(state.warnings) { warning ->
            Text(warning, style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onValidate, modifier = Modifier.weight(1f)) {
            Text("Validate")
        }
        Button(
            onClick = onSave,
            enabled = state.saveStatus != OperationStatus.Loading &&
                state.validateStatus !is OperationStatus.Error,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (state.saveStatus == OperationStatus.Loading) "Saving" else "Save")
        }
    }
}

@Composable
private fun SummaryCard(summary: ParsedSummary) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("Name: ${summary.name}", style = MaterialTheme.typography.bodyMedium)
        Text("Variables: ${summary.variableCount}")
        Text("Actions: ${summary.actionCount}")
        Text("UI nodes: ${summary.uiNodeCount}")
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
        CircularProgressIndicator()
        return
    }

    val widget = state.runtimeWidget ?: return

    Text(widget.definition.metadata.name, style = MaterialTheme.typography.headlineSmall)
    Text("Placed ${state.placementCount} times")
    Spacer(Modifier.height(16.dp))

    widget.state.values.forEach { (name, value) ->
        Text("$name: ${displayValue(value)}")
    }

    Spacer(Modifier.height(16.dp))
    widget.definition.actions.forEach { action ->
        if (action !is WidgetAction.OpenApp && action !is WidgetAction.OpenUrl) {
            OutlinedButton(
                onClick = { onAction(action.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(action.id)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Button(onClick = onPin, modifier = Modifier.fillMaxWidth()) {
        Text("Add to home screen")
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
            Text("Edit")
        }
        OutlinedButton(
            onClick = onDelete,
            enabled = state.placementCount == 0,
            modifier = Modifier.weight(1f)
        ) {
            Text("Delete")
        }
    }
}

@Composable
private fun SettingsScreen(
    state: MainUiState,
    onImport: (android.net.Uri) -> Unit,
    onRemove: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImport)
    }

    val settings = state.modelSettings
    if (settings.ready) {
        Text("Model: ${settings.fileName}")
        Text("Size: ${settings.sizeBytes / (1024 * 1024)} MB")
    } else {
        Text("No on-device model imported")
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { launcher.launch(arrayOf("*/*")) },
        enabled = settings.status != OperationStatus.Loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (settings.ready) "Replace model" else "Import model")
    }
    if (settings.ready) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
            Text("Remove model")
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

private fun displayValue(value: VariableValue): String {
    return when (value) {
        is VariableValue.BooleanValue -> value.value.toString()
        is VariableValue.DoubleValue -> value.value.toString()
        is VariableValue.IntValue -> value.value.toString()
        is VariableValue.StringValue -> value.value
    }
}

private fun formatDate(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(timestamp))
}

private fun screenTitle(state: MainUiState): String {
    return when (state.screen) {
        AppScreen.LIST -> "AI Widget Studio"
        AppScreen.EDITOR -> if (state.editingWidgetId == null) "Create widget" else "Edit widget"
        AppScreen.DETAIL -> "Widget"
        AppScreen.SETTINGS -> "Model"
    }
}
