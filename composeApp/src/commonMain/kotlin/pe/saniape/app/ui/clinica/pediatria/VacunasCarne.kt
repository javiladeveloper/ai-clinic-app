package pe.saniape.app.ui.clinica.pediatria

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import pe.saniape.app.data.staff.BorradorVacuna
import pe.saniape.app.data.staff.CATALOGO_VACUNAS
import pe.saniape.app.data.staff.DOSIS_OPCIONES
import pe.saniape.app.data.staff.DosisCarne
import pe.saniape.app.data.staff.EstadoDosis
import pe.saniape.app.data.staff.GRUPOS_ESQUEMA
import pe.saniape.app.data.staff.LimitesVacuna
import pe.saniape.app.data.staff.PediatriaRepo
import pe.saniape.app.data.staff.VacunaPaciente
import pe.saniape.app.data.staff.borradorDeVacuna
import pe.saniape.app.data.staff.carneVacunas
import pe.saniape.app.data.staff.cortoVacuna
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.htmlCarneVacunas
import pe.saniape.app.data.staff.lugarVacuna
import pe.saniape.app.data.staff.nombreVacuna
import pe.saniape.app.data.staff.textoResumenCarne
import pe.saniape.app.data.staff.vacunaDeJson
import pe.saniape.app.data.staff.validarVacuna
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.historia.fechaHc
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/** Colores (texto, fondo, borde) de cada estado de dosis, con el tema. */
@Composable
internal fun coloresDosis(e: EstadoDosis): Triple<Color, Color, Color> {
    val c = Sania.colors
    return when (e) {
        EstadoDosis.APLICADA -> Triple(c.ok, c.okBg, c.ok)
        EstadoDosis.PENDIENTE -> Triple(c.pend, c.pendBg, c.pend)
        EstadoDosis.ATRASADA -> Triple(c.error, c.errorBg, c.error)
        EstadoDosis.PROXIMA -> Triple(c.info, c.infoBg, c.info)
        else -> Triple(c.textoSuave, c.superficie, c.borde)
    }
}

private fun iconoDosis(e: EstadoDosis): String = when (e) {
    EstadoDosis.APLICADA -> "✓ "
    EstadoDosis.ATRASADA -> "! "
    EstadoDosis.PENDIENTE -> "• "
    EstadoDosis.PROXIMA -> "◷ "
    else -> ""
}

/** Qué se registra: una dosis del carné, otra vacuna, o editar un registro. */
private sealed interface FormVac {
    data class Dosis(val d: DosisCarne) : FormVac
    data object Otra : FormVac
    data class Editar(val v: VacunaPaciente) : FormVac
}

/**
 * 💉 Vacunas (dentro de la pestaña 👶 Crecimiento): el carné por edad según el
 * Esquema Nacional de Inmunizaciones (NTS 246-MINSA/DGIESP-2026). Gemelo de
 * VacunasCarne.tsx: cada dosis es un chip con su estado; tocar una que falta
 * abre el registro (fecha = hoy de la clínica, lote, "aplicada en otro
 * establecimiento"); tocar una aplicada muestra el detalle (editar / eliminar
 * con confirmación). Una consulta a /api/staff/vacunas al abrir la pestaña; el
 * estado de cada dosis es puro (`carneVacunas`). Es apoyo: no bloquea nada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VacunasCarne(
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    fechaNacimiento: String?,
    puedeEditar: Boolean,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    var token by remember { mutableStateOf(0) }
    var items by remember(pacienteId) { mutableStateOf<List<VacunaPaciente>?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    LaunchedEffect(pacienteId, token) {
        val r = PediatriaRepo.vacunas(pacienteId)
        if (r.items != null) { items = r.items; error = null } else if (items == null) error = r.error
        cargando = false
    }
    var form by remember { mutableStateOf<FormVac?>(null) }
    var detalle by remember { mutableStateOf<Pair<VacunaPaciente, Boolean>?>(null) }

    if (cargando && items == null) {
        Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
        return
    }
    error?.let { msg ->
        Tarjeta {
            Text(msg, color = c.error, fontSize = 13.sp)
            Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp).clickable { error = null; cargando = true; token++ }.padding(6.dp))
        }
        return
    }

    val lista = items.orEmpty()
    val hoy = hoyClinicaIso()
    val carne = remember(fechaNacimiento, hoy, lista) { carneVacunas(fechaNacimiento, hoy, lista) }
    val r = carne.resumen
    val siguiente = r.siguientes.firstOrNull()
    fun tocar(d: DosisCarne) {
        val reg = d.registro
        if (reg != null) detalle = reg to d.viaHexavalente
        else if (puedeEditar && d.estado != EstadoDosis.SIN_FECHA) form = FormVac.Dosis(d)
    }

    Tarjeta {
        Text("💉 Vacunas", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Esquema Nacional de Inmunizaciones · NTS 246-MINSA/DGIESP-2026 · apoyo", color = c.textoSuave, fontSize = 10.sp)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPed("🖨 Carné") {
                acciones.abrirHtml(
                    htmlCarneVacunas(carne, fechaNacimiento, hoy, pacienteNombre, pacienteDocumento, clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal),
                    "Carné de vacunas",
                )
            }
            if (puedeEditar) BotonPed("＋ Otra vacuna") { form = FormVac.Otra }
        }

        // Resumen
        val (fgR, bgR) = when {
            r.atrasadas > 0 -> c.error to c.errorBg
            r.alDia -> c.ok to c.okBg
            else -> c.texto to c.chipBg
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bgR)
                .border(1.dp, if (r.atrasadas > 0 || r.alDia) fgR else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text("${textoResumenCarne(carne, fechaNacimiento != null)} · ${r.aplicadas} de ${r.total} aplicadas",
                color = fgR, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            siguiente?.let { s ->
                Text(
                    "${if (s.estado == EstadoDosis.PROXIMA) "Próxima" else "Le toca"}: ${s.dosis.vacuna} ${s.dosis.dosis}" +
                        (s.fechaRecomendada?.let { " (${fechaHc(it)})" } ?: ""),
                    color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Carné por edad
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GRUPOS_ESQUEMA.forEach { g ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(8.dp),
                ) {
                    Text(g.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        carne.dosis.filter { it.dosis.grupo == g }.forEach { d ->
                            val accionable = d.registro != null || (puedeEditar && d.estado != EstadoDosis.SIN_FECHA)
                            ChipDosis(d, accionable) { tocar(d) }
                        }
                    }
                }
            }
        }

        if (carne.otras.isNotEmpty()) {
            Text("OTRAS VACUNAS", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                carne.otras.forEach { o ->
                    val (fg, bg, borde) = coloresDosis(EstadoDosis.APLICADA)
                    Column(
                        Modifier.widthIn(max = 260.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
                            .border(1.5.dp, borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable(role = Role.Button) { detalle = o to false }.padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text("✓ ${cortoVacuna(o.vacunaCodigo, o.vacunaNombre)} · ${o.dosis}", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(fechaHc(o.fechaAplicacion), color = fg, fontSize = 10.sp)
                    }
                }
            }
        }
        if (fechaNacimiento == null) {
            Text("Registra la fecha de nacimiento para saber qué dosis le tocan y cuáles están atrasadas.",
                color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }

    form?.let { f ->
        FormularioVacuna(
            form = f, pacienteId = pacienteId, hoy = hoy, fechaNacimiento = fechaNacimiento,
            onCerrar = { form = null },
            onGuardada = { v ->
                form = null
                if (v != null) items = if (lista.any { it.id == v.id }) lista.map { if (it.id == v.id) v else it } else listOf(v) + lista
                token++
            },
        )
    }
    detalle?.let { (v, via) ->
        DetalleVacuna(v, via, puedeEditar,
            onCerrar = { detalle = null },
            onEditar = { detalle = null; form = FormVac.Editar(v) },
            onBorrada = { id -> detalle = null; items = lista.filter { it.id != id }; token++ },
        )
    }
}

@Composable
private fun ChipDosis(d: DosisCarne, accionable: Boolean, onClick: () -> Unit) {
    val (fg, bg, borde) = coloresDosis(d.estado)
    val reg = d.registro
    val sub = if (reg != null) {
        fechaHc(reg.fechaAplicacion) + (if (d.viaHexavalente) " (hexavalente)" else "") + (if (reg.externa) " · otro EE.SS." else "")
    } else d.estado.texto
    val desc = "${d.dosis.vacuna} ${d.dosis.dosis}: ${d.estado.texto}" + (reg?.let { " el ${fechaHc(it.fechaAplicacion)}" } ?: "")
    val apagada = d.estado == EstadoDosis.FUERA_DE_EDAD
    Column(
        Modifier.widthIn(max = 260.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
            .border(1.5.dp, borde.copy(alpha = if (apagada) 0.5f else 1f), RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = accionable, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text("${iconoDosis(d.estado)}${d.dosis.vacuna} · ${d.dosis.dosis}", color = fg.copy(alpha = if (apagada) 0.7f else 1f),
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = fg.copy(alpha = if (apagada) 0.7f else 1f), fontSize = 10.sp)
    }
}

// ─── Registrar / editar ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioVacuna(
    form: FormVac,
    pacienteId: String,
    hoy: String,
    fechaNacimiento: String?,
    onCerrar: () -> Unit,
    onGuardada: (VacunaPaciente?) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val inicial = (form as? FormVac.Editar)?.v?.let(::borradorDeVacuna)
    val deDosis = (form as? FormVac.Dosis)?.d?.dosis
    var codigo by remember { mutableStateOf(inicial?.vacunaCodigo ?: deDosis?.acepta?.first() ?: "") }
    var nombre by remember { mutableStateOf(inicial?.vacunaNombre.orEmpty()) }
    var dosis by remember { mutableStateOf(inicial?.dosis ?: deDosis?.dosis.orEmpty()) }
    var fecha by remember { mutableStateOf(inicial?.fechaAplicacion ?: hoy) }
    var lote by remember { mutableStateOf(inicial?.lote.orEmpty()) }
    var externa by remember { mutableStateOf(inicial?.externa ?: false) }
    var establecimiento by remember { mutableStateOf(inicial?.establecimiento.orEmpty()) }
    var observaciones by remember { mutableStateOf(inicial?.observaciones.orEmpty()) }
    var elegirFecha by remember { mutableStateOf(false) }
    var elegirVacuna by remember { mutableStateOf(false) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }

    val titulo = when (form) {
        is FormVac.Editar -> "Editar ${cortoVacuna(form.v.vacunaCodigo, form.v.vacunaNombre)} · ${form.v.dosis}"
        is FormVac.Dosis -> "Registrar ${form.d.dosis.vacuna} · ${form.d.dosis.dosis}"
        FormVac.Otra -> "Registrar otra vacuna"
    }

    fun guardar() {
        if (guardando) return
        val b = BorradorVacuna(
            pacienteId = pacienteId, vacunaCodigo = codigo, vacunaNombre = nombre, dosis = dosis, fechaAplicacion = fecha,
            lote = lote, establecimiento = establecimiento, externa = externa, observaciones = observaciones,
        )
        val e = validarVacuna(b, hoy, fechaNacimiento)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = PediatriaRepo.guardarVacuna(b, (form as? FormVac.Editar)?.v?.id)
            guardando = false
            if (r.registrada) {
                Toaster.exito(if (form is FormVac.Editar) "Vacuna actualizada" else "Vacuna registrada")
                onGuardada((r.cuerpo?.get("vacuna") as? JsonObject)?.let(::vacunaDeJson))
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = titulo,
        subtitulo = "Esquema nacional · fecha de hoy por defecto",
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        deDosis?.nota?.let { Text("ℹ️ $it", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        val opciones = deDosis?.acepta
        if (opciones != null && opciones.size > 1) {
            // El casillero acepta varias (pentavalente / hexavalente): chips.
            EtqForm("Vacuna aplicada")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                opciones.forEach { op -> ChipPed(cortoVacuna(op), activo = codigo == op) { codigo = op } }
            }
            Spacer(Modifier.height(12.dp))
        } else if (opciones == null) {
            EtqForm("Vacuna")
            CajaSelectorForm(if (codigo.isBlank()) "Elige…" else nombreVacuna(codigo)) { elegirVacuna = true }
            if (codigo == "otra") {
                Spacer(Modifier.height(8.dp))
                EtqForm("Nombre de la vacuna")
                CampoTexto(nombre, "Ej. Meningococo B") { nombre = it.take(LimitesVacuna.NOMBRE) }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (form !is FormVac.Dosis) {
            EtqForm("Dosis")
            FlowRow(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DOSIS_OPCIONES.forEach { o -> ChipPed(o, activo = dosis == o) { dosis = o } }
            }
            CampoTexto(dosis, "1.ª, refuerzo…") { dosis = it.take(LimitesVacuna.DOSIS) }
            Spacer(Modifier.height(12.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                EtqForm("Fecha")
                CajaSelectorForm(fechaHc(fecha)) { elegirFecha = true }
            }
            Column(Modifier.weight(1f)) {
                EtqForm("Lote")
                CampoTexto(lote, "Opcional") { lote = it.take(LimitesVacuna.LOTE) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(role = Role.Checkbox) { externa = !externa },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = externa, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.navy))
            Text("Aplicada en otro establecimiento", color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        }
        if (externa) {
            EtqForm("Dónde")
            CampoTexto(establecimiento, "Ej. C.S. La Esperanza, campaña escolar…") { establecimiento = it.take(LimitesVacuna.ESTABLECIMIENTO) }
        }
        Spacer(Modifier.height(10.dp))
        EtqForm("Observaciones (internas)")
        OutlinedTextField(
            value = observaciones, onValueChange = { observaciones = it.take(LimitesVacuna.OBSERVACIONES) },
            placeholder = { Text("Opcional: reacción, indicación…", color = c.textoSuave, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), colors = coloresCampoForm(),
        )
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
            fechaNacimiento != null && f < fechaNacimiento -> Toaster.error("La fecha es anterior al nacimiento.")
            else -> fecha = f
        }
    }, onCerrar = { elegirFecha = false }, inicial = fecha)
    if (elegirVacuna) {
        // Lista corta (24): Column con scroll del diálogo, sin listas perezosas.
        AlertDialog(
            onDismissRequest = { elegirVacuna = false },
            title = { Text("Vacuna", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    CATALOGO_VACUNAS.forEach { v ->
                        Text(v.nombre, color = if (v.codigo == codigo) c.navy else c.texto, fontSize = 14.sp,
                            fontWeight = if (v.codigo == codigo) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp))
                                .clickable { codigo = v.codigo; elegirVacuna = false }.padding(horizontal = 8.dp, vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { elegirVacuna = false }) { Text("Cerrar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

@Composable
private fun CampoTexto(valor: String, ejemplo: String, onChange: (String) -> Unit) {
    val c = Sania.colors
    OutlinedTextField(
        value = valor, onValueChange = onChange, singleLine = true,
        placeholder = { Text(ejemplo, color = c.textoSuave, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(), colors = coloresCampoForm(),
    )
}

// ─── Detalle de una dosis aplicada ───────────────────────────────────────────

@Composable
private fun DetalleVacuna(
    v: VacunaPaciente,
    viaHexavalente: Boolean,
    puedeEditar: Boolean,
    onCerrar: () -> Unit,
    onEditar: () -> Unit,
    onBorrada: (String) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var confirmar by remember { mutableStateOf(false) }
    var borrando by remember { mutableStateOf(false) }
    val filas = listOf(
        "Vacuna" to nombreVacuna(v.vacunaCodigo, v.vacunaNombre),
        "Dosis" to v.dosis,
        "Fecha" to fechaHc(v.fechaAplicacion),
        "Lote" to v.lote,
        "Lugar" to lugarVacuna(v),
        "Observaciones" to v.observaciones,
    ).filter { !it.second.isNullOrBlank() }
    AlertDialog(
        onDismissRequest = { if (!borrando) onCerrar() },
        title = { Text(if (confirmar) "¿Eliminar este registro?" else "Dosis aplicada", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (viaHexavalente) Text("La IPV de esta dosis está cubierta por la hexavalente.", color = c.textoSuave, fontSize = 12.sp)
                filas.forEach { (k, x) ->
                    Row {
                        Text(k, color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.widthIn(min = 96.dp))
                        Text(x.orEmpty(), color = c.texto, fontSize = 13.sp, textAlign = TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = {
            if (puedeEditar) {
                if (confirmar) TextButton(enabled = !borrando, onClick = {
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = PediatriaRepo.borrarVacuna(v.id)
                        borrando = false
                        if (r.registrada) { Toaster.exito("Registro eliminado"); onBorrada(v.id) }
                        else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Sí, eliminar", color = c.error, fontWeight = FontWeight.Bold) }
                else TextButton(onClick = onEditar) { Text("Editar", color = c.navy, fontWeight = FontWeight.Bold) }
            } else TextButton(onClick = onCerrar) { Text("Cerrar", color = c.textoSuave) }
        },
        dismissButton = {
            if (puedeEditar) {
                if (confirmar) TextButton(enabled = !borrando, onClick = { confirmar = false }) { Text("No", color = c.textoSuave) }
                else TextButton(onClick = { confirmar = true }) { Text("Eliminar", color = c.error) }
            }
        },
        containerColor = c.superficie,
    )
}
