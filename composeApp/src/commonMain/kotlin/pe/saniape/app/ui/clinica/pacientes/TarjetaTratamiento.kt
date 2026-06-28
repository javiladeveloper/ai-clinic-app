package pe.saniape.app.ui.clinica.pacientes

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.SesionFicha
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.EstadosColor
import pe.saniape.app.ui.theme.Sania

/** Estado de sesión que se puede fijar desde el menú ⋯ (igual que la web). */
private val ESTADOS_SESION = listOf("Reprogramada", "No asistió", "Cancelada", "Otro")

/**
 * Tarjeta de un tratamiento en la ficha: cabecera (procedimiento/estado/progreso/pago)
 * + al expandir, la lista de sus sesiones con acciones (Completar, estados) y "Dar de Alta".
 * Las escrituras van por endpoints (sesión/estado, tratamiento/alta) → reglas en la web.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaTratamiento(
    t: TratamientoPaciente,
    verPagos: Boolean,
    esAdmin: Boolean,
    puedeSesiones: Boolean,
    onCompletarSesion: (SesionFicha) -> Unit,   // abre modal observaciones
    onCambioRealizado: () -> Unit,               // refrescar ficha tras acción
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var expandido by remember { mutableStateOf(false) }
    var sesiones by remember { mutableStateOf<List<SesionFicha>?>(null) }
    var accionando by remember { mutableStateOf(false) }
    var menuDe by remember { mutableStateOf<SesionFicha?>(null) }   // sesión con menú ⋯ abierto

    val estado = EstadosColor.cita(t.estado)
    val terminado = t.estado == "Alta" || t.estado == "Cancelado" || t.estado == "Suspendido"

    // Cargar sesiones al expandir (una vez).
    LaunchedEffect(expandido) {
        if (expandido && sesiones == null) {
            sesiones = runCatching { PacientesRepo.sesionesDe(t.id) }.getOrDefault(emptyList())
        }
    }

    fun recargarSesiones() {
        scope.launch {
            sesiones = runCatching { PacientesRepo.sesionesDe(t.id) }.getOrDefault(sesiones ?: emptyList())
            onCambioRealizado()
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
    ) {
        // Cabecera (tocable para expandir)
        Row(
            Modifier.fillMaxWidth().clickable { expandido = !expandido },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${modalidadIcono(t.modalidad)} ${t.procedimiento ?: "Tratamiento"}",
                color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(t.estado ?: "—", color = estado.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Text(if (expandido) "▴" else "▾", color = c.navy)
        }
        t.terapeutaNombre?.let {
            Text("con $it", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }

        // Progreso (si es por sesiones)
        if (t.modalidad != "Consulta" && t.totalSesiones > 0) {
            Spacer(Modifier.height(6.dp))
            val frac = (t.sesionesCompletadas.toFloat() / t.totalSesiones).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.chipBg)) {
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ok))
                }
                Spacer(Modifier.width(8.dp))
                Text("${t.sesionesCompletadas}/${t.totalSesiones} ses.", color = c.textoSuave, fontSize = 11.sp)
            }
        }

        // Estado de pago
        if (verPagos && t.estadoPago != null) {
            val pago = EstadosColor.cita(when (t.estadoPago) { "Pagado" -> "Confirmada"; "Parcial" -> "Pendiente"; else -> "Cancelada" })
            Spacer(Modifier.height(6.dp))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(pago.bg)
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("Pago: ${t.estadoPago}", color = pago.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Contenido expandido: sesiones + acciones ──
        if (expandido) {
            Spacer(Modifier.height(Sania.dim.md))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Spacer(Modifier.height(Sania.dim.md))

            when (val s = sesiones) {
                null -> Box(Modifier.fillMaxWidth().padding(Sania.dim.md), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                }
                else -> {
                    if (s.isEmpty()) {
                        Text("Sin sesiones registradas.", color = c.textoSuave, fontSize = 12.sp)
                    } else {
                        s.forEach { ses ->
                            FilaSesion(
                                ses = ses, verCosto = verPagos, puedeSesiones = puedeSesiones, accionando = accionando,
                                menuAbierto = menuDe?.id == ses.id,
                                onToggleMenu = { menuDe = if (menuDe?.id == ses.id) null else ses },
                                onCompletar = { onCompletarSesion(ses) },
                                onEstado = { nuevo ->
                                    menuDe = null
                                    if (accionando) return@FilaSesion
                                    accionando = true
                                    scope.launch {
                                        PacientesRepo.cambiarEstadoSesion(ses.id, nuevo)
                                        accionando = false
                                        recargarSesiones()
                                    }
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // Pagos (solo con permiso): acordado/pagado/saldo + registrar/editar/borrar.
                    if (verPagos) {
                        Spacer(Modifier.height(Sania.dim.md))
                        SeccionPagos(t = t, esAdmin = esAdmin, onCambio = { recargarSesiones() })
                    }

                    // Dar de alta (si el tratamiento sigue en curso y puede sesiones)
                    if (!terminado && puedeSesiones) {
                        Spacer(Modifier.height(Sania.dim.sm))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.ok.copy(alpha = 0.12f))
                                .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable(enabled = !accionando) {
                                    accionando = true
                                    scope.launch {
                                        PacientesRepo.darDeAlta(t.id)
                                        accionando = false
                                        onCambioRealizado()
                                    }
                                }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("✓ Dar de alta este tratamiento", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaSesion(
    ses: SesionFicha,
    verCosto: Boolean,
    puedeSesiones: Boolean,
    accionando: Boolean,
    menuAbierto: Boolean,
    onToggleMenu: () -> Unit,
    onCompletar: () -> Unit,
    onEstado: (String) -> Unit,
) {
    val c = Sania.colors
    val estado = EstadosColor.sesion(ses.estado)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.fondo).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sesión #${ses.numero}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${ses.fecha} ${ses.hora?.let { hora12(it) } ?: ""}".trim(), color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(ses.estado, color = estado.fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (verCosto && (ses.costo ?: 0.0) > 0) {
            Text("S/ ${formato2(ses.costo!!)}", color = c.teal, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        ses.motivoEstado?.takeIf { it.isNotBlank() }?.let {
            Text("Motivo: $it", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }

        // Acciones: solo en sesiones pendientes y con permiso.
        if (puedeSesiones && ses.pendiente) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniBtn("✓ Completar", c.navy, !accionando) { onCompletar() }
                MiniBtn("⋯ Estado", c.textoSuave, !accionando) { onToggleMenu() }
            }
            if (menuAbierto) {
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ESTADOS_SESION.forEach { e ->
                        MiniBtn(e, c.pend, !accionando) { onEstado(e) }
                    }
                }
            }
        }
    }
}

/** Métodos de pago (igual que la web). */
private val METODOS_PAGO = listOf("Efectivo", "Yape", "BCP", "Transferencia", "Otro")

/** Sección de pagos del tratamiento: resumen + lista + registrar (reusa endpoint). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionPagos(t: TratamientoPaciente, esAdmin: Boolean, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var pagos by remember { mutableStateOf<List<pe.saniape.app.data.staff.PagoFicha>?>(null) }
    var agregando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var monto by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf("Efectivo") }
    var editando by remember { mutableStateOf<String?>(null) }   // id del pago en edición
    var editMonto by remember { mutableStateOf("") }
    var editMetodo by remember { mutableStateOf("Efectivo") }
    var borrarId by remember { mutableStateOf<String?>(null) }   // confirmación de borrado

    suspend fun recargar() { pagos = runCatching { PacientesRepo.pagosDe(t.id) }.getOrDefault(pagos ?: emptyList()) }

    LaunchedEffect(t.id) {
        pagos = runCatching { PacientesRepo.pagosDe(t.id) }.getOrDefault(emptyList())
    }

    val acordado = t.montoAcordado
    val pagado = pagos?.sumOf { it.monto } ?: 0.0
    val saldo = acordado - pagado
    val frac = if (acordado > 0) (pagado / acordado).coerceIn(0.0, 1.0).toFloat() else 0f

    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
    Spacer(Modifier.height(Sania.dim.md))
    Text("💰 Pagos", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))

    // Resumen acordado/pagado/saldo
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ColMonto("Acordado", acordado, c.texto)
        ColMonto("Pagado", pagado, c.ok)
        ColMonto("Saldo", saldo, if (saldo > 0.005) c.error else c.ok)
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.chipBg)) {
        Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ok))
    }

    // Lista de pagos
    pagos?.takeIf { it.isNotEmpty() }?.let { lista ->
        Spacer(Modifier.height(8.dp))
        lista.forEach { p ->
            if (editando == p.id) {
                // Edición inline (solo Admin)
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = editMonto, onValueChange = { editMonto = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        placeholder = { Text("Monto", color = c.textoSuave) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        METODOS_PAGO.forEach { m -> ChipMetodo(m, editMetodo == m) { editMetodo = m } }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniBtn("Guardar", c.ok, !guardando) {
                            val m = editMonto.toDoubleOrNull()
                            if (m == null || m <= 0 || guardando) return@MiniBtn
                            guardando = true
                            scope.launch {
                                val ok = PacientesRepo.editarPago(p.id, m, editMetodo)
                                guardando = false
                                if (ok) { editando = null; recargar(); onCambio() }
                            }
                        }
                        MiniBtn("Cancelar", c.textoSuave, !guardando) { editando = null }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(p.metodo, color = c.navy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(p.fecha, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("S/ ${formato2(p.monto)}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    // Editar / borrar (solo Admin)
                    if (esAdmin) {
                        Spacer(Modifier.width(8.dp))
                        Text("✏", fontSize = 14.sp, modifier = Modifier.clickable {
                            editando = p.id; editMonto = formato2(p.monto); editMetodo = p.metodo; borrarId = null
                        })
                        Spacer(Modifier.width(10.dp))
                        Text("🗑", fontSize = 14.sp, modifier = Modifier.clickable { borrarId = p.id })
                    }
                }
                // Confirmación de borrado
                if (borrarId == p.id) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("¿Borrar este pago?", color = c.error, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        MiniBtn("Sí, borrar", c.error, !guardando) {
                            if (guardando) return@MiniBtn
                            guardando = true
                            scope.launch {
                                val ok = PacientesRepo.borrarPago(p.id)
                                guardando = false
                                if (ok) { borrarId = null; recargar(); onCambio() }
                            }
                        }
                        MiniBtn("No", c.textoSuave, !guardando) { borrarId = null }
                    }
                }
            }
        }
    }

    // Registrar pago
    Spacer(Modifier.height(8.dp))
    if (!agregando) {
        MiniBtn(if (saldo > 0.005) "+ Registrar pago" else "+ Pago adicional", c.navy, !guardando) { agregando = true }
    } else {
        androidx.compose.material3.OutlinedTextField(
            value = monto, onValueChange = { monto = it.filter { ch -> ch.isDigit() || ch == '.' } },
            placeholder = { Text("Monto", color = c.textoSuave) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            METODOS_PAGO.forEach { m -> ChipMetodo(m, metodo == m) { metodo = m } }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniBtn("Guardar pago", c.ok, !guardando) {
                val m = monto.toDoubleOrNull()
                if (m == null || m <= 0 || guardando) return@MiniBtn
                guardando = true
                scope.launch {
                    val ok = PacientesRepo.registrarPago(t.id, m, metodo)
                    guardando = false
                    if (ok) {
                        monto = ""; agregando = false
                        pagos = runCatching { PacientesRepo.pagosDe(t.id) }.getOrDefault(pagos ?: emptyList())
                        onCambio()
                    }
                }
            }
            MiniBtn("Cancelar", c.textoSuave, !guardando) { agregando = false; monto = "" }
        }
    }
}

@Composable
private fun ChipMetodo(m: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 5.dp),
    ) { Text(m, color = if (activo) c.sobreNavy else c.texto, fontSize = 11.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun ColMonto(label: String, monto: Double, color: Color) {
    val c = Sania.colors
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = c.textoSuave, fontSize = 10.sp)
        Text("S/ ${formato2(monto)}", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniBtn(label: String, color: Color, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado) { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

private fun modalidadIcono(m: String?): String = when (m) {
    "Paquete" -> "📦"; "Sesión suelta" -> "🎫"; "Consulta" -> "🩺"; else -> "•"
}

private fun formato2(n: Double): String {
    val cent = (n * 100).toLong()
    return "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
}
