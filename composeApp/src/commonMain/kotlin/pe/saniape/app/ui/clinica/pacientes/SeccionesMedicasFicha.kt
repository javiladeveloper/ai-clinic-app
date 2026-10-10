package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.NivelAlerta
import pe.saniape.app.data.staff.PuntoVital
import pe.saniape.app.data.staff.RecetaStaff
import pe.saniape.app.data.staff.RecetasStaffRepo
import pe.saniape.app.data.staff.TomaVital
import pe.saniape.app.data.staff.AtencionMedicaRepo
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.ProfesionalPlan
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.alertaPresion
import pe.saniape.app.data.staff.alertaVital
import pe.saniape.app.data.staff.clasificarImc
import pe.saniape.app.data.staff.datosDeToma
import pe.saniape.app.data.staff.decimal
import pe.saniape.app.data.staff.escalaY
import pe.saniape.app.data.staff.fijo
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.nivelImc
import pe.saniape.app.data.staff.puntosCon
import pe.saniape.app.data.staff.recetaVigente
import pe.saniape.app.data.staff.seriesVitales
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.DatoReceta
import pe.saniape.app.ui.FilaMedicamentoReceta
import pe.saniape.app.ui.clinica.agenda.modales.coloresAlerta
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// 📈 Signos vitales (ficha → Resumen). Gemelo de SignosVitalesFicha.tsx.
// ─────────────────────────────────────────────────────────────────────────────

private const val FILAS_INICIALES = 6

/**
 * La última toma con su lectura de color, la evolución (presión y peso/IMC,
 * desde la segunda toma) y la lista de tomas. La pantalla lo monta SOLO si la
 * clínica toma triaje (`vitalesVisiblesEnFicha`: DALU y RENOVA no hacen ni una
 * consulta); sin tomas no se ve nada.
 */
@Composable
fun SignosVitalesFicha(pacienteId: String, edad: Int?) {
    var tomas by remember(pacienteId) { mutableStateOf<List<TomaVital>?>(null) }
    LaunchedEffect(pacienteId) { tomas = AtencionMedicaRepo.historialVitales(pacienteId) ?: emptyList() }
    val lista = tomas
    if (lista.isNullOrEmpty()) return
    ContenidoVitales(lista, edad)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContenidoVitales(tomas: List<TomaVital>, edad: Int?) {
    val c = Sania.colors
    val ultima = tomas.last()
    val serie = remember(tomas) { seriesVitales(tomas) }
    val hayPresion = puntosCon(serie, "sistolica")
    val hayPeso = puntosCon(serie, "peso")
    val hayImc = puntosCon(serie, "imc")
    var medida by rememberSaveable { mutableStateOf(if (hayPeso) "peso" else "imc") }
    var verTodas by rememberSaveable { mutableStateOf(false) }
    val recientes = tomas.reversed()
    val visibles = if (verTodas) recientes else recientes.take(FILAS_INICIALES)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📈 Signos vitales", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${tomas.size} ${if (tomas.size == 1) "toma" else "tomas"}", color = c.textoSuave, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))

        // Última toma: el dato que se mira primero, con su lectura de color.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Text(
                "ÚLTIMA TOMA · " + fechaDMA(ultima.fecha) +
                    (ultima.hora?.let { " · ${hora12(it)}" } ?: "") + (ultima.tomadoPor?.let { " · $it" } ?: ""),
                color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                datosDeToma(ultima, edad).forEach { d ->
                    val (fg, bg) = if (d.alerta != null) coloresAlerta(d.alerta.nivel) else c.texto to c.superficie
                    Row(
                        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
                            .border(1.dp, if (d.alerta != null) bg else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(d.etiqueta + " ", color = if (d.alerta != null) fg else c.textoSuave, fontSize = 12.sp)
                        Text(d.valor, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        d.alerta?.let { Text(" · ${it.texto}", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // Evolución (con una sola toma una "evolución" no dice nada).
        if (hayPresion || hayPeso || hayImc) {
            if (hayPresion) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PRESIÓN ARTERIAL (mmHg)", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Leyenda(c.error, "Sistólica", false)
                    Spacer(Modifier.width(10.dp))
                    Leyenda(c.navy, "Diastólica", true)
                }
                Spacer(Modifier.height(4.dp))
                GraficoVitales(serie, "presion")
                Text("Líneas rojas punteadas: 140 / 90 (presión elevada en adultos).", color = c.textoSuave, fontSize = 10.sp)
            }
            if (hayPeso || hayImc) {
                val tipo = if (hayPeso && hayImc) medida else if (hayPeso) "peso" else "imc"
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (tipo == "peso") "PESO (kg)" else "IMC", color = c.textoSuave, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (hayPeso && hayImc) {
                        Row(Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))) {
                            listOf("peso" to "Peso", "imc" to "IMC").forEach { (k, t) ->
                                Box(
                                    Modifier.background(if (medida == k) c.navy else c.superficie)
                                        .clickable { medida = k }.padding(horizontal = 10.dp, vertical = 4.dp),
                                ) { Text(t, color = if (medida == k) c.sobreNavy else c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                GraficoVitales(serie, tipo)
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text("La evolución en gráfico aparece desde la segunda toma.", color = c.textoSuave, fontSize = 11.sp)
        }

        // Tomas (la más reciente arriba): la vista equivalente del gráfico.
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = c.borde)
        visibles.forEach { t -> FilaToma(t, edad) }
        if (recientes.size > FILAS_INICIALES) {
            Text(
                if (verTodas) "Ver menos" else "Ver las ${recientes.size} tomas",
                color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { verTodas = !verTodas },
            )
        }
    }
}

/** Una toma en la lista: fecha y cada valor con su color (rojo/ámbar), "—" si no se midió. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaToma(t: TomaVital, edad: Int?) {
    val c = Sania.colors
    @Composable
    fun Valor(etq: String, v: String?, nivel: NivelAlerta?) {
        if (v == null) return
        val color = when (nivel) { NivelAlerta.ALERTA -> c.error; NivelAlerta.ATENCION -> c.pend; null -> c.texto }
        Row {
            Text("$etq ", color = c.textoSuave, fontSize = 11.sp)
            Text(v, color = color, fontSize = 12.sp, fontWeight = if (nivel != null) FontWeight.Bold else FontWeight.Normal)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row {
            Text(fechaDMA(t.fecha), color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            t.tomadoPor?.let { Text(it, color = c.textoSuave, fontSize = 10.sp, maxLines = 1) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val pa = t.presionSistolica != null && t.presionDiastolica != null
            Valor("PA", if (pa) "${decimal(t.presionSistolica!!)}/${decimal(t.presionDiastolica!!)}" else null,
                if (pa) alertaPresion(t.presionSistolica, t.presionDiastolica, edad)?.nivel else null)
            Valor("FC", t.frecuenciaCardiaca?.let { decimal(it) }, alertaVital("frecuencia_cardiaca", t.frecuenciaCardiaca, edad)?.nivel)
            Valor("FR", t.frecuenciaRespiratoria?.let { decimal(it) }, alertaVital("frecuencia_respiratoria", t.frecuenciaRespiratoria, edad)?.nivel)
            Valor("T°", t.temperatura?.let { fijo(it, 1) }, alertaVital("temperatura", t.temperatura, edad)?.nivel)
            Valor("SatO₂", t.saturacionO2?.let { "${decimal(it)}%" }, alertaVital("saturacion_o2", t.saturacionO2, edad)?.nivel)
            Valor("Peso", t.peso?.let { decimal(it) }, null)
            Valor("IMC", t.imc?.let { fijo(it, 1) }, nivelImc(clasificarImc(t.imc, edad)))
            Valor("P. abd.", t.perimetroAbdominal?.let { decimal(it) }, null)
        }
    }
    HorizontalDivider(color = c.borde)
}

@Composable
private fun Leyenda(color: Color, texto: String, guiones: Boolean) {
    val c = Sania.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(16.dp).height(6.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(),
                pathEffect = if (guiones) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null)
        }
        Spacer(Modifier.width(3.dp))
        Text(texto, color = c.textoSuave, fontSize = 10.sp)
    }
}

/**
 * Gráfico de líneas simple (Canvas, sin librerías): eje Y de `escalaY` (marcas
 * redondas, presión 60–160 con las referencias 140/90), puntos en orden
 * cronológico, y un hueco donde una toma no trae el dato.
 */
@Composable
private fun GraficoVitales(serie: List<PuntoVital>, tipo: String) {
    val c = Sania.colors
    val medidor = rememberTextMeasurer()
    val lineas: List<Pair<List<Double?>, Pair<Color, Boolean>>> = when (tipo) {
        "presion" -> listOf(serie.map { it.sistolica } to (c.error to false), serie.map { it.diastolica } to (c.navy to true))
        "peso" -> listOf(serie.map { it.peso } to (c.navy to false))
        else -> listOf(serie.map { it.imc } to (c.purple to false))
    }
    val escala = escalaY(tipo, lineas.flatMap { it.first })
    val estiloEje = TextStyle(color = c.textoSuave, fontSize = 9.sp)
    val colorBorde = c.borde
    val colorRef = c.error
    val primera = serie.firstOrNull()?.fecha?.let { fechaCortaDM(it) }
    val ultimaF = serie.lastOrNull()?.fecha?.let { fechaCortaDM(it) }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val izq = 30.dp.toPx()
        val abajo = 16.dp.toPx()
        val arriba = 6.dp.toPx()
        val ancho = size.width - izq - 8.dp.toPx()
        val alto = size.height - abajo - arriba
        val rango = (escala.hi - escala.lo).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = arriba + alto - ((v - escala.lo) / rango * alto).toFloat()
        fun x(i: Int) = izq + if (serie.size <= 1) ancho / 2 else ancho * i / (serie.size - 1)
        // Marcas del eje Y.
        escala.ticks.forEach { t ->
            val yy = y(t)
            drawLine(colorBorde, Offset(izq, yy), Offset(izq + ancho, yy), strokeWidth = 1f)
            val txt = medidor.measure(decimal(t), estiloEje)
            drawText(txt, topLeft = Offset(izq - txt.size.width - 4.dp.toPx(), yy - txt.size.height / 2))
        }
        // Referencias de presión elevada (140/90).
        if (tipo == "presion") listOf(140.0, 90.0).forEach { r ->
            drawLine(colorRef.copy(alpha = 0.6f), Offset(izq, y(r)), Offset(izq + ancho, y(r)), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        }
        // Las series.
        lineas.forEach { (valores, estilo) ->
            val (color, guiones) = estilo
            val path = Path()
            var abierto = false
            valores.forEachIndexed { i, v ->
                if (v == null) { abierto = false; return@forEachIndexed }
                val p = Offset(x(i), y(v))
                if (!abierto) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                abierto = true
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(),
                pathEffect = if (guiones) PathEffect.dashPathEffect(floatArrayOf(10f, 6f)) else null))
            valores.forEachIndexed { i, v -> if (v != null) drawCircle(color, 3.dp.toPx(), Offset(x(i), y(v))) }
        }
        // Fechas de la primera y la última toma.
        primera?.let { f ->
            val txt = medidor.measure(f, estiloEje)
            drawText(txt, topLeft = Offset(izq, size.height - txt.size.height))
        }
        if (serie.size > 1) ultimaF?.let { f ->
            val txt = medidor.measure(f, estiloEje)
            drawText(txt, topLeft = Offset(izq + ancho - txt.size.width, size.height - txt.size.height))
        }
    }
}

/** "2026-09-27" → "27/09". */
private fun fechaCortaDM(iso: String): String {
    val p = iso.take(10).split("-")
    return if (p.size == 3) "${p[2]}/${p[1]}" else iso
}

// ─────────────────────────────────────────────────────────────────────────────
// 💊 Recetas (ficha). Gemelo de RecetasPaciente.tsx — emitir e imprimir, nativos.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lista de recetas del paciente (la más nueva arriba; las anuladas tachadas con
 * su motivo). Tocar una muestra el detalle. "📝 Nueva" emite con el mismo diálogo de
 * la consulta (sin cita ni tratamiento); imprimir abre el HTML del servidor en el
 * visor nativo. Anular sigue en la web.
 */
@Composable
fun ContenidoRecetasFicha(ctx: ContextoStaff, pacienteId: String, fichaInactiva: Boolean, acciones: AccionesNativas) {
    val c = Sania.colors
    var recetas by remember(pacienteId) { mutableStateOf<List<RecetaStaff>?>(null) }
    var fallo by remember(pacienteId) { mutableStateOf(false) }
    // Sube al emitir una receta: vuelve a cargar la lista.
    var recarga by remember(pacienteId) { mutableStateOf(0) }
    // También al emitir una desde "📝 Dar indicaciones" de una atención (fuera de esta pestaña).
    val emitidasFuera = pe.saniape.app.ui.clinica.recetas.RecetaTrasAtencion.emitidas
    LaunchedEffect(pacienteId, recarga, emitidasFuera) {
        val r = RecetasStaffRepo.recetasDe(pacienteId)
        fallo = r == null
        recetas = r ?: emptyList()
    }
    val hoy = remember { hoyClinicaIso() }
    val scope = rememberCoroutineScope()
    val puedeEmitir = ctx.puede("sesiones") && !fichaInactiva
    // "📝 Nueva": el equipo (prescriptores) se carga al abrir; sin cita, no viene de la consulta.
    var equipo by remember { mutableStateOf<List<ProfesionalPlan>?>(null) }
    var emitiendo by remember(pacienteId) { mutableStateOf(false) }
    var cargandoEquipo by remember { mutableStateOf(false) }
    // "🖨 Imprimir" en curso: un doble toque no abre dos visores.
    var abriendo by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💊 Recetas", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (puedeEmitir) {
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                        .clickable(enabled = !cargandoEquipo) {
                            scope.launch {
                                if (equipo.isNullOrEmpty()) {
                                    cargandoEquipo = true
                                    val cargado = RecetasStaffRepo.equipoPrescriptores(ctx.modulosClinicos.mapaReceta)
                                    cargandoEquipo = false
                                    // Sin equipo no se abre: un fallo de red NO es "nadie puede recetar".
                                    if (cargado == null) {
                                        pe.saniape.app.ui.Toaster.error("No se pudo cargar el equipo")
                                        return@launch
                                    }
                                    equipo = cargado
                                }
                                emitiendo = true
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("📝 Nueva", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(10.dp))
        val lista = recetas
        when {
            lista == null -> Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
            }
            fallo -> Text("No se pudieron cargar las recetas. Revisa tu conexión.", color = c.textoSuave, fontSize = 12.sp)
            lista.isEmpty() -> Text(
                "Sin recetas emitidas. Cada receta sale numerada, con los datos del establecimiento y del " +
                    "prescriptor, lista para imprimir, firmar y sellar.",
                color = c.textoSuave, fontSize = 12.sp,
            )
            else -> lista.forEach { r ->
                FilaRecetaStaff(r, recetaVigente(r.estado, r.validaHasta, hoy)) {
                    if (abriendo) return@FilaRecetaStaff
                    abriendo = true
                    scope.launch {
                        try {
                            AtencionRepo.htmlImprimible("receta", r.id)?.let { acciones.abrirHtml(it, r.numeroTexto) }
                                ?: pe.saniape.app.ui.Toaster.error("No se pudo abrir la receta")
                        } finally {
                            abriendo = false
                        }
                    }
                }
                HorizontalDivider(color = c.borde)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Las recetas no se editan ni se borran: si una salió mal, anúlala (queda en el historial con el motivo) y emite otra. " +
                "Anular se hace en la web.",
            color = c.textoSuave, fontSize = 10.sp,
        )
    }

    if (emitiendo) {
        pe.saniape.app.ui.clinica.atencion.DialogoReceta(
            ctx = ctx,
            pacienteId = pacienteId,
            profesionales = equipo.orEmpty(),
            diagnostico = null,
            cie10 = null,
            citaId = null,
            tratamientoId = null,
            recetasOptIn = ctx.modulosClinicos.recetasOptIn,
            onCancelar = { emitiendo = false },
            onEmitida = { emitiendo = false; recarga++ },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaRecetaStaff(r: RecetaStaff, vigente: Boolean, onImprimir: () -> Unit) {
    val c = Sania.colors
    var abierta by rememberSaveable(r.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { abierta = !abierta }.padding(vertical = 10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(r.numeroTexto, color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(fechaDMA(r.fecha), color = c.textoSuave, fontSize = 12.sp)
            val (texto, fg, bg) = when {
                r.anulada -> Triple("Anulada", c.error, c.errorBg)
                vigente -> Triple("Vigente hasta ${fechaDMA(r.validaHasta)}", c.ok, c.okBg)
                else -> Triple("Vencida", c.textoSuave, c.chipBg)
            }
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            r.items.joinToString(" · ") { "${it.dci} ${it.concentracion}".trim() },
            color = if (r.anulada) c.textoSuave else c.texto, fontSize = 13.sp,
            textDecoration = if (r.anulada) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(top = 3.dp),
        )
        val sub = listOfNotNull(
            r.prescriptor?.let { p -> p.nombre + (p.colegiatura.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") },
            r.diagnostico?.let { "Dx: $it" },
            r.motivoAnulacion?.takeIf { r.anulada }?.let { "Motivo: $it" },
        ).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 11.sp)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (abierta) "Ocultar detalle" else "Ver detalle", color = c.navy, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.navy, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onImprimir() }.padding(horizontal = 10.dp, vertical = 5.dp),
            ) { Text(if (r.anulada) "🖨 Ver" else "🖨 Imprimir", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        AnimatedVisibility(abierta) {
            Column(Modifier.padding(top = 8.dp)) {
                r.establecimiento?.let { DatoReceta("Establecimiento", it) }
                r.prescriptor?.let { p ->
                    DatoReceta("Prescriptor", listOfNotNull(p.nombre, p.profesion, p.especialidad).joinToString(" · "))
                    if (p.colegiatura.isNotBlank()) DatoReceta("Colegiatura", p.colegiatura)
                }
                if (r.validaHasta.isNotBlank()) DatoReceta("Válida hasta", fechaDMA(r.validaHasta))
                r.diagnostico?.let { d -> DatoReceta("Diagnóstico", d + (r.cie10?.let { " ($it)" } ?: "")) }
                r.items.forEachIndexed { i, m ->
                    Spacer(Modifier.height(6.dp))
                    FilaMedicamentoReceta(i + 1, m)
                }
                r.indicacionesGenerales?.let {
                    Spacer(Modifier.height(6.dp))
                    DatoReceta("Indicaciones", it)
                }
            }
        }
    }
}
