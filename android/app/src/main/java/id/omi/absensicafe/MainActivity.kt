package id.omi.absensicafe

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import id.omi.absensicafe.ui.auth.AuthViewModel
import id.omi.absensicafe.ui.auth.LoginScreen
import id.omi.absensicafe.ui.device.DeviceScreen
import id.omi.absensicafe.ui.kiosk.KioskScreen
import id.omi.absensicafe.ui.kiosk.KioskViewModel
import id.omi.absensicafe.ui.kiosk.PinPad
import id.omi.absensicafe.ui.theme.AbsensiTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val auth: AuthViewModel by viewModels()
    private val kiosk: KioskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tablet kios dicolok terus di dudukan kasir; layar yang mati membuat
        // karyawan mengira aplikasinya tidak jalan.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AbsensiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Root(auth, kiosk, versionName())
                }
            }
        }
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }
}

/** Layar mana yang tampil di luar alur absen. */
private enum class Route { KIOSK, DEVICE_PIN, DEVICE }

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun Root(auth: AuthViewModel, kiosk: KioskViewModel, versionName: String) {
    val context = LocalContext.current
    val authState by auth.state.collectAsState()
    val kioskState by kiosk.state.collectAsState()
    val step by kiosk.step.collectAsState()

    var route by remember { mutableStateOf(Route.KIOSK) }
    var pinError by remember { mutableStateOf<String?>(null) }

    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(authState.signedIn) {
        if (authState.signedIn) {
            permissions.launchMultiplePermissionRequest()
            kiosk.touchDevice(versionName)
        }
    }

    if (!authState.checked) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        return
    }

    if (!authState.signedIn) {
        LoginScreen(
            busy = authState.busy,
            error = authState.error,
            onLogin = auth::login
        )
        return
    }

    when (route) {
        Route.KIOSK -> KioskScreen(
            state = kioskState,
            step = step,
            onSelectEmployee = kiosk::selectEmployee,
            onSubmitPin = kiosk::submitPin,
            onUseAdminOverride = kiosk::useAdminOverride,
            onSubmitGeoOverride = kiosk::submitGeoOverride,
            onPhotoTaken = kiosk::onPhotoTaken,
            onCancel = kiosk::cancel,
            onToggleSort = kiosk::toggleSort,
            onOpenDeviceSettings = {
                pinError = null
                route = Route.DEVICE_PIN
            }
        )

        Route.DEVICE_PIN -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            PinPad(
                title = "Setelan perangkat",
                subtitle = "Masukkan PIN penyelia",
                error = pinError,
                confirmLabel = "Buka",
                onSubmit = { pin ->
                    if (pin == kioskState.settings.kioskAdminPin) {
                        pinError = null
                        route = Route.DEVICE
                    } else {
                        pinError = "PIN salah"
                    }
                },
                onCancel = { route = Route.KIOSK }
            )
        }

        Route.DEVICE -> {
            val app = context.applicationContext as AbsensiApp
            var deviceId by remember { mutableStateOf("") }
            var label by remember { mutableStateOf("Kios Kasir") }

            LaunchedEffect(Unit) {
                deviceId = app.deviceStore.deviceId()
                label = app.deviceStore.label.first()
            }

            DeviceScreen(
                deviceId = deviceId,
                label = label,
                email = authState.email,
                appVersion = versionName,
                cameraGranted = permissions.permissions
                    .first { it.permission == Manifest.permission.CAMERA }
                    .status.isGranted,
                locationGranted = permissions.permissions
                    .first { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }
                    .status.isGranted,
                onLabelChange = { baru ->
                    label = baru
                    kiosk.setDeviceLabel(baru, versionName)
                },
                onRequestPermissions = { permissions.launchMultiplePermissionRequest() },
                onLogout = {
                    auth.logout()
                    route = Route.KIOSK
                },
                onClose = { route = Route.KIOSK }
            )
        }
    }
}
