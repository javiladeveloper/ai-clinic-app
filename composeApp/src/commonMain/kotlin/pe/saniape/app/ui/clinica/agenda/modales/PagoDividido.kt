package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.FilaPago
import pe.saniape.app.data.staff.MAX_PARTES_COBRO
import pe.saniape.app.data.staff.MIN_PARTES_COBRO
import pe.saniape.app.data.staff.completarFaltante
import pe.saniape.app.data.staff.diferenciaFilas
import pe.saniape.app.data.staff.estadoReparto
import pe.saniape.app.data.staff.filaNueva
import pe.saniape.app.data.staff.repartoValido
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * "Pagó con más de un medio": filas de medio + monto con el Falta / Sobra en
 * vivo (gemelo de components/cobro/PagoDividido.tsx de la web). La regla es la
 * del servidor ([pe.saniape.app.data.staff.validarPagosDivididos]): si aquí se
 * habilita Cobrar, el servidor acepta.
 */
@Composable
fun PagoDividido(
    total: Double,
    metodos: List<String>,
    filas: List<FilaPago>,
    onCambiar: (List<FilaPago>) -> Unit,
    deshabilitado: Boolean = false,
    /**
     * Tope de filas. El pago de un tratamiento con saldo a favor usa 3: la parte
     * con saldo ya ocupa uno de los 4 medios que acepta el servidor.
     */
    maxFilas: Int = MAX_PARTES_COBRO,
) {
    val c = Sania.colors
    val lista = metodos.ifEmpty { listOf("Efectivo") }
    fun cambiar(i: Int, f: FilaPago) = onCambiar(filas.mapIndexed { j, x -> if (j == i) f else x })

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        filas.forEachIndexed { i, f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectorMedio(
                    valor = f.metodo,
                    opciones = if (f.metodo in lista) lista else listOf(f.metodo) + lista,
                    habilitado = !deshabilitado,
                    modifier = Modifier.weight(1f),
                ) { cambiar(i, f.copy(metodo = it)) }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    colors = coloresCampoForm(),
                    value = f.monto,
                    onValueChange = { t -> cambiar(i, f.copy(monto = t.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' })) },
                    placeholder = { Text("0.00", color = c.textoSuave) },
                    singleLine = true,
                    enabled = !deshabilitado,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(110.dp),
                )
                if (filas.size > MIN_PARTES_COBRO) {
                    Box(
                        Modifier.padding(start = 4.dp).size(32.dp).clip(CircleShape)
                            .clickable(enabled = !deshabilitado) { onCambiar(filas.filterIndexed { j, _ -> j != i }) },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = c.textoSuave, fontSize = 14.sp) }
                } else {
                    Spacer(Modifier.width(36.dp))
                }
            }
        }

        val dif = diferenciaFilas(filas, total)
        val cuadra = repartoValido(filas, total) != null
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (filas.size < maxFilas) {
                Text(
                    "+ Agregar otro medio", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !deshabilitado) { onCambiar(filas + filaNueva(lista, filas)) }
                        .padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                estadoReparto(filas, total),
                color = when {
                    dif > 0 -> c.pend
                    dif < 0 || !cuadra -> c.error
                    else -> c.ok
                },
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        completarFaltante(filas, total)?.let { (destino, texto) ->
            Text(
                "Poner lo que falta en ${filas[destino].metodo}",
                color = c.textoSuave, fontSize = 12.sp, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(enabled = !deshabilitado) { cambiar(destino, filas[destino].copy(monto = texto)) },
            )
        }
    }
}

/** Caja con el medio elegido que abre la lista de medios de la clínica. */
@Composable
private fun SelectorMedio(
    valor: String,
    opciones: List<String>,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    onElegir: (String) -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(enabled = habilitado) { abierto = true }
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(valor, color = c.texto, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text("▾", color = c.navy)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { m ->
                DropdownMenuItem(text = { Text(m, fontSize = 14.sp) }, onClick = { abierto = false; onElegir(m) })
            }
        }
    }
}
