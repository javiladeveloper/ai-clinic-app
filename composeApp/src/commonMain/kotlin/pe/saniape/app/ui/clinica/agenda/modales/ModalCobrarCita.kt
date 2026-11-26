package pe.saniape.app.ui.clinica.agenda.modales

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.FilaPago
import pe.saniape.app.data.staff.PartePago
import pe.saniape.app.data.staff.filasIniciales
import pe.saniape.app.data.staff.repartoValido
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.clinica.pacientes.rememberMetodosPago
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.data.staff.simboloMoneda
import pe.saniape.app.data.staff.textoLista
import pe.saniape.app.ui.clinica.presupuestos.CampoPrecioPresu

/**
 * "💵 Registrar cobro" de una Consulta/Evaluación (gemelo del modal de /citas web).
 *
 * Pide el MÉTODO (antes todo se anotaba como efectivo y el arqueo salía mal) y la
 * FECHA DEL PAGO: por defecto HOY (Lima) — el cobro entra a la caja del día en que
 * se cobra (decisión del dueño 2026-10-08; antes era el día de la cita y el arqueo
 * de hoy no cuadraba con el cajón). Recepción la cambia solo si el paciente pagó
 * otro día.
 *
 * Tres destinos del cobro (Renova 2026-08-26): cobrar normal, abonarlo al
 * tratamiento del paciente, o no cobrar (la cita queda en S/ 0 y saldada).
 * [onConfirmar] recibe (método, modo, fecha yyyy-MM-dd, pagos). Sin botón de
 * tarjeta: la app no tiene aquí el flujo de QR de la web.
 *
 * PRECIO ACORDADO (regla del proyecto: el catálogo es la sugerencia, el precio
 * siempre se negocia; gemelo del campo de /citas web, 2026-10-16): arranca en
 * el costo de la cita y «↺ Precio de la cita» lo devuelve. Si en caja se acuerda
 * otro, se cobra ese y llega en `costoAcordado` (el servidor lo guarda en la
 * cita antes de cobrar); igual al de la cita → null.
 *
 * COBRO DIVIDIDO (DALU 2026-10-03): con "Pagó con más de un medio" se reparte el
 * monto en 2 a 4 medios (S/ 20 Efectivo + S/ 20 Yape) y `pagos` llega con las
 * partes validadas; si no, llega null y vale `metodo`. Cobrar se habilita solo
 * cuando el reparto cuadra al céntimo. Las filas viven fuera del `if` del modo:
 * si un cobro responde "incierto", el reintento sale con el MISMO reparto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalCobrarCita(
    cita: CitaStaff,
    onCancelar: () -> Unit,
    onConfirmar: (metodo: String, modo: String, fecha: String, pagos: List<PartePago>?, costoAcordado: Double?, adelanto: Double?) -> Unit,
    guardando: Boolean,
    /** Cómo llama el flujo de la clínica al tipo de la cita ("Diagnóstico"…). */
    nombreTipo: String = cita.tipo ?: "Cita",
    /**
     * Vuelto con otro medio (26/11/2026): si se pasa, el modal ofrece «¿Con cuánto paga?»
     * y avisa aquí, justo ANTES de [onConfirmar], qué vuelto mandar (null = ninguno).
     */
    onVuelto: ((pe.saniape.app.data.staff.VueltoCobro?) -> Unit)? = null,
) {
    // Multipaís: la moneda de la sede de la cita.
    val moneda = pe.saniape.app.data.staff.monedaDeFila(cita.sedeId)
    val c = Sania.colors
    var modo by remember(cita.id) { mutableStateOf("cobrar") }
    // El plan que nació de esta cita trae una campaña que «incluye la evaluación»:
    // lo cobrado va al plan (sin tocar lo que ya eligió quien cobra). Gemelo de
    // debeAbonarPorCampania en /citas web. Sin dato, lo de siempre.
    // Quien cobra ya tocó un destino: la lectura nunca lo pisa. Mientras la lectura
    // no llega (y nadie eligió), Cobrar espera: así no se cobra "normal" por apuro
    // lo que debía abonarse al plan.
    var modoElegido by remember(cita.id) { mutableStateOf(false) }
    var leyendoCampania by remember(cita.id) { mutableStateOf(cita.tipo != "Sesión" && cita.pacienteId != null) }
    androidx.compose.runtime.LaunchedEffect(cita.id) {
        val pid = cita.pacienteId
        try {
            if (cita.tipo != "Sesión" && pid != null &&
                cita.abonado <= 0 && pe.saniape.app.data.staff.CampaniaEntradaRepo.debeAbonar(pid, cita.id) && !modoElegido && modo == "cobrar"
            ) modo = "abonar"
        } finally { leyendoCampania = false }
    }
    var metodo by rememberMetodoPagoInicial(cita.pacienteId)
    // Por defecto HOY (Lima): el cobro entra a la caja del día en que se cobra
    // (decisión del dueño 2026-10-08), no la de la cita. Se cambia solo si el
    // paciente pagó otro día.
    var fecha by remember(cita.id) { mutableStateOf(pe.saniape.app.ui.clinica.agenda.hoyIso()) }
    var mostrarFecha by remember { mutableStateOf(false) }
    val metodos = rememberMetodosPago()
    var dividido by remember(cita.id) { mutableStateOf(false) }
    var filas by remember(cita.id) { mutableStateOf<List<FilaPago>>(emptyList()) }
    // Precio acordado: lo escrito (válido y > 0) o el costo de la cita.
    val costoCita = cita.costo ?: 0.0
    var precio by remember(cita.id) { mutableStateOf(textoLista(costoCita, moneda)) }
    // Vacío = el de la cita; 0 no (para no cobrar nada está "No cobrar"), como la web.
    val precioCobro = pe.saniape.app.data.staff.precioCobroCita(precio, costoCita, moneda)
    val total = precioCobro.total
    val precioInvalido = precioCobro.invalido
    val conDivision = dividido && modo != "gratis"
    // ADELANTO (20/11/2026, gemelo de /citas web): lo ya adelantado y lo que falta;
    // o, si no dejó nada, «Solo deja un adelanto» (el resto se cobra al venir).
    val abonado = cita.abonado.coerceAtLeast(0.0)
    val falta = pe.saniape.app.data.staff.faltaDeCita(total, abonado)
    var conAdelanto by remember(cita.id) { mutableStateOf(false) }
    var adelantoTxt by remember(cita.id) { mutableStateOf("") }
    val ofreceAdelanto = modo == "cobrar" && abonado <= 0 && !dividido && cita.tipo != "Sesión"
    val pideAdelanto = conAdelanto && ofreceAdelanto
    val adelanto = if (pideAdelanto) pe.saniape.app.data.staff.adelantoValido(adelantoTxt, falta, moneda) else null
    // Con el adelanto marcado y sin un monto válido no se cobra (nunca el total por descuido).
    val adelantoInvalido = pideAdelanto && adelanto == null
    val reparto = if (conDivision) repartoValido(filas, falta) else null
    val monto = textoSoles(adelanto ?: falta, moneda)
    // Vuelto: lo entregado menos lo que se cobra ahora; por defecto, con el mismo medio.
    val montoNum = adelanto ?: falta
    var recibidoTxt by remember(cita.id) { mutableStateOf("") }
    var metodoVuelto by remember(cita.id) { mutableStateOf("") }
    val medioVuelto = metodoVuelto.ifBlank { metodo }
    val vueltoMonto = pe.saniape.app.data.staff.calcularVuelto(montoNum, recibidoTxt.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)
    val vueltoCobro = pe.saniape.app.data.staff.vueltoParaEnviar(montoNum, metodo, recibidoTxt, medioVuelto)
    val tipoMin = nombreTipo.lowercase()

    if (mostrarFecha) {
        // Nunca una caja futura (el servidor también lo rechaza): hasta HOY en Lima.
        val topeMillis = pe.saniape.app.data.staff.isoAMillisUtc(pe.saniape.app.ui.clinica.agenda.hoyIso()) ?: Long.MAX_VALUE
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = pe.saniape.app.data.staff.isoAMillisUtc(fecha),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= topeMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                TextButton(onClick = { estado.selectedDateMillis?.let { fecha = millisAIso(it) }; mostrarFecha = false }) {
                    Text("Aceptar", color = c.navy)
                }
            },
            dismissButton = { TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estado) }
    }

    DialogoForm(
        titulo = "💵 Registrar cobro",
        subtitulo = null,
        textoAccion = when {
            guardando -> "Guardando…"
            modo == "gratis" -> "Marcar sin costo"
            modo == "abonar" -> "Cobrar y abonar $monto"
            adelanto != null -> "Cobrar $monto de adelanto"
            abonado > 0 -> "Cobrar $monto que falta"
            else -> "Cobrar $monto"
        },
        accionHabilitada = !guardando && (!conDivision || reparto != null) && (!leyendoCampania || modoElegido) &&
            (modo == "gratis" || (!precioInvalido && !adelantoInvalido)),
        ancla = "cobro",
        anclaAccion = "cobro.confirmar",
        onCancelar = { if (!guardando) onCancelar() },
        onAccion = {
            val acordado = if (modo != "gratis") precioCobro.costoAcordado else null
            onVuelto?.invoke(if (!conDivision && modo != "gratis" && fecha == pe.saniape.app.ui.clinica.agenda.hoyIso()) vueltoCobro else null)
            if (conDivision) reparto?.let { onConfirmar(metodo, modo, fecha, it, acordado, null) }
            else onConfirmar(metodo, modo, fecha, null, acordado, adelanto)
        },
    ) {
        // Qué se cobra: tipo · paciente, y el monto grande.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.okBg)
                .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.md.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("$nombreTipo · ${cita.pacienteNombre ?: "${LocalTerminologiaPaciente.current.Paciente}"}", color = c.textoSuave, fontSize = 12.sp)
            Text(if (modo == "gratis") textoSoles(0.0, moneda) else monto, color = c.texto, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (abonado > 0) {
                Text("Ya dejó ${textoSoles(abonado, moneda)} de adelanto · falta ${textoSoles(falta, moneda)}",
                    color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Precio acordado: el de la cita es la sugerencia; si se acuerda otro, se cobra ese.
        if (modo != "gratis") {
            Spacer(Modifier.height(12.dp))
            CampoPrecioPresu(
                etiqueta = "Precio acordado",
                valor = precio,
                onCambio = { precio = it },
                lista = costoCita,
                moneda = moneda,
                habilitado = !guardando,
                textoBoton = "↺ Precio de la cita",
            )
        }

        Spacer(Modifier.height(12.dp))

        // ¿A dónde va este cobro? Si el paciente decidió entrar en tratamiento,
        // recepción puede abonarlo a su tratamiento o perdonarlo.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOfNotNull(
                Triple("cobrar", "Cobrar normal", "Entra a caja como pago de la $tipoMin"),
                // Con adelanto, lo que falta se cobra como cita (el servidor rechaza abonarlo).
                Triple("abonar", "Cobrar y abonar al tratamiento", "El monto se descuenta de la deuda del tratamiento del ${LocalTerminologiaPaciente.current.paciente}")
                    .takeIf { abonado <= 0 },
                Triple("gratis", "No cobrar",
                    if (abonado > 0) "Se perdona lo que falta: la $tipoMin queda en lo que ya dejó"
                    else "Sigue a tratamiento: la $tipoMin queda en ${simboloMoneda(moneda)} 0"),
            ).forEach { (valor, titulo, detalle) ->
                OpcionModo(titulo, detalle, activo = modo == valor) { if (!guardando) { modo = valor; modoElegido = true } }
            }
        }

        if (modo != "gratis") {
            Spacer(Modifier.height(14.dp))
            Etiqueta("MÉTODO DE PAGO")
            if (!dividido) ChipsMetodoPago(metodo) { metodo = it }
            // Solo un adelanto: deja una parte para separar la cita; el resto al venir.
            if (ofreceAdelanto) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = !guardando) { conAdelanto = !conAdelanto; if (!conAdelanto) adelantoTxt = "" },
                ) {
                    Checkbox(
                        checked = conAdelanto,
                        onCheckedChange = { conAdelanto = it; if (!it) adelantoTxt = "" },
                        enabled = !guardando,
                        colors = CheckboxDefaults.colors(checkedColor = c.navy),
                    )
                    Text("Solo deja un adelanto", color = c.texto, fontSize = 13.sp)
                }
                if (conAdelanto) {
                    androidx.compose.material3.OutlinedTextField(
                        colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
                        value = adelantoTxt,
                        onValueChange = { t -> adelantoTxt = t.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        placeholder = { Text("Monto del adelanto, ej. 20", color = c.textoSuave) },
                        singleLine = true,
                        enabled = !guardando,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when {
                        adelanto != null -> Text("Queda ${textoSoles(pe.saniape.app.data.staff.faltaDeCita(falta, adelanto), moneda)} por pagar cuando venga.",
                            color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        adelantoTxt.isNotBlank() -> Text("El adelanto tiene que ser menor que ${textoSoles(falta, moneda)}.",
                            color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.tourAncla("cobro.dividir").clickable(enabled = !guardando) {
                    dividido = !dividido
                    if (dividido && filas.isEmpty()) filas = filasIniciales(metodos, metodo)
                },
            ) {
                Checkbox(
                    checked = dividido,
                    onCheckedChange = {
                        dividido = it
                        if (it && filas.isEmpty()) filas = filasIniciales(metodos, metodo)
                    },
                    enabled = !guardando,
                    colors = CheckboxDefaults.colors(checkedColor = c.navy),
                )
                Text("Pagó con más de un medio", color = c.texto, fontSize = 13.sp)
            }
            if (dividido) {
                // Tutoriales: "cobro.medios" se cumple al escribir algún monto.
                Column(Modifier.tourAncla("cobro.medios", valor = filas.joinToString("") { it.monto.trim() })) {
                    PagoDividido(total = total, metodos = metodos, filas = filas, onCambiar = { filas = it }, deshabilitado = guardando, moneda = moneda)
                }
            }

            // «¿Con cuánto paga?» → vuelto y con qué medio (26/11/2026, gemelo de CampoVuelto de la
            // web). Solo cobro de HOY con un medio: el servidor ata el vuelto al ingreso de la caja.
            if (onVuelto != null && !conDivision && modo != "gratis" && fecha == pe.saniape.app.ui.clinica.agenda.hoyIso()) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
                    value = recibidoTxt,
                    onValueChange = { t -> recibidoTxt = t.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    placeholder = { Text("¿Con cuánto paga? (opcional)", color = c.textoSuave) },
                    singleLine = true,
                    enabled = !guardando,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vueltoMonto > 0) {
                    Text("Vuelto ${textoSoles(vueltoMonto, moneda)} por:", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (listOf(metodo) + metodos).distinct().forEach { m ->
                            val activo = m == medioVuelto
                            Text(
                                m, color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                    .background(if (activo) c.navy else c.superficie)
                                    .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                                    .clickable(enabled = !guardando) { metodoVuelto = m }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Text(
                        if (vueltoCobro != null) "La caja lo cuenta sola: el ingreso sigue siendo ${textoSoles(montoNum, moneda)}."
                        else "Vuelto con el mismo medio: no cambia la caja.",
                        color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            pe.saniape.app.ui.componentes.EtiquetaConAhora(
                modo = pe.saniape.app.ui.componentes.ModoAhora.FECHA,
                zona = pe.saniape.app.ui.componentes.zonaParaAhora(cita.sedeId), habilitado = !guardando,
                onAhora = { a -> fecha = a.fecha },
            ) { Etiqueta("FECHA DEL PAGO") }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable(enabled = !guardando) { mostrarFecha = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("📅 ${fechaDMA(fecha)}", color = c.texto, fontSize = Sania.txt.cuerpo)
                Text("▾", color = c.navy)
            }
            Text(
                "Por defecto, hoy: el cobro entra a la caja del día en que se cobra. Cámbiala solo si el ${LocalTerminologiaPaciente.current.paciente} pagó otro día.",
                color = c.textoSuave, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            when (modo) {
                "gratis" -> "No entra dinero a caja: la $tipoMin queda saldada con ${simboloMoneda(moneda)} 0."
                "abonar" -> "Entra a caja como pago del tratamiento: lo verás sumado en la ficha del ${LocalTerminologiaPaciente.current.paciente}."
                else -> if (adelanto != null) "Entra a caja solo el adelanto; la $tipoMin queda debiendo el resto hasta que venga."
                    else if (conDivision) "Entra a caja un ingreso por cada medio, en la fecha del pago (el arqueo por método cuadra)."
                    else "Entra a caja en la fecha del pago, con este método. Si el ${LocalTerminologiaPaciente.current.paciente} aún no paga, puedes atenderlo igual y cobrarle después."
            },
            color = c.textoSuave, fontSize = 12.sp, lineHeight = 16.sp,
        )
    }
}

/** Opción tipo radio (borde navy cuando está elegida), como las de la web. */
@Composable
private fun OpcionModo(titulo: String, detalle: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (activo) c.navy.copy(alpha = 0.06f) else c.superficie)
            .border(if (activo) 1.5.dp else 1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 2.dp).size(18.dp).clip(CircleShape)
                .border(2.dp, if (activo) c.navy else c.borde, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (activo) Box(Modifier.size(9.dp).clip(CircleShape).background(c.navy))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detalle, color = c.textoSuave, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(texto, color = Sania.colors.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp))
}

/** 40.0 → "S/ 40.00" (como formatearSoles de la web), con el símbolo de la moneda de la sede. */
internal fun textoSoles(n: Double, moneda: String = pe.saniape.app.data.staff.monedaActiva()): String {
    val centavos = kotlin.math.round(n * 100).toLong()
    return "${simboloMoneda(moneda)} ${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"
}

private fun millisAIso(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}
