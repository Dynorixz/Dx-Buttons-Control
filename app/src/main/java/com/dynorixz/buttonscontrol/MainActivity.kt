package com.dynorixz.buttonscontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dynorixz.buttonscontrol.ui.DxApp
import com.dynorixz.buttonscontrol.ui.DxTheme

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startScreen = intent.getStringExtra("screen")?.takeIf { it in setOf("hold_test", "calibration", "about", "settings", "diagnostics", "button_report") } ?: "home"
        setContent { DxTheme { DxApp(model, startScreen) } }
    }
    override fun onResume() { super.onResume(); model.refresh() }
}
