package id.omi.absensicafe.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Mengubah foto kamera menjadi data URL JPEG kecil.
 *
 * Foto bukti disimpan **di dalam dokumen absen**, bukan sebagai berkas terpisah
 * di Cloud Storage. Alasannya luring: penulisan Firestore mengantre sendiri
 * saat internet cafe mati, sementara unggahan Storage tidak punya antrean —
 * dengan menanam fotonya, absen berikut buktinya tersimpan dalam satu
 * penulisan yang pasti terkirim begitu jaringan hidup.
 *
 * Konsekuensinya ukuran harus dijaga: dokumen Firestore dibatasi 1 MB, dan satu
 * catatan memuat dua foto. Foto dipotong menjadi bujur sangkar [SIZE] piksel
 * dengan mutu [QUALITY], menghasilkan sekitar 20–30 KB per lembar — cukup jelas
 * untuk mengenali wajah, jauh di bawah batas.
 */
object PhotoEncoder {

    private const val SIZE = 360
    private const val QUALITY = 72

    /** Mengembalikan data URL, atau string kosong bila fotonya tidak terbaca. */
    fun encode(file: File): String {
        val bitmap = decodeScaled(file) ?: return ""
        return try {
            val tegak = applyOrientation(file, bitmap)
            val kotak = cropSquare(tegak)
            val kecil = Bitmap.createScaledBitmap(kotak, SIZE, SIZE, true)
            val out = ByteArrayOutputStream()
            kecil.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            "data:image/jpeg;base64," +
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Membaca berkas dengan penyusutan bawaan.
     *
     * Foto kamera tablet bisa belasan megapiksel; memuatnya utuh ke memori
     * hanya untuk dikecilkan menjadi 360 piksel gampang membuat aplikasi mati
     * kehabisan memori di perangkat murah.
     */
    private fun decodeScaled(file: File): Bitmap? {
        val ukuran = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, ukuran)
        val terkecil = min(ukuran.outWidth, ukuran.outHeight)
        if (terkecil <= 0) return null

        var contoh = 1
        while (terkecil / (contoh * 2) >= SIZE) contoh *= 2

        val opsi = BitmapFactory.Options().apply { inSampleSize = contoh }
        return BitmapFactory.decodeFile(file.absolutePath, opsi)
    }

    /** Kamera menyimpan arah di EXIF, bukan pada pikselnya. */
    private fun applyOrientation(file: File, bitmap: Bitmap): Bitmap {
        val exif = try {
            ExifInterface(file.absolutePath)
        } catch (_: Exception) {
            return bitmap
        }
        val derajat = when (
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val m = Matrix().apply { postRotate(derajat) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /** Dipotong di tengah, bukan digepengkan, supaya wajah tidak berubah bentuk. */
    private fun cropSquare(bitmap: Bitmap): Bitmap {
        val sisi = min(bitmap.width, bitmap.height)
        val x = max(0, (bitmap.width - sisi) / 2)
        val y = max(0, (bitmap.height - sisi) / 2)
        return Bitmap.createBitmap(bitmap, x, y, sisi, sisi)
    }
}
