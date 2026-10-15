package pe.saniape.app.ui.clinica.obstetricia

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import pe.saniape.app.data.offline.nuevoIdTemporal
import pe.saniape.app.data.staff.AprendidoPlan
import pe.saniape.app.data.staff.BorradorControl
import pe.saniape.app.data.staff.BorradorEmbarazo
import pe.saniape.app.data.staff.CitaVinculable
import pe.saniape.app.data.staff.ControlPrenatal
import pe.saniape.app.data.staff.DESENLACES_EMB
import pe.saniape.app.data.staff.EDEMAS_EMB
import pe.saniape.app.data.staff.Embarazo
import pe.saniape.app.data.staff.EmbarazoRepo
import pe.saniape.app.data.staff.FraseFrecuenteApp
import pe.saniape.app.data.staff.GRUPOS_SANGUINEOS
import pe.saniape.app.data.staff.LimitesControl
import pe.saniape.app.data.staff.LimitesEmbarazo
import pe.saniape.app.data.staff.MOVIMIENTOS_FETALES
import pe.saniape.app.data.staff.MapaGineco
import pe.saniape.app.data.staff.NOMBRE_CATEGORIA_IMC
import pe.saniape.app.data.staff.PRESENTACIONES_FETALES
import pe.saniape.app.data.staff.PROTEINURIAS_EMB
import pe.saniape.app.data.staff.RIESGOS_EMBARAZO
import pe.saniape.app.data.staff.SIGNOS_ALARMA_EMB
import pe.saniape.app.data.staff.SUPLEMENTOS_EMB
import pe.saniape.app.data.staff.aNumeroEmb
import pe.saniape.app.data.staff.alertasControl
import pe.saniape.app.data.staff.auEsperada
import pe.saniape.app.data.staff.avisosEmbarazo
import pe.saniape.app.data.staff.borradorDesdeEmbarazo
import pe.saniape.app.data.staff.categoriaImc
import pe.saniape.app.data.staff.citaParaVincular
import pe.saniape.app.data.staff.controlDeJson
import pe.saniape.app.data.staff.controlLocal
import pe.saniape.app.data.staff.datacion
import pe.saniape.app.data.staff.egDiasEn
import pe.saniape.app.data.staff.embarazoDeJson
import pe.saniape.app.data.staff.esMultiple
import pe.saniape.app.data.staff.evaluarGanancia
import pe.saniape.app.data.staff.fechaEmb
import pe.saniape.app.data.staff.frasesObservacionControl
import pe.saniape.app.data.staff.imcEmb
import pe.saniape.app.data.staff.numEmb
import pe.saniape.app.data.staff.proximoControlSugerido
import pe.saniape.app.data.staff.riesgosSugeridos
import pe.saniape.app.data.staff.suplementosSugeridos
import pe.saniape.app.data.staff.textoEg
import pe.saniape.app.data.staff.textoGananciaRecomendada
import pe.saniape.app.data.staff.validarControl
import pe.saniape.app.data.staff.validarControlEnEmbarazo
import pe.saniape.app.data.staff.validarEmbarazo
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.atencion.CampoTextoClinico
import pe.saniape.app.ui.clinica.mapas.BotonMapa
import pe.saniape.app.ui.clinica.mapas.CajaErroresMapa
import pe.saniape.app.ui.clinica.mapas.ChipMapa
import pe.saniape.app.ui.clinica.mapas.EtiquetaMapa
import pe.saniape.app.ui.clinica.mapas.SegmentadoMapa
import pe.saniape.app.ui.clinica.mapas.TituloSeccionMapa
import pe.saniape.app.ui.clinica.mapas.colorHex
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania
import kotlin.random.Random

// ─── Piezas ──────────────────────────────────────────────────────────────────

/** Campo numérico (teclado numérico o decimal); filtra lo que no sea número. */
@Composable
internal fun CampoNumEmb(
    etiqueta: String, valor: String, onCambio: (String) -> Unit, modifier: Modifier = Modifier,
    decimal: Boolean = false, placeholder: String? = null, ayuda: String? = null, colorAyuda: androidx.compose.ui.graphics.Color? = null, max: Int = 6,
) {
    val c = Sania.colors
    Column(modifier) {
        EtqForm(etiqueta)
        OutlinedTextField(
            value = valor,
            onValueChange = { v -> onCambio(v.filter { ch -> ch.isDigit() || (decimal && (ch == '.' || ch == ',')) }.take(max)) },
            singleLine = true, colors = coloresCampoForm(),
            placeholder = placeholder?.let { p -> { Text(p, color = c.textoSuave, fontSize = 14.sp) } },
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Next),
            textStyle = TextStyle(fontSize = 16.sp, color = c.texto),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = etiqueta },
        )
        ayuda?.let { Text(it, color = colorAyuda ?: c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@Composable
private fun FilaCheck(texto: String, marcado: Boolean, onCambio: (Boolean) -> Unit) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onCambio(!marcado) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = marcado, onCheckedChange = onCambio, colors = CheckboxDefaults.colors(checkedColor = c.navy))
        Text(texto, color = c.texto, fontSize = 13.sp)
    }
}

/** Selector de fecha con "Quitar" opcional (la fecha nunca se teclea). */
@Composable
private fun CampoFecha(etiqueta: String, valor: String?, vacio: String, onAbrir: () -> Unit, onQuitar: (() -> Unit)? = null) {
    val c = Sania.colors
    EtqForm(etiqueta)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { CajaSelectorForm(valor?.let { fechaEmb(it) } ?: vacio, onAbrir) }
        if (valor != null && onQuitar != null) {
            Text("Quitar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onQuitar).padding(horizontal = 10.dp, vertical = 14.dp))
        }
    }
}

private fun String.minIso(otra: String): String = if (this < otra) this else otra
private fun String.maxIso(otra: String): String = if (this > otra) this else otra

/** Una clave de idempotencia por formulario (el mismo control enviado dos veces no se duplica). */
private fun nuevaClaveControl(): String = "cp-" + (1..32).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

// ─── Registrar / editar el embarazo ──────────────────────────────────────────

/**
 * Gemelo de FormEmbarazo.tsx: datación por FUR o ecografía precoz con la FPP y
 * la EG EN VIVO (la misma función que usa el servidor), fórmula obstétrica,
 * grupo y Rh, peso pregestacional y talla (IMC y rango IOM), riesgos (los que
 * se deducen de la edad / Rh / cesáreas, sugeridos) y antecedentes internos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormEmbarazo(
    pacienteId: String,
    hoy: String,
    edad: Int?,
    talla: Int?,
    especialidadId: String?,
    /** El que se edita (null = nuevo). */
    embarazo: Embarazo?,
    onCerrar: () -> Unit,
    onGuardado: (Embarazo) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val nuevo = embarazo == null
    fun s(v: Any?) = when (v) { null -> ""; is Double -> numEmb(v); else -> v.toString() }
    var fur by remember { mutableStateOf(embarazo?.fur) }
    var furIncierta by remember { mutableStateOf(embarazo?.furIncierta ?: false) }
    var conEco by remember { mutableStateOf(embarazo?.ecoFecha != null) }
    var ecoFecha by remember { mutableStateOf(embarazo?.ecoFecha) }
    var ecoSem by remember { mutableStateOf(s(embarazo?.ecoEgSemanas)) }
    var ecoDias by remember { mutableStateOf(s(embarazo?.ecoEgDias)) }
    var g by remember { mutableStateOf(s(embarazo?.gestas)) }
    var p by remember { mutableStateOf(s(embarazo?.partos)) }
    var a by remember { mutableStateOf(s(embarazo?.abortos)) }
    var ces by remember { mutableStateOf(s(embarazo?.cesareas)) }
    var grupo by remember { mutableStateOf(embarazo?.grupoSanguineo) }
    var rh by remember { mutableStateOf(embarazo?.rh) }
    var pesoPre by remember { mutableStateOf(s(embarazo?.pesoPregestacional)) }
    var tallaCm by remember { mutableStateOf(if (embarazo != null) s(embarazo.tallaCm) else s(talla)) }
    var riesgos by remember { mutableStateOf(embarazo?.riesgos ?: riesgosSugeridos(edad = edad)) }
    var antecedentes by remember { mutableStateOf(embarazo?.antecedentes.orEmpty()) }
    var elegir by remember { mutableStateOf<String?>(null) }  // "fur" | "eco"
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }

    val borrador = BorradorEmbarazo(
        pacienteId = pacienteId, especialidadId = if (nuevo) especialidadId else embarazo?.especialidadId,
        fur = fur, furIncierta = furIncierta,
        ecoFecha = if (conEco) ecoFecha else null, ecoEgSemanas = if (conEco) ecoSem.ifEmpty { null } else null,
        ecoEgDias = if (conEco) ecoDias.ifEmpty { null } else null,
        gestas = g.ifEmpty { null }, partos = p.ifEmpty { null }, abortos = a.ifEmpty { null }, cesareas = ces.ifEmpty { null },
        grupoSanguineo = grupo, rh = rh, pesoPregestacional = pesoPre.ifEmpty { null }, tallaCm = tallaCm.ifEmpty { null },
        riesgos = riesgos, antecedentes = antecedentes.trim().ifEmpty { null },
    )
    val d = datacion(borrador.entrada)
    val egHoy = d?.let { egDiasEn(it.inicio, hoy) }
    val imcPre = imcEmb(aNumeroEmb(pesoPre), aNumeroEmb(tallaCm))
    val cat = categoriaImc(imcPre)
    val avisos = avisosEmbarazo(borrador)
    val sugeridos = riesgosSugeridos(edad = edad, rh = rh, cesareas = aNumeroEmb(ces)?.toInt()).filter { it !in riesgos }
    fun toggleRiesgo(id: String) { riesgos = if (id in riesgos) riesgos - id else riesgos + id }

    fun guardar() {
        if (guardando) return
        val e = validarEmbarazo(borrador, hoy)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = EmbarazoRepo.guardarEmbarazo(borrador, embarazo?.id)
            guardando = false
            val fila = (r.cuerpo?.get("embarazo") as? JsonObject)?.let(::embarazoDeJson)
            if (r.registrada && fila != null) {
                Toaster.exito(if (nuevo) "Embarazo registrado" else "Embarazo actualizado")
                onGuardado(fila)
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = if (nuevo) "Registrar embarazo" else "Editar embarazo",
        subtitulo = "🤰 Datación, fórmula obstétrica, peso y riesgos",
        textoAccion = if (guardando) "Guardando…" else if (nuevo) "Registrar embarazo" else "Guardar cambios",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        TituloSeccionMapa("1 · Datación")
        CampoFecha("FUR (fecha de última regla)", fur, "Elegir fecha", { elegir = "fur" }, { fur = null })
        FilaCheck("FUR incierta (no recuerda bien, ciclos irregulares, anticonceptivo reciente)", furIncierta) { furIncierta = it }
        if (!conEco) {
            BotonMapa("＋ Ecografía precoz (corrige la FUR)") { conEco = true }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🩻 Ecografía precoz", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Quitar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.heightIn(min = 44.dp).clickable { conEco = false; ecoFecha = null; ecoSem = ""; ecoDias = "" }
                            .padding(horizontal = 10.dp, vertical = 14.dp))
                }
                CampoFecha("Fecha de la ecografía", ecoFecha, "Elegir fecha", { elegir = "eco" })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoNumEmb("EG que dio: semanas", ecoSem, { ecoSem = it }, Modifier.weight(1f), placeholder = "Ej. 8", max = 2)
                    CampoNumEmb("y días (0–6)", ecoDias, { ecoDias = it.filter { ch -> ch in '0'..'6' }.take(1) }, Modifier.weight(1f), placeholder = "0", max = 1)
                }
            }
        }
        d?.let { dt ->
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                    .padding(10.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text("FPP ${fechaEmb(dt.fpp)}" + (egHoy?.let { " · EG hoy ${textoEg(it)}" } ?: ""), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(dt.motivo, color = if (dt.corregidaPorEco) c.pend else c.textoSuave, fontSize = 12.sp,
                    fontWeight = if (dt.corregidaPorEco) FontWeight.Bold else FontWeight.Normal)
            }
        }

        TituloSeccionMapa("2 · Fórmula obstétrica y grupo")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CampoNumEmb("Gestas", g, { g = it }, Modifier.weight(1f), placeholder = "1", max = 2)
            CampoNumEmb("Partos", p, { p = it }, Modifier.weight(1f), placeholder = "0", max = 2)
            CampoNumEmb("Abortos", a, { a = it }, Modifier.weight(1f), placeholder = "0", max = 2)
            CampoNumEmb("Cesáreas", ces, { ces = it }, Modifier.weight(1f), placeholder = "0", max = 2)
        }
        Text("Gestas incluye el embarazo actual.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        avisos.forEach { Text("⚠ $it", color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        EtiquetaMapa("Grupo sanguíneo")
        SegmentadoMapa(GRUPOS_SANGUINEOS.map { it to it }, grupo, { grupo = it })
        EtiquetaMapa("Factor Rh")
        SegmentadoMapa(listOf("+" to "Rh +", "-" to "Rh −"), rh, { rh = it }, colores = mapOf("-" to colorHex("#dc2626")))

        TituloSeccionMapa("3 · Peso antes del embarazo")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumEmb("Peso pregestacional (kg)", pesoPre, { pesoPre = it }, Modifier.weight(1f), decimal = true, placeholder = "Ej. 58,5")
            CampoNumEmb("Talla (cm)", tallaCm, { tallaCm = it }, Modifier.weight(1f), decimal = true, placeholder = "Ej. 156")
        }
        if (imcPre != null && cat != null) {
            Text("IMC ${numEmb(imcPre)} · ${NOMBRE_CATEGORIA_IMC[cat]} → ganancia recomendada en todo el embarazo: ${textoGananciaRecomendada(cat, esMultiple(riesgos))} · IOM 2009",
                color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            Text("Con el peso y la talla se dibuja la curva de ganancia de peso recomendada.", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp))
        }

        TituloSeccionMapa("4 · Antecedentes y factores de riesgo")
        if (sugeridos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Sugeridos:", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                sugeridos.forEach { id ->
                    Text("＋ ${RIESGOS_EMBARAZO.firstOrNull { it.id == id }?.nombre ?: id}", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.heightIn(min = 44.dp).clickable { toggleRiesgo(id) }.padding(horizontal = 4.dp, vertical = 12.dp))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RIESGOS_EMBARAZO.forEach { r ->
                val activo = r.id in riesgos
                ChipMapa((if (activo) "✓ " else "") + r.nombre, activo) { toggleRiesgo(r.id) }
            }
        }
        Spacer(Modifier.height(10.dp))
        EtqForm("Otros antecedentes (interno, no lo ve la paciente)")
        OutlinedTextField(
            value = antecedentes, onValueChange = { antecedentes = it.take(LimitesEmbarazo.ANTECEDENTES) },
            minLines = 2, colors = coloresCampoForm(), textStyle = TextStyle(fontSize = 15.sp, color = c.texto),
            placeholder = { Text("Ej. Preeclampsia en la 2.ª gestación (2021), parto a las 35 semanas", color = c.textoSuave, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
        )
        CajaErroresMapa(errores)
    }

    elegir?.let { cual ->
        DialogoFecha(
            onElegir = { f -> val v = f.minIso(hoy); if (cual == "fur") fur = v else ecoFecha = v },
            onCerrar = { elegir = null },
            inicial = (if (cual == "fur") fur else ecoFecha) ?: hoy,
        )
    }
}

// ─── Finalizar (parto / cesárea / aborto / otro) ─────────────────────────────

@Composable
internal fun FinalizarEmbarazo(emb: Embarazo, hoy: String, onCerrar: () -> Unit, onHecho: (Embarazo) -> Unit) {
    val scope = rememberCoroutineScope()
    val inicio = remember(emb) { datacion(emb.entrada)?.inicio }
    var desenlace by remember { mutableStateOf<String?>(null) }
    var fecha by remember { mutableStateOf(hoy) }
    var elegir by remember { mutableStateOf(false) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    fun guardar() {
        if (guardando || desenlace == null) return
        // La misma regla que la API: fin no futuro ni anterior al inicio del embarazo.
        val e = validarEmbarazo(borradorDesdeEmbarazo(emb).copy(estado = "finalizado", desenlace = desenlace, fechaFin = fecha), hoy)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = EmbarazoRepo.cambiarEstado(emb.id, finalizar = true, desenlace = desenlace, fechaFin = fecha)
            guardando = false
            val fila = (r.cuerpo?.get("embarazo") as? JsonObject)?.let(::embarazoDeJson)
            if (r.registrada && fila != null) { Toaster.exito("Embarazo finalizado"); onHecho(fila) }
            else { val msg = r.rechazo?.error ?: "No se pudo guardar"; errores = listOf(msg); Toaster.error(msg) }
        }
    }
    DialogoForm(
        titulo = "Finalizar embarazo",
        subtitulo = "🏁 ¿Cómo terminó el embarazo?",
        textoAccion = if (guardando) "Guardando…" else "Finalizar",
        accionHabilitada = !guardando && desenlace != null,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtiquetaMapa("Desenlace")
        SegmentadoMapa(DESENLACES_EMB.map { it.id to it.nombre }, desenlace, { desenlace = it })
        Spacer(Modifier.height(10.dp))
        CampoFecha("Fecha", fecha, "Elegir fecha", { elegir = true })
        CajaErroresMapa(errores)
    }
    if (elegir) DialogoFecha(
        onElegir = { f -> fecha = f.minIso(hoy).let { v -> inicio?.let { v.maxIso(it) } ?: v } },
        onCerrar = { elegir = false }, inicial = fecha,
    )
}

// ─── Control prenatal (nuevo o editar) ───────────────────────────────────────

/**
 * Gemelo de FormControl.tsx — un control en un minuto: fecha (hoy) con la EG
 * calculada (editable), peso (el del control anterior SOLO como referencia),
 * PA, AU con su rango esperado, LCF; chips de movimientos, presentación,
 * edema, proteinuria y suplementos (los del anterior o los que tocan por la
 * EG); signos de alarma; observaciones que aprenden y el próximo control
 * sugerido por la NTS 105-MINSA. Alertas de apoyo EN VIVO (la misma función
 * que la ficha). El control nuevo se vincula a la cita de gineco del día y va
 * por la cola offline con su clave de idempotencia.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormControl(
    embarazo: Embarazo,
    pacienteId: String,
    hoy: String,
    /** El que se edita (null = nuevo). */
    control: ControlPrenatal?,
    /** El control anterior (referencia de peso y suplementos). */
    anterior: ControlPrenatal?,
    citasUsadas: Set<String>,
    mapaGineco: MapaGineco,
    clinicaId: String?,
    onCerrar: () -> Unit,
    onGuardado: (ControlPrenatal, Boolean) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val nuevo = control == null
    val d = remember(embarazo) { datacion(embarazo.entrada) }
    fun s(v: Any?) = when (v) { null -> ""; is Double -> numEmb(v); else -> v.toString() }
    var fecha by remember { mutableStateOf(control?.fecha?.takeIf { it.isNotBlank() } ?: hoy) }
    val egCalculada = d?.let { egDiasEn(it.inicio, fecha) }
    // EG: la guardada si se corrigió a mano; si no, la calculada.
    var egManual by remember {
        mutableStateOf(
            if (control?.egDias != null && control.egDias != d?.let { egDiasEn(it.inicio, control.fecha) })
                (control.egDias / 7).toString() to (control.egDias % 7).toString() else null,
        )
    }
    val eg = egManual?.takeIf { it.first.isNotEmpty() }?.let { (it.first.toIntOrNull() ?: 0) * 7 + (it.second.toIntOrNull() ?: 0) } ?: egCalculada
    // El peso anterior es REFERENCIA (placeholder), no un valor: si hoy no se pesa,
    // el control no queda con el peso de otro día.
    var peso by remember { mutableStateOf(s(control?.peso)) }
    val pesoAnterior = anterior?.peso
    val clave = remember { nuevaClaveControl() }
    var sist by remember { mutableStateOf(s(control?.paSistolica)) }
    var diast by remember { mutableStateOf(s(control?.paDiastolica)) }
    var au by remember { mutableStateOf(s(control?.alturaUterina)) }
    var lcf by remember { mutableStateOf(s(control?.lcf)) }
    var mov by remember { mutableStateOf(control?.movimientosFetales) }
    var pres by remember { mutableStateOf(control?.presentacion) }
    var edema by remember { mutableStateOf(control?.edema) }
    var prot by remember { mutableStateOf(control?.proteinuria) }
    var suple by remember {
        mutableStateOf(control?.suplementos ?: anterior?.suplementos?.takeIf { it.isNotEmpty() } ?: suplementosSugeridos(egCalculada))
    }
    var signos by remember { mutableStateOf(control?.signosAlarma.orEmpty()) }
    var explicados by remember { mutableStateOf(control?.signosAlarmaExplicados ?: false) }
    var obs by remember { mutableStateOf(control?.observaciones.orEmpty()) }
    var proximo by remember { mutableStateOf(control?.proximoControl) }
    var elegir by remember { mutableStateOf<String?>(null) }   // "fecha" | "proximo"
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var aprendidos by remember { mutableStateOf<List<AprendidoPlan>>(emptyList()) }
    LaunchedEffect(Unit) { aprendidos = EmbarazoRepo.sugerencias(clinicaId) }
    // Cita a vincular: la ÚNICA de gineco-obstetricia de la fecha que aún no tenga control.
    var citas by remember { mutableStateOf<List<CitaVinculable>>(emptyList()) }
    if (nuevo) LaunchedEffect(fecha) { citas = EmbarazoRepo.citasDelDia(pacienteId, fecha) }
    val citaVinculo = if (control != null) control.citaId else citaParaVincular(citas, fecha, citasUsadas, mapaGineco)

    val proximoSugerido = proximoControlSugerido(fecha, eg)
    val ref = auEsperada(eg)
    val ganancia = evaluarGanancia(aNumeroEmb(peso), embarazo.pesoPregestacional, embarazo.tallaCm, eg, esMultiple(embarazo.riesgos))
    val alertas = alertasControl(
        ControlPrenatal(
            fecha = fecha, egDias = eg, peso = aNumeroEmb(peso), paSistolica = aNumeroEmb(sist)?.toInt(), paDiastolica = aNumeroEmb(diast)?.toInt(),
            alturaUterina = aNumeroEmb(au), lcf = aNumeroEmb(lcf)?.toInt(), movimientosFetales = mov, proteinuria = prot, edema = edema,
            signosAlarma = signos,
        ),
        embarazo,
    )
    val finMax = embarazo.fechaFin?.minIso(hoy) ?: hoy
    fun toggle(lista: List<String>, id: String) = if (id in lista) lista - id else lista + id

    fun guardar() {
        if (guardando) return
        val b = BorradorControl(
            embarazoId = embarazo.id, fecha = fecha,
            egDias = if (egManual?.first?.isNotEmpty() == true) eg?.toString() else null,
            peso = peso.ifEmpty { null }, paSistolica = sist.ifEmpty { null }, paDiastolica = diast.ifEmpty { null },
            alturaUterina = au.ifEmpty { null }, lcf = lcf.ifEmpty { null },
            movimientosFetales = mov, presentacion = pres, edema = edema, proteinuria = prot,
            suplementos = suple, signosAlarma = signos, signosAlarmaExplicados = explicados,
            observaciones = obs.trim().ifEmpty { null }, proximoControl = proximo ?: proximoSugerido,
            citaId = if (nuevo) citaVinculo else null, clave = if (nuevo) clave else null,
        )
        val e = validarControl(b, hoy) + validarControlEnEmbarazo(fecha, embarazo, editando = !nuevo)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = if (nuevo) EmbarazoRepo.crearControl(b) else EmbarazoRepo.editarControl(b, control!!.id)
            guardando = false
            val fila = (r.cuerpo?.get("control") as? JsonObject)?.let(::controlDeJson)
            when {
                r.registrada && fila != null -> {
                    EmbarazoRepo.olvidarSugerencias()
                    Toaster.exito(if (nuevo) "Control registrado" else "Control actualizado")
                    onGuardado(fila, nuevo)
                }
                // Sin señal: quedó en la cola (la misma clave en cada reintento). Se muestra ya, marcado pendiente.
                r.registrada && r.encolada && nuevo -> onGuardado(controlLocal(b, d, hoy, nuevoIdTemporal(), pacienteId), true)
                else -> {
                    val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                    errores = listOf(msg)
                    if (r.rechazo != null) Toaster.error(msg)
                }
            }
        }
    }

    DialogoForm(
        titulo = if (nuevo) "Nuevo control prenatal" else "Editar control del ${fechaEmb(control!!.fecha)}",
        subtitulo = "🩺 Medidas, examen, signos de alarma y próximo control",
        textoAccion = if (guardando) "Guardando…" else if (nuevo) "Guardar control" else "Guardar cambios",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        CampoFecha("Fecha", fecha, "Elegir fecha", { elegir = "fecha" })
        Spacer(Modifier.height(8.dp))
        EtqForm("Edad gestacional")
        val em = egManual
        if (em == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(textoEg(egCalculada) ?: "—", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(if (d != null) "por ${if (d.metodo == "eco") "ecografía" else "FUR"}" else "sin datación", color = c.textoSuave, fontSize = 11.sp)
                }
                Text("✏️ Corregir", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).clickable {
                        egManual = (egCalculada?.let { (it / 7).toString() } ?: "") to (egCalculada?.let { (it % 7).toString() } ?: "0")
                    }.padding(horizontal = 10.dp, vertical = 14.dp))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                CampoNumEmb("Semanas", em.first, { egManual = it.take(2) to (egManual?.second ?: "0") }, Modifier.weight(1f), max = 2)
                CampoNumEmb("Días", em.second, { egManual = (egManual?.first ?: "") to it.filter { ch -> ch in '0'..'6' }.take(1) }, Modifier.weight(1f), max = 1)
                Text("↺ Calculada", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 44.dp).clickable { egManual = null }.padding(horizontal = 6.dp, vertical = 14.dp))
            }
        }
        if (citaVinculo != null && nuevo) {
            Text("🔗 Queda vinculado a la cita del ${fechaEmb(fecha)}.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }

        TituloSeccionMapa("Medidas")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumEmb("Peso (kg)", peso, { peso = it }, Modifier.weight(1f), decimal = true,
                placeholder = if (pesoAnterior != null && nuevo) numEmb(pesoAnterior) else "Ej. 64,5",
                ayuda = ganancia?.let { "${if (it.ganancia > 0) "+" else ""}${numEmb(it.ganancia)} kg (esperado ${numEmb(it.banda.min)}–${numEmb(it.banda.max)})" }
                    ?: if (pesoAnterior != null && nuevo) "anterior: ${numEmb(pesoAnterior)} kg" else null,
                colorAyuda = ganancia?.let { if (it.estado == "dentro") c.ok else c.pend })
            CampoNumEmb("Altura uterina (cm)", au, { au = it }, Modifier.weight(1f), decimal = true,
                placeholder = ref?.let { numEmb(kotlin.math.floor(it.esperada + 0.5)) } ?: "cm", max = 4,
                ayuda = ref?.let { "esperada ${numEmb(it.min)}–${numEmb(it.max)} cm" })
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumEmb("PA sistólica", sist, { sist = it }, Modifier.weight(1f), placeholder = "110", max = 3)
            CampoNumEmb("PA diastólica", diast, { diast = it }, Modifier.weight(1f), placeholder = "70", max = 3)
            CampoNumEmb("LCF (lpm)", lcf, { lcf = it }, Modifier.weight(1f), placeholder = "140", max = 3, ayuda = "normal 110–160")
        }

        TituloSeccionMapa("Examen")
        EtiquetaMapa("Movimientos fetales")
        SegmentadoMapa(MOVIMIENTOS_FETALES.map { it.id to it.nombre }, mov, { mov = it },
            colores = mapOf("presentes" to c.ok, "disminuidos" to colorHex("#d97706"), "ausentes" to colorHex("#dc2626")))
        EtiquetaMapa("Presentación")
        SegmentadoMapa(PRESENTACIONES_FETALES.map { it.id to it.nombre }, pres, { pres = it })
        EtiquetaMapa("Edema")
        SegmentadoMapa(EDEMAS_EMB.map { it.id to it.nombre }, edema, { edema = it }, colores = mapOf("+++" to colorHex("#dc2626")))
        EtiquetaMapa("Proteinuria (tira)")
        SegmentadoMapa(PROTEINURIAS_EMB.map { it.id to it.nombre }, prot, { prot = it },
            colores = mapOf("+" to colorHex("#d97706"), "++" to colorHex("#dc2626"), "+++" to colorHex("#dc2626")))
        EtiquetaMapa("Suplementos indicados")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SUPLEMENTOS_EMB.forEach { o ->
                val activo = o.id in suple
                ChipMapa((if (activo) "✓ " else "") + o.nombre, activo) { suple = toggle(suple, o.id) }
            }
        }
        EtiquetaMapa("Signos de alarma que refiere")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SIGNOS_ALARMA_EMB.forEach { o ->
                val activo = o.id in signos
                ChipMapa((if (activo) "⚠ " else "") + o.nombre, activo, colorActivo = colorHex("#dc2626")) { signos = toggle(signos, o.id) }
            }
        }
        FilaCheck("Se le explicaron los signos de alarma", explicados) { explicados = it }
        Spacer(Modifier.height(6.dp))
        val frases = remember(aprendidos) { frasesObservacionControl(aprendidos) }
        val chips = remember(frases) { frases.chips.map { FraseFrecuenteApp("obstetricia", it.texto, 0) } }
        CampoTextoClinico(
            label = "📝 Observaciones (interno)", valor = obs, onChange = { obs = it.take(LimitesControl.OBSERVACIONES) },
            frases = chips, soloLectura = false, minLineas = 2, placeholder = "Ej. Gestante asintomática, se solicita ecografía",
            porLinea = frases.porLinea, opciones = frases.opciones, chipsVisibles = 6,
        )

        TituloSeccionMapa("Próximo control")
        CampoFecha("Próximo control", proximo ?: proximoSugerido, "Elegir fecha", { elegir = "proximo" },
            onQuitar = if (proximo != null) ({ proximo = null }) else null)
        Text("Sugerido ${fechaEmb(proximoSugerido)}: mensual hasta las 32 semanas, quincenal hasta las 36, semanal después (NTS 105-MINSA).",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

        if (alertas.isNotEmpty()) Box(Modifier.padding(top = 12.dp)) { ListaAlertas(alertas) }
        CajaErroresMapa(errores)
    }

    elegir?.let { cual ->
        DialogoFecha(
            onElegir = { f ->
                if (cual == "fecha") {
                    // Dentro del embarazo: desde su inicio hasta hoy (o su fin, si está finalizado).
                    fecha = f.minIso(finMax).let { v -> d?.inicio?.let { v.maxIso(it) } ?: v }
                } else {
                    proximo = if (f <= fecha) null else f
                }
            },
            onCerrar = { elegir = null },
            inicial = if (cual == "fecha") fecha else proximo ?: proximoSugerido,
        )
    }
}
