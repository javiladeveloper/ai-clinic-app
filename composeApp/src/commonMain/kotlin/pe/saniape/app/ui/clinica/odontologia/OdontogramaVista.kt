package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CUADRANTES_ADULTO
import pe.saniape.app.data.staff.CUADRANTES_DECIDUO
import pe.saniape.app.data.staff.COLOR_REALIZADO
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.OdontogramaRepo
import pe.saniape.app.data.staff.PSEUDO_DIENTES
import pe.saniape.app.data.staff.PintadoDiente
import pe.saniape.app.data.staff.Zona
import pe.saniape.app.data.staff.caraEnZona
import pe.saniape.app.data.staff.pintarDiente
import pe.saniape.app.data.staff.zonaEn
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/**
 * El odontograma de un paciente: 32 piezas (o 20 de leche) con sus 5 caras
 * tocables, más los hallazgos de boca completa.
 *
 * SOLO ODONTOLOGÍA. Esta vista vive aparte, en su propia carpeta, y la ficha la
 * monta únicamente cuando la clínica hace odontología (`esOdontologia`). Una
 * clínica de fisioterapia o estética nunca la carga, ni sus consultas.
 *
 * Gemelo de `components/odontologia/Odontograma.tsx` en la web.
 *
 * [citaId] ata lo que se marque a la atención en curso. [soloLectura] para
 * pacientes dados de baja: se ve, no se marca.
 */
@Composable
fun OdontogramaVista(
    pacienteId: String,
    citaId: String? = null,
    soloLectura: Boolean = false,
    /** Se llama cada vez que cambia algo, por si la pantalla de arriba necesita refrescar. */
    onCambio: () -> Unit = {},
    /** En la revisión previa al diagnóstico se marca y nada más: sin presupuesto. */
    mostrarPresupuesto: Boolean = true,
    /** Para ofrecer solo servicios dentales en una clínica mixta. */
    mapaDental: pe.saniape.app.data.staff.MapaDental = pe.saniape.app.data.staff.MapaDental(),
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var catalogo by remember { mutableStateOf<List<HallazgoDental>>(emptyList()) }
    var hallazgos by remember { mutableStateOf<List<DienteHallazgo>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var deciduo by remember { mutableStateOf(false) }
    // El diente abierto en el panel, y la cara que se tocó para llegar.
    var abierto by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var dictadoAbierto by remember { mutableStateOf(false) }
    var panelBoca by remember { mutableStateOf(false) }

    suspend fun recargar() {
        hallazgos = OdontogramaRepo.hallazgos(pacienteId)
        onCambio()
    }

    LaunchedEffect(pacienteId) {
        cargando = true
        catalogo = OdontogramaRepo.catalogo()
        hallazgos = OdontogramaRepo.hallazgos(pacienteId)
        cargando = false
    }

    if (cargando) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = c.navy)
        }
        return
    }

    val porDiente = remember(hallazgos) { hallazgos.groupBy { it.diente } }
    val cuadrantes = if (deciduo) CUADRANTES_DECIDUO else CUADRANTES_ADULTO

    Column(Modifier.fillMaxWidth()) {
        // ── Cabecera: adulto / niño y dictado ─────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("🦷 Odontograma", color = c.texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (deciduo) "Adulto" else "Niño", activo = false) { deciduo = !deciduo }
                if (!soloLectura) Chip("🎙 Dictar", activo = true) { dictadoAbierto = true }
            }
        }

        // ── La boca: arcada superior sobre la inferior ────────────────────
        Boca(
            cuadrantes = cuadrantes,
            porDiente = porDiente, catalogo = catalogo,
            onTocar = { d, cara -> if (!soloLectura) abierto = d to cara },
        )

        Spacer(Modifier.height(10.dp))
        Leyenda()

        // ── Hallazgos de boca completa (sarro, gingivitis…) ───────────────
        // No son de una pieza, así que no tienen lugar en el diagrama: sin
        // esta sección se marcarían y no se verían en ninguna parte.
        val deBoca = hallazgos.filter { it.diente in PSEUDO_DIENTES }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Boca completa", color = c.texto, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            if (!soloLectura) Chip("+ Añadir", activo = false) { panelBoca = true }
        }
        if (deBoca.isEmpty()) {
            Text("Sin hallazgos generales.", color = c.textoSuave, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
        } else {
            val porId = catalogo.associateBy { it.id }
            deBoca.forEach { h ->
                val hal = porId[h.hallazgoId]
                FilaHallazgo(
                    nombre = hal?.nombre ?: "Hallazgo",
                    color = colorDe(if (h.estado == "Realizado") COLOR_REALIZADO else hal?.color),
                    detalle = if (h.estado == "Realizado") "Realizado" else "Pendiente",
                    soloLectura = soloLectura,
                    onAlternar = {
                        scope.launch {
                            val nuevo = if (h.estado == "Realizado") "Pendiente" else "Realizado"
                            if (OdontogramaRepo.cambiarEstado(h.id, nuevo)) recargar()
                            else Toaster.error("No se pudo cambiar")
                        }
                    },
                    onQuitar = {
                        scope.launch {
                            if (OdontogramaRepo.borrar(h.id)) recargar() else Toaster.error("No se pudo quitar")
                        }
                    },
                )
            }
        }

        // ── Presupuesto ──────────────────────────────────────────────────
        if (mostrarPresupuesto) PresupuestoOdontograma(
            mapaDental = mapaDental,
            pacienteId = pacienteId,
            citaId = citaId,
            hallazgos = hallazgos,
            catalogo = catalogo,
            soloLectura = soloLectura,
            // El catálogo también: al asignar un servicio a un hallazgo, sin
            // recargarlo seguiría figurando "sin servicio".
            onCambio = { scope.launch { catalogo = OdontogramaRepo.catalogo(); recargar() } },
        )
    }

    // ── Panel de un diente ────────────────────────────────────────────────
    abierto?.let { (diente, cara) ->
        PanelDiente(
            diente = diente,
            carasPreseleccionadas = listOfNotNull(cara),
            hallazgosDelDiente = porDiente[diente].orEmpty(),
            catalogo = catalogo,
            onCerrar = { abierto = null },
            onAgregar = { hallazgoId, caras ->
                scope.launch {
                    val ok = OdontogramaRepo.agregar(pacienteId, diente, hallazgoId, caras, citaId)
                    if (ok) recargar() else Toaster.error("No se pudo marcar")
                }
            },
            onAlternar = { h ->
                scope.launch {
                    val nuevo = if (h.estado == "Realizado") "Pendiente" else "Realizado"
                    if (OdontogramaRepo.cambiarEstado(h.id, nuevo)) recargar() else Toaster.error("No se pudo cambiar")
                }
            },
            onQuitar = { h ->
                scope.launch {
                    if (OdontogramaRepo.borrar(h.id)) recargar() else Toaster.error("No se pudo quitar")
                }
            },
        )
    }

    // ── Marcar un hallazgo para la boca entera ────────────────────────────
    if (panelBoca) {
        PanelBoca(
            catalogo = catalogo,
            yaMarcados = hallazgos.filter { it.diente in PSEUDO_DIENTES && it.estado == "Pendiente" }
                .map { it.hallazgoId }.toSet(),
            onCerrar = { panelBoca = false },
            onElegir = { hallazgoId ->
                panelBoca = false
                scope.launch {
                    // "BOCA", igual que la web: el presupuesto lo cobra una vez
                    // y el diagnóstico lo redacta como general.
                    val ok = OdontogramaRepo.agregar(pacienteId, "BOCA", hallazgoId, null, citaId)
                    if (ok) { recargar(); Toaster.exito("Registrado para toda la boca") }
                    else Toaster.error("No se pudo registrar")
                }
            },
        )
    }

    // ── Dictado por voz ───────────────────────────────────────────────────
    if (dictadoAbierto) {
        DictadoOdontograma(
            catalogo = catalogo,
            onCerrar = { dictadoAbierto = false },
            onAplicar = { lote ->
                scope.launch {
                    var fallos = 0
                    for (h in lote) {
                        val ok = OdontogramaRepo.agregar(
                            pacienteId,
                            diente = h.diente ?: "BOCA",
                            hallazgoId = h.hallazgoId ?: continue,
                            superficies = h.superficies,
                            citaId = citaId,
                        )
                        if (!ok) fallos++
                    }
                    recargar()
                    if (fallos == 0) Toaster.exito("${lote.size} hallazgo(s) marcados")
                    else Toaster.error("$fallos no se pudieron marcar")
                }
                dictadoAbierto = false
            },
        )
    }
}

/**
 * La boca completa, adaptada al ancho.
 *
 * Si las 16 piezas de una arcada entran con un tamaño que se pueda tocar
 * (tablet, teléfono girado), se ve todo junto como en papel. En un teléfono
 * NO entran: antes cada arcada se deslizaba por su cuenta, se veía media boca
 * y la superior quedaba desalineada de la inferior. Ahora la boca se parte en
 * dos páginas por LADO del paciente (derecho: 18–11 sobre 48–41; izquierdo:
 * 21–28 sobre 31–38): cada página llena el ancho con piezas grandes, las dos
 * arcadas se mueven juntas y la línea media queda en el borde que toca.
 * Las pestañas cuentan las piezas con hallazgos de cada lado, para que no se
 * pase por alto lo que está en la página que no se ve.
 */
@Composable
private fun Boca(
    cuadrantes: List<List<String>>,
    porDiente: Map<String, List<DienteHallazgo>>,
    catalogo: List<HallazgoDental>,
    onTocar: (String, String?) -> Unit,
) {
    val c = Sania.colors
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        val porCuadrante = cuadrantes[0].size
        val todoJunto = (maxWidth - LINEA_MEDIA) / (porCuadrante * 2) - PAD_PIEZA * 2
        if (todoJunto >= PIEZA_MIN) {
            val tam = minOf(todoJunto, PIEZA_MAX)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                EtiquetaArcada("Superior")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilaPiezas(cuadrantes[0], tam, porDiente, catalogo, onTocar)
                    LineaMedia(tam)
                    FilaPiezas(cuadrantes[1], tam, porDiente, catalogo, onTocar)
                }
                PlanoOclusal()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilaPiezas(cuadrantes[2], tam, porDiente, catalogo, onTocar)
                    LineaMedia(tam)
                    FilaPiezas(cuadrantes[3], tam, porDiente, catalogo, onTocar)
                }
                EtiquetaArcada("Inferior")
            }
        } else {
            // Una página por lado: el cuadrante llena el ancho (menos la línea media).
            val tam = minOf((maxWidth - LINEA_MEDIA) / porCuadrante - PAD_PIEZA * 2, PIEZA_MAX)
            val pager = rememberPagerState { 2 }
            val scope = rememberCoroutineScope()
            val lados = listOf(
                Triple("Lado derecho", cuadrantes[0], cuadrantes[2]),
                Triple("Lado izquierdo", cuadrantes[1], cuadrantes[3]),
            )
            Column(Modifier.fillMaxWidth()) {
                // Pestañas: qué lado se ve, y cuántas piezas con hallazgos tiene cada uno.
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(50)).background(c.fondo),
                ) {
                    lados.forEachIndexed { i, (nombre, sup, inf) ->
                        val marcadas = (sup + inf).count { !porDiente[it].isNullOrEmpty() }
                        val activo = pager.currentPage == i
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(if (activo) c.navy else Color.Transparent)
                                .clickable { scope.launch { pager.animateScrollToPage(i) } }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                nombre + (if (marcadas > 0) " · $marcadas" else ""),
                                color = if (activo) c.sobreNavy else c.textoSuave,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            )
                        }
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { i ->
                    val (_, sup, inf) = lados[i]
                    // La línea media va del lado que toca: a la derecha en la página
                    // del lado derecho (la boca se mira de frente) y a la izquierda
                    // en la otra.
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EtiquetaArcada("Superior")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (i == 1) LineaMedia(tam)
                            FilaPiezas(sup, tam, porDiente, catalogo, onTocar)
                            if (i == 0) LineaMedia(tam)
                        }
                        PlanoOclusal()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (i == 1) LineaMedia(tam)
                            FilaPiezas(inf, tam, porDiente, catalogo, onTocar)
                            if (i == 0) LineaMedia(tam)
                        }
                        EtiquetaArcada("Inferior")
                    }
                }
                Text(
                    "Desliza para ver el otro lado de la boca",
                    color = c.textoSuave, fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FilaPiezas(
    piezas: List<String>,
    tam: Dp,
    porDiente: Map<String, List<DienteHallazgo>>,
    catalogo: List<HallazgoDental>,
    onTocar: (String, String?) -> Unit,
) {
    piezas.forEach { d -> Pieza(d, tam, pintarDiente(porDiente[d].orEmpty(), catalogo), onTocar) }
}

/** La línea media: sin ella no se distingue dónde termina un cuadrante y empieza el otro. */
@Composable
private fun LineaMedia(tam: Dp) {
    Box(
        Modifier.padding(horizontal = (LINEA_MEDIA - 2.dp) / 2).width(2.dp).height(tam + 16.dp)
            .background(Sania.colors.navy.copy(alpha = 0.35f)),
    )
}

/** El plano de oclusión: separa la arcada superior de la inferior. */
@Composable
private fun PlanoOclusal() {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Sania.colors.borde))
}

@Composable
private fun EtiquetaArcada(texto: String) {
    Text(
        texto, color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** Debajo de 34dp una cara no se toca sin acertar a la de al lado. */
private val PIEZA_MIN = 34.dp
private val PIEZA_MAX = 52.dp
private val PAD_PIEZA = 1.dp
private val LINEA_MEDIA = 10.dp

/** Una pieza: su número y el diagrama de 5 caras. */
@Composable
private fun Pieza(diente: String, tam: Dp, pintado: PintadoDiente, onTocar: (String, String?) -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.padding(horizontal = PAD_PIEZA).width(tam),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DienteDiagrama(diente, tam, pintado, onTocarCara = { cara -> onTocar(diente, cara) })
        Text(
            diente,
            color = c.texto, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            // Tocar el número abre el diente sin cara elegida (diente entero).
            modifier = Modifier.clickable { onTocar(diente, null) }.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

/**
 * El diagrama de 5 caras de una pieza, dibujado con Canvas.
 *
 * El tamaño lo decide [Boca] según el ancho disponible (34–52dp).
 * La orientación (qué cara va en qué lado) sale de `caraEnZona`, probado aparte.
 */
@Composable
private fun DienteDiagrama(diente: String, tam: Dp, pintado: PintadoDiente, onTocarCara: (String) -> Unit) {
    val c = Sania.colors
    val borde = c.textoSuave.copy(alpha = 0.55f)
    val vacio = c.fondo
    Canvas(
        Modifier.size(tam).pointerInput(diente) {
            detectTapGestures { p ->
                val lado = size.width.toFloat()
                val zona = zonaEn(p.x, p.y, lado, lado * MARGEN) ?: return@detectTapGestures
                onTocarCara(caraEnZona(diente, zona))
            }
        },
    ) {
        val s = size.width
        val m = s * MARGEN
        fun rellenar(zona: Zona, puntos: List<Offset>) {
            val cara = caraEnZona(diente, zona)
            val color = pintado.porSuperficie[cara]?.let { colorDe(it) } ?: vacio
            val path = Path().apply {
                moveTo(puntos[0].x, puntos[0].y)
                puntos.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, color)
            drawPath(path, borde, style = Stroke(width = 1.2f))
        }
        rellenar(Zona.ARRIBA, listOf(Offset(0f, 0f), Offset(s, 0f), Offset(s - m, m), Offset(m, m)))
        rellenar(Zona.ABAJO, listOf(Offset(0f, s), Offset(s, s), Offset(s - m, s - m), Offset(m, s - m)))
        rellenar(Zona.IZQUIERDA, listOf(Offset(0f, 0f), Offset(0f, s), Offset(m, s - m), Offset(m, m)))
        rellenar(Zona.DERECHA, listOf(Offset(s, 0f), Offset(s, s), Offset(s - m, s - m), Offset(s - m, m)))
        rellenar(Zona.CENTRO, listOf(Offset(m, m), Offset(s - m, m), Offset(s - m, s - m), Offset(m, s - m)))

        // Pieza ausente: una X encima de todo el diagrama, como en papel.
        if (pintado.ausente) {
            val x = Color(0xFF6B7280)
            drawLine(x, Offset(2f, 2f), Offset(s - 2f, s - 2f), strokeWidth = 3f)
            drawLine(x, Offset(s - 2f, 2f), Offset(2f, s - 2f), strokeWidth = 3f)
        }
    }
}

/** Proporción del borde de los trapecios respecto al lado del cuadrado. */
private const val MARGEN = 0.3f

@Composable
private fun Leyenda() {
    val c = Sania.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemLeyenda(Color(0xFFDC2626), "Pendiente")
        ItemLeyenda(colorDe(COLOR_REALIZADO), "Realizado")
        Text("✕ Ausente", color = c.textoSuave, fontSize = 11.sp)
    }
}

@Composable
private fun ItemLeyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(texto, color = Sania.colors.textoSuave, fontSize = 11.sp)
    }
}

@Composable
internal fun Chip(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(50))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Una fila de hallazgo con sus dos acciones: cambiar estado y quitar. */
@Composable
internal fun FilaHallazgo(
    nombre: String,
    color: Color,
    detalle: String,
    soloLectura: Boolean,
    onAlternar: () -> Unit,
    onQuitar: () -> Unit,
) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(detalle, color = c.textoSuave, fontSize = 11.sp)
        }
        if (!soloLectura) {
            // Revertir importa: marcar "hecho" por error no debe obligar a
            // borrar y perder la fecha.
            Text(
                if (detalle.startsWith("Realizado")) "↩ Pendiente" else "✓ Hecho",
                color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAlternar() }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                "✕", color = c.error, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onQuitar() }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** "#dc2626" → Color. Un color mal formado cae al rojo de hallazgo, no rompe el dibujo. */
internal fun colorDe(hex: String?): Color = runCatching {
    Color(("ff" + (hex ?: "#dc2626").removePrefix("#").take(6)).toLong(16))
}.getOrDefault(Color(0xFFDC2626))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipsCatalogo(
    catalogo: List<HallazgoDental>,
    onElegir: (HallazgoDental) -> Unit,
    deshabilitados: Set<String> = emptySet(),
) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        catalogo.forEach { h ->
            val off = h.id in deshabilitados
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(50))
                    .clickable(enabled = !off) { onElegir(h) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorDe(h.color)))
                Spacer(Modifier.width(6.dp))
                Text(h.nombre, color = if (off) c.textoSuave else c.texto, fontSize = 12.sp)
            }
        }
    }
}
