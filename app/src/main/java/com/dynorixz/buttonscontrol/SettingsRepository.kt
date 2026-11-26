package com.dynorixz.buttonscontrol

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dynorixz.buttonscontrol.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.dxDataStore by preferencesDataStore("dx_buttons")
class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.dxDataStore
    private val key = stringPreferencesKey("settings_v1")
    val error = MutableStateFlow<String?>(null)
    val ready = MutableStateFlow(false)
    val settings: StateFlow<DxSettings> = store.data.catch { e ->
        if (e is IOException) { error.value = "Не удалось прочитать хранилище"; emit(androidx.datastore.preferences.core.emptyPreferences()) } else throw e
    }.map { preferences ->
        val decoded = preferences[key]?.let { raw ->
            runCatching { SettingsCodec.decode(raw) }.getOrElse { error.value = "Настройки повреждены: обработка отключена до исправления"; DxSettings(runWhenConnected = false) }
        } ?: DxSettings()
        ready.value = true
        decoded
    }.stateIn(scope, SharingStarted.Eagerly, DxSettings())

    suspend fun update(transform: (DxSettings) -> DxSettings) {
        store.edit { values ->
            val current = values[key]?.let(SettingsCodec::decode) ?: DxSettings()
            val next = transform(current)
            val encoded = SettingsCodec.encode(next)
            SettingsCodec.decode(encoded)
            values[key] = encoded
        }
        error.value = null
    }
    suspend fun importJson(raw: String) {
        val incoming = SettingsCodec.decode(raw)
        // A imported hardware/window fingerprint must be calibrated on this device again.
        store.edit { it[key] = SettingsCodec.encode(incoming.copy(button = null, face = null, mode = InputMode.OBSERVE)) }
        error.value = null
    }
    fun exportJson() = SettingsCodec.encode(settings.value)
    suspend fun reset() { store.edit { it.clear() }; error.value = null }
}
