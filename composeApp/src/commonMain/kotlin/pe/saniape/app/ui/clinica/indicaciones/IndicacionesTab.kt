package pe.saniape.app.ui.clinica.indicaciones

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import pe.saniape.app.data.staff.CatalogoIndicaciones
import pe.saniape.app.data.staff.ContenidoEditorIndicaciones
import pe.saniape.app.data.staff.ContenidoIndicacionesFuente
import pe.saniape.app.data.staff.FraseFrecuenteApp
import pe.saniape.app.data.staff.FrasesCampoPlan
import pe.saniape.app.data.staff.IndicacionPaciente
import pe.saniape.app.data.staff.IndicacionesRepo
import pe.saniape.app.data.staff.LimitesIndicaciones
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.PlantillaBaseIndicaciones
import pe.saniape.app.data.staff.PlantillaIndicaciones
import pe.saniape.app.data.staff.SugerenciasIndicaciones
import pe.saniape.app.data.staff.borradorIndicacionDesdeEditor
import pe.saniape.app.data.staff.coincideBusquedaIndicacion
import pe.saniape.app.data.staff.contenidoEditorDesde
import pe.saniape.app.data.staff.enlaceWhatsAppSede
import pe.saniape.app.data.staff.fechaIndicacion
import pe.saniape.app.data.staff.frasesCampoIndicacion
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.htmlIndicaciones
import pe.saniape.app.data.staff.indicacionDeJson
import pe.saniape.app.data.staff.lineasIndicacion
import pe.saniape.app.data.staff.nombreRubroIndicaciones
import pe.saniape.app.data.staff.ordenarIndicaciones
import pe.saniape.app.data.staff.seccionesIndicacion
import pe.saniape.app.data.staff.textoIndicacionesWhatsApp
import pe.saniape.app.data.staff.validarIndicacion
import pe.saniape.app.data.staff.validarPlantillaIndicaciones
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.atencion.CampoTextoClinico
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/** De dónde parte una hoja nueva (para el aviso del editor y "Cambiar"). */
private data class OrigenIndicaciones(
    val contenido: ContenidoEditorIndicaciones,
    /** Nombre de la plantilla de la que se parte (null = en blanco). */
    val etiqueta: String?,
    /** true = plantilla BASE (sugerencia editable). */
    val base: Boolean,
    val especialidadId: String? = null,
)

private fun origenBase(p: PlantillaBaseIndicaciones) = OrigenIndicaciones(contenidoEditorDesde(p), p.titulo, base = true)

/** El editor: cerrado, eligiendo plantilla, o escribiendo (nueva con su origen, o editando una). */
private sealed interface ModoEditor {
    data object Selector : ModoEditor
    data class Nueva(val origen: OrigenIndicaciones) : ModoEditor
    data class Editar(val id: String) : ModoEditor
}

/**
 * Pestaña 📝 Indicaciones de la ficha (TODAS las especialidades). Gemelo de
 * `IndicacionesTab.tsx` de la web:
 *
 *  · Lista (la última abierta, las demás plegadas): imprimir / PDF (hoja nativa
 *    gemela de HojaIndicaciones), WhatsApp, editar, borrar y "Guardar como plantilla".
 *  · "＋ Nuevas indicaciones" → selector con buscador: las plantillas de la
 *    clínica primero, luego las sugeridas para la especialidad (del catálogo
 *    del servidor: la app no copia los textos), "Ver de otras" y "En blanco".
 *  · Desde la agenda ("📝 Dejar indicaciones"): abre de una con la plantilla
 *    que sugirió el servicio y la hoja queda vinculada a esa cita.
 *  · Editor: título, fecha y 4 campos con chips + autocompletado que aprende;
 *    signos de alarma resaltados; "Visible para el paciente en su app".
 *
 * Carga SOLO cuando la pestaña se abre (la monta el padre en su `when`). Sin
 * listas perezosas: vive dentro del `verticalScroll` de la ficha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndicacionesTab(
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    /** `puede('pacientes') || puede('sesiones')` y ficha no dada de baja (como el API). */
    puedeEditar: Boolean,
    /** Teléfono para avisos (el del apoderado si los recibe). */
    telefono: String?,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    /** La clínica activa: catálogo y aprendidos se guardan en caché por clínica. */
    clinicaId: String?,
    /** Especialidad de la hoja nueva y de las sugerencias (la de la cita o la única del paciente). */
    especialidadId: String?,
    /** La cita de la que viene (agenda): la PRÓXIMA hoja que se guarde queda vinculada a ella. */
    citaId: String? = null,
    /** Nombre del servicio de esa cita: sugiere la plantilla base. */
    servicio: String? = null,
    /** Clave de la plantilla que sugirió el servicio (la agenda ya la conoce). */
    plantillaClave: String? = null,
    /** Abrir una hoja nueva de una (viene de "📝 Dejar indicaciones"). */
    abrirNuevo: Boolean = false,
    /** Se abrió de una: el padre apaga la bandera (al remontar no se reabre). */
    onAbiertoNuevo: () -> Unit = {},
    /** Se guardó una hoja NUEVA: el padre olvida la cita (las siguientes ya no son de ella). */
    onGuardadaNueva: () -> Unit = {},
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<IndicacionPaciente>?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pacienteId, token) {
        val r = IndicacionesRepo.listar(pacienteId)
        if (r.items != null) { items = r.items; error = null } else if (items == null) error = r.error
        cargando = false
    }
    // Quién indicó cada hoja (terapeuta_id → nombre), como `profesionales` en la web.
    var profesionales by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        profesionales = runCatching { PacientesRepo.terapeutasActivos().associate { it.id to it.nombre } }.getOrDefault(emptyMap())
    }
    // El catálogo base (plantillas + chips) de la especialidad: UN viaje (caché de la sesión).
    var catalogo by remember { mutableStateOf<CatalogoIndicaciones?>(null) }
    LaunchedEffect(especialidadId, servicio) {
        catalogo = IndicacionesRepo.catalogoParaAtencion(clinicaId, especialidadId, servicio)
    }

    var modo by remember { mutableStateOf<ModoEditor?>(null) }
    // Desde la agenda: se abre UNA vez, con la plantilla del servicio si el catálogo la tiene.
    LaunchedEffect(Unit) {
        if (!abrirNuevo) return@LaunchedEffect
        onAbiertoNuevo()
        if (!puedeEditar) return@LaunchedEffect
        val cat = IndicacionesRepo.catalogoParaAtencion(clinicaId, especialidadId, servicio)
        if (cat != null) catalogo = cat
        val sug = cat?.plantillas?.firstOrNull { it.clave == plantillaClave } ?: cat?.plantillaSugerida
        modo = if (sug != null) ModoEditor.Nueva(origenBase(sug)) else ModoEditor.Selector
    }
    // La hoja recién guardada: imprimir y mandarla por WhatsApp quedan ahí mismo.
    var recien by remember { mutableStateOf<String?>(null) }
    var confirmarBorrar by remember { mutableStateOf<IndicacionPaciente?>(null) }
    var borrando by remember { mutableStateOf(false) }
    var comoPlantilla by remember { mutableStateOf<IndicacionPaciente?>(null) }

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

    val hojas = items.orEmpty()
    val waValido = enlaceWhatsAppSede(telefono) != null
    fun profesionalDe(i: IndicacionPaciente) = i.terapeutaId?.let { profesionales[it] }
    fun imprimir(i: IndicacionPaciente) {
        acciones.abrirHtml(
            htmlIndicaciones(i.fecha, i, clinicaNombre, clinicaDireccion, logoUrl, colorPrincipal,
                pacienteNombre, pacienteDocumento, profesionalDe(i)),
            "Indicaciones para casa",
        )
    }
    fun enviarWhatsApp(i: IndicacionPaciente) {
        val texto = textoIndicacionesWhatsApp(i.fecha, i, clinicaNombre, pacienteNombre, profesionalDe(i))
        // enlaceWhatsAppSede → urlWhatsApp (api.whatsapp.com/send): wa.me?text= rompe los emojis.
        val url = enlaceWhatsAppSede(telefono, texto)
        if (url == null) Toaster.error("El paciente no tiene un celular válido") else acciones.abrirUrl(url)
    }
    fun nueva() { recien = null; modo = ModoEditor.Selector }
    val guardada = recien?.takeIf { modo == null }?.let { id -> hojas.firstOrNull { it.id == id } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("📝 Indicaciones para casa", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        guardada?.let { g ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.okBg)
                    .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Indicaciones guardadas · ¿se las entregas?", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("✕", color = c.textoSuave, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { recien = null }.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonInd("🖨 Imprimir / PDF") { imprimir(g) }
                    if (waValido) BotonInd("💬 Enviar por WhatsApp") { enviarWhatsApp(g) }
                    if (puedeEditar) BotonInd("💾 Guardar como plantilla") { comoPlantilla = g }
                }
                if (g.visiblePaciente) {
                    Text("📱 El paciente ya las ve en su app.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        if (puedeEditar && hojas.isNotEmpty()) BotonInd("＋ Nuevas indicaciones", lleno = true) { nueva() }

        if (hojas.isEmpty()) {
            TarjetaInd {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", fontSize = 30.sp)
                    Text("Aún no hay indicaciones para casa", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Qué hacer, qué evitar, los signos de alarma y cuándo volver. Se imprime, se manda por WhatsApp y el paciente lo ve en su app.",
                        color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    if (puedeEditar) {
                        Spacer(Modifier.height(12.dp))
                        BotonInd("＋ Nuevas indicaciones", lleno = true) { nueva() }
                        val sugeridas = catalogo?.plantillas.orEmpty().take(4)
                        if (sugeridas.isNotEmpty()) {
                            Text("O EMPIEZA CON UNA SUGERENCIA", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                sugeridas.forEach { p ->
                                    Text("+ ${p.titulo}", color = c.navy, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                                            .clickable { recien = null; modo = ModoEditor.Nueva(origenBase(p)) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            hojas.forEachIndexed { i, h ->
                // key: al borrar o agregar, cada tarjeta conserva su abierto/cerrado.
                androidx.compose.runtime.key(h.id) {
                    TarjetaIndicacion(
                        indicacion = h, actual = i == 0, puedeEditar = puedeEditar, waValido = waValido,
                        profesional = profesionalDe(h),
                        onImprimir = { imprimir(h) },
                        onWhatsApp = { enviarWhatsApp(h) },
                        onEditar = { recien = null; modo = ModoEditor.Editar(h.id) },
                        onPlantilla = { comoPlantilla = h },
                        onBorrar = { confirmarBorrar = h },
                    )
                }
            }
        }
    }

    when (val m = modo) {
        null -> Unit
        ModoEditor.Selector -> SelectorPlantilla(
            clinicaId = clinicaId, especialidadId = especialidadId, catalogo = catalogo, servicio = servicio,
            onElegir = { modo = ModoEditor.Nueva(it) },
            onCancelar = { modo = null },
        )
        else -> {
            val enEdicion = (m as? ModoEditor.Editar)?.let { e -> hojas.firstOrNull { it.id == e.id } }
            val origen = (m as? ModoEditor.Nueva)?.origen
            // La hoja que se editaba ya no está (la borró otro equipo): se cierra.
            if (enEdicion == null && origen == null) LaunchedEffect(m) { modo = null }
            else androidx.compose.runtime.key(m) {
                EditorIndicaciones(
                    pacienteId = pacienteId, indicacion = enEdicion, origen = origen,
                    citaId = citaId, clinicaId = clinicaId,
                    especialidadId = enEdicion?.especialidadId ?: especialidadId ?: origen?.especialidadId,
                    onCambiarPlantilla = { modo = ModoEditor.Selector },
                    onCerrar = { modo = null },
                    onGuardado = { p, esNueva ->
                        modo = null
                        recien = p.id
                        items = ordenarIndicaciones(if (esNueva) listOf(p) + hojas else hojas.map { if (it.id == p.id) p else it })
                        if (esNueva) onGuardadaNueva()
                    },
                )
            }
        }
    }

    comoPlantilla?.let { p -> DialogoGuardarPlantilla(p) { comoPlantilla = null } }

    confirmarBorrar?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!borrando) confirmarBorrar = null },
            title = { Text("¿Eliminar estas indicaciones?", fontWeight = FontWeight.Bold) },
            text = { Text("«${p.titulo}» del ${fechaIndicacion(p.fecha)}", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    // Dos toques en el mismo frame: `enabled` recién aplica en la siguiente recomposición.
                    if (borrando) return@TextButton
                    borrando = true
                    scope.launch {
                        val r = IndicacionesRepo.borrar(p.id)
                        borrando = false
                        // Ya la había borrado otro equipo: para el usuario es lo mismo.
                        if (r.registrada || r.rechazo?.codigo == "NO_ENCONTRADO") {
                            confirmarBorrar = null
                            items = hojas.filter { it.id != p.id }
                            if (recien == p.id) recien = null
                            Toaster.exito("Indicaciones eliminadas")
                        } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !borrando, onClick = { confirmarBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ─── Una hoja (lectura) ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaIndicacion(
    indicacion: IndicacionPaciente,
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
    TarjetaInd {
        Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📝 ${indicacion.titulo}", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (actual) Pildora("Última", c.ok, c.okBg)
                    // El paciente no la ve en su app.
                    if (!indicacion.visiblePaciente) Pildora("🔒 Solo la clínica", c.textoSuave, c.chipBg)
                }
                Text(fechaIndicacion(indicacion.fecha) + (profesional?.let { " · $it" }.orEmpty()),
                    color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text(if (abierto) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        }
        if (abierto) {
            Spacer(Modifier.height(10.dp))
            DetalleIndicacion(indicacion)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonInd("🖨 Imprimir", onClick = onImprimir)
                if (waValido) BotonInd("💬 WhatsApp", onClick = onWhatsApp)
                if (puedeEditar) {
                    BotonInd("✏️ Editar", onClick = onEditar)
                    BotonInd("💾 Guardar como plantilla", onClick = onPlantilla)
                    BotonInd("🗑️", onClick = onBorrar)
                }
            }
        }
    }
}

/** Secciones con viñetas; los signos de alarma en un recuadro rojo; el control al final. */
@Composable
internal fun DetalleIndicacion(i: ContenidoIndicacionesFuente, tituloAlarma: String = "🚨 Acuda de inmediato si…") {
    val c = Sania.colors
    val control = lineasIndicacion(i.control)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        seccionesIndicacion(i).forEach { s ->
            if (s.clave == "signos_alarma") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                        .border(1.dp, c.error, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(tituloAlarma.uppercase(), color = c.error, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    s.items.forEach { Text("• $it", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp)) }
                }
            } else {
                Column {
                    Text("${s.icono} ${s.titulo}".uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    s.items.forEach { Text("• $it", color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
        }
        if (control.isNotEmpty()) {
            Text("📅 Control: ${control.joinToString(" · ")}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DialogoGuardarPlantilla(indicacion: IndicacionPaciente, onCerrar: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(indicacion.titulo.take(LimitesIndicaciones.NOMBRE_PLANTILLA)) }
    var guardando by remember { mutableStateOf(false) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }

    fun guardar() {
        if (guardando) return
        val e = validarPlantillaIndicaciones(nombre, indicacion)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = IndicacionesRepo.guardarPlantilla(nombre, indicacion, indicacion.especialidadId)
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
        CampoInd(nombre, { nombre = it.take(LimitesIndicaciones.NOMBRE_PLANTILLA) }, "Ej. Post peeling — piel grasa")
        Text("Si ya hay una con ese nombre, se reemplaza. Los datos del paciente no se guardan.",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        CajaErrores(errores)
    }
}

// ─── Selector: ¿de dónde parte la hoja nueva? ────────────────────────────────

@Composable
private fun SelectorPlantilla(
    clinicaId: String?,
    especialidadId: String?,
    /** El catálogo de la especialidad (o de la clínica sin ella), con la sugerida por el servicio. */
    catalogo: CatalogoIndicaciones?,
    servicio: String?,
    onElegir: (OrigenIndicaciones) -> Unit,
    onCancelar: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var plantillas by remember { mutableStateOf<List<PlantillaIndicaciones>>(emptyList()) }
    LaunchedEffect(Unit) { plantillas = IndicacionesRepo.plantillas() }
    // Las de TODA la clínica (para "Ver de otras especialidades"): solo hace falta si hay especialidad.
    var deClinica by remember { mutableStateOf<CatalogoIndicaciones?>(null) }
    LaunchedEffect(especialidadId) {
        if (!especialidadId.isNullOrBlank()) deClinica = IndicacionesRepo.catalogo(clinicaId)
    }
    var busqueda by remember { mutableStateOf("") }
    var otras by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf<String?>(null) }
    var eliminando by remember { mutableStateOf(false) }

    val sugeridaCita = catalogo?.plantillaSugerida
    val propias = plantillas.filter { coincideBusquedaIndicacion(busqueda, it.nombre, it.titulo) }
    val deRubro = catalogo?.plantillas.orEmpty()
    val idsRubro = deRubro.map { it.clave }.toSet()
    val sugeridas = deRubro.filter { it.clave != sugeridaCita?.clave && coincideBusquedaIndicacion(busqueda, it.titulo) }
    val deOtras = deClinica?.plantillas.orEmpty().filter { it.clave !in idsRubro && coincideBusquedaIndicacion(busqueda, it.titulo) }
    val verOtras = otras || busqueda.isNotBlank()
    val nombresRubros = catalogo?.rubros.orEmpty().joinToString(" · ") { nombreRubroIndicaciones(it) }

    fun eliminarPlantilla(id: String) {
        if (eliminando) return
        eliminando = true
        scope.launch {
            val r = IndicacionesRepo.borrarPlantilla(id)
            eliminando = false
            if (!r.registrada && r.rechazo?.codigo != "NO_ENCONTRADO") { Toaster.error(r.rechazo?.error ?: "No se pudo eliminar la plantilla"); return@launch }
            Toaster.exito("Plantilla eliminada")
            borrar = null
            plantillas = plantillas.filter { it.id != id }
        }
    }

    DialogoForm(
        titulo = "Nuevas indicaciones",
        subtitulo = "📝 ¿De dónde partimos?",
        textoAccion = "En blanco",
        onCancelar = onCancelar,
        onAccion = { onElegir(OrigenIndicaciones(ContenidoEditorIndicaciones(), null, base = false)) },
    ) {
        if (!servicio.isNullOrBlank() && sugeridaCita == null) {
            Text("Para «$servicio» no hay una sugerencia exacta: elige la más parecida o empieza en blanco.",
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        CampoInd(busqueda, { busqueda = it.take(60) }, "Buscar: peeling, extracción, uña, fiebre…")

        if (sugeridaCita != null && coincideBusquedaIndicacion(busqueda, sugeridaCita.titulo)) {
            TituloSeccion("Sugerida por el servicio" + (servicio?.takeIf { it.isNotBlank() }?.let { " «$it»" }.orEmpty()))
            FilaPlantilla("⭐ ${sugeridaCita.titulo}", lineasIndicacion(sugeridaCita.indicaciones).firstOrNull(), destacada = true) {
                onElegir(origenBase(sugeridaCita))
            }
        }

        if (propias.isNotEmpty()) {
            TituloSeccion("Plantillas de tu clínica")
            propias.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        FilaPlantilla(p.nombre, p.titulo.takeIf { it != p.nombre }) {
                            onElegir(OrigenIndicaciones(contenidoEditorDesde(p), p.nombre, base = false, especialidadId = p.especialidadId))
                        }
                    }
                    if (borrar == p.id) {
                        Text(if (eliminando) "…" else "Eliminar", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp).clickable(enabled = !eliminando) { eliminarPlantilla(p.id) }.padding(8.dp))
                        Text("No", color = c.textoSuave, fontSize = 12.sp,
                            modifier = Modifier.clickable(enabled = !eliminando) { borrar = null }.padding(8.dp))
                    } else {
                        Text("🗑️", fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp).clickable { borrar = p.id }.padding(8.dp))
                    }
                }
            }
        }

        if (sugeridas.isNotEmpty()) {
            TituloSeccion("Sugeridas" + (if (nombresRubros.isNotEmpty()) " · $nombresRubros" else "") + " (editables)")
            sugeridas.forEach { p ->
                Box(Modifier.padding(bottom = 6.dp)) {
                    FilaPlantilla(p.titulo, lineasIndicacion(p.indicaciones).firstOrNull()) { onElegir(origenBase(p)) }
                }
            }
        }

        if (deOtras.isNotEmpty()) {
            if (verOtras) {
                TituloSeccion("De otras especialidades")
                deOtras.forEach { p ->
                    Box(Modifier.padding(bottom = 6.dp)) {
                        FilaPlantilla(p.titulo, p.rubros.joinToString(" · ") { nombreRubroIndicaciones(it) }) { onElegir(origenBase(p)) }
                    }
                }
            } else {
                Text("Ver de otras especialidades (${deOtras.size})", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp).clickable { otras = true }.padding(vertical = 8.dp))
            }
        }

        if (catalogo == null && plantillas.isEmpty()) {
            Text("Sin sugerencias por ahora (revisa tu conexión). Puedes empezar en blanco.",
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        } else if (busqueda.isNotBlank() && propias.isEmpty() && sugeridas.isEmpty() && deOtras.isEmpty() &&
            (sugeridaCita == null || !coincideBusquedaIndicacion(busqueda, sugeridaCita.titulo))) {
            Text("No hay plantillas con «${busqueda.trim()}». Empieza en blanco y guárdala como plantilla al terminar.",
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun TituloSeccion(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun FilaPlantilla(titulo: String, detalle: String?, destacada: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(if (destacada) 1.5.dp else 1.dp, if (destacada) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(titulo, color = if (destacada) c.navy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        detalle?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = c.textoSuave, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── Editor (nueva o editar) ─────────────────────────────────────────────────

@Composable
private fun EditorIndicaciones(
    pacienteId: String,
    /** La hoja que se edita (null = nueva). */
    indicacion: IndicacionPaciente?,
    /** Hoja nueva: de dónde parte. */
    origen: OrigenIndicaciones?,
    /** Solo para una hoja NUEVA: la cita de la que viene. */
    citaId: String?,
    clinicaId: String?,
    especialidadId: String?,
    onCambiarPlantilla: () -> Unit,
    onCerrar: () -> Unit,
    onGuardado: (IndicacionPaciente, Boolean) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val nueva = indicacion == null
    var fecha by remember { mutableStateOf(indicacion?.fecha?.takeIf { it.isNotBlank() } ?: hoyClinicaIso()) }
    var elegirFecha by remember { mutableStateOf(false) }
    var cont by remember { mutableStateOf(if (indicacion != null) contenidoEditorDesde(indicacion) else origen?.contenido ?: ContenidoEditorIndicaciones()) }
    // Lo ve el paciente en su app (por defecto sí).
    var visible by remember { mutableStateOf(indicacion?.visiblePaciente ?: true) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    // Chips base (catálogo) y autocompletado que aprende: UN viaje al abrir (caché de la sesión).
    var catalogo by remember { mutableStateOf<CatalogoIndicaciones?>(null) }
    var sugerencias by remember { mutableStateOf<SugerenciasIndicaciones?>(null) }
    LaunchedEffect(especialidadId) {
        catalogo = IndicacionesRepo.catalogoParaAtencion(clinicaId, especialidadId, null)
        sugerencias = IndicacionesRepo.sugerencias(clinicaId, especialidadId)
    }

    fun guardar() {
        if (guardando) return
        val b = borradorIndicacionDesdeEditor(
            pacienteId, fecha, cont, visible,
            citaId = if (nueva) citaId else null, especialidadId = if (nueva) especialidadId else null,
        )
        val e = validarIndicacion(b)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = IndicacionesRepo.guardar(b, indicacion?.id)
            guardando = false
            val fila = (r.cuerpo?.get("indicacion") as? JsonObject)?.let(::indicacionDeJson)
            if (r.registrada && fila != null) {
                // Lo escrito ya cuenta para el autocompletado: la próxima vez se vuelve a pedir.
                IndicacionesRepo.olvidarSugerencias()
                Toaster.exito(if (nueva) "Indicaciones guardadas" else "Indicaciones actualizadas")
                onGuardado(fila, nueva)
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = if (nueva) "Nuevas indicaciones para casa" else "Editar indicaciones",
        subtitulo = "📝 Qué hacer, qué evitar, signos de alarma y control",
        textoAccion = if (guardando) "Guardando…" else if (nueva) "Guardar" else "Guardar cambios",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        if (nueva) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (origen?.base == true) c.pendBg else c.chipBg).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    origen?.etiqueta?.let { "Partimos de «$it»" + if (origen.base) ": es una sugerencia editable, ajústala a tu paciente." else "." }
                        ?: "En blanco: toca los chips para escribir menos.",
                    color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                Text("Cambiar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !guardando, onClick = onCambiarPlantilla).padding(8.dp))
            }
        }
        EtqForm("Título")
        CampoInd(cont.titulo, { cont = cont.copy(titulo = it.take(LimitesIndicaciones.TITULO)) }, "Ej. Cuidados después del peeling químico")
        Spacer(Modifier.height(10.dp))
        EtqForm("Fecha")
        CajaSelectorForm(fechaIndicacion(fecha)) { elegirFecha = true }

        Spacer(Modifier.height(12.dp))
        CampoConChipsInd("✅ Qué hacer en casa (una por línea)", "indicaciones", cont, { cont = it },
            frasesCampoIndicacion("indicaciones", catalogo, sugerencias), "Ej. Aplique frío local 10 minutos cada hora el primer día", 4, 8)
        Spacer(Modifier.height(12.dp))
        CampoConChipsInd("🚫 Evitar (una por línea)", "evitar", cont, { cont = it },
            frasesCampoIndicacion("evitar", catalogo, sugerencias), "Ej. Sol directo por 7 días", 2, 8)
        Spacer(Modifier.height(12.dp))
        // Lo que el paciente no puede pasar por alto: resaltado, como en la hoja.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                .border(1.dp, c.error, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
        ) {
            CampoConChipsInd("🚨 Signos de alarma — acuda de inmediato si… (una por línea)", "signos_alarma", cont, { cont = it },
                frasesCampoIndicacion("signos_alarma", catalogo, sugerencias), "Ej. Fiebre, sangrado que no para, dolor que aumenta", 2, 6)
        }
        Spacer(Modifier.height(12.dp))
        CampoConChipsInd("📅 Control", "control", cont, { cont = it },
            frasesCampoIndicacion("control", catalogo, sugerencias), "Ej. Volver en 7 días para el retiro de puntos", 1, 6,
            limite = LimitesIndicaciones.CONTROL)

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { visible = !visible }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("📱 Visible para el paciente en su app", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Las ve en «Mis indicaciones» y las descarga en PDF.", color = c.textoSuave, fontSize = 11.sp)
            }
            Switch(
                checked = visible, onCheckedChange = { visible = it },
                colors = SwitchDefaults.colors(checkedTrackColor = c.navy, checkedThumbColor = c.sobreNavy),
            )
        }
        CajaErrores(errores)
    }

    if (elegirFecha) DialogoFecha(onElegir = { fecha = it }, onCerrar = { elegirFecha = false }, inicial = fecha)
}

// ─── Piezas ──────────────────────────────────────────────────────────────────

/**
 * Campo con chips y autocompletado: el MISMO de la consulta guiada
 * (CampoTextoClinico, gemelo de CampoConFrases.tsx), una indicación por línea.
 */
@Composable
private fun CampoConChipsInd(
    label: String,
    campo: String,
    cont: ContenidoEditorIndicaciones,
    onCont: (ContenidoEditorIndicaciones) -> Unit,
    frases: FrasesCampoPlan,
    placeholder: String,
    minLineas: Int,
    chipsVisibles: Int,
    limite: Int = LimitesIndicaciones.TEXTO,
) {
    val chips = remember(frases) { frases.chips.map { FraseFrecuenteApp("indicaciones", it.texto, 0) } }
    CampoTextoClinico(
        label = label, valor = cont.deCampo(campo), onChange = { onCont(cont.conCampo(campo, it.take(limite))) },
        frases = chips, soloLectura = false, minLineas = minLineas, placeholder = placeholder, porLinea = true,
        opciones = frases.opciones, chipsVisibles = chipsVisibles,
    )
}

@Composable
private fun CampoInd(valor: String, onChange: (String) -> Unit, placeholder: String) {
    val c = Sania.colors
    OutlinedTextField(
        value = valor, onValueChange = onChange,
        placeholder = { Text(placeholder, color = c.textoSuave, fontSize = 13.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth(),
        colors = coloresCampoForm(),
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
private fun TarjetaInd(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        content = contenido,
    )
}

@Composable
private fun Pildora(texto: String, color: androidx.compose.ui.graphics.Color, fondo: androidx.compose.ui.graphics.Color) {
    Text(texto, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(fondo).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun BotonInd(texto: String, lleno: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    val fondo = if (lleno) c.navy else c.superficie
    Text(texto, color = if (lleno) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier
        .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(fondo)
        .then(if (lleno) Modifier else Modifier.border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}
