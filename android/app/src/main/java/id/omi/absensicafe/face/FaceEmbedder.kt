package id.omi.absensicafe.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Mengubah potongan wajah menjadi *embedding* — vektor beberapa ratus angka
 * yang mewakili wajah itu, sehingga dua foto orang yang sama menghasilkan
 * vektor yang searah.
 *
 * Modelnya **tidak disertakan di repo**: berkas `.tflite` harus diletakkan
 * sendiri di `app/src/main/assets/face_embedder.tflite`. Kalau berkasnya tidak
 * ada, [available] bernilai false dan verifikasi wajah dilewati begitu saja —
 * absensi tetap jalan seperti biasa. Itu disengaja: ketiadaan model tidak
 * boleh menghentikan karyawan absen.
 *
 * Bentuk masukan dan keluaran dibaca dari modelnya sendiri, bukan dipatok,
 * supaya model dengan ukuran berbeda tetap bisa dipakai tanpa mengubah kode.
 */
class FaceEmbedder(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var inputWidth = 0
    private var inputHeight = 0
    private var outputDim = 0
    private var quantized = false
    private var id = ""
    private var siap = false
    private var gagal = false

    val available: Boolean
        get() {
            siapkan()
            return siap
        }

    /**
     * Pengenal model, disimpan bersama template wajah.
     *
     * Memuat ukuran berkas dan panjang keluaran, jadi berganti model membuat
     * template lama tidak lagi dianggap sebanding — lebih baik meminta daftar
     * ulang daripada menghasilkan skor yang menyesatkan.
     */
    val modelId: String
        get() {
            siapkan()
            return id
        }

    @Synchronized
    private fun siapkan() {
        if (siap || gagal) return
        try {
            val fd = context.assets.openFd(ASSET)
            val ukuran = fd.length
            val buffer = fd.createInputStream().use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, ukuran)
            }

            val opsi = Interpreter.Options().apply { numThreads = 2 }
            val itp = Interpreter(buffer, opsi)

            val bentukMasuk = itp.getInputTensor(0).shape()   // [1, tinggi, lebar, 3]
            val bentukKeluar = itp.getOutputTensor(0).shape() // [1, dimensi]
            if (bentukMasuk.size != 4 || bentukKeluar.size != 2) {
                itp.close()
                gagal = true
                return
            }

            inputHeight = bentukMasuk[1]
            inputWidth = bentukMasuk[2]
            outputDim = bentukKeluar[1]
            quantized = itp.getInputTensor(0).dataType() == DataType.UINT8

            interpreter = itp
            id = "$ASSET-$ukuran-$outputDim"
            siap = true
        } catch (_: Exception) {
            // Berkas model tidak ada, atau tidak bisa dibaca. Bukan alasan
            // menghentikan absensi.
            gagal = true
        }
    }

    /** Ukuran potongan wajah yang diharapkan model. */
    fun inputSize(): Pair<Int, Int> {
        siapkan()
        return inputWidth to inputHeight
    }

    /**
     * Menghitung embedding satu potongan wajah.
     *
     * `Interpreter` tidak aman dipakai dari beberapa utas sekaligus, jadi
     * seluruh pemanggilan diserialkan.
     */
    @Synchronized
    fun embed(face: Bitmap): List<Float>? {
        siapkan()
        val itp = interpreter ?: return null
        return try {
            val skala = if (face.width == inputWidth && face.height == inputHeight) face
            else Bitmap.createScaledBitmap(face, inputWidth, inputHeight, true)

            val masukan = bufferOf(skala)
            val keluaran = Array(1) { FloatArray(outputDim) }
            itp.run(masukan, keluaran)

            if (skala !== face) skala.recycle()
            normalisasi(keluaran[0])
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cara piksel disiapkan sebelum masuk ke model.
     *
     * Ini **harus cocok dengan cara modelnya dilatih**. Salah pilih tidak
     * memunculkan galat apa pun — skornya sekadar turun, dan penyebabnya
     * hampir tidak mungkin ditebak dari hasilnya.
     */
    private enum class Normalization {
        /** Piksel dipetakan ke -1..1. Dipakai MobileFaceNet dan model keluarga InsightFace. */
        SIGNED,

        /**
         * Setiap foto distandarkan memakai rata-rata dan simpangannya sendiri
         * (*prewhitening*). Dipakai FaceNet, termasuk turunan David Sandberg
         * dan keras-facenet.
         */
        STANDARDIZED
    }

    /** Ganti ini kalau model yang dipasang menuntut penyiapan yang lain. */
    private val normalization = Normalization.SIGNED

    /**
     * Menyusun tensor masukan dari piksel. Model terkuantisasi menerima byte
     * apa adanya, tanpa penyiapan.
     */
    private fun bufferOf(bitmap: Bitmap): ByteBuffer {
        val bytesPerChannel = if (quantized) 1 else 4
        val buffer = ByteBuffer
            .allocateDirect(inputWidth * inputHeight * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())

        val piksel = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(piksel, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        if (quantized) {
            for (p in piksel) {
                buffer.put(((p shr 16) and 0xFF).toByte())
                buffer.put(((p shr 8) and 0xFF).toByte())
                buffer.put((p and 0xFF).toByte())
            }
            buffer.rewind()
            return buffer
        }

        val (geser, bagi) = when (normalization) {
            Normalization.SIGNED -> 127.5f to 127.5f
            Normalization.STANDARDIZED -> sebaran(piksel)
        }

        for (p in piksel) {
            buffer.putFloat((((p shr 16) and 0xFF) - geser) / bagi)
            buffer.putFloat((((p shr 8) and 0xFF) - geser) / bagi)
            buffer.putFloat(((p and 0xFF) - geser) / bagi)
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Rata-rata dan simpangan baku seluruh kanal satu foto.
     *
     * Simpangan dibatasi bawah agar foto yang nyaris rata warna — misalnya
     * ruangan gelap — tidak menghasilkan pembagian yang meledak.
     */
    private fun sebaran(piksel: IntArray): Pair<Float, Float> {
        var jumlah = 0.0
        var jumlahKuadrat = 0.0
        val n = piksel.size * 3.0
        for (p in piksel) {
            val r = ((p shr 16) and 0xFF).toDouble()
            val g = ((p shr 8) and 0xFF).toDouble()
            val b = (p and 0xFF).toDouble()
            jumlah += r + g + b
            jumlahKuadrat += r * r + g * g + b * b
        }
        val rata = jumlah / n
        val varians = (jumlahKuadrat / n) - (rata * rata)
        val simpangan = Math.sqrt(Math.max(varians, 0.0))
        return rata.toFloat() to Math.max(simpangan, 1.0).toFloat()
    }

    /** Panjang vektor dijadikan satu, supaya kosinus tidak terpengaruh skala. */
    private fun normalisasi(v: FloatArray): List<Float> {
        var jumlah = 0.0
        for (x in v) jumlah += x.toDouble() * x
        val panjang = Math.sqrt(jumlah).toFloat()
        if (panjang <= 0f) return v.toList()
        return v.map { it / panjang }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        siap = false
    }

    companion object {
        const val ASSET = "face_embedder.tflite"
    }
}
