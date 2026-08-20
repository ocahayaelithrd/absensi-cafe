package id.omi.absensicafe.ui.face

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.omi.absensicafe.AbsensiApp
import id.omi.absensicafe.data.model.Employee
import id.omi.absensicafe.domain.FaceMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface EnrollStep {
    /** Memilih karyawan yang akan didaftarkan. */
    data object Pick : EnrollStep

    data class Shoot(
        val employee: Employee,
        val taken: Int,
        val busy: Boolean = false,
        val error: String? = null
    ) : EnrollStep

    data class Saved(val employee: Employee, val count: Int) : EnrollStep
}

/**
 * Pendaftaran wajah karyawan, dijalankan di tablet.
 *
 * Sengaja bukan di web admin: kamera, jarak berdiri, dan pencahayaan saat
 * mendaftar harus sama dengan saat absen sehari-hari. Mendaftarkan wajah lewat
 * webcam PC di ruang belakang menghasilkan template yang tidak pernah cocok
 * dengan foto di kasir, dan penyebabnya tidak akan kelihatan dari skornya.
 */
class FaceEnrollViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as AbsensiApp).repository
    private val scanner = (app as AbsensiApp).faceScanner

    private val _step = MutableStateFlow<EnrollStep>(EnrollStep.Pick)
    val step: StateFlow<EnrollStep> = _step.asStateFlow()

    /** Vektor yang sudah terkumpul untuk karyawan yang sedang didaftarkan. */
    private val terkumpul = mutableListOf<List<Float>>()

    val employees: StateFlow<List<Employee>> = repo.employeesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val modelAvailable: Boolean get() = scanner.available

    val shots: Int get() = FaceMatch.SHOTS

    fun select(employee: Employee) {
        terkumpul.clear()
        _step.value = EnrollStep.Shoot(employee, taken = 0)
    }

    fun back() {
        terkumpul.clear()
        _step.value = EnrollStep.Pick
    }

    /**
     * Memproses satu jepretan.
     *
     * Jepretan yang wajahnya tidak terdeteksi **tidak dihitung** — lebih baik
     * meminta ulang di tempat daripada menyimpan template kosong yang nanti
     * menolak orangnya sendiri setiap hari.
     */
    fun onShot(file: File?) {
        val s = _step.value as? EnrollStep.Shoot ?: return
        if (s.busy) return
        _step.value = s.copy(busy = true, error = null)

        viewModelScope.launch {
            if (file == null) {
                _step.value = s.copy(busy = false, error = "Kamera gagal mengambil foto.")
                return@launch
            }

            val vektor = withContext(Dispatchers.Default) {
                scanner.embedFrom(file).also { file.delete() }
            }

            if (vektor == null) {
                _step.value = s.copy(
                    busy = false,
                    error = "Wajah tidak terdeteksi. Hadapkan wajah ke kamera, " +
                        "pastikan ruangan cukup terang."
                )
                return@launch
            }

            terkumpul += vektor
            if (terkumpul.size < FaceMatch.SHOTS) {
                _step.value = s.copy(taken = terkumpul.size, busy = false, error = null)
                return@launch
            }

            repo.saveFaceTemplates(s.employee.id, terkumpul.toList(), scanner.modelId)
            _step.value = EnrollStep.Saved(s.employee, terkumpul.size)
            terkumpul.clear()
        }
    }

    fun clearFace(employee: Employee) {
        repo.clearFaceTemplates(employee.id)
    }
}
