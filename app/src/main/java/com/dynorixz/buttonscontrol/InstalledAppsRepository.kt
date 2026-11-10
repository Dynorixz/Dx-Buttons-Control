package com.dynorixz.buttonscontrol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.dynorixz.buttonscontrol.core.ActionResult
import com.dynorixz.buttonscontrol.core.ApplicationAvailability
import com.dynorixz.buttonscontrol.core.LaunchTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String, val component: ComponentName?, val icon: Drawable?, val enabled: Boolean) {
    val launchable get() = component != null && enabled
}
class InstalledAppsRepository(private val context: Context) {
    private val pm = context.packageManager
    @Suppress("DEPRECATION")
    suspend fun list(): List<InstalledApp> = withContext(Dispatchers.IO) {
        pm.getInstalledApplications(0).map { app ->
            InstalledApp(app.packageName, runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName),
                component(app.packageName), runCatching { pm.getApplicationIcon(app) }.getOrNull(), app.enabled)
        }.sortedWith(compareByDescending<InstalledApp> { it.launchable }.thenBy { it.label.lowercase() })
    }
    @Suppress("DEPRECATION")
    fun component(packageName: String): ComponentName? {
        val main = pm.getLaunchIntentForPackage(packageName) ?: pm.getLeanbackLaunchIntentForPackage(packageName)
        main?.component?.let { if (valid(it)) return it }
        // INFO activities are explicit public entry points for packages without LAUNCHER.
        val info = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_INFO).setPackage(packageName)
        return pm.queryIntentActivities(info, 0).firstOrNull { valid(ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
            ?.activityInfo?.let { ComponentName(it.packageName, it.name) }
    }
    @Suppress("DEPRECATION") private fun valid(component: ComponentName) = runCatching {
        val a = pm.getActivityInfo(component, 0)
        ApplicationAvailability.usable(LaunchTarget(a.enabled, a.applicationInfo.enabled, a.exported, a.permission == null || context.checkSelfPermission(a.permission) == PackageManager.PERMISSION_GRANTED))
    }.getOrDefault(false)
    fun availability(packageName: String): ActionResult = ApplicationAvailability.check(packageName, component(packageName) != null)
    fun launch(packageName: String): ActionResult {
        val c = component(packageName) ?: return availability(packageName)
        return try {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult(true, "Запрошен запуск $packageName")
        } catch (_: SecurityException) { ActionResult(false, "Запуск приложения запрещён") }
        catch (_: android.content.ActivityNotFoundException) { ActionResult(false, "Activity больше недоступна") }
    }
}
