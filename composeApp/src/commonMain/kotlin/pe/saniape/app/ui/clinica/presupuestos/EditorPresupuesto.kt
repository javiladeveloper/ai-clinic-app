package pe.saniape.app.ui.clinica.presupuestos

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CHIPS_DESCUENTO_PCT
import pe.saniape.app.data.staff.CHIPS_VIGENCIA
import pe.saniape.app.data.staff.CobroPresu
import pe.saniape.app.data.staff.DatosPresupuesto
import pe.saniape.app.data.staff.ItemPresupuesto
import pe.saniape.app.data.staff.LimitesPresupuesto
import pe.saniape.app.data.staff.ModoItemPresu
import pe.saniape.app.data.staff.NOTAS_SUGERIDAS
import pe.saniape.app.data.staff.PaqueteServicio
import pe.saniape.app.data.staff.PlantillaPresupuesto
import pe.saniape.app.data.staff.Presupuesto
import pe.saniape.app.data.staff.ServicioPresupuesto
import pe.saniape.app.data.staff.TITULOS_SUGERIDOS
import pe.saniape.app.data.staff.VIGENCIA_DIAS_DEFECTO
import pe.saniape.app.data.staff.comoPaquete
import pe.saniape.app.data.staff.comoSueltas
import pe.saniape.app.data.staff.diasEntre
import pe.saniape.app.data.staff.errorDescuento
import pe.saniape.app.data.staff.errorItems
import pe.saniape.app.data.staff.esPaquete
import pe.saniape.app.data.staff.etiquetaDescuento
import pe.saniape.app.data.staff.fechaLarga
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.itemDeServicio
import pe.saniape.app.data.staff.itemLibre
import pe.saniape.app.data.staff.leerPrecio
import pe.saniape.app.data.staff.listaPaquete
import pe.saniape.app.data.staff.modoDeServicio
import pe.saniape.app.data.staff.paquetesDelServicio
import pe.saniape.app.data.staff.simboloMoneda
import pe.saniape.app.data.staff.subtotalItem
import pe.saniape.app.data.staff.sumarDias
import pe.saniape.app.data.staff.textoAhorroPaquete
import pe.saniape.app.data.staff.textoCantidad
import pe.saniape.app.data.staff.textoPrecioCampo
import pe.saniape.app.data.staff.totalesPresupuesto
import pe.saniape.app.data.staff.validarVigencia
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/** Lo que edita el formulario (el precio como TEXTO: así se escribe "150.5"). */
private data class LineaForm(val item: ItemPresupuesto, val precio: String)

private fun conPrecio(item: ItemPresupuesto) = LineaForm(item, textoPrecioCampo(item.precioUnitario))

/**
 * Editor del 🧾 presupuesto (gemelo de EditorPresupuesto.tsx):
 *  · Servicios del catálogo con buscador, o un ítem LIBRE (solo quien fija precios).
 *  · Cada ítem: cantidad (− / +) × precio negociable ("↺ Precio de lista" /
 *    "↺ Precio del paquete"). Por sesiones: "Sesiones sueltas" o "📦 Paquete" (el
 *    precio EXACTO del catálogo, a elegir si hay varios, con el ahorro frente a sueltas).
 *  · Descuento sobre el TOTAL con "%" (chips 5/10/15/20) o "S/ monto", validado
 *    contra el total; total y ahorro en vivo.
 *  · Vigencia 7/15/30 días (o una fecha), notas para el paciente con chips, nota interna.
 *  · Paquetes guardados: aplicar uno al empezar y "💾 Guardar como paquete".
 * Quien no puede fijar precios ve precios y descuento bloqueados (el servidor también).
 * Guardar en la cabecera ("📤 Guardar y enviar") y "Guardar borrador" al pie del cuerpo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorPresupuesto(
    inicial: Presupuesto?,
    moneda: String,
    /** La sede cobra en otra moneda que el catálogo: sin lista, cada precio se escribe. */
    otraMoneda: Boolean,
    hoy: String,
    servicios: List<ServicioPresupuesto>,
    paquetes: List<PlantillaPresupuesto>,
    puedePrecio: Boolean,
    onCerrar: () -> Unit,
    /** Guarda (crear o editar); `enviar` = lo siguiente es enviarlo. Devuelve un error o null. */
    onGuardar: suspend (DatosPresupuesto, Boolean) -> String?,
    onGuardarPaquete: (suspend (String, DatosPresupuesto) -> String?)?,
    /** Campañas vigentes de la clínica (⚡ en cada ítem del servicio). */
    campanias: List<pe.saniape.app.data.staff.CampaniaApp> = emptyList(),
) {
    val c = Sania.colors
    val nombreEntrada = pe.saniape.app.data.staff.nombreCitaEntrada(pe.saniape.app.data.staff.StaffContextoRepo.actual?.flujo)
    val scope = rememberCoroutineScope()
    var titulo by remember { mutableStateOf(inicial?.titulo ?: "") }
    var lineas by remember { mutableStateOf(inicial?.items.orEmpty().map { conPrecio(it) }) }
    var modoDesc by remember { mutableStateOf(inicial?.descuentoTipo ?: "porcentaje") }
    var valorDesc by remember { mutableStateOf(inicial?.descuentoValor?.takeIf { it > 0 }?.let { textoPrecioCampo(it) } ?: "") }
    var vigencia by remember {
        mutableStateOf(inicial?.vigenciaHasta?.takeIf { it.isNotBlank() && it >= hoy } ?: sumarDias(hoy, VIGENCIA_DIAS_DEFECTO))
    }
    var notasP by remember { mutableStateOf(inicial?.notasPaciente ?: "") }
    var notasI by remember { mutableStateOf(inicial?.notasInternas ?: "") }
    var verNotaInterna by remember { mutableStateOf(!inicial?.notasInternas.isNullOrBlank()) }
    var busqueda by remember { mutableStateOf("") }
    var nombrePaquete by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    var eligiendoFecha by remember { mutableStateOf(false) }
    fun din(n: Double) = formatearDinero(n, moneda)

    // Las líneas con el precio leído (lo inválido cuenta 0 hasta corregirlo).
    val items = lineas.map { l -> l.item.copy(precioUnitario = leerPrecio(l.precio, moneda).valor ?: 0.0) }
    val descNum = valorDesc.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
    val tipoDesc = if (descNum > 0) modoDesc else null
    val t = totalesPresupuesto(items, tipoDesc, descNum, moneda)
    val errDescVivo = errorDescuento(modoDesc, valorDesc, t.subtotal, moneda)
    val activos = remember(servicios) { servicios.filter { (it.estado ?: "Activo") == "Activo" } }
    val q = busqueda.trim().lowercase()
    val resultados = if (q.isEmpty()) emptyList() else activos.filter { it.nombre.lowercase().contains(q) }.take(8)
    fun servicioDe(it: ItemPresupuesto) = it.procedimientoId?.let { id -> servicios.firstOrNull { s -> s.id == id } }

    fun agregar(it: ItemPresupuesto) {
        if (lineas.size >= LimitesPresupuesto.ITEMS) { Toaster.error("A lo más ${LimitesPresupuesto.ITEMS} servicios"); return }
        lineas = lineas + conPrecio(it)
        busqueda = ""
    }
    fun cambiarLinea(i: Int, f: (LineaForm) -> LineaForm) { lineas = lineas.mapIndexed { k, l -> if (k == i) f(l) else l } }
    fun cambiarCantidad(i: Int, n: Int) = cambiarLinea(i) { l0 ->
        val cantidad = n.coerceIn(1, LimitesPresupuesto.CANTIDAD_MAX)
        // Otra cantidad ya no es la promo: se quita la campaña (el precio se revisa).
        val l = if (cantidad == l0.item.cantidad) l0 else l0.copy(item = pe.saniape.app.data.staff.itemSinCampania(l0.item))
        if (!esPaquete(l.item)) return@cambiarLinea l.copy(item = l.item.copy(cantidad = cantidad))
        // Paquete: si el catálogo tiene uno de ESAS sesiones, su precio (y su lista); si no, personalizado.
        val lista = listaPaquete(servicioDe(l.item), cantidad, otraMoneda)
        if (lista != null) conPrecio(l.item.copy(cantidad = cantidad, precioUnitario = lista, precioLista = lista))
        else l.copy(item = l.item.copy(cantidad = cantidad, precioLista = null))
    }
    /** "Sesiones sueltas" ↔ "Paquete" (y cuál paquete): se parte del precio ESCRITO. */
    fun elegirCobro(i: Int, cobro: String, paquete: PaqueteServicio? = null) = cambiarLinea(i) { l ->
        val s2 = servicioDe(l.item)
        val leido = leerPrecio(l.precio, moneda)
        val actual = pe.saniape.app.data.staff.itemSinCampania(l.item)
            .copy(precioUnitario = if (leido.ok && leido.valor != null) leido.valor else l.item.precioUnitario)
        conPrecio(if (cobro == CobroPresu.PAQUETE) comoPaquete(actual, s2, otraMoneda, paquete) else comoSueltas(actual, s2, otraMoneda))
    }
    fun aplicarPaquete(pq: PlantillaPresupuesto) {
        // Los precios del paquete son sugerencias: cada servicio vuelve a su lista vigente.
        lineas = pq.items.map { it ->
            val s = it.procedimientoId?.let { id -> activos.firstOrNull { x -> x.id == id } }
            var base = if (s != null) itemDeServicio(s, it.cantidad, otraMoneda, if (otraMoneda) null else it.precioUnitario)
            else itemLibre(it.nombre, if (otraMoneda) 0.0 else it.precioUnitario, it.cantidad)
            if (s != null && base.modo == ModoItemPresu.SESIONES && it.cobro == CobroPresu.PAQUETE) {
                val del = paquetesDelServicio(s).firstOrNull { p -> p.sesiones == it.cantidad }
                base = comoPaquete(base, s, otraMoneda, del ?: PaqueteServicio(null, it.cantidad, it.precioUnitario))
                if (!otraMoneda) base = base.copy(precioUnitario = it.precioUnitario, precioLista = listaPaquete(s, it.cantidad))
            }
            conPrecio(base.copy(detalle = it.detalle))
        }
        if (titulo.isBlank()) titulo = pq.titulo
        if (puedePrecio) { modoDesc = pq.descuentoTipo ?: "porcentaje"; valorDesc = pq.descuentoValor.takeIf { it > 0 }?.let { textoPrecioCampo(it) } ?: "" }
        if (!pq.notasPaciente.isNullOrBlank() && notasP.isBlank()) notasP = pq.notasPaciente
        vigencia = sumarDias(hoy, pq.vigenciaDias.takeIf { it > 0 } ?: VIGENCIA_DIAS_DEFECTO)
        Toaster.exito("Paquete «${pq.nombre}» aplicado: revisa los precios")
    }

    /** Lo que se guarda, o el primer problema. */
    fun datos(): Pair<DatosPresupuesto?, String?> {
        val tt = titulo.replace(Regex("\\s+"), " ").trim()
        if (tt.isEmpty()) return null to "Ponle un título al presupuesto"
        if (lineas.isEmpty()) return null to "Agrega al menos un servicio"
        for (l in lineas) {
            if (l.item.nombre.isBlank()) return null to "Escribe el nombre del ítem libre"
            val p = leerPrecio(l.precio, moneda)
            if (!p.ok) return null to "${l.item.nombre}: ${p.error}"
            if (p.valor == null) return null to "Pon el precio de ${l.item.nombre}"
        }
        val limpios = items.map { it.copy(nombre = it.nombre.replace(Regex("\\s+"), " ").trim(), detalle = it.detalle?.trim()?.takeIf { d -> d.isNotEmpty() }) }
        errorItems(limpios, moneda)?.let { return null to it }
        errorDescuento(modoDesc, valorDesc, t.subtotal, moneda)?.let { return null to it }
        validarVigencia(vigencia, hoy)?.let { return null to it }
        return DatosPresupuesto(
            titulo = tt.take(LimitesPresupuesto.TITULO), items = limpios,
            descuentoTipo = if (descNum > 0) tipoDesc else null, descuentoValor = if (descNum > 0 && tipoDesc != null) descNum else 0.0,
            notasPaciente = notasP.trim().takeIf { it.isNotEmpty() }, notasInternas = notasI.trim().takeIf { it.isNotEmpty() },
            vigenciaHasta = vigencia,
        ) to null
    }

    fun guardar(enviar: Boolean) {
        if (guardando) return
        val (d, err) = datos()
        if (d == null) { Toaster.error(err ?: "Revisa el presupuesto"); return }
        guardando = true
        scope.launch {
            val e = runCatching { onGuardar(d, enviar) }.getOrElse { "No se pudo guardar" }
            guardando = false
            if (e != null) Toaster.error(e)
        }
    }
    fun guardarPaquete() {
        val f = onGuardarPaquete ?: return
        val nombre = nombrePaquete ?: return
        if (guardando) return
        val (d, err) = datos()
        if (d == null) { Toaster.error(err ?: "Revisa el presupuesto"); return }
        if (nombre.isBlank()) { Toaster.error("Ponle un nombre al paquete"); return }
        guardando = true
        scope.launch {
            val e = runCatching { f(nombre.trim(), d) }.getOrElse { "No se pudo guardar el paquete" }
            guardando = false
            if (e != null) Toaster.error(e)
            else { Toaster.exito("Paquete guardado: lo encuentras al armar el próximo presupuesto"); nombrePaquete = null }
        }
    }

    val diasVig = diasEntre(hoy, vigencia)
    val esNuevo = inicial == null

    DialogoForm(
        titulo = if (esNuevo) "🧾 Nuevo presupuesto" else "🧾 Editar presupuesto",
        subtitulo = if (lineas.isEmpty()) "Varios servicios con precio de paquete" else "Total ${din(t.total)}",
        textoAccion = if (guardando) "Guardando…" else "📤 Guardar y enviar",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar(true) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (inicial?.estado == "Enviado") {
                Text(
                    "Ya se lo enviaste: si cambias servicios, precios, descuento o notas, vuelve a borrador y se lo reenvías " +
                        "(así nunca acepta una versión que no vio). Extender la vigencia no.",
                    color = c.pend, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg).padding(10.dp),
                )
            }
            // Paquetes guardados: solo al empezar.
            if (esNuevo && paquetes.isNotEmpty() && lineas.isEmpty()) {
                Column {
                    EtqForm("Empezar desde un paquete guardado")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        paquetes.forEach { pq -> ChipPresu("📦 ${pq.nombre}", false) { aplicarPaquete(pq) } }
                    }
                }
            }

            // ── Título ──
            Column {
                CampoPresu(titulo, { titulo = it }, etiqueta = "Título", placeholder = "Ej. Plan rejuvenecimiento facial", max = LimitesPresupuesto.TITULO)
                if (titulo.isBlank()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TITULOS_SUGERIDOS.forEach { x -> ChipPresu(x, false) { titulo = x } }
                    }
                }
            }

            // ── Servicios ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtqForm("Servicios del plan")
                if (lineas.isEmpty()) {
                    Text("Busca los servicios que le propones. El precio de lista es solo la sugerencia: puedes acordar otro.",
                        color = c.textoSuave, fontSize = 12.sp)
                }
                lineas.forEachIndexed { i, l ->
                    androidx.compose.runtime.key(l.item.clave) {
                        LineaEditor(
                            linea = l, item = items[i], moneda = moneda, puedePrecio = puedePrecio,
                            paquetes = if (l.item.modo == ModoItemPresu.SESIONES) paquetesDelServicio(servicioDe(l.item)) else emptyList(),
                            onNombre = { v -> cambiarLinea(i) { it.copy(item = it.item.copy(nombre = v.take(LimitesPresupuesto.NOMBRE_ITEM))) } },
                            onDetalle = { v -> cambiarLinea(i) { it.copy(item = it.item.copy(detalle = v.take(LimitesPresupuesto.DETALLE).ifEmpty { null })) } },
                            onQuitar = { lineas = lineas.filterIndexed { k, _ -> k != i } },
                            onCantidad = { n -> cambiarCantidad(i, n) },
                            onPrecio = { v -> cambiarLinea(i) { it.copy(precio = v) } },
                            onCobro = { cobro, pq -> elegirCobro(i, cobro, pq) },
                            promos = pe.saniape.app.data.staff.campaniasParaItem(campanias, l.item),
                            puesta = l.item.campaniaId?.let { id -> campanias.firstOrNull { it.id == id } },
                            nombreEntrada = nombreEntrada,
                            onCampania = { camp ->
                                cambiarLinea(i) { x -> conPrecio(pe.saniape.app.data.staff.itemConCampania(x.item, camp, servicioDe(x.item), otraMoneda, moneda)) }
                            },
                            onQuitarCampania = { cambiarLinea(i) { x -> x.copy(item = pe.saniape.app.data.staff.itemSinCampania(x.item)) } },
                        )
                    }
                }
                // Buscador
                OutlinedTextField(
                    value = busqueda, onValueChange = { busqueda = it },
                    placeholder = { Text(if (lineas.isNotEmpty()) "＋ Agregar otro servicio…" else "🔎 Buscar servicio…", color = c.textoSuave, fontSize = 13.sp) },
                    singleLine = true, colors = coloresCampoForm(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth(),
                )
                if (resultados.isNotEmpty()) {
                    val forma = RoundedCornerShape(Sania.shape.md.dp)
                    Column(Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, c.borde, forma)) {
                        resultados.forEachIndexed { k, s ->
                            val it = itemDeServicio(s, otraMoneda = otraMoneda)
                            val sufijo = when (it.modo) {
                                ModoItemPresu.UNIDADES -> " / ${(it.unidadLabel ?: "unidad").removeSuffix("s")}"
                                ModoItemPresu.SESIONES -> " / sesión"
                                else -> ""
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) { agregar(itemDeServicio(s, otraMoneda = otraMoneda)) }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(s.nombre, color = c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(it.precioLista?.let { p -> "${din(p)}$sufijo" } ?: "sin precio", color = c.textoSuave, fontSize = 12.sp)
                            }
                            if (k < resultados.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                        }
                    }
                } else if (q.isNotEmpty()) {
                    Text("Ningún servicio coincide con «${busqueda.trim()}».", color = c.textoSuave, fontSize = 12.sp)
                }
                // Un ítem libre lleva un precio escrito: solo quien puede fijar precios.
                if (puedePrecio) {
                    Text(
                        "＋ Ítem libre${if (busqueda.isNotBlank()) " «${busqueda.trim()}»" else ""} (no está en el catálogo)",
                        color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { agregar(itemLibre(busqueda.trim(), 0.0)) }
                            .padding(vertical = 8.dp),
                    )
                }
            }

            // ── Descuento sobre el total + totales ──
            if (lineas.isNotEmpty()) {
                TarjetaPresu(fondo = c.chipBg) {
                    EtqForm("Descuento sobre el total")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Dos botones visibles (% o monto) con el campo al lado.
                        ChipPresu("%", modoDesc == "porcentaje", habilitado = puedePrecio) {
                            if (modoDesc != "porcentaje") { modoDesc = "porcentaje"; valorDesc = "" }
                        }
                        ChipPresu("${simboloMoneda(moneda)} monto", modoDesc == "monto", habilitado = puedePrecio) {
                            if (modoDesc != "monto") { modoDesc = "monto"; valorDesc = "" }
                        }
                        OutlinedTextField(
                            value = valorDesc, onValueChange = { valorDesc = it.replace(',', '.') },
                            placeholder = { Text(if (modoDesc == "monto") "0.00" else "0", color = c.textoSuave, fontSize = 13.sp) },
                            prefix = if (modoDesc == "monto") ({ Text("${simboloMoneda(moneda)} ", color = c.textoSuave, fontSize = 13.sp) }) else null,
                            suffix = if (modoDesc == "porcentaje") ({ Text("%", color = c.textoSuave, fontSize = 13.sp) }) else null,
                            singleLine = true, enabled = puedePrecio, isError = errDescVivo != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = coloresCampoForm(), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            modifier = Modifier.weight(1f).widthIn(min = 80.dp),
                        )
                    }
                    if (modoDesc == "porcentaje") {
                        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CHIPS_DESCUENTO_PCT.forEach { p -> ChipPresu("$p %", descNum == p.toDouble(), habilitado = puedePrecio) { valorDesc = p.toString() } }
                            if (descNum > 0 && puedePrecio) ChipPresu("Quitar", false) { valorDesc = "" }
                        }
                    } else if (descNum > 0 && puedePrecio) {
                        Text("Quitar", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp).clickable(role = Role.Button) { valorDesc = "" }.padding(vertical = 6.dp))
                    }
                    errDescVivo?.let { Text(it, color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                    if (!puedePrecio) {
                        Text("🔒 Los precios y el descuento los cambia quien cobra o el profesional que armó el presupuesto.",
                            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    if (t.descuento > 0) {
                        FilaTotal("Subtotal", din(t.subtotal))
                        FilaTotal(etiquetaDescuento(tipoDesc, descNum), "−${din(t.descuento)}", color = c.ok)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(din(t.total), color = c.navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    if (t.ahorro > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("por separado ", color = c.textoSuave, fontSize = 12.sp)
                            Text(din(t.porSeparado), color = c.textoSuave, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough)
                            Text(" · ahorra ${din(t.ahorro)}", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Vigencia ──
            Column {
                EtqForm("Válido hasta")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CHIPS_VIGENCIA.forEach { d -> ChipPresu("$d días", diasVig == d) { vigencia = sumarDias(hoy, d) } }
                    ChipPresu("📅 ${fechaLarga(vigencia)}", diasVig !in CHIPS_VIGENCIA) { eligiendoFecha = true }
                }
            }

            // ── Notas ──
            Column {
                CampoPresu(notasP, { notasP = it }, etiqueta = "Notas para el paciente", placeholder = "Qué incluye, formas de pago, cuidados…",
                    multilinea = true, max = LimitesPresupuesto.NOTAS)
                val sugeridas = NOTAS_SUGERIDAS.filter { !notasP.contains(it) }
                if (sugeridas.isNotEmpty()) {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sugeridas.forEach { n -> ChipPresu("＋ $n", false) { notasP = if (notasP.isNotBlank()) "${notasP.trim()}\n$n" else n } }
                    }
                }
            }
            if (verNotaInterna) {
                CampoPresu(notasI, { notasI = it }, etiqueta = "🔒 Nota interna (el paciente no la ve)",
                    placeholder = "Ej. le interesa más el relleno; llamar el viernes", multilinea = true, max = LimitesPresupuesto.NOTAS)
            } else {
                Text("🔒 ＋ Nota interna", color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { verNotaInterna = true }.padding(vertical = 8.dp))
            }

            // ── Guardar como paquete ──
            if (onGuardarPaquete != null && lineas.isNotEmpty()) {
                val np = nombrePaquete
                if (np == null) {
                    Text("💾 Guardar como paquete para reutilizarlo", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { nombrePaquete = titulo.trim() }.padding(vertical = 8.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CampoPresu(np, { nombrePaquete = it }, etiqueta = "Nombre del paquete", max = LimitesPresupuesto.NOMBRE_PLANTILLA)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotonPresu("💾 Guardar paquete", habilitado = !guardando) { guardarPaquete() }
                            BotonPresu("Cancelar") { nombrePaquete = null }
                        }
                    }
                }
            }

            if (lineas.isNotEmpty()) {
                Text("Resumen: " + lineas.joinToString(" · ") { "${it.item.nombre.ifBlank { "Ítem" }} ${textoCantidad(it.item)}".trim() },
                    color = c.textoSuave, fontSize = 11.sp)
            }
            // El otro guardado (la acción principal, "Guardar y enviar", va en la cabecera / pie).
            BotonPresu(if (guardando) "Guardando…" else "💾 Guardar borrador", habilitado = !guardando, modifier = Modifier.fillMaxWidth()) { guardar(false) }
        }
    }

    if (eligiendoFecha) {
        DialogoFecha(
            inicial = vigencia,
            onElegir = { f ->
                eligiendoFecha = false
                val e = validarVigencia(f, hoy)
                if (e != null) Toaster.error(e) else vigencia = f
            },
            onCerrar = { eligiendoFecha = false },
        )
    }
}

/** Una línea del editor: nombre/detalle, sueltas o paquete, cantidad, precio negociable y subtotal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineaEditor(
    linea: LineaForm,
    item: ItemPresupuesto,
    moneda: String,
    puedePrecio: Boolean,
    paquetes: List<PaqueteServicio>,
    onNombre: (String) -> Unit,
    onDetalle: (String) -> Unit,
    onQuitar: () -> Unit,
    onCantidad: (Int) -> Unit,
    onPrecio: (String) -> Unit,
    onCobro: (String, PaqueteServicio?) -> Unit,
    /** ⚡ Campañas vigentes del servicio del ítem. */
    promos: List<pe.saniape.app.data.staff.CampaniaApp> = emptyList(),
    /** La campaña puesta en el ítem (null: ninguna, o ya no está vigente). */
    puesta: pe.saniape.app.data.staff.CampaniaApp? = null,
    nombreEntrada: String = "evaluación",
    onCampania: (pe.saniape.app.data.staff.CampaniaApp) -> Unit = {},
    onQuitarCampania: () -> Unit = {},
) {
    val c = Sania.colors
    val l = linea.item
    val libre = l.procedimientoId == null
    val paquete = esPaquete(l)
    val ahorroPaq = textoAhorroPaquete(item, moneda)
    val etiquetaCant = when (l.modo) {
        ModoItemPresu.UNIDADES -> l.unidadLabel ?: "unidades"
        ModoItemPresu.SESIONES -> if (paquete) "sesiones del paquete" else "sesiones"
        else -> "veces"
    }
    TarjetaPresu {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (libre) CampoPresu(l.nombre, onNombre, placeholder = "Nombre del servicio (no está en el catálogo)")
                else Text(l.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                CampoPresu(l.detalle ?: "", onDetalle, placeholder = "＋ Detalle (zona, producto…)", modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            Box(
                Modifier.padding(start = 6.dp).size(40.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable(role = Role.Button, onClickLabel = "Quitar ${l.nombre}") { onQuitar() },
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = c.textoSuave, fontSize = 16.sp) }
        }
        // Por sesiones: sueltas o paquete (y cuál paquete del catálogo).
        if (l.modo == ModoItemPresu.SESIONES) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipPresu("Sesiones sueltas", !paquete) { onCobro(CobroPresu.SUELTAS, null) }
                ChipPresu("📦 Paquete", paquete) { onCobro(CobroPresu.PAQUETE, null) }
                if (paquete && paquetes.size > 1) {
                    paquetes.forEach { pq ->
                        ChipPresu("${pq.sesiones} ses · ${formatearDinero(pq.precio, moneda)}", l.cantidad == pq.sesiones && item.precioLista == pq.precio) {
                            onCobro(CobroPresu.PAQUETE, pq)
                        }
                    }
                }
            }
            if (paquete && paquetes.isEmpty()) {
                Text("Sin paquetes en el catálogo: paquete personalizado", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        // ⚡ Campañas vigentes del servicio: un toque aplica su precio.
        if (l.campaniaId != null || promos.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (l.campaniaId != null) {
                    ChipPresu("⚡ ${puesta?.nombre ?: "Campaña aplicada"}", true) {}
                    Text("Quitar", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(role = Role.Button) { onQuitarCampania() }
                            .padding(horizontal = 6.dp, vertical = 8.dp))
                } else promos.forEach { camp ->
                    ChipPresu("⚡ ${camp.nombre} · ${camp.etiqueta(moneda)}", false) { onCampania(camp) }
                }
            }
            if (puesta != null && pe.saniape.app.data.staff.campaniaIncluyeEntrada(puesta.alcance, puesta.incluyeEntrada)) {
                Text("🩺 Incluye ${pe.saniape.app.data.staff.conArticulo(nombreEntrada)}: lo pagado en ella se descuenta al crear el tratamiento desde esa cita.",
                    color = c.teal, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        // Cantidad
        Column(Modifier.padding(top = 10.dp)) {
            EtqForm("Cantidad ($etiquetaCant)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BotonPresu("−", modifier = Modifier.width(44.dp)) { onCantidad(l.cantidad - 1) }
                OutlinedTextField(
                    value = l.cantidad.toString(),
                    onValueChange = { v -> v.filter { it.isDigit() }.take(6).toIntOrNull()?.let(onCantidad) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = coloresCampoForm(), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.width(90.dp),
                )
                BotonPresu("+", modifier = Modifier.width(44.dp)) { onCantidad(l.cantidad + 1) }
            }
        }
        // Precio negociable + subtotal
        Column(Modifier.padding(top = 10.dp)) {
            CampoPrecioPresu(
                etiqueta = when {
                    l.modo == ModoItemPresu.UNIDADES -> "Precio por ${(l.unidadLabel ?: "unidad").removeSuffix("s")}"
                    paquete -> "Precio del paquete"
                    l.modo == ModoItemPresu.SESIONES -> "Precio por sesión"
                    else -> "Precio"
                },
                valor = linea.precio, onCambio = onPrecio, lista = l.precioLista, moneda = moneda, habilitado = puedePrecio,
                textoBoton = if (paquete) "↺ Precio del paquete" else "↺ Precio de lista",
                placeholder = if (l.precioLista == null) "Escribe el precio" else null,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SUBTOTAL", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formatearDinero(subtotalItem(item, moneda), moneda), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            ahorroPaq?.let { Text(it, color = c.ok, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}
