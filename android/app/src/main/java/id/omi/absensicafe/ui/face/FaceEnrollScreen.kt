package id.omi.absensicafe.ui.face

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.ui.camera.SelfieCamera
import java.io.File

@Composable
fun FaceEnrollScreen(
    step: EnrollStep,
    employees: List<Employee>,
    modelAvailable: Boolean,
    shots: Int,
    onSelect: (Employee) -> Unit,
    onShot: (File?) -> Unit,
    onClearFace: (Employee) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    when (step) {
        is EnrollStep.Pick -> PickScreen(
            employees = employees,
            modelAvailable = modelAvailable,
            onSelect = onSelect,
            onClearFace = onClearFace,
            onClose = onClose
        )

        is EnrollStep.Shoot -> SelfieCamera(
            title = step.employee.name,
            subtitle = "Jepretan ${step.taken + 1} dari $shots — " +
                "ubah sedikit posisi kepala tiap jepretan",
            busy = step.busy,
            onCaptured = onShot,
            onCancel = onBack,
            statusBar = {
                if (step.error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            step.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Berdirilah pada jarak yang sama seperti saat absen sehari-hari.",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        )

        is EnrollStep.Saved -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Wajah ${step.employee.name} terdaftar",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "${step.count} pola tersimpan",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onBack) { Text("Daftarkan orang lain") }
                    TextButton(onClick = onClose) { Text("Selesai") }
                }
            }
        }
    }
}

@Composable
private fun PickScreen(
    employees: List<Employee>,
    modelAvailable: Boolean,
    onSelect: (Employee) -> Unit,
    onClearFace: (Employee) -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Daftarkan wajah",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Ketuk nama untuk mendaftarkan atau memperbarui wajahnya",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = onClose) { Text("Tutup") }
        }

        if (!modelAvailable) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    "Model wajah belum dipasang di aplikasi ini, jadi pendaftaran " +
                        "tidak bisa dilakukan. Absensi tetap berjalan normal.",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(employees, key = { it.id }) { karyawan ->
                Card(
                    onClick = { if (modelAvailable) onSelect(karyawan) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initials(karyawan.name), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            karyawan.name,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        if (karyawan.hasFace) {
                            Text(
                                "terdaftar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = { onClearFace(karyawan) }) {
                                Text("Hapus", fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                "belum terdaftar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
