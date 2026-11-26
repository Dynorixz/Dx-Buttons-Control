package com.dynorixz.buttonscontrol

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DxApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var settings: SettingsRepository
        private set
    val diagnostics by lazy { DiagnosticsRepository(this, appScope) }
    lateinit var apps: InstalledAppsRepository
        private set
    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this, appScope)
        apps = InstalledAppsRepository(this)
    }
}
