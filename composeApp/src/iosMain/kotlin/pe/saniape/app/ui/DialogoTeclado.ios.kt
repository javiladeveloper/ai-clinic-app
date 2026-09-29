package pe.saniape.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** En iOS el diálogo de Compose ya se aparta del teclado; basta con el ancho completo. */
@Composable
actual fun propiedadesDialogoTeclado(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false)

@Composable
actual fun margenesDialogoTeclado(): MargenesDialogo = MargenesDialogo(24.dp, 24.dp)
