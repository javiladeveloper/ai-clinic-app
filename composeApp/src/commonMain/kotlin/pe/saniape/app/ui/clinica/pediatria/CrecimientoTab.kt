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
import pe.saniape.app.data.staff.BorradorMedicion
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
    val evaluaciones: Map<String, EvaluacionCrecimiento> = remember(tablas, sexo, nac, lista) {
        val t = tablas
        if (t == null || sexo == null || nac == null) emptyMap()
        else lista.mapNotNull { m -> evaluarMedicion(t, sexo, nac, m.aCrecimiento())?.let { m.id to it } }.toMap()
    }
    val edadHoy = edadEnDias(nac, hoy)
    val fuera = edadHoy != null && mesesDeDias(edadHoy.toDouble()) > MESES_MAX
    val evUltima = lista.lastOrNull { it.id in evaluaciones }?.let { evaluaciones[it.id] }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("👶 Crecimiento", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)

        if (nac == null) AvisoNacimiento(pacienteId, puedeEditar, onNacimientoGuardado)
        if (sexo == null) AvisoSexo(pacienteId, puedeEditar, onSexoGuardado)
        if (fuera) Tarjeta { Text("Tiene más de 19 años: las curvas de la OMS no aplican.", color = c.textoSuave, fontSize = 12.sp) }

        val t = tablas
        val puedeImprimir = lista.isNotEmpty() && sexo != null && nac != null && t != null
        if (puedeEditar || puedeImprimir) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (puedeEditar) BotonPed("＋ Registrar peso y talla", primario = true) { registrar = true }
                if (puedeImprimir) BotonPed("🖨 Curva de crecimiento") {
                    acciones.abrirHtml(
                        htmlCurvaCrecimiento(t!!, sexo!!, nac!!, lista.map { it.aCrecimiento() }, hoy, pacienteNombre, pacienteDocumento,
                            clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal),
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

        evUltima?.let { UltimosZ(it) }
        if (t != null && sexo != null && nac != null && evUltima != null) {
            Curvas(t, sexo, nac, lista.map { it.aCrecimiento() }, evUltima.edadMeses)
        }
        if (lista.isNotEmpty()) {
            Tarjeta {
                TituloPed("🗂️ Mediciones (${lista.size})")
                lista.asReversed().forEachIndexed { i, m ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                    FilaMedicionPed(m, evaluaciones[m.id], nac, puedeEditar) { confirmarBorrar = m }
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
            pacienteId = pacienteId, hoy = hoy, nac = nac, sexo = sexo, tablas = tablas,
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

// ─── Estado nutricional y curvas ─────────────────────────────────────────────

@Composable
private fun UltimosZ(ev: EvaluacionCrecimiento) {
    val c = Sania.colors
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("📊 Estado nutricional", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${fechaHc(ev.fecha)} · ${ev.edadTexto}", color = c.textoSuave, fontSize = 11.sp)
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
        Text(
            "Apoyo a la decisión clínica: patrones OMS (${ev.resultados.firstOrNull()?.fuente ?: "OMS"}) y clasificación de la " +
                "NTS 238-MINSA/DGIESP-2025. Edad cronológica (sin corregir por prematuridad); longitud acostado hasta los 2 años y talla de pie desde los 2.",
            color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Curvas(tablas: TablasOms, sexo: String, nac: String, mediciones: List<MedicionCrecimiento>, mesesUltima: Double) {
    val c = Sania.colors
    val disponibles = remember(mediciones, mesesUltima) { indicadoresConCurva(mediciones, mesesUltima) }
    var elegido by remember { mutableStateOf<IndicadorCrecimiento?>(null) }
    val ind = elegido?.takeIf { it in disponibles } ?: disponibles.firstOrNull() ?: return
    val ventana = ventanaCurva(ind, mesesUltima) ?: return
    val curva = remember(tablas, ind, sexo, ventana) { curvaReferencia(tablas, ind, sexo, ventana, 80) }
    val puntos = remember(tablas, ind, sexo, nac, mediciones, ventana) { puntosPaciente(tablas, ind, sexo, nac, mediciones, ventana) }
    Tarjeta {
        Text("📈 Curvas OMS · ${if (sexo == "F") "niña" else "niño"}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (disponibles.size > 1) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                disponibles.forEach { i -> ChipPed(i.sigla, activo = i == ind) { elegido = i } }
            }
        }
        Text("${ind.nombre} (${ind.unidad}) · líneas −3, −2, 0, +2 y +3 DE", color = c.textoSuave, fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        GraficoCrecimiento(ind, curva, puntos, ventana)
        if (puntos.isEmpty()) Text("Ninguna medición cae en esta ventana de edad.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FilaMedicionPed(m: MedicionCorporal, ev: EvaluacionCrecimiento?, nac: String?, puedeEditar: Boolean, onBorrar: () -> Unit) {
    val c = Sania.colors
    fun zDe(i: IndicadorCrecimiento) = ev?.resultados?.firstOrNull { it.indicador == i }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fechaHc(m.fecha), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("  ${textoEdadEntre(nac, m.fecha)}", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (puedeEditar) Text("🗑", fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onBorrar).padding(8.dp))
        }
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
    val vista = remember(tablas, sexo, nac, peso, talla, pc, fecha) {
        if (tablas == null || sexo == null || nac == null) null
        else {
            fun ok(campo: String, v: String) = numeroMedicion(campo, v)?.takeIf { !it.isNaN() }
            evaluarMedicion(tablas, sexo, nac, MedicionCrecimiento(fecha, ok("peso", peso), ok("talla", talla), ok("perimetro_cefalico", pc)))
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
internal fun BotonPed(texto: String, primario: Boolean = false, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (primario) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier
        .clip(RoundedCornerShape(Sania.shape.sm.dp))
        .background(if (primario) c.navy else c.superficie)
        .border(1.dp, if (primario) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
        .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp))
}
