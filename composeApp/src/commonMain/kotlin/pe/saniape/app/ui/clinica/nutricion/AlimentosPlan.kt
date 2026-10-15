package pe.saniape.app.ui.clinica.nutricion

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.Adecuacion
import pe.saniape.app.data.staff.Alimento
import pe.saniape.app.data.staff.AlimentoFrecuente
import pe.saniape.app.data.staff.AlimentosRepo
import pe.saniape.app.data.staff.BorradorAlimentoClinica
import pe.saniape.app.data.staff.EnergiaDesdeAporte
import pe.saniape.app.data.staff.EntradaAlimento
import pe.saniape.app.data.staff.EstadoAdecuacion
import pe.saniape.app.data.staff.FilaAlimentoClinica
import pe.saniape.app.data.staff.GRUPOS_ALIMENTO
import pe.saniape.app.data.staff.ItemComida
import pe.saniape.app.data.staff.LecturaCatalogo
import pe.saniape.app.data.staff.LimitesAlimento
import pe.saniape.app.data.staff.MedidaCasera
import pe.saniape.app.data.staff.Nutriente
import pe.saniape.app.data.staff.OrigenAlimento
import pe.saniape.app.data.staff.RangoAdecuacion
import pe.saniape.app.data.staff.aporteDeComidas
import pe.saniape.app.data.staff.aporteDeItem
import pe.saniape.app.data.staff.alimentoDeClinica
import pe.saniape.app.data.staff.buscarAlimentos
import pe.saniape.app.data.staff.cambiarUnidad
import pe.saniape.app.data.staff.cantidadValida
import pe.saniape.app.data.staff.compararConPlan
import pe.saniape.app.data.staff.conCantidad
import pe.saniape.app.data.staff.energiaDesdeAporte
import pe.saniape.app.data.staff.filaGuardada
import pe.saniape.app.data.staff.fraccion
import pe.saniape.app.data.staff.indexarAlimentos
import pe.saniape.app.data.staff.itemConHabitual
import pe.saniape.app.data.staff.lineaItem
import pe.saniape.app.data.staff.medidasDe
import pe.saniape.app.data.staff.normalizarBusqueda
import pe.saniape.app.data.staff.numJs
import pe.saniape.app.data.staff.parsearCantidad
import pe.saniape.app.data.staff.redondearJs
import pe.saniape.app.data.staff.refAlimento
import pe.saniape.app.data.staff.sumarAporte
import pe.saniape.app.data.staff.textoAporte
import pe.saniape.app.data.staff.textoCantidad
import pe.saniape.app.data.staff.textoDeCantidad
import pe.saniape.app.data.staff.textoGramos
import pe.saniape.app.data.staff.textoValor
import pe.saniape.app.data.staff.validarAlimentoClinica
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania
import kotlin.math.abs
import kotlin.math.min

/**
 * ALIMENTOS del plan alimentario (editor y lectura). Gemelo de
 * `AlimentosComida.tsx`, `ResumenAporte.tsx`, `AlimentosClinicaModal.tsx` y
 * `useAlimentos.ts` de la web:
 *
 *  · En cada comida, "＋ Agregar alimento": buscador en memoria sobre el
 *    catálogo (bajado UNA vez por sesión) + los propios de la clínica. Vacío
 *    muestra lo que más usa la clínica, con su cantidad habitual.
 *  · Cada ítem: cantidad + unidad (gramos o sus medidas caseras), kcal y ✕.
 *  · Subtotal por comida y 📊 aporte del día contra el requerimiento.
 *  · 🥫 Alimentos de la clínica: crear (desde el buscador), editar y borrar.
 *
 * Sin listas perezosas (todo vive dentro del scroll del formulario) y sin
 * IntrinsicSize: las barras de color son Box con fracción del ancho.
 */

private const val MAX_OPCIONES = 8

enum class EstadoCatalogo { CARGANDO, LISTO, NO_DISPONIBLE, ERROR }

/** Lo que necesita el buscador (gemelo de `DatosAlimentos`). */
@Stable
class DatosAlimentos(
    val estado: EstadoCatalogo,
    val error: String?,
    val indice: List<EntradaAlimento>,
    /** refAlimento → alimento (para las medidas caseras de un ítem ya puesto). */
    val porRef: Map<String, Alimento>,
    /** Lo que más usa la clínica, con su cantidad habitual. */
    val frecuentes: List<AlimentoFrecuente>,
    val habitual: Map<String, AlimentoFrecuente>,
    val usos: Map<String, Int>,
    val propios: List<FilaAlimentoClinica>,
    /** Cambia la lista de propios (tras crear / editar / borrar) sin volver a pedir el catálogo. */
    val mutarPropios: ((List<FilaAlimentoClinica>) -> List<FilaAlimentoClinica>) -> Unit,
) {
    /** Backend viejo sin la ruta: sin buscador ni propios (el texto libre sigue). */
    val disponible: Boolean get() = estado != EstadoCatalogo.NO_DISPONIBLE
}

/**
 * Carga el catálogo (caché de la sesión) y los propios de la clínica al abrir el
 * editor. La pestaña en lectura NO lo llama: sus totales salen de la foto `por100`.
 */
@Composable
fun recordarDatosAlimentos(frecuentes: List<AlimentoFrecuente>): DatosAlimentos {
    var estado by remember { mutableStateOf(EstadoCatalogo.CARGANDO) }
    var error by remember { mutableStateOf<String?>(null) }
    var catalogo by remember { mutableStateOf<List<Alimento>>(emptyList()) }
    var propios by remember { mutableStateOf<List<FilaAlimentoClinica>>(emptyList()) }
    LaunchedEffect(Unit) {
        when (val r = AlimentosRepo.catalogo()) {
            is LecturaCatalogo.Listo -> { catalogo = r.catalogo.alimentos; estado = EstadoCatalogo.LISTO }
            LecturaCatalogo.NoDisponible -> estado = EstadoCatalogo.NO_DISPONIBLE
            is LecturaCatalogo.Fallo -> { error = r.error; estado = EstadoCatalogo.ERROR }
        }
        if (estado != EstadoCatalogo.NO_DISPONIBLE) AlimentosRepo.propios()?.let { propios = it }
    }
    val lista = remember(catalogo, propios) { propios.map(::alimentoDeClinica) + catalogo }
    val indice = remember(lista) { indexarAlimentos(lista) }
    val porRef = remember(lista) { lista.associateBy { refAlimento(it) } }
    val habitual = remember(frecuentes) { frecuentes.associateBy { refAlimento(it) } }
    val usos = remember(frecuentes) { frecuentes.associate { refAlimento(it) to it.usos } }
    return DatosAlimentos(estado, error, indice, porRef, frecuentes, habitual, usos, propios) { f -> propios = f(propios) }
}

// ─── Los alimentos de una comida (editor) ───────────────────────────────────

/**
 * La lista de ítems de una comida y el buscador para agregar uno más. Al
 * elegir, el ítem entra con la cantidad habitual (o la primera medida casera,
 * o 100 g) y el foco salta a su cantidad, ya seleccionada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlimentosComidaEditor(
    comida: String,
    items: List<ItemComida>,
    onChange: (List<ItemComida>) -> Unit,
    datos: DatosAlimentos,
    /** "Crear «texto» como alimento de la clínica" (null = sin permiso). */
    onCrear: ((String) -> Unit)?,
) {
    val c = Sania.colors
    var q by remember { mutableStateOf("") }
    var enfocado by remember { mutableStateOf(false) }
    var enfocar by remember { mutableStateOf<Int?>(null) }
    val lleno = items.size >= LimitesAlimento.ITEMS_POR_COMIDA

    val opciones = remember(q, datos.indice, datos.usos, datos.frecuentes, datos.porRef) {
        if (q.isNotBlank()) buscarAlimentos(datos.indice, q, MAX_OPCIONES, datos.usos)
        else datos.frecuentes.mapNotNull { datos.porRef[refAlimento(it)] }.take(MAX_OPCIONES)
    }
    val puedeCrear = onCrear != null && q.trim().length >= 2 && datos.estado == EstadoCatalogo.LISTO

    fun agregar(a: Alimento) {
        val it = itemConHabitual(a, datos.habitual[refAlimento(a)])
        enfocar = items.size
        onChange(items + it)
        q = ""
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (items.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            ) {
                items.forEachIndexed { i, it ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                    key(i, refAlimento(it)) {
                        FilaItemEditor(
                            it = it,
                            medidas = medidasDe(it, datos.porRef[refAlimento(it)]),
                            enfocar = enfocar == i,
                            onEnfocado = { enfocar = null },
                            onCambio = { nuevo -> onChange(items.mapIndexed { j, x -> if (j == i) nuevo else x }) },
                            onQuitar = { onChange(items.filterIndexed { j, _ -> j != i }) },
                        )
                    }
                }
            }
        }

        if (datos.disponible && !lleno) {
            OutlinedTextField(
                value = q, onValueChange = { q = it.take(60) },
                placeholder = { Text("＋ Agregar alimento (quinua, pollo, palta…)", color = c.textoSuave, fontSize = 13.sp) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth().onFocusChanged { enfocado = it.isFocused },
                colors = coloresCampoForm(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { opciones.firstOrNull()?.let(::agregar) }),
                trailingIcon = if (q.isNotEmpty()) ({
                    Text("✕", color = c.textoSuave, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { q = "" }.padding(8.dp))
                }) else null,
            )
            val abierto = q.isNotBlank() || enfocado
            if (abierto) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
                ) {
                    when {
                        datos.estado == EstadoCatalogo.CARGANDO && opciones.isEmpty() ->
                            AvisoBuscador("Cargando la base de alimentos…", c.textoSuave)
                        datos.estado == EstadoCatalogo.ERROR && opciones.isEmpty() ->
                            AvisoBuscador(datos.error ?: "No se pudo cargar la base de alimentos", c.error)
                        q.isBlank() && opciones.isNotEmpty() ->
                            Text("LOS QUE MÁS USA LA CLÍNICA", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp))
                        q.isNotBlank() && opciones.isEmpty() ->
                            AvisoBuscador("Nada con «${q.trim()}» en la tabla peruana ni en los alimentos de la clínica.", c.textoSuave)
                        q.isBlank() -> AvisoBuscador("Escribe para buscar en la tabla peruana de alimentos (INS/CENAN).", c.textoSuave)
                    }
                    opciones.forEachIndexed { i, a ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                        OpcionAlimento(a, datos.habitual[refAlimento(a)]) { agregar(a) }
                    }
                    if (puedeCrear) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                        Text("＋ Crear «${q.trim()}» como alimento de la clínica", color = c.navy, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clickable { val n = q.trim(); q = ""; onCrear!!(n) }
                                .heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 12.dp))
                    }
                }
            }
        } else if (lleno) {
            Text("Máximo ${LimitesAlimento.ITEMS_POR_COMIDA} alimentos en «$comida».", color = c.textoSuave, fontSize = 11.sp)
        }

        if (items.isNotEmpty()) {
            Text(textoAporte(sumarAporte(items)), color = c.textoSuave, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AvisoBuscador(texto: String, color: Color) {
    Text(texto, color = color, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
}

@Composable
private fun OpcionAlimento(a: Alimento, h: AlimentoFrecuente?, onClick: () -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(a.nombre, color = c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${redondearJs(a.por100.kcal).toInt()} kcal/100 g", color = c.textoSuave, fontSize = 10.sp,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        val sub = if (h != null) "Lo usual: ${textoCantidad(h.gramos, h.medida)}"
        else listOfNotNull(
            a.grupo,
            a.medidas.takeIf { it.isNotEmpty() }?.let { m -> "${m.size} medida${if (m.size > 1) "s" else ""} casera${if (m.size > 1) "s" else ""}" },
        ).joinToString(" · ")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            if (a.origen == OrigenAlimento.CLINICA) {
                PildoraAl("de la clínica")
                Spacer(Modifier.width(6.dp))
            }
            if (sub.isNotEmpty()) Text(sub, color = c.textoSuave, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PildoraAl(texto: String) {
    val c = Sania.colors
    Text(texto, color = c.teal, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.tealBg).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** Atajos de cantidad para una medida casera (en el teclado numérico no hay "/"). */
private val ATAJOS_CANTIDAD = listOf(0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0)

/** Un ítem en el editor: cantidad (se escribe "½", "1/2", "1,5" o "80") + unidad, kcal y ✕. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaItemEditor(
    it: ItemComida,
    medidas: List<MedidaCasera>,
    enfocar: Boolean,
    onEnfocado: () -> Unit,
    onCambio: (ItemComida) -> Unit,
    onQuitar: () -> Unit,
) {
    val c = Sania.colors
    // Lo que se escribe ("1/" a medio teclear) vive aquí; el ítem cambia cuando es un número válido.
    var txt by remember { mutableStateOf(TextFieldValue(textoDeCantidad(it))) }
    // Si el ítem cambia desde afuera (otra unidad, un atajo), el texto se pone al día.
    val clave = "${it.medida?.nombre ?: "g"}|${it.medida?.cantidad ?: it.gramos}"
    var claveVista by remember { mutableStateOf(clave) }
    if (claveVista != clave) {
        claveVista = clave
        val x = parsearCantidad(txt.text)
        val actual = it.medida?.cantidad ?: it.gramos
        if (x == null || x.isNaN() || abs(x - actual) > 0.051) txt = TextFieldValue(textoDeCantidad(it))
    }
    val invalido = cantidadValida(txt.text, it) == null
    var focoCantidad by remember { mutableStateOf(false) }
    var menuUnidad by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(enfocar) {
        if (enfocar) {
            txt = txt.copy(selection = TextRange(0, txt.text.length))
            runCatching { foco.requestFocus() }
            onEnfocado()
        }
    }
    val a = aporteDeItem(it)
    fun r1(x: Double?) = x?.let { v -> numJs(redondearJs(v * 10) / 10) } ?: "—"

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(it.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (it.origen == OrigenAlimento.CLINICA) Box(Modifier.padding(top = 2.dp)) { PildoraAl("de la clínica") }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text("${redondearJs(a[Nutriente.KCAL] ?: 0.0).toInt()} kcal", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("P ${r1(a[Nutriente.PROTEINAS])} · G ${r1(a[Nutriente.GRASAS])} · C ${r1(a[Nutriente.CARBOHIDRATOS])}",
                    color = c.textoSuave, fontSize = 10.sp)
            }
            Text("✕", color = c.textoSuave, fontSize = 15.sp,
                modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onQuitar)
                    .padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = txt,
                onValueChange = { v ->
                    txt = v
                    // Como la web: la cantidad cambia con la medida DEL ítem (su peso guardado).
                    cantidadValida(v.text, it)?.let { x -> onCambio(conCantidad(it, x, it.medida?.let { m -> MedidaCasera(m.nombre, m.gramos) })) }
                },
                singleLine = true,
                isError = invalido,
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier.width(84.dp).focusRequester(foco).onFocusChanged { f ->
                    focoCantidad = f.isFocused
                    // Al salir con algo que no es una cantidad, vuelve a la que vale.
                    if (!f.isFocused && cantidadValida(txt.text, it) == null) txt = TextFieldValue(textoDeCantidad(it))
                },
                colors = coloresCampoForm(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
            Box(Modifier.weight(1f)) {
                CajaSelectorForm("${it.medida?.nombre ?: "gramos"}  ▾") { menuUnidad = true }
                DropdownMenu(expanded = menuUnidad, onDismissRequest = { menuUnidad = false }) {
                    DropdownMenuItem(text = { Text("gramos", fontSize = 14.sp) }, onClick = {
                        menuUnidad = false
                        val nuevo = cambiarUnidad(it, null)
                        txt = TextFieldValue(textoDeCantidad(nuevo)); onCambio(nuevo)
                    })
                    medidas.forEach { m ->
                        DropdownMenuItem(text = { Text("${m.nombre} (${textoGramos(m.gramos)})", fontSize = 14.sp) }, onClick = {
                            menuUnidad = false
                            val nuevo = cambiarUnidad(it, m)
                            txt = TextFieldValue(textoDeCantidad(nuevo)); onCambio(nuevo)
                        })
                    }
                }
            }
        }
        if (it.medida != null) {
            Text("= ${textoGramos(it.gramos)}", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (invalido) {
            val r = if (it.medida != null) LimitesAlimento.CANTIDAD else LimitesAlimento.GRAMOS
            Text("Escribe una cantidad entre ${numJs(r.min)} y ${numJs(r.max)}${if (it.medida == null) " g" else ""} (½, 1/2, 1,5…).",
                color = c.error, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        // Con una medida casera: atajos ¼ ½ ¾ 1… mientras se edita la cantidad.
        val m = it.medida
        if (m != null && focoCantidad) {
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ATAJOS_CANTIDAD.forEach { x ->
                    val activo = abs(m.cantidad - x) < 1e-9
                    Text(fraccion(x), color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (activo) c.navy else c.chipBg)
                            .clickable {
                                val med = MedidaCasera(m.nombre, m.gramos)
                                val nuevo = conCantidad(it, x, med)
                                txt = TextFieldValue(textoDeCantidad(nuevo)); onCambio(nuevo)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

// ─── Lectura: los alimentos de una comida en la tarjeta del plan ────────────

/** Cada alimento con su cantidad legible y sus kcal (la tarjeta del staff). */
@Composable
fun ItemsLectura(items: List<ItemComida>) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { it ->
            Row(verticalAlignment = Alignment.Top) {
                Text("• ${lineaItem(it)}", color = c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (it.por100 != null) {
                    Text("${redondearJs(aporteDeItem(it)[Nutriente.KCAL] ?: 0.0).toInt()} kcal", color = c.textoSuave, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp, top = 1.dp))
                }
            }
        }
    }
}

// ─── 📊 Aporte del día contra el requerimiento ───────────────────────────────

/** La barra va de 0 a 130 % del requerimiento. */
private const val TOPE_BARRA = 130

/**
 * Lo que suman los alimentos del día contra el requerimiento del plan (kcal y
 * gramos de cada macro), con una barra verde (90–110 %), ámbar (bajo) o roja
 * (alto), y el reparto % contra el del plan. Nada si no hay alimentos.
 */
@Composable
fun ResumenAporteUi(
    itemsPorComida: List<List<ItemComida>>,
    kcal: Double?,
    proteinasPct: Double?,
    carbohidratosPct: Double?,
    grasasPct: Double?,
    /** Editor: copiar kcal y % al plan. */
    onUsar: ((EnergiaDesdeAporte) -> Unit)? = null,
    /** Lectura (tarjeta del plan): sin botón ni notas. */
    compacto: Boolean = false,
) {
    val c = Sania.colors
    val dia = remember(itemsPorComida) { aporteDeComidas(itemsPorComida) }
    if (!dia.conItems) return
    val total = dia.total
    val cmp = compararConPlan(total, kcal, proteinasPct, carbohidratosPct, grasasPct)
    val usar = if (onUsar != null) energiaDesdeAporte(total) else null
    val yaIgual = usar != null && kcal == usar.kcal.toDouble() && proteinasPct == usar.proteinasPct &&
        carbohidratosPct == usar.carbohidratosPct && grasasPct == usar.grasasPct
    data class Fila(val nombre: String, val valor: String, val adec: Adecuacion?, val pct: Double? = null, val metaPct: Double? = null)
    val filas = listOf(
        Fila("Energía", textoValor(total, Nutriente.KCAL), cmp.kcal),
        Fila("Proteínas", textoValor(total, Nutriente.PROTEINAS), cmp.proteinas, cmp.reparto?.proteinas, cmp.metaReparto?.proteinas),
        Fila("Grasas", textoValor(total, Nutriente.GRASAS), cmp.grasas, cmp.reparto?.grasas, cmp.metaReparto?.grasas),
        Fila("Carbohidratos disp.", textoValor(total, Nutriente.CARBOHIDRATOS), cmp.carbohidratos, cmp.reparto?.carbohidratos, cmp.metaReparto?.carbohidratos),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("📊 APORTE DE LOS ALIMENTOS DEL DÍA", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        filas.forEach { f ->
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(f.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                        Text(f.valor, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        f.adec?.let { ad ->
                            Text("de ${numJs(ad.meta)}${if (f.nombre == "Energía") "" else " g"} · ${ad.pct} % (${ad.estado.palabra})",
                                color = colorEstado(ad.estado), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        f.pct?.let { p ->
                            Text("${numJs(p)} % de la energía${f.metaPct?.let { m -> " (plan: ${numJs(m)} %)" }.orEmpty()}",
                                color = c.textoSuave, fontSize = 10.sp)
                        }
                    }
                }
                f.adec?.let { ad -> BarraAdecuacion(ad) }
            }
        }
        Text(
            listOf(Nutriente.FIBRA, Nutriente.SODIO, Nutriente.HIERRO, Nutriente.CALCIO)
                .joinToString(" · ") { k -> "${k.etiqueta} ${textoValor(total, k)}" },
            color = c.textoSuave, fontSize = 11.sp,
        )
        if (!compacto && cmp.kcal == null) {
            Text("Pon las kcal del plan (o usa la calculadora) para comparar con el requerimiento.", color = c.textoSuave, fontSize = 11.sp)
        }
        if (usar != null && !yaIgual) {
            Text("Usar estos totales en el plan", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onUsar!!(usar) }.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        if (!compacto) {
            Text(
                "Carbohidratos disponibles (sin fibra), con los que se calcula la energía. «≥» = algún alimento no tiene ese dato en la tabla " +
                    "(no se estima). Fuente: Tablas Peruanas de Composición de Alimentos 2023 (INS/CENAN) y los alimentos de la clínica.",
                color = c.textoSuave, fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun colorEstado(e: EstadoAdecuacion): Color = when (e) {
    EstadoAdecuacion.BAJO -> Sania.colors.pend
    EstadoAdecuacion.ADECUADO -> Sania.colors.ok
    EstadoAdecuacion.ALTO -> Sania.colors.error
}

/** Barra 0–130 % con la zona adecuada (90–110 %) marcada. Box con fracción del ancho: sin IntrinsicSize. */
@Composable
private fun BarraAdecuacion(ad: Adecuacion) {
    val c = Sania.colors
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.borde)) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Spacer(Modifier.weight(RangoAdecuacion.BAJO.toFloat()))
            Box(Modifier.weight((RangoAdecuacion.ALTO - RangoAdecuacion.BAJO).toFloat()).fillMaxHeight().background(c.okBg))
            Spacer(Modifier.weight((TOPE_BARRA - RangoAdecuacion.ALTO).toFloat()))
        }
        val frac = min(ad.pct, TOPE_BARRA).coerceAtLeast(0) / TOPE_BARRA.toFloat()
        if (frac > 0f) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(colorEstado(ad.estado)))
        }
    }
}

// ─── 🥫 Alimentos de la clínica ──────────────────────────────────────────────

/** Qué muestra el diálogo: la lista, o el formulario para crear (desde el buscador) o editar. */
sealed interface ModoAlimentosClinica {
    data object Lista : ModoAlimentosClinica
    /** Crear desde el buscador: al guardar, [alGuardar] lo agrega a la comida. */
    data class Crear(val nombre: String, val alGuardar: ((Alimento) -> Unit)? = null) : ModoAlimentosClinica
}

/**
 * Productos que no están en la tabla peruana (un yogur de marca con su
 * etiqueta, una receta propia). Valores por 100 g, como la tabla.
 */
@Composable
fun DialogoAlimentosClinica(
    inicial: ModoAlimentosClinica,
    datos: DatosAlimentos,
    puedeEditar: Boolean,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    // null = la lista; "nuevo" o la fila que se edita.
    var form by remember { mutableStateOf<Any?>(if (inicial is ModoAlimentosClinica.Crear) "nuevo" else null) }
    val crear = inicial as? ModoAlimentosClinica.Crear
    val f = form
    if (f != null) {
        FormAlimentoClinica(
            fila = f as? FilaAlimentoClinica,
            nombreInicial = if (f == "nuevo") crear?.nombre.orEmpty() else "",
            datos = datos,
            onCancelar = { if (crear != null) onCerrar() else form = null },
            onGuardado = { fila, nuevo ->
                datos.mutarPropios { prev -> if (nuevo) prev + fila else prev.map { x -> if (x.id == fila.id) fila else x } }
                if (crear?.alGuardar != null) { crear.alGuardar.invoke(alimentoDeClinica(fila)); onCerrar() } else form = null
            },
            onUsarExistente = crear?.alGuardar?.let { agregar -> { fila: FilaAlimentoClinica -> agregar(alimentoDeClinica(fila)); onCerrar() } },
            onBorrado = { id -> datos.mutarPropios { prev -> prev.filter { x -> x.id != id } }; form = null },
        )
        return
    }
    DialogoForm(
        titulo = "🥫 Alimentos de la clínica",
        subtitulo = "Lo que no está en la tabla peruana",
        textoAccion = if (puedeEditar) "＋ Nuevo" else "Cerrar",
        onCancelar = onCerrar,
        onAccion = { if (puedeEditar) form = "nuevo" else onCerrar() },
        textoCancelar = "Cerrar",
    ) {
        Text("Productos que no están en la tabla peruana. Se buscan junto con ella. Borrar uno no cambia los planes que ya lo usan.",
            color = c.textoSuave, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        if (datos.propios.isEmpty()) {
            Text("Aún no hay alimentos propios.", color = c.texto, fontSize = 13.sp)
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            ) {
                datos.propios.sortedBy { normalizarBusqueda(it.nombre) }.forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${redondearJs(p.kcal ?: 0.0).toInt()} kcal · P ${numJs(p.proteinas ?: 0.0)} · G ${numJs(p.grasas ?: 0.0)} · " +
                                    "C ${numJs(p.carbohidratos ?: 0.0)} g por 100 g${p.fuente?.let { " · $it" }.orEmpty()}",
                                color = c.textoSuave, fontSize = 11.sp,
                            )
                        }
                        if (puedeEditar) {
                            Text("✏️ Editar", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                    .clickable { form = p }.padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormAlimentoClinica(
    fila: FilaAlimentoClinica?,
    nombreInicial: String,
    datos: DatosAlimentos,
    onCancelar: () -> Unit,
    onGuardado: (FilaAlimentoClinica, Boolean) -> Unit,
    /** Crear desde el buscador y ya existe uno con ese nombre: agregarlo. */
    onUsarExistente: ((FilaAlimentoClinica) -> Unit)?,
    onBorrado: (String) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    fun s(v: Double?) = v?.let(::numJs).orEmpty()
    var nombre by remember { mutableStateOf(fila?.nombre ?: nombreInicial) }
    var grupo by remember { mutableStateOf(fila?.grupo.orEmpty()) }
    var kcal by remember { mutableStateOf(s(fila?.kcal)) }
    var prot by remember { mutableStateOf(s(fila?.proteinas)) }
    var grasas by remember { mutableStateOf(s(fila?.grasas)) }
    var carb by remember { mutableStateOf(s(fila?.carbohidratos)) }
    var fibra by remember { mutableStateOf(s(fila?.fibra)) }
    var sodio by remember { mutableStateOf(s(fila?.sodio)) }
    var hierro by remember { mutableStateOf(s(fila?.hierro)) }
    var calcio by remember { mutableStateOf(s(fila?.calcio)) }
    var fuente by remember { mutableStateOf(fila?.fuente.orEmpty()) }
    var medidas by remember {
        mutableStateOf(fila?.medidas?.takeIf { it.isNotEmpty() }?.map { it.nombre to numJs(it.gramos) } ?: listOf("" to ""))
    }
    var mas by remember { mutableStateOf(fila != null && (fila.sodio != null || fila.hierro != null || fila.calcio != null)) }
    var menuGrupo by remember { mutableStateOf(false) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }
    var existente by remember { mutableStateOf<FilaAlimentoClinica?>(null) }

    fun vacio(t: String) = t.trim().ifEmpty { null }
    fun borrador() = BorradorAlimentoClinica(
        nombre = nombre.trim(), grupo = vacio(grupo), kcal = vacio(kcal), proteinas = vacio(prot), grasas = vacio(grasas),
        carbohidratos = vacio(carb), fibra = vacio(fibra), sodio = vacio(sodio), hierro = vacio(hierro), calcio = vacio(calcio),
        medidas = medidas.filter { (n, g) -> n.isNotBlank() || g.isNotBlank() }.map { (n, g) -> n.trim() to g.trim().replace(',', '.') },
        fuente = vacio(fuente),
    )

    fun guardar() {
        if (guardando) return // anti doble toque: `enabled` recién aplica en la siguiente recomposición
        val b = borrador()
        val e = validarAlimentoClinica(b)
        errores = e
        existente = null
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = AlimentosRepo.guardarPropio(b, fila?.id)
            val f = filaGuardada(r)
            if (r.registrada && f != null) {
                guardando = false
                Toaster.exito(if (fila != null) "Alimento actualizado" else "Alimento guardado")
                onGuardado(f, fila == null)
                return@launch
            }
            val msg = r.rechazo?.error ?: "No se pudo guardar"
            if (r.rechazo?.codigo == "DUPLICADO") {
                // Otro equipo pudo haberlo creado: se vuelve a leer la lista y se ofrece el que ya existe.
                val lista = AlimentosRepo.propios()
                if (lista != null) datos.mutarPropios { lista }
                val clave = normalizarBusqueda(b.nombre)
                existente = (lista ?: datos.propios).firstOrNull { x -> x.id != fila?.id && normalizarBusqueda(x.nombre) == clave }
                errores = listOf(if (msg.isBlank()) "Ya hay un alimento de la clínica con ese nombre." else msg)
            } else errores = listOf(msg)
            guardando = false
            Toaster.error(errores.first())
        }
    }

    fun borrar() {
        val f = fila ?: return
        if (guardando) return
        guardando = true
        scope.launch {
            val r = AlimentosRepo.borrarPropio(f.id)
            guardando = false
            // Ya lo había borrado otro equipo: para el usuario es lo mismo.
            if (r.registrada || r.rechazo?.codigo == "NO_ENCONTRADO") { Toaster.exito("Alimento eliminado"); onBorrado(f.id) }
            else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
        }
    }

    DialogoForm(
        titulo = if (fila == null) "Nuevo alimento de la clínica" else "Editar alimento de la clínica",
        subtitulo = "Valores por 100 g (de la etiqueta: «por 100 g»)",
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCancelar() },
        onAccion = { guardar() },
    ) {
        EtqForm("Nombre")
        CampoAl(nombre, { nombre = it.take(LimitesAlimento.NOMBRE) }, "Ej. Yogur griego natural (marca)", numero = false)
        Spacer(Modifier.height(10.dp))
        EtqForm("Grupo (opcional)")
        Box {
            CajaSelectorForm("${grupo.ifEmpty { "—" }}  ▾") { menuGrupo = true }
            DropdownMenu(expanded = menuGrupo, onDismissRequest = { menuGrupo = false }) {
                DropdownMenuItem(text = { Text("—", fontSize = 14.sp) }, onClick = { menuGrupo = false; grupo = "" })
                GRUPOS_ALIMENTO.forEach { g ->
                    DropdownMenuItem(text = { Text(g, fontSize = 14.sp) }, onClick = { menuGrupo = false; grupo = g })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { EtqForm("Kcal"); CampoAl(kcal, { kcal = limpiarNum(it) }, "120") }
            Column(Modifier.weight(1f)) { EtqForm("Proteínas g"); CampoAl(prot, { prot = limpiarNum(it) }, "0") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { EtqForm("Grasas g"); CampoAl(grasas, { grasas = limpiarNum(it) }, "0") }
            Column(Modifier.weight(1f)) { EtqForm("Carbohid. g"); CampoAl(carb, { carb = limpiarNum(it) }, "0") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { EtqForm("Fibra g"); CampoAl(fibra, { fibra = limpiarNum(it) }, "opcional") }
            Spacer(Modifier.weight(1f))
        }
        Text("${if (mas) "▾" else "▸"} Sodio, hierro y calcio (opcional)", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { mas = !mas }.padding(vertical = 8.dp))
        if (mas) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { EtqForm("Sodio mg"); CampoAl(sodio, { sodio = limpiarNum(it) }, "") }
                Column(Modifier.weight(1f)) { EtqForm("Hierro mg"); CampoAl(hierro, { hierro = limpiarNum(it) }, "") }
                Column(Modifier.weight(1f)) { EtqForm("Calcio mg"); CampoAl(calcio, { calcio = limpiarNum(it) }, "") }
            }
        }
        Spacer(Modifier.height(10.dp))
        EtqForm("Medidas caseras (opcional)")
        medidas.forEachIndexed { i, (n, g) ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    CampoAl(n, { v -> medidas = medidas.mapIndexed { j, x -> if (j == i) v.take(LimitesAlimento.NOMBRE_MEDIDA) to x.second else x } },
                        "Vaso, unidad, porción", numero = false)
                }
                Box(Modifier.width(84.dp)) {
                    CampoAl(g, { v -> medidas = medidas.mapIndexed { j, x -> if (j == i) x.first to limpiarNum(v) else x } }, "g")
                }
                Text("✕", color = c.textoSuave, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .clickable { medidas = if (medidas.size > 1) medidas.filterIndexed { j, _ -> j != i } else listOf("" to "") }
                        .padding(10.dp))
            }
        }
        if (medidas.size < LimitesAlimento.MEDIDAS) {
            Text("＋ Otra medida", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { medidas = medidas + ("" to "") }.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(6.dp))
        EtqForm("De dónde salen los valores (opcional)")
        CampoAl(fuente, { fuente = it.take(LimitesAlimento.FUENTE) }, "Etiqueta del producto", numero = false)

        if (errores.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) { errores.forEach { Text(it, color = c.error, fontSize = 12.sp) } }
        }
        val ex = existente
        if (ex != null && onUsarExistente != null) {
            Text("＋ Agregar «${ex.nombre}», el que ya existe", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onUsarExistente(ex) }.padding(horizontal = 12.dp, vertical = 10.dp))
        }

        if (fila != null) {
            Spacer(Modifier.height(14.dp))
            if (confirmar) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Eliminar?", color = c.texto, fontSize = 13.sp)
                    Text(if (guardando) "Eliminando…" else "Eliminar", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.error)
                            .clickable(enabled = !guardando) { borrar() }.padding(horizontal = 12.dp, vertical = 8.dp))
                    Text("No", color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { confirmar = false }.padding(8.dp))
                }
            } else {
                Text("🗑️ Eliminar", color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { confirmar = true }.padding(vertical = 8.dp))
            }
        }
    }
}

/** Solo dígitos y un separador decimal (los valores van por 100 g). */
private fun limpiarNum(v: String): String = v.filter { it.isDigit() || it == '.' || it == ',' }.take(8)

@Composable
private fun CampoAl(valor: String, onChange: (String) -> Unit, placeholder: String, numero: Boolean = true) {
    val c = Sania.colors
    OutlinedTextField(
        value = valor, onValueChange = onChange,
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = c.textoSuave, fontSize = 13.sp) }) else null,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth(),
        colors = coloresCampoForm(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numero) KeyboardType.Decimal else KeyboardType.Text),
    )
}
