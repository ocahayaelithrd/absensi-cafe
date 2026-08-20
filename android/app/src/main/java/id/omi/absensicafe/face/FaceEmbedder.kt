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
     * Menyusun tensor masukan dari piksel.
     *
     * Model wajah float umumnya dilatih dengan piksel yang dipetakan ke
     * rentang -1..1; itu yang dipakai di sini. Model terkuantisasi menerima
     * byte apa adanya.
     */
    private fun bufferOf(bitmap: Bitmap): ByteBuffer {
        val bytesPerChannel = if (quantized) 1 else 4
        val buffer = ByteBuffer
            .allocateDirect(inputWidth * inputHeight * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())

        val piksel = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(piksel, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        for (p in piksel) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (quantized) {
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            } else {
                buffer.putFloat((r - 127.5f) / 127.5f)
                buffer.putFloat((g - 127.5f) / 127.5f)
                buffer.putFloat((b - 127.5f) / 127.5f)
            }
        }
        buffer.rewind()
        return buffer
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
