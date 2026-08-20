package id.omi.absensicafe.domain

import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.data.model.FaceMode
import id.omi.absensicafe.data.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMatchTest {

    private val model = "face_embedder.tflite-1234-192"

    private fun karyawan(
        templates: List<List<Float>> = emptyList(),
        faceModel: String = model
    ) = Employee(
        id = "e1",
        name = "Abner",
        faceTemplates = templates,
        faceModel = if (templates.isEmpty()) "" else faceModel
    )

    private val settings = Settings(faceMode = FaceMode.STRICT, faceThreshold = 65)

    @Test
    fun `vektor identik menghasilkan seratus persen`() {
        val v = listOf(0.1f, 0.5f, -0.3f, 0.8f)
        assertEquals(100, FaceMatch.similarity(v, v))
    }

    @Test
    fun `vektor tegak lurus menghasilkan nol`() {
        assertEquals(0, FaceMatch.similarity(listOf(1f, 0f), listOf(0f, 1f)))
    }

    @Test
    fun `kemiripan negatif dipotong ke nol`() {
        assertEquals(0, FaceMatch.similarity(listOf(1f, 0f), listOf(-1f, 0f)))
    }

    @Test
    fun `panjang vektor tidak mempengaruhi kemiripan`() {
        val a = listOf(0.2f, 0.4f)
        val b = listOf(2f, 4f)
        assertEquals(100, FaceMatch.similarity(a, b))
    }

    @Test
    fun `ukuran berbeda tidak dipaksakan cocok`() {
        assertEquals(0, FaceMatch.similarity(listOf(1f, 0f), listOf(1f, 0f, 0f)))
    }

    @Test
    fun `kemiripan terbaik diambil dari template yang paling cocok`() {
        val calon = listOf(1f, 0f)
        val templates = listOf(
            listOf(0f, 1f),   // 0%
            listOf(1f, 0f)    // 100%
        )
        assertEquals(100, FaceMatch.bestSimilarity(calon, templates))
    }

    @Test
    fun `template dari model lain tidak dipakai`() {
        val e = karyawan(listOf(listOf(1f, 0f)), faceModel = "model-lain-128")
        assertTrue(FaceMatch.usableTemplates(e, model).isEmpty())
    }

    @Test
    fun `mode nonaktif melewati pemeriksaan`() {
        val hasil = FaceMatch.decide(
            candidate = listOf(1f, 0f),
            employee = karyawan(listOf(listOf(1f, 0f))),
            modelId = model,
            settings = settings.copy(faceMode = FaceMode.OFF)
        )
        assertEquals(FaceOutcome.DISABLED, hasil.outcome)
    }

    @Test
    fun `wajah yang belum didaftarkan tidak menahan absen`() {
        val hasil = FaceMatch.decide(listOf(1f, 0f), karyawan(), model, settings)
        assertEquals(FaceOutcome.NOT_ENROLLED, hasil.outcome)
        assertFalse(hasil.flagged)
        assertFalse(hasil.blocks(FaceMode.STRICT))
    }

    @Test
    fun `model yang tidak tersedia tidak menahan absen`() {
        val hasil = FaceResult(FaceOutcome.UNAVAILABLE)
        assertFalse(hasil.flagged)
        assertFalse(hasil.blocks(FaceMode.STRICT))
    }

    @Test
    fun `wajah tidak terdeteksi ditandai dan menahan pada mode wajib`() {
        val hasil = FaceMatch.decide(null, karyawan(listOf(listOf(1f, 0f))), model, settings)
        assertEquals(FaceOutcome.NO_FACE, hasil.outcome)
        assertTrue(hasil.flagged)
        assertTrue(hasil.blocks(FaceMode.STRICT))
        assertFalse(hasil.blocks(FaceMode.WARN))
    }

    @Test
    fun `di atas ambang dianggap cocok`() {
        val hasil = FaceMatch.decide(
            listOf(1f, 0f),
            karyawan(listOf(listOf(1f, 0f))),
            model,
            settings
        )
        assertEquals(FaceOutcome.PASSED, hasil.outcome)
        assertEquals(100, hasil.score)
        assertFalse(hasil.flagged)
    }

    @Test
    fun `di bawah ambang ditandai tapi hanya menahan pada mode wajib`() {
        // Sudut 60 derajat: kosinus 0,5 -> 50 persen, di bawah ambang 65.
        val hasil = FaceMatch.decide(
            listOf(1f, 0f),
            karyawan(listOf(listOf(0.5f, 0.866f))),
            model,
            settings
        )
        assertEquals(FaceOutcome.LOW, hasil.outcome)
        assertEquals(50, hasil.score)
        assertTrue(hasil.flagged)
        assertTrue(hasil.blocks(FaceMode.STRICT))
        assertFalse(hasil.blocks(FaceMode.WARN))
        assertFalse(hasil.blocks(FaceMode.OFF))
    }
}
