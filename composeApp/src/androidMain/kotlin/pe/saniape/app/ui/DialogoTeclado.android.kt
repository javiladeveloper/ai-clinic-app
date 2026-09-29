package pe.saniape.app.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun AjustarDialogoAlTeclado() {
    val view = LocalView.current
    DisposableEffect(view) {
        // La vista del contenido cuelga del DialogLayout, que da la ventana.
        (view.parent as? DialogWindowProvider)?.window
            ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {}
    }
}
