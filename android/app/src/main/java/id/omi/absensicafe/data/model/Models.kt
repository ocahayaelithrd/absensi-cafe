package id.omi.absensicafe.data.model

import java.time.Instant

/* Model yang dipakai di dalam aplikasi.
 *
 * Ini BUKAN bentuk dokumen Firestore. Firestore masih memakai struktur warisan
 * aplikasi lama — semuanya di bawah `cafe/main`, dengan nama field seperti
 * `empId`, `inAt`, `lateMin`, dan roster di field `hari`. Penerjemahannya ada
 * di `data/Mappers.kt`, supaya nama lama tidak menyebar ke seluruh kode.
 *
 * Model yang sama ditulis ulang di web admin pada `src/lib/types.ts`. */

/** Mode pembatasan lokasi absen. */
enum class GeoMode { OFF, WARN, STRICT;
    companion object {
        fun from(v: String?) = when (v) {
            "strict" -> STRICT
            "warn" -> WARN
            else -> OFF
        }
    }
    val wire: String get() = name.lowercase()
}

/** Cara sebuah absen diloloskan; di Firestore `inPinBy` / `outPinBy`. */
enum class PinBy(val wire: String) {
    /** PIN pribadi diterima. */
    PIN("pin"),

    /** PIN karyawan itu belum diatur admin. */
    KOSONG("kosong"),

    /** Diloloskan penyelia lewat PIN penyelia. */
    ADMIN("admin"),

    /** Fitur PIN sedang dimatikan di pengaturan. */
    OFF("off");

    companion object {
        fun from(v: String?) = entries.firstOrNull { it.wire == v } ?: OFF
    }
}

/**
 * Satu tingkat denda keterlambatan. [upToMinutes] null berarti tingkat
 * terakhir — berlaku untuk telat berapa pun di atas tingkat sebelumnya.
 */
data class FineTier(
    val upToMinutes: Int?,
    val amount: Long
)

data class Settings(
    val cafeName: String = "Absensi Cafe",
    /** Toleransi telat umum dalam menit, dipakai bila karyawan tidak punya sendiri. */
    val toleranceMinutes: Int = 10,
    /** Lembur baru dihitung bila kelebihan jam mencapai angka ini. */
    val minOvertimeMinutes: Int = 30,
    val fineEnabled: Boolean = true,
    val fineTiers: List<FineTier> = listOf(
        FineTier(15, 5_000),
        FineTier(30, 15_000),
        FineTier(null, 30_000)
    ),
    val geoMode: GeoMode = GeoMode.OFF,
    val geoLat: Double? = null,
    val geoLon: Double? = null,
    val geoRadiusMeters: Int = 100,
    /** PIN yang dipegang penyelia untuk meloloskan absen bermasalah di tablet. */
    val kioskAdminPin: String = "1234",
    /** Karyawan wajib memasukkan PIN pribadi sebelum berfoto. */
    val pinRequired: Boolean = true
)

data class Employee(
    val id: String,
    val name: String,
    /** Jabatan bebas, ikut dari data lama. */
    val role: String = "",
    /**
     * PIN apa adanya dari data lama. Kosong berarti PIN belum diatur, atau
     * sudah dipindahkan ke [pinHash] lewat web admin.
     */
    val plainPin: String = "",
    /** PBKDF2-HMAC-SHA256 dari PIN, dibuat web admin. */
    val pinHash: String = "",
    val pinSalt: String = "",
    val pinIterations: Int = 0,
    /** Toleransi khusus karyawan ini; null berarti ikut pengaturan umum. */
    val toleranceMinutes: Int? = null,
    val active: Boolean = true
) {
    val hasPin: Boolean
        get() = plainPin.isNotBlank() || (pinHash.isNotBlank() && pinSalt.isNotBlank())
}

data class Shift(
    val id: String,
    val code: String,
    val name: String,
    /** Format "HH:mm". Bila [end] lebih awal dari [start], shift lewat tengah malam. */
    val start: String,
    val end: String
) {
    val crossesMidnight: Boolean get() = minutesOf(end) <= minutesOf(start)
}

/** Penugasan shift satu hari: id karyawan -> id shift, atau [ROSTER_OFF] untuk libur. */
data class RosterDay(
    val date: String,
    val assign: Map<String, String> = emptyMap()
)

const val ROSTER_OFF = "off"

/** Satu sisi absen: masuk atau pulang. */
data class Punch(
    val at: Instant,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyMeters: Double? = null,
    /** Jarak ke titik cafe dalam meter, null bila titik cafe belum diatur. */
    val distanceMeters: Double? = null,
    val outsideGeofence: Boolean = false,
    val pinBy: PinBy = PinBy.OFF,
    /**
     * Foto bukti sebagai data URL JPEG, tertanam di dokumen absen. Kosong
     * berarti kamera gagal atau absen itu memang tidak berfoto.
     */
    val photo: String = ""
)

data class AttendanceRecord(
    val id: String,
    val employeeId: String,
    /** Tanggal kerja "yyyy-MM-dd", diambil dari tanggal shift dimulai. */
    val date: String,
    val shiftId: String = "",
    /** Absen di hari yang tidak ada di roster: tercatat, tapi telat & lembur tidak dihitung. */
    val offSchedule: Boolean = false,
    val checkIn: Punch? = null,
    val checkOut: Punch? = null,
    val lateMinutes: Int = 0,
    val earlyLeaveMinutes: Int = 0,
    val workMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val note: String = "",
    /** Pernah dikoreksi admin dari web; di Firestore bernama `edited`. */
    val edited: Boolean = false
) {
    val complete: Boolean get() = checkIn != null && checkOut != null
}

/** "HH:mm" -> menit sejak tengah malam. Nilai tak terbaca dianggap 0. */
fun minutesOf(hhmm: String): Int {
    val parts = hhmm.split(":")
    if (parts.size != 2) return 0
    val h = parts[0].toIntOrNull() ?: return 0
    val m = parts[1].toIntOrNull() ?: return 0
    return (h.coerceIn(0, 23) * 60) + m.coerceIn(0, 59)
}
