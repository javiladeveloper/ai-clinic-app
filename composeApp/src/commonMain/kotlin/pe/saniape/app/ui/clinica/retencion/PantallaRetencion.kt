package pe.saniape.app.ui.clinica.retencion

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToLong
import pe.saniape.app.data.staff.FiltrosLlamadas
import pe.saniape.app.data.staff.SatisfaccionPersona
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.FilaRetencion
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.ResumenRetencion
import pe.saniape.app.data.staff.RetencionRepo
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.puedeMarcarNoVolvio
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.PantallaCrearCita
import pe.saniape.app.ui.clinica.PrefillCita
import pe.saniape.app.ui.clinica.fisio.ModalNoVolvio
import pe.saniape.app.ui.clinica.pacientes.PantallaFichaPaciente
import pe.saniape.app.ui.clinica.servicios.ChipFiltro
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.proximaHoraEnPunto
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.data.staff.soles

/** Pestañas de Retención (las mismas que la web). */
private enum class VistaRet(val etiqueta: String) {
    LLAMAR("📋 Para llamar"), NO_VUELVEN("🔄 No vuelven"), CONTROLES("📅 Controles"),
    NUNCA("🆕 Nunca empezaron"), PRESUPUESTOS("🦷 Presupuestos"), CUOTAS("💳 Cuotas"),
}

/** Lo que las listas necesitan del exterior (ficha, agendar, cerrar, IA, contacto). */
class AccionesRet(
    val ctx: ContextoStaff,
    val acciones: AccionesNativas,
    /** Profesional sin permiso de pacientes: solo ve lo suyo (lo aplica la base). */
    val soloMios: Boolean,
    val recarga: Int,
    val onFicha: (String) -> Unit,
    val onAgendar: (FilaRetencion) -> Unit,
    val onAgendarControl: (FilaRetencion) -> Unit,
    val onCerrar: (FilaRetencion) -> Unit,
    val onIA: (nombre: String, telefono: String?, objetivo: String, detalle: String) -> Unit,
)

/**
 * 🔄 Retención (Más). Gemelo de /seguimiento en la web: a quién llamar y cómo va
 * la retención. Pestañas: Para llamar (filtros + exportar), No vuelven, Controles,
 * Nunca empezaron y, en clínicas dentales, Presupuestos y Cuotas. Plan Plus
 * (feature `recuperacion`); gestores con permiso "pacientes" o profesionales en
 * modo clínico (solo ven lo suyo). El servidor y la base validan igual.
 */
@Composable
fun PantallaRetencion(ctx: ContextoStaff, onSalir: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    val hoy = remember { hoyClinicaIso() }

    val esGestor = ctx.esGestor
    val tieneAcceso = esGestor || ctx.modoClinico
    val planOk = ctx.can("recuperacion")
    val soloMios = ctx.modoClinico && !esGestor && ctx.miTerapeutaId != null
    val activo = tieneAcceso && planOk
    val hayDental = ctx.mapaDental.activo

    var recarga by remember { mutableIntStateOf(0) }
    var resumen by remember { mutableStateOf<ResumenRetencion?>(null) }
    var cargandoResumen by remember { mutableStateOf(true) }
    // Números de las pestañas dentales: el del servidor (una llamada) y, cuando la
    // pestaña carga su detalle, el real. Cada uno por separado (uno no pisa al otro).
    var nPresupuestos by remember { mutableStateOf<Int?>(null) }
    var nCuotas by remember { mutableStateOf<Int?>(null) }
    // Los filtros de "Para llamar" viven acá: al cambiar de pestaña o volver de la
    // ficha, la lista queda como estaba.
    var filtrosLlamar by remember { mutableStateOf(FiltrosLlamadas()) }
    var busquedaLlamar by remember { mutableStateOf("") }
    var satisfaccion by remember { mutableStateOf<List<SatisfaccionPersona>>(emptyList()) }
    var vista by remember { mutableStateOf(VistaRet.LLAMAR) }

    // Sub-vistas encima de la lista (cada una trae su propio "atrás").
    var fichaAbierta by remember { mutableStateOf<pe.saniape.app.data.staff.PacienteStaff?>(null) }
    var agendando by remember { mutableStateOf<PrefillCita?>(null) }
    var cerrando by remember { mutableStateOf<FilaRetencion?>(null) }
    var guardandoCierre by remember { mutableStateOf(false) }
    var ia by remember { mutableStateOf<PedidoIA?>(null) }

    LaunchedEffect(activo, recarga) {
        if (!activo) return@LaunchedEffect
        try { resumen = RetencionRepo.resumen(hoy) }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { }
        cargandoResumen = false
        if (esGestor && hayDental && nPresupuestos == null && nCuotas == null) {
            RetencionRepo.conteosDental(hoy)?.let { (p, q) -> if (nPresupuestos == null) nPresupuestos = p; if (nCuotas == null) nCuotas = q }
        }
    }
    // Cómo califican al equipo: UNA carga por pantalla (no por pestaña ni por scroll).
    LaunchedEffect(activo, recarga) {
        if (!activo || !esGestor || !ctx.can("reportes")) return@LaunchedEffect
        try { satisfaccion = RetencionRepo.satisfaccion() }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { }
    }

    fun abrirFicha(id: String) {
        scope.launch {
            val p = try { PacientesRepo.porId(id) } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (_: Exception) { null }
            if (p != null) fichaAbierta = p else Toaster.error("No se pudo abrir la ficha")
        }
    }

    cerrando?.let { f ->
        ModalNoVolvio(
            nombreTratamiento = "${f.nombre}${f.servicio?.let { " · $it" }.orEmpty()}",
            futurasPendientes = if (f.proximaCita != null) 1 else 0,
            guardando = guardandoCierre,
            onCancelar = { if (!guardandoCierre) cerrando = null },
            onConfirmar = { d ->
                guardandoCierre = true
                scope.launch {
                    val r = try {
                        conIndicador(Gestion.GUARDANDO) {
                            PacientesRepo.marcarNoVolvio(f.tratamientoId, d.motivo, d.detalle, d.fecha, d.cancelarFuturas)
                        }
                    } finally { guardandoCierre = false }
                    if (r.registrada) {
                        Toaster.exito("${f.nombre}: tratamiento cerrado como \"No volvió\"")
                        cerrando = null; recarga++
                    } else Toaster.error(r.rechazo?.error ?: "No se pudo cerrar el tratamiento")
                }
            },
        )
    }
    ia?.let { DialogoRedactarIA(it, acciones, onCerrar = { ia = null }) }

    val tipoEntrada = when {
        ctx.flujo.usaConsulta -> "Consulta"
        ctx.flujo.usaEvaluacion -> "Evaluación"
        else -> "Sesión"
    }
    val acc = AccionesRet(
        ctx = ctx, acciones = acciones, soloMios = soloMios, recarga = recarga,
        onFicha = ::abrirFicha,
        onAgendar = { f ->
            agendando = PrefillCita(
                tipo = "Sesión", pacienteId = f.pacienteId, pacienteNombre = f.nombre,
                fecha = hoy, hora = proximaHoraEnPunto(), terapeutaId = f.terapeutaId, tratamientoId = f.tratamientoId,
            )
        },
        onAgendarControl = { f ->
            agendando = PrefillCita(
                tipo = tipoEntrada, pacienteId = f.pacienteId, pacienteNombre = f.nombre,
                fecha = pe.saniape.app.data.staff.fechaParaControlRet(f.proximoControl, hoy),
                hora = proximaHoraEnPunto(), terapeutaId = f.terapeutaId, tratamientoId = f.tratamientoId,
            )
        },
        onCerrar = { cerrando = it },
        onIA = { n, t, o, d -> ia = PedidoIA(n, t, o, d) },
    )

    val pestanas = buildList {
        add(VistaRet.LLAMAR to null)
        add(VistaRet.NO_VUELVEN to resumen?.noVuelven)
        add(VistaRet.CONTROLES to resumen?.controles)
        if (esGestor) add(VistaRet.NUNCA to resumen?.nuncaEmpezaron)
        if (esGestor && hayDental) add(VistaRet.PRESUPUESTOS to nPresupuestos)
        if (esGestor && hayDental && ctx.puede("pagos")) add(VistaRet.CUOTAS to nCuotas)
    }
    val visible = if (pestanas.any { it.first == vista }) vista else VistaRet.LLAMAR
    val porRecontactar = resumen?.let { it.noVuelven + it.controles }
    val term = ctx.terminologiaPaciente()

    // La lista queda compuesta DEBAJO de la ficha / "agendar": al volver conserva
    // filtros, página y posición.
    Box(Modifier.fillMaxSize()) {
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { onSalir() }.padding(vertical = 2.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Retención de $term", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            porRecontactar == null -> "A quién llamar y cómo va la retención"
                            porRecontactar > 0 -> "$porRecontactar por recontactar"
                            else -> "Nadie pendiente de recontactar hoy"
                        },
                        color = c.sobreNavy.copy(alpha = 0.75f), fontSize = Sania.txt.mini,
                    )
                }
            }

            when {
                !tieneAcceso -> MensajeCentro("🔒", "No tienes acceso a Retención", "Pídele acceso al administrador de la clínica.")
                !planOk -> MensajeCentro(
                    "💎", "Retención de pacientes",
                    "Detecta pacientes por abandonar, controles vencidos y a quién llamar hoy — y mide cuántos recuperas. Disponible en el plan Plus.",
                )
                else -> {
                    // Pestañas
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        pestanas.forEach { (v, n) ->
                            ChipFiltro(v.etiqueta + if ((n ?: 0) > 0) "  $n" else "", visible == v) { vista = v }
                        }
                    }
                    val kpis: @Composable () -> Unit = { if (ctx.can("reportes")) KpisRetencion(resumen, cargandoResumen) }
                    val pie: @Composable () -> Unit = { if (esGestor && ctx.can("reportes")) PanelSatisfaccion(satisfaccion) }
                    when (visible) {
                        VistaRet.LLAMAR -> TabLlamar(acc, resumen, esGestor, filtrosLlamar, { filtrosLlamar = it }, busquedaLlamar, { busquedaLlamar = it }, kpis, pie)
                        VistaRet.NO_VUELVEN -> TabNoVuelven(acc, resumen, kpis, pie)
                        VistaRet.CONTROLES -> TabControles(acc, kpis, pie)
                        VistaRet.NUNCA -> TabNuncaEmpezaron(acc, kpis, pie)
                        VistaRet.PRESUPUESTOS -> TabPresupuestos(acc, kpis, pie) { n -> nPresupuestos = n }
                        VistaRet.CUOTAS -> TabCuotas(acc, kpis, pie) { n -> nCuotas = n }
                    }
                }
            }
        }
    }
    fichaAbierta?.let { p ->
        CapaEncima {
            pe.saniape.app.tutoriales.PantallaTutorial("Ficha") {
                PantallaFichaPaciente(ctx = ctx, pacienteInicial = p, onCerrar = { fichaAbierta = null; recarga++ })
            }
        }
    }
    agendando?.let { pre ->
        CapaEncima {
            PantallaCrearCita(
                ctx = ctx, fechaInicial = pre.fecha, prefill = pre,
                onListo = { agendando = null; recarga++ }, onCancelar = { agendando = null },
            )
        }
    }
    }
}

/** Pantalla completa encima de la lista (la de abajo no recibe toques). */
@Composable
private fun CapaEncima(contenido: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Sania.colors.fondo)
            .pointerInput(Unit) { detectTapGestures { } },
    ) { contenido() }
}

@Composable
private fun MensajeCentro(emoji: String, titulo: String, texto: String) {
    val c = Sania.colors
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(emoji, fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text(titulo, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(texto, color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** "Cómo va": fichas compactas 2×2 (solo con reportes). */
@Composable
private fun KpisRetencion(r: ResumenRetencion?, cargando: Boolean) {
    val c = Sania.colors
    if (r == null) {
        if (cargando) CargandoLista(filas = 2, conAvatar = false, conMargen = false)
        return
    }
    val pct = r.pctCompletan
    @Composable fun ficha(m: Modifier, etq: String, valor: String, color: androidx.compose.ui.graphics.Color) {
        Column(
            m.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(etq.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(valor, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ficha(Modifier.weight(1f), "En riesgo", r.noVuelven.toString(), c.error)
            ficha(Modifier.weight(1f), "Dinero en riesgo", "S/ ${r.dineroRiesgo.roundToLong()}", c.pend)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ficha(Modifier.weight(1f), "Completan paquete", if (pct != null) "$pct%" else "—", c.ok)
            ficha(Modifier.weight(1f), "Recuperados (mes)", r.recuperadosMes.toString(), c.navy)
        }
        if (r.probableAbandono > 0) {
            Text("${r.probableAbandono} con $DIAS_PROBABLE_ABANDONO_TXT+ días sin venir", color = c.error, fontSize = 11.sp)
        }
    }
}

private const val DIAS_PROBABLE_ABANDONO_TXT = pe.saniape.app.data.staff.DIAS_PROBABLE_ABANDONO

/** Términos del rubro para "paciente(s)" en el título. */
private fun ContextoStaff.terminologiaPaciente(): String = "pacientes"

@Composable
internal fun CampoBusqueda(valor: String, onCambio: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio, placeholder = { Text(placeholder) }, singleLine = true,
        colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
    )
}

internal fun solesRet(m: Double): String = soles(m)
