package pe.saniape.app.ui.clinica.retencion

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.daysUntil
import pe.saniape.app.data.staff.*
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.clinica.servicios.BotonContorno
import pe.saniape.app.ui.clinica.servicios.ChipFiltro
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.data.staff.formatearDinero

// ─────────────────────────── piezas comunes ───────────────────────────

@Composable
internal fun Pildora(texto: String, fg: Color, bg: Color) {
    Text(
        texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun BotonAccion(texto: String, fondo: Color, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(fondo).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/** 📞 Llamar + 💬 WhatsApp con intents nativos (tel: / wa.me). Sin teléfono: lo dice. */
@Composable
internal fun ContactoRet(tel: String?, acc: AccionesRet) {
    val c = Sania.colors
    val t = tel?.trim().orEmpty()
    if (t.isEmpty()) { Text("Sin teléfono", color = c.textoSuave, fontSize = 11.sp, fontStyle = FontStyle.Italic); return }
    BotonAccion("📞 Llamar", c.chipBg, c.navy) { acc.acciones.abrirUrl("tel:${t.filter { it.isDigit() }}") }
    enlaceWhatsAppSede(t)?.let { wa -> BotonAccion("💬 WhatsApp", c.okBg, c.ok) { acc.acciones.abrirUrl(wa) } }
}

@Composable
internal fun BotonIA(acc: AccionesRet, nombre: String, tel: String?, objetivo: String, detalle: String) {
    if (!acc.ctx.can("ia")) return
    BotonAccion("✨ Redactar", Sania.colors.purpleBg, Sania.colors.purple) { acc.onIA(nombre, tel, objetivo, detalle) }
}

@Composable
private fun TarjetaRet(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
        content = contenido,
    )
}

@Composable
private fun Cargando() = CargandoLista(filas = 5, conAvatar = false)

@Composable
private fun ErrorCarga(onReintentar: () -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No se pudo cargar la lista. Revisa tu conexión.", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        BotonAccion("Reintentar", c.navy, c.sobreNavy, onReintentar)
    }
}

@Composable
private fun ChipsFila(titulo: String, opciones: List<Pair<String, String>>, sel: String, onSel: (String) -> Unit) {
    val c = Sania.colors
    Text(titulo.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { (v, t) -> ChipFiltro(t, sel == v) { onSel(v) } }
    }
}

/** Texto "Última sesión" con el color del nivel de abandono. */
@Composable
private fun UltimaSesionTxt(r: FilaRetencion) {
    val c = Sania.colors
    val color = when (nivelAbandono(r)) { "critico" -> c.error; "probable" -> c.pend; else -> c.textoSuave }
    val t = if (r.ultimaSesion == null) "Sin sesiones · inicio ${haceDias(r.diasSin)}"
    else "Última ${fechaCortaRet(r.ultimaSesion)} · ${haceDias(r.diasSin)}"
    Text(t, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MarcaEstado(r: FilaRetencion) {
    val c = Sania.colors
    val nivel = nivelAbandono(r)
    when {
        r.noVolvio -> Pildora("No volvió", c.textoSuave, c.chipBg)
        nivel != null -> Pildora("Probable abandono", if (nivel == "critico") c.error else c.pend, if (nivel == "critico") c.errorBg else c.pendBg)
        r.estado != "Activo" -> Pildora(r.estado, c.textoSuave, c.chipBg)
    }
}

private fun cerrable(acc: AccionesRet, r: FilaRetencion): Boolean =
    acc.ctx.puede("sesiones") && nivelAbandono(r) != null && puedeMarcarNoVolvio(r.estado, r.modalidad, r.totalSesiones)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccionesFila(acc: AccionesRet, r: FilaRetencion, conAgendar: Boolean = true) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ContactoRet(r.telefono, acc)
        if (conAgendar && r.estado == "Activo" && r.proximaCita == null) BotonAccion("📅 Agendar", c.chipBg, c.navy) { acc.onAgendar(r) }
        if (cerrable(acc, r)) BotonAccion("No volvió", c.errorBg, c.error) { acc.onCerrar(r) }
    }
}

// ─────────────────────────── Para llamar ───────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabLlamar(
    acc: AccionesRet, resumen: ResumenRetencion?, puedeExportar: Boolean,
    filtrosIniciales: FiltrosLlamadas, onFiltros: (FiltrosLlamadas) -> Unit,
    busquedaInicial: String, onBusqueda: (String) -> Unit,
    kpis: @Composable () -> Unit, pie: @Composable () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val hoy = remember { hoyClinicaIso() }
    val opciones = resumen?.opciones
    var f by remember { mutableStateOf(filtrosIniciales) }
    var qTexto by remember { mutableStateOf(busquedaInicial) }
    // Se guardan arriba: al cambiar de pestaña o volver de la ficha todo sigue igual.
    LaunchedEffect(f) { onFiltros(f) }
    LaunchedEffect(qTexto) { onBusqueda(qTexto) }
    var mas by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<Pair<Int, List<FilaRetencion>>?>(null) }
    var error by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var reintento by remember { mutableStateOf(0) }
    var exportando by remember { mutableStateOf("") }
    var eligiendoFecha by remember { mutableStateOf<String?>(null) }

    // Búsqueda con respiro: no una consulta por tecla.
    LaunchedEffect(qTexto) {
        if (qTexto == f.q) return@LaunchedEffect
        delay(300)
        f = f.copy(q = qTexto, pagina = 1)
    }
    LaunchedEffect(f, acc.recarga, reintento) {
        cargando = true; error = false
        try {
            data = RetencionRepo.llamar(hoy, filtrosARpc(f), f.orden, f.asc, POR_PAGINA_RET, (f.pagina - 1) * POR_PAGINA_RET)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { if (data != null) Toaster.error("No se pudo actualizar la lista") else error = true }
        cargando = false
    }
    val total = data?.first ?: 0
    val filas = data?.second.orEmpty()
    val paginas = maxOf(1, (total + POR_PAGINA_RET - 1) / POR_PAGINA_RET)
    LaunchedEffect(total, f.pagina) { if (data != null && f.pagina > paginas) f = f.copy(pagina = paginas) }

    fun cambiar(nuevo: FiltrosLlamadas) { f = nuevo.copy(pagina = 1) }
    fun limpiar() { qTexto = ""; f = FiltrosLlamadas() }
    val preset = presetActivoRet(f)
    val nAvanz = contarFiltrosAvanzados(f)

    fun nombres() = Triple(
        opciones?.profesionales?.firstOrNull { it.id == f.profesional }?.nombre,
        opciones?.servicios?.firstOrNull { it.id == f.servicio }?.nombre,
        opciones?.especialidades?.firstOrNull { it.id == f.especialidad }?.nombre,
    )

    fun exportar(modo: String) {
        if (exportando.isNotEmpty()) return
        exportando = modo
        scope.launch {
            try {
                val (n, todas) = RetencionRepo.llamar(hoy, filtrosARpc(f), f.orden, f.asc, TOPE_EXPORTAR_RET, 0)
                val (pr, sv, es) = nombres()
                val desc = describirFiltros(f, pr, sv, es)
                if (modo == "excel") {
                    val filasCsv = buildList {
                        add(listOf("Lista de llamadas — ${acc.ctx.clinicaNombre}"))
                        add(listOf("Generada el ${fechaCortaRet(hoy)} · $n ${if (n == 1) "tratamiento" else "tratamientos"}"))
                        add(listOf("Filtros: ${desc.joinToString(" · ").ifEmpty { "ninguno" }}"))
                        add(emptyList())
                        add(COLUMNAS_EXPORTAR_RET)
                        todas.forEach { add(filaExportable(it)) }
                    }
                    acc.acciones.compartirArchivo("lista-llamadas-$hoy.csv", csvDe(filasCsv), "text/csv", "Lista de llamadas")
                } else {
                    acc.acciones.abrirHtml(htmlHojaLlamadas(acc.ctx.clinicaNombre, fechaCortaRet(hoy), desc, todas, n), "Lista de llamadas")
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
            catch (_: Exception) { Toaster.error("No se pudo exportar") }
            finally { exportando = "" }
        }
    }

    eligiendoFecha?.let { cual ->
        DialogoFecha(
            inicial = (if (cual == "desde") f.ultimaDesde else f.ultimaHasta).ifEmpty { null },
            onElegir = { d -> cambiar(if (cual == "desde") f.copy(ultimaDesde = d) else f.copy(ultimaHasta = d)) },
            onCerrar = { eligiendoFecha = null },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { kpis() }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESETS_RET.forEach { p ->
                    ChipFiltro(p.label, preset == p.id) { if (preset == p.id) limpiar() else { qTexto = ""; f = aplicarPresetRet(p.id) } }
                }
                if (hayFiltrosRet(f)) Text(
                    "Limpiar filtros", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { limpiar() }.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
        item { CampoBusqueda(qTexto, { qTexto = it }, "🔍 Nombre, DNI, teléfono o diagnóstico…") }
        item {
            ChipsFila(
                "Última sesión",
                listOf("" to "Cualquier fecha") + ATAJOS_DIAS.map { it.toString() to "Hace $it+ días" } + ("rango" to "Entre fechas…"),
                f.ultima,
            ) { cambiar(f.copy(ultima = it)) }
            if (f.ultima == "rango") {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { EtqForm("Desde"); CajaSelectorForm(f.ultimaDesde.ifEmpty { "Elegir…" }) { eligiendoFecha = "desde" } }
                    Column(Modifier.weight(1f)) { EtqForm("Hasta"); CajaSelectorForm(f.ultimaHasta.ifEmpty { "Elegir…" }) { eligiendoFecha = "hasta" } }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ChipFiltro(if (f.sinCita) "☑ Sin cita futura" else "☐ Sin cita futura", f.sinCita) { cambiar(f.copy(sinCita = !f.sinCita)) }
                ChipFiltro("Más filtros${if (nAvanz > 0) " ($nAvanz)" else ""} ${if (mas) "▴" else "▾"}", mas) { mas = !mas }
            }
        }
        if (mas) item {
            TarjetaRet {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoNumero("Sesión desde", f.sesionDesde, Modifier.weight(1f)) { cambiar(f.copy(sesionDesde = it)) }
                    CampoNumero("hasta", f.sesionHasta, Modifier.weight(1f)) { cambiar(f.copy(sesionHasta = it)) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoNumero("Le quedan desde", f.quedanMin, Modifier.weight(1f)) { cambiar(f.copy(quedanMin = it)) }
                    CampoNumero("hasta", f.quedanMax, Modifier.weight(1f)) { cambiar(f.copy(quedanMax = it)) }
                }
                Text("ESTADO DEL TRATAMIENTO", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ESTADOS_TRATAMIENTO.forEach { e ->
                        ChipFiltro(e, e in f.estados) {
                            val set = f.estados.toMutableSet().also { if (!it.add(e)) it.remove(e) }
                            cambiar(f.copy(estados = ESTADOS_TRATAMIENTO.filter { it in set }))
                        }
                    }
                    ChipFiltro("Todos", f.estados.isEmpty()) { cambiar(f.copy(estados = emptyList())) }
                }
                if (!acc.soloMios && (opciones?.profesionales?.size ?: 0) > 1) {
                    ChipsFila("Profesional", listOf("" to "Todos") + opciones!!.profesionales.map { it.id to it.nombre }, f.profesional) { cambiar(f.copy(profesional = it)) }
                }
                if ((opciones?.especialidades?.size ?: 0) > 1) {
                    ChipsFila("Especialidad", listOf("" to "Todas") + opciones!!.especialidades.map { it.id to it.nombre }, f.especialidad) { cambiar(f.copy(especialidad = it)) }
                }
                if ((opciones?.servicios?.size ?: 0) > 1) {
                    ChipsFila("Servicio", listOf("" to "Todos") + opciones!!.servicios.map { it.id to it.nombre }, f.servicio) { cambiar(f.copy(servicio = it)) }
                }
                ChipsFila(
                    "Modalidad",
                    listOf("" to "Paquete o sueltas") + MODALIDADES_RET.filter { opciones == null || it in opciones.modalidades }.map { it to it },
                    f.modalidad,
                ) { cambiar(f.copy(modalidad = it)) }
                ChipsFila("Saldo", listOf("" to "Con o sin saldo", "si" to "Con saldo pendiente", "no" to "Sin saldo pendiente"), f.saldo) { cambiar(f.copy(saldo = it)) }
            }
        }
        item {
            ChipsFila(
                "Ordenar por",
                OrdenRet.entries.filter { it != OrdenRet.CONTROL }.map { it.clave to (it.etiqueta + if (f.orden == it) (if (f.asc) " ▲" else " ▼") else "") },
                f.orden.clave,
            ) { clave ->
                val o = OrdenRet.entries.first { it.clave == clave }
                f = if (f.orden == o) f.copy(asc = !f.asc, pagina = 1) else f.copy(orden = o, asc = o.asc1, pagina = 1)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (data != null) "$total ${if (total == 1) "tratamiento" else "tratamientos"}${if (cargando) " · Actualizando…" else ""}" else "Buscando…",
                    color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                if (puedeExportar) {
                    BotonContorno(if (exportando == "excel") "Exportando…" else "⬇️ Excel", total > 0 && exportando.isEmpty()) { exportar("excel") }
                    BotonContorno(if (exportando == "pdf") "Preparando…" else "🖨️ PDF", total > 0 && exportando.isEmpty()) { exportar("pdf") }
                }
            }
        }
        when {
            error && data == null -> item { ErrorCarga { reintento++ } }
            cargando && data == null -> item { Cargando() }
            filas.isEmpty() -> item {
                EstadoVacio("🔎", "Ningún ${LocalTerminologiaPaciente.current.paciente} coincide con estos filtros", null,
                    if (hayFiltrosRet(f)) "Limpiar filtros" else null, if (hayFiltrosRet(f)) ({ limpiar() }) else null)
            }
            else -> {
                items(filas, key = { it.tratamientoId }) { r -> Box(Modifier.alpha(if (cargando) 0.6f else 1f)) { FilaLlamar(r, acc) } }
                if (paginas > 1) item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        BotonContorno("← Anterior", f.pagina > 1) { f = f.copy(pagina = f.pagina - 1) }
                        Text("${f.pagina} / $paginas", color = c.textoSuave, fontSize = 12.sp)
                        BotonContorno("Siguiente →", f.pagina < paginas) { f = f.copy(pagina = f.pagina + 1) }
                    }
                }
            }
        }
        item { pie() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CampoNumero(etiqueta: String, valor: Int?, modifier: Modifier, onCambio: (Int?) -> Unit) {
    OutlinedTextField(
        value = valor?.toString().orEmpty(),
        onValueChange = { t -> val d = t.filter { it.isDigit() }.take(3); onCambio(d.toIntOrNull()) },
        label = { Text(etiqueta, fontSize = 11.sp) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = coloresCampoForm(), modifier = modifier,
    )
}

@Composable
private fun FilaLlamar(r: FilaRetencion, acc: AccionesRet) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    TarjetaRet(Modifier.clickable { acc.onFicha(r.pacienteId) }) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(r.nombre, color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(r.servicio ?: "—", r.profesional.takeIf { !acc.soloMios }, r.modalidad).joinToString(" · "),
                    color = c.textoSuave, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                (r.diagnostico ?: r.motivo)?.let { Text(it, color = c.texto, fontSize = 12.sp, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(textoSesionRet(r), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (r.totalSesiones > 0) Text(if (r.quedan > 0) "quedan ${r.quedan}" else "completo", color = c.textoSuave, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        UltimaSesionTxt(r)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.proximaCita != null) Text("Cita ${fechaCortaRet(r.proximaCita)}${r.proximaHora?.let { " " + it.take(5) }.orEmpty()}", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (r.saldo > 0.005) Text("Debe ${formatearDinero(r.saldo, moneda)}", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            MarcaEstado(r)
        }
        Spacer(Modifier.height(6.dp))
        AccionesFila(acc, r)
    }
}

// ─────────────────────────── No vuelven ───────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabNoVuelven(acc: AccionesRet, resumen: ResumenRetencion?, kpis: @Composable () -> Unit, pie: @Composable () -> Unit) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val hoy = remember { hoyClinicaIso() }
    var data by remember { mutableStateOf<List<FilaRetencion>?>(null) }
    var error by remember { mutableStateOf(false) }
    var reintento by remember { mutableStateOf(0) }
    var filtroPago by remember { mutableStateOf("todos") }
    var filtroProf by remember { mutableStateOf("") }
    var busqueda by remember { mutableStateOf("") }

    LaunchedEffect(acc.recarga, reintento) {
        error = false
        try { data = RetencionRepo.llamar(hoy, rpcNoVuelven(), OrdenRet.DIAS, false, TOPE_EXPORTAR_RET, 0).second }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { if (data != null) Toaster.error("No se pudo actualizar la lista") else error = true }
    }
    val lista = remember(data, filtroPago, filtroProf, busqueda) {
        val q = busqueda.trim().lowercase()
        priorizarNoVuelven((data ?: emptyList()).filter { a ->
            val saldo = maxOf(0.0, a.saldo)
            when (filtroPago) {
                "debe" -> if (saldo <= 0.005) return@filter false
                "pago_todo" -> if (saldo > 0.005) return@filter false
                "parcial" -> if (!(saldo > 0.005 && a.pagado > 0.005)) return@filter false
            }
            if (filtroProf.isNotEmpty() && a.terapeutaId != filtroProf) return@filter false
            q.isEmpty() || a.nombre.lowercase().contains(q)
        })
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { kpis() }
        item {
            Text(
                "Les quedan sesiones, no tienen cita y hace $DIAS_NO_VUELVE+ días que no vienen. Primero quienes más deben o ya pagaron sin venir.",
                color = c.textoSuave, fontSize = 12.sp,
            )
        }
        when {
            error && data == null -> item { ErrorCarga { reintento++ } }
            data == null -> item { Cargando() }
            data!!.isEmpty() -> item { EstadoVacio("🎉", "No hay ${LocalTerminologiaPaciente.current.pacientes} pendientes de recontactar") }
            else -> {
                item {
                    CampoBusqueda(busqueda, { busqueda = it }, "🔍 Buscar ${LocalTerminologiaPaciente.current.paciente}…")
                    ChipsFila("Pago", listOf("todos" to "Todos los pagos", "debe" to "Con saldo pendiente", "parcial" to "Pago parcial", "pago_todo" to "Pagó todo"), filtroPago) { filtroPago = it }
                    val profs = resumen?.opciones?.profesionales.orEmpty()
                    if (!acc.soloMios && profs.size > 1) ChipsFila("Profesional", listOf("" to "Todos") + profs.map { it.id to it.nombre }, filtroProf) { filtroProf = it }
                    Text("${lista.size} ${if (lista.size != 1) LocalTerminologiaPaciente.current.pacientes else LocalTerminologiaPaciente.current.paciente}", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                if (lista.isEmpty()) item { Text("Ningún ${LocalTerminologiaPaciente.current.paciente} coincide con los filtros.", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
                items(lista, key = { it.fila.tratamientoId }) { a ->
                    val r = a.fila
                    val nivel = nivelAbandono(r)
                    TarjetaRet {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(50)).background(when (a.prioridad) { "alta" -> c.error; "media" -> c.pend; else -> c.textoSuave }))
                            Text(r.nombre, color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { acc.onFicha(r.pacienteId) })
                            if (nivel != null) Pildora("Probable abandono", if (nivel == "critico") c.error else c.pend, if (nivel == "critico") c.errorBg else c.pendBg)
                        }
                        Text(
                            listOfNotNull(r.servicio, "sesión ${textoSesionRet(r)}").joinToString(" · "),
                            color = c.textoSuave, fontSize = 12.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("hace ${r.diasSin ?: 0} días", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (a.situacion == SituacionPago.PAGO_TODO_NO_VOLVIO) Text("pagó todo", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            else Text("debe ${formatearDinero(maxOf(0.0, r.saldo), moneda)}${if (r.pagado > 0.005) " (pagó ${formatearDinero(r.pagado, moneda)})" else ""}", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ContactoRet(r.telefono, acc)
                            BotonIA(acc, r.nombre, r.telefono, "reactivar", "Lleva ${r.diasSin ?: 0} días sin venir a la clínica.")
                            BotonAccion("📅 Agendar", c.navy, c.sobreNavy) { acc.onAgendar(r) }
                            if (cerrable(acc, r)) BotonAccion("No volvió", c.errorBg, c.error) { acc.onCerrar(r) }
                        }
                    }
                }
            }
        }
        item { pie() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────── Controles ───────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabControles(acc: AccionesRet, kpis: @Composable () -> Unit, pie: @Composable () -> Unit) {
    val c = Sania.colors
    val hoy = remember { hoyClinicaIso() }
    var data by remember { mutableStateOf<List<FilaRetencion>?>(null) }
    var error by remember { mutableStateOf(false) }
    var reintento by remember { mutableStateOf(0) }
    LaunchedEffect(acc.recarga, reintento) {
        error = false
        try { data = RetencionRepo.llamar(hoy, rpcControles(), OrdenRet.CONTROL, true, TOPE_EXPORTAR_RET, 0).second }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { if (data != null) Toaster.error("No se pudo actualizar la lista") else error = true }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { kpis() }
        item { Text("Controles programados que ya vencieron (o son hoy) y todavía no tienen cita.", color = c.textoSuave, fontSize = 12.sp) }
        when {
            error && data == null -> item { ErrorCarga { reintento++ } }
            data == null -> item { Cargando() }
            data!!.isEmpty() -> item { EstadoVacio("✅", "No hay controles pendientes de agendar") }
            else -> items(data!!, key = { it.tratamientoId }) { r ->
                val pc = r.proximoControl.orEmpty()
                val vencido = pc.take(10) < hoy
                TarjetaRet {
                    Text(r.nombre, color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { acc.onFicha(r.pacienteId) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Control: ${fechaCortaRet(pc)}${if (vencido) " (vencido)" else " (hoy)"}", color = if (vencido) c.error else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        r.servicio?.let { Text("· $it", color = c.textoSuave, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ContactoRet(r.telefono, acc)
                        BotonIA(acc, r.nombre, r.telefono, "recordar_control", "Tiene un control pendiente / vencido.")
                        BotonAccion("📅 Agendar control", c.tealBg, c.teal) { acc.onAgendarControl(r) }
                    }
                }
            }
        }
        item { pie() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────── Nunca empezaron ───────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabNuncaEmpezaron(acc: AccionesRet, kpis: @Composable () -> Unit, pie: @Composable () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val hoy = remember { hoyClinicaIso() }
    var data by remember { mutableStateOf<List<NuncaEmpezo>?>(null) }
    var error by remember { mutableStateOf(false) }
    var reintento by remember { mutableStateOf(0) }
    var embudo by remember { mutableStateOf("todos") }
    var desde by remember { mutableStateOf("") }
    var hasta by remember { mutableStateOf("") }
    var verDescartados by remember { mutableStateOf(false) }
    var busqueda by remember { mutableStateOf("") }
    var descartando by remember { mutableStateOf<String?>(null) }
    var pidiendoMotivo by remember { mutableStateOf<NuncaEmpezo?>(null) }
    var motivo by remember { mutableStateOf("Solo vino a preguntar") }
    var eligiendoFecha by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(acc.recarga, reintento) {
        error = false
        try { data = RetencionRepo.nuncaEmpezaron(hoy) }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { if (data != null) Toaster.error("No se pudo actualizar la lista") else error = true }
    }
    val items = data.orEmpty()
    val filtrados = remember(data, embudo, desde, hasta, verDescartados, busqueda) {
        val q = busqueda.trim().lowercase()
        items.filter { p ->
            if (!verDescartados && p.descartado) return@filter false
            if (embudo == "sin_nada" && p.hizoCita) return@filter false
            if (embudo == "no_siguio" && !p.hizoCita) return@filter false
            val f = p.fechaIngreso?.take(10).orEmpty()
            if (desde.isNotEmpty() && (f.isEmpty() || f < desde)) return@filter false
            if (hasta.isNotEmpty() && (f.isEmpty() || f > hasta)) return@filter false
            q.isEmpty() || p.nombre.lowercase().contains(q)
        }
    }
    val activos = items.filter { !it.descartado }
    val nSinNada = activos.count { !it.hizoCita }
    val nNoSiguio = activos.count { it.hizoCita }
    val nDescartados = items.count { it.descartado }

    fun aplicar(p: NuncaEmpezo, descartar: Boolean, m: String) {
        if (descartando != null) return
        descartando = p.id
        scope.launch {
            try {
                val r = RetencionRepo.descartar(p.id, descartar, m)
                if (r.registrada) {
                    data = data?.map { if (it.id == p.id) it.copy(descartado = descartar, descartadoMotivo = if (descartar) m.ifBlank { null } else null) else it }
                    Toaster.exito(if (descartar) "${p.nombre} descartado" else "${p.nombre} recuperado a la lista")
                } else Toaster.error(r.rechazo?.error ?: "No se pudo actualizar")
            } finally { descartando = null }
        }
    }

    fun rangoPeriodo(cual: String): Pair<String, String> {
        val h = LocalDateRet.parse(hoy)
        val ini = if (cual == "este_mes") h.primerDiaMes() else h.primerDiaMes().restarMes()
        return ini.iso() to ini.ultimoDiaMes().iso()
    }
    fun periodo(cual: String) {
        if (cual == "todo") { desde = ""; hasta = ""; return }
        val (d, h) = rangoPeriodo(cual)
        desde = d; hasta = h
    }
    fun enPeriodo(cual: String) = (desde to hasta) == rangoPeriodo(cual)

    fun exportar(html: Boolean) {
        if (html) {
            val filas = filtrados.joinToString("") { p ->
                "<tr><td>${esc(p.nombre)}</td><td>${esc(p.telefono ?: "")}</td><td>${esc(p.fechaIngreso ?: "")}</td><td>${p.diasDesdeIngreso}</td>" +
                    "<td>${if (p.hizoCita) "Hizo cita, no siguió" else "Registrado sin nada"}</td><td>${esc(if (p.descartado) p.descartadoMotivo ?: "" else p.diagnostico ?: "")}</td></tr>"
            }
            acc.acciones.abrirHtml(
                "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Nunca empezaron</title>" +
                    "<style>body{font-family:Arial,sans-serif;color:#1e2d5e;font-size:12px}table{width:100%;border-collapse:collapse}th,td{border-bottom:1px solid #dde1f0;padding:5px;text-align:left}</style></head><body>" +
                    "<h2>${acc.ctx.terminologiaPaciente.Pacientes} que nunca empezaron — ${esc(acc.ctx.clinicaNombre)}</h2><p>${filtrados.size} ${acc.ctx.terminologiaPaciente.pacientes} · generado $hoy</p>" +
                    "<table><tr><th>Nombre</th><th>Teléfono</th><th>Ingreso</th><th>Días</th><th>Estado</th><th>Motivo</th></tr>$filas</table></body></html>",
                "Nunca empezaron",
            )
        } else {
            val cab = listOf("Nombre", "Teléfono", "Fecha ingreso", "Días", "Estado embudo", "Motivo")
            val filas = filtrados.map { p ->
                val et = if (p.hizoCita) "Hizo cita, no siguió" else "Registrado sin nada"
                listOf(p.nombre, p.telefono.orEmpty(), p.fechaIngreso.orEmpty(), p.diasDesdeIngreso.toString(),
                    if (p.descartado) "Descartado ($et)" else et, if (p.descartado) p.descartadoMotivo.orEmpty() else p.diagnostico.orEmpty())
            }
            acc.acciones.compartirArchivo("nunca-empezaron-$hoy.csv", csvDe(listOf(cab) + filas), "text/csv", "Nunca empezaron")
        }
    }

    eligiendoFecha?.let { cual ->
        DialogoFecha(
            inicial = (if (cual == "desde") desde else hasta).ifEmpty { null },
            onElegir = { d -> if (cual == "desde") desde = d else hasta = d },
            onCerrar = { eligiendoFecha = null },
        )
    }
    pidiendoMotivo?.let { p ->
        DialogoForm(
            titulo = "Descartar", subtitulo = p.nombre, textoAccion = "Descartar",
            onCancelar = { pidiendoMotivo = null },
            onAccion = { aplicar(p, true, motivo.trim()); pidiendoMotivo = null },
        ) {
            EtqForm("Motivo (opcional)")
            OutlinedTextField(
                value = motivo, onValueChange = { motivo = it.take(200) }, singleLine = true,
                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { kpis() }
        item { Text("Se registraron pero nunca empezaron un tratamiento — los más antiguos primero. Descarta a quienes solo vinieron a preguntar.", color = c.textoSuave, fontSize = 12.sp) }
        when {
            error && data == null -> item { ErrorCarga { reintento++ } }
            data == null -> item { Cargando() }
            else -> {
                item {
                    ChipsFila("Embudo", listOf(
                        "todos" to "Todos (${activos.size})", "sin_nada" to "Registrado sin nada ($nSinNada)", "no_siguio" to "Hizo cita, no siguió ($nNoSiguio)",
                    ), embudo) { embudo = it }
                    Spacer(Modifier.height(6.dp))
                    CampoBusqueda(busqueda, { busqueda = it }, "🔍 Buscar ${LocalTerminologiaPaciente.current.paciente}…")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { EtqForm("Registrado desde"); CajaSelectorForm(desde.ifEmpty { "Elegir…" }) { eligiendoFecha = "desde" } }
                        Column(Modifier.weight(1f)) { EtqForm("Hasta"); CajaSelectorForm(hasta.ifEmpty { "Elegir…" }) { eligiendoFecha = "hasta" } }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChipFiltro("Este mes", enPeriodo("este_mes")) { periodo("este_mes") }
                        ChipFiltro("Mes pasado", enPeriodo("mes_pasado")) { periodo("mes_pasado") }
                        ChipFiltro("Todo", desde.isEmpty() && hasta.isEmpty()) { periodo("todo") }
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipFiltro((if (verDescartados) "☑" else "☐") + " Ver descartados" + if (nDescartados > 0) " ($nDescartados)" else "", verDescartados) { verDescartados = !verDescartados }
                        Text("${filtrados.size} ${if (filtrados.size != 1) LocalTerminologiaPaciente.current.pacientes else LocalTerminologiaPaciente.current.paciente}", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    if (acc.ctx.esGestor) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonContorno("⬇️ Excel", filtrados.isNotEmpty()) { exportar(false) }
                        BotonContorno("🖨️ PDF", filtrados.isNotEmpty()) { exportar(true) }
                    }
                }
                if (filtrados.isEmpty()) item { EstadoVacio("🎉", "No hay ${LocalTerminologiaPaciente.current.pacientes} en esta lista con los filtros aplicados.") }
                items(filtrados, key = { it.id }) { p ->
                    TarjetaRet(Modifier.alpha(if (p.descartado) 0.6f else 1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(p.nombre, color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, false).clickable { acc.onFicha(p.id) })
                            if (p.hizoCita) Pildora("Hizo cita, no siguió", c.pend, c.pendBg) else Pildora("Registrado sin nada", c.navy, c.chipBg)
                            if (p.descartado) Pildora("Descartado", c.textoSuave, c.chipBg)
                        }
                        Text(
                            (if (p.fechaIngreso != null) "Registrado ${p.fechaIngreso}${if (p.diasDesdeIngreso > 0) " · hace ${p.diasDesdeIngreso} días" else ""}" else "Sin fecha de ingreso") +
                                if (p.descartado) (p.descartadoMotivo?.let { " · motivo: $it" } ?: " · descartado") else (p.diagnostico?.let { " · $it" }.orEmpty()),
                            color = c.textoSuave, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ContactoRet(p.telefono, acc)
                            BotonAccion(if (p.descartado) "↩ Recuperar" else "✕ Descartar", c.chipBg, c.textoSuave) {
                                if (descartando == null) { if (p.descartado) aplicar(p, false, "") else { motivo = "Solo vino a preguntar"; pidiendoMotivo = p } }
                            }
                        }
                    }
                }
            }
        }
        item { pie() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Fechas AAAA-MM-DD mínimas para "este mes / mes pasado". */
private class LocalDateRet(val y: Int, val m: Int, val d: Int) {
    companion object { fun parse(s: String) = s.take(10).split("-").let { LocalDateRet(it[0].toInt(), it[1].toInt(), it[2].toInt()) } }
    fun primerDiaMes() = LocalDateRet(y, m, 1)
    fun restarMes() = if (m == 1) LocalDateRet(y - 1, 12, 1) else LocalDateRet(y, m - 1, 1)
    fun ultimoDiaMes(): LocalDateRet {
        val dias = when (m) { 2 -> if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
        return LocalDateRet(y, m, dias)
    }
    fun iso() = "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

// ─────────────────────────── Dental ───────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabPresupuestos(acc: AccionesRet, kpis: @Composable () -> Unit, pie: @Composable () -> Unit, onConteo: (Int) -> Unit) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val hoy = remember { hoyClinicaIso() }
    var data by remember { mutableStateOf<Pair<List<PresupuestoPendiente>, List<PresupuestoAceptado>>?>(null) }
    var error by remember { mutableStateOf(false) }
    var reintento by remember { mutableStateOf(0) }
    LaunchedEffect(acc.recarga, reintento) {
        error = false
        try { data = RetencionRepo.presupuestos(acc.ctx.mapaDental).also { onConteo(it.first.size) } }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { if (data != null) Toaster.error("No se pudo actualizar la lista") else error = true }
    }
    val filas = data?.first.orEmpty()
    val aceptados = data?.second.orEmpty()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { kpis() }
        item {
            val total = filas.sumOf { it.suma }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🦷 Presupuestos sin aceptar", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (total > 0) Text("${formatearDinero(total, moneda)} por cerrar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("${LocalTerminologiaPaciente.current.Pacientes} con piezas por tratar en el odontograma que todavía no tienen tratamiento. Llámalos o escríbeles para retomar.", color = c.textoSuave, fontSize = 12.sp)
        }
        when {
            error && data == null -> item { ErrorCarga { reintento++ } }
            data == null -> item { Cargando() }
            filas.isEmpty() -> item { EstadoVacio("🎉", "Todos los presupuestos se convirtieron en tratamiento") }
            else -> items(filas, key = { it.pacienteId }) { f ->
                val dias = diasDesde(f.desde, hoy)
                TarjetaRet {
                    Text(f.nombre, color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { acc.onFicha(f.pacienteId) })
                    Text("Evaluado ${if (dias <= 0) "hoy" else "hace $dias día${if (dias == 1) "" else "s"}"} · ${fechaCortaRet(f.desde)}", color = c.textoSuave, fontSize = 12.sp)
                    Text(f.resumen, color = c.texto, fontSize = 13.sp)
                    f.enlaceEnviado?.let { Text("🔗 Enlace enviado el ${fechaHoraLima(it)}", color = c.textoSuave, fontSize = 11.sp) }
                    if (f.suma > 0) Text(formatearDinero(f.suma, moneda), color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val msg = "Hola ${f.nombre.split(" ").first()}, te escribimos de la clínica sobre el presupuesto de tu tratamiento dental. ¿Pudiste revisarlo? Podemos agendarte cuando te acomode."
                        val t = f.telefono?.trim().orEmpty()
                        if (t.isEmpty()) Text("Sin teléfono", color = c.textoSuave, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                        else {
                            BotonAccion("📞 Llamar", c.chipBg, c.navy) { acc.acciones.abrirUrl("tel:${t.filter { it.isDigit() }}") }
                            enlaceWhatsAppSede(t, msg)?.let { wa -> BotonAccion("💬 WhatsApp", c.okBg, c.ok) { acc.acciones.abrirUrl(wa) } }
                        }
                    }
                }
            }
        }
        if (aceptados.isNotEmpty()) {
            item { Text("✓ Aceptados por el ${LocalTerminologiaPaciente.current.paciente} · falta crear el tratamiento", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(aceptados, key = { "a-" + it.pacienteId }) { a ->
                TarjetaRet(Modifier.clickable { acc.onFicha(a.pacienteId) }) {
                    Text(a.nombre, color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Aceptado el ${fechaHoraLima(a.aceptadoAt)}${a.total?.let { " · ${formatearDinero(it, moneda)}" }.orEmpty()}", color = c.textoSuave, fontSize = 12.sp)
                }
            }
        }
        item { pie() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun diasDesde(fecha: String, hoy: String): Int =
    runCatching { kotlinx.datetime.LocalDate.parse(fecha.take(10)).daysUntil(kotlinx.datetime.LocalDate.parse(hoy.take(10))) }.getOrDefault(0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabCuotas(acc: AccionesRet, kpis: @Composable () -> Unit, pie: @Composable () -> Unit, onConteo: (Int) -> Unit) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val hoy = remember { hoyClinicaIso() }
    var data by remember { mutableStateOf<List<CuotaPorCobrar>?>(null) }
    var error by remember { mutableStateOf(false) }
    var reintento by remember { mutableStateOf(0) }
    LaunchedEffect(acc.recarga, reintento) {
        error = false
        try { data = RetencionRepo.cuotas(hoy).also { onConteo(it.size) } }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { if (data != null) Toaster.error("No se pudo actualizar la lista") else error = true }
    }
    val filas = data.orEmpty()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { kpis() }
        item {
            val atrasado = filas.filter { it.cuota.estado == EstadoCuota.ATRASADA }.sumOf { it.cuota.saldo }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅 Cuotas por cobrar", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (atrasado > 0) Text("${formatearDinero(atrasado, moneda)} atrasado", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("Cuotas atrasadas y las que vencen en los próximos 7 días. Escríbele al ${LocalTerminologiaPaciente.current.paciente} para recordarle.", color = c.textoSuave, fontSize = 12.sp)
        }
        when {
            error && data == null -> item { ErrorCarga { reintento++ } }
            data == null -> item { Cargando() }
            filas.isEmpty() -> item { EstadoVacio("🎉", "No hay cuotas atrasadas ni por vencer esta semana") }
            else -> items(filas, key = { "${it.pacienteId}-${it.cuota.numero}-${it.cuota.vence}" }) { f ->
                val q = f.cuota
                val cual = if (q.numero == 0) "la cuota inicial" else "la cuota ${q.numero} de ${f.nCuotas}"
                val cuando = when (q.estado) {
                    EstadoCuota.ATRASADA -> "venció el ${fechaCortaRet(q.vence)}"
                    EstadoCuota.VENCE_HOY -> "vence hoy"
                    else -> "vence el ${fechaCortaRet(q.vence)}"
                }
                TarjetaRet {
                    Text(f.nombre, color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { acc.onFicha(f.pacienteId) })
                    Text(f.servicio, color = c.textoSuave, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(etiquetaCuota(q.numero, f.nCuotas), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("vence ${fechaCortaRet(q.vence)}", color = c.textoSuave, fontSize = 13.sp)
                        Text(formatearDinero(q.saldo, moneda), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(textoEstadoCuota(q), color = if (q.estado == EstadoCuota.ATRASADA) c.error else c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val t = f.telefono?.trim().orEmpty()
                        if (t.isEmpty()) Text("Sin teléfono", color = c.textoSuave, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                        else {
                            val msg = "Hola ${f.nombre.split(" ").first()}, te escribimos de la clínica para recordarte que $cual de tu tratamiento (${f.servicio}), por ${formatearDinero(q.saldo, moneda)}, $cuando. ¡Gracias!"
                            BotonAccion("📞 Llamar", c.chipBg, c.navy) { acc.acciones.abrirUrl("tel:${t.filter { it.isDigit() }}") }
                            enlaceWhatsAppSede(t, msg)?.let { wa -> BotonAccion("💬 WhatsApp", c.okBg, c.ok) { acc.acciones.abrirUrl(wa) } }
                        }
                    }
                }
            }
        }
        item { pie() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────── Satisfacción del equipo ───────────────────────────

/** Cómo califican a cada persona del equipo. NO es un ranking: orden alfabético, sin podio. */
@Composable
internal fun PanelSatisfaccion(filas: List<SatisfaccionPersona>) {
    val c = Sania.colors
    // Sin respuestas todavía no se pinta una sección vacía.
    if (filas.isEmpty()) return
    TarjetaRet(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⭐ Cómo califican a tu equipo", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Text("Al terminar cada tratamiento", color = c.textoSuave, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        filas.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    f.quejaTop?.takeIf { it.second > 1 }?.let { Text("Lo más repetido: ${it.first.lowercase()} (${it.second})", color = c.pend, fontSize = 11.sp) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${f.promedio} ★", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${f.total} ${if (f.total == 1) "respuesta" else "respuestas"}", color = c.textoSuave, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─────────────────────────── Redactar con IA ───────────────────────────

class PedidoIA(val nombre: String, val telefono: String?, val objetivo: String, val detalle: String)

@Composable
internal fun DialogoRedactarIA(p: PedidoIA, acciones: pe.saniape.app.ui.AccionesNativas, onCerrar: () -> Unit) {
    val c = Sania.colors
    var texto by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(true) }
    var reintento by remember { mutableStateOf(0) }
    LaunchedEffect(p, reintento) {
        cargando = true
        texto = try { RetencionRepo.redactarMensaje(p.objetivo, p.nombre, p.detalle) }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (e: Exception) { (e.message ?: "No se pudo generar el mensaje.").let { m -> if (m.startsWith("⚠")) m else "⚠ $m" } }
        cargando = false
    }
    val wa = enlaceWhatsAppSede(p.telefono, texto)
    DialogoForm(
        titulo = "✨ Mensaje sugerido", subtitulo = p.nombre,
        textoAccion = if (wa != null) "Abrir WhatsApp" else "Copiar",
        accionHabilitada = !cargando && texto.isNotBlank() && !texto.startsWith("⚠"),
        onCancelar = onCerrar,
        onAccion = {
            if (wa != null) acciones.abrirUrl(wa) else acciones.copiarTexto(texto, "Mensaje")
            onCerrar()
        },
    ) {
        if (cargando) Text("Redactando…", color = c.textoSuave, fontSize = 13.sp)
        else {
            OutlinedTextField(
                value = texto, onValueChange = { texto = it }, colors = coloresCampoForm(),
                modifier = Modifier.fillMaxWidth(), minLines = 4,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonContorno("Copiar") { acciones.copiarTexto(texto, "Mensaje") }
                BotonContorno("↻ Otra versión") { reintento++ }
            }
            Text("Revisa el mensaje antes de enviarlo: la IA solo redacta, no contacta al ${LocalTerminologiaPaciente.current.paciente}.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
