package id.omi.absensicafe.ui.camera

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pratinjau kamera depan dengan satu tombol jepret.
 *
 * Dipakai dua layar: absen sehari-hari dan pendaftaran wajah. Keduanya harus
 * memakai kamera, jarak, dan pencahayaan yang sama — kalau pendaftaran
 * dilakukan lewat perangkat lain, kecocokannya turun tanpa sebab yang kelihatan.
 *
 * [statusBar] diisi pemanggil untuk menampilkan keadaannya sendiri: bilah lokasi
 * pada layar absen, penghitung jepretan pada layar pendaftaran.
 */
@Composable
fun SelfieCamera(
    title: String,
    subtitle: String,
    busy: Boolean,
    onCaptured: (File?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    statusBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
        }

        statusBar()

        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel, enabled = !busy) { Text("Batal") }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(88.dp)
            ) {
                Button(
                    onClick = {
                        if (!busy) takePhoto(context, imageCapture, onCaptured)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (busy) {
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
