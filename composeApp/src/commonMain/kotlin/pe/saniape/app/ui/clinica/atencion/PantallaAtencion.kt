package pe.saniape.app.ui.clinica.atencion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.fechaLegibleCorta
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// CONSULTA GUIADA — la atención médica de UNA cita (gemela de ConsultaGuiada.tsx):
//   Consulta:      motivo y anamnesis → vitales → examen → diagnóstico → plan → cierre
//   Procedimiento: consentimiento y procedimiento → vitales → diagnóstico →
//                  indicaciones y control → cierre
// Los pasos, quién puede editar y qué falta los resuelve el servidor (flags);
// el borrador y el guardado viven en AtencionViewModel.
// ─────────────────────────────────────────────────────────────────────────────

/** Nombre del estado en la cola (NOMBRE_ESTADO_COLA de la web). */
private val NOMBRE_ESTADO_COLA = mapOf(
    "por_llegar" to "Por llegar",
    "en_espera" to "En espera",
    "en_consulta" to "En consulta",
    "atendido" to "Atendido",
    "no_vino" to "No vino",
)

/**
 * Pantalla de la atención de [citaId]. [onSalir] vuelve a la agenda (también al
 * terminar la atención). [ctx]: permisos (cobro del cierre, quién firma) y la hoja de filiación.
 * [apertura]: distinto en cada "▶ Atender" — va en la key del ViewModel, así cada
 * apertura arranca con un VM nuevo (sin el borrador ni el `terminada` de la anterior).
 */
@Composable
fun PantallaAtencion(
    ctx: ContextoStaff,
    citaId: String,
    apertura: Long,
    acciones: AccionesNativas,
    onSalir: () -> Unit,
    onVerFicha: (pacienteId: String) -> Unit,
    onOdontograma: (citaId: String) -> Unit,
) {
    val c = Sania.colors
    val vm: AtencionViewModel = viewModel(key = "$citaId:$apertura") { AtencionViewModel(citaId) }
    val scope = rememberCoroutineScope()
    var confirmarSalir by remember { mutableStateOf(false) }

    fun intentarSalir() {
        if (vm.sucio && !vm.soloLectura) confirmarSalir = true else onSalir()
    }
    ManejarAtras(activo = true) { intentarSalir() }

    // Terminada → de vuelta a la agenda. Correcto aunque `terminada` no se
    // reinicie: el VM es nuevo en cada apertura (key con [apertura]).
    LaunchedEffect(vm.terminada) { if (vm.terminada) onSalir() }

    // Consentimiento emitido al abrir (el servidor lo deja PENDIENTE): se avisa una
    // vez (saveable: al rotar no se repite el aviso).
    var avisoCi by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm.datos?.flags?.consentimientosEmitidos) {
        if (!avisoCi && (vm.datos?.flags?.consentimientosEmitidos ?: 0) > 0) {
            avisoCi = true
            Toaster.exito("Consentimiento informado generado: imprímelo para la firma")
        }
    }

    // Con el teclado abierto el pie queda tapado: "Guardar" sube a la cabecera
    // (misma regla que los diálogos, 29/09/2026).
    val tecladoAbierto = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val d = vm.datos

    // Un solo dictado a la vez en toda la pantalla (ver CampoTextoClinico). Al
    // cambiar de paso o salir de la atención se suelta: el campo que escuchaba
    // se detiene.
    val dictadoActivo = remember { mutableStateOf<Any?>(null) }
    DisposableEffect(vm.paso) { onDispose { dictadoActivo.value = null } }

    CompositionLocalProvider(LocalDictadoActivo provides dictadoActivo) {
        Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                if (d == null) {
                    CabeceraSimple(onAgenda = ::intentarSalir)
                    Box(Modifier.fillMaxSize().padding(Sania.dim.xl), contentAlignment = Alignment.Center) {
                        val err = vm.error
                        if (err != null && !vm.cargando) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(err, color = c.error, fontSize = Sania.txt.cuerpo, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(Sania.dim.md))
                                BotonPie("Reintentar", primario = true, onClick = { vm.cargar() })
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = c.navy)
                                Spacer(Modifier.height(Sania.dim.md))
                                Text("Cargando la atención…", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                            }
                        }
                    }
                } else {
                    ContenidoAtencion(vm, d, ctx, acciones, tecladoAbierto, ::intentarSalir, onVerFicha, onOdontograma)
                }
            }
        }
    }

    if (confirmarSalir) {
        DialogoConfirmarSalida(
            guardando = vm.guardando,
            onGuardarYSalir = {
                scope.launch {
                    if (vm.guardar()) { confirmarSalir = false; onSalir() }
                }
            },
            onSalirSinGuardar = { confirmarSalir = false; onSalir() },
            onSeguir = { confirmarSalir = false },
        )
    }
}

/** La atención ya cargada: cabecera, estado, alergias, pasos, el paso actual y el pie. */
@Composable
private fun ColumnScope.ContenidoAtencion(
    vm: AtencionViewModel,
    d: DatosConsultaApp,
    ctx: ContextoStaff,
    acciones: AccionesNativas,
    tecladoAbierto: Boolean,
    onAgenda: () -> Unit,
    onVerFicha: (pacienteId: String) -> Unit,
    onOdontograma: (citaId: String) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val soloLectura = vm.soloLectura
    val pacienteId = d.cita.paciente_id ?: d.cita.paciente?.id
    var verFiliacion by remember { mutableStateOf(false) }
    var verReceta by remember { mutableStateOf(false) }
    Cabecera(
        d = d,
        accionTeclado = if (tecladoAbierto && vm.sucio && !soloLectura) {
            { scope.launch { vm.guardar() } }
        } else null,
        guardando = vm.guardando,
        onAgenda = onAgenda,
        onFicha = { onVerFicha(d.cita.paciente?.id ?: d.cita.paciente_id ?: return@Cabecera) },
        onFiliacion = { if (pacienteId != null) verFiliacion = true },
        onOdontograma = { onOdontograma(d.cita.id) },
    )

    val cuerpo = rememberScrollState()
    // Cada paso empieza arriba.
    LaunchedEffect(vm.paso) { cuerpo.scrollTo(0) }
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(cuerpo)
            .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md),
    ) {
        FilaEstado(d, soloLectura = soloLectura, guardando = vm.guardando, sucio = vm.sucio)
        if (soloLectura) {
            Spacer(Modifier.height(Sania.dim.sm))
            Aviso(textoSoloLectura(d), c.textoSuave, c.chipBg)
        }
        FranjaPaciente(d)
        Spacer(Modifier.height(Sania.dim.md))
        BarraPasos(vm)
        Spacer(Modifier.height(Sania.dim.md))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
        ) {
            when (vm.pasoActual?.clave) {
                "motivo" -> PasoMotivo(vm, d, soloLectura)
                "vitales" -> PasoVitales(vm, d, soloLectura)
                "examen" -> PasoExamen(vm, d, soloLectura)
                "procedimiento" -> PasoProcedimiento(vm, d, soloLectura, acciones)
                "diagnostico" -> PasoDiagnostico(vm, d, soloLectura)
                "plan" -> PasoPlan(vm, d, soloLectura, acciones, onNuevaReceta = { if (pacienteId != null) verReceta = true })
                "cierre" -> PasoCierre(
                    vm, d, soloLectura, acciones, ctx,
                    onVerFicha = { (d.cita.paciente?.id ?: d.cita.paciente_id)?.let(onVerFicha) },
                )
                // Una clave que esta versión no conoce (el servidor manda los pasos).
                else -> PasoPendiente(vm.pasoActual?.titulo ?: "Paso")
            }
        }
        Spacer(Modifier.height(Sania.dim.lg))
    }

    // En el último paso (cierre) el pie lo pinta el propio paso.
    if (!vm.esUltimoPaso && !tecladoAbierto) PieNavegacion(vm, soloLectura)

    if (verFiliacion && pacienteId != null) {
        DialogoFiliacion(
            pacienteId = pacienteId,
            faltantes = d.flags.faltantesFiliacion,
            // Como la web (esMenorDeEdad): por la edad, no por los faltantes.
            menorDeEdad = d.flags.edad?.let { it < 18 } == true,
            onCancelar = { verFiliacion = false },
            // El toast ("Filiación actualizada" / "Historia clínica abierta…") lo muestra el diálogo.
            onGuardada = { verFiliacion = false; vm.recargar() },
        )
    }
    if (verReceta && pacienteId != null) {
        // El borrador ya se guardó (BloqueReceta guarda antes de abrir). Prellenado
        // como el `prefill` de la web: todos los diagnósticos, el primer código
        // CIE-10, las indicaciones y el profesional que atiende.
        val (dxTexto, dxCodigo) = diagnosticoParaReceta(vm.borrador.diagnosticos)
        DialogoReceta(
            ctx = ctx,
            pacienteId = pacienteId,
            profesionales = d.profesionales,
            diagnostico = dxTexto,
            cie10 = dxCodigo,
            citaId = d.cita.id,
            tratamientoId = d.cita.tratamiento_id,
            recetasOptIn = d.modulos.recetasOptIn,
            onCancelar = { verReceta = false },
            // El toast con el número lo muestra el diálogo.
            onEmitida = { verReceta = false; vm.recargar() },
            terapeutaSugerido = vm.borrador.terapeutaId ?: d.cita.terapeuta_id,
            indicaciones = vm.borrador.textos["tratamiento"],
        )
    }
}

/**
 * Diagnóstico y CIE-10 con que se prellena la receta (dxTexto / dxCodigo de
 * ConsultaGuiada.tsx): las descripciones unidas por "; " (null si no hay) y el
 * código del PRIMER diagnóstico que lo tenga.
 */
internal fun diagnosticoParaReceta(dx: List<pe.saniape.app.data.staff.DiagnosticoCie>): Pair<String?, String?> {
    // normalizarDiagnosticos de la web: sin vacíos ni repetidos; sin descripción, el código.
    val vistos = mutableSetOf<String>()
    val normalizados = dx.mapNotNull { d ->
        val descripcion = d.descripcion.replace(Regex("\\s+"), " ").trim()
        val codigo = d.codigo?.trim()?.uppercase()?.ifEmpty { null }
        if (descripcion.isEmpty() && codigo == null) return@mapNotNull null
        val clave = codigo?.let { "c:$it" } ?: "t:${descripcion.lowercase()}"
        if (!vistos.add(clave)) return@mapNotNull null
        descripcion.ifEmpty { codigo.orEmpty() } to codigo
    }
    val texto = normalizados.joinToString("; ") { it.first }.ifBlank { null }
    return texto to normalizados.firstNotNullOfOrNull { it.second }
}

/** Aviso de solo lectura, con el motivo (como la web). */
private fun textoSoloLectura(d: DatosConsultaApp): String = when {
    !d.flags.puedeAtender -> "Solo lectura: solo el profesional que atiende puede editar."
    d.cita.paciente?.estado == "Inactivo" -> "Solo lectura: el paciente está dado de baja."
    else -> "Solo lectura."
}

// ── Cabecera ─────────────────────────────────────────────────────────────────

@Composable
private fun CabeceraSimple(onAgenda: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.lg, vertical = Sania.dim.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonCabecera("← Agenda", onClick = onAgenda)
        Spacer(Modifier.width(Sania.dim.md))
        Text("Atención", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
    }
}

/**
 * Nombre, edad y HC; servicio y fecha · hora; y las salidas: Ficha,
 * 📋 Filiación (⚠ n si falta algo) y 🦷 Odontograma (dental). [accionTeclado]
 * = "Guardar" en la cabecera cuando el teclado tapa el pie.
 */
@Composable
private fun Cabecera(
    d: DatosConsultaApp,
    accionTeclado: (() -> Unit)?,
    guardando: Boolean,
    onAgenda: () -> Unit,
    onFicha: () -> Unit,
    onFiliacion: () -> Unit,
    onOdontograma: () -> Unit,
) {
    val c = Sania.colors
    val cita = d.cita
    val f = d.flags
    val servicio = cita.procedimiento?.nombre?.takeIf { it.isNotBlank() }
    val queEs = if (f.esProcedimiento) "Procedimiento${servicio?.let { ": $it" } ?: ""}"
    else servicio ?: cita.tipo.ifBlank { "Consulta" }
    val linea1 = listOfNotNull(
        f.edad?.let { "$it años" },
        f.numeroHc.ifBlank { "HC por abrir" },
    ).joinToString(" · ")
    val cuando = listOfNotNull(
        cita.fecha.takeIf { it.isNotBlank() }?.let { fechaLegibleCorta(it) },
        cita.hora?.takeIf { it.isNotBlank() }?.let { hora12(it) },
    ).joinToString(" · ")
    val faltaFil = f.faltantesFiliacion.size

    Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCabecera("← Agenda", onClick = onAgenda)
            Spacer(Modifier.weight(1f))
            if (accionTeclado != null) {
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.md.dp))
                        .background(if (!guardando) c.sobreNavy else c.sobreNavy.copy(alpha = 0.35f))
                        .clickable(enabled = !guardando) { accionTeclado() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) { Text(if (guardando) "Guardando…" else "Guardar", color = c.navyDark, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(Sania.dim.sm))
        Text(cita.paciente?.nombre?.ifBlank { null } ?: "Paciente", color = c.sobreNavy, fontSize = Sania.txt.subtitulo,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(linea1, color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 13.sp)
        Text(listOf(queEs, cuando).filter { it.isNotBlank() }.joinToString(" · "),
            color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Sania.dim.sm))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonCabecera("Ficha", onClick = onFicha)
            BotonCabecera(
                "📋 Filiación" + (if (faltaFil > 0) " ⚠ $faltaFil" else ""),
                alerta = faltaFil > 0, onClick = onFiliacion,
            )
            if (f.dental) BotonCabecera("🦷 Odontograma", onClick = onOdontograma)
        }
    }
}

@Composable
private fun BotonCabecera(texto: String, alerta: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    val borde = if (alerta) c.pend else c.sobreNavy.copy(alpha = 0.45f)
    Box(
        Modifier.heightIn(min = 36.dp).clip(forma).border(1.dp, borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (alerta) c.pendBg else c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

// ── Estado, alergias y pasos ─────────────────────────────────────────────────

/** Badge de la cola + "Solo lectura" / "Guardando…" / "Cambios sin guardar". */
@Composable
private fun FilaEstado(d: DatosConsultaApp, soloLectura: Boolean, guardando: Boolean, sucio: Boolean) {
    val c = Sania.colors
    val estado = d.flags.estadoCola
    val (fg, bg) = when (estado) {
        "en_consulta" -> c.sobreNavy to c.navy
        "atendido", "atendida" -> c.ok to c.okBg
        else -> c.navy to c.chipBg
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pastilla(NOMBRE_ESTADO_COLA[estado] ?: estado, fg, bg)
        if (soloLectura) Pastilla("Solo lectura", c.textoSuave, c.chipBg)
        when {
            guardando -> Text("Guardando…", color = c.textoSuave, fontSize = 12.sp)
            sucio && !soloLectura -> Text("Cambios sin guardar", color = c.pend, fontSize = 12.sp)
        }
    }
}

/** Alergias (en rojo), antecedentes y medicación actual: lo que se ve de un vistazo. */
@Composable
private fun FranjaPaciente(d: DatosConsultaApp) {
    val c = Sania.colors
    val p = d.cita.paciente ?: return
    val alergias = p.alergias?.trim()?.takeIf { it.isNotEmpty() }
    val antecedentes = p.antecedentes?.trim()?.takeIf { it.isNotEmpty() }
    val medicacion = p.medicacion_actual?.trim()?.takeIf { it.isNotEmpty() }
    if (alergias == null && antecedentes == null && medicacion == null) return
    Spacer(Modifier.height(Sania.dim.sm))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        alergias?.let { Aviso("⚠ Alergias: $it", c.error, c.errorBg, negrita = true) }
        antecedentes?.let { Aviso("Antecedentes: $it", c.texto, c.pendBg) }
        medicacion?.let { Aviso("Medicación actual: $it", c.texto, c.pendBg) }
    }
}

@Composable
private fun Aviso(texto: String, fg: Color, bg: Color, negrita: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(texto, color = fg, fontSize = 13.sp, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun Pastilla(texto: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(texto, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Pasos en una tira horizontal: el actual en navy, los anteriores con ✓. Tocar = ir (guarda solo). */
@Composable
private fun BarraPasos(vm: AtencionViewModel) {
    val c = Sania.colors
    val estado = rememberLazyListState()
    LaunchedEffect(vm.paso) { if (vm.pasos.isNotEmpty()) estado.animateScrollToItem(vm.paso) }
    LazyRow(state = estado, horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 8.dp)) {
        itemsIndexed(vm.pasos, key = { _, p -> p.clave }) { i, p ->
            val actual = i == vm.paso
            val hecho = i < vm.paso
            val forma = RoundedCornerShape(Sania.shape.sm.dp)
            Row(
                Modifier.heightIn(min = 40.dp).clip(forma)
                    .background(when { actual -> c.navy; hecho -> c.chipBg; else -> c.superficie })
                    .border(1.dp, if (actual) c.navy else c.borde, forma)
                    .clickable { vm.irA(i) }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(if (actual) c.sobreNavy else c.borde),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (hecho) "✓" else "${i + 1}", color = if (actual) c.navy else c.texto, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Text(p.titulo, color = when { actual -> c.sobreNavy; hecho -> c.navy; else -> c.textoSuave },
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// ── Pie ──────────────────────────────────────────────────────────────────────

/** "← Atrás" · "Guardar" (con cambios) · "Siguiente →". */
@Composable
internal fun PieNavegacion(vm: AtencionViewModel, soloLectura: Boolean) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
    Row(
        Modifier.fillMaxWidth().background(c.superficie).padding(horizontal = Sania.dim.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (vm.paso > 0) BotonPie("← Atrás", onClick = { vm.atras() })
        Spacer(Modifier.weight(1f))
        if (!soloLectura && vm.sucio) {
            BotonPie(if (vm.guardando) "Guardando…" else "Guardar", habilitado = !vm.guardando,
                onClick = { scope.launch { vm.guardar() } })
        }
        if (!vm.esUltimoPaso) BotonPie("Siguiente →", primario = true, onClick = { vm.siguiente() })
    }
}

@Composable
private fun BotonPie(texto: String, primario: Boolean = false, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Box(
        Modifier.heightIn(min = 44.dp).clip(forma)
            .background(if (primario) (if (habilitado) c.navy else c.borde) else c.superficie)
            .border(1.dp, if (primario) Color.Transparent else c.borde, forma)
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (primario) c.sobreNavy else if (habilitado) c.navy else c.textoSuave,
            fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Placeholder de un paso que esta versión de la app todavía no conoce. */
@Composable
internal fun PasoPendiente(titulo: String) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(vertical = Sania.dim.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(titulo, color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Actualiza la app para ver este paso.", color = c.textoSuave, fontSize = 13.sp)
    }
}

/** Salir con cambios sin guardar: guardar y salir, salir igual o seguir editando. */
@Composable
private fun DialogoConfirmarSalida(
    guardando: Boolean,
    onGuardarYSalir: () -> Unit,
    onSalirSinGuardar: () -> Unit,
    onSeguir: () -> Unit,
) {
    val c = Sania.colors
    AlertDialog(
        onDismissRequest = onSeguir,
        title = { Text("¿Salir de la atención?", fontWeight = FontWeight.Bold) },
        text = { Text("Tienes cambios sin guardar en la historia clínica.", color = c.texto, fontSize = Sania.txt.cuerpo) },
        confirmButton = {
            TextButton(onClick = onGuardarYSalir, enabled = !guardando) {
                Text(if (guardando) "Guardando…" else "Guardar y salir", color = c.navy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSalirSinGuardar) { Text("Salir sin guardar", color = c.error) }
                TextButton(onClick = onSeguir) { Text("Seguir", color = c.textoSuave) }
            }
        },
        containerColor = c.superficie,
    )
}
