package com.dynorixz.buttonscontrol.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable enum class Gesture { HOLD, DOUBLE, TRIPLE }
@Serializable enum class InputMode { OBSERVE, CAPTURE_BACK }
@Serializable data class Action(val id: String = "none", val argument: String = "", val steps: List<Action> = emptyList())
@Serializable data class GestureSetting(val enabled: Boolean = false, val action: Action = Action(), val haptic: Boolean = true)
@Serializable data class ButtonBinding(
    val keyCode: Int, val scanCode: Int, val descriptor: String, val deviceName: String = "",
    val deviceManufacturer: String = "", val deviceModel: String = "", val eventSource: Int = 0
)
@Serializable data class FaceSignature(val packageName: String, val className: String, val rootClass: String, val viewIds: List<String>, val topology: String)
@Serializable data class DxSettings(
    val schema: Int = 1,
    val hold: GestureSetting = GestureSetting(),
    val double: GestureSetting = GestureSetting(),
    val triple: GestureSetting = GestureSetting(),
    val holdMs: Long = 650,
    val holdOnTimer: Boolean = true,
    val gapMs: Long = 350,
    val debounceMs: Long = 25,
    val button: ButtonBinding? = null,
    val face: FaceSignature? = null,
    val mode: InputMode = InputMode.OBSERVE,
    val excludedPackages: Set<String> = emptySet(),
    val favorites: Set<String> = emptySet(),
    val recentApps: List<String> = emptyList(),
    val quickActions: List<Action> = listOf(Action("home"), Action("notifications"), Action("flashlight"), Action("settings")),
    val vibration: Boolean = true,
    val diagnostics: Boolean = false,
    val runWhenConnected: Boolean = true,
    val onboardingComplete: Boolean = false
) {
    fun gesture(g: Gesture) = when(g) { Gesture.HOLD -> hold; Gesture.DOUBLE -> double; Gesture.TRIPLE -> triple }
    fun withGesture(g: Gesture, value: GestureSetting) = when(g) {
        Gesture.HOLD -> copy(hold = value); Gesture.DOUBLE -> copy(double = value); Gesture.TRIPLE -> copy(triple = value)
    }
    fun assigned(g: Gesture) = gesture(g).let { it.enabled && it.action.id != "none" }
}

object SettingsCodec {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; prettyPrint = true }
    fun encode(settings: DxSettings) = json.encodeToString(DxSettings.serializer(), settings)
    fun decode(value: String): DxSettings {
        require(value.length <= 131_072) { "Файл настроек слишком большой" }
        val s = json.decodeFromString(DxSettings.serializer(), value.removePrefix("\uFEFF"))
        require(s.schema == 1) { "Неподдерживаемая версия настроек" }
        require(s.holdMs in 400..5000 && s.gapMs in 150..1000 && s.debounceMs in 10..100)
        require(s.quickActions.size in 4..6 && s.excludedPackages.size <= 200 && s.favorites.size <= 200 && s.recentApps.size <= 12)
        s.button?.let { require(ButtonSafety.allowed(it)) { ButtonSafety.bindingIssues(it).joinToString { issue -> issue.name } } }
        s.face?.let { require(it.packageName.isNotBlank() && it.className.isNotBlank() && it.rootClass.isNotBlank() && it.viewIds.size in 1..48 && it.topology.length == 64) }
        require(s.mode != InputMode.CAPTURE_BACK || s.button?.keyCode == 4)
        (listOf(s.hold.action, s.double.action, s.triple.action) + s.quickActions).forEach { ActionRegistry.validate(it) }
        return s
    }
}

object ButtonSafety {
    // Some Samsung firmware maps the separate Back driver to STEM_PRIMARY.
    // Home/Power remain protected; this exception never enables capture.
    private val reserved = setOf(3, 6, 24, 25, 26, 79, 82, 164, 187, 219, 223, 224, 231, 264, 280, 281, 282, 283)
    fun allowed(code: Int) = code > 0 && code !in reserved
    fun allowed(binding: ButtonBinding): Boolean = bindingIssues(binding).isEmpty()
    fun bindingIssues(binding: ButtonBinding): List<ButtonRejection> = buildList {
        if (binding.scanCode <= 0) add(ButtonRejection.SCAN_CODE_MISSING)
        if (binding.descriptor.isBlank()) add(ButtonRejection.DESCRIPTOR_MISSING)
        when {
            binding.keyCode <= 0 -> add(ButtonRejection.KEY_CODE_INVALID)
            binding.keyCode == 264 -> {
                when (binding.deviceName) {
                    "pmic_resin" -> if (binding.scanCode !in setOf(114, 580)) add(ButtonRejection.STEM_PRIMARY_SCAN_UNVERIFIED)
                    "gpio_keys" -> {
                        if (!ButtonDeviceEnvironment(binding.deviceManufacturer, binding.deviceModel).isSamsungUltraL705()) add(ButtonRejection.DEVICE_MODEL_UNVERIFIED)
                        if (binding.scanCode != 580) add(ButtonRejection.STEM_PRIMARY_SCAN_UNVERIFIED)
                        if (binding.eventSource != 0x101) add(ButtonRejection.EVENT_SOURCE_UNVERIFIED)
                    }
                    else -> add(ButtonRejection.STEM_PRIMARY_DEVICE_UNVERIFIED)
                }
            }
            !allowed(binding.keyCode) -> add(ButtonRejection.KEY_CODE_PROTECTED)
        }
    }
    fun inspect(event: ButtonKeyEvent, lookup: InputDeviceLookup, environment: ButtonDeviceEnvironment = ButtonDeviceEnvironment()): ButtonVerdict {
        val device = lookup.device?.takeIf { lookup.state == DeviceLookupState.READY }
        if (device == null) return ButtonVerdict(null, null, listOf(when(lookup.state) {
            DeviceLookupState.PENDING -> ButtonRejection.DEVICE_INFO_PENDING
            DeviceLookupState.ERROR -> ButtonRejection.DEVICE_INFO_READ_FAILED
            DeviceLookupState.REMOVED -> ButtonRejection.DEVICE_REMOVED
            else -> ButtonRejection.DEVICE_NOT_FOUND
        }))
        val issues = bindingIssues(event.binding(device, environment))
        val hardwareIssues = buildList {
            if (device.isVirtual) add(ButtonRejection.VIRTUAL_DEVICE)
            if (device.id != event.deviceId) add(ButtonRejection.DEVICE_ID_MISMATCH)
            if (event.keyCode == 264 && device.name == "gpio_keys" && device.sources != 0x101) add(ButtonRejection.DEVICE_SOURCE_UNVERIFIED)
        }
        return ButtonVerdict(issues.isEmpty(), hardwareIssues.isEmpty(), issues + hardwareIssues)
    }
    fun matches(binding: ButtonBinding, event: ButtonKeyEvent, lookup: InputDeviceLookup, environment: ButtonDeviceEnvironment = ButtonDeviceEnvironment()): Boolean {
        val device = lookup.device ?: return false
        if (binding.keyCode == 264 && binding.deviceName == "gpio_keys" &&
            (!binding.deviceManufacturer.equals(environment.manufacturer, true) || !binding.deviceModel.equals(environment.model, true))) return false
        return allowed(binding) && inspect(event, lookup, environment).accepted && event.keyCode == binding.keyCode &&
            event.scanCode == binding.scanCode && device.descriptor == binding.descriptor &&
            (binding.deviceName.isBlank() || device.name == binding.deviceName)
    }
    fun standardCandidate(code: Int) = code in setOf(4, 265, 266, 267)
}
