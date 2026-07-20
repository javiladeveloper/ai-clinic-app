package pe.saniape.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Indicador GLOBAL de "guardando…".
 *
 * Las gestiones (completar/crear/editar/borrar sesión, pagos, tratamientos…)
 * encadenan varias operaciones en el servidor y tardan un par de segundos. Antes
 * el modal se cerraba al instante y la pantalla quedaba igual, sin señal de que
 * algo estaba pasando: se sentía colgado.
 *
 * Con esto, cualquier acción envuelta en [conIndicador] enciende una barra en la
 * parte superior mientras dura, y la apaga al terminar. Un solo lugar en la UI
 * cubre TODAS las gestiones, sin tocar cada modal.
 */
/** Qué está haciendo la app. El rótulo debe decir la VERDAD: "Guardando…" al abrir
 *  una ficha hace pensar que se modificó algo que no se tocó. */
enum class Gestion(val rotulo: String) {
    GUARDANDO("Guardando…"),
    ELIMINANDO("Eliminando…"),
    ACTUALIZANDO("Actualizando…"),
    CARGANDO("Cargando…"),
}

object EstadoGuardando {
    private val _enCurso = MutableStateFlow(0)

    /** Cuántas operaciones hay en curso (0 = nada). */
    val enCurso: StateFlow<Int> = _enCurso

    // Pila de gestiones activas: el rótulo visible es el de la MÁS específica en curso.
    // Al borrar, por ejemplo, se solapan el borrado y la recarga de la lista; mostrar
    // "Eliminando…" durante todo el bloque es más claro que alternar los textos.
    private val activas = mutableListOf<Gestion>()

    private val _rotulo = MutableStateFlow(Gestion.GUARDANDO.rotulo)
    /** Texto a mostrar, acorde a lo que realmente está ocurriendo. */
    val rotulo: StateFlow<String> = _rotulo

    // Orden de prioridad: una escritura manda sobre la recarga que la acompaña.
    private val prioridad = listOf(Gestion.ELIMINANDO, Gestion.GUARDANDO, Gestion.ACTUALIZANDO, Gestion.CARGANDO)

    private fun recalcular() {
        _rotulo.value = (prioridad.firstOrNull { it in activas } ?: Gestion.GUARDANDO).rotulo
    }

    fun inicio(gestion: Gestion = Gestion.GUARDANDO) {
        _enCurso.value = _enCurso.value + 1
        activas += gestion
        recalcular()
    }

    fun fin(gestion: Gestion = Gestion.GUARDANDO) {
        _enCurso.value = (_enCurso.value - 1).coerceAtLeast(0)
        activas.remove(gestion)
        recalcular()
    }
}

/**
 * Envuelve una gestión para que muestre el indicador mientras dura.
 * Se apaga siempre, incluso si la operación falla.
 */
suspend fun <T> conIndicador(gestion: Gestion = Gestion.GUARDANDO, bloque: suspend () -> T): T {
    EstadoGuardando.inicio(gestion)
    try {
        return bloque()
    } finally {
        EstadoGuardando.fin(gestion)
    }
}

/**
 * Dibuja el "Guardando…" sobre CUALQUIER pantalla. Se monta una sola vez en App().
 *
 * Antes el chip vivía dentro de HeaderMarcaClinica, que solo usa la pantalla de
 * Inicio: en la ficha del paciente —donde se borran y crean sesiones, que es
 * justo donde más se espera— el estado se encendía pero no había nada que lo
 * dibujara. Por eso "no aparecía el loader" aunque la lógica fuera correcta.
 *
 * Va al CENTRO, sobre un velo: la gestión bloquea (el usuario está esperando el
 * resultado), y un chip arriba se lee como aviso de fondo, algo que pasa "por
 * detrás". En el centro queda claro que la app está ocupada y que hay que
 * aguardar. El velo además evita toques sobre datos que están por cambiar.
 */
@Composable
fun IndicadorGuardandoHost() {
    val enCurso by EstadoGuardando.enCurso.collectAsState()
    val rotulo by EstadoGuardando.rotulo.collectAsState()
    AnimatedVisibility(visible = enCurso > 0, enter = fadeIn(), exit = fadeOut()) {
        Box(
            // Velo tenue: atenúa el fondo sin ocultarlo. `clickable` sin efecto visual
            // se traga los toques mientras dura la gestión (evita doble envío y que se
            // toque una fila que está a punto de desaparecer).
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* absorbe el toque */ },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E2D5E))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    color = Color.White,
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(14.dp))
                Text(rotulo, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
