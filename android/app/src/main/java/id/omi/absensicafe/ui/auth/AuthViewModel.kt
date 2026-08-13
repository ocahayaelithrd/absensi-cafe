package id.omi.absensicafe.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthState(
    val checked: Boolean = false,
    val signedIn: Boolean = false,
    val email: String = "",
    val busy: Boolean = false,
    val error: String? = null
)

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(
        AuthState(
            checked = true,
            signedIn = auth.currentUser != null,
            email = auth.currentUser?.email.orEmpty()
        )
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        auth.addAuthStateListener { a ->
            _state.value = _state.value.copy(
                checked = true,
                signedIn = a.currentUser != null,
                email = a.currentUser?.email.orEmpty()
            )
        }
    }

    fun login(email: String, password: String) {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _state.value = _state.value.copy(busy = false, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = pesan(e))
            }
        }
    }

    fun logout() {
        auth.signOut()
    }

    private fun pesan(e: Exception): String = when (e) {
        is FirebaseAuthInvalidUserException -> "Akun tidak ditemukan."
        is FirebaseAuthInvalidCredentialsException -> "Email atau kata sandi salah."
        else -> "Gagal masuk: ${e.message ?: "periksa koneksi internet"}"
    }
}
