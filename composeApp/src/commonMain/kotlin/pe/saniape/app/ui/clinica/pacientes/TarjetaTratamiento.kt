package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
    consultaDone: Boolean = false,   // para la barra de recorrido (de los hitos del paciente)
    evalDone: Boolean = false,
    onCompletarSesion: (SesionFicha, anterior: SesionFicha?) -> Unit,   // abre modal (con sesión previa de referencia)
    onCambioRealizado: () -> Unit,               // refrescar ficha tras acción
    onEditar: (TratamientoPaciente) -> Unit = {},
    onAmpliar: (TratamientoPaciente) -> Unit = {},
    onCambiarEstadoTrat: (tratamientoId: String, estado: String) -> Unit = { _, _ -> },
    onCrearSesion: (TratamientoPaciente) -> Unit = {},
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var expandido by remember { mutableStateOf(false) }
    var sesiones by remember { mutableStateOf<List<SesionFicha>?>(null) }
    var accionando by remember { mutableStateOf(false) }
    var menuDe by remember { mutableStateOf<SesionFicha?>(null) }   // sesión con menú ⋯ abierto
    var cambioToken by remember { mutableStateOf(0) }   // recarga la sección de pagos tras cobros
    var menuTrat by remember { mutableStateOf(false) }   // menú ⋯ del tratamiento
    // Sesiones objetivo de cada modal (o null).
    var editarSesion by remember { mutableStateOf<SesionFicha?>(null) }
    var borrarSesion by remember { mutableStateOf<SesionFicha?>(null) }
    var reasignarSesion by remember { mutableStateOf<SesionFicha?>(null) }
    var cobrarSesion by remember { mutableStateOf<SesionFicha?>(null) }

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
            cambioToken++          // fuerza que la sección de pagos se recargue
            onCambioRealizado()
        }
    }

    // Acento de color por tipo: sesiones = teal, consulta = morado (multi-especialidad).
    val acento = if (t.esConsulta) c.purple else c.teal
    val cerrado = t.estado == "Alta" || t.estado == "Cancelado" || t.estado == "Suspendido"

    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)),
    ) {
        // Barra de acento lateral (tipo de tratamiento)
        Box(Modifier.width(5.dp).fillMaxHeight().background(if (cerrado) c.borde else acento))

      Column(Modifier.fillMaxWidth().padding(Sania.dim.tarjeta)) {
        // Cabecera (tocable para expandir): icono + nombre + estado
        Row(
            Modifier.fillMaxWidth().clickable { expandido = !expandido },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${if (t.esConsulta) "🩺" else "📦"} ${t.procedimiento ?: "Tratamiento"}",
                    color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                // Especialidad + profesional en una línea sutil
                val sub = listOfNotNull(t.especialidadNombre, t.terapeutaNombre?.let { "con $it" }).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
            }
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(t.estado ?: "—", color = estado.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Text(if (expandido) "▴" else "▾", color = c.navy)
        }

        // Barra de recorrido del tratamiento (adaptativa por tipo). Solo si está vivo.
        if (t.estado == "Activo" || t.estado == "Completado" || t.estado == "Alta") {
            Spacer(Modifier.height(10.dp))
            BarraRecorrido(trat = t, consultaDone = consultaDone, evalDone = evalDone)
        }

        // ── Cuerpo adaptado al tipo ──
        if (t.esConsulta) {
            // CONSULTA (medicina/nutrición): diagnóstico + medicación + próximo control
            t.diagnostico?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp)); ChipInfo("📋", it, c.info, c.infoBg)
            }
            t.medicacion?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp)); ChipInfo("💊", it, c.purple, c.purpleBg)
            }
            t.proximoControl?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp)); ChipInfo("📅 Próximo control", it, c.navy, c.chipBg)
            }
        }

        // Acciones del tratamiento (⋯): editar / ampliar / suspender / cancelar / reactivar.
        if (puedeSesiones) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { menuTrat = !menuTrat }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("⋯ Opciones del tratamiento", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (menuTrat) {
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
                ) {
                    // (Agendar sesión está como botón visible arriba, no se repite aquí.)
                    ItemMenu("✏ Editar tratamiento", c.texto) { menuTrat = false; onEditar(t) }
                    if (!t.esConsulta) ItemMenu("➕ Ampliar (más sesiones)", c.navy) { menuTrat = false; onAmpliar(t) }
                    when (t.estado) {
                        "Activo" -> {
                            ItemMenu("⏸ Suspender", c.pend) { menuTrat = false; onCambiarEstadoTrat(t.id, "Suspendido") }
                            ItemMenu("✗ Cancelar", c.error) { menuTrat = false; onCambiarEstadoTrat(t.id, "Cancelado") }
                        }
                        "Suspendido", "Cancelado" ->
                            ItemMenu("↻ Reactivar", c.ok) { menuTrat = false; onCambiarEstadoTrat(t.id, "Activo") }
                    }
                }
            }
        }

        // Barra de progreso fina (el conteo N/M ya lo muestra el paso de la barra de recorrido).
        if (!t.esConsulta && t.totalSesiones > 0) {
            Spacer(Modifier.height(8.dp))
            val frac = (t.sesionesCompletadas.toFloat() / t.totalSesiones).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.chipBg)) {
                Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ok))
            }
        }

        // Acción más usada a la vista: agendar sesión (tratamientos por sesiones activos).
        if (!t.esConsulta && t.estado == "Activo" && puedeSesiones) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.navy.copy(alpha = 0.10f))
                    .border(1.dp, c.navy, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onCrearSesion(t) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("📅 + Agendar sesión", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

            // Las Consultas (especialidad sin sesiones) no listan sesiones; solo pagos.
            if (t.esConsulta) {
                Text("Consulta médica — sin sesiones.", color = c.textoSuave, fontSize = 12.sp)
                if (verPagos) {
                    Spacer(Modifier.height(Sania.dim.md))
                    SeccionPagos(t = t, esAdmin = esAdmin, recargaToken = cambioToken, onCambio = { recargarSesiones() })
                }
            } else when (val s = sesiones) {
                null -> Box(Modifier.fillMaxWidth().padding(Sania.dim.md), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                }
                else -> {
                    if (s.isEmpty()) {
                        Text("Sin sesiones registradas.", color = c.textoSuave, fontSize = 12.sp)
                    } else {
                        s.forEach { ses ->
                            FilaSesion(
                                ses = ses, verCosto = verPagos, puedeSesiones = puedeSesiones,
                                puedePagos = verPagos, esAdmin = esAdmin, accionando = accionando,
                                menuAbierto = menuDe?.id == ses.id,
                                onToggleMenu = { menuDe = if (menuDe?.id == ses.id) null else ses },
                                onCompletar = {
                                    // Sesión anterior (numero-1) como referencia de evolución en el modal.
                                    val anterior = s.filter { it.numero < ses.numero }.maxByOrNull { it.numero }
                                    onCompletarSesion(ses, anterior)
                                },
                                onEstado = { nuevo ->
                                    menuDe = null
                                    if (accionando) return@FilaSesion
                                    accionando = true
                                    scope.launch {
                                        PacientesRepo.cambiarEstadoSesion(ses.id, nuevo)
                                        accionando = false; recargarSesiones()
                                    }
                                },
                                onEditar = { editarSesion = ses; menuDe = null },
                                onRevertir = {
                                    menuDe = null
                                    if (accionando) return@FilaSesion
                                    accionando = true
                                    scope.launch { PacientesRepo.revertirSesion(ses.id); accionando = false; recargarSesiones() }
                                },
                                onBorrar = { borrarSesion = ses; menuDe = null },
                                onReasignar = { reasignarSesion = ses; menuDe = null },
                                onCobrar = { cobrarSesion = ses; menuDe = null },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // Pagos (solo con permiso): acordado/pagado/saldo + registrar/editar/borrar.
                    if (verPagos) {
                        Spacer(Modifier.height(Sania.dim.md))
                        SeccionPagos(t = t, esAdmin = esAdmin, recargaToken = cambioToken, onCambio = { recargarSesiones() })
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
      } // cierre Column del contenido
    } // cierre Row de la tarjeta (acento + contenido)

    // ── Modales de acciones de sesión ──
    editarSesion?.let { ses ->
        ModalEditarSesion(ses, onCancelar = { editarSesion = null }, onGuardar = { fecha, hora, dur, costo, notas ->
            editarSesion = null
            scope.launch { PacientesRepo.editarSesion(ses.id, fecha, hora, dur, costo, notas); recargarSesiones() }
        })
    }
    reasignarSesion?.let { ses ->
        ModalReasignar(onCancelar = { reasignarSesion = null }, onElegir = { terId ->
            reasignarSesion = null
            scope.launch { PacientesRepo.reasignarSesion(ses.id, terId); recargarSesiones() }
        })
    }
    cobrarSesion?.let { ses ->
        ModalCobrar(ses, onCancelar = { cobrarSesion = null }, onConfirmar = { monto, metodo, obs ->
            cobrarSesion = null
            // La nota incluye "Sesión #N" + observación, para que se vea el origen y el detalle.
            val nota = "Cobro Sesión #${ses.numero}" + (obs?.let { " · $it" } ?: "")
            scope.launch { PacientesRepo.cobrarSesion(t.id, ses.id, monto, metodo, nota); recargarSesiones() }
        })
    }
    borrarSesion?.let { ses ->
        ModalBorrarSesion(ses, onCancelar = { borrarSesion = null }, onBorrar = { borrarPagos ->
            borrarSesion = null
            scope.launch { PacientesRepo.borrarSesion(ses.id, borrarPagos); recargarSesiones() }
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaSesion(
    ses: SesionFicha,
    verCosto: Boolean,
    puedeSesiones: Boolean,
    puedePagos: Boolean,
    esAdmin: Boolean,
    accionando: Boolean,
    menuAbierto: Boolean,
    onToggleMenu: () -> Unit,
    onCompletar: () -> Unit,
    onEstado: (String) -> Unit,
    onEditar: () -> Unit,
    onRevertir: () -> Unit,
    onBorrar: () -> Unit,
    onReasignar: () -> Unit,
    onCobrar: () -> Unit,
) {
    val c = Sania.colors
    val estado = EstadosColor.sesion(ses.estado)
    val completada = ses.estado == "Completada"
    var expandida by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.fondo).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // Cabecera tocable → expande el detalle (procedimientos, mejorías, etc.)
        Row(
            Modifier.fillMaxWidth().clickable { expandida = !expandida },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sesión #${ses.numero}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${ses.fecha} ${ses.hora?.let { hora12(it) } ?: ""}".trim(), color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(ses.estado, color = estado.fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Text(if (expandida) "▴" else "▾", color = c.textoSuave, fontSize = 12.sp)
        }

        // Detalle expandido (igual que la web): procedimientos, mejorías, etc.
        if (expandida) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .padding(10.dp),
            ) {
                DetalleSesion("Estado", ses.estado, c.texto)
                ses.duracion?.let { DetalleSesion("Duración", "$it min", c.texto) }
                ses.terapeutaNombre?.let { DetalleSesion("Profesional", it, c.texto) }
                if (verCosto) DetalleSesion("Pago sesión",
                    if ((ses.costo ?: 0.0) > 0) "S/ ${formato2(ses.costo!!)}" else "Sin costo registrado", c.texto)
                ses.motivoEstado?.takeIf { it.isNotBlank() }?.let { DetalleSesion("Motivo", it, c.pend) }
                DetalleSesion("Procedimientos", ses.notas?.takeIf { it.isNotBlank() } ?: "Sin observaciones", c.texto)
                ses.mejorias?.takeIf { it.isNotBlank() }?.let { DetalleSesion("Mejorías", it, c.ok) }
            }
        }

        if (puedeSesiones) {
            Spacer(Modifier.height(8.dp))
            // Fila limpia: 1 acción principal + ✏ editar + ⋯ menú (resto). Como la web.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    ses.pendiente -> MiniBtn("✓ Completar", c.navy, !accionando) { onCompletar() }
                    // Completada: si ya tiene cobro → badge "✓ Pagada"; si no → "💳 Cobrar".
                    completada && puedePagos && ses.pagada -> Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.okBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("✓ Pagada", color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    completada && puedePagos -> MiniBtn("💳 Cobrar", c.teal, !accionando) { onCobrar() }
                }
                if (ses.pendiente) IconoBtn("✏", !accionando) { onEditar() }
                IconoBtn("⋯", !accionando) { onToggleMenu() }
            }
            // Menú desplegable: estados (si pendiente) + revertir (si completada) + reasignar + borrar.
            if (menuAbierto) {
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
                ) {
                    ItemMenu("✏ Editar sesión", c.texto) { onEditar() }
                    if (ses.pendiente) ESTADOS_SESION.forEach { e -> ItemMenu(e, c.texto) { onEstado(e) } }
                    if (completada) ItemMenu("↩ Revertir", c.pend) { onRevertir() }
                    ItemMenu("👤 Reasignar profesional", c.texto) { onReasignar() }
                    // Borrar es destructivo: solo Admin (igual criterio que borrar pagos).
                    if (esAdmin) ItemMenu("🗑 Borrar sesión", c.error) { onBorrar() }
                }
            }
        }
    }
}

/** Botón de icono compacto (✏ / ⋯) discreto. */
@Composable
private fun IconoBtn(icono: String, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(icono, color = c.textoSuave, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

/** Fila label:valor del detalle de una sesión. */
@Composable
private fun DetalleSesion(label: String, valor: String, colorValor: Color) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label:", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.width(96.dp))
        Text(valor, color = colorValor, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/** Chip de info clínica (diagnóstico/medicación/control) — para las Consultas. */
@Composable
private fun ChipInfo(icono: String, texto: String, fg: Color, bg: Color) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
        .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("$icono $texto", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** Fila de un menú desplegable. */
@Composable
private fun ItemMenu(texto: String, color: Color, onClick: () -> Unit) {
    Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp))
}

/** Métodos de pago (igual que la web). */
private val METODOS_PAGO = listOf("Efectivo", "Yape", "BCP", "Transferencia", "Otro")

/** Sección de pagos del tratamiento: resumen + lista + registrar (reusa endpoint). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionPagos(t: TratamientoPaciente, esAdmin: Boolean, recargaToken: Int, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var pagos by remember { mutableStateOf<List<pe.saniape.app.data.staff.PagoFicha>?>(null) }
    var agregando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var monto by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf("Efectivo") }
    var notaPago by remember { mutableStateOf("") }
    var editando by remember { mutableStateOf<String?>(null) }   // id del pago en edición
    var editMonto by remember { mutableStateOf("") }
    var editMetodo by remember { mutableStateOf("Efectivo") }
    var borrarId by remember { mutableStateOf<String?>(null) }   // confirmación de borrado

    suspend fun recargar() { pagos = runCatching { PacientesRepo.pagosDe(t.id) }.getOrDefault(pagos ?: emptyList()) }

    // Recarga al montar y cada vez que cambia recargaToken (tras cobrar una sesión).
    LaunchedEffect(t.id, recargaToken) {
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
                    // Origen del pago: amarrado a una sesión vs pago libre del tratamiento.
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.tealBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(p.numeroSesion?.let { "Sesión #$it" } ?: "Pago libre",
                            color = c.teal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                // Observación del pago (la ve quien cobre la próxima vez).
                p.notas?.takeIf { it.isNotBlank() }?.let {
                    Text("📝 $it", color = c.textoSuave, fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 2.dp))
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
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.OutlinedTextField(
            value = notaPago, onValueChange = { notaPago = it },
            placeholder = { Text("Observación (opcional)", color = c.textoSuave) },
            minLines = 1, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniBtn("Guardar pago", c.ok, !guardando) {
                val m = monto.toDoubleOrNull()
                if (m == null || m <= 0 || guardando) return@MiniBtn
                guardando = true
                scope.launch {
                    val ok = PacientesRepo.registrarPago(t.id, m, metodo, notaPago.trim().ifBlank { null })
                    guardando = false
                    if (ok) {
                        monto = ""; notaPago = ""; agregando = false
                        pagos = runCatching { PacientesRepo.pagosDe(t.id) }.getOrDefault(pagos ?: emptyList())
                        onCambio()
                    }
                }
            }
            MiniBtn("Cancelar", c.textoSuave, !guardando) { agregando = false; monto = ""; notaPago = "" }
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

// ── Modales de acciones de sesión ──

@Composable
private fun ModalEditarSesion(
    ses: SesionFicha,
    onCancelar: () -> Unit,
    onGuardar: (fecha: String, hora: String?, duracion: Int, costo: Double?, notas: String?) -> Unit,
) {
    val c = Sania.colors
    var fecha by remember { mutableStateOf(ses.fecha) }
    var hora by remember { mutableStateOf(ses.hora?.take(5) ?: "") }
    var costo by remember { mutableStateOf(ses.costo?.let { formato2(it) } ?: "") }
    var notas by remember { mutableStateOf(ses.notas ?: "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("✏ Editar sesión #${ses.numero}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CampoTexto("Fecha (AAAA-MM-DD)", fecha) { fecha = it }
                Spacer(Modifier.height(8.dp))
                CampoTexto("Hora (HH:MM)", hora) { hora = it }
                Spacer(Modifier.height(8.dp))
                CampoTexto("Costo (S/)", costo, soloNumero = true) { costo = it }
                Spacer(Modifier.height(8.dp))
                CampoTexto("Notas / procedimientos", notas, multilinea = true) { notas = it }
            }
        },
        confirmButton = {
            BotonModalP("Guardar") {
                onGuardar(fecha.trim(), hora.trim().ifBlank { null }, ses.duracion ?: 45, costo.toDoubleOrNull(), notas.trim().ifBlank { null })
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@Composable
private fun ModalReasignar(onCancelar: () -> Unit, onElegir: (String) -> Unit) {
    val c = Sania.colors
    var terapeutas by remember { mutableStateOf<List<pe.saniape.app.data.staff.RefNombre>>(emptyList()) }
    LaunchedEffect(Unit) { terapeutas = runCatching { PacientesRepo.terapeutasActivos() }.getOrDefault(emptyList()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("👤 Reasignar profesional", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (terapeutas.isEmpty()) Text("Cargando…", color = c.textoSuave, fontSize = 12.sp)
                terapeutas.forEach { ter ->
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { onElegir(ter.id) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    ) { Text(ter.nombre, color = c.texto, fontSize = Sania.txt.cuerpo) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModalCobrar(ses: SesionFicha, onCancelar: () -> Unit, onConfirmar: (Double, String, String?) -> Unit) {
    val c = Sania.colors
    var monto by remember { mutableStateOf(ses.costo?.let { formato2(it) } ?: "") }
    var metodo by remember { mutableStateOf("Efectivo") }
    var obs by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("💳 Cobrar sesión #${ses.numero}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CampoTexto("Monto (S/)", monto, soloNumero = true) { monto = it }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    METODOS_PAGO.forEach { m -> ChipMetodo(m, metodo == m) { metodo = m } }
                }
                Spacer(Modifier.height(8.dp))
                CampoTexto("Observación (opcional)", obs, multilinea = true) { obs = it }
                Text("La verá quien cobre la próxima vez (ej. compromiso del paciente).",
                    color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        },
        confirmButton = {
            BotonModalP("Registrar pago") {
                monto.toDoubleOrNull()?.takeIf { it > 0 }?.let { onConfirmar(it, metodo, obs.trim().ifBlank { null }) }
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@Composable
private fun ModalBorrarSesion(ses: SesionFicha, onCancelar: () -> Unit, onBorrar: (borrarPagos: Boolean) -> Unit) {
    val c = Sania.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("🗑 Borrar sesión #${ses.numero}", fontWeight = FontWeight.Bold) },
        text = {
            Text("¿Seguro? Si la sesión tiene pagos, elige qué hacer con ellos.",
                color = c.texto, fontSize = Sania.txt.cuerpo)
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                BotonModalP("Borrar (conservar pagos)") { onBorrar(false) }
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.TextButton(onClick = { onBorrar(true) }) {
                    Text("Borrar también el pago", color = c.error, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@Composable
private fun CampoTexto(
    label: String, value: String, soloNumero: Boolean = false, multilinea: Boolean = false, onChange: (String) -> Unit,
) {
    val c = Sania.colors
    Text(label, color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { if (soloNumero) onChange(it.filter { ch -> ch.isDigit() || ch == '.' }) else onChange(it) },
        singleLine = !multilinea, minLines = if (multilinea) 2 else 1,
        keyboardOptions = if (soloNumero) androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else androidx.compose.foundation.text.KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun BotonModalP(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
            .clickable { onClick() }.padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(texto, color = c.sobreNavy, fontWeight = FontWeight.Bold) }
}
