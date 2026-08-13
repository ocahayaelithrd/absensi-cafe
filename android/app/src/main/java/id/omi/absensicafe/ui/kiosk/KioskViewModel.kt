package id.omi.absensicafe.ui.kiosk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.omi.absensicafe.AbsensiApp
import id.omi.absensicafe.data.PhotoUploadWorker
import id.omi.absensicafe.data.PunchSide
import id.omi.absensicafe.data.model.AttendanceRecord
import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.data.model.GeoMode
import id.omi.absensicafe.data.model.Punch
import id.omi.absensicafe.data.model.ROSTER_OFF
import id.omi.absensicafe.data.model.RosterDay
import id.omi.absensicafe.data.model.Settings
import id.omi.absensicafe.data.model.Shift
import id.omi.absensicafe.data.openRecordFor
import id.omi.absensicafe.domain.AttendanceRules
import id.omi.absensicafe.domain.Pin
import id.omi.absensicafe.location.LocationFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Langkah yang sedang ditampilkan layar kios. */
sealed interface KioskStep {
    /** Daftar nama karyawan. */
    data object Grid : KioskStep

    data class AskPin(
        val employee: Employee,
        val side: PunchSide,
        val wrongAttempts: Int = 0,
        val offerAdmin: Boolean = false,
        val adminMode: Boolean = false,
        val error: String? = null
    ) : KioskStep

    data class Capture(
        val employee: Employee,
        val side: PunchSide,
        val pinOk: Boolean,
        val adminOverride: Boolean,
        val noPin: Boolean
    ) : KioskStep

    /** Absen ditahan karena di luar area; menunggu izin penyelia. */
    data class GeoBlocked(
        val pending: PendingPunch,
        val reason: String,
        val error: String? = null
    ) : KioskStep

    data class Done(
        val employeeName: String,
        val side: PunchSide,
        val at: Instant,
        val lateMinutes: Int,
        val workMinutes: Int,
        val overtimeMinutes: Int,
        val warnings: List<String>
    ) : KioskStep
}

/** Absen yang sudah difoto tapi belum tercatat, menunggu keputusan geofence. */
data class PendingPunch(
    val employee: Employee,
    val side: PunchSide,
    val pinOk: Boolean,
    val adminOverride: Boolean,
    val noPin: Boolean,
    val photo: File?,
    val fix: LocationFix?,
    val distanceMeters: Double?,
    val outside: Boolean
)

data class KioskUiState(
    val ready: Boolean = false,
    val settings: Settings = Settings(),
    val employees: List<Employee> = emptyList(),
    val shifts: List<Shift> = emptyList(),
    val roster: Map<String, RosterDay> = emptyMap(),
    val records: List<AttendanceRecord> = emptyList(),
    val sortAscending: Boolean = true
) {
    /** Karyawan yang PIN-nya belum diatur admin, untuk peringatan di layar. */
    val withoutPin: List<String> get() = employees.filter { !it.hasPin }.map { it.name }

    fun shiftById(id: String?): Shift? = shifts.firstOrNull { it.id == id }
}

class KioskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as AbsensiApp).repository
    private val deviceStore = (app as AbsensiApp).deviceStore
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _step = MutableStateFlow<KioskStep>(KioskStep.Grid)
    val step: StateFlow<KioskStep> = _step.asStateFlow()

    private var deviceId: String = ""

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<KioskUiState> =
        combine(
            repo.settingsFlow(),
            deviceStore.sortAscending,
            repo.shiftsFlow()
        ) { settings, asc, shifts -> Triple(settings, asc, shifts) }
            .flatMapLatest { (settings, asc, shifts) ->
                val dates = repo.watchedDates()
                combine(
                    repo.employeesFlow(asc),
                    repo.rosterFlow(dates),
                    repo.recordsFlow(dates)
                ) { employees, roster, records ->
                    KioskUiState(
                        ready = true,
                        settings = settings,
                        employees = employees,
                        shifts = shifts,
                        roster = roster,
                        records = records,
                        sortAscending = asc
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KioskUiState())

    init {
        viewModelScope.launch {
            deviceId = deviceStore.deviceId()
        }
    }

    fun toggleSort() {
        viewModelScope.launch {
            deviceStore.setSortAscending(!state.value.sortAscending)
        }
    }

    /** Sisi absen berikutnya untuk seorang karyawan. */
    fun sideFor(employeeId: String, now: Instant = Instant.now()): PunchSide =
        if (state.value.records.openRecordFor(employeeId, now) != null) PunchSide.OUT
        else PunchSide.IN

    fun selectEmployee(employee: Employee) {
        val side = sideFor(employee.id)
        _step.value = if (employee.hasPin) {
            KioskStep.AskPin(employee, side)
        } else {
            // PIN belum diatur admin. Karyawan tetap dibiarkan absen supaya
            // shift pagi tidak macet, tapi catatannya ditandai "tanpa PIN".
            KioskStep.Capture(employee, side, pinOk = false, adminOverride = false, noPin = true)
        }
    }

    fun submitPin(pin: String) {
        val s = _step.value as? KioskStep.AskPin ?: return
        if (s.adminMode) {
            if (pin == state.value.settings.kioskAdminPin) {
                _step.value = KioskStep.Capture(
                    s.employee, s.side,
                    pinOk = false, adminOverride = true, noPin = false
                )
            } else {
                _step.value = s.copy(error = "PIN penyelia salah")
            }
            return
        }

        if (Pin.verify(pin, s.employee)) {
            _step.value = KioskStep.Capture(
                s.employee, s.side,
                pinOk = true, adminOverride = false, noPin = false
            )
            return
        }

        val gagal = s.wrongAttempts + 1
        _step.value = s.copy(
            wrongAttempts = gagal,
            offerAdmin = gagal >= 3,
            error = if (gagal >= 3) "PIN salah $gagal kali" else "PIN salah, coba lagi"
        )
    }

    fun useAdminOverride() {
        val s = _step.value as? KioskStep.AskPin ?: return
        _step.value = s.copy(adminMode = true, error = null)
    }

    fun submitGeoOverride(pin: String) {
        val s = _step.value as? KioskStep.GeoBlocked ?: return
        if (pin != state.value.settings.kioskAdminPin) {
            _step.value = s.copy(error = "PIN penyelia salah")
            return
        }
        commit(s.pending.copy(adminOverride = true))
    }

    fun cancel() {
        (_step.value as? KioskStep.GeoBlocked)?.pending?.photo?.delete()
        _step.value = KioskStep.Grid
    }

    /**
     * Dipanggil setelah foto selesai diambil. Di sini geofence diperiksa:
     * pada mode wajib, absen di luar radius ditahan dan baru diteruskan bila
     * penyelia memasukkan PIN.
     */
    fun onPhotoTaken(photo: File?, fix: LocationFix?) {
        val s = _step.value as? KioskStep.Capture ?: return
        val settings = state.value.settings

        val jarak = if (fix != null && settings.geoLat != null && settings.geoLon != null) {
            AttendanceRules.distanceMeters(fix.lat, fix.lon, settings.geoLat, settings.geoLon)
        } else null

        val diLuar = when {
            settings.geoMode == GeoMode.OFF -> false
            jarak == null -> true
            else -> jarak > settings.geoRadiusMeters
        }

        val pending = PendingPunch(
            employee = s.employee,
            side = s.side,
            pinOk = s.pinOk,
            adminOverride = s.adminOverride,
            noPin = s.noPin,
            photo = photo,
            fix = fix,
            distanceMeters = jarak,
            outside = diLuar
        )

        if (settings.geoMode == GeoMode.STRICT && diLuar && !s.adminOverride) {
            val alasan = when {
                fix == null -> "Lokasi belum didapat. Nyalakan GPS dan tunggu sinyal."
                fix.mocked -> "Lokasi terdeteksi palsu."
                jarak == null -> "Titik cafe belum diatur admin."
                else -> "Berada ${AttendanceRules.formatMeters(jarak)} dari cafe, " +
                    "batasnya ${settings.geoRadiusMeters} m."
            }
            _step.value = KioskStep.GeoBlocked(pending, alasan)
            return
        }

        commit(pending)
    }

    private fun commit(p: PendingPunch) {
        val st = state.value
        val settings = st.settings
        val now = Instant.now()

        val punch = Punch(
            at = now,
            lat = p.fix?.lat,
            lon = p.fix?.lon,
            accuracyMeters = p.fix?.accuracyMeters,
            distanceMeters = p.distanceMeters,
            outsideGeofence = p.outside,
            photoPath = "",
            pinOk = p.pinOk,
            adminOverride = p.adminOverride,
            noPin = p.noPin
        )

        val peringatan = buildList {
            if (p.noPin) add("Tanpa PIN")
            if (p.adminOverride) add("Izin penyelia")
            if (p.outside) add("Di luar area")
            if (p.fix?.mocked == true) add("Lokasi palsu")
        }

        val terbuka = st.records.openRecordFor(p.employee.id, now)
        if (p.side == PunchSide.OUT && terbuka != null) {
            val shift = st.shiftById(terbuka.shiftId)
            val lengkap = AttendanceRules.withComputedTotals(
                terbuka.copy(checkOut = punch), shift, settings, zone
            )
            repo.saveCheckOut(lengkap)
            p.photo?.let {
                PhotoUploadWorker.enqueue(getApplication<Application>(), lengkap.id, PunchSide.OUT, it)
            }

            _step.value = KioskStep.Done(
                employeeName = p.employee.name,
                side = PunchSide.OUT,
                at = now,
                lateMinutes = lengkap.lateMinutes,
                workMinutes = lengkap.workMinutes,
                overtimeMinutes = lengkap.overtimeMinutes,
                warnings = peringatan + buildList {
                    if (lengkap.earlyLeaveMinutes > 0) {
                        add("Pulang cepat ${AttendanceRules.formatDuration(lengkap.earlyLeaveMinutes)}")
                    }
                }
            )
            return
        }

        // Absen masuk.
        val today = LocalDate.now(zone)
        val kemarin = AttendanceRules.formatDate(today.minusDays(1))
        val shiftKemarin = st.shiftById(st.roster[kemarin]?.assign?.get(p.employee.id))
        val tanggalKerja = AttendanceRules.resolveWorkDate(now, zone, shiftKemarin)
        val kunciTanggal = AttendanceRules.formatDate(tanggalKerja)

        val tugas = st.roster[kunciTanggal]?.assign?.get(p.employee.id)
        val shift = if (tugas == ROSTER_OFF) null else st.shiftById(tugas)
        val diLuarJadwal = shift == null

        val telat = AttendanceRules.lateMinutes(
            checkInAt = now,
            date = tanggalKerja,
            shift = shift,
            toleranceMinutes = AttendanceRules.toleranceFor(p.employee, settings),
            zone = zone
        )

        val record = AttendanceRecord(
            id = repo.newRecordId(),
            employeeId = p.employee.id,
            employeeName = p.employee.name,
            date = kunciTanggal,
            shiftId = shift?.id.orEmpty(),
            shiftName = shift?.name.orEmpty(),
            shiftStart = shift?.start.orEmpty(),
            shiftEnd = shift?.end.orEmpty(),
            offSchedule = diLuarJadwal,
            checkIn = punch,
            lateMinutes = telat,
            deviceId = deviceId
        )
        repo.saveCheckIn(record)
        p.photo?.let {
            PhotoUploadWorker.enqueue(getApplication<Application>(), record.id, PunchSide.IN, it)
        }

        _step.value = KioskStep.Done(
            employeeName = p.employee.name,
            side = PunchSide.IN,
            at = now,
            lateMinutes = telat,
            workMinutes = 0,
            overtimeMinutes = 0,
            warnings = peringatan + buildList {
                if (diLuarJadwal) add("Di luar jadwal")
            }
        )
    }

    fun setDeviceLabel(label: String, versionName: String) {
        viewModelScope.launch {
            deviceStore.setLabel(label)
            repo.touchDevice(deviceStore.deviceId(), deviceStore.label.first(), versionName)
        }
    }

    fun touchDevice(versionName: String) {
        viewModelScope.launch {
            repo.touchDevice(deviceStore.deviceId(), deviceStore.label.first(), versionName)
        }
    }
}
