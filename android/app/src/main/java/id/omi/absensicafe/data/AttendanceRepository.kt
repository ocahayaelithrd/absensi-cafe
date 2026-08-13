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
import java.time.LocalDate
import java.time.ZoneId

/**
 * Satu-satunya pintu ke Firestore untuk tablet kios.
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

    private val settingsDoc get() = db.collection("config").document("settings")
    private val employees get() = db.collection("employees")
    private val shifts get() = db.collection("shifts")
    private val roster get() = db.collection("roster")
    private val records get() = db.collection("records")

    fun settingsFlow(): Flow<Settings> = callbackFlow {
        val reg = settingsDoc.addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) trySend(snap.toSettings())
            else trySend(Settings())
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
                .sortedWith(compareBy({ it.order }, { it.name }))
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    /** Roster dua hari: hari ini dan kemarin, untuk menampung shift malam. */
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

    /** Tanggal yang perlu dipantau kios: hari ini dan dua hari sebelumnya. */
    fun watchedDates(today: LocalDate = LocalDate.now(zone)): List<String> =
        (0L..2L).map { AttendanceRules.formatDate(today.minusDays(it)) }

    fun newRecordId(): String = records.document().id

    fun saveCheckIn(record: AttendanceRecord) {
        records.document(record.id).set(record.toMap())
    }

    fun saveCheckOut(record: AttendanceRecord) {
        records.document(record.id).set(record.toMap())
    }

    /** Menandai tablet ini masih hidup, supaya admin tahu kios mana yang mati. */
    fun touchDevice(deviceId: String, label: String, versionName: String) {
        db.collection("devices").document(deviceId).set(
            mapOf(
                "label" to label,
                "appVersion" to versionName,
                "lastSeen" to com.google.firebase.Timestamp.now()
            )
        )
    }

}

/** Sisi absen, dipakai untuk menyusun nama berkas dan nama field. */
enum class PunchSide(val field: String, val fileName: String, val label: String) {
    IN("checkIn", "masuk.jpg", "Masuk"),
    OUT("checkOut", "pulang.jpg", "Pulang");

    companion object {
        fun from(name: String?): PunchSide = if (name == OUT.name) OUT else IN
    }
}

/** Absen yang sudah masuk tapi belum pulang, dan belum kedaluwarsa. */
fun List<AttendanceRecord>.openRecordFor(employeeId: String, now: java.time.Instant):
    AttendanceRecord? =
    filter { it.employeeId == employeeId && it.checkIn != null && it.checkOut == null }
        .filter { java.time.Duration.between(it.checkIn!!.at, now) < AttendanceRules.MAX_OPEN }
        .maxByOrNull { it.checkIn!!.at }
