package pe.saniape.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.saniape.app.ui.theme.Sania

/**
 * Lo que se ve mientras carga una lista.
 *
 * Antes era un aro girando en medio de una pantalla vacía: no decía qué venía —
 * ¿pacientes? ¿citas? ¿se rompió algo? — y al llegar los datos todo aparecía de
 * golpe, con el salto que eso da.
 *
 * Un andamio con la forma del contenido se lee como "ya está llegando", y cuando
 * llegan los datos no hay salto porque el espacio ya estaba ocupado.
 */
@Composable
private fun Bloque(ancho: Dp? = null, alto: Dp = 14.dp, redondeo: Dp = 6.dp, modifier: Modifier = Modifier) {
    val c = Sania.colors
    // Un latido lento y de poco recorrido: marca que la pantalla está viva sin
    // llamar la atención. Parpadear fuerte cansa cuando la espera se alarga.
    val transicion = rememberInfiniteTransition(label = "latido")
    val opacidad by transicion.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "opacidad",
    )
    Spacer(
        modifier
            .then(if (ancho != null) Modifier.width(ancho) else Modifier.fillMaxWidth())
            .height(alto)
            .alpha(opacidad)
            .clip(RoundedCornerShape(redondeo))
            .background(c.borde),
    )
}

/**
 * Andamio de una lista de tarjetas (pacientes, citas, sesiones).
 *
 * @param filas cuántas tarjetas dibujar — las que suelen caber en pantalla.
 *   De más solo alarga el andamio sin aportar.
 * @param conAvatar la lista muestra un círculo con las iniciales a la izquierda.
 */
@Composable
fun CargandoLista(
    filas: Int = 6,
    conAvatar: Boolean = true,
    /** false cuando quien llama ya puso el margen lateral (evita doblarlo). */
    conMargen: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = Sania.colors
    Column(
        modifier.fillMaxWidth()
            .padding(horizontal = if (conMargen) Sania.dim.lg else 0.dp, vertical = Sania.dim.sm),
        verticalArrangement = Arrangement.spacedBy(Sania.dim.sm),
    ) {
        repeat(filas) { i ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Sania.dim.tarjeta))
                    .background(c.superficie)
                    .padding(Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (conAvatar) {
                    // redondeo = la mitad del lado → círculo, sin encadenar otro clip.
                    Bloque(ancho = 40.dp, alto = 40.dp, redondeo = 20.dp)
                    Spacer(Modifier.width(Sania.dim.md))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Anchos desiguales: una columna de bloques idénticos se ve
                    // como una tabla rota, no como nombres de distinta longitud.
                    Bloque(ancho = if (i % 2 == 0) 180.dp else 140.dp, alto = 15.dp)
                    Bloque(ancho = if (i % 3 == 0) 110.dp else 90.dp, alto = 11.dp)
                }
                Bloque(ancho = 56.dp, alto = 22.dp, redondeo = 11.dp)
            }
        }
    }
}

/** Andamio de la ficha de un paciente: encabezado, tarjetas de datos y contenido. */
@Composable
fun CargandoFicha(modifier: Modifier = Modifier) {
    val c = Sania.colors
    Column(
        modifier.fillMaxWidth().padding(Sania.dim.lg),
        verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
    ) {
        Bloque(ancho = 220.dp, alto = 24.dp)
        Bloque(ancho = 140.dp, alto = 13.dp)
        Spacer(Modifier.height(Sania.dim.xs))
        // Las cuatro tarjetas de arriba (progreso, saldo, próxima cita, última atención).
        repeat(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
                repeat(2) {
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(Sania.dim.tarjeta))
                            .background(c.superficie)
                            .padding(Sania.dim.md),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Bloque(ancho = 70.dp, alto = 10.dp)
                        Bloque(ancho = 90.dp, alto = 18.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(Sania.dim.xs))
        // Sin margen propio: esta Column ya lo puso. Doblarlo dejaría las
        // tarjetas del listado más angostas que las de arriba.
        CargandoLista(filas = 3, conAvatar = false, conMargen = false)
    }
}
