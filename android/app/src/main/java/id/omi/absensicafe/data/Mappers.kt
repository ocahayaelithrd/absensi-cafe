package id.omi.absensicafe.data

import com.google.firebase.firestore.DocumentSnapshot
import id.omi.absensicafe.data.model.AttendanceRecord
import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.data.model.FineTier
import id.omi.absensicafe.data.model.GeoMode
import id.omi.absensicafe.data.model.PinBy
import id.omi.absensicafe.data.model.Punch
import id.omi.absensicafe.data.model.RosterDay
import id.omi.absensicafe.data.model.Settings
import id.omi.absensicafe.data.model.Shift
import java.time.Instant

/*
 * Penerjemah antara dokumen Firestore bergaya lama dan model aplikasi.
 *
 * Bentuk dokumennya warisan aplikasi satu berkas: semua di bawah `cafe/main`,
 * kedua sisi absen ditulis mendatar dalam satu dokumen (`inAt`, `inLat`,
 * `outAt`, …), waktu sebagai milidetik epoch, dan foto tertanam sebagai data
 * URL. Bentuk itu dipertahankan supaya data yang sudah ada langsung terbaca.
 *
 * Pemetaan ditulis tangan, bukan lewat refleksi: satu field bertipe lain di
 * dokumen lama akan membuat seluruh dokumen gagal dibaca, dan di aplikasi
 * absensi itu berarti karyawan menghilang dari layar saat mau absen.
 */

private fun Any?.asInt(default: Int = 0): Int = when (val v = this) {
    is Number -> v.toInt()
    is String -> v.toIntOrNull() ?: default
    else -> default
}

private fun Any?.asIntOrNull(): Int? = when (val v = this) {
    is Number -> v.toInt()
    is String -> v.toIntOrNull()
    else -> null
}

private fun Any?.asLong(default: Long = 0): Long = when (val v = this) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull() ?: default
    else -> default
}

private fun Any?.asLongOrNull(): Long? = when (val v = this) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull()
    else -> null
}

private fun Any?.asDoubleOrNull(): Double? = when (val v = this) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

private fun Any?.asString(default: String = ""): String = when (val v = this) {
    is String -> v
    null -> default
    else -> v.toString()
}

private fun Any?.asBool(default: Boolean): Boolean = when (val v = this) {
    is Boolean -> v
    else -> default
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

/** Pengaturan tersimpan sebagai satu peta di field `settings` dokumen `cafe/main`. */
fun DocumentSnapshot.toSettings(): Settings {
    val s = data?.get("settings").asMap()
    val bawaan = Settings()
    val tiers = (s["fineTiers"] as? List<*>)?.mapNotNull { raw ->
        val m = raw.asMap()
        if (m.isEmpty()) null
        else FineTier(upToMinutes = m["upTo"].asIntOrNull(), amount = m["amount"].asLong())
    }
    return Settings(
        cafeName = s["cafeName"].asString(bawaan.cafeName),
        toleranceMinutes = s["tolerance"].asInt(bawaan.toleranceMinutes),
        minOvertimeMinutes = s["minOvertime"].asInt(bawaan.minOvertimeMinutes),
        fineEnabled = s["fineEnabled"].asBool(bawaan.fineEnabled),
        fineTiers = if (tiers.isNullOrEmpty()) bawaan.fineTiers else tiers,
        geoMode = GeoMode.from(s["geoMode"] as? String),
        geoLat = s["geoLat"].asDoubleOrNull(),
        geoLon = s["geoLon"].asDoubleOrNull(),
        geoRadiusMeters = s["geoRadius"].asInt(bawaan.geoRadiusMeters),
        kioskAdminPin = s["pin"].asString(bawaan.kioskAdminPin),
        pinRequired = s["pinMode"].asString("wajib") != "off"
    )
}

fun DocumentSnapshot.toEmployee(): Employee? {
    val d = data ?: return null
    val name = d["name"].asString()
    if (name.isBlank()) return null
    return Employee(
        id = id,
        name = name,
        role = d["role"].asString(),
        plainPin = d["pin"].asString(),
        pinHash = d["pinHash"].asString(),
        pinSalt = d["pinSalt"].asString(),
        pinIterations = d["pinIterations"].asInt(0),
        toleranceMinutes = d["tolerance"].asIntOrNull(),
        active = d["active"].asBool(true)
    )
}

fun DocumentSnapshot.toShift(): Shift? {
    val d = data ?: return null
    val name = d["name"].asString()
    if (name.isBlank()) return null
    return Shift(
        id = id,
        code = d["code"].asString(name.take(1).uppercase()),
        name = name,
        start = d["start"].asString("00:00"),
        end = d["end"].asString("00:00")
    )
}

/** Dokumen roster menyimpan penugasan di field `hari`. */
fun DocumentSnapshot.toRosterDay(): RosterDay {
    val assign = data?.get("hari").asMap()
        .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
        .toMap()
    return RosterDay(date = id, assign = assign)
}

/**
 * Menyusun satu sisi absen dari field berawalan `in`/`out`.
 *
 * Waktu disimpan sebagai milidetik epoch, bukan Timestamp Firestore.
 */
private fun Map<String, Any?>.toPunch(sisi: String): Punch? {
    val at = this["${sisi}At"].asLongOrNull() ?: return null
    return Punch(
        at = Instant.ofEpochMilli(at),
        lat = this["${sisi}Lat"].asDoubleOrNull(),
        lon = this["${sisi}Lon"].asDoubleOrNull(),
        accuracyMeters = this["${sisi}Acc"].asDoubleOrNull(),
        distanceMeters = this["${sisi}Dist"].asDoubleOrNull(),
        outsideGeofence = this["${sisi}GeoFlag"].asBool(false),
        pinBy = PinBy.from(this["${sisi}PinBy"] as? String),
        photo = this[if (sisi == "in") "fotoMasuk" else "fotoPulang"].asString()
    )
}

fun DocumentSnapshot.toRecord(): AttendanceRecord? {
    val d = data ?: return null
    val employeeId = d["empId"].asString()
    val date = d["date"].asString()
    if (employeeId.isBlank() || date.isBlank()) return null
    return AttendanceRecord(
        id = id,
        employeeId = employeeId,
        date = date,
        shiftId = d["shiftId"].asString(),
        offSchedule = d["offSchedule"].asBool(false),
        checkIn = d.toPunch("in"),
        checkOut = d.toPunch("out"),
        lateMinutes = d["lateMin"].asInt(),
        earlyLeaveMinutes = d["earlyMin"].asInt(),
        workMinutes = d["workMin"].asInt(),
        overtimeMinutes = d["otMin"].asInt(),
        note = d["note"].asString(),
        edited = d["edited"].asBool(false)
    )
}

/** Field satu sisi absen, siap digabung ke dokumen. */
private fun Punch.toFields(sisi: String): Map<String, Any?> = mapOf(
    "${sisi}At" to at.toEpochMilli(),
    "${sisi}Lat" to lat,
    "${sisi}Lon" to lon,
    "${sisi}Acc" to accuracyMeters,
    "${sisi}Dist" to distanceMeters,
    "${sisi}GeoFlag" to outsideGeofence,
    "${sisi}PinBy" to pinBy.wire,
    "${sisi}PinFlag" to (pinBy == PinBy.KOSONG || pinBy == PinBy.ADMIN),
    (if (sisi == "in") "fotoMasuk" else "fotoPulang") to photo.ifBlank { null }
)

/**
 * Dokumen absen lengkap.
 *
 * `hasIn` dan `hasOut` ikut ditulis karena aplikasi lama memakainya untuk tahu
 * sisi mana yang punya foto, dan alat lama seperti pemulihan cadangan masih
 * membacanya.
 */
fun AttendanceRecord.toMap(): Map<String, Any?> = buildMap {
    put("id", id)
    put("empId", employeeId)
    put("date", date)
    put("shiftId", shiftId.ifBlank { null })
    put("offSchedule", offSchedule)
    put("lateMin", lateMinutes)
    put("earlyMin", earlyLeaveMinutes)
    put("workMin", workMinutes)
    put("otMin", overtimeMinutes)
    put("note", note)
    put("edited", edited)
    put("hasIn", checkIn != null)
    put("hasOut", checkOut != null)

    if (checkIn != null) putAll(checkIn.toFields("in")) else put("inAt", null)
    if (checkOut != null) putAll(checkOut.toFields("out")) else put("outAt", null)
}
