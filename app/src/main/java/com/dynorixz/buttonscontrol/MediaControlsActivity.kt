package com.dynorixz.buttonscontrol

import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.dynorixz.buttonscontrol.core.*
import com.dynorixz.buttonscontrol.ui.*
import kotlinx.coroutines.launch

class MediaControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DxApplication
        val platform = AndroidActionPlatform(this, app.apps, DxButtonAccessibilityService.current())
        setContent { DxTheme {
            var message by remember { mutableStateOf("Команды для активного плеера на часах") }
            DxList("Музыка") {
                item { DxInfo(message) }
                listOf("play_pause", "previous", "next").forEach { id -> item {
                    DxCard(ActionRegistry.title(Action(id)), icon = ActionRegistry.spec(id)?.icon ?: "play") {
                        lifecycleScope.launch { val result = ActionExecutor(platform).execute(Action(id)); message = result.message; app.diagnostics.result(result) }
                    }
                } }
                item { DxButton("Разрешить MediaSession", { runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.onFailure { message = "Раздел доступа к уведомлениям недоступен на этой прошивке" } }) }
                item { DxInfo("Управление телефоном возможно только если его плеер публикует доступную сессию на часах. Содержимое уведомлений и тексты треков не читаются.") }
            }
        } }
    }
}
