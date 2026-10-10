package pe.saniape.app.ui.clinica.recetas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PrefillRecetaAtencion
import pe.saniape.app.data.staff.ProfesionalPlan
import pe.saniape.app.data.staff.RecetaAtencionRepo
import pe.saniape.app.data.staff.RecetaVinculada
import pe.saniape.app.data.staff.etiquetaRecetaVinculada
import pe.saniape.app.data.staff.RecetasStaffRepo
import pe.saniape.app.data.staff.nombreHojaReceta
import pe.saniape.app.data.staff.textoAtencionCompletada
import pe.saniape.app.data.staff.textoDarReceta
import pe.saniape.app.data.staff.textoOfertaReceta
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * "📝 Dar receta / indicaciones" después de completar una atención, para TODA la
 * app de staff: agenda, ficha, Sesiones. Quien completa llama a [ofrecer] SOLO
 * tras el éxito (el camino de completar no cambia: un viaje al servidor); el
 * [HostRecetaTrasAtencion], montado una vez en ClinicaConTabs, muestra la oferta
 * como una barra abajo (no un diálogo: no tapa el "¿Agendar la siguiente?" ni el
 * plan de la evaluación, y DALU completa decenas de sesiones al día) y abre la
 * emisión nativa prellenada. Los menús de cada atención usan [abrir] directo.
 */
object RecetaTrasAtencion {
    private val ofertaEstado = mutableStateOf<PrefillRecetaAtencion?>(null)
    private val abiertaEstado = mutableStateOf<PrefillRecetaAtencion?>(null)
    private val existenteEstado = mutableStateOf<Pair<PrefillRecetaAtencion, RecetaVinculada>?>(null)
    private val emitidasEstado = mutableStateOf(0)
    private val ocultadoresEstado = mutableStateOf(0)

    /** Cuántas pantallas completas (crear cita, consulta guiada…) están abiertas: la barra no va encima. */
    internal val ocultadores: Int get() = ocultadoresEstado.value
    internal fun ocultar() { ocultadoresEstado.value++ }
    internal fun mostrar() { ocultadoresEstado.value = (ocultadoresEstado.value - 1).coerceAtLeast(0) }

    /** Otra clínica (o salir): nada de la anterior queda ofrecido ni abierto. */
    fun reiniciar() {
        ofertaEstado.value = null
        abiertaEstado.value = null
        existenteEstado.value = null
    }

    /** La oferta en la barra (null = nada). */
    val oferta: PrefillRecetaAtencion? get() = ofertaEstado.value

    /** La receta que se está emitiendo (null = ninguna). */
    val abierta: PrefillRecetaAtencion? get() = abiertaEstado.value

    /** Sube con cada receta emitida desde aquí: las pantallas recargan su indicador. */
    val emitidas: Int get() = emitidasEstado.value

    /** Tras completar: la barra "¿Le dejas indicaciones…?". La nueva reemplaza a la anterior. */
    fun ofrecer(p: PrefillRecetaAtencion) { ofertaEstado.value = p }

    /** La atención ya tiene su hoja: se pregunta antes de emitir otra (como la web). */
    val existente: Pair<PrefillRecetaAtencion, RecetaVinculada>? get() = existenteEstado.value

    /**
     * Abre la emisión (desde la barra o desde el menú de una atención). Si la
     * atención ya tiene una hoja vigente ([yaTiene]), primero el aviso "Esta
     * atención ya tiene su hoja": 🖨 Ver / reimprimir · Emitir otra.
     */
    fun abrir(p: PrefillRecetaAtencion, yaTiene: RecetaVinculada? = null) {
        ofertaEstado.value = null
        if (yaTiene != null) existenteEstado.value = p to yaTiene
        else abiertaEstado.value = p
    }

    internal fun emitirOtra() {
        val e = existenteEstado.value ?: return
        existenteEstado.value = null
        abiertaEstado.value = e.first
    }

    internal fun cerrarAviso() { existenteEstado.value = null }

    fun descartar() { ofertaEstado.value = null }

    internal fun cerrar() { abiertaEstado.value = null }
    internal fun marcarEmitida() { emitidasEstado.value++ }
}

/** Vida de la oferta en pantalla: si no se usa, queda en el menú de la atención. */
internal const val VIDA_OFERTA_MS = 25_000L

/**
 * Montar en una pantalla COMPLETA (formulario que tapa la pantalla, no un
 * diálogo): mientras esté, la barra de la oferta no se dibuja encima.
 */
@Composable
fun OcultarOfertaReceta() {
    androidx.compose.runtime.DisposableEffect(Unit) {
        RecetaTrasAtencion.ocultar()
        onDispose { RecetaTrasAtencion.mostrar() }
    }
}

/** Pide el HTML imprimible de la receta y lo abre en el visor nativo. false si no se pudo. */
suspend fun abrirRecetaImpresa(acciones: AccionesNativas, recetaId: String, titulo: String): Boolean {
    val html = AtencionRepo.htmlImprimible("receta", recetaId) ?: return false
    acciones.abrirHtml(html, titulo)
    return true
}

/**
 * Host único (en ClinicaConTabs, encima de todo): la barra de la oferta, la
 * emisión y el "¿Imprimir ahora?" tras emitir.
 */
@Composable
fun HostRecetaTrasAtencion(
    ctx: ContextoStaff,
    /** Hay un flujo a pantalla completa abierto (agenda: crear cita, ▶ Atender): sin barra. */
    oculta: Boolean = false,
) {
    val modulos = ctx.modulosClinicos
    // Doble candado: sin el módulo (o sin permiso) no se muestra nada aunque
    // alguien hubiera llamado a ofrecer().
    val habilitado = modulos.recetas && ctx.puede("sesiones")
    val oferta = RecetaTrasAtencion.oferta?.takeIf { habilitado }
    val acciones = recordarAcciones()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    // Recién emitida: (id, título) para ofrecer imprimirla.
    var porImprimir by remember { mutableStateOf<Pair<String, String>?>(null) }
    var imprimiendo by remember { mutableStateOf(false) }

    // La última oferta mostrada: se sigue dibujando mientras la barra sale (si no, se vaciaría al irse).
    var mostrada by remember { mutableStateOf<PrefillRecetaAtencion?>(null) }
    LaunchedEffect(oferta) {
        if (oferta != null) mostrada = oferta
        visible = false
        if (oferta != null) {
            // Primero se lee el "✓ Sesión completada" del toast; después la oferta.
            delay(2_600)
            visible = true
            // Una oferta vieja no queda colgada (sigue en el menú de la atención).
            delay(VIDA_OFERTA_MS)
            if (RecetaTrasAtencion.oferta === oferta) RecetaTrasAtencion.descartar()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible && oferta != null && !oculta && RecetaTrasAtencion.ocultadores == 0,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            val p = mostrada ?: return@AnimatedVisibility
            BarraOfertaReceta(
                completada = textoAtencionCompletada(p),
                pregunta = textoOfertaReceta(modulos, p),
                boton = textoDarReceta(modulos),
                onDar = { RecetaTrasAtencion.abrir(p) },
                onDescartar = { RecetaTrasAtencion.descartar() },
            )
        }
    }

    RecetaTrasAtencion.abierta?.takeIf { habilitado }?.let { p ->
        EmisionRecetaAtencion(
            ctx = ctx, p = p,
            onCerrar = { RecetaTrasAtencion.cerrar() },
            onEmitida = { id ->
                RecetaTrasAtencion.cerrar()
                RecetaTrasAtencion.marcarEmitida()
                if (id.isNotBlank()) porImprimir = id to nombreHojaReceta(modulos)
            },
        )
    }

    // "Esta atención ya tiene su hoja": ver / reimprimir la que tiene, o emitir otra.
    RecetaTrasAtencion.existente?.takeIf { habilitado }?.let { (_, r) ->
        val c = Sania.colors
        AlertDialog(
            onDismissRequest = { if (!imprimiendo) RecetaTrasAtencion.cerrarAviso() },
            title = { Text("Esta atención ya tiene su hoja", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${etiquetaRecetaVinculada(r)}. Puedes verla o reimprimirla, o emitir otra " +
                        "(la anterior sigue vigente; si salió mal, anúlala en la web).",
                    color = c.textoSuave, fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(enabled = !imprimiendo, onClick = {
                    imprimiendo = true
                    scope.launch {
                        try {
                            if (!abrirRecetaImpresa(acciones, r.id, "${r.nombreHoja} ${r.numeroTexto}"))
                                Toaster.error("No se pudo abrir. Revisa tu conexión.")
                        } finally {
                            imprimiendo = false
                            RecetaTrasAtencion.cerrarAviso()
                        }
                    }
                }) { Text(if (imprimiendo) "Abriendo…" else "🖨 Ver / reimprimir", color = c.navy, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !imprimiendo, onClick = { RecetaTrasAtencion.emitirOtra() }) {
                    Text("Emitir otra", color = c.purple)
                }
            },
            containerColor = c.superficie,
        )
    }

    porImprimir?.let { (id, nombre) ->
        val c = Sania.colors
        AlertDialog(
            onDismissRequest = { if (!imprimiendo) porImprimir = null },
            title = { Text("🖨 ¿Imprimir ${nombre.lowercase()}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Quedó emitida y vinculada a esta atención. Puedes imprimirla ahora para entregarla, " +
                        "o después desde la ficha (pestaña 💊).",
                    color = c.textoSuave, fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(enabled = !imprimiendo, onClick = {
                    imprimiendo = true
                    scope.launch {
                        try {
                            if (!abrirRecetaImpresa(acciones, id, nombre)) Toaster.error("No se pudo abrir. Revisa tu conexión.")
                        } finally {
                            imprimiendo = false
                            porImprimir = null
                        }
                    }
                }) { Text(if (imprimiendo) "Abriendo…" else "🖨 Imprimir", color = c.navy, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !imprimiendo, onClick = { porImprimir = null }) { Text("Listo", color = c.textoSuave) }
            },
            containerColor = c.superficie,
        )
    }
}

@Composable
private fun BarraOfertaReceta(
    completada: String,
    pregunta: String,
    boton: String,
    onDar: () -> Unit,
    onDescartar: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier
            .padding(bottom = 90.dp, start = 16.dp, end = 16.dp)   // sobre la barra de tabs, como el toast
            .fillMaxWidth()
            .shadow(6.dp, forma)
            .clip(forma)
            .background(c.superficie)
            .border(1.dp, c.borde, forma)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(completada, color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(pregunta, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "✕", color = c.textoSuave, fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(onClick = onDescartar)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.navy)
                .clickable(onClick = onDar).padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) { Text(boton, color = c.sobreNavy, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}

/**
 * Carga lo que falta (equipo de prescriptores + sesión/diagnóstico de la
 * atención, en paralelo) y abre DialogoReceta prellenada y vinculada. Sin
 * equipo no se abre: un fallo de red NO es "nadie puede recetar".
 */
@Composable
private fun EmisionRecetaAtencion(
    ctx: ContextoStaff,
    p: PrefillRecetaAtencion,
    onCerrar: () -> Unit,
    onEmitida: (String) -> Unit,
) {
    var equipo by remember(p) { mutableStateOf<List<ProfesionalPlan>?>(null) }
    var prefill by remember(p) { mutableStateOf<PrefillRecetaAtencion?>(null) }
    LaunchedEffect(p) {
        coroutineScope {
            val e = async { RecetasStaffRepo.equipoPrescriptores(ctx.modulosClinicos.mapaReceta) }
            val pf = async { RecetaAtencionRepo.completarPrefill(p) }
            val eq = e.await()
            if (eq == null) {
                pf.cancel()
                Toaster.error("No se pudo cargar el equipo. Revisa tu conexión.")
                onCerrar()
                return@coroutineScope
            }
            prefill = pf.await()
            equipo = eq
        }
    }
    val eq = equipo
    val pf = prefill
    if (eq == null || pf == null) {
        val c = Sania.colors
        Dialog(onDismissRequest = onCerrar) {
            Row(
                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Preparando…", color = c.texto, fontSize = 14.sp)
            }
        }
        return
    }
    pe.saniape.app.ui.clinica.atencion.DialogoReceta(
        ctx = ctx,
        pacienteId = pf.pacienteId,
        profesionales = eq,
        diagnostico = pf.diagnostico,
        cie10 = pf.cie10,
        citaId = pf.citaId,
        tratamientoId = pf.tratamientoId,
        recetasOptIn = ctx.modulosClinicos.recetasOptIn,
        onCancelar = onCerrar,
        onEmitida = onEmitida,
        terapeutaSugerido = pf.terapeutaId,
        sesionId = pf.sesionId,
        medicacionRef = pf.medicacionRef,
        indicaciones = pf.indicaciones,
    )
}
