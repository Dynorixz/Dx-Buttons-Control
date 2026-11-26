package com.dynorixz.buttonscontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.*
import com.dynorixz.buttonscontrol.core.*
import com.dynorixz.buttonscontrol.ui.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class QuickActionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DxApplication
        setContent { DxTheme {
            val s by app.settings.settings.collectAsStateWithLifecycle()
            RadialQuickActions(s.quickActions) { action ->
                val service = DxButtonAccessibilityService.current()
                if(service != null) { service.execute(action); finish() }
                else lifecycleScope.launch { app.diagnostics.result(ActionExecutor(AndroidActionPlatform(this@QuickActionsActivity, app.apps)).execute(action)); finish() }
            }
        } }
    }
}

@Composable private fun RadialQuickActions(actions: List<Action>, execute: (Action) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var accumulated by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val appIds = setOf("app", "music_app", "stopwatch", "find_phone")
    val labels = remember(actions) { actions.map { action ->
        if(action.id in appIds) runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(action.argument, 0)).toString() }.getOrDefault(ActionRegistry.title(action)) else ActionRegistry.title(action)
    } }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .onRotaryScrollEvent { event ->
            accumulated += event.verticalScrollPixels
            if(kotlin.math.abs(accumulated) > 20) { selected = (selected + if(accumulated > 0) 1 else actions.size - 1) % actions.size; accumulated = 0f }
            true
        }.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && event.key in listOf(Key.Enter, Key.DirectionCenter)) { execute(actions[selected]); true } else false
        }.focusRequester(focus).focusable(), contentAlignment = Alignment.Center) {
        val radius = minOf(maxWidth, maxHeight) * 0.30f
        actions.forEachIndexed { index, action ->
            val angle = -Math.PI / 2 + 2 * Math.PI * index / actions.size
            val color by animateColorAsState(if(selected == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "quick_color")
            Box(Modifier.offset(x = radius * cos(angle).toFloat(), y = radius * sin(angle).toFloat())
                .size(52.dp).background(color, CircleShape)
                .semantics { contentDescription = labels[index]; role = Role.Button }
                .clickable { selected = index; execute(action) }, contentAlignment = Alignment.Center) {
                val bitmap = remember(action) { if(action.id in appIds) runCatching { context.packageManager.getApplicationIcon(action.argument).toBitmap(40, 40).asImageBitmap() }.getOrNull() else null }
                if(bitmap != null) Image(bitmap, null, modifier = Modifier.size(29.dp))
                else Icon(actionIcon(ActionRegistry.spec(action.id)?.icon ?: "bolt"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(29.dp))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(67.dp)) {
            Text("DX", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(labels[selected], style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
