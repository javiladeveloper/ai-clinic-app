package pe.saniape.app.ui.clinica.presupuestos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.staff.Presupuesto
import pe.saniape.app.data.staff.PresupuestosRepo
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.ServicioPresupuesto
import pe.saniape.app.data.staff.TratamientoConvertido
import pe.saniape.app.data.staff.etiquetaAgendaPresupuesto
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.leerPrecio
import pe.saniape.app.data.staff.mensajeRechazoPresupuesto
import pe.saniape.app.data.staff.nuevaClave
import pe.saniape.app.data.staff.repartirAdelanto
import pe.saniape.app.data.staff.textoCantidad
import pe.saniape.app.data.staff.tipoAgendaDeModalidad
import pe.saniape.app.data.staff.totalesDe
import pe.saniape.app.data.staff.tratamientosDeConversion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.PrefillCita
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.theme.Sania

/**
 * ✓ Acepta EN PERSONA y crea los tratamientos (gemelo de AceptarPresupuesto.tsx):
 *  1. Confirmar: queda quién lo registró, cuándo y "en persona" (manda el
 *     `updated_at` que se está mostrando: si otro lo editó, 409 CAMBIO → recargar).
 *     Con "Crear los tratamientos ahora" (permiso de sesiones) sigue la
 *     conversión: UN tratamiento por ítem con el descuento repartido (idempotente;
 *     un PARCIAL deja lo creado y el reintento crea lo que falta).
 *  2. Lo que sigue: 💵 adelanto opcional (el cobro de siempre) REPARTIDO en orden
 *     sin pasar del total, y 📅 agendar cada tratamiento con el formulario de
 *     crear cita de siempre, prellenado.
 */
@Composable
internal fun AceptarPresupuesto(
    presupuesto: Presupuesto,
    pacienteId: String,
    pacienteNombre: String,
    profesionales: List<RefNombre>,
    /** Catálogo (servicio de un ítem libre y la especialidad al agendar). */
    servicios: List<ServicioPresupuesto>,
    puedeConvertir: Boolean,
    puedeCobrar: Boolean,
    onCerrar: () -> Unit,
    /** Algo cambió (aceptado / convertido / cobrado): el padre recarga. */
    onCambio: () -> Unit,
    /** "📅 Agendar…": el formulario de crear cita, prellenado. null = no se ofrece. */
    onAgendar: ((PrefillCita) -> Unit)?,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val p = presupuesto
    val t = totalesDe(p)
    fun din(n: Double) = formatearDinero(n, p.moneda)
    val yaAceptado = p.estado == "Aceptado"
    val libres = p.items.filter { it.procedimientoId == null }
    val activos = remember(servicios) { servicios.filter { (it.estado ?: "Activo") == "Activo" } }

    var crearAhora by remember { mutableStateOf(puedeConvertir) }
    var terapeutaId by remember { mutableStateOf(p.terapeutaId) }
    var servLibre by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var trabajando by remember { mutableStateOf(false) }
    var creados by remember { mutableStateOf<List<TratamientoConvertido>?>(null) }
    var eligiendoProf by remember { mutableStateOf(false) }
    var eligiendoServicioDe by remember { mutableStateOf<String?>(null) }

    fun confirmar() {
        if (trabajando) return
        trabajando = true
        scope.launch {
            try {
                if (!yaAceptado) {
                    val r = PresupuestosRepo.accion(p.id, "aceptar", updatedAt = p.updatedAt)
                    if (!r.registrada) {
                        Toaster.error(mensajeRechazoPresupuesto(r, "No se pudo registrar la aceptación"))
                        // Lo editaron mientras tanto: se recarga y se cierra para que lo revisen.
                        if (r.codigo == "CAMBIO") { onCambio(); onCerrar() }
                        return@launch
                    }
                    onCambio()
                }
                if (crearAhora && puedeConvertir) {
                    val r = PresupuestosRepo.convertir(p.id, terapeutaId, servLibre)
                    if (r.registrada) {
                        val lista = tratamientosDeConversion(r)
                        creados = lista
                        Toaster.exito(if (lista.size == 1) "Presupuesto aceptado: tratamiento creado" else "Presupuesto aceptado: ${lista.size} tratamientos creados")
                        onCambio()
                    } else {
                        // Parcial: lo creado queda y se puede agendar; el reintento crea lo que falta.
                        val parcial = tratamientosDeConversion(r)
                        if (parcial.isNotEmpty()) { creados = parcial; onCambio() }
                        Toaster.error(mensajeRechazoPresupuesto(r, "No se pudieron crear los tratamientos"))
                    }
                } else {
                    Toaster.exito("Presupuesto aceptado en persona")
                    onCerrar()
                }
            } finally {
                trabajando = false
            }
        }
    }

    val lista = creados
    if (lista != null) {
        SiguientePaso(p, pacienteId, pacienteNombre, lista, servicios, puedeCobrar, onCerrar, onCambio, onAgendar)
        return
    }

    DialogoForm(
        titulo = if (yaAceptado) "🩺 Crear los tratamientos" else "✓ Acepta en persona",
        subtitulo = "«${p.titulo}» · ${din(t.total)}",
        textoAccion = when {
            trabajando -> "Guardando…"
            yaAceptado -> "🩺 Crear tratamientos"
            crearAhora && puedeConvertir -> "✓ Acepta y crear tratamientos"
            else -> "✓ Acepta"
        },
        accionHabilitada = !trabajando && !(yaAceptado && !(crearAhora && puedeConvertir)),
        onCancelar = { if (!trabajando) onCerrar() },
        onAccion = { confirmar() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!yaAceptado) {
                Text("$pacienteNombre acepta el presupuesto «${p.titulo}» por ${din(t.total)}. Quedará registrado que lo aceptó " +
                    "en la clínica, ante ti, con la fecha y la hora.", color = c.texto, fontSize = 13.sp)
            } else if (p.aceptadoCanal == "portal") {
                Text("$pacienteNombre lo aceptó desde su app. Crea sus tratamientos para empezar a agendar y cobrar.", color = c.texto, fontSize = 13.sp)
            }
            TarjetaPresu {
                p.items.forEach { it ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(it.nombre, color = c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(textoCantidad(it), color = c.textoSuave, fontSize = 12.sp)
                    }
                }
                FilaTotal("Total", din(t.total), negrita = true)
            }
            if (puedeConvertir) {
                if (!yaAceptado) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(role = Role.Checkbox) { crearAhora = !crearAhora },
                        verticalAlignment = Alignment.Top) {
                        Checkbox(checked = crearAhora, onCheckedChange = { crearAhora = it }, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                        Text("Crear los tratamientos ahora — uno por servicio, con el descuento repartido: lo acordado suma exactamente ${din(t.total)}.",
                            color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                if (crearAhora) {
                    Column {
                        EtqForm("Profesional de los tratamientos")
                        CajaSelectorForm(profesionales.firstOrNull { it.id == terapeutaId }?.nombre ?: "Sin asignar") { eligiendoProf = true }
                    }
                    libres.forEach { it ->
                        Column {
                            EtqForm("«${it.nombre}» no está en el catálogo — servicio (opcional)")
                            CajaSelectorForm(servLibre[it.clave]?.let { id -> activos.firstOrNull { s -> s.id == id }?.nombre } ?: "Crear sin servicio (con su nombre)") {
                                eligiendoServicioDe = it.clave
                            }
                        }
                    }
                }
            } else {
                Text("Los tratamientos los crea quien gestiona tratamientos (permiso de sesiones).", color = c.textoSuave, fontSize = 12.sp)
            }
        }
    }

    if (eligiendoProf) {
        DialogoOpciones(
            titulo = "Profesional",
            opciones = listOf<Pair<String?, String>>(null to "Sin asignar") + profesionales.map { it.id to it.nombre },
            onElegir = { terapeutaId = it; eligiendoProf = false },
            onCerrar = { eligiendoProf = false },
        )
    }
    eligiendoServicioDe?.let { clave ->
        // Con buscador (el catálogo puede ser largo), como el selector de la web.
        pe.saniape.app.ui.clinica.DialogoElegirBuscable(
            titulo = "Servicio",
            opciones = activos, etiqueta = { it.nombre },
            detalle = { s -> s.precio?.takeIf { it > 0 }?.let { pe.saniape.app.data.staff.dineroActivo(it) } },
            opcionVacia = "Crear sin servicio (con su nombre)",
            onElegir = { s -> servLibre = if (s == null) servLibre - clave else servLibre + (clave to s.id); eligiendoServicioDe = null },
            onCerrar = { eligiendoServicioDe = null },
        )
    }
}

/** "✓ Aceptado — ¿qué sigue?": adelanto opcional y agendar cada tratamiento. */
@Composable
private fun SiguientePaso(
    p: Presupuesto,
    pacienteId: String,
    pacienteNombre: String,
    creados: List<TratamientoConvertido>,
    servicios: List<ServicioPresupuesto>,
    puedeCobrar: Boolean,
    onCerrar: () -> Unit,
    onCambio: () -> Unit,
    onAgendar: ((PrefillCita) -> Unit)?,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    fun din(n: Double) = formatearDinero(n, p.moneda)
    var adelanto by remember { mutableStateOf("") }
    val metodo = rememberMetodoPagoInicial(pacienteId)
    var cobrado by remember { mutableStateOf<String?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    // Clave de idempotencia: la MISMA para reintentar el mismo cobro, NUEVA si cambia el monto o el método.
    var claveCobro by remember { mutableStateOf<Pair<String, String>?>(null) }
    val lectura = leerPrecio(adelanto, p.moneda)
    val totalCreados = creados.sumOf { it.monto }
    val reparto = if (lectura.ok && lectura.valor != null && lectura.valor > 0) repartirAdelanto(lectura.valor, creados.map { it.id to it.monto }, p.moneda) else emptyList()

    fun cobrar() {
        if (trabajando) return
        val v = lectura.valor
        if (!lectura.ok || v == null || v <= 0) { Toaster.error(if (lectura.ok) "Escribe el monto del adelanto" else lectura.error ?: "Monto no válido"); return }
        val partes = reparto
        if (partes == null) { Toaster.error("El adelanto no puede pasar del total (${din(totalCreados)})"); return }
        val m = metodo.value
        val firma = "$v|$m"
        if (claveCobro?.first != firma) claveCobro = firma to (nuevaClave() + kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
        val base = claveCobro!!.second
        trabajando = true
        scope.launch {
            try {
                // Un cobro por tratamiento, en orden. Si uno falla, el reintento (misma clave) no duplica.
                for ((i, parte) in partes.withIndex()) {
                    val r = PresupuestosRepo.registrarAdelanto(parte.first, parte.second, m, "Adelanto del presupuesto «${p.titulo}»", "$base-$i")
                    if (!r.registrada) {
                        Toaster.error("${r.rechazo?.error ?: "No se pudo registrar el adelanto"}. Reintenta: lo ya cobrado no se repite.")
                        return@launch
                    }
                }
                cobrado = "${din(v)} · $m"
                Toaster.exito("Adelanto registrado: ${din(v)}")
                onCambio()
            } finally {
                trabajando = false
            }
        }
    }

    fun agendar(t: TratamientoConvertido) {
        val f = onAgendar ?: return
        if (trabajando) return
        trabajando = true
        scope.launch {
            try {
                val especialidad = t.procedimientoId?.let { id -> servicios.firstOrNull { it.id == id }?.especialidadId }
                val oferta = pe.saniape.app.ui.clinica.pacientes.ofertaPrimeraCitaDe(
                    tratamientoId = t.id, pacienteId = pacienteId, pacienteNombre = pacienteNombre,
                    tipo = tipoAgendaDeModalidad(t.modalidad, t.monto), terapeutaId = t.terapeutaId, especialidadId = especialidad,
                    respuesta = buildJsonObject { if (t.sedeId != null) put("sedeId", t.sedeId) else put("sedeId", kotlinx.serialization.json.JsonNull) },
                )
                onCerrar()
                f(oferta.prefill())
            } finally {
                trabajando = false
            }
        }
    }

    DialogoForm(
        titulo = "✓ Aceptado — ¿qué sigue?",
        subtitulo = "«${p.titulo}»",
        textoAccion = "Listo",
        onCancelar = onCerrar,
        onAccion = onCerrar,
        textoCancelar = "Cerrar",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${if (creados.size == 1) "Tratamiento creado" else "${creados.size} tratamientos creados"} por ${din(totalCreados)} — el total del presupuesto.",
                color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.okBg)
                    .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
            )
            if (puedeCobrar) {
                TarjetaPresu {
                    Text("💵 Adelanto (opcional)", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.heightIn(min = 6.dp))
                    val hecho = cobrado
                    if (hecho != null) {
                        Text("✓ Registrado: $hecho", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        CampoPresu(adelanto, { adelanto = it.replace(',', '.') }, etiqueta = "Monto", placeholder = "0.00", teclado = KeyboardType.Decimal,
                            error = !lectura.ok)
                        Spacer(Modifier.heightIn(min = 6.dp))
                        EtqForm("Método")
                        ChipsMetodoPago(metodo.value) { metodo.value = it }
                        when {
                            reparto == null -> Text("No puede pasar del total: ${din(totalCreados)}.", color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            reparto.size > 1 -> Text(
                                "Se reparte en orden: " + reparto.joinToString(" · ") { (id, m) -> "${creados.firstOrNull { it.id == id }?.nombre.orEmpty()} ${din(m)}" },
                                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Spacer(Modifier.heightIn(min = 8.dp))
                        BotonPresu(if (trabajando) "Registrando…" else "💵 Registrar adelanto", ok = true, habilitado = !trabajando && reparto != null,
                            modifier = Modifier.fillMaxWidth()) { cobrar() }
                    }
                }
            }
            if (onAgendar != null) {
                TarjetaPresu {
                    Text("📅 Agendar", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Al agendar uno se abre la cita; los demás los agendas luego desde 🩺 Atenciones.", color = c.textoSuave, fontSize = 11.sp)
                    creados.forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.nombre, color = c.texto, fontSize = 13.sp)
                                Text(din(t.monto) + if (t.yaExistia) " · ya estaba creado" else "", color = c.textoSuave, fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            BotonPresu(etiquetaAgendaPresupuesto(t.modalidad), lleno = true, habilitado = !trabajando) { agendar(t) }
                        }
                    }
                }
            }
        }
    }
}

/** Lista simple para elegir (profesional, servicio). Sin listas perezosas dentro de un diálogo. */
@Composable
internal fun DialogoOpciones(
    titulo: String,
    opciones: List<Pair<String?, String>>,
    onElegir: (String?) -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                opciones.forEach { (id, nombre) ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(role = Role.Button) { onElegir(id) }
                        .padding(horizontal = 8.dp, vertical = 12.dp)) {
                        Text(nombre, color = c.texto, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}
