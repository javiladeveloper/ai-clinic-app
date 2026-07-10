package pe.saniape.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Espaciados y tamaños — escala consistente. Evita "números mágicos" en las
 * pantallas: usar `Sania.dim.md` en vez de `16.dp` sueltos.
 */
object Dimens {
    // Espaciado (escala 4)
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 28.dp

    // Padding de pantalla y de tarjeta
    val pantalla: Dp = 20.dp
    val tarjeta: Dp = 16.dp

    // Alturas de control
    val boton: Dp = 52.dp
    val barraProgreso: Dp = 6.dp
    // Grosor de la barra de acento lateral de las tarjetas (tipo/estado).
    val acento: Dp = 5.dp
}

/** Radios de esquina. */
object Formas {
    const val sm = 10
    const val md = 16
    const val lg = 20
    const val pill = 50
}

/** Tamaños de texto nombrados (en sp), para no repetir literales. */
object Texto {
    val titulo: TextUnit = 22.sp
    val subtitulo: TextUnit = 20.sp
    val seccion: TextUnit = 16.sp
    val cuerpo: TextUnit = 14.sp
    val pequeno: TextUnit = 13.sp
    val mini: TextUnit = 11.sp
    val marca: TextUnit = 36.sp
}