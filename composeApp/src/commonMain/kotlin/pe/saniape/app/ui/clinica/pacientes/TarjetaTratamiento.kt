package pe.saniape.app.ui.clinica.pacientes

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import pe.saniape.app.ui.clinica.ChevronExpandible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import pe.saniape.app.data.staff.AccionSesion
import pe.saniape.app.data.staff.AccionTratamiento
import pe.saniape.app.data.staff.ACCIONES_ESTADO_SESION
import pe.saniape.app.data.staff.ROJAS_SESION
import pe.saniape.app.data.staff.ROJAS_TRATAMIENTO
import pe.saniape.app.data.staff.estadoDeAccionSesion
import pe.saniape.app.data.staff.repartirAccionesSesion
import pe.saniape.app.data.staff.repartirAccionesTratamiento
import pe.saniape.app.data.staff.textoAccionSesion
import pe.saniape.app.ui.componentes.BotonTarjeta
import pe.saniape.app.ui.componentes.DialogoConfirmar
import pe.saniape.app.ui.componentes.MenuMas
import pe.saniape.app.data.staff.AvisoRx
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.SesionFicha
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.EstadosColor
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.data.staff.FlujoClinica
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.data.staff.simboloMoneda

/** Aviso cuando se intenta registrar algo en la ficha de un paciente dado de baja. */
private fun avisoBaja(t: pe.saniape.app.data.staff.TerminologiaPaciente) = "${t.Paciente} dado de baja: reactívalo para registrar sesiones, citas o pagos."

/**
 * 📝 Receta / indicaciones en la tarjeta de un tratamiento (null = no aplica:
 * módulo apagado, sin permiso, especialidad que no receta). La arma la ficha.
 */
class RecetaEnTarjeta(
    /** "📝 Dar indicaciones" / "📝 Dar receta / indicaciones". */
    val textoDar: String,
    /** Recetas del paciente atadas a una atención (para el indicador por sesión). */
    val vinculadas: List<pe.saniape.app.data.staff.RecetaVinculada>,
    /** La cita de una sesión (si se agendó), para encontrar su receta. */
    val citaDeSesion: (String) -> String?,
    /** Abrir la emisión: de esa sesión, o del tratamiento entero (null). */
    val onDar: (SesionFicha?) -> Unit,
    /** Tras registrar el servicio único: la barra "¿Le dejas indicaciones?". */
    val onOfrecer: () -> Unit,
) {
    fun deSesion(ses: SesionFicha): pe.saniape.app.data.staff.RecetaVinculada? =
        pe.saniape.app.data.staff.recetaDeAtencion(vinculadas, citaDeSesion(ses.id), ses.id)
}

/**
 * Tarjeta de un tratamiento en la ficha: cabecera (procedimiento/estado/progreso/pago)
 * + al expandir, la lista de sus sesiones con acciones (Completar, estados) y "Dar de Alta".
 * Las escrituras van por endpoints (sesión/estado, tratamiento/alta) → reglas en la web.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaTratamiento(
    t: TratamientoPaciente,
    // Nombres que esta clínica le da a los pasos del recorrido.
    flujo: FlujoClinica = FlujoClinica(),
    verPagos: Boolean,
    esAdmin: Boolean,
    puedeSesiones: Boolean,
    pacienteId: String = "",
    /** Para no aprender técnicas que contengan su nombre (registrar_chips). */
    pacienteNombre: String? = null,
    puedeFotos: Boolean = false,   // feature fotosEvolutivas (Premium)
    puedeIA: Boolean = false,      // feature ia (Plus): sugerencia de sesión
    esDental: Boolean = false,     // tratamiento dental: "Piezas del plan" (solo odontología)
    /**
     * Tratamiento de FISIOTERAPIA (`citaEsFisio`): curva de dolor, aviso de renovación
     * y "🚪 No volvió". En false la tarjeta queda exactamente como antes.
     */
    esFisio: Boolean = false,
    /** "📦 Nuevo paquete" del aviso de renovación (abre el form prellenado). */
    onNuevoPaquete: (TratamientoPaciente) -> Unit = {},
    /** sesion_id de las citas futuras pendientes de ESTE tratamiento (de los hitos). */
    citasFuturasPendientes: List<String?> = emptyList(),
    recargaToken: Int = 0,         // cambia tras cualquier acción de la ficha → refresca las sesiones
    consultaDone: Boolean = false,   // para la barra de recorrido (de los hitos del paciente)
    evalDone: Boolean = false,
    citaConsulta: pe.saniape.app.data.staff.CitaHito? = null,   // detalle para la nube de la bolita
    citaEvaluacion: pe.saniape.app.data.staff.CitaHito? = null,
    onEditarCita: (pe.saniape.app.data.staff.CitaHito) -> Unit = {},
    onCompletarSesion: (SesionFicha, anterior: SesionFicha?, tecnicasSugeridas: String?, trat: TratamientoPaciente) -> Unit,   // abre modal (con sesión previa + técnicas + tratamiento)
    /** 📝 Receta / indicaciones (null = no aplica a este tratamiento). */
    receta: RecetaEnTarjeta? = null,
    onCambioRealizado: () -> Unit,               // refrescar ficha tras acción
    onEditar: (TratamientoPaciente) -> Unit = {},
    onAmpliar: (TratamientoPaciente) -> Unit = {},
    onCambiarEstadoTrat: (tratamientoId: String, estado: String) -> Unit = { _, _ -> },
    onCrearSesion: (TratamientoPaciente) -> Unit = {},
    onDerivar: (TratamientoPaciente) -> Unit = {},
    puedeDerivar: Boolean = false,
    onAgendarControl: (TratamientoPaciente) -> Unit = {},   // sin sesiones: agendar próximo control
    onRegistrarAtencion: (TratamientoPaciente) -> Unit = {},// sin sesiones: registrar medicación/receta
    /**
     * Ficha dada de baja (paciente Inactivo): se consulta todo, pero no se ofrece
     * registrar sesiones, cobros ni altas (como la web). El servidor además lo rechaza.
     */
    soloLectura: Boolean = false,
    /** Tras un alta correcta: la ficha ofrece la encuesta de satisfacción. */
    onAltaHecha: (TratamientoPaciente) -> Unit = {},
    /**
     * «✓ Dar de alta» pedido desde la agenda / sesiones (la sesión que cerró el
     * plan): al cambiar (> 0) abre ESTA misma confirmación de alta, con su saldo
     * y sus avisos. 0 = nada que abrir.
     */
    pedirAltaToken: Int = 0,
    /**
     * Evaluación psicológica (servicio con `tipo_clinico`): sus botones van en
     * [bloqueEvaluacionPsico] y no se ofrece "➕ Ampliar" (las citas se suman sin
     * cobrar más, con su botón propio). En false la tarjeta queda como siempre.
     */
    esEvaluacionPsico: Boolean = false,
    bloqueEvaluacionPsico: (@Composable () -> Unit)? = null,
    /** Citas de ESTE tratamiento (de los hitos): controles post-tratamiento y "En control". */
    citasTrat: List<pe.saniape.app.data.staff.CitaCtl> = emptyList(),
    /** ¿Ya llegaron los hitos (y con ellos [citasTrat])? Mientras no, no se afirma "Por hacer". */
    hitosCargados: Boolean = true,
) {
    val tpl = LocalTerminologiaPaciente.current
    // Permiso 'historia_clinica': sin él la tarjeta queda operativa (servicio,
    // profesional, progreso, sesiones con su estado, pagos, alta) y sin lo clínico:
    // diagnóstico, medicación, notas, EVA, piezas, fotos, sugerencia IA.
    val verClinico = pe.saniape.app.data.staff.LocalVerHistoriaClinica.current
    val puedeFotosEf = puedeFotos && verClinico
    val puedeIAEf = puedeIA && verClinico
    // Permisos EFECTIVOS: el rol decide (puede()), y la baja del paciente los apaga.
    val puedeSesionesEf = puedeSesiones && !soloLectura
    val puedeCobrarEf = verPagos && !soloLectura
    val c = Sania.colors
    val moneda = pe.saniape.app.ui.monedaUI()
    val scope = rememberCoroutineScope()
    var expandido by remember { mutableStateOf(false) }
    var sesiones by remember { mutableStateOf<List<SesionFicha>?>(null) }
    // Lo pagado NETO que cargó la sección de pagos (null = aún no): decide el "💳 Cobrar" por sesión.
    var pagadoCargado by remember(t.id) { mutableStateOf<Double?>(null) }
    var accionando by remember { mutableStateOf(false) }
    var cambioToken by remember { mutableStateOf(0) }   // recarga la sección de pagos tras cobros
    // «⏸ Suspender» / «✗ Cancelar» del «⋯» esperan confirmación (estado al que pasaría, o null).
    var confirmarEstadoTrat by remember { mutableStateOf<String?>(null) }
    // "🖨 Ver indicaciones" de una sesión en curso (un doble toque no abre dos visores).
    var abriendoReceta by remember { mutableStateOf(false) }
    val accionesNat = pe.saniape.app.ui.recordarAcciones()
    var eliminarAbierto by remember { mutableStateOf(false) }   // "🗑 Eliminar tratamiento" (Admin)
    // Sesiones objetivo de cada modal (o null).
    var editarSesion by remember { mutableStateOf<SesionFicha?>(null) }
    // Admin corrigiendo una sesión del historial (completada / tratamiento cerrado / ficha de baja).
    var corregirSesion by remember { mutableStateOf<SesionFicha?>(null) }
    var borrarSesion by remember { mutableStateOf<SesionFicha?>(null) }
    var reasignarSesion by remember { mutableStateOf<SesionFicha?>(null) }
    var cobrarSesion by remember { mutableStateOf<SesionFicha?>(null) }
    // SERVICIO ÚNICO: modal "Registrar atención" (nota de qué se hizo + cobro opcional).
    var registrarServicioAbierto by remember { mutableStateOf(false) }
    // Cambio de estado desde el menú ⋯ (sesión, estado) → modal con fecha/motivo.
    var cambioEstado by remember { mutableStateOf<Pair<SesionFicha, String>?>(null) }
    // Confirmación de alta (antes era 1 toque directo, fácil de tocar por error).
    var confirmarAlta by remember { mutableStateOf(false) }
    // Saldo al abrir la confirmación de alta (null = cargando o sin acceso a pagos)
    // y la señal para abrir "Registrar pago" en la sección de pagos.
    var saldoAlta by remember { mutableStateOf<Double?>(null) }
    var abrirPagoToken by remember { mutableStateOf(0) }
    LaunchedEffect(confirmarAlta) {
        if (!confirmarAlta) return@LaunchedEffect
        saldoAlta = null
        saldoAlta = runCatching {
            (t.montoAcordado - pe.saniape.app.data.staff.pagadoNeto(PacientesRepo.pagosDe(t.id))).coerceAtLeast(0.0)
        }.getOrNull()
    }
    if (eliminarAbierto) {
        DialogoEliminarTratamiento(
            t = t,
            onCerrar = { eliminarAbierto = false },
            onTermino = { eliminarAbierto = false; onCambioRealizado() },
        )
    }
    // «⏸ Suspender» / «✗ Cancelar»: antes de un toque, ahora con confirmación.
    confirmarEstadoTrat?.let { nuevo ->
        val cancelar = nuevo == "Cancelado"
        DialogoConfirmar(
            titulo = if (cancelar) "¿Cancelar este tratamiento?" else "¿Suspender este tratamiento?",
            texto = "«${t.procedimiento ?: "Tratamiento"}» quedará ${if (cancelar) "cancelado" else "suspendido"}. " +
                "El historial se conserva y puedes reactivarlo después.",
            textoConfirmar = if (cancelar) "Sí, cancelar" else "Sí, suspender",
            peligro = cancelar,
            onCancelar = { confirmarEstadoTrat = null },
            onConfirmar = { confirmarEstadoTrat = null; onCambiarEstadoTrat(t.id, nuevo) },
        )
    }
    // Fisio (M4): modal "No volvió" abierto (con cuántas citas/sesiones futuras tiene).
    var noVolvioFuturas by remember { mutableStateOf<Int?>(null) }
    var guardandoNoVolvio by remember { mutableStateOf(false) }

    val estado = EstadosColor.cita(t.estado)
    val terminado = t.estado == "Alta" || t.estado == "Cancelado" || t.estado == "Suspendido"
    // Alta pedida desde la agenda: la misma confirmación que el botón de abajo (y la
    // tarjeta se abre, para contar las sesiones pendientes en el aviso).
    LaunchedEffect(pedirAltaToken) {
        if (pedirAltaToken > 0 && !terminado && puedeSesionesEf) { expandido = true; confirmarAlta = true }
    }

    // Cargar sesiones al expandir (una vez). cargaFallo distingue "error de red" de "vacío real".
    var cargaFallo by remember { mutableStateOf(false) }
    LaunchedEffect(expandido) {
        if (expandido && sesiones == null) {
            runCatching { PacientesRepo.sesionesDe(t.id) }
                .onSuccess { sesiones = it; cargaFallo = false }
                .onFailure { cargaFallo = true }
        }
    }

    fun recargarSesiones() {
        scope.launch {
            // conIndicador: recargar la lista es la parte LENTA de cada gestión (borrar,
            // editar, cobrar…). Sin esto el indicador se apagaba al terminar la acción y
            // la espera larga quedaba muda: la pantalla parecía colgada.
            // CARGANDO: en sí misma es una lectura. Cuando acompaña a una escritura, la
            // gestión de esa escritura (Eliminando/Guardando) tiene prioridad en el rótulo.
            pe.saniape.app.ui.conIndicador(pe.saniape.app.ui.Gestion.CARGANDO) {
                runCatching { PacientesRepo.sesionesDe(t.id) }
                    .onSuccess { sesiones = it; cargaFallo = false }
                    .onFailure { cargaFallo = true }
            }
            cambioToken++          // fuerza que la sección de pagos se recargue
            onCambioRealizado()
        }
    }

    // Si la ficha se recargó por una acción EXTERNA a esta tarjeta (p. ej. crear una
    // sesión desde el modal de la ficha), y esta tarjeta ya tenía sus sesiones cargadas,
    // las volvemos a pedir para reflejar el cambio. Sin esto, la sesión recién creada no
    // aparecía hasta colapsar y volver a expandir. Se salta el primer render (sesiones ==
    // null → todavía no hay nada que refrescar) y NO llama a onCambioRealizado (evita bucles).
    LaunchedEffect(recargaToken) {
        if (sesiones != null) {
            runCatching { PacientesRepo.sesionesDe(t.id) }
                .onSuccess { sesiones = it; cargaFallo = false }
                .onFailure { cargaFallo = true }
        }
    }

    // Acento de color por tipo: sesiones/servicio = teal, consulta médica = morado.
    val acento = if (t.esConsulta && !t.esServicioUnico) c.purple else c.teal
    val cerrado = t.estado == "Alta" || t.estado == "Cancelado" || t.estado == "Suspendido"
    val tratEnControl = pe.saniape.app.data.staff.enControl(t, citasTrat)

    // La barra de acento se DIBUJA detrás (drawBehind), no es un Box con
    // fillMaxHeight dentro de un Row con height(IntrinsicSize.Min): las medidas
    // intrínsecas revientan con cualquier LazyRow/BoxWithConstraints adentro, y
    // la galería de fotos de las sesiones es una LazyRow → al expandir un
    // paquete con fotos la app se cerraba (QA, 2026-10-08, desde que 2.29.0
    // guarda las fotos al completar desde la agenda).
    val colorAcento = if (cerrado) c.borde else acento
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .drawBehind { drawRect(colorAcento, size = Size(Sania.dim.acento.toPx(), size.height)) }
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)),
    ) {
        // Espacio de la barra de acento lateral (tipo de tratamiento)
        Spacer(Modifier.width(Sania.dim.acento))

      Column(Modifier.fillMaxWidth().padding(Sania.dim.tarjeta).animateContentSize()) {
        // Cabecera tocable (expande/colapsa). La barra de recorrido va aparte para que
        // tocar una bolita NO expanda la tarjeta (abre su nube de referencia).
        Column(Modifier.fillMaxWidth().clickable { expandido = !expandido }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val esUnid = t.tipo == pe.saniape.app.data.staff.TipoTratamiento.UNIDADES
                    Text("${if (esUnid) "🔢" else if (t.esServicioUnico) "✨" else if (t.esConsulta) "🩺" else "📦"} ${t.procedimiento ?: "Tratamiento"}",
                        color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                    // Unidades: "1500 folículos × S/ 1.2" bajo el nombre (como el badge web).
                    if (esUnid && t.cantidadUnidades != null) {
                        Text("${t.cantidadUnidades} ${t.unidadLabel ?: "unidades"}${t.precioUnitario?.let { " × ${simboloMoneda(moneda)} $it" } ?: ""}",
                            color = c.teal, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 1.dp))
                    }
                    val sub = listOfNotNull(t.especialidadNombre, t.terapeutaNombre?.let { "con $it" }).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
                    // El diagnóstico bajo el nombre: con dos tratamientos del mismo
                    // servicio es lo que dice cuál es cuál (28/09/2026). Las consultas
                    // ya lo muestran abajo, con su medicación.
                    if (verClinico && !t.esConsulta && !t.esServicioUnico) t.diagnostico?.takeIf { it.isNotBlank() }?.let {
                        Text("📋 $it", color = c.info, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    // M4: por qué y cuándo se cerró por abandono (solo si se marcó así).
                    if (pe.saniape.app.data.staff.esNoVolvio(t.estado, t.noVolvio)) {
                        val cuando = t.cerradoAt?.take(10)?.split("-")?.takeIf { it.size == 3 }
                            ?.let { (a, m, d) -> "$d/$m/$a" }
                        Text(listOfNotNull("🚪 No volvió", t.motivoCierre, cuando).joinToString(" · "),
                            color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
                // Servicio único / unidades realizado con controles pendientes: sigue
                // abierto (Activo) y se dice "En control" (decisión del dueño 2026-10-08).
                if (tratEnControl) {
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.infoBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("🔁 En control", color = c.info, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(t.estado ?: "—", color = estado.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                ChevronExpandible(expandido)
            }

            // CONSULTA (medicina/nutrición): diagnóstico + medicación + próximo control.
            // SERVICIO ÚNICO (blanqueamiento): solo diagnóstico (medicación/control no aplican).
            if (t.esConsulta || t.esServicioUnico) {
                if (verClinico) t.diagnostico?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp)); ChipInfo("📋", it, c.info, c.infoBg)
                }
                if (!t.esServicioUnico) {
                    if (verClinico) t.medicacion?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp)); ChipInfo("💊", it, c.purple, c.purpleBg)
                    }
                    t.proximoControl?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp)); ChipInfo("📅 Próximo control", it, c.navy, c.chipBg)
                    }
                }
            }
        }

        // Barra de recorrido (fuera del clickable: las bolitas abren su nube de referencia).
        if (t.estado == "Activo" || t.estado == "Completado" || t.estado == "Alta") {
            Spacer(Modifier.height(10.dp))
            BarraRecorrido(
                trat = t, flujo = flujo, consultaDone = consultaDone, evalDone = evalDone, citasTrat = citasTrat,
                citasCargadas = hitosCargados,
                citaConsulta = citaConsulta, citaEvaluacion = citaEvaluacion,
                puedePagos = verPagos, puedeGestionar = puedeSesionesEf, expandido = expandido, onEditarCita = onEditarCita,
                onToggleSesiones = { expandido = !expandido },
                onColapsarTarjeta = { expandido = false },
                onAgendarControl = {
                    if (soloLectura) pe.saniape.app.ui.Toaster.error(avisoBaja(tpl)) else onAgendarControl(t)
                },
                // Servicio único: abre el modal local (nota + cobro). Consulta médica: el de siempre.
                onRegistrarAtencion = {
                    if (soloLectura) pe.saniape.app.ui.Toaster.error(avisoBaja(tpl))
                    else if (t.esServicioUnico) registrarServicioAbierto = true else onRegistrarAtencion(t)
                },
                onRevertirServicio = {
                    if (soloLectura) { pe.saniape.app.ui.Toaster.error(avisoBaja(tpl)); return@BarraRecorrido }
                    if (accionando) return@BarraRecorrido
                    accionando = true
                    scope.launch {
                        val ok = PacientesRepo.revertirServicio(t.id)
                        if (ok) pe.saniape.app.ui.Toaster.exito("Servicio revertido") else pe.saniape.app.ui.Toaster.error("No se pudo revertir")
                        accionando = false; onCambioRealizado()
                    }
                },
                onDarAlta = {
                    if (soloLectura) { pe.saniape.app.ui.Toaster.error(avisoBaja(tpl)); return@BarraRecorrido }
                    if (!accionando) confirmarAlta = true
                },
            )
        }

        // Resto de la zona-resumen, también tocable para expandir.
        Column(Modifier.fillMaxWidth().clickable { expandido = !expandido }) {
            if (!t.esConsulta && !t.esServicioUnico && t.totalSesiones > 0) {
                Spacer(Modifier.height(Sania.dim.sm))
                val frac = (t.sesionesCompletadas.toFloat() / t.totalSesiones).coerceIn(0f, 1f)
                // Progreso de SESIONES en navy (el verde se reserva a pago/completado).
                pe.saniape.app.ui.clinica.BarraProgreso(frac, color = c.navy)
            }
            if (!expandido) {
                Spacer(Modifier.height(8.dp))
                Text(if (t.esConsulta || t.esServicioUnico) "Toca para ver detalle y pagos" else "Toca para ver sesiones y pagos",
                    color = c.textoSuave, fontSize = 10.sp)
            }
        }

        // 🧠 Evaluación psicológica: el espacio de trabajo y "+ Agregar cita de evaluación".
        bloqueEvaluacionPsico?.let { bloque -> Column(Modifier.tourAncla("ficha.evaluacion_psico")) { bloque() } }

        // Fisio (M3): al paquete le quedan 1–2 sesiones → ofrecer renovar.
        val restantesRenovacion = if (esFisio)
            pe.saniape.app.data.staff.avisoRenovacion(t.estado, t.modalidad, t.totalSesiones, t.sesionesCompletadas)
        else null
        // El aviso ya muestra «➕ Ampliar»: el ⋯ del tratamiento no lo repite.
        val avisoRenovacionConAmpliar = restantesRenovacion != null && puedeSesionesEf
        if (esFisio) {
            restantesRenovacion
                ?.let { restantes ->
                    Spacer(Modifier.height(8.dp))
                    pe.saniape.app.ui.clinica.fisio.AvisoRenovacion(
                        restantes = restantes,
                        onAmpliar = if (puedeSesionesEf) ({ onAmpliar(t) }) else null,
                        onNuevoPaquete = if (puedeSesionesEf && t.procedimientoId != null) ({ onNuevoPaquete(t) }) else null,
                    )
                }
        }

        // Acciones del tratamiento: a la vista solo lo del MOMENTO del plan (en curso:
        // Agendar · Editar; terminado: Dar de alta · Ampliar · Editar; pausado:
        // Reactivar) y el resto en «⋯ Más», agrupado (repartirAccionesTratamiento,
        // gemela de la web). Antes: la columna «⋯ Opciones del tratamiento» que crecía
        // dentro de la tarjeta, «📅 + Agendar sesión» aparte y «✓ Dar de alta» abajo.
        // Cada acción conserva su condición y su handler de siempre.
        val accionesTrat = buildList {
            if (puedeSesionesEf) {
                // Agendar: tratamientos por sesiones activos (la consulta agenda su control en la nube).
                if (!t.esConsulta && t.estado == "Activo") add(AccionTratamiento.Agendar)
                // Editar solo en planes por sesiones: precio/n° sesiones. En consultas
                // todo (incl. costo) se registra en "Registrar atención" (paso Control).
                if (!t.esConsulta) {
                    add(AccionTratamiento.Editar)
                    // Evaluación psicológica: las citas se suman sin cobrar (botón propio).
                    // Con el aviso de renovación a la vista, su «➕ Ampliar» no se repite en el ⋯.
                    if (!esEvaluacionPsico && !avisoRenovacionConAmpliar) add(AccionTratamiento.Ampliar)
                }
                // 📝 Receta / indicaciones para el tratamiento (gemelo del "📝 Receta" de la web).
                if (receta != null) add(AccionTratamiento.Receta)
                // Derivar este tratamiento a otra especialidad (feature Premium).
                if (puedeDerivar && t.estado == "Activo") add(AccionTratamiento.Derivar)
                when (t.estado) {
                    "Activo" -> {
                        add(AccionTratamiento.Suspender)
                        // Fisio (M4): cierre por abandono con motivo. No dental (libera piezas),
                        // ni Unidades/Consulta (no tienen "sesiones que faltan").
                        if (esFisio && !esDental && pe.saniape.app.data.staff.puedeMarcarNoVolvio(t.estado, t.modalidad, t.totalSesiones))
                            add(AccionTratamiento.NoVolvio)
                        add(AccionTratamiento.Cancelar)
                    }
                    "Suspendido", "Cancelado" -> add(AccionTratamiento.Reactivar)
                }
                // Eliminar (borrado lógico): solo el Admin, como la ficha web.
                if (esAdmin) add(AccionTratamiento.Eliminar)
                // Dar de alta (con confirmación) mientras siga en curso. El servicio único,
                // una vez realizado (o En control: el alta cancela los controles que faltan).
                if (!terminado && (!t.esServicioUnico || t.estado == "Completado" || tratEnControl)) add(AccionTratamiento.Alta)
            } else if (soloLectura && esAdmin && puedeSesiones && !t.esConsulta) {
                // Ficha dada de baja: nadie registra nada nuevo, pero el ADMIN puede CORREGIR
                // el tratamiento (el servidor valida el rol). Los demás roles no ven nada nuevo.
                add(AccionTratamiento.Editar)
            }
        }
        val planTrat = repartirAccionesTratamiento(
            pe.saniape.app.data.staff.TratamientoParaTerminar(t.estado, t.totalSesiones, t.sesionesCompletadas),
            accionesTrat,
        )
        fun textoTrat(a: AccionTratamiento, aLaVista: Boolean): String = when (a) {
            AccionTratamiento.Agendar -> "📅 Agendar sesión"
            AccionTratamiento.Editar -> when {
                !puedeSesionesEf -> "✏ Corregir tratamiento"
                aLaVista -> "✏ Editar"
                else -> "✏ Editar tratamiento"
            }
            AccionTratamiento.Ampliar -> if (aLaVista) "➕ Ampliar" else "➕ Ampliar (más sesiones)"
            AccionTratamiento.SesionPasada -> "🗂 Sesión pasada"
            AccionTratamiento.Alta -> if (aLaVista) "✓ Dar de alta" else "✓ Dar de alta este tratamiento"
            AccionTratamiento.Reactivar -> "↻ Reactivar"
            AccionTratamiento.Suspender -> "⏸ Suspender"
            AccionTratamiento.NoVolvio -> "🚪 No volvió"
            AccionTratamiento.Cancelar -> "✗ Cancelar tratamiento"
            AccionTratamiento.Receta -> receta?.textoDar ?: "📝 Receta"
            AccionTratamiento.Derivar -> "↗ Derivar a otra especialidad"
            AccionTratamiento.Eliminar -> "🗑 Eliminar tratamiento"
        }
        fun ejecutarTrat(a: AccionTratamiento) {
            when (a) {
                AccionTratamiento.Agendar -> onCrearSesion(t)
                AccionTratamiento.Editar -> onEditar(t)
                AccionTratamiento.Ampliar -> onAmpliar(t)
                AccionTratamiento.SesionPasada -> Unit
                // La tarjeta se abre para contar las sesiones pendientes en el aviso del alta.
                AccionTratamiento.Alta -> if (!accionando) { expandido = true; confirmarAlta = true }
                AccionTratamiento.Reactivar -> onCambiarEstadoTrat(t.id, "Activo")
                // Suspender y cancelar se confirman (antes se ejecutaban de un toque).
                AccionTratamiento.Suspender -> confirmarEstadoTrat = "Suspendido"
                AccionTratamiento.Cancelar -> confirmarEstadoTrat = "Cancelado"
                AccionTratamiento.NoVolvio -> {
                    if (accionando) return
                    scope.launch {
                        // Cuántas citas/sesiones futuras pendientes tiene (una sesión con
                        // su cita cuenta una vez). Las sesiones se piden solo si la
                        // tarjeta no las tenía cargadas.
                        val lista = sesiones ?: runCatching { PacientesRepo.sesionesDe(t.id) }.getOrNull().orEmpty()
                        val hoy = pe.saniape.app.data.staff.hoyClinicaIso()
                        val conCita = citasFuturasPendientes.filterNotNull().toSet()
                        noVolvioFuturas = citasFuturasPendientes.size +
                            lista.count { it.pendiente && it.fecha >= hoy && it.id !in conCita }
                    }
                }
                AccionTratamiento.Receta -> receta?.onDar?.invoke(null)
                AccionTratamiento.Derivar -> onDerivar(t)
                // Antes de eliminar se decide qué pasa con su dinero (caja y saldo a favor).
                AccionTratamiento.Eliminar -> eliminarAbierto = true
            }
        }
        if (planTrat.visibles.isNotEmpty() || planTrat.menu.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                planTrat.primaria?.let { a ->
                    BotonTarjeta(textoTrat(a, true), colorAccionTrat(a), !accionando, lleno = true) { ejecutarTrat(a) }
                }
                planTrat.secundarias.forEach { a ->
                    BotonTarjeta(textoTrat(a, true), colorAccionTrat(a), !accionando) { ejecutarTrat(a) }
                }
                MenuMas(
                    grupos = planTrat.menu,
                    texto = { textoTrat(it, false) },
                    onAccion = { ejecutarTrat(it) },
                    rojo = { it in ROJAS_TRATAMIENTO },
                    habilitado = !accionando,
                    descripcion = "Más acciones del tratamiento",
                )
            }
        }

        // Estado de pago
        // Sesión suelta: la cuenta en vivo (lo atendido), no el estado guardado.
        val estadoPagoMostrado = t.estadoPagoVivo(pagadoCargado)
        if (verPagos && estadoPagoMostrado != null) {
            val pago = EstadosColor.cita(when (estadoPagoMostrado) { "Pagado" -> "Confirmada"; "Parcial" -> "Pendiente"; else -> "Cancelada" })
            Spacer(Modifier.height(6.dp))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(pago.bg)
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("Pago: $estadoPagoMostrado", color = pago.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Contenido expandido: sesiones + acciones ──
        if (expandido) {
            Spacer(Modifier.height(Sania.dim.md))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Spacer(Modifier.height(Sania.dim.md))

            // Las Consultas (especialidad sin sesiones) y el SERVICIO ÚNICO no listan sesiones;
            // solo pagos + fotos + alta (el servicio único, una vez realizado).
            if (t.esConsulta || t.esServicioUnico) {
                Text(
                    if (t.esServicioUnico) "Servicio único — se registra desde el paso “Por hacer” del recorrido."
                    else "Atención sin sesiones.",
                    color = c.textoSuave, fontSize = 12.sp,
                )
                // Odontología (servicio único dental: profilaxis…): sus piezas también.
                if (verClinico && esDental && pacienteId.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    pe.saniape.app.ui.clinica.odontologia.PlanPiezas(
                        pacienteId = pacienteId, tratamientoId = t.id, sesiones = emptyList(),
                        recargaToken = recargaToken + cambioToken,
                    )
                }
                // Fotos evolutivas ARRIBA de pagos (el antes/después es parte de la atención,
                // no del cobro) — también en consultas/servicios (documentan resultados).
                if (puedeFotosEf && pacienteId.isNotBlank()) {
                    GaleriaFotos(pacienteId = pacienteId, tratamientoId = t.id, sesiones = emptyList())
                }
                if (verPagos) {
                    Spacer(Modifier.height(Sania.dim.md))
                    SeccionPagos(t = t, esAdmin = esAdmin, recargaToken = cambioToken, onCambio = { recargarSesiones() },
                        soloLectura = soloLectura, pacienteId = pacienteId, abrirRegistro = abrirPagoToken)
                }
                // «✓ Dar de alta» ya no va abajo: está en las acciones de arriba (a la
                // vista cuando terminó; si no, en el «⋯»), también sin sesiones
                // (2026-10-02: "en algunos procedimientos no encontré dar de alta").
            } else if (cargaFallo) {
                // La carga falló (timeout/red). NO decir "sin sesiones": ofrecer reintentar.
                BloqueReintentar("No se pudieron cargar las sesiones.") { sesiones = null; recargarSesiones() }
            } else when (val s = sesiones) {
                null -> Box(Modifier.fillMaxWidth().padding(Sania.dim.md), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                }
                else -> {
                    // Fisioterapia (M1): curva del dolor EVA por sesión, solo si hay datos.
                    if (esFisio && verClinico) {
                        val puntos = pe.saniape.app.data.staff.curvaDolor(s)
                        if (puntos.isNotEmpty()) {
                            pe.saniape.app.ui.clinica.fisio.CurvaDolor(puntos)
                            Spacer(Modifier.height(Sania.dim.sm))
                        }
                    }
                    // Odontología: arriba de las sesiones, qué piezas cubre el plan y
                    // cuáles ya se hicieron (y en qué sesión). Otros rubros: nada.
                    if (verClinico && esDental && pacienteId.isNotBlank()) {
                        pe.saniape.app.ui.clinica.odontologia.PlanPiezas(
                            pacienteId = pacienteId, tratamientoId = t.id, sesiones = s,
                            recargaToken = recargaToken + cambioToken,
                        )
                    }
                    if (s.isEmpty() && t.sesionesCompletadas > 0) {
                        // Inconsistencia: el contador dice que hay sesiones pero la lista vino vacía
                        // (casi siempre un fallo de carga). Ofrecer reintentar en vez de mentir.
                        BloqueReintentar("El contador indica ${t.sesionesCompletadas} sesión(es), pero no se listaron.") {
                            sesiones = null; recargarSesiones()
                        }
                    } else if (s.isEmpty()) {
                        Text("Sin sesiones registradas.", color = c.textoSuave, fontSize = 12.sp)
                    } else {
                        // Orden: PENDIENTES (por realizar) por fecha+hora ascendente (lo que toca
                        // primero arriba) y el RESTO (completadas/anuladas) por número desc. Así, si
                        // el fisio llena varias rápido con fechas distintas, se ven en orden real sin
                        // renumerar (el #N se conserva). Respeta reprogramar (cambia la fecha → se
                        // reubica) y no toca el máximo de sesiones del paquete.
                        val esPend = { e: String -> e == "Planificada" || e == "En progreso" || e == "Reprogramada" }
                        val ordenadas = run {
                            val (pend, resto) = s.partition { esPend(it.estado) }
                            pend.sortedBy { it.fecha + (it.hora ?: "") } +
                                resto.sortedByDescending { it.numero }
                        }
                        ordenadas.forEach { ses ->
                            // ¿La sesión inmediatamente anterior (por número) dejó RX pendiente?
                            val anteriorSes = s.filter { it.numero < ses.numero }.maxByOrNull { it.numero }
                            // Admin + registro cerrado → corrección (también en una ficha de baja).
                            val correccion = esAdmin && puedeSesiones &&
                                pe.saniape.app.data.staff.esCorreccionSesion(esAdmin, ses.estado, t.estado, soloLectura)
                            FilaSesion(
                                ses = ses, verCosto = verPagos, puedeSesiones = puedeSesionesEf,
                                puedeCorregir = correccion,
                                puedePagos = puedeCobrarEf, esAdmin = esAdmin, accionando = accionando,
                                sinSaldo = t.sinSaldoCon(pagadoCargado),
                                avisoRxPrevia = ses.pendiente && AvisoRx.dejoRx(anteriorSes),
                                onCompletar = {
                                    // Sesión anterior (numero-1) como referencia de evolución en el modal.
                                    val anterior = s.filter { it.numero < ses.numero }.maxByOrNull { it.numero }
                                    onCompletarSesion(ses, anterior, t.tecnicasSugeridas, t)
                                },
                                // Reprogramada / No asistió / Cancelada / Otro: primero el modal
                                // (fecha+hora y motivo). Antes se mandaba en un toque, sin nada.
                                onEstado = { nuevo ->
                                    if (!accionando) cambioEstado = ses to nuevo
                                },
                                onEditar = {
                                    if (correccion) corregirSesion = ses else editarSesion = ses
                                },
                                onRevertir = {
                                    if (accionando) return@FilaSesion
                                    accionando = true
                                    scope.launch {
                                        pe.saniape.app.ui.conIndicador(pe.saniape.app.ui.Gestion.ACTUALIZANDO) {
                                            PacientesRepo.revertirSesion(ses.id)
                                        }
                                        accionando = false; recargarSesiones()
                                    }
                                },
                                onBorrar = { borrarSesion = ses },
                                onReasignar = { reasignarSesion = ses },
                                onCobrar = { cobrarSesion = ses },
                                // 📝 Receta / indicaciones de la sesión atendida (o verla si ya tiene).
                                textoReceta = receta?.textoDar?.takeIf { ses.estado == "Completada" && !soloLectura },
                                recetaSesion = receta?.deSesion(ses),
                                onReceta = { receta?.onDar?.invoke(ses) },
                                onVerReceta = { r ->
                                    if (!abriendoReceta) {
                                        abriendoReceta = true
                                        scope.launch {
                                            try {
                                                if (!pe.saniape.app.ui.clinica.recetas.abrirRecetaImpresa(
                                                        accionesNat, r.id, "${r.nombreHoja} ${r.numeroTexto}"))
                                                    pe.saniape.app.ui.Toaster.error("No se pudo abrir. Revisa tu conexión.")
                                            } finally { abriendoReceta = false }
                                        }
                                    }
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // Sugerencia IA para la próxima sesión (Plus) — solo en tratamientos activos.
                    if (puedeIAEf && !terminado) {
                        Spacer(Modifier.height(Sania.dim.sm))
                        SugerenciaSesionIA(tratamientoId = t.id)
                    }

                    // Fotos evolutivas (antes/durante/después) ARRIBA de pagos — feature Premium.
                    if (puedeFotosEf && pacienteId.isNotBlank()) {
                        GaleriaFotos(pacienteId = pacienteId, tratamientoId = t.id, sesiones = s)
                    }

                    // Pagos (solo con permiso): acordado/pagado/saldo + registrar/editar/borrar.
                    if (verPagos) {
                        Spacer(Modifier.height(Sania.dim.md))
                        SeccionPagos(t = t, esAdmin = esAdmin, recargaToken = cambioToken, onCambio = { recargarSesiones() },
                            soloLectura = soloLectura, pacienteId = pacienteId, abrirRegistro = abrirPagoToken,
                            onPagado = { pagadoCargado = it })
                    }
                    // (Dar de alta: en las acciones de arriba, con su confirmación.)
                }
            }
        }
      } // cierre Column del contenido
    } // cierre Row de la tarjeta (acento + contenido)

    // ── Modales de acciones de sesión ──
    cambioEstado?.let { (ses, nuevo) ->
        ModalEstadoSesion(
            numero = ses.numero, estado = nuevo,
            fechaInicial = ses.fecha, horaInicial = ses.hora,
            guardando = accionando,
            onCancelar = { if (!accionando) cambioEstado = null },
            onConfirmar = { motivo, fecha, hora ->
                if (accionando) return@ModalEstadoSesion
                accionando = true
                scope.launch {
                    // Mismo endpoint que la web (sync de cita + serie + contador + comisión).
                    val r = PacientesRepo.cambiarEstadoSesionDetalle(ses.id, nuevo, motivo = motivo, fecha = fecha, hora = hora)
                    accionando = false
                    if (r.registrada) {
                        cambioEstado = null
                        if (!r.encolada) pe.saniape.app.ui.Toaster.exito(
                            if (nuevo == "Reprogramada") "Sesión reprogramada al $fecha ${hora?.let { pe.saniape.app.ui.hora12(it) } ?: ""}".trim()
                            else "Sesión marcada como $nuevo"
                        )
                        recargarSesiones()
                    } else {
                        // Sin cupo, ficha dada de baja…: el modal queda abierto para corregir.
                        pe.saniape.app.ui.Toaster.error(r.rechazo?.error ?: "No se pudo actualizar la sesión")
                    }
                }
            },
        )
    }
    if (confirmarAlta) {
        DialogoConfirmarAlta(
            // Si las sesiones no se cargaron (tarjeta sin expandir), mensaje genérico.
            sesionesPendientes = sesiones?.count { it.pendiente },
            saldo = saldoAlta,
            controlesPendientes = if (t.esServicioUnico) pe.saniape.app.data.staff.resumenControles(citasTrat).pendientes else 0,
            onRegistrarPago = if (verPagos && !soloLectura) {
                { confirmarAlta = false; expandido = true; abrirPagoToken++ }
            } else null,
            onCancelar = { confirmarAlta = false },
            onConfirmar = {
                confirmarAlta = false
                if (accionando) return@DialogoConfirmarAlta
                accionando = true
                scope.launch {
                    val ok = PacientesRepo.darDeAlta(t.id)
                    accionando = false
                    if (ok) {
                        pe.saniape.app.ui.Toaster.exito("Tratamiento dado de alta")
                        onAltaHecha(t)
                    } else pe.saniape.app.ui.Toaster.error("No se pudo dar de alta")
                    onCambioRealizado()
                }
            },
        )
    }
    // Fisio (M4) · "No volvió": cierre por abandono (misma acción canónica que la web).
    noVolvioFuturas?.let { futuras ->
        pe.saniape.app.ui.clinica.fisio.ModalNoVolvio(
            nombreTratamiento = t.procedimiento ?: "El tratamiento",
            futurasPendientes = futuras,
            guardando = guardandoNoVolvio,
            onCancelar = { noVolvioFuturas = null },
            onConfirmar = { d ->
                if (guardandoNoVolvio) return@ModalNoVolvio
                guardandoNoVolvio = true
                scope.launch {
                    val r = PacientesRepo.marcarNoVolvio(t.id, d.motivo, d.detalle, d.fecha, d.cancelarFuturas)
                    guardandoNoVolvio = false
                    if (r.registrada) {
                        noVolvioFuturas = null
                        if (!r.encolada) pe.saniape.app.ui.Toaster.exito("Tratamiento cerrado: no volvió. Puedes reactivarlo si regresa.")
                        // Pudo cancelar sesiones futuras: la recarga de la ficha (recargaToken)
                        // vuelve a leer las sesiones si la tarjeta las tenía cargadas.
                        onCambioRealizado()
                    } else {
                        pe.saniape.app.ui.Toaster.error(r.rechazo?.error ?: "No se pudo cerrar el tratamiento")
                    }
                }
            },
        )
    }
    editarSesion?.let { ses ->
        ModalEditarSesion(ses, verClinico = verClinico, onCancelar = { editarSesion = null }, onGuardar = { fecha, hora, dur, costo, notas ->
            editarSesion = null
            scope.launch {
                pe.saniape.app.ui.conIndicador(pe.saniape.app.ui.Gestion.ACTUALIZANDO) {
                    PacientesRepo.editarSesion(ses.id, fecha, hora, dur, costo, if (verClinico) notas.orEmpty() else null)
                }
                recargarSesiones()
            }
        })
    }
    corregirSesion?.let { ses ->
        ModalCorregirSesion(
            ses = ses, esFisio = esFisio, verClinico = verClinico, guardando = accionando,
            onCancelar = { if (!accionando) corregirSesion = null },
            onGuardar = { f ->
                if (accionando) return@ModalCorregirSesion
                accionando = true
                scope.launch {
                    val completada = ses.estado == "Completada"
                    val r = pe.saniape.app.ui.conIndicador(pe.saniape.app.ui.Gestion.ACTUALIZANDO) {
                        PacientesRepo.corregirSesion(
                            ses.id, f.fecha, f.hora, f.duracion, f.costo, f.notas.takeIf { verClinico },
                            mejorias = if (completada && verClinico) f.mejorias else null,
                            dolorInicio = f.dolorInicio, dolorFin = f.dolorFin, conEva = completada && esFisio && verClinico,
                            terapeutaId = f.terapeutaId,
                        )
                    }
                    accionando = false
                    if (r.registrada) {
                        corregirSesion = null
                        if (!r.encolada) pe.saniape.app.ui.Toaster.exito("Sesión corregida")
                        recargarSesiones()
                    } else {
                        // Sin permiso, sin conexión definitiva…: el modal queda abierto.
                        pe.saniape.app.ui.Toaster.error(r.rechazo?.error ?: "No se pudo corregir la sesión")
                    }
                }
            },
        )
    }
    reasignarSesion?.let { ses ->
        ModalReasignar(onCancelar = { reasignarSesion = null }, onElegir = { terId ->
            reasignarSesion = null
            scope.launch {
                pe.saniape.app.ui.conIndicador(pe.saniape.app.ui.Gestion.ACTUALIZANDO) {
                    PacientesRepo.reasignarSesion(ses.id, terId)
                }
                recargarSesiones()
            }
        })
    }
    cobrarSesion?.let { ses ->
        ModalCobrar(ses, pacienteId = pacienteId, onCancelar = { cobrarSesion = null }, onConfirmar = { monto, metodo, obs ->
            cobrarSesion = null
            // La nota es SOLO la observación del cajero. El origen "Sesión #N" ya lo muestra
            // el chip (vía sesion_id), así que no se antepone para no duplicar.
            scope.launch {
                val ok = PacientesRepo.cobrarSesion(t.id, ses.id, monto, metodo, obs?.trim()?.ifBlank { null })
                if (ok) pe.saniape.app.data.staff.MetodoPagoPreferido.recordar(pacienteId, metodo)
                if (ok) pe.saniape.app.ui.Toaster.exito("Cobro registrado")
                else pe.saniape.app.ui.Toaster.error("No se pudo cobrar. Verifica en caja antes de reintentar.")
                recargarSesiones()
            }
        })
    }
    borrarSesion?.let { ses ->
        ModalBorrarSesion(
            ses,
            hoy = pe.saniape.app.ui.clinica.agenda.hoyIso(),
            onCancelar = { borrarSesion = null },
            onBorrar = { borrarPagos ->
                borrarSesion = null
                scope.launch {
                    pe.saniape.app.ui.conIndicador(pe.saniape.app.ui.Gestion.ELIMINANDO) {
                        PacientesRepo.borrarSesion(ses.id, borrarPagos)
                    }
                    recargarSesiones()
                }
            },
        )
    }
    // SERVICIO ÚNICO: registrar la atención (qué se hizo + cobro opcional en el mismo paso).
    if (registrarServicioAbierto) {
        ModalRegistrarServicio(
            t = t,
            verClinico = verClinico,
            puedePagos = verPagos,
            pacienteId = pacienteId,
            onCancelar = { registrarServicioAbierto = false },
            onConfirmar = { nota, cobrar, monto, metodo ->
                registrarServicioAbierto = false
                if (accionando) return@ModalRegistrarServicio
                accionando = true
                scope.launch {
                    // 1) Marca Completado + acumula la nota (server-side).
                    val rServicio = PacientesRepo.registrarServicio(t.id, nota, conPago = cobrar && monto != null && monto > 0)
                    // Parcial (409): el servicio SÍ quedó registrado, pero sus controles no
                    // se agendaron (cupo lleno…). Se avisa y el resto (técnicas, cobro) sigue.
                    val parcialServicio = !rServicio.registrada && rServicio.rechazo?.datos
                        ?.get("parcial")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" } == true
                    val okServicio = rServicio.registrada || parcialServicio
                    // 2) Aprender las técnicas usadas (se sugieren en futuras atenciones).
                    if (okServicio && !nota.isNullOrBlank()) {
                        pe.saniape.app.data.staff.TecnicasRepo.registrar(nota, t.especialidadId, pacienteNombre)
                    }
                    // 3) Cobro opcional (pago + kardex + recálculo, server-side).
                    var okCobro = true
                    if (cobrar && monto != null && monto > 0) {
                        okCobro = PacientesRepo.registrarPago(t.id, monto, metodo, notas = "Pago del servicio")
                        if (okCobro) pe.saniape.app.data.staff.MetodoPagoPreferido.recordar(pacienteId, metodo)
                    }
                    when {
                        !okServicio -> pe.saniape.app.ui.Toaster.error(rServicio.rechazo?.error ?: "No se pudo registrar la atención")
                        parcialServicio -> pe.saniape.app.ui.Toaster.error(
                            (rServicio.rechazo?.datos?.get("causa") as? kotlinx.serialization.json.JsonPrimitive)?.content
                                ?: "Atención registrada, pero no se agendaron sus controles. Agéndalos desde la agenda.",
                        )
                        !okCobro -> pe.saniape.app.ui.Toaster.error("Atención registrada, pero el cobro falló. Revisa caja.")
                        else -> pe.saniape.app.ui.Toaster.exito("Atención registrada")
                    }
                    // 📝 "¿Le dejas indicaciones?" tras registrar la atención (después del éxito).
                    if (okServicio && !rServicio.encolada) receta?.onOfrecer?.invoke()
                    accionando = false
                    cambioToken++
                    onCambioRealizado()
                }
            },
        )
    }
}

/** Modal del SERVICIO ÚNICO: nota de la atención (con autocompletado de técnicas) + cobro opcional.
 *  El cobro SOLO se ofrece con permiso de pagos (el fisio/médico registra la atención;
 *  recepción cobra después) — el endpoint igual lo valida server-side. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModalRegistrarServicio(
    t: TratamientoPaciente,
    /** Sin 'historia_clinica': se registra (y cobra) sin la nota de qué se hizo. */
    verClinico: Boolean = true,
    puedePagos: Boolean,
    pacienteId: String? = null,
    onCancelar: () -> Unit,
    onConfirmar: (nota: String?, cobrar: Boolean, monto: Double?, metodo: String) -> Unit,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    var nota by remember { mutableStateOf("") }
    var cobrar by remember { mutableStateOf(false) }
    var monto by remember { mutableStateOf((t.precioAcordado ?: t.precioBase)?.let { formato2(it) } ?: "") }
    var metodo by rememberMetodoPagoInicial(pacienteId)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("✨ Registrar servicio", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (verClinico) {
                    Text("Qué se hizo / receta (opcional)", color = c.textoSuave, fontSize = Sania.txt.mini,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    pe.saniape.app.ui.clinica.agenda.componentes.TecnicasInput(
                        value = nota, onChange = { nota = it }, especialidadId = t.especialidadId,
                    )
                } else {
                    pe.saniape.app.ui.clinica.AvisoHistoriaClinicaOculta()
                }
                // Cobro en el mismo paso (opcional) — SOLO con permiso de pagos.
                if (puedePagos) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { cobrar = !cobrar }) {
                        Text(if (cobrar) "☑" else "☐", fontSize = 18.sp, color = if (cobrar) c.teal else c.textoSuave)
                        Spacer(Modifier.width(8.dp))
                        Text("💳 ¿El ${LocalTerminologiaPaciente.current.paciente} pagó el servicio?", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (cobrar) {
                        Spacer(Modifier.height(8.dp))
                        CampoTexto("Monto (${simboloMoneda(moneda)})", monto, soloNumero = true) { monto = it }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rememberMetodosPago().forEach { m -> ChipMetodo(m, metodo == m) { metodo = m } }
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text("El cobro lo registra recepción (Pagos del tratamiento).",
                        color = c.textoSuave, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            BotonModalP("✓ Registrar") {
                onConfirmar(nota.trim().ifBlank { null }.takeIf { verClinico }, cobrar, monto.toDoubleOrNull(), metodo)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaSesion(
    ses: SesionFicha,
    verCosto: Boolean,
    puedeSesiones: Boolean,
    /** Admin sobre un registro cerrado: en una ficha de baja (sin acciones) igual ve ✏. */
    puedeCorregir: Boolean = false,
    puedePagos: Boolean,
    esAdmin: Boolean,
    accionando: Boolean,
    /** Tratamiento pagado completo: sin "💳 Cobrar" (las que tienen pago siguen "✓ Pagada"). */
    sinSaldo: Boolean = false,
    avisoRxPrevia: Boolean,
    onCompletar: () -> Unit,
    onEstado: (String) -> Unit,
    onEditar: () -> Unit,
    onRevertir: () -> Unit,
    onBorrar: () -> Unit,
    onReasignar: () -> Unit,
    onCobrar: () -> Unit,
    /** "📝 Dar indicaciones" (solo en la sesión completada); null = no aplica. */
    textoReceta: String? = null,
    /** La receta ya vinculada a esta sesión (indicador + "🖨 Ver"). */
    recetaSesion: pe.saniape.app.data.staff.RecetaVinculada? = null,
    onReceta: () -> Unit = {},
    onVerReceta: (pe.saniape.app.data.staff.RecetaVinculada) -> Unit = {},
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val estado = EstadosColor.sesion(ses.estado)
    val completada = ses.estado == "Completada"
    var expandida by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.fondo).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // Cabecera tocable → expande el detalle (procedimientos, mejorías, etc.)
        Row(
            Modifier.fillMaxWidth().clickable { expandida = !expandida },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sesión #${ses.numero}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            // Aviso: la sesión anterior dejó una RX pendiente (recordatorio antes de abrirla).
            if (avisoRxPrevia) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.pendBg)
                    .padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("⚕️ RX", color = c.pend, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            // Indicador: esta sesión ya tiene receta / indicaciones vinculadas.
            if (recetaSesion != null) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.purpleBg)
                    .padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("${if (recetaSesion.esIndicaciones) "📋" else "💊"} ${recetaSesion.numeroTexto}", color = c.purple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("${ses.fecha} ${ses.hora?.let { hora12(it) } ?: ""}".trim(), color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(ses.estado, color = estado.fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            ChevronExpandible(expandida)
        }

        // Detalle expandido (igual que la web): procedimientos, mejorías, etc.
        if (expandida) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .padding(10.dp),
            ) {
                DetalleSesion("Estado", ses.estado, c.texto)
                ses.duracion?.let { DetalleSesion("Duración", "$it min", c.texto) }
                ses.terapeutaNombre?.let { DetalleSesion("Profesional", it, c.texto) }
                if (verCosto) DetalleSesion("Pago sesión",
                    if ((ses.costo ?: 0.0) > 0) "${simboloMoneda(moneda)} ${formato2(ses.costo!!)}" else "Sin costo registrado", c.texto)
                ses.motivoEstado?.takeIf { it.isNotBlank() }?.let { DetalleSesion("Motivo", it, c.pend) }
                // Lo realizado y la evolución son clínicos: solo con 'historia_clinica'.
                if (pe.saniape.app.data.staff.LocalVerHistoriaClinica.current) {
                    DetalleSesion("Procedimientos", ses.notas?.takeIf { it.isNotBlank() } ?: "Sin observaciones", c.texto)
                    ses.mejorias?.takeIf { it.isNotBlank() }?.let { DetalleSesion("Mejorías", it, c.ok) }
                }
            }
        }

        if (puedeSesiones) {
            // 1 acción del momento (✓ Completar / 💳 Cobrar / la receta) + «⋯ Más» con el
            // resto (repartirAccionesSesion, la misma regla que Sesiones). El ✏ suelto se
            // fue: «✏ Editar sesión» ya estaba en el ⋯. Lo rojo (Canceló, Revertir,
            // Borrar) va al final del menú.
            val acciones = buildList {
                if (ses.pendiente) add(AccionSesion.Completar)
                // Completada: si ya tiene cobro → badge "✓ Pagada"; si no → "💳 Cobrar".
                if (completada && puedePagos && !ses.pagada && !sinSaldo) add(AccionSesion.Cobrar)
                add(AccionSesion.Editar)
                if (ses.pendiente) addAll(ACCIONES_ESTADO_SESION)
                // Receta / indicaciones de la sesión atendida: verla (si tiene) y dar una.
                if (completada && recetaSesion != null) add(AccionSesion.VerReceta)
                if (completada && textoReceta != null) add(AccionSesion.Receta)
                if (completada) add(AccionSesion.Revertir)
                add(AccionSesion.Reasignar)
                // Borrar es destructivo: solo Admin (igual criterio que borrar pagos).
                if (esAdmin) add(AccionSesion.Borrar)
            }
            val plan = repartirAccionesSesion(ses.estado, acciones)
            fun texto(a: AccionSesion): String = when (a) {
                AccionSesion.VerReceta -> recetaSesion?.let { "🖨 Ver ${it.nombreHoja.lowercase()} ${it.numeroTexto}" } ?: textoAccionSesion(a)
                AccionSesion.Receta -> textoReceta ?: textoAccionSesion(a)
                else -> textoAccionSesion(a)
            }
            fun ejecutar(a: AccionSesion) {
                when (a) {
                    AccionSesion.Completar -> onCompletar()
                    AccionSesion.Cobrar -> onCobrar()
                    AccionSesion.Editar -> onEditar()
                    AccionSesion.VerReceta -> recetaSesion?.let { onVerReceta(it) }
                    AccionSesion.Receta -> onReceta()
                    AccionSesion.Revertir -> onRevertir()
                    AccionSesion.Reasignar -> onReasignar()
                    AccionSesion.Borrar -> onBorrar()
                    // Reprogramar / No asistió / Canceló / Otro: el modal de siempre (fecha y motivo).
                    else -> estadoDeAccionSesion(a)?.let { onEstado(it) }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.primaria?.let { a ->
                    BotonTarjeta(texto(a), colorAccionSesion(a), !accionando, lleno = true) { ejecutar(a) }
                }
                if (completada && puedePagos && ses.pagada) Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.okBg)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) { Text("✓ Pagada", color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                plan.secundarias.forEach { a ->
                    BotonTarjeta(texto(a), colorAccionSesion(a), !accionando) { ejecutar(a) }
                }
                MenuMas(
                    grupos = plan.menu,
                    texto = { texto(it) },
                    onAccion = { ejecutar(it) },
                    rojo = { it in ROJAS_SESION },
                    habilitado = !accionando,
                    descripcion = "Más acciones de la sesión ${ses.numero}",
                )
            }
        } else if (puedeCorregir) {
            // Ficha dada de baja: solo el Admin, y solo corregir (nada nuevo).
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoBtn("✏", !accionando) { onEditar() }
                Spacer(Modifier.width(8.dp))
                Text("Corregir", color = c.textoSuave, fontSize = 11.sp)
            }
        }
    }
}

/** Botón de icono compacto (✏ / ⋯) discreto. */
@Composable
private fun IconoBtn(icono: String, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(icono, color = c.textoSuave, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

/** Fila label:valor del detalle de una sesión. */
@Composable
private fun DetalleSesion(label: String, valor: String, colorValor: Color) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label:", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.width(96.dp))
        Text(valor, color = colorValor, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/** Chip de info clínica (diagnóstico/medicación/control) — para las Consultas. */
@Composable
private fun ChipInfo(icono: String, texto: String, fg: Color, bg: Color) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
        .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("$icono $texto", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** Aviso de carga fallida con botón Reintentar (no confundir con "lista vacía"). */
@Composable
private fun BloqueReintentar(mensaje: String, onReintentar: () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.pendBg).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⚠ $mensaje", color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.navy)
                .clickable { onReintentar() }.padding(horizontal = 16.dp, vertical = 7.dp),
        ) { Text("↻ Reintentar", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

/** El único botón de la fila de un pago (🧾 o ⋯): 36 dp, con nombre para el lector de pantalla. */
@Composable
private fun IconoFilaPago(icono: String, descripcion: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .semantics { contentDescription = descripcion; role = androidx.compose.ui.semantics.Role.Button }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(icono, fontSize = 15.sp, color = Sania.colors.textoSuave, fontWeight = FontWeight.Bold) }
}

/** Color de cada acción del tratamiento a la vista (el alta SIEMPRE la declara el profesional: verde). */
@Composable
private fun colorAccionTrat(a: AccionTratamiento): Color {
    val c = Sania.colors
    return when (a) {
        AccionTratamiento.Agendar, AccionTratamiento.Ampliar -> c.navy
        AccionTratamiento.Alta, AccionTratamiento.Reactivar -> c.ok
        AccionTratamiento.Receta, AccionTratamiento.Derivar -> c.purple
        AccionTratamiento.Suspender, AccionTratamiento.NoVolvio -> c.pend
        AccionTratamiento.Cancelar, AccionTratamiento.Eliminar -> c.error
        else -> c.textoSuave
    }
}

/** Color de cada acción de la fila de sesión a la vista. */
@Composable
private fun colorAccionSesion(a: AccionSesion): Color {
    val c = Sania.colors
    return when (a) {
        AccionSesion.Completar -> c.navy
        AccionSesion.Cobrar -> c.teal
        AccionSesion.VerReceta, AccionSesion.Receta -> c.purple
        AccionSesion.Alta -> c.ok
        AccionSesion.Ampliar -> c.info
        else -> c.textoSuave
    }
}

/** Métodos de pago (igual que la web). */
// Métodos de pago: ahora salen de la configuración de la clínica — ver
// rememberMetodosPago() en FormDialogo.kt (fallback a los de siempre).

/** Sección de pagos del tratamiento: resumen + lista + registrar (reusa endpoint). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeccionPagos(
    t: TratamientoPaciente, esAdmin: Boolean, recargaToken: Int, onCambio: () -> Unit,
    /** Ficha dada de baja: se ven los pagos, pero no se registran/editan/borran. */
    soloLectura: Boolean = false,
    /** Para arrancar en el último método de pago de este paciente. */
    pacienteId: String? = null,
    /** Cada vez que sube, abre "Registrar pago" con el saldo precargado (aviso del alta). */
    abrirRegistro: Int = 0,
    /** Lo pagado NETO, cada vez que se cargan los pagos: la tarjeta decide con eso si
     *  ofrece "💳 Cobrar" por sesión (el estado_pago guardado puede estar viejo). */
    onPagado: (Double) -> Unit = {},
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var pagos by remember { mutableStateOf<List<pe.saniape.app.data.staff.PagoFicha>?>(null) }
    var agregando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var monto by remember { mutableStateOf("") }
    var metodo by rememberMetodoPagoInicial(pacienteId)
    var notaPago by remember { mutableStateOf("") }
    var editando by remember { mutableStateOf<String?>(null) }   // id del pago en edición
    var editMonto by remember { mutableStateOf("") }
    var editMetodo by remember { mutableStateOf("Efectivo") }
    var borrarId by remember { mutableStateOf<String?>(null) }   // confirmación de borrado
    // 🧾 Comprobante del pago (PDF, recibo interno) ya hecho: id del pago cuyo formato
    // se está eligiendo. Es una REIMPRESIÓN (gemelo de BotonComprobante web): mismo
    // recibo, con la marca "Reimpresión"; un error del servidor se dice tal cual.
    var reciboDe by remember { mutableStateOf<String?>(null) }
    var bajandoRecibo by remember { mutableStateOf(false) }
    val accionesRecibo = pe.saniape.app.ui.recordarAcciones()
    fun abrirRecibo(pagoId: String, formato: String) {
        if (bajandoRecibo) return
        bajandoRecibo = true
        scope.launch {
            val r = pe.saniape.app.data.staff.ReciboRepo.descargar("pago", pagoId, formato, reimpresion = true)
            bajandoRecibo = false
            reciboDe = null
            when (r) {
                is pe.saniape.app.data.staff.ResultadoRecibo.Ok -> accionesRecibo.abrirPdf("comprobante-pago-${pagoId.take(8)}-$formato.pdf", r.pdf)
                is pe.saniape.app.data.staff.ResultadoRecibo.Error -> pe.saniape.app.ui.Toaster.error(r.mensaje)
            }
        }
    }
    // ── Pagar con saldo a favor (docs/app-contrato-pagar-con-saldo.md) ──
    var usarSaldo by remember { mutableStateOf(false) }
    var montoSaldo by remember { mutableStateOf("") }
    /** null = el resto con UN medio ([metodo]); si no, el reparto en varios medios. */
    var filasResto by remember { mutableStateOf<List<pe.saniape.app.data.staff.FilaPago>?>(null) }
    var consultaSaldo by remember { mutableStateOf<pe.saniape.app.data.staff.ConsultaSaldo?>(null) }
    var consultaSaldoToken by remember { mutableStateOf(0) }


    // Recarga al montar y cada vez que cambia recargaToken (tras cobrar una sesión).
    LaunchedEffect(t.id, recargaToken) {
        pagos = runCatching { PacientesRepo.pagosDe(t.id) }.getOrDefault(emptyList())
    }

    // Sesión suelta: el acordado suma lo atendido + lo cobrado por adelantado
    // (data/SesionesSueltas.kt), con los pagos recién leídos (su sesion_id).
    val pagosDev = pagos?.map { pe.saniape.app.data.PagoDevengo(it.sesionId, it.monto) }
    val acordado = t.acordadoCon(pagos = pagosDev)
    val detalleSuelta = t.contextoSueltas(pagos = pagosDev)?.let { pe.saniape.app.data.detalleSesionesSueltas(t.tratPago, it) }
    // Pagado NETO: Σ de todas las filas, también las de saldo (el "consumo" resta).
    val pagado = pagos?.let { pe.saniape.app.data.staff.pagadoNeto(it) } ?: 0.0
    LaunchedEffect(pagos) { if (pagos != null) onPagado(pagado) }
    val saldo = acordado - pagado
    // Disponible del paciente (sin contar ESTE tratamiento): se PREGUNTA al servidor al
    // abrir "Registrar pago", solo si este tratamiento debe algo y puede recibir saldo.
    val admiteSaldo = !soloLectura && pacienteId != null && t.estado != "Cancelado" && t.estado != "Eliminado"
    LaunchedEffect(agregando, consultaSaldoToken, t.id) {
        if (!agregando || !admiteSaldo || saldo <= 0.005) { consultaSaldo = null; return@LaunchedEffect }
        consultaSaldo = pe.saniape.app.data.staff.PagarConSaldoRepo.saldoDisponible(pacienteId!!, t.id)
    }
    val disponibleSaldo = (consultaSaldo as? pe.saniape.app.data.staff.ConsultaSaldo.Ok)?.saldo?.disponible ?: 0.0
    val usableSaldo = pe.saniape.app.data.staff.saldoUsable(disponibleSaldo, saldo)
    val puedeUsarSaldo = admiteSaldo && usableSaldo > 0.005
    val totalForm = pe.saniape.app.data.staff.montoDeTexto(monto)
    val validacionSaldo = if (usarSaldo && puedeUsarSaldo)
        pe.saniape.app.data.staff.partesDelFormulario(totalForm, montoSaldo, usableSaldo, metodo, filasResto, moneda) else null
    fun limpiarSaldo() { usarSaldo = false; montoSaldo = ""; filasResto = null }
    // Pedido de afuera (confirmación de alta con deuda): abrir el registro con el
    // saldo, una vez por pedido y recién con los pagos cargados.
    var registroAbiertoPara by remember { mutableStateOf(0) }
    LaunchedEffect(abrirRegistro, pagos) {
        if (abrirRegistro > 0 && pagos != null && registroAbiertoPara != abrirRegistro && !soloLectura) {
            registroAbiertoPara = abrirRegistro
            monto = if (saldo > 0.005) formato2(saldo) else ""
            agregando = true
        }
    }
    val frac = if (acordado > 0) (pagado / acordado).coerceIn(0.0, 1.0).toFloat() else 0f

    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
    Spacer(Modifier.height(Sania.dim.md))
    Text("💰 Pagos", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))

    // Resumen acordado/pagado/saldo
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ColMonto("Acordado", acordado, c.texto)
        ColMonto("Pagado", pagado, c.ok)
        // Pagó de más: "A favor" (reglas en data/SaldoAFavor.kt: en suelta/consulta
        // se compara contra lo realizado). Si no aplica, el saldo nunca se muestra negativo.
        val aFavor = pe.saniape.app.data.saldoAFavorTratamiento(t.cuentaCon(pagado, acordado))
        if (aFavor > 0.0) ColMonto("A favor", aFavor, c.ok)
        else ColMonto("Saldo", saldo.coerceAtLeast(0.0), if (saldo > 0.005) c.error else c.ok)
    }
    // Sesiones sueltas: de dónde sale el acordado ("2 sesiones atendidas · S/ 200")
    // y, si pagó por sesiones que vienen, cuánto lleva adelantado (no es saldo a
    // favor: ya tiene sesiones agendadas que lo usan). Como la PagoCard web.
    if (detalleSuelta != null && pagos != null && t.estado != "Cancelado") {
        val dinero = { n: Double -> "${simboloMoneda(moneda)} ${formato2(n)}" }
        val aFavorSuelta = pe.saniape.app.data.saldoAFavorTratamiento(t.cuentaCon(pagado, acordado))
        val adelantado = kotlin.math.round((pagado - acordado - aFavorSuelta) * 100) / 100
        Spacer(Modifier.height(4.dp))
        Text(pe.saniape.app.data.textoSesionesSueltas(detalleSuelta, dinero), color = c.textoSuave, fontSize = 11.sp)
        if (adelantado > 0.005) {
            Text("${dinero(adelantado)} adelantado para las próximas sesiones",
                color = c.teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.chipBg)) {
        Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ok))
    }

    // Cancelado con dinero pagado y no atendido: no es saldo a favor hasta que el Admin lo pasa.
    pagos?.let { AvisoSaldoCancelado(t, it, esAdmin, soloLectura, onCambio) }

    // Lista de pagos. Un pago con saldo a favor o en varias partes va en UNA línea
    // ("Saldo a favor S/ X + Yape S/ Y"); el saldo que salió hacia otro tratamiento,
    // como "Saldo aplicado a …" (sin acciones). Los pagos simples, como siempre.
    pagos?.takeIf { it.isNotEmpty() }?.let { lista ->
        Spacer(Modifier.height(8.dp))
        pe.saniape.app.data.staff.agruparPagos(lista, moneda).forEach { g ->
            if (g.agrupado) {
                FilaPagoAgrupado(g, esAdmin, soloLectura, onCambio)
                return@forEach
            }
            val p = g.partes.first()
            if (editando == p.id) {
                // Edición inline (solo Admin)
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    androidx.compose.material3.OutlinedTextField(colors = coloresCampoForm(), 
                        value = editMonto, onValueChange = { editMonto = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        placeholder = { Text("Monto", color = c.textoSuave) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rememberMetodosPago().forEach { m -> ChipMetodo(m, editMetodo == m) { editMetodo = m } }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniBtn("Guardar", c.ok, !guardando) {
                            val m = editMonto.toDoubleOrNull()
                            if (m == null || m <= 0 || guardando) return@MiniBtn
                            guardando = true
                            scope.launch {
                                val ok = pe.saniape.app.ui.conIndicador(pe.saniape.app.ui.Gestion.ACTUALIZANDO) {
                                    PacientesRepo.editarPago(p.id, m, editMetodo)
                                }
                                guardando = false
                                // onCambio() ya re-dispara la recarga (evita doble query).
                                if (ok) { editando = null; pe.saniape.app.ui.Toaster.exito("Pago actualizado"); onCambio() }
                                else pe.saniape.app.ui.Toaster.error("No se pudo editar")
                            }
                        }
                        MiniBtn("Cancelar", c.textoSuave, !guardando) { editando = null }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(p.metodo, color = c.navy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    // Origen del pago: amarrado a una sesión vs pago libre del tratamiento.
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.tealBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(p.numeroSesion?.let { "Sesión #$it" } ?: "Pago libre",
                            color = c.teal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(p.fecha, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("${simboloMoneda(moneda)} ${formato2(p.monto)}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    // Un solo botón: 🧾 (o ⋯ si el pago no tiene recibo) abre el menú con el
                    // comprobante (A4 / ticket), ✏ Editar y 🗑 Borrar (estos dos solo Admin y
                    // no en una ficha dada de baja). Antes: 🧾 ✏ 🗑 sueltos + otra fila de recibo.
                    val conRecibo = p.saldoTipo != "consumo"
                    val corregir = esAdmin && !soloLectura
                    val planPago = pe.saniape.app.data.staff.accionesPago(conRecibo, editar = corregir, borrar = corregir)
                    if (bajandoRecibo && reciboDe == p.id) {
                        Spacer(Modifier.width(8.dp))
                        Text("Generando…", color = c.textoSuave, fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    MenuMas(
                        grupos = planPago.menu,
                        texto = { pe.saniape.app.data.staff.textoAccionPago(it) },
                        rojo = { it == pe.saniape.app.data.staff.AccionPago.Borrar },
                        habilitado = !bajandoRecibo && !guardando,
                        descripcion = if (conRecibo) "Comprobante y acciones del pago" else "Acciones del pago",
                        onAccion = { a ->
                            when (a) {
                                pe.saniape.app.data.staff.AccionPago.ComprobanteA4 -> { reciboDe = p.id; abrirRecibo(p.id, "a4") }
                                pe.saniape.app.data.staff.AccionPago.Ticket -> { reciboDe = p.id; abrirRecibo(p.id, "ticket") }
                                pe.saniape.app.data.staff.AccionPago.Editar -> {
                                    editando = p.id; editMonto = formato2(p.monto); editMetodo = p.metodo; borrarId = null
                                }
                                pe.saniape.app.data.staff.AccionPago.Borrar -> borrarId = p.id
                            }
                        },
                        disparador = { abrir ->
                            IconoFilaPago(if (conRecibo) "🧾" else "⋯", if (conRecibo) "Comprobante y acciones del pago" else "Acciones del pago", abrir)
                        },
                    )
                }
                // Observación del pago (la ve quien cobre la próxima vez).
                p.notas?.takeIf { it.isNotBlank() }?.let {
                    Text("📝 $it", color = c.textoSuave, fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 2.dp))
                }
                // Confirmación de borrado
                if (borrarId == p.id) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("¿Borrar este pago?", color = c.error, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        MiniBtn("Sí, borrar", c.error, !guardando) {
                            if (guardando) return@MiniBtn
                            guardando = true
                            scope.launch {
                                val ok = pe.saniape.app.ui.conIndicador(pe.saniape.app.ui.Gestion.ELIMINANDO) {
                                    PacientesRepo.borrarPago(p.id)
                                }
                                guardando = false
                                // onCambio() ya re-dispara la recarga (evita doble query).
                                if (ok) { borrarId = null; pe.saniape.app.ui.Toaster.exito("Pago eliminado"); onCambio() }
                                else pe.saniape.app.ui.Toaster.error("No se pudo eliminar")
                            }
                        }
                        MiniBtn("No", c.textoSuave, !guardando) { borrarId = null }
                    }
                }
            }
        }
    }

    // Registrar pago (no en una ficha dada de baja: el historial se ve, no se escribe).
    Spacer(Modifier.height(8.dp))
    if (soloLectura) {
        Text("${LocalTerminologiaPaciente.current.Paciente} dado de baja: no se registran pagos nuevos.", color = c.textoSuave, fontSize = 11.sp)
    } else if (!agregando) {
        Column(Modifier.tourAncla("ficha.pago_registrar")) {
            MiniBtn(if (saldo > 0.005) "+ Registrar pago" else "+ Pago adicional", c.navy, !guardando) {
                // Precarga el monto con el SALDO pendiente: registrar el pago completo = 1 confirmación.
                monto = if (saldo > 0.005) formato2(saldo) else ""
                limpiarSaldo()
                agregando = true
            }
        }
    } else {
        androidx.compose.material3.OutlinedTextField(colors = coloresCampoForm(), 
            value = monto, onValueChange = { monto = it.filter { ch -> ch.isDigit() || ch == '.' } },
            placeholder = { Text("Monto", color = c.textoSuave) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().tourAncla("pago_form"),
        )
        val metodosClinica = rememberMetodosPago()
        // PAGAR CON SALDO A FAVOR: solo si el paciente tiene saldo y este tratamiento
        // debe algo. Sin saldo, el formulario es el de siempre.
        if (puedeUsarSaldo) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.tourAncla("pago_form.usar_saldo")) { OpcionSaldoAFavor(
                usable = usableSaldo, total = totalForm,
                usar = usarSaldo,
                onUsar = { on ->
                    usarSaldo = on
                    if (on) {
                        val (s, _) = pe.saniape.app.data.staff.repartoSugerido(totalForm ?: 0.0, usableSaldo)
                        montoSaldo = if (s > 0) pe.saniape.app.data.staff.textoMonto(s) else ""
                    } else { montoSaldo = ""; filasResto = null }
                },
                montoSaldo = montoSaldo, onMontoSaldo = { montoSaldo = it },
                metodos = metodosClinica, metodoResto = metodo, onMetodoResto = { metodo = it },
                filasResto = filasResto, onFilasResto = { filasResto = it },
                validacion = validacionSaldo, deshabilitado = guardando,
            ) }
        } else if (consultaSaldo == pe.saniape.app.data.staff.ConsultaSaldo.SinRed) {
            Spacer(Modifier.height(4.dp))
            Text("Sin conexión: no se pudo consultar el saldo a favor del ${LocalTerminologiaPaciente.current.paciente}.", color = c.textoSuave, fontSize = 10.sp)
        }
        if (!(usarSaldo && puedeUsarSaldo)) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                metodosClinica.filterNot { pe.saniape.app.data.staff.esMetodoSaldo(it) }
                    .forEach { m -> ChipMetodo(m, metodo == m) { metodo = m } }
            }
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.OutlinedTextField(colors = coloresCampoForm(), 
            value = notaPago, onValueChange = { notaPago = it },
            placeholder = { Text("Observación (opcional)", color = c.textoSuave) },
            minLines = 1, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.tourAncla("pago_form.confirmar")) { MiniBtn("Guardar pago", c.ok, !guardando && validacionSaldo !is pe.saniape.app.data.staff.ValidacionPartes.Error) {
                val m = monto.toDoubleOrNull()
                if (m == null || m <= 0 || guardando) return@MiniBtn
                if (usarSaldo && puedeUsarSaldo) {
                    // Con saldo: DIRECTO al servidor (nunca a la cola offline: necesita el
                    // saldo en vivo). La clave se conserva ante una respuesta incierta.
                    val v = validacionSaldo as? pe.saniape.app.data.staff.ValidacionPartes.Ok ?: return@MiniBtn
                    guardando = true
                    scope.launch {
                        val r = pe.saniape.app.ui.conIndicador(pe.saniape.app.ui.Gestion.GUARDANDO) {
                            pe.saniape.app.data.staff.PagarConSaldoRepo.registrar(
                                pe.saniape.app.data.staff.cuerpoPagoConSaldo(t.id, m, v.partes, notaPago.trim().ifBlank { null }, recordar = false),
                                moneda = moneda,
                            )
                        }
                        guardando = false
                        when (r) {
                            is pe.saniape.app.data.staff.ResultadoPagoSaldo.Ok -> {
                                if (filasResto == null && v.partes.size > 1) pe.saniape.app.data.staff.MetodoPagoPreferido.recordar(pacienteId, metodo)
                                monto = ""; notaPago = ""; agregando = false; limpiarSaldo()
                                pe.saniape.app.ui.Toaster.exito("Pago registrado: ${pe.saniape.app.data.staff.resumenPartes(v.partes, moneda)}")
                                onCambio()
                            }
                            is pe.saniape.app.data.staff.ResultadoPagoSaldo.Rechazo -> {
                                pe.saniape.app.ui.Toaster.error(r.mensaje)
                                // El disponible cambió (otra recepción lo usó): volver a preguntarlo.
                                if (r.rechazo.codigo == pe.saniape.app.data.staff.CODIGO_SALDO_INSUFICIENTE) consultaSaldoToken++
                            }
                            pe.saniape.app.data.staff.ResultadoPagoSaldo.SinRed ->
                                pe.saniape.app.ui.Toaster.error(pe.saniape.app.data.staff.MENSAJE_PAGO_SALDO_SIN_RED)
                        }
                    }
                    return@MiniBtn
                }
                guardando = true
                scope.launch {
                    val ok = PacientesRepo.registrarPago(t.id, m, metodo, notaPago.trim().ifBlank { null })
                    guardando = false
                    if (ok) {
                        pe.saniape.app.data.staff.MetodoPagoPreferido.recordar(pacienteId, metodo)
                        monto = ""; notaPago = ""; agregando = false
                        pe.saniape.app.ui.Toaster.exito("Pago registrado")
                        // NO recargamos pagos aquí: onCambio() → cambioToken++ ya re-dispara
                        // el LaunchedEffect que recarga pagosDe (evita la doble query).
                        onCambio()
                    } else pe.saniape.app.ui.Toaster.error("No se pudo registrar el pago")
                }
            } }
            MiniBtn("Cancelar", c.textoSuave, !guardando) { agregando = false; monto = ""; notaPago = ""; limpiarSaldo() }
        }
    }
}

@Composable
private fun ChipMetodo(m: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 5.dp),
    ) { Text(m, color = if (activo) c.sobreNavy else c.texto, fontSize = 11.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun ColMonto(label: String, monto: Double, color: Color) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = c.textoSuave, fontSize = 10.sp)
        Text("${simboloMoneda(moneda)} ${formato2(monto)}", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniBtn(label: String, color: Color, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado) { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

private fun modalidadIcono(m: String?): String = when (m) {
    "Paquete" -> "📦"; "Sesión suelta" -> "🎫"; "Consulta" -> "🩺"; else -> "•"
}

private fun formato2(n: Double): String {
    // Redondeo (no truncado: 0.29 × 100 = 28.999…) y con signo delante.
    val cent = kotlin.math.round(n * 100).toLong()
    val abs = kotlin.math.abs(cent)
    return (if (cent < 0) "-" else "") + "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

// ── Modales de acciones de sesión ──

@Composable
private fun ModalEditarSesion(
    ses: SesionFicha,
    /** Sin 'historia_clinica': sin las notas (van en null = no se tocan). */
    verClinico: Boolean = true,
    onCancelar: () -> Unit,
    onGuardar: (fecha: String, hora: String?, duracion: Int, costo: Double?, notas: String?) -> Unit,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    // Fecha y hora con PICKERS nativos (antes eran texto libre "AAAA-MM-DD" sin
    // validación: un typo guardaba cualquier cosa) + duración editable, como la web.
    var fecha by remember { mutableStateOf(ses.fecha) }
    var hora by remember { mutableStateOf(ses.hora?.take(5) ?: "") }
    var duracion by remember { mutableStateOf(ses.duracion ?: 45) }
    var costo by remember { mutableStateOf(ses.costo?.let { formato2(it) } ?: "") }
    var notas by remember { mutableStateOf(ses.notas ?: "") }
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }

    if (mostrarFecha) DialogoFecha(onElegir = { fecha = it }, onCerrar = { mostrarFecha = false })
    if (mostrarHora) DialogoHora(hora.ifBlank { "09:00" }, onElegir = { hora = it }, onCerrar = { mostrarHora = false })

    DialogoForm(
        titulo = "✏ Editar sesión #${ses.numero}",
        subtitulo = null,
        textoAccion = "Guardar",
        accionHabilitada = fecha.isNotBlank(),
        onCancelar = onCancelar,
        onAccion = {
            onGuardar(fecha.trim(), hora.trim().ifBlank { null }, duracion, costo.toDoubleOrNull(),
                notas.trim().ifBlank { null }.takeIf { verClinico })
        },
    ) {
        TarjetaForm(titulo = "Programación", icono = "📅") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { EtqForm("Fecha"); CajaSelectorForm(fecha) { mostrarFecha = true } }
                Column(Modifier.weight(1f)) {
                    EtqForm("Hora")
                    CajaSelectorForm(if (hora.isBlank()) "—" else pe.saniape.app.ui.hora12(hora)) { mostrarHora = true }
                }
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Duración")
            ChipsDuracion(duracion, onChange = { duracion = it })
            Spacer(Modifier.height(10.dp))
            CampoTexto("Costo (${simboloMoneda(moneda)}) — vacío si no aplica", costo, soloNumero = true) { costo = it }
            if (verClinico) {
                Spacer(Modifier.height(10.dp))
                CampoTexto("Notas / procedimientos", notas, multilinea = true) { notas = it }
            }
        }
    }
}

/** Lo que guarda la corrección de una sesión del historial. */
private data class CorreccionSesion(
    val fecha: String, val hora: String?, val duracion: Int, val costo: Double?, val notas: String,
    val mejorias: String, val dolorInicio: Int?, val dolorFin: Int?, val terapeutaId: String?,
)

/**
 * Corrección del ADMIN sobre una sesión del historial: lo del modal de editar
 * (fecha/hora/duración/costo/técnicas) + mejorías, EVA (fisio) y profesional.
 * Gemelo del modal de la ficha web en modo corrección. No cambia estados ni cobros.
 */
@Composable
private fun ModalCorregirSesion(
    ses: SesionFicha,
    esFisio: Boolean,
    /** Sin 'historia_clinica': sin técnicas, mejorías ni EVA (no se mandan). */
    verClinico: Boolean = true,
    guardando: Boolean,
    onCancelar: () -> Unit,
    onGuardar: (CorreccionSesion) -> Unit,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val completada = ses.estado == "Completada"
    var fecha by remember { mutableStateOf(ses.fecha) }
    var hora by remember { mutableStateOf(ses.hora?.take(5) ?: "") }
    var duracion by remember { mutableStateOf(ses.duracion ?: 45) }
    var costo by remember { mutableStateOf(ses.costo?.let { formato2(it) } ?: "") }
    var notas by remember { mutableStateOf(ses.notas ?: "") }
    var mejorias by remember { mutableStateOf(ses.mejorias ?: "") }
    var dolorInicio by remember { mutableStateOf(ses.dolorInicio) }
    var dolorFin by remember { mutableStateOf(ses.dolorFin) }
    var terapeutaId by remember { mutableStateOf(ses.terapeutaId) }
    var terapeutas by remember { mutableStateOf<List<pe.saniape.app.data.staff.RefNombre>?>(null) }
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { terapeutas = runCatching { PacientesRepo.terapeutasActivos() }.getOrDefault(emptyList()) }

    if (mostrarFecha) DialogoFecha(onElegir = { fecha = it }, onCerrar = { mostrarFecha = false })
    if (mostrarHora) DialogoHora(hora.ifBlank { "09:00" }, onElegir = { hora = it }, onCerrar = { mostrarHora = false })

    DialogoForm(
        titulo = "✏ Corregir sesión #${ses.numero}",
        subtitulo = null,
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = fecha.isNotBlank() && !guardando,
        onCancelar = onCancelar,
        onAccion = {
            onGuardar(CorreccionSesion(
                fecha.trim(), hora.trim().ifBlank { null }, duracion, costo.toDoubleOrNull(), notas.trim(),
                mejorias.trim(), dolorInicio, dolorFin, terapeutaId,
            ))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg).padding(12.dp),
        ) {
            Text("⚠ ${pe.saniape.app.data.staff.AVISO_CORRECCION}", color = c.pend, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(pe.saniape.app.data.staff.DETALLE_CORRECCION, color = c.pend, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        TarjetaForm(titulo = "Programación", icono = "📅") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { EtqForm("Fecha"); CajaSelectorForm(fecha) { mostrarFecha = true } }
                Column(Modifier.weight(1f)) {
                    EtqForm("Hora")
                    CajaSelectorForm(if (hora.isBlank()) "—" else pe.saniape.app.ui.hora12(hora)) { mostrarHora = true }
                }
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Duración")
            ChipsDuracion(duracion, onChange = { duracion = it })
            Spacer(Modifier.height(10.dp))
            CampoTexto("Costo (${simboloMoneda(moneda)}) — vacío si no aplica", costo, soloNumero = true) { costo = it }
        }
        if (verClinico) {
            Spacer(Modifier.height(10.dp))
            TarjetaForm(titulo = "Atención", icono = "🩺") {
                CampoTexto("Técnicas / procedimientos", notas, multilinea = true) { notas = it }
                if (completada) {
                    Spacer(Modifier.height(10.dp))
                    CampoTexto("Mejorías", mejorias, multilinea = true) { mejorias = it }
                }
            }
        }
        if (completada && esFisio && verClinico) {
            Spacer(Modifier.height(10.dp))
            pe.saniape.app.ui.clinica.fisio.BloqueEva(dolorInicio, dolorFin, onInicio = { dolorInicio = it }, onFin = { dolorFin = it })
        }
        Spacer(Modifier.height(10.dp))
        TarjetaForm(titulo = "Profesional que atendió", icono = "👤") {
            val lista = terapeutas
            if (lista == null) Text("Cargando…", color = c.textoSuave, fontSize = 12.sp)
            else {
                if (ses.terapeutaNombre != null && lista.none { it.id == ses.terapeutaId }) {
                    Text("Actual: ${ses.terapeutaNombre} (inactivo)", color = c.textoSuave, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                }
                lista.forEach { ter ->
                    val elegido = ter.id == terapeutaId
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (elegido) c.navy.copy(alpha = 0.12f) else c.fondo)
                            .border(1.dp, if (elegido) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { terapeutaId = ter.id }.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) { Text((if (elegido) "✓ " else "") + ter.nombre, color = c.texto, fontSize = Sania.txt.cuerpo) }
                }
                Text("Cambia también su cita. La comisión ya generada no se mueve.", color = c.textoSuave, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ModalReasignar(onCancelar: () -> Unit, onElegir: (String) -> Unit) {
    val c = Sania.colors
    var terapeutas by remember { mutableStateOf<List<pe.saniape.app.data.staff.RefNombre>>(emptyList()) }
    LaunchedEffect(Unit) { terapeutas = runCatching { PacientesRepo.terapeutasActivos() }.getOrDefault(emptyList()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("👤 Reasignar profesional", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (terapeutas.isEmpty()) Text("Cargando…", color = c.textoSuave, fontSize = 12.sp)
                terapeutas.forEach { ter ->
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { onElegir(ter.id) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    ) { Text(ter.nombre, color = c.texto, fontSize = Sania.txt.cuerpo) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModalCobrar(ses: SesionFicha, pacienteId: String? = null, onCancelar: () -> Unit, onConfirmar: (Double, String, String?) -> Unit) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    var monto by remember { mutableStateOf(ses.costo?.let { formato2(it) } ?: "") }
    var metodo by rememberMetodoPagoInicial(pacienteId)
    var obs by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("💳 Cobrar sesión #${ses.numero}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CampoTexto("Monto (${simboloMoneda(moneda)})", monto, soloNumero = true) { monto = it }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rememberMetodosPago().forEach { m -> ChipMetodo(m, metodo == m) { metodo = m } }
                }
                Spacer(Modifier.height(8.dp))
                CampoTexto("Observación (opcional)", obs, multilinea = true) { obs = it }
                Text("La verá quien cobre la próxima vez (ej. compromiso del ${LocalTerminologiaPaciente.current.paciente}).",
                    color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        },
        confirmButton = {
            BotonModalP("Registrar pago") {
                monto.toDoubleOrNull()?.takeIf { it > 0 }?.let { onConfirmar(it, metodo, obs.trim().ifBlank { null }) }
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/**
 * Confirmación de borrado de una sesión.
 *
 * El pago SIEMPRE se borra con la sesión (un pago sin sesión queda huérfano, no
 * sirve para nada y estorba al recrearla). Lo único que cambia es el aviso:
 *  - Sin pago            → confirmación simple.
 *  - Pago de HOY         → se borra todo; la caja del día aún no se cuadró.
 *  - Pago de días previos→ se avisa la fecha, porque ese ingreso pudo entrar ya
 *                          en un cierre de caja y borrarlo lo descuadraría.
 */
@Composable
private fun ModalBorrarSesion(
    ses: SesionFicha,
    hoy: String,
    onCancelar: () -> Unit,
    onBorrar: (borrarPagos: Boolean) -> Unit,
) {
    val c = Sania.colors
    // OJO: se compara la fecha del PAGO (no la de la sesión: el pago tiene la suya
    // y puede reasignarse). Si no la tenemos, se asume el peor caso y se avisa.
    val fechaDelPago = ses.fechaPago?.take(10)
    val pagoDeOtroDia = ses.pagada && fechaDelPago != hoy
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("🗑 Borrar sesión #${ses.numero}", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                when {
                    pagoDeOtroDia ->
                        "Esta sesión tiene un pago${fechaDelPago?.let { " del $it" } ?: " de otro día"}, " +
                            "que ya pudo entrar en un cierre de caja. Al borrarla se elimina también ese " +
                            "cobro y su ingreso, y la caja de ese día cambiará. ¿Continuar?"
                    ses.pagada ->
                        "Se borrará la sesión y también su pago (y el ingreso en caja). ¿Continuar?"
                    else -> "¿Seguro que quieres borrar esta sesión?"
                },
                color = c.texto, fontSize = Sania.txt.cuerpo,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onBorrar(true) }) {
                Text("Borrar", color = c.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@Composable
private fun CampoTexto(
    label: String, value: String, soloNumero: Boolean = false, multilinea: Boolean = false, onChange: (String) -> Unit,
) {
    val c = Sania.colors
    Text(label, color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
    androidx.compose.material3.OutlinedTextField(colors = coloresCampoForm(), 
        value = value,
        onValueChange = { if (soloNumero) onChange(it.filter { ch -> ch.isDigit() || ch == '.' }) else onChange(it) },
        singleLine = !multilinea, minLines = if (multilinea) 2 else 1,
        keyboardOptions = if (soloNumero) androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else androidx.compose.foundation.text.KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun BotonModalP(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
            .clickable { onClick() }.padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(texto, color = c.sobreNavy, fontWeight = FontWeight.Bold) }
}

/**
 * Botón "💡 Sugerir para la próxima sesión" (IA, Plus). Espeja SugerenciaSesion.tsx:
 * la IA propone 2-4 técnicas para la siguiente sesión según diagnóstico/evolución; el
 * profesional decide. Muestra el texto completo al terminar (sin efecto "escribiendo").
 */
@Composable
private fun SugerenciaSesionIA(tratamientoId: String) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var texto by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

    fun sugerir() {
        if (cargando) return
        scope.launch {
            cargando = true
            texto = PacientesRepo.sugerirSesion(tratamientoId)
                ?: "⚠ El asistente de IA no está disponible. Inténtalo de nuevo."
            cargando = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                .border(1.dp, c.lav, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(enabled = !cargando) { sugerir() }.padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(if (cargando) "✨ Analizando…" else "💡 Sugerir para la próxima sesión",
                color = c.lav, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        texto?.let {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.chipBg).padding(12.dp)) {
                Text(it, color = c.texto, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("↺ Regenerar", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !cargando) { sugerir() })
            }
        }
    }
}
