package pe.saniape.app.ui.clinica.pacientes

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.FilaPago
import pe.saniape.app.data.staff.MAX_PARTES_RESTO
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.PagarConSaldoRepo
import pe.saniape.app.data.staff.PagoFicha
import pe.saniape.app.data.staff.PagoMostrado
import pe.saniape.app.data.staff.ResumenEliminar
import pe.saniape.app.data.staff.TipoPagoMostrado
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.data.staff.ValidacionPartes
import pe.saniape.app.data.staff.aCentimos
import pe.saniape.app.data.staff.esMetodoSaldo
import pe.saniape.app.data.staff.fechaLiberacion
import pe.saniape.app.data.staff.filasIniciales
import pe.saniape.app.data.staff.montoDeTexto
import pe.saniape.app.data.staff.pagadoNeto
import pe.saniape.app.data.staff.resumenAlEliminar
import pe.saniape.app.data.staff.resumenPartes
import pe.saniape.app.data.staff.yaDioSaldo
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.agenda.modales.PagoDividido
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.simboloMoneda

// ─────────────────────────────────────────────────────────────────────────────
// PAGAR CON SALDO A FAVOR y el saldo de los CANCELADOS en la tarjeta de pagos
// (gemelo de components/pacientes/PagoCard.tsx). La lógica de pantalla vive en
// data/staff/PagarConSaldo.kt; el dinero lo decide el servidor.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * "Usar saldo a favor (disponible S/ X)" dentro de Registrar pago. Encendido: la
 * parte con saldo (precargada con min(disponible, monto)) y el resto con los
 * medios de siempre — uno, o varios con la UI del cobro dividido (hasta 3: la
 * parte con saldo ocupa uno de los 4 medios). Abajo, en vivo, cómo queda el pago.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpcionSaldoAFavor(
    usable: Double,
    total: Double?,
    usar: Boolean,
    onUsar: (Boolean) -> Unit,
    montoSaldo: String,
    onMontoSaldo: (String) -> Unit,
    metodos: List<String>,
    metodoResto: String,
    onMetodoResto: (String) -> Unit,
    filasResto: List<FilaPago>?,
    onFilasResto: (List<FilaPago>?) -> Unit,
    validacion: ValidacionPartes?,
    deshabilitado: Boolean,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val medios = metodos.filterNot { esMetodoSaldo(it) }.ifEmpty { listOf("Efectivo") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.tealBg)
            .border(1.dp, c.teal, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(enabled = !deshabilitado) { onUsar(!usar) },
        ) {
            Checkbox(
                checked = usar, onCheckedChange = { onUsar(it) }, enabled = !deshabilitado,
                colors = CheckboxDefaults.colors(checkedColor = c.teal),
            )
            Column(Modifier.weight(1f)) {
                Text("Usar saldo a favor (disponible ${formatearDinero(usable, moneda)})", color = c.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("No entra a caja: ese dinero ya se recibió.", color = c.textoSuave, fontSize = 10.sp)
            }
        }
        if (!usar) return@Column

        val parteSaldo = montoDeTexto(montoSaldo) ?: 0.0
        val restoC = (total?.let { aCentimos(it) } ?: 0L) - aCentimos(parteSaldo)
        val resto = restoC / 100.0
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                colors = coloresCampoForm(),
                value = montoSaldo,
                onValueChange = { t -> onMontoSaldo(t.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) },
                label = { Text("Con saldo a favor (${simboloMoneda(moneda)})", fontSize = 11.sp) },
                singleLine = true, enabled = !deshabilitado,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Resto a cobrar", color = c.textoSuave, fontSize = 10.sp)
                Text(formatearDinero(resto.coerceAtLeast(0.0), moneda), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (restoC > 0) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Text("El resto con", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (filasResto == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        medios.forEach { m -> ChipMedio(m, metodoResto == m, !deshabilitado) { onMetodoResto(m) } }
                    }
                    Text(
                        "Pagó el resto con más de un medio", color = c.navy, fontSize = 12.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.padding(top = 6.dp)
                            .clickable(enabled = !deshabilitado) { onFilasResto(filasIniciales(medios, metodoResto)) },
                    )
                } else {
                    PagoDividido(
                        total = resto, metodos = medios, filas = filasResto, onCambiar = { onFilasResto(it) },
                        deshabilitado = deshabilitado, maxFilas = MAX_PARTES_RESTO,
                    )
                    Text(
                        "Un solo medio para el resto", color = c.navy, fontSize = 12.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.padding(top = 6.dp).clickable(enabled = !deshabilitado) { onFilasResto(null) },
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        val (texto, color) = when (validacion) {
            is ValidacionPartes.Ok ->
                (if (validacion.partes.size == 1) "✓ Todo con saldo a favor (${formatearDinero(validacion.saldo, moneda)})"
                else "✓ ${resumenPartes(validacion.partes, moneda)}") to c.ok
            is ValidacionPartes.Error -> validacion.mensaje to c.error
            null -> "" to c.textoSuave
        }
        if (texto.isNotEmpty()) {
            Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun ChipMedio(m: String, activo: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(enabled = habilitado) { onClick() }.padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(m, color = if (activo) c.sobreNavy else c.texto, fontSize = 11.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun BotonChico(label: String, color: Color, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado) { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Una línea del historial que NO es un pago simple: un pago con saldo a favor
 * o en varias partes ("Saldo a favor S/ 205.97 + Yape S/ 284.03 = S/ 490.00"),
 * o un consumo ("Saldo aplicado a Ortodoncia -S/ 100.00", sin acciones: se
 * deshace desde el pago donde se usó). Admin: editar la parte con saldo y
 * borrar el pago COMPLETO.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilaPagoAgrupado(
    g: PagoMostrado,
    esAdmin: Boolean,
    soloLectura: Boolean,
    onCambio: () -> Unit,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val tpl = LocalTerminologiaPaciente.current
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var confirmarBorrar by remember(g.clave) { mutableStateOf(false) }
    var editandoSaldo by remember(g.clave) { mutableStateOf(false) }
    var montoEdit by remember(g.clave) { mutableStateOf("") }
    var guardando by remember(g.clave) { mutableStateOf(false) }

    if (g.tipo == TipoPagoMostrado.CONSUMO) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.tealBg)
                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("Saldo a favor", color = c.teal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(g.detalle.orEmpty(), color = c.textoSuave, fontSize = 11.sp)
                Text(g.fecha, color = c.textoSuave, fontSize = 10.sp)
            }
            Text(formatearDinero(g.monto, moneda), color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    val uso = g.uso
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                g.partes.forEach { p ->
                    val conSaldo = p.saldoTipo == pe.saniape.app.data.staff.SALDO_TIPO_USO
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (conSaldo) c.tealBg else c.chipBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("${p.metodo} ${formatearDinero(p.monto, moneda)}", color = if (conSaldo) c.teal else c.navy,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("= ${formatearDinero(g.monto, moneda)}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(g.fecha, color = c.textoSuave, fontSize = 10.sp)
            }
            if (esAdmin && !soloLectura) {
                if (uso != null) {
                    Spacer(Modifier.width(8.dp))
                    Text("✏", fontSize = 14.sp, modifier = Modifier.clickable(enabled = !guardando) {
                        editandoSaldo = true; confirmarBorrar = false; montoEdit = pe.saniape.app.data.staff.textoMonto(uso.monto)
                    })
                }
                Spacer(Modifier.width(10.dp))
                Text("🗑", fontSize = 14.sp, modifier = Modifier.clickable(enabled = !guardando) {
                    confirmarBorrar = true; editandoSaldo = false
                })
            }
        }
        g.partes.mapNotNull { it.notas?.takeIf { n -> n.isNotBlank() } }.firstOrNull()?.let {
            Text("📝 $it", color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }

        if (editandoSaldo && uso != null) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                colors = coloresCampoForm(),
                value = montoEdit,
                onValueChange = { t -> montoEdit = t.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text("Con saldo a favor (${simboloMoneda(moneda)})", fontSize = 11.sp) },
                singleLine = true, enabled = !guardando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Solo dentro del saldo disponible. Las demás partes no cambian.", color = c.textoSuave, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonChico(if (guardando) "Guardando…" else "Guardar", c.ok, !guardando) {
                    val m = montoDeTexto(montoEdit)
                    if (m == null || m <= 0 || guardando) return@BotonChico
                    guardando = true
                    scope.launch {
                        val err = conIndicador(Gestion.ACTUALIZANDO) { PagarConSaldoRepo.editarParteSaldo(uso.id, m) }
                        guardando = false
                        if (err == null) { editandoSaldo = false; Toaster.exito("Pago actualizado"); onCambio() }
                        else Toaster.error(err)
                    }
                }
                BotonChico("Cancelar", c.textoSuave, !guardando) { editandoSaldo = false }
            }
        }

        if (confirmarBorrar) {
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    "¿Borrar el pago completo de ${formatearDinero(g.monto, moneda)}?" +
                        (if (uso != null) " El saldo a favor vuelve al ${LocalTerminologiaPaciente.current.paciente}." else "") +
                        " Las demás partes salen de caja.",
                    color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonChico(if (guardando) "Borrando…" else "Borrar pago completo", c.error, !guardando) {
                        if (guardando) return@BotonChico
                        guardando = true
                        scope.launch {
                            val err = conIndicador(Gestion.ELIMINANDO) {
                                PagarConSaldoRepo.borrar(g.partes.first().id, borrarGrupo = true)
                            }
                            guardando = false
                            confirmarBorrar = false
                            if (err == null) Toaster.exito(if (uso != null) "Pago eliminado: el saldo a favor volvió al ${tpl.paciente}" else "Pago eliminado")
                            else Toaster.error(err)
                            // También tras un error: un borrado parcial cambia lo que hay que ver.
                            onCambio()
                        }
                    }
                    BotonChico("No", c.textoSuave, !guardando) { confirmarBorrar = false }
                }
            }
        }
    }
}

/**
 * Tratamiento CANCELADO con dinero pagado y no atendido: no es saldo a favor
 * hasta que un Admin lo pasa (decisión del dueño 2026-10-06). Liberado: quién y
 * cuándo, y "Deshacer" (si ese saldo aún no pagó otro tratamiento).
 */
@Composable
fun AvisoSaldoCancelado(
    t: TratamientoPaciente,
    pagos: List<PagoFicha>,
    esAdmin: Boolean,
    soloLectura: Boolean,
    onCambio: () -> Unit,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val tpl = LocalTerminologiaPaciente.current
    if (t.estado != "Cancelado") return
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var trabajando by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }
    // Lo pagado (neto) y no atendido, con la MISMA regla que la base. Ante la duda (sin
    // conteos, Unidades con atenciones…) da 0 y no se ofrece nada.
    val noAtendido = pe.saniape.app.data.noAtendidoCancelado(t.cuentaCon(pagadoNeto(pagos)))
    val liberadoAt = t.saldoLiberadoAt

    if (liberadoAt != null) {
        var nombre by remember(t.saldoLiberadoPor) { mutableStateOf<String?>(null) }
        LaunchedEffect(t.saldoLiberadoPor) {
            nombre = t.saldoLiberadoPor?.let { PagarConSaldoRepo.nombrePerfil(it) }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.tealBg)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                "✓ Pasado a saldo a favor el ${fechaLiberacion(liberadoAt)}" +
                    (nombre?.let { " por $it" } ?: "") +
                    (if (noAtendido > 0.005) " · quedan ${formatearDinero(noAtendido, moneda)} sin usar" else ""),
                color = c.texto, fontSize = 12.sp,
            )
            if (esAdmin && !soloLectura) {
                if (yaDioSaldo(pagos)) {
                    Text("Ya se usó para pagar otro tratamiento: para deshacerlo, primero elimina ese pago.",
                        color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                } else {
                    Text(
                        if (trabajando) "Deshaciendo…" else "Deshacer", color = c.navy, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.padding(top = 4.dp).clickable(enabled = !trabajando) {
                            trabajando = true
                            scope.launch {
                                val err = conIndicador(Gestion.ACTUALIZANDO) { PagarConSaldoRepo.revocar(t.id) }
                                trabajando = false
                                if (err == null) Toaster.exito("Se deshizo: ese dinero ya no es saldo a favor")
                                else Toaster.error(err)
                                onCambio()
                            }
                        },
                    )
                }
            }
        }
        return
    }

    if (noAtendido <= 0.005) return
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text("${formatearDinero(noAtendido, moneda)} pagados y no atendidos. No son saldo a favor hasta que un administrador los pase.",
            color = c.texto, fontSize = 12.sp)
        if (esAdmin && !soloLectura) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.teal)
                    .clickable(enabled = !trabajando) { confirmar = true }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (trabajando) "Guardando…" else "Pasar lo pagado no atendido a saldo a favor (${formatearDinero(noAtendido, moneda)})",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { if (!trabajando) confirmar = false },
            title = { Text("¿Pasar a saldo a favor?", fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Pasar ${formatearDinero(noAtendido, moneda)} a saldo a favor del ${LocalTerminologiaPaciente.current.paciente}? Podrá usarlo para pagar otro tratamiento.",
                    color = c.texto, fontSize = Sania.txt.cuerpo)
            },
            confirmButton = {
                TextButton(enabled = !trabajando, onClick = {
                    trabajando = true
                    scope.launch {
                        val (r, err) = conIndicador(Gestion.GUARDANDO) { PagarConSaldoRepo.liberar(t.id) }
                        trabajando = false
                        confirmar = false
                        if (err == null) Toaster.exito("${formatearDinero(r?.monto ?: noAtendido, moneda)} pasaron a saldo a favor del ${tpl.paciente}")
                        else Toaster.error(err)
                        onCambio()
                    }
                }) { Text("Sí, pasar", color = c.ok, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !trabajando, onClick = { confirmar = false }) { Text("Cancelar", color = c.textoSuave) }
            },
            containerColor = c.superficie,
        )
    }
}

/**
 * Eliminar un tratamiento (solo Admin; borrado lógico). Si tiene dinero, se
 * decide qué pasa con él, como la ficha web: los pagos REALES en caja, lo que
 * recibió con saldo (vuelve siempre al paciente) y lo que dio a otro (si se
 * revierte, ese otro vuelve a deberlo). Si algo no se puede, el servidor NO lo
 * elimina y su texto se muestra tal cual (también un parcial).
 */
@Composable
fun DialogoEliminarTratamiento(
    t: TratamientoPaciente,
    onCerrar: () -> Unit,
    /** Terminó (bien o con un parcial): la ficha se recarga. */
    onTermino: () -> Unit,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var resumen by remember(t.id) { mutableStateOf<ResumenEliminar?>(null) }
    var fallo by remember(t.id) { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf(false) }
    LaunchedEffect(t.id) {
        runCatching { PacientesRepo.pagosDe(t.id) }
            .onSuccess { resumen = resumenAlEliminar(it) }
            .onFailure { if (it is kotlin.coroutines.cancellation.CancellationException) throw it; fallo = true }
    }

    fun eliminar(revertir: Boolean) {
        if (trabajando) return
        trabajando = true
        scope.launch {
            val err = conIndicador(Gestion.ELIMINANDO) { PagarConSaldoRepo.eliminarTratamiento(t.id, revertir) }
            trabajando = false
            if (err == null) {
                Toaster.exito(if (revertir) "Tratamiento eliminado y sus pagos revertidos de la caja" else "Tratamiento eliminado")
            } else {
                Toaster.error(err)
            }
            onTermino()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!trabajando) onCerrar() },
        title = { Text("🗑 Eliminar este tratamiento", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se ocultará de la ficha con sus sesiones y citas.", color = c.textoSuave, fontSize = 12.sp)
                val r = resumen
                when {
                    fallo -> Text("Sin conexión: no se pudieron revisar sus pagos. Inténtalo con señal.",
                        color = c.error, fontSize = 12.sp)
                    r == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp).height(16.dp).width(16.dp))
                        Text("Revisando sus pagos…", color = c.textoSuave, fontSize = 12.sp)
                    }
                    !r.tieneDinero -> Text("No tiene pagos: no se toca la caja.", color = c.texto, fontSize = 12.sp)
                    else -> {
                        if (r.reales.isNotEmpty()) {
                            AvisoCaja(
                                "💳 Tiene ${if (r.reales.size == 1) "un pago" else "${r.reales.size} pagos"} por ${formatearDinero(r.totalReales, moneda)} " +
                                    "registrado${if (r.reales.size == 1) "" else "s"} en caja. ¿Qué hago con ese dinero?",
                                c.teal, c.tealBg,
                            )
                        }
                        if (r.recibido > 0.005) {
                            AvisoCaja("${formatearDinero(r.recibido, moneda)} se pagaron con saldo a favor: vuelven al saldo del ${LocalTerminologiaPaciente.current.paciente}.", c.texto, c.chipBg)
                        }
                        if (r.dado.isNotEmpty()) {
                            AvisoCaja(
                                "Parte de este dinero ya pagó otro tratamiento como saldo a favor. Si lo reviertes, " +
                                    r.dado.joinToString(", ") { (n, m) -> "$n vuelve a deber ${formatearDinero(m, moneda)}" } + ".",
                                c.texto, c.pendBg,
                            )
                        }
                        BotonAncho(if (trabajando) "Eliminando…" else "Conservar el dinero en caja (se cobró de verdad)", c.ok, !trabajando) { eliminar(false) }
                        BotonAncho("Revertir: borrar el pago y su ingreso de caja", c.error, !trabajando) { eliminar(true) }
                    }
                }
            }
        },
        confirmButton = {
            val r = resumen
            if (r != null && !r.tieneDinero) {
                TextButton(enabled = !trabajando, onClick = { eliminar(false) }) {
                    Text(if (trabajando) "Eliminando…" else "Sí, eliminar", color = c.error, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !trabajando, onClick = onCerrar) { Text("Cancelar", color = c.textoSuave) }
        },
        containerColor = c.superficie,
    )
}

@Composable
private fun AvisoCaja(texto: String, fg: Color, bg: Color) {
    Text(texto, color = fg, fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg).padding(8.dp))
}

@Composable
private fun BotonAncho(texto: String, color: Color, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado) { onClick() }.padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}
