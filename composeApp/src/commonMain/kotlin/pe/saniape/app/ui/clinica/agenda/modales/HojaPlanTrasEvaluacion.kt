package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.MODALIDAD_PAQUETE
import pe.saniape.app.data.staff.MODALIDAD_SUELTA
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.PasoPlanTrasEvaluacion
import pe.saniape.app.data.staff.ServicioUnaVez
import pe.saniape.app.data.staff.aServicioUnaVez
import pe.saniape.app.data.staff.hojaPlanTieneQueOfrecer
import pe.saniape.app.data.staff.pasoPlanTrasEvaluacion
import pe.saniape.app.data.staff.PlanTrasEvaluacionRepo
import pe.saniape.app.data.staff.ProcedimientoRef
import pe.saniape.app.data.staff.ResultadoValoresPlan
import pe.saniape.app.data.staff.TerapeutaConEsp
import pe.saniape.app.data.staff.aServicioPlan
import pe.saniape.app.data.staff.abonoAlPlan
import pe.saniape.app.data.staff.leerPrecio
import pe.saniape.app.data.staff.listaDelPlan
import pe.saniape.app.data.staff.modoDeServicioPlan
import pe.saniape.app.data.staff.planPrellenado
import pe.saniape.app.data.staff.servicioSugerido
import pe.saniape.app.data.staff.textoPrecioCampo
import pe.saniape.app.data.staff.valoresDelPlan
import pe.saniape.app.ui.clinica.pacientes.ChipsCobro
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.TarjetaForm
import pe.saniape.app.ui.clinica.pacientes.TratamientoNuevo
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.clinica.presupuestos.CampoPrecioPresu
import pe.saniape.app.ui.theme.Sania

/** El plan recién creado, para seguir con «Agendar las N» (el plan entero). */
data class PlanCreadoTrasEvaluacion(
    val tratamientoId: String,
    val nombre: String,
    val faltan: Int,
    val terapeutaId: String?,
    val desde: String,
    val hora: String,
)

/**
 * «🩺 Plan de tratamiento» tras completar la evaluación (T3, 24/10/2026; gemelo
 * de components/citas/PlanTrasEvaluacion de la web; reglas en
 * data/staff/PlanTrasEvaluacion.kt). Todo viene propuesto: el diagnóstico de la
 * evaluación, el servicio sugerido, las sesiones y el precio del catálogo
 * (negociable, «↺ Precio de lista»), quien evaluó. «Crear plan y agendar» crea
 * por el camino de siempre (guardarTratamientoNuevo → /api/staff/tratamiento/accion),
 * cobra el abono si lo hubo (/api/staff/pago/registrar) y sigue con el plan
 * entero ([onCreado] con agendar = true). «Solo crear plan» y «Ahora no».
 * Lo que no cabe acá (plantilla, campaña, presupuesto, servicio único, unidades,
 * otra moneda) va a [onMasOpciones]: el formulario de siempre. Si el paciente ya
 * tiene un plan abierto, [onYaTienePlan] (la lista de siempre).
 * Un servicio de una vez (pago único / unidades) que la cita permite registrar
 * realizado ([enVisita]) ofrece «✓ Se realizó en esta visita» ([onServicioEnVisita]:
 * el mismo diálogo y POST de la tarjeta), como la web: un solo camino.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HojaPlanTrasEvaluacion(
    ctx: ContextoStaff,
    cita: CitaStaff,
    diagnostico: String?,
    /** El formulario de siempre con el servicio y la modalidad elegidos acá (o ninguno). */
    onMasOpciones: (servicioId: String?, modalidad: String?) -> Unit,
    onYaTienePlan: () -> Unit,
    onCerrar: (cambio: Boolean) -> Unit,
    onCreado: (plan: PlanCreadoTrasEvaluacion, agendar: Boolean) -> Unit,
    /** La cita permite «✓ Se realizó en esta visita» (ofreceServicioEnVisita). */
    enVisita: Boolean = false,
    /** El catálogo de una vez de la agenda (con tipo_clinico); si falta, se deduce del servicio. */
    serviciosUnaVez: List<ServicioUnaVez> = emptyList(),
    onServicioEnVisita: ((servicioId: String) -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val pid = cita.pacienteId ?: return
    val verHc = pe.saniape.app.data.staff.LocalVerHistoriaClinica.current
    val cobra = ctx.puede("pagos")
    val moneda = pe.saniape.app.data.staff.monedaDeFila(cita.sedeId)
    val otraMoneda = !pe.saniape.app.data.staff.mismaMonedaQueLaClinica(ctx.moneda, listOf(moneda))

    var cargando by remember(cita.id) { mutableStateOf(true) }
    var procedimientos by remember(cita.id) { mutableStateOf<List<ProcedimientoRef>>(emptyList()) }
    var terapeutas by remember(cita.id) { mutableStateOf<List<TerapeutaConEsp>>(emptyList()) }
    var servicio by remember(cita.id) { mutableStateOf<ProcedimientoRef?>(null) }
    var modalidad by remember(cita.id) { mutableStateOf(MODALIDAD_PAQUETE) }
    var sesiones by remember(cita.id) { mutableStateOf("10") }
    var precio by remember(cita.id) { mutableStateOf("") }
    var terapeutaId by remember(cita.id) { mutableStateOf(cita.terapeutaId) }
    var diag by remember(cita.id) { mutableStateOf(diagnostico.orEmpty()) }
    var abono by remember(cita.id) { mutableStateOf("") }
    var metodo by rememberMetodoPagoInicial(pid)
    var guardando by remember(cita.id) { mutableStateOf(false) }
    var error by remember(cita.id) { mutableStateOf<String?>(null) }
    val puedeEnVisita = enVisita && onServicioEnVisita != null
    fun unaVezDe(p: ProcedimientoRef): ServicioUnaVez = serviciosUnaVez.find { it.id == p.id } ?: p.aServicioUnaVez()

    fun elegir(p: ProcedimientoRef) {
        servicio = p
        val s = p.aServicioPlan()
        val pre = planPrellenado(s)
        modalidad = pre.modalidad
        sesiones = pre.sesiones.toString()
        precio = textoPrecioCampo(listaDelPlan(s, pre.modalidad, pre.sesiones))
        error = null
    }

    // UNA ida, en paralelo: ¿ya tiene un plan abierto?, el catálogo, los profesionales
    // y qué se elige tras una evaluación (el «más usado»).
    LaunchedEffect(cita.id) {
        if (otraMoneda) { onMasOpciones(null, null); return@LaunchedEffect }
        val activo = PlanTrasEvaluacionRepo.tieneTratamientoActivo(pid)
        // Sin poder leer sus planes no se concluye que no tenga: la lista de siempre lo resuelve.
        if (activo != false) { onYaTienePlan(); return@LaunchedEffect }
        procedimientos = runCatching { PacientesRepo.procedimientos() }.getOrDefault(emptyList())
        terapeutas = runCatching { PacientesRepo.terapeutasConEspecialidad() }.getOrDefault(emptyList())
        val usos = PlanTrasEvaluacionRepo.usosTrasEvaluacion()
        val espsProf = terapeutas.find { it.id == cita.terapeutaId }?.especialidadIds.orEmpty()
        val candidatos = candidatosDelPlan(procedimientos, cita.especialidadId, espsProf)
        val sugerido = servicioSugerido(candidatos.map { it.aServicioPlan() }, usos = usos, excluir = cita.procedimientoId)
            ?.let { id -> procedimientos.find { it.id == id } }
        // Lo que no va por sesiones (servicio único, consulta, unidades) se arma en el
        // formulario de siempre: abrir esta hoja ahí sería un toque de más (como la web).
        // Salvo lo de una vez que la cita permite registrar realizado: la hoja se queda
        // y ofrece «✓ Se realizó en esta visita».
        if (sugerido != null && pasoPlanTrasEvaluacion(unaVezDe(sugerido), puedeEnVisita) == PasoPlanTrasEvaluacion.FORMULARIO) {
            onMasOpciones(sugerido.id, null); return@LaunchedEffect
        }
        if (!hojaPlanTieneQueOfrecer(candidatos.map { unaVezDe(it) }, puedeEnVisita)) { onMasOpciones(null, null); return@LaunchedEffect }
        sugerido?.let { elegir(it) }
        cargando = false
    }

    val espsProf = terapeutas.find { it.id == terapeutaId }?.especialidadIds.orEmpty()
    val candidatos = candidatosDelPlan(procedimientos, cita.especialidadId, espsProf)
    val s = servicio?.aServicioPlan()
    val modo = modoDeServicioPlan(s)
    val n = sesiones.trim().toIntOrNull() ?: 0
    val lista = listaDelPlan(s, modalidad, n)
    val previa = if (s != null && modo == "sesiones") valoresDelPlan(
        s, modalidad, sesiones, precio, diag, terapeutaId, cita.id, pe.saniape.app.data.staff.hoyClinicaIso(), moneda,
    ) else null
    val lecturaAbono = leerPrecio(abono, moneda)
    val montoAbono = if (lecturaAbono.ok) lecturaAbono.valor ?: 0.0 else 0.0

    fun crear(agendar: Boolean) {
        val p = servicio ?: return
        val v = when (previa) {
            is ResultadoValoresPlan.Ok -> previa.valores
            is ResultadoValoresPlan.Error -> { error = previa.mensaje; return }
            null -> return
        }
        if (!lecturaAbono.ok) { error = "Abono: ${lecturaAbono.error}"; return }
        guardando = true
        scope.launch {
            val nuevo = TratamientoNuevo(
                procedimientoId = v.procedimientoId, terapeutaId = v.terapeutaId, modalidad = v.modalidad,
                totalSesiones = v.totalSesiones, precioPaquete = v.precioPaquete, precioPorSesion = v.precioPorSesion,
                precioAcordado = v.precioAcordado, diagnostico = v.diagnostico, citaOrigenId = v.citaOrigenId,
                medicacion = null, proximoControl = null, fechaInicio = v.fechaInicio,
            )
            val r = pe.saniape.app.ui.clinica.pacientes.guardarTratamientoNuevo(
                pid, nuevo, cita.pacienteNombre,
                textoExito = "Plan creado: ${p.nombre}" + if (v.modalidad == MODALIDAD_PAQUETE) " · ${v.totalSesiones} sesiones" else "",
            )
            val id = r.tratamientoId
            if (!r.creado || id == null) {
                guardando = false
                // Creado sin id (cola offline) o a medias: no se ofrece crearlo de nuevo.
                if (r.creado) onCerrar(true)
                return@launch
            }
            if (cobra && montoAbono > 0) {
                val saldo = if (v.modalidad == MODALIDAD_PAQUETE) PlanTrasEvaluacionRepo.pagadoDe(id)?.let { v.total - it } else null
                val monto = abonoAlPlan(montoAbono, saldo)
                if (monto <= 0) pe.saniape.app.ui.Toaster.info("El plan ya estaba pagado: no se registró el abono")
                else {
                    val pago = PacientesRepo.registrarPagoDetalle(id, monto, metodo, "Abono al crear el plan")
                    if (pago.registrada) pe.saniape.app.ui.Toaster.exito("Abono de ${pe.saniape.app.data.staff.formatearDinero(monto, moneda)} registrado")
                    else pe.saniape.app.ui.Toaster.error("El plan quedó creado, pero el abono no se registró: ${pago.rechazo?.error ?: "regístralo desde la ficha"}")
                }
            }
            guardando = false
            val horario = pe.saniape.app.data.staff.parsearHorariosAtencion(
                runCatching { PacientesRepo.horarioAtencionCrudo(cita.sedeId) }.getOrNull())
            onCreado(PlanCreadoTrasEvaluacion(
                tratamientoId = id, nombre = p.nombre,
                faltan = maxOf(1, n),
                terapeutaId = v.terapeutaId,
                desde = pe.saniape.app.data.staff.desdePlanTrasAtencion(pe.saniape.app.data.staff.hoyClinicaIso(), cita.fecha, horario),
                hora = cita.hora.take(5).ifBlank { "09:00" },
            ), agendar)
        }
    }

    val enSesiones = s != null && modo == "sesiones"
    val enVisitaAhora = !enSesiones &&
        servicio?.let { pasoPlanTrasEvaluacion(unaVezDe(it), puedeEnVisita) } == PasoPlanTrasEvaluacion.EN_VISITA
    DialogoForm(
        titulo = "🩺 Plan de tratamiento",
        subtitulo = cita.pacienteNombre,
        textoAccion = when {
            guardando -> "Creando…"
            enVisitaAhora -> "✓ Se realizó en esta visita"
            s != null && !enSesiones -> "Continuar en el formulario"
            else -> "Crear plan y agendar"
        },
        accionHabilitada = !guardando && !cargando && s != null,
        textoCancelar = "Ahora no",
        onCancelar = { if (!guardando) onCerrar(false) },
        onAccion = {
            val sid = servicio?.id
            when {
                enSesiones -> crear(true)
                enVisitaAhora && sid != null -> onServicioEnVisita?.invoke(sid)
                else -> onMasOpciones(sid, null)
            }
        },
    ) {
        if (cargando) {
            Text("Preparando el plan…", color = c.textoSuave, fontSize = 13.sp)
            return@DialogoForm
        }
        Text("Desde la evaluación · todo viene propuesto, cámbialo si hace falta.", color = c.textoSuave, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        TarjetaForm(titulo = "Plan", icono = "📋") {
            if (verHc) {
                EtqForm("Diagnóstico")
                OutlinedTextField(
                    value = diag, onValueChange = { diag = it }, colors = coloresCampoForm(),
                    modifier = Modifier.fillMaxWidth(), minLines = 1,
                )
                Spacer(Modifier.height(10.dp))
            }
            EtqForm("Servicio")
            pe.saniape.app.ui.clinica.SelectorBuscable(
                items = candidatos, elegido = servicio, etiqueta = { it.nombre },
                placeholder = "Elige el servicio…", onElegir = { elegir(it) },
                detalle = { p -> p.precio.takeIf { it > 0 }?.let { pe.saniape.app.data.staff.formatearDinero(it, moneda) } },
            )
            if (enVisitaAhora) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Se hace de una vez. Si se le hizo hoy, regístralo realizado" + (if (cobra) " y cóbralo" else "") +
                        " en un paso; si es para otro día, prográmalo en el formulario.",
                    color = c.textoSuave, fontSize = 12.sp,
                )
            } else if (s != null && !enSesiones) {
                Spacer(Modifier.height(6.dp))
                Text("Este servicio no va por sesiones: se arma en el formulario completo.", color = c.textoSuave, fontSize = 12.sp)
            }
            if (enSesiones) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        EtqForm("Cobro")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(MODALIDAD_PAQUETE to "Paquete", MODALIDAD_SUELTA to "Por sesión").forEach { (m, t) ->
                                val on = modalidad == m
                                Box(
                                    Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                                        .background(if (on) c.navy else c.superficie)
                                        .border(1.dp, if (on) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                        .clickable {
                                            modalidad = m
                                            precio = textoPrecioCampo(listaDelPlan(s, m, n))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                ) { Text(t, color = if (on) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    Column(Modifier.weight(0.6f)) {
                        EtqForm("Sesiones")
                        OutlinedTextField(
                            value = sesiones,
                            onValueChange = { t ->
                                sesiones = t.filter { it.isDigit() }.take(3)
                                // El precio del paquete sigue a las sesiones si estaba en la lista.
                                val nn = sesiones.toIntOrNull() ?: 0
                                val antes = listaDelPlan(s, modalidad, n)
                                if (modalidad == MODALIDAD_PAQUETE && (precio.isBlank() || leerPrecio(precio, moneda).valor == antes)) {
                                    precio = textoPrecioCampo(listaDelPlan(s, modalidad, nn))
                                }
                            },
                            singleLine = true, colors = coloresCampoForm(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                CampoPrecioPresu(
                    etiqueta = if (modalidad == MODALIDAD_PAQUETE) "Precio del paquete" else "Precio por sesión",
                    valor = precio, onCambio = { precio = it }, lista = lista, moneda = moneda,
                    habilitado = !guardando,
                )
            }
            Spacer(Modifier.height(10.dp))
            EtqForm("Profesional")
            pe.saniape.app.ui.clinica.SelectorBuscable(
                items = terapeutas, elegido = terapeutas.find { it.id == terapeutaId }, etiqueta = { it.nombre },
                placeholder = "Sin asignar", onElegir = { terapeutaId = it.id }, textoBuscar = "🔎 Buscar profesional…",
            )
        }

        // «¿Pagó?» del plan recién creado: opcional, nada lo bloquea.
        val total = (previa as? ResultadoValoresPlan.Ok)?.valores?.total ?: 0.0
        if (cobra && enSesiones && total > 0) {
            Spacer(Modifier.height(12.dp))
            TarjetaForm(titulo = "¿Deja un abono? (opcional)", icono = "💵") {
                ChipsCobro(total = total, saldo = null, valor = abono, onElegir = { abono = it }, moneda = moneda, habilitado = !guardando)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = abono, onValueChange = { t -> abono = t.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                    placeholder = { Text("0.00", color = c.textoSuave) }, singleLine = true, colors = coloresCampoForm(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                ChipsMetodoPago(metodo) { metodo = it }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = c.error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (enSesiones) {
                Text("Solo crear plan", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !guardando) { crear(false) }.padding(vertical = 6.dp))
            }
            Text(if (enVisitaAhora) "Programarlo →" else "Más opciones →", color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = !guardando) { onMasOpciones(servicio?.id, if (enSesiones) modalidad else null) }.padding(vertical = 6.dp))
        }
    }
}

/** Los servicios del selector: los de la especialidad de la cita (o del profesional), como el formulario. */
private fun candidatosDelPlan(todos: List<ProcedimientoRef>, especialidadCita: String?, espsProf: List<String>): List<ProcedimientoRef> =
    todos.filter { p ->
        when {
            !especialidadCita.isNullOrBlank() -> p.especialidadId == null || p.especialidadId == especialidadCita
            espsProf.isNotEmpty() -> p.especialidadId == null || p.especialidadId in espsProf
            else -> true
        }
    }
