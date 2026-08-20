package id.omi.absensicafe.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Menemukan wajah pada foto absen lalu mengubahnya menjadi embedding.
 *
 * Dua langkah yang sengaja dipisah dari kamera: verifikasi dijalankan atas
 * berkas foto yang sudah tersimpan, bukan atas aliran pratinjau. Dengan begitu
 * jalur absen tidak berubah sama sekali — kalau bagian wajah gagal, fotonya
 * tetap ada dan absennya tetap tercatat.
 */
class FaceScanner(private val embedder: FaceEmbedder) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            // Akurat, bukan cepat: satu wajah per absen, dan salah potong jauh
            // lebih merugikan daripada tambahan seratus milidetik.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
    )

    val available: Boolean get() = embedder.available
    val modelId: String get() = embedder.modelId

    /**
     * Mengembalikan embedding wajah pada berkas foto, atau null bila tidak ada
     * wajah yang bisa dipakai.
     */
    suspend fun embedFrom(file: File): List<Float>? {
        if (!embedder.available) return null
        val bitmap = decode(file) ?: return null
        return try {
            val wajah = terbesar(bitmap) ?: return null
            val potongan = crop(bitmap, wajah.boundingBox) ?: return null
            embedder.embed(potongan).also { potongan.recycle() }
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Embedding dari bitmap yang sudah ada, dipakai layar pendaftaran. */
    suspend fun embedFrom(bitmap: Bitmap): List<Float>? {
        if (!embedder.available) return null
        val wajah = terbesar(bitmap) ?: return null
        val potongan = crop(bitmap, wajah.boundingBox) ?: return null
        return embedder.embed(potongan).also { potongan.recycle() }
    }

    private suspend fun terbesar(bitmap: Bitmap): Face? {
        val hasil = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        // Kalau ada dua orang di dalam bingkai, yang terdekat ke kamera yang
        // dianggap sedang absen.
        return hasil.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
    }

    /**
     * Memotong wajah dengan sedikit ruang di sekitarnya.
     *
     * Model embedding dilatih atas potongan yang memuat dahi dan dagu, bukan
     * kotak wajah yang mepet; tanpa marjin ini skornya turun untuk orang yang
     * sama.
     */
    private fun crop(bitmap: Bitmap, kotak: Rect): Bitmap? {
        val marjin = (max(kotak.width(), kotak.height()) * MARGIN).roundToInt()
        val kiri = max(0, kotak.left - marjin)
        val atas = max(0, kotak.top - marjin)
        val kanan = min(bitmap.width, kotak.right + marjin)
        val bawah = min(bitmap.height, kotak.bottom + marjin)
        val lebar = kanan - kiri
        val tinggi = bawah - atas
        if (lebar <= 0 || tinggi <= 0) return null
        return Bitmap.createBitmap(bitmap, kiri, atas, lebar, tinggi)
    }

    /**
     * Membaca foto pada ukuran yang cukup untuk deteksi.
     *
     * Foto kamera tablet bisa belasan megapiksel; memuatnya utuh gampang
     * membuat aplikasi mati kehabisan memori di perangkat murah, sementara
     * deteksi wajah tidak butuh sebanyak itu.
     */
    private fun decode(file: File): Bitmap? {
        val batas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, batas)
        val terbesarSisi = max(batas.outWidth, batas.outHeight)
        if (terbesarSisi <= 0) return null

        var contoh = 1
        while (terbesarSisi / (contoh * 2) >= MAX_SIDE) contoh *= 2

        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = contoh }
        ) ?: return null

        return tegakkan(file, bitmap)
    }

    /** Kamera menyimpan arah di EXIF, bukan pada pikselnya. */
    private fun tegakkan(file: File, bitmap: Bitmap): Bitmap {
        val derajat = try {
            when (
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }
        } catch (_: Exception) {
            return bitmap
        }
        val m = Matrix().apply { postRotate(derajat) }
        val diputar = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (diputar !== bitmap) bitmap.recycle()
        return diputar
    }

    fun close() {
        detector.close()
        embedder.close()
    }

    private companion object {
        /** Bagian sisi kotak wajah yang ditambahkan di setiap tepi. */
        const val MARGIN = 0.18f
        const val MAX_SIDE = 1024
    }
}
