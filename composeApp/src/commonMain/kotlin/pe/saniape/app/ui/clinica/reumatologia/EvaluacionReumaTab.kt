package pe.saniape.app.ui.clinica.reumatologia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import pe.saniape.app.data.staff.ARTICULACIONES_REUMA
import pe.saniape.app.data.staff.ActividadReuma
import pe.saniape.app.data.staff.BorradorEvaluacionReuma
import pe.saniape.app.data.staff.EstadoArticulacion
import pe.saniape.app.data.staff.EvaluacionReumatologica
import pe.saniape.app.data.staff.EvaluacionReumatologicaRepo
import pe.saniape.app.data.staff.GRUPOS_FUERZA_REUMA
import pe.saniape.app.data.staff.MapaArticularGeo
import pe.saniape.app.data.staff.NivelActividadReuma
import pe.saniape.app.data.staff.PUNTOS_DOLOROSOS_REUMA
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.actividadReuma
import pe.saniape.app.data.staff.ArticulacionDef
import pe.saniape.app.data.staff.PROPORCION_MANO_REUMA
import pe.saniape.app.data.staff.PROPORCION_PIE_REUMA
import pe.saniape.app.data.staff.PUNTOS_MANO_REUMA
import pe.saniape.app.data.staff.PUNTOS_PIE_REUMA
import pe.saniape.app.data.staff.ToqueMapaReuma
import pe.saniape.app.data.staff.toqueEnMapaReuma
import pe.saniape.app.data.staff.toqueEnZonaReuma
import pe.saniape.app.data.staff.zonaReumaEnEspejo
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.cdaiReuma
import pe.saniape.app.data.staff.compararEvaluacionesReuma
import pe.saniape.app.data.staff.conteosReuma
import pe.saniape.app.data.staff.das28Reuma
import pe.saniape.app.data.staff.evaluacionReumaDeJson
import pe.saniape.app.data.staff.fechaEvalPodo
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.nombresArticulaciones
import pe.saniape.app.data.staff.rotarEstadoArticulacion
import pe.saniape.app.data.staff.validarEvaluacionReuma
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.mapas.BotonMapa
import pe.saniape.app.ui.clinica.mapas.CajaErroresMapa
import pe.saniape.app.ui.clinica.mapas.ChipMapa
import pe.saniape.app.ui.clinica.mapas.EtiquetaMapa
import pe.saniape.app.ui.clinica.mapas.FilaBotonesMapa
import pe.saniape.app.ui.clinica.mapas.TarjetaMapa
import pe.saniape.app.ui.clinica.mapas.TituloSeccionMapa
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * 🦴 Pestaña «Evaluación articular» de la ficha (reumatología). Gemelo de
 * `components/reumatologia/EvaluacionReumaTab.tsx`: mapa articular tocable
 * (dolor → inflamación → ambas), escalas a un toque, lo demás plegado, la
 * actividad EN VIVO y el comparativo contra la evaluación anterior.
 */

private val COLOR_DOLOR = Color(0xFFF59E0B)
// Morado y no azul: el azul se perdía sobre el cuerpo azul.
private val COLOR_INFLAMADA = Color(0xFFA855F7)
private val COLOR_AMBAS = Color(0xFFEF4444)
private val BORDE_PUNTO = Color(0xFF1E3A8A)

private fun colorEstado(e: EstadoArticulacion?): Color? = when {
    e == null -> null
    e.d && e.i -> COLOR_AMBAS
    e.d -> COLOR_DOLOR
    e.i -> COLOR_INFLAMADA
    else -> null
}

@Composable
private fun colorNivel(n: NivelActividadReuma): Color {
    val c = Sania.colors
    return when (n) { NivelActividadReuma.REMISION -> c.ok; NivelActividadReuma.BAJA -> c.teal; NivelActividadReuma.MODERADA -> c.pend; NivelActividadReuma.ALTA -> c.error }
}

private fun limpio(n: Double?): String = when {
    n == null -> ""
    n == n.toLong().toDouble() -> n.toLong().toString()
    else -> n.toString()
}

/** Las ilustraciones son las mismas de la web (cuerpo, mano y pie): se cargan de ahí y quedan en caché. */
private fun urlFiguraReuma(nombre: String) = "${Supabase.SITE_URL}/reumatologia/$nombre.webp"

private fun nombreZonaReuma(zona: String, lado: String) =
    (if (zona == "mano") "Mano" else "Pie") + " " + if (lado == "der") (if (zona == "mano") "derecha" else "derecho") else (if (zona == "mano") "izquierda" else "izquierdo")

/**
 * El cuerpo (ilustración) con las articulaciones grandes encima. Cada mano y cada
 * pie lleva una BOLITA: al tocarla se abre la mano o el pie en grande para marcar
 * nudillos y dedos. La bolita muestra cuántas hay marcadas y el color del peor
 * hallazgo. Gemelo de `components/reumatologia/MapaArticular.tsx`.
 */
@Composable
fun MapaArticular(
    valor: Map<String, EstadoArticulacion>,
    completo: Boolean,
    onCambiar: ((Map<String, EstadoArticulacion>) -> Unit)? = null,
    /** Modo ELEGIR (💉 infiltraciones): tocar avisa el id en vez de rotar dolor/inflamación. */
    onTocar: ((String) -> Unit)? = null,
    /** En modo elegir, el color de cada punto (null = blanco). */
    colorPor: ((String) -> Color?)? = null,
    /** Texto bajo la mano/pie ampliado en modo elegir. */
    ayuda: String? = null,
) {
    val visibles = remember(completo, valor, colorPor) { ARTICULACIONES_REUMA.filter { completo || it.en28 || valor.containsKey(it.id) || colorPor?.invoke(it.id) != null } }
    val colorDe: (String) -> Color? = { id -> if (colorPor != null) colorPor(id) else colorEstado(valor[id]) }
    val tocarAhora by rememberUpdatedState(onTocar)
    var abierta by remember { mutableStateOf<Pair<String, String>?>(null) }
    val alCambiar by rememberUpdatedState(onCambiar)
    val actual by rememberUpdatedState(valor)
    val visiblesAhora by rememberUpdatedState(visibles)
    val resumen = conteosReuma(valor).let { "Mapa articular: ${it.dolorosas} dolorosas, ${it.inflamadas} inflamadas" }
    // Las bolitas (mano / pie por lado) que tienen algo que mostrar.
    val zonas = remember(visibles) { visibles.mapNotNull { a -> a.zona?.let { Triple(it, a.lado, a) } }.distinctBy { it.first + it.second } }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.widthIn(max = 270.dp).fillMaxWidth().aspectRatio(MapaArticularGeo.ANCHO / MapaArticularGeo.ALTO)
                .semantics { contentDescription = resumen }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val s = size.width / MapaArticularGeo.ANCHO
                        when (val t = toqueEnMapaReuma(p.x / s, p.y / s, visiblesAhora)) {
                            is ToqueMapaReuma.Articulacion -> tocarAhora?.invoke(t.id) ?: alCambiar?.let { cb ->
                                val sig = rotarEstadoArticulacion(actual[t.id])
                                cb(if (sig == null) actual - t.id else actual + (t.id to sig))
                            }
                            is ToqueMapaReuma.Zona -> abierta = t.zona to t.lado
                            null -> Unit
                        }
                    }
                },
        ) {
            val escala = maxWidth.value / MapaArticularGeo.ANCHO
            AsyncImage(model = urlFiguraReuma("cuerpo"), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / MapaArticularGeo.ANCHO
                visibles.filter { it.zona == null }.forEach { a ->
                    val color = colorDe(a.id)
                    drawCircle(color ?: Color.White, 8.5f * s, Offset(a.x * s, a.y * s))
                    drawCircle(if (color != null) Color.White else BORDE_PUNTO, 8.5f * s, Offset(a.x * s, a.y * s), style = Stroke(2f * s))
                }
            }
            zonas.forEach { (zona, lado, ref) ->
                val suyas = visibles.filter { it.zona == zona && it.lado == lado }
                val marcadas = if (colorPor != null) suyas.filter { colorPor(it.id) != null } else suyas.filter { valor[it.id] != null }
                val hayD = marcadas.any { valor[it.id]?.d == true }; val hayI = marcadas.any { valor[it.id]?.i == true }
                val peor = if (colorPor != null) marcadas.firstNotNullOfOrNull { colorPor(it.id) }
                    else if (hayD && hayI) COLOR_AMBAS else if (hayI) COLOR_INFLAMADA else if (hayD) COLOR_DOLOR else null
                val radio = 12f * escala
                Box(
                    Modifier.offset(x = (ref.x * escala - radio).dp, y = (ref.y * escala - radio).dp).size((radio * 2).dp)
                        .clip(CircleShape).background(peor ?: Color.White).border(1.5.dp, if (peor != null) Color.White else BORDE_PUNTO, CircleShape)
                        .semantics { contentDescription = "${nombreZonaReuma(zona, lado)}: ${if (marcadas.isEmpty()) "sin marcar" else "${marcadas.size} marcadas"}. Abrir para ver los dedos" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (marcadas.isEmpty()) "+" else "${marcadas.size}", color = if (peor != null) Color.White else BORDE_PUNTO,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    abierta?.let { (zona, lado) ->
        DialogoZonaReuma(zona, lado, valor, visibles, onCambiar, onTocar, colorDe, ayuda, onCerrar = { abierta = null })
    }
}

/** La mano o el pie en grande, con sus articulaciones para tocar. */
@Composable
private fun DialogoZonaReuma(
    zona: String, lado: String,
    valor: Map<String, EstadoArticulacion>,
    visibles: List<ArticulacionDef>,
    onCambiar: ((Map<String, EstadoArticulacion>) -> Unit)?,
    onTocar: ((String) -> Unit)?,
    colorDe: (String) -> Color?,
    ayuda: String?,
    onCerrar: () -> Unit,
) {
    val tocarAhora by rememberUpdatedState(onTocar)
    val c = Sania.colors
    val proporcion = if (zona == "mano") PROPORCION_MANO_REUMA else PROPORCION_PIE_REUMA
    val puntos = if (zona == "mano") PUNTOS_MANO_REUMA else PUNTOS_PIE_REUMA
    val espejo = zonaReumaEnEspejo(zona, lado)
    val suyas = visibles.filter { it.zona == zona && it.lado == lado }
    val alCambiar by rememberUpdatedState(onCambiar)
    val actual by rememberUpdatedState(valor)
    val visiblesAhora by rememberUpdatedState(visibles)
    val marcadas = suyas.count { colorDe(it.id) != null }
    Dialog(onDismissRequest = onCerrar) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(c.superficie).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nombreZonaReuma(zona, lado) + if (marcadas > 0) " · $marcadas" else "", color = c.texto, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("✕", color = c.textoSuave, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCerrar).padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Box(
                Modifier.padding(top = 6.dp).widthIn(max = 260.dp).fillMaxWidth().aspectRatio(proporcion)
                    .semantics { contentDescription = "Articulaciones: ${nombreZonaReuma(zona, lado)}" }
                    .then(if (onCambiar != null || onTocar != null) Modifier.pointerInput(zona, lado) {
                        detectTapGestures { p ->
                            toqueEnZonaReuma(zona, lado, p.x * 100f / size.width, p.y * 100f / size.height, visiblesAhora, proporcion)?.let { id ->
                                val elegir = tocarAhora
                                if (elegir != null) { elegir(id); return@let }
                                val sig = rotarEstadoArticulacion(actual[id])
                                alCambiar?.invoke(if (sig == null) actual - id else actual + (id to sig))
                            }
                        }
                    } else Modifier),
            ) {
                AsyncImage(model = urlFiguraReuma(zona), contentDescription = null, contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (espejo) -1f else 1f })
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.width * 0.05f
                    suyas.forEach { a ->
                        val p = puntos[a.id.substringBeforeLast('_')] ?: return@forEach
                        val x = (if (espejo) 100f - p.first else p.first) * size.width / 100f
                        val y = p.second * size.height / 100f
                        val color = colorDe(a.id)
                        drawCircle(color ?: Color.White, r, Offset(x, y))
                        drawCircle(if (color != null) Color.White else BORDE_PUNTO, r, Offset(x, y), style = Stroke(r * 0.28f))
                    }
                }
            }
            if (onTocar != null) {
                Text(ayuda ?: "Toca la articulación.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            } else if (onCambiar != null) {
                Text("Toca cada articulación: un toque dolor, dos inflamación, tres ambas.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Text("Listo", color = c.sobreNavy, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.navy).clickable(onClick = onCerrar).padding(vertical = 12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeyendaArticular() {
    val c = Sania.colors
    FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        listOf(COLOR_DOLOR to "Dolorosa", COLOR_INFLAMADA to "Inflamada", COLOR_AMBAS to "Ambas").forEach { (col, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(4.dp))
                Text(t, color = c.textoSuave, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BadgeActividad(a: ActividadReuma?) {
    if (a == null) return
    val c = Sania.colors
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(colorNivel(a.nivel)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(a.nombre.uppercase(), color = c.sobreNavy, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(limpio(a.valor), color = c.sobreNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(a.nivel.texto, color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Conteo(n: Int, texto: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$n", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(texto, color = Sania.colors.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

private sealed interface EditorReuma {
    data object Nueva : EditorReuma
    data class Editar(val id: String) : EditorReuma
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvaluacionReumaTab(pacienteId: String, puedeEditar: Boolean) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<EvaluacionReumatologica>?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pacienteId, token) {
        val r = EvaluacionReumatologicaRepo.listar(pacienteId)
        if (r.items != null) { items = r.items; error = null } else if (items == null) error = r.error
        cargando = false
    }
    var profesionales by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        profesionales = runCatching { PacientesRepo.terapeutasActivos().associate { it.id to it.nombre } }.getOrDefault(emptyMap())
    }
    var editor by remember { mutableStateOf<EditorReuma?>(null) }
    var abierta by remember { mutableStateOf<String?>(null) }
    var confirmarBorrar by remember { mutableStateOf<EvaluacionReumatologica?>(null) }

    if (cargando && items == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 240).forEach { h -> Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg)) }
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🦴 Evaluación articular", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (puedeEditar) BotonMapa("＋ Nueva evaluación", lleno = true) { editor = EditorReuma.Nueva }
        }
        if (lista.isEmpty()) TarjetaMapa {
            Text("Aún no tiene evaluaciones.", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Marca en el mapa las articulaciones dolorosas e inflamadas; el sistema cuenta y calcula la actividad, y en cada control te muestra qué cambió.",
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (lista.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lista.reversed().takeLast(6).forEach { e ->
                val a = e.actividad
                Column(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, c.borde, RoundedCornerShape(8.dp)).clickable { abierta = e.id }.padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(fechaEvalPodo(e.fecha), color = c.textoSuave, fontSize = 10.sp)
                    Text(a?.let { "${it.nombre} ${limpio(it.valor)}" } ?: "${e.nad28} dol · ${e.nat28} infl",
                        color = a?.let { colorNivel(it.nivel) } ?: c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        lista.forEachIndexed { k, ev ->
            val anterior = lista.getOrNull(k + 1)
            val desplegada = if (abierta != null) abierta == ev.id else k == 0
            TarjetaMapa {
                Row(Modifier.fillMaxWidth().clickable { abierta = if (desplegada && abierta != null) null else ev.id }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fechaEvalPodo(ev.fecha) + if (k == 0) " · la más reciente" else "", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        ev.terapeutaId?.let { profesionales[it] }?.let { Text(it, color = c.textoSuave, fontSize = 11.sp) }
                    }
                    BadgeActividad(ev.actividad)
                }
                if (desplegada) {
                    Spacer(Modifier.height(10.dp))
                    MapaArticular(ev.articulaciones, completo = true)
                    LeyendaArticular()
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Conteo(ev.nad28, "dolorosas (de 28)", COLOR_DOLOR)
                        Conteo(ev.nat28, "inflamadas (de 28)", COLOR_INFLAMADA)
                    }
                    val cambios = compararEvaluacionesReuma(ev, anterior)
                    if (cambios.isNotEmpty()) {
                        EtiquetaMapa("Desde la evaluación anterior")
                        cambios.forEach { ch ->
                            Text("${if (ch.mejora) "▼" else "▲"} ${ch.etiqueta}: ${ch.antes} → ${ch.ahora}", color = if (ch.mejora) c.ok else c.error, fontSize = 12.sp)
                        }
                    }
                    val datos = listOfNotNull(
                        ev.eva?.let { "Dolor (EVA) ${limpio(it)}" }, ev.evgPaciente?.let { "Paciente ${limpio(it)}" }, ev.evgMedico?.let { "Médico ${limpio(it)}" },
                        ev.rigidezMin?.let { "Rigidez $it min" }, ev.vsg?.let { "VSG ${limpio(it)} mm/h" }, ev.pcr?.let { "PCR ${limpio(it)} mg/L" },
                    )
                    if (datos.isNotEmpty()) Text(datos.joinToString(" · "), color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    nombresArticulaciones(ev.articulaciones, inflamadas = true).takeIf { it.isNotEmpty() }?.let {
                        Text("Inflamadas: ${it.joinToString(", ")}", color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (ev.fuerza.isNotEmpty()) Text("Fuerza: " + GRUPOS_FUERZA_REUMA.filter { ev.fuerza[it.id] != null }.joinToString(" · ") { "${it.nombre} ${ev.fuerza[it.id]}/5" },
                        color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    if (ev.puntosDolorosos.isNotEmpty()) Text("Puntos dolorosos (${ev.puntosDolorosos.size}/18): " +
                        PUNTOS_DOLOROSOS_REUMA.filter { it.id in ev.puntosDolorosos }.joinToString(", ") { it.nombre },
                        color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    ev.observaciones?.let { Text(it, color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                    if (puedeEditar) {
                        Spacer(Modifier.height(8.dp))
                        FilaBotonesMapa {
                            BotonMapa("✏️ Editar") { editor = EditorReuma.Editar(ev.id) }
                            BotonMapa("Eliminar") { confirmarBorrar = ev }
                        }
                    }
                }
            }
        }
    }

    editor?.let { ed ->
        val evaluacion = (ed as? EditorReuma.Editar)?.let { e -> lista.firstOrNull { it.id == e.id } }
        val anterior = if (evaluacion == null) lista.firstOrNull() else lista.getOrNull(lista.indexOfFirst { it.id == evaluacion.id } + 1)
        EditorEvaluacionReuma(pacienteId, evaluacion, anterior, onCerrar = { editor = null }, onGuardado = { editor = null; abierta = null; token++ })
    }
    confirmarBorrar?.let { ev ->
        AlertDialog(
            onDismissRequest = { confirmarBorrar = null },
            title = { Text("¿Eliminar la evaluación?") },
            text = { Text("La del ${fechaEvalPodo(ev.fecha)}. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrar = null
                    scope.launch {
                        val r = EvaluacionReumatologicaRepo.borrar(ev.id)
                        if (r.registrada) { Toaster.exito("Evaluación eliminada"); token++ } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text("Eliminar", color = c.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrar = null }) { Text("Cancelar") } },
        )
    }
}

/** Escala 0–10 a un toque (tocar el elegido lo quita). */
@Composable
private fun Escala10(etiqueta: String, valor: Double?, onChange: (Double?) -> Unit) {
    val c = Sania.colors
    EtiquetaMapa(etiqueta + (valor?.let { " · ${limpio(it)}/10" } ?: ""))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (0..10).forEach { n ->
            val activo = valor == n.toDouble()
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (activo) c.navy else c.superficie).border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(8.dp))
                    .clickable { onChange(if (activo) null else n.toDouble()) }
                    .semantics { contentDescription = "$etiqueta: $n de 10" },
                contentAlignment = Alignment.Center,
            ) { Text("$n", color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorEvaluacionReuma(
    pacienteId: String,
    evaluacion: EvaluacionReumatologica?,
    anterior: EvaluacionReumatologica?,
    onCerrar: () -> Unit,
    onGuardado: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val hoy = remember { hoyClinicaIso() }
    val nueva = evaluacion == null
    fun fuera28(a: Map<String, EstadoArticulacion>) = conteosReuma(a).let { it.dolorosas > it.nad28 || it.inflamadas > it.nat28 }
    var fecha by remember { mutableStateOf(evaluacion?.fecha?.takeIf { it.isNotBlank() } ?: hoy) }
    var elegirFecha by remember { mutableStateOf(false) }
    var articulaciones by remember { mutableStateOf(evaluacion?.articulaciones.orEmpty()) }
    var completo by remember { mutableStateOf(fuera28(articulaciones) || (nueva && anterior != null && fuera28(anterior.articulaciones))) }
    var eva by remember { mutableStateOf(evaluacion?.eva) }
    var evgPaciente by remember { mutableStateOf(evaluacion?.evgPaciente) }
    var evgMedico by remember { mutableStateOf(evaluacion?.evgMedico) }
    var rigidez by remember { mutableStateOf(evaluacion?.rigidezMin?.toString().orEmpty()) }
    var vsg by remember { mutableStateOf(limpio(evaluacion?.vsg)) }
    var pcr by remember { mutableStateOf(limpio(evaluacion?.pcr)) }
    var fuerza by remember { mutableStateOf(evaluacion?.fuerza.orEmpty()) }
    var puntos by remember { mutableStateOf(evaluacion?.puntosDolorosos.orEmpty()) }
    var observaciones by remember { mutableStateOf(evaluacion?.observaciones.orEmpty()) }
    var verMas by remember { mutableStateOf(fuerza.isNotEmpty() || puntos.isNotEmpty() || rigidez.isNotBlank() || vsg.isNotBlank() || pcr.isNotBlank()) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }

    fun num(t: String) = t.trim().replace(',', '.').toDoubleOrNull()
    // Lo mismo que calculará el servidor, en vivo.
    val conteo = conteosReuma(articulaciones)
    val das = das28Reuma(conteo, evgPaciente, num(vsg), num(pcr))
    val actividad = actividadReuma(cdaiReuma(conteo, evgPaciente, evgMedico), das?.first, das?.second)

    fun guardar() {
        if (guardando) return
        val b = BorradorEvaluacionReuma(
            pacienteId = pacienteId, fecha = fecha.ifBlank { hoy }, articulaciones = articulaciones, puntosDolorosos = puntos, fuerza = fuerza,
            eva = eva, evgPaciente = evgPaciente, evgMedico = evgMedico, rigidezMin = rigidez.trim().toIntOrNull(),
            vsg = num(vsg), pcr = num(pcr), observaciones = observaciones.trim().ifEmpty { null },
        )
        val e = validarEvaluacionReuma(b)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = EvaluacionReumatologicaRepo.guardar(b, evaluacion?.id)
            guardando = false
            val fila = (r.cuerpo?.get("evaluacion") as? JsonObject)?.let(::evaluacionReumaDeJson)
            if (r.registrada && fila != null) {
                Toaster.exito(if (nueva) "Evaluación guardada" else "Evaluación actualizada")
                onGuardado()
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = if (nueva) "Nueva evaluación articular" else "Editar evaluación articular",
        subtitulo = "🦴 Toca cada articulación: dolor → inflamación → ambas",
        textoAccion = if (guardando) "Guardando…" else if (nueva) "Guardar evaluación" else "Guardar cambios",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtqForm("Fecha")
        CajaSelectorForm(fechaEvalPodo(fecha)) { elegirFecha = true }
        Spacer(Modifier.height(10.dp))

        // Resultado en vivo.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg).padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Conteo(conteo.nad28, "dolorosas", COLOR_DOLOR)
                Conteo(conteo.nat28, "inflamadas", COLOR_INFLAMADA)
            }
            Spacer(Modifier.height(6.dp))
            if (actividad != null) BadgeActividad(actividad)
            else Text("Marca la valoración del paciente y la del médico y la actividad se calcula sola.", color = c.textoSuave, fontSize = 11.sp)
        }

        TituloSeccionMapa("1 · Mapa articular")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipMapa("28", !completo) { completo = false }
            ChipMapa("Mapa completo", completo) { completo = true }
        }
        Spacer(Modifier.height(6.dp))
        MapaArticular(articulaciones, completo, onCambiar = { articulaciones = it })
        LeyendaArticular()
        Spacer(Modifier.height(6.dp))
        FilaBotonesMapa {
            if (nueva && anterior != null && anterior.articulaciones.isNotEmpty()) {
                BotonMapa("↺ Partir de la anterior (${fechaEvalPodo(anterior.fecha)})") { articulaciones = anterior.articulaciones }
            }
            if (articulaciones.isNotEmpty()) BotonMapa("Limpiar el mapa") { articulaciones = emptyMap() }
        }

        TituloSeccionMapa("2 · Escalas")
        Escala10("Dolor (EVA)", eva) { eva = it }
        Escala10("Valoración global del paciente", evgPaciente) { evgPaciente = it }
        Escala10("Valoración global del médico", evgMedico) { evgMedico = it }

        Text((if (verMas) "▾" else "▸") + " Rigidez, laboratorio, fuerza y puntos dolorosos (opcional)",
            color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp).clickable { verMas = !verMas }.padding(vertical = 8.dp))
        if (verMas) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Rigidez (min)", rigidez) { t: String -> rigidez = t.filter(Char::isDigit).take(4) },
                    Triple("VSG (mm/h)", vsg) { t: String -> vsg = t.take(6) },
                    Triple("PCR (mg/L)", pcr) { t: String -> pcr = t.take(7) },
                ).forEach { (etq, v, set) ->
                    OutlinedTextField(
                        value = v, onValueChange = set, label = { Text(etq, fontSize = 11.sp) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = coloresCampoForm(), modifier = Modifier.weight(1f),
                    )
                }
            }
            EtiquetaMapa("Fuerza (0 a 5)")
            GRUPOS_FUERZA_REUMA.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(g.nombre, color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        (0..5).forEach { n ->
                            val activo = fuerza[g.id] == n
                            Box(
                                Modifier.size(width = 34.dp, height = 40.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (activo) c.navy else c.superficie).border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(8.dp))
                                    .clickable { fuerza = if (activo) fuerza - g.id else fuerza + (g.id to n) }
                                    .semantics { contentDescription = "${g.nombre}: $n de 5" },
                                contentAlignment = Alignment.Center,
                            ) { Text("$n", color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            EtiquetaMapa("Puntos dolorosos" + if (puntos.isNotEmpty()) " · ${puntos.size}/18" else "")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PUNTOS_DOLOROSOS_REUMA.forEach { p ->
                    val activo = p.id in puntos
                    ChipMapa((if (activo) "✓ " else "") + p.nombre, activo) { puntos = if (activo) puntos - p.id else puntos + p.id }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = observaciones, onValueChange = { observaciones = it.take(4000) },
            label = { Text("Observaciones") }, minLines = 3, colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )
        CajaErroresMapa(errores)
    }

    if (elegirFecha) DialogoFecha(
        onElegir = { f -> fecha = if (f > hoy) hoy else f },
        onCerrar = { elegirFecha = false }, inicial = fecha,
    )
}
