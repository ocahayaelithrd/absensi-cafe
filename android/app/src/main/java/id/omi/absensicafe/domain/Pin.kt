package id.omi.absensicafe.domain

import id.omi.absensicafe.data.model.Employee
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN pribadi karyawan.
 *
 * PIN hanya empat angka, jadi hash cepat seperti SHA-256 polos bisa ditebak
 * habis dalam hitungan detik kalau isinya bocor. Karena itu dipakai
 * PBKDF2-HMAC-SHA256 dengan garam per karyawan: menebak 10.000 kemungkinan
 * jadi butuh waktu, sementara satu kali pemeriksaan di tablet tetap sekejap.
 *
 * Algoritme dan jumlah putarannya sama persis dengan yang dipakai web admin
 * lewat WebCrypto, sehingga PIN yang dibuat di PC bisa diperiksa di tablet.
 */
object Pin {

    const val LENGTH = 4
    const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    private val random = SecureRandom()

    fun isValidFormat(pin: String): Boolean =
        pin.length == LENGTH && pin.all { it.isDigit() }

    fun newSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, saltHex: String, iterations: Int = ITERATIONS): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.fromHex(), iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.toHex()
    }

    /** Perbandingan waktu tetap, supaya lama proses tidak membocorkan tebakan. */
    fun verify(pin: String, employee: Employee): Boolean {
        if (!employee.hasPin || !isValidFormat(pin)) return false
        val iterations = if (employee.pinIterations > 0) employee.pinIterations else ITERATIONS
        val calon = hash(pin, employee.pinSalt, iterations)
        return MessageDigest.isEqual(
            calon.toByteArray(Charsets.US_ASCII),
            employee.pinHash.toByteArray(Charsets.US_ASCII)
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
