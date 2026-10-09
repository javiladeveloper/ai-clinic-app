package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.ExamenSolicitado
import pe.saniape.app.data.staff.RecetaBreve
import pe.saniape.app.data.staff.ServicioPlan
import pe.saniape.app.data.staff.fechaLegibleCorta
import pe.saniape.app.data.staff.formatearNumeroReceta
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.isoAMillisUtc
import pe.saniape.app.data.staff.sumarDiasIso
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.ArchivoSeleccionado
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.agenda.modales.textoSoles
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.recordarSelectorArchivo
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.tutoriales.tourAncla

// ─────────────────────────────────────────────────────────────────────────────
// PASO "PLAN" de la consulta guiada (gemelo de PlanAtencion.tsx): desde el MISMO
// lugar el médico emite la receta, pide exámenes (o adjunta el resultado), indica
// un procedimiento (con su consentimiento pendiente, que se genera solo) y
// programa el control. Todo queda ligado a esta cita/atención.
//
// Las acciones que crean filas (control, procedimiento, resultado) guardan antes
// (vm.accionPlan) y corren en el scope del VM: cambiar de paso no las corta. Su
// "ocupado" también vive en el VM (vm.accionando): una a la vez, y salir del
// paso y volver no rehabilita el botón de una que sigue enviando.
// ─────────────────────────────────────────────────────────────────────────────

/** Tope de exámenes por atención (MAX_EXAMENES de lib/atencion-medica.ts). */
internal const val MAX_EXAMENES = 30

/** Los más pedidos en consulta externa (EXAMENES_FRECUENTES_MEDICINA de la web). */
internal val EXAMENES_FRECUENTES_MEDICINA = listOf(
    "Hemograma completo", "Glucosa en ayunas", "Perfil lipídico", "Creatinina", "Urea",
    "Examen completo de orina", "Urocultivo", "Perfil hepático", "Hemoglobina glicosilada (HbA1c)",
    "TSH", "Proteína C reactiva", "Grupo sanguíneo y factor Rh", "Prueba rápida VIH / sífilis",
    "Radiografía de tórax", "Ecografía abdominal", "Electrocardiograma",
)

/** EXAMENES_FRECUENTES_ODONTOLOGIA de la web. */
internal val EXAMENES_FRECUENTES_ODONTOLOGIA = listOf(
    "Radiografía periapical", "Radiografía panorámica", "Radiografía bite-wing", "Tomografía cone beam",
    "Hemograma completo", "Tiempo de coagulación y sangría", "Glucosa en ayunas",
)

/** Resultados de examen: foto o PDF, hasta 15 MB (lo mismo que valida el servidor). */
private const val MAX_BYTES_RESULTADO = 15 * 1024 * 1024

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PasoPlan(
    vm: AtencionViewModel,
    d: DatosConsultaApp,
    soloLectura: Boolean,
    acciones: AccionesNativas,
    onNuevaReceta: () -> Unit,
) {
    val esProc = d.flags.esProcedimiento
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d.flags.recetasAplica) BloqueReceta(vm, d, soloLectura, acciones, onNuevaReceta)
        if (!esProc) BloqueExamenes(vm, d, soloLectura)
        if (!esProc) BloqueProcedimiento(vm, d, soloLectura)
        BloqueControl(vm, d, soloLectura)
        Bloque {
            CampoTextoClinico(
                label = "Indicaciones para el ${LocalTerminologiaPaciente.current.paciente}",
                valor = vm.borrador.textos["tratamiento"].orEmpty(), onChange = { vm.texto("tratamiento", it) },
                frases = frasesDe(d, "indicaciones"), soloLectura = soloLectura, minLineas = 3,
                placeholder = "Medidas generales: reposo, dieta, signos de alarma… (los medicamentos van en la receta)",
                porLinea = true,
            )
            if (!esProc) {
                Spacer(Modifier.height(12.dp))
                CampoTextoClinico(
                    label = "Plan de trabajo (interconsultas, referencia…)",
                    valor = vm.borrador.textos["plan_trabajo"].orEmpty(), onChange = { vm.texto("plan_trabajo", it) },
                    frases = emptyList(), soloLectura = soloLectura, minLineas = 2,
                    placeholder = "Opcional",
                )
            }
        }
    }
}

// ── 💊 Receta ────────────────────────────────────────────────────────────────

@Composable
private fun BloqueReceta(
    vm: AtencionViewModel,
    d: DatosConsultaApp,
    soloLectura: Boolean,
    acciones: AccionesNativas,
    onNuevaReceta: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var abriendo by remember { mutableStateOf<String?>(null) }

    fun imprimir(r: RecetaBreve) {
        if (abriendo != null) return
        abriendo = r.id
        scope.launch {
            val html = AtencionRepo.htmlImprimible("receta", r.id)
            abriendo = null
            if (html != null) acciones.abrirHtml(html, "Receta ${formatearNumeroReceta(r.numero)}")
            else Toaster.error("No se pudo abrir la receta. Revisa tu conexión.")
        }
    }

    Bloque {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TituloBloque("💊 Receta", Modifier.weight(1f))
            if (!soloLectura) Column(Modifier.tourAncla("consulta.receta")) {
                BotonChico(
                    if (vm.accionando == "receta") "Guardando…" else "📝 Emitir receta", c.sobreNavy, c.navy,
                    habilitado = vm.accionando == null,
                ) {
                    // Guarda antes: la receta se prellena con el diagnóstico y las indicaciones.
                    vm.lanzar("receta") {
                        if (vm.guardarAntes()) onNuevaReceta()
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (d.recetas.isEmpty()) {
            Texto("Sin receta en esta atención. Se prellena con el ${LocalTerminologiaPaciente.current.paciente} y el diagnóstico.")
        } else {
            d.recetas.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatearNumeroReceta(r.numero), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (r.estado == "Anulada") {
                        Insignia("Anulada", c.textoSuave, c.chipBg)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(r.fecha.takeIf { it.isNotBlank() }?.let { fechaLegibleCorta(it) }.orEmpty(),
                        color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    BotonChico(if (abriendo == r.id) "Abriendo…" else "🖨 Imprimir", c.navy, c.superficie, borde = c.borde,
                        habilitado = abriendo == null) { imprimir(r) }
                }
            }
        }
    }
}

// ── 🧪 Exámenes auxiliares ───────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BloqueExamenes(vm: AtencionViewModel, d: DatosConsultaApp, soloLectura: Boolean) {
    val c = Sania.colors
    val examenes = vm.borrador.examenes
    var nuevo by remember { mutableStateOf("") }
    // A qué examen va el archivo que se está eligiendo (el selector devuelve solo el archivo).
    var indicePendiente by remember { mutableStateOf<Int?>(null) }
    val frecuentes = if (d.flags.dental) EXAMENES_FRECUENTES_ODONTOLOGIA else EXAMENES_FRECUENTES_MEDICINA

    fun agregar(nombre: String) {
        val n = nombre.trim()
        if (n.length < 2 || examenes.size >= MAX_EXAMENES || examenes.any { it.nombre.equals(n, ignoreCase = true) }) return
        vm.examenes(examenes + ExamenSolicitado(nombre = n))
        nuevo = ""
    }

    fun adjuntar(i: Int, a: ArchivoSeleccionado) {
        val mime = mimeResultado(a)
        if (mime == null) { Toaster.error("Solo se adjunta una foto o un PDF."); return }
        if (a.bytes.size > MAX_BYTES_RESULTADO) { Toaster.error("El archivo supera 15 MB."); return }
        vm.lanzar("subir:$i") {
            var cuerpo: JsonObject? = null
            val ok = vm.accionPlan {
                AtencionRepo.adjuntarResultado(d.cita.id, i, a.bytes, a.nombre, mime, null).also { cuerpo = it.cuerpo }
            }
            if (ok) {
                // La lista con el documento ligado: sin esto el próximo guardar lo pisaría.
                (AtencionRepo.examenesDeRespuesta(cuerpo) ?: vm.datos?.atencion?.examenes)?.let { vm.examenesDelServidor(it) }
                Toaster.exito("Resultado adjuntado a la historia")
            }
        }
    }

    val elegirArchivo = recordarSelectorArchivo { a ->
        val i = indicePendiente ?: return@recordarSelectorArchivo
        indicePendiente = null
        adjuntar(i, a)
    }

    Bloque {
        TituloBloque("🧪 Exámenes auxiliares")
        Spacer(Modifier.height(8.dp))
        if (!soloLectura) {
            val sugeridos = frecuentes.filter { f -> examenes.none { it.nombre == f } }.take(10)
            if (sugeridos.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sugeridos.forEach { f -> ChipAgregar("+ $f") { agregar(f) } }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = nuevo, onValueChange = { nuevo = it.take(120) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Otro examen (ej. Ferritina)", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { agregar(nuevo) }),
                    colors = coloresCampoForm(), shape = RoundedCornerShape(Sania.shape.sm.dp),
                )
                Spacer(Modifier.width(8.dp))
                BotonChico("Agregar", c.navy, c.superficie, borde = c.borde, habilitado = nuevo.trim().length >= 2) { agregar(nuevo) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (examenes.isEmpty()) {
            Texto("Ninguno pedido.")
        } else {
            examenes.forEachIndexed { i, e ->
                // Clave por fila (índice + nombre): el estado de una fila no se cruza con el de otro examen.
                key(i, e.nombre) {
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (!soloLectura && e.documento_id == null) {
                                TextButton(onClick = { vm.examenes(examenes.filterIndexed { j, _ -> j != i }) }) {
                                    Text("Quitar", color = c.error, fontSize = 12.sp)
                                }
                            }
                        }
                        if (!soloLectura) {
                            OutlinedTextField(
                                value = e.indicacion.orEmpty(),
                                onValueChange = { v -> vm.examenes(examenes.mapIndexed { j, x -> if (j == i) x.copy(indicacion = v.take(200)) else x }) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("Indicación (ayuno 8 h…)", fontSize = 13.sp) },
                                colors = coloresCampoForm(), shape = RoundedCornerShape(Sania.shape.sm.dp),
                            )
                        } else if (!e.indicacion.isNullOrBlank()) {
                            Text("(${e.indicacion})", color = c.textoSuave, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        when {
                            e.documento_id != null -> Text(
                                "📎 Resultado adjunto" + (e.fecha_resultado?.let { " · ${fechaLegibleCorta(it)}" } ?: "") +
                                    (e.resultado?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                                color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                            !soloLectura -> BotonChico(
                                if (vm.accionando == "subir:$i") "Subiendo…" else "📎 Adjuntar resultado", c.navy, c.superficie, borde = c.borde,
                                habilitado = vm.accionando == null,
                            ) {
                                indicePendiente = i
                                elegirArchivo()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("La orden se imprime en el cierre, junto con las indicaciones.", color = c.textoSuave, fontSize = 11.sp)
        }
    }
}

/** El tipo del archivo si es foto o PDF (por el mime o, si no vino, por la extensión). null = no se acepta. */
internal fun mimeResultado(a: ArchivoSeleccionado): String? {
    val m = a.mime?.lowercase()
    if (m != null && (m.startsWith("image/") || m == "application/pdf")) return m
    val ext = a.nombre.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        else -> null
    }
}

// ── 🩹 Procedimiento ─────────────────────────────────────────────────────────

/** Especialidad de la cita (la suya o la de su servicio), como `esp` de la web. */
private fun especialidadCita(d: DatosConsultaApp): String? =
    d.cita.especialidad_id ?: d.cita.tratamiento?.procedimiento?.especialidad_id

private val EMPIEZA_CONSULTA_O_CONTROL = Regex("^(consulta|control)", RegexOption.IGNORE_CASE)
private val DICE_CONTROL = Regex("control", RegexOption.IGNORE_CASE)

/**
 * Procedimientos que se pueden indicar: los servicios de la especialidad de la
 * cita (o sin especialidad) que NO son la consulta de entrada (sin `tipo_cita`
 * y que no se llamen "Consulta…"/"Control…"). Misma regla que el servidor.
 */
internal fun procedimientosDelPlan(d: DatosConsultaApp): List<ServicioPlan> {
    val esp = especialidadCita(d)
    return d.servicios.filter { s ->
        s.tipo_cita.isNullOrBlank() && (esp == null || s.especialidad_id == null || s.especialidad_id == esp) &&
            !EMPIEZA_CONSULTA_O_CONTROL.containsMatchIn(s.nombre)
    }
}

/** El servicio con que se agenda el control (solo se muestra: lo resuelve el servidor). */
internal fun servicioControlDe(d: DatosConsultaApp): ServicioPlan? {
    val esp = especialidadCita(d)
    val tipo = if (d.cita.tipo == "Evaluación") "Evaluación" else "Consulta"
    return d.servicios.firstOrNull { DICE_CONTROL.containsMatchIn(it.nombre) && (esp == null || it.especialidad_id == esp) }
        ?: d.servicios.firstOrNull { it.tipo_cita == tipo && (esp == null || it.especialidad_id == esp) }
}

/** "Masaje · S/ 80.00", con la moneda de la sede de la cita (PEN en un solo local, como siempre). */
private fun conPrecio(s: ServicioPlan, d: DatosConsultaApp): String =
    s.nombre + (s.precio?.takeIf { it > 0 }?.let { " · ${textoSoles(it, pe.saniape.app.data.staff.monedaDeFila(d.cita.sede_id))}" } ?: "")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BloqueProcedimiento(vm: AtencionViewModel, d: DatosConsultaApp, soloLectura: Boolean) {
    val c = Sania.colors
    val hoy = remember { hoyClinicaIso() }
    val procedimientos = remember(d.servicios, d.cita) { procedimientosDelPlan(d) }
    var procId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var cuando by remember { mutableStateOf("hoy") }
    var fecha by remember { mutableStateOf(sumarDiasIso(hoy, 1)) }
    var hora by remember { mutableStateOf(horaPorDefecto(d)) }
    val elegido = procedimientos.firstOrNull { it.id == procId }
    val requiereCi = elegido?.plantillas?.any { it.activo } == true
    // Como la web: el texto de todos los diagnósticos, separados por "; ".
    val dxTexto = vm.borrador.diagnosticos.joinToString("; ") { it.descripcion }

    fun indicar() {
        val s = elegido ?: run { Toaster.error("Elige el procedimiento"); return }
        val programar = cuando == "programar"
        vm.lanzar("indicar") {
            var cuerpo: JsonObject? = null
            val ok = vm.accionPlan {
                AtencionRepo.indicarProcedimiento(
                    d.cita.id, s.id, if (programar) "programar" else "hoy",
                    if (programar) fecha else null, if (programar) hora else null, dxTexto,
                ).also { cuerpo = it.cuerpo }
            }
            if (ok) {
                val cis = (cuerpo?.get("consentimientos") as? JsonPrimitive)?.intOrNull ?: 0
                Toaster.exito(if (cis > 0) "${s.nombre} indicado · consentimiento listo para imprimir y firmar" else "${s.nombre} indicado")
                (cuerpo?.get("avisoConsentimiento") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?.let { Toaster.info(it) }
                procId = null
            }
        }
    }

    Bloque {
        TituloBloque("🩹 Procedimiento")
        Spacer(Modifier.height(8.dp))
        d.indicados.forEach { t ->
            val cita = t.citas.minByOrNull { it.fecha + (it.hora ?: "") }
            Column(
                Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.fondo).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(t.procedimiento?.nombre?.ifBlank { null } ?: "Procedimiento", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (cita != null) {
                    val cuandoTxt = (if (cita.fecha == hoy) "hoy" else fechaLegibleCorta(cita.fecha)) +
                        (cita.hora?.takeIf { it.isNotBlank() }?.let { " ${hora12(it)}" } ?: "")
                    Text("$cuandoTxt · ${if (cita.estado == "Completada") "realizado" else "pendiente"}", color = c.textoSuave, fontSize = 12.sp)
                }
                val cis = t.consentimientos_informados.filter { it.estado != "Anulado" }
                if (cis.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        cis.forEach { ci ->
                            val (fg, bg) = when (ci.estado) {
                                "Pendiente" -> c.pend to c.pendBg
                                "Firmado" -> c.ok to c.okBg
                                "Rechazado" -> c.error to c.errorBg
                                "Revocado" -> c.purple to c.purpleBg
                                else -> c.textoSuave to c.chipBg
                            }
                            Insignia("CI: ${NOMBRE_ESTADO_CI[ci.estado] ?: ci.estado}", fg, bg)
                        }
                    }
                }
            }
        }
        if (d.indicados.isEmpty() && soloLectura) Texto("Ninguno indicado.")
        if (!soloLectura) {
            EtqForm("Procedimiento (servicio del catálogo)")
            Box {
                CajaSelectorForm(elegido?.let { conPrecio(it, d) } ?: "— Elegir —") { if (procedimientos.isNotEmpty()) menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    procedimientos.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(conPrecio(s, d) + if (s.plantillas.any { it.activo }) " · requiere consentimiento" else "",
                                    fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            onClick = { procId = s.id; menu = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Segmento("Hoy, ahora", cuando == "hoy", Modifier.weight(1f)) { cuando = "hoy" }
                Segmento("Programar", cuando == "programar", Modifier.weight(1f)) { cuando = "programar" }
            }
            if (cuando == "programar") {
                Spacer(Modifier.height(8.dp))
                SelectorFechaHora(fecha = fecha, hora = hora, minimo = hoy, onFecha = { fecha = it }, onHora = { hora = it })
            }
            Spacer(Modifier.height(10.dp))
            BotonChico(if (vm.accionando == "indicar") "Indicando…" else "Indicar procedimiento", c.sobreNavy, c.navy,
                habilitado = vm.accionando == null && elegido != null) { indicar() }
            if (requiereCi) {
                Spacer(Modifier.height(6.dp))
                Text("Se generará el consentimiento informado pendiente de firma.", color = c.pend, fontSize = 12.sp)
            }
            if (procedimientos.isEmpty() && d.servicios.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Texto("No hay procedimientos en el catálogo: cárgalos en Configuración → Historia clínica.")
            }
        }
    }
}

// ── 📅 Próximo control ───────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BloqueControl(vm: AtencionViewModel, d: DatosConsultaApp, soloLectura: Boolean) {
    val c = Sania.colors
    val hoy = remember { hoyClinicaIso() }
    var fecha by remember { mutableStateOf(sumarDiasIso(hoy, 7)) }
    var hora by remember { mutableStateOf(horaPorDefecto(d)) }
    var motivo by remember { mutableStateOf("") }
    val control = d.atencion?.control
    val servicioControl = remember(d.servicios, d.cita) { servicioControlDe(d) }

    fun programar() {
        val f = fecha
        val h = hora
        val m = motivo
        vm.lanzar("control") {
            val ok = vm.accionPlan { AtencionRepo.control(d.cita.id, f, h, m) }
            if (ok) Toaster.exito("Control agendado para el ${fechaLegibleCorta(f)} a las ${hora12(h)}")
        }
    }

    Bloque {
        TituloBloque("📅 Próximo control")
        Spacer(Modifier.height(8.dp))
        when {
            control != null -> Text(
                "Agendado para el ${control.fecha?.let { fechaLegibleCorta(it) } ?: "—"}" +
                    (control.hora?.takeIf { it.isNotBlank() }?.let { " a las ${hora12(it)}" } ?: "") +
                    (control.estado?.takeIf { it.isNotBlank() }?.let { " (${it.lowercase()})" } ?: "") + ".",
                color = c.texto, fontSize = 13.sp,
            )
            soloLectura -> Texto("Sin control programado.")
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 7, 15, 30).forEach { n ->
                        val f = sumarDiasIso(hoy, n)
                        ChipAgregar("en $n días", activo = fecha == f) { fecha = f }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SelectorFechaHora(fecha = fecha, hora = hora, minimo = hoy, onFecha = { fecha = it }, onHora = { hora = it })
                Spacer(Modifier.height(8.dp))
                EtqForm("Motivo (opcional)")
                OutlinedTextField(
                    value = motivo, onValueChange = { motivo = it.take(200) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Ej. ver resultados, control de PA", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = coloresCampoForm(), shape = RoundedCornerShape(Sania.shape.sm.dp),
                )
                servicioControl?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Servicio: ${conPrecio(it, d)}", color = c.textoSuave, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                BotonChico(
                    if (vm.accionando == "control") "Agendando…" else "Agendar control", c.sobreNavy, c.navy,
                    habilitado = vm.accionando == null,
                ) { programar() }
            }
        }
    }
}

/** Hora con que arrancan el control y el procedimiento: la de la cita (vacía = sin hora) o 09:00. */
internal fun horaPorDefecto(d: DatosConsultaApp): String =
    (d.cita.hora?.takeIf { it.isNotBlank() } ?: "09:00").take(5)

// ── Piezas ───────────────────────────────────────────────────────────────────

/** Fecha y hora tocables con sus pickers (como ModalPasarEvaluacion). [minimo] = no antes de ese día. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectorFechaHora(
    fecha: String,
    hora: String,
    minimo: String?,
    onFecha: (String) -> Unit,
    onHora: (String) -> Unit,
) {
    val c = Sania.colors
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }

    if (mostrarFecha) {
        val min = isoAMillisUtc(minimo)
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = isoAMillisUtc(fecha),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = min == null || utcTimeMillis >= min
            },
        )
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                TextButton(onClick = { estado.selectedDateMillis?.let { onFecha(millisAIsoPlan(it)) }; mostrarFecha = false }) {
                    Text("Aceptar", color = c.navy)
                }
            },
            dismissButton = { TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estado) }
    }
    if (mostrarHora) {
        val p = hora.split(":")
        val estado = rememberTimePickerState(p.getOrNull(0)?.toIntOrNull() ?: 9, p.getOrNull(1)?.toIntOrNull() ?: 0, false)
        DatePickerDialog(
            onDismissRequest = { mostrarHora = false },
            confirmButton = {
                TextButton(onClick = {
                    onHora("${estado.hour.toString().padStart(2, '0')}:${estado.minute.toString().padStart(2, '0')}")
                    mostrarHora = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarHora = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) { TimePicker(state = estado) } }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            EtqForm("Fecha")
            CajaSelectorForm("📅 ${fechaLegibleCorta(fecha)}") { mostrarFecha = true }
        }
        Column(Modifier.weight(1f)) {
            EtqForm("Hora")
            CajaSelectorForm("🕐 ${hora12(hora)}") { mostrarHora = true }
        }
    }
}

private fun millisAIsoPlan(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}

/** Tarjeta de un bloque del plan / cierre (borde suave, como `bloqueCls` de la web). */
@Composable
internal fun Bloque(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).border(1.dp, c.borde, forma).background(c.superficie).padding(14.dp),
        content = contenido,
    )
}

@Composable
internal fun TituloBloque(texto: String, modifier: Modifier = Modifier) {
    Text(texto, color = Sania.colors.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
internal fun Texto(texto: String) {
    Text(texto, color = Sania.colors.textoSuave, fontSize = 13.sp)
}

@Composable
private fun ChipAgregar(texto: String, activo: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Box(
        Modifier.heightIn(min = 32.dp).clip(forma).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp) }
}

@Composable
private fun Segmento(texto: String, activo: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box(
        modifier.heightIn(min = 40.dp).clip(forma).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (activo) c.sobreNavy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}
