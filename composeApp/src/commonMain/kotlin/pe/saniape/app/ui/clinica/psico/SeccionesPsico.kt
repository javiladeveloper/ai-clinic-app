package pe.saniape.app.ui.clinica.psico

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import pe.saniape.app.data.staff.AREAS_ANALISIS
import pe.saniape.app.data.staff.ENFOQUES_PSICO
import pe.saniape.app.data.staff.FRECUENCIAS_PSICO
import pe.saniape.app.data.staff.FuentePsico
import pe.saniape.app.data.staff.GUIA_OBSERVACION
import pe.saniape.app.data.staff.SECCIONES_ENTREVISTA
import pe.saniape.app.data.staff.ServicioPsico
import pe.saniape.app.data.staff.TIPOS_FUENTE
import pe.saniape.app.data.staff.fechaDmyPsico
import pe.saniape.app.data.staff.nuevoIdFuente
import pe.saniape.app.data.staff.ofrecerSugerencia
import pe.saniape.app.data.staff.precioPropuestoPlan
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.clinica.atencion.EditorDiagnosticosCie
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

// Formularios de los componentes 1, 2, 3, 5 y 6 (el 4, tests, en TestsPsico.kt).
// Cada cambio va al ViewModel, que guarda solo (1,2 s después de dejar de escribir).

// ── 1. Entrevista ────────────────────────────────────────────────────────────

@Composable
internal fun SeccionEntrevista(vm: EvaluacionPsicoViewModel, acciones: AccionesNativas) {
    val ev = vm.ev ?: return
    val ro = vm.soloLectura
    val e = ev.entrevista
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SECCIONES_ENTREVISTA.filter { it.corta }.forEach { s ->
            CampoCortoPsico(s.titulo, e.texto(s.clave), { vm.entrevista(e.con(s.clave, it)) }, ro, placeholder = s.ayuda)
        }
        SECCIONES_ENTREVISTA.filter { !it.corta }.forEach { s ->
            TextoLargoPsico(s.titulo, e.texto(s.clave), { vm.entrevista(vm.ev!!.entrevista.con(s.clave, it)) }, ro, placeholder = s.ayuda)
        }
        Column {
            EtqForm("Genograma (foto, opcional)")
            BloqueFotosPsico(
                fotos = vm.fotos.filter { it.id == e.genogramaDocumentoId },
                soloLectura = ro,
                subiendo = vm.accionando?.startsWith("foto:genograma") == true,
                acciones = acciones,
                textoCamara = "📷 Fotografiar genograma",
                unaSola = true,
                onArchivo = { vm.subirFoto(it, uso = "genograma") },
                onBorrar = { vm.borrarFoto(it) },
            )
        }
    }
}

// ── 2. Recopilación de información ───────────────────────────────────────────

@Composable
internal fun SeccionFuentes(vm: EvaluacionPsicoViewModel, acciones: AccionesNativas) {
    val c = Sania.colors
    val ev = vm.ev ?: return
    val ro = vm.soloLectura
    val fuentes = ev.fuentes
    var fechaDe by remember { mutableStateOf<String?>(null) }
    var quitar by remember { mutableStateOf<FuentePsico?>(null) }
    fun cambiar(id: String, cambio: (FuentePsico) -> FuentePsico) {
        vm.fuentes(vm.ev!!.fuentes.map { if (it.id == id) cambio(it) else it })
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (fuentes.isEmpty()) {
            Text("Padres, colegio, médico que deriva, informes previos… Agrega cada fuente con su resumen.",
                color = c.textoSuave, fontSize = 13.sp)
        }
        fuentes.forEach { f ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📂 ${TIPOS_FUENTE.find { it.valor == f.tipo }?.nombre ?: "Fuente"}", color = c.texto,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!ro) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable { quitar = f },
                            contentAlignment = Alignment.Center) { Text("✕", color = c.error, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                SelectorChipsPsico("Fuente", TIPOS_FUENTE, f.tipo, ro) { t -> if (t != null) cambiar(f.id) { it.copy(tipo = t) } }
                CampoCortoPsico("Quién / qué", f.nombre, { v -> cambiar(f.id) { it.copy(nombre = v) } }, ro,
                    placeholder = "Ej. Tutora de aula, informe neurológico 2025")
                Column {
                    EtqForm("Fecha")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            CajaSelectorForm(f.fecha?.let { fechaDmyPsico(it) }?.ifBlank { null } ?: "Sin fecha") { if (!ro) fechaDe = f.id }
                        }
                        if (!ro && f.fecha != null) {
                            TextButton(onClick = { cambiar(f.id) { it.copy(fecha = null) } }) { Text("Quitar", color = c.textoSuave) }
                        }
                    }
                }
                TextoLargoPsico("Resumen", f.resumen, { v -> cambiar(f.id) { it.copy(resumen = v) } }, ro,
                    placeholder = "Lo que aporta esta fuente")
                Column {
                    EtqForm("Adjuntos")
                    BloqueFotosPsico(
                        fotos = vm.fotos.filter { it.id in f.documentoIds },
                        soloLectura = ro,
                        subiendo = vm.accionando == "foto:fuente:",
                        acciones = acciones,
                        textoCamara = "📎 Fotografiar",
                        onArchivo = { a ->
                            vm.subirFoto(a, uso = "fuente") { foto ->
                                // La foto entra a ESTA fuente y se guarda la lista (contrato §8).
                                if (foto != null) cambiar(f.id) { it.copy(documentoIds = it.documentoIds + foto.id) }
                            }
                        },
                        onBorrar = { vm.borrarFoto(it) },
                    )
                }
            }
        }
        if (!ro) {
            BotonPsico("+ Agregar fuente", modifier = Modifier.fillMaxWidth()) {
                val lista = vm.ev!!.fuentes
                vm.fuentes(lista + FuentePsico(id = nuevoIdFuente(lista, Clock.System.now().toEpochMilliseconds()), tipo = "padres"))
            }
        }
    }

    fechaDe?.let { id ->
        DialogoFecha(
            onElegir = { fecha -> cambiar(id) { it.copy(fecha = fecha) } },
            onCerrar = { fechaDe = null },
            inicial = fuentes.find { it.id == id }?.fecha,
        )
    }
    quitar?.let { f ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            title = { Text("¿Quitar esta fuente?", fontWeight = FontWeight.Bold) },
            text = { Text("Se quita de la recopilación (sus adjuntos quedan en la evaluación).", color = c.texto) },
            confirmButton = { TextButton(onClick = { quitar = null; vm.fuentes(vm.ev!!.fuentes.filter { it.id != f.id }) }) { Text("Quitar", color = c.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ── 3. Observación de la conducta ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionObservacion(vm: EvaluacionPsicoViewModel) {
    val ev = vm.ev ?: return
    val ro = vm.soloLectura
    val o = ev.observacion
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GUIA_OBSERVACION.forEach { g ->
            Column {
                EtqForm(g.titulo)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Las elegidas que no son de la guía (texto libre de la web) también se ven.
                    (g.opciones + o.elegidas(g.clave).filter { it !in g.opciones }).forEach { op ->
                        ChipPsico(op, op in o.elegidas(g.clave), habilitado = !ro) {
                            vm.observacion(vm.ev!!.observacion.alternar(g.clave, op))
                        }
                    }
                }
            }
        }
        TextoLargoPsico("Notas de observación", o.notas, { vm.observacion(vm.ev!!.observacion.copy(notas = it)) }, ro,
            placeholder = "Lo que observaste durante las sesiones", minLineas = 4)
    }
}

// ── 5. Análisis completo ─────────────────────────────────────────────────────

@Composable
internal fun SeccionAnalisis(vm: EvaluacionPsicoViewModel) {
    val c = Sania.colors
    val ev = vm.ev ?: return
    val ro = vm.soloLectura
    val a = ev.analisis
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Llena solo las áreas que aplican a esta evaluación.", color = c.textoSuave, fontSize = 12.sp)
        AREAS_ANALISIS.forEach { area ->
            TextoLargoPsico(area.nombre, a.areas[area.valor].orEmpty(), { v ->
                val actual = vm.ev!!.analisis
                vm.analisis(actual.copy(areas = actual.areas + (area.valor to v)))
            }, ro, minLineas = 2)
        }
        Column {
            EtqForm("Impresión diagnóstica (CIE-10)")
            EditorDiagnosticosCie(
                lista = ev.diagnosticos,
                onLista = { vm.diagnosticos(it) },
                soloLectura = ro,
                nota = "Busca por código (F90.0) o por palabras. Tipo: Presuntivo, Definitivo o Repetido.",
            )
        }
        TextoLargoPsico("Conclusiones", a.conclusiones, { vm.analisis(vm.ev!!.analisis.copy(conclusiones = it)) }, ro, minLineas = 4)
    }
}

// ── 6. Plan de intervención ──────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionPlan(vm: EvaluacionPsicoViewModel, servicios: List<ServicioPsico>, onCrearTratamiento: (() -> Unit)?) {
    val c = Sania.colors
    val ev = vm.ev ?: return
    val ro = vm.soloLectura
    val p = ev.plan
    // Los números se escriben como texto (para poder borrar y tipear "12" o "960.50").
    var sesionesTxt by remember(ev.id) { mutableStateOf(p.numeroSesiones?.toString().orEmpty()) }
    var precioTxt by remember(ev.id) { mutableStateOf(p.precio?.let { formatoMonto(it) }.orEmpty()) }
    // Si el valor cambió desde afuera (sugerencia, servicio), el campo lo refleja.
    LaunchedEffect(p.numeroSesiones) { if (sesionesTxt.toIntOrNull() != p.numeroSesiones) sesionesTxt = p.numeroSesiones?.toString().orEmpty() }
    LaunchedEffect(p.precio) { if (precioTxt.replace(',', '.').toDoubleOrNull() != p.precio) precioTxt = p.precio?.let { formatoMonto(it) }.orEmpty() }
    var elegirServicio by remember { mutableStateOf(false) }
    val objetivos = p.objetivos.ifEmpty { listOf("") }
    fun plan(cambio: (pe.saniape.app.data.staff.PlanPsico) -> pe.saniape.app.data.staff.PlanPsico) = vm.plan(cambio(vm.ev!!.plan))

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EtqForm("Objetivos")
            objetivos.forEachIndexed { i, o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        CampoCortoPsico("", o, { v -> plan { it.copy(objetivos = objetivos.mapIndexed { j, x -> if (j == i) v else x }) } }, ro,
                            placeholder = "Ej. Disminuir la ansiedad ante evaluaciones escolares")
                    }
                    if (!ro && objetivos.size > 1) {
                        Box(Modifier.size(40.dp).clickable { plan { it.copy(objetivos = objetivos.filterIndexed { j, _ -> j != i }) } },
                            contentAlignment = Alignment.Center) { Text("✕", color = c.error, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (!ro) Text("+ Objetivo", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { plan { it.copy(objetivos = objetivos + "") } }.padding(vertical = 6.dp))
        }

        Column {
            EtqForm("Enfoque (puedes elegir varios)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ENFOQUES_PSICO.forEach { e ->
                    val on = e.valor in p.enfoques
                    ChipPsico(e.nombre, on, habilitado = !ro) {
                        plan { it.copy(enfoques = if (on) it.enfoques - e.valor else it.enfoques + e.valor) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            CampoCortoPsico("", p.enfoqueOtro, { v -> plan { it.copy(enfoqueOtro = v) } }, ro, placeholder = "Otro enfoque (opcional)")
        }

        Column {
            CampoCortoPsico("Número de sesiones", sesionesTxt, { v ->
                val limpio = v.filter { it.isDigit() }.take(3)
                sesionesTxt = limpio
                plan { it.copy(numeroSesiones = limpio.toIntOrNull()?.takeIf { n -> n > 0 }) }
            }, ro, numerico = true)
            val sug = vm.sugerencia
            if (!ro && sug != null && ofrecerSugerencia(sug, p.numeroSesiones)) {
                Text("Sugerido: ${sug.sesiones} sesiones (${sug.nombre}) — usar", color = c.navy, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { plan { it.copy(numeroSesiones = sug.sesiones) } }.padding(top = 6.dp))
            } else if (sug != null) {
                Text("Sugerido por el diagnóstico: ${sug.sesiones} sesiones (${sug.nombre}).", color = c.textoSuave,
                    fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        SelectorChipsPsico("Frecuencia", FRECUENCIAS_PSICO, p.frecuencia, ro, permiteVacio = true) { f -> plan { it.copy(frecuencia = f) } }

        CampoCortoPsico("Sesiones con padres / familia", p.sesionesFamilia, { v -> plan { it.copy(sesionesFamilia = v) } }, ro,
            placeholder = "Ej. 2, o 1 cada 4")

        Column {
            EtqForm("Servicio de terapia propuesto")
            val elegido = servicios.find { it.id == p.procedimientoId }
            CajaSelectorForm(elegido?.let { s -> s.nombre + (s.precio?.let { " · S/ ${formatoMonto(it)}" } ?: "") } ?: "Elegir…") {
                if (!ro) elegirServicio = true
            }
        }
        CampoCortoPsico("Precio propuesto (S/)", precioTxt, { v ->
            val limpio = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10)
            precioTxt = limpio
            plan { it.copy(precio = limpio.replace(',', '.').toDoubleOrNull()) }
        }, ro, numerico = true)

        TextoLargoPsico("Recomendaciones", p.recomendaciones, { v -> plan { it.copy(recomendaciones = v) } }, ro)

        Column {
            CasillaPsico("Incluir el plan en el informe", p.incluirEnInforme, ro) { v -> plan { it.copy(incluirEnInforme = v) } }
            CasillaPsico("Agendar re-evaluación al cerrar el plan", p.reevaluarAlCerrar, ro) { v -> plan { it.copy(reevaluarAlCerrar = v) } }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (p.tratamientoCreadoId != null) {
                Text("✓ Tratamiento creado con este plan", color = c.ok, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("Si el paciente acepta, se crea el tratamiento de siempre con estos datos (todo editable).",
                    color = c.textoSuave, fontSize = 12.sp)
                if (onCrearTratamiento != null) {
                    BotonPsico("Crear tratamiento con este plan", relleno = true, habilitado = vm.accionando == null,
                        modifier = Modifier.fillMaxWidth(), onClick = onCrearTratamiento)
                } else {
                    Text("Créalo desde la ficha del paciente: Tratamientos → 🧠 Evaluación → Plan.", color = c.textoSuave, fontSize = 12.sp)
                }
            }
        }
    }

    if (elegirServicio) {
        AlertDialog(
            onDismissRequest = { elegirServicio = false },
            title = { Text("Servicio de terapia", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (servicios.isEmpty()) Text("No hay servicios activos.", color = c.textoSuave)
                    (listOf<ServicioPsico?>(null) + servicios).forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable {
                                elegirServicio = false
                                plan { it.copy(procedimientoId = s?.id, precio = precioPropuestoPlan(it.precio, s?.precio, it.numeroSesiones)) }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s?.nombre ?: "— Ninguno", color = if (s?.id == p.procedimientoId) c.navy else c.texto, fontSize = 14.sp,
                                fontWeight = if (s?.id == p.procedimientoId) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            s?.precio?.let { Text("S/ ${formatoMonto(it)}", color = c.teal, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { elegirServicio = false }) { Text("Cerrar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

/** "80" o "79.50". */
internal fun formatoMonto(n: Double): String =
    if (n % 1.0 == 0.0) n.toLong().toString() else {
        val cent = kotlin.math.round(n * 100).toLong()
        "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
    }
