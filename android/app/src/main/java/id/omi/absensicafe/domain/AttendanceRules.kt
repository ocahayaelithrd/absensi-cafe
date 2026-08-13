package id.omi.absensicafe.domain

import id.omi.absensicafe.data.model.AttendanceRecord
import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.data.model.FineTier
import id.omi.absensicafe.data.model.Settings
import id.omi.absensicafe.data.model.Shift
import id.omi.absensicafe.data.model.minutesOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Perhitungan telat, pulang cepat, durasi kerja, lembur, dan denda.
 *
 * Semuanya murni: tidak menyentuh jaringan maupun penyimpanan, sehingga aturan
 * yang sama bisa dijalankan ulang oleh web admin saat catatan dikoreksi.
 */
object AttendanceRules {

    /** Batas sebuah absen masuk dianggap masih menunggu absen pulang. */
    val MAX_OPEN = Duration.ofHours(18)

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun formatDate(date: LocalDate): String = date.format(DATE)

    fun parseDate(text: String): LocalDate = LocalDate.parse(text, DATE)

    /** Awal shift pada tanggal kerja tertentu. */
    fun shiftStart(date: LocalDate, shift: Shift, zone: ZoneId): Instant =
        date.atTime(LocalTime.ofSecondOfDay(minutesOf(shift.start) * 60L))
            .atZone(zone).toInstant()

    /** Akhir shift; shift lewat tengah malam berakhir di tanggal berikutnya. */
    fun shiftEnd(date: LocalDate, shift: Shift, zone: ZoneId): Instant {
        val day = if (shift.crossesMidnight) date.plusDays(1) else date
        return day.atTime(LocalTime.ofSecondOfDay(minutesOf(shift.end) * 60L))
            .atZone(zone).toInstant()
    }

    /** Toleransi yang berlaku untuk seorang karyawan. */
    fun toleranceFor(employee: Employee, settings: Settings): Int =
        employee.toleranceMinutes ?: settings.toleranceMinutes

    /**
     * Menit keterlambatan absen masuk. Nol bila tepat waktu, masih dalam
     * toleransi, atau tidak ada shift yang dijadwalkan hari itu.
     */
    fun lateMinutes(
        checkInAt: Instant,
        date: LocalDate,
        shift: Shift?,
        toleranceMinutes: Int,
        zone: ZoneId
    ): Int {
        if (shift == null) return 0
        val batas = shiftStart(date, shift, zone).plusSeconds(toleranceMinutes * 60L)
        val selisih = Duration.between(batas, checkInAt).toMinutes()
        return selisih.coerceAtLeast(0).toInt()
    }

    /** Menit pulang lebih cepat dari jadwal. */
    fun earlyLeaveMinutes(
        checkOutAt: Instant,
        date: LocalDate,
        shift: Shift?,
        zone: ZoneId
    ): Int {
        if (shift == null) return 0
        val selesai = shiftEnd(date, shift, zone)
        return Duration.between(checkOutAt, selesai).toMinutes().coerceAtLeast(0).toInt()
    }

    /**
     * Menit lembur. Kelebihan jam di bawah [Settings.minOvertimeMinutes] tidak
     * dihitung, supaya pulang telat beberapa menit karena merapikan meja tidak
     * ikut jadi upah lembur.
     */
    fun overtimeMinutes(
        checkOutAt: Instant,
        date: LocalDate,
        shift: Shift?,
        settings: Settings,
        zone: ZoneId
    ): Int {
        if (shift == null) return 0
        val selesai = shiftEnd(date, shift, zone)
        val lebih = Duration.between(selesai, checkOutAt).toMinutes().coerceAtLeast(0).toInt()
        return if (lebih >= settings.minOvertimeMinutes) lebih else 0
    }

    fun workMinutes(checkInAt: Instant, checkOutAt: Instant): Int =
        Duration.between(checkInAt, checkOutAt).toMinutes().coerceAtLeast(0).toInt()

    /**
     * Denda untuk sejumlah menit telat.
     *
     * Dihitung saat ditampilkan, bukan disimpan di catatan absen, supaya
     * perubahan tarif langsung berlaku serempak di seluruh rekap dan ekspor.
     */
    fun fineFor(lateMinutes: Int, settings: Settings): Long {
        if (!settings.fineEnabled || lateMinutes <= 0) return 0
        val tiers = sortTiers(settings.fineTiers)
        if (tiers.isEmpty()) return 0
        for (t in tiers) {
            val batas = t.upToMinutes ?: return t.amount
            if (lateMinutes <= batas) return t.amount
        }
        return tiers.last().amount
    }

    /** Tingkat denda diurutkan naik; tingkat tanpa batas selalu di akhir. */
    fun sortTiers(tiers: List<FineTier>): List<FineTier> =
        tiers.sortedWith(compareBy({ it.upToMinutes == null }, { it.upToMinutes ?: Int.MAX_VALUE }))

    /**
     * Menentukan tanggal kerja sebuah absen masuk.
     *
     * Biasanya tanggal hari ini. Perkecualiannya shift malam: karyawan yang
     * kesiangan dan baru datang pukul 00:30 tetap masuk ke tanggal kemarin,
     * selama shift kemarin memang lewat tengah malam dan belum berakhir.
     */
    fun resolveWorkDate(
        now: Instant,
        zone: ZoneId,
        shiftYesterday: Shift?,
        graceAfterEnd: Duration = Duration.ofHours(1)
    ): LocalDate {
        val today = now.atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        if (shiftYesterday != null && shiftYesterday.crossesMidnight) {
            val selesai = shiftEnd(yesterday, shiftYesterday, zone).plus(graceAfterEnd)
            if (now.isBefore(selesai)) return yesterday
        }
        return today
    }

    /** Melengkapi catatan dengan hitungan setelah absen pulang tercatat. */
    fun withComputedTotals(
        record: AttendanceRecord,
        shift: Shift?,
        settings: Settings,
        zone: ZoneId
    ): AttendanceRecord {
        val masuk = record.checkIn ?: return record
        val date = parseDate(record.date)
        val pulang = record.checkOut
            ?: return record.copy(
                lateMinutes = record.lateMinutes,
                workMinutes = 0,
                earlyLeaveMinutes = 0,
                overtimeMinutes = 0
            )
        val efektif = if (record.offSchedule) null else shift
        return record.copy(
            workMinutes = workMinutes(masuk.at, pulang.at),
            earlyLeaveMinutes = earlyLeaveMinutes(pulang.at, date, efektif, zone),
            overtimeMinutes = overtimeMinutes(pulang.at, date, efektif, settings, zone)
        )
    }

    /**
     * Jarak dua titik bumi dalam meter (haversine).
     *
     * Radius bumi rata-rata sudah cukup teliti untuk jarak puluhan meter di
     * sekitar cafe, dan tidak perlu Play Services sehingga bisa dipakai juga
     * saat menghitung ulang di layar rekap.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dp / 2) * Math.sin(dp / 2) +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /** "7j 30m" untuk ditampilkan; menit negatif dianggap nol. */
    fun formatDuration(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val j = m / 60
        val sisa = m % 60
        return if (j > 0) "${j}j ${sisa}m" else "${sisa}m"
    }

    fun formatMeters(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m"
        else String.format("%.2f km", meters / 1000)
}
