package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import pe.saniape.app.data.offline.nuevaIdemKey
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.ErrorServicioEnVisita
import pe.saniape.app.data.staff.MetodoPagoPreferido
import pe.saniape.app.data.staff.ModoUnaVez
import pe.saniape.app.data.staff.PedidoServicioEnVisita
import pe.saniape.app.data.staff.ServicioEnVisitaRepo
import pe.saniape.app.data.staff.ServicioUnaVez
import pe.saniape.app.data.staff.leerPrecio
import pe.saniape.app.data.staff.modoServicioDeUnaVez
import pe.saniape.app.data.staff.precioListaServicioEnVisita
import pe.saniape.app.data.staff.serviciosDeUnaVez
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.clinica.presupuestos.CampoPrecioPresu
import pe.saniape.app.ui.theme.Sania

/**
 * «✓ Se realizó en esta visita» — gemelo de components/citas/ServicioEnVisita.tsx
 * (web). En la cita de evaluación/consulta: qué servicio de una vez se hizo (uno
 * solo viene elegido), el precio acordado (↺ lista) y, con permiso de pagos, el
 * medio. Un guardado (ServicioEnVisitaRepo → POST /api/staff/cita/servicio-en-visita):
 * el tratamiento realizado, la cita completada y el cobro.
 */
@Composable
internal fun DialogoServicioEnVisita(
    cita: CitaStaff,
    servicios: List<ServicioUnaVez>,
    moneda: String,
    /** Nombre de la cita en el flujo («Evaluación podológica», «Consulta»…). */
    nombreCita: String,
    puedeCobrar: Boolean,
    /** Lo clínico (qué se hizo) solo con la historia clínica. */
    verClinico: Boolean,
    onCerrar: () -> Unit,
    onRegistrado: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val opciones = remember(servicios, cita.especialidadId) { serviciosDeUnaVez(servicios, cita.especialidadId) }
    var elegidoId by remember { mutableStateOf<String?>(null) }
    // Uno solo: no es una pregunta, viene elegido.
    val servicio = opciones.firstOrNull { it.id == elegidoId } ?: opciones.singleOrNull()
    val modo = servicio?.let { modoServicioDeUnaVez(it) }
    var cantidad by remember { mutableStateOf("1") }
    val nCantidad = (cantidad.trim().toIntOrNull() ?: 1).coerceAtLeast(1)
    val lista = servicio?.let { precioListaServicioEnVisita(it, if (modo == ModoUnaVez.UNIDADES) nCantidad else 1) }
    // Vacío = el de lista (precio siempre negociable).
    var precio by remember { mutableStateOf("") }
    val lectura = leerPrecio(precio, moneda)
    val precioFinal = if (lectura.ok) (lectura.valor ?: lista ?: 0.0) else null
    var nota by remember { mutableStateOf("") }

    val consulta = if (cita.pagadaAt == null) cita.costo ?: 0.0 else 0.0
    var cobrarConsulta by remember { mutableStateOf(true) }
    // Encendido: el paciente está saliendo. Se apaga si no paga ahora.
    var cobrarAhora by remember { mutableStateOf(true) }
    var metodo by rememberMetodoPagoInicial(cita.pacienteId)
    val total = kotlin.math.round(((precioFinal ?: 0.0) + (if (cobrarConsulta) consulta else 0.0)) * 100) / 100
    val cobra = puedeCobrar && cobrarAhora && total > 0

    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var idem by remember { mutableStateOf(nuevaIdemKey()) }

    val falta = when {
        servicio == null -> "Elige qué servicio se realizó"
        precioFinal == null -> lectura.error ?: "Pon el precio"
        else -> null
    }

    fun guardar() {
        val s = servicio ?: return
        val p = precioFinal ?: return
        if (guardando || falta != null) return
        guardando = true
        error = null
        scope.launch {
            try {
                val r = ServicioEnVisitaRepo.registrar(
                    PedidoServicioEnVisita(
                        citaId = cita.id, procedimientoId = s.id, precio = p,
                        cantidad = if (modo == ModoUnaVez.UNIDADES) nCantidad else null,
                        nota = nota.takeIf { verClinico }, terapeutaId = cita.terapeutaId,
                        cobrarConsulta = cobrarConsulta, metodo = if (cobra) metodo else null,
                    ),
                    idem,
                )
                r.onSuccess { res ->
                    val hecho = "${s.nombre} registrado · $nombreCita completada"
                    when {
                        res.errorCobro != null -> Toaster.error("$hecho, pero el cobro no entró: ${res.errorCobro} Cóbralo desde 💰 o la ficha.")
                        res.cobrado > 0 -> {
                            MetodoPagoPreferido.recordar(cita.pacienteId, metodo)
                            Toaster.exito("$hecho · cobrado ${textoSoles(res.cobrado, moneda)} ($metodo)")
                        }
                        else -> Toaster.exito(hecho)
                    }
                    res.aviso?.let { Toaster.info(it) }
                    onRegistrado()
                    onCerrar()
                }.onFailure { e ->
                    // Reanudable: con la misma clave no se duplica; si no, una nueva.
                    if ((e as? ErrorServicioEnVisita)?.reintentarIgual != true) idem = nuevaIdemKey()
                    error = e.message ?: "No se pudo registrar el servicio."
                }
            } finally {
                guardando = false
            }
        }
    }

    DialogoForm(
        titulo = "✓ Se realizó en esta visita",
        subtitulo = cita.pacienteNombre,
        textoAccion = when {
            guardando -> "Guardando…"
            cobra -> "Guardar y cobrar ${textoSoles(total, moneda)}"
            else -> "Guardar"
        },
        accionHabilitada = !guardando && falta == null,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        Text(
            "El servicio que se le hizo hoy. Al guardar queda su tratamiento realizado y la " +
                "${nombreCita.lowercase()} completada" + (if (puedeCobrar) ", con su cobro." else "."),
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp),
        )
        error?.let {
            Text("⚠ $it", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        }
        EtqForm("Servicio")
        when {
            opciones.isEmpty() -> Text("No hay servicios de pago único activos para esta especialidad.", color = c.texto, fontSize = 13.sp)
            opciones.size == 1 -> Text(opciones[0].nombre, color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            else -> Column {
                opciones.forEach { s ->
                    val activo = s.id == servicio?.id
                    val forma = RoundedCornerShape(Sania.shape.sm.dp)
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(forma)
                            .background(if (activo) c.navy.copy(alpha = 0.10f) else c.superficie)
                            .border(if (activo) 2.dp else 1.dp, if (activo) c.navy else c.borde, forma)
                            .clickable { elegidoId = s.id; precio = "" }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (activo) "◉" else "○", color = c.navy, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(s.nombre, color = c.texto, fontSize = 14.sp, fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f))
                        if (s.precio > 0) Text(textoSoles(s.precio, moneda), color = c.textoSuave, fontSize = 12.sp)
                    }
                }
            }
        }
        if (servicio != null) {
            Spacer(Modifier.height(10.dp))
            if (modo == ModoUnaVez.UNIDADES) {
                EtqForm(servicio.unidadLabel?.replaceFirstChar { it.uppercase() } ?: "Unidades")
                OutlinedTextField(
                    value = cantidad, onValueChange = { cantidad = it.filter(Char::isDigit).take(6); precio = "" },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            CampoPrecioPresu(
                etiqueta = "Precio acordado", valor = precio, onCambio = { precio = it },
                lista = lista, moneda = moneda, habilitado = !guardando,
            )
        }
        if (verClinico) {
            Spacer(Modifier.height(10.dp))
            EtqForm("Qué se hizo (opcional)")
            OutlinedTextField(
                value = nota, onValueChange = { nota = it.take(2000) },
                placeholder = { Text("Técnica, zona, indicaciones…", color = c.textoSuave) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
        }
        if (consulta > 0) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clickable { cobrarConsulta = !cobrarConsulta },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (cobrarConsulta) "☑" else "☐", fontSize = 18.sp, color = c.navy)
                Spacer(Modifier.width(6.dp))
                Text(
                    "La ${nombreCita.lowercase()} se cobra (${textoSoles(consulta, moneda)})" +
                        if (!cobrarConsulta) " · queda en ${textoSoles(0.0, moneda)}" else "",
                    color = c.texto, fontSize = 13.sp,
                )
            }
        }
        if (puedeCobrar && total > 0) {
            val forma = RoundedCornerShape(Sania.shape.md.dp)
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(forma)
                    .background(if (cobrarAhora) c.okBg else c.superficie)
                    .border(1.5.dp, if (cobrarAhora) c.ok else c.borde, forma).padding(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { cobrarAhora = !cobrarAhora },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (cobrarAhora) "☑" else "☐", fontSize = 18.sp, color = c.ok)
                    Spacer(Modifier.width(6.dp))
                    Text("💵 Cobrar ahora", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(textoSoles(total, moneda), color = c.ok, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (cobrarAhora) {
                    Spacer(Modifier.height(8.dp))
                    ChipsMetodoPago(metodo) { metodo = it }
                }
            }
        }
    }
}
