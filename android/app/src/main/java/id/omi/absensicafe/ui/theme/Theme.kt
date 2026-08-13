package id.omi.absensicafe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* Kios dipakai di bawah lampu cafe yang temaram dan sering dari jarak satu
   meter, jadi warnanya gelap dengan aksen terang berkontras tinggi. */

private val Hijau = Color(0xFF1D8A5E)
private val HijauTerang = Color(0xFF4CD293)
private val Gelap = Color(0xFF0E1A24)
private val GelapMuda = Color(0xFF16252F)
private val Merah = Color(0xFFE05252)

private val skemaGelap = darkColorScheme(
    primary = HijauTerang,
    onPrimary = Color(0xFF06231A),
    primaryContainer = Hijau,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF7FB4D6),
    background = Gelap,
    onBackground = Color(0xFFE7EEF3),
    surface = GelapMuda,
    onSurface = Color(0xFFE7EEF3),
    surfaceVariant = Color(0xFF1F313D),
    onSurfaceVariant = Color(0xFFB4C4CE),
    error = Merah,
    onError = Color.White,
    outline = Color(0xFF3A4E5C)
)

private val skemaTerang = lightColorScheme(
    primary = Hijau,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EEDC),
    onPrimaryContainer = Color(0xFF06231A),
    secondary = Color(0xFF2C6B92),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF14212B),
    surface = Color.White,
    onSurface = Color(0xFF14212B),
    surfaceVariant = Color(0xFFE3EAF0),
    onSurfaceVariant = Color(0xFF4A5C68),
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Color(0xFFB6C4CE)
)

@Composable
fun AbsensiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) skemaGelap else skemaTerang,
        content = content
    )
}
