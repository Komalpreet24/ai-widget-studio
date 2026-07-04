package com.example.aiwidgetstudio.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.aiwidgetstudio.MainActivity
import com.example.aiwidgetstudio.data.local.dao.WidgetListEntry
import com.example.aiwidgetstudio.data.repository.WidgetRepository
import com.example.aiwidgetstudio.glance.WidgetGlanceAppWidget
import com.example.aiwidgetstudio.ui.theme.AIWidgetStudioTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetConfigureActivity : ComponentActivity() {

    private val viewModel: ConfigureViewModel by viewModels()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            AIWidgetStudioTheme {
                ConfigureScreen(
                    viewModel = viewModel,
                    onSelect = { widgetId -> bindWidget(widgetId) },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    private fun bindWidget(widgetId: String) {
        viewModel.bind(appWidgetId, widgetId) {
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)
            lifecycleScope.launch {
                val manager = GlanceAppWidgetManager(this@WidgetConfigureActivity)
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                WidgetGlanceAppWidget().update(this@WidgetConfigureActivity, glanceId)
                finish()
            }
        }
    }
}

@HiltViewModel
class ConfigureViewModel @Inject constructor(
    private val repository: WidgetRepository
) : ViewModel() {

    val widgets = repository.observeWidgetListEntries().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun bind(appWidgetId: Int, widgetId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveInstance(appWidgetId, widgetId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureScreen(
    viewModel: ConfigureViewModel,
    onSelect: (String) -> Unit,
    onOpenApp: () -> Unit
) {
    val widgets by viewModel.widgets.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Choose widget") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (widgets.isEmpty()) {
                Text("No saved widgets yet")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
                    Text("Open app")
                }
                return@Column
            }

            LazyColumn {
                items(widgets, key = { it.widget.widgetId }) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(entry.widget.widgetId) }
                            .padding(vertical = 14.dp)
                    ) {
                        Text(entry.widget.name, style = MaterialTheme.typography.titleMedium)
                        Text("${entry.placementCount} placements", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
