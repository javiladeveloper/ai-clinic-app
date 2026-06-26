package pe.saniape.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta Sania — igual que la web (globals.css).
val Navy = Color(0xFF2C3E7A)
val NavyDark = Color(0xFF1E2D5E)
val Lav = Color(0xFF8892C8)
val Sand = Color(0xFFF7F8FC)
val BorderColor = Color(0xFFDDE1F0)
val Muted = Color(0xFF6B7280)
val GreenOk = Color(0xFF16A34A)
val Amber = Color(0xFFD97706)
val RedDanger = Color(0xFFDC2626)

private val ColoresSania = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Lav,
    background = Sand,
    onBackground = Color(0xFF1F2937),
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    outline = BorderColor,
)

private val FormasSania = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun TemaSania(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColoresSania,
        shapes = FormasSania,
        content = content,
    )
}