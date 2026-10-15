package pe.saniape.app.ui.clinica.capilar

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import pe.saniape.app.data.staff.AREAS_RECEPTORAS_RAPIDAS
import pe.saniape.app.data.staff.BorradorTricoscopia
import pe.saniape.app.data.staff.COLOR_ZONA_TRICO
import pe.saniape.app.data.staff.Cambio
import pe.saniape.app.data.staff.ESCALAS
import pe.saniape.app.data.staff.EntradaTrasplante
import pe.saniape.app.data.staff.EvaluacionTricoscopica
import pe.saniape.app.data.staff.LimitesTricoscopia
import pe.saniape.app.data.staff.MetricaZona
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.ResultadoTrasplante
import pe.saniape.app.data.staff.SIGNOS_TRICOSCOPICOS
import pe.saniape.app.data.staff.TrasplanteDefecto
import pe.saniape.app.data.staff.TricoscopiaRepo
import pe.saniape.app.data.staff.ZonaCapilar
import pe.saniape.app.data.staff.ZonaCruda
import pe.saniape.app.data.staff.aNumeroTrico
import pe.saniape.app.data.staff.comparacionConDonante
import pe.saniape.app.data.staff.copiaDeAnterior
import pe.saniape.app.data.staff.cumpleCriteriosAndrogeneticos
import pe.saniape.app.data.staff.escalaDe
import pe.saniape.app.data.staff.escalaSugerida
import pe.saniape.app.data.staff.estimarTrasplante
import pe.saniape.app.data.staff.evaluacionDeJson
import pe.saniape.app.data.staff.evolucionZonas
import pe.saniape.app.data.staff.fechaTricoscopiaFutura
import pe.saniape.app.data.staff.flechaCambio
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.htmlInformeTricoscopia
import pe.saniape.app.data.staff.indiciosAndrogeneticos
import pe.saniape.app.data.staff.metricasConEvolucion
import pe.saniape.app.data.staff.milesTrico
import pe.saniape.app.data.staff.nombreSigno
import pe.saniape.app.data.staff.numTrico
import pe.saniape.app.data.staff.ordenarEvaluaciones
import pe.saniape.app.data.staff.paresDelFormulario
import pe.saniape.app.data.staff.pelosPorUfCalculado
import pe.saniape.app.data.staff.pelosPorUfParaEditar
import pe.saniape.app.data.staff.referenciaCasilla
import pe.saniape.app.data.staff.serieZona
import pe.saniape.app.data.staff.textoCambio
import pe.saniape.app.data.staff.textoCampoTrico
import pe.saniape.app.data.staff.textoClasificacion
import pe.saniape.app.data.staff.textoComparacionDonante
import pe.saniape.app.data.staff.textoMedicion
import pe.saniape.app.data.staff.textoNivelTrasplante
import pe.saniape.app.data.staff.textoValor
import pe.saniape.app.data.staff.trasplanteDeEvaluacion
import pe.saniape.app.data.staff.validarTricoscopia
import pe.saniape.app.data.staff.zonasConEvolucion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.fisio.CampoTextoEval
import pe.saniape.app.ui.clinica.historia.GraficoLineaHc
import pe.saniape.app.ui.clinica.historia.fechaHc
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/** Un tratamiento capilar del paciente (la evaluación se vincula al primero, el activo más reciente). */
data class TratamientoCapilar(val id: String, val nombre: String, val activo: Boolean)

/** Color fijo de cada zona en los gráficos (los mismos de la web). */
internal fun colorZona(z: ZonaCapilar): Color {
    val hex = COLOR_ZONA_TRICO.getValue(z).color.removePrefix("#").toLong(16)
    return Color(0xFF000000 or hex)
}

/**
 * Pestaña 💇 Tricoscopía de la ficha (solo CAPILAR, ver `pacienteEsCapilar`).
 * Gemelo de TricoscopiaTab.tsx de la web: clasificación (Hamilton-Norwood /
 * Ludwig / Sinclair con siluetas), mediciones por zona, signos, la ESTIMACIÓN
 * para trasplante, la evolución (contra la anterior y la primera, y el
 * gráfico por zona con eje de tiempo real), el historial con informe
 * imprimible, editar y borrar.
 *
 * Carga SOLO cuando la pestaña se abre (la monta el padre en su `when`). Sin
 * listas perezosas: vive dentro del `verticalScroll` de la ficha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TricoscopiaTab(
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    /** `puede('pacientes') || puede('sesiones')` y ficha no dada de baja (como el API). */
    puedeEditar: Boolean,
    /** Los tratamientos capilares del paciente (el activo más reciente primero). */
    tratamientos: List<TratamientoCapilar>,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<EvaluacionTricoscopica>?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    var sinAcceso by remember(pacienteId) { mutableStateOf(false) }
    LaunchedEffect(pacienteId, token) {
        val r = TricoscopiaRepo.listar(pacienteId)
        if (r.items != null) { items = r.items; error = null; sinAcceso = false }
        else if (items == null || r.sinAcceso) { error = r.error; sinAcceso = r.sinAcceso }
        cargando = false
    }
    // Quién evaluó (terapeuta_id → nombre), como `profesionales` en la web.
    var profesionales by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        profesionales = runCatching { PacientesRepo.terapeutasActivos().associate { it.id to it.nombre } }.getOrDefault(emptyMap())
    }
    // null = cerrado; Some(null) = nueva; Some(ev) = editar.
    var editor by remember { mutableStateOf<Pair<Boolean, EvaluacionTricoscopica?>?>(null) }
    var confirmarBorrar by remember { mutableStateOf<EvaluacionTricoscopica?>(null) }
    var borrando by remember { mutableStateOf(false) }

    if (cargando && items == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 170, 110).forEach { h ->
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            }
        }
        return
    }
    error?.let { msg ->
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (sinAcceso) "🔒" else "⚠", fontSize = 28.sp)
            Text(msg, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp))
            if (!sinAcceso) {
                Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp).clickable { error = null; cargando = true; token++ }.padding(8.dp))
            }
        }
        return
    }

    val lista = items.orEmpty()
    fun imprimir(ev: EvaluacionTricoscopica) {
        acciones.abrirHtml(
            htmlInformeTricoscopia(ev, lista, clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal,
                pacienteNombre, pacienteDocumento, ev.terapeutaId?.let { profesionales[it] }),
            "Informe tricoscópico",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💇 Tricoscopía", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (puedeEditar && lista.isNotEmpty()) BotonTrico("＋ Nueva tricoscopía") { editor = true to null }
        }
        if (lista.isEmpty()) {
            TarjetaTrico {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔬", fontSize = 30.sp)
                    Text("Aún no hay tricoscopías", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Clasifica el patrón (Hamilton-Norwood, Ludwig o Sinclair) y anota la densidad por zona en cada visita para " +
                        "ver si el tratamiento funciona. Si hay trasplante, estima cuántas unidades foliculares da la zona donante. " +
                        "El paciente ve su evolución en su app.",
                        color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    if (puedeEditar) {
                        Spacer(Modifier.height(12.dp))
                        BotonTrico("＋ Nueva tricoscopía") { editor = true to null }
                    }
                }
            }
        } else {
            EvolucionTrico(lista)
            HistorialTrico(
                items = lista,
                puedeEditar = puedeEditar,
                nombreProf = { id -> id?.let { profesionales[it] } },
                onImprimir = ::imprimir,
                onEditar = { editor = false to it },
                onBorrar = { confirmarBorrar = it },
            )
        }
    }

    editor?.let { (_, ev) -> androidx.compose.runtime.key(ev?.id ?: "nueva") {
        FormTricoscopia(
            pacienteId = pacienteId,
            ev = ev,
            items = lista,
            tratamientos = tratamientos,
            onCerrar = { editor = null },
            onGuardada = { nueva ->
                editor = null
                // Se muestra al instante; la relectura confirma lo que quedó en la base.
                nueva?.let { n -> items = ordenarEvaluaciones(lista.filter { it.id != n.id } + n) }
                token++
            },
        )
    } }
    confirmarBorrar?.let { ev ->
        AlertDialog(
            onDismissRequest = { if (!borrando) confirmarBorrar = null },
            title = { Text("¿Eliminar esta evaluación?", fontWeight = FontWeight.Bold) },
            text = {
                Text("${fechaHc(ev.fecha)} · ${textoClasificacion(ev.escala, ev.grado) ?: "Sin clasificar"}. No se puede deshacer.",
                    color = c.textoSuave)
            },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    // Dos toques en el mismo frame: `enabled` recién aplica en la siguiente recomposición.
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = TricoscopiaRepo.borrar(ev.id)
                        borrando = false
                        if (r.registrada) {
                            confirmarBorrar = null
                            items = lista.filter { it.id != ev.id }
                            Toaster.exito("Evaluación eliminada")
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

// ─── Formulario ──────────────────────────────────────────────────────────────

private fun numCampo(s: String): Double? = aNumeroTrico(s)?.takeIf { !it.isNaN() }

/** Solo dígitos y un separador decimal (coma o punto), corto. */
private fun limpiarNumero(s: String): String = s.filter { it.isDigit() || it == '.' || it == ',' }.take(6)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormTricoscopia(
    pacienteId: String,
    ev: EvaluacionTricoscopica?,
    items: List<EvaluacionTricoscopica>,
    tratamientos: List<TratamientoCapilar>,
    onCerrar: () -> Unit,
    onGuardada: (EvaluacionTricoscopica?) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val nueva = ev == null
    val hoy = remember { hoyClinicaIso() }
    val anterior = if (nueva) items.lastOrNull() else null
    // La visita previa a la que se carga (o edita): sus valores salen en gris ("ant. 120"), NUNCA como valor.
    val previa = if (nueva) anterior else items.indexOfFirst { it.id == ev!!.id }.let { i -> if (i > 0) items[i - 1] else null }

    var fecha by remember { mutableStateOf(ev?.fecha ?: hoy) }
    var elegirFecha by remember { mutableStateOf(false) }
    // Visita nueva: la escala de la última evaluación (el grado se mira de nuevo).
    var escala by remember { mutableStateOf(if (ev != null) ev.escala.orEmpty() else escalaSugerida(items).orEmpty()) }
    var grado by remember { mutableStateOf(ev?.grado.orEmpty()) }
    var valores by remember {
        mutableStateOf(
            ev?.zonas.orEmpty().associate { z ->
                z.zona to MetricaZona.entries.associateWith { m ->
                    if (m == MetricaZona.PELOS_POR_UF) textoCampoTrico(pelosPorUfParaEditar(z)) else textoCampoTrico(z.valor(m))
                }
            },
        )
    }
    var signos by remember { mutableStateOf(ev?.zonas.orEmpty().associate { it.zona to it.signos }) }
    var donanteArea by remember { mutableStateOf(textoCampoTrico(ev?.donante?.areaCm2)) }
    var extraible by remember { mutableStateOf(textoCampoTrico(ev?.donante?.extraiblePct)) }
    var receptorArea by remember { mutableStateOf(textoCampoTrico(ev?.receptor?.areaCm2)) }
    var densidadObjetivo by remember { mutableStateOf(textoCampoTrico(ev?.receptor?.densidadObjetivo)) }
    var observaciones by remember { mutableStateOf(ev?.observaciones.orEmpty()) }
    var visible by remember { mutableStateOf(ev?.visiblePaciente ?: true) }
    var tratamientoId by remember { mutableStateOf(if (ev != null) ev.tratamientoId.orEmpty() else tratamientos.firstOrNull()?.id.orEmpty()) }
    var copiado by remember { mutableStateOf(false) }
    var signosAbiertos by remember { mutableStateOf<ZonaCapilar?>(null) }
    var verTrasplante by remember { mutableStateOf(ev?.donante != null || ev?.receptor != null) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    val def = escalaDe(escala)

    fun valor(z: ZonaCapilar, m: MetricaZona) = valores[z]?.get(m).orEmpty()
    fun ponerValor(z: ZonaCapilar, m: MetricaZona, v: String) {
        valores = valores + (z to (valores[z].orEmpty() + (m to limpiarNumero(v))))
    }

    // Copia la clasificación y la zona donante / receptora (no las mediciones: van en gris).
    fun copiarAnterior() {
        val a = anterior ?: return
        val cp = copiaDeAnterior(a)
        escala = cp.escala.orEmpty()
        grado = cp.grado.orEmpty()
        donanteArea = textoCampoTrico(cp.donante?.areaCm2)
        extraible = textoCampoTrico(cp.donante?.extraiblePct)
        receptorArea = textoCampoTrico(cp.receptor?.areaCm2)
        densidadObjetivo = textoCampoTrico(cp.receptor?.densidadObjetivo)
        if (cp.donante != null || cp.receptor != null) verTrasplante = true
        copiado = true
        Toaster.exito("Copiada la clasificación del ${fechaHc(a.fecha)}: revísala")
    }

    // Estimación en vivo con la zona occipital del formulario.
    val occ = ZonaCapilar.OCCIPITAL
    val estimacion = estimarTrasplante(EntradaTrasplante(
        ufCm2Donante = numCampo(valor(occ, MetricaZona.UF_CM2)),
        densidadDonante = numCampo(valor(occ, MetricaZona.DENSIDAD)),
        pelosPorUfDonante = numCampo(valor(occ, MetricaZona.PELOS_POR_UF))
            ?: pelosPorUfCalculado(numCampo(valor(occ, MetricaZona.DENSIDAD)), numCampo(valor(occ, MetricaZona.UF_CM2))),
        areaDonanteCm2 = numCampo(donanteArea), extraiblePct = numCampo(extraible),
        areaReceptoraCm2 = numCampo(receptorArea), densidadObjetivo = numCampo(densidadObjetivo),
    ))

    fun borrador(): BorradorTricoscopia {
        val (don, rec) = paresDelFormulario(donanteArea, extraible, receptorArea, densidadObjetivo)
        return BorradorTricoscopia(
            pacienteId = pacienteId,
            fecha = fecha.ifBlank { hoy },
            tratamientoId = tratamientoId.ifBlank { null },
            escala = escala.ifBlank { null },
            grado = if (escala.isNotBlank()) grado.trim().ifEmpty { null } else null,
            zonas = ZonaCapilar.entries.map { z ->
                ZonaCruda(z.id, MetricaZona.entries.associateWith { m -> valor(z, m) }, signos[z].orEmpty())
            },
            donante = don, receptor = rec,
            observaciones = observaciones.trim().ifEmpty { null },
            visiblePaciente = visible,
        )
    }

    fun guardar() {
        if (guardando) return
        val b = borrador()
        val e = validarTricoscopia(b, hoy)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = TricoscopiaRepo.guardar(b, ev?.id)
            guardando = false
            if (r.registrada) {
                Toaster.exito(if (nueva) "Tricoscopía guardada" else "Cambios guardados")
                onGuardada((r.cuerpo?.get("evaluacion") as? JsonObject)?.let(::evaluacionDeJson))
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = if (nueva) "🔬 Nueva tricoscopía" else "Editar tricoscopía",
        subtitulo = if (nueva) "Clasificación y mediciones de hoy" else "Del ${fechaHc(ev!!.fecha)}",
        textoAccion = if (guardando) "Guardando…" else if (nueva) "Guardar tricoscopía" else "Guardar cambios",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        if (anterior != null && !copiado && (anterior.escala != null || anterior.donante != null || anterior.receptor != null)) {
            BotonContorno("↺ Copiar clasificación del ${fechaHc(anterior.fecha)}") { copiarAnterior() }
            Spacer(Modifier.height(12.dp))
        }
        EtqForm("Fecha")
        CajaSelectorForm(fechaHc(fecha)) { elegirFecha = true }
        if (tratamientos.size > 1) {
            Spacer(Modifier.height(12.dp))
            EtqForm("Tratamiento")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipTrico("Sin tratamiento", tratamientoId.isEmpty()) { tratamientoId = "" }
                tratamientos.forEach { t ->
                    ChipTrico(t.nombre + if (t.activo) "" else " (terminado)", tratamientoId == t.id) { tratamientoId = t.id }
                }
            }
        }

        // ── Clasificación ──
        Spacer(Modifier.height(16.dp))
        EtqForm("Clasificación")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ESCALAS.forEach { e ->
                ChipTrico(e.boton, escala == e.id) {
                    escala = if (escala == e.id) "" else e.id
                    grado = ""
                }
            }
        }
        def?.let { Text(it.ayuda, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        if (def != null && def.grados.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                def.grados.forEach { g ->
                    val sel = grado == g
                    Column(
                        Modifier.width(62.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) c.chipBg else c.superficie)
                            .border(if (sel) 2.dp else 1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(12.dp))
                            .clickable { grado = if (sel) "" else g }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SiluetaCapilar(def.id, g, tam = 40.dp)
                        Text(g, color = c.texto, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        if (def?.id == "otro") {
            OutlinedTextField(
                value = grado, onValueChange = { grado = it.take(LimitesTricoscopia.GRADO) },
                placeholder = { Text("Ej. Alopecia areata en placa, cicatricial…", color = c.textoSuave, fontSize = 13.sp) },
                singleLine = true, colors = coloresCampoForm(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        // ── Mediciones por zona ──
        Spacer(Modifier.height(16.dp))
        EtqForm("Mediciones por zona")
        Text(
            "Llena solo las zonas que miraste. Pelos/UF se calcula solo con densidad y UF/cm² (≈)." +
                (previa?.let { " En gris, lo de la visita del ${fechaHc(it.fecha)} («ant.»): escribe lo de hoy." } ?: ""),
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp),
        )
        ZonaCapilar.entries.forEach { z ->
            val zPrevia = previa?.zonas?.firstOrNull { it.zona == z }
            val sigs = signos[z].orEmpty()
            val abiertos = signosAbiertos == z
            Column(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(z.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        if (sigs.isNotEmpty()) "Signos (${sigs.size})" else "＋ Signos",
                        color = if (sigs.isNotEmpty()) c.navy else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (sigs.isNotEmpty()) c.navy else c.borde, RoundedCornerShape(8.dp))
                            .clickable { signosAbiertos = if (abiertos) null else z }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                val dens = numCampo(valor(z, MetricaZona.DENSIDAD))
                val uf = numCampo(valor(z, MetricaZona.UF_CM2))
                listOf(
                    listOf(MetricaZona.DENSIDAD, MetricaZona.UF_CM2),
                    listOf(MetricaZona.MINIATURIZACION, MetricaZona.PELOS_POR_UF),
                    listOf(MetricaZona.DIAMETRO),
                ).forEach { fila ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fila.forEach { m ->
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (m) {
                                        MetricaZona.DENSIDAD -> "Densidad (pelos/cm²)"
                                        MetricaZona.DIAMETRO -> "Diám. µm (opc.)"
                                        else -> m.corto
                                    },
                                    color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp),
                                )
                                CampoTextoEval(
                                    valor = valor(z, m),
                                    onChange = { ponerValor(z, m, it) },
                                    placeholder = referenciaCasilla(m, dens, uf, zPrevia).texto,
                                    modifier = Modifier.fillMaxWidth(),
                                    teclado = KeyboardType.Decimal,
                                )
                            }
                        }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (abiertos) {
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SIGNOS_TRICOSCOPICOS.forEach { (id, nombre) ->
                            val on = id in sigs
                            ChipTrico(nombre, on) {
                                signos = signos + (z to (if (on) sigs - id else sigs + id))
                            }
                        }
                    }
                } else if (sigs.isNotEmpty()) {
                    Text(sigs.joinToString(" · ") { nombreSigno(it) }, color = c.textoSuave, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // ── Zona donante y trasplante ──
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { verTrasplante = !verTrasplante }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧮 Zona donante y estimación para trasplante", color = c.texto, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (verTrasplante) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp)
            }
            if (verTrasplante) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    Text("Usa la zona occipital de arriba (UF/cm², o densidad y pelos/UF). Los vacíos toman los valores por defecto, conservadores.",
                        color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoTrasplante("Área donante (cm²)", donanteArea, numTrico(TrasplanteDefecto.AREA_DONANTE_CM2), Modifier.weight(1f)) { donanteArea = limpiarNumero(it) }
                        CampoTrasplante("% extraíble", extraible, numTrico(TrasplanteDefecto.EXTRAIBLE_PCT), Modifier.weight(1f)) { extraible = limpiarNumero(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoTrasplante("Área receptora (cm²)", receptorArea, "60", Modifier.weight(1f)) { receptorArea = limpiarNumero(it) }
                        CampoTrasplante("Densidad objetivo (UF/cm²)", densidadObjetivo, numTrico(TrasplanteDefecto.DENSIDAD_OBJETIVO), Modifier.weight(1f)) { densidadObjetivo = limpiarNumero(it) }
                    }
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Área receptora:", color = c.textoSuave, fontSize = 11.sp)
                        AREAS_RECEPTORAS_RAPIDAS.forEach { a ->
                            ChipTrico("$a cm²", receptorArea == a.toString()) { receptorArea = a.toString() }
                        }
                    }
                    if (densidadObjetivo.isNotBlank() && receptorArea.isBlank()) {
                        Text("Falta el área receptora: sin ella la densidad objetivo no se guarda ni se calcula lo necesario.",
                            color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    ResultadoEstimacion(estimacion)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        EtqForm("Observaciones (solo para la clínica)")
        OutlinedTextField(
            value = observaciones, onValueChange = { observaciones = it.take(LimitesTricoscopia.OBSERVACIONES) },
            placeholder = { Text("Impresión diagnóstica, plan, lo que no entra en la grilla…", color = c.textoSuave, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            colors = coloresCampoForm(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).clickable { visible = !visible }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("📱 Visible para el paciente en su app", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Ve su clasificación y la densidad por zona. Nunca las observaciones ni la estimación de trasplante.",
                    color = c.textoSuave, fontSize = 11.sp)
            }
            Switch(checked = visible, onCheckedChange = { visible = it },
                colors = SwitchDefaults.colors(checkedTrackColor = c.navy))
        }
        if (errores.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) { errores.forEach { Text(it, color = c.error, fontSize = 12.sp) } }
        }
    }
    if (elegirFecha) DialogoFecha(onElegir = { elegida ->
        if (fechaTricoscopiaFutura(elegida, hoy)) Toaster.error("La fecha no puede ser futura.") else fecha = elegida
    }, onCerrar = { elegirFecha = false }, inicial = fecha)
}

@Composable
private fun CampoTrasplante(etiqueta: String, valor: String, placeholder: String, modifier: Modifier, onChange: (String) -> Unit) {
    Column(modifier) {
        Text(etiqueta, color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, modifier = Modifier.padding(bottom = 3.dp))
        CampoTextoEval(valor = valor, onChange = onChange, placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(), teclado = KeyboardType.Decimal)
    }
}

/** El resultado de la estimación: disponibles vs necesarias y el mensaje, SIEMPRE rotulado "Estimación". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResultadoEstimacion(r: ResultadoTrasplante) {
    val c = Sania.colors
    val (borde, fondo, texto) = when (r.nivel) {
        "alcanza" -> Triple(c.ok, c.okBg, c.ok)
        "justo" -> Triple(c.pend, c.pendBg, c.texto)
        "no_alcanza" -> Triple(c.error, c.errorBg, c.error)
        else -> Triple(c.borde, c.chipBg, c.textoSuave)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fondo)
            .border(1.5.dp, borde, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ESTIMACIÓN", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
            r.ufCm2Donante?.let { uf ->
                Text("Donante ${numTrico(uf)} UF/cm²${if (r.origenDonante == "densidad") " (de la densidad)" else ""} × " +
                    "${numTrico(r.areaDonanteCm2)} cm² × ${numTrico(r.extraiblePct)} %", color = c.textoSuave, fontSize = 11.sp)
            }
        }
        if (r.nivel != "incompleto") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("UF DISPONIBLES", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(r.ufDisponibles?.let(::milesTrico) ?: "—", color = c.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    r.pelosDisponibles?.let { Text("≈ ${milesTrico(it)} pelos", color = c.textoSuave, fontSize = 11.sp) }
                }
                Column(Modifier.weight(1f)) {
                    Text("UF NECESARIAS", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(r.ufNecesarias?.let(::milesTrico) ?: "—", color = c.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${r.areaReceptoraCm2?.let(::numTrico)} cm² × ${numTrico(r.densidadObjetivo)} UF/cm²", color = c.textoSuave, fontSize = 11.sp)
                }
            }
        }
        Text(r.mensaje, color = texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        r.avisos.forEach { Text("⚠ $it", color = c.error, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
        Text("Orientativa: no reemplaza la evaluación del cirujano (densidad real, calibre, laxitud, progresión).",
            color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

// ─── Evolución ───────────────────────────────────────────────────────────────

@Composable
private fun colorTendencia(cb: Cambio?): Color {
    val c = Sania.colors
    return when (cb?.tendencia) {
        "mejora" -> c.ok
        "empeora" -> c.error
        else -> c.textoSuave
    }
}

@Composable
private fun LineaCambio(cb: Cambio?, m: MetricaZona, sufijo: String) {
    if (cb == null) return
    Text("${flechaCambio(cb)} ${textoCambio(cb, m)} $sufijo", color = colorTendencia(cb), fontSize = 11.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EvolucionTrico(items: List<EvaluacionTricoscopica>) {
    val c = Sania.colors
    val ultima = items.last()
    val evol = remember(items) { evolucionZonas(items) }
    val metricas = remember(items) {
        metricasConEvolucion(items).filter { it == MetricaZona.DENSIDAD || it == MetricaZona.MINIATURIZACION || it == MetricaZona.UF_CM2 }
    }
    var elegida by remember { mutableStateOf<MetricaZona?>(null) }
    val metrica = elegida?.takeIf { it in metricas } ?: metricas.firstOrNull()
    val vsDonante = remember(ultima) { comparacionConDonante(ultima) }
    val indicios = remember(ultima) { indiciosAndrogeneticos(ultima) }
    val clasif = textoClasificacion(ultima.escala, ultima.grado)
    val primeraClasif = items.firstOrNull { it.escala != null }
    val cambioClasif = if (primeraClasif != null && primeraClasif.id != ultima.id && ultima.escala != null &&
        textoClasificacion(primeraClasif.escala, primeraClasif.grado) != clasif
    ) "${textoClasificacion(primeraClasif.escala, primeraClasif.grado)} (${fechaHc(primeraClasif.fecha)})" else null

    TarjetaTrico {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📈 EVOLUCIÓN", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f))
            Text("${items.size} ${if (items.size == 1) "evaluación" else "evaluaciones"} · última ${fechaHc(ultima.fecha)}",
                color = c.textoSuave, fontSize = 11.sp)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SiluetaCapilar(ultima.escala, ultima.grado, tam = 52.dp, titulo = clasif ?: "Sin clasificación")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("CLASIFICACIÓN ACTUAL", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(clasif ?: "Sin clasificar", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                cambioClasif?.let { Text("Antes: $it", color = c.textoSuave, fontSize = 11.sp) }
            }
        }
        // Por zona: densidad y miniaturización con su cambio (vs anterior y vs la primera).
        evol.forEachIndexed { i, z ->
            Box(Modifier.fillMaxWidth().padding(top = if (i == 0) 12.dp else 8.dp).height(1.dp).background(c.borde))
            Text(z.zona.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(MetricaZona.DENSIDAD, MetricaZona.MINIATURIZACION).forEach { m ->
                    Column(Modifier.weight(1f)) {
                        Text(m.nombre.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(textoValor(z.actual[m], m), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val ant = z.vsAnterior[m]
                        val pri = z.vsPrimera[m]
                        LineaCambio(ant, m, "vs anterior")
                        if (pri != null && pri.fechaDesde != ant?.fechaDesde) LineaCambio(pri, m, "vs 1.ª (${fechaHc(pri.fechaDesde)})")
                    }
                }
            }
        }
        if (vsDonante.isNotEmpty() || indicios.mayores.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(c.chipBg)
                    .border(1.dp, c.borde, RoundedCornerShape(12.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (vsDonante.isNotEmpty()) {
                    Text("Contra la zona donante: ${textoComparacionDonante(vsDonante)}", color = c.texto, fontSize = 12.sp)
                }
                if (indicios.mayores.isNotEmpty()) {
                    Text(
                        (if (cumpleCriteriosAndrogeneticos(indicios)) "Compatible con patrón androgenético" else "Hallazgos de patrón androgenético") +
                            " (criterios de Rakowska, orientativo): " + (indicios.mayores + indicios.menores).joinToString(" · ") + ".",
                        color = c.texto, fontSize = 12.sp,
                    )
                }
            }
        }
        if (metrica != null) {
            Spacer(Modifier.height(14.dp))
            Text("${metrica.nombre.uppercase()} (${metrica.unidad}) POR ZONA", color = c.textoSuave, fontSize = 10.sp,
                fontWeight = FontWeight.Bold)
            if (metricas.size > 1) {
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    metricas.forEach { m -> ChipTrico(m.corto, m == metrica) { elegida = m } }
                }
            }
            // Una línea por zona con ≥ 2 visitas, eje de TIEMPO real (los días entre visitas).
            zonasConEvolucion(items, metrica).forEach { z ->
                Spacer(Modifier.height(10.dp))
                GraficoLineaHc(
                    titulo = z.nombre, unidad = metrica.unidad,
                    puntos = serieZona(items, z, metrica), color = colorZona(z), ejeTiempo = true,
                )
            }
        } else {
            Text("El gráfico aparece desde la segunda evaluación con la misma zona medida.", color = c.textoSuave,
                fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

// ─── Historial (la más reciente arriba) ──────────────────────────────────────

private const val VISIBLES_AL_INICIO = 3

@Composable
private fun HistorialTrico(
    items: List<EvaluacionTricoscopica>,
    puedeEditar: Boolean,
    nombreProf: (String?) -> String?,
    onImprimir: (EvaluacionTricoscopica) -> Unit,
    onEditar: (EvaluacionTricoscopica) -> Unit,
    onBorrar: (EvaluacionTricoscopica) -> Unit,
) {
    val c = Sania.colors
    val recientes = remember(items) { items.reversed() }
    var todas by remember { mutableStateOf(false) }
    val lista = if (todas) recientes else recientes.take(VISIBLES_AL_INICIO)
    TarjetaTrico {
        Text("🗂️ HISTORIAL", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 4.dp))
        lista.forEachIndexed { i, ev ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(c.borde))
            FilaEvaluacion(ev, nombreProf(ev.terapeutaId), puedeEditar, { onImprimir(ev) }, { onEditar(ev) }, { onBorrar(ev) })
        }
        if (recientes.size > VISIBLES_AL_INICIO) {
            Text(
                if (todas) "▴ Ver menos" else "▾ Ver las ${recientes.size - VISIBLES_AL_INICIO} anteriores",
                color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { todas = !todas }.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun FilaEvaluacion(
    ev: EvaluacionTricoscopica,
    profesional: String?,
    puedeEditar: Boolean,
    onImprimir: () -> Unit,
    onEditar: () -> Unit,
    onBorrar: () -> Unit,
) {
    val c = Sania.colors
    val clasif = textoClasificacion(ev.escala, ev.grado)
    val trasplante = remember(ev) { trasplanteDeEvaluacion(ev) }
    val cols = MetricaZona.entries.filter { m -> ev.zonas.any { textoMedicion(it, m) != "—" } }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            SiluetaCapilar(ev.escala, ev.grado, tam = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${fechaHc(ev.fecha)} · ${clasif ?: "Sin clasificar"}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                listOfNotNull(profesional, if (ev.visiblePaciente) null else "🔒 Solo para la clínica")
                    .takeIf { it.isNotEmpty() }
                    ?.let { Text(it.joinToString(" · "), color = c.textoSuave, fontSize = 11.sp) }
            }
            AccionIcono("🖨", onImprimir)
            if (puedeEditar) {
                AccionIcono("✏️", onEditar)
                AccionIcono("🗑️", onBorrar)
            }
        }
        ev.zonas.forEach { z ->
            Text(
                z.zona.nombre + ": " + cols.joinToString(" · ") { m -> "${m.corto} ${textoMedicion(z, m)}" },
                color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
            if (z.signos.isNotEmpty()) Text(z.signos.joinToString(" · ") { nombreSigno(it) }, color = c.textoSuave, fontSize = 11.sp)
        }
        if (trasplante != null && trasplante.nivel != "incompleto") {
            Text("🧮 Estimación: ${trasplante.ufDisponibles?.let(::milesTrico)} UF disponibles vs " +
                "${trasplante.ufNecesarias?.let(::milesTrico)} necesarias (${textoNivelTrasplante(trasplante.nivel)})",
                color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        ev.observaciones?.let { Text("📝 $it", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

// ─── Piezas ──────────────────────────────────────────────────────────────────

@Composable
private fun AccionIcono(icono: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center) { Text(icono, fontSize = 15.sp) }
}

@Composable
private fun TarjetaTrico(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        content = contenido,
    )
}

@Composable
internal fun ChipTrico(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun BotonTrico(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier
        .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.navy)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun BotonContorno(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier
        .clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.5.dp, c.lav, RoundedCornerShape(Sania.shape.sm.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp))
}
