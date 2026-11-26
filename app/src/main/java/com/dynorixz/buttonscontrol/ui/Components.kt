package com.dynorixz.buttonscontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.wear.compose.material3.*
import com.dynorixz.buttonscontrol.core.Gesture
import kotlinx.coroutines.launch

fun gestureTitle(g: Gesture) = when(g) { Gesture.HOLD -> "Удержание"; Gesture.DOUBLE -> "Двойное нажатие"; Gesture.TRIPLE -> "Тройное нажатие" }
fun actionIcon(name: String): ImageVector = when(name) {
    "home" -> Icons.Default.Home; "notifications" -> Icons.Default.Notifications
    "back" -> Icons.AutoMirrored.Filled.ArrowBack; "lock" -> Icons.Default.Lock
    "play" -> Icons.Default.PlayArrow; "settings" -> Icons.Default.Settings
    else -> DxIcons.get(name)
}
@Composable fun DxList(title: String, subtitle: String? = null, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LazyColumn(state = state, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 38.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .onRotaryScrollEvent { scope.launch { state.scrollBy(it.verticalScrollPixels) }; true }
            .focusRequester(focus).focusable()) {
        item { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        } }
        content()
        item { Spacer(Modifier.height(20.dp)) }
    }
}
@Composable fun DxCard(title: String, detail: String? = null, icon: String = "settings", onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(actionIcon(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
@Composable fun DxButton(title: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), label = { Text(title) })
}
@Composable fun DxToggle(title: String, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    SwitchButton(checked = checked, onCheckedChange = onChange, modifier = Modifier.fillMaxWidth(), label = { Text(title) },
        secondaryLabel = detail?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } })
}
@Composable fun DxInfo(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(6.dp)) }
@Composable fun DxInput(value: String, hint: String, onChange: (String) -> Unit) {
    BasicTextField(value = value, onValueChange = onChange, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp)).padding(14.dp),
        decorationBox = { inner -> Column { if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
}
