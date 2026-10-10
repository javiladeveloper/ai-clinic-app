package pe.saniape.app.ui.clinica.atencion

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.BorradorInforme
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DOC_DESCANSO
import pe.saniape.app.data.staff.DOC_INFORME
import pe.saniape.app.data.staff.DOC_ORDEN
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.data.staff.ExamenAprendido
import pe.saniape.app.data.staff.ExamenOrden
import pe.saniape.app.data.staff.FirmanteDoc
import pe.saniape.app.data.staff.InformesMedicosRepo
import pe.saniape.app.data.staff.MAX_DIAS_ATRAS_DOC
import pe.saniape.app.data.staff.MAX_EXAMENES_ORDEN
import pe.saniape.app.data.staff.PrefillInforme
import pe.saniape.app.data.staff.TIPOS_DOC_MEDICO
import pe.saniape.app.data.staff.cuerpoEmitirInforme
import pe.saniape.app.data.staff.diasDescanso
import pe.saniape.app.data.staff.filtrarExamenes
import pe.saniape.app.data.staff.firmanteInicial
import pe.saniape.app.data.staff.firmantesDocumento
import pe.saniape.app.data.staff.formatearNumeroInforme
import pe.saniape.app.data.staff.hastaPorDias
import pe.saniape.app.data.staff.iconoTipoDoc
import pe.saniape.app.data.staff.nombreTipoDoc
import pe.saniape.app.data.staff.normalizarExamenesOrden
import pe.saniape.app.data.staff.restarDias
import pe.saniape.app.data.staff.sugerenciasExamenes
import pe.saniape.app.data.staff.titulosSugeridos
import pe.saniape.app.data.staff.validarInforme
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// "📄 Informe médico", "🛌 Descanso médico" y "🧪 Orden de exámenes" — gemelo de
// components/informes/InformeMedicoForm.tsx. Se prellena con la atención y el
// médico EDITA todo antes de emitir. Emitido no se edita: se anula y se emite
// otro. Emite por POST /api/staff/informe/emitir (el mismo endpoint de la web):
// ahí y en la RLS se valida que firme el propio profesional o el Admin, que sea
// médico u odontólogo con colegiatura y la fecha (hoy − 30 … hoy).
// ─────────────────────────────────────────────────────────────────────────────

/** Días que se ofrecen de un toque en el descanso (los de la web). */
private val DIAS_DESCANSO_RAPIDOS = listOf(1, 2, 3, 5, 7, 15, 30)

/**
 * El formulario de un documento médico. [hoy] = la fecha en la zona de la sede
 * (de la cita en la consulta; la activa en la ficha). [onEmitido] recibe el id
 * del documento y su número ("DM N° 000003") para abrir la impresión.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoInformeMedico(
    ctx: ContextoStaff,
    tipoInicial: String,
    pacienteId: String,
    pacienteNombre: String,
    prefill: PrefillInforme?,
    hoy: String,
    onCancelar: () -> Unit,
    onEmitido: (id: String, numeroTexto: String) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val p = prefill ?: PrefillInforme()
    val esAdmin = ctx.esAdmin

    // ── Quién firma (se carga al abrir; null = cargando, falló = vacío con aviso) ──
    var equipo by remember { mutableStateOf<List<FirmanteDoc>?>(null) }
    var falloEquipo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val r = InformesMedicosRepo.equipoFirmantes()
        falloEquipo = r == null
        equipo = r.orEmpty()
    }
    val firmantes = remember(equipo) { firmantesDocumento(equipo.orEmpty(), esAdmin, ctx.miTerapeutaId) }
    var terapeutaId by remember { mutableStateOf("") }
    LaunchedEffect(equipo) {
        if (equipo != null && terapeutaId.isEmpty()) terapeutaId = firmanteInicial(firmantes, esAdmin, ctx.miTerapeutaId, p.terapeutaId)
    }

    var tipo by remember { mutableStateOf(tipoInicial) }
    var fecha by remember { mutableStateOf(hoy) }
    var titulo by remember { mutableStateOf("") }
    var dirigidoA by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf(p.motivo.orEmpty()) }
    var antecedentes by remember { mutableStateOf(p.antecedentes.orEmpty()) }
    var examen by remember { mutableStateOf(p.examen.orEmpty()) }
    var examenMental by remember { mutableStateOf(p.examenMental.orEmpty()) }
    var escalas by remember { mutableStateOf(p.escalas.orEmpty()) }
    var tratamiento by remember { mutableStateOf(p.tratamiento.orEmpty()) }
    var recomendaciones by remember { mutableStateOf(p.recomendaciones.orEmpty()) }
    var dx by remember { mutableStateOf<List<DiagnosticoCie>>(p.diagnosticos) }
    var ocultarDx by remember { mutableStateOf(false) }
    var desde by remember { mutableStateOf(hoy) }
    var hasta by remember { mutableStateOf(hastaPorDias(hoy, 3)) }
    var examenes by remember { mutableStateOf(normalizarExamenesOrden(p.examenes)) }
    var nuevoEx by remember { mutableStateOf("") }
    var indicaciones by remember { mutableStateOf("") }
    var seguimiento by remember { mutableStateOf(true) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var emitiendo by remember { mutableStateOf(false) }
    // Un intento = una clave: reintentar tras un corte no emite dos documentos ni gasta dos números.
    val clave = remember { nuevaClaveCliente() }
    // Qué selector de fecha está abierto: "fecha" | "desde" | "hasta".
    var calendario by remember { mutableStateOf<String?>(null) }

    val firmante = firmantes.firstOrNull { it.id == terapeutaId }
    val psiq = p.psiq || firmante?.psiquiatra == true
    val titulos = titulosSugeridos(tipo, psiq)

    // Exámenes: lo aprendido por la clínica + la base del rubro (una consulta, solo en la orden).
    var aprendidos by remember { mutableStateOf<List<ExamenAprendido>?>(null) }
    LaunchedEffect(tipo) { if (tipo == DOC_ORDEN && aprendidos == null) aprendidos = InformesMedicosRepo.examenesAprendidos() }
    val opcionesEx = remember(aprendidos, psiq) { sugerenciasExamenes(aprendidos.orEmpty(), psiq) }
    fun yaEsta(n: String) = examenes.any { it.nombre.equals(n, ignoreCase = true) }
    val chipsEx = opcionesEx.filterNot { yaEsta(it) }.take(14)
    val typeahead = filtrarExamenes(opcionesEx, nuevoEx).filterNot { yaEsta(it) }
    fun agregarEx(nombre: String) {
        val n = nombre.trim()
        if (n.length < 2 || examenes.size >= MAX_EXAMENES_ORDEN || yaEsta(n)) return
        examenes = examenes + ExamenOrden(n, "")
        nuevoEx = ""
    }

    val dias = diasDescanso(desde, hasta)

    fun emitir() {
        if (emitiendo) return
        val b = BorradorInforme(
            tipo = tipo, pacienteId = pacienteId, terapeutaId = terapeutaId.ifBlank { null }, fecha = fecha,
            titulo = titulo, dirigidoA = dirigidoA, motivo = motivo, antecedentes = antecedentes, examen = examen,
            examenMental = examenMental, escalas = escalas, tratamiento = tratamiento, recomendaciones = recomendaciones,
            diagnosticos = dx, ocultarDiagnostico = ocultarDx, descansoDesde = desde, descansoHasta = hasta,
            examenes = examenes, indicaciones = indicaciones,
            citaId = p.citaId, atencionId = p.atencionId, tratamientoId = p.tratamientoId,
        )
        val errs = validarInforme(b, hoy)
        errores = errs
        if (errs.isNotEmpty()) return
        emitiendo = true
        scope.launch {
            val r = AtencionRepo.emitirInforme(cuerpoEmitirInforme(b, clave, seguimiento = tipo == DOC_ORDEN && ctx.can("examenes") && seguimiento))
            emitiendo = false
            if (r.registrada) {
                val inf = r.cuerpo?.get("informe") as? JsonObject
                val id = (inf?.get("id") as? JsonPrimitive)?.contentOrNull.orEmpty()
                val numero = formatearNumeroInforme(tipo, (inf?.get("numero") as? JsonPrimitive)?.intOrNull)
                val repetido = (r.cuerpo?.get("repetido") as? JsonPrimitive)?.booleanOrNull == true
                Toaster.exito(if (repetido) "Ya se había emitido: $numero" else "${nombreTipoDoc(tipo)} emitido · $numero")
                val creadas = ((r.cuerpo?.get("seguimiento") as? JsonObject)?.get("creadas") as? JsonPrimitive)?.intOrNull ?: 0
                if (creadas > 0) Toaster.info("$creadas examen(es) en seguimiento (pestaña Exámenes)")
                onEmitido(id, numero)
            } else {
                val lista = r.rechazo?.error?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?.ifEmpty { null } ?: listOf("No se pudo emitir el documento.")
                errores = lista
                Toaster.error(lista.first())
            }
        }
    }

    DialogoForm(
        titulo = "${iconoTipoDoc(tipo)} ${nombreTipoDoc(tipo)}",
        subtitulo = pacienteNombre,
        textoAccion = if (emitiendo) "Emitiendo…" else "Emitir e imprimir",
        accionHabilitada = !emitiendo && terapeutaId.isNotBlank(),
        onCancelar = { if (!emitiendo) onCancelar() },
        onAccion = ::emitir,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (errores.isNotEmpty()) CajaAviso(errores.joinToString("\n") { "• $it" }, c.error, c.errorBg)

            // ── Tipo ──
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TIPOS_DOC_MEDICO.forEach { t ->
                    ChipDoc("${iconoTipoDoc(t)} ${nombreTipoDoc(t)}", activo = t == tipo) { tipo = t }
                }
            }

            // ── Quién firma ──
            Column {
                EtqForm("Profesional que firma y sella")
                when {
                    equipo == null -> CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                    falloEquipo -> Text("No se pudo cargar el equipo. Revisa tu conexión y vuelve a abrir.", color = c.error, fontSize = 13.sp)
                    firmantes.isEmpty() -> Text(
                        if (esAdmin) "Ningún médico u odontólogo tiene N° de colegiatura (CMP): complétalo en Equipo."
                        else "Solo un médico u odontólogo con N° de colegiatura (CMP) firma estos documentos: revisa tu ficha en Equipo.",
                        color = c.error, fontSize = 13.sp,
                    )
                    // El profesional firma solo a su nombre (el servidor también lo exige: PROFESIONAL_AJENO).
                    !esAdmin -> CajaFija(firmante?.let { "${it.nombre} · ${it.cmp.orEmpty()}" } ?: "—")
                    else -> SelectorDialogo("", terapeutaId, firmantes.map { it.id to "${it.nombre} · ${it.cmp.orEmpty()}" }) { terapeutaId = it }
                }
            }

            // ── Fecha, título, dirigido a ──
            Column {
                EtqForm("Fecha")
                CajaSelectorForm("📅 ${fechaDMA(fecha)}") { calendario = "fecha" }
                Text("Entre hoy y $MAX_DIAS_ATRAS_DOC días atrás.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Column {
                CampoDialogo("Título impreso", titulo, { titulo = it.take(120) }, titulos.first())
                Spacer(Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    titulos.forEach { t -> ChipDoc(t, activo = titulo == t) { titulo = t } }
                }
            }
            CampoDialogo("Dirigido a (opcional)", dirigidoA, { dirigidoA = it.take(200) }, "A quien corresponda")

            // ── Cuerpo del informe ──
            if (tipo == DOC_INFORME) {
                CampoDialogo("Motivo de consulta y enfermedad actual", motivo, { motivo = it },
                    "Motivo, tiempo de enfermedad y relato…", unaLinea = false, minLineas = 3)
                CampoDialogo("Antecedentes relevantes", antecedentes, { antecedentes = it },
                    "Personales, familiares, medicación habitual, alergias…", unaLinea = false, minLineas = 2)
                if (psiq || examenMental.isNotBlank()) {
                    CampoDialogo("Examen mental", examenMental, { examenMental = it },
                        "Porte, conciencia, atención, lenguaje, afecto, pensamiento, percepción, juicio, riesgo…", unaLinea = false, minLineas = 4)
                }
                CampoDialogo(if (psiq || examenMental.isNotBlank()) "Examen físico (opcional)" else "Examen y evaluación", examen, { examen = it },
                    "Examen físico y hallazgos relevantes…", unaLinea = false, minLineas = if (psiq) 2 else 4)
                if (psiq || escalas.isNotBlank()) {
                    CampoDialogo("Escalas aplicadas", escalas, { escalas = it },
                        "PHQ-9: 14 / 27 — Depresión moderada…", unaLinea = false, minLineas = 2)
                }
            }

            // ── Descanso: desde / hasta ──
            if (tipo == DOC_DESCANSO) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        EtqForm("Desde")
                        CajaSelectorForm(fechaDMA(desde)) { calendario = "desde" }
                    }
                    Column(Modifier.weight(1f)) {
                        EtqForm("Hasta (inclusive)")
                        CajaSelectorForm(fechaDMA(hasta)) { calendario = "hasta" }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DIAS_DESCANSO_RAPIDOS.forEach { d ->
                        ChipDoc("$d ${if (d == 1) "día" else "días"}", activo = dias == d) { hasta = hastaPorDias(desde, d) }
                    }
                }
                if (dias != null && dias > 0) {
                    Text("Descanso de $dias ${if (dias == 1) "día" else "días"}.", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Revisa las fechas.", color = c.error, fontSize = 13.sp)
                }
            }

            // ── Diagnóstico (en los tres) ──
            Column {
                EtqForm(if (tipo == DOC_ORDEN) "Diagnóstico presuntivo (CIE-10)" else "Diagnóstico (CIE-10)")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { ocultarDx = !ocultarDx },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(ocultarDx, { ocultarDx = it }, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                    Text("No imprimir el diagnóstico (a pedido del paciente)", color = c.texto, fontSize = 13.sp)
                }
                if (ocultarDx) {
                    Text("Se guarda en el documento, pero el papel dirá \"Reservado a solicitud del paciente\" (confidencialidad del acto médico).",
                        color = c.textoSuave, fontSize = 11.sp)
                }
                Spacer(Modifier.padding(top = 4.dp))
                EditorDiagnosticosCie(lista = dx, onLista = { dx = it }, soloLectura = false, psiq = psiq,
                    nota = "CIE-10 con tipo: Presuntivo, Definitivo o Repetido.")
            }

            // ── Orden: exámenes ──
            if (tipo == DOC_ORDEN) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EtqForm("Exámenes solicitados")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chipsEx.forEach { n -> ChipDoc("+ $n", activo = false) { agregarEx(n) } }
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoDialogo("Otro examen", nuevoEx, { nuevoEx = it }, "Escribe y elige", modifier = Modifier.weight(1f))
                        BotonChico("Agregar", c.navy, c.superficie, borde = c.borde, habilitado = nuevoEx.trim().length >= 2) {
                            agregarEx(typeahead.firstOrNull() ?: nuevoEx)
                        }
                    }
                    if (typeahead.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie),
                        ) {
                            typeahead.forEach { n ->
                                Text(n, color = c.texto, fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { agregarEx(n) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp))
                            }
                        }
                    }
                    if (examenes.isEmpty()) {
                        Text("Ninguno todavía.", color = c.textoSuave, fontSize = 13.sp)
                    } else {
                        examenes.forEachIndexed { i, e ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}. ${e.nombre}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f))
                                    Text("Quitar", color = c.error, fontSize = 12.sp,
                                        modifier = Modifier.clickable { examenes = examenes.filterIndexed { j, _ -> j != i } }.padding(6.dp))
                                }
                                CampoDialogo("Indicación", e.indicacion.orEmpty(),
                                    { v -> examenes = examenes.mapIndexed { j, x -> if (j == i) x.copy(indicacion = v.take(200)) else x } },
                                    "Ayuno 8 h, 12 h tras la última dosis…")
                            }
                        }
                    }
                    if (ctx.can("examenes")) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { seguimiento = !seguimiento },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(seguimiento, { seguimiento = it }, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                            Text("Hacer seguimiento de los resultados (aparecen en la pestaña Exámenes)", color = c.texto, fontSize = 13.sp)
                        }
                    } else {
                        Text("La orden se imprime en todos los planes. El seguimiento de resultados (y que el paciente los vea en su app) es del plan Plus.",
                            color = c.textoSuave, fontSize = 11.sp)
                    }
                }
            }

            if (tipo == DOC_INFORME) {
                CampoDialogo("Tratamiento e indicaciones", tratamiento, { tratamiento = it },
                    "Medicación, dosis y medidas indicadas…", unaLinea = false, minLineas = 3)
                CampoDialogo("Recomendaciones y plan", recomendaciones, { recomendaciones = it },
                    "Controles, psicoterapia, interconsultas, pronóstico…", unaLinea = false, minLineas = 3)
            }
            CampoDialogo(
                when (tipo) {
                    DOC_ORDEN -> "Indicaciones para el paciente"
                    DOC_DESCANSO -> "Indicaciones / observaciones (opcional)"
                    else -> "Observaciones (opcional)"
                },
                indicaciones, { indicaciones = it },
                if (tipo == DOC_ORDEN) "Ayuno de 8 a 12 horas, traer los resultados en el próximo control…" else "Opcional",
                unaLinea = false, minLineas = 2,
            )
            Text("Emitido no se edita: si sale mal, se anula y se emite otro.", color = c.textoSuave, fontSize = 11.sp)
        }
    }

    calendario?.let { campo ->
        DialogoFecha(
            inicial = when (campo) { "desde" -> desde; "hasta" -> hasta; else -> fecha },
            onCerrar = { calendario = null },
            onElegir = { f ->
                when (campo) {
                    "desde" -> {
                        // Mantiene la duración elegida al mover el inicio.
                        val n = diasDescanso(desde, hasta)?.takeIf { it > 0 } ?: 1
                        desde = f
                        hasta = hastaPorDias(f, n)
                    }
                    "hasta" -> hasta = f
                    else -> fecha = f.takeIf { it <= hoy && it >= restarDias(hoy, MAX_DIAS_ATRAS_DOC) } ?: run {
                        Toaster.error("La fecha debe estar entre hoy y $MAX_DIAS_ATRAS_DOC días atrás.")
                        fecha
                    }
                }
            },
        )
    }
}

@Composable
private fun ChipDoc(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Box(
        Modifier.heightIn(min = 32.dp).clip(forma).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun CajaFija(texto: String) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 13.dp),
    ) { Text(texto, color = c.texto, fontSize = 14.sp) }
}
