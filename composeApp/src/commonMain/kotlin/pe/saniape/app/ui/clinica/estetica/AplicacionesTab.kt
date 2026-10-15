package pe.saniape.app.ui.clinica.estetica

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.staff.Aplicacion
import pe.saniape.app.data.staff.AplicacionEstetica
import pe.saniape.app.data.staff.AplicacionesEsteticasRepo
import pe.saniape.app.data.staff.BorradorAplicacion
import pe.saniape.app.data.staff.CitaParaMapa
import pe.saniape.app.data.staff.CrudoAplicacion
import pe.saniape.app.data.staff.LimitesAplicacion
import pe.saniape.app.data.staff.MapaEstetica
import pe.saniape.app.data.staff.PROFUNDIDADES
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.ProductoReciente
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.RostroGeometria
import pe.saniape.app.data.staff.TECNICAS_APLICACION
import pe.saniape.app.data.staff.TIPOS_PRODUCTO
import pe.saniape.app.data.staff.TratamientoParaMapa
import pe.saniape.app.data.staff.VISTAS_MAPA
import pe.saniape.app.data.staff.aplicacionEsteticaDeJson
import pe.saniape.app.data.staff.avisoRiesgoVascular
import pe.saniape.app.data.staff.calcularTotales
import pe.saniape.app.data.staff.chipsProducto
import pe.saniape.app.data.staff.copiarParaRepetir
import pe.saniape.app.data.staff.dosisSugeridas
import pe.saniape.app.data.staff.evaluarDosis
import pe.saniape.app.data.staff.fechaIndicacion
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.htmlRegistroAplicacion
import pe.saniape.app.data.staff.inferirTipoProducto
import pe.saniape.app.data.staff.leerNumeroDosis
import pe.saniape.app.data.staff.normalizarAplicacion
import pe.saniape.app.data.staff.numeroDosis
import pe.saniape.app.data.staff.ofertaUnidadesCobradas
import pe.saniape.app.data.staff.ordenarAplicaciones
import pe.saniape.app.data.staff.plegarProducto
import pe.saniape.app.data.staff.profundidadPorDefecto
import pe.saniape.app.data.staff.rangoTipico
import pe.saniape.app.data.staff.servicioParaIndicaciones
import pe.saniape.app.data.staff.textoDosis
import pe.saniape.app.data.staff.textoTotales
import pe.saniape.app.data.staff.tipoPorZona
import pe.saniape.app.data.staff.tipoProducto
import pe.saniape.app.data.staff.totalesPorZona
import pe.saniape.app.data.staff.ultimaToxina
import pe.saniape.app.data.staff.unidadPorDefecto
import pe.saniape.app.data.staff.validarAplicacionEstetica
import pe.saniape.app.data.staff.vencidoEn
import pe.saniape.app.data.staff.vistaDeZona
import pe.saniape.app.data.staff.zonaFacial
import pe.saniape.app.data.staff.zonasDeVista
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.mapas.AvisoMapa
import pe.saniape.app.ui.clinica.mapas.BotonMapa
import pe.saniape.app.ui.clinica.mapas.CajaErroresMapa
import pe.saniape.app.ui.clinica.mapas.ChipMapa
import pe.saniape.app.ui.clinica.mapas.EtiquetaMapa
import pe.saniape.app.ui.clinica.mapas.FilaBotonesMapa
import pe.saniape.app.ui.clinica.mapas.PildoraMapa
import pe.saniape.app.ui.clinica.mapas.SegmentadoMapa
import pe.saniape.app.ui.clinica.mapas.TarjetaMapa
import pe.saniape.app.ui.clinica.mapas.colorHex
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/** El editor: un mapa nuevo (opcionalmente partiendo de uno anterior) o el que se edita. */
private sealed interface EditorAp {
    data class Nuevo(val inicial: List<Aplicacion>? = null, val desde: String? = null) : EditorAp
    data class Editar(val id: String) : EditorAp
}

/**
 * Pestaña 💉 Aplicaciones de la ficha (solo estética: `pacienteEsEstetica`).
 * Gemelo de `AplicacionesTab.tsx` de la web: el MAPA DE APLICACIÓN de cada
 * sesión — zona, producto, dosis, puntos, técnica, profundidad, lote y vencimiento.
 *
 *  · Editor: la atención (cita / tratamiento / quién aplicó / fecha), el rostro
 *    (frente y perfiles) con zonas tocables o "Elegir la zona de una lista", el
 *    formulario de la zona (chips de producto: los de la clínica primero; dosis
 *    típicas; último lote usado) y los totales. Rangos orientativos: avisan,
 *    nunca bloquean. Un mapa por cita (409 → se abre el que ya existe).
 *  · "Repetir la sesión anterior" y el aviso del tiempo desde la última toxina.
 *  · Al guardar: 🖨 Registro de aplicación (hoja nativa), 📝 Dejar indicaciones
 *    y, si el tratamiento cobra por unidades, abrir SU formulario con la
 *    cantidad pre-llenada (el dinero no se toca acá).
 *
 * Carga SOLO cuando la pestaña se abre. Sin listas perezosas: vive dentro del
 * `verticalScroll` de la ficha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AplicacionesTab(
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    /** `puede('pacientes') || puede('sesiones')` y ficha no dada de baja (como el API). */
    puedeEditar: Boolean,
    mapaEstetica: MapaEstetica,
    /** Los tratamientos de estética del paciente (vincular el mapa y el cobro por unidades). */
    tratamientos: List<TratamientoParaMapa>,
    /** procedimientoId → nombre (de sus tratamientos): el servicio de las citas sin tratamiento. */
    nombresServicio: Map<String, String>,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    clinicaId: String?,
    /** 📝 Abre Indicaciones con la plantilla base del servicio ("Toxina botulínica"). */
    onDejarIndicaciones: ((servicio: String?, citaId: String?) -> Unit)? = null,
    /** Abre el formulario del tratamiento con la cantidad pre-llenada (el usuario confirma). */
    onUsarUnidades: ((tratamientoId: String, cantidad: Int) -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<AplicacionEstetica>?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pacienteId, token) {
        val r = AplicacionesEsteticasRepo.listar(pacienteId)
        if (r.items != null) { items = r.items; error = null } else if (items == null) error = r.error
        cargando = false
    }
    var profesionales by remember { mutableStateOf<List<RefNombre>>(emptyList()) }
    LaunchedEffect(Unit) { profesionales = runCatching { PacientesRepo.terapeutasActivos() }.getOrDefault(emptyList()) }
    // Las citas de estética del paciente: para el vínculo del mapa y el servicio de cada tarjeta.
    var citas by remember(pacienteId) { mutableStateOf<List<CitaParaMapa>>(emptyList()) }
    LaunchedEffect(pacienteId) { citas = AplicacionesEsteticasRepo.citasEstetica(pacienteId, mapaEstetica, nombresServicio) }
    var editor by remember { mutableStateOf<EditorAp?>(null) }
    var recien by remember { mutableStateOf<String?>(null) }
    var confirmarBorrar by remember { mutableStateOf<AplicacionEstetica?>(null) }
    var borrando by remember { mutableStateOf(false) }

    if (cargando && items == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 220, 110).forEach { h ->
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            }
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
    val hoy = hoyClinicaIso()
    val nombrePorId = profesionales.associate { it.id to it.nombre }
    fun tratDe(id: String?) = id?.let { i -> tratamientos.firstOrNull { it.id == i } }
    fun servicioDe(a: AplicacionEstetica) = a.citaId?.let { id -> citas.firstOrNull { it.id == id }?.servicio } ?: tratDe(a.tratamientoId)?.nombre
    fun imprimir(a: AplicacionEstetica) {
        acciones.abrirHtml(
            htmlRegistroAplicacion(a, clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal, pacienteNombre, pacienteDocumento,
                a.terapeutaId?.let { nombrePorId[it] }, servicioDe(a)),
            "Registro de aplicación",
        )
    }
    fun nuevo(desde: AplicacionEstetica? = null) {
        recien = null
        editor = EditorAp.Nuevo(desde?.let { copiarParaRepetir(it.aplicaciones) }, desde?.fecha)
    }
    val guardado = recien?.takeIf { editor == null }?.let { id -> lista.firstOrNull { it.id == id } }
    val toxina = ultimaToxina(lista, hoy)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("💉 Aplicaciones", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (toxina != null && editor == null) {
            val (borde, fondo) = when (toxina.nivel) {
                "pronto" -> c.pend to c.pendBg
                "habitual" -> c.ok to c.okBg
                else -> c.borde to c.chipBg
            }
            AvisoMapa("💉 ${toxina.texto} (${fechaIndicacion(toxina.fecha)})", borde, fondo, negrita = toxina.nivel != null)
        }

        guardado?.let { g ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.okBg)
                    .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Mapa guardado · ${textoTotales(g.totales)}", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("✕", color = c.textoSuave, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { recien = null }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                FilaBotonesMapa {
                    BotonMapa("🖨 Registro de aplicación") { imprimir(g) }
                    onDejarIndicaciones?.let { cb -> BotonMapa("📝 Dejar indicaciones") { cb(servicioParaIndicaciones(g.aplicaciones), g.citaId) } }
                }
                if (puedeEditar && onUsarUnidades != null) BotonUnidades(g, tratDe(g.tratamientoId), onUsarUnidades)
            }
        }

        if (puedeEditar && editor == null && lista.isNotEmpty()) {
            FilaBotonesMapa {
                BotonMapa("↺ Repetir la anterior") { nuevo(lista.first()) }
                BotonMapa("＋ Nuevo mapa", lleno = true) { nuevo() }
            }
        }

        if (lista.isEmpty()) {
            TarjetaMapa {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💉", fontSize = 30.sp)
                    Text("Aún no hay mapas de aplicación", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Toca las zonas del rostro y registra producto, dosis, puntos, lote y vencimiento de cada aplicación. Queda en la historia clínica y se imprime como registro de trazabilidad.",
                        color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    if (puedeEditar) {
                        Spacer(Modifier.height(12.dp))
                        BotonMapa("＋ Nuevo mapa", lleno = true) { nuevo() }
                    }
                }
            }
        } else {
            lista.forEachIndexed { i, a ->
                key(a.id) {
                    TarjetaAplicacion(
                        a = a, actual = i == 0, servicio = servicioDe(a), profesional = a.terapeutaId?.let { nombrePorId[it] },
                        puedeEditar = puedeEditar && editor == null,
                        onImprimir = { imprimir(a) },
                        onEditar = { recien = null; editor = EditorAp.Editar(a.id) },
                        onRepetir = { nuevo(a) },
                        onBorrar = { confirmarBorrar = a },
                    )
                }
            }
        }
    }

    when (val m = editor) {
        null -> Unit
        else -> {
            val enEdicion = (m as? EditorAp.Editar)?.let { e -> lista.firstOrNull { it.id == e.id } }
            if (m is EditorAp.Editar && enEdicion == null) LaunchedEffect(m) { editor = null }
            else key(m) {
                val nuevoM = m as? EditorAp.Nuevo
                EditorMapa(
                    pacienteId = pacienteId, hoy = hoy, citas = citas, tratamientos = tratamientos,
                    citasConMapa = lista.mapNotNull { it.citaId }.toSet(),
                    aplicacion = enEdicion, inicial = nuevoM?.inicial, desde = nuevoM?.desde,
                    anterior = if (nuevoM != null) lista.firstOrNull() else null,
                    profesionales = profesionales, clinicaId = clinicaId,
                    onCerrar = { editor = null },
                    onYaExiste = { id ->
                        scope.launch {
                            val r = AplicacionesEsteticasRepo.listar(pacienteId)
                            r.items?.let { items = it }
                            editor = if (r.items?.any { it.id == id } == true) EditorAp.Editar(id) else null
                        }
                    },
                    onGuardado = { a, esNuevo ->
                        editor = null
                        recien = a.id
                        items = ordenarAplicaciones(if (esNuevo) listOf(a) + lista else lista.map { if (it.id == a.id) a else it })
                    },
                    onGuardadoSinFila = { editor = null; recien = null; token++ },
                )
            }
        }
    }

    confirmarBorrar?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!borrando) confirmarBorrar = null },
            title = { Text("¿Eliminar este mapa?", fontWeight = FontWeight.Bold) },
            text = { Text("Mapa de aplicación del ${fechaIndicacion(p.fecha)} · ${textoTotales(p.totales)}", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = AplicacionesEsteticasRepo.borrar(p.id)
                        borrando = false
                        if (r.registrada || r.rechazo?.codigo == "NO_ENCONTRADO") {
                            confirmarBorrar = null
                            items = lista.filter { it.id != p.id }
                            if (recien == p.id) recien = null
                            Toaster.exito("Mapa eliminado")
                        } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !borrando, onClick = { confirmarBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ─── Cobro por unidades: solo pre-llenar ─────────────────────────────────────

/** "🔢 Usar … como … cobradas", con su confirmación (tratamiento por unidades y cantidad distinta). */
@Composable
private fun BotonUnidades(a: AplicacionEstetica, t: TratamientoParaMapa?, onUsarUnidades: (String, Int) -> Unit) {
    val c = Sania.colors
    val uc = ofertaUnidadesCobradas(a.aplicaciones, t) ?: return
    if (t == null) return
    var confirmar by remember { mutableStateOf(false) }
    val etiqueta = t.unidadLabel ?: "unidades"
    Box(Modifier.padding(top = 8.dp)) {
        if (!confirmar) BotonMapa("🔢 Usar ${uc.texto} como $etiqueta cobradas") { confirmar = true }
        else Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg)
                .border(1.dp, c.pend, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
        ) {
            Text("Se abre el formulario del tratamiento con ${uc.cantidad} $etiqueta (hoy tiene ${t.cantidadUnidades ?: 0}). " +
                "Al editar un tratamiento existente solo cambia la cantidad: revisa el precio final antes de guardar.",
                color = c.texto, fontSize = 12.sp)
            if (uc.mezclaMarcas) Text("⚠ Aproximado: se sumaron U de marcas que no son equivalentes (Dysport ≈ ×2,5 de Botox/Xeomin).",
                color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            if (uc.detalle.isNotEmpty()) Text("Por producto: ${uc.detalle.joinToString(" · ")}", color = c.textoSuave, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
            if (uc.aproximado && !uc.mezclaMarcas) Text("Cantidad redondeada o convertida: ajústala si hace falta.", color = c.textoSuave,
                fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            FilaBotonesMapa {
                BotonMapa("Abrir el formulario", lleno = true) { confirmar = false; onUsarUnidades(t.id, uc.cantidad) }
                BotonMapa("Cancelar") { confirmar = false }
            }
        }
    }
}

// ─── Un mapa del historial ───────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaAplicacion(
    a: AplicacionEstetica,
    actual: Boolean,
    servicio: String?,
    profesional: String?,
    puedeEditar: Boolean,
    onImprimir: () -> Unit,
    onEditar: () -> Unit,
    onRepetir: () -> Unit,
    onBorrar: () -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(actual) }
    val porZona = remember(a.aplicaciones) { totalesPorZona(a.aplicaciones) }
    val t = a.totales
    val ladoPerfil = if (a.aplicaciones.any { zonaFacial(it.zona)?.let { z -> z.lado == "izq" && z.vista == "lateral" } == true }) "lateral_izq" else "lateral_der"
    TarjetaMapa {
        Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }, verticalAlignment = Alignment.Top) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Miniatura("frontal", porZona)
                if (a.vista != "frontal") Miniatura(ladoPerfil, porZona)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("💉 ${fechaIndicacion(a.fecha)}", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (actual) PildoraMapa("Última", c.ok, c.okBg)
                }
                listOfNotNull(servicio, profesional).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Text(textoTotales(t), color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Text(t.porProducto.joinToString(" · ") { p -> "${p.producto} ${textoDosis(p.total, p.unidad)}" +
                    if (p.lotes.isNotEmpty()) " (lote ${p.lotes.joinToString(", ")})" else "" },
                    color = c.textoSuave, fontSize = 11.sp)
            }
            Text(if (abierto) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        if (!abierto) return@TarjetaMapa
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            a.aplicaciones.forEach { x -> FilaAplicacionLectura(x, a.fecha) }
            a.observaciones?.let { Text("Observaciones: $it", color = c.texto, fontSize = 12.sp) }
            FilaBotonesMapa {
                BotonMapa("🖨 Imprimir", onClick = onImprimir)
                if (puedeEditar) {
                    BotonMapa("✏️ Editar", onClick = onEditar)
                    BotonMapa("↺ Repetir", onClick = onRepetir)
                    BotonMapa("🗑️", onClick = onBorrar)
                }
            }
        }
    }
}

@Composable
private fun Miniatura(vista: String, porZona: Map<String, pe.saniape.app.data.staff.ResumenZonaAplicada>) {
    val c = Sania.colors
    Box(Modifier.width(52.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.borde, RoundedCornerShape(8.dp))) {
        MapaFacial(vista, porZona, Modifier.fillMaxWidth(), compacto = true)
    }
}

/** Una aplicación (lectura): zona, producto, dosis, puntos, técnica, lote y vencimiento (rojo si vencido). */
@Composable
private fun FilaAplicacionLectura(x: Aplicacion, fecha: String) {
    val c = Sania.colors
    val vencido = vencidoEn(x.vencimiento, fecha)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(colorHex(RostroGeometria.colorTipo(x.tipoProducto))))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(zonaFacial(x.zona)?.nombre ?: x.zona, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${textoDosis(x.dosis, x.unidad)} ${x.producto}" + (x.puntos?.let { " · $it ptos." } ?: ""), color = c.texto, fontSize = 12.sp)
            val tec = listOfNotNull(TECNICAS_APLICACION.firstOrNull { it.id == x.tecnica }?.nombre, PROFUNDIDADES.firstOrNull { it.id == x.profundidad }?.nombre)
            if (tec.isNotEmpty()) Text(tec.joinToString(" · "), color = c.textoSuave, fontSize = 11.sp)
            Text("Lote ${x.lote ?: "—"} · Vence ${x.vencimiento ?: "—"}${if (vencido) " ⚠" else ""}",
                color = if (vencido) c.error else c.textoSuave, fontSize = 11.sp, fontWeight = if (vencido) FontWeight.Bold else FontWeight.Normal)
            x.notas?.let { Text(it, color = c.textoSuave, fontSize = 11.sp) }
        }
    }
}

// ─── Editor (nuevo o editar) ─────────────────────────────────────────────────

/** El formulario de UNA aplicación (textos tal como se escriben). idx null = nueva. */
private data class FormAp(
    val idx: Int?,
    val zona: String,
    val tipo: String,
    val producto: String,
    val dosis: String,
    val unidad: String,
    val puntos: String,
    val tecnica: String,
    val profundidad: String,
    val lote: String,
    val vencimiento: String,
    val notas: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorMapa(
    pacienteId: String,
    hoy: String,
    citas: List<CitaParaMapa>,
    tratamientos: List<TratamientoParaMapa>,
    citasConMapa: Set<String>,
    /** El mapa que se edita (null = nuevo). */
    aplicacion: AplicacionEstetica?,
    /** Mapa nuevo: lo que trae al abrir ("Repetir") y de qué fecha. */
    inicial: List<Aplicacion>?,
    desde: String?,
    /** El último mapa del paciente (para "Repetir la sesión anterior" desde uno en blanco). */
    anterior: AplicacionEstetica?,
    profesionales: List<RefNombre>,
    clinicaId: String?,
    onCerrar: () -> Unit,
    onYaExiste: (String) -> Unit,
    onGuardado: (AplicacionEstetica, Boolean) -> Unit,
    /** Guardado, pero sin la fila en la respuesta: el padre cierra y recarga. */
    onGuardadoSinFila: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val nuevo = aplicacion == null
    // La cita: la que ya tenía, la de hoy (de estética, sin mapa) o ninguna.
    val citasElegibles = remember(citas, citasConMapa, aplicacion) {
        citas.filter { it.estado != "Cancelada" && (it.id !in citasConMapa || it.id == aplicacion?.citaId) }
            .sortedByDescending { "${it.fecha} ${it.hora.orEmpty()}" }
    }
    val citaPorDefecto = if (aplicacion != null) aplicacion.citaId.orEmpty() else citasElegibles.firstOrNull { it.fecha == hoy }?.id.orEmpty()
    var citaSel by remember { mutableStateOf(citaPorDefecto) }
    var tocoCita by remember { mutableStateOf(false) }
    val activos = tratamientos.filter { it.estado == "Activo" }
    var tratSel by remember { mutableStateOf(aplicacion?.tratamientoId ?: activos.singleOrNull()?.id.orEmpty()) }
    var fecha by remember { mutableStateOf(aplicacion?.fecha ?: hoy) }
    // Quién aplicó: el guardado o el de la cita. Vacío = el servidor decide (el de la cita o el del usuario).
    var terSel by remember { mutableStateOf(aplicacion?.terapeutaId.orEmpty()) }
    // Las citas llegan después de abrir: la de hoy se elige sola (si nadie tocó la cita).
    LaunchedEffect(citasElegibles) {
        if (nuevo && !tocoCita && citaSel.isEmpty()) {
            citasElegibles.firstOrNull { it.fecha == hoy }?.let { ci ->
                citaSel = ci.id; fecha = ci.fecha
                ci.tratamientoId?.let { tratSel = it }
                ci.terapeutaId?.let { terSel = it }
            }
        }
    }
    val citaObj = citasElegibles.firstOrNull { it.id == citaSel }
    var lista by remember { mutableStateOf(aplicacion?.aplicaciones ?: inicial.orEmpty()) }
    var observaciones by remember { mutableStateOf(aplicacion?.observaciones.orEmpty()) }
    var vista by remember { mutableStateOf((aplicacion?.aplicaciones ?: inicial)?.firstOrNull()?.let { vistaDeZona(it.zona) } ?: "frontal") }
    var zonaSel by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<FormAp?>(null) }
    var verLista by remember { mutableStateOf(false) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var elegirFecha by remember { mutableStateOf(false) }
    var recientes by remember { mutableStateOf<List<ProductoReciente>>(emptyList()) }
    LaunchedEffect(Unit) { recientes = AplicacionesEsteticasRepo.recientes(clinicaId) }

    val porZona = remember(lista) { totalesPorZona(lista) }
    val totales = remember(lista) { calcularTotales(lista) }
    val nVencidos = lista.count { vencidoEn(it.vencimiento, fecha) }
    val nFueraRango = lista.count { a -> evaluarDosis(a)?.let { it.nivel != "ok" } == true }

    fun elegirCita(id: String) {
        tocoCita = true
        citaSel = id
        citasElegibles.firstOrNull { it.id == id }?.let { ci ->
            fecha = ci.fecha
            ci.tratamientoId?.let { tratSel = it }
            ci.terapeutaId?.let { terSel = it }
        }
    }
    /** El último lote/vencimiento de ese producto en este mapa. */
    fun loteDe(producto: String): Pair<String, String>? {
        val k = producto.trim().lowercase()
        if (k.isEmpty()) return null
        return lista.lastOrNull { it.producto.lowercase() == k && it.lote != null }?.let { it.lote!! to it.vencimiento.orEmpty() }
    }
    fun abrirNueva(zona: String) {
        val ultimo = lista.lastOrNull()
        val tipo = ultimo?.tipoProducto ?: tipoPorZona(zona)
        val mismoTipo = lista.lastOrNull { it.tipoProducto == tipo }
        val producto = mismoTipo?.producto ?: chipsProducto(tipo, recientes).firstOrNull()?.producto.orEmpty()
        val lote = loteDe(producto)
        form = FormAp(null, zona, tipo, producto, "", mismoTipo?.unidad ?: unidadPorDefecto(tipo), "",
            mismoTipo?.tecnica ?: "aguja", profundidadPorDefecto(tipo, zona).orEmpty(),
            lote?.first.orEmpty(), lote?.second.orEmpty(), "")
        errores = emptyList()
    }
    fun abrirExistente(idx: Int) {
        val a = lista[idx]
        form = FormAp(idx, a.zona, a.tipoProducto, a.producto, numeroDosis(a.dosis), a.unidad, a.puntos?.toString().orEmpty(),
            a.tecnica.orEmpty(), a.profundidad.orEmpty(), a.lote.orEmpty(), a.vencimiento.orEmpty(), a.notas.orEmpty())
        errores = emptyList()
    }
    fun tocarZona(id: String) {
        zonaSel = id
        if (lista.none { it.zona == id }) abrirNueva(id) else form = null
    }
    fun guardarForm() {
        val f = form ?: return
        val r = normalizarAplicacion(CrudoAplicacion(f.zona, f.producto, f.tipo, f.dosis, f.unidad, f.puntos, f.tecnica, f.profundidad,
            f.lote, f.vencimiento, f.notas))
        val ap = r.aplicacion
        if (ap == null) { errores = r.errores.map { it.replace(Regex("""^Aplicación \d+: """), "") }; return }
        lista = if (f.idx == null) lista + ap else lista.mapIndexed { i, x -> if (i == f.idx) ap else x }
        form = null
        errores = emptyList()
    }
    fun quitar(idx: Int) { lista = lista.filterIndexed { i, _ -> i != idx }; form = null }
    fun repetirAnterior() {
        val ant = anterior ?: return
        lista = copiarParaRepetir(ant.aplicaciones)
        vista = vistaDeZona(ant.aplicaciones.firstOrNull()?.zona ?: "glabela")
        form = null; zonaSel = null
        Toaster.info("Copiado del ${fechaIndicacion(ant.fecha)}: revisa las dosis y pon el lote de hoy")
    }
    fun guardar() {
        if (guardando) return
        if (form != null) { Toaster.error("Termina o cancela la aplicación que estás editando"); return }
        val b = BorradorAplicacion(pacienteId, lista, citaId = citaSel.ifEmpty { null }, tratamientoId = tratSel.ifEmpty { null },
            terapeutaId = terSel.ifEmpty { null }, fecha = fecha, observaciones = observaciones.trim().ifEmpty { null })
        val e = validarAplicacionEstetica(b)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = AplicacionesEsteticasRepo.guardar(b, aplicacion?.id)
            guardando = false
            if (r.rechazo?.codigo == "YA_EXISTE") {
                val id = (r.cuerpo?.get("id") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                if (id != null) {
                    Toaster.info("Esa cita ya tiene su mapa: lo abrimos para que lo edites")
                    onYaExiste(id)
                    return@launch
                }
            }
            val fila = (r.cuerpo?.get("aplicacion") as? JsonObject)?.let(::aplicacionEsteticaDeJson)
            if (r.registrada && fila != null) {
                AplicacionesEsteticasRepo.olvidarRecientes()
                Toaster.exito(if (nuevo) "Mapa de aplicación guardado" else "Mapa actualizado")
                onGuardado(fila, nuevo)
            } else if (r.registrada) {
                // El servidor lo guardó pero no devolvió la fila: se cierra y se recarga la
                // lista (un "No se pudo guardar" llevaría a reintentar y duplicar el mapa).
                AplicacionesEsteticasRepo.olvidarRecientes()
                Toaster.exito(if (nuevo) "Mapa de aplicación guardado" else "Mapa actualizado")
                onGuardadoSinFila()
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = if (nuevo) "Nuevo mapa de aplicación" else "Editar mapa del ${fechaIndicacion(aplicacion!!.fecha)}",
        subtitulo = "💉 Zona, producto, dosis, lote y vencimiento",
        textoAccion = if (guardando) "Guardando…" else if (nuevo) "Guardar mapa" else "Guardar cambios",
        accionHabilitada = !guardando && lista.isNotEmpty(),
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        if (desde != null && nuevo) {
            AvisoMapa("Partimos de la sesión del ${fechaIndicacion(desde)}: revisa las dosis y pon el lote de hoy.", c.pend, c.pendBg)
            Spacer(Modifier.height(10.dp))
        }
        // La atención
        EtqForm("Cita")
        SelectorOpciones(
            citaObj?.let { textoCita(it) } ?: "Sin cita",
            listOf("" to "Sin cita") + citasElegibles.map { it.id to textoCita(it) },
        ) { elegirCita(it) }
        Spacer(Modifier.height(8.dp))
        EtqForm("Tratamiento")
        SelectorOpciones(
            tratamientos.firstOrNull { it.id == tratSel }?.let { textoTrat(it) } ?: "Sin tratamiento",
            listOf("" to "Sin tratamiento") + tratamientos.map { it.id to textoTrat(it) },
        ) { tratSel = it }
        Spacer(Modifier.height(8.dp))
        EtqForm("Aplicó")
        val vacioTer = if (citaObj != null) "El profesional de la cita" else "Yo"
        SelectorOpciones(
            profesionales.firstOrNull { it.id == terSel }?.nombre ?: vacioTer,
            listOf("" to vacioTer) + profesionales.map { it.id to it.nombre },
        ) { terSel = it }
        Spacer(Modifier.height(8.dp))
        EtqForm("Fecha")
        CajaSelectorForm(fechaIndicacion(fecha)) { elegirFecha = true }

        if (nuevo && lista.isEmpty() && anterior != null) {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(c.chipBg).border(1.dp, c.lav, RoundedCornerShape(Sania.shape.md.dp))
                    .clickable { repetirAnterior() }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("↺ Repetir la sesión anterior", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${fechaIndicacion(anterior.fecha)} · ${textoTotales(anterior.totales)} · ${anterior.totales.zonas} zonas (sin lote)",
                    color = c.textoSuave, fontSize = 11.sp)
            }
        }

        // El rostro
        Spacer(Modifier.height(12.dp))
        SegmentadoMapa(VISTAS_MAPA, vista, { v -> v?.let { vista = it; zonaSel = null; form = null } }, permiteVacio = false)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MapaFacial(vista, porZona, Modifier.widthIn(max = 320.dp).fillMaxWidth(), seleccionada = zonaSel, onZona = { tocarZona(it) })
        }
        Text(if (verLista) "Ocultar la lista de zonas" else "📋 Elegir la zona de una lista", color = c.navy, fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.heightIn(min = 44.dp).clickable { verLista = !verLista }.padding(vertical = 12.dp))
        if (verLista) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                zonasDeVista(vista).forEach { z ->
                    ChipMapa(z.corto + (porZona[z.id]?.let { " · ${it.texto}" } ?: ""), zonaSel == z.id) { tocarZona(z.id) }
                }
            }
        }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TIPOS_PRODUCTO.filter { it.id != "otro" }.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(colorHex(RostroGeometria.colorTipo(t.id))))
                    Spacer(Modifier.width(4.dp))
                    Text(t.corto, color = c.textoSuave, fontSize = 10.sp)
                }
            }
        }

        // El panel: la zona elegida
        Spacer(Modifier.height(10.dp))
        val zona = zonaSel?.let { zonaFacial(it) }
        if (zona == null) {
            AvisoMapa("👆 Toca una zona del rostro para registrar la aplicación. Derecha e izquierda son las del paciente.", c.borde, c.chipBg)
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                    .padding(10.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(zona.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (form == null) BotonMapa("＋ Agregar") { abrirNueva(zona.id) }
                }
                if (form == null) lista.forEachIndexed { i, a ->
                    if (a.zona != zona.id) return@forEachIndexed
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                            .padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(colorHex(RostroGeometria.colorTipo(a.tipoProducto))))
                        Text("${textoDosis(a.dosis, a.unidad)} ${a.producto}" + (a.puntos?.let { " · $it ptos." } ?: "") +
                            (a.lote?.let { " · lote $it" } ?: ""),
                            color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text("✏️", fontSize = 15.sp, modifier = Modifier.heightIn(min = 44.dp).clickable { abrirExistente(i) }.padding(12.dp))
                        Text("✕", fontSize = 15.sp, color = c.textoSuave, modifier = Modifier.heightIn(min = 44.dp).clickable { quitar(i) }.padding(12.dp))
                    }
                }
                form?.takeIf { it.zona == zona.id }?.let { f ->
                    FormularioAplicacion(
                        form = f, setForm = { form = it }, recientes = recientes, fecha = fecha, errores = errores,
                        loteDe = ::loteDe,
                        onGuardar = { guardarForm() }, onCancelar = { form = null; errores = emptyList() },
                        onQuitar = f.idx?.let { idx -> { quitar(idx) } },
                    )
                }
            }
        }

        // Totales de la sesión
        if (lista.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
            ) {
                EtiquetaMapa("Total de la sesión")
                Text(textoTotales(totales), color = c.navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${totales.zonas} ${if (totales.zonas == 1) "zona" else "zonas"} · ${totales.puntos} puntos", color = c.textoSuave, fontSize = 11.sp)
                totales.porProducto.forEach { p ->
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(colorHex(RostroGeometria.colorTipo(p.tipoProducto))))
                        Text(p.producto + if (p.lotes.isNotEmpty()) " · lote ${p.lotes.joinToString(", ")}" else "",
                            color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text(textoDosis(p.total, p.unidad), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (nVencidos > 0) Text("⚠ ${if (nVencidos == 1) "Una aplicación tiene" else "$nVencidos aplicaciones tienen"} el producto vencido a la fecha de la sesión.",
                    color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                if (nFueraRango > 0) Text("ℹ $nFueraRango fuera de lo habitual (orientativo, no impide guardar).",
                    color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(Modifier.height(10.dp))
        EtqForm("Observaciones de la sesión")
        OutlinedTextField(
            value = observaciones, onValueChange = { observaciones = it.take(LimitesAplicacion.OBSERVACIONES) },
            placeholder = { Text("Ej. Sin eventos. Control en 15 días para retoque.", color = c.textoSuave, fontSize = 13.sp) },
            minLines = 2, modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
        )
        if (form == null) CajaErroresMapa(errores)
    }

    if (elegirFecha) DialogoFecha(onElegir = { fecha = it }, onCerrar = { elegirFecha = false }, inicial = fecha)
}

private fun textoCita(ci: CitaParaMapa): String =
    "${fechaIndicacion(ci.fecha)}${ci.hora?.let { " ${it.take(5)}" }.orEmpty()} · ${ci.servicio ?: "Atención"} · ${ci.estado}"

private fun textoTrat(t: TratamientoParaMapa): String = t.nombre + if (t.estado != "Activo") " (${t.estado})" else ""

/** Selector de una lista (cita, tratamiento, quién aplicó): caja tocable + menú. */
@Composable
private fun SelectorOpciones(valor: String, opciones: List<Pair<String, String>>, onElegir: (String) -> Unit) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Box {
        CajaSelectorForm("$valor  ▾") { abierto = true }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (id, texto) ->
                DropdownMenuItem(text = { Text(texto, color = c.texto, fontSize = 13.sp) }, onClick = { abierto = false; onElegir(id) })
            }
        }
    }
}

// ─── Formulario de UNA aplicación (en la zona elegida) ───────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioAplicacion(
    form: FormAp,
    setForm: (FormAp) -> Unit,
    recientes: List<ProductoReciente>,
    fecha: String,
    errores: List<String>,
    loteDe: (String) -> Pair<String, String>?,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
    onQuitar: (() -> Unit)?,
) {
    val c = Sania.colors
    fun set(f: FormAp) = setForm(f)
    val tipo = tipoProducto(form.tipo)
    val chips = chipsProducto(form.tipo, recientes).take(8)
    val rango = rangoTipico(form.zona, form.tipo, form.producto)
    val dosisNum = leerNumeroDosis(form.dosis)
    val aviso = if (form.dosis.isNotBlank() && dosisNum.isFinite()) evaluarDosis(form.zona, form.tipo, form.producto, dosisNum, form.unidad) else null
    val riesgo = avisoRiesgoVascular(form.zona, form.tipo)
    // El último lote: el de este mapa o el de la clínica (chip; nunca se rellena solo).
    val propio = loteDe(form.producto)
    val deClinica = recientes.firstOrNull { it.producto.lowercase() == form.producto.trim().lowercase() && it.ultimoLote != null }
    val ultimoLote = propio ?: deClinica?.let { it.ultimoLote!! to it.ultimoVencimiento.orEmpty() }
    val vencido = vencidoEn(form.vencimiento.ifBlank { null }, fecha)
    val puntos = form.puntos.toIntOrNull() ?: 0

    fun elegirTipo(t: String) {
        val unidades = tipoProducto(t).unidades
        set(form.copy(tipo = t, unidad = if (form.unidad in unidades) form.unidad else unidades.first(),
            producto = chipsProducto(t, recientes).firstOrNull()?.producto.orEmpty(),
            profundidad = profundidadPorDefecto(t, form.zona).orEmpty(), lote = "", vencimiento = ""))
    }
    fun elegirProducto(nombre: String, t: String? = null) {
        val lote = loteDe(nombre)
        val nuevoTipo = t ?: inferirTipoProducto(nombre) ?: form.tipo
        val unidades = tipoProducto(nuevoTipo).unidades
        var f = form.copy(producto = nombre, tipo = nuevoTipo, unidad = if (form.unidad in unidades) form.unidad else unidades.first())
        if (nuevoTipo != form.tipo) f = f.copy(profundidad = profundidadPorDefecto(nuevoTipo, form.zona).orEmpty())
        // El mismo vial del mapa se usa en todas sus zonas: el lote se repite solo.
        if (lote != null) f = f.copy(lote = lote.first, vencimiento = lote.second)
        set(f)
    }

    Column(Modifier.padding(top = 8.dp)) {
        EtiquetaMapa("Tipo de producto")
        SegmentadoMapa(TIPOS_PRODUCTO.map { it.id to it.corto }, form.tipo, { v -> v?.let { elegirTipo(it) } }, permiteVacio = false)

        EtiquetaMapa("Producto / marca")
        OutlinedTextField(
            value = form.producto,
            onValueChange = { v ->
                val p = v.take(LimitesAplicacion.PRODUCTO)
                // Si el nombre delata otro tipo ("Botox" con relleno elegido), se acomoda solo.
                val t = inferirTipoProducto(p)
                if (t != null && t != form.tipo) elegirProducto(p, t) else set(form.copy(producto = p))
            },
            placeholder = {
                Text(when (form.tipo) { "toxina" -> "Ej. Botox, Xeomin, Dysport"; "acido_hialuronico" -> "Ej. Juvéderm Volbella"; else -> "Nombre del producto" },
                    color = c.textoSuave, fontSize = 13.sp)
            },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
        )
        if (chips.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { ch ->
                    ChipMapa((if (ch.propio) "★ " else "") + ch.producto, plegarProducto(form.producto) == plegarProducto(ch.producto)) {
                        elegirProducto(ch.producto, ch.tipo)
                    }
                }
            }
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                EtiquetaMapa("Dosis")
                OutlinedTextField(
                    value = form.dosis, onValueChange = { v -> set(form.copy(dosis = v.filter { it.isDigit() || it == '.' || it == ',' }.take(8))) },
                    placeholder = { Text(rango?.let { "${numeroDosis(it.min)}–${numeroDosis(it.max)}" } ?: "0", color = c.textoSuave, fontSize = 13.sp) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
                )
            }
            Column(Modifier.weight(1f)) {
                EtiquetaMapa("Unidad")
                SegmentadoMapa(tipo.unidades.map { it to it }, form.unidad, { v -> v?.let { set(form.copy(unidad = it)) } }, permiteVacio = false)
            }
        }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            dosisSugeridas(form.zona, form.tipo, form.producto).forEach { d ->
                val unidad = rango?.unidad?.takeIf { it in tipo.unidades } ?: form.unidad
                ChipMapa(textoDosis(d, rango?.unidad ?: form.unidad), dosisNum == d) { set(form.copy(dosis = numeroDosis(d), unidad = unidad)) }
            }
        }
        if (aviso != null || rango != null) {
            val fuera = aviso != null && aviso.nivel != "ok"
            Text(aviso?.texto ?: rango!!.texto, color = if (fuera) c.pend else c.textoSuave, fontSize = 11.sp,
                fontWeight = if (fuera) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 6.dp))
        }
        riesgo?.let { Box(Modifier.padding(top = 6.dp)) { AvisoMapa("⚠ $it", c.error, c.errorBg) } }

        EtiquetaMapa("Puntos")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonMapa("−") { set(form.copy(puntos = maxOf(0, puntos - 1).toString())) }
            OutlinedTextField(
                value = form.puntos, onValueChange = { v -> set(form.copy(puntos = v.filter { it.isDigit() }.take(3))) },
                placeholder = { Text("—", color = c.textoSuave) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp), colors = coloresCampoForm(),
            )
            BotonMapa("＋") { set(form.copy(puntos = minOf(LimitesAplicacion.PUNTOS, puntos + 1).toString())) }
        }

        EtiquetaMapa("Técnica")
        SegmentadoMapa(TECNICAS_APLICACION.map { it.id to it.nombre }, form.tecnica.ifEmpty { null }, { set(form.copy(tecnica = it.orEmpty())) })
        EtiquetaMapa("Profundidad")
        SegmentadoMapa(PROFUNDIDADES.map { it.id to it.nombre }, form.profundidad.ifEmpty { null }, { set(form.copy(profundidad = it.orEmpty())) })

        // Trazabilidad
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    EtiquetaMapa("Lote")
                    OutlinedTextField(
                        value = form.lote, onValueChange = { set(form.copy(lote = it.uppercase().take(LimitesAplicacion.LOTE))) },
                        placeholder = { Text("Ej. C7254AC4", color = c.textoSuave, fontSize = 13.sp) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    EtiquetaMapa("Vencimiento")
                    OutlinedTextField(
                        value = form.vencimiento, onValueChange = { set(form.copy(vencimiento = formatearVencimiento(it))) },
                        placeholder = { Text("AAAA-MM", color = c.textoSuave, fontSize = 13.sp) }, singleLine = true,
                        isError = vencido, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
                    )
                }
            }
            if (ultimoLote != null && ultimoLote.first != form.lote) {
                Box(Modifier.padding(top = 8.dp)) {
                    ChipMapa("Último lote: ${ultimoLote.first}" + (ultimoLote.second.takeIf { it.isNotBlank() }?.let { " · vence ${it.take(7)}" } ?: ""), false) {
                        set(form.copy(lote = ultimoLote.first, vencimiento = ultimoLote.second))
                    }
                }
            }
            if (vencido) Text("⚠ Vencido a la fecha de la sesión. Revisa el lote.", color = c.error, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text("Trazabilidad: ante un evento adverso, el lote identifica el producto.", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp))
        }

        EtiquetaMapa("Notas de la zona")
        OutlinedTextField(
            value = form.notas, onValueChange = { set(form.copy(notas = it.take(LimitesAplicacion.NOTAS))) },
            placeholder = { Text("Ej. 5 puntos en V, bolo profundo", color = c.textoSuave, fontSize = 13.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
        )
        CajaErroresMapa(errores)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onQuitar != null) Text("Quitar", color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onQuitar).padding(12.dp))
            Spacer(Modifier.weight(1f))
            BotonMapa("Cancelar", onClick = onCancelar)
            BotonMapa(if (form.idx == null) "Agregar" else "Listo", lleno = true, onClick = onGuardar)
        }
    }
}

/** "202703" → "2027-03"; "20270315" → "2027-03-15" (el vial suele decir solo mes y año). */
internal fun formatearVencimiento(v: String): String {
    val d = v.filter { it.isDigit() }.take(8)
    return when {
        d.length <= 4 -> d
        d.length <= 6 -> "${d.take(4)}-${d.drop(4)}"
        else -> "${d.take(4)}-${d.substring(4, 6)}-${d.drop(6)}"
    }
}
