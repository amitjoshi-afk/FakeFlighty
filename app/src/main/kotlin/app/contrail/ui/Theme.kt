package app.contrail.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Contrail palette: warm paper, deep ink, and an ember accent for anything that needs attention.
val Paper = Color(0xFFF6F1E7)
val Ink = Color(0xFF0E1A24)
val InkSoft = Color(0xFF1C2B38)
val Ember = Color(0xFFE2603B)
val Sage = Color(0xFF3E8E6E)
val Mist = Color(0xFF8A97A3)
val Amber = Color(0xFFD49A2A)

private val Light = lightColorScheme(
    primary = Ink, onPrimary = Paper,
    secondary = Ember, onSecondary = Color.White,
    background = Paper, onBackground = Ink,
    surface = Color(0xFFFFFBF4), onSurface = Ink,
    surfaceVariant = Color(0xFFEDE5D6), onSurfaceVariant = Color(0xFF4B5863),
    outline = Color(0xFFCFC5B3),
)

private val Dark = darkColorScheme(
    primary = Paper, onPrimary = Ink,
    secondary = Ember, onSecondary = Color.White,
    background = Ink, onBackground = Paper,
    surface = InkSoft, onSurface = Paper,
    surfaceVariant = Color(0xFF263644), onSurfaceVariant = Color(0xFFB4BFC8),
    outline = Color(0xFF3A4B59),
)

private val Mono = FontFamily.Monospace

val ContrailType = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp),
)

@Composable
fun ContrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = ContrailType, content = content)
}
