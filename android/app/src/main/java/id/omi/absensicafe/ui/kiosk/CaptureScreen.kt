package id.omi.absensicafe.ui.kiosk

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.omi.absensicafe.data.PunchSide
import id.omi.absensicafe.data.model.GeoMode
import id.omi.absensicafe.data.model.Settings
import id.omi.absensicafe.domain.AttendanceRules
import id.omi.absensicafe.location.LocationFix
import id.omi.absensicafe.location.LocationProvider
import id.omi.absensicafe.location.LocationState
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Layar foto selfie.
 *
 * Pencarian lokasi dimulai bersamaan dengan kamera, bukan saat tombol jepret
 * ditekan, supaya GPS sudah dapat sinyal ketika fotonya diambil. Statusnya
 * ditampilkan sebagai bilah di bawah pratinjau agar karyawan tahu kapan boleh
 * menekan tombol.
 */
@Composable
fun CaptureScreen(
    employeeName: String,
    side: PunchSide,
    settings: Settings,
    onCaptured: (File?, LocationFix?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationProvider = remember { LocationProvider(context) }
    val locationState by produceState<LocationState>(LocationState.Idle, locationProvider) {
        locationProvider.updates().collect { value = it }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val cameraProvider by produceState<ProcessCameraProvider?>(null, context) {
        value = runCatching { context.cameraProvider() }.getOrNull()
    }
    var memproses by remember { mutableStateOf(false) }

    DisposableEffect(cameraProvider, lifecycleOwner) {
        val provider = cameraProvider
        if (provider != null) {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }
        }
        onDispose { cameraProvider?.unbindAll() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                employeeName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Absen ${side.label.lowercase()} — hadapkan wajah ke kamera",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
        }

        LocationBar(locationState, settings)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel, enabled = !memproses) { Text("Batal") }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(88.dp)
            ) {
                Button(
                    onClick = {
                        if (memproses) return@Button
                        memproses = true
                        val fix = (locationState as? LocationState.Fixed)?.fix
                        takePhoto(context, imageCapture) { file -> onCaptured(file, fix) }
                    },
                    enabled = !memproses,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (memproses) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Ambil foto",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.size(72.dp))
        }
    }
}

@Composable
private fun LocationBar(state: LocationState, settings: Settings) {
    if (settings.geoMode == GeoMode.OFF && settings.geoLat == null) return

    val (teks, warna) = when (state) {
        LocationState.Idle, LocationState.Searching ->
            "Mencari lokasi…" to MaterialTheme.colorScheme.surfaceVariant

        LocationState.PermissionDenied ->
            "Izin lokasi belum diberikan" to MaterialTheme.colorScheme.error

        LocationState.ServiceOff ->
            "GPS mati" to MaterialTheme.colorScheme.error

        is LocationState.Fixed -> {
            val fix = state.fix
            val jarak = if (settings.geoLat != null && settings.geoLon != null) {
                AttendanceRules.distanceMeters(fix.lat, fix.lon, settings.geoLat, settings.geoLon)
            } else null
            when {
                fix.mocked ->
                    "Lokasi palsu terdeteksi" to MaterialTheme.colorScheme.error

                jarak == null ->
                    "Lokasi didapat (±${fix.accuracyMeters.toInt()} m)" to
                        MaterialTheme.colorScheme.surfaceVariant

                jarak <= settings.geoRadiusMeters ->
                    "Di area cafe — ${AttendanceRules.formatMeters(jarak)} " +
                        "(±${fix.accuracyMeters.toInt()} m)" to
                        MaterialTheme.colorScheme.primaryContainer

                else ->
                    "Di luar area — ${AttendanceRules.formatMeters(jarak)} dari cafe" to
                        MaterialTheme.colorScheme.error
            }
        }
    }

    Surface(color = warna, modifier = Modifier.fillMaxWidth()) {
        Text(
            teks,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Menunggu CameraX siap tanpa memblokir utas utama. */
private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onDone: (File?) -> Unit
) {
    val dir = File(context.cacheDir, "foto").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
    val file = File(dir, "absen-$stamp.jpg")

    val options = ImageCapture.OutputFileOptions.Builder(file)
        .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = true })
        .build()

    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onDone(file)
            }

            override fun onError(exception: ImageCaptureException) {
                // Kamera gagal bukan alasan menahan absen: catatannya tetap
                // tersimpan, hanya tanpa foto bukti.
                file.delete()
                onDone(null)
            }
        }
    )
}
