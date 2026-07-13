package com.example.aiwidgetstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiwidgetstudio.domain.model.UiNode
import com.example.aiwidgetstudio.domain.model.UiNodeMargin
import com.example.aiwidgetstudio.domain.model.UiNodeStyle
import com.example.aiwidgetstudio.domain.model.WidgetAlignment
import com.example.aiwidgetstudio.domain.model.WidgetDefinition
import com.example.aiwidgetstudio.engine.state.WidgetState
import com.example.aiwidgetstudio.glance.BindingResolver

@Composable
fun WidgetPreview(definition: WidgetDefinition, state: WidgetState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFAFAFA))
            .padding(12.dp)
    ) {
        PreviewNode(definition, state, definition.ui, depth = 1)
    }
}

@Composable
private fun PreviewNode(definition: WidgetDefinition, state: WidgetState, node: UiNode, depth: Int) {
    if (depth > 10) return

    when (node) {
        is UiNode.Column -> {
            Column(
                modifier = styleModifier(node.style),
                verticalArrangement = verticalArrangement(node.style.alignment),
                horizontalAlignment = horizontalAlignment(node.style.alignment)
            ) {
                node.children.take(20).forEach { PreviewNode(definition, state, it, depth + 1) }
            }
        }

        is UiNode.Row -> {
            Row(
                modifier = styleModifier(node.style),
                verticalAlignment = rowVerticalAlignment(node.style.alignment),
                horizontalArrangement = horizontalArrangement(node.style.arrangement)
            ) {
                node.children.take(20).forEach { PreviewNode(definition, state, it, depth + 1) }
            }
        }

        is UiNode.Box -> {
            Box(
                modifier = styleModifier(node.style),
                contentAlignment = boxAlignment(node.style.alignment)
            ) {
                PreviewNode(definition, state, node.child, depth + 1)
            }
        }

        is UiNode.Card -> {
            val bg = parseColor(node.style.backgroundColor) ?: Color(0xFFECEFF1)
            Box(
                modifier = styleModifier(node.style)
                    .clip(RoundedCornerShape((node.style.cornerRadius ?: 8).coerceIn(0, 64).dp))
                    .background(bg)
                    .padding((node.style.cornerRadius ?: 8).coerceIn(0, 64).dp)
            ) {
                PreviewNode(definition, state, node.child, depth + 1)
            }
        }

        is UiNode.Text -> {
            val color = parseColor(node.style.textColor) ?: Color(0xFF212121)
            Text(
                text = BindingResolver.resolveText(node.value, state),
                modifier = styleModifier(node.style),
                color = color,
                fontSize = 14.sp
            )
        }

        is UiNode.Icon -> {
            Text(
                text = node.icon.ifBlank { "?" },
                modifier = styleModifier(node.style),
                fontSize = 20.sp
            )
        }

        is UiNode.Button -> {
            val bg = parseColor(node.style.backgroundColor) ?: Color(0xFF1976D2)
            val textColor = parseColor(node.style.textColor) ?: Color.White
            Box(
                modifier = styleModifier(node.style)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = node.text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        is UiNode.Progress -> {
            val ratio = BindingResolver.progressRatio(node.current, node.max, state)
            val track = parseColor(node.style.backgroundColor) ?: Color(0xFFE0E0E0)
            val indicator = parseColor(node.style.textColor) ?: Color(0xFF4CAF50)
            LinearProgressIndicator(
                progress = { ratio },
                modifier = styleModifier(node.style).fillMaxWidth().height(8.dp),
                color = indicator,
                trackColor = track
            )
        }

        is UiNode.Spacer -> {
            Spacer(
                modifier = Modifier
                    .then(node.width?.let { Modifier.width(it.coerceIn(0, 300).dp) } ?: Modifier)
                    .then(node.height?.let { Modifier.height(it.coerceIn(0, 300).dp) } ?: Modifier.height(8.dp))
            )
        }

        is UiNode.Divider -> {
            Box(
                modifier = styleModifier(node.style)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFBDBDBD))
            )
        }
    }
}

private fun styleModifier(style: UiNodeStyle): Modifier {
    var mod = Modifier as Modifier
    style.padding?.let { mod = mod.padding(it.toPaddingValues()) }
    style.margin?.let { mod = mod.padding(it.toPaddingValues()) }
    style.width?.let { mod = mod.width(it.coerceIn(0, 300).dp) }
    style.height?.let { mod = mod.height(it.coerceIn(0, 300).dp) }
    parseColor(style.backgroundColor)?.let { mod = mod.background(it) }
    return mod
}

private fun UiNodeMargin.toPaddingValues() = androidx.compose.foundation.layout.PaddingValues(
    start = (left ?: 0).coerceIn(0, 300).dp,
    top = (top ?: 0).coerceIn(0, 300).dp,
    end = (right ?: 0).coerceIn(0, 300).dp,
    bottom = (bottom ?: 0).coerceIn(0, 300).dp
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

private fun horizontalAlignment(a: WidgetAlignment?) = when (a) {
    WidgetAlignment.CENTER -> Alignment.CenterHorizontally
    WidgetAlignment.END -> Alignment.End
    else -> Alignment.Start
}

private fun verticalArrangement(a: WidgetAlignment?) = when (a) {
    WidgetAlignment.CENTER -> Arrangement.Center
    WidgetAlignment.END -> Arrangement.Bottom
    else -> Arrangement.Top
}

private fun horizontalArrangement(a: WidgetAlignment?) = when (a) {
    WidgetAlignment.CENTER -> Arrangement.Center
    WidgetAlignment.END -> Arrangement.End
    else -> Arrangement.Start
}

private fun rowVerticalAlignment(a: WidgetAlignment?) = when (a) {
    WidgetAlignment.CENTER -> Alignment.CenterVertically
    WidgetAlignment.END -> Alignment.Bottom
    else -> Alignment.Top
}

private fun boxAlignment(a: WidgetAlignment?) = when (a) {
    WidgetAlignment.CENTER -> Alignment.Center
    WidgetAlignment.END -> Alignment.BottomEnd
    else -> Alignment.TopStart
}
