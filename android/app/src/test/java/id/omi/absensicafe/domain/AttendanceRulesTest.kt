package id.omi.absensicafe.domain

import id.omi.absensicafe.data.model.FineTier
import id.omi.absensicafe.data.model.Settings
import id.omi.absensicafe.data.model.Shift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AttendanceRulesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")
    private val tanggal: LocalDate = LocalDate.of(2026, 8, 13)
    private val pagi = Shift("p", "P", "Pagi", "07:00", "15:00")
    private val malam = Shift("m", "M", "Malam", "23:00", "07:00")
    private val settings = Settings()

    private fun jam(h: Int, m: Int, date: LocalDate = tanggal) =
        date.atTime(LocalTime.of(h, m)).atZone(zone).toInstant()

    @Test
    fun `datang dalam toleransi tidak dihitung telat`() {
        val telat = AttendanceRules.lateMinutes(jam(7, 10), tanggal, pagi, 10, zone)
        assertEquals(0, telat)
    }

    @Test
    fun `telat dihitung dari batas toleransi bukan dari jam shift`() {
        val telat = AttendanceRules.lateMinutes(jam(7, 25), tanggal, pagi, 10, zone)
        assertEquals(15, telat)
    }

    @Test
    fun `tanpa shift tidak ada telat`() {
        val telat = AttendanceRules.lateMinutes(jam(9, 0), tanggal, null, 10, zone)
        assertEquals(0, telat)
    }

    @Test
    fun `shift malam berakhir di tanggal berikutnya`() {
        val selesai = AttendanceRules.shiftEnd(tanggal, malam, zone)
        assertEquals(jam(7, 0, tanggal.plusDays(1)), selesai)
        assertTrue(malam.crossesMidnight)
        assertFalse(pagi.crossesMidnight)
    }

    @Test
    fun `pulang setelah tengah malam pada shift malam bukan pulang cepat`() {
        val cepat = AttendanceRules.earlyLeaveMinutes(jam(7, 0, tanggal.plusDays(1)), tanggal, malam, zone)
        assertEquals(0, cepat)
    }

    @Test
    fun `lembur di bawah ambang tidak dihitung`() {
        val sebentar = AttendanceRules.overtimeMinutes(jam(15, 20), tanggal, pagi, settings, zone)
        assertEquals(0, sebentar)

        val cukup = AttendanceRules.overtimeMinutes(jam(16, 0), tanggal, pagi, settings, zone)
        assertEquals(60, cukup)
    }

    @Test
    fun `denda mengikuti tingkat yang berlaku`() {
        assertEquals(0L, AttendanceRules.fineFor(0, settings))
        assertEquals(5_000L, AttendanceRules.fineFor(1, settings))
        assertEquals(5_000L, AttendanceRules.fineFor(15, settings))
        assertEquals(15_000L, AttendanceRules.fineFor(16, settings))
        assertEquals(30_000L, AttendanceRules.fineFor(120, settings))
    }

    @Test
    fun `denda mati mengembalikan nol`() {
        val mati = settings.copy(fineEnabled = false)
        assertEquals(0L, AttendanceRules.fineFor(90, mati))
    }

    @Test
    fun `tingkat denda diurutkan dan yang tanpa batas selalu terakhir`() {
        val acak = listOf(
            FineTier(null, 30_000),
            FineTier(30, 15_000),
            FineTier(15, 5_000)
        )
        val urut = AttendanceRules.sortTiers(acak)
        assertEquals(listOf(15, 30, null), urut.map { it.upToMinutes })
    }

    @Test
    fun `absen dini hari masuk ke tanggal shift malam kemarin`() {
        val dinihari = jam(0, 30, tanggal.plusDays(1))
        val hasil = AttendanceRules.resolveWorkDate(dinihari, zone, malam)
        assertEquals(tanggal, hasil)
    }

    @Test
    fun `absen pagi tetap masuk ke tanggal hari ini walau kemarin shift malam`() {
        val pagiHari = jam(8, 0, tanggal.plusDays(1))
        val hasil = AttendanceRules.resolveWorkDate(pagiHari, zone, malam)
        assertEquals(tanggal.plusDays(1), hasil)
    }

    @Test
    fun `jarak dua titik dihitung dalam meter`() {
        // Dua titik berjarak sekitar 111 m pada garis lintang yang sama.
        val meter = AttendanceRules.distanceMeters(-6.200000, 106.816666, -6.201000, 106.816666)
        assertTrue("jarak $meter", meter in 100.0..120.0)
    }
}
