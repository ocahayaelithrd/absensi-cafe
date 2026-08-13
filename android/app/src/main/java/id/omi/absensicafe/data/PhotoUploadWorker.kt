package id.omi.absensicafe.data

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Mengunggah foto bukti absen ke Cloud Storage, di luar jalur absen.
 *
 * Absen tidak boleh menunggu jaringan: karyawan mengetuk, foto tersimpan ke
 * berkas lokal, catatan langsung ditulis, dan pekerjaan ini yang mengantar
 * fotonya kapan pun internet cafe hidup. Kalau gagal, WorkManager mengulang
 * sendiri dengan jeda yang makin panjang, termasuk setelah tablet dinyalakan
 * ulang.
 */
class PhotoUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recordId = inputData.getString(KEY_RECORD_ID) ?: return Result.failure()
        val localPath = inputData.getString(KEY_LOCAL_PATH) ?: return Result.failure()
        val side = PunchSide.from(inputData.getString(KEY_SIDE))

        val file = File(localPath)
        if (!file.exists()) {
            // Sudah pernah terunggah dan berkasnya dibersihkan; tidak ada sisa kerja.
            return Result.success()
        }
        if (FirebaseAuth.getInstance().currentUser == null) {
            return Result.retry()
        }

        return try {
            val path = "records/$recordId/${side.fileName}"
            FirebaseStorage.getInstance().reference.child(path)
                .putFile(Uri.fromFile(file))
                .await()

            FirebaseFirestore.getInstance()
                .collection("records").document(recordId)
                .update("${side.field}.photoPath", path)

            file.delete()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val KEY_RECORD_ID = "recordId"
        const val KEY_SIDE = "side"
        const val KEY_LOCAL_PATH = "localPath"
        private const val MAX_ATTEMPTS = 20

        fun enqueue(context: Context, recordId: String, side: PunchSide, file: File) {
            val request = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_RECORD_ID, recordId)
                        .putString(KEY_SIDE, side.name)
                        .putString(KEY_LOCAL_PATH, file.absolutePath)
                        .build()
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "foto-$recordId-${side.name}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        const val TAG = "unggah-foto"
    }
}
