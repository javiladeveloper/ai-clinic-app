package pe.saniape.app.ui.clinica.pedidos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.offline.nuevaIdemKey
import pe.saniape.app.data.staff.CatalogoPedido
import pe.saniape.app.data.staff.LimitesPedido
import pe.saniape.app.data.staff.LineaPedidoForm
import pe.saniape.app.data.staff.MetodoPagoPreferido
import pe.saniape.app.data.staff.PedidoRecepcion
import pe.saniape.app.data.staff.PedidosRepo
import pe.saniape.app.data.staff.citaCobrableConPedido
import pe.saniape.app.data.staff.cuerpoGuardarPedido
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.huellaPedido
import pe.saniape.app.data.staff.leerPrecio
import pe.saniape.app.data.staff.resumenPedido
import pe.saniape.app.data.staff.subtotalItemPedido
import pe.saniape.app.data.staff.totalesPedido
import pe.saniape.app.data.staff.totalesPedidoDe
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodosPago
import pe.saniape.app.ui.theme.Sania

private val TILDES = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')
private fun plegar(s: String) = s.lowercase().map { TILDES[it] ?: it }.joinToString("")

/**
 * 🧾 PEDIDO PARA RECEPCIÓN (gemelo de PedidoRecepcionModal.tsx): el profesional
 * arma, durante o al cerrar la atención, productos / kits (con cantidad) y
 * servicios extra que se le hicieron en esta misma atención. A recepción le llega
 * pendiente de cobro. Sirve también para editarlo mientras siga Pendiente (y
 * anularlo). El precio del catálogo es la SUGERENCIA: cada línea deja escribir el
 * acordado y "↺" vuelve al de lista.
 */
@Composable
fun DialogoPedidoRecepcion(
    citaId: String?,
    pacienteId: String?,
    pacienteNombre: String?,
    pedido: PedidoRecepcion?,
    onCerrar: () -> Unit,
    onGuardado: () -> Unit,
    /** Nota para recepción al crear uno nuevo (psicología: «Indicado: terapia»). */
    notasIniciales: String? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var cat by remember { mutableStateOf<CatalogoPedido?>(null) }
    var errorCat by remember { mutableStateOf<String?>(null) }
    var lineas by remember {
        mutableStateOf(pedido?.items.orEmpty().map { i ->
            LineaPedidoForm(
                clave = i.clave, tipo = i.tipo, refId = i.refId, nombre = i.nombre, cantidad = i.cantidad,
                precio = if (i.precioLista != null && i.precioUnit == i.precioLista) "" else montoTexto(i.precioUnit),
                lista = i.precioLista, notas = i.notas.orEmpty(),
            )
        })
    }
    var notas by remember { mutableStateOf(pedido?.notas ?: notasIniciales.orEmpty()) }
    // El pedido que se edita: el que llegó o, si esta cita ya tiene uno Pendiente
    // (p. ej. el envío anterior llegó aunque la respuesta se perdió), ese.
    var actual by remember { mutableStateOf(pedido) }
    // Antes de crear uno nuevo para una cita, se mira si ya tiene uno: hasta saberlo, no se envía.
    var verificando by remember { mutableStateOf(pedido == null && citaId != null) }
    fun adoptar(p: PedidoRecepcion) {
        actual = p
        notas = p.notas.orEmpty()
        lineas = p.items.map { i ->
            LineaPedidoForm(
                clave = i.clave, tipo = i.tipo, refId = i.refId, nombre = i.nombre, cantidad = i.cantidad,
                precio = if (i.precioLista != null && i.precioUnit == i.precioLista) "" else montoTexto(i.precioUnit),
                lista = i.precioLista, notas = i.notas.orEmpty(),
            )
        }
    }
    /** El pedido Pendiente que ya tiene esta cita (null = ninguno o no se pudo leer). */
    suspend fun pendienteDeLaCita(): PedidoRecepcion? =
        citaId?.let { cid -> PedidosRepo.pendientes()?.pedidos?.firstOrNull { it.citaId == cid } }
    LaunchedEffect(Unit) {
        if (verificando) {
            pendienteDeLaCita()?.let { p ->
                adoptar(p)
                Toaster.info("Esta cita ya tiene un pedido pendiente: lo abrimos para que lo revises.")
            }
            verificando = false
        }
    }
    var pestana by remember { mutableStateOf("productos") }
    var busca by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var anulando by remember { mutableStateOf(false) }
    var confirmarAnular by remember { mutableStateOf(false) }
    // Un intento = una clave (reintentar tras un corte no crea dos pedidos). La clave
    // vale para ESE contenido: si cambian las líneas o la nota, es otro envío (clave nueva).
    var idem by remember { mutableStateOf<Pair<String, String>?>(null) }
    val citaDelPedido = pedido?.citaId ?: citaId
    val moneda = actual?.moneda ?: cat?.moneda ?: "PEN"

    LaunchedEffect(citaDelPedido) {
        val r = PedidosRepo.catalogo(citaDelPedido)
        if (r == null) errorCat = "No se pudo leer el catálogo. Revisa tu conexión."
        else {
            cat = r
            if (r.productos.isEmpty() && r.kits.isEmpty()) pestana = "servicios"
        }
    }

    val q = plegar(busca.trim())
    val opciones = cat?.let { ct ->
        fun ok(n: String) = q.isEmpty() || plegar(n).contains(q)
        if (pestana == "servicios") ct.servicios.filter { ok(it.nombre) }.take(40).map { Triple("servicio", it, if (ct.sinLista) null else it.precio) }
        else (ct.kits.map { Triple("kit", it, if (ct.sinLista) null else it.precio) } + ct.productos.map { Triple("producto", it, if (ct.sinLista) null else it.precio) })
            .filter { ok(it.second.nombre) }.take(40)
    }.orEmpty()

    fun agregar(tipo: String, id: String, nombre: String, precio: Double?) {
        if (lineas.size >= LimitesPedido.ITEMS) { Toaster.error("A lo más ${LimitesPedido.ITEMS} ítems por pedido."); return }
        // Producto/kit ya en la lista con el precio de lista: suma 1.
        val i = if (tipo != "servicio") lineas.indexOfFirst { it.tipo == tipo && it.refId == id && it.precio.isBlank() } else -1
        lineas = if (i >= 0) lineas.mapIndexed { k, l -> if (k == i) l.copy(cantidad = minOf(LimitesPedido.CANTIDAD_MAX, l.cantidad + 1)) else l }
        else lineas + LineaPedidoForm(null, tipo, id, nombre, 1, "", precio, "")
    }
    fun cambiar(k: Int, f: (LineaPedidoForm) -> LineaPedidoForm) { lineas = lineas.mapIndexed { j, l -> if (j == k) f(l) else l } }

    // Precio efectivo de cada línea (lo escrito o la lista) y su error.
    val leidas = lineas.map { l ->
        val r = leerPrecio(l.precio, moneda)
        val valor = if (r.ok) (r.valor ?: l.lista) else null
        val error = when {
            !r.ok -> r.error
            valor == null -> "Pon el precio"
            l.tipo == "servicio" && valor <= 0 -> "Pon el precio del servicio"
            else -> null
        }
        Triple(l, valor, error)
    }
    val totales = totalesPedido(leidas.map { (l, v, _) -> Triple(l.tipo, l.cantidad, v ?: 0.0) }, moneda)
    val listo = lineas.isNotEmpty() && leidas.all { it.third == null }
    fun din(n: Double) = formatearDinero(n, moneda)

    fun guardar() {
        if (guardando || !listo || verificando) return
        guardando = true
        val cuerpo = cuerpoGuardarPedido(actual?.id, citaId, pacienteId, lineas, notas)
        val huella = cuerpo.toString()
        val clave = idem?.takeIf { it.second == huella }?.first ?: nuevaIdemKey().also { idem = it to huella }
        scope.launch {
            val r = PedidosRepo.guardar(cuerpo, clave)
            guardando = false
            if (!r.ok) {
                if (!r.sinRed) idem = null
                if (r.sinRed) {
                    // La respuesta pudo perderse con el pedido YA creado: se mira antes de
                    // dejar repetir (repetir con la misma clave tampoco duplica).
                    Toaster.error("Sin conexión: puede haber llegado. Revisa antes de repetir.")
                    if (actual == null) pendienteDeLaCita()?.let { p ->
                        adoptar(p)
                        idem = null
                        Toaster.info("El pedido sí llegó a recepción: ahora lo estás editando.")
                    }
                } else Toaster.error(r.rechazo?.error ?: "No se pudo guardar el pedido")
                return@launch
            }
            val creado = (r.cuerpo?.get("creado") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
            Toaster.exito(if (creado) "🧾 Pedido enviado a recepción" else "Pedido actualizado")
            onGuardado()
        }
    }
    fun anular() {
        val p = actual ?: return
        if (anulando) return
        anulando = true
        scope.launch {
            val r = PedidosRepo.anular(p.id, "Anulado desde el pedido")
            anulando = false
            if (r.ok) { Toaster.exito("Pedido anulado"); onGuardado() }
            else Toaster.error(if (r.sinRed) "Sin conexión: no se anuló." else r.rechazo?.error ?: "No se pudo anular el pedido")
        }
    }

    val nombrePac = actual?.pacienteNombre ?: pacienteNombre
    DialogoForm(
        titulo = if (actual != null) "🧾 Editar pedido" else "🧾 Pedido para recepción",
        subtitulo = nombrePac,
        textoAccion = when {
            guardando -> "Guardando…"
            verificando -> "Revisando…"
            actual != null -> "Guardar cambios"
            lineas.isNotEmpty() -> "Enviar · ${din(totales.total)}"
            else -> "Enviar a recepción"
        },
        accionHabilitada = listo && !guardando && !verificando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = ::guardar,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Lo que se lleva o lo que se le hizo además en esta atención. Recepción lo cobra al salir" +
                    (if (citaDelPedido != null) ", junto con su cita si aún no la pagó." else "."),
                color = c.textoSuave, fontSize = 12.sp,
            )
            // ── Catálogo ──
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("productos" to "🛍 Productos", "servicios" to "💆 Servicios extra").forEach { (v, t) ->
                        val activo = pestana == v
                        Text(t, color = if (activo) c.sobreNavy else c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (activo) c.navy else c.chipBg)
                                .clickable { pestana = v }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = busca, onValueChange = { busca = it.take(60) }, singleLine = true,
                    placeholder = { Text(if (pestana == "productos") "Buscar producto o kit…" else "Buscar servicio…", fontSize = 13.sp) },
                    colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
                )
                errorCat?.let { Text(it, color = c.error, fontSize = 12.sp) }
                if (cat == null && errorCat == null) Text("Cargando catálogo…", color = c.textoSuave, fontSize = 12.sp)
                if (cat?.sinLista == true) Text("Esta sede cobra en otra moneda que el catálogo: escribe el precio de cada línea.", color = c.pend, fontSize = 11.sp)
                if (cat != null && opciones.isEmpty()) {
                    Text(
                        if (q.isNotEmpty()) "Nada con ese nombre."
                        else if (pestana == "productos") "La clínica no tiene productos activos (se cargan en Productos)." else "No hay servicios activos.",
                        color = c.textoSuave, fontSize = 12.sp,
                    )
                }
                opciones.forEach { (tipo, o, precio) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { agregar(tipo, o.id, o.nombre, precio) }.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(o.nombre, color = c.texto, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            o.extra?.let { Text(it, color = if (it == "Sin stock") c.error else c.textoSuave, fontSize = 10.sp) }
                        }
                        Text((precio?.let { din(it) } ?: "—") + "  ＋", color = c.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── El pedido ──
            EtqForm("El pedido")
            if (lineas.isEmpty()) Text("Toca un producto o servicio de arriba para agregarlo.", color = c.textoSuave, fontSize = 12.sp)
            leidas.forEachIndexed { k, (l, valor, error) ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                        .border(1.dp, if (error != null) c.error else c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(l.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(when (l.tipo) { "servicio" -> "Servicio extra"; "kit" -> "Kit"; else -> "Producto" }, color = c.textoSuave, fontSize = 10.sp)
                        }
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { lineas = lineas.filterIndexed { j, _ -> j != k } },
                            contentAlignment = Alignment.Center) { Text("✕", color = c.textoSuave, fontSize = 15.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BotonCant("−") { cambiar(k) { it.copy(cantidad = maxOf(1, it.cantidad - 1)) } }
                        Text("${l.cantidad}", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                        BotonCant("＋") { cambiar(k) { it.copy(cantidad = minOf(LimitesPedido.CANTIDAD_MAX, it.cantidad + 1)) } }
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = l.precio, onValueChange = { v -> cambiar(k) { it.copy(precio = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(10)) } },
                            singleLine = true, placeholder = { Text(l.lista?.let { montoTexto(it) } ?: "Precio", fontSize = 12.sp) },
                            label = { Text("c/u", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = coloresCampoForm(), modifier = Modifier.weight(1f),
                        )
                        Text(valor?.let { din(it * l.cantidad) } ?: "—", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (l.precio.isNotBlank() && l.lista != null) {
                        Text("↺ Precio de lista (${din(l.lista)})", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { cambiar(k) { it.copy(precio = "") } }.padding(vertical = 4.dp))
                    }
                    if (valor != null && l.lista != null && kotlin.math.abs(valor - l.lista) > 1e-9 && error == null) {
                        Text("Acordado · lista ${din(l.lista)}", color = c.textoSuave, fontSize = 10.sp)
                    }
                    error?.let { Text(it, color = c.error, fontSize = 11.sp) }
                    if (l.tipo == "servicio") {
                        OutlinedTextField(
                            value = l.notas, onValueChange = { v -> cambiar(k) { it.copy(notas = v.take(LimitesPedido.NOTAS_ITEM)) } },
                            placeholder = { Text("Detalle para la historia (zona, observación) — opcional", fontSize = 12.sp) },
                            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            EtqForm("Nota para recepción (opcional)")
            OutlinedTextField(
                value = notas, onValueChange = { notas = it.take(LimitesPedido.NOTAS) }, minLines = 2,
                placeholder = { Text("Ej.: se lleva 2 de regalo de la campaña, ya pagó la consulta…", fontSize = 12.sp) },
                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
            )

            if (lineas.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg).padding(12.dp)) {
                    if (totales.productos > 0) FilaTotal("Productos", din(totales.productos))
                    if (totales.servicios > 0) FilaTotal("Servicios extra", din(totales.servicios))
                    FilaTotal("Total del pedido", din(totales.total), fuerte = true)
                }
            }

            if (actual != null) {
                Text(
                    if (anulando) "Anulando…" else if (confirmarAnular) "¿Anular el pedido? Toca de nuevo para confirmar" else "Anular pedido",
                    color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable(enabled = !anulando && !guardando) { if (confirmarAnular) anular() else confirmarAnular = true }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * 🧾 COBRAR UN PEDIDO (gemelo de CobrarPedidoModal.tsx) — "se le cobra por los 2":
 * si la cita de esa atención aún no se pagó, va en el MISMO cobro con un solo
 * medio de pago. Primero la cita (POST cita/cobrar) y luego el pedido (POST
 * pedido/cobrar con la huella), los dos idempotentes. Si el pedido cambió
 * mientras estaba abierto, se relee y se avisa en vez de cobrar otro total.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DialogoCobrarPedido(
    pedidoInicial: PedidoRecepcion,
    onCerrar: () -> Unit,
    onEditar: ((PedidoRecepcion) -> Unit)?,
    onCobrado: (conCita: Boolean) -> Unit,
    /** sesiones / citas / agendar: hace falta para cobrar servicios (nacen como tratamiento). */
    puedeCrearTratamiento: Boolean = true,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var pedido by remember(pedidoInicial.id) { mutableStateOf(pedidoInicial) }
    val metodos = rememberMetodosPago().filter { it.trim().lowercase() != "saldo a favor" }
    var metodo by remember(pedidoInicial.id) { mutableStateOf("") }
    LaunchedEffect(metodos) { if (metodo.isEmpty() || metodo !in metodos) metodo = MetodoPagoPreferido.inicial(pedido.pacienteId, metodos) }
    var conCita by remember(pedidoInicial.id) { mutableStateOf(true) }
    var citaCobrada by remember(pedidoInicial.id) { mutableStateOf(false) }
    var cobrando by remember { mutableStateOf(false) }
    var anulando by remember { mutableStateOf(false) }
    var confirmarAnular by remember { mutableStateOf(false) }
    var idemPedido by remember(pedidoInicial.id) { mutableStateOf<String?>(null) }
    var idemCita by remember(pedidoInicial.id) { mutableStateOf<String?>(null) }

    val m = pedido.moneda
    fun din(n: Double) = formatearDinero(n, m)
    val citaMonto = if (citaCobrada) 0.0 else citaCobrableConPedido(pedido)
    val incluirCita = citaMonto > 0 && conCita
    val tot = totalesPedidoDe(pedido.items, m)
    val total = kotlin.math.round((tot.total + (if (incluirCita) citaMonto else 0.0)) * 100) / 100

    /**
     * Relee el pedido (con su cita) justo antes de mover dinero y lo deja como el
     * que se muestra. true si cambió algo de lo que se cobra (ítems, o la cita:
     * ya pagada / otro costo) o ya no se cobra: se avisa y NO se cobra nada.
     */
    suspend fun cambioAntesDeCobrar(): Boolean {
        val visto = pedido
        val fresco = PedidosRepo.porId(visto.id) ?: return false   // sin lectura: decide el servidor con la huella
        pedido = fresco
        if (fresco.estado != "Pendiente") {
            Toaster.info(if (fresco.estado == "Cobrado") "Este pedido ya se cobró." else "Este pedido fue anulado.")
            onCobrado(false)
            return true
        }
        if (huellaPedido(fresco.items) != huellaPedido(visto.items)) {
            idemPedido = null
            Toaster.error("El pedido cambió mientras lo tenías abierto: ahora suma ${formatearDinero(fresco.total, fresco.moneda)}. No se cobró nada.")
            return true
        }
        if (!citaCobrada && citaCobrableConPedido(fresco) != citaCobrableConPedido(visto)) {
            idemCita = null
            Toaster.error(
                if (citaCobrableConPedido(fresco) == 0.0) "La cita ya se cobró (o cambió) mientras lo tenías abierto: revisa el total. No se cobró nada."
                else "El precio de la cita cambió: ahora es ${formatearDinero(citaCobrableConPedido(fresco), fresco.moneda)}. No se cobró nada.",
            )
            return true
        }
        return false
    }

    fun cobrar() {
        if (cobrando || metodo.isBlank()) return
        // Servicios del pedido = tratamientos: sin permiso de crearlos, no se empieza
        // (ni siquiera la cita), como el 403 SIN_PERMISO_TRATAMIENTO del servidor.
        if (pedido.items.any { it.tipo == "servicio" } && !puedeCrearTratamiento) {
            Toaster.error("Este pedido tiene servicios: para cobrarlo hace falta también permiso de agenda o de atención (se registran como tratamiento).")
            return
        }
        cobrando = true
        scope.launch {
            try {
                if (cambioAntesDeCobrar()) return@launch
                // Desde aquí, todo con lo RECIÉN leído (nunca con lo que se pintó antes).
                val p = pedido
                val montoCita = if (citaCobrada) 0.0 else citaCobrableConPedido(p)
                val conLaCita = montoCita > 0 && conCita
                var citaSumada = false
                val cita = p.cita
                if (conLaCita && cita != null) {
                    val clave = idemCita ?: nuevaIdemKey().also { idemCita = it }
                    val r = PedidosRepo.cobrarCita(cita.id, metodo, clave)
                    if (!r.ok) {
                        Toaster.error("No se cobró nada: " + (if (r.sinRed) "sin conexión (revisa si la cita quedó cobrada antes de repetir)." else textoRechazo(r, "la cita no se pudo cobrar")))
                        return@launch
                    }
                    citaCobrada = true
                    // `yaEstaba`: la cita ya estaba cobrada (otro cobro): no se suma aquí.
                    citaSumada = (r.cuerpo?.get("yaEstaba") as? kotlinx.serialization.json.JsonPrimitive)?.content != "true"
                }
                val yaLaCita = if (citaSumada) "La cita SÍ quedó cobrada. " else ""
                val clave = idemPedido ?: nuevaIdemKey().also { idemPedido = it }
                val r = PedidosRepo.cobrar(p.id, metodo, huellaPedido(p.items), clave)
                if (!r.ok && r.rechazo?.codigo == "PEDIDO_CAMBIO") {
                    idemPedido = null
                    PedidosRepo.porId(p.id)?.let { pedido = it }
                    Toaster.error("${yaLaCita}El pedido cambió: revisa el nuevo total antes de cobrarlo.")
                    return@launch
                }
                if (!r.ok) {
                    // Rechazo limpio: clave nueva para reintentar. Sin red: misma clave (no duplica).
                    if (!r.sinRed) idemPedido = null
                    Toaster.error(yaLaCita + (if (r.sinRed) "Sin conexión: revisa si el cobro entró antes de repetirlo (no se duplica)." else textoRechazo(r, "No se pudo cobrar el pedido")))
                    return@launch
                }
                MetodoPagoPreferido.recordar(p.pacienteId, metodo)
                val yaEstaba = (r.cuerpo?.get("yaEstaba") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
                val aviso = (r.cuerpo?.get("aviso") as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                val cobradoTotal = kotlin.math.round((totalesPedidoDe(p.items, p.moneda).total + (if (citaSumada) montoCita else 0.0)) * 100) / 100
                Toaster.exito(if (yaEstaba) "Este pedido ya estaba cobrado" else "✓ Cobrado ${formatearDinero(cobradoTotal, p.moneda)} ($metodo)")
                aviso?.let { Toaster.info(it) }
                onCobrado(citaSumada || conLaCita)
            } finally { cobrando = false }
        }
    }
    fun anular() {
        if (anulando) return
        anulando = true
        scope.launch {
            val r = PedidosRepo.anular(pedido.id, "Anulado en recepción")
            anulando = false
            if (r.ok) { Toaster.exito("Pedido anulado"); onCobrado(false) }
            else Toaster.error(if (r.sinRed) "Sin conexión: no se anuló." else r.rechazo?.error ?: "No se pudo anular el pedido")
        }
    }

    DialogoForm(
        titulo = "🧾 Cobrar pedido",
        subtitulo = pedido.pacienteNombre ?: "Paciente",
        textoAccion = if (cobrando) "Cobrando…" else "Cobrar ${din(total)}",
        accionHabilitada = !cobrando && metodo.isNotBlank(),
        onCancelar = { if (!cobrando) onCerrar() },
        onAccion = ::cobrar,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val de = listOfNotNull(
                pedido.terapeutaNombre?.let { "lo pidió $it" },
                pedido.cita?.fecha?.takeIf { it.length >= 10 }?.let { "atención del ${it.substring(8, 10)}/${it.substring(5, 7)}" },
            ).joinToString(" · ")
            if (de.isNotBlank()) Text(de, color = c.textoSuave, fontSize = 12.sp)
            pedido.notas?.let {
                Text("📝 $it", color = c.texto, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg).padding(10.dp))
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)),
            ) {
                if (citaMonto > 0) {
                    Row(Modifier.fillMaxWidth().clickable { conCita = !conCita }.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = conCita, onCheckedChange = { conCita = it }, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                        Text("Su ${(pedido.cita?.tipo ?: "cita").lowercase()} (todavía sin pagar)", color = c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(din(citaMonto), color = if (conCita) c.texto else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp))
                    }
                }
                pedido.items.forEach { i ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(i.nombre + (if (i.cantidad > 1) " ×${i.cantidad}" else ""), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            val sub = (when (i.tipo) { "servicio" -> "Servicio extra (queda como tratamiento)"; "kit" -> "Kit"; else -> "Producto" }) +
                                (if (i.precioLista != null && kotlin.math.abs(i.precioUnit - i.precioLista) > 1e-9) " · acordado (lista ${din(i.precioLista)})" else "") +
                                (i.notas?.let { " · $it" } ?: "")
                            Text(sub, color = c.textoSuave, fontSize = 10.sp)
                        }
                        Text(din(subtotalItemPedido(i.cantidad, i.precioUnit, m)), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("Total", color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text(din(total), color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (pedido.cita?.tipo == "Sesión") {
                Text("Su sesión se cobra desde su tratamiento (💰 de la cita), no aquí.", color = c.textoSuave, fontSize = 11.sp)
            }
            EtqForm("Medio de pago")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                metodos.forEach { x ->
                    val activo = metodo == x
                    Text(x, color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (activo) c.navy else c.superficie)
                            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                            .clickable(enabled = !cobrando) { metodo = x }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            Text("Un solo medio. Si paga con dos (efectivo + Yape), cobra la cita con su 💰 y después este pedido.", color = c.textoSuave, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (onEditar != null) {
                    Text("✏ Editar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(enabled = !cobrando) { onEditar(pedido) }.padding(vertical = 8.dp))
                }
                Text(
                    if (anulando) "Anulando…" else if (confirmarAnular) "¿Anular? Toca de nuevo: no se cobra" else "Anular",
                    color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable(enabled = !cobrando && !anulando) { if (confirmarAnular) anular() else confirmarAnular = true }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** 🧾 "N pedidos por cobrar" (solo quien cobra): la lista con un toque para cobrar cada uno. */
@Composable
fun BannerPedidosPorCobrar(pendientes: List<PedidoRecepcion>, total: Int, onCobrar: (PedidoRecepcion) -> Unit) {
    if (pendientes.isEmpty()) return
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    val n = maxOf(total, pendientes.size)
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(Modifier.fillMaxWidth().clip(forma).border(1.dp, c.teal, forma)) {
        Row(
            Modifier.fillMaxWidth().background(c.teal.copy(alpha = 0.10f)).clickable { abierto = !abierto }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧾", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("$n pedido${if (n != 1) "s" else ""} por cobrar", color = c.teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Lo que pidieron los profesionales en la atención: productos y servicios extra.", color = c.textoSuave, fontSize = 11.sp)
            }
            Text(if (abierto) "▲" else "▼", color = c.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (abierto) {
            pendientes.forEach { p ->
                val cita = citaCobrableConPedido(p)
                Row(
                    Modifier.fillMaxWidth().background(c.superficie).clickable { onCobrar(p) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.pacienteNombre ?: "Paciente", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            resumenPedido(p.items) + (p.terapeutaNombre?.let { " · $it" } ?: "") +
                                (if (cita > 0) " · + su ${(p.cita?.tipo ?: "cita").lowercase()} sin pagar" else ""),
                            color = c.textoSuave, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(formatearDinero(p.total + cita, p.moneda), color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text("Cobrar", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.ok).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            if (n > pendientes.size) Text("Se muestran los ${pendientes.size} más recientes de $n.", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

/** Chip de la cita: "🧾 Pedido pendiente · S/ 120". */
@Composable
fun ChipPedido(pedidos: List<PedidoRecepcion>?, onClick: (PedidoRecepcion) -> Unit) {
    val p = pedidos?.firstOrNull() ?: return
    val c = Sania.colors
    val total = pedidos.sumOf { it.total }
    Text(
        "🧾 Pedido pendiente · ${formatearDinero(total, p.moneda)}",
        color = c.teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.teal.copy(alpha = 0.12f))
            .border(1.dp, c.teal.copy(alpha = 0.3f), RoundedCornerShape(Sania.shape.pill.dp))
            .clickable { onClick(p) }.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun BotonCant(t: String, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = c.navy, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun FilaTotal(t: String, v: String, fuerte: Boolean = false) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(t, color = if (fuerte) c.navy else c.texto, fontSize = if (fuerte) 15.sp else 13.sp,
            fontWeight = if (fuerte) FontWeight.ExtraBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(v, color = if (fuerte) c.navy else c.texto, fontSize = if (fuerte) 15.sp else 13.sp,
            fontWeight = if (fuerte) FontWeight.ExtraBold else FontWeight.Normal)
    }
}

/**
 * El texto de un rechazo: la `causa` original si el servidor la manda (un 409 de
 * un cobro a medias, conRespaldoIdempotente), si no el `error` (como la web).
 */
private fun textoRechazo(r: PedidosRepo.Envio, porDefecto: String): String =
    (r.rechazo?.datos?.get("causa") as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
        ?: r.rechazo?.error?.takeIf { it.isNotBlank() } ?: porDefecto

/** 12.0 → "12", 12.5 → "12.5" (para el campo de precio). */
private fun montoTexto(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
