package pe.saniape.app.ui.clinica.fisio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.ESCALA_FUERZA
import pe.saniape.app.data.staff.ESTADOS_OBJETIVO
import pe.saniape.app.data.staff.EvaluacionFisio
import pe.saniape.app.data.staff.EvaluacionFisioRepo
import pe.saniape.app.data.staff.FilaComparativo
import pe.saniape.app.data.staff.ObjetivoBorrador
import pe.saniape.app.data.staff.ObjetivoTratamiento
import pe.saniape.app.data.staff.TESTS_FUNCIONALES
import pe.saniape.app.data.staff.TIPOS_EVALUACION
import pe.saniape.app.data.staff.TratamientoOrigen
import pe.saniape.app.data.staff.buscarMovimiento
import pe.saniape.app.data.staff.formatoValor
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.interpretar
import pe.saniape.app.data.staff.medidaTexto
import pe.saniape.app.data.staff.nombreTipoEvaluacion
import pe.saniape.app.data.staff.numJs
import pe.saniape.app.data.staff.regionesProbables
import pe.saniape.app.data.staff.resumenEvaluacionFisio
import pe.saniape.app.data.staff.sugerirObjetivos
import pe.saniape.app.data.staff.textoDelta
import pe.saniape.app.data.staff.textoZonas
import pe.saniape.app.data.staff.tieneDatos
import pe.saniape.app.data.staff.tratamientoDeEvaluacion
import pe.saniape.app.data.staff.tratamientoDeObjetivo
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña 📏 Evaluación de la ficha (solo FISIOTERAPIA). Gemelo de
 * `components/fisio/EvaluacionFisioTab.tsx`: comparativo inicial → última con deltas
 * en verde/rojo, mapa del dolor antes/ahora, objetivos con su avance (estado en un
 * toque) e historial; botón "Nueva reevaluación".
 *
 * Carga SOLO cuando la pestaña se abre (la monta el padre en su `when`): dos
 * consultas chicas por paciente, en paralelo.
 */

/** Un tratamiento del paciente, como lo necesita la pestaña. */
data class TratamientoEval(val id: String, val nombre: String, val citaOrigenId: String?)

private data class AbrirEval(val tipo: String, val tratamientoId: String?, val editar: EvaluacionFisio? = null)

private fun fechaCorta(f: String?): String {
    if (f.isNullOrBlank() || f.length < 10) return f ?: ""
    return "${f.substring(8, 10)}/${f.substring(5, 7)}/${f.substring(2, 4)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvaluacionFisioTab(
    pacienteId: String,
    tratamientos: List<TratamientoEval>,
    textoRegion: String?,
    /** `puede('sesiones')` y ficha no dada de baja (como la web). */
    puedeEditar: Boolean,
    miTerapeutaId: String?,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(0) }
    var cargando by remember { mutableStateOf(true) }
    var datos by remember { mutableStateOf<EvaluacionFisioRepo.DatosEvaluacion?>(null) }
    var fallo by remember { mutableStateOf(false) }
    LaunchedEffect(pacienteId, token) {
        val r = EvaluacionFisioRepo.cargar(pacienteId)
        if (r != null) { datos = r; fallo = false } else fallo = datos == null
        cargando = false
    }
    fun recargar() { token++ }

    var filtroTrat by remember { mutableStateOf<String?>(null) }
    var abierta by remember { mutableStateOf<String?>(null) }
    var abrir by remember { mutableStateOf<AbrirEval?>(null) }
    var confirmarBorrar by remember { mutableStateOf<EvaluacionFisio?>(null) }

    if (cargando && datos == null) {
        // Andamio en vez de un aro: la forma de lo que viene.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 150, 110).forEach { h ->
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            }
        }
        return
    }
    if (fallo) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No se pudo cargar la evaluación", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { cargando = true; recargar() }.padding(8.dp))
        }
        return
    }

    val evaluaciones = datos?.evaluaciones.orEmpty()
    val objetivos = datos?.objetivos.orEmpty()
    val origenes = remember(tratamientos) { tratamientos.map { TratamientoOrigen(it.id, it.citaOrigenId) } }
    fun nombreTrat(id: String?) = id?.let { t -> tratamientos.firstOrNull { it.id == t }?.nombre }
    val tratsConDatos = remember(evaluaciones, objetivos, origenes) {
        val ids = mutableSetOf<String>()
        evaluaciones.forEach { e -> tratamientoDeEvaluacion(e, origenes)?.let(ids::add) }
        objetivos.forEach { o -> tratamientoDeObjetivo(o, evaluaciones, origenes)?.let(ids::add) }
        tratamientos.filter { it.id in ids }
    }
    val r = remember(evaluaciones, objetivos, origenes, filtroTrat) {
        resumenEvaluacionFisio(evaluaciones, objetivos, origenes, filtroTrat)
    }
    val regiones = remember(textoRegion, r) { regionesProbables(textoRegion, r.ultima?.datos ?: r.inicial?.datos) }
    val tratamientoDefecto = filtroTrat ?: when {
        tratsConDatos.size == 1 -> tratsConDatos[0].id
        tratamientos.size == 1 -> tratamientos[0].id
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("📏 Evaluación fisioterapéutica", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (puedeEditar) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonTab("＋ ${if (evaluaciones.isNotEmpty()) "Nueva reevaluación" else "Evaluación inicial"}", lleno = true) {
                    abrir = AbrirEval(if (evaluaciones.isNotEmpty()) "reevaluacion" else "inicial", tratamientoDefecto)
                }
                if (evaluaciones.isNotEmpty()) BotonTab("De alta", lleno = false) { abrir = AbrirEval("alta", tratamientoDefecto) }
            }
        }

        if (tratsConDatos.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipEval("Todo", activo = filtroTrat == null) { filtroTrat = null }
                tratsConDatos.forEach { t -> ChipEval(t.nombre, activo = filtroTrat == t.id) { filtroTrat = t.id } }
            }
        }

        if (!r.hayDatos) {
            TarjetaTab {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📏", fontSize = 30.sp)
                    Text("Sin evaluaciones estructuradas todavía", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Al completar la Evaluación, abre “Evaluación estructurada”: EVA, mapa del dolor, rangos, fuerza, pruebas y un test funcional. Todo opcional. Con una reevaluación verás el avance aquí.",
                        color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            if (r.comparativo.isNotEmpty()) {
                TarjetaTab {
                    val desde = fechaCorta(r.comparativo.firstOrNull()?.fechaInicial ?: r.inicial?.fecha)
                    TituloTarjeta("Evolución", r.inicial?.let { " · $desde${r.ultima?.let { u -> " → ${fechaCorta(u.fecha)}" } ?: ""}" })
                    r.comparativo.forEachIndexed { i, f ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                        FilaDelta(f)
                    }
                    if (r.ultima == null) Text("Haz una reevaluación para ver el cambio.", color = c.textoSuave, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            r.zonasInicial?.let { zi ->
                TarjetaTab {
                    TituloTarjeta("Mapa del dolor", null)
                    if (r.zonasUltima != null) Text("Inicial", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    MapaCorporal(zi, ancho = 110.dp, leyenda = r.zonasUltima == null)
                    r.zonasUltima?.let { zu ->
                        Spacer(Modifier.height(10.dp))
                        Text("Última", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        MapaCorporal(zu, ancho = 110.dp, leyenda = false)
                    }
                }
            }
        }

        // ── Objetivos ──
        TarjetaTab {
            TituloTarjeta("🎯 Objetivos", if (r.objetivos.isNotEmpty()) " · ${r.objetivosLogrados} de ${r.objetivos.size} logrados" else null)
            if (r.objetivos.isNotEmpty()) {
                val avance by animateFloatAsState(r.objetivosLogrados.toFloat() / r.objetivos.size, tween(500), label = "avanceObj")
                Box(Modifier.fillMaxWidth().padding(bottom = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.borde)) {
                    Box(Modifier.fillMaxWidth(avance).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ok))
                }
            }
            ObjetivosGuardados(
                objetivos = r.objetivos, pacienteId = pacienteId, tratamientoId = tratamientoDefecto,
                nombreTratamiento = if (filtroTrat != null || tratsConDatos.size <= 1) null else { id -> nombreTrat(id) },
                puedeEditar = puedeEditar, regiones = regiones, onCambio = { recargar() },
            )
        }

        // ── Historial ──
        if (r.evaluaciones.isNotEmpty()) {
            TarjetaTab {
                TituloTarjeta("Evaluaciones (${r.evaluaciones.size})", null)
                r.evaluaciones.reversed().forEach { ev ->
                    val trat = nombreTrat(tratamientoDeEvaluacion(ev, origenes))
                    val estaAbierta = abierta == ev.id
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().clickable { abierta = if (estaAbierta) null else ev.id },
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(nombreTipoEvaluacion(ev.tipo), color = c.purple, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.purpleBg)
                                    .padding(horizontal = 8.dp, vertical = 3.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                fechaCorta(ev.fecha) + (ev.terapeutaNombre?.let { " · $it" } ?: "") + (trat?.let { " · $it" } ?: ""),
                                color = c.texto, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Text(if (estaAbierta) "▴" else "▾", color = c.textoSuave, fontSize = 12.sp)
                        }
                        AnimatedVisibility(estaAbierta) {
                            Column {
                                DetalleEvaluacion(ev)
                                if (puedeEditar) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("✏ Editar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { abrir = AbrirEval(ev.tipo, ev.tratamientoId, ev) }.padding(4.dp))
                                    Text("🗑 Borrar", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { confirmarBorrar = ev }.padding(4.dp))
                                }
                            }
                        }
                    }
                }
                Text("Fuerza: escala MRC 0–5 (3 = ${ESCALA_FUERZA[3].second.lowercase()}).", color = c.textoSuave, fontSize = 10.sp)
            }
        }
    }

    abrir?.let { a ->
        ModalEvaluacionFisio(
            pacienteId = pacienteId, abrir = a, tratamientos = tratamientos, miTerapeutaId = miTerapeutaId,
            textoRegion = textoRegion,
            onCerrar = { abrir = null },
            onGuardado = { abrir = null; recargar() },
        )
    }
    confirmarBorrar?.let { ev ->
        AlertDialog(
            onDismissRequest = { confirmarBorrar = null },
            title = { Text("¿Borrar esta evaluación?", fontWeight = FontWeight.Bold) },
            text = { Text("Los objetivos que se crearon con ella se conservan.", color = c.textoSuave) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrar = null
                    scope.launch {
                        if (EvaluacionFisioRepo.borrar(ev.id)) { Toaster.exito("Evaluación borrada"); recargar() }
                        else Toaster.error("No se pudo borrar")
                    }
                }) { Text("Borrar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ─── Piezas de la pestaña ────────────────────────────────────────────────────

@Composable
private fun TarjetaTab(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        content = contenido,
    )
}

@Composable
private fun TituloTarjeta(titulo: String, extra: String?) {
    val c = Sania.colors
    Row(Modifier.padding(bottom = 8.dp)) {
        Text(titulo.uppercase(), color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        extra?.let { Text(it, color = c.textoSuave, fontSize = 11.sp) }
    }
}

@Composable
private fun BotonTab(texto: String, lleno: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (lleno) c.sobreNavy else c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier
        .clip(RoundedCornerShape(Sania.shape.sm.dp))
        .background(if (lleno) c.navy else c.superficie)
        .border(1.dp, if (lleno) c.navy else c.lav, RoundedCornerShape(Sania.shape.sm.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

/** "Flexión rodilla D · normal 135°   90° → 125°  [+35°]" con el delta en verde/rojo. */
@Composable
private fun FilaDelta(f: FilaComparativo) {
    val c = Sania.colors
    val (fg, bg) = when (f.mejora) { true -> c.ok to c.okBg; false -> c.error to c.errorBg; null -> c.textoSuave to c.chipBg }
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f.etiqueta + (f.referencia?.let { " · normal $it°" } ?: ""), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            f.interpretacion?.let { Text(it, color = c.textoSuave, fontSize = 10.sp) }
        }
        Text(
            formatoValor(f, f.inicial, f.inicialTexto) + (if (f.fechaUltima != null) " → ${formatoValor(f, f.ultimo, f.ultimoTexto)}" else ""),
            color = c.texto, fontSize = 12.sp, fontWeight = if (f.fechaUltima != null) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        if (f.fechaUltima != null) {
            Text(textoDelta(f), color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier
                .widthIn(min = 44.dp).clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 3.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun DetalleEvaluacion(ev: EvaluacionFisio) {
    val c = Sania.colors
    val d = ev.datos
    val items = buildList {
        d.eva?.let { add("EVA" to "$it/10") }
        if (d.zonas.isNotEmpty()) add("Dolor en" to textoZonas(d.zonas))
        d.localizacion?.let { add("Características" to it) }
        if (d.rangos.isNotEmpty()) add("Rangos" to d.rangos.joinToString(" · ") { r ->
            val (art, mov) = buscarMovimiento(r.articulacion, r.movimiento)
            "${mov?.nombre ?: r.movimiento} ${art?.nombre?.lowercase() ?: r.articulacion}${r.lado?.let { " $it" } ?: ""} ${r.grados ?: "—"}°"
        })
        if (d.fuerza.isNotEmpty()) add("Fuerza" to d.fuerza.joinToString(" · ") { f -> "${f.grupo}${f.lado?.let { " $it" } ?: ""} ${f.valor ?: "—"}/5" })
        if (d.pruebas.isNotEmpty()) add("Pruebas" to d.pruebas.joinToString(" · ") { p ->
            "${p.nombre}${p.lado?.let { " $it" } ?: ""} ${if (p.resultado == "+") "(+)" else "(−)"}"
        })
        d.tests.forEach { t ->
            val def = TESTS_FUNCIONALES[t.id] ?: return@forEach
            add(def.corto to (t.puntaje?.let { "${numJs(it)}${if (def.unidad == "%") " %" else " pts"} · ${interpretar(t.id, it)}" } ?: "incompleto"))
        }
        d.notas?.let { add("Notas" to it) }
    }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (k, v) ->
            Row {
                Text(k.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(92.dp).padding(top = 2.dp))
                Text(v, color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ─── Objetivos guardados ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObjetivosGuardados(
    objetivos: List<ObjetivoTratamiento>,
    pacienteId: String,
    tratamientoId: String?,
    nombreTratamiento: ((String?) -> String?)?,
    puedeEditar: Boolean,
    regiones: List<String>,
    onCambio: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf<String?>(null) }
    var agregando by remember { mutableStateOf(false) }

    fun cambiar(o: ObjetivoTratamiento, bloque: suspend () -> Boolean) {
        ocupado = o.id
        scope.launch {
            if (bloque()) onCambio() else Toaster.error("No se pudo guardar el objetivo")
            ocupado = null
        }
    }
    fun agregar(b: ObjetivoBorrador) {
        scope.launch {
            if (EvaluacionFisioRepo.crearObjetivo(b, pacienteId, tratamientoId, objetivos.size)) onCambio()
            else Toaster.error("No se pudo agregar el objetivo")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (objetivos.isEmpty()) Text("Aún no hay objetivos. Ponlos en la evaluación o agrégalos aquí: el paciente ve hacia dónde va.",
            color = c.textoSuave, fontSize = 12.sp)
        objetivos.forEach { o ->
            val logrado = o.estado == "logrado"
            val est = ESTADOS_OBJETIVO.firstOrNull { it.id == o.estado } ?: ESTADOS_OBJETIVO[0]
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("${if (logrado) "✓ " else "🎯 "}${o.texto}", color = if (logrado) c.ok else c.texto,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val partes = listOfNotNull(
                            medidaTexto(o.valorInicial, o.meta, o.unidad).takeIf { it.isNotEmpty() }
                                ?.let { m -> m + (o.logrado?.let { " · logrado ${numJs(it)}${o.unidad ?: ""}" } ?: "") },
                            o.fechaEstado?.takeIf { o.estado != "en_curso" }?.let { "${est.label.lowercase()} el ${fechaCorta(it)}" },
                            nombreTratamiento?.invoke(o.tratamientoId),
                        )
                        if (partes.isNotEmpty()) Text(partes.joinToString(" · "), color = c.textoSuave, fontSize = 11.sp)
                    }
                    if (puedeEditar) Text("✕", color = c.textoSuave, fontSize = 13.sp, modifier = Modifier
                        .clickable(enabled = ocupado == null) { cambiar(o) { EvaluacionFisioRepo.borrarObjetivo(o.id) } }.padding(4.dp))
                }
                if (puedeEditar) {
                    FlowRow(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ESTADOS_OBJETIVO.forEach { e ->
                            val on = o.estado == e.id
                            val (fg, bg) = colorEstadoObjetivo(e.id)
                            Text(e.label, color = if (on) fg else c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                    .background(if (on) bg else c.superficie)
                                    .border(1.dp, if (on) bg else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                                    .clickable(enabled = ocupado == null && !on) {
                                        cambiar(o) { EvaluacionFisioRepo.actualizarObjetivo(o.id, estado = e.id) }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        if (o.valorInicial != null || o.meta != null) CampoLogrado(o) { v ->
                            cambiar(o) { EvaluacionFisioRepo.actualizarObjetivo(o.id, logrado = v, tocarLogrado = true) }
                        }
                    }
                } else {
                    val (fg, bg) = colorEstadoObjetivo(o.estado)
                    Text(est.label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
                        .clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                if (ocupado == o.id) Text("Guardando…", color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (puedeEditar) {
            if (agregando) {
                val sugeridos = sugerirObjetivos(regiones, null, objetivos.map { it.texto }).take(5)
                if (sugeridos.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sugeridos.forEach { s -> ChipEval("+ ${s.texto}") { agregar(s) } }
                }
                NuevoObjetivo { agregar(it) }
            } else {
                Text("＋ Agregar objetivo", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { agregando = true }.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun colorEstadoObjetivo(id: String) = Sania.colors.let { c ->
    when (id) { "logrado" -> c.ok to c.okBg; "parcial" -> c.pend to c.pendBg; else -> c.info to c.infoBg }
}

/** Valor logrado: se guarda al tocar "listo" en el teclado o al salir del campo. */
@Composable
private fun CampoLogrado(o: ObjetivoTratamiento, onGuardar: (Double?) -> Unit) {
    var texto by remember(o.id, o.logrado) { mutableStateOf(o.logrado?.let { numJs(it) } ?: "") }
    var tuvoFoco by remember { mutableStateOf(false) }
    fun guardar() { val v = numDe(texto); if (v != o.logrado) onGuardar(v) }
    CampoTextoEval(
        texto, { texto = it }, "Logrado ${o.unidad ?: ""}".trim(),
        Modifier.width(96.dp).onFocusChanged { f ->
            if (f.isFocused) tuvoFoco = true else if (tuvoFoco) { tuvoFoco = false; guardar() }
        },
        teclado = KeyboardType.Decimal, onListo = { guardar() },
    )
}

// ─── Modal: nueva evaluación (reevaluación / de alta / inicial) o editar ─────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModalEvaluacionFisio(
    pacienteId: String,
    abrir: AbrirEval,
    tratamientos: List<TratamientoEval>,
    miTerapeutaId: String?,
    textoRegion: String?,
    onCerrar: () -> Unit,
    onGuardado: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val ed = abrir.editar
    var tipo by remember { mutableStateOf(ed?.tipo ?: abrir.tipo) }
    var tratId by remember { mutableStateOf(ed?.tratamientoId ?: abrir.tratamientoId ?: tratamientos.singleOrNull()?.id) }
    var terId by remember { mutableStateOf(ed?.terapeutaId ?: miTerapeutaId) }
    var fecha by remember { mutableStateOf(ed?.fecha ?: hoyClinicaIso()) }
    var eligiendoFecha by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var terapeutas by remember { mutableStateOf<List<pe.saniape.app.data.staff.TerapeutaRef>>(emptyList()) }
    LaunchedEffect(Unit) { terapeutas = runCatching { AgendaRepo.terapeutasActivos() }.getOrDefault(emptyList()) }
    val borrador = remember { RefBorradorFisio() }
    val titulo = when (tipo) { "inicial" -> "Evaluación inicial"; "alta" -> "Evaluación de alta"; else -> "Reevaluación" }

    fun guardar() {
        val b = borrador.valor
        if (b == null || (!tieneDatos(b.datos) && b.objetivos.none { it.texto.isNotBlank() })) {
            Toaster.error("No hay nada que guardar: marca al menos un dato.")
            return
        }
        guardando = true
        scope.launch {
            val ok = if (ed != null) {
                EvaluacionFisioRepo.actualizar(ed.id, b.datos, tipo, fecha, terId, tratId)
            } else runCatching {
                EvaluacionFisioRepo.guardar(
                    pacienteId = pacienteId, tipo = tipo, datos = b.datos, objetivos = b.objetivos,
                    tratamientoId = tratId, terapeutaId = terId, fecha = fecha,
                )
                true
            }.getOrDefault(false)
            guardando = false
            if (ok) { Toaster.exito(if (ed != null) "Evaluación actualizada" else "$titulo guardada"); onGuardado() }
            else Toaster.error("No se pudo guardar la evaluación. Intenta de nuevo.")
        }
    }

    DialogoForm(
        titulo = if (ed != null) "✏ Editar evaluación" else "📏 $titulo",
        subtitulo = null,
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TIPOS_EVALUACION.forEach { (id, label) -> ChipEval(label, activo = tipo == id) { tipo = id } }
        }
        Spacer(Modifier.height(12.dp))
        EtqForm("Fecha")
        CajaSelectorForm(fechaCorta(fecha)) { eligiendoFecha = true }
        if (terapeutas.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            EtqForm("Quién evaluó")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                terapeutas.forEach { t ->
                    ChipEval(t.nombre, activo = terId == t.id) { terId = if (terId == t.id) null else t.id }
                }
            }
        }
        if (tratamientos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            EtqForm("Tratamiento")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipEval("Sin tratamiento", activo = tratId == null) { tratId = null }
                tratamientos.forEach { t -> ChipEval(t.nombre, activo = tratId == t.id) { tratId = t.id } }
            }
        }
        Spacer(Modifier.height(12.dp))
        EvaluacionFisioForm(
            plegable = false, inicial = ed?.datos, textoRegion = textoRegion, conObjetivos = ed == null,
            onCambio = { borrador.valor = it },
        )
    }
    if (eligiendoFecha) DialogoFecha(
        onElegir = { f -> fecha = if (f > hoyClinicaIso()) hoyClinicaIso() else f },
        onCerrar = { eligiendoFecha = false },
    )
}
