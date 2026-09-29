package pe.saniape.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Diálogo que NO queda tapado por el teclado (29/09/2026).
 *
 * La app apunta a Android 16 (targetSdk 36) y dibuja de borde a borde: ahí
 * Android IGNORA "achicar la ventana con el teclado" (SOFT_INPUT_ADJUST_RESIZE),
 * que fue el intento de la 2.17.2 — y el teclado siguió tapando el botón de
 * guardar del triaje y de "Nuevo tratamiento". Lo que sí funciona: la ventana
 * del diálogo ocupa toda la pantalla sin encajarse en las barras del sistema, y
 * el contenido se aparta del teclado con los insets (`safeDrawingPadding`
 * incluye el del teclado). El cuerpo desplazable del formulario se achica y el
 * pie con los botones queda a la vista.
 */
@Composable
expect fun propiedadesDialogoTeclado(): DialogProperties

/**
 * Márgenes que el contenido del diálogo debe dejar: arriba la barra de estado,
 * abajo el TECLADO (o la barra de navegación si no hay teclado).
 *
 * Se leen de la ventana del diálogo con un escuchador nativo: dentro de un
 * Dialog, Compose no recibe la altura del teclado (`safeDrawingPadding` no
 * hacía nada) y con "ajustar al teclado" Android ni siquiera lo desplaza.
 * Probado en emulador Android 16 (29/09/2026).
 */
data class MargenesDialogo(
    val arriba: androidx.compose.ui.unit.Dp,
    val abajo: androidx.compose.ui.unit.Dp,
    /** El teclado está abierto sobre el diálogo. */
    val teclado: Boolean = false,
)

/**
 * ¿Hay teclado abierto sobre el diálogo? Con él abierto, los formularios
 * muestran su botón de guardar TAMBIÉN en la cabecera, que nunca queda tapada:
 * cuánto tapa el teclado de verdad varía por teléfono y teclado (Gboard suma
 * avisos encima), y calcularlo no fue confiable (emulador Android 16,
 * 29/09/2026). Es el patrón de Material para formularios en ventana.
 */
val LocalTecladoEnDialogo = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
expect fun margenesDialogoTeclado(): MargenesDialogo

/** Contenedor: ventana completa + espacio para barras y teclado; el contenido centrado. */
@Composable
fun DialogoConTeclado(onDismissRequest: () -> Unit, contenido: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onDismissRequest, properties = propiedadesDialogoTeclado()) {
        val m = margenesDialogoTeclado()
        androidx.compose.runtime.CompositionLocalProvider(LocalTecladoEnDialogo provides m.teclado) {
            Box(
                Modifier.fillMaxSize().padding(top = m.arriba + 12.dp, bottom = m.abajo + 12.dp),
                contentAlignment = Alignment.Center, content = contenido,
            )
        }
    }
}

/**
 * Reemplazo de AlertDialog con los mismos slots, pero que respeta el teclado.
 * El texto va en un área que se achica (el llamador lo hace desplazable).
 */
@Composable
fun AlertaConTeclado(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = MaterialTheme.shapes.extraLarge,
) {
    DialogoConTeclado(onDismissRequest) {
        Surface(shape = shape, color = containerColor, modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                val teclado = LocalTecladoEnDialogo.current
                if (title != null || teclado) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() } }
                        }
                        // Con el teclado abierto el pie puede quedar tapado: el botón sube acá.
                        if (teclado) { Spacer(Modifier.width(8.dp)); confirmButton() }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                text?.let {
                    Box(Modifier.weight(1f, fill = false)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                    }
                }
                // Con el teclado abierto el botón ya está junto al título: el pie
                // lo duplicaba (reporte 29/09/2026). Cancelar = gesto atrás.
                if (!teclado) {
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        dismissButton?.let { it(); Spacer(Modifier.width(8.dp)) }
                        confirmButton()
                    }
                }
            }
        }
    }
}
