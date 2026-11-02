package pe.saniape.app.ui.clinica.comprobante

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ComprobanteSunat
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DOCUMENTOS_BOLETA
import pe.saniape.app.data.staff.DatosCobroSunat
import pe.saniape.app.data.staff.FacturacionSunatRepo
import pe.saniape.app.data.staff.ReciboRepo
import pe.saniape.app.data.staff.RespuestaSunat
import pe.saniape.app.data.staff.ResultadoRecibo
import pe.saniape.app.data.staff.TOPE_BOLETA_SIN_DOCUMENTO
import pe.saniape.app.data.staff.TonoSunat
import pe.saniape.app.data.staff.aceptadoSunat
import pe.saniape.app.data.staff.avisoResultadoSunat
import pe.saniape.app.data.staff.boletaSinDocExcede
import pe.saniape.app.data.staff.comprobanteVigente
import pe.saniape.app.data.staff.consultableSunat
import pe.saniape.app.data.staff.cuerpoReceptor
import pe.saniape.app.data.staff.enlaceWhatsApp
import pe.saniape.app.data.staff.etiquetaEstadoSunat
import pe.saniape.app.data.staff.nombreTipoSunat
import pe.saniape.app.data.staff.notaCreditoVigente
import pe.saniape.app.data.staff.receptorListo
import pe.saniape.app.data.staff.tonoEstadoSunat
import pe.saniape.app.data.staff.ultimoRechazo
import pe.saniape.app.ui.DialogoConTeclado
import pe.saniape.app.ui.LocalTecladoEnDialogo
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.clinica.recetas.RecetaTrasAtencion
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.theme.tocable

/**
 * Puente entre lo que COBRA y la boleta/factura SUNAT (gemelo de
 * lib/facturacion-cliente.ts de la web). [HostFacturacionSunat], montado una vez
 * en ClinicaConTabs, sabe si la clínica emite y muestra la oferta y la hoja de
 * emisión. Quien cobra llama a [ofrecer] en vez del toast de siempre: con la
 * facturación apagada (o sin permiso de pagos) devuelve false y el llamador
 * muestra su toast como antes.
 */
object FacturacionSunat {
    data class Oferta(val origenTipo: String, val origenId: String, val mensaje: String)
    data class Pedido(val origenTipo: String, val origenId: String, val tipoInicial: String = "03")

    private val activaEstado = mutableStateOf(false)
    private val ofertaEstado = mutableStateOf<Oferta?>(null)
    private val pedidoEstado = mutableStateOf<Pedido?>(null)
    private val cambiosEstado = mutableIntStateOf(0)

    /** La clínica emite y quien mira tiene 'pagos' (lo fija el host). */
    val activa: Boolean get() = activaEstado.value
    internal fun fijarActiva(v: Boolean) { activaEstado.value = v; if (!v) { ofertaEstado.value = null; pedidoEstado.value = null } }

    val oferta: Oferta? get() = ofertaEstado.value
    val pedido: Pedido? get() = pedidoEstado.value

    /** Sube con cada emisión/reintento: los chips de los cobros se recargan. */
    val cambios: Int get() = cambiosEstado.intValue
    internal fun huboCambio() { cambiosEstado.intValue++ }

    /**
     * Tras un cobro: «✓ [mensaje] · ¿Boleta o factura electrónica?» (como el toast
     * de `ofrecerRecibo` de la web). false = facturación apagada: el llamador
     * muestra su toast de siempre.
     */
    fun ofrecer(origenTipo: String, origenId: String, mensaje: String = "Pago registrado"): Boolean {
        if (!activa || origenId.isBlank()) return false
        ofertaEstado.value = Oferta(origenTipo, origenId, mensaje)
        return true
    }

    /** Abre la hoja del cobro: su comprobante si ya tiene, o la emisión si no. */
    fun abrir(origenTipo: String, origenId: String, tipoInicial: String = "03") {
        if (!activa || origenId.isBlank()) return
        ofertaEstado.value = null
        pedidoEstado.value = Pedido(origenTipo, origenId, tipoInicial)
    }

    fun descartar() { ofertaEstado.value = null }
    internal fun cerrar() { pedidoEstado.value = null }

    /** Otra clínica o salir: nada de la anterior queda ofrecido ni abierto. */
    fun reiniciar() {
        activaEstado.value = false; ofertaEstado.value = null; pedidoEstado.value = null
        FacturacionSunatRepo.limpiarCache()
    }
}

/**
 * Tras registrar un pago de TRATAMIENTO (ficha, cobro de sesión): con la
 * facturación activa busca el pago recién creado y ofrece su boleta; si no (o
 * si el pago quedó en la cola offline y no aparece), el toast de siempre.
 */
suspend fun ofrecerPagoSunat(mensaje: String, tratamientoId: String, monto: Double, sesionId: String? = null) {
    if (FacturacionSunat.activa) {
        val id = FacturacionSunatRepo.pagoRecien(tratamientoId, monto, sesionId)
        if (id != null && FacturacionSunat.ofrecer("pago", id, mensaje)) return
    }
    Toaster.exito(mensaje)
}

/** Vida de la oferta en pantalla (como el toast de 20 s de la web). */
private const val VIDA_OFERTA_SUNAT_MS = 20_000L

/**
 * Host único (en ClinicaConTabs, encima de todo): averigua UNA vez por clínica si
 * emite (GET sin params, cacheado), y pinta la barra de la oferta y la hoja.
 * Sin el add-on o sin permiso de pagos no pinta nada.
 */
@Composable
fun HostFacturacionSunat(ctx: ContextoStaff) {
    val puede = ctx.puede("pagos")
    LaunchedEffect(ctx.clinicaId, puede) {
        FacturacionSunat.fijarActiva(false)
        if (puede) FacturacionSunat.fijarActiva(FacturacionSunatRepo.habilitada(ctx.clinicaId))
    }
    val oferta = FacturacionSunat.oferta
    var mostrada by remember { mutableStateOf<FacturacionSunat.Oferta?>(null) }
    LaunchedEffect(oferta) {
        if (oferta != null) {
            mostrada = oferta
            delay(VIDA_OFERTA_SUNAT_MS)
            if (FacturacionSunat.oferta === oferta) FacturacionSunat.descartar()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = oferta != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            val o = mostrada ?: return@AnimatedVisibility
            // Si la barra de "Dar receta" está a la vista, esta va encima (no se tapan).
            BarraOfertaSunat(
                mensaje = o.mensaje,
                abajo = if (RecetaTrasAtencion.oferta != null) 220.dp else 90.dp,
                onBoleta = { FacturacionSunat.abrir(o.origenTipo, o.origenId, "03") },
                onFactura = { FacturacionSunat.abrir(o.origenTipo, o.origenId, "01") },
                onDescartar = { FacturacionSunat.descartar() },
            )
        }
    }
    FacturacionSunat.pedido?.let { p ->
        DialogoSunat(p.origenTipo, p.origenId, p.tipoInicial) { FacturacionSunat.cerrar() }
    }
}

@Composable
private fun BarraOfertaSunat(
    mensaje: String,
    abajo: androidx.compose.ui.unit.Dp,
    onBoleta: () -> Unit,
    onFactura: () -> Unit,
    onDescartar: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.padding(bottom = abajo, start = 16.dp, end = 16.dp).fillMaxWidth()
            .shadow(6.dp, forma).clip(forma).background(c.superficie).border(1.dp, c.borde, forma)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("✓ $mensaje", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("¿Boleta o factura electrónica?", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "✕", color = c.textoSuave, fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).tocable { onDescartar() }
                    .padding(horizontal = 8.dp, vertical = 2.dp).semantics { contentDescription = "Ahora no" },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonLleno("🧾 Boleta", c.navy, Modifier.weight(1f), onClick = onBoleta)
            BotonBorde("🏢 Factura", c.navy, Modifier.weight(1f), onClick = onFactura)
        }
    }
}

// ── La hoja del cobro: comprobante emitido o emisión ───────────────────────

private sealed class EstadoHoja {
    data object Cargando : EstadoHoja()
    data class Falla(val mensaje: String) : EstadoHoja()
    data class Listo(val datos: DatosCobroSunat) : EstadoHoja()
}

/**
 * La hoja SUNAT de UN cobro (gemelo de EmitirModal + el chip de la web, en una):
 *  · si ya tiene su boleta/factura vigente → número, estado y acciones (Ver PDF,
 *    WhatsApp, Reintentar si quedó pendiente, Consultar SUNAT si está en cola);
 *  · si no → la emisión: Boleta (DNI del paciente precargado) o Factura (RUC +
 *    Buscar → razón social y dirección).
 * Una sede fuera del Perú (otra moneda) no lleva SUNAT: se ofrece el recibo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoSunat(origenTipo: String, origenId: String, tipoInicial: String = "03", onCerrar: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var estado by remember(origenTipo, origenId) { mutableStateOf<EstadoHoja>(EstadoHoja.Cargando) }
    var recarga by remember { mutableIntStateOf(0) }
    // Recién emitido/reintentado: se muestra ESE (aunque la lectura aún no lo traiga).
    var resultado by remember(origenTipo, origenId) { mutableStateOf<ComprobanteSunat?>(null) }
    // Emitir otra vez tras un rechazo: vuelve al formulario aunque haya historial.
    var forzarFormulario by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf<String?>(null) }   // texto de lo que está en curso

    LaunchedEffect(origenTipo, origenId, recarga) {
        if (resultado == null) estado = EstadoHoja.Cargando
        estado = when (val r = FacturacionSunatRepo.datos(origenTipo, origenId)) {
            is RespuestaSunat.Ok -> EstadoHoja.Listo(r.dato)
            is RespuestaSunat.Error -> EstadoHoja.Falla(r.mensaje)
        }
    }

    // Formulario
    var tipo by remember(origenTipo, origenId) { mutableStateOf(if (tipoInicial == "01") "01" else "03") }
    var doc by remember { mutableStateOf<String?>(null) }
    var num by remember { mutableStateOf<String?>(null) }
    var nombre by remember { mutableStateOf<String?>(null) }
    var ruc by remember { mutableStateOf("") }
    var razon by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var ubigeo by remember { mutableStateOf("") }
    var buscandoRuc by remember { mutableStateOf(false) }

    val datos = (estado as? EstadoHoja.Listo)?.datos
    val sugerido = datos?.sugerido
    val total = datos?.total ?: 0.0
    val docB = doc ?: sugerido?.tipoDoc ?: "1"
    val numB = num ?: sugerido?.numDoc?.takeIf { it != "-" }.orEmpty()
    val nombreB = nombre ?: sugerido?.razonSocial.orEmpty()
    val sinSunat = datos?.moneda?.let { it.isNotBlank() && it != "PEN" } == true
    val vigente = resultado?.takeIf { pe.saniape.app.data.staff.esVigenteSunat(it.estado) }
        ?: datos?.comprobantes?.let { comprobanteVigente(it) }
    val mostrarFormulario = datos != null && !sinSunat && (vigente == null || forzarFormulario)
    val listo = receptorListo(tipo, docB, numB, nombreB, ruc, razon, total)
    val enviando = ocupado != null

    fun alTerminar(r: RespuestaSunat<ComprobanteSunat?>) {
        when (r) {
            is RespuestaSunat.Ok -> {
                r.dato?.let { comp ->
                    val (txt, mal) = avisoResultadoSunat(comp)
                    if (mal) Toaster.error(txt) else Toaster.exito(txt)
                    resultado = comp
                    forzarFormulario = false
                }
                FacturacionSunat.huboCambio()
                recarga++
            }
            is RespuestaSunat.Error -> Toaster.error(r.mensaje)
        }
    }

    fun buscarRuc() {
        if (ruc.length != 11 || ruc.any { !it.isDigit() }) { Toaster.error("El RUC tiene 11 dígitos"); return }
        if (buscandoRuc) return
        buscandoRuc = true
        scope.launch {
            when (val r = FacturacionSunatRepo.ruc(ruc)) {
                is RespuestaSunat.Ok -> {
                    razon = r.dato.razonSocial; direccion = r.dato.direccion
                    ubigeo = r.dato.ubigeo?.takeIf { it.length == 6 && it.all { ch -> ch.isDigit() } }.orEmpty()
                    if (!r.dato.estado.isNullOrBlank() && r.dato.estado != "ACTIVO") {
                        Toaster.info("Ese RUC figura ${r.dato.estado} / ${r.dato.condicion.orEmpty()} en SUNAT")
                    }
                }
                is RespuestaSunat.Error -> Toaster.error(r.mensaje)
            }
            buscandoRuc = false
        }
    }

    fun emitir() {
        if (enviando || !listo || datos == null || sinSunat || total <= 0) return
        val receptor = cuerpoReceptor(tipo, docB, numB, nombreB, sugerido?.email, ruc, razon, direccion, ubigeo)
        ocupado = "Enviando a SUNAT…"
        scope.launch {
            val r = FacturacionSunatRepo.emitir(origenTipo, origenId, tipo, receptor)
            ocupado = null
            alTerminar(r)
        }
    }

    DialogoConTeclado(onDismissRequest = { if (!enviando) onCerrar() }) {
        Surface(
            shape = RoundedCornerShape(Sania.shape.md.dp), color = c.superficie,
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Column {
                // Cabecera navy (como los demás formularios). Con el teclado abierto,
                // «Emitir» también va aquí: el pie puede quedar tapado.
                Row(
                    Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🧾 Comprobante electrónico SUNAT", color = c.sobreNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (LocalTecladoEnDialogo.current && mostrarFormulario) {
                        Text(
                            if (enviando) "Enviando…" else "Emitir", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.ok)
                                .tocable(habilitado = listo && !enviando) { emitir() }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    } else {
                        Text(
                            "✕", color = c.sobreNavy, fontSize = 16.sp,
                            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).tocable(habilitado = !enviando) { onCerrar() }
                                .padding(horizontal = 8.dp, vertical = 2.dp).semantics { contentDescription = "Cerrar" },
                        )
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false).heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (val e = estado) {
                        EstadoHoja.Cargando -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.width(18.dp).height(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Cargando el cobro…", color = c.textoSuave, fontSize = 13.sp)
                        }
                        is EstadoHoja.Falla -> {
                            Text(e.mensaje, color = c.error, fontSize = 13.sp)
                            BotonBorde("Reintentar", c.navy, Modifier.fillMaxWidth()) { recarga++ }
                        }
                        is EstadoHoja.Listo -> when {
                            sinSunat -> {
                                Text(
                                    "Este cobro es de una sede fuera del Perú (${e.datos.moneda}): no lleva comprobante SUNAT.",
                                    color = c.texto, fontSize = 13.sp,
                                )
                                BotonBorde(if (ocupado != null) "Generando…" else "🧾 Ver el recibo", c.navy, Modifier.fillMaxWidth(), habilitado = !enviando) {
                                    ocupado = "recibo"
                                    scope.launch {
                                        val r = ReciboRepo.descargar(origenTipo, origenId, "a4")
                                        ocupado = null
                                        when (r) {
                                            is ResultadoRecibo.Ok -> { onCerrar(); acciones.abrirPdf("recibo-$origenTipo-${origenId.take(8)}.pdf", r.pdf) }
                                            is ResultadoRecibo.Error -> Toaster.error(r.mensaje)
                                        }
                                    }
                                }
                            }
                            !mostrarFormulario && vigente != null -> VistaComprobante(
                                comp = vigente,
                                ocupado = ocupado,
                                onPdf = {
                                    ocupado = "pdf"
                                    scope.launch {
                                        val r = FacturacionSunatRepo.pdf(vigente.id)
                                        ocupado = null
                                        when (r) {
                                            is RespuestaSunat.Ok -> acciones.abrirPdf("${vigente.numero ?: "comprobante"}.pdf", r.dato)
                                            is RespuestaSunat.Error -> Toaster.error(r.mensaje)
                                        }
                                    }
                                },
                                onWhatsApp = {
                                    ocupado = "whatsapp"
                                    scope.launch {
                                        val r = FacturacionSunatRepo.enlace(vigente.id)
                                        ocupado = null
                                        when (r) {
                                            is RespuestaSunat.Ok -> {
                                                val url = enlaceWhatsApp(r.dato.telefono, r.dato.texto)
                                                if (url != null) acciones.abrirUrl(url)
                                                else {
                                                    acciones.copiarTexto(r.dato.texto, "Comprobante")
                                                    Toaster.info("El paciente no tiene celular registrado: copiamos el mensaje con el enlace para que lo pegues.")
                                                }
                                            }
                                            is RespuestaSunat.Error -> Toaster.error(r.mensaje)
                                        }
                                    }
                                },
                                onReintentar = {
                                    ocupado = "Enviando a SUNAT…"
                                    scope.launch {
                                        val r = FacturacionSunatRepo.reintentar(vigente.id)
                                        ocupado = null
                                        alTerminar(r)
                                    }
                                },
                                onConsultar = {
                                    ocupado = "Consultando SUNAT…"
                                    scope.launch {
                                        val r = FacturacionSunatRepo.refrescar(vigente.id)
                                        ocupado = null
                                        if (r is RespuestaSunat.Ok && r.dato?.estado == vigente.estado) Toaster.info("Sin cambios todavía: ${etiquetaEstadoSunat(vigente.estado)}")
                                        else alTerminar(r)
                                    }
                                },
                            )
                            else -> {
                                // Lo anterior de este cobro: la nota de crédito que lo anuló o el rechazo.
                                val filas = e.datos.comprobantes
                                notaCreditoVigente(filas)?.numero?.let {
                                    Text("↩ Anulado con la nota de crédito $it. Puedes emitir uno nuevo.", color = c.textoSuave, fontSize = 12.sp)
                                }
                                (resultado?.takeIf { !pe.saniape.app.data.staff.esVigenteSunat(it.estado) } ?: ultimoRechazo(filas))?.let { r ->
                                    Text(
                                        "⚠ ${nombreTipoSunat(r.tipo)} ${r.numero.orEmpty()} ${etiquetaEstadoSunat(r.estado).lowercase()}${r.mensaje?.let { ": $it" } ?: ""}",
                                        color = c.error, fontSize = 12.sp,
                                    )
                                }
                                Formulario(
                                    total = total, tipo = tipo, onTipo = { tipo = it },
                                    docB = docB, onDoc = { doc = it }, numB = numB, onNum = { num = it },
                                    nombreB = nombreB, onNombre = { nombre = it },
                                    ruc = ruc, onRuc = { v ->
                                        ruc = v.filter { it.isDigit() }.take(11)
                                        // Como el onBlur de la web: al completar los 11 dígitos se busca solo.
                                        if (ruc.length == 11 && razon.isBlank()) buscarRuc()
                                    },
                                    razon = razon, onRazon = { razon = it },
                                    direccion = direccion, onDireccion = { direccion = it },
                                    buscandoRuc = buscandoRuc, onBuscarRuc = { buscarRuc() },
                                    habilitado = !enviando,
                                )
                                Text(
                                    "Se envía a SUNAT al instante. Si te equivocas, el administrador puede anularlo con una nota de crédito (desde la web).",
                                    color = c.textoSuave, fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
                // Pie
                if (mostrarFormulario && !LocalTecladoEnDialogo.current) {
                    Row(
                        Modifier.fillMaxWidth().background(c.fondo).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BotonBorde("Cancelar", c.textoSuave, Modifier.weight(1f), habilitado = !enviando) {
                            if (forzarFormulario) forzarFormulario = false else onCerrar()
                        }
                        BotonLleno(
                            if (enviando) "Enviando a SUNAT…" else "Emitir ${nombreTipoSunat(tipo).lowercase()}",
                            c.navy, Modifier.weight(1.6f), habilitado = listo && !enviando && total > 0,
                        ) { emitir() }
                    }
                    if (enviando) {
                        Text(
                            "Puede tardar unos segundos. No cierres la app.",
                            color = c.textoSuave, fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().background(c.fondo).padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        )
                    }
                } else if (!mostrarFormulario && vigente != null && !sinSunat) {
                    Row(
                        Modifier.fillMaxWidth().background(c.fondo).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) { BotonLleno("Listo", c.navy, Modifier.width(120.dp), habilitado = !enviando) { onCerrar() } }
                }
            }
        }
    }
}

/** El comprobante emitido: número, estado y lo que se puede hacer con él. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VistaComprobante(
    comp: ComprobanteSunat,
    ocupado: String?,
    onPdf: () -> Unit,
    onWhatsApp: () -> Unit,
    onReintentar: () -> Unit,
    onConsultar: () -> Unit,
) {
    val c = Sania.colors
    val (fg, bg) = coloresTono(tonoEstadoSunat(comp.estado))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(bg).padding(14.dp),
        ) {
            Text(nombreTipoSunat(comp.tipo).uppercase(), color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(comp.numero ?: "Sin número todavía", color = c.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("S/ ${pe.saniape.app.data.staff.textoMonto(comp.importeTotal)}", color = c.texto, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                (if (comp.estado == "accepted") "✓ " else if (tonoEstadoSunat(comp.estado) == TonoSunat.CAMINO) "⏳ " else "⚠ ") + etiquetaEstadoSunat(comp.estado),
                color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            comp.mensaje?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }
        }
        when {
            comp.estado == "pendiente" -> {
                Text("No sabemos si llegó a SUNAT. Reintentar no la duplica.", color = c.textoSuave, fontSize = 12.sp)
                BotonLleno(if (ocupado != null) ocupado else "↻ Reintentar", c.navy, Modifier.fillMaxWidth(), habilitado = ocupado == null, onClick = onReintentar)
            }
            consultableSunat(comp.estado) -> {
                if (comp.estado == "revisar") Text(
                    "Pudo haber quedado aceptada. No se emite otra para no duplicarla; escríbenos para confirmarla.",
                    color = c.textoSuave, fontSize = 12.sp,
                )
                BotonBorde(if (ocupado == "Consultando SUNAT…") ocupado else "↻ Consultar SUNAT", c.navy, Modifier.fillMaxWidth(), habilitado = ocupado == null, onClick = onConsultar)
            }
        }
        if (aceptadoSunat(comp.estado) || comp.numero != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonBorde(if (ocupado == "pdf") "Abriendo…" else "📄 Ver PDF", c.navy, Modifier.weight(1f), habilitado = ocupado == null, onClick = onPdf)
                BotonLleno(if (ocupado == "whatsapp") "Abriendo…" else "📲 WhatsApp", c.ok, Modifier.weight(1f), habilitado = ocupado == null && aceptadoSunat(comp.estado), onClick = onWhatsApp)
            }
        }
    }
}

@Composable
private fun Formulario(
    total: Double,
    tipo: String, onTipo: (String) -> Unit,
    docB: String, onDoc: (String) -> Unit,
    numB: String, onNum: (String) -> Unit,
    nombreB: String, onNombre: (String) -> Unit,
    ruc: String, onRuc: (String) -> Unit,
    razon: String, onRazon: (String) -> Unit,
    direccion: String, onDireccion: (String) -> Unit,
    buscandoRuc: Boolean, onBuscarRuc: () -> Unit,
    habilitado: Boolean,
) {
    val c = Sania.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Total del comprobante", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("S/ ${pe.saniape.app.data.staff.textoMonto(total)}", color = c.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OpcionTipo("Boleta", "A nombre de la persona (DNI)", tipo == "03", Modifier.weight(1f), habilitado) { onTipo("03") }
            OpcionTipo("Factura", "A nombre de una empresa (RUC)", tipo == "01", Modifier.weight(1f), habilitado) { onTipo("01") }
        }
        if (tipo == "03") {
            Etiqueta("Documento")
            FilaChips(DOCUMENTOS_BOLETA, docB, habilitado, onDoc)
            if (docB != "0") {
                OutlinedTextField(
                    value = numB, onValueChange = { v -> onNum(if (docB == "1") v.filter { it.isDigit() }.take(8) else v.trim().take(12)) },
                    label = { Text(if (docB == "1") "Número (8 dígitos)" else "Número") }, singleLine = true, enabled = habilitado,
                    keyboardOptions = KeyboardOptions(keyboardType = if (docB == "1") KeyboardType.Number else KeyboardType.Text),
                    colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = nombreB, onValueChange = onNombre, label = { Text("Nombre del cliente") }, singleLine = true, enabled = habilitado,
                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
            )
            if (boletaSinDocExcede(docB, total)) {
                Text("SUNAT pide el DNI en boletas de más de S/ ${TOPE_BOLETA_SIN_DOCUMENTO.toInt()}.", color = c.pend, fontSize = 12.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ruc, onValueChange = onRuc, label = { Text("RUC (11 dígitos)") }, singleLine = true, enabled = habilitado,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = coloresCampoForm(), modifier = Modifier.weight(1f),
                )
                BotonBorde(if (buscandoRuc) "…" else "Buscar", c.navy, Modifier.width(88.dp), habilitado = habilitado && !buscandoRuc && ruc.length == 11, onClick = onBuscarRuc)
            }
            OutlinedTextField(
                value = razon, onValueChange = onRazon, label = { Text("Razón social") }, enabled = habilitado,
                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = direccion, onValueChange = onDireccion, label = { Text("Dirección fiscal") }, enabled = habilitado,
                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaChips(opciones: List<Pair<String, String>>, sel: String, habilitado: Boolean, onSel: (String) -> Unit) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { (v, t) ->
            val activo = v == sel
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                    .background(if (activo) c.navy else c.chipBg)
                    .tocable(habilitado = habilitado) { onSel(v) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) { Text(t, color = if (activo) c.sobreNavy else c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun OpcionTipo(titulo: String, detalle: String, activo: Boolean, modifier: Modifier, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        modifier.clip(forma).background(if (activo) c.chipBg else c.superficie)
            .border(if (activo) 2.dp else 1.dp, if (activo) c.navy else c.borde, forma)
            .tocable(habilitado = habilitado) { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(detalle, color = c.textoSuave, fontSize = 11.sp)
    }
}

@Composable
private fun Etiqueta(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun BotonLleno(texto: String, color: Color, modifier: Modifier = Modifier, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(if (habilitado) color else c.borde)
            .tocable(habilitado = habilitado) { onClick() }.padding(vertical = 11.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (habilitado) c.sobreNavy else c.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun BotonBorde(texto: String, color: Color, modifier: Modifier = Modifier, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box(
        modifier.clip(forma).border(1.dp, if (habilitado) color else c.borde, forma)
            .tocable(habilitado = habilitado) { onClick() }.padding(vertical = 10.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (habilitado) color else c.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun coloresTono(t: TonoSunat): Pair<Color, Color> {
    val c = Sania.colors
    return when (t) {
        TonoSunat.OK -> c.ok to c.okBg
        TonoSunat.CAMINO -> c.pend to c.pendBg
        TonoSunat.MAL -> c.error to c.errorBg
        TonoSunat.ANULADO -> c.textoSuave to c.chipBg
    }
}

// ── El chip SUNAT de un cobro ya hecho ─────────────────────────────────────

/**
 * Botón/chip SUNAT de UN cobro (gemelo de ComprobanteSunat.tsx): con su
 * boleta/factura vigente, el número y su estado (verde aceptado, ámbar en
 * camino, rojo rechazado); si no tiene, «🧾 Boleta / Factura» para emitirla.
 * Tocarlo abre [DialogoSunat]. [comprobantes] = los de este cobro si el padre ya
 * los leyó (la lista de pagos los trae en una consulta); null = los pide él.
 * Solo con la facturación activa (y permiso de pagos): si no, no pinta nada.
 */
@Composable
fun ChipSunat(
    origenTipo: String,
    origenId: String,
    modifier: Modifier = Modifier,
    comprobantes: List<ComprobanteSunat>? = null,
    compacto: Boolean = false,
) {
    if (!FacturacionSunat.activa) return
    val c = Sania.colors
    var propios by remember(origenTipo, origenId) { mutableStateOf<List<ComprobanteSunat>?>(null) }
    val cambios = FacturacionSunat.cambios
    if (comprobantes == null) {
        LaunchedEffect(origenTipo, origenId, cambios) {
            (FacturacionSunatRepo.datos(origenTipo, origenId) as? RespuestaSunat.Ok)?.let { propios = it.dato.comprobantes }
        }
    }
    val filas = comprobantes ?: propios ?: return
    val vigente = comprobanteVigente(filas)
    val nc = notaCreditoVigente(filas)
    val (texto, tono) = when {
        vigente == null -> (if (compacto) "🧾 SUNAT" else "🧾 Boleta / Factura") to null
        vigente.estado == "pendiente" -> "⏳ ${nombreTipoSunat(vigente.tipo)}: reintentar" to TonoSunat.CAMINO
        else -> ("🧾 ${vigente.numero ?: nombreTipoSunat(vigente.tipo)}" +
            if (compacto) "" else if (vigente.estado == "accepted") " ✓" else " · ${etiquetaEstadoSunat(vigente.estado)}") to tonoEstadoSunat(vigente.estado)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (vigente == null && nc?.numero != null) {
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("↩ ${nc.numero}", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        val (fg, bg) = tono?.let { coloresTono(it) } ?: (c.navy to c.chipBg)
        Box(
            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
                .then(if (tono == null) Modifier.border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp)) else Modifier)
                .tocable { FacturacionSunat.abrir(origenTipo, origenId) }
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .semantics { contentDescription = if (vigente == null) "Emitir boleta o factura electrónica" else "Ver comprobante electrónico" },
        ) { Text(texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
