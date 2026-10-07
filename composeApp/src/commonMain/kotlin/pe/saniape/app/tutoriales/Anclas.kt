package pe.saniape.app.tutoriales

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/** Pantalla de la app en la que se compone este contenido (vocabulario §1.3). */
val LocalPantallaTutorial = staticCompositionLocalOf<String?> { null }

/** Capa (ventana) en la que se dibuja: 0 = la principal; un diálogo con host, la suya. */
val LocalCapaTutorial = staticCompositionLocalOf { 0 }

/**
 * Marca una pantalla de la app para el motor de tutoriales: mientras esté
 * compuesta, es "la pantalla actual" (la última que entró). Las anclas de
 * adentro quedan atadas a ella: si otra pantalla la tapa, dejan de contar como
 * visibles aunque sigan compuestas debajo.
 */
@Composable
fun PantallaTutorial(id: String, contenido: @Composable () -> Unit) {
    val token = remember { Any() }
    DisposableEffect(id) {
        MotorTutoriales.entrarPantalla(token, id)
        onDispose { MotorTutoriales.salirPantalla(token) }
    }
    CompositionLocalProvider(LocalPantallaTutorial provides id, content = contenido)
}

/**
 * Ancla lógica de un tutorial (`agenda.nueva_cita`…, contrato §1.5): el motor
 * la señala, sabe si está en pantalla ("aparece") y si se tocó ("clic").
 *
 * [valor]: el texto/selección actual de un campo dentro del ancla (esperas
 * `valor`, p. ej. el DNI del paciente).
 *
 * No consume ningún toque ni cambia el layout. Sin tutorial en curso solo se
 * anota en un mapa.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourAncla(id: String, valor: String? = null): Modifier = composed {
    val pantalla = LocalPantallaTutorial.current
    val capa = LocalCapaTutorial.current
    val reg = remember(id, pantalla, capa) { RegistroAncla(id, pantalla, capa) }
    DisposableEffect(reg) {
        MotorTutoriales.registrar(reg)
        onDispose { MotorTutoriales.quitar(reg) }
    }
    if (valor != null) {
        LaunchedEffect(reg, valor) { MotorTutoriales.campo(id, valor) }
    }
    val traer = remember { BringIntoViewRequester() }
    // Cuando pasa a ser el elemento señalado, se trae a la vista (una vez).
    LaunchedEffect(reg) {
        snapshotFlow { MotorTutoriales.objetivo === reg }
            .distinctUntilChanged()
            .collect { es ->
                if (es) { delay(120); runCatching { traer.bringIntoView() } }
            }
    }
    this
        .bringIntoViewRequester(traer)
        .onGloballyPositioned { MotorTutoriales.posicion(reg, it.boundsInWindow()) }
        .pointerInput(reg) {
            // Observa (pase Initial, sin consumir): el toque sigue a su destino normal.
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    if (ev.type != PointerEventType.Press) continue
                    val ch = ev.changes.firstOrNull() ?: continue
                    val inicio = ch.position
                    var fin: androidx.compose.ui.geometry.Offset? = null
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val c = e.changes.firstOrNull { it.id == ch.id } ?: break
                        if (!c.pressed) { fin = c.position; break }
                    }
                    val f = fin ?: continue
                    if ((f - inicio).getDistance() < viewConfiguration.touchSlop * 2) MotorTutoriales.toque(id)
                }
            }
        }
}
