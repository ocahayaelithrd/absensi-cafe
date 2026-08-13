package id.omi.absensicafe.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/** Satu pembacaan GPS. */
data class LocationFix(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Double,
    /** Benar bila lokasi berasal dari aplikasi pemalsu lokasi. */
    val mocked: Boolean = false
)

sealed interface LocationState {
    data object Idle : LocationState
    data object Searching : LocationState
    data object PermissionDenied : LocationState
    data object ServiceOff : LocationState
    data class Fixed(val fix: LocationFix) : LocationState
}

/**
 * Membaca lokasi selama layar kamera terbuka.
 *
 * Pencarian dimulai berbarengan dengan kamera, bukan saat tombol jepret
 * ditekan, supaya GPS sudah dapat sinyal ketika fotonya diambil — di dalam
 * ruangan pembacaan pertama sering butuh belasan detik.
 */
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isServiceEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    fun updates(intervalMillis: Long = 2_000): Flow<LocationState> {
        if (!hasPermission()) return flowOf(LocationState.PermissionDenied)
        if (!isServiceEnabled()) return flowOf(LocationState.ServiceOff)

        return callbackFlow {
            trySend(LocationState.Searching)

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setMinUpdateIntervalMillis(intervalMillis)
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    trySend(
                        LocationState.Fixed(
                            LocationFix(
                                lat = loc.latitude,
                                lon = loc.longitude,
                                accuracyMeters = loc.accuracy.toDouble(),
                                mocked = loc.isMocked()
                            )
                        )
                    )
                }
            }

            client.requestLocationUpdates(request, callback, context.mainLooper)
            awaitClose { client.removeLocationUpdates(callback) }
        }
    }

    /** `isMock` baru ada sejak Android 12; versi lama memakai nama yang lama. */
    private fun Location.isMocked(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock
        else @Suppress("DEPRECATION") isFromMockProvider
}
