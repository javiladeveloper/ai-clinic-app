package pe.saniape.app.ui.clinica.comprobante

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ReciboRepo
import pe.saniape.app.data.staff.ResultadoRecibo
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.theme.tocable

/**
 * "🧾 Comprobante" de un pago YA hecho (gemelo de `elegirRecibo` de la web,
 * 2026-10-17): la MISMA elección que al cobrar — recibo A4 o ticket de 80 mm — y
 * el mismo recibo, marcado como reimpresión. Se pide ANTES de abrir: si el
 * servidor dice que no (anulado, aún no cobrado, sin permiso, sin red) es un
 * Toaster.error con su mensaje y no se abre nada. Lo abre el visor/compartir
 * PDF de siempre (`abrirPdf`). Quien lo muestra decide si corresponde.
 */
@Composable
fun DialogoComprobante(tipo: String, id: String, onCerrar: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var bajando by remember(tipo, id) { mutableStateOf<String?>(null) }   // formato en curso
    fun abrir(formato: String) {
        if (bajando != null) return
        bajando = formato
        scope.launch {
            val r = ReciboRepo.descargar(tipo, id, formato, reimpresion = true)
            bajando = null
            when (r) {
                is ResultadoRecibo.Ok -> { onCerrar(); acciones.abrirPdf("comprobante-$tipo-${id.take(8)}-$formato.pdf", r.pdf) }
                is ResultadoRecibo.Error -> { onCerrar(); Toaster.error(r.mensaje) }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (bajando == null) onCerrar() },
        title = { Text("🧾 Comprobante", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "¿En qué formato lo imprimes? Es el mismo recibo del cobro, marcado como reimpresión.",
                    color = c.textoSuave, fontSize = 13.sp,
                )
                OpcionFormato(if (bajando == "a4") "Generando…" else "🧾 Recibo A4", bajando == null) { abrir("a4") }
                OpcionFormato(if (bajando == "ticket") "Generando…" else "🎫 Ticket 80 mm", bajando == null) { abrir("ticket") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(enabled = bajando == null, onClick = onCerrar) { Text("Cancelar", color = c.textoSuave) }
        },
        containerColor = c.superficie,
    )
}

@Composable
private fun OpcionFormato(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box(
        Modifier.fillMaxWidth().clip(forma).background(c.navy.copy(alpha = 0.08f))
            .border(1.dp, if (habilitado) c.navy else c.borde, forma)
            .tocable(habilitado = habilitado) { onClick() }
            .padding(vertical = 12.dp, horizontal = 14.dp),
    ) { Text(texto, color = if (habilitado) c.navy else c.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Botón "🧾" de una fila (pago, movimiento de caja) que abre [DialogoComprobante].
 * Toque de 34 dp, como las demás acciones de fila.
 */
@Composable
fun BotonComprobante(tipo: String, id: String, modifier: Modifier = Modifier) {
    var abierto by remember(tipo, id) { mutableStateOf(false) }
    Box(
        modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).tocable { abierto = true }
            .semantics { contentDescription = "Reimprimir comprobante" },
        contentAlignment = Alignment.Center,
    ) { Text("🧾", fontSize = 15.sp) }
    if (abierto) DialogoComprobante(tipo, id) { abierto = false }
}
