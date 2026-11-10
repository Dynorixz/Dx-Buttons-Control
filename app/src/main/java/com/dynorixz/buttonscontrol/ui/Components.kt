package com.dynorixz.buttonscontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import com.dynorixz.buttonscontrol.core.Gesture
import kotlinx.coroutines.launch

fun gestureTitle(g: Gesture) = when(g) { Gesture.HOLD -> "Удержание"; Gesture.DOUBLE -> "Двойное"; Gesture.TRIPLE -> "Тройное" }
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
    LazyColumn(state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .onRotaryScrollEvent { scope.launch { state.scrollBy(it.verticalScrollPixels) }; true }.focusRequester(focus).focusable()) {
        item(key = "dx_header") { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
            Text(title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2) }
        } }
        content()
    }
}
@Composable fun DxCard(title: String, detail: String? = null, icon: String = "settings", onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 10.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(actionIcon(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}
@Composable fun DxButton(title: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), label = { Text(title, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) })
}
@Composable fun DxToggle(title: String, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    SwitchButton(checked = checked, onCheckedChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(title, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 2) },
        secondaryLabel = detail?.let { { Text(it, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2) } })
}
@Composable fun DxInfo(text: String) { if (text.isNotBlank()) Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp)) }
@Composable fun DxSection(text: String) { Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 7.dp, bottom = 2.dp)) }
@Composable fun DxInput(value: String, hint: String, onChange: (String) -> Unit) {
    BasicTextField(value = value, onValueChange = onChange, singleLine = true, textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        decorationBox = { inner -> Box { if(value.isEmpty()) Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
}
@Composable fun DxStepper(value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = minus), contentAlignment = Alignment.Center) { Text("−", fontSize = 22.sp) }
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = plus), contentAlignment = Alignment.Center) { Text("+", fontSize = 22.sp) }
    }
}
@Composable fun DxGestureCard(gesture: Gesture, action: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 63.dp).clip(RoundedCornerShape(23.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = if(enabled) 0.18f else 0.06f)), contentAlignment = Alignment.Center) {
            Text(when(gesture) { Gesture.HOLD -> "1—"; Gesture.DOUBLE -> "2×"; Gesture.TRIPLE -> "3×" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if(enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(gestureTitle(gesture), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(if(enabled) action else "Не назначено", fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}