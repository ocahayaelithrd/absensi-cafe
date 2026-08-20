package id.omi.absensicafe.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Setelan yang hanya berlaku untuk tablet ini.
 *
 * Semua data absensi — karyawan, shift, jadwal, denda, titik cafe — diatur di
 * web admin, bukan di sini. Tablet hanya perlu tahu namanya sendiri dan akun
 * mana yang dipakai, supaya kesalahan setelan di kios tidak pernah mengubah
 * angka gaji.
 */
@Composable
fun DeviceScreen(
    deviceId: String,
    label: String,
    email: String,
    appVersion: String,
    cameraGranted: Boolean,
    locationGranted: Boolean,
    faceModelAvailable: Boolean,
    onLabelChange: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onEnrollFaces: () -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit
) {
    var teks by remember(label) { mutableStateOf(label) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "Setelan perangkat",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Karyawan, shift, jadwal, dan denda diatur dari web admin.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Nama kios", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = teks,
                    onValueChange = { teks = it },
                    singleLine = true,
                    label = { Text("mis. Kios Kasir Depan") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onLabelChange(teks) }) { Text("Simpan nama") }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Baris("Akun", email.ifBlank { "belum masuk" })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Baris("Pengenal perangkat", deviceId)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Baris("Versi aplikasi", appVersion)
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Izin", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Baris("Kamera", if (cameraGranted) "diizinkan" else "belum diizinkan")
                Baris("Lokasi", if (locationGranted) "diizinkan" else "belum diizinkan")
                if (!cameraGranted || !locationGranted) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequestPermissions) { Text("Minta izin") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Pengenalan wajah", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Baris(
                    "Model di aplikasi ini",
                    if (faceModelAvailable) "terpasang" else "tidak ada"
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Wajah harus didaftarkan dari tablet ini, bukan dari PC — kamera, " +
                        "jarak berdiri, dan pencahayaannya harus sama dengan saat absen " +
                        "sehari-hari. Mode dan ambang kemiripannya diatur di web admin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onEnrollFaces, enabled = faceModelAvailable) {
                    Text("Daftarkan wajah karyawan")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onClose) { Text("Kembali ke layar absen") }
            TextButton(onClick = onLogout) { Text("Keluar dari akun") }
        }
    }
}

@Composable
private fun Baris(label: String, nilai: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(nilai, fontWeight = FontWeight.Medium)
    }
}
