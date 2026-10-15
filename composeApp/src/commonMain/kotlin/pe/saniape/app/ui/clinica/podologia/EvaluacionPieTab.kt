package pe.saniape.app.ui.clinica.podologia

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import pe.saniape.app.data.staff.BorradorEvaluacionPodo
import pe.saniape.app.data.staff.DEFORMIDADES_PODO
import pe.saniape.app.data.staff.ESTADOS_PULSO
import pe.saniape.app.data.staff.EvaluacionPodologica
import pe.saniape.app.data.staff.EvaluacionPodologicaRepo
import pe.saniape.app.data.staff.FraseFrecuenteApp
import pe.saniape.app.data.staff.FrasesCampoPlan
import pe.saniape.app.data.staff.HIGIENES
import pe.saniape.app.data.staff.HallazgoMarcado
import pe.saniape.app.data.staff.LimitesEvalPodo
import pe.saniape.app.data.staff.OpcionPodo
import pe.saniape.app.data.staff.PIES_PODO
import pe.saniape.app.data.staff.PIE_DER
import pe.saniape.app.data.staff.PISADAS
import pe.saniape.app.data.staff.PULSOS_PODO
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.SEVERIDADES_PODO
import pe.saniape.app.data.staff.SITIOS_MONOFILAMENTO
import pe.saniape.app.data.staff.SugerenciasPodo
import pe.saniape.app.data.staff.TIPOS_PIE
import pe.saniape.app.data.staff.TamizajePodo
import pe.saniape.app.data.staff.ZONAS_PODO
import pe.saniape.app.data.staff.ZonaMarcadaPodo
import pe.saniape.app.data.staff.categoriaIwgdf
import pe.saniape.app.data.staff.compararEvaluacionesPodo
import pe.saniape.app.data.staff.diabeticoPorAntecedentes
import pe.saniape.app.data.staff.estadoRiesgo
import pe.saniape.app.data.staff.evaluacionPodoDeJson
import pe.saniape.app.data.staff.fechaControlParaAgendar
import pe.saniape.app.data.staff.fechaEvalPodo
import pe.saniape.app.data.staff.frasesCampoPodo
import pe.saniape.app.data.staff.hallazgoPodo
import pe.saniape.app.data.staff.hallazgosParaZona
import pe.saniape.app.data.staff.hayUlceraActiva
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.htmlEvaluacionPodo
import pe.saniape.app.data.staff.lineasTamizaje
import pe.saniape.app.data.staff.nombrePie
import pe.saniape.app.data.staff.nombreSeleccionPie
import pe.saniape.app.data.staff.normalizarTamizaje
import pe.saniape.app.data.staff.normalizarZonasPodo
import pe.saniape.app.data.staff.ordenarEvaluacionesPodo
import pe.saniape.app.data.staff.paresDatosGenerales
import pe.saniape.app.data.staff.riesgoIwgdf
import pe.saniape.app.data.staff.tamizajeInicialPodo
import pe.saniape.app.data.staff.tamizajeTieneDatos
import pe.saniape.app.data.staff.textoCambioPodo
import pe.saniape.app.data.staff.textoZonaMarcada
import pe.saniape.app.data.staff.validarEvaluacionPodo
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.atencion.CampoTextoClinico
import pe.saniape.app.ui.clinica.mapas.AvisoMapa
import pe.saniape.app.ui.clinica.mapas.BotonMapa
import pe.saniape.app.ui.clinica.mapas.CajaErroresMapa
import pe.saniape.app.ui.clinica.mapas.ChipMapa
import pe.saniape.app.ui.clinica.mapas.EtiquetaMapa
import pe.saniape.app.ui.clinica.mapas.FilaBotonesMapa
import pe.saniape.app.ui.clinica.mapas.FilaEtiquetada
import pe.saniape.app.ui.clinica.mapas.PildoraMapa
import pe.saniape.app.ui.clinica.mapas.SegmentadoMapa
import pe.saniape.app.ui.clinica.mapas.TarjetaMapa
import pe.saniape.app.ui.clinica.mapas.TituloSeccionMapa
import pe.saniape.app.ui.clinica.mapas.colorHex
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/** El editor: cerrado, una evaluación nueva o la que se edita (por id). */
private sealed interface EditorPie {
    data object Nueva : EditorPie
    data class Editar(val id: String) : EditorPie
}

/**
 * Pestaña 🦶 Evaluación del pie de la ficha (solo pacientes de podología:
 * `pacienteEsPodologia`). Gemelo de `EvaluacionPieTab.tsx` de la web:
 *
 *  · Lista: la última abierta (riesgo IWGDF, cambios vs la anterior, mapa en
 *    solo lectura, tamizaje, datos generales); las anteriores plegadas, cada
 *    una contra la suya anterior. Arriba, la evolución del riesgo.
 *  · "＋ Nueva evaluación": mapa del pie (tocar zona → hallazgos con
 *    severidad, o "Elegir la zona por nombre"), tamizaje de pie diabético con
 *    el riesgo EN VIVO (la misma regla que el servidor) y datos generales con
 *    chips y autocompletado que aprende. Precarga de la anterior lo que casi no
 *    cambia (diabetes, deformidades, antecedentes, tipo de pie, pisada, calzado).
 *  · Tras guardar: 🖨 Informe (hoja nativa), 📅 Agendar próximo control (fecha
 *    por la categoría; con el riesgo SIN DETERMINAR se ofrece completar el
 *    tamizaje, nunca el control anual) y, con riesgo ≥ 2, 📝 cuidado del pie diabético.
 *
 * Carga SOLO cuando la pestaña se abre (la monta el padre en su `when`). Sin
 * listas perezosas: vive dentro del `verticalScroll` de la ficha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvaluacionPieTab(
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    /** Antecedentes y diagnóstico: "Paciente diabético" por defecto en la primera evaluación. */
    antecedentes: String?,
    diagnostico: String?,
    /** `puede('pacientes') || puede('sesiones')` y ficha no dada de baja (como el API). */
    puedeEditar: Boolean,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    clinicaId: String?,
    /** "📅 Agendar próximo control" con la fecha de la categoría (null = no se ofrece). */
    onAgendarControl: ((fecha: String, ev: EvaluacionPodologica) -> Unit)? = null,
    /** "📝 Indicaciones: cuidado del pie diabético" (riesgo ≥ 2; null = no se ofrece). */
    onIndicacionesPieDiabetico: (() -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<EvaluacionPodologica>?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pacienteId, token) {
        val r = EvaluacionPodologicaRepo.listar(pacienteId)
        if (r.items != null) { items = r.items; error = null } else if (items == null) error = r.error
        cargando = false
    }
    // Quién evaluó (terapeuta_id → nombre), como `profesionales` en la web.
    var profesionales by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        profesionales = runCatching { PacientesRepo.terapeutasActivos().associate { it.id to it.nombre } }.getOrDefault(emptyMap())
    }
    var editor by remember { mutableStateOf<EditorPie?>(null) }
    var recien by remember { mutableStateOf<String?>(null) }
    var confirmarBorrar by remember { mutableStateOf<EvaluacionPodologica?>(null) }
    var borrando by remember { mutableStateOf(false) }

    if (cargando && items == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 220, 110).forEach { h ->
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            }
        }
        return
    }
    error?.let { msg ->
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { error = null; cargando = true; token++ }.padding(8.dp))
        }
        return
    }

    val lista = items.orEmpty()
    val hoy = hoyClinicaIso()
    fun profesionalDe(e: EvaluacionPodologica) = e.terapeutaId?.let { profesionales[it] }
    fun imprimir(e: EvaluacionPodologica) {
        val i = lista.indexOfFirst { it.id == e.id }
        val anterior = if (i >= 0) lista.getOrNull(i + 1) else null
        acciones.abrirHtml(
            htmlEvaluacionPodo(e, anterior, clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal, pacienteNombre, pacienteDocumento, profesionalDe(e)),
            "Evaluación del pie",
        )
    }
    val guardada = recien?.takeIf { editor == null }?.let { id -> lista.firstOrNull { it.id == id } }
    val conRiesgo = lista.filter { it.riesgoIwgdf != null }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🦶 Evaluación del pie", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)

        guardada?.let { g ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.okBg)
                    .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Evaluación guardada", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("✕", color = c.textoSuave, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { recien = null }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                val est = estadoRiesgo(g)
                if (est.categoria != null || est.sinDeterminar) {
                    Box(Modifier.padding(top = 6.dp)) { BadgeRiesgo(est.categoria, est.incompleto, est.sinDeterminar) }
                }
                Spacer(Modifier.height(8.dp))
                FilaBotonesMapa {
                    BotonMapa("🖨 Informe / PDF") { imprimir(g) }
                    AccionesRiesgo(g, hoy, onAgendarControl, onIndicacionesPieDiabetico,
                        onCompletar = if (puedeEditar) ({ recien = null; editor = EditorPie.Editar(g.id) }) else null)
                }
            }
        }

        if (lista.isNotEmpty()) {
            if (conRiesgo.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("RIESGO:", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val ult = conRiesgo.reversed().takeLast(6)
                    ult.forEachIndexed { i, e ->
                        val cat = categoriaIwgdf(e.riesgoIwgdf)
                        val col = cat?.let { colorHex(it.color) } ?: c.textoSuave
                        PildoraMapa("${e.riesgoIwgdf}", col, col.copy(alpha = 0.14f))
                        Text(fechaEvalPodo(e.fecha) + if (i < ult.size - 1) " →" else "", color = c.textoSuave, fontSize = 11.sp)
                    }
                }
            }
            if (puedeEditar) BotonMapa("＋ Nueva evaluación", lleno = true) { recien = null; editor = EditorPie.Nueva }
        }

        if (lista.isEmpty()) {
            TarjetaMapa {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🦶", fontSize = 30.sp)
                    Text("Aún no hay evaluaciones del pie", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Mapa del pie con los hallazgos por zona, tamizaje de pie diabético con su riesgo (IWGDF 2023) y cada cuánto controlar. Queda el historial para comparar visita a visita.",
                        color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    if (puedeEditar) {
                        Spacer(Modifier.height(12.dp))
                        BotonMapa("＋ Primera evaluación", lleno = true) { editor = EditorPie.Nueva }
                    }
                }
            }
        } else {
            lista.forEachIndexed { i, ev ->
                key(ev.id) {
                    TarjetaEvaluacion(
                        ev = ev, anterior = lista.getOrNull(i + 1), actual = i == 0, hoy = hoy,
                        profesional = profesionalDe(ev), puedeEditar = puedeEditar && editor == null,
                        onAgendarControl = if (i == 0) onAgendarControl else null,
                        onIndicacionesPieDiabetico = if (i == 0) onIndicacionesPieDiabetico else null,
                        onImprimir = { imprimir(ev) },
                        onEditar = { recien = null; editor = EditorPie.Editar(ev.id) },
                        onBorrar = { confirmarBorrar = ev },
                    )
                }
            }
        }
    }

    when (val m = editor) {
        null -> Unit
        else -> {
            val enEdicion = (m as? EditorPie.Editar)?.let { e -> lista.firstOrNull { it.id == e.id } }
            if (m is EditorPie.Editar && enEdicion == null) LaunchedEffect(m) { editor = null }
            else key(m) {
                val idx = enEdicion?.let { e -> lista.indexOfFirst { it.id == e.id } } ?: -1
                EditorEvaluacionPie(
                    pacienteId = pacienteId, hoy = hoy, evaluacion = enEdicion,
                    anterior = if (enEdicion == null) lista.firstOrNull() else lista.getOrNull(idx + 1),
                    diabeticoSugerido = diabeticoPorAntecedentes(antecedentes, diagnostico),
                    clinicaId = clinicaId,
                    onCerrar = { editor = null },
                    onGuardado = { ev, nueva ->
                        editor = null
                        recien = ev.id
                        items = ordenarEvaluacionesPodo(if (nueva) listOf(ev) + lista else lista.map { if (it.id == ev.id) ev else it })
                    },
                )
            }
        }
    }

    confirmarBorrar?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!borrando) confirmarBorrar = null },
            title = { Text("¿Eliminar esta evaluación?", fontWeight = FontWeight.Bold) },
            text = { Text("Evaluación del pie del ${fechaEvalPodo(p.fecha)}", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    // Dos toques en el mismo frame: `enabled` recién aplica en la siguiente recomposición.
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = EvaluacionPodologicaRepo.borrar(p.id)
                        borrando = false
                        if (r.registrada || r.rechazo?.codigo == "NO_ENCONTRADO") {
                            confirmarBorrar = null
                            items = lista.filter { it.id != p.id }
                            if (recien == p.id) recien = null
                            Toaster.exito("Evaluación eliminada")
                        } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !borrando, onClick = { confirmarBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ─── El riesgo ───────────────────────────────────────────────────────────────

/**
 * Badge del riesgo IWGDF con la categoría y el control. Sin determinar: ámbar,
 * SIN control (nunca "anual" sin haber descartado LOPS y EAP).
 */
@Composable
internal fun BadgeRiesgo(categoria: Int?, incompleto: Boolean, sinDeterminar: Boolean, compacto: Boolean = false) {
    val c = Sania.colors
    val (color, texto, detalle) = if (sinDeterminar) Triple(c.pend, "🦶 Riesgo sin determinar", "completa el tamizaje (monofilamento y pulsos)")
    else {
        val cat = categoriaIwgdf(categoria) ?: return
        Triple(colorHex(cat.color), "🦶 Riesgo IWGDF ${cat.categoria} · ${cat.nombre}${if (incompleto) " (tamizaje incompleto)" else ""}",
            "control ${cat.control.lowercase()}")
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f))
            .border(2.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = if (compacto) 8.dp else 12.dp, vertical = if (compacto) 2.dp else 6.dp),
    ) {
        Text(texto, color = color, fontSize = if (compacto) 11.sp else 13.sp, fontWeight = FontWeight.Bold)
        if (!compacto) Text("· $detalle", color = c.texto, fontSize = 11.sp)
    }
}

/** "📅 Agendar próximo control" / "📝 Indicaciones" o, sin determinar, "🩺 Completar el tamizaje". */
@Composable
private fun AccionesRiesgo(
    ev: EvaluacionPodologica, hoy: String,
    onAgendarControl: ((String, EvaluacionPodologica) -> Unit)?,
    onIndicacionesPieDiabetico: (() -> Unit)?,
    onCompletar: (() -> Unit)?,
) {
    if (estadoRiesgo(ev).sinDeterminar) {
        onCompletar?.let { BotonMapa("🩺 Completar el tamizaje", onClick = it) }
        return
    }
    val fecha = fechaControlParaAgendar(ev, hoy)
    if (fecha != null && onAgendarControl != null) BotonMapa("📅 Agendar próximo control · ${fechaEvalPodo(fecha)}") { onAgendarControl(fecha, ev) }
    if ((ev.riesgoIwgdf ?: 0) >= 2 && onIndicacionesPieDiabetico != null) {
        BotonMapa("📝 Indicaciones: cuidado del pie diabético", onClick = onIndicacionesPieDiabetico)
    }
}

// ─── Una evaluación (lectura) con su comparativo ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaEvaluacion(
    ev: EvaluacionPodologica,
    anterior: EvaluacionPodologica?,
    actual: Boolean,
    hoy: String,
    profesional: String?,
    puedeEditar: Boolean,
    onAgendarControl: ((String, EvaluacionPodologica) -> Unit)?,
    onIndicacionesPieDiabetico: (() -> Unit)?,
    onImprimir: () -> Unit,
    onEditar: () -> Unit,
    onBorrar: () -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(actual) }
    var vista by remember { mutableStateOf("plantar") }
    val zonas = ev.zonas
    val est = estadoRiesgo(ev)
    val ulcera = hayUlceraActiva(zonas)
    TarjetaMapa {
        Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🦶 Evaluación del ${fechaEvalPodo(ev.fecha)}", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (actual) PildoraMapa("Última", c.ok, c.okBg)
                    if (!abierto && (est.categoria != null || est.sinDeterminar)) BadgeRiesgo(est.categoria, est.incompleto, est.sinDeterminar, compacto = true)
                    if (ulcera) PildoraMapa("Úlcera activa", c.error, c.errorBg)
                }
                Text(
                    (if (zonas.isNotEmpty()) "${zonas.size} zona${if (zonas.size == 1) "" else "s"} con hallazgos" else "Sin hallazgos en el mapa") +
                        (profesional?.let { " · $it" }.orEmpty()),
                    color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(if (abierto) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        if (!abierto) return@TarjetaMapa
        val comp = compararEvaluacionesPodo(ev, anterior)
        val riesgo = riesgoIwgdf(ev.tamizaje)
        val tamizaje = lineasTamizaje(ev.tamizaje)
        val datos = paresDatosGenerales(ev.tipoPie, ev.pisada, ev.higiene, ev.calzado)
        val conDorsal = zonas.any { it.vista == "dorsal" }
        val conPlantar = zonas.any { it.vista == "plantar" }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ulcera) AvisoMapa("🚨 Úlcera activa: no es un control de rutina — tratar, descargar la zona y derivar si hay signos de infección o isquemia.",
                c.error, c.errorBg)
            if (est.categoria != null || est.sinDeterminar) {
                Column {
                    BadgeRiesgo(est.categoria, est.incompleto, est.sinDeterminar)
                    val cambio = comp.riesgo.cambio
                    if (cambio != null && cambio != "igual") {
                        Text("${if (cambio == "subio") "▲ subió" else "▼ bajó"} desde ${comp.riesgo.antes}",
                            color = if (cambio == "subio") c.error else c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    riesgo.motivos.forEach { Text("• $it", color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
            if (anterior != null && comp.cambios.isNotEmpty()) {
                Column {
                    EtiquetaMapa("Cambios desde el ${fechaEvalPodo(anterior.fecha)}")
                    comp.cambios.forEach { ch ->
                        val sube = ch.cambio == "nuevo" || ch.cambio == "empeoro"
                        Text("${if (sube) "▲" else "▼"} ${textoCambioPodo(ch)}", color = if (sube) c.error else c.ok, fontSize = 12.sp)
                    }
                }
            }
            if (anterior != null && comp.cambios.isEmpty() && zonas.isNotEmpty()) {
                Text("Mismos hallazgos que el ${fechaEvalPodo(anterior.fecha)}.", color = c.textoSuave, fontSize = 12.sp)
            }
            if (zonas.isNotEmpty()) {
                Column {
                    if (conPlantar && conDorsal) ToggleVistaPie(vista) { vista = it }
                    Spacer(Modifier.height(6.dp))
                    MapaPie(zonas, if (conPlantar && conDorsal) vista else if (conDorsal) "dorsal" else "plantar", ancho = 120.dp)
                    zonas.forEach { Text("• ${textoZonaMarcada(it)}", color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
            if (tamizaje.isNotEmpty()) {
                Column {
                    EtiquetaMapa("Tamizaje de pie diabético")
                    tamizaje.forEach { Text(it, color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp)) }
                }
            }
            if (datos.isNotEmpty()) {
                Column { datos.forEach { (e, v) -> Text("$e: $v", color = c.texto, fontSize = 12.sp) } }
            }
            ev.observaciones?.let { Text("Observaciones: $it", color = c.texto, fontSize = 12.sp) }
            FilaBotonesMapa {
                BotonMapa("🖨 Informe", onClick = onImprimir)
                if (puedeEditar) {
                    AccionesRiesgo(ev, hoy, onAgendarControl, onIndicacionesPieDiabetico, onCompletar = onEditar)
                    BotonMapa("✏️ Editar", onClick = onEditar)
                    BotonMapa("🗑️", onClick = onBorrar)
                }
            }
        }
    }
}

@Composable
private fun ToggleVistaPie(vista: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SegmentadoMapa(listOf("plantar" to "👣 Plantar", "dorsal" to "🦶 Dorsal"), vista, { it?.let(onChange) }, permiteVacio = false)
    }
}

// ─── Editor (nueva o editar) ─────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorEvaluacionPie(
    pacienteId: String,
    hoy: String,
    /** La que se edita (null = nueva). */
    evaluacion: EvaluacionPodologica?,
    /** La anterior: precarga lo que casi no cambia y "Copiar mapa anterior". */
    anterior: EvaluacionPodologica?,
    diabeticoSugerido: Boolean,
    clinicaId: String?,
    onCerrar: () -> Unit,
    onGuardado: (EvaluacionPodologica, Boolean) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val nueva = evaluacion == null
    var fecha by remember { mutableStateOf(evaluacion?.fecha?.takeIf { it.isNotBlank() } ?: hoy) }
    var elegirFecha by remember { mutableStateOf(false) }
    var zonas by remember { mutableStateOf(evaluacion?.zonas.orEmpty()) }
    var tam by remember { mutableStateOf(evaluacion?.tamizaje?.let { normalizarTamizaje(it) } ?: tamizajeInicialPodo(anterior, diabeticoSugerido)) }
    var tipoPie by remember { mutableStateOf(if (evaluacion != null) evaluacion.tipoPie else anterior?.tipoPie) }
    var pisada by remember { mutableStateOf(if (evaluacion != null) evaluacion.pisada else anterior?.pisada) }
    var higiene by remember { mutableStateOf(evaluacion?.higiene) }
    var calzado by remember { mutableStateOf(if (evaluacion != null) evaluacion.calzado.orEmpty() else anterior?.calzado.orEmpty()) }
    var observaciones by remember { mutableStateOf(evaluacion?.observaciones.orEmpty()) }
    var vista by remember { mutableStateOf("plantar") }
    var sel by remember { mutableStateOf<Pair<String, String>?>(null) }
    var verTodos by remember { mutableStateOf(false) }
    var porNombre by remember { mutableStateOf(false) }
    var piePorNombre by remember { mutableStateOf(PIE_DER) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var sugerencias by remember { mutableStateOf<SugerenciasPodo?>(null) }
    LaunchedEffect(Unit) { sugerencias = EvaluacionPodologicaRepo.sugerencias(clinicaId) }

    val riesgo = riesgoIwgdf(tam)
    val anteriorZonas = anterior?.zonas.orEmpty()
    val zonaSel = sel?.let { s -> zonas.firstOrNull { it.pie == s.first && it.zona == s.second } }

    fun seleccionar(pie: String, zona: String) {
        verTodos = false
        sel = if (sel?.first == pie && sel?.second == zona) null else pie to zona
    }
    fun editarZona(f: (List<HallazgoMarcado>) -> List<HallazgoMarcado>) {
        val s = sel ?: return
        val base = zonas.firstOrNull { it.pie == s.first && it.zona == s.second }
            ?: ZonaMarcadaPodo(s.first, vista, s.second, emptyList())
        val hs = f(base.hallazgos)
        val resto = zonas.filter { !(it.pie == s.first && it.zona == s.second) }
        zonas = normalizarZonasPodo(if (hs.isNotEmpty()) resto + base.copy(hallazgos = hs) else resto)
    }
    fun toggleHallazgo(tipo: String) = editarZona { hs -> if (hs.any { it.tipo == tipo }) hs.filter { it.tipo != tipo } else hs + HallazgoMarcado(tipo, 1) }
    fun ponerSeveridad(tipo: String, s: Int) = editarZona { hs -> hs.map { if (it.tipo == tipo) it.copy(severidad = s) else it } }
    fun setT(f: (TamizajePodo) -> TamizajePodo) { tam = f(tam) }

    fun guardar() {
        if (guardando) return
        val b = BorradorEvaluacionPodo(
            pacienteId = pacienteId, fecha = fecha.ifBlank { hoy }, zonas = zonas,
            // No diabético y nada registrado = sin tamizaje (así "evaluación vacía" se detecta).
            tamizaje = tam.takeIf { tamizajeTieneDatos(it) },
            tipoPie = tipoPie, pisada = pisada, higiene = higiene,
            calzado = calzado.trim().ifEmpty { null }, observaciones = observaciones.trim().ifEmpty { null },
        )
        val e = validarEvaluacionPodo(b)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = EvaluacionPodologicaRepo.guardar(b, evaluacion?.id)
            guardando = false
            val fila = (r.cuerpo?.get("evaluacion") as? JsonObject)?.let(::evaluacionPodoDeJson)
            if (r.registrada && fila != null) {
                EvaluacionPodologicaRepo.olvidarSugerencias()
                Toaster.exito(if (nueva) "Evaluación guardada" else "Evaluación actualizada")
                onGuardado(fila, nueva)
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = if (nueva) "Nueva evaluación del pie" else "Editar evaluación del pie",
        subtitulo = "🦶 Mapa, tamizaje de pie diabético y datos generales",
        textoAccion = if (guardando) "Guardando…" else if (nueva) "Guardar evaluación" else "Guardar cambios",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtqForm("Fecha")
        CajaSelectorForm(fechaEvalPodo(fecha)) { elegirFecha = true }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { setT { it.copy(diabetes = !it.diabetes) } }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("🩸 Paciente diabético", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Activa el tamizaje de pie diabético y su riesgo (IWGDF 2023).", color = c.textoSuave, fontSize = 11.sp)
            }
            Switch(checked = tam.diabetes, onCheckedChange = { v -> setT { it.copy(diabetes = v) } },
                colors = SwitchDefaults.colors(checkedTrackColor = c.navy, checkedThumbColor = c.sobreNavy))
        }

        // ── 1. Mapa del pie ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TituloSeccionMapa("1 · Mapa del pie") }
            if (nueva && anteriorZonas.isNotEmpty() && zonas.isEmpty()) {
                Text("↺ Copiar mapa anterior", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp).clickable { zonas = normalizarZonasPodo(anteriorZonas) }.padding(8.dp))
            }
        }
        ToggleVistaPie(vista) { vista = it; sel = null }
        Text("Como si estuviera frente al paciente · toca una zona", color = c.textoSuave, fontSize = 11.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp))
        MapaPie(zonas, vista, sel, onSeleccionar = { p, z -> seleccionar(p, z) })
        LeyendaPie()

        sel?.let { s ->
            val hz = hallazgosParaZona(s.second)
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg)
                    .border(2.dp, c.lav, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍 ${nombreSeleccionPie(s.first, s.second)}", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("Listo", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.heightIn(min = 44.dp).clickable { sel = null }.padding(horizontal = 10.dp, vertical = 12.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (hz.sugeridos + if (verTodos) hz.otros else emptyList()).forEach { h ->
                        val puesto = zonaSel?.hallazgos?.any { it.tipo == h.id } == true
                        ChipMapa((if (puesto) "✓ " else "") + h.nombre, puesto) { toggleHallazgo(h.id) }
                    }
                    if (!verTodos && hz.otros.isNotEmpty()) {
                        Text("+ ${hz.otros.size} más", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.heightIn(min = 44.dp).clickable { verTodos = true }.padding(horizontal = 8.dp, vertical = 12.dp))
                    }
                }
                zonaSel?.hallazgos?.forEach { h ->
                    FilaEtiquetada(hallazgoPodo(h.tipo)?.nombre ?: h.tipo) {
                        SegmentadoMapa(
                            SEVERIDADES_PODO.map { it.first.toString() to it.second }, h.severidad.toString(),
                            { v -> v?.toIntOrNull()?.let { ponerSeveridad(h.tipo, it) } },
                            colores = mapOf("1" to colorHex("#d97706"), "2" to colorHex("#ea580c"), "3" to colorHex("#dc2626")),
                            permiteVacio = false,
                        )
                    }
                }
            }
        }

        // Elegir la zona por nombre: las chicas (dedos, uñas, espacios) son difíciles de tocar en el celular.
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(horizontal = 10.dp),
        ) {
            Text((if (porNombre) "▾ " else "▸ ") + "Elegir la zona por nombre", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { porNombre = !porNombre }.padding(vertical = 12.dp))
            if (porNombre) {
                SegmentadoMapa(PIES_PODO.map { it to nombrePie(it) }, piePorNombre, { v -> v?.let { piePorNombre = it } }, permiteVacio = false)
                FlowRow(Modifier.padding(top = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZONAS_PODO.filter { it.vista == vista }.forEach { z ->
                        val activa = sel?.first == piePorNombre && sel?.second == z.id
                        val marcada = zonas.any { it.pie == piePorNombre && it.zona == z.id }
                        ChipMapa((if (marcada) "● " else "") + z.nombre, activa) { verTodos = false; sel = piePorNombre to z.id }
                    }
                }
            }
        }

        if (hayUlceraActiva(zonas)) {
            Box(Modifier.padding(top = 10.dp)) {
                AvisoMapa("🚨 Hay una úlcera activa en el mapa: tratar, descargar la zona y derivar si hay infección o isquemia.", c.error, c.errorBg, negrita = true)
            }
        }
        if (zonas.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                zonas.forEach { z ->
                    Text(textoZonaMarcada(z), color = c.texto, fontSize = 12.sp,
                        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { vista = z.vista; sel = z.pie to z.zona }.padding(horizontal = 10.dp, vertical = 12.dp))
                }
            }
        }

        // ── 2. Tamizaje de pie diabético ──
        TituloSeccionMapa("2 · Tamizaje de pie diabético")
        if (!tam.diabetes) {
            Text("Solo para pacientes con diabetes. Activa «Paciente diabético» arriba si corresponde.", color = c.textoSuave, fontSize = 12.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { EtiquetaMapa("Monofilamento 10 g") }
                Text("✓ Todos sienten", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).clickable { setT { it.copy(monofilamento = SITIOS_MONOFILAMENTO.associate { s -> s.id to true }) } }
                        .padding(horizontal = 8.dp, vertical = 12.dp))
            }
            PIES_PODO.forEach { pie ->
                CajaPie(nombrePie(pie)) {
                    SITIOS_MONOFILAMENTO.filter { it.pie == pie }.forEach { s ->
                        FilaEtiquetada(s.nombre) {
                            SegmentadoMapa(
                                listOf("si" to "✓ Siente", "no" to "✗ No siente"),
                                tam.monofilamento[s.id]?.let { if (it) "si" else "no" },
                                { v -> setT { it.copy(monofilamento = it.monofilamento + (s.id to v?.let { x -> x == "si" })) } },
                                colores = mapOf("si" to c.ok, "no" to colorHex("#dc2626")),
                            )
                        }
                    }
                    FilaEtiquetada("Vibración 128 Hz (opcional)") {
                        SegmentadoMapa(
                            listOf("presente" to "Presente", "ausente" to "Ausente"), tam.vibracion[pie],
                            { v -> setT { it.copy(vibracion = it.vibracion + (pie to v)) } },
                            colores = mapOf("presente" to c.ok, "ausente" to colorHex("#dc2626")),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { EtiquetaMapa("Pulsos") }
                Text("✓ Todos presentes", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).clickable { setT { it.copy(pulsos = PULSOS_PODO.associate { p -> p.id to "presente" }) } }
                        .padding(horizontal = 8.dp, vertical = 12.dp))
            }
            PIES_PODO.forEach { pie ->
                CajaPie(nombrePie(pie)) {
                    PULSOS_PODO.filter { it.pie == pie }.forEach { p ->
                        FilaEtiquetada(p.nombre) {
                            SegmentadoMapa(ESTADOS_PULSO, tam.pulsos[p.id], { v -> setT { it.copy(pulsos = it.pulsos + (p.id to v)) } },
                                colores = mapOf("presente" to c.ok, "disminuido" to colorHex("#d97706"), "ausente" to colorHex("#dc2626")))
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { setT { it.copy(eapDiagnosticada = !it.eapDiagnosticada) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = tam.eapDiagnosticada, onCheckedChange = { v -> setT { it.copy(eapDiagnosticada = v) } },
                    colors = CheckboxDefaults.colors(checkedColor = c.navy))
                Text("Enfermedad arterial periférica ya diagnosticada (Doppler / ITB)", color = c.texto, fontSize = 12.sp)
            }
            EtiquetaMapa("Deformidades")
            ChipsMultiples(DEFORMIDADES_PODO, tam.deformidades) { id ->
                setT { it.copy(deformidades = if (id in it.deformidades) it.deformidades - id else it.deformidades + id) }
            }
            EtiquetaMapa("Antecedentes de alto riesgo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipMapa((if (tam.antecedenteUlcera) "✓ " else "") + "Úlcera previa en el pie", tam.antecedenteUlcera) { setT { it.copy(antecedenteUlcera = !it.antecedenteUlcera) } }
                ChipMapa((if (tam.amputacion) "✓ " else "") + "Amputación (menor o mayor)", tam.amputacion) { setT { it.copy(amputacion = !it.amputacion) } }
                ChipMapa((if (tam.erc) "✓ " else "") + "Enfermedad renal terminal / diálisis", tam.erc) { setT { it.copy(erc = !it.erc) } }
            }
            // El riesgo EN VIVO (la misma regla que el servidor al guardar).
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                BadgeRiesgo(riesgo.categoria, riesgo.categoria != null && !riesgo.completo, riesgo.sinDeterminar)
                categoriaIwgdf(riesgo.categoria)?.let { Text(it.criterio, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
                riesgo.motivos.forEach { Text("• $it", color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
                riesgo.avisos.forEach { Text("⚠ $it", color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }

        // ── 3. Datos generales ──
        TituloSeccionMapa("3 · Datos generales")
        EtiquetaMapa("Tipo de pie")
        SegmentadoMapa(TIPOS_PIE.map { it.id to it.nombre }, tipoPie, { tipoPie = it })
        EtiquetaMapa("Pisada")
        SegmentadoMapa(PISADAS.map { it.id to it.nombre }, pisada, { pisada = it })
        EtiquetaMapa("Higiene")
        SegmentadoMapa(HIGIENES.map { it.id to it.nombre }, higiene, { higiene = it })
        Spacer(Modifier.height(10.dp))
        CampoFrasesPodo("👟 Calzado", calzado, { calzado = it.take(LimitesEvalPodo.CALZADO) }, frasesCampoPodo("calzado", sugerencias),
            "Ej. Punta estrecha, sin medias", 1)
        Spacer(Modifier.height(10.dp))
        CampoFrasesPodo("📝 Observaciones (una por línea)", observaciones, { observaciones = it.take(LimitesEvalPodo.OBSERVACIONES) },
            frasesCampoPodo("observaciones", sugerencias), "Ej. Piel seca, se corta los callos en casa", 3)

        if (riesgo.aplica) Box(Modifier.padding(top = 12.dp)) {
            BadgeRiesgo(riesgo.categoria, riesgo.categoria != null && !riesgo.completo, riesgo.sinDeterminar, compacto = true)
        }
        CajaErroresMapa(errores)
    }

    if (elegirFecha) DialogoFecha(
        onElegir = { f -> fecha = if (f > hoy) hoy else f },
        onCerrar = { elegirFecha = false }, inicial = fecha,
    )
}

@Composable
private fun CajaPie(titulo: String, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
    ) {
        Text(titulo, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        contenido()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsMultiples(opciones: List<OpcionPodo>, elegidos: List<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { o ->
            val activo = o.id in elegidos
            ChipMapa((if (activo) "✓ " else "") + o.nombre, activo) { onToggle(o.id) }
        }
    }
}

/** Campo con chips y autocompletado que aprende (el de la consulta guiada, gemelo de CampoConFrases). */
@Composable
private fun CampoFrasesPodo(
    label: String, valor: String, onChange: (String) -> Unit, frases: FrasesCampoPlan, placeholder: String, minLineas: Int,
) {
    val chips = remember(frases) { frases.chips.map { FraseFrecuenteApp("podologia", it.texto, 0) } }
    CampoTextoClinico(
        label = label, valor = valor, onChange = onChange, frases = chips, soloLectura = false,
        minLineas = minLineas, placeholder = placeholder, porLinea = frases.porLinea,
        opciones = frases.opciones, chipsVisibles = 6,
    )
}

/** Gemelo de `tipoDeEntrada`: la cita de entrada del flujo (Consulta → Evaluación → Sesión). */
fun tipoEntradaFlujo(usaConsulta: Boolean, usaEvaluacion: Boolean): String =
    if (usaConsulta) "Consulta" else if (usaEvaluacion) "Evaluación" else "Sesión"
