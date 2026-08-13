package id.omi.absensicafe.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import id.omi.absensicafe.data.model.AttendanceRecord
import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.data.model.FineTier
import id.omi.absensicafe.data.model.GeoMode
import id.omi.absensicafe.data.model.Punch
import id.omi.absensicafe.data.model.RosterDay
import id.omi.absensicafe.data.model.Settings
import id.omi.absensicafe.data.model.Shift
import java.time.Instant

/*
 * Pemetaan dokumen Firestore ke model, ditulis tangan.
 *
 * Pemetaan otomatis lewat refleksi gampang pecah diam-diam: satu field bertipe
 * lain di dokumen lama membuat seluruh dokumen gagal dibaca, dan di aplikasi
 * absensi itu berarti karyawan tidak muncul di layar saat mau absen. Di sini
 * setiap field dibaca sendiri dengan nilai bawaan yang aman.
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

private fun Any?.asInstant(): Instant? = when (val v = this) {
    is Timestamp -> Instant.ofEpochSecond(v.seconds, v.nanoseconds.toLong())
    is Number -> Instant.ofEpochMilli(v.toLong())
    else -> null
}

fun Instant.toTimestamp(): Timestamp = Timestamp(epochSecond, nano)

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

fun DocumentSnapshot.toSettings(): Settings {
    val d = data ?: return Settings()
    val fallback = Settings()
    val tiers = (d["fineTiers"] as? List<*>)?.mapNotNull { raw ->
        val m = raw.asMap()
        if (m.isEmpty()) null
        else FineTier(
            upToMinutes = m["upToMinutes"].asIntOrNull(),
            amount = m["amount"].asLong()
        )
    }
    return Settings(
        cafeName = d["cafeName"].asString(fallback.cafeName),
        toleranceMinutes = d["toleranceMinutes"].asInt(fallback.toleranceMinutes),
        minOvertimeMinutes = d["minOvertimeMinutes"].asInt(fallback.minOvertimeMinutes),
        fineEnabled = d["fineEnabled"].asBool(fallback.fineEnabled),
        fineTiers = if (tiers.isNullOrEmpty()) fallback.fineTiers else tiers,
        geoMode = GeoMode.from(d["geoMode"] as? String),
        geoLat = d["geoLat"].asDoubleOrNull(),
        geoLon = d["geoLon"].asDoubleOrNull(),
        geoRadiusMeters = d["geoRadiusMeters"].asInt(fallback.geoRadiusMeters),
        kioskAdminPin = d["kioskAdminPin"].asString(fallback.kioskAdminPin),
        photoRequired = d["photoRequired"].asBool(fallback.photoRequired)
    )
}

fun DocumentSnapshot.toEmployee(): Employee? {
    val d = data ?: return null
    val name = d["name"].asString()
    if (name.isBlank()) return null
    return Employee(
        id = id,
        name = name,
        pinHash = d["pinHash"].asString(),
        pinSalt = d["pinSalt"].asString(),
        pinIterations = d["pinIterations"].asInt(0),
        toleranceMinutes = d["toleranceMinutes"].asIntOrNull(),
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
        end = d["end"].asString("00:00"),
        order = d["order"].asInt(0)
    )
}

fun DocumentSnapshot.toRosterDay(): RosterDay {
    val assign = data?.get("assign").asMap()
        .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
        .toMap()
    return RosterDay(date = id, assign = assign)
}

private fun Map<String, Any?>.toPunch(): Punch? {
    val at = this["at"].asInstant() ?: return null
    return Punch(
        at = at,
        lat = this["lat"].asDoubleOrNull(),
        lon = this["lon"].asDoubleOrNull(),
        accuracyMeters = this["accuracyMeters"].asDoubleOrNull(),
        distanceMeters = this["distanceMeters"].asDoubleOrNull(),
        outsideGeofence = this["outsideGeofence"].asBool(false),
        photoPath = this["photoPath"].asString(),
        pinOk = this["pinOk"].asBool(true),
        adminOverride = this["adminOverride"].asBool(false),
        noPin = this["noPin"].asBool(false)
    )
}

fun Punch.toMap(): Map<String, Any?> = mapOf(
    "at" to at.toTimestamp(),
    "lat" to lat,
    "lon" to lon,
    "accuracyMeters" to accuracyMeters,
    "distanceMeters" to distanceMeters,
    "outsideGeofence" to outsideGeofence,
    "photoPath" to photoPath,
    "pinOk" to pinOk,
    "adminOverride" to adminOverride,
    "noPin" to noPin
)

fun DocumentSnapshot.toRecord(): AttendanceRecord? {
    val d = data ?: return null
    val employeeId = d["employeeId"].asString()
    val date = d["date"].asString()
    if (employeeId.isBlank() || date.isBlank()) return null
    return AttendanceRecord(
        id = id,
        employeeId = employeeId,
        employeeName = d["employeeName"].asString(),
        date = date,
        shiftId = d["shiftId"].asString(),
        shiftName = d["shiftName"].asString(),
        shiftStart = d["shiftStart"].asString(),
        shiftEnd = d["shiftEnd"].asString(),
        offSchedule = d["offSchedule"].asBool(false),
        checkIn = d["checkIn"].asMap().takeIf { it.isNotEmpty() }?.toPunch(),
        checkOut = d["checkOut"].asMap().takeIf { it.isNotEmpty() }?.toPunch(),
        lateMinutes = d["lateMinutes"].asInt(),
        earlyLeaveMinutes = d["earlyLeaveMinutes"].asInt(),
        workMinutes = d["workMinutes"].asInt(),
        overtimeMinutes = d["overtimeMinutes"].asInt(),
        note = d["note"].asString(),
        deviceId = d["deviceId"].asString(),
        correctedBy = d["correctedBy"].asString()
    )
}

fun AttendanceRecord.toMap(): Map<String, Any?> = mapOf(
    "employeeId" to employeeId,
    "employeeName" to employeeName,
    "date" to date,
    "shiftId" to shiftId,
    "shiftName" to shiftName,
    "shiftStart" to shiftStart,
    "shiftEnd" to shiftEnd,
    "offSchedule" to offSchedule,
    "checkIn" to checkIn?.toMap(),
    "checkOut" to checkOut?.toMap(),
    "lateMinutes" to lateMinutes,
    "earlyLeaveMinutes" to earlyLeaveMinutes,
    "workMinutes" to workMinutes,
    "overtimeMinutes" to overtimeMinutes,
    "note" to note,
    "deviceId" to deviceId,
    "correctedBy" to correctedBy
)
