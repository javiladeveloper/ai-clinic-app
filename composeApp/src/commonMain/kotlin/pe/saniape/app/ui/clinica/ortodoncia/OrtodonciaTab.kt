package pe.saniape.app.ui.clinica.ortodoncia

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ARCADAS_ORTO
import pe.saniape.app.data.staff.CADENETAS_ORTO
import pe.saniape.app.data.staff.CitaOrto
import pe.saniape.app.data.staff.ControlOrtodoncia
import pe.saniape.app.data.staff.CuotasTratamientoOrto
import pe.saniape.app.data.staff.EstadoCuota
import pe.saniape.app.data.staff.DOLORES_ORTO
import pe.saniape.app.data.staff.MapaDental
import pe.saniape.app.data.staff.MapaOrtodoncia
import pe.saniape.app.data.staff.NIVELES_ORTO
import pe.saniape.app.data.staff.OrtodonciaRepo
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.PlanOrtodoncia
import pe.saniape.app.data.staff.RETENCIONES_ORTO
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.TratamientoOrto
import pe.saniape.app.data.staff.aparatologiaOrtoPorId
import pe.saniape.app.data.staff.estadoControles
import pe.saniape.app.data.staff.estadosFases
import pe.saniape.app.data.staff.faseEsperada
import pe.saniape.app.data.staff.faseOrtoPorId
import pe.saniape.app.data.staff.fechaIndicacion
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.htmlFichaOrtodoncia
import pe.saniape.app.data.staff.nombreOrto
import pe.saniape.app.data.staff.normalizarElasticos
import pe.saniape.app.data.staff.ofreceCuidadosBrackets
import pe.saniape.app.data.staff.ordenarControles
import pe.saniape.app.data.staff.ordenarPlanesOrto
import pe.saniape.app.data.staff.progresionArcos
import pe.saniape.app.data.staff.progresoTratamiento
import pe.saniape.app.data.staff.proximaCitaAgendada
import pe.saniape.app.data.staff.resumenCuotasOrto
import pe.saniape.app.data.staff.tendenciaHigiene
import pe.saniape.app.data.staff.textoArcosControl
import pe.saniape.app.data.staff.textoAtrasoCuotas
import pe.saniape.app.data.staff.textoClaseInicial
import pe.saniape.app.data.staff.textoElastico
import pe.saniape.app.data.staff.textoLigadura
import pe.saniape.app.data.staff.textoProximaCuota
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.mapas.BotonMapa
import pe.saniape.app.ui.clinica.mapas.ChipMapa
import pe.saniape.app.ui.clinica.mapas.EtiquetaMapa
import pe.saniape.app.ui.clinica.mapas.FilaBotonesMapa
import pe.saniape.app.ui.clinica.mapas.PildoraMapa
import pe.saniape.app.ui.clinica.mapas.TarjetaMapa
import pe.saniape.app.ui.monedaUI
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/** Cómo se abre la pestaña desde la agenda ("😁 Registrar control"): el control vinculado a esa cita. */
data class AperturaOrtodoncia(val citaId: String?)

/** El editor de controles: uno nuevo (copiado del anterior o no) o el que se edita. */
private sealed interface EditorCtl {
    data class Nuevo(val citaInicial: String? = null, val repetir: Boolean = false) : EditorCtl
    data class Editar(val id: String) : EditorCtl
}

/**
 * Pestaña 😁 Ortodoncia de la ficha (solo pacientes dentales con ortodoncia:
 * `pacienteEsOrtodoncia`). Gemelo de `OrtodonciaTab.tsx` de la web:
 *
 *  · Sin plan: "😁 Iniciar plan de ortodoncia" (todo en chips, con defaults).
 *  · Cabecera: aparatología, "Mes 8 de 24", barra de fases (real vs la que
 *    sugiere el tiempo, orientativa), clase inicial, objetivos, retención,
 *    próximo control / ⚠ atrasado, higiene con su tendencia y 🖨 la ficha.
 *  · "＋ Registrar control" y "↺ Repetir el anterior" (sin la fase), el
 *    historial con la progresión de arcos y los saltos de más de 8 semanas.
 *  · Tras el control de la INSTALACIÓN: "📝 Dejar los cuidados con brackets".
 *  · 💰 Cuotas del tratamiento (con permiso de pagos): "5 de 16 pagadas ·
 *    ⚠ 1 atrasada · próxima…", solo lectura (ResumenCuotasOrto.tsx).
 *
 * Carga SOLO cuando la pestaña se abre (la monta el padre en su `when`). Sin
 * listas perezosas: vive dentro del `verticalScroll` de la ficha.
 */
@Composable
fun OrtodonciaTab(
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    /** `puede('pacientes') || puede('sesiones')` y ficha activa (como el API). */
    puedeEditar: Boolean,
    mapaDental: MapaDental,
    mapaOrtodoncia: MapaOrtodoncia,
    /** Los tratamientos de ortodoncia del paciente (para atar el plan). */
    tratamientos: List<TratamientoOrto>,
    /** procedimientoId → nombre (de sus tratamientos): el servicio de las citas sin tratamiento. */
    nombresServicio: Map<String, String>,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    clinicaId: String?,
    /** Desde la agenda: el control nuevo queda vinculado a esta cita (si ya tiene el suyo, se abre ese). */
    apertura: AperturaOrtodoncia? = null,
    /** La apertura ya se atendió (vale una vez). */
    onAperturaConsumida: () -> Unit = {},
    /** Tras la instalación: la pestaña 📝 con "Cuidados con brackets" vinculada a la cita. */
    onDejarCuidados: ((citaId: String?, especialidadId: String?) -> Unit)? = null,
    /** `puede('pagos')`: muestra el resumen 💰 de cuotas del tratamiento (como `verCuotas` de la web). */
    puedePagos: Boolean = false,
    /** "Ver pagos →": lleva a la pestaña 💰 Pagos de la ficha, donde se cobra. */
    onVerPagos: (() -> Unit)? = null,
    /** "Hoy" en la zona de la sede del PACIENTE (`regPac.hoy()` de la web); null = la de la sede activa. */
    hoyFicha: String? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val hoy = hoyFicha ?: hoyClinicaIso()
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var planes by remember(pacienteId) { mutableStateOf<List<PlanOrtodoncia>?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pacienteId, token) {
        val r = OrtodonciaRepo.listarPlanes(pacienteId)
        if (r.items != null) { planes = r.items; error = null } else if (planes == null) error = r.error
        cargando = false
    }
    var profesionales by remember { mutableStateOf<List<RefNombre>>(emptyList()) }
    LaunchedEffect(Unit) { profesionales = runCatching { PacientesRepo.terapeutasActivos() }.getOrDefault(emptyList()) }
    var citas by remember(pacienteId) { mutableStateOf<List<CitaOrto>>(emptyList()) }
    LaunchedEffect(pacienteId, token) { citas = OrtodonciaRepo.citasOrtodoncia(pacienteId, mapaDental, mapaOrtodoncia, nombresServicio) }
    var planSel by remember { mutableStateOf<String?>(null) }
    // null = cerrado · "nuevo" · "editar"
    var editorPlan by remember { mutableStateOf<String?>(null) }
    var agendaAtendida by remember { mutableStateOf(false) }

    if (cargando && planes == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 180, 110).forEach { h ->
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            }
        }
        return
    }
    error?.let { msg ->
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 44.dp).clickable { error = null; cargando = true; token++ }.padding(12.dp))
        }
        return
    }
    val ps = planes.orEmpty()
    // Desde la agenda sin plan: primero el plan (una vez); al guardarlo se abre el control de la cita.
    LaunchedEffect(apertura, ps.isEmpty()) {
        if (apertura != null && puedeEditar && !agendaAtendida && planes != null && ps.isEmpty()) {
            agendaAtendida = true
            editorPlan = "nuevo"
        }
    }
    val citaAgenda = apertura?.citaId?.let { id -> citas.firstOrNull { it.id == id } }
    // El plan que se ve: el elegido; si no, el del tratamiento de la cita de la agenda; si no, el más reciente.
    val plan = ps.firstOrNull { it.id == planSel }
        ?: citaAgenda?.tratamientoId?.let { t -> ps.firstOrNull { it.tratamientoId == t } }
        ?: ps.firstOrNull()
    val ocupados = ps.mapNotNull { it.tratamientoId }.toSet()
    val libres = tratamientos.filter { it.id !in ocupados }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("😁 Ortodoncia", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (ps.isEmpty()) {
            TarjetaMapa {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😁", fontSize = 30.sp)
                    Text("Aún no tiene plan de ortodoncia", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Registra la aparatología, la clase inicial y los objetivos. Después, cada control queda con sus arcos, elásticos y brackets despegados, y ves el avance mes a mes.",
                        color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    if (puedeEditar) {
                        Spacer(Modifier.height(12.dp))
                        BotonMapa("😁 Iniciar plan de ortodoncia", lleno = true) { editorPlan = "nuevo" }
                    }
                }
            }
        } else {
            if (ps.size > 1 || (puedeEditar && libres.isNotEmpty())) {
                SelectorPlanes(ps, plan?.id, tratamientos, puedeEditar && libres.isNotEmpty(),
                    onElegir = { planSel = it }, onOtro = { editorPlan = "nuevo" })
            }
            plan?.let { p ->
                key(p.id) {
                    PlanConControles(
                        plan = p, hoy = hoy, puedeEditar = puedeEditar, citas = citas, profesionales = profesionales,
                        tratamiento = tratamientos.firstOrNull { it.id == p.tratamientoId },
                        pacienteNombre = pacienteNombre, pacienteDocumento = pacienteDocumento,
                        clinicaNombre = clinicaNombre, clinicaDireccion = clinicaDireccion, logoUrl = logoUrl, colorPrincipal = colorPrincipal,
                        clinicaId = clinicaId,
                        apertura = apertura?.takeIf { puedeEditar },
                        onAperturaConsumida = { planSel = p.id; onAperturaConsumida() },
                        onEditarPlan = { editorPlan = "editar" },
                        onPlanActualizado = { np -> planes = ps.map { if (it.id == np.id) np else it } },
                        onDejarCuidados = onDejarCuidados,
                        puedePagos = puedePagos, onVerPagos = onVerPagos,
                    )
                }
            }
        }
    }

    editorPlan?.let { modo ->
        if (!puedeEditar) return@let
        key(modo, plan?.id) {
            FormularioPlanOrto(
                pacienteId = pacienteId, hoy = hoy, plan = if (modo == "editar") plan else null,
                tratamientos = tratamientos, profesionales = profesionales, ocupados = ocupados,
                onCerrar = { editorPlan = null },
                onYaExiste = { id ->
                    scope.launch {
                        val r = OrtodonciaRepo.listarPlanes(pacienteId)
                        r.items?.let { planes = it }
                        planSel = id
                        editorPlan = null
                    }
                },
                onGuardado = { np, esNuevo ->
                    planes = ordenarPlanesOrto(if (esNuevo) listOf(np) + ps else ps.map { if (it.id == np.id) np else it })
                    planSel = np.id
                    editorPlan = null
                },
                onGuardadoSinFila = { editorPlan = null; token++ },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorPlanes(
    planes: List<PlanOrtodoncia>,
    actual: String?,
    tratamientos: List<TratamientoOrto>,
    puedeOtro: Boolean,
    onElegir: (String) -> Unit,
    onOtro: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (planes.size > 1) planes.forEach { p ->
            val nombre = tratamientos.firstOrNull { it.id == p.tratamientoId }?.nombre ?: aparatologiaOrtoPorId(p.aparatologia)?.corto ?: "Plan"
            ChipMapa("$nombre · ${p.fechaInstalacion?.let { fechaIndicacion(it) } ?: "sin instalar"}", p.id == actual) { onElegir(p.id) }
        }
        if (puedeOtro) BotonMapa("＋ Otro plan", onClick = onOtro)
    }
}

// ─── Un plan: cabecera + controles ───────────────────────────────────────────

@Composable
private fun PlanConControles(
    plan: PlanOrtodoncia,
    hoy: String,
    puedeEditar: Boolean,
    citas: List<CitaOrto>,
    profesionales: List<RefNombre>,
    tratamiento: TratamientoOrto?,
    pacienteNombre: String,
    pacienteDocumento: String?,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    clinicaId: String?,
    apertura: AperturaOrtodoncia?,
    onAperturaConsumida: () -> Unit,
    onEditarPlan: () -> Unit,
    onPlanActualizado: (PlanOrtodoncia) -> Unit,
    onDejarCuidados: ((String?, String?) -> Unit)?,
    puedePagos: Boolean,
    onVerPagos: (() -> Unit)?,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var token by remember { mutableStateOf(0) }
    var cargando by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<ControlOrtodoncia>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(plan.id, token) {
        val r = OrtodonciaRepo.listarControles(plan.id)
        if (r.items != null) { items = r.items; error = null } else if (items == null) error = r.error
        cargando = false
    }
    var editor by remember { mutableStateOf<EditorCtl?>(null) }
    var cuidadosDe by remember { mutableStateOf<String?>(null) } // cita del control de la instalación ("" = sin cita)
    var confirmarBorrar by remember { mutableStateOf<ControlOrtodoncia?>(null) }
    var borrando by remember { mutableStateOf(false) }
    var agendaAtendida by remember { mutableStateOf(false) }
    val controles = items.orEmpty()

    // Desde la agenda, UNA vez y con los controles ya leídos: si esa cita ya tiene
    // el suyo se abre ESE (un control por cita); si no, uno nuevo vinculado a ella.
    LaunchedEffect(apertura, items != null) {
        if (apertura == null || items == null || agendaAtendida) return@LaunchedEffect
        agendaAtendida = true
        val existente = apertura.citaId?.let { id -> controles.firstOrNull { it.citaId == id } }
        editor = if (existente != null) EditorCtl.Editar(existente.id) else EditorCtl.Nuevo(citaInicial = apertura.citaId)
        if (existente != null) onAperturaConsumida()
    }

    val nombrePorId = profesionales.associate { it.id to it.nombre }
    fun imprimir() {
        acciones.abrirHtml(
            htmlFichaOrtodoncia(plan, controles, clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal, pacienteNombre, pacienteDocumento,
                plan.terapeutaId?.let { nombrePorId[it] }, tratamiento?.nombre, hoy),
            "Ficha de ortodoncia",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabeceraPlan(plan, controles, hoy, tratamiento, citas, plan.terapeutaId?.let { nombrePorId[it] }, puedeEditar,
            onImprimir = { imprimir() }, onEditar = onEditarPlan, puedePagos = puedePagos, onVerPagos = onVerPagos)

        cuidadosDe?.takeIf { editor == null && onDejarCuidados != null }?.let { citaId ->
            AvisoCuidadosBrackets(
                onDejar = { cuidadosDe = null; onDejarCuidados?.invoke(citaId.ifBlank { null }, tratamiento?.especialidadId) },
                onCerrar = { cuidadosDe = null },
            )
        }

        if (puedeEditar && editor == null) {
            FilaBotonesMapa {
                if (controles.isNotEmpty()) BotonMapa("↺ Repetir el anterior") { cuidadosDe = null; editor = EditorCtl.Nuevo(repetir = true) }
                BotonMapa("＋ Registrar control", lleno = true) { cuidadosDe = null; editor = EditorCtl.Nuevo() }
            }
        }

        when {
            cargando && items == null -> Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            error != null && items == null -> TarjetaMapa {
                Text("No se pudieron cargar los controles.", color = c.error, fontSize = 13.sp)
                Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).clickable { error = null; cargando = true; token++ }.padding(vertical = 12.dp))
            }
            else -> Historial(controles, plan, hoy, citas, nombrePorId, puedeEditar && editor == null,
                onEditar = { id -> cuidadosDe = null; editor = EditorCtl.Editar(id) },
                onBorrar = { confirmarBorrar = it })
        }
    }

    editor?.let { m ->
        val enEdicion = (m as? EditorCtl.Editar)?.let { e -> controles.firstOrNull { it.id == e.id } }
        if (m is EditorCtl.Editar && enEdicion == null) LaunchedEffect(m) { editor = null }
        else key(m) {
            val nuevoM = m as? EditorCtl.Nuevo
            // El "anterior" de un control: el previo en el tiempo (para uno nuevo, el último).
            val anterior = if (enEdicion != null) controles.getOrNull(controles.indexOf(enEdicion) + 1) else controles.firstOrNull()
            FormularioControlOrto(
                plan = plan, control = enEdicion, anterior = anterior, repetir = nuevoM?.repetir == true,
                citas = citas, citasConControl = controles.mapNotNull { it.citaId }.toSet(),
                citaInicial = nuevoM?.citaInicial, hoy = hoy, profesionales = profesionales, clinicaId = clinicaId,
                onCerrar = { editor = null },
                onYaExiste = { id ->
                    scope.launch {
                        val r = OrtodonciaRepo.listarControles(plan.id)
                        r.items?.let { items = it }
                        editor = if (r.items?.any { it.id == id } == true) EditorCtl.Editar(id) else null
                        if (apertura?.citaId != null) onAperturaConsumida()
                    }
                },
                onGuardado = { ctl, planNuevo, esNuevo ->
                    editor = null
                    items = ordenarControles(if (esNuevo) listOf(ctl) + controles else controles.map { if (it.id == ctl.id) ctl else it })
                    planNuevo?.let(onPlanActualizado)
                    if (esNuevo && apertura?.citaId != null && ctl.citaId == apertura.citaId) onAperturaConsumida()
                    // La agenda ofreció "Registrar control" EN LUGAR de las indicaciones:
                    // tras la instalación, los cuidados con brackets quedan a un toque.
                    val cita = ctl.citaId?.let { id -> citas.firstOrNull { it.id == id } }
                    cuidadosDe = if (onDejarCuidados != null && ofreceCuidadosBrackets(esNuevo, plan.aparatologia, cita)) ctl.citaId.orEmpty() else null
                },
                onGuardadoSinFila = { editor = null; token++ },
            )
        }
    }

    confirmarBorrar?.let { ctl ->
        AlertDialog(
            onDismissRequest = { if (!borrando) confirmarBorrar = null },
            title = { Text("¿Eliminar este control?", fontWeight = FontWeight.Bold) },
            text = { Text("Control del ${fechaIndicacion(ctl.fecha)}${textoArcosControl(ctl)?.let { " · $it" }.orEmpty()}", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = OrtodonciaRepo.borrarControl(ctl.id)
                        borrando = false
                        if (r.registrada || r.rechazo?.codigo == "NO_ENCONTRADO") {
                            confirmarBorrar = null
                            items = controles.filter { it.id != ctl.id }
                            Toaster.exito("Control eliminado")
                        } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !borrando, onClick = { confirmarBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ─── Cabecera: progreso, fases, clase, objetivos, adherencia, higiene ────────

@Composable
private fun colorNivel(n: String?): Color {
    val c = Sania.colors
    return when (n) { "buena" -> c.ok; "regular" -> c.pend; "mala" -> c.error; else -> c.borde }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CabeceraPlan(
    plan: PlanOrtodoncia,
    controles: List<ControlOrtodoncia>,
    hoy: String,
    tratamiento: TratamientoOrto?,
    citas: List<CitaOrto>,
    profesional: String?,
    puedeEditar: Boolean,
    onImprimir: () -> Unit,
    onEditar: () -> Unit,
    puedePagos: Boolean,
    onVerPagos: (() -> Unit)?,
) {
    val c = Sania.colors
    val ap = aparatologiaOrtoPorId(plan.aparatologia)
    val prog = progresoTratamiento(plan, hoy)
    val esperada = if (prog.retirado || plan.fase == "retencion") null else faseEsperada(prog.meses, plan.duracionEstimadaMeses)
    val est = estadoControles(controles, plan, hoy)
    val higiene = tendenciaHigiene(controles)
    val clase = textoClaseInicial(plan)
    val enAgenda = proximaCitaAgendada(citas, hoy)
    TarjetaMapa {
        Text("😁 ${ap?.nombre ?: plan.aparatologia}", color = c.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            listOfNotNull(nombreOrto(ARCADAS_ORTO, plan.arcadas), tratamiento?.nombre,
                plan.fechaInstalacion?.let { "instalado ${fechaIndicacion(it)}" } ?: "sin instalar", profesional).joinToString(" · "),
            color = c.textoSuave, fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        FilaBotonesMapa {
            BotonMapa("🖨 Ficha de ortodoncia", onClick = onImprimir)
            if (puedeEditar) BotonMapa("✏️ Editar plan", onClick = onEditar)
        }

        // Tiempo: mes N de M
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(prog.texto, color = if (prog.excedido) c.pend else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            prog.pct?.let { Text("$it%", color = c.textoSuave, fontSize = 12.sp) }
        }
        prog.pct?.let { pct ->
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.chipBg)
                .semantics { contentDescription = "Tiempo de tratamiento: $pct%" }) {
                Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (prog.excedido) c.pend else c.navy))
            }
        }

        // Fases: hechas / actual / pendientes; ◆ la que sugiere el tiempo
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            estadosFases(plan.fase).forEach { (f, estado) ->
                Column(Modifier.weight(1f).semantics { contentDescription = "${f.nombre}: $estado" }) {
                    Box(
                        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(when (estado) { "hecha" -> c.ok; "actual" -> c.navy; else -> c.chipBg })
                            .then(if (estado == "pendiente") Modifier.border(1.dp, c.borde, RoundedCornerShape(4.dp)) else Modifier),
                    )
                    Text((if (esperada == f.id && esperada != plan.fase) "◆ " else "") + f.corto,
                        color = if (estado == "actual") c.navy else c.textoSuave, fontSize = 10.sp,
                        fontWeight = if (estado == "actual") FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (esperada != null && esperada != plan.fase) {
            Text("◆ Por el tiempo suele ir en ${faseOrtoPorId(esperada)?.nombre?.lowercase()} (orientativo).",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // Datos del plan
        clase?.let { Text("Clase inicial: $it", color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        plan.retencionPlanificada?.let { r ->
            Text("Retención: ${nombreOrto(RETENCIONES_ORTO, r) ?: r}${plan.fechaRetiro?.let { " · retiro ${fechaIndicacion(it)}" }.orEmpty()}",
                color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (plan.objetivos.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                plan.objetivos.forEach { PildoraMapa(it, c.navy, c.chipBg) }
            }
        }

        // Adherencia y higiene
        val (bordeProx, fondoProx) = when {
            est.atrasado && enAgenda == null -> c.error to c.errorBg
            est.toca && enAgenda == null -> c.pend to c.pendBg
            else -> c.borde to c.superficie
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(fondoProx)
                .border(1.dp, bordeProx, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            EtiquetaMapa("Próximo control")
            when {
                enAgenda != null -> Text("📅 ${fechaIndicacion(enAgenda.fecha)}${enAgenda.hora?.let { " · ${it.take(5)}" }.orEmpty()} (agendado)",
                    color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                est.proximoEstimado != null -> Text("≈ ${fechaIndicacion(est.proximoEstimado)} (sin agendar)", color = c.texto, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold)
                else -> Text("—", color = c.textoSuave, fontSize = 13.sp)
            }
            if (est.atrasado && enAgenda == null) {
                Text("⚠ Control atrasado${est.semanasDesdeUltimo?.let { ": último hace $it semanas" }.orEmpty()}",
                    color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (est.toca && enAgenda == null) Text("Ya le toca el control", color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
        ) {
            EtiquetaMapa("Higiene")
            if (higiene.serie.isEmpty()) Text("Sin registros", color = c.textoSuave, fontSize = 13.sp)
            else Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = "Higiene en los últimos controles: ${higiene.serie.joinToString(", ")}" }) {
                higiene.serie.forEach { h ->
                    Box(Modifier.padding(end = 4.dp).size(12.dp).clip(CircleShape).background(colorNivel(h)))
                }
                higiene.tendencia?.let { t ->
                    Text(when (t) { "mejora" -> "↗ Mejora"; "empeora" -> "↘ Empeora"; else -> "→ Estable" },
                        color = when (t) { "mejora" -> c.ok; "empeora" -> c.error; else -> c.textoSuave },
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        // 💰 Cuotas: solo con permiso de pagos y un tratamiento atado (como la web).
        if (puedePagos) plan.tratamientoId?.let { CuotasPlanOrto(it, hoy, tratamiento?.moneda, onVerPagos) }
        plan.notas?.let { Text("Notas: $it", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/**
 * 💰 Las cuotas del tratamiento en un recuadro, junto a la adherencia (gemelo de
 * ResumenCuotasOrto.tsx): "5 de 16 pagadas", ⚠ atrasadas con su monto o ✓ al
 * día, la barra de cuotas pagadas y la próxima por pagar. SOLO LECTURA: ni un
 * sol se mueve acá (se cobra en 💰 Pagos). Lee al abrir la pestaña; si no se
 * pudo leer (backend viejo, sin red), el recuadro no aparece.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CuotasPlanOrto(tratamientoId: String, hoy: String, monedaTratamiento: String?, onVerPagos: (() -> Unit)?) {
    val c = Sania.colors
    // La moneda del TRATAMIENTO (su sede); sin ella, la de la ficha. Nunca se mezclan.
    val moneda = monedaTratamiento ?: monedaUI()
    var datos by remember(tratamientoId) { mutableStateOf<CuotasTratamientoOrto?>(null) }
    var cargando by remember(tratamientoId) { mutableStateOf(true) }
    LaunchedEffect(tratamientoId) {
        datos = OrtodonciaRepo.cuotasTratamiento(tratamientoId)
        cargando = false
    }
    if (!cargando && datos == null) return
    val r = remember(datos, hoy) { datos?.let { resumenCuotasOrto(it, hoy) } }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .padding(10.dp),
    ) {
        EtiquetaMapa("💰 Cuotas")
        val res = r?.resumen
        when {
            r == null -> Box(Modifier.padding(top = 2.dp).fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(c.chipBg))
            res == null -> Text("Sin cronograma de cuotas · pagado ${formatearDinero(r.pagado, moneda)}", color = c.textoSuave, fontSize = 13.sp)
            else -> {
                val total = r.cuotas.size
                // Como la web: "5 de 16 pagadas · ⚠ 1 atrasada (S/ 125.00)" o "· al día".
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${res.pagadas} de $total pagadas", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val atraso = textoAtrasoCuotas(res, moneda)
                    Text("· ${atraso ?: "al día"}", color = if (atraso != null) c.error else c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                val pct = if (total > 0) res.pagadas.toFloat() / total else 0f
                Box(
                    Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.chipBg)
                        .semantics { contentDescription = "${res.pagadas} de $total cuotas pagadas" },
                ) {
                    if (pct > 0f) Box(Modifier.fillMaxWidth(pct).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ok))
                }
                textoProximaCuota(res, moneda, hoy)?.let { t ->
                    val estado = res.proxima?.estado
                    Text(t, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                        color = when (estado) {
                            EstadoCuota.ATRASADA -> c.error
                            EstadoCuota.VENCE_HOY, EstadoCuota.POR_VENCER -> c.pend
                            else -> c.textoSuave
                        },
                        fontWeight = if (estado == EstadoCuota.ATRASADA || estado == EstadoCuota.VENCE_HOY) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        if (r != null && onVerPagos != null) {
            Text("Ver pagos →", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp).heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onVerPagos)
                    .padding(vertical = 12.dp))
        }
    }
}

// ─── Historial de controles ──────────────────────────────────────────────────

@Composable
private fun Historial(
    controles: List<ControlOrtodoncia>,
    plan: PlanOrtodoncia,
    hoy: String,
    citas: List<CitaOrto>,
    nombrePorId: Map<String, String>,
    puedeEditar: Boolean,
    onEditar: (String) -> Unit,
    onBorrar: (ControlOrtodoncia) -> Unit,
) {
    val c = Sania.colors
    if (controles.isEmpty()) {
        TarjetaMapa {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aún no hay controles", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Cada control guarda arcos, ligaduras, elásticos, brackets despegados, higiene y la próxima cita.",
                    color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
        return
    }
    val est = estadoControles(controles, plan, hoy)
    val brechaAntesDe = est.brechas.associateBy { it.hasta }
    val sup = progresionArcos(controles, superior = true)
    val inf = progresionArcos(controles, superior = false)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sup.isNotEmpty() || inf.isNotEmpty()) {
            TarjetaMapa {
                EtiquetaMapa("Progresión de arcos")
                if (sup.isNotEmpty()) Text("Sup.: ${sup.joinToString(" → ") { it.first }}", color = c.texto, fontSize = 12.sp)
                if (inf.isNotEmpty()) Text("Inf.: ${inf.joinToString(" → ") { it.first }}", color = c.texto, fontSize = 12.sp)
            }
        }
        Text("CONTROLES (${controles.size})", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        controles.forEachIndexed { i, ctl ->
            key(ctl.id) {
                TarjetaControl(
                    ctl, ultimo = i == 0, cita = ctl.citaId?.let { id -> citas.firstOrNull { it.id == id } },
                    profesional = ctl.terapeutaId?.let { nombrePorId[it] }, puedeEditar = puedeEditar,
                    onEditar = { onEditar(ctl.id) }, onBorrar = { onBorrar(ctl) },
                )
                brechaAntesDe[ctl.fecha]?.let { b ->
                    Text("⚠ ${b.semanas} semanas sin control (${fechaIndicacion(b.desde)} → ${fechaIndicacion(b.hasta)}) · control atrasado",
                        color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaControl(
    ctl: ControlOrtodoncia,
    ultimo: Boolean,
    cita: CitaOrto?,
    profesional: String?,
    puedeEditar: Boolean,
    onEditar: () -> Unit,
    onBorrar: () -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(ultimo) }
    val elasticos = normalizarElasticos(ctl.elasticos)
    val arcos = textoArcosControl(ctl)
    TarjetaMapa {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { abierto = !abierto }, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(fechaIndicacion(ctl.fecha), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    ctl.fase?.let { f -> faseOrtoPorId(f)?.corto?.let { PildoraMapa(it, c.navy, c.chipBg) } }
                    if (ultimo) PildoraMapa("Último", c.ok, c.okBg)
                    ctl.higiene?.let { h ->
                        Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(colorNivel(h))
                            .semantics { contentDescription = "Higiene $h" })
                    }
                    if (ctl.bracketsDespegados.isNotEmpty()) {
                        Text("${ctl.bracketsDespegados.size} despegado${if (ctl.bracketsDespegados.size == 1) "" else "s"}",
                            color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(arcos ?: elasticos.takeIf { it.isNotEmpty() }?.joinToString(" · ") { textoElastico(it) } ?: ctl.observaciones ?: "Control",
                    color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text(if (abierto) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        if (!abierto) return@TarjetaMapa
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            @Composable fun fila(k: String, v: String?) {
                if (!v.isNullOrBlank()) Text("$k: $v", color = c.texto, fontSize = 12.sp)
            }
            fila("Cita", cita?.let { "${it.servicio ?: "Atención"}${it.hora?.let { h -> " · ${h.take(5)}" }.orEmpty()}" })
            fila("Atendió", profesional)
            fila("Ligaduras", textoLigadura(ctl.ligaduraTipo, ctl.ligaduraColor))
            fila("Cadeneta", nombreOrto(CADENETAS_ORTO, ctl.cadeneta))
            if (elasticos.isNotEmpty()) fila("Elásticos", elasticos.joinToString(" · ") { textoElastico(it) })
            fila("Despegados", ctl.bracketsDespegados.joinToString(", ").ifEmpty { null })
            fila("Recementados", ctl.bracketsRecementados.joinToString(", ").ifEmpty { null })
            fila("Higiene", nombreOrto(NIVELES_ORTO, ctl.higiene))
            fila("Usa los elásticos", nombreOrto(NIVELES_ORTO, ctl.cooperacionElasticos))
            fila("Dolor", nombreOrto(DOLORES_ORTO, ctl.dolor))
            fila("Observaciones", ctl.observaciones)
            fila("Próxima cita", ctl.proximaSemanas?.let { "en $it semanas" })
            if (puedeEditar) {
                Spacer(Modifier.height(6.dp))
                FilaBotonesMapa {
                    BotonMapa("✏️ Editar", onClick = onEditar)
                    BotonMapa("🗑️ Eliminar", onClick = onBorrar)
                }
            }
        }
    }
}
