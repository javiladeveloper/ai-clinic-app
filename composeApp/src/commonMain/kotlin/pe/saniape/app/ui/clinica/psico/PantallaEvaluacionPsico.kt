package pe.saniape.app.ui.clinica.psico

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.COMPONENTES_PSICO
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PrefillPlanPsico
import pe.saniape.app.data.staff.puedeAgregarCitaEvaluacion
import pe.saniape.app.data.staff.textoCitaDeEvaluacion
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.clinica.atencion.LocalDictadoActivo
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.tutoriales.tourAncla

/** Clave de la pestaña del informe (los 6 componentes usan las suyas). */
internal const val PESTANIA_INFORME = "informe"

/** Estilo de un chip según su estado (vacío / en curso / completo) — puro para los tests. */
internal enum class TonoChip { VACIO, EN_CURSO, COMPLETO }

internal fun tonoChip(estado: String): TonoChip = when (estado) {
    "completo" -> TonoChip.COMPLETO
    "en_curso" -> TonoChip.EN_CURSO
    else -> TonoChip.VACIO
}

internal fun marcaChip(estado: String): String = when (tonoChip(estado)) {
    TonoChip.COMPLETO -> "●"
    TonoChip.EN_CURSO -> "◐"
    TonoChip.VACIO -> "○"
}

/** El chip del informe: emitido = completo; borrador = en curso; sin informe = vacío. */
internal fun estadoChipInforme(estado: String?): String = when (estado) {
    "emitido" -> "completo"
    null -> "vacio"
    else -> "en_curso"
}

/**
 * "🧠 Evaluación psicológica" a pantalla completa (gemela de
 * EspacioEvaluacionPsico.tsx): 6 chips de estado arriba y el formulario del
 * componente elegido, con autoguardado; al final, el informe.
 *
 * [onCrearTratamiento]: "Crear tratamiento con este plan" abre el formulario de
 * tratamiento de SIEMPRE pre-llenado (lo maneja quien abre esta pantalla). null =
 * no se ofrece aquí. [apertura] distinto en cada apertura (key del ViewModel).
 */
@Composable
fun PantallaEvaluacionPsico(
    ctx: ContextoStaff,
    /** El tratamiento de la evaluación; o null y [citaId] (desde la agenda). */
    tratamientoId: String?,
    apertura: Long,
    acciones: AccionesNativas,
    onSalir: () -> Unit,
    onCrearTratamiento: ((evaluacionId: String, prefill: PrefillPlanPsico) -> Unit)? = null,
    /** Desde la agenda: la cita (el servidor resuelve su tratamiento, contrato §11). */
    citaId: String? = null,
) {
    val c = Sania.colors
    val vm: EvaluacionPsicoViewModel = viewModel(key = "psico:${tratamientoId ?: citaId}:$apertura") { EvaluacionPsicoViewModel(tratamientoId, citaId) }
    val scope = rememberCoroutineScope()

    fun salir() { vm.guardarAlSalir(); onSalir() }
    ManejarAtras(activo = true) { salir() }

    // Un solo dictado a la vez (como la consulta guiada); al cambiar de pestaña se suelta.
    val dictadoActivo = remember { mutableStateOf<Any?>(null) }
    DisposableEffect(vm.pestania) { onDispose { dictadoActivo.value = null } }

    CompositionLocalProvider(LocalDictadoActivo provides dictadoActivo) {
        Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Cabecera(vm, ctx, onVolver = ::salir)
                val ev = vm.ev
                if (ev == null) {
                    Box(Modifier.fillMaxSize().padding(Sania.dim.xl), contentAlignment = Alignment.Center) {
                        val err = vm.error
                        when {
                            vm.cargando -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = c.navy)
                                Spacer(Modifier.height(Sania.dim.md))
                                Text("Cargando la evaluación…", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                            }
                            vm.sinAcceso -> EstadoVacio(
                                emoji = "🔒", titulo = "Evaluación confidencial",
                                subtitulo = err ?: "Solo el Admin y el profesional tratante pueden verla.",
                                textoAccion = "Volver", onAccion = ::salir,
                            )
                            err != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(err, color = c.error, fontSize = Sania.txt.cuerpo, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(Sania.dim.md))
                                BotonPsico("Reintentar", relleno = true) { vm.cargar() }
                            }
                        }
                    }
                } else {
                    val cuerpo = rememberScrollState()
                    LaunchedEffect(vm.pestania) { cuerpo.scrollTo(0) }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(cuerpo)
                            .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md),
                    ) {
                        if (vm.soloLectura) {
                            AvisoPsico("Informe emitido: la evaluación está cerrada (solo lectura). Para corregir el informe, emite una nueva versión en 📄 Informe.", c.ok, c.okBg, negrita = true)
                            Spacer(Modifier.height(Sania.dim.sm))
                        }
                        val titulo = COMPONENTES_PSICO.find { it.clave == vm.pestania }?.let { "${it.icono} ${it.tituloLargo}" }
                            ?: "📄 Informe psicológico"
                        Text(titulo, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(Sania.dim.sm))
                        Column(
                            Modifier.fillMaxWidth().tourAncla("evaluacion_psico.contenido").clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
                        ) {
                            when (vm.pestania) {
                                "entrevista" -> SeccionEntrevista(vm, acciones)
                                "fuentes" -> SeccionFuentes(vm, acciones)
                                "observacion" -> SeccionObservacion(vm)
                                "tests" -> SeccionTests(vm, acciones)
                                "analisis" -> SeccionAnalisis(vm)
                                "plan" -> SeccionPlan(
                                    vm, vm.espacio?.servicios.orEmpty(),
                                    onCrearTratamiento = onCrearTratamiento?.let { cb ->
                                        {
                                            scope.launch {
                                                val r = vm.prefillParaCrear() ?: return@launch
                                                vm.guardarAlSalir()
                                                cb(r.first, r.second)
                                            }
                                        }
                                    },
                                )
                                // La IA del informe solo con la feature `ia` del plan (hoy apagada en todos).
                                else -> SeccionInforme(vm, acciones, conIA = ctx.can("ia"))
                            }
                        }
                        Spacer(Modifier.height(Sania.dim.xxl))
                    }
                }
            }
        }
    }
}

/** Paciente, servicio y "cita N de M", "+ Agregar cita de evaluación" y los 7 chips. */
@Composable
private fun Cabecera(vm: EvaluacionPsicoViewModel, ctx: ContextoStaff, onVolver: () -> Unit) {
    val c = Sania.colors
    val e = vm.espacio
    Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonCabeceraPsico("← Volver", onClick = onVolver)
            Spacer(Modifier.weight(1f))
            if (vm.guardando > 0) Text("Guardando…", color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(Sania.dim.sm))
        Text("🧠 Evaluación psicológica", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
        e?.paciente?.let { p ->
            Text(listOfNotNull(p.nombre, p.edadTexto.ifBlank { null }, p.dni?.let { "DNI $it" }).joinToString(" · "),
                color = c.sobreNavy, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        e?.tratamiento?.let { t ->
            Text(
                listOfNotNull(t.procedimientoNombre ?: "Evaluación", textoCitaDeEvaluacion(t.copy(totalSesiones = vm.totalCitas)))
                    .joinToString(" · "),
                color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 12.sp,
            )
            val agrega = !vm.soloLectura && puedeAgregarCitaEvaluacion(
                ctx.puede("citas"), ctx.puede("sesiones"), t.estado, fichaInactiva = false,
            )
            if (agrega) {
                Spacer(Modifier.height(Sania.dim.sm))
                BotonCabeceraPsico(if (vm.accionando == "cita") "Agregando…" else "+ Agregar cita de evaluación") {
                    if (vm.accionando == null) vm.agregarCita()
                }
            }
        }
        if (vm.ev != null) {
            Spacer(Modifier.height(Sania.dim.md))
            Row(Modifier.fillMaxWidth().tourAncla("evaluacion_psico.componentes").horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                COMPONENTES_PSICO.forEach { comp ->
                    ChipComponente("${marcaChip(vm.estados.de(comp.clave))} ${comp.icono} ${comp.titulo}",
                        tonoChip(vm.estados.de(comp.clave)), vm.pestania == comp.clave) { vm.irA(comp.clave) }
                }
                val ei = estadoChipInforme(vm.informe?.estado)
                ChipComponente("${marcaChip(ei)} 📄 Informe", tonoChip(ei), vm.pestania == PESTANIA_INFORME) { vm.irA(PESTANIA_INFORME) }
            }
        }
    }
}

@Composable
private fun ChipComponente(texto: String, tono: TonoChip, elegido: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val (fg, bg) = when (tono) {
        TonoChip.COMPLETO -> c.ok to c.okBg
        TonoChip.EN_CURSO -> c.pend to c.pendBg
        TonoChip.VACIO -> c.textoSuave to c.superficie
    }
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Box(
        Modifier.heightIn(min = 38.dp).clip(forma).background(bg)
            .border(if (elegido) 3.dp else 1.dp, if (elegido) c.sobreNavy else fg.copy(alpha = 0.6f), forma)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
private fun BotonCabeceraPsico(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box(
        Modifier.heightIn(min = 36.dp).clip(forma).border(1.dp, c.sobreNavy.copy(alpha = 0.45f), forma)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}
