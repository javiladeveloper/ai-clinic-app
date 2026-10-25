package pe.saniape.app.ui.componentes

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import pe.saniape.app.ui.theme.Sania

/**
 * Confirmación antes de una acción destructiva de un toque (dar de baja,
 * cancelar o suspender un tratamiento, borrar una foto o un documento…). El
 * mismo dibujo que `ConfirmacionAccion` de la agenda: «Sí, …» en color y «No».
 *
 * [peligro] = rojo (borrar, cancelar, dar de baja); si no, ámbar (suspender).
 */
@Composable
fun DialogoConfirmar(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit,
    peligro: Boolean = true,
    textoCancelar: String = "No",
) {
    val c = Sania.colors
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(texto, color = c.texto, fontSize = Sania.txt.cuerpo) },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(textoConfirmar, color = if (peligro) c.error else c.pend, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text(textoCancelar, color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}
