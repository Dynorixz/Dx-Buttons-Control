package com.dynorixz.buttonscontrol.core

data class LaunchTarget(val enabled: Boolean, val appEnabled: Boolean, val exported: Boolean, val permissionGranted: Boolean)
object ApplicationAvailability {
    fun usable(target: LaunchTarget) = target.enabled && target.appEnabled && target.exported && target.permissionGranted
    fun check(packageName: String, hasEntryPoint: Boolean) = when {
        packageName.isBlank() -> ActionResult(false, "Выберите приложение")
        !hasEntryPoint -> ActionResult(false, "Приложение удалено, отключено или не имеет доступной Activity. Используйте опубликованный ярлык.")
        else -> ActionResult(true, "Доступно")
    }
}
