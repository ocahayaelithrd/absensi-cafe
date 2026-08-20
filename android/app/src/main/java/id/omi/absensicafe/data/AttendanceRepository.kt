package id.omi.absensicafe.data

import com.google.firebase.firestore.FirebaseFirestore
import id.omi.absensicafe.data.model.AttendanceRecord
import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.data.model.RosterDay
import id.omi.absensicafe.data.model.Settings
import id.omi.absensicafe.data.model.Shift
import id.omi.absensicafe.domain.AttendanceRules
import id.omi.absensicafe.domain.NameSort
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Satu-satunya pintu ke Firestore untuk tablet kios.
 *
 * Seluruh data cafe bersarang di bawah satu dokumen `cafe/main`, warisan
 * aplikasi versi lama. Jalurnya dikumpulkan di kelas ini supaya tidak ada
 * string "cafe/main" yang berserakan di layar-layar.
 *
 * Semua bacaan lewat pendengar snapshot, bukan sekali ambil, karena Firestore
 * melayani pendengar dari simpanan lokal saat internet mati — begitu data
 * pernah tersinkron sekali, absensi tetap jalan tanpa jaringan.
 *
 * Penulisan sengaja **tidak ditunggu**. Saat luring, `Task` dari Firestore baru
 * selesai setelah tersambung lagi, jadi menunggunya akan menggantung layar
 * karyawan. Perubahannya sendiri sudah langsung terlihat di pendengar lokal dan
 * dikirim sendiri oleh Firestore begitu ada jaringan.
 */
class AttendanceRepository(
    private val db: FirebaseFirestore,
    private val zone: ZoneId = ZoneId.systemDefault()
) {

    private val cafe get() = db.collection("cafe").document("main")
    private val employees get() = cafe.collection("employees")
    private val shifts get() = cafe.collection("shifts")
    private val roster get() = cafe.collection("roster")
    private val records get() = cafe.collection("records")
    private val devices get() = cafe.collection("devices")

    fun settingsFlow(): Flow<Settings> = callbackFlow {
        val reg = cafe.addSnapshotListener { snap, _ ->
            trySend(if (snap != null && snap.exists()) snap.toSettings() else Settings())
        }
        awaitClose { reg.remove() }
    }

    fun employeesFlow(ascending: Boolean = true): Flow<List<Employee>> = callbackFlow {
        val reg = employees.addSnapshotListener { snap, _ ->
            val list = snap?.documents.orEmpty()
                .mapNotNull { it.toEmployee() }
                .filter { it.active }
                .sortedWith(NameSort.by(ascending) { it.name })
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    fun shiftsFlow(): Flow<List<Shift>> = callbackFlow {
        val reg = shifts.addSnapshotListener { snap, _ ->
            val list = snap?.documents.orEmpty()
                .mapNotNull { it.toShift() }
                .sortedWith(compareBy({ it.start }, { it.name }))
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    /** Roster beberapa hari sekaligus, untuk menampung shift yang lewat tengah malam. */
    fun rosterFlow(dates: List<String>): Flow<Map<String, RosterDay>> = callbackFlow {
        val hasil = mutableMapOf<String, RosterDay>()
        val regs = dates.map { tanggal ->
            roster.document(tanggal).addSnapshotListener { snap, _ ->
                hasil[tanggal] = snap?.takeIf { it.exists() }?.toRosterDay()
                    ?: RosterDay(tanggal)
                trySend(hasil.toMap())
            }
        }
        awaitClose { regs.forEach { it.remove() } }
    }

    /**
     * Absen pada rentang tanggal tertentu.
     *
     * Kios hanya perlu beberapa hari terakhir; menahan pendengar di situ
     * sekaligus menghangatkan simpanan lokal, sehingga daftar absen hari ini
     * dan pencarian absen yang belum ditutup tetap benar saat internet mati.
     */
    fun recordsFlow(dates: List<String>): Flow<List<AttendanceRecord>> = callbackFlow {
        if (dates.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = records.whereIn("date", dates.take(10))
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents.orEmpty()
                    .mapNotNull { it.toRecord() }
                    .sortedByDescending { it.checkIn?.at }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Tanggal yang perlu dipantau kios: dua hari ke belakang, hari ini, dan
     * besok.
     *
     * Ke belakang untuk menemukan absen masuk yang belum ditutup, termasuk
     * shift malam yang melewati tengah malam. Ke depan supaya jadwal besok
     * sudah tersimpan lokal sebelum harinya tiba — tanpa itu ada celah sesaat
     * lewat tengah malam ketika kios belum sempat memasang pendengar untuk
     * tanggal baru, dan absen paling pagi bisa tertandai di luar jadwal.
     */
    fun watchedDates(today: LocalDate = LocalDate.now(zone)): List<String> =
        (-1L..2L).map { AttendanceRules.formatDate(today.minusDays(it)) }

    /**
     * Pengenal bergaya aplikasi lama: waktu dalam basis 36 ditambah beberapa
     * huruf acak, sehingga dokumen baru terurut menurut waktu pembuatan dan
     * tidak terlihat asing di samping data lama.
     */
    fun newRecordId(): String =
        System.currentTimeMillis().toString(36) +
            (1..5).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

    fun saveRecord(record: AttendanceRecord) {
        records.document(record.id).set(record.toMap())
    }

    /**
     * Menyimpan wajah yang baru didaftarkan.
     *
     * Ini satu-satunya field karyawan yang boleh ditulis kios; aturan Firestore
     * membatasinya tepat pada dua field ini. Pendaftaran memang harus terjadi di
     * tablet — kamera, jarak, dan pencahayaannya sama dengan saat absen
     * sehari-hari, dan itu yang paling menentukan akurasinya.
     */
    fun saveFaceTemplates(employeeId: String, templates: List<List<Float>>, modelId: String) {
        employees.document(employeeId).update(
            mapOf(
                "faceTemplates" to templates.toTemplateMap(),
                "faceModel" to modelId
            )
        )
    }

    fun clearFaceTemplates(employeeId: String) {
        employees.document(employeeId).update(
            mapOf("faceTemplates" to emptyMap<String, Any>(), "faceModel" to "")
        )
    }

    /** Menandai tablet ini masih hidup, supaya admin tahu kios mana yang mati. */
    fun touchDevice(deviceId: String, label: String, versionName: String) {
        devices.document(deviceId).set(
            mapOf(
                "label" to label,
                "appVersion" to versionName,
                "lastSeen" to System.currentTimeMillis()
            )
        )
    }
}

/** Absen yang sudah masuk tapi belum pulang, dan belum kedaluwarsa. */
fun List<AttendanceRecord>.openRecordFor(employeeId: String, now: Instant):
    AttendanceRecord? =
    filter { it.employeeId == employeeId && it.checkIn != null && it.checkOut == null }
        .filter { Duration.between(it.checkIn!!.at, now) < AttendanceRules.MAX_OPEN }
        .maxByOrNull { it.checkIn!!.at }
