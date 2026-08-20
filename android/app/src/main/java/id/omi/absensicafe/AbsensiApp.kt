package id.omi.absensicafe

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import id.omi.absensicafe.data.AttendanceRepository
import id.omi.absensicafe.data.DeviceStore
import id.omi.absensicafe.face.FaceEmbedder
import id.omi.absensicafe.face.FaceScanner
import java.time.ZoneId

class AbsensiApp : Application() {

    lateinit var repository: AttendanceRepository
        private set
    lateinit var deviceStore: DeviceStore
        private set

    /**
     * Dibuat sekali untuk seluruh aplikasi: modelnya dipetakan ke memori saat
     * pertama dipakai, dan memuatnya berulang kali di tiap layar memboroskan
     * memori tablet tanpa alasan.
     *
     * Malas dimuat, bukan saat aplikasi mulai, supaya layar absen tetap tampil
     * seketika di tablet lambat meski modelnya besar.
     */
    val faceScanner: FaceScanner by lazy { FaceScanner(FaceEmbedder(this)) }

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
