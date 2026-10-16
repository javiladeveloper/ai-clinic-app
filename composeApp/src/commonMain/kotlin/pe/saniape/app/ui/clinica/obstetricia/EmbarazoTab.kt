package pe.saniape.app.ui.clinica.obstetricia

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import pe.saniape.app.data.staff.AlertaEmbarazo
import pe.saniape.app.data.staff.ControlPrenatal
import pe.saniape.app.data.staff.Embarazo
import pe.saniape.app.data.staff.EmbarazoRepo
import pe.saniape.app.data.staff.LecturaEmbarazo
import pe.saniape.app.data.staff.MapaGineco
import pe.saniape.app.data.staff.NOMBRE_CATEGORIA_IMC
import pe.saniape.app.data.staff.NOMBRE_TRIMESTRE
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.alertasControl
import pe.saniape.app.data.staff.bandaAu
import pe.saniape.app.data.staff.bandaGanancia
import pe.saniape.app.data.staff.categoriaImc
import pe.saniape.app.data.staff.datacion
import pe.saniape.app.data.staff.egDelControl
import pe.saniape.app.data.staff.embarazoDeJson
import pe.saniape.app.data.staff.esMultiple
import pe.saniape.app.data.staff.fechaEmb
import pe.saniape.app.data.staff.formulaObstetrica
import pe.saniape.app.data.staff.grupoRh
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.htmlCarnePerinatal
import pe.saniape.app.data.staff.imcEmb
import pe.saniape.app.data.staff.nombreDesenlace
import pe.saniape.app.data.staff.nombreMovimientos
import pe.saniape.app.data.staff.nombrePresentacion
import pe.saniape.app.data.staff.nombreRiesgo
import pe.saniape.app.data.staff.nombreSuplemento
import pe.saniape.app.data.staff.numEmb
import pe.saniape.app.data.staff.ordenarControles
import pe.saniape.app.data.staff.ordenarEmbarazos
import pe.saniape.app.data.staff.resumenEmbarazo
import pe.saniape.app.data.staff.PA_DIAST_ELEVADA
import pe.saniape.app.data.staff.PA_SIST_ELEVADA
import pe.saniape.app.data.staff.PA_DIAST_SEVERA
import pe.saniape.app.data.staff.PA_SIST_SEVERA
import pe.saniape.app.data.staff.puntosPa
import pe.saniape.app.data.staff.resumenAlertasPa
import pe.saniape.app.data.staff.serieAu
import pe.saniape.app.data.staff.textoPuntoPa
import pe.saniape.app.data.staff.serieGanancia
import pe.saniape.app.data.staff.textoEg
import pe.saniape.app.data.staff.textoGananciaRecomendada
import pe.saniape.app.data.staff.PuntoSerieHc
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.historia.BandaGrafico
import pe.saniape.app.ui.clinica.historia.EjeXGrafico
import pe.saniape.app.ui.clinica.historia.AlertaPunto
import pe.saniape.app.ui.clinica.historia.GraficoLineaHc
import pe.saniape.app.ui.clinica.historia.LineaRefGrafico
import pe.saniape.app.ui.clinica.historia.SerieGrafico
import pe.saniape.app.ui.clinica.mapas.BotonMapa
import pe.saniape.app.ui.clinica.mapas.EtiquetaMapa
import pe.saniape.app.ui.clinica.mapas.FilaBotonesMapa
import pe.saniape.app.ui.clinica.mapas.PildoraMapa
import pe.saniape.app.ui.clinica.mapas.TarjetaMapa
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/** El editor del control: nuevo o el que se edita (por id). */
internal sealed interface EditorControl {
    data object Nuevo : EditorControl
    data class Editar(val id: String) : EditorControl
}

/**
 * Pestaña 🤰 Embarazo de la ficha (solo pacientes de gineco-obstetricia:
 * `pacienteEsGineco`). Gemelo de `EmbarazoTab.tsx` de la web — el carné
 * perinatal digital: un embarazo activo por paciente y sus controles.
 *
 *  · Sin embarazo activo: "＋ Registrar embarazo" y los anteriores.
 *  · Activo: EG hoy, FPP, datación, controles N de 6 (NTS 105-MINSA), próximo
 *    control (📅 agendar), fórmula, grupo y Rh, IMC, riesgos y las alertas de
 *    apoyo del último control; "＋ Control prenatal", gráficos (AU y ganancia
 *    de peso vs EG), los controles, 🖨 Carné perinatal y "🏁 Finalizar".
 *
 * "Hoy" es el de la SEDE de la paciente ([hoyDeSede]): decide la EG por defecto
 * y las fechas futuras, como la API. Carga SOLO cuando la pestaña se abre (la
 * monta el padre en su `when`). Sin listas perezosas: vive dentro del
 * `verticalScroll` de la ficha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmbarazoTab(
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    pacienteEdad: Int?,
    pacienteTelefono: String?,
    /** Talla de la ficha (cm): precarga la del embarazo nuevo. */
    pacienteTalla: Int?,
    /** `puede('pacientes') || puede('sesiones')` y ficha no dada de baja (como el API). */
    puedeEditar: Boolean,
    mapaGineco: MapaGineco,
    /** Especialidad de gineco de quien registra (apartado de la HC). */
    especialidadId: String?,
    /** "Hoy" en la zona de la sede de la paciente (null = la principal). */
    hoyDeSede: (String?) -> String,
    clinicaId: String?,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    /** "📅 Agendar próximo control" (null = no se ofrece). */
    onAgendarControl: ((String) -> Unit)? = null,
    /**
     * Desde la agenda (citaId de la web): abre un control nuevo vinculado a ESA
     * cita. Se consume al guardarlo (otro control nuevo vuelve a la regla del día).
     */
    citaId: String? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var datos by remember(pacienteId) { mutableStateOf<LecturaEmbarazo?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pacienteId, token) {
        val r = EmbarazoRepo.cargar(pacienteId)
        if (r.embarazos != null) { datos = r; error = null } else if (datos == null) error = r.error
        cargando = false
    }
    var profesionales by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        profesionales = runCatching { PacientesRepo.terapeutasActivos().associate { it.id to it.nombre } }.getOrDefault(emptyMap())
    }
    var editorEmb by remember { mutableStateOf<String?>(null) }          // "nuevo" | "editar"
    var editorCtrl by remember { mutableStateOf<EditorControl?>(null) }
    var citaPendiente by remember(pacienteId) { mutableStateOf(citaId) }
    // Desde la agenda abre un control nuevo, una sola vez (como la web: editorCtrl = 'nuevo').
    var abrioDesdeAgenda by remember(pacienteId) { mutableStateOf(false) }
    var finalizar by remember { mutableStateOf(false) }
    var borrarEmb by remember { mutableStateOf<Embarazo?>(null) }
    var borrarCtrl by remember { mutableStateOf<ControlPrenatal?>(null) }
    var borrando by remember { mutableStateOf(false) }
    var verAnteriores by remember { mutableStateOf(false) }
    var reabriendo by remember { mutableStateOf(false) }

    if (cargando && datos == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(120, 220).forEach { h ->
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

    val d0 = datos ?: return
    val hoy = hoyDeSede(d0.sedeId)
    val embarazos = ordenarEmbarazos(d0.embarazos.orEmpty())
    val activo = embarazos.firstOrNull { it.activo }
    val anteriores = embarazos.filter { it.id != activo?.id }
    LaunchedEffect(activo?.id) {
        if (!abrioDesdeAgenda && citaPendiente != null && activo != null && puedeEditar && editorCtrl == null) {
            abrioDesdeAgenda = true
            editorCtrl = EditorControl.Nuevo
        }
    }
    fun controlesDe(id: String) = d0.controles.filter { it.embarazoId == id }
    fun ponerEmbarazo(e: Embarazo) {
        val lista = d0.embarazos.orEmpty()
        datos = d0.copy(embarazos = if (lista.any { it.id == e.id }) lista.map { if (it.id == e.id) e else it } else listOf(e) + lista)
    }
    fun imprimir(e: Embarazo) = acciones.abrirHtml(
        htmlCarnePerinatal(e, controlesDe(e.id), hoy, clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal,
            pacienteNombre, pacienteDocumento, pacienteEdad, pacienteTelefono, profesionales),
        "Carné perinatal",
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🤰 Embarazo", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)

        if (activo == null) {
            TarjetaMapa {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🤰", fontSize = 30.sp)
                    Text(if (anteriores.isNotEmpty()) "No hay un embarazo en curso" else "Aún no hay un embarazo registrado",
                        color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text("Con la FUR (o la ecografía precoz) Sania calcula la FPP y la edad gestacional de cada control, cuenta los controles de la norma, sugiere el próximo y arma el carné perinatal para imprimir.",
                        color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    if (puedeEditar) {
                        Spacer(Modifier.height(12.dp))
                        BotonMapa("＋ Registrar embarazo", lleno = true) { editorEmb = "nuevo" }
                    }
                }
            }
        } else {
            EmbarazoActivo(
                emb = activo, controles = controlesDe(activo.id), hoy = hoy, puedeEditar = puedeEditar,
                profesionales = profesionales,
                onNuevoControl = { editorCtrl = EditorControl.Nuevo },
                onEditarControl = { editorCtrl = EditorControl.Editar(it.id) },
                onBorrarControl = { borrarCtrl = it },
                onEditar = { editorEmb = "editar" },
                onFinalizar = { finalizar = true },
                onBorrar = { borrarEmb = activo },
                onImprimir = { imprimir(activo) },
                onAgendarControl = onAgendarControl,
            )
        }

        if (anteriores.isNotEmpty()) {
            Text((if (verAnteriores) "▾ " else "▸ ") + "Embarazos anteriores (${anteriores.size})", color = c.navy, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { verAnteriores = !verAnteriores }
                    .padding(vertical = 12.dp))
            if (verAnteriores) anteriores.forEach { e ->
                key(e.id) {
                    EmbarazoAnterior(
                        emb = e, controles = controlesDe(e.id), profesionales = profesionales,
                        onImprimir = { imprimir(e) },
                        onReabrir = if (puedeEditar && activo == null) ({
                            if (!reabriendo) {
                                reabriendo = true
                                scope.launch {
                                    val r = EmbarazoRepo.cambiarEstado(e.id, finalizar = false)
                                    reabriendo = false
                                    val fila = (r.cuerpo?.get("embarazo") as? JsonObject)?.let(::embarazoDeJson)
                                    if (r.registrada && fila != null) { ponerEmbarazo(fila); Toaster.exito("Embarazo reabierto") }
                                    else Toaster.error(r.rechazo?.error ?: "No se pudo reabrir")
                                }
                            }
                        }) else null,
                    )
                }
            }
        }
    }

    // ── Diálogos ──
    editorEmb?.let { modo ->
        key(modo) {
            FormEmbarazo(
                pacienteId = pacienteId, hoy = hoy, edad = pacienteEdad, talla = pacienteTalla, especialidadId = especialidadId,
                embarazo = if (modo == "editar") activo else null,
                onCerrar = { editorEmb = null },
                // Editar puede cambiar la datación: el servidor recalcula la EG de los
                // controles, así que se relee (sin vaciar la pantalla), como la web.
                onGuardado = { e -> editorEmb = null; ponerEmbarazo(e); if (modo == "editar") token++ },
            )
        }
    }
    val embCtrl = activo
    when (val m = editorCtrl) {
        null -> Unit
        else -> if (embCtrl == null) LaunchedEffect(m) { editorCtrl = null } else {
            val lista = ordenarControles(controlesDe(embCtrl.id))
            val enEdicion = (m as? EditorControl.Editar)?.let { x -> lista.firstOrNull { it.id == x.id } }
            if (m is EditorControl.Editar && enEdicion == null) LaunchedEffect(m) { editorCtrl = null }
            else key(m) {
                val idx = enEdicion?.let { e -> lista.indexOfFirst { it.id == e.id } } ?: -1
                FormControl(
                    embarazo = embCtrl, pacienteId = pacienteId, hoy = hoy, control = enEdicion,
                    anterior = if (enEdicion == null) lista.lastOrNull() else lista.getOrNull(idx - 1),
                    citasUsadas = lista.mapNotNull { it.citaId }.toSet(), mapaGineco = mapaGineco, clinicaId = clinicaId,
                    citaId = if (m is EditorControl.Nuevo) citaPendiente else null,
                    onCerrar = { editorCtrl = null },
                    onGuardado = { ctl, nuevo ->
                        editorCtrl = null
                        if (nuevo) citaPendiente = null
                        val actuales = datos?.controles.orEmpty()
                        datos = datos?.copy(controles = if (nuevo) listOf(ctl) + actuales else actuales.map { if (it.id == ctl.id) ctl else it })
                    },
                )
            }
        }
    }
    if (finalizar && activo != null) {
        FinalizarEmbarazo(emb = activo, hoy = hoy, onCerrar = { finalizar = false }, onHecho = { e -> finalizar = false; ponerEmbarazo(e) })
    }
    borrarEmb?.let { e ->
        AlertDialog(
            onDismissRequest = { if (!borrando) borrarEmb = null },
            title = { Text("¿Eliminar el embarazo?", fontWeight = FontWeight.Bold) },
            text = { Text("Se eliminan también sus ${controlesDe(e.id).size} controles. No se puede deshacer.", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = EmbarazoRepo.borrarEmbarazo(e.id)
                        borrando = false
                        if (r.registrada || r.rechazo?.codigo == "NO_ENCONTRADO") {
                            borrarEmb = null
                            datos = datos?.let { x -> x.copy(embarazos = x.embarazos.orEmpty().filter { it.id != e.id }, controles = x.controles.filter { it.embarazoId != e.id }) }
                            Toaster.exito("Embarazo eliminado")
                        } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !borrando, onClick = { borrarEmb = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
    borrarCtrl?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!borrando) borrarCtrl = null },
            title = { Text("¿Eliminar este control?", fontWeight = FontWeight.Bold) },
            text = { Text("Control prenatal del ${fechaEmb(p.fecha)}", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = EmbarazoRepo.borrarControl(p.id)
                        borrando = false
                        if (r.registrada || r.rechazo?.codigo == "NO_ENCONTRADO") {
                            borrarCtrl = null
                            datos = datos?.let { x -> x.copy(controles = x.controles.filter { it.id != p.id }) }
                            Toaster.exito("Control eliminado")
                        } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !borrando, onClick = { borrarCtrl = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ─── El embarazo en curso ────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmbarazoActivo(
    emb: Embarazo,
    controles: List<ControlPrenatal>,
    hoy: String,
    puedeEditar: Boolean,
    profesionales: Map<String, String>,
    onNuevoControl: () -> Unit,
    onEditarControl: (ControlPrenatal) -> Unit,
    onBorrarControl: (ControlPrenatal) -> Unit,
    onEditar: () -> Unit,
    onFinalizar: () -> Unit,
    onBorrar: () -> Unit,
    onImprimir: () -> Unit,
    onAgendarControl: ((String) -> Unit)?,
) {
    val c = Sania.colors
    val r = remember(emb, controles, hoy) { resumenEmbarazo(emb, controles, hoy) }
    val d = r.datacion
    val fppPasada = r.diasParaFpp != null && r.diasParaFpp < 0
    val ultimo = ordenarControles(controles).lastOrNull()
    TarjetaMapa {
        Text("🤰 Embarazo en curso", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        d?.let { Text(it.motivo, color = if (it.corregidaPorEco) c.pend else c.textoSuave, fontSize = 12.sp,
            fontWeight = if (it.corregidaPorEco) FontWeight.Bold else FontWeight.Normal) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dato("EG hoy", textoEg(r.egHoyDias) ?: "—", r.trimestre?.let { NOMBRE_TRIMESTRE[it] }, null, Modifier.weight(1f))
            Dato("FPP", d?.let { fechaEmb(it.fpp) } ?: "—",
                r.diasParaFpp?.let { if (it < 0) "hace ${-it} días" else "faltan $it días" }, if (fppPasada) "alerta" else null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dato("Controles", "${r.minsa.realizados} de ${r.minsa.minimo}",
                when {
                    r.minsa.controlada -> "gestante controlada (MINSA)"
                    r.minsa.atrasada -> "a esta EG se esperaban ${r.minsa.esperadosALaFecha}"
                    else -> "mínimo de la NTS 105-MINSA"
                },
                if (r.minsa.controlada) "ok" else if (r.minsa.atrasada) "alerta" else null, Modifier.weight(1f))
            Dato("Próximo control", r.proximoControl?.let { fechaEmb(it) } ?: "—",
                if (r.proximoVencido) "ya pasó: agéndalo" else if (ultimo == null) "registra el primer control" else null,
                if (r.proximoVencido) "alerta" else null, Modifier.weight(1f))
        }
        val lineas = listOfNotNull(
            formulaObstetrica(emb)?.let { "Fórmula: $it" },
            grupoRh(emb.grupoSanguineo, emb.rh)?.let { "Grupo: $it" },
            if (r.imcPregestacional != null && r.categoriaImc != null)
                "IMC pregestacional: ${numEmb(r.imcPregestacional)} (${NOMBRE_CATEGORIA_IMC[r.categoriaImc]}, ganar ${textoGananciaRecomendada(r.categoriaImc, esMultiple(emb.riesgos))})"
            else null,
        )
        if (lineas.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            lineas.forEach { l ->
                Text(l, color = if (l.startsWith("Grupo") && emb.rh == "-") c.error else c.texto, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (emb.riesgos.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                emb.riesgos.forEach { PildoraMapa(nombreRiesgo(it), c.pend, c.pendBg) }
            }
        }
        if (r.alertas.isNotEmpty()) Box(Modifier.padding(top = 10.dp)) { ListaAlertas(r.alertas) }
        Spacer(Modifier.height(10.dp))
        FilaBotonesMapa {
            if (puedeEditar) BotonMapa("＋ Control prenatal", lleno = true, onClick = onNuevoControl)
            if (puedeEditar && r.proximoControl != null && onAgendarControl != null) {
                val fecha = if (r.proximoControl < hoy) hoy else r.proximoControl
                BotonMapa("📅 Agendar próximo control · ${fechaEmb(fecha)}") { onAgendarControl(fecha) }
            }
            BotonMapa("🖨 Carné perinatal", onClick = onImprimir)
            if (puedeEditar) {
                BotonMapa("✏️ Editar", onClick = onEditar)
                BotonMapa("🏁 Finalizar embarazo", onClick = onFinalizar)
                BotonMapa("🗑️", onClick = onBorrar)
            }
        }
    }

    GraficosEmbarazo(emb, controles)

    TarjetaMapa {
        Text("Controles prenatales (${controles.size})", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (controles.isEmpty()) {
            Text("Aún no hay controles en este embarazo.", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            Spacer(Modifier.height(6.dp))
            ordenarControles(controles).reversed().forEach { ctl ->
                key(ctl.id) {
                    TarjetaControl(emb, ctl, profesionales, puedeEditar, { onEditarControl(ctl) }, { onBorrarControl(ctl) })
                }
            }
        }
    }
}

/** Un dato grande de la cabecera ("EG hoy 25 sem 3 d"). tono: "alerta" | "ok" | null. */
@Composable
private fun Dato(etiqueta: String, valor: String, detalle: String?, tono: String?, modifier: Modifier) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(etiqueta.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(valor, color = when (tono) { "alerta" -> c.error; "ok" -> c.ok; else -> c.texto }, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        detalle?.let { Text(it, color = c.textoSuave, fontSize = 11.sp) }
    }
}

/** Alertas de apoyo clínico: rojas (actuar) y ámbar (revisar). Nunca bloquean. */
@Composable
internal fun ListaAlertas(alertas: List<AlertaEmbarazo>) {
    if (alertas.isEmpty()) return
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text("APOYO CLÍNICO · NO REEMPLAZA TU CRITERIO", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        alertas.forEach { a ->
            val icono = when (a.nivel) { "alerta" -> "🔴"; "atencion" -> "🟠"; else -> "ℹ️" }
            Text("$icono ${a.texto}" + (a.fuente?.let { " · $it" } ?: ""),
                color = when (a.nivel) { "alerta" -> c.error; "atencion" -> c.pend; else -> c.texto }, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Las tres curvas de `GraficosEmbarazo.tsx` con el gráfico de la app: AU vs EG
 * (banda EG ± 2 cm), ganancia de peso vs EG (banda IOM) y la PA (sistólica y
 * diastólica, líneas de 140/90 y las tomas ≥ 140/90 resaltadas en rojo).
 */
@Composable
private fun GraficosEmbarazo(emb: Embarazo, controles: List<ControlPrenatal>) {
    val c = Sania.colors
    val d = remember(emb) { datacion(emb.entrada) }
    val au = remember(controles, d) { serieAu(controles, d) }
    val cat = categoriaImc(imcEmb(emb.pesoPregestacional, emb.tallaCm))
    val multiple = esMultiple(emb.riesgos)
    val gan = remember(controles, d, cat) { if (cat != null) serieGanancia(controles, d, emb.pesoPregestacional) else emptyList() }
    val pa = remember(controles, d) { puntosPa(controles, d) }
    if (au.isEmpty() && gan.isEmpty() && pa.isEmpty()) return
    TarjetaMapa {
        if (au.isNotEmpty()) {
            val banda = bandaAu().map { BandaGrafico(it.x, it.min, it.max) }
            GraficoLineaHc(
                "Altura uterina vs EG (banda: EG ± 2 cm)", "cm", au.map { PuntoSerieHc(it.fecha, it.y) }, color = c.navy,
                ejeX = EjeXGrafico(au.map { it.x }, minOf(16.0, au.minOf { it.x }), maxOf(41.0, au.maxOf { it.x }), "sem"),
                banda = banda,
                textoPunto = { p, i -> "${numEmb(au[i].x)} sem: ${numEmb(p.valor)} cm" },
            )
        }
        if (gan.isNotEmpty() && cat != null) {
            if (au.isNotEmpty()) Spacer(Modifier.height(12.dp))
            val banda = bandaGanancia(cat, multiple).map { BandaGrafico(it.x, it.min, it.max) }
            GraficoLineaHc(
                "Ganancia de peso vs EG (IOM${if (multiple) " gemelar" else ""}: ${NOMBRE_CATEGORIA_IMC[cat].orEmpty().lowercase()})", "kg",
                gan.map { PuntoSerieHc(it.fecha, it.y) }, color = c.teal,
                ejeX = EjeXGrafico(gan.map { it.x }, 0.0, maxOf(41.0, gan.maxOf { it.x }), "sem"),
                banda = banda,
                textoPunto = { p, i -> "${numEmb(gan[i].x)} sem: ${if (p.valor > 0) "+" else ""}${numEmb(p.valor)} kg" },
            )
        }
        if (pa.isNotEmpty()) {
            if (au.isNotEmpty() || gan.isNotEmpty()) Spacer(Modifier.height(12.dp))
            GraficoLineaHc(
                "Presión arterial vs EG (mmHg)", "mmHg", pa.map { PuntoSerieHc(it.fecha, it.sistolica.toDouble()) }, color = c.navy,
                nombre = "Sistólica",
                serie2 = SerieGrafico("Diastólica", pa.map { PuntoSerieHc(it.fecha, it.diastolica.toDouble()) }, c.teal),
                ejeX = EjeXGrafico(pa.map { it.x }, minOf(8.0, pa.minOf { it.x }), maxOf(41.0, pa.maxOf { it.x }), "sem"),
                lineas = listOf(LineaRefGrafico(PA_SIST_ELEVADA.toDouble(), "$PA_SIST_ELEVADA"), LineaRefGrafico(PA_DIAST_ELEVADA.toDouble(), "$PA_DIAST_ELEVADA")),
                alerta = { serie, i ->
                    pa.getOrNull(i)?.let { p ->
                        val (alta, severa) = if (serie == 0) p.sistolicaAlta to p.sistolicaSevera else p.diastolicaAlta to p.diastolicaSevera
                        if (severa) AlertaPunto.SEVERA else if (alta) AlertaPunto.ELEVADA else null
                    }
                },
                leyendaAlerta = "≥ $PA_SIST_ELEVADA/$PA_DIAST_ELEVADA",
                leyendaSevera = if (pa.any { it.nivel == "severa" }) "Severa ≥ $PA_SIST_SEVERA/$PA_DIAST_SEVERA" else null,
                aviso = resumenAlertasPa(pa),
                descripcion = "Presión arterial por semana de gestación: " + pa.joinToString("; ") { textoPuntoPa(it) },
                textoPunto = { _, i -> textoPuntoPa(pa[i]) },
                alto = 140.dp,
            )
        }
    }
}

// ─── Un control (lista) ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaControl(
    emb: Embarazo, ctl: ControlPrenatal, profesionales: Map<String, String>, puedeEditar: Boolean,
    onEditar: () -> Unit, onBorrar: () -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    val d = remember(emb) { datacion(emb.entrada) }
    val eg = egDelControl(ctl, d)
    val al = remember(ctl, emb) { alertasControl(ctl.copy(egDias = eg), emb) }
    val rojo = al.any { it.nivel == "alerta" }
    val ambar = !rojo && al.isNotEmpty()
    val medidas = listOfNotNull(
        ctl.peso?.let { "⚖️ ${numEmb(it)} kg" },
        ctl.paSistolica?.let { "🩺 PA $it/${ctl.paDiastolica}" },
        ctl.alturaUterina?.let { "📏 AU ${numEmb(it)} cm" },
        ctl.lcf?.let { "💓 $it lpm" },
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
    ) {
        Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }.padding(10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (rojo) "🔴 " else if (ambar) "🟠 " else "") + fechaEmb(ctl.fecha) + (textoEg(eg)?.let { " · $it" } ?: ""),
                    color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
                if (medidas.isNotEmpty()) Text(medidas.joinToString("  "), color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                if (ctl.pendiente) Text("⏳ Pendiente de sincronizar", color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (abierto) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        if (abierto) {
            Column(Modifier.fillMaxWidth().background(c.chipBg).padding(10.dp)) {
                val pares = listOfNotNull(
                    nombreMovimientos(ctl.movimientosFetales)?.let { "Movimientos fetales" to it },
                    nombrePresentacion(ctl.presentacion)?.let { "Presentación" to it },
                    ctl.edema?.let { "Edema" to if (it == "no") "no" else it },
                    ctl.proteinuria?.let { "Proteinuria" to it },
                    ctl.suplementos.takeIf { it.isNotEmpty() }?.let { "Suplementos" to it.joinToString(", ") { s -> nombreSuplemento(s) } },
                    "Signos de alarma explicados" to if (ctl.signosAlarmaExplicados) "sí" else "no",
                    ctl.proximoControl?.let { "Próximo control" to fechaEmb(it) },
                    ctl.terapeutaId?.let { profesionales[it] }?.let { "Atendió" to it },
                )
                pares.forEach { (k, v) -> Text("$k: $v", color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp)) }
                ctl.observaciones?.let { Text("Observaciones: $it", color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                if (al.isNotEmpty()) Box(Modifier.padding(top = 8.dp)) { ListaAlertas(al) }
                if (puedeEditar && !ctl.pendiente) {
                    Spacer(Modifier.height(8.dp))
                    FilaBotonesMapa {
                        BotonMapa("✏️ Editar", onClick = onEditar)
                        BotonMapa("🗑️", onClick = onBorrar)
                    }
                }
            }
        }
    }
}

// ─── Embarazos anteriores ────────────────────────────────────────────────────

@Composable
private fun EmbarazoAnterior(
    emb: Embarazo, controles: List<ControlPrenatal>, profesionales: Map<String, String>,
    onImprimir: () -> Unit, onReabrir: (() -> Unit)?,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    TarjetaMapa {
        Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text((nombreDesenlace(emb.desenlace) ?: "Finalizado") + (emb.fechaFin?.let { " · ${fechaEmb(it)}" } ?: ""),
                    color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("FPP ${fechaEmb(emb.fpp)} · ${controles.size} control${if (controles.size == 1) "" else "es"}" +
                    (formulaObstetrica(emb)?.let { " · $it" } ?: ""), color = c.textoSuave, fontSize = 12.sp)
            }
            Text(if (abierto) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        if (abierto) {
            if (controles.isNotEmpty()) {
                EtiquetaMapa("Controles")
                ordenarControles(controles).reversed().forEach { ctl ->
                    key(ctl.id) { TarjetaControl(emb, ctl, profesionales, false, {}, {}) }
                }
            }
            Spacer(Modifier.height(8.dp))
            FilaBotonesMapa {
                BotonMapa("🖨 Carné perinatal", onClick = onImprimir)
                onReabrir?.let { BotonMapa("↺ Reabrir (lo finalicé por error)", onClick = it) }
            }
        }
    }
}
