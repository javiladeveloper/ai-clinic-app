package pe.saniape.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
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
    val teal: Color, val tealBg: Color,
    val purple: Color, val purpleBg: Color,
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
    teal = Paleta.Teal, tealBg = Paleta.TealBg,
    purple = Paleta.Purple, purpleBg = Paleta.PurpleBg,
    chipBg = Paleta.Navy50,
)

/** Tema OSCURO: mismos tokens semánticos con la paleta oscura (espeja el dark de la web).
 *  Las pantallas no cambian: consumen Sania.colors y el mapeo decide. */
private val ColoresOscuro = SaniaColors(
    navy = Paleta.Navy500,            // más luz para que el primario contraste sobre oscuro
    navyDark = Paleta.NavyDarkOscuro,
    navyLight = Paleta.Lav300,
    lav = Paleta.Lav300,
    fondo = Paleta.FondoOscuro,
    superficie = Paleta.SuperficieOscura,
    borde = Paleta.BordeOscuro,
    texto = Paleta.TextoClaro,
    textoSuave = Paleta.MutedOscuro,
    sobreNavy = Paleta.Blanco,
    ok = Paleta.GreenOsc, okBg = Paleta.GreenBgOsc,
    pend = Paleta.AmberOsc, pendBg = Paleta.AmberBgOsc,
    error = Paleta.RedOsc, errorBg = Paleta.RedBgOsc,
    info = Paleta.BlueOsc, infoBg = Paleta.BlueBgOsc,
    teal = Paleta.TealOsc, tealBg = Paleta.TealBgOsc,
    purple = Paleta.PurpleOsc, purpleBg = Paleta.PurpleBgOsc,
    chipBg = Paleta.ChipOscuro,
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
    val txt: Texto get() = Texto
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

private val EsquemaM3Oscuro = darkColorScheme(
    primary = Paleta.Navy500,
    onPrimary = Paleta.Blanco,
    secondary = Paleta.Lav300,
    background = Paleta.FondoOscuro,
    onBackground = Paleta.TextoClaro,
    surface = Paleta.SuperficieOscura,
    onSurface = Paleta.TextoClaro,
    outline = Paleta.BordeOscuro,
    error = Paleta.RedOsc,
)

private val FormasM3 = Shapes(
    small = RoundedCornerShape(Formas.sm.dp),
    medium = RoundedCornerShape(Formas.md.dp),
    large = RoundedCornerShape(Formas.lg.dp),
)

@Composable
fun TemaSania(content: @Composable () -> Unit) {
    // Sigue el tema del SISTEMA (como la web, que respeta prefers-color-scheme).
    val oscuro = isSystemInDarkTheme()
    CompositionLocalProvider(LocalSaniaColors provides if (oscuro) ColoresOscuro else ColoresClaro) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaM3Oscuro else EsquemaM3,
            shapes = FormasM3,
            typography = TipografiaSania(),
            content = content,
        )
    }
}