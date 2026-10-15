package pe.saniape.app.ui.clinica.nutricion

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import pe.saniape.app.data.staff.ComidaEditor
import pe.saniape.app.data.staff.ContenidoEditorPlan
import pe.saniape.app.data.staff.DatosCalculo
import pe.saniape.app.data.staff.FrasesCampoPlan
import pe.saniape.app.data.staff.SugerenciasPlan
import pe.saniape.app.data.staff.frasesCampoTextoPlan
import pe.saniape.app.data.staff.frasesComidaPlan
import pe.saniape.app.data.staff.LimitesPlan
import pe.saniape.app.data.staff.MedicionesRepo
import pe.saniape.app.data.staff.NivelActividad
import pe.saniape.app.data.staff.ObjetivoPeso
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.PlanAlimentacion
import pe.saniape.app.data.staff.PlanAlimentacionRepo
import pe.saniape.app.data.staff.PlantillaPlan
import pe.saniape.app.data.staff.Sexo
import pe.saniape.app.data.staff.SnapshotCalculo
import pe.saniape.app.data.staff.aJson
import pe.saniape.app.data.staff.aNumero
import pe.saniape.app.data.staff.borradorDesdeEditor
import pe.saniape.app.data.staff.calcularRequerimiento
import pe.saniape.app.data.staff.conClave
import pe.saniape.app.data.staff.contenidoDesde
import pe.saniape.app.data.staff.enlaceWhatsAppSede
import pe.saniape.app.data.staff.fechaPlan
import pe.saniape.app.data.staff.gramosMacros
import pe.saniape.app.data.staff.horaPlan
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.htmlPlanAlimentario
import pe.saniape.app.data.staff.kcalParaUsar
import pe.saniape.app.data.staff.macrosDePlan
import pe.saniape.app.data.staff.normalizarComidas
import pe.saniape.app.data.staff.LimitesAlimento
import pe.saniape.app.data.staff.avisoItemsIlegibles
import pe.saniape.app.data.staff.detalleVisible
import pe.saniape.app.data.staff.itemConHabitual
import pe.saniape.app.data.staff.redondearJs
import pe.saniape.app.data.staff.refAlimento
import pe.saniape.app.data.staff.sumarAporte
import pe.saniape.app.data.staff.numJs
import pe.saniape.app.data.staff.planDeJson
import pe.saniape.app.data.staff.tallaEnCm
import pe.saniape.app.data.staff.textoPct
import pe.saniape.app.data.staff.textoPlanWhatsApp
import pe.saniape.app.data.staff.ultimaTalla
import pe.saniape.app.data.staff.usarCalculoEnEditor
import pe.saniape.app.data.staff.validarDatosCalculo
import pe.saniape.app.data.staff.validarPlan
import pe.saniape.app.data.staff.validarPlantilla
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.DialogoHora
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña 🥗 Plan alimentario de la ficha (solo NUTRICIÓN, la misma regla que
 * ⚖️ Medidas: `pacienteEsNutricion`). Gemelo de `PlanAlimentarioTab.tsx` de la web:
 *
 *  · Lista de planes (el más reciente abierto): ver / imprimir, WhatsApp,
 *    editar, borrar y "Guardar como plantilla".
 *  · Editor: fecha, objetivo, comidas del día (los 5 tiempos; se agregan,
 *    quitan y reordenan), recomendaciones, evitar, hidratación y la
 *    calculadora (Mifflin-St Jeor) con peso y talla de la última medición.
 *    Cada texto con chips para tocar y autocompletado que aprende de lo que
 *    la clínica ya escribió (PlanAlimentacionChips.kt + /sugerencias), y el
 *    interruptor "Visible para el paciente en su app".
 *  · Recién guardado: imprimir y WhatsApp ahí mismo (y, sin cita por delante,
 *    "📅 Agendar siguiente control").
 *
 * Carga SOLO cuando la pestaña se abre (la monta el padre en su `when`). Las
 * plantillas y las medidas se piden recién al abrir el editor. Sin listas
 * perezosas: vive dentro del `verticalScroll` de la ficha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanAlimentarioTab(
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    /** Para la calculadora (de la fecha de nacimiento o la edad anotada). */
    edad: Int?,
    /** `puede('pacientes') || puede('sesiones')` y ficha no dada de baja (como el API). */
    puedeEditar: Boolean,
    /** Teléfono para avisos (el del apoderado si los recibe). */
    telefono: String?,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    /** La cita de la que viene (agenda o ⚖️ Medidas): el plan NUEVO queda vinculado a ella. */
    citaId: String? = null,
    /** Abrir un plan nuevo de una ("Continuar con el plan alimentario →"); parte del último. */
    abrirNuevo: Boolean = false,
    /** Se abrió de una: el padre apaga la bandera (al remontar no se reabre otro plan nuevo). */
    onAbiertoNuevo: () -> Unit = {},
    /** La clínica activa: el autocompletado aprendido es de ELLA (caché por clínica). */
    clinicaId: String? = null,
    /** "📅 Agendar siguiente control" tras guardar (la ficha lo pasa solo si no tiene cita por delante). */
    onAgendarControl: (() -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<PlanAlimentacion>?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pacienteId, token) {
        val r = PlanAlimentacionRepo.listar(pacienteId)
        if (r.items != null) { items = r.items; error = null } else if (items == null) error = r.error
        cargando = false
    }
    // Quién indicó cada plan (terapeuta_id → nombre), como `profesionales` en la web.
    var profesionales by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        profesionales = runCatching { PacientesRepo.terapeutasActivos().associate { it.id to it.nombre } }.getOrDefault(emptyMap())
    }
    // null = cerrado · "nuevo" · id del plan que se edita
    var editor by remember { mutableStateOf(if (abrirNuevo && puedeEditar) "nuevo" else null) }
    LaunchedEffect(Unit) { if (abrirNuevo) onAbiertoNuevo() }
    // El plan recién guardado: imprimir y mandarlo por WhatsApp quedan ahí mismo.
    var recien by remember { mutableStateOf<String?>(null) }
    var confirmarBorrar by remember { mutableStateOf<PlanAlimentacion?>(null) }
    var borrando by remember { mutableStateOf(false) }
    var comoPlantilla by remember { mutableStateOf<PlanAlimentacion?>(null) }

    if (cargando && items == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 150, 110).forEach { h ->
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

    val planes = items.orEmpty()
    val waValido = enlaceWhatsAppSede(telefono) != null
    fun imprimir(p: PlanAlimentacion) {
        acciones.abrirHtml(
            htmlPlanAlimentario(p, clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal,
                pacienteNombre, pacienteDocumento, p.terapeutaId?.let { profesionales[it] }),
            "Plan alimentario",
        )
    }
    fun enviarWhatsApp(p: PlanAlimentacion) {
        val texto = textoPlanWhatsApp(p, clinicaNombre, pacienteNombre, p.terapeutaId?.let { profesionales[it] })
        // enlaceWhatsAppSede → urlWhatsApp (api.whatsapp.com/send): wa.me?text= rompe los emojis.
        val url = enlaceWhatsAppSede(telefono, texto)
        if (url == null) Toaster.error("El paciente no tiene un celular válido") else acciones.abrirUrl(url)
    }
    val guardado = recien?.takeIf { editor == null }?.let { id -> planes.firstOrNull { it.id == id } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🥗 Plan alimentario", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        guardado?.let { g ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.okBg)
                    .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Plan guardado · ¿se lo entregas?", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("✕", color = c.textoSuave, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { recien = null }.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonPlan("🖨 Imprimir / PDF") { imprimir(g) }
                    if (waValido) BotonPlan("💬 Enviar por WhatsApp") { enviarWhatsApp(g) }
                    onAgendarControl?.let { agendar -> BotonPlan("📅 Agendar siguiente control") { recien = null; agendar() } }
                }
            }
        }
        if (puedeEditar && planes.isNotEmpty()) BotonPlan("＋ Nuevo plan", lleno = true) { editor = "nuevo" }

        if (planes.isEmpty()) {
            TarjetaPlanUi {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🥗", fontSize = 30.sp)
                    Text("Aún no hay planes alimentarios", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Escribe el objetivo, las comidas del día y las recomendaciones. Se imprime, se manda por WhatsApp y sale en la historia clínica.",
                        color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    if (puedeEditar) {
                        Spacer(Modifier.height(12.dp))
                        BotonPlan("＋ Nuevo plan", lleno = true) { editor = "nuevo" }
                    }
                }
            }
        } else {
            planes.forEachIndexed { i, p ->
                // key: al borrar o agregar, cada tarjeta conserva su abierto/cerrado.
                androidx.compose.runtime.key(p.id) {
                    TarjetaPlan(
                        plan = p, actual = i == 0, puedeEditar = puedeEditar, waValido = waValido,
                        profesional = p.terapeutaId?.let { profesionales[it] },
                        onImprimir = { imprimir(p) },
                        onWhatsApp = { enviarWhatsApp(p) },
                        onEditar = { editor = p.id },
                        onPlantilla = { comoPlantilla = p },
                        onBorrar = { confirmarBorrar = p },
                    )
                }
            }
        }
    }

    editor?.let { e ->
        val enEdicion = if (e != "nuevo") planes.firstOrNull { it.id == e } else null
        androidx.compose.runtime.key(e) {
            EditorPlan(
                pacienteId = pacienteId, edad = edad, plan = enEdicion, ultimo = planes.firstOrNull(),
                citaId = citaId,
                clinicaId = clinicaId,
                onCerrar = { editor = null },
                onGuardado = { p, nuevo ->
                    editor = null
                    recien = p.id
                    items = pe.saniape.app.data.staff.ordenarPlanes(if (nuevo) listOf(p) + planes else planes.map { if (it.id == p.id) p else it })
                },
            )
        }
    }

    comoPlantilla?.let { p -> DialogoGuardarPlantilla(p) { comoPlantilla = null } }

    confirmarBorrar?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!borrando) confirmarBorrar = null },
            title = { Text("¿Eliminar este plan?", fontWeight = FontWeight.Bold) },
            text = { Text("Plan del ${fechaPlan(p.fecha)}${p.objetivo?.let { " · $it" }.orEmpty()}", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    // Dos toques en el mismo frame: `enabled` recién aplica en la siguiente recomposición.
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = PlanAlimentacionRepo.borrar(p.id)
                        borrando = false
                        // Ya lo había borrado otro equipo: para el usuario es lo mismo.
                        if (r.registrada || r.rechazo?.codigo == "NO_ENCONTRADO") {
                            confirmarBorrar = null
                            items = planes.filter { it.id != p.id }
                            if (recien == p.id) recien = null
                            Toaster.exito("Plan eliminado")
                        } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !borrando, onClick = { confirmarBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ─── Un plan (lectura) ───────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaPlan(
    plan: PlanAlimentacion,
    actual: Boolean,
    puedeEditar: Boolean,
    waValido: Boolean,
    profesional: String?,
    onImprimir: () -> Unit,
    onWhatsApp: () -> Unit,
    onEditar: () -> Unit,
    onPlantilla: () -> Unit,
    onBorrar: () -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(actual) }
    val comidas = remember(plan) { normalizarComidas(plan.comidas) }
    val macros = remember(plan) { macrosDePlan(plan) }
    TarjetaPlanUi {
        Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🥗 Plan del ${fechaPlan(plan.fecha)}", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (actual) Pildora("Vigente", c.ok, c.okBg)
                    plan.kcal?.let { Pildora("$it kcal", c.teal, c.tealBg) }
                    // El paciente no lo ve en su app (las filas viejas, sin el dato, sí lo ven).
                    if (!plan.visiblePaciente) Pildora("🔒 Solo la clínica", c.textoSuave, c.chipBg)
                }
                plan.objetivo?.let {
                    Text("🎯 $it", color = c.textoSuave, fontSize = 12.sp, maxLines = if (abierto) Int.MAX_VALUE else 1,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(if (abierto) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        if (abierto) {
            Spacer(Modifier.height(10.dp))
            macros?.let { ms ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ms.forEach { m ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(m.nombre.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${textoPct(m.pct)} %", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            m.gramos?.let { Text("$it g/día", color = c.textoSuave, fontSize = 10.sp) }
                        }
                    }
                }
            }
            if (comidas.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
                ) {
                    comidas.forEachIndexed { i, cm ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                cm.tiempo.uppercase() + (horaPlan(cm.hora)?.let { " · $it" }.orEmpty()) +
                                    (if (cm.items.isNotEmpty()) " · ≈ ${redondearJs(sumarAporte(cm.items).kcal).toInt()} kcal" else ""),
                                color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                            )
                            if (cm.items.isNotEmpty()) ItemsLectura(cm.items)
                            detalleVisible(cm).takeIf { it.isNotEmpty() }?.let {
                                Text(it, color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
            if (comidas.any { it.items.isNotEmpty() }) {
                Box(Modifier.padding(bottom = 10.dp)) {
                    ResumenAporteUi(
                        itemsPorComida = comidas.map { it.items }, kcal = plan.kcal?.toDouble(),
                        proteinasPct = plan.proteinasPct, carbohidratosPct = plan.carbohidratosPct, grasasPct = plan.grasasPct,
                        compacto = true,
                    )
                }
            }
            listOf("✅ Recomendaciones" to plan.recomendaciones, "🚫 Evitar" to plan.evitar, "💧 Hidratación" to plan.hidratacion)
                .filter { !it.second.isNullOrBlank() }
                .forEach { (t, v) ->
                    Text(t.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Text(v.orEmpty(), color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                }
            profesional?.let { Text("Indicado por $it", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPlan("🖨 Ver / imprimir", onClick = onImprimir)
                if (waValido) BotonPlan("📱 WhatsApp", onClick = onWhatsApp)
                if (puedeEditar) {
                    BotonPlan("✏️ Editar", onClick = onEditar)
                    BotonPlan("💾 Guardar como plantilla", onClick = onPlantilla)
                    BotonPlan("🗑️", onClick = onBorrar)
                }
            }
        }
    }
}

@Composable
private fun DialogoGuardarPlantilla(plan: PlanAlimentacion, onCerrar: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(plan.objetivo?.take(60).orEmpty()) }
    var guardando by remember { mutableStateOf(false) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }

    fun guardar() {
        if (guardando) return
        val e = validarPlantilla(nombre, plan)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = PlanAlimentacionRepo.guardarPlantilla(nombre, plan)
            guardando = false
            if (r.registrada) {
                val actualizada = (r.cuerpo?.get("actualizada") as? JsonPrimitive)?.booleanOrNull == true
                Toaster.exito(if (actualizada) "Plantilla actualizada" else "Plantilla guardada")
                onCerrar()
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar la plantilla"
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = "Guardar como plantilla",
        subtitulo = "Para no escribir todo desde cero la próxima vez",
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtqForm("Nombre de la plantilla")
        CampoPlan(nombre, { nombre = it.take(LimitesPlan.NOMBRE_PLANTILLA) }, "Ej. Hipocalórica 1500 kcal")
        Text("Si ya hay una con ese nombre, se reemplaza. Los datos del paciente no se guardan.",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        CajaErrores(errores)
    }
}

// ─── Editor (nuevo o editar) ─────────────────────────────────────────────────

@Composable
private fun EditorPlan(
    pacienteId: String,
    edad: Int?,
    /** El plan que se edita (null = nuevo). */
    plan: PlanAlimentacion?,
    /** El último plan del paciente: un plan nuevo parte de él. */
    ultimo: PlanAlimentacion?,
    /** Solo para un plan NUEVO: la cita de la que viene. */
    citaId: String?,
    clinicaId: String?,
    onCerrar: () -> Unit,
    onGuardado: (PlanAlimentacion, Boolean) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val nuevo = plan == null
    var fecha by remember { mutableStateOf(plan?.fecha?.takeIf { it.isNotBlank() } ?: hoyClinicaIso()) }
    var elegirFecha by remember { mutableStateOf(false) }
    var cont by remember { mutableStateOf(contenidoDesde(plan ?: ultimo)) }
    var calculo by remember { mutableStateOf(plan?.calculo) }
    // Plantillas: recién al abrir el editor de un plan nuevo.
    var plantillas by remember { mutableStateOf<List<PlantillaPlan>>(emptyList()) }
    LaunchedEffect(nuevo) { if (nuevo) plantillas = PlanAlimentacionRepo.plantillas() }
    var origen by remember { mutableStateOf(if (ultimo != null) "ultimo" else "blanco") }
    var menuOrigen by remember { mutableStateOf(false) }
    var borrarPlantilla by remember { mutableStateOf(false) }
    var borrandoPlantilla by remember { mutableStateOf(false) }
    var calcAbierta by remember { mutableStateOf(false) }
    var horaDe by remember { mutableStateOf<Int?>(null) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    // Lo ve el paciente en su app (por defecto sí; las filas viejas no traen el dato = sí).
    var visible by remember { mutableStateOf(plan?.visiblePaciente ?: true) }
    // Autocompletado que aprende: UN viaje al abrir el editor (caché de la sesión);
    // al escribir se filtra en memoria. Si falla, quedan los chips base.
    var sugerencias by remember { mutableStateOf<SugerenciasPlan?>(null) }
    LaunchedEffect(Unit) { sugerencias = PlanAlimentacionRepo.sugerencias(clinicaId) }
    // Base de alimentos: el catálogo UNA vez por sesión + los propios; los frecuentes vienen en /sugerencias.
    val alimentos = recordarDatosAlimentos(sugerencias?.alimentos.orEmpty())
    var modalAlimentos by remember { mutableStateOf<ModoAlimentosClinica?>(null) }

    fun partirDe(valor: String) {
        origen = valor
        borrarPlantilla = false
        calculo = null
        cont = when (valor) {
            "blanco" -> contenidoDesde(null)
            "ultimo" -> contenidoDesde(ultimo)
            else -> plantillas.firstOrNull { it.id == valor }?.let { contenidoDesde(it) } ?: cont
        }
    }

    fun eliminarPlantilla(id: String) {
        if (borrandoPlantilla) return
        borrandoPlantilla = true
        scope.launch {
            val r = PlanAlimentacionRepo.borrarPlantilla(id)
            borrandoPlantilla = false
            if (!r.registrada) { Toaster.error(r.rechazo?.error ?: "No se pudo eliminar la plantilla"); return@launch }
            Toaster.exito("Plantilla eliminada")
            borrarPlantilla = false
            origen = if (ultimo != null) "ultimo" else "blanco"
            plantillas = plantillas.filter { it.id != id }
        }
    }

    fun setComida(k: Int, f: (ComidaEditor) -> ComidaEditor) {
        cont = cont.copy(comidas = cont.comidas.map { if (it.k == k) f(it) else it })
    }
    fun moverComida(i: Int, d: Int) {
        val j = i + d
        if (j < 0 || j >= cont.comidas.size) return
        val lista = cont.comidas.toMutableList()
        val t = lista[i]; lista[i] = lista[j]; lista[j] = t
        cont = cont.copy(comidas = lista)
    }

    fun guardar() {
        if (guardando) return
        // Al EDITAR, alimentos guardados que la app no puede leer se perderían con itemsV: 1.
        if (!nuevo) avisoItemsIlegibles(cont.itemsIlegibles)?.let { aviso ->
            errores = listOf(aviso)
            Toaster.error(aviso)
            return
        }
        val b = borradorDesdeEditor(pacienteId, fecha, cont, calculo,
            citaId = if (nuevo) citaId else null, visiblePaciente = visible)
        val e = validarPlan(b)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = PlanAlimentacionRepo.guardar(b, plan?.id)
            guardando = false
            val fila = (r.cuerpo?.get("plan") as? JsonObject)?.let(::planDeJson)
            if (r.registrada && fila != null) {
                // Lo escrito ya cuenta para el autocompletado: la próxima vez se vuelve a pedir.
                PlanAlimentacionRepo.olvidarSugerencias()
                Toaster.exito(if (nuevo) "Plan guardado" else "Plan actualizado")
                onGuardado(fila, nuevo)
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    val plantillaElegida = plantillas.firstOrNull { it.id == origen }
    val kcalNum = aNumero(cont.kcal)?.takeIf { !it.isNaN() }
    val pP = aNumero(cont.prot)?.takeIf { !it.isNaN() }
    val pC = aNumero(cont.carb)?.takeIf { !it.isNaN() }
    val pG = aNumero(cont.grasa)?.takeIf { !it.isNaN() }
    val gramos = gramosMacros(kcalNum, pP, pC, pG)
    val sumaPct = if (pP != null && pC != null && pG != null) pe.saniape.app.data.staff.redondearJs((pP + pC + pG) * 10) / 10 else null

    DialogoForm(
        titulo = if (nuevo) "Nuevo plan alimentario" else "Editar plan alimentario",
        subtitulo = "🥗 Objetivo, comidas del día y recomendaciones",
        textoAccion = if (guardando) "Guardando…" else if (nuevo) "Guardar plan" else "Guardar cambios",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtqForm("Fecha")
        CajaSelectorForm(fechaPlan(fecha)) { elegirFecha = true }

        if (nuevo) {
            Spacer(Modifier.height(12.dp))
            EtqForm("Partir de")
            val etiquetaOrigen = when (origen) {
                "ultimo" -> ultimo?.let { "El último plan (${fechaPlan(it.fecha)})" } ?: "En blanco"
                "blanco" -> "En blanco"
                else -> plantillaElegida?.nombre ?: "En blanco"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    CajaSelectorForm("$etiquetaOrigen  ▾") { menuOrigen = true }
                    DropdownMenu(expanded = menuOrigen, onDismissRequest = { menuOrigen = false }) {
                        ultimo?.let { u ->
                            DropdownMenuItem(text = { Text("El último plan (${fechaPlan(u.fecha)})", fontSize = 14.sp) },
                                onClick = { menuOrigen = false; partirDe("ultimo") })
                        }
                        DropdownMenuItem(text = { Text("En blanco", fontSize = 14.sp) }, onClick = { menuOrigen = false; partirDe("blanco") })
                        if (plantillas.isNotEmpty()) {
                            Text("PLANTILLAS", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            plantillas.forEach { pl ->
                                DropdownMenuItem(text = { Text(pl.nombre, fontSize = 14.sp) }, onClick = { menuOrigen = false; partirDe(pl.id) })
                            }
                        }
                    }
                }
                if (plantillaElegida != null && !borrarPlantilla) {
                    Text("🗑️", fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp).clickable { borrarPlantilla = true }.padding(8.dp))
                }
            }
            if (plantillaElegida != null && borrarPlantilla) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("¿Eliminar la plantilla «${plantillaElegida.nombre}»?", color = c.texto, fontSize = 13.sp)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonPlan(if (borrandoPlantilla) "Eliminando…" else "Eliminar", peligro = true) { eliminarPlantilla(plantillaElegida.id) }
                        BotonPlan("No") { if (!borrandoPlantilla) borrarPlantilla = false }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        CampoConChipsPlan(
            label = "🎯 Objetivo", valor = cont.objetivo,
            onChange = { cont = cont.copy(objetivo = it.take(LimitesPlan.OBJETIVO)) },
            frases = frasesCampoTextoPlan("objetivo", sugerencias), placeholder = "Ej. Bajar 4 kg en 2 meses, controlar la glucosa",
            minLineas = 1, chipsVisibles = 8,
        )

        // ── Energía y macros ──
        Spacer(Modifier.height(14.dp))
        SeccionPlan {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥 ENERGÍA Y MACROS", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
                Text("(opcional)", color = c.textoSuave, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            BotonPlan(if (calcAbierta) "🧮 Cerrar calculadora" else "🧮 Calcular requerimiento") { calcAbierta = !calcAbierta }
            if (calcAbierta) {
                Spacer(Modifier.height(8.dp))
                Calculadora(pacienteId, edad, calculo) { s ->
                    calculo = s.aJson()
                    cont = usarCalculoEnEditor(cont, s)
                    calcAbierta = false
                    val k = kcalParaUsar(s)
                    Toaster.exito("$k kcal al día${if (s.conPiso) " (el mínimo recomendado)" else ""}")
                }
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Kcal al día")
            CampoPlan(cont.kcal, { v -> cont = cont.copy(kcal = v.filter { it.isDigit() }.take(4)) }, "1800", teclado = KeyboardType.Number)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Proteínas %", cont.prot, gramos.proteinas),
                    Triple("Carbohid. %", cont.carb, gramos.carbohidratos),
                    Triple("Grasas %", cont.grasa, gramos.grasas),
                ).forEachIndexed { i, (etq, valor, g) ->
                    Column(Modifier.weight(1f)) {
                        EtqForm(etq)
                        CampoPlan(valor, { v ->
                            val limpio = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(5)
                            cont = when (i) { 0 -> cont.copy(prot = limpio); 1 -> cont.copy(carb = limpio); else -> cont.copy(grasa = limpio) }
                        }, "", teclado = KeyboardType.Decimal)
                        Text(g?.let { "$it g/día" } ?: " ", color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            if (sumaPct != null && kotlin.math.abs(sumaPct - 100) > 1) {
                Text("Los porcentajes suman ${numJs(sumaPct)} %: tienen que sumar 100 %.", color = c.pend, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }

        // ── Comidas ──
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🍽️ COMIDAS DEL DÍA", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f))
            if (alimentos.disponible) {
                Text("🥫 Alimentos de la clínica${if (alimentos.propios.isNotEmpty()) " (${alimentos.propios.size})" else ""}",
                    color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { modalAlimentos = ModoAlimentosClinica.Lista }
                        .padding(horizontal = 6.dp, vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        cont.comidas.forEachIndexed { i, m ->
            androidx.compose.runtime.key(m.k) {
                SeccionPlan(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            CampoPlan(m.tiempo, { v -> setComida(m.k) { it.copy(tiempo = v.take(LimitesPlan.TIEMPO)) } }, "Comida")
                        }
                        Box(Modifier.width(118.dp)) {
                            CajaSelectorForm(horaPlan(m.hora) ?: "🕒 Hora") { horaDe = m.k }
                        }
                        if (m.hora != null) {
                            Text("✕", color = c.textoSuave, fontSize = 13.sp,
                                modifier = Modifier.clickable { setComida(m.k) { it.copy(hora = null) } }.padding(4.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Alimento + cantidad: suman kcal y macros (tabla peruana + los de la clínica).
                    AlimentosComidaEditor(
                        comida = m.tiempo.ifBlank { "esta comida" },
                        items = m.items,
                        onChange = { nuevos -> setComida(m.k) { it.copy(items = nuevos) } },
                        datos = alimentos,
                        onCrear = { nombre ->
                            modalAlimentos = ModoAlimentosClinica.Crear(nombre) { a ->
                                setComida(m.k) { cm ->
                                    if (cm.items.size >= LimitesAlimento.ITEMS_POR_COMIDA) cm
                                    else cm.copy(items = cm.items + itemConHabitual(a, alimentos.habitual[refAlimento(a)]))
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    // Uno por línea (así sale en la hoja): chips del tiempo de comida + lo que la clínica ya usa.
                    CampoConChipsPlan(
                        label = "Qué come en ${m.tiempo.ifBlank { "esta comida" }}", valor = m.detalle,
                        onChange = { v -> setComida(m.k) { it.copy(detalle = v.take(LimitesPlan.DETALLE)) } },
                        frases = frasesComidaPlan(m.tiempo, sugerencias), placeholder = "Toca una opción o escribe (una por línea)",
                        minLineas = 2, chipsVisibles = 6, mostrarEtiqueta = false,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                        AccionComida("↑", habilitada = i > 0) { moverComida(i, -1) }
                        AccionComida("↓", habilitada = i < cont.comidas.size - 1) { moverComida(i, 1) }
                        AccionComida("✕", habilitada = true) { cont = cont.copy(comidas = cont.comidas.filter { it.k != m.k }) }
                    }
                }
            }
        }
        if (cont.comidas.size < LimitesPlan.COMIDAS) {
            BotonPlan("＋ Agregar comida") {
                if (cont.comidas.size < LimitesPlan.COMIDAS) {
                    cont = cont.copy(comidas = cont.comidas + conClave(listOf(pe.saniape.app.data.staff.ComidaPlan("", null, ""))))
                }
            }
        }
        Text("Las comidas sin alimentos ni detalle no se guardan.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        if (cont.itemsIlegibles > 0) {
            val n = cont.itemsIlegibles
            Text(
                if (nuevo) "${if (n == 1) "1 alimento" else "$n alimentos"} del plan de partida no se pudieron leer y no se copian."
                else avisoItemsIlegibles(n).orEmpty(),
                color = c.pend, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.pendBg).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        ResumenAporteUi(
            itemsPorComida = cont.comidas.map { it.items },
            kcal = kcalNum, proteinasPct = pP, carbohidratosPct = pC, grasasPct = pG,
            onUsar = { e ->
                cont = cont.copy(kcal = e.kcal.toString(), prot = numJs(e.proteinasPct), carb = numJs(e.carbohidratosPct), grasa = numJs(e.grasasPct))
                Toaster.exito("Plan: ${e.kcal} kcal · P ${numJs(e.proteinasPct)} % · C ${numJs(e.carbohidratosPct)} % · G ${numJs(e.grasasPct)} %")
            },
        )

        // ── Indicaciones ──
        listOf(
            Triple("✅ Recomendaciones", "recomendaciones", "Ej. Comer despacio, no saltarse comidas…"),
            Triple("🚫 Alimentos a evitar", "evitar", "Ej. Gaseosas, frituras, embutidos…"),
            Triple("💧 Hidratación", "hidratacion", "Ej. 2 litros de agua al día"),
        ).forEach { (etq, campo, ph) ->
            Spacer(Modifier.height(12.dp))
            val valor = when (campo) { "recomendaciones" -> cont.recomendaciones; "evitar" -> cont.evitar; else -> cont.hidratacion }
            CampoConChipsPlan(
                label = etq, valor = valor,
                onChange = { v ->
                    val t = v.take(LimitesPlan.TEXTO)
                    cont = when (campo) {
                        "recomendaciones" -> cont.copy(recomendaciones = t)
                        "evitar" -> cont.copy(evitar = t)
                        else -> cont.copy(hidratacion = t)
                    }
                },
                frases = frasesCampoTextoPlan(campo, sugerencias), placeholder = ph, minLineas = 2, chipsVisibles = 8,
            )
        }

        // ── Lo ve el paciente en su app ──
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { visible = !visible }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("📱 Visible para el paciente en su app", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Lo ve en «Mi plan de alimentación» y lo descarga en PDF.", color = c.textoSuave, fontSize = 11.sp)
            }
            Switch(
                checked = visible, onCheckedChange = { visible = it },
                colors = SwitchDefaults.colors(checkedTrackColor = c.navy, checkedThumbColor = c.sobreNavy),
            )
        }
        CajaErrores(errores)
    }

    if (elegirFecha) DialogoFecha(onElegir = { fecha = it }, onCerrar = { elegirFecha = false }, inicial = fecha)
    modalAlimentos?.let { modo ->
        DialogoAlimentosClinica(inicial = modo, datos = alimentos, puedeEditar = true, onCerrar = { modalAlimentos = null })
    }
    horaDe?.let { k ->
        val actual = cont.comidas.firstOrNull { it.k == k }
        DialogoHora(
            horaInicial = actual?.hora ?: "08:00",
            onElegir = { h -> setComida(k) { it.copy(hora = h) } },
            onCerrar = { horaDe = null },
        )
    }
}

// ─── Calculadora de requerimiento energético (Mifflin-St Jeor) ──────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Calculadora(pacienteId: String, edad: Int?, previo: JsonObject?, onUsar: (SnapshotCalculo) -> Unit) {
    val c = Sania.colors
    // Peso y talla de la última medición (las mismas de la pestaña ⚖️ Medidas).
    var ultimoPeso by remember { mutableStateOf<Double?>(null) }
    var ultTalla by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(pacienteId) {
        val m = MedicionesRepo.listar(pacienteId).orEmpty()
        ultimoPeso = m.lastOrNull { it.peso != null }?.peso
        ultTalla = ultimaTalla(m)
    }
    fun pv(k: String) = (previo?.get(k) as? JsonPrimitive)?.content
    var sexo by remember { mutableStateOf(Sexo.de(pv("sexo"))) }
    var edadTxt by remember { mutableStateOf(edad?.toString().orEmpty()) }
    var pesoTxt by remember { mutableStateOf<String?>(null) } // null = sin tocar → la última medición
    var tallaTxt by remember { mutableStateOf<String?>(null) }
    var actividad by remember { mutableStateOf(NivelActividad.de(pv("actividad")) ?: NivelActividad.LIGERA) }
    var objetivo by remember { mutableStateOf(ObjetivoPeso.de(pv("objetivo")) ?: ObjetivoPeso.MANTENER) }
    var menuActividad by remember { mutableStateOf(false) }

    val peso = pesoTxt ?: ultimoPeso?.let(::numJs).orEmpty()
    val talla = tallaTxt ?: ultTalla?.let(::numJs).orEmpty()
    val datos = DatosCalculo(
        sexo = sexo,
        edad = aNumero(edadTxt),
        peso = aNumero(peso),
        talla = tallaEnCm(aNumero(talla)),
        actividad = actividad, objetivo = objetivo,
    )
    val errores = validarDatosCalculo(datos)
    val r = if (errores.isEmpty()) calcularRequerimiento(datos) else null

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(10.dp),
    ) {
        EtqForm("Sexo")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sexo.entries.forEach { s -> ChipPlan(s.etiqueta, activo = sexo == s) { sexo = s } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                EtqForm("Edad (años)")
                CampoPlan(edadTxt, { v -> edadTxt = v.filter { it.isDigit() }.take(3) }, "", teclado = KeyboardType.Number)
            }
            Column(Modifier.weight(1f)) {
                EtqForm("Peso (kg)")
                CampoPlan(peso, { v -> pesoTxt = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) }, "70", teclado = KeyboardType.Decimal)
            }
            Column(Modifier.weight(1f)) {
                EtqForm("Talla (cm)")
                CampoPlan(talla, { v -> tallaTxt = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) }, "165", teclado = KeyboardType.Decimal)
            }
        }
        if ((ultimoPeso != null || ultTalla != null) && pesoTxt == null && tallaTxt == null) {
            Text("Peso y talla de la última medición (⚖️ Medidas).", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        EtqForm("Actividad física")
        Box {
            CajaSelectorForm("${actividad.etiqueta}  ▾") { menuActividad = true }
            DropdownMenu(expanded = menuActividad, onDismissRequest = { menuActividad = false }) {
                NivelActividad.entries.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.etiqueta, fontSize = 14.sp, fontWeight = if (a == actividad) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { menuActividad = false; actividad = a },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        EtqForm("Objetivo")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ObjetivoPeso.entries.forEach { o -> ChipPlan(o.etiqueta, activo = objetivo == o) { objetivo = o } }
        }
        Spacer(Modifier.height(10.dp))
        if (r != null) {
            Text("TMB ${r.tmb} · GET ${r.get} → ${r.kcal} kcal/día", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (r.conPiso) Text("Se usa el mínimo recomendado (${r.kcal} kcal).", color = c.pend, fontSize = 11.sp)
            if (kcalParaUsar(r) != r.kcal) Text("Se usarán ${kcalParaUsar(r)} kcal (lo máximo que admite el plan).", color = c.pend, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            BotonPlan("Usar estas kcal", lleno = true) { onUsar(r) }
        } else {
            Text(errores.firstOrNull().orEmpty(), color = c.textoSuave, fontSize = 12.sp)
        }
    }
}

// ─── Piezas ──────────────────────────────────────────────────────────────────

/**
 * Campo de texto del plan con chips y autocompletado: el MISMO campo de la
 * consulta guiada (CampoTextoClinico, gemelo de CampoConFrases.tsx). Tocar un
 * chip lo agrega al final (las comidas, uno por línea; el resto, con coma) y
 * tocarlo otra vez lo quita; al escribir se ofrece lo aprendido y la base.
 */
@Composable
private fun CampoConChipsPlan(
    label: String,
    valor: String,
    onChange: (String) -> Unit,
    frases: FrasesCampoPlan,
    placeholder: String,
    minLineas: Int,
    chipsVisibles: Int,
    mostrarEtiqueta: Boolean = true,
) {
    pe.saniape.app.ui.clinica.CampoConFrases(
        label = label, valor = valor, onChange = onChange, frases = frases, placeholder = placeholder,
        minLineas = minLineas, chipsVisibles = chipsVisibles, mostrarEtiqueta = mostrarEtiqueta, campo = "plan",
    )
}

@Composable
private fun CampoPlan(
    valor: String,
    onChange: (String) -> Unit,
    placeholder: String,
    multilinea: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
) {
    val c = Sania.colors
    OutlinedTextField(
        value = valor, onValueChange = onChange,
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = c.textoSuave, fontSize = 13.sp) }) else null,
        singleLine = !multilinea,
        minLines = if (multilinea) 2 else 1,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().then(if (multilinea) Modifier.heightIn(min = 64.dp) else Modifier),
        colors = coloresCampoForm(),
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
    )
}

@Composable
private fun CajaErrores(errores: List<String>) {
    if (errores.isEmpty()) return
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) { errores.forEach { Text(it, color = c.error, fontSize = 12.sp) } }
}

@Composable
private fun TarjetaPlanUi(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        content = contenido,
    )
}

@Composable
private fun SeccionPlan(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
        content = contenido,
    )
}

@Composable
private fun Pildora(texto: String, color: androidx.compose.ui.graphics.Color, fondo: androidx.compose.ui.graphics.Color) {
    Text(texto, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(fondo).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun ChipPlan(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun AccionComida(texto: String, habilitada: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (habilitada) c.textoSuave else c.textoSuave.copy(alpha = 0.3f), fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = habilitada, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun BotonPlan(texto: String, lleno: Boolean = false, peligro: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    val fondo = when { peligro -> c.error; lleno -> c.navy; else -> c.superficie }
    val color = if (peligro || lleno) c.sobreNavy else c.texto
    Text(texto, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier
        .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(fondo)
        .then(if (lleno || peligro) Modifier else Modifier.border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}
