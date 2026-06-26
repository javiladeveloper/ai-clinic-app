package pe.saniape.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens de color SEMÁNTICOS que las pantallas consumen (no colores crudos).
 * Cambiar la paleta o agregar tema oscuro = cambiar SOLO el mapeo de aquí.
 */
@Immutable
data class SaniaColors(
    val navy: Color,
    val navyDark: Color,
    val navyLight: Color,
    val lav: Color,
    val fondo: Color,        // fondo de pantalla (sand)
    val superficie: Color,   // tarjetas (blanco)
    val borde: Color,
    val texto: Color,        // texto principal
    val textoSuave: Color,   // muted
    val sobreNavy: Color,    // texto sobre navy (blanco)
    // Estado
    val ok: Color, val okBg: Color,
    val pend: Color, val pendBg: Color,
    val error: Color, val errorBg: Color,
    val info: Color, val infoBg: Color,
    val chipBg: Color,        // fondo de chip/selección suave (navy50)
)

private val ColoresClaro = SaniaColors(
    navy = Paleta.Navy600,
    navyDark = Paleta.Navy700,
    navyLight = Paleta.Navy500,
    lav = Paleta.Lav300,
    fondo = Paleta.Sand,
    superficie = Paleta.Blanco,
    borde = Paleta.Borde,
    texto = Paleta.Texto,
    textoSuave = Paleta.Muted,
    sobreNavy = Paleta.Blanco,
    ok = Paleta.Green, okBg = Paleta.GreenBg,
    pend = Paleta.Amber, pendBg = Paleta.AmberBg,
    error = Paleta.Red, errorBg = Paleta.RedBg,
    info = Paleta.Blue, infoBg = Paleta.BlueBg,
    chipBg = Paleta.Navy50,
)

/** Acceso a los colores de marca desde cualquier Composable: `Sania.colors.navy`. */
val LocalSaniaColors: ProvidableCompositionLocal<SaniaColors> =
    staticCompositionLocalOf { ColoresClaro }

/** Punto de acceso corto al sistema de diseño. */
object Sania {
    val colors: SaniaColors
        @Composable get() = LocalSaniaColors.current
    val dim: Dimens get() = Dimens
    val shape: Formas get() = Formas
}

private val EsquemaM3 = lightColorScheme(
    primary = Paleta.Navy600,
    onPrimary = Paleta.Blanco,
    secondary = Paleta.Lav300,
    background = Paleta.Sand,
    onBackground = Paleta.Texto,
    surface = Paleta.Blanco,
    onSurface = Paleta.Texto,
    outline = Paleta.Borde,
    error = Paleta.Red,
)

private val FormasM3 = Shapes(
    small = RoundedCornerShape(Formas.sm.dp),
    medium = RoundedCornerShape(Formas.md.dp),
    large = RoundedCornerShape(Formas.lg.dp),
)

@Composable
fun TemaSania(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSaniaColors provides ColoresClaro) {
        MaterialTheme(
            colorScheme = EsquemaM3,
            shapes = FormasM3,
            typography = TipografiaSania(),
            content = content,
        )
    }
}