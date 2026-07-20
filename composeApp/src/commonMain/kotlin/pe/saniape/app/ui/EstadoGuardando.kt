package pe.saniape.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
object EstadoGuardando {
    private val _enCurso = MutableStateFlow(0)

    /** Cuántas operaciones se están guardando ahora mismo (0 = nada en curso). */
    val enCurso: StateFlow<Int> = _enCurso

    fun inicio() { _enCurso.value = _enCurso.value + 1 }
    fun fin() { _enCurso.value = (_enCurso.value - 1).coerceAtLeast(0) }
}

/**
 * Envuelve una gestión para que muestre el indicador mientras dura.
 * Se apaga siempre, incluso si la operación falla.
 */
suspend fun <T> conIndicador(bloque: suspend () -> T): T {
    EstadoGuardando.inicio()
    try {
        return bloque()
    } finally {
        EstadoGuardando.fin()
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
 * Va ARRIBA porque los toasts salen abajo (ToastHost) y se taparían.
 */
@Composable
fun IndicadorGuardandoHost() {
    val enCurso by EstadoGuardando.enCurso.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = enCurso > 0,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            Row(
                Modifier
                    .padding(top = 48.dp)   // bajo la barra de estado
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E2D5E))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Guardando…", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
