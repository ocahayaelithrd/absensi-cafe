package id.omi.absensicafe

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import id.omi.absensicafe.data.AttendanceRepository
import id.omi.absensicafe.data.DeviceStore
import java.time.ZoneId

class AbsensiApp : Application() {

    lateinit var repository: AttendanceRepository
        private set
    lateinit var deviceStore: DeviceStore
        private set

    override fun onCreate() {
        super.onCreate()

        /* Simpanan lokal tanpa batas ukuran. Bawaannya 100 MB dan Firestore
           membuang dokumen lama saat penuh — di kios yang berbulan-bulan tidak
           dibersihkan, itu berarti karyawan bisa hilang dari layar absen saat
           internet mati. */
        val db = FirebaseFirestore.getInstance()
        db.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(
                persistentCacheSettings {
                    setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                }
            )
        }

        repository = AttendanceRepository(db, ZoneId.systemDefault())
        deviceStore = DeviceStore(this)
    }
}
