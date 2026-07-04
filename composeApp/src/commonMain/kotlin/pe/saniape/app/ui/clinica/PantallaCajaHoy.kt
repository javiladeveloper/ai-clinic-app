package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CajaRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.MovimientoCaja
import pe.saniape.app.ui.theme.Sania

/**
 * 💰 Caja de HOY (esencial móvil): cuánto entró hoy y por qué método, + egresos y neto.
 * Solo lectura — el kardex completo y el cierre formal viven en la web (/finanzas).
 */
@Composable
fun PantallaCajaHoy(ctx: ContextoStaff) {
    val c = Sania.colors
    var movs by remember { mutableStateOf<List<MovimientoCaja>?>(null) }
    var fallo by remember { mutableStateOf(false) }

    LaunchedEffect(ctx.clinicaId) {
        runCatching { CajaRepo.movimientosDeHoy() }
            .onSuccess { movs = it; fallo = false }
            .onFailure { fallo = true }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💰 Caja de hoy", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            }

            when {
                fallo -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("No se pudo cargar la caja. Revisa tu conexión.", color = c.textoSuave, fontSize = 13.sp)
                }
                movs == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                }
                else -> {
                    val lista = movs ?: emptyList()
                    val ingresos = lista.filter { it.tipo == "Ingreso" }
                    val egresos = lista.filter { it.tipo == "Egreso" }
                    val totalIn = ingresos.sumOf { it.monto }
                    val totalEg = egresos.sumOf { it.monto }
                    val porMetodo = ingresos.groupBy { it.metodoPago ?: "Sin método" }
                        .mapValues { (_, v) -> v.sumOf { it.monto } }
                        .entries.sortedByDescending { it.value }

                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        // Resumen: ingresos / egresos / neto
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CajaStat("Ingresos", totalIn, c.ok, Modifier.weight(1f))
                                CajaStat("Egresos", totalEg, c.error, Modifier.weight(1f))
                                CajaStat("Neto", totalIn - totalEg, c.navy, Modifier.weight(1f))
                            }
                        }
                        // Desglose de ingresos por método (lo que se cuadra al cierre)
                        if (porMetodo.isNotEmpty()) {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                                        .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                                        .padding(14.dp),
                                ) {
                                    Text("INGRESOS POR MÉTODO", color = c.textoSuave, fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                                        modifier = Modifier.padding(bottom = 8.dp))
                                    porMetodo.forEach { (metodo, monto) ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            Text(iconoMetodo(metodo) + "  " + metodo, color = c.texto,
                                                fontSize = 13.sp, modifier = Modifier.weight(1f))
                                            Text("S/ ${formatoCaja(monto)}", color = c.texto,
                                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        // Movimientos del día
                        item {
                            Text("MOVIMIENTOS (${lista.size})", color = c.textoSuave, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        if (lista.isEmpty()) {
                            item { Text("Sin movimientos hoy.", color = c.textoSuave, fontSize = 13.sp) }
                        }
                        items(lista, key = { it.id }) { m ->
                            val esIn = m.tipo == "Ingreso"
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        m.descripcion?.takeIf { it.isNotBlank() } ?: m.categoria ?: m.tipo,
                                        color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    )
                                    val sub = listOfNotNull(
                                        m.pacienteNombre,
                                        m.metodoPago,
                                    ).joinToString(" · ")
                                    if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 11.sp)
                                }
                                Text(
                                    (if (esIn) "+" else "−") + " S/ ${formatoCaja(m.monto)}",
                                    color = if (esIn) c.ok else c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        item {
                            Text("El kardex completo y el cierre de caja están en la web (Finanzas).",
                                color = c.textoSuave, fontSize = 10.sp,
                                modifier = Modifier.padding(vertical = 8.dp))
                        }
                        item { Spacer(Modifier.height(Sania.dim.xxl)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CajaStat(titulo: String, monto: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(titulo.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(3.dp))
        Text("S/ ${formatoCaja(monto)}", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

private fun iconoMetodo(m: String): String = when (m) {
    "Efectivo" -> "💵"; "Yape" -> "🟣"; "Plin" -> "🔵"; "BCP" -> "🏦"; "Transferencia" -> "🔁"; else -> "💳"
}

private fun formatoCaja(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else ((v * 100).toInt() / 100.0).toString()
