package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import pe.saniape.app.data.staff.calcularCPO
import pe.saniape.app.data.staff.esDePiezaEntera
import pe.saniape.app.data.staff.ordenarCaras
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
@OptIn(ExperimentalLayoutApi::class)
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
    /**
     * Recibe lo cargado cada vez que cambia (al abrir y tras cada marca). Así
     * quien monta el odontograma (la revisión previa) no vuelve a pedir a la red
     * lo mismo que esta vista acaba de traer.
     */
    onDatos: ((hallazgos: List<DienteHallazgo>, catalogo: List<HallazgoDental>) -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var catalogo by remember { mutableStateOf<List<HallazgoDental>>(emptyList()) }
    var hallazgos by remember { mutableStateOf<List<DienteHallazgo>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    // Escrituras EN FILA: dos toques rápidos (pincel sobre la misma cara, doble
    // toque en "✓ Hecho") no crean dos registros ni se pisan; cada una ve lo que
    // dejó la anterior. [guardando] prende un aviso chico sin tapar el diagrama.
    val fila = remember { Mutex() }
    var guardando by remember { mutableIntStateOf(0) }
    val onCambioActual by rememberUpdatedState(onCambio)
    val onDatosActual by rememberUpdatedState(onDatos)
    var deciduo by remember { mutableStateOf(false) }
    // El diente abierto en el panel, y la cara que se tocó para llegar.
    var abierto by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var dictadoAbierto by remember { mutableStateOf(false) }
    var panelBoca by remember { mutableStateOf(false) }
    // Modo pincel: se elige el hallazgo UNA vez y cada toque en una cara lo registra.
    var pincel by remember { mutableStateOf<HallazgoDental?>(null) }
    var eligiendoPincel by remember { mutableStateOf(false) }
    var mostrarPaciente by remember { mutableStateOf(false) }

    suspend fun recargar() {
        // Si la red falla se conserva lo que se ve (no se "vacía" la boca).
        OdontogramaRepo.hallazgosONull(pacienteId)?.let { hallazgos = it }
        onDatosActual?.invoke(hallazgos, catalogo)
        onCambioActual()
    }

    /** Una escritura en fila, con aviso de "Guardando…". */
    fun escribir(bloque: suspend () -> Unit) {
        scope.launch {
            guardando++
            try { fila.withLock { bloque() } } finally { guardando-- }
        }
    }

    LaunchedEffect(pacienteId) {
        cargando = true
        // En paralelo: el catálogo (casi siempre ya en caché) y lo del paciente.
        coroutineScope {
            val cat = async { OdontogramaRepo.catalogo() }
            val hal = async { OdontogramaRepo.hallazgos(pacienteId) }
            catalogo = cat.await()
            hallazgos = hal.await()
        }
        cargando = false
        onDatosActual?.invoke(hallazgos, catalogo)
    }

    if (cargando) {
        EsqueletoOdontograma()
        return
    }

    val porDiente = remember(hallazgos) { hallazgos.groupBy { it.diente } }
    val cuadrantes = if (deciduo) CUADRANTES_DECIDUO else CUADRANTES_ADULTO
    val porIdCatalogo = remember(catalogo) { catalogo.associateBy { it.id } }

    // El toque en una pieza con una referencia ESTABLE: si cambiara en cada
    // recomposición (captura porDiente, pincel…), las 32 piezas se volverían a
    // componer y redibujar por marcar una sola.
    val tocarActual by rememberUpdatedState<(String, String?) -> Unit> { d, cara ->
        val p = pincel
        if (!soloLectura) {
            if (p == null) abierto = d to cara
            else escribir {
                // Pincel: la misma pieza con el mismo hallazgo pendiente se FUSIONA
                // (una caries MO, se cobra una vez); los de pieza entera, sin caras.
                // Se lee `hallazgos` DENTRO de la fila: lo que dejó el toque anterior.
                val existente = hallazgos
                    .firstOrNull { it.diente == d && it.hallazgoId == p.id && it.estado == "Pendiente" }
                val entera = esDePiezaEntera(p) || cara == null
                val previas = existente?.superficies.orEmpty()
                val ok = when {
                    existente != null && (entera || previas.isEmpty() || cara in previas) -> null  // ya estaba
                    existente != null && cara != null -> OdontogramaRepo.actualizarSuperficies(existente.id, previas + cara)
                    else -> OdontogramaRepo.agregar(pacienteId, d, p.id, if (entera || cara == null) null else listOf(cara), citaId)
                }
                when (ok) {
                    true -> recargar()
                    false -> Toaster.error("No se pudo marcar")
                    null -> {}
                }
            }
        }
    }
    val onTocar: (String, String?) -> Unit = remember { { d, cara -> tocarActual(d, cara) } }

    Column(Modifier.fillMaxWidth()) {
        // ── Cabecera: adulto / niño y dictado ─────────────────────────────
        Text("🦷 Odontograma", color = c.texto, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip(if (deciduo) "Adulto" else "Niño", activo = false) { deciduo = !deciduo }
            if (!soloLectura) Chip("🎙 Dictar", activo = true) { dictadoAbierto = true }
            if (!soloLectura) Chip(if (pincel != null) "🖌 Pincel ✓" else "🖌 Pincel", activo = pincel != null) {
                if (pincel != null) pincel = null else eligiendoPincel = true
            }
            // Girar el celular hacia el paciente y explicarle: sin botones ni siglas.
            Chip("👁 Mostrar al paciente", activo = false) { mostrarPaciente = true }
        }

        // Pincel activo: qué se está marcando y cómo salir. Entra y sale
        // deslizando: sin eso el diagrama "salta" hacia abajo de golpe.
        // (Recuerda el último hallazgo para que la salida no se dibuje vacía.)
        var ultimoPincel by remember { mutableStateOf<HallazgoDental?>(null) }
        if (pincel != null) ultimoPincel = pincel
        AnimatedVisibility(
            visible = pincel != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) { ultimoPincel?.let { p ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.chipBg).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorDe(p.color)))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (esDePiezaEntera(p)) "Pincel: ${p.nombre}. Toca cada pieza." else "Pincel: ${p.nombre}. Toca cada cara afectada.",
                    color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                Text("✕ Salir", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { pincel = null }.padding(horizontal = 4.dp))
            }
        } }

        // ── La boca: arcada superior sobre la inferior ────────────────────
        Boca(cuadrantes = cuadrantes, porDiente = porDiente, catalogo = catalogo, onTocar = onTocar)

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Leyenda() }
            // Aviso chico mientras se guarda: el diagrama sigue a la vista y usable.
            AnimatedVisibility(visible = guardando > 0, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guardando…", color = c.textoSuave, fontSize = 11.sp)
                }
            }
        }

        // Índice de la norma MINSA: CPO-D (permanente) o ceo-d (temporal).
        val cpo = remember(hallazgos, catalogo, deciduo) { calcularCPO(hallazgos, catalogo, permanente = !deciduo) }
        if (cpo.total > 0) {
            Text(
                (if (deciduo) "ceo-d" else "CPO-D") + ": ${cpo.total}  ·  " +
                    (if (deciduo) "c ${cpo.c} · e ${cpo.p} · o ${cpo.o}" else "C ${cpo.c} · P ${cpo.p} · O ${cpo.o}"),
                color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // ── Hallazgos de boca completa (sarro, gingivitis…) ───────────────
        // No son de una pieza, así que no tienen lugar en el diagrama: sin
        // esta sección se marcarían y no se verían en ninguna parte.
        val deBoca = remember(hallazgos) { hallazgos.filter { it.diente in PSEUDO_DIENTES } }
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
            deBoca.forEach { h ->
                val hal = porIdCatalogo[h.hallazgoId]
                FilaHallazgo(
                    nombre = hal?.nombre ?: "Hallazgo",
                    color = colorDe(if (h.estado == "Realizado") COLOR_REALIZADO else hal?.color),
                    detalle = if (h.estado == "Realizado") "Realizado" else "Pendiente",
                    soloLectura = soloLectura,
                    onAlternar = {
                        escribir {
                            val nuevo = if (h.estado == "Realizado") "Pendiente" else "Realizado"
                            if (OdontogramaRepo.cambiarEstado(h.id, nuevo)) recargar()
                            else Toaster.error("No se pudo cambiar")
                        }
                    },
                    onQuitar = {
                        escribir {
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
            onCambio = {
                escribir {
                    // Forzado: acaba de cambiar (vincular servicio) y la caché lo tendría viejo.
                    coroutineScope {
                        val cat = async { OdontogramaRepo.catalogo(forzar = true) }
                        val hal = async { OdontogramaRepo.hallazgosONull(pacienteId) }
                        catalogo = cat.await()
                        hal.await()?.let { hallazgos = it }
                    }
                    onDatosActual?.invoke(hallazgos, catalogo)
                    onCambioActual()
                }
            },
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
            onMarcar = { hallazgoId, caras, existente ->
                escribir {
                    // Ya pendiente en esta pieza: se le SUMAN las caras (una sola caries
                    // MO, se cobra una vez). Si no, un registro nuevo.
                    val ok = if (existente != null) OdontogramaRepo.actualizarSuperficies(existente.id, caras.orEmpty())
                        else OdontogramaRepo.agregar(pacienteId, diente, hallazgoId, caras?.let { ordenarCaras(it) }, citaId)
                    if (ok) recargar() else Toaster.error("No se pudo marcar")
                }
            },
            onNota = { h, texto ->
                escribir {
                    if (OdontogramaRepo.actualizarNotas(h.id, texto)) recargar() else Toaster.error("No se pudo guardar la nota")
                }
            },
            onAlternar = { h ->
                escribir {
                    val nuevo = if (h.estado == "Realizado") "Pendiente" else "Realizado"
                    if (OdontogramaRepo.cambiarEstado(h.id, nuevo)) recargar() else Toaster.error("No se pudo cambiar")
                }
            },
            onQuitar = { h ->
                escribir {
                    if (OdontogramaRepo.borrar(h.id)) recargar() else Toaster.error("No se pudo quitar")
                }
            },
        )
    }

    // ── Marcar un hallazgo para la boca entera ────────────────────────────
    if (panelBoca) {
        PanelBoca(
            catalogo = catalogo,
            yaMarcados = remember(hallazgos) {
                hallazgos.filter { it.diente in PSEUDO_DIENTES && it.estado == "Pendiente" }.map { it.hallazgoId }.toSet()
            },
            onCerrar = { panelBoca = false },
            onElegir = { hallazgoId ->
                panelBoca = false
                escribir {
                    // "BOCA", igual que la web: el presupuesto lo cobra una vez
                    // y el diagnóstico lo redacta como general.
                    val ok = OdontogramaRepo.agregar(pacienteId, "BOCA", hallazgoId, null, citaId)
                    if (ok) { recargar(); Toaster.exito("Registrado para toda la boca") }
                    else Toaster.error("No se pudo registrar")
                }
            },
        )
    }

    // ── Elegir el hallazgo del pincel ─────────────────────────────────────
    if (eligiendoPincel) {
        pe.saniape.app.ui.clinica.pacientes.DialogoForm(
            titulo = "🖌 Modo pincel",
            subtitulo = "Elige el hallazgo y toca las caras en el diagrama",
            textoAccion = "Cerrar",
            onCancelar = { eligiendoPincel = false },
            onAccion = { eligiendoPincel = false },
        ) {
            ChipsCatalogo(
                remember(catalogo) { catalogo.filter { it.estado == "Activo" && !it.porBoca } },
                onElegir = { h -> pincel = h; eligiendoPincel = false },
            )
        }
    }

    // ── Mostrar al paciente ───────────────────────────────────────────────
    if (mostrarPaciente) {
        MostrarAlPaciente(
            cuadrantes = cuadrantes, porDiente = porDiente, hallazgos = hallazgos, catalogo = catalogo,
            onCerrar = { mostrarPaciente = false },
        )
    }

    // ── Dictado por voz ───────────────────────────────────────────────────
    if (dictadoAbierto) {
        DictadoOdontograma(
            catalogo = catalogo,
            onCerrar = { dictadoAbierto = false },
            onAplicar = { lote ->
                escribir {
                    // UN insert para todo el dictado (antes, uno por pieza en serie).
                    val filas = lote.mapNotNull { h ->
                        OdontogramaRepo.NuevoHallazgo(h.diente ?: "BOCA", h.hallazgoId ?: return@mapNotNull null, h.superficies)
                    }
                    if (OdontogramaRepo.agregarVarios(pacienteId, filas, citaId)) {
                        recargar()
                        Toaster.exito("${filas.size} hallazgo(s) marcados")
                    } else Toaster.error("No se pudo marcar lo dictado. Prueba de nuevo.")
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
internal fun Boca(
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
                        // La página a la que se está llegando (no espera a que termine el gesto).
                        val activo = pager.targetPage == i
                        val fondo by animateColorAsState(if (activo) c.navy else Color.Transparent, label = "pestanaLado")
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(fondo)
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
    piezas.forEach { d ->
        // Cada pieza recuerda su pintado: se recalcula SOLO si cambió lo de esa
        // pieza (igualdad por contenido de sus hallazgos). Con el mismo objeto,
        // Compose salta la pieza entera: marcar la 16 no redibuja las otras 31.
        val deLaPieza = porDiente[d].orEmpty()
        val pintado = remember(deLaPieza, catalogo) { pintarDiente(deLaPieza, catalogo) }
        Pieza(d, tam, pintado, onTocar)
    }
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
        // Siglas de la norma (R restaurada, TC conductos, IMP implante).
        if (pintado.siglas.isNotEmpty() && !pintado.ausente) {
            Text(pintado.siglas.joinToString(" "), color = colorDe(COLOR_REALIZADO), fontSize = 8.sp,
                fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

/**
 * El diagrama de 5 caras de una pieza, dibujado con Canvas.
 *
 * El tamaño lo decide [Boca] según el ancho disponible (34–52dp).
 * La orientación (qué cara va en qué lado) sale de `caraEnZona`, probado aparte.
 */
@Composable
internal fun DienteDiagrama(
    diente: String,
    tam: Dp,
    pintado: PintadoDiente,
    onTocarCara: (String) -> Unit,
    /** Caras elegidas (pieza grande del panel): se resaltan con un borde navy. */
    seleccion: Set<String> = emptySet(),
) {
    val c = Sania.colors
    val borde = c.textoSuave.copy(alpha = 0.55f)
    val vacio = c.fondo
    val resalte = c.navy
    // Arcada superior: la raíz va hacia arriba; inferior, hacia abajo.
    val superior = diente.firstOrNull().let { it == '1' || it == '2' || it == '5' || it == '6' }
    // El gesto se instala UNA vez por pieza (clave = diente): sin esto usaba
    // para siempre la primera lambda recibida, con el estado de ese momento
    // (en el pincel, "¿ya existe en esta pieza?" se respondía con datos viejos).
    val tocarCara by rememberUpdatedState(onTocarCara)
    Spacer(
        Modifier.size(tam).pointerInput(diente) {
            detectTapGestures { p ->
                val lado = size.width.toFloat()
                val zona = zonaEn(p.x, p.y, lado, lado * MARGEN) ?: return@detectTapGestures
                tocarCara(caraEnZona(diente, zona))
            }
        }.drawWithCache {
            // Las 5 caras (trapecios + centro) se arman aquí y se reusan en cada
            // dibujo; solo se rehacen si cambia el tamaño o lo de ESTA pieza.
            val s = size.width
            val m = s * MARGEN
            fun trazo(vararg xy: Float) = Path().apply {
                moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1]); close()
            }
            val caras = listOf(
                Zona.ARRIBA to trazo(0f, 0f, s, 0f, s - m, m, m, m),
                Zona.ABAJO to trazo(0f, s, s, s, s - m, s - m, m, s - m),
                Zona.IZQUIERDA to trazo(0f, 0f, 0f, s, m, s - m, m, m),
                Zona.DERECHA to trazo(s, 0f, s, s, s - m, s - m, s - m, m),
                Zona.CENTRO to trazo(m, m, s - m, m, s - m, s - m, m, s - m),
            ).map { (zona, path) -> caraEnZona(diente, zona) to path }
            val lineaFina = Stroke(width = 1.2f)
            val lineaSeleccion = Stroke(width = s * 0.05f)
            onDrawBehind {
        for ((cara, path) in caras) {
            drawPath(path, pintado.porSuperficie[cara]?.let { colorDe(it) } ?: vacio)
            drawPath(path, borde, style = lineaFina)
            if (cara in seleccion) drawPath(path, resalte, style = lineaSeleccion)
        }

        val grosor = maxOf(2.5f, s * 0.06f)
        // Tratamiento de conductos (norma): línea en la raíz — la zona de la
        // raíz es la de arriba en la arcada superior y la de abajo en la inferior.
        pintado.endodoncia?.let { hex ->
            val y0 = if (superior) 0f else s
            val y1 = if (superior) m else s - m
            drawLine(colorDe(hex), Offset(s / 2, y0), Offset(s / 2, y1), strokeWidth = grosor * 1.2f)
        }
        // Corona: circunferencia alrededor de la pieza.
        if (pintado.corona != null && !pintado.ausente) {
            drawCircle(colorDe(pintado.corona), radius = s / 2 - grosor / 2, style = Stroke(width = grosor))
        }
        // Fractura: línea diagonal.
        if (pintado.fractura != null && !pintado.ausente) {
            drawLine(colorDe(pintado.fractura), Offset(3f, s - 3f), Offset(s - 3f, 3f), strokeWidth = grosor)
        }
        // Aspa: AZUL si la pieza ya no está (norma NTS 150: es un estado), ROJA
        // si la extracción está indicada (algo por hacer).
        if (pintado.ausente || pintado.extraccion != null) {
            val x = if (pintado.ausente) colorDe(COLOR_REALIZADO) else colorDe(pintado.extraccion)
            drawLine(x, Offset(2f, 2f), Offset(s - 2f, s - 2f), strokeWidth = grosor)
            drawLine(x, Offset(s - 2f, 2f), Offset(2f, s - 2f), strokeWidth = grosor)
        }
            }
        },
    )
}

/**
 * Mientras llega el odontograma: la silueta de la boca (dos arcadas de piezas
 * grises) en vez de un spinner, para que al cargar no "salte" la pantalla.
 * Sin animación a propósito: es breve, no gasta batería y respeta a quien
 * tiene las animaciones del sistema reducidas.
 */
@Composable
private fun EsqueletoOdontograma() {
    val c = Sania.colors
    val gris = c.borde.copy(alpha = 0.6f)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(bottom = 10.dp).width(130.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(gris))
        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { Box(Modifier.width(78.dp).height(28.dp).clip(RoundedCornerShape(50)).background(gris)) }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                .padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            val tam = minOf((maxWidth - LINEA_MEDIA) / 8 - PAD_PIEZA * 2, PIEZA_MAX)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(2) { fila ->
                    if (fila == 1) Spacer(Modifier.height(13.dp))
                    Row {
                        repeat(8) {
                            Box(Modifier.padding(horizontal = PAD_PIEZA).size(tam).clip(RoundedCornerShape(4.dp)).background(gris))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
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
        Text("✕ Ausente", color = colorDe(COLOR_REALIZADO), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    /** Nota de esta pieza; con [onNota] se puede escribir/editar ("caries profunda…"). */
    nota: String? = null,
    onNota: ((String?) -> Unit)? = null,
    /** Texto del botón para volver a pendiente vs. marcar hecho (lo decide quien llama). */
    realizado: Boolean = detalle.startsWith("Realizado"),
) {
    val c = Sania.colors
    var editandoNota by remember { mutableStateOf(false) }
    var textoNota by remember(nota) { mutableStateOf(nota.orEmpty()) }
    Column(Modifier.fillMaxWidth()) {
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
            if (onNota != null && !editandoNota) {
                Text(
                    if (nota.isNullOrBlank()) "+ nota" else "📝 $nota",
                    color = if (nota.isNullOrBlank()) c.navy else c.textoSuave,
                    fontSize = 11.sp, fontWeight = if (nota.isNullOrBlank()) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp)
                        .then(if (soloLectura) Modifier else Modifier.clickable { editandoNota = true }),
                )
            } else if (!nota.isNullOrBlank()) {
                Text("📝 $nota", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (!soloLectura) {
            // Revertir importa: marcar "hecho" por error no debe obligar a
            // borrar y perder la fecha.
            Text(
                if (realizado) "↩ Pendiente" else "✓ Hecho",
                color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAlternar() }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                "✕", color = c.error, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onQuitar() }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
    // Nota de ESTA pieza: la ve quien atienda la próxima sesión.
    if (editandoNota && onNota != null) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = textoNota, onValueChange = { textoNota = it },
                placeholder = { Text("Ej: caries profunda, riesgo pulpar", color = c.textoSuave, fontSize = 12.sp) },
                singleLine = true, modifier = Modifier.weight(1f),
                colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
            )
            Text("Guardar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    editandoNota = false
                    if (textoNota.trim() != nota.orEmpty()) onNota(textoNota.trim().ifBlank { null })
                }.padding(horizontal = 8.dp, vertical = 8.dp))
        }
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
    /** Ya registrados (pendientes) en la pieza: se muestran con ✎ y borde de su color. */
    marcados: Set<String> = emptySet(),
) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        catalogo.forEach { h ->
            val off = h.id in deshabilitados
            val marcado = h.id in marcados
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(c.superficie)
                    .border(if (marcado) 1.5.dp else 1.dp, if (marcado) colorDe(h.color) else c.borde, RoundedCornerShape(50))
                    .clickable(enabled = !off) { onElegir(h) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorDe(h.color)))
                Spacer(Modifier.width(6.dp))
                Text((if (marcado) "✎ " else "") + h.nombre, color = if (off) c.textoSuave else c.texto, fontSize = 12.sp)
            }
        }
    }
}
