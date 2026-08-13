package id.omi.absensicafe.domain

import java.text.Collator
import java.util.Locale

/**
 * Urutan nama karyawan yang sama di semua layar.
 *
 * Huruf besar-kecil dan tanda aksen diabaikan, dan angka dibaca sebagai angka
 * — sehingga "Budi 2" berada sebelum "Budi 10", bukan sesudahnya.
 */
object NameSort {

    private val collator: Collator = Collator.getInstance(Locale("id", "ID")).apply {
        strength = Collator.PRIMARY
    }

    fun comparator(ascending: Boolean = true): Comparator<String> {
        val base = Comparator<String> { a, b -> compareNatural(a, b) }
        return if (ascending) base else base.reversed()
    }

    fun <T> by(ascending: Boolean = true, name: (T) -> String): Comparator<T> {
        val c = comparator(ascending)
        return Comparator { a, b -> c.compare(name(a), name(b)) }
    }

    private fun compareNatural(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(startA, i).trimStart('0')
                val nb = b.substring(startB, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val cmp = na.compareTo(nb)
                if (cmp != 0) return cmp
            } else {
                val cmp = collator.compare(ca.toString(), cb.toString())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
