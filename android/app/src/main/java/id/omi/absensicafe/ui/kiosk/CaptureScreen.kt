package id.omi.absensicafe.ui.kiosk

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.omi.absensicafe.data.PunchSide
import id.omi.absensicafe.data.model.GeoMode
import id.omi.absensicafe.data.model.Settings
import id.omi.absensicafe.domain.AttendanceRules
import id.omi.absensicafe.location.LocationFix
import id.omi.absensicafe.location.LocationProvider
import id.omi.absensicafe.location.LocationState
import id.omi.absensicafe.ui.camera.SelfieCamera
import java.io.File

/**
 * Layar foto selfie untuk absen.
 *
 * Pencarian lokasi dimulai bersamaan dengan kamera, bukan saat tombol jepret
 * ditekan, supaya GPS sudah dapat sinyal ketika fotonya diambil. Statusnya
 * ditampilkan sebagai bilah di bawah pratinjau agar karyawan tahu kapan boleh
 * menekan tombol.
 */
@Composable
fun CaptureScreen(
    employeeName: String,
    side: PunchSide,
    settings: Settings,
    busy: Boolean,
    onCaptured: (File?, LocationFix?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val locationProvider = remember { LocationProvider(context) }
    val locationState by produceState<LocationState>(LocationState.Idle, locationProvider) {
        locationProvider.updates().collect { value = it }
    }

    SelfieCamera(
        title = employeeName,
        subtitle = "Absen ${side.label.lowercase()} — hadapkan wajah ke kamera",
        busy = busy,
        onCaptured = { file ->
            onCaptured(file, (locationState as? LocationState.Fixed)?.fix)
        },
        onCancel = onCancel,
        statusBar = { LocationBar(locationState, settings) }
    )
}

@Composable
private fun LocationBar(state: LocationState, settings: Settings) {
    if (settings.geoMode == GeoMode.OFF && settings.geoLat == null) return

    val (teks, warna) = when (state) {
        LocationState.Idle, LocationState.Searching ->
            "Mencari lokasi…" to MaterialTheme.colorScheme.surfaceVariant

        LocationState.PermissionDenied ->
            "Izin lokasi belum diberikan" to MaterialTheme.colorScheme.error

        LocationState.ServiceOff ->
            "GPS mati" to MaterialTheme.colorScheme.error

        is LocationState.Fixed -> {
            val fix = state.fix
            val jarak = if (settings.geoLat != null && settings.geoLon != null) {
                AttendanceRules.distanceMeters(fix.lat, fix.lon, settings.geoLat, settings.geoLon)
            } else null
            when {
                fix.mocked ->
                    "Lokasi palsu terdeteksi" to MaterialTheme.colorScheme.error

                jarak == null ->
                    "Lokasi didapat (±${fix.accuracyMeters.toInt()} m)" to
                        MaterialTheme.colorScheme.surfaceVariant

                jarak <= settings.geoRadiusMeters ->
                    "Di area cafe — ${AttendanceRules.formatMeters(jarak)} " +
                        "(±${fix.accuracyMeters.toInt()} m)" to
                        MaterialTheme.colorScheme.primaryContainer

                else ->
                    "Di luar area — ${AttendanceRules.formatMeters(jarak)} dari cafe" to
                        MaterialTheme.colorScheme.error
            }
        }
    }

    Surface(color = warna, modifier = Modifier.fillMaxWidth()) {
        Text(
            teks,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
