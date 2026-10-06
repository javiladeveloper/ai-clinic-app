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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ESTADOS_TEST_PSICO
import pe.saniape.app.data.staff.EvaluacionPsicoRepo
import pe.saniape.app.data.staff.OpcionPsico
import pe.saniape.app.data.staff.POBLACIONES_PSICO
import pe.saniape.app.data.staff.PuntajeEscalaPsico
import pe.saniape.app.data.staff.TestAplicadoPsico
import pe.saniape.app.data.staff.TestCatalogoPsico
import pe.saniape.app.data.staff.VALIDEZ_PSICO
import pe.saniape.app.data.staff.agruparCatalogoPsico
import pe.saniape.app.data.staff.alertas
import pe.saniape.app.data.staff.instrumentosDeTest
import pe.saniape.app.data.staff.perfilDeTest
import pe.saniape.app.data.staff.fechaDmyPsico
import pe.saniape.app.data.staff.mensajePsico
import pe.saniape.app.data.staff.testCatalogoDeRespuesta
import pe.saniape.app.data.staff.textoEdadMeses
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.DialogoConTeclado
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.ChevronExpandible
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

// Componente 4 — Aplicación de tests. La psicóloga INGRESA los puntajes (texto:
// "T 65", "II+", "112"): en los tests comerciales el sistema no calcula nada
// (derechos de autor: no hay ítems ni baremos). Las fotos de dibujos y hojas son
// material protegido. Fase 3: los instrumentos LIBRES (PHQ-9, GAD-7, AUDIT, SRQ,
// Rosenberg, APGAR, ASRS, PSC-17) se pueden "Responder ítems" y los puntúa el
// SERVIDOR; cada test muestra su perfil y se compara con una aplicación anterior.

/** Color del seguimiento: aplicado → calificado → interpretado. */
@Composable
private fun colorEstadoTest(estado: String) = when (estado) {
    "interpretado" -> Sania.colors.ok to Sania.colors.okBg
    "calificado" -> Sania.colors.info to Sania.colors.infoBg
    else -> Sania.colors.pend to Sania.colors.pendBg
}

@Composable
internal fun SeccionTests(vm: EvaluacionPsicoViewModel, acciones: AccionesNativas) {
    val c = Sania.colors
    val ro = vm.soloLectura
    var abierto by remember { mutableStateOf<String?>(null) }
    var eligiendo by remember { mutableStateOf(false) }
    var quitar by remember { mutableStateOf<TestAplicadoPsico?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (vm.tests.isEmpty()) Text("Aún no hay tests aplicados.", color = c.textoSuave, fontSize = 13.sp)
        vm.tests.forEach { t ->
            TarjetaTest(
                vm = vm, t = t, abierto = abierto == t.id, acciones = acciones,
                onAbrir = { abierto = if (abierto == t.id) null else t.id },
                onQuitar = { quitar = t },
            )
        }
        if (!ro) {
            BotonPsico(if (vm.accionando == "test") "Agregando…" else "+ Agregar test", habilitado = vm.accionando == null,
                modifier = Modifier.fillMaxWidth()) { eligiendo = true }
        }
    }

    if (eligiendo) {
        DialogoElegirTest(
            onCerrar = { eligiendo = false },
            onElegir = { test ->
                eligiendo = false
                vm.agregarTest(test) { id -> if (id != null) abierto = id }
            },
        )
    }
    quitar?.let { t ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            title = { Text("¿Quitar ${t.nombreCorto}?", fontWeight = FontWeight.Bold) },
            text = { Text("Se quita de la evaluación y se borran también sus fotos.", color = c.texto) },
            confirmButton = { TextButton(onClick = { quitar = null; vm.borrarTest(t) }) { Text("Quitar", color = c.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaTest(
    vm: EvaluacionPsicoViewModel,
    t: TestAplicadoPsico,
    abierto: Boolean,
    acciones: AccionesNativas,
    onAbrir: () -> Unit,
    onQuitar: () -> Unit,
) {
    val c = Sania.colors
    val ro = vm.soloLectura
    val fotos = vm.fotos.filter { it.testAplicadoId == t.id }
    val (fgE, bgE) = colorEstadoTest(t.estado)
    var elegirFecha by remember { mutableStateOf(false) }
    fun cambiar(nuevo: TestAplicadoPsico) = vm.cambiarTest(nuevo)
    // Fase 3: autocálculo (solo instrumentos libres del catálogo global), perfil y retest.
    val instrumentos = remember(t.test, vm.instrumentos) { instrumentosDeTest(t.test, vm.instrumentos) }
    val perfil = remember(t, vm.instrumentos) { perfilDeTest(t, vm.instrumentos) }
    val alertas = t.alertas
    var respondiendo by remember { mutableStateOf(false) }
    var verPerfil by remember { mutableStateOf(false) }
    var comparando by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onAbrir).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.nombreCorto + if (t.test?.generaImagen == true) " 📷" else "", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                val sub = listOf(fechaDmyPsico(t.fecha), textoEdadMeses(t.edadMeses)).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 12.sp)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bgE).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(ESTADOS_TEST_PSICO.find { it.valor == t.estado }?.nombre ?: t.estado, color = fgE, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!t.enInforme) Text("no va al informe", color = c.textoSuave, fontSize = 11.sp)
                    if (fotos.isNotEmpty()) Text("📷 ${fotos.size}", color = c.textoSuave, fontSize = 11.sp)
                }
                if (t.respuestas != null || alertas.isNotEmpty()) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (t.respuestas != null) {
                            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text(if (t.respuestas.editado) "ítems · corregido a mano" else "ítems respondidos", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (alertas.isNotEmpty()) {
                            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.errorBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("⚠ Evaluar riesgo suicida", color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            ChevronExpandible(abierto)
        }
        if (!abierto) return@Column
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            t.test?.nombre?.takeIf { it.isNotBlank() && it != t.nombreCorto }?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }
            AlertasTestPsico(alertas)

            val conResponder = instrumentos.isNotEmpty() && !ro
            if (conResponder || perfil != null || vm.fase3) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (conResponder) {
                        BotonPsico(if (t.respuestas != null) "📝 Ver / cambiar respuestas" else "📝 Responder ítems",
                            habilitado = vm.accionando == null) { respondiendo = true }
                    }
                    if (perfil != null) BotonPsico(if (verPerfil) "📊 Ocultar perfil" else "📊 Ver perfil", color = c.textoSuave) { verPerfil = !verPerfil }
                    if (vm.fase3) BotonPsico("↔ Comparar con aplicación anterior", color = c.textoSuave) { comparando = true }
                }
            }
            t.respuestas?.let { r -> ResultadoItemsPsico(r, instrumentos.firstOrNull { it.id == r.instrumento }?.corto ?: t.nombreCorto) }
            if (verPerfil && perfil != null) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
                ) { GraficoPerfilPsico(perfil) }
            }

            // Lo más usado en el celular primero: fotografiar la hoja o el dibujo.
            Column {
                EtqForm("Fotos de dibujos y hojas")
                BloqueFotosPsico(
                    fotos = fotos, soloLectura = ro,
                    subiendo = vm.accionando == "foto:test:${t.id}",
                    acciones = acciones,
                    textoCamara = if (t.test?.generaImagen == true) "📷 Fotografiar hoja / dibujo" else "📷 Tomar foto",
                    onArchivo = { vm.subirFoto(it, uso = "test", testAplicadoId = t.id) },
                    onBorrar = { vm.borrarFoto(it) },
                )
            }

            Column {
                EtqForm("Fecha de aplicación")
                CajaSelectorForm(fechaDmyPsico(t.fecha).ifBlank { "Elegir" }) { if (!ro) elegirFecha = true }
                if (t.edadMeses != null) Text("Edad al aplicarlo: ${textoEdadMeses(t.edadMeses)}", color = c.textoSuave,
                    fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            CampoCortoPsico("Informante", t.informante, { cambiar(t.copy(informante = it)) }, ro)
            SelectorChipsPsico("Modalidad", listOf(OpcionPsico("presencial", "Presencial"), OpcionPsico("virtual", "Virtual")), t.modalidad, ro) {
                if (it != null) cambiar(t.copy(modalidad = it))
            }
            CampoCortoPsico("Forma / versión", t.forma, { cambiar(t.copy(forma = it)) }, ro)
            CampoCortoPsico("Baremo usado", t.baremo, { cambiar(t.copy(baremo = it)) }, ro, placeholder = "Ej. Lima 2002")
            SelectorChipsPsico("Validez del protocolo", VALIDEZ_PSICO, t.validez, ro, permiteVacio = true) { cambiar(t.copy(validez = it)) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtqForm("Puntajes por escala")
                if (t.puntajes.isEmpty()) Text("Sin escalas. Agrega las que uses.", color = c.textoSuave, fontSize = 12.sp)
                t.puntajes.forEachIndexed { i, p ->
                    FilaPuntaje(p, ro,
                        onCambio = { nuevo -> cambiar(t.copy(puntajes = t.puntajes.mapIndexed { j, x -> if (j == i) nuevo else x })) },
                        onQuitar = { cambiar(t.copy(puntajes = t.puntajes.filterIndexed { j, _ -> j != i })) })
                }
                if (!ro) Text("+ Escala", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { cambiar(t.copy(puntajes = t.puntajes + PuntajeEscalaPsico())) }.padding(vertical = 4.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoCortoPsico("Puntaje global", t.global.puntaje, { cambiar(t.copy(global = t.global.copy(puntaje = it))) }, ro,
                    placeholder = "Ej. CIT 98", modifier = Modifier.weight(1f))
                CampoCortoPsico("Categoría global", t.global.categoria, { cambiar(t.copy(global = t.global.copy(categoria = it))) }, ro,
                    placeholder = "Ej. Promedio", modifier = Modifier.weight(1f))
            }
            TextoLargoPsico("Interpretación", t.interpretacion, { cambiar(t.copy(interpretacion = it)) }, ro)
            TextoLargoPsico("Observaciones durante la aplicación", t.observaciones, { cambiar(t.copy(observaciones = it)) }, ro, minLineas = 2)
            SelectorChipsPsico("Seguimiento", ESTADOS_TEST_PSICO, t.estado, ro) { if (it != null) cambiar(t.copy(estado = it)) }
            CasillaPsico("Entra al informe", t.enInforme, ro) { cambiar(t.copy(enInforme = it)) }

            if (!ro) {
                Text("Quitar test", color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End).clickable(onClick = onQuitar).padding(6.dp))
            }
        }
    }

    if (elegirFecha) {
        DialogoFecha(onElegir = { f -> cambiar(t.copy(fecha = f)) }, onCerrar = { elegirFecha = false }, inicial = t.fecha.ifBlank { null })
    }
    if (respondiendo && instrumentos.isNotEmpty()) {
        DialogoResponderItems(
            test = t, disponibles = instrumentos, guardando = vm.accionando == "responder:${t.id}",
            onCerrar = { respondiendo = false },
            onGuardar = { ins, valores -> vm.responderItems(t, ins, valores) { ok -> if (ok) respondiendo = false } },
        )
    }
    if (comparando) DialogoRetest(t) { comparando = false }
}

/** Una escala: su nombre y los 4 puntajes en 2×2 (en el celular no entra la tabla de la web). */
@Composable
private fun FilaPuntaje(p: PuntajeEscalaPsico, ro: Boolean, onCambio: (PuntajeEscalaPsico) -> Unit, onQuitar: () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CampoCortoPsico("", p.escala, { onCambio(p.copy(escala = it)) }, ro, placeholder = "Escala") }
            if (!ro) Box(Modifier.size(40.dp).clickable(onClick = onQuitar), contentAlignment = Alignment.Center) {
                Text("✕", color = c.error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CampoCortoPsico("Directo", p.directo, { onCambio(p.copy(directo = it)) }, ro, modifier = Modifier.weight(1f))
            CampoCortoPsico("Transformado", p.transformado, { onCambio(p.copy(transformado = it)) }, ro, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CampoCortoPsico("Percentil", p.percentil, { onCambio(p.copy(percentil = it)) }, ro, modifier = Modifier.weight(1f))
            CampoCortoPsico("Categoría", p.categoria, { onCambio(p.copy(categoria = it)) }, ro, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * "+ Agregar test": buscar en el catálogo (agrupado por categoría, los más usados
 * en Perú primero), filtrar por edad, o crear uno propio de la clínica.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoElegirTest(onCerrar: () -> Unit, onElegir: (TestCatalogoPsico) -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var tests by remember { mutableStateOf<List<TestCatalogoPsico>?>(null) }
    var fallo by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var pob by remember { mutableStateOf<String?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    var nCorto by remember { mutableStateOf("") }
    var nNombre by remember { mutableStateOf("") }
    var nEscalas by remember { mutableStateOf("") }
    var nImagen by remember { mutableStateOf(false) }
    var creando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val r = EvaluacionPsicoRepo.catalogo()
        if (r == null) fallo = true else tests = r
    }
    val grupos = remember(tests, q, pob) { agruparCatalogoPsico(tests.orEmpty(), q, pob) }

    DialogoConTeclado(onCerrar) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 720.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            Row(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (nuevo) "Test propio de la clínica" else "Agregar test", color = c.sobreNavy, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("✕", color = c.sobreNavy, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onCerrar).padding(6.dp))
            }
            if (nuevo) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoCortoPsico("Nombre corto", nCorto, { nCorto = it }, false, placeholder = "Ej. EDAH")
                    CampoCortoPsico("Nombre completo", nNombre, { nNombre = it }, false)
                    TextoLargoPsico("Escalas (una por línea, opcional)", nEscalas, { nEscalas = it }, false, minLineas = 3)
                    CasillaPsico("Genera imagen (dibujos u hojas para fotografiar)", nImagen, false) { nImagen = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonPsico("Volver", color = c.textoSuave, modifier = Modifier.weight(1f)) { nuevo = false }
                        BotonPsico(if (creando) "Creando…" else "Crear y agregar", relleno = true,
                            habilitado = !creando && (nCorto.isNotBlank() || nNombre.isNotBlank()), modifier = Modifier.weight(1f)) {
                            creando = true
                            scope.launch {
                                val r = EvaluacionPsicoRepo.crearTestCatalogo(
                                    nombreCorto = nCorto.trim().ifBlank { nNombre.trim() },
                                    nombre = nNombre.trim().ifBlank { nCorto.trim() },
                                    escalas = nEscalas.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                    generaImagen = nImagen,
                                )
                                creando = false
                                val t = testCatalogoDeRespuesta(r.cuerpo)
                                if (r.registrada && t != null) onElegir(t) else Toaster.error(r.mensajePsico())
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoCortoPsico("", q, { q = it.take(60) }, false, placeholder = "Buscar (WISC, HTP, ansiedad…)")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChipPsico("Todas las edades", pob == null) { pob = null }
                        POBLACIONES_PSICO.forEach { p -> ChipPsico(p.nombre, pob == p.valor) { pob = if (pob == p.valor) null else p.valor } }
                    }
                }
                Box(Modifier.weight(1f, fill = false)) {
                    when {
                        fallo -> Text("No se pudo cargar el catálogo. Revisa tu conexión.", color = c.error, fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp))
                        tests == null -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = c.navy, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Cargando catálogo…", color = c.textoSuave, fontSize = 13.sp)
                        }
                        grupos.isEmpty() -> Text("Sin resultados.", color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                        else -> LazyColumn(Modifier.fillMaxWidth()) {
                            grupos.forEach { (categoria, lista) ->
                                item(key = "cat:$categoria") {
                                    Text(categoria.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp))
                                }
                                items(lista, key = { it.id }) { x ->
                                    Column(Modifier.fillMaxWidth().clickable { onElegir(x) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                        Text(x.nombreCorto + (if (x.generaImagen) " 📷" else "") + (if (x.clinicaId != null) " · propio" else ""),
                                            color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        val pobl = x.poblacion.mapNotNull { p -> POBLACIONES_PSICO.find { it.valor == p }?.nombre }.joinToString(", ")
                                        Text(listOf(x.nombre, pobl).filter { it.isNotBlank() }.joinToString(" · "), color = c.textoSuave,
                                            fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                Text("¿No está? Agregar un test propio", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { nuevo = true; nCorto = q.trim() }.padding(16.dp))
            }
        }
    }
}
