package id.omi.absensicafe.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "perangkat")

/**
 * Pengaturan yang melekat pada tablet ini saja dan tidak ikut tersinkron:
 * pengenal perangkat, nama kios, dan arah urutan nama.
 */
class DeviceStore(private val context: Context) {

    private val keyDeviceId = stringPreferencesKey("deviceId")
    private val keyLabel = stringPreferencesKey("label")
    private val keySortAsc = booleanPreferencesKey("sortAsc")

    val label: Flow<String> = context.dataStore.data.map { it[keyLabel] ?: "Kios Kasir" }

    val sortAscending: Flow<Boolean> = context.dataStore.data.map { it[keySortAsc] ?: true }

    /** Dibuat sekali saat pertama dipakai lalu tetap sama selama aplikasi terpasang. */
    suspend fun deviceId(): String {
        val ada = context.dataStore.data.first()[keyDeviceId]
        if (ada != null) return ada
        val baru = UUID.randomUUID().toString().take(12)
        context.dataStore.edit { it[keyDeviceId] = baru }
        return baru
    }

    suspend fun setLabel(value: String) {
        context.dataStore.edit { it[keyLabel] = value.trim().ifBlank { "Kios Kasir" } }
    }

    suspend fun setSortAscending(value: Boolean) {
        context.dataStore.edit { it[keySortAsc] = value }
    }
}
