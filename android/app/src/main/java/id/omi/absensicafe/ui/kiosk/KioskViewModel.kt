package id.omi.absensicafe.ui.kiosk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.omi.absensicafe.AbsensiApp
import id.omi.absensicafe.data.PhotoEncoder
import id.omi.absensicafe.data.PunchSide
import id.omi.absensicafe.data.model.AttendanceRecord
import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.data.model.FaceMode
import id.omi.absensicafe.data.model.GeoMode
import id.omi.absensicafe.data.model.PinBy
import id.omi.absensicafe.data.model.Punch
import id.omi.absensicafe.data.model.ROSTER_OFF
import id.omi.absensicafe.data.model.RosterDay
import id.omi.absensicafe.data.model.Settings
import id.omi.absensicafe.data.model.Shift
import id.omi.absensicafe.data.openRecordFor
import id.omi.absensicafe.domain.AttendanceRules
import id.omi.absensicafe.domain.FaceMatch
import id.omi.absensicafe.domain.FaceOutcome
import id.omi.absensicafe.domain.FaceResult
import id.omi.absensicafe.domain.Pin
import id.omi.absensicafe.location.LocationFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val pinBy: PinBy,
        /** Foto sudah diambil dan wajahnya sedang diperiksa. */
        val checking: Boolean = false
    ) : KioskStep

    /**
     * Absen ditahan — di luar area, atau wajahnya tidak cocok. Menunggu izin
     * penyelia.
     */
    data class Blocked(
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

/** Absen yang sudah difoto tapi belum tercatat, menunggu keputusan penjagaan. */
data class PendingPunch(
    val employee: Employee,
    val side: PunchSide,
    val pinBy: PinBy,
    val photo: File?,
    val fix: LocationFix?,
    val distanceMeters: Double?,
    val outside: Boolean,
    val face: FaceResult = FaceResult(FaceOutcome.DISABLED)
)

data class KioskUiState(
    val ready: Boolean = false,
    val settings: Settings = Settings(),
    val employees: List<Employee> = emptyList(),
    val shifts: List<Shift> = emptyList(),
    val roster: Map<String, RosterDay> = emptyMap(),
    val records: List<AttendanceRecord> = emptyList(),
    val sortAscending: Boolean = true,
    /** Galat bacaan Firestore yang perlu ditindak, atau null selama sehat. */
    val bacaanGagal: String? = null
) {
    /** Karyawan yang PIN-nya belum diatur admin, untuk peringatan di layar. */
    val withoutPin: List<String>
        get() = if (!settings.pinRequired) emptyList()
        else employees.filter { !it.hasPin }.map { it.name }

    fun shiftById(id: String?): Shift? = shifts.firstOrNull { it.id == id }

    fun nameOf(employeeId: String): String =
        employees.firstOrNull { it.id == employeeId }?.name ?: "(karyawan dihapus)"
}

class KioskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as AbsensiApp).repository
    private val deviceStore = (app as AbsensiApp).deviceStore
    private val scanner = (app as AbsensiApp).faceScanner
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _step = MutableStateFlow<KioskStep>(KioskStep.Grid)
    val step: StateFlow<KioskStep> = _step.asStateFlow()

    private data class Acuan(
        val settings: Settings,
        val sortAscending: Boolean,
        val shifts: List<Shift>,
        val today: LocalDate
    )

    /**
     * Tanggal hari ini, terbit ulang setiap kali harinya berganti.
     *
     * Tablet kios menyala terus-menerus dan tidak pernah dibuka ulang — layarnya
     * bahkan sengaja dijaga tetap hidup. "Hari ini" yang dihitung sekali saat
     * aplikasi dijalankan karena itu basi lewat tengah malam, dan kios berhenti
     * menemukan jadwal hari berikutnya sehingga setiap absen ditandai di luar
     * jadwal padahal rosternya ada.
     */
    private fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(zone))
            delay(30_000)
        }
    }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<KioskUiState> =
        combine(
            repo.settingsFlow(),
            deviceStore.sortAscending,
            repo.shiftsFlow(),
            todayFlow()
        ) { settings, asc, shifts, today -> Acuan(settings, asc, shifts, today) }
            .flatMapLatest { acuan ->
                val dates = repo.watchedDates(acuan.today)
                combine(
                    repo.employeesFlow(acuan.sortAscending),
                    repo.rosterFlow(dates),
                    repo.recordsFlow(dates),
                    repo.bacaanGagal
                ) { employees, roster, records, gagal ->
                    KioskUiState(
                        ready = true,
                        settings = acuan.settings,
                        employees = employees,
                        shifts = acuan.shifts,
                        roster = roster,
                        records = records,
                        sortAscending = acuan.sortAscending,
                        bacaanGagal = gagal
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KioskUiState())

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
        val settings = state.value.settings

        _step.value = when {
            !settings.pinRequired ->
                KioskStep.Capture(employee, side, PinBy.OFF)

            employee.hasPin ->
                KioskStep.AskPin(employee, side)

            // PIN belum diatur admin. Karyawan tetap dibiarkan absen supaya
            // shift pagi tidak macet, tapi catatannya ditandai "tanpa PIN".
            else ->
                KioskStep.Capture(employee, side, PinBy.KOSONG)
        }
    }

    fun submitPin(pin: String) {
        val s = _step.value as? KioskStep.AskPin ?: return
        if (s.adminMode) {
            if (pin == state.value.settings.kioskAdminPin) {
                _step.value = KioskStep.Capture(s.employee, s.side, PinBy.ADMIN)
            } else {
                _step.value = s.copy(error = "PIN penyelia salah")
            }
            return
        }

        if (Pin.verify(pin, s.employee)) {
            _step.value = KioskStep.Capture(s.employee, s.side, PinBy.PIN)
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

    fun submitOverride(pin: String) {
        val s = _step.value as? KioskStep.Blocked ?: return
        if (pin != state.value.settings.kioskAdminPin) {
            _step.value = s.copy(error = "PIN penyelia salah")
            return
        }
        commit(s.pending.copy(pinBy = PinBy.ADMIN))
    }

    fun cancel() {
        (_step.value as? KioskStep.Blocked)?.pending?.photo?.delete()
        _step.value = KioskStep.Grid
    }

    /**
     * Dipanggil setelah foto selesai diambil.
     *
     * Dua penjagaan diperiksa di sini: lokasi dan wajah. Pada mode wajib,
     * absen yang tidak lolos ditahan dan baru diteruskan bila penyelia
     * memasukkan PIN. Pemeriksaan wajah berjalan di latar karena menjalankan
     * model butuh waktu; layarnya menampilkan keadaan memeriksa supaya karyawan
     * tidak menekan tombol dua kali.
     */
    fun onPhotoTaken(photo: File?, fix: LocationFix?) {
        val s = _step.value as? KioskStep.Capture ?: return
        if (s.checking) return
        _step.value = s.copy(checking = true)

        viewModelScope.launch {
            val settings = state.value.settings

            val jarak = if (fix != null && settings.geoLat != null && settings.geoLon != null) {
                AttendanceRules.distanceMeters(fix.lat, fix.lon, settings.geoLat, settings.geoLon)
            } else null

            val diLuar = when {
                settings.geoMode == GeoMode.OFF -> false
                jarak == null -> true
                else -> jarak > settings.geoRadiusMeters
            }

            val wajah = periksaWajah(photo, s.employee, settings)

            val pending = PendingPunch(
                employee = s.employee,
                side = s.side,
                pinBy = s.pinBy,
                photo = photo,
                fix = fix,
                distanceMeters = jarak,
                outside = diLuar,
                face = wajah
            )

            val sudahDiloloskan = s.pinBy == PinBy.ADMIN

            val alasanLokasi = if (settings.geoMode == GeoMode.STRICT && diLuar) {
                when {
                    fix == null -> "Lokasi belum didapat. Nyalakan GPS dan tunggu sinyal."
                    fix.mocked -> "Lokasi terdeteksi palsu."
                    jarak == null -> "Titik cafe belum diatur admin."
                    else -> "Berada ${AttendanceRules.formatMeters(jarak)} dari cafe, " +
                        "batasnya ${settings.geoRadiusMeters} m."
                }
            } else null

            val alasanWajah = if (wajah.blocks(settings.faceMode)) {
                when (wajah.outcome) {
                    FaceOutcome.NO_FACE ->
                        "Wajah tidak terdeteksi di foto. Hadapkan wajah ke kamera."
                    else ->
                        "Wajah tidak cocok — kemiripan ${wajah.score}%, " +
                            "batasnya ${settings.faceThreshold}%."
                }
            } else null

            val alasan = alasanLokasi ?: alasanWajah
            if (alasan != null && !sudahDiloloskan) {
                _step.value = KioskStep.Blocked(pending, alasan)
                return@launch
            }

            commit(pending)
        }
    }

    /**
     * Memeriksa wajah pada foto yang baru diambil.
     *
     * Setiap kegagalan di jalur ini — model tidak ada, foto tidak terbaca,
     * deteksi meleset — mengembalikan hasil yang tidak menahan absen. Fitur
     * pengaman tidak boleh berubah menjadi penghalang orang bekerja.
     */
    private suspend fun periksaWajah(
        photo: File?,
        employee: Employee,
        settings: Settings
    ): FaceResult {
        if (settings.faceMode == FaceMode.OFF) return FaceResult(FaceOutcome.DISABLED)
        if (photo == null) return FaceResult(FaceOutcome.NO_FACE)
        if (!scanner.available) return FaceResult(FaceOutcome.UNAVAILABLE)

        val vektor = withContext(Dispatchers.Default) { scanner.embedFrom(photo) }
        return FaceMatch.decide(vektor, employee, scanner.modelId, settings)
    }

    /**
     * Mencatat absen.
     *
     * Layar hasil ditampilkan lebih dulu, lalu foto dikecilkan dan dokumennya
     * ditulis di latar. Pengecilan foto memakan ratusan milidetik di tablet
     * murah, dan menahan layar selama itu membuat karyawan mengira absennya
     * gagal lalu menekan ulang.
     */
    private fun commit(p: PendingPunch) {
        val st = state.value
        val settings = st.settings
        val now = Instant.now()

        val peringatan = buildList {
            if (p.pinBy == PinBy.KOSONG) add("Tanpa PIN")
            if (p.pinBy == PinBy.ADMIN) add("Izin penyelia")
            if (p.outside) add("Di luar area")
            if (p.fix?.mocked == true) add("Lokasi palsu")
            if (p.face.flagged) add(FaceMatch.label(p.face))
        }

        val terbuka = st.records.openRecordFor(p.employee.id, now)

        if (p.side == PunchSide.OUT && terbuka != null) {
            val shift = st.shiftById(terbuka.shiftId)
            val dasar = terbuka.copy(checkOut = punchOf(p, now, ""))
            val lengkap = AttendanceRules.withComputedTotals(dasar, shift, settings, zone)

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

            simpanDenganFoto(p) { foto ->
                lengkap.copy(checkOut = lengkap.checkOut?.copy(photo = foto))
            }
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
            date = kunciTanggal,
            shiftId = shift?.id.orEmpty(),
            offSchedule = diLuarJadwal,
            checkIn = punchOf(p, now, ""),
            lateMinutes = telat
        )

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

        simpanDenganFoto(p) { foto ->
            record.copy(checkIn = record.checkIn?.copy(photo = foto))
        }
    }

    private fun punchOf(p: PendingPunch, now: Instant, photo: String) = Punch(
        at = now,
        lat = p.fix?.lat,
        lon = p.fix?.lon,
        accuracyMeters = p.fix?.accuracyMeters,
        distanceMeters = p.distanceMeters,
        outsideGeofence = p.outside,
        pinBy = p.pinBy,
        faceScore = p.face.score,
        faceFlag = p.face.flagged,
        photo = photo
    )

    private fun simpanDenganFoto(
        p: PendingPunch,
        build: (foto: String) -> AttendanceRecord
    ) {
        viewModelScope.launch {
            val foto = p.photo?.let { berkas ->
                withContext(Dispatchers.Default) {
                    PhotoEncoder.encode(berkas).also { berkas.delete() }
                }
            }.orEmpty()
            repo.saveRecord(build(foto))
        }
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
