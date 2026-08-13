package id.omi.absensicafe.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.omi.absensicafe.domain.Pin

/**
 * Papan angka besar untuk PIN empat digit.
 *
 * Sengaja tidak memakai papan ketik sistem: tablet kios sering dipasang dalam
 * dudukan, dan papan ketik yang muncul-hilang membuat tata letaknya melompat
 * saat pergantian shift sedang antre.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: String?,
    confirmLabel: String = "Lanjut",
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(Pin.LENGTH) { i ->
                val terisi = i < pin.length
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (terisi) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            error ?: " ",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))

        val tekan: (String) -> Unit = { angka ->
            if (pin.length < Pin.LENGTH) pin += angka
        }

        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { baris ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                baris.forEach { angka -> TombolAngka(angka) { tekan(angka) } }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(76.dp))
            TombolAngka("0") { tekan("0") }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(76.dp)
            ) {
                TextButton(
                    onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Hapus")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onSubmit(pin); pin = "" },
            enabled = pin.length == Pin.LENGTH,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(confirmLabel, fontSize = 18.sp)
        }

        Spacer(Modifier.height(8.dp))
        extraAction?.invoke()

        TextButton(onClick = onCancel) { Text("Batal") }
    }
}

@Composable
private fun TombolAngka(angka: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(76.dp)
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(
                angka,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
