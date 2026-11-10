package com.dynorixz.buttonscontrol.core

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable data class ButtonDeviceEnvironment(val manufacturer: String = "", val model: String = "") {
    fun isSamsungUltraL705() = manufacturer.equals("samsung", true) && ultraModel.matches(model.uppercase(Locale.ROOT))
    companion object { private val ultraModel = Regex("SM-L705[A-Z0-9]?") }
}

@Serializable enum class DeviceLookupState { PENDING, READY, NOT_FOUND, ERROR, REMOVED }
@Serializable data class InputDeviceIdentity(val id: Int, val name: String, val descriptor: String, val sources: Int, val isVirtual: Boolean)
@Serializable data class InputDeviceLookup(val state: DeviceLookupState, val device: InputDeviceIdentity? = null, val error: String? = null)

@Serializable data class ButtonKeyEvent(
    val action: Int, val keyCode: Int, val scanCode: Int, val deviceId: Int, val source: Int,
    val downTime: Long, val eventTime: Long, val repeatCount: Int = 0, val canceled: Boolean = false, val flags: Int = 0
) {
    fun binding(device: InputDeviceIdentity, environment: ButtonDeviceEnvironment = ButtonDeviceEnvironment()) =
        if (keyCode == 264 && device.name == "gpio_keys") ButtonBinding(keyCode, scanCode, device.descriptor, device.name,
            environment.manufacturer, environment.model, source)
        else ButtonBinding(keyCode, scanCode, device.descriptor, device.name)
}

@Serializable enum class ButtonRejection(val explanation: String) {
    KEY_CODE_INVALID("Недопустимый keyCode"),
    KEY_CODE_PROTECTED("Этот системный keyCode защищён: Home/Power и другие зарезервированные кнопки не назначаются"),
    SCAN_CODE_MISSING("scanCode отсутствует или равен нулю"),
    DESCRIPTOR_MISSING("InputDevice.descriptor пуст"),
    STEM_PRIMARY_DEVICE_UNVERIFIED("STEM_PRIMARY поступил от источника без разрешённого аппаратного профиля"),
    STEM_PRIMARY_SCAN_UNVERIFIED("ScanCode не соответствует профилю: pmic_resin 580/114, gpio_keys на Ultra SM-L705 только 580"),
    DEVICE_MODEL_UNVERIFIED("Для gpio_keys + STEM_PRIMARY нужен Samsung Galaxy Watch Ultra семейства SM-L705"),
    EVENT_SOURCE_UNVERIFIED("Для gpio_keys + STEM_PRIMARY подтверждён только source=0x101"),
    DEVICE_SOURCE_UNVERIFIED("Для gpio_keys + STEM_PRIMARY подтверждены только InputDevice.sources=0x101"),
    DEVICE_INFO_PENDING("Данные InputDevice ещё загружаются; признак isVirtual пока неизвестен"),
    DEVICE_NOT_FOUND("InputManager не нашёл устройство с этим deviceId"),
    DEVICE_INFO_READ_FAILED("Не удалось прочитать данные InputDevice; см. lookupError"),
    DEVICE_REMOVED("InputDevice отключён или заменён во время проверки"),
    DEVICE_ID_MISMATCH("deviceId события не совпадает с id прочитанного InputDevice"),
    VIRTUAL_DEVICE("InputDevice.isVirtual=true: виртуальный источник не разрешён"),
    DOWN_MISSING("Не получен начальный ACTION_DOWN"),
    UP_MISSING("Не получен ACTION_UP"),
    PAIR_MISMATCH("DOWN/UP относятся к разным кнопкам, устройствам или downTime"),
    EVENT_CANCELED("Android отменил последовательность кнопки"),
    INVALID_SEQUENCE("Нужны начальный DOWN без повторов и последующий UP с монотонным временем")
}

@Serializable data class ButtonVerdict(val bindingAllowed: Boolean?, val hardwareAllowed: Boolean?, val rejections: List<ButtonRejection>) {
    val accepted get() = bindingAllowed == true && hardwareAllowed == true && rejections.isEmpty()
    fun explanation() = if (accepted) "Проверки ButtonSafety и InputDevice пройдены" else rejections.joinToString("\n") { "${it.name}: ${it.explanation}" }
}

@Serializable data class ButtonCalibrationReport(
    val down: ButtonKeyEvent?, val up: ButtonKeyEvent?,
    val downLookup: InputDeviceLookup?, val upLookup: InputDeviceLookup?,
    val resolvedLookup: InputDeviceLookup, val verdict: ButtonVerdict,
    val sequenceIssues: List<ButtonRejection>, val environment: ButtonDeviceEnvironment = ButtonDeviceEnvironment()
) {
    val accepted get() = verdict.accepted && sequenceIssues.isEmpty()
    val candidate get() = if (accepted) up?.binding(requireNotNull(resolvedLookup.device), environment) else null
    val rejections get() = (sequenceIssues + verdict.rejections).distinct()
    fun explanation() = if (accepted) "Получена аппаратная пара DOWN/UP. Подтвердите нижнюю кнопку."
        else rejections.joinToString("\n") { "${it.name}: ${it.explanation}" }
}

object ButtonCalibration {
    fun evaluate(down: ButtonKeyEvent?, up: ButtonKeyEvent?, downLookup: InputDeviceLookup?, upLookup: InputDeviceLookup?, resolved: InputDeviceLookup,
        environment: ButtonDeviceEnvironment = ButtonDeviceEnvironment()): ButtonCalibrationReport {
        val sequenceIssues = buildList {
            if (down == null) add(ButtonRejection.DOWN_MISSING)
            if (up == null) add(ButtonRejection.UP_MISSING)
            if (down != null && up != null) {
                if (down.deviceId != up.deviceId || down.keyCode != up.keyCode || down.scanCode != up.scanCode || down.downTime != up.downTime) add(ButtonRejection.PAIR_MISMATCH)
                if (down.action != 0 || down.repeatCount != 0 || up.action != 1 || up.eventTime < down.eventTime || up.eventTime < up.downTime) add(ButtonRejection.INVALID_SEQUENCE)
                if (resolved.device?.name == "gpio_keys" && up.keyCode == 264 && down.source != up.source) add(ButtonRejection.PAIR_MISMATCH)
            }
            if (down?.canceled == true || up?.canceled == true) add(ButtonRejection.EVENT_CANCELED)
        }
        val event = up ?: down
        val verdict = event?.let { ButtonSafety.inspect(it, resolved, environment) } ?: ButtonVerdict(null, null, emptyList())
        return ButtonCalibrationReport(down, up, downLookup, upLookup, resolved, verdict, sequenceIssues, environment)
    }
}

@Serializable data class CalibrationEnvelope(
    val appVersion: String, val manufacturer: String, val model: String, val sdkInt: Int,
    val androidRelease: String, val buildDisplay: String, val collectedAtUtc: String,
    val report: ButtonCalibrationReport, val format: Int = 1
)

object CalibrationReportCodec {
    fun encode(value: CalibrationEnvelope) = SettingsCodec.json.encodeToString(CalibrationEnvelope.serializer(), value)
    fun decode(value: String): CalibrationEnvelope {
        require(value.length <= 131_072)
        return SettingsCodec.json.decodeFromString(CalibrationEnvelope.serializer(), value).also { require(it.format == 1) }
    }
}
