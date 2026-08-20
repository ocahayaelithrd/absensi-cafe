package id.omi.absensicafe.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.omi.absensicafe.data.PunchSide
import id.omi.absensicafe.data.model.AttendanceRecord
import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.data.model.FaceMode
import id.omi.absensicafe.data.model.PinBy
import id.omi.absensicafe.data.openRecordFor
import id.omi.absensicafe.domain.AttendanceRules
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val JAM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun KioskScreen(
    state: KioskUiState,
    step: KioskStep,
    onSelectEmployee: (Employee) -> Unit,
    onSubmitPin: (String) -> Unit,
    onUseAdminOverride: () -> Unit,
    onSubmitOverride: (String) -> Unit,
    onPhotoTaken: (java.io.File?, id.omi.absensicafe.location.LocationFix?) -> Unit,
    onCancel: () -> Unit,
    onToggleSort: () -> Unit,
    onOpenDeviceSettings: () -> Unit
) {
    when (step) {
        is KioskStep.Grid -> GridScreen(
            state = state,
            onSelectEmployee = onSelectEmployee,
            onToggleSort = onToggleSort,
            onOpenDeviceSettings = onOpenDeviceSettings
        )

        is KioskStep.AskPin -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            PinPad(
                title = step.employee.name,
                subtitle = if (step.adminMode) {
                    "Masukkan PIN penyelia untuk meloloskan absen ${step.side.label.lowercase()}"
                } else {
                    "Masukkan PIN untuk absen ${step.side.label.lowercase()}"
                },
                error = step.error,
                confirmLabel = if (step.adminMode) "Loloskan" else "Lanjut",
                onSubmit = onSubmitPin,
                onCancel = onCancel,
                extraAction = {
                    if (step.offerAdmin && !step.adminMode) {
                        TextButton(onClick = onUseAdminOverride) {
                            Text("Minta izin penyelia")
                        }
                    }
                }
            )
        }

        is KioskStep.Capture -> CaptureScreen(
            employeeName = step.employee.name,
            side = step.side,
            settings = state.settings,
            busy = step.checking,
            onCaptured = onPhotoTaken,
            onCancel = onCancel
        )

        is KioskStep.Blocked -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Absen ditahan",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Text(step.reason, color = MaterialTheme.colorScheme.onError)
                    }
                }
                PinPad(
                    title = step.pending.employee.name,
                    subtitle = "PIN penyelia untuk tetap mencatat absen ini",
                    error = step.error,
                    confirmLabel = "Loloskan",
                    onSubmit = onSubmitOverride,
                    onCancel = onCancel
                )
            }
        }

        is KioskStep.Done -> DoneScreen(step, onCancel)
    }
}

@Composable
private fun GridScreen(
    state: KioskUiState,
    onSelectEmployee: (Employee) -> Unit,
    onToggleSort: () -> Unit,
    onOpenDeviceSettings: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    var sekarang by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            sekarang = Instant.now()
            delay(30_000)
        }
    }

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
                    state.settings.cafeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    sekarang.atZone(zone).format(JAM) + " — ketuk nama untuk absen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onToggleSort) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = if (state.sortAscending) "Urutkan Z–A" else "Urutkan A–Z"
                )
            }
            IconButton(onClick = onOpenDeviceSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Setelan perangkat")
            }
        }

        if (state.withoutPin.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    "PIN belum diatur: ${state.withoutPin.joinToString(", ")}. " +
                        "Absennya tetap tercatat tapi ditandai tanpa PIN.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (!state.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (state.employees.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada karyawan.\nTambahkan lewat web admin di PC.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.employees, key = { it.id }) { karyawan ->
                val terbuka = state.records.openRecordFor(karyawan.id, sekarang)
                val selesaiHariIni = state.records.firstOrNull {
                    it.employeeId == karyawan.id && it.checkOut != null
                }
                EmployeeCard(
                    employee = karyawan,
                    openRecord = terbuka,
                    finishedRecord = selesaiHariIni,
                    showNoPin = state.settings.pinRequired && !karyawan.hasPin,
                    showNoFace = state.settings.faceMode != FaceMode.OFF && !karyawan.hasFace,
                    zone = zone,
                    onClick = { onSelectEmployee(karyawan) }
                )
            }
        }

        ActivityList(state, zone)
    }
}

@Composable
private fun EmployeeCard(
    employee: Employee,
    openRecord: AttendanceRecord?,
    finishedRecord: AttendanceRecord?,
    showNoPin: Boolean,
    showNoFace: Boolean,
    zone: ZoneId,
    onClick: () -> Unit
) {
    val (status, warna) = when {
        openRecord != null ->
            "Masuk ${openRecord.checkIn?.at?.atZone(zone)?.format(JAM)}" to
                MaterialTheme.colorScheme.primary

        finishedRecord != null ->
            "Selesai ${finishedRecord.checkOut?.at?.atZone(zone)?.format(JAM)}" to
                MaterialTheme.colorScheme.onSurfaceVariant

        else -> "Belum absen" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.aspectRatio(0.92f)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials(employee.name),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                employee.name,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        openRecord != null -> Icons.AutoMirrored.Filled.Logout
                        finishedRecord != null -> Icons.Default.CheckCircle
                        else -> Icons.AutoMirrored.Filled.Login
                    },
                    contentDescription = null,
                    tint = warna,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = warna,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showNoPin) {
                Text(
                    "tanpa PIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (showNoFace) {
                Text(
                    "wajah belum didaftarkan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ActivityList(state: KioskUiState, zone: ZoneId) {
    val records = state.records
    if (records.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                "Aktivitas terakhir",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn {
                items(records.take(20), key = { it.id }) { r ->
                    val masuk = r.checkIn?.at?.atZone(zone)?.format(JAM) ?: "—"
                    val pulang = r.checkOut?.at?.atZone(zone)?.format(JAM) ?: "—"
                    val tanda = buildList {
                        if (r.lateMinutes > 0) {
                            add("telat ${AttendanceRules.formatDuration(r.lateMinutes)}")
                        }
                        if (r.offSchedule) add("di luar jadwal")
                        if (r.checkIn?.pinBy == PinBy.KOSONG || r.checkOut?.pinBy == PinBy.KOSONG) {
                            add("tanpa PIN")
                        }
                        if (r.checkIn?.pinBy == PinBy.ADMIN || r.checkOut?.pinBy == PinBy.ADMIN) {
                            add("izin penyelia")
                        }
                        if (r.checkIn?.outsideGeofence == true ||
                            r.checkOut?.outsideGeofence == true
                        ) {
                            add("di luar area")
                        }
                        if (r.checkIn?.faceFlag == true || r.checkOut?.faceFlag == true) {
                            add("wajah tidak cocok")
                        }
                    }
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(
                            state.nameOf(r.employeeId),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "$masuk → $pulang" + if (tanda.isEmpty()) "" else "  (${tanda.joinToString(", ")})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneScreen(step: KioskStep.Done, onDone: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    LaunchedEffect(step) {
        delay(6_000)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onDone),
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
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Absen ${step.side.label.lowercase()} tercatat",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(step.employeeName, style = MaterialTheme.typography.titleMedium)
            Text(
                step.at.atZone(zone).format(JAM),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            if (step.side == PunchSide.IN && step.lateMinutes > 0) {
                Text(
                    "Telat ${AttendanceRules.formatDuration(step.lateMinutes)}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (step.side == PunchSide.OUT) {
                Text("Kerja ${AttendanceRules.formatDuration(step.workMinutes)}")
                if (step.overtimeMinutes > 0) {
                    Text(
                        "Lembur ${AttendanceRules.formatDuration(step.overtimeMinutes)}",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            step.warnings.forEach {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Ketuk di mana saja untuk kembali",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
