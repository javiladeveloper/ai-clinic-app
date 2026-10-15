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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.staff.APARATOLOGIAS_ORTO
import pe.saniape.app.data.staff.ARCADAS_ORTO
import pe.saniape.app.data.staff.ARCOS_COMUNES
import pe.saniape.app.data.staff.AprendidoPlan
import pe.saniape.app.data.staff.BorradorControlOrto
import pe.saniape.app.data.staff.BorradorPlanOrto
import pe.saniape.app.data.staff.CADENETAS_ORTO
import pe.saniape.app.data.staff.CLASES_ANGLE
import pe.saniape.app.data.staff.COLORES_LIGADURA
import pe.saniape.app.data.staff.CUADRANTES_ADULTO
import pe.saniape.app.data.staff.CUADRANTES_DECIDUO
import pe.saniape.app.data.staff.CitaOrto
import pe.saniape.app.data.staff.ControlOrtodoncia
import pe.saniape.app.data.staff.DOLORES_ORTO
import pe.saniape.app.data.staff.DURACIONES_ORTO
import pe.saniape.app.data.staff.ElasticoOrto
import pe.saniape.app.data.staff.FASES_ORTO
import pe.saniape.app.data.staff.FUERZAS_ELASTICO
import pe.saniape.app.data.staff.FraseFrecuenteApp
import pe.saniape.app.data.staff.HORAS_ELASTICO
import pe.saniape.app.data.staff.LADOS_ELASTICO
import pe.saniape.app.data.staff.LIGADURAS_ORTO
import pe.saniape.app.data.staff.LimitesOrtodoncia
import pe.saniape.app.data.staff.NIVELES_ORTO
import pe.saniape.app.data.staff.OBJETIVOS_ORTO
import pe.saniape.app.data.staff.OrtodonciaRepo
import pe.saniape.app.data.staff.PlanOrtodoncia
import pe.saniape.app.data.staff.RETENCIONES_ORTO
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.SEMANAS_CONTROL_ORTO
import pe.saniape.app.data.staff.TAMANOS_ELASTICO
import pe.saniape.app.data.staff.TIPOS_ELASTICO
import pe.saniape.app.data.staff.TratamientoOrto
import pe.saniape.app.data.staff.aparatologiaOrtoPorId
import pe.saniape.app.data.staff.aparatologiaSugerida
import pe.saniape.app.data.staff.citasElegiblesControl
import pe.saniape.app.data.staff.controlOrtoDeJson
import pe.saniape.app.data.staff.copiarControlAnterior
import pe.saniape.app.data.staff.elegirTratamientoPlan
import pe.saniape.app.data.staff.faseOrtoPorId
import pe.saniape.app.data.staff.fechaIndicacion
import pe.saniape.app.data.staff.frasesObservacionesOrto
import pe.saniape.app.data.staff.mismoArco
import pe.saniape.app.data.staff.normalizarElasticos
import pe.saniape.app.data.staff.planOrtoDeJson
import pe.saniape.app.data.staff.plegarOrto
import pe.saniape.app.data.staff.siguienteArco
import pe.saniape.app.data.staff.sumarDiasOrto
import pe.saniape.app.data.staff.validarControlOrto
import pe.saniape.app.data.staff.validarPlanOrto
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.atencion.CampoTextoClinico
import pe.saniape.app.ui.clinica.mapas.AvisoMapa
import pe.saniape.app.ui.clinica.mapas.BotonMapa
import pe.saniape.app.ui.clinica.mapas.CajaErroresMapa
import pe.saniape.app.ui.clinica.mapas.ChipMapa
import pe.saniape.app.ui.clinica.mapas.EtiquetaMapa
import pe.saniape.app.ui.clinica.mapas.FilaBotonesMapa
import pe.saniape.app.ui.clinica.mapas.SegmentadoMapa
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/** Arcos a la vista antes de "+ acero / TMA" (la secuencia típica; el resto, plegado). */
private const val ARCOS_A_LA_VISTA = 7

/** Un elástico nuevo: lo más habitual (clase II, ambos lados, 3/16", media, todo el día). */
private val ELASTICO_NUEVO = ElasticoOrto("clase_ii", "ambos", "3/16\"", "media", 22)

/** El id de una respuesta 409 YA_EXISTE (el plan / control que ya existe). */
internal fun idYaExiste(cuerpo: JsonObject?): String? =
    (cuerpo?.get("id") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

// ─── Piezas chicas ───────────────────────────────────────────────────────────

/** Selector de una lista (cita, tratamiento, profesional): caja tocable + menú. */
@Composable
internal fun SelectorOrto(valor: String, opciones: List<Pair<String, String>>, onElegir: (String) -> Unit) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Box {
        CajaSelectorForm("$valor  ▾") { abierto = true }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (id, texto) ->
                DropdownMenuItem(text = { Text(texto, color = c.texto, fontSize = 13.sp) }, onClick = { abierto = false; onElegir(id) })
            }
        }
    }
}

internal fun textoCitaOrto(ci: CitaOrto): String =
    "${fechaIndicacion(ci.fecha)}${ci.hora?.let { " ${it.take(5)}" }.orEmpty()} · ${ci.servicio ?: "Atención"} · ${ci.estado}"

/**
 * Elegir piezas tocándolas, con la MISMA numeración y orden visual del
 * odontograma (FDI: 18→11 | 21→28 arriba, 48→41 | 31→38 abajo, como se mira
 * la boca de frente). Gemelo de SelectorPiezasFDI.tsx. [marcadas] = piezas de
 * referencia (las despegadas, al recementar) con un punto ámbar.
 */
@Composable
internal fun SelectorPiezasFdi(
    etiqueta: String,
    valor: List<String>,
    onChange: (List<String>) -> Unit,
    marcadas: List<String> = emptyList(),
) {
    val c = Sania.colors
    var temporales by remember { mutableStateOf(valor.any { (it.toIntOrNull() ?: 0) >= 51 }) }
    val cuadrantes = if (temporales) CUADRANTES_DECIDUO else CUADRANTES_ADULTO
    val elegidas = valor.toSet()
    fun alternar(d: String) {
        val s = elegidas.toMutableSet()
        if (!s.add(d)) s.remove(d)
        onChange(s.sortedBy { it.toInt() })
    }
    @Composable
    fun fila(q: List<String>) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            q.forEach { d ->
                val sel = d in elegidas
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (sel) c.navy else c.superficie)
                        .border(1.5.dp, if (sel) c.navy else c.borde, RoundedCornerShape(6.dp))
                        .semantics { selected = sel; role = Role.Button; contentDescription = "Pieza $d" }
                        .clickable { alternar(d) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(d, color = if (sel) c.sobreNavy else c.texto, fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold)
                    if (d in marcadas && !sel) {
                        Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(6.dp).clip(CircleShape).background(c.pend))
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().semantics { contentDescription = etiqueta }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Der. del paciente ← → Izq.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(if (temporales) "Ver permanentes" else "Ver temporales", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.heightIn(min = 44.dp).clickable { temporales = !temporales }.padding(horizontal = 8.dp, vertical = 12.dp))
        }
        fila(cuadrantes[0]); fila(cuadrantes[1])
        Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(c.borde))
        fila(cuadrantes[2]); fila(cuadrantes[3])
    }
}

// ─── Plan: iniciar / editar ──────────────────────────────────────────────────

/**
 * Iniciar / editar el plan (gemelo de PlanOrtoForm.tsx): todo con chips y valores
 * por defecto (aparatología según el servicio, ambas arcadas, alineación, la
 * duración habitual de la aparatología, instalación hoy). Obliga solo el
 * tratamiento (un plan por tratamiento) y la aparatología.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormularioPlanOrto(
    pacienteId: String,
    hoy: String,
    plan: PlanOrtodoncia?,
    tratamientos: List<TratamientoOrto>,
    profesionales: List<RefNombre>,
    /** Tratamientos que ya tienen su plan (no se ofrecen para uno nuevo). */
    ocupados: Set<String>,
    onCerrar: () -> Unit,
    onYaExiste: (String) -> Unit,
    onGuardado: (PlanOrtodoncia, Boolean) -> Unit,
    onGuardadoSinFila: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val nuevo = plan == null
    val elegibles = tratamientos.filter { it.id !in ocupados || it.id == plan?.tratamientoId }
    val tratInicial = plan?.tratamientoId ?: elegirTratamientoPlan(elegibles).orEmpty()
    fun servicioDe(id: String) = tratamientos.firstOrNull { it.id == id }?.nombre
    fun terDe(id: String) = tratamientos.firstOrNull { it.id == id }?.terapeutaId.orEmpty()
    val apInicial = plan?.aparatologia ?: aparatologiaSugerida(servicioDe(tratInicial))

    var tratId by remember { mutableStateOf(tratInicial) }
    var aparatologia by remember { mutableStateOf(apInicial) }
    // ¿El usuario ya tocó estos? Entonces cambiar el tratamiento no los pisa.
    var tocoAparatologia by remember { mutableStateOf(!nuevo) }
    var tocoDuracion by remember { mutableStateOf(!nuevo) }
    var arcadas by remember { mutableStateOf(plan?.arcadas ?: "ambas") }
    var instalada by remember { mutableStateOf(if (nuevo) true else plan?.fechaInstalacion != null) }
    var fechaInstalacion by remember { mutableStateOf(plan?.fechaInstalacion ?: hoy) }
    var duracion by remember { mutableStateOf((plan?.duracionEstimadaMeses ?: aparatologiaOrtoPorId(apInicial)?.meses ?: 24).toString()) }
    var fase by remember { mutableStateOf(plan?.fase ?: "alineacion") }
    var molarDer by remember { mutableStateOf(plan?.claseMolarDer) }
    var molarIzq by remember { mutableStateOf(plan?.claseMolarIzq) }
    var caninaDer by remember { mutableStateOf(plan?.claseCaninaDer) }
    var caninaIzq by remember { mutableStateOf(plan?.claseCaninaIzq) }
    var objetivos by remember { mutableStateOf(plan?.objetivos.orEmpty()) }
    var objetivoNuevo by remember { mutableStateOf("") }
    var retencion by remember { mutableStateOf(plan?.retencionPlanificada) }
    var fechaRetiro by remember { mutableStateOf(plan?.fechaRetiro.orEmpty()) }
    var terapeutaId by remember { mutableStateOf(plan?.terapeutaId ?: terDe(tratInicial)) }
    var notas by remember { mutableStateOf(plan?.notas.orEmpty()) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var elegirFecha by remember { mutableStateOf<String?>(null) } // "instalacion" | "retiro"

    fun elegirTratamiento(id: String) {
        tratId = id
        if (!tocoAparatologia) {
            val ap = aparatologiaSugerida(servicioDe(id))
            aparatologia = ap
            if (!tocoDuracion) duracion = (aparatologiaOrtoPorId(ap)?.meses ?: 24).toString()
        }
        if (nuevo) terapeutaId = terDe(id)
    }
    fun elegirAparatologia(id: String) {
        tocoAparatologia = true
        aparatologia = id
        if (!tocoDuracion) duracion = (aparatologiaOrtoPorId(id)?.meses ?: 24).toString()
    }
    fun tieneObjetivo(o: String) = objetivos.any { plegarOrto(it) == plegarOrto(o) }
    fun alternarObjetivo(o: String) {
        objetivos = if (tieneObjetivo(o)) objetivos.filter { plegarOrto(it) != plegarOrto(o) } else objetivos + o
    }
    fun agregarObjetivo() {
        val o = objetivoNuevo.replace(Regex("""\s+"""), " ").trim().take(LimitesOrtodoncia.OBJETIVO)
        if (o.isEmpty()) return
        if (!tieneObjetivo(o)) objetivos = objetivos + o
        objetivoNuevo = ""
    }
    fun guardar() {
        if (guardando) return
        if (nuevo && tratId.isBlank()) { errores = listOf("Elige el tratamiento de ortodoncia."); return }
        val d = duracion.trim()
        val b = BorradorPlanOrto(
            pacienteId = pacienteId, tratamientoId = tratId.ifBlank { null }, terapeutaId = terapeutaId.ifBlank { null },
            aparatologia = aparatologia, arcadas = arcadas,
            fechaInstalacion = if (instalada) fechaInstalacion.ifBlank { null } else null,
            duracionEstimadaMeses = if (d.isEmpty()) null else d.toIntOrNull() ?: -1,
            fase = fase, claseMolarDer = molarDer, claseMolarIzq = molarIzq, claseCaninaDer = caninaDer, claseCaninaIzq = caninaIzq,
            objetivos = objetivos, retencionPlanificada = retencion, fechaRetiro = fechaRetiro.ifBlank { null },
            notas = notas.trim().ifEmpty { null },
        )
        val e = validarPlanOrto(b, nuevo)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = OrtodonciaRepo.guardarPlan(b, plan?.id)
            guardando = false
            if (r.rechazo?.codigo == "YA_EXISTE") {
                idYaExiste(r.cuerpo)?.let { id ->
                    Toaster.info("Ese tratamiento ya tiene su plan: lo abrimos")
                    onYaExiste(id)
                    return@launch
                }
            }
            val fila = planOrtoDeJson(r.cuerpo?.get("plan") as? JsonObject)
            when {
                r.registrada && fila != null -> {
                    Toaster.exito(if (nuevo) "Plan de ortodoncia iniciado" else "Plan actualizado")
                    onGuardado(fila, nuevo)
                }
                // Guardado sin la fila en la respuesta: se cierra y se recarga (reintentar duplicaría).
                r.registrada -> { Toaster.exito(if (nuevo) "Plan de ortodoncia iniciado" else "Plan actualizado"); onGuardadoSinFila() }
                else -> {
                    val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                    errores = listOf(msg); Toaster.error(msg)
                }
            }
        }
    }

    DialogoForm(
        titulo = if (nuevo) "Iniciar plan de ortodoncia" else "Editar plan de ortodoncia",
        subtitulo = "😁 Aparatología, fase, clase inicial y objetivos",
        textoAccion = if (guardando) "Guardando…" else if (nuevo) "Iniciar plan" else "Guardar",
        accionHabilitada = !guardando && (!nuevo || tratId.isNotBlank()),
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtiquetaMapa("Tratamiento")
        SelectorOrto(
            tratamientos.firstOrNull { it.id == tratId }?.let { t -> t.nombre.orEmpty() + if (t.estado != "Activo") " (${t.estado})" else "" }
                ?: "Elige el tratamiento…",
            elegibles.map { t -> t.id to (t.nombre.orEmpty() + if (t.estado != "Activo") " (${t.estado})" else "") },
        ) { elegirTratamiento(it) }
        if (nuevo && elegibles.isEmpty()) {
            Text("Primero crea el tratamiento de ortodoncia (p. ej. «Instalación de brackets») en Atenciones.",
                color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (profesionales.isNotEmpty()) {
            EtiquetaMapa("Ortodoncista")
            SelectorOrto(
                profesionales.firstOrNull { it.id == terapeutaId }?.nombre ?: "—",
                listOf("" to "—") + profesionales.map { it.id to it.nombre },
            ) { terapeutaId = it }
        }
        EtiquetaMapa("Aparatología")
        SegmentadoMapa(APARATOLOGIAS_ORTO.map { it.id to it.corto }, aparatologia, { v -> v?.let { elegirAparatologia(it) } }, permiteVacio = false)
        EtiquetaMapa("Arcadas")
        SegmentadoMapa(ARCADAS_ORTO.map { it.id to it.nombre }, arcadas, { v -> v?.let { arcadas = it } }, permiteVacio = false)
        EtiquetaMapa("Instalación")
        if (instalada) CajaSelectorForm(fechaIndicacion(fechaInstalacion)) { elegirFecha = "instalacion" }
        Box(Modifier.padding(top = 6.dp)) {
            ChipMapa((if (!instalada) "✓ " else "") + "Todavía no se instala", !instalada) { instalada = !instalada }
        }
        EtiquetaMapa("Duración estimada (meses)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DURACIONES_ORTO.forEach { d ->
                ChipMapa(d.toString(), duracion == d.toString()) { tocoDuracion = true; duracion = d.toString() }
            }
        }
        OutlinedTextField(
            value = if (duracion.toIntOrNull() in DURACIONES_ORTO) "" else duracion,
            onValueChange = { v -> tocoDuracion = true; duracion = v.filter { it.isDigit() }.take(2) },
            placeholder = { Text("Otra", color = c.textoSuave, fontSize = 13.sp) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.padding(top = 6.dp).width(110.dp), colors = coloresCampoForm(),
        )
        EtiquetaMapa("Fase actual")
        SegmentadoMapa(FASES_ORTO.map { it.id to it.corto }, fase, { v -> v?.let { fase = it } }, permiteVacio = false)
        EtiquetaMapa("Clase de Angle inicial (lado del paciente)")
        val clases = CLASES_ANGLE.map { it to it }
        listOf(
            Triple("Molar der.", molarDer) { v: String? -> molarDer = v },
            Triple("Molar izq.", molarIzq) { v: String? -> molarIzq = v },
            Triple("Canina der.", caninaDer) { v: String? -> caninaDer = v },
            Triple("Canina izq.", caninaIzq) { v: String? -> caninaIzq = v },
        ).forEach { (t, v, set) ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(86.dp))
                SegmentadoMapa(clases, v, set)
            }
        }
        EtiquetaMapa("Objetivos")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (OBJETIVOS_ORTO + objetivos.filter { o -> OBJETIVOS_ORTO.none { plegarOrto(it) == plegarOrto(o) } }).forEach { o ->
                val activo = tieneObjetivo(o)
                ChipMapa((if (activo) "✓ " else "") + o, activo) { alternarObjetivo(o) }
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = objetivoNuevo, onValueChange = { objetivoNuevo = it.take(LimitesOrtodoncia.OBJETIVO) },
                placeholder = { Text("Otro objetivo…", color = c.textoSuave, fontSize = 13.sp) }, singleLine = true,
                modifier = Modifier.weight(1f), colors = coloresCampoForm(),
            )
            BotonMapa("Agregar", habilitado = objetivoNuevo.isNotBlank()) { agregarObjetivo() }
        }
        EtiquetaMapa("Retención planificada")
        SegmentadoMapa(RETENCIONES_ORTO.map { it.id to it.nombre }, retencion, { retencion = it })
        if (!nuevo || fase == "retencion") {
            EtiquetaMapa("Retiro de la aparatología")
            CajaSelectorForm(fechaRetiro.takeIf { it.isNotBlank() }?.let { fechaIndicacion(it) } ?: "Sin retirar") { elegirFecha = "retiro" }
            if (fechaRetiro.isNotBlank()) {
                Text("Quitar la fecha de retiro", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).clickable { fechaRetiro = "" }.padding(vertical = 12.dp))
            }
        }
        EtiquetaMapa("Notas internas (no las ve el paciente)")
        OutlinedTextField(
            value = notas, onValueChange = { notas = it.take(LimitesOrtodoncia.NOTAS) },
            placeholder = { Text("Ej.: extracciones de 14 y 24; anclaje máximo superior.", color = c.textoSuave, fontSize = 13.sp) },
            minLines = 2, modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
        )
        CajaErroresMapa(errores)
    }

    elegirFecha?.let { cual ->
        DialogoFecha(
            onElegir = { f -> if (cual == "instalacion") fechaInstalacion = f else fechaRetiro = f },
            onCerrar = { elegirFecha = null },
            inicial = if (cual == "instalacion") fechaInstalacion else fechaRetiro.ifBlank { hoy },
        )
    }
}

// ─── Control: registrar / editar ─────────────────────────────────────────────

/**
 * Registrar / editar un control, rápido (gemelo de ControlOrtoForm.tsx):
 * "↺ Copiar el control anterior" (arcos, ligaduras, elásticos, cadeneta,
 * semanas — NUNCA la fase), chips de arco con "= el anterior" y el "siguiente
 * sugerido", elásticos en chips, brackets despegados / recementados con las
 * piezas FDI del odontograma, observaciones con chips que aprenden y la
 * próxima cita en N semanas. Sin brackets los arcos se pliegan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormularioControlOrto(
    plan: PlanOrtodoncia,
    /** null = control nuevo. */
    control: ControlOrtodoncia?,
    /** El control previo (para copiar y para "= el anterior"). */
    anterior: ControlOrtodoncia?,
    /** Abrir ya copiado del anterior ("↺ Repetir el anterior"). */
    repetir: Boolean,
    citas: List<CitaOrto>,
    citasConControl: Set<String>,
    citaInicial: String?,
    hoy: String,
    profesionales: List<RefNombre>,
    clinicaId: String?,
    onCerrar: () -> Unit,
    onYaExiste: (String) -> Unit,
    onGuardado: (ControlOrtodoncia, PlanOrtodoncia?, Boolean) -> Unit,
    onGuardadoSinFila: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val nuevo = control == null
    val ap = aparatologiaOrtoPorId(plan.aparatologia)
    val conSup = plan.arcadas != "inferior"
    val conInf = plan.arcadas != "superior"
    val elegibles = remember(citas, citasConControl, control) { citasElegiblesControl(citas, citasConControl, control?.citaId) }
    fun citaDe(id: String?) = id?.let { i -> citas.firstOrNull { it.id == i } }
    // La cita por defecto: la del control, la que viene de la agenda o la de HOY sin control.
    val citaDefecto = control?.citaId ?: citaInicial ?: elegibles.firstOrNull { it.fecha.take(10) == hoy }?.id
    var b by remember {
        val base = if (control != null) pe.saniape.app.data.staff.borradorControlDesde(control) else BorradorControlOrto(
            planId = plan.id, citaId = citaDefecto,
            fecha = citaDe(citaDefecto)?.fecha?.take(10) ?: hoy,
            terapeutaId = citaDe(citaDefecto)?.terapeutaId ?: plan.terapeutaId,
            fase = plan.fase,
            ligaduraTipo = if (plan.aparatologia == "autoligado") "sin_ligadura" else null,
            proximaSemanas = anterior?.proximaSemanas ?: ap?.semanas ?: 4,
        )
        mutableStateOf(if (nuevo && repetir && anterior != null) copiarControlAnterior(base, anterior) else base)
    }
    var copiado by remember { mutableStateOf(nuevo && repetir && anterior != null) }
    var verArcos by remember { mutableStateOf(ap?.brackets == true || !b.arcoSuperior.isNullOrBlank() || !b.arcoInferior.isNullOrBlank()) }
    var verPiezas by remember { mutableStateOf(b.bracketsDespegados.isNotEmpty() || b.bracketsRecementados.isNotEmpty()) }
    var todosArcos by remember { mutableStateOf(false) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var elegirFecha by remember { mutableStateOf(false) }
    var aprendidos by remember { mutableStateOf<List<AprendidoPlan>>(emptyList()) }
    LaunchedEffect(Unit) { aprendidos = OrtodonciaRepo.sugerencias(clinicaId) }
    val frasesObs = remember(aprendidos) { frasesObservacionesOrto(aprendidos) }
    val chipsObs = remember(frasesObs) { frasesObs.chips.map { FraseFrecuenteApp("ortodoncia", it.texto, 0) } }

    fun elegirCita(id: String) {
        val ci = citaDe(id)
        b = b.copy(citaId = id.ifBlank { null }, fecha = ci?.fecha?.take(10) ?: b.fecha, terapeutaId = ci?.terapeutaId ?: b.terapeutaId)
    }
    fun copiarAnterior() {
        val ant = anterior ?: return
        b = copiarControlAnterior(b, ant)
        if (!ant.arcoSuperior.isNullOrBlank() || !ant.arcoInferior.isNullOrBlank()) verArcos = true
        copiado = true
    }
    fun cambiarElastico(i: Int, f: (ElasticoOrto) -> ElasticoOrto) {
        b = b.copy(elasticos = b.elasticos.mapIndexed { k, x -> if (k == i) f(x) else x })
    }
    val hayElasticos = b.elasticos.isNotEmpty() || anterior?.elasticos?.isNotEmpty() == true

    fun guardar() {
        if (guardando) return
        val envio = b.copy(
            ligaduraColor = if (b.ligaduraTipo == "elasticas") b.ligaduraColor else null,
            cooperacionElasticos = if (hayElasticos) b.cooperacionElasticos else null,
        )
        val e = validarControlOrto(envio)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = OrtodonciaRepo.guardarControl(envio, control?.id)
            guardando = false
            if (r.rechazo?.codigo == "YA_EXISTE") {
                idYaExiste(r.cuerpo)?.let { id ->
                    Toaster.info("Esa cita ya tiene su control: lo abrimos para que lo edites")
                    onYaExiste(id)
                    return@launch
                }
            }
            val fila = controlOrtoDeJson(r.cuerpo?.get("control") as? JsonObject)
            val planNuevo = planOrtoDeJson(r.cuerpo?.get("plan") as? JsonObject)
            when {
                r.registrada && fila != null -> {
                    if (!envio.observaciones.isNullOrBlank()) OrtodonciaRepo.olvidarSugerencias()
                    Toaster.exito(
                        (if (nuevo) "Control registrado" else "Control actualizado") +
                            (planNuevo?.let { " · el plan pasó a: ${faseOrtoPorId(it.fase)?.nombre ?: it.fase}" } ?: ""),
                    )
                    onGuardado(fila, planNuevo, nuevo)
                }
                r.registrada -> { Toaster.exito(if (nuevo) "Control registrado" else "Control actualizado"); onGuardadoSinFila() }
                else -> {
                    val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                    errores = listOf(msg); Toaster.error(msg)
                }
            }
        }
    }

    DialogoForm(
        titulo = if (nuevo) "Registrar control" else "Editar control del ${fechaIndicacion(control!!.fecha)}",
        subtitulo = "😁 Arcos, ligaduras, elásticos y brackets",
        textoAccion = if (guardando) "Guardando…" else if (nuevo) "Guardar control" else "Guardar cambios",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        if (nuevo && anterior != null) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 6.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(if (copiado) c.okBg else c.chipBg).border(1.dp, if (copiado) c.ok else c.lav, RoundedCornerShape(Sania.shape.md.dp))
                    .clickable(enabled = !copiado) { copiarAnterior() }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(if (copiado) "✓ Copiado del control del ${fechaIndicacion(anterior.fecha)}" else "↺ Copiar el control del ${fechaIndicacion(anterior.fecha)}",
                    color = if (copiado) c.ok else c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Arcos, ligaduras, elásticos, cadeneta y semanas (la fase no: parte de la actual del plan).",
                    color = c.textoSuave, fontSize = 11.sp)
            }
        }
        // La atención
        EtiquetaMapa("Cita")
        SelectorOrto(
            citaDe(b.citaId)?.let { textoCitaOrto(it) } ?: "Sin cita",
            listOf("" to "Sin cita") + elegibles.map { it.id to textoCitaOrto(it) },
        ) { elegirCita(it) }
        EtiquetaMapa("Fecha")
        CajaSelectorForm(fechaIndicacion(b.fecha ?: hoy)) { elegirFecha = true }
        if (profesionales.isNotEmpty()) {
            EtiquetaMapa("Atendió")
            SelectorOrto(
                profesionales.firstOrNull { it.id == b.terapeutaId }?.nombre ?: "—",
                listOf("" to "—") + profesionales.map { it.id to it.nombre },
            ) { b = b.copy(terapeutaId = it.ifBlank { null }) }
        }
        EtiquetaMapa("Fase")
        SegmentadoMapa(FASES_ORTO.map { it.id to it.corto }, b.fase, { v -> v?.let { b = b.copy(fase = it) } }, permiteVacio = false)
        if (b.fase != plan.fase) {
            Text("Al guardar, si es el último control, el plan pasa a «${faseOrtoPorId(b.fase)?.nombre}».",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // Arcos, ligaduras y cadeneta (aparatología fija)
        if (verArcos) {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
            ) {
                if (conSup) ChipsArco("Arco superior", b.arcoSuperior.orEmpty(), anterior?.arcoSuperior, todosArcos, { todosArcos = true }) {
                    b = b.copy(arcoSuperior = it)
                }
                if (conInf) ChipsArco("Arco inferior", b.arcoInferior.orEmpty(), anterior?.arcoInferior, todosArcos, { todosArcos = true }) {
                    b = b.copy(arcoInferior = it)
                }
                EtiquetaMapa("Ligaduras")
                SegmentadoMapa(LIGADURAS_ORTO.map { it.id to it.corto }, b.ligaduraTipo, { b = b.copy(ligaduraTipo = it) })
                if (b.ligaduraTipo == "elasticas") {
                    EtiquetaMapa("Color de las ligaduras")
                    val previo = anterior?.ligaduraColor
                    val colores = if (previo != null && previo !in COLORES_LIGADURA) listOf(previo) + COLORES_LIGADURA else COLORES_LIGADURA
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        colores.forEach { col ->
                            val activo = b.ligaduraColor == col
                            ChipMapa(if (previo == col) "= $col" else col, activo) { b = b.copy(ligaduraColor = if (activo) null else col) }
                        }
                    }
                }
                EtiquetaMapa("Cadeneta")
                SegmentadoMapa(CADENETAS_ORTO.map { it.id to it.nombre }, b.cadeneta, { b = b.copy(cadeneta = it) })
            }
        } else {
            Text("＋ Arcos, ligaduras y cadeneta", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.heightIn(min = 44.dp).clickable { verArcos = true }.padding(vertical = 12.dp))
        }

        // Elásticos intermaxilares
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ELÁSTICOS INTERMAXILARES", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (b.elasticos.size < LimitesOrtodoncia.ELASTICOS) BotonMapa("＋ Elástico") { b = b.copy(elasticos = b.elasticos + ELASTICO_NUEVO) }
        }
        if (b.elasticos.isEmpty()) Text("Sin elásticos.", color = c.textoSuave, fontSize = 12.sp)
        b.elasticos.forEachIndexed { i, x ->
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Elástico ${i + 1}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("✕", color = c.textoSuave, fontSize = 15.sp,
                        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Quitar elástico ${i + 1}" }
                            .clickable { b = b.copy(elasticos = b.elasticos.filterIndexed { k, _ -> k != i }) }.padding(12.dp))
                }
                SegmentadoMapa(TIPOS_ELASTICO.map { it.id to it.corto }, x.tipo, { v -> v?.let { cambiarElastico(i) { e -> e.copy(tipo = it) } } }, permiteVacio = false)
                Spacer(Modifier.height(6.dp))
                SegmentadoMapa(LADOS_ELASTICO.map { it.id to it.nombre }, x.lado, { v -> v?.let { cambiarElastico(i) { e -> e.copy(lado = it) } } }, permiteVacio = false)
                Spacer(Modifier.height(6.dp))
                SegmentadoMapa(TAMANOS_ELASTICO.map { it to it }, x.tamano, { v -> cambiarElastico(i) { e -> e.copy(tamano = v) } })
                Spacer(Modifier.height(6.dp))
                SegmentadoMapa(FUERZAS_ELASTICO.map { it.id to it.nombre }, x.fuerza, { v -> cambiarElastico(i) { e -> e.copy(fuerza = v) } })
                Spacer(Modifier.height(6.dp))
                SegmentadoMapa(HORAS_ELASTICO.map { it.first.toString() to it.second }, x.horas?.toString(),
                    { v -> cambiarElastico(i) { e -> e.copy(horas = v?.toIntOrNull()) } })
            }
        }

        // Brackets despegados / recementados
        if (verPiezas) {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
            ) {
                EtiquetaMapa("Brackets despegados" + if (b.bracketsDespegados.isNotEmpty()) " (${b.bracketsDespegados.joinToString(", ")})" else "")
                SelectorPiezasFdi("Brackets despegados", b.bracketsDespegados, { b = b.copy(bracketsDespegados = it) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        EtiquetaMapa("Recementados" + if (b.bracketsRecementados.isNotEmpty()) " (${b.bracketsRecementados.joinToString(", ")})" else "")
                    }
                    if (b.bracketsDespegados.isNotEmpty()) {
                        Text("= los despegados", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.heightIn(min = 44.dp).clickable { b = b.copy(bracketsRecementados = b.bracketsDespegados) }
                                .padding(horizontal = 6.dp, vertical = 12.dp))
                    }
                }
                SelectorPiezasFdi("Brackets recementados", b.bracketsRecementados, { b = b.copy(bracketsRecementados = it) }, marcadas = b.bracketsDespegados)
            }
        } else {
            Text("＋ Brackets despegados / recementados", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.heightIn(min = 44.dp).clickable { verPiezas = true }.padding(vertical = 12.dp))
        }

        EtiquetaMapa("Higiene")
        SegmentadoMapa(NIVELES_ORTO.map { it.id to it.nombre }, b.higiene, { b = b.copy(higiene = it) })
        if (hayElasticos) {
            EtiquetaMapa("Usa los elásticos")
            SegmentadoMapa(NIVELES_ORTO.map { it.id to it.nombre }, b.cooperacionElasticos, { b = b.copy(cooperacionElasticos = it) })
        }
        EtiquetaMapa("Dolor / molestias")
        SegmentadoMapa(DOLORES_ORTO.map { it.id to it.nombre }, b.dolor, { b = b.copy(dolor = it) })

        Spacer(Modifier.height(10.dp))
        CampoTextoClinico(
            label = "Observaciones (internas)", valor = b.observaciones.orEmpty(),
            onChange = { b = b.copy(observaciones = it.take(LimitesOrtodoncia.OBSERVACIONES)) },
            frases = chipsObs, soloLectura = false, minLineas = 2,
            placeholder = "Ej.: se activó el arco; refiere molestias leves.",
            porLinea = frasesObs.porLinea, opciones = frasesObs.opciones, chipsVisibles = 8,
        )

        EtiquetaMapa("Próxima cita en")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SEMANAS_CONTROL_ORTO.forEach { s -> ChipMapa("$s sem.", b.proximaSemanas == s) { b = b.copy(proximaSemanas = s) } }
        }
        val sem = b.proximaSemanas
        val f = b.fecha
        if (sem != null && f != null) {
            Text("≈ ${fechaIndicacion(sumarDiasOrto(f, sem * 7))}", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        CajaErroresMapa(errores)
    }

    if (elegirFecha) DialogoFecha(onElegir = { b = b.copy(fecha = it) }, onCerrar = { elegirFecha = false }, inicial = b.fecha ?: hoy)
}

/** Chips de un arco: "= el anterior", el siguiente sugerido y los comunes, más el texto libre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsArco(
    titulo: String,
    valor: String,
    previo: String?,
    todos: Boolean,
    onVerTodos: () -> Unit,
    onChange: (String?) -> Unit,
) {
    val c = Sania.colors
    val sugerido = siguienteArco(previo)
    val comunes = if (todos) ARCOS_COMUNES else ARCOS_COMUNES.take(ARCOS_A_LA_VISTA)
    val resto = comunes.filter { !mismoArco(it, previo) && !mismoArco(it, sugerido) }
    fun alternar(a: String) = onChange(if (mismoArco(valor, a)) null else a)
    EtiquetaMapa(titulo)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        previo?.takeIf { it.isNotBlank() }?.let { p -> ChipMapa("= $p", mismoArco(valor, p)) { alternar(p) } }
        sugerido?.let { s -> ChipMapa("→ $s", mismoArco(valor, s), colorActivo = c.ok) { alternar(s) } }
        resto.forEach { a -> ChipMapa(a, mismoArco(valor, a)) { alternar(a) } }
        if (!todos) {
            Text("+ acero / TMA", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onVerTodos).padding(horizontal = 6.dp, vertical = 12.dp))
        }
    }
    OutlinedTextField(
        value = valor, onValueChange = { onChange(it.take(LimitesOrtodoncia.ARCO).ifEmpty { null }) },
        placeholder = { Text("Otro (ej. NiTi térmico .016×.025)", color = c.textoSuave, fontSize = 13.sp) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = coloresCampoForm(),
    )
}

/** Fila con aviso "✓ guardado" + acciones (cuidados con brackets). */
@Composable
internal fun AvisoCuidadosBrackets(onDejar: () -> Unit, onCerrar: () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.okBg)
            .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓ Control de la instalación guardado", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("✕", color = c.textoSuave, fontSize = 14.sp,
                modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Cerrar aviso" }
                    .clickable(onClick = onCerrar).padding(horizontal = 12.dp, vertical = 12.dp))
        }
        FilaBotonesMapa { BotonMapa("📝 Dejar los cuidados con brackets", onClick = onDejar) }
    }
}

/** Aviso amarillo genérico de la pestaña. */
@Composable
internal fun AvisoOrto(texto: String) {
    val c = Sania.colors
    AvisoMapa(texto, c.pend, c.pendBg)
}
