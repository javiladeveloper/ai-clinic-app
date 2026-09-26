package pe.saniape.app.ui.clinica.fisio

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import pe.saniape.app.data.staff.ARTICULACIONES
import pe.saniape.app.data.staff.BorradorEvaluacionFisio
import pe.saniape.app.data.staff.ESCALA_FUERZA
import pe.saniape.app.data.staff.EvaluacionFisioDatos
import pe.saniape.app.data.staff.FuerzaMedida
import pe.saniape.app.data.staff.GRUPOS_MUSCULARES
import pe.saniape.app.data.staff.LISTA_TESTS
import pe.saniape.app.data.staff.ObjetivoBorrador
import pe.saniape.app.data.staff.PRUEBAS_ESPECIALES
import pe.saniape.app.data.staff.PruebaEspecial
import pe.saniape.app.data.staff.RangoMedido
import pe.saniape.app.data.staff.TESTS_FUNCIONALES
import pe.saniape.app.data.staff.TEST_POR_REGION
import pe.saniape.app.data.staff.TestAplicado
import pe.saniape.app.data.staff.buscarMovimiento
import pe.saniape.app.data.staff.calcularPuntaje
import pe.saniape.app.data.staff.medidaTexto
import pe.saniape.app.data.staff.numJs
import pe.saniape.app.data.staff.ordenarPorRegion
import pe.saniape.app.data.staff.regionesProbables
import pe.saniape.app.data.staff.sugerirObjetivos
import pe.saniape.app.data.staff.textoZonas
import pe.saniape.app.data.staff.tieneDatos
import pe.saniape.app.ui.theme.Sania

/**
 * Evaluación fisioterapéutica estructurada (M5) — gemelo de
 * `components/fisio/EvaluacionFisioForm.tsx`. Se llena al completar una Evaluación
 * de fisio (plegada y opcional) o en una reevaluación desde la ficha.
 *
 * "Si es rápido mejor, el fisio no puede perder tiempo": todo opcional, nada bloquea.
 * Los bloques se abren con chips; los catálogos se ordenan por la región del
 * diagnóstico; cada toque agrega una fila casi lista (solo falta el número).
 *
 * Guarda su propio estado y avisa con [onCambio] (el padre lo guarda en un holder,
 * sin recomponer la pantalla). Al montarse avisa "vacío": un borrador de otra cita
 * no se cuela.
 */

/** Holder SIN estado de Compose: el padre guarda aquí el borrador (no recompone nada). */
class RefBorradorFisio { var valor: BorradorEvaluacionFisio? = null }

private enum class Bloque(val label: String) {
    DOLOR("😣 Dolor y mapa"), RANGOS("📐 Rangos"), FUERZA("💪 Fuerza"),
    PRUEBAS("🔎 Pruebas"), TEST("📋 Test funcional"), OBJETIVOS("🎯 Objetivos"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvaluacionFisioForm(
    citaId: String? = null,
    /** Diagnóstico / motivo: de ahí sale la región para ordenar catálogos. */
    textoRegion: String? = null,
    inicial: EvaluacionFisioDatos? = null,
    objetivosIniciales: List<ObjetivoBorrador> = emptyList(),
    /** true = arranca cerrado con un botón "Evaluación estructurada (opcional)". */
    plegable: Boolean = true,
    conObjetivos: Boolean = true,
    onCambio: (BorradorEvaluacionFisio) -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(!plegable) }
    var datos by remember { mutableStateOf(inicial ?: EvaluacionFisioDatos()) }
    var objetivos by remember { mutableStateOf(objetivosIniciales) }
    var visibles by remember {
        mutableStateOf(buildSet {
            val d = inicial ?: EvaluacionFisioDatos()
            if (d.eva != null || d.zonas.isNotEmpty() || d.localizacion != null) add(Bloque.DOLOR)
            if (d.rangos.isNotEmpty()) add(Bloque.RANGOS)
            if (d.fuerza.isNotEmpty()) add(Bloque.FUERZA)
            if (d.pruebas.isNotEmpty()) add(Bloque.PRUEBAS)
            if (d.tests.isNotEmpty()) add(Bloque.TEST)
            if (objetivosIniciales.isNotEmpty()) add(Bloque.OBJETIVOS)
            if (isEmpty()) add(Bloque.DOLOR) // lo más usado, abierto de entrada
        })
    }
    var artSel by remember { mutableStateOf<String?>(null) }
    var verTodasPruebas by remember { mutableStateOf(false) }
    var pruebaLibre by remember { mutableStateOf("") }

    // Aviso al padre (y "vacío" al montar: un borrador viejo no se cuela).
    val avisar by rememberUpdatedState(onCambio)
    LaunchedEffect(citaId, datos, objetivos) { avisar(BorradorEvaluacionFisio(citaId, datos, objetivos)) }

    val regiones = remember(textoRegion, datos) { regionesProbables(textoRegion, datos) }
    val articulaciones = remember(regiones) { ordenarPorRegion(ARTICULACIONES, regiones) { it.region } }
    val grupos = remember(regiones) { ordenarPorRegion(GRUPOS_MUSCULARES, regiones) { it.region } }
    val pruebas = remember(regiones) { ordenarPorRegion(PRUEBAS_ESPECIALES, regiones) { it.region } }
    val testSugerido = regiones.firstNotNullOfOrNull { TEST_POR_REGION[it] }

    fun set(nuevo: EvaluacionFisioDatos) { datos = nuevo }
    fun toggleBloque(b: Bloque) { visibles = if (b in visibles) visibles - b else visibles + b }
    val lleno = tieneDatos(datos) || objetivos.isNotEmpty()
    val cuenta = mapOf(
        Bloque.DOLOR to (if (datos.eva != null) 1 else 0) + datos.zonas.size,
        Bloque.RANGOS to datos.rangos.count { it.grados != null },
        Bloque.FUERZA to datos.fuerza.count { it.valor != null },
        Bloque.PRUEBAS to datos.pruebas.size,
        Bloque.TEST to datos.tests.size,
        Bloque.OBJETIVOS to objetivos.size,
    )

    if (!abierto) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                .border(1.5.dp, c.lav.copy(alpha = 0.6f), RoundedCornerShape(Sania.shape.md.dp))
                .clickable { abierto = true }.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📏", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Evaluación estructurada (opcional)", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (lleno) "Tiene datos cargados — toca para verlos"
                    else "EVA, mapa del dolor, rangos, fuerza, pruebas, test funcional y objetivos",
                    color = c.textoSuave, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("▸", color = c.textoSuave)
        }
        return
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg)
            .border(1.dp, c.lav.copy(alpha = 0.5f), RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📏 Evaluación estructurada", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            if (plegable) Text("plegar ▴", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.clickable { abierto = false }.padding(4.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Bloque.entries.filter { conObjetivos || it != Bloque.OBJETIVOS }.forEach { b ->
                val n = cuenta[b] ?: 0
                ChipEval(b.label + if (n > 0) " · $n" else "", activo = b in visibles) { toggleBloque(b) }
            }
        }

        // ── Dolor: EVA + mapa corporal ──
        if (Bloque.DOLOR in visibles) SeccionEval("Dolor (EVA 0–10) y localización", { toggleBloque(Bloque.DOLOR) }) {
            EvaEscala("Dolor (EVA)", datos.eva) { set(datos.copy(eva = it)) }
            Spacer(Modifier.height(10.dp))
            MapaCorporal(datos.zonas, onChange = { set(datos.copy(zonas = it)) }, ancho = 140.dp)
            Spacer(Modifier.height(8.dp))
            CampoTextoEval(datos.localizacion ?: "", { set(datos.copy(localizacion = it)) },
                "Características (irradia, punzante, nocturno…) — opcional", Modifier.fillMaxWidth())
        }

        // ── Rangos articulares ──
        if (Bloque.RANGOS in visibles) SeccionEval("Rangos articulares (goniometría)", { toggleBloque(Bloque.RANGOS) }) {
            datos.rangos.forEachIndexed { i, r ->
                val (art, mov) = buscarMovimiento(r.articulacion, r.movimiento)
                fun upd(n: RangoMedido) = set(datos.copy(rangos = datos.rangos.mapIndexed { j, x -> if (j == i) n else x }))
                val bajo = r.grados != null && mov != null && mov.normal > 0 && r.grados < mov.normal * 0.9
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${mov?.nombre ?: r.movimiento} ${art?.nombre?.lowercase() ?: r.articulacion}",
                            color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("normal ${mov?.normal ?: "—"}°", color = c.textoSuave, fontSize = 10.sp)
                    }
                    if (mov?.bilateral != false) { LadoToggle(r.lado) { upd(r.copy(lado = it)) }; Spacer(Modifier.width(6.dp)) }
                    CampoGrados(r.grados, bajo) { upd(r.copy(grados = it)) }
                    Text("✕", color = c.textoSuave, fontSize = 14.sp, modifier = Modifier
                        .clickable { set(datos.copy(rangos = datos.rangos.filterIndexed { j, _ -> j != i })) }.padding(8.dp))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                articulaciones.forEach { a -> ChipEval(a.nombre, activo = artSel == a.id) { artSel = if (artSel == a.id) null else a.id } }
            }
            val sel = artSel?.let { id -> ARTICULACIONES.firstOrNull { it.id == id } }
            if (sel != null) {
                FlowRow(
                    Modifier.padding(top = 8.dp, start = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sel.movimientos.forEach { m ->
                        ChipEval("+ ${m.nombre} (${m.normal}°)") {
                            val ultimoLado = datos.rangos.lastOrNull { it.articulacion == sel.id }?.lado
                            set(datos.copy(rangos = datos.rangos + RangoMedido(sel.id, m.id,
                                if (!m.bilateral) null else (ultimoLado ?: "D"), null)))
                        }
                    }
                }
            }
            Text("Déficit de extensión en negativo (usa ±). Normales de referencia: AAOS.", color = c.textoSuave,
                fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }

        // ── Fuerza muscular ──
        if (Bloque.FUERZA in visibles) SeccionEval("Fuerza muscular (0–5)", { toggleBloque(Bloque.FUERZA) }) {
            datos.fuerza.forEachIndexed { i, f ->
                fun upd(n: FuerzaMedida) = set(datos.copy(fuerza = datos.fuerza.mapIndexed { j, x -> if (j == i) n else x }))
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.grupo, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("✕", color = c.textoSuave, fontSize = 14.sp, modifier = Modifier
                            .clickable { set(datos.copy(fuerza = datos.fuerza.filterIndexed { j, _ -> j != i })) }.padding(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LadoToggle(f.lado) { upd(f.copy(lado = it)) }
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ESCALA_FUERZA.forEach { (v, _) ->
                                val on = f.valor == v
                                Box(
                                    Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (on) c.navy else c.superficie)
                                        .border(1.dp, if (on) c.navy else c.borde, RoundedCornerShape(8.dp))
                                        .clickable { upd(f.copy(valor = if (on) null else v)) },
                                    contentAlignment = Alignment.Center,
                                ) { Text("$v", color = if (on) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    f.valor?.let { v -> Text(ESCALA_FUERZA[v].second, color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                grupos.take(10).forEach { g ->
                    ChipEval("+ ${g.nombre}") { set(datos.copy(fuerza = datos.fuerza + FuerzaMedida(g.nombre, "D", null))) }
                }
            }
        }

        // ── Pruebas especiales ──
        if (Bloque.PRUEBAS in visibles) SeccionEval("Pruebas especiales", { toggleBloque(Bloque.PRUEBAS) }) {
            datos.pruebas.forEachIndexed { i, p ->
                fun upd(n: PruebaEspecial) = set(datos.copy(pruebas = datos.pruebas.mapIndexed { j, x -> if (j == i) n else x }))
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.nombre, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    LadoToggle(p.lado) { upd(p.copy(lado = it)) }
                    Spacer(Modifier.width(6.dp))
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, c.borde, RoundedCornerShape(8.dp))) {
                        listOf("+", "-").forEach { r ->
                            val on = p.resultado == r
                            Box(
                                Modifier.width(36.dp).height(32.dp)
                                    .background(if (on) (if (r == "+") c.error else c.ok) else Color.Transparent)
                                    .clickable { upd(p.copy(resultado = r)) },
                                contentAlignment = Alignment.Center,
                            ) { Text(if (r == "+") "+" else "−", color = if (on) Color.White else c.textoSuave, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Text("✕", color = c.textoSuave, fontSize = 14.sp, modifier = Modifier
                        .clickable { set(datos.copy(pruebas = datos.pruebas.filterIndexed { j, _ -> j != i })) }.padding(8.dp))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (verTodasPruebas) pruebas else pruebas.take(10))
                    .filter { p -> datos.pruebas.none { it.nombre == p.nombre } }
                    .forEach { p -> ChipEval("+ ${p.nombre}") { set(datos.copy(pruebas = datos.pruebas + PruebaEspecial(p.nombre, null, "+"))) } }
                if (!verTodasPruebas && pruebas.size > 10) ChipEval("ver todas…", destacado = true) { verTodasPruebas = true }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                fun agregarLibre() {
                    if (pruebaLibre.isBlank()) return
                    set(datos.copy(pruebas = datos.pruebas + PruebaEspecial(pruebaLibre.trim(), null, "+")))
                    pruebaLibre = ""
                }
                CampoTextoEval(pruebaLibre, { pruebaLibre = it }, "Otra prueba…", Modifier.weight(1f), onListo = { agregarLibre() })
                Spacer(Modifier.width(6.dp))
                BotonMas(habilitado = pruebaLibre.isNotBlank()) { agregarLibre() }
            }
        }

        // ── Test funcional ──
        if (Bloque.TEST in visibles) SeccionEval("Test funcional", { toggleBloque(Bloque.TEST) }) {
            datos.tests.forEachIndexed { i, t ->
                TestFuncionalCard(
                    id = t.id, respuestas = t.respuestas,
                    onChange = { r ->
                        set(datos.copy(tests = datos.tests.mapIndexed { j, x ->
                            if (j == i) x.copy(respuestas = r, puntaje = calcularPuntaje(x.id, r).puntaje) else x
                        }))
                    },
                    onQuitar = { set(datos.copy(tests = datos.tests.filterIndexed { j, _ -> j != i })) },
                )
                Spacer(Modifier.height(8.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LISTA_TESTS.filter { t -> datos.tests.none { it.id == t.id } }
                    .sortedByDescending { it.id == testSugerido }
                    .forEach { t ->
                        val sug = t.id == testSugerido
                        ChipEval("${if (sug) "★ " else "+ "}${t.corto} · ${t.region.lowercase()}", destacado = sug) {
                            set(datos.copy(tests = datos.tests + TestAplicado(t.id, List(t.preguntas.size) { null }, null)))
                        }
                    }
            }
        }

        // ── Objetivos ──
        if (conObjetivos && Bloque.OBJETIVOS in visibles) SeccionEval("Objetivos del tratamiento", { toggleBloque(Bloque.OBJETIVOS) }) {
            ObjetivosBorrador(objetivos, { objetivos = it }, regiones, datos)
        }

        if (lleno) {
            val z = textoZonas(datos.zonas)
            Text("Se guarda al completar${if (z.isNotEmpty()) " · $z" else ""}. Lo verás en la pestaña 📏 Evaluación de la ficha.",
                color = c.textoSuave, fontSize = 10.sp)
        }
    }
}

// ─── Piezas ──────────────────────────────────────────────────────────────────

@Composable
internal fun ChipEval(texto: String, activo: Boolean = false, destacado: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else if (destacado) c.navyLight else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(texto, color = if (activo) c.sobreNavy else if (destacado) c.navy else c.texto,
            fontSize = 11.sp, fontWeight = if (activo || destacado) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun SeccionEval(titulo: String, onCerrar: () -> Unit, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
            Text("ocultar", color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.clickable(onClick = onCerrar).padding(4.dp))
        }
        contenido()
    }
}

/** D / I: tocar el marcado lo quita. */
@Composable
internal fun LadoToggle(valor: String?, onChange: (String?) -> Unit) {
    val c = Sania.colors
    Row(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, c.borde, RoundedCornerShape(8.dp))) {
        listOf("D", "I").forEach { l ->
            val on = valor == l
            Box(
                Modifier.width(32.dp).height(32.dp).background(if (on) c.navy else Color.Transparent)
                    .clickable { onChange(if (on) null else l) },
                contentAlignment = Alignment.Center,
            ) { Text(l, color = if (on) c.sobreNavy else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Campo de texto compacto (los OutlinedTextField de 56dp no caben en filas). */
@Composable
internal fun CampoTextoEval(
    valor: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text, alinear: TextAlign = TextAlign.Start, borde: Color? = null,
    onListo: (() -> Unit)? = null,
) {
    val c = Sania.colors
    BasicTextField(
        value = valor, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = c.texto, fontSize = 13.sp, textAlign = alinear),
        cursorBrush = SolidColor(c.navy),
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = if (onListo != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onListo?.invoke() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(c.superficie)
                    .border(1.5.dp, borde ?: c.borde, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 8.dp),
            ) {
                if (valor.isEmpty()) Text(placeholder, color = c.textoSuave, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = alinear, modifier = Modifier.fillMaxWidth())
                inner()
            }
        },
    )
}

/**
 * Grados: teclado numérico + botón ± (el teclado numérico de Android no siempre
 * trae el signo menos, y el déficit de extensión va en negativo).
 */
@Composable
private fun CampoGrados(grados: Int?, bajo: Boolean, onChange: (Int?) -> Unit) {
    val c = Sania.colors
    var texto by remember(grados) { mutableStateOf(grados?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("±", color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier
            .clickable { grados?.let { onChange(-it) } }.padding(horizontal = 6.dp, vertical = 6.dp))
        CampoTextoEval(
            texto,
            { nuevo ->
                val limpio = nuevo.filterIndexed { i, ch -> ch.isDigit() || (ch == '-' && i == 0) }.take(4)
                texto = limpio
                onChange(limpio.toIntOrNull())
            },
            "°", Modifier.width(58.dp), teclado = KeyboardType.Number, alinear = TextAlign.End,
            borde = if (bajo) c.pend else null,
        )
    }
}

@Composable
private fun BotonMas(habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (habilitado) c.navy else c.borde)
            .clickable(enabled = habilitado, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("＋", color = if (habilitado) c.sobreNavy else c.textoSuave, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

// ─── Test funcional ──────────────────────────────────────────────────────────

/**
 * Un test con el puntaje EN VIVO. El fisio lo llena en la lista (una fila de opciones
 * por pregunta) o se lo pasa al PACIENTE: pantalla completa, letra grande, una
 * pregunta por vez que avanza sola. Contestado todo, la lista se pliega.
 */
@Composable
private fun TestFuncionalCard(id: String, respuestas: List<Int?>, onChange: (List<Int?>) -> Unit, onQuitar: () -> Unit) {
    val c = Sania.colors
    val t = TESTS_FUNCIONALES[id] ?: return
    var abierto by remember { mutableStateOf(true) }
    var modoPaciente by remember { mutableStateOf(false) }
    val pts = calcularPuntaje(id, respuestas)
    val resp by rememberUpdatedState(respuestas)
    fun set(i: Int, v: Int?) {
        val r = resp.toMutableList()
        while (r.size < t.preguntas.size) r.add(null)
        r[i] = v
        onChange(r)
        if (v != null && r.indices.all { j -> r[j] != null || t.preguntas[j].omitible }) abierto = false
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { abierto = !abierto }) {
                Text(t.nombre, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val extra = pts.puntaje?.let { " · ${numJs(it)}${if (t.unidad == "%") " %" else " pts"} · ${pts.interpretacion}" } ?: ""
                Text("${pts.contestadas}/${pts.total} respondidas$extra", color = if (pts.puntaje != null) c.navy else c.textoSuave,
                    fontSize = 11.sp, fontWeight = if (pts.puntaje != null) FontWeight.Bold else FontWeight.Normal)
            }
            Text("📱 Paciente", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier
                .clip(RoundedCornerShape(8.dp)).border(1.dp, c.lav, RoundedCornerShape(8.dp))
                .clickable { modoPaciente = true }.padding(horizontal = 8.dp, vertical = 5.dp))
            Text("✕", color = c.textoSuave, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onQuitar).padding(8.dp))
        }
        AnimatedVisibility(abierto) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                t.preguntas.forEachIndexed { i, p ->
                    Column {
                        Text("${i + 1}. ${p.titulo}${if (p.omitible) " (puede omitirse)" else ""}", color = c.texto,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        p.opciones.forEach { op ->
                            val sel = respuestas.getOrNull(i) == op.valor
                            Text(op.texto, color = if (sel) c.sobreNavy else c.texto, fontSize = 12.sp, modifier = Modifier
                                .fillMaxWidth().padding(bottom = 3.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (sel) c.navy else c.superficie)
                                .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(8.dp))
                                .clickable { set(i, if (sel) null else op.valor) }
                                .padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
    if (modoPaciente) ModoPacienteTest(id, respuestas, onSet = { i, v -> set(i, v) }, onCerrar = { modoPaciente = false })
}

/** El paciente contesta en la tablet/celular: una pregunta por pantalla, avanza sola. */
@Composable
private fun ModoPacienteTest(id: String, respuestas: List<Int?>, onSet: (Int, Int?) -> Unit, onCerrar: () -> Unit) {
    val c = Sania.colors
    val t = TESTS_FUNCIONALES[id] ?: return
    val primeraSin = t.preguntas.indices.firstOrNull { respuestas.getOrNull(it) == null } ?: 0
    var i by remember { mutableStateOf(primeraSin) }
    var fin by remember { mutableStateOf(false) }
    var avanzarA by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(avanzarA) {
        val sig = avanzarA ?: return@LaunchedEffect
        delay(180) // se ve la opción marcada antes de pasar
        if (sig >= t.preguntas.size) fin = true else i = sig
        avanzarA = null
    }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.fondo)) {
            Row(Modifier.fillMaxWidth().background(c.superficie).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.nombre, color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!fin) Text("Pregunta ${i + 1} de ${t.preguntas.size}", color = c.textoSuave, fontSize = 12.sp)
                }
                Text("Cerrar", color = c.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onCerrar).padding(8.dp))
            }
            val avance = (if (fin) t.preguntas.size else i).toFloat() / t.preguntas.size
            Box(Modifier.fillMaxWidth().height(5.dp).background(c.borde)) {
                Box(Modifier.fillMaxWidth(avance).height(5.dp).background(c.navy))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp)) {
                if (fin) {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", fontSize = 44.sp)
                        Text("¡Gracias! Ya terminó.", color = c.texto, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp))
                        Text("Por favor, devuelva el equipo a su fisioterapeuta.", color = c.textoSuave, fontSize = 16.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                        Text("Listo", color = c.sobreNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier
                            .padding(top = 28.dp).clip(RoundedCornerShape(12.dp)).background(c.navy)
                            .clickable(onClick = onCerrar).padding(horizontal = 28.dp, vertical = 12.dp))
                    }
                } else {
                    val p = t.preguntas[i]
                    if (i == 0) Text(t.instrucciones, color = c.textoSuave, fontSize = 15.sp, modifier = Modifier.padding(bottom = 14.dp))
                    Text(p.titulo, color = c.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp,
                        modifier = Modifier.padding(bottom = 16.dp))
                    p.opciones.forEach { op ->
                        val sel = respuestas.getOrNull(i) == op.valor
                        Text(op.texto, color = if (sel) c.sobreNavy else c.texto, fontSize = 16.sp, lineHeight = 21.sp, modifier = Modifier
                            .fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) c.navy else c.superficie)
                            .border(2.dp, if (sel) c.navy else c.borde, RoundedCornerShape(16.dp))
                            .clickable(enabled = avanzarA == null) { onSet(i, op.valor); avanzarA = i + 1 }
                            .padding(horizontal = 16.dp, vertical = 14.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("← Anterior", color = if (i == 0) c.borde else c.textoSuave, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = i > 0) { i -= 1 }.padding(10.dp))
                        Spacer(Modifier.weight(1f))
                        if (p.omitible) Text("Prefiero no responder →", color = c.textoSuave, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onSet(i, null); if (i < t.preguntas.size - 1) i += 1 else fin = true }.padding(10.dp))
                    }
                }
            }
        }
    }
}

// ─── Objetivos (borrador, dentro de la evaluación) ───────────────────────────

internal fun numDe(s: String): Double? = s.replace(',', '.').trim().toDoubleOrNull()

/** Texto + (opcional) inicial / meta / unidad. */
@Composable
internal fun NuevoObjetivo(onAgregar: (ObjetivoBorrador) -> Unit) {
    val c = Sania.colors
    var texto by remember { mutableStateOf("") }
    var medida by remember { mutableStateOf(false) }
    var ini by remember { mutableStateOf("") }
    var meta by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf("") }
    fun agregar() {
        if (texto.isBlank()) return
        onAgregar(ObjetivoBorrador(texto.trim(), unidad.trim().ifEmpty { null }, numDe(ini), numDe(meta)))
        texto = ""; ini = ""; meta = ""; unidad = ""; medida = false
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CampoTextoEval(texto, { texto = it }, "Ej. Volver a correr 5 km", Modifier.weight(1f), onListo = { agregar() })
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .border(1.dp, if (medida) c.navy else c.borde, RoundedCornerShape(8.dp)).clickable { medida = !medida },
                contentAlignment = Alignment.Center,
            ) { Text("📐", fontSize = 14.sp) }
            Spacer(Modifier.width(6.dp))
            BotonMas(texto.isNotBlank()) { agregar() }
        }
        if (medida) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CampoTextoEval(ini, { ini = it }, "Inicial", Modifier.width(72.dp), teclado = KeyboardType.Decimal)
                Text(" → ", color = c.textoSuave, fontSize = 12.sp)
                CampoTextoEval(meta, { meta = it }, "Meta", Modifier.width(72.dp), teclado = KeyboardType.Decimal)
                Spacer(Modifier.width(6.dp))
                CampoTextoEval(unidad, { unidad = it }, "° / km / min", Modifier.width(96.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObjetivosBorrador(
    valor: List<ObjetivoBorrador>,
    onChange: (List<ObjetivoBorrador>) -> Unit,
    regiones: List<String>,
    datos: EvaluacionFisioDatos,
) {
    val c = Sania.colors
    val sugeridos = sugerirObjetivos(regiones, datos, valor.map { it.texto })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        valor.forEachIndexed { i, o ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.chipBg).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val m = medidaTexto(o.valorInicial, o.meta, o.unidad)
                Text("🎯 ${o.texto}${if (m.isNotEmpty()) " · $m" else ""}", color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("✕", color = c.textoSuave, fontSize = 13.sp, modifier = Modifier
                    .clickable { onChange(valor.filterIndexed { j, _ -> j != i }) }.padding(4.dp))
            }
        }
        if (sugeridos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sugeridos.forEach { s ->
                    val m = medidaTexto(s.valorInicial, s.meta, s.unidad)
                    ChipEval("+ ${s.texto}${if (m.isNotEmpty()) " ($m)" else ""}") { onChange(valor + s) }
                }
            }
        }
        NuevoObjetivo { onChange(valor + it) }
    }
}
