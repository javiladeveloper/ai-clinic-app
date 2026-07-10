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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.SesionGlobal
import pe.saniape.app.data.staff.SesionesRepo
import pe.saniape.app.ui.clinica.agenda.hoyIso
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.TarjetaForm
import pe.saniape.app.ui.theme.Sania

/**
 * Módulo Sesiones (lista global, fuera de la ficha). Espeja `app/(app)/sesiones`
 * de la web: stats (Completadas/En progreso/Planificadas), filtros (búsqueda/estado/
 * servicio/fecha/profesional) y acciones (✓ Completar, ⋯ estados/reasignar, revertir/
 * reactivar). Respeta el scope del profesional (solo sus sesiones) y reusa los
 * endpoints de /api/staff/sesion vía PacientesRepo (misma lógica que la ficha y la web).
 *
 * [onAbrirPaciente] permite tocar el nombre del paciente para ir a su ficha (opcional).
 */
@Composable
fun PantallaSesiones(
    ctx: ContextoStaff,
    onAbrirPaciente: ((pacienteId: String) -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    var cargando by remember { mutableStateOf(true) }
    var cargaFallo by remember { mutableStateOf(false) }
    var sesiones by remember { mutableStateOf<List<SesionGlobal>>(emptyList()) }
    var profesionales by remember { mutableStateOf<List<RefNombre>>(emptyList()) }
    var intento by remember { mutableStateOf(0) }
    // Anti-doble-tap: mientras una acción de sesión corre, no se dispara otra.
    var accionando by remember { mutableStateOf(false) }

    // Filtros
    var busqueda by remember { mutableStateOf("") }
    var filtroEstado by remember { mutableStateOf<String?>(null) }
    var filtroServicio by remember { mutableStateOf<String?>(null) }
    var filtroFecha by remember { mutableStateOf<String?>(null) }
    // "Mi agenda" por defecto si el usuario está vinculado a un profesional.
    var filtroProf by remember { mutableStateOf(ctx.miTerapeutaId) }

    // Modales
    var completar by remember { mutableStateOf<SesionGlobal?>(null) }
    var cambioEstado by remember { mutableStateOf<Pair<SesionGlobal, String>?>(null) }
    var reasignar by remember { mutableStateOf<SesionGlobal?>(null) }

    fun recargar() {
        scope.launch {
            cargando = true; cargaFallo = false
            val r = runCatching { SesionesRepo.listar(soloTerapeutaId = ctx.miTerapeutaId) }
            r.onSuccess { sesiones = it }.onFailure { cargaFallo = true }
            cargando = false
        }
    }

    // Ejecuta una acción de sesión con anti-doble-tap + toast de éxito/error + recarga.
    fun accion(exito: String, bloque: suspend () -> Boolean) {
        if (accionando) return
        accionando = true
        scope.launch {
            val ok = runCatching { bloque() }.getOrDefault(false)
            if (ok) pe.saniape.app.ui.Toaster.exito(exito)
            else pe.saniape.app.ui.Toaster.error("No se pudo completar la acción")
            accionando = false
            recargar()
        }
    }

    LaunchedEffect(intento) {
        recargar()
        if (ctx.esGestor) {
            profesionales = runCatching { PacientesRepo.terapeutasActivos() }.getOrDefault(emptyList())
        }
    }

    // Stats sobre el conjunto YA acotado por scope (lo que devuelve el repo).
    val completadas = sesiones.count { it.estado == "Completada" }
    val enProgreso = sesiones.count { it.estado == "En progreso" }
    val planificadas = sesiones.count { it.estado == "Planificada" }

    val servicios = remember(sesiones) { SesionesRepo.serviciosDe(sesiones) }

    val filtradas = remember(sesiones, busqueda, filtroEstado, filtroServicio, filtroFecha, filtroProf) {
        val q = busqueda.trim().lowercase()
        sesiones.filter { s ->
            (q.isBlank() ||
                (s.pacienteNombre?.lowercase()?.contains(q) == true) ||
                (s.terapeutaNombre?.lowercase()?.contains(q) == true)) &&
                (filtroEstado == null || s.estado == filtroEstado) &&
                (filtroServicio == null || s.procedimiento == filtroServicio) &&
                (filtroFecha == null || s.fecha == filtroFecha) &&
                (filtroProf == null || s.terapeutaId == filtroProf)
        }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header navy
            Box(Modifier.fillMaxWidth().background(c.navyDark)
                .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg)) {
                Column {
                    Text("Sesiones", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Text("${sesiones.size} sesiones registradas", color = c.sobreNavy.copy(alpha = 0.7f),
                        fontSize = Sania.txt.pequeno)
                }
            }

            if (cargando) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                return@Column
            }
            if (cargaFallo) {
                Box(Modifier.fillMaxSize().padding(Sania.dim.xxl), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠", fontSize = 40.sp)
                        Text("No se pudieron cargar las sesiones.", color = c.error,
                            fontSize = Sania.txt.cuerpo, modifier = Modifier.padding(vertical = 10.dp))
                        Box(Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                            .clickable { intento++ }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                            Text("Reintentar", color = c.sobreNavy, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                return@Column
            }

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Sania.dim.lg),
                verticalArrangement = Arrangement.spacedBy(Sania.dim.sm),
            ) {
                // Stats 3-col
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
                        StatSesion("✅", completadas, "Completadas", c.ok, Modifier.weight(1f))
                        StatSesion("🔄", enProgreso, "En progreso", c.info, Modifier.weight(1f))
                        StatSesion("📋", planificadas, "Planificadas", c.navy, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Sania.dim.sm))
                }

                // Filtros
                item {
                    OutlinedTextField(
                        value = busqueda, onValueChange = { busqueda = it },
                        placeholder = { Text("Buscar por paciente o profesional…", color = c.textoSuave) },
                        leadingIcon = { Text("🔍") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    Spacer(Modifier.height(Sania.dim.sm))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FiltroChip("Estado", filtroEstado, ESTADOS, { filtroEstado = it }, Modifier.weight(1f))
                        FiltroChip("Servicio", filtroServicio, servicios, { filtroServicio = it }, Modifier.weight(1f))
                    }
                    if (ctx.esGestor && profesionales.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FiltroProfesional(filtroProf, profesionales, ctx.miTerapeutaId) { filtroProf = it }
                    }
                    if (filtroFecha != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                                .clickable { filtroFecha = null }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text("📅 ${filtroFecha}  ✕", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(Sania.dim.xs))
                }

                if (filtradas.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✨", fontSize = 34.sp)
                                Text(
                                    if (busqueda.isNotBlank() || filtroEstado != null || filtroServicio != null || filtroFecha != null)
                                        "No se encontraron sesiones con esos filtros"
                                    else "No hay sesiones registradas",
                                    color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                } else {
                    items(filtradas, key = { it.id }) { s ->
                        TarjetaSesion(
                            s = s, ctx = ctx,
                            onAbrirPaciente = onAbrirPaciente,
                            onCompletar = {
                                accion("Sesión completada") { PacientesRepo.cambiarEstadoSesion(s.id, "Completada") }
                            },
                            onEstado = { est -> cambioEstado = s to est },
                            onReasignar = { reasignar = s },
                            onRevertir = {
                                accion("Sesión reabierta") { PacientesRepo.cambiarEstadoSesion(s.id, "Planificada") }
                            },
                            onReactivar = {
                                accion("Sesión reactivada") { PacientesRepo.cambiarEstadoSesion(s.id, "Planificada", motivo = "") }
                            },
                        )
                    }
                }
            }
        }
    }

    // ── Modal: cambio de estado / reprogramar ──
    cambioEstado?.let { (ses, est) ->
        ModalEstadoSesion(
            ses = ses, estado = est,
            onCancelar = { cambioEstado = null },
            onConfirmar = { motivo, fecha, hora ->
                cambioEstado = null
                accion("Sesión actualizada") { PacientesRepo.cambiarEstadoSesion(ses.id, est, motivo = motivo, fecha = fecha, hora = hora) }
            },
        )
    }

    // ── Modal: reasignar profesional ──
    reasignar?.let { ses ->
        ModalReasignarProfesional(
            ses = ses, profesionales = profesionales,
            onCancelar = { reasignar = null },
            onConfirmar = { profId ->
                reasignar = null
                accion("Profesional reasignado") { PacientesRepo.reasignarSesion(ses.id, profId) }
            },
        )
    }
}

private val ESTADOS = listOf(
    "Completada", "En progreso", "Planificada", "Reprogramada", "No asistió", "Cancelada", "Otro",
)

// ── Stat card compacta ──
@Composable
private fun StatSesion(icono: String, valor: Int, etq: String, color: Color, modifier: Modifier = Modifier) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
    ) {
        Text(icono, fontSize = 18.sp)
        Text("$valor", color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(etq, color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Filtro tipo dropdown (con opción "Todos") ──
@Composable
private fun FiltroChip(
    titulo: String, seleccion: String?, opciones: List<String>,
    onSeleccion: (String?) -> Unit, modifier: Modifier = Modifier,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(if (seleccion != null) c.chipBg else c.superficie)
                .border(1.dp, if (seleccion != null) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { abierto = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                seleccion ?: titulo,
                color = if (seleccion != null) c.navy else c.textoSuave,
                fontSize = 12.sp, fontWeight = if (seleccion != null) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Text("▾", color = c.textoSuave, fontSize = 11.sp)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { onSeleccion(null); abierto = false })
            opciones.forEach { op ->
                DropdownMenuItem(text = { Text(op) }, onClick = { onSeleccion(op); abierto = false })
            }
        }
    }
}

@Composable
private fun FiltroProfesional(
    seleccion: String?, profesionales: List<RefNombre>, miTerapeutaId: String?,
    onSeleccion: (String?) -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    val nombreSel = profesionales.find { it.id == seleccion }?.nombre
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(if (seleccion != null) c.chipBg else c.superficie)
                .border(1.dp, if (seleccion != null) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { abierto = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                nombreSel?.let { if (seleccion == miTerapeutaId) "👤 Mi agenda — $it" else it } ?: "Todo el personal",
                color = if (seleccion != null) c.navy else c.textoSuave, fontSize = 12.sp,
                fontWeight = if (seleccion != null) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Text("▾", color = c.textoSuave, fontSize = 11.sp)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Todo el personal") }, onClick = { onSeleccion(null); abierto = false })
            profesionales.forEach { p ->
                DropdownMenuItem(
                    text = { Text(if (p.id == miTerapeutaId) "👤 Mi agenda — ${p.nombre}" else p.nombre) },
                    onClick = { onSeleccion(p.id); abierto = false },
                )
            }
        }
    }
}

// ── Tarjeta de sesión ──
@Composable
private fun TarjetaSesion(
    s: SesionGlobal,
    ctx: ContextoStaff,
    onAbrirPaciente: ((String) -> Unit)?,
    onCompletar: () -> Unit,
    onEstado: (String) -> Unit,
    onReasignar: () -> Unit,
    onRevertir: () -> Unit,
    onReactivar: () -> Unit,
) {
    val c = Sania.colors
    var menu by remember { mutableStateOf(false) }
    val (badgeFg, badgeBg) = colorEstado(s.estado, c)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // # de sesión
            Box(Modifier.width(34.dp), Alignment.CenterStart) {
                Text(
                    "#${s.numero}", color = if (s.estado == "Cancelada" || s.estado == "No asistió") c.textoSuave else c.navy,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                val nombre = s.pacienteNombre ?: "—"
                if (onAbrirPaciente != null && s.pacienteId != null) {
                    Text(nombre, color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onAbrirPaciente(s.pacienteId) })
                } else {
                    Text(nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.fecha + (s.hora?.let { " · ${it.take(5)}" } ?: ""),
                        color = c.textoSuave, fontSize = 11.sp)
                    s.duracion?.let {
                        Text(" · $it min", color = c.textoSuave, fontSize = 11.sp)
                    }
                }
            }
            // Badge estado
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(badgeBg)
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(s.estado, color = badgeFg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            s.procedimiento?.let {
                Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.tealBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(it, color = c.teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
            s.terapeutaNombre?.let {
                Text(it, color = c.textoSuave, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } ?: Spacer(Modifier.weight(1f))
            // Costo (solo con permiso de pagos y si no es paquete)
            if (ctx.puede("pagos")) {
                val costo = s.costoMostrar
                if (costo != null) {
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.tealBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("S/ ${fmt(costo)}", color = c.teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Motivo de estado (si lo hay)
        s.motivoEstado?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        // Acciones — solo si tiene permiso de sesiones
        if (ctx.puede("sesiones")) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s.pendiente) {
                    BotonAccion("✓ Completar", c.navy, c.sobreNavy, onClick = onCompletar)
                    Box {
                        BotonAccion("⋯", c.superficie, c.texto, borde = true) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("👤 Cambiar profesional", color = c.navy, fontWeight = FontWeight.Bold) },
                                onClick = { menu = false; onReasignar() },
                            )
                            DropdownMenuItem(text = { Text("📅 Reprogramar") }, onClick = { menu = false; onEstado("Reprogramada") })
                            DropdownMenuItem(text = { Text("🚫 No asistió") }, onClick = { menu = false; onEstado("No asistió") })
                            DropdownMenuItem(text = { Text("✕ Canceló") }, onClick = { menu = false; onEstado("Cancelada") })
                            DropdownMenuItem(text = { Text("⚪ Otro") }, onClick = { menu = false; onEstado("Otro") })
                        }
                    }
                }
                if (s.estado == "Completada") {
                    BotonAccion("↩ Revertir", c.superficie, c.texto, borde = true, onClick = onRevertir)
                }
                if (s.estado == "Cancelada" || s.estado == "No asistió" || s.estado == "Otro") {
                    BotonAccion("↩ Reactivar", c.superficie, c.texto, borde = true, onClick = onReactivar)
                }
            }
        }
    }
}

@Composable
private fun BotonAccion(
    texto: String, fondo: Color, contenido: Color, borde: Boolean = false, onClick: () -> Unit,
) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(fondo)
            .then(if (borde) Modifier.border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)) else Modifier)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(texto, color = contenido, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

private fun colorEstado(estado: String, c: pe.saniape.app.ui.theme.SaniaColors): Pair<Color, Color> = when (estado) {
    "Completada" -> c.ok to c.okBg
    "En progreso" -> c.info to c.infoBg
    "Planificada" -> c.navy to c.chipBg
    "Reprogramada" -> c.pend to c.pendBg
    "No asistió", "Cancelada" -> c.error to c.errorBg
    else -> c.textoSuave to c.chipBg
}

private fun fmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else ((v * 100).toLong() / 100.0).toString()

// ── Modal de estado (Reprogramar / No asistió / Cancelar / Otro) ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalEstadoSesion(
    ses: SesionGlobal, estado: String,
    onCancelar: () -> Unit,
    onConfirmar: (motivo: String?, fecha: String?, hora: String?) -> Unit,
) {
    val c = Sania.colors
    val esReprog = estado == "Reprogramada"
    var motivo by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(ses.fecha.ifBlank { hoyIso() }) }
    var hora by remember { mutableStateOf(ses.hora?.take(5) ?: "09:00") }
    var mostrarFecha by remember { mutableStateOf(false) }

    if (mostrarFecha) {
        val estadoP = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoP.selectedDateMillis?.let { ms ->
                        val d = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                        fecha = "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
                    }
                    mostrarFecha = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estadoP) }
    }

    DialogoForm(
        titulo = if (esReprog) "📅 Reprogramar sesión" else "Marcar como \"$estado\"",
        subtitulo = "Sesión #${ses.numero} · ${ses.pacienteNombre ?: ""}",
        textoAccion = "Confirmar",
        accionHabilitada = !esReprog || fecha.isNotBlank(),
        onCancelar = onCancelar,
        onAccion = { onConfirmar(motivo.trim().ifBlank { null }, if (esReprog) fecha else null, if (esReprog) hora else null) },
    ) {
        if (esReprog) {
            TarjetaForm(titulo = "Nueva fecha y hora", icono = "📅") {
                EtqForm("Fecha")
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { mostrarFecha = true }.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Text(fecha, color = c.texto, fontSize = 14.sp)
                }
                Spacer(Modifier.height(10.dp))
                EtqForm("Hora")
                OutlinedTextField(
                    value = hora, onValueChange = { hora = it },
                    placeholder = { Text("09:00", color = c.textoSuave) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        TarjetaForm(titulo = "Motivo" + if (esReprog) " (opcional)" else "", icono = "📝") {
            OutlinedTextField(
                value = motivo, onValueChange = { motivo = it },
                placeholder = { Text("Ej. El paciente pidió cambiar la fecha…", color = c.textoSuave) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
        }
    }
}

// ── Modal reasignar profesional ──
@Composable
private fun ModalReasignarProfesional(
    ses: SesionGlobal, profesionales: List<RefNombre>,
    onCancelar: () -> Unit, onConfirmar: (String) -> Unit,
) {
    val c = Sania.colors
    var profId by remember { mutableStateOf(ses.terapeutaId ?: "") }
    var abierto by remember { mutableStateOf(false) }
    val nombreSel = profesionales.find { it.id == profId }?.nombre

    DialogoForm(
        titulo = "👤 Cambiar profesional",
        subtitulo = "Sesión #${ses.numero} · ${ses.fecha}",
        textoAccion = "Confirmar",
        accionHabilitada = profId.isNotBlank() && profId != ses.terapeutaId,
        onCancelar = onCancelar,
        onAccion = { onConfirmar(profId) },
    ) {
        Text("El nuevo profesional la verá en su agenda y el anterior dejará de verla.",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        TarjetaForm(titulo = "Profesional", icono = "👤") {
            Box {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable { abierto = true }.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(nombreSel ?: "Selecciona un profesional…",
                        color = if (nombreSel != null) c.texto else c.textoSuave, fontSize = 14.sp)
                    Text("▾", color = c.textoSuave)
                }
                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    profesionales.forEach { p ->
                        DropdownMenuItem(text = { Text(p.nombre) }, onClick = { profId = p.id; abierto = false })
                    }
                }
            }
        }
    }
}
