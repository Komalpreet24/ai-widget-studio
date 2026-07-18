package com.example.aiwidgetstudio.glance

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.aiwidgetstudio.MainActivity
import com.example.aiwidgetstudio.di.WidgetRuntimeEntryPoint
import com.example.aiwidgetstudio.domain.model.UiNode
import com.example.aiwidgetstudio.domain.model.UiNodeMargin
import com.example.aiwidgetstudio.domain.model.UiNodeStyle
import com.example.aiwidgetstudio.domain.model.WidgetAction
import com.example.aiwidgetstudio.domain.model.WidgetAlignment
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import com.example.aiwidgetstudio.engine.state.WidgetState
import dagger.hilt.android.EntryPointAccessors

object WidgetGlanceContent {

    private const val MAX_UI_DEPTH = 10
    private const val MAX_UI_NODES = 100
    private const val MAX_UI_CHILDREN = 20

    val WidgetIdKey = ActionParameters.Key<String>("widgetId")
    val ActionIdKey = ActionParameters.Key<String>("actionId")

    @Composable
    fun Widget(
        context: Context,
        widgetId: String,
        definition: WidgetDefinition,
        state: WidgetState
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFFFAFAFA))
                .padding(12.dp),
            verticalAlignment = Alignment.Vertical.Top,
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            RenderNode(context, widgetId, definition, state, definition.ui, depth = 1, nodesVisited = 0)
        }
    }

    @Composable
    fun Loading() {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Loading…", style = TextStyle(fontSize = 14.sp))
        }
    }

    @Composable
    fun Fallback(widgetId: String) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(8.dp)
                .then(
                    if (widgetId.isNotBlank()) {
                        GlanceModifier.clickable(openAppAction(context, widgetId))
                    } else {
                        GlanceModifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Unable to render widget",
                style = TextStyle(fontSize = 14.sp)
            )
        }
    }

    @Composable
    private fun RenderNode(
        context: Context,
        widgetId: String,
        definition: WidgetDefinition,
        state: WidgetState,
        node: UiNode,
        depth: Int,
        nodesVisited: Int
    ) {
        if (nodesVisited >= MAX_UI_NODES || depth > MAX_UI_DEPTH) return

        val modifier = buildStyleModifier(node.style)

        when (node) {
            is UiNode.Column -> {
                Column(
                    modifier = modifier.fillMaxWidth(),
                    verticalAlignment = mapVerticalAlignment(node.style.alignment),
                    horizontalAlignment = mapHorizontalAlignment(node.style.alignment)
                ) {
                    var visited = nodesVisited + 1
                    node.children.take(MAX_UI_CHILDREN).forEach { child ->
                        if (visited >= MAX_UI_NODES) return@forEach
                        RenderNode(context, widgetId, definition, state, child, depth + 1, visited)
                        visited++
                    }
                }
            }

            is UiNode.Row -> {
                Row(
                    modifier = modifier.fillMaxWidth(),
                    verticalAlignment = mapRowVerticalAlignment(node.style.alignment),
                    horizontalAlignment = mapHorizontalAlignment(node.style.arrangement)
                ) {
                    var visited = nodesVisited + 1
                    node.children.take(MAX_UI_CHILDREN).forEach { child ->
                        if (visited >= MAX_UI_NODES) return@forEach
                        RenderNode(context, widgetId, definition, state, child, depth + 1, visited)
                        visited++
                    }
                }
            }

            is UiNode.Box -> {
                Box(
                    modifier = modifier.fillMaxWidth(),
                    contentAlignment = mapBoxAlignment(node.style.alignment)
                ) {
                    RenderNode(context, widgetId, definition, state, node.child, depth + 1, nodesVisited + 1)
                }
            }

            is UiNode.Card -> {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .background(parseColor(node.style.backgroundColor) ?: Color(0xFFECEFF1))
                        .padding(clampDp(node.style.cornerRadius, 0, 64).dp)
                ) {
                    RenderNode(context, widgetId, definition, state, node.child, depth + 1, nodesVisited + 1)
                }
            }

            is UiNode.Text -> {
                val textColor = parseColor(node.style.textColor)
                Text(
                    text = BindingResolver.resolveText(node.value, state),
                    modifier = modifier,
                    style = if (textColor != null) TextStyle(color = ColorProvider(textColor), fontSize = 14.sp)
                        else TextStyle(fontSize = 14.sp)
                )
            }

            is UiNode.Icon -> {
                Text(
                    text = node.icon.ifBlank { "?" },
                    modifier = modifier,
                    style = TextStyle(fontSize = 20.sp)
                )
            }

            is UiNode.Button -> {
                val action = definition.actions.find { it.id == node.action }
                val clickModifier = when (action) {
                    is WidgetAction.OpenApp -> modifier.clickable(openAppAction(context, widgetId))
                    is WidgetAction.OpenUrl -> if (isValidUrl(action.url)) {
                        modifier.clickable(openUrlAction(action.url))
                    } else {
                        modifier
                    }
                    null -> modifier
                    else -> modifier.clickable(
                        actionRunCallback<WidgetActionCallback>(
                            actionParametersOf(
                                WidgetIdKey to widgetId,
                                ActionIdKey to node.action
                            )
                        )
                    )
                }
                val btnBg = parseColor(node.style.backgroundColor) ?: Color(0xFF1976D2)
                val btnTextColor = parseColor(node.style.textColor) ?: Color.White
                Box(
                    modifier = clickModifier
                        .background(btnBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = node.text,
                        style = TextStyle(
                            color = ColorProvider(btnTextColor),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            is UiNode.Progress -> {
                val ratio = BindingResolver.progressRatio(node.current, node.max, state)
                val trackColor = parseColor(node.style.backgroundColor) ?: Color(0xFFE0E0E0)
                val indicatorColor = parseColor(node.style.textColor) ?: Color(0xFF4CAF50)
                Column(modifier = modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = ratio,
                        modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                        color = ColorProvider(indicatorColor),
                        backgroundColor = ColorProvider(trackColor)
                    )
                }
            }

            is UiNode.Spacer -> {
                Spacer(
                    modifier = modifier
                        .then(
                            node.width?.let { GlanceModifier.width(clampDp(it, 0, 300).dp) }
                                ?: GlanceModifier
                        )
                        .then(
                            node.height?.let { GlanceModifier.height(clampDp(it, 0, 300).dp) }
                                ?: GlanceModifier.height(8.dp)
                        )
                )
            }

            is UiNode.Divider -> {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFBDBDBD))
                ) {}
            }
        }
    }

    private fun openAppAction(context: Context, widgetId: String) = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_WIDGET_ID, widgetId)
        }
    )

    private fun openUrlAction(url: String) = actionStartActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
    )

    private fun isValidUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme in setOf("http", "https")
    }

    private fun buildStyleModifier(style: UiNodeStyle): GlanceModifier {
        var mod: GlanceModifier = GlanceModifier
        style.padding?.let { mod = mod.then(marginToPadding(it)) }
        style.margin?.let { mod = mod.then(marginToPadding(it)) }
        style.width?.let { mod = mod.width(clampDp(it, 0, 300).dp) }
        style.height?.let { mod = mod.height(clampDp(it, 0, 300).dp) }
        parseColor(style.backgroundColor)?.let { mod = mod.background(it) }
        return mod
    }

    private fun marginToPadding(margin: UiNodeMargin): GlanceModifier = GlanceModifier.padding(
        start = clampDp(margin.left ?: 0, 0, 300).dp,
        top = clampDp(margin.top ?: 0, 0, 300).dp,
        end = clampDp(margin.right ?: 0, 0, 300).dp,
        bottom = clampDp(margin.bottom ?: 0, 0, 300).dp
    )

    private fun parseColor(value: String?): Color? {
        if (value.isNullOrBlank()) return null
        val normalized = value.removePrefix("#")
        val parsed = normalized.toLongOrNull(16) ?: return null
        return when (normalized.length) {
            6 -> Color(0xFF000000 or parsed)
            8 -> Color(parsed)
            else -> null
        }
    }

    private fun clampDp(value: Int?, min: Int, max: Int): Int = (value ?: 0).coerceIn(min, max)

    private fun mapHorizontalAlignment(alignment: WidgetAlignment?) = when (alignment) {
        WidgetAlignment.CENTER -> Alignment.Horizontal.CenterHorizontally
        WidgetAlignment.END -> Alignment.Horizontal.End
        else -> Alignment.Horizontal.Start
    }

    private fun mapVerticalAlignment(alignment: WidgetAlignment?) = when (alignment) {
        WidgetAlignment.CENTER -> Alignment.Vertical.CenterVertically
        WidgetAlignment.END -> Alignment.Vertical.Bottom
        else -> Alignment.Vertical.Top
    }

    private fun mapRowVerticalAlignment(alignment: WidgetAlignment?) = when (alignment) {
        WidgetAlignment.CENTER -> Alignment.Vertical.CenterVertically
        WidgetAlignment.END -> Alignment.Vertical.Bottom
        else -> Alignment.Vertical.Top
    }

    private fun mapBoxAlignment(alignment: WidgetAlignment?) = when (alignment) {
        WidgetAlignment.CENTER -> Alignment.Center
        WidgetAlignment.END -> Alignment.BottomEnd
        else -> Alignment.TopStart
    }
}

class WidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val widgetId = parameters[WidgetGlanceContent.WidgetIdKey] ?: return
        val actionId = parameters[WidgetGlanceContent.ActionIdKey] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetRuntimeEntryPoint::class.java
        )
        entryPoint.widgetRuntime().applyAction(widgetId, actionId)
    }
}
