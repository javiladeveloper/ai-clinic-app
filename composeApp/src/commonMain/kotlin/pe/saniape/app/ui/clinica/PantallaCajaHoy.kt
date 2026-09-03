package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CajaRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.MovimientoCaja
import pe.saniape.app.ui.theme.Sania

/**
 * 💰 Caja de HOY (esencial móvil): cuánto entró hoy y por qué método, + egresos y neto,
 * y "+ Registrar" para meter un movimiento manual (recepción lo pedía: la caja era solo
 * un reporte y los gastos del día se quedaban sin anotar hasta llegar a la web).
 * El kardex completo y el cierre formal siguen en la web (/finanzas).
 */
@Composable
fun PantallaCajaHoy(ctx: ContextoStaff) {
    val c = Sania.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var movs by remember { mutableStateOf<List<MovimientoCaja>?>(null) }
    var fallo by remember { mutableStateOf(false) }
    var registrando by remember { mutableStateOf(false) }

    suspend fun cargar() {
        runCatching { CajaRepo.movimientosDeHoy() }
            .onSuccess { movs = it; fallo = false }
            .onFailure { fallo = true }
    }
    LaunchedEffect(ctx.clinicaId) { cargar() }

    if (registrando) {
        ModalRegistrarMovimiento(
            onCancelar = { registrando = false },
            onGuardar = { tipo, categoria, descripcion, monto, metodo, comprobante ->
                registrando = false
                scope.launch {
                    val err = CajaRepo.registrarMovimiento(tipo, categoria, descripcion, monto, metodo, comprobante)
                    if (err == null) pe.saniape.app.ui.Toaster.exito("$tipo de S/ ${formatoCaja(monto)} registrado")
                    else pe.saniape.app.ui.Toaster.error(err)
                    cargar()
                }
            },
        )
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💰 Caja de hoy", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                        .clickable { registrando = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("+ Registrar", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
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

/**
 * Registrar un movimiento manual (paridad con "Registrar Movimiento" de /finanzas web).
 * Default EGRESO: los ingresos de pacientes ya entran solos por pagos/citas — lo que
 * recepción anota a mano suele ser el gasto del día (agua, taxi, insumos).
 */
@Composable
private fun ModalRegistrarMovimiento(
    onCancelar: () -> Unit,
    onGuardar: (tipo: String, categoria: String, descripcion: String?, monto: Double, metodo: String?, comprobante: String?) -> Unit,
) {
    val c = Sania.colors
    var tipo by remember { mutableStateOf("Egreso") }
    var monto by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf("Efectivo") }
    var comprobante by remember { mutableStateOf("") }

    pe.saniape.app.ui.clinica.pacientes.DialogoForm(
        titulo = "Registrar movimiento",
        subtitulo = "Entra al kardex de hoy (se ve también en Finanzas web)",
        textoAccion = "✓ Registrar",
        accionHabilitada = (monto.toDoubleOrNull() ?: 0.0) > 0 && descripcion.isNotBlank(),
        onCancelar = onCancelar,
        onAccion = {
            val m = monto.toDoubleOrNull() ?: return@DialogoForm
            onGuardar(
                tipo,
                categoria.trim().ifBlank { if (tipo == "Egreso") "Otro" else "Otro ingreso" },
                descripcion.trim().ifBlank { null }, m, metodo,
                comprobante.trim().ifBlank { null },
            )
        },
    ) {
        pe.saniape.app.ui.clinica.pacientes.TarjetaForm(titulo = "Movimiento", icono = "🧾") {
            pe.saniape.app.ui.clinica.pacientes.EtqForm("Tipo")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Egreso" to "− Egreso (gasto)", "Ingreso" to "+ Ingreso").forEach { (v, etq) ->
                    val activo = tipo == v
                    val col = if (v == "Ingreso") c.ok else c.error
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (activo) col.copy(alpha = 0.15f) else c.superficie)
                            .border(1.5.dp, if (activo) col else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { tipo = v }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(etq, color = if (activo) col else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(10.dp))
            pe.saniape.app.ui.clinica.pacientes.EtqForm("Descripción *")
            androidx.compose.material3.OutlinedTextField(
                colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
                value = descripcion, onValueChange = { descripcion = it },
                placeholder = { Text(if (tipo == "Egreso") "Ej. Compra de sábanas, taxi…" else "Ej. Venta de faja…", color = c.textoSuave) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    pe.saniape.app.ui.clinica.pacientes.EtqForm("Monto (S/) *")
                    androidx.compose.material3.OutlinedTextField(
                        colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
                        value = monto,
                        onValueChange = { monto = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    pe.saniape.app.ui.clinica.pacientes.EtqForm("Categoría")
                    androidx.compose.material3.OutlinedTextField(
                        colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
                        value = categoria, onValueChange = { categoria = it },
                        placeholder = { Text(if (tipo == "Egreso") "Insumos" else "Otro ingreso", color = c.textoSuave) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            pe.saniape.app.ui.clinica.pacientes.EtqForm("Método")
            pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago(metodo) { metodo = it }
            Spacer(Modifier.height(10.dp))
            pe.saniape.app.ui.clinica.pacientes.EtqForm("Comprobante — opcional")
            androidx.compose.material3.OutlinedTextField(
                colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
                value = comprobante, onValueChange = { comprobante = it },
                placeholder = { Text("N° de boleta/recibo (uno por clínica)", color = c.textoSuave) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
