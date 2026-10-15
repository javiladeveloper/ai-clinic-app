package pe.saniape.app.ui.clinica.pediatria

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import pe.saniape.app.data.staff.AVISO_ANTES_DEL_TERMINO
import pe.saniape.app.data.staff.AVISO_FIN_CORRECCION
import pe.saniape.app.data.staff.BorradorMedicion
import pe.saniape.app.data.staff.EdadGestacional
import pe.saniape.app.data.staff.LecturaEg
import pe.saniape.app.data.staff.MESES_FIN_CORRECCION
import pe.saniape.app.data.staff.edadCurvaMeses
import pe.saniape.app.data.staff.edadParaEvaluarEntre
import pe.saniape.app.data.staff.egValida
import pe.saniape.app.data.staff.esPrematuro
import pe.saniape.app.data.staff.mesesCumplidos
import pe.saniape.app.data.staff.primeraSinCorreccion
import pe.saniape.app.data.staff.textoEdadCorregida
import pe.saniape.app.data.staff.textoEdadEvaluada
import pe.saniape.app.data.staff.textoEg
import pe.saniape.app.data.staff.textoSemanasDias
import pe.saniape.app.data.staff.EvaluacionCrecimiento
import pe.saniape.app.data.staff.IndicadorCrecimiento
import pe.saniape.app.data.staff.MESES_MAX
import pe.saniape.app.data.staff.MedicionCorporal
import pe.saniape.app.data.staff.MedicionCrecimiento
import pe.saniape.app.data.staff.MedicionesRepo
import pe.saniape.app.data.staff.PediatriaRepo
import pe.saniape.app.data.staff.TablasOms
import pe.saniape.app.data.staff.curvaReferencia
import pe.saniape.app.data.staff.edadEnDias
import pe.saniape.app.data.staff.evaluarMedicion
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.htmlCurvaCrecimiento
import pe.saniape.app.data.staff.indicadoresConCurva
import pe.saniape.app.data.staff.medicionDeJson
import pe.saniape.app.data.staff.mesesDeDias
import pe.saniape.app.data.staff.numeroHc
import pe.saniape.app.data.staff.numeroMedicion
import pe.saniape.app.data.staff.ordenarMediciones
import pe.saniape.app.data.staff.puntosPaciente
import pe.saniape.app.data.staff.textoCampoMedicion
import pe.saniape.app.data.staff.textoEdadEntre
import pe.saniape.app.data.staff.textoZ
import pe.saniape.app.data.staff.validarMedicion
import pe.saniape.app.data.staff.ventanaCurva
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.fisio.CampoTextoEval
import pe.saniape.app.ui.clinica.historia.fechaHc
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/** Una medición corporal → lo que evalúa el crecimiento. */
internal fun MedicionCorporal.aCrecimiento() = MedicionCrecimiento(fecha, peso, talla, perimetroCefalico)

/**
 * Pestaña 👶 Crecimiento de la ficha (solo pediatría, ver `pacienteEsPediatria`).
 * Gemela de CrecimientoTab.tsx de la web:
 *  · registro rápido de peso, talla y perímetro cefálico (la API de medidas,
 *    la misma de ⚖️ Medidas);
 *  · los últimos z-score OMS con su clasificación (NTS 238-MINSA) — APOYO;
 *  · las curvas OMS (P/E, T/E, P/T, IMC/E, PC/E) con los puntos del niño;
 *  · el historial y, debajo, el 💉 carné de vacunas.
 *
 * Carga SOLO con la pestaña abierta (la monta el `when` de la ficha): las
 * mediciones (una consulta) y las tablas de la OMS (se convierten a números la
 * primera vez, fuera del hilo de la UI). Sin sexo o sin fecha de nacimiento
 * pide el dato en 1 toque antes de las curvas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrecimientoTab(
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    fechaNacimiento: String?,
    /** "F" / "M" / null. */
    sexo: String?,
    /** `puede('pacientes') || puede('sesiones')` y ficha no dada de baja (como el API). */
    puedeEditar: Boolean,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    /** Tras elegir el sexo aquí mismo (la ficha lo guarda en su estado). */
    onSexoGuardado: (String) -> Unit,
    /** Tras registrar la fecha de nacimiento aquí mismo (la ficha recarga al paciente). */
    onNacimientoGuardado: (String) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<MedicionCorporal>?>(null) }
    var fallo by remember(pacienteId) { mutableStateOf(false) }
    LaunchedEffect(pacienteId, token) {
        val r = MedicionesRepo.listar(pacienteId)
        if (r != null) { items = r; fallo = false } else fallo = items == null
        cargando = false
    }
    // Las tablas de la OMS: se arman la primera vez (perezosas), fuera del hilo de la UI.
    var tablas by remember { mutableStateOf<TablasOms?>(null) }
    LaunchedEffect(Unit) { tablas = withContext(Dispatchers.Default) { TablasOms.cargar() } }
    // 🍼 Edad gestacional al nacer: lectura aparte y tolerante (una base sin las
    // columnas → no disponible: todo como antes, sin la tarjeta).
    var lecturaEg by remember(pacienteId) { mutableStateOf<LecturaEg?>(null) }
    LaunchedEffect(pacienteId) { lecturaEg = PediatriaRepo.edadGestacionalDe(pacienteId) }
    val eg = lecturaEg?.eg
    var registrar by remember { mutableStateOf(false) }
    var confirmarBorrar by remember { mutableStateOf<MedicionCorporal?>(null) }
    var borrando by remember { mutableStateOf(false) }

    if (cargando && items == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 120, 220).forEach { h ->
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            }
        }
        return
    }
    if (fallo) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No se pudieron cargar las medidas", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { cargando = true; token++ }.padding(8.dp))
        }
        return
    }

    val lista = remember(items) { ordenarMediciones(items.orEmpty()) }
    val nac = fechaNacimiento?.take(10)
    val hoy = hoyClinicaIso()
    val evaluaciones: Map<String, EvaluacionCrecimiento> = remember(tablas, sexo, nac, lista, eg) {
        val t = tablas
        if (t == null || sexo == null || nac == null) emptyMap()
        else lista.mapNotNull { m -> evaluarMedicion(t, sexo, nac, m.aCrecimiento(), eg)?.let { m.id to it } }.toMap()
    }
    val edadHoy = edadEnDias(nac, hoy)
    val fuera = edadHoy != null && mesesDeDias(edadHoy.toDouble()) > MESES_MAX
    val evUltima = lista.lastOrNull { it.id in evaluaciones }?.let { evaluaciones[it.id] }
    // Prematuro que ya cumplió 2 años: desde esa medición se evalúa con la edad real (el punto "salta").
    val finCorreccion = remember(evaluaciones, eg) { primeraSinCorreccion(evaluaciones.values.toList(), eg) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("👶 Crecimiento", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)

        if (nac == null) AvisoNacimiento(pacienteId, puedeEditar, onNacimientoGuardado)
        if (sexo == null) AvisoSexo(pacienteId, puedeEditar, onSexoGuardado)
        if (fuera) Tarjeta { Text("Tiene más de 19 años: las curvas de la OMS no aplican.", color = c.textoSuave, fontSize = 12.sp) }
        // 🍼 Prematuridad: se ofrece mientras corrige (aún no cumple 2 años) o si ya hay dato.
        if (lecturaEg?.disponible == true && nac != null && edadHoy != null &&
            (eg != null || (mesesCumplidos(nac, hoy) ?: MESES_FIN_CORRECCION) < MESES_FIN_CORRECCION)
        ) {
            TarjetaNacimiento(pacienteId, nac, hoy, eg, puedeEditar) { nueva -> lecturaEg = LecturaEg(nueva, disponible = true) }
        }

        val t = tablas
        val puedeImprimir = lista.isNotEmpty() && sexo != null && nac != null && t != null
        if (puedeEditar || puedeImprimir) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (puedeEditar) BotonPed("＋ Registrar peso y talla", primario = true) { registrar = true }
                if (puedeImprimir) BotonPed("🖨 Curva de crecimiento") {
                    acciones.abrirHtml(
                        htmlCurvaCrecimiento(t!!, sexo!!, nac!!, lista.map { it.aCrecimiento() }, hoy, pacienteNombre, pacienteDocumento,
                            clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal, eg),
                        "Curva de crecimiento",
                    )
                }
            }
        }

        if (lista.isEmpty()) {
            Tarjeta {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👶", fontSize = 30.sp)
                    Text("Aún no hay mediciones", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text("Anota el peso, la talla y el perímetro cefálico en cada control para ver sus curvas de la OMS.",
                        color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        evUltima?.let { UltimosZ(it, eg, finCorreccion = it.fecha == finCorreccion) }
        val mesesCurva = evUltima?.let { edadCurvaMeses(it) }
        if (t != null && sexo != null && nac != null && mesesCurva != null) {
            Curvas(t, sexo, nac, eg, lista.map { it.aCrecimiento() }, mesesCurva)
        }
        if (lista.isNotEmpty()) {
            Tarjeta {
                TituloPed("🗂️ Mediciones (${lista.size})")
                lista.asReversed().forEachIndexed { i, m ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                    FilaMedicionPed(m, evaluaciones[m.id], nac, puedeEditar, finCorreccion == m.fecha) { confirmarBorrar = m }
                }
            }
        }

        VacunasCarne(
            pacienteId = pacienteId, pacienteNombre = pacienteNombre, pacienteDocumento = pacienteDocumento,
            fechaNacimiento = nac, puedeEditar = puedeEditar, clinicaNombre = clinicaNombre,
            clinicaDireccion = clinicaDireccion, logoUrl = logoUrl, colorPrincipal = colorPrincipal,
        )
    }

    if (registrar) {
        FormularioCrecimiento(
            pacienteId = pacienteId, hoy = hoy, nac = nac, sexo = sexo, tablas = tablas, eg = eg,
            tallaPrevia = remember(lista) { lista.lastOrNull { it.talla != null }?.talla },
            onCerrar = { registrar = false },
            onGuardada = { nueva ->
                registrar = false
                nueva?.let { n -> items = ordenarMediciones(lista.filter { it.id != n.id } + n) }
                token++
            },
        )
    }
    confirmarBorrar?.let { m ->
        AlertDialog(
            onDismissRequest = { if (!borrando) confirmarBorrar = null },
            title = { Text("¿Eliminar esta medición?", fontWeight = FontWeight.Bold) },
            text = { Text("${fechaHc(m.fecha)} · ${lineaPed(m)}", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = MedicionesRepo.borrar(m.id)
                        borrando = false
                        if (r.registrada) {
                            confirmarBorrar = null
                            items = lista.filter { it.id != m.id }
                            Toaster.exito("Medición eliminada")
                            token++
                        } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !borrando, onClick = { confirmarBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

private fun lineaPed(m: MedicionCorporal): String = listOfNotNull(
    m.peso?.let { "Peso ${numeroHc(it)} kg" },
    m.talla?.let { "Talla ${numeroHc(it)} cm" },
    m.perimetroCefalico?.let { "P. cefálico ${numeroHc(it)} cm" },
).joinToString(" · ").ifBlank { "—" }

// ─── Datos que faltan (1 toque) ──────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvisoSexo(pacienteId: String, puedeEditar: Boolean, onGuardado: (String) -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var guardando by remember { mutableStateOf<String?>(null) }
    fun elegir(s: String) {
        if (guardando != null) return
        guardando = s
        scope.launch {
            val r = PediatriaRepo.actualizarFicha(pacienteId, sexo = s, tocarSexo = true)
            guardando = null
            if (r.registrada) { Toaster.exito("Sexo guardado"); onGuardado(s) }
            else Toaster.error(r.rechazo?.error ?: "No se pudo guardar")
        }
    }
    TarjetaAviso {
        Text(
            "Las curvas de la OMS son por sexo. " + if (puedeEditar) "¿Es niña o niño?" else "Falta registrar el sexo del paciente.",
            color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        if (puedeEditar) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPed(if (guardando == "F") "Guardando…" else "👧 Femenino", habilitado = guardando == null) { elegir("F") }
                BotonPed(if (guardando == "M") "Guardando…" else "👦 Masculino", habilitado = guardando == null) { elegir("M") }
            }
        }
    }
}

@Composable
private fun AvisoNacimiento(pacienteId: String, puedeEditar: Boolean, onGuardado: (String) -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var elegir by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    TarjetaAviso {
        Text("Falta la fecha de nacimiento.", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Con ella se calculan las curvas y el carné de vacunas por edad.", color = c.textoSuave, fontSize = 12.sp)
        if (puedeEditar) {
            Box(Modifier.padding(top = 8.dp)) {
                BotonPed(if (guardando) "Guardando…" else "📅 Registrar fecha de nacimiento", habilitado = !guardando) { elegir = true }
            }
        }
    }
    if (elegir) DialogoFecha(onElegir = { f ->
        if (f > hoyClinicaIso()) { Toaster.error("La fecha de nacimiento no puede ser futura."); return@DialogoFecha }
        if (guardando) return@DialogoFecha
        guardando = true
        scope.launch {
            val r = PediatriaRepo.actualizarFicha(pacienteId, fechaNacimiento = f)
            guardando = false
            if (r.registrada) { Toaster.exito("Fecha de nacimiento guardada"); onGuardado(f) }
            else Toaster.error(r.rechazo?.error ?: "No se pudo guardar")
        }
    }, onCerrar = { elegir = false })
}

// ─── 🍼 Prematuridad: edad gestacional al nacer ──────────────────────────────

/** Las semanas que más se registran (1 toque); otra se escribe. Como la web. */
private val SEMANAS_RAPIDAS = listOf(28, 30, 32, 34, 35, 36)

/**
 * 🍼 Edad gestacional al nacer (gemela de `Nacimiento` de CrecimientoTab.tsx).
 * Sin dato: "¿Nació prematuro?". Con dato: "Prematuro de 32+4 sem · hoy: 3
 * meses de edad corregida" y Editar. Guarda por /api/staff/paciente/actualizar.
 */
@Composable
private fun TarjetaNacimiento(
    pacienteId: String, nac: String, hoy: String, eg: EdadGestacional?, puedeEditar: Boolean,
    onGuardada: (EdadGestacional?) -> Unit,
) {
    val c = Sania.colors
    var editando by remember { mutableStateOf(false) }
    Tarjeta {
        if (eg == null) {
            Text("🍼 ¿Nació prematuro?", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Con las semanas al nacer, las curvas usan la edad corregida hasta los 2 años.", color = c.textoSuave, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp))
            if (puedeEditar) Box(Modifier.padding(top = 8.dp)) { BotonPed("Registrar semanas al nacer") { editando = true } }
        } else {
            val e = edadParaEvaluarEntre(nac, hoy, eg)
            val estado = when {
                !esPrematuro(eg) -> "A término: se evalúa con la edad cronológica."
                e == null -> ""
                e.antesDelTermino -> "Hoy: ${textoSemanasDias(e.postmenstrualDias!!)} de edad posmenstrual (aún antes del término)."
                e.usaEdadCorregida -> "Hoy: ${textoEdadCorregida(nac, hoy, eg)} de edad corregida (${textoEdadEntre(nac, hoy)} cronológica)."
                else -> "Ya cumplió 2 años: se evalúa con la edad real."
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🍼 " + if (esPrematuro(eg)) "Prematuro de ${textoEg(eg)}" else "Nació de ${textoEg(eg)}",
                        color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (estado.isNotEmpty()) Text(estado, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (puedeEditar) Box(Modifier.padding(start = 8.dp)) { BotonPed("Editar") { editando = true } }
            }
        }
    }
    if (editando) {
        DialogoEdadGestacional(pacienteId, nac, hoy, eg, onCerrar = { editando = false }) { nueva ->
            editando = false
            onGuardada(nueva)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoEdadGestacional(
    pacienteId: String, nac: String, hoy: String, eg: EdadGestacional?,
    onCerrar: () -> Unit, onGuardada: (EdadGestacional?) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var semanas by remember { mutableStateOf(eg?.semanas?.toString().orEmpty()) }
    // Lo escrito en "Otra": propio, así no se vacía al pasar por 28/30/32… mientras se teclea.
    var otra by remember { mutableStateOf(eg?.semanas?.takeIf { it !in SEMANAS_RAPIDAS }?.toString().orEmpty()) }
    var dias by remember { mutableStateOf(eg?.dias ?: 0) }
    var confirmarQuitar by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val sem = semanas.trim().takeIf { it.length == 2 && it.all { ch -> ch.isDigit() } }?.toInt()
    val valida = egValida(sem, dias)
    val nuevo = if (valida) EdadGestacional(sem!!, dias) else null
    val vista = nuevo?.let { edadParaEvaluarEntre(nac, hoy, it) }
    val textoVista = when {
        nuevo == null -> null
        !esPrematuro(nuevo) -> "${textoEg(nuevo)}: a término (≥ 37 semanas), no se corrige la edad."
        vista == null -> null
        vista.antesDelTermino -> "Prematuro de ${textoEg(nuevo)} · hoy ${textoSemanasDias(vista.postmenstrualDias!!)} de edad posmenstrual: aún sin curva OMS."
        vista.usaEdadCorregida -> "Prematuro de ${textoEg(nuevo)} · hoy: ${textoEdadCorregida(nac, hoy, nuevo)} de edad corregida."
        else -> "Prematuro de ${textoEg(nuevo)} · ya cumplió 2 años: no se corrige."
    }

    fun guardar(valor: EdadGestacional?) {
        if (guardando) return // anti doble toque
        guardando = true
        error = null
        scope.launch {
            val r = PediatriaRepo.actualizarFicha(pacienteId, tocarEg = true, eg = valor)
            guardando = false
            if (r.registrada) {
                Toaster.exito(if (valor != null) "Edad gestacional guardada" else "Edad gestacional quitada")
                onGuardada(valor)
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar"
                error = msg
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = "🍼 Edad gestacional al nacer",
        subtitulo = "Si nació antes de las 37 semanas, el peso, la talla y el perímetro cefálico se evalúan con la edad corregida hasta los 24 meses.",
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = !guardando && valida,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { if (nuevo != null) guardar(nuevo) else error = "Indica las semanas al nacer (22 a 42)." },
    ) {
        EtqForm("Semanas al nacer")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SEMANAS_RAPIDAS.forEach { s -> ChipPed("$s", activo = sem == s && otra.isEmpty()) { semanas = s.toString(); otra = "" } }
            Box(
                Modifier.width(76.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.superficie)
                    .border(1.dp, if (otra.isNotEmpty()) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                CampoTextoEval(
                    valor = otra,
                    onChange = { v -> val d = v.filter { ch -> ch.isDigit() }.take(2); otra = d; semanas = d },
                    placeholder = "Otra", modifier = Modifier.fillMaxWidth(), teclado = KeyboardType.Number,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        EtqForm("Días (opcional)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..6).forEach { d -> ChipPed("+$d", activo = dias == d) { dias = d } }
        }
        textoVista?.let {
            Text(it, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (semanas.isNotBlank() && !valida) Text("Entre 22 y 42 semanas.", color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        error?.let {
            Text(it, color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp).fillMaxWidth()
                .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg).padding(horizontal = 10.dp, vertical = 8.dp))
        }
        if (eg != null) {
            Spacer(Modifier.height(14.dp))
            if (confirmarQuitar) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("¿Quitar el dato?", color = c.textoSuave, fontSize = 12.sp)
                    BotonPed(if (guardando) "Quitando…" else "Quitar", habilitado = !guardando, color = c.error) { guardar(null) }
                    BotonPed("No", habilitado = !guardando) { confirmarQuitar = false }
                }
            } else {
                BotonPed("Quitar dato", habilitado = !guardando) { confirmarQuitar = true }
            }
        }
    }
}

// ─── Estado nutricional y curvas ─────────────────────────────────────────────

@Composable
private fun UltimosZ(ev: EvaluacionCrecimiento, eg: EdadGestacional?, finCorreccion: Boolean) {
    val c = Sania.colors
    // Antes del término no hay patrón OMS: se dice, sin inventar un z.
    if (ev.antesDelTermino) {
        TarjetaAviso {
            Text("📊 Estado nutricional", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${fechaHc(ev.fecha)} · ${textoEdadEvaluada(ev)}", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            Text(AVISO_ANTES_DEL_TERMINO, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text("Nació de ${textoEg(eg)}. La medición queda guardada; desde las 40 semanas se evalúa con la edad corregida.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("📊 Estado nutricional", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${fechaHc(ev.fecha)} · Edad: ${textoEdadEvaluada(ev)}", color = c.textoSuave, fontSize = 11.sp,
                textAlign = TextAlign.End, modifier = Modifier.padding(start = 8.dp))
        }
        if (finCorreccion) {
            Text(
                "🍼 $AVISO_FIN_CORRECCION El punto puede “saltar” en la curva respecto de los controles anteriores.",
                color = c.info, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.infoBg)
                    .border(1.dp, c.info, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ev.resultados.forEach { r ->
                val (fg, bg) = coloresNivel(r.clasificacion?.nivel)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
                        .border(1.dp, fg, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("${r.indicador.sigla} · ${r.indicador.nombre}".uppercase(), color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                    Text("${numeroHc(r.valor)} ${r.indicador.unidad}  ${textoZ(r.z, r.percentil)}", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(r.clasificacion?.texto ?: "Referencial (sin clasificación MINSA a esta edad)", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        val edadUsada = when {
            ev.usaEdadCorregida -> "Evaluado con la edad corregida por prematuridad (nació de ${textoEg(eg)})."
            esPrematuro(eg) -> "Edad cronológica: desde los $MESES_FIN_CORRECCION meses ya no se corrige por prematuridad."
            eg != null -> "Edad cronológica (nació a término)."
            else -> "Edad cronológica (sin dato de prematuridad)."
        }
        Text(
            "Apoyo a la decisión clínica: patrones OMS (${ev.resultados.firstOrNull()?.fuente ?: "OMS"}) y clasificación de la " +
                "NTS 238-MINSA/DGIESP-2025. $edadUsada Longitud acostado hasta los 2 años y talla de pie desde los 2.",
            color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Curvas(
    tablas: TablasOms, sexo: String, nac: String, eg: EdadGestacional?, mediciones: List<MedicionCrecimiento>,
    /** Edad de la última medición con la que se dibuja (corregida si aplica). */
    mesesUltima: Double,
) {
    val c = Sania.colors
    val disponibles = remember(mediciones, mesesUltima) { indicadoresConCurva(mediciones, mesesUltima) }
    var elegido by remember { mutableStateOf<IndicadorCrecimiento?>(null) }
    val ind = elegido?.takeIf { it in disponibles } ?: disponibles.firstOrNull() ?: return
    val ventana = ventanaCurva(ind, mesesUltima) ?: return
    val curva = remember(tablas, ind, sexo, ventana) { curvaReferencia(tablas, ind, sexo, ventana, 80) }
    val puntos = remember(tablas, ind, sexo, nac, mediciones, ventana, eg) { puntosPaciente(tablas, ind, sexo, nac, mediciones, ventana, eg) }
    val corrige = esPrematuro(eg) && !ventana.ejeCm && ventana.desde < MESES_FIN_CORRECCION
    Tarjeta {
        Text("📈 Curvas OMS · ${if (sexo == "F") "niña" else "niño"}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (disponibles.size > 1) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                disponibles.forEach { i -> ChipPed(i.sigla, activo = i == ind) { elegido = i } }
            }
        }
        Text("${ind.nombre} (${ind.unidad}) · líneas −3, −2, 0, +2 y +3 DE", color = c.textoSuave, fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        GraficoCrecimiento(ind, curva, puntos, ventana, etiquetaEje = if (corrige) "Edad (corregida hasta los 24 meses)" else null)
        if (puntos.isEmpty()) Text("Ninguna medición cae en esta ventana de edad.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        if (corrige) {
            Text(
                "🍼 Prematuro de ${textoEg(eg)}: los puntos hasta los $MESES_FIN_CORRECCION meses se ubican por su edad corregida; " +
                    "los de antes de las 40 semanas de edad posmenstrual no se dibujan (usar INTERGROWTH-21st o Fenton).",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun FilaMedicionPed(
    m: MedicionCorporal, ev: EvaluacionCrecimiento?, nac: String?, puedeEditar: Boolean,
    /** La primera medición de un prematuro ya sin corrección (cumplió 2 años). */
    finCorreccion: Boolean,
    onBorrar: () -> Unit,
) {
    val c = Sania.colors
    fun zDe(i: IndicadorCrecimiento) = ev?.resultados?.firstOrNull { it.indicador == i }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fechaHc(m.fecha), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("  ${ev?.let { textoEdadEvaluada(it, corto = true) } ?: textoEdadEntre(nac, m.fecha)}", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (puedeEditar) Text("🗑", fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onBorrar).padding(8.dp))
        }
        if (finCorreccion) Text("desde aquí, edad real", color = c.info, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple("Peso", m.peso?.let { "${numeroHc(it)} kg" }, zDe(IndicadorCrecimiento.PESO_EDAD)),
                Triple("Talla", m.talla?.let { "${numeroHc(it)} cm" }, zDe(IndicadorCrecimiento.TALLA_EDAD)),
                Triple("P. cef.", m.perimetroCefalico?.let { "${numeroHc(it)} cm" }, zDe(IndicadorCrecimiento.PC_EDAD)),
            ).forEach { (etq, valor, r) ->
                Column(Modifier.weight(1f)) {
                    Text(etq.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(valor ?: "—", color = c.texto, fontSize = 12.sp)
                    r?.let {
                        val (fg, _) = coloresNivel(it.clasificacion?.nivel?.takeIf { n -> n != pe.saniape.app.data.staff.NivelCrecimiento.OK })
                        Text("z ${numeroHc(it.z)}", color = fg, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ─── Formulario ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioCrecimiento(
    pacienteId: String,
    hoy: String,
    nac: String?,
    sexo: String?,
    tablas: TablasOms?,
    eg: EdadGestacional?,
    tallaPrevia: Double?,
    onCerrar: () -> Unit,
    onGuardada: (MedicionCorporal?) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var fecha by remember { mutableStateOf(hoy) }
    var elegirFecha by remember { mutableStateOf(false) }
    var peso by remember { mutableStateOf("") }
    var talla by remember { mutableStateOf(textoCampoMedicion(tallaPrevia)) }
    var pc by remember { mutableStateOf("") }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    fun valores() = mapOf("peso" to peso, "talla" to talla, "perimetro_cefalico" to pc)

    // z-score en vivo mientras se escribe (apoyo).
    val vista = remember(tablas, sexo, nac, peso, talla, pc, fecha, eg) {
        if (tablas == null || sexo == null || nac == null) null
        else {
            fun ok(campo: String, v: String) = numeroMedicion(campo, v)?.takeIf { !it.isNaN() }
            evaluarMedicion(tablas, sexo, nac, MedicionCrecimiento(fecha, ok("peso", peso), ok("talla", talla), ok("perimetro_cefalico", pc)), eg)
        }
    }

    fun guardar() {
        if (guardando) return
        val b = BorradorMedicion(pacienteId = pacienteId, fecha = fecha, valores = valores())
        val e = validarMedicion(b).toMutableList()
        if (fecha > hoy) e.add("La fecha no puede ser futura.")
        if (nac != null && fecha < nac) e.add("La fecha es anterior al nacimiento.")
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = MedicionesRepo.registrar(b)
            guardando = false
            if (r.registrada) {
                Toaster.exito("Medición guardada")
                onGuardada((r.cuerpo?.get("medicion") as? JsonObject)?.let(::medicionDeJson))
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = "👶 Registrar medición",
        subtitulo = "Peso, talla y perímetro cefálico del control",
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtqForm("Fecha")
        CajaSelectorForm(fechaHc(fecha)) { elegirFecha = true }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoNumero("Peso (kg)", peso, "8.45", Modifier.weight(1f)) { peso = it }
            CampoNumero("Talla (cm)", talla, "72.5", Modifier.weight(1f)) { talla = it }
        }
        Spacer(Modifier.height(10.dp))
        CampoNumero("Perímetro cefálico (cm)", pc, "45", Modifier.fillMaxWidth()) { pc = it }
        if (vista != null && (vista.usaEdadCorregida || vista.antesDelTermino) && (peso.isNotBlank() || talla.isNotBlank() || pc.isNotBlank())) {
            Text(
                if (vista.antesDelTermino) "${vista.edadPostmenstrualTexto} de edad posmenstrual: se guarda sin z-score (aún antes del término)."
                else "Se evalúa con la edad corregida: ${vista.edadCorregidaTexto}.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
        vista?.resultados?.takeIf { it.isNotEmpty() }?.let { rs ->
            FlowRow(
                Modifier.padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rs.forEach { r ->
                    val (fg, bg) = coloresNivel(r.clasificacion?.nivel)
                    Text(
                        "${r.indicador.sigla} ${textoZ(r.z, r.percentil)}" + (r.clasificacion?.let { " · ${it.texto}" } ?: ""),
                        color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
                            .border(1.dp, fg, RoundedCornerShape(Sania.shape.pill.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        if (errores.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) { errores.forEach { Text(it, color = c.error, fontSize = 12.sp) } }
        }
    }
    if (elegirFecha) DialogoFecha(onElegir = { f ->
        when {
            f > hoy -> Toaster.error("La fecha no puede ser futura.")
            nac != null && f < nac -> Toaster.error("La fecha es anterior al nacimiento.")
            else -> fecha = f
        }
    }, onCerrar = { elegirFecha = false }, inicial = fecha)
}

@Composable
private fun CampoNumero(etiqueta: String, valor: String, ejemplo: String, modifier: Modifier, onChange: (String) -> Unit) {
    Column(modifier) {
        EtqForm(etiqueta)
        CampoTextoEval(
            valor = valor,
            onChange = { nuevo -> onChange(nuevo.filter { it.isDigit() || it == '.' || it == ',' }.take(6)) },
            placeholder = ejemplo, modifier = Modifier.fillMaxWidth(), teclado = KeyboardType.Decimal,
        )
    }
}

// ─── Piezas ──────────────────────────────────────────────────────────────────

@Composable
internal fun Tarjeta(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        content = contenido,
    )
}

@Composable
private fun TarjetaAviso(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.pendBg)
            .border(1.dp, c.pend, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
        content = contenido,
    )
}

@Composable
internal fun TituloPed(t: String) {
    Text(t, color = Sania.colors.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun ChipPed(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
internal fun BotonPed(texto: String, primario: Boolean = false, habilitado: Boolean = true, color: Color? = null, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (primario) c.sobreNavy else color ?: c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier
        .clip(RoundedCornerShape(Sania.shape.sm.dp))
        .background(if (primario) c.navy else c.superficie)
        .border(1.dp, if (primario) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
        .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp))
}
