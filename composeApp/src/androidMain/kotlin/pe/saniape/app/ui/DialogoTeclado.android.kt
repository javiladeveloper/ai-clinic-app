package pe.saniape.app.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Composable
actual fun propiedadesDialogoTeclado(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

@Composable
actual fun margenesDialogoTeclado(): MargenesDialogo {
    val view = LocalView.current
    val densidad = LocalDensity.current
    var margenes by remember { mutableStateOf(MargenesDialogo(24.dp, 24.dp)) }
    DisposableEffect(view) {
        // La vista del contenido cuelga del DialogLayout, que da la ventana.
        val ventana = (view.parent as? DialogWindowProvider)?.window
        // "No ajustar": sin esto Android desplaza el diálogo (pan), y con "ajustar"
        // redimensiona la ventana y vuelve a avisar el teclado en 0. Con NOTHING
        // la altura del teclado llega y se mantiene: el margen lo pone el contenido.
        ventana?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val decor = ventana?.decorView
        if (decor != null) {
            ViewCompat.setOnApplyWindowInsetsListener(decor) { v, insets ->
                val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
                margenes = with(densidad) {
                    MargenesDialogo(barras.top.toDp(), maxOf(teclado.bottom, barras.bottom).toDp(), teclado.bottom > 0)
                }
                ViewCompat.onApplyWindowInsets(v, insets)
            }
            ViewCompat.requestApplyInsets(decor)
        }
        onDispose { decor?.let { ViewCompat.setOnApplyWindowInsetsListener(it, null) } }
    }
    return margenes
}
