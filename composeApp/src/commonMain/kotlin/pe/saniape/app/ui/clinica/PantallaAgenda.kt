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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.AgendaBanners
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.BannersAgenda
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.Derivacion
import pe.saniape.app.data.staff.EspecialidadRef
import pe.saniape.app.ui.theme.Sania

/**
 * Agenda del staff: tira de días + citas del día con acciones (confirmar, completar,
 * cancelar). Respeta el scope (si es profesional vinculado, solo sus citas).
 */
@Composable
fun PantallaAgenda(ctx: ContextoStaff) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    val hoy = remember { hoyIso() }
    var fechaSel by remember { mutableStateOf(hoy) }
    var cargando by remember { mutableStateOf(true) }
    var citas by remember { mutableStateOf<List<CitaStaff>>(emptyList()) }
    var especialidades by remember { mutableStateOf<List<EspecialidadRef>>(emptyList()) }
    var banners by remember { mutableStateOf<BannersAgenda?>(null) }
    var accionando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Diálogos / sub-pantallas
    var completarCita by remember { mutableStateOf<CitaStaff?>(null) }    // modal completar (por tipo)
    var confirmarCita by remember { mutableStateOf<Pair<CitaStaff, String>?>(null) } // (cita, accion) cancelar/revertir
    var creandoCita by remember { mutableStateOf(false) }                  // pantalla de nueva cita

    suspend fun recargar() {
        cargando = true
        try { citas = AgendaRepo.citasDelDia(fechaSel, ctx.miTerapeutaId) }
        catch (_: Exception) { citas = emptyList() }
        cargando = false
    }

    LaunchedEffect(fechaSel) { recargar() }
    LaunchedEffect(Unit) {
        try { especialidades = AgendaRepo.especialidades() } catch (_: Exception) {}
        try {
            banners = AgendaBanners.cargar(hoy, mananaIso(hoy), ctx.miTerapeutaId, ctx.esGestor)
        } catch (_: Exception) {}
    }

    suspend fun ejecutar(
        accion: String, cita: CitaStaff,
        observaciones: String? = null, diagnostico: String? = null, derivarEspId: String? = null,
    ) {
        accionando = true; mensaje = null
        val ok = when (accion) {
            "confirmar" -> AgendaRepo.confirmar(cita.id)
            "completar" -> AgendaRepo.completar(cita.id, observaciones, diagnostico, derivarEspId)
            "revertir" -> AgendaRepo.revertir(cita.id)
            "cancelar" -> AgendaRepo.cancelar(cita.id)
            else -> false
        }
        mensaje = if (ok) "✓ Listo" else "⚠ No se pudo, intenta de nuevo"
        recargar()
        accionando = false
    }

    // Pantalla de crear cita (solo si tiene permiso de citas).
    if (creandoCita) {
        PantallaCrearCita(
            ctx = ctx, fechaInicial = fechaSel,
            onListo = { creandoCita = false; scope.launch { recargar() } },
            onCancelar = { creandoCita = false },
        )
        return
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra con botón "+ Nueva"
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Agenda", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                        .background(c.sobreNavy.copy(alpha = 0.15f))
                        .clickable { creandoCita = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) { Text("+ Nueva", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }

            // Tira de días: desde 2 días atrás hasta +12 (igual que la web).
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = Sania.dim.sm),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Sania.dim.lg),
            ) {
                items(diasDesde(hoy, -2, 15)) { dia ->
                    val activo = dia.iso == fechaSel
                    Column(
                        Modifier.size(width = 52.dp, height = 64.dp)
                            .clip(RoundedCornerShape(Sania.shape.md.dp))
                            .background(if (activo) c.navy else c.superficie)
                            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.md.dp))
                            .clickable { fechaSel = dia.iso }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(dia.diaSemana, color = if (activo) c.sobreNavy else c.textoSuave, fontSize = 11.sp)
                        Text(dia.diaMes, color = if (activo) c.sobreNavy else c.texto,
                            fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            mensaje?.let {
                Text(it, color = c.navy, fontSize = Sania.txt.pequeno,
                    modifier = Modifier.padding(horizontal = Sania.dim.lg, vertical = 4.dp))
            }

            // ── Banners (mañana / vencidas / derivaciones) ──
            banners?.let { b ->
                BannersAgendaUI(
                    banners = b,
                    onVerCitaManana = { c2 -> fechaSel = c2.fecha },
                    onCerrarVencida = { c2, vino ->
                        scope.launch {
                            if (vino) {
                                if (c2.tipo == "Evaluación" || c2.tipo == "Sesión") completarCita = c2
                                else ejecutar("completar", c2)
                            } else ejecutar("cancelar", c2)
                            try { banners = AgendaBanners.cargar(hoy, mananaIso(hoy), ctx.miTerapeutaId, ctx.esGestor) } catch (_: Exception) {}
                        }
                    },
                    onAgendarDerivacion = { creandoCita = true },
                    onMarcarDerivacion = { d ->
                        scope.launch {
                            if (AgendaBanners.marcarDerivacion(d.id)) {
                                banners = banners?.copy(derivaciones = banners!!.derivaciones.filter { it.id != d.id })
                            }
                        }
                    },
                )
            }

            when {
                cargando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                citas.isEmpty() -> Box(Modifier.fillMaxSize().padding(Sania.dim.xxl), Alignment.Center) {
                    Text("No hay citas para este día.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Sania.dim.md),
                ) {
                    items(citas) { cita ->
                        TarjetaCitaStaff(
                            cita = cita,
                            accionando = accionando,
                            onAccion = { accion ->
                                if (accionando) return@TarjetaCitaStaff
                                when (accion) {
                                    "confirmar" -> scope.launch { ejecutar("confirmar", cita) }
                                    "completar" ->
                                        if (cita.tipo == "Evaluación" || cita.tipo == "Sesión") completarCita = cita
                                        else scope.launch { ejecutar("completar", cita) }
                                    "cancelar" -> confirmarCita = cita to "cancelar"
                                    "revertir" -> confirmarCita = cita to "revertir"
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // ── Modal: completar Evaluación/Sesión ──
    completarCita?.let { cita ->
        ModalCompletar(
            cita = cita,
            especialidades = especialidades,
            onCancelar = { completarCita = null },
            onConfirmar = { observaciones, diagnostico, derivarEspId ->
                completarCita = null
                scope.launch { ejecutar("completar", cita, observaciones, diagnostico, derivarEspId) }
            },
        )
    }

    // ── Confirmación: cancelar / revertir ──
    confirmarCita?.let { (cita, accion) ->
        val esCancelar = accion == "cancelar"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmarCita = null },
            title = { Text(if (esCancelar) "¿Cancelar esta cita?" else "¿Revertir esta cita?") },
            text = {
                Text(
                    if (esCancelar) {
                        if (cita.tipo == "Sesión") "Se eliminará la sesión vinculada."
                        else "La cita quedará como cancelada."
                    } else "Volverá a confirmada y se deshará el cobro/registro asociado.",
                    color = c.texto, fontSize = Sania.txt.cuerpo,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmarCita = null
                    scope.launch { ejecutar(accion, cita) }
                }) {
                    Text(if (esCancelar) "Sí, cancelar" else "Sí, revertir",
                        color = if (esCancelar) c.error else c.pend, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmarCita = null }) {
                    Text("No", color = c.textoSuave)
                }
            },
            containerColor = c.superficie,
        )
    }
}

@Composable
private fun TarjetaCitaStaff(cita: CitaStaff, accionando: Boolean, onAccion: (String) -> Unit) {
    val c = Sania.colors
    val colorTipo = when (cita.tipo) {
        "Evaluación" -> c.info
        "Sesión" -> c.ok
        else -> c.navy
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colorTipo))
            Spacer(Modifier.width(8.dp))
            Text(cita.hora.take(5), color = c.navy, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            BadgeEstadoCita(cita.estado)
        }
        Spacer(Modifier.height(6.dp))
        Text(cita.pacienteNombre ?: "Paciente", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(cita.tipo, cita.procedimiento, cita.terapeutaNombre?.let { "con $it" })
                .joinToString(" · "),
            color = c.textoSuave, fontSize = 12.sp,
        )

        // Acciones según estado
        val acciones = accionesPara(cita.estado)
        if (acciones.isNotEmpty()) {
            Spacer(Modifier.height(Sania.dim.md))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                acciones.forEach { (label, accion, color) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(color.copy(alpha = 0.12f))
                            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable(enabled = !accionando) { onAccion(accion) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Acciones disponibles según el estado de la cita: (label, accion, color). */
@Composable
private fun accionesPara(estado: String): List<Triple<String, String, Color>> {
    val c = Sania.colors
    return when (estado) {
        "Pendiente" -> listOf(
            Triple("✓ Confirmar", "confirmar", c.ok),
            Triple("✓ Completar", "completar", c.navy),
            Triple("✕ Cancelar", "cancelar", c.error),
        )
        "Confirmada" -> listOf(
            Triple("✓ Completar", "completar", c.navy),
            Triple("✕ Cancelar", "cancelar", c.error),
        )
        "Completada" -> listOf(
            Triple("↩ Revertir", "revertir", c.pend),
        )
        else -> emptyList()
    }
}

/**
 * Modal de completar según tipo:
 *  - Evaluación: diagnóstico + opción de derivar a especialidad.
 *  - Sesión: observaciones (procedimientos realizados).
 */
@Composable
private fun ModalCompletar(
    cita: CitaStaff,
    especialidades: List<EspecialidadRef>,
    onCancelar: () -> Unit,
    onConfirmar: (observaciones: String?, diagnostico: String?, derivarEspId: String?) -> Unit,
) {
    val c = Sania.colors
    val esEvaluacion = cita.tipo == "Evaluación"
    var texto by remember { mutableStateOf("") }
    var derivar by remember { mutableStateOf(false) }
    var espElegida by remember { mutableStateOf<EspecialidadRef?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (esEvaluacion) "✓ Completar evaluación" else "✓ Completar sesión") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text(if (esEvaluacion) "Diagnóstico" else "Procedimientos realizados") },
                    placeholder = {
                        Text(
                            if (esEvaluacion) "Ej. Lumbalgia mecánica…" else "¿Qué se hizo en la sesión?",
                            color = c.textoSuave,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                if (esEvaluacion && especialidades.size > 1) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { derivar = !derivar }) {
                        Text(if (derivar) "☑" else "☐", fontSize = 18.sp, color = c.navy)
                        Spacer(Modifier.width(6.dp))
                        Text("↗ Derivar a otra especialidad", color = c.texto, fontSize = Sania.txt.cuerpo)
                    }
                    if (derivar) {
                        Spacer(Modifier.height(Sania.dim.sm))
                        especialidades.forEach { esp ->
                            val activa = espElegida?.id == esp.id
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .background(if (activa) c.chipBg else c.superficie)
                                    .border(1.dp, if (activa) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                    .clickable { espElegida = if (activa) null else esp }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(esp.nombre, color = if (activa) c.navy else c.texto, fontSize = Sania.txt.pequeno,
                                    fontWeight = if (activa) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onConfirmar(
                    texto.trim().ifBlank { null },
                    if (esEvaluacion) texto.trim().ifBlank { null } else null,
                    if (esEvaluacion && derivar) espElegida?.id else null,
                )
            }) { Text("Guardar y completar", color = c.navy, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) }
        },
        containerColor = c.superficie,
    )
}

// ── Helpers de fecha ──
private data class DiaTira(val iso: String, val diaSemana: String, val diaMes: String)

// Indexado por dayOfWeek.ordinal: MONDAY=0 .. SUNDAY=6
private val DIAS_ES = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

private fun hoyIso(): String {
    val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}

private fun mananaIso(hoy: String): String {
    val p = hoy.split("-")
    val d = kotlinx.datetime.LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt()).plus(DatePeriod(days = 1))
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}

private fun diasDesde(isoInicio: String, offset: Int, n: Int): List<DiaTira> {
    val partes = isoInicio.split("-")
    var fecha = kotlinx.datetime.LocalDate(partes[0].toInt(), partes[1].toInt(), partes[2].toInt())
        .plus(DatePeriod(days = offset))
    val lista = mutableListOf<DiaTira>()
    repeat(n) {
        lista.add(DiaTira(
            iso = "${fecha.year}-${fecha.monthNumber.toString().padStart(2, '0')}-${fecha.dayOfMonth.toString().padStart(2, '0')}",
            diaSemana = DIAS_ES[fecha.dayOfWeek.ordinal],
            diaMes = fecha.dayOfMonth.toString(),
        ))
        fecha = fecha.plus(DatePeriod(days = 1))
    }
    return lista
}