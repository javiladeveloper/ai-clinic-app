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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Toast GLOBAL de la app (equivalente al toast.success/error de la web). Cualquier parte
 * de la app puede avisar "creado / guardado / error" sin acoplarse a la UI: emite por el
 * flujo y el [ToastHost] (montado una sola vez en la raíz) lo muestra.
 *
 * Uso: `Toaster.exito("Sesión #1 creada")` · `Toaster.error("No se pudo guardar")`.
 */
enum class TipoToast { EXITO, ERROR, INFO }

data class MensajeToast(val texto: String, val tipo: TipoToast)

object Toaster {
    // extraBufferCapacity: si se emiten varios muy seguidos, no se pierden.
    private val _mensajes = MutableSharedFlow<MensajeToast>(extraBufferCapacity = 8)
    val mensajes = _mensajes.asSharedFlow()

    fun exito(texto: String) { _mensajes.tryEmit(MensajeToast(texto, TipoToast.EXITO)) }
    fun error(texto: String) { _mensajes.tryEmit(MensajeToast(texto, TipoToast.ERROR)) }
    fun info(texto: String) { _mensajes.tryEmit(MensajeToast(texto, TipoToast.INFO)) }
}

/**
 * Host visual del toast. Se monta UNA vez, arriba de todo (en App.kt), para que el aviso
 * se vea sobre cualquier pantalla. Muestra el último mensaje ~2.5s con animación.
 */
@Composable
fun ToastHost() {
    var visible by remember { mutableStateOf(false) }
    var actual by remember { mutableStateOf<MensajeToast?>(null) }

    LaunchedEffect(Unit) {
        Toaster.mensajes.collect { m ->
            actual = m
            visible = true
            delay(2500)
            visible = false
            delay(250) // deja terminar la animación de salida
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            actual?.let { m ->
                val (bg, icono) = when (m.tipo) {
                    TipoToast.EXITO -> Color(0xFF16A34A) to "✓"
                    TipoToast.ERROR -> Color(0xFFDC2626) to "⚠"
                    TipoToast.INFO -> Color(0xFF1E2D5E) to "ℹ"
                }
                Row(
                    Modifier
                        .padding(bottom = 90.dp, start = 20.dp, end = 20.dp)  // sobre el bottom-nav
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(icono, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(m.texto, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
