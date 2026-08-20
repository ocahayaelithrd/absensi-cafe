package id.omi.absensicafe.domain

import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.data.model.FaceMode
import id.omi.absensicafe.data.model.Settings
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Hasil pemeriksaan wajah pada satu absen. */
enum class FaceOutcome {
    /** Cocok di atas ambang. */
    PASSED,

    /** Wajah terdeteksi tapi kemiripannya di bawah ambang. */
    LOW,

    /** Tidak ada wajah yang bisa dipakai di foto itu. */
    NO_FACE,

    /** Karyawan itu belum pernah mendaftarkan wajah. */
    NOT_ENROLLED,

    /** Model tidak tersedia atau gagal dijalankan. */
    UNAVAILABLE,

    /** Fitur sedang dimatikan. */
    DISABLED
}

data class FaceResult(
    val outcome: FaceOutcome,
    /** Kemiripan 0–100, null bila tidak sampai terhitung. */
    val score: Int? = null
) {
    /** Perlu ditandai di catatan absen. */
    val flagged: Boolean
        get() = outcome == FaceOutcome.LOW || outcome == FaceOutcome.NO_FACE

    /**
     * Menahan absen. Hanya berlaku pada mode wajib, dan hanya untuk hasil yang
     * memang menunjuk ke orangnya — model yang tidak tersedia atau wajah yang
     * belum didaftarkan bukan kesalahan karyawan, jadi absennya tidak ditahan.
     */
    fun blocks(mode: FaceMode): Boolean =
        mode == FaceMode.STRICT && flagged
}

/**
 * Pencocokan wajah dengan kemiripan kosinus antar embedding.
 *
 * Perhitungannya murni: tidak menyentuh model, kamera, maupun jaringan,
 * sehingga bisa diuji tanpa perangkat.
 */
object FaceMatch {

    /** Jumlah jepretan saat mendaftarkan wajah. */
    const val SHOTS = 3

    /**
     * Kemiripan dua embedding sebagai angka 0–100.
     *
     * Kosinus berkisar -1..1, tapi untuk embedding wajah yang searah nilainya
     * praktis selalu positif; bagian negatif dipotong ke nol supaya angka yang
     * ditampilkan ke admin tidak pernah aneh.
     */
    fun similarity(a: List<Float>, b: List<Float>): Int {
        if (a.isEmpty() || a.size != b.size) return 0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            normA += x * x
            normB += y * y
        }
        if (normA <= 0.0 || normB <= 0.0) return 0
        val cos = dot / (sqrt(normA) * sqrt(normB))
        return (cos * 100).coerceIn(0.0, 100.0).roundToInt()
    }

    /**
     * Kemiripan terbaik terhadap seluruh template karyawan.
     *
     * Diambil yang tertinggi, bukan rata-rata: jepretan pendaftaran sengaja
     * dibuat beberapa kali agar mencakup posisi berbeda, jadi cukup satu yang
     * cocok. Merata-ratakan justru menghukum keragaman yang memang diinginkan.
     */
    fun bestSimilarity(candidate: List<Float>, templates: List<List<Float>>): Int =
        templates.maxOfOrNull { similarity(candidate, it) } ?: 0

    /** Template yang masih sebanding dengan model yang sedang dipakai. */
    fun usableTemplates(employee: Employee, modelId: String): List<List<Float>> =
        if (employee.faceModel.isNotBlank() && employee.faceModel == modelId) {
            employee.faceTemplates
        } else {
            emptyList()
        }

    fun decide(
        candidate: List<Float>?,
        employee: Employee,
        modelId: String,
        settings: Settings
    ): FaceResult {
        if (settings.faceMode == FaceMode.OFF) return FaceResult(FaceOutcome.DISABLED)

        val templates = usableTemplates(employee, modelId)
        if (templates.isEmpty()) return FaceResult(FaceOutcome.NOT_ENROLLED)
        if (candidate == null) return FaceResult(FaceOutcome.NO_FACE)

        val skor = bestSimilarity(candidate, templates)
        return FaceResult(
            outcome = if (skor >= settings.faceThreshold) FaceOutcome.PASSED else FaceOutcome.LOW,
            score = skor
        )
    }

    /** Keterangan singkat untuk ditampilkan di tablet. */
    fun label(result: FaceResult): String = when (result.outcome) {
        FaceOutcome.PASSED -> "Wajah cocok ${result.score}%"
        FaceOutcome.LOW -> "Wajah tidak cocok (${result.score}%)"
        FaceOutcome.NO_FACE -> "Wajah tidak terdeteksi"
        FaceOutcome.NOT_ENROLLED -> "Wajah belum didaftarkan"
        FaceOutcome.UNAVAILABLE -> "Model wajah tidak tersedia"
        FaceOutcome.DISABLED -> ""
    }
}
