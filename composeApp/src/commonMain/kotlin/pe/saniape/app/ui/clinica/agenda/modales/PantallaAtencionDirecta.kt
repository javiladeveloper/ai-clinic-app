package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.AtencionDirectaRepo
import pe.saniape.app.data.staff.CAMPOS_PSICO_INICIAL
import pe.saniape.app.data.staff.SIGUIENTES_PRIMERA_CONSULTA
import pe.saniape.app.data.staff.ayudaSiguiente
import pe.saniape.app.data.staff.planConSiguiente
import pe.saniape.app.data.staff.CobroRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DatosAtencionDirecta
import pe.saniape.app.data.staff.EvolucionSesionPsico
import pe.saniape.app.data.staff.MetodoPagoPreferido
import pe.saniape.app.data.staff.avisoGuardadoAtencionDirecta
import pe.saniape.app.data.staff.bloqueoAtencionDirecta
import pe.saniape.app.data.staff.camposEvolucionAnterior
import pe.saniape.app.data.staff.evolucionConContenido
import pe.saniape.app.data.staff.fechaLegibleCorta
import pe.saniape.app.data.staff.leerPrecio
import pe.saniape.app.data.staff.monedaDeFila
import pe.saniape.app.data.staff.ofrecePagoAtencion
import pe.saniape.app.data.staff.pasosCobroAtencionDirecta
import pe.saniape.app.data.staff.precioSugeridoAtencionDirecta
import pe.saniape.app.data.staff.simboloMoneda
import pe.saniape.app.data.staff.textoBotonAtencionDirecta
import pe.saniape.app.data.staff.textoPrecioCampo
import pe.saniape.app.data.staff.tituloAtencionDirecta
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.BloqueContextoCaso
import pe.saniape.app.ui.clinica.BloqueEvolucionAnterior
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.clinica.recordarContextoCaso
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.Sania

/**
 * ⚡ «▶ Atender» / «📝 Evolución» de una cita de psicología SIN tratamiento —
 * gemelo de `components/citas/AtenderRapido.tsx` (web). UNA pantalla:
 * el contexto del caso y la última evolución (lo que se LEE), los 4 campos de la
 * evolución (los 3 secundarios opcionales) y «💵 ¿El paciente pagó?».
 *
 * Guardar: POST /api/staff/cita/atender-rapido (la evolución en la historia
 * psicológica, ligada a la cita, y la cita Completada) y, si pagó, el cobro de la
 * cita con los endpoints de siempre (ajustar-monto si no tenía precio, y cobrar
 * con el método real). Ya atendida: modo edición — sin «¿pagó?», no vuelve a
 * completar ni a cobrar. [onGuardado] recibe si quedó con evolución escrita.
 */
@Composable
fun PantallaAtencionDirecta(
    ctx: ContextoStaff,
    citaId: String,
    onCerrar: () -> Unit,
    onGuardado: (conEvolucion: Boolean) -> Unit,
    /**
     * Primera consulta con «¿Qué sigue?» = terapia o evaluación: la agenda abre el
     * paquete (quien cobra) o el pedido para recepción. [servicioId] = el de la
     * evaluación psicológica (null en terapia).
     */
    onSiguiente: (tipo: String, citaId: String, servicioId: String?) -> Unit = { _, _, _ -> },
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var datos by remember(citaId) { mutableStateOf<DatosAtencionDirecta?>(null) }
    var errorCarga by remember(citaId) { mutableStateOf<String?>(null) }
    var intento by remember(citaId) { mutableIntStateOf(0) }
    var valor by remember(citaId) { mutableStateOf(EvolucionSesionPsico()) }
    var pago by remember(citaId) { mutableStateOf(false) }
    var monto by remember(citaId) { mutableStateOf("") }
    var guardando by remember(citaId) { mutableStateOf(false) }
    var errorGuardar by remember(citaId) { mutableStateOf<String?>(null) }
    var inicial by remember(citaId) { mutableStateOf(CAMPOS_PSICO_INICIAL.associate { it.first to "" }) }
    var siguiente by remember(citaId) { mutableStateOf<String?>(null) }
    var firmo by remember(citaId) { mutableStateOf<Boolean?>(null) }
    var servicioEvalId by remember(citaId) { mutableStateOf<String?>(null) }
    var verTests by remember(citaId) { mutableStateOf(false) }
    val acciones = pe.saniape.app.ui.recordarAcciones()

    ManejarAtras(activo = true) { if (!guardando) onCerrar() }

    LaunchedEffect(citaId, intento) {
        errorCarga = null
        when (val r = AtencionDirectaRepo.leer(citaId)) {
            is AtencionDirectaRepo.Lectura.Ok -> {
                val primera = datos == null
                datos = r.datos
                // Al abrir: lo ya escrito y el precio de la cita. Al releer tras un
                // guardado a medias, lo que se escribió en pantalla se conserva.
                if (primera) {
                    valor = r.datos.evolucion ?: EvolucionSesionPsico()
                    inicial = CAMPOS_PSICO_INICIAL.associate { (k, _, _) ->
                        k to (r.datos.inicial?.get(k) ?: if (k == "motivo_consulta") r.datos.motivoPaciente.orEmpty() else "")
                    }
                    monto = r.datos.cita.costo?.takeIf { it > 0 }?.let { textoPrecioCampo(it) }.orEmpty()
                }
            }
            is AtencionDirectaRepo.Lectura.Error -> if (datos == null) errorCarga = r.mensaje
        }
    }

    val d = datos
    val cita = d?.cita
    val atender = d?.atender == true
    val moneda = monedaDeFila(cita?.sedeId)
    val ofrecePago = d != null && ofrecePagoAtencion(atender, cita?.tipo, cita?.pagada == true, ctx.puede("pagos"), d.soloLectura)
    // La cita sin precio: se sugiere el de la sesión de psicoterapia (editable).
    LaunchedEffect(cita?.id, ofrecePago) {
        val ci = cita ?: return@LaunchedEffect
        if (!ofrecePago || (ci.costo ?: 0.0) > 0 || monto.isNotBlank()) return@LaunchedEffect
        precioSugeridoAtencionDirecta(AtencionDirectaRepo.serviciosPrecio(), ci.especialidadId)
            ?.let { if (monto.isBlank()) monto = textoPrecioCampo(it) }
    }
    val esInicial = d?.esInicial == true
    // Primera consulta: ¿firmó el consentimiento? y el servicio de la evaluación (si la clínica lo tiene).
    LaunchedEffect(cita?.id, esInicial) {
        val ci = cita ?: return@LaunchedEffect
        if (!esInicial) return@LaunchedEffect
        firmo = AtencionDirectaRepo.firmoConsentimiento(ci.pacienteId)
        val evals = AtencionDirectaRepo.serviciosPrecio().filter { it.tipoClinico == "evaluacion_psicologica" }
        servicioEvalId = (evals.firstOrNull { it.especialidadId == ci.especialidadId } ?: evals.firstOrNull { it.especialidadId == null } ?: evals.firstOrNull())?.id
    }
    val metodo = rememberMetodoPagoInicial(cita?.pacienteId)
    val lectura = leerPrecio(monto, moneda)
    val montoValido = lectura.valor?.takeIf { lectura.ok && it > 0 }
    val conTexto = if (esInicial) inicial.values.any { it.isNotBlank() } else evolucionConContenido(valor)
    val bloqueo = if (d == null) null else bloqueoAtencionDirecta(
        soloLectura = d.soloLectura, atender = atender, conTexto = conTexto, yaTeniaEvolucion = (if (esInicial) d.inicial else d.evolucion) != null,
        pago = ofrecePago && pago, montoValido = montoValido != null, montoError = lectura.error,
    )
    val cobrara = ofrecePago && pago && montoValido != null
    val textoBoton = when {
        guardando -> "Guardando…"
        d == null -> "Guardar"
        esInicial && !atender -> "Guardar primera consulta"
        esInicial && !conTexto && !cobrara -> "✓ Completar sin anotar"
        else -> textoBotonAtencionDirecta(atender, conTexto, if (cobrara) textoSoles(montoValido!!, moneda) else null)
    }

    // Suspendida (no lanzada aparte): la pantalla se cierra DESPUÉS del cobro; si
    // no, al cerrarse se cancelaría a mitad.
    suspend fun cobrarAtencion(ci: pe.saniape.app.data.staff.CitaAtencionDirecta, valorMonto: Double, conEvo: Boolean) {
        val pasos = pasosCobroAtencionDirecta(ci.tipo, ci.costo, valorMonto) ?: return
        fun avisarFallo(motivo: String) = Toaster.error(
            "La atención quedó registrada, pero el cobro no entró a caja: $motivo. Cóbralo con 💰 desde la agenda.")
        pasos.fijarPrecio?.let { p ->
            CobroRepo.ponerPrecio(ci.id, p)?.let { avisarFallo(it); return }
        }
        val r = AgendaRepo.cobrarCita(ci.id, metodo.value, "cobrar", null, costoAcordado = pasos.costoAcordado)
        when {
            r.rechazo != null -> avisarFallo(r.rechazo!!.error)
            !r.registrada -> avisarFallo("no se pudo enviar")
            r.encolada -> {
                MetodoPagoPreferido.recordar(ci.pacienteId, metodo.value)
                Toaster.info("Atención registrada. El cobro se registrará al volver la señal.")
            }
            r.yaEstaba -> Toaster.info("Atención registrada. Esta cita ya estaba cobrada: no se cobró de nuevo")
            else -> {
                MetodoPagoPreferido.recordar(ci.pacienteId, metodo.value)
                Toaster.exito("${if (conEvo) "Atención registrada" else "Cita completada"} · cobrado ${textoSoles(valorMonto, moneda)} (${metodo.value})")
            }
        }
    }

    fun guardar() {
        val dd = datos ?: return
        if (guardando || bloqueo != null) return
        guardando = true
        errorGuardar = null
        scope.launch {
            try {
                val ini = if (dd.esInicial) inicial + ("plan_trabajo" to planConSiguiente(inicial["plan_trabajo"].orEmpty(), siguiente)) else null
                val r = AtencionDirectaRepo.guardar(dd.cita.id, valor, cobrara, ini)
                if (!r.ok) {
                    errorGuardar = r.error
                    // La evolución pudo quedar aunque la cita no se completó: se relee.
                    if (r.atencionId != null) intento++
                    return@launch
                }
                if (cobrara) cobrarAtencion(dd.cita, montoValido!!, conTexto)
                else Toaster.exito(if (dd.esInicial && conTexto) "Listo: primera consulta guardada en su historia" else avisoGuardadoAtencionDirecta(dd.atender, conTexto))
                onGuardado(conTexto || dd.evolucion != null || dd.inicial != null)
                val sig = siguiente
                onCerrar()
                if (dd.esInicial && (sig == "terapia" || sig == "evaluacion")) {
                    onSiguiente(sig, dd.cita.id, if (sig == "evaluacion") servicioEvalId else null)
                }
            } finally {
                guardando = false
            }
        }
    }

    val tecladoAbierto = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.lg, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (d == null) "▶ Atender"
                    else if (d.esInicial) "${if (atender) "▶" else "📝"} Primera consulta · ${cita?.pacienteNombre?.takeIf { it.isNotBlank() } ?: "Paciente"}"
                    else tituloAtencionDirecta(atender, d.evolucion != null, cita?.pacienteNombre),
                    color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Con el teclado abierto el botón del final queda tapado: sube acá.
                if (tecladoAbierto && d != null) {
                    val ok = !guardando && bloqueo == null
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.md.dp))
                            .background(if (ok) c.sobreNavy else c.sobreNavy.copy(alpha = 0.35f))
                            .clickable(enabled = ok) { guardar() }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) { Text("Guardar", color = c.navyDark, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.xl),
                verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
            ) {
                when {
                    errorCarga != null -> {
                        Text(errorCarga!!, color = c.error, fontSize = Sania.txt.cuerpo)
                        BotonSecundario("Reintentar") { intento++ }
                        BotonSecundario("Cerrar") { onCerrar() }
                    }
                    d == null || cita == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("  Abriendo la atención…", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                    }
                    else -> {
                        Text(
                            fechaLegibleCorta(cita.fecha) + (cita.hora?.let { " · ${hora12(it)}" } ?: ""),
                            color = c.textoSuave, fontSize = Sania.txt.pequeno,
                        )
                        errorGuardar?.let {
                            Text("⚠ $it", color = c.error, fontSize = Sania.txt.pequeno,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .background(c.errorBg).padding(Sania.dim.md))
                        }
                        // Lo que se LEE mientras se escribe: el caso y la última evolución.
                        val contexto = recordarContextoCaso(
                            activo = ctx.puede("historia_clinica"), pacienteId = cita.pacienteId,
                            tratamientoId = null, especialidadId = cita.especialidadId,
                        )
                        if (!d.esInicial) {
                            BloqueContextoCaso(contexto, abiertoInicial = d.anterior == null)
                            d.anterior?.let { (fecha, evo) -> BloqueEvolucionAnterior(fecha, camposEvolucionAnterior(evo)) }
                        }

                        val habilitado = !guardando && !d.soloLectura
                        if (d.esInicial) {
                            Text("Es su primera vez: lo que escribas queda como la historia inicial del paciente.",
                                color = c.textoSuave, fontSize = 12.sp)
                            CAMPOS_PSICO_INICIAL.forEach { (k, etiqueta, ayuda) ->
                                CampoEvolucion(etiqueta, inicial[k].orEmpty(), ayuda, if (k == "relato") 4 else 2, habilitado) {
                                    inicial = inicial + (k to it)
                                }
                            }
                            // ¿Qué sigue? (terapia / evaluación / derivar / solo esta consulta)
                            val forma = RoundedCornerShape(Sania.shape.md.dp)
                            Column(
                                Modifier.fillMaxWidth().clip(forma).border(1.5.dp, c.borde, forma).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("¿QUÉ SIGUE?", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                SIGUIENTES_PRIMERA_CONSULTA.filter { it.first != "evaluacion" || servicioEvalId != null }.chunked(2).forEach { fila ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        fila.forEach { (v, texto) ->
                                            val sel = siguiente == v
                                            Box(
                                                Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                                    .background(if (sel) c.navy.copy(alpha = 0.12f) else c.superficie)
                                                    .border(1.5.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                                    .clickable(enabled = habilitado) { siguiente = if (sel) null else v }
                                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                            ) { Text(texto, color = if (sel) c.navy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                        }
                                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                                siguiente?.let { Text(ayudaSiguiente(it, ctx.puede("pagos")), color = c.textoSuave, fontSize = 12.sp) }
                                if (firmo == false) {
                                    Text("⚠ Aún no firmó el consentimiento informado: fírmalo desde su ficha.",
                                        color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            CampoEvolucion("Temas trabajados y evolución", valor.evolucion, "Qué se trabajó hoy y cómo se encuentra…", 4, habilitado) {
                                valor = valor.copy(evolucion = it)
                            }
                            CampoEvolucion("Estado emocional / examen mental", valor.estado, "Opcional", 2, habilitado) {
                                valor = valor.copy(estado = it)
                            }
                            CampoEvolucion("Tareas e indicaciones", valor.tareas, "Opcional", 2, habilitado) {
                                valor = valor.copy(tareas = it)
                            }
                            CampoEvolucion("Plan para la próxima sesión", valor.plan, "Opcional", 2, habilitado) {
                                valor = valor.copy(plan = it)
                            }
                        }

                        // 🧪 Un test en esta misma atención (PHQ-9, 16PF…): queda en la historia
                        // del paciente, sin abrir una evaluación. Se guarda solo (gemelo de la web).
                        if (ctx.puede("historia_clinica") && (ctx.esAdmin || ctx.miTerapeutaId != null) && !d.soloLectura) {
                            if (verTests) {
                                val vmTests: pe.saniape.app.ui.clinica.psico.EvaluacionPsicoViewModel =
                                    androidx.lifecycle.viewmodel.compose.viewModel(key = "tests-paciente:${cita.pacienteId}:${cita.id}") {
                                        pe.saniape.app.ui.clinica.psico.EvaluacionPsicoViewModel(null, null, pacienteTests = cita.pacienteId to cita.id)
                                    }
                                val forma = RoundedCornerShape(Sania.shape.md.dp)
                                Column(
                                    Modifier.fillMaxWidth().clip(forma).border(1.5.dp, c.borde, forma).padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🧪 Tests", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text("Ocultar", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { verTests = false })
                                    }
                                    when {
                                        vmTests.cargando -> Text("Cargando tests…", color = c.textoSuave, fontSize = 12.sp)
                                        vmTests.error != null -> Text(vmTests.error!!, color = c.error, fontSize = 12.sp)
                                        else -> pe.saniape.app.ui.clinica.psico.SeccionTests(vmTests, acciones)
                                    }
                                }
                            } else {
                                BotonSecundario("🧪 Aplicar un test", habilitado = !guardando) { verTests = true }
                            }
                        }

                        if (ofrecePago) {
                            val forma = RoundedCornerShape(Sania.shape.md.dp)
                            Column(
                                Modifier.fillMaxWidth().clip(forma)
                                    .background(if (pago) c.okBg else c.superficie)
                                    .border(1.5.dp, if (pago) c.ok else c.borde, forma)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = !guardando) { pago = !pago },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = pago, onCheckedChange = { if (!guardando) pago = it },
                                        colors = CheckboxDefaults.colors(checkedColor = c.ok),
                                    )
                                    Text("💵 ¿El paciente pagó?", color = c.texto, fontSize = Sania.txt.cuerpo,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (!pago && (cita.costo ?: 0.0) > 0) {
                                        Text(textoSoles(cita.costo!!, moneda), color = c.textoSuave, fontSize = 12.sp)
                                    }
                                }
                                if (pago) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Monto (${simboloMoneda(moneda)})", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = monto, onValueChange = { monto = it },
                                        enabled = !guardando, singleLine = true, colors = coloresCampoForm(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text("Método", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 6.dp))
                                    ChipsMetodoPago(metodo.value) { metodo.value = it }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }

                        // Por qué no se puede guardar todavía (ámbar), y el botón que dice lo que pasa.
                        if (!guardando && bloqueo != null) {
                            Text(bloqueo, color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f)) { BotonSecundario("Cancelar", habilitado = !guardando) { onCerrar() } }
                            Box(Modifier.weight(2f)) {
                                val ok = !guardando && bloqueo == null
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = Sania.dim.boton)
                                        .clip(RoundedCornerShape(Sania.shape.md.dp))
                                        .background(if (ok) c.navy else c.navy.copy(alpha = 0.4f))
                                        .clickable(enabled = ok) { guardar() }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (guardando) {
                                        CircularProgressIndicator(color = c.sobreNavy, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(textoBoton, color = c.sobreNavy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(Sania.dim.xxl))
                    }
                }
            }
        }
    }
}

@Composable
private fun CampoEvolucion(etiqueta: String, valor: String, placeholder: String, lineas: Int, habilitado: Boolean, onChange: (String) -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth()) {
        Text(etiqueta.uppercase(), color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = valor, onValueChange = { onChange(it.take(12000)) },
            enabled = habilitado, minLines = lineas, colors = coloresCampoForm(),
            placeholder = { Text(placeholder, color = c.textoSuave, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BotonSecundario(texto: String, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().heightIn(min = Sania.dim.boton).clip(RoundedCornerShape(Sania.shape.md.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = c.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
