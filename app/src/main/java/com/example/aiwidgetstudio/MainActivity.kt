package com.example.aiwidgetstudio

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
        }
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                launchSingleTop = true
                popUpTo("list") { inclusive = route == "list" }
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    onToggleAdvanced = viewModel::toggleAdvancedEditor,
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
    onToggleAdvanced: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Describe your widget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChanged,
            placeholder = { Text("e.g. A water tracker that counts glasses per day with +1 and -1 buttons, resets daily at midnight") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(
            onClick = onGenerate,
            enabled = state.generateStatus != OperationStatus.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.generateStatus == OperationStatus.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Creating widget…")
            } else {
                Text("Create with AI")
            }
        }

        state.parsedSummary?.let { summary ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("✓ Widget ready", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(summary.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${summary.variableCount} variables · ${summary.actionCount} actions · ${summary.uiNodeCount} UI elements",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = state.saveStatus != OperationStatus.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saveStatus == OperationStatus.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.editingWidgetId == null) "Save widget" else "Update widget")
            }
        }

        if (state.warnings.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.warnings.forEach { warning ->
                    Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        TextButton(onClick = onToggleAdvanced) {
            Text(if (state.showAdvancedEditor) "Hide advanced editor" else "Advanced: paste JSON manually")
        }

        AnimatedVisibility(visible = state.showAdvancedEditor) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.editorJson,
                    onValueChange = onJsonChanged,
                    label = { Text("Widget JSON") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { onJsonChanged(state.editorJson) }, modifier = Modifier.weight(1f)) { Text("Validate") }
                    Button(onClick = onSave, enabled = state.saveStatus != OperationStatus.Loading, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
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
        Column {
            Text(widget.definition.metadata.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (state.placementCount > 0) "On home screen (${state.placementCount} placement${if (state.placementCount > 1) "s" else ""})"
                else "Not placed on home screen yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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

        val interactiveActions = widget.definition.actions.filter {
            it !is WidgetAction.OpenApp && it !is WidgetAction.OpenUrl
        }
        if (interactiveActions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Actions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                interactiveActions.forEach { action ->
                    FilledTonalButton(onClick = { onAction(action.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(formatActionLabel(action))
                    }
                }
            }
        }

        Button(onClick = onPin, modifier = Modifier.fillMaxWidth()) { Text("Add to home screen") }

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

private fun formatActionLabel(action: WidgetAction): String =
    action.id.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace("_", " ")
        .replaceFirstChar { it.uppercase() }

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

private fun screenTitle(state: MainUiState): String = when (state.screen) {
    AppScreen.LIST -> "AI Widget Studio"
    AppScreen.EDITOR -> if (state.editingWidgetId == null) "Create widget" else "Edit widget"
    AppScreen.DETAIL -> "Widget"
}
