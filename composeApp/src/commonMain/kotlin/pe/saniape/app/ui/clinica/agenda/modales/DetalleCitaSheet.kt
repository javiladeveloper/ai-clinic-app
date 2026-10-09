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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.BloqueDetalleCita
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.DatosDetalleCita
import pe.saniape.app.data.staff.FlujoClinica
import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import pe.saniape.app.data.staff.bloquesDetalleCita
import pe.saniape.app.data.staff.fechaLegibleCorta
import pe.saniape.app.data.staff.monedaDeFila
import pe.saniape.app.ui.clinica.agenda.componentes.BadgeEstadoCita
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * Detalle de la cita al tocar su tarjeta en la agenda (gemelo de `PopupCita` de
 * /citas web): cuándo, con quién y — cargado al abrir, por id — las observaciones
 * de la cita (con "📍 Abrir en Maps" si son una dirección: atención a domicilio)
 * y, para quien atiende ([verClinico]), el diagnóstico y las notas de la sesión.
 * Las acciones siguen en la tarjeta: aquí no se duplica la lógica.
 */
@Composable
fun DetalleCitaSheet(
    cita: CitaStaff,
    flujo: FlujoClinica,
    puedeVerCosto: Boolean,
    verClinico: Boolean,
    onCerrar: () -> Unit,
    onVerResumen: ((String) -> Unit)?,
) {
    val c = Sania.colors
    var cargando by remember(cita.id) { mutableStateOf(true) }
    var datos by remember(cita.id) { mutableStateOf<DatosDetalleCita?>(null) }
    var fallo by remember(cita.id) { mutableStateOf(false) }

    LaunchedEffect(cita.id, verClinico) {
        cargando = true
        val r = AgendaRepo.detalleCita(cita.id, verClinico)
        datos = r
        fallo = r == null
        cargando = false
    }

    val bloques = bloquesDetalleCita(cita.tipo, flujo.esCitaQueEvalua(cita.tipo), datos, verClinico)

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BadgeEstadoCita(cita.estado, cita.confirmadaPorPaciente)
                    cita.tipo?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(flujo.nombreTipo(it).uppercase() + (cita.numeroSesion?.let { n -> " #$n" } ?: ""),
                            color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(cita.pacienteNombre ?: LocalTerminologiaPaciente.current.Paciente,
                    fontWeight = FontWeight.Bold, color = c.texto)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Dato("Cuándo", buildString {
                    append(fechaLegibleCorta(cita.fecha)).append(" · ").append(hora12(cita.hora))
                    cita.duracion?.takeIf { it > 0 }?.let { append(" · $it min") }
                })
                Dato("Con", cita.terapeutaNombre ?: "Sin profesional asignado",
                    suave = cita.terapeutaNombre == null)
                cita.procedimiento?.takeIf { it.isNotBlank() }?.let { Dato("Servicio", it) }
                if (puedeVerCosto && (cita.costo ?: 0.0) > 0) {
                    Dato("Costo", textoSoles(cita.costo ?: 0.0, monedaDeFila(cita.sedeId)))
                }

                when {
                    cargando -> Row(
                        Modifier.fillMaxWidth().padding(top = Sania.dim.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        Text("  Cargando observaciones…", color = c.textoSuave, fontSize = 12.sp)
                    }
                    fallo -> Text("Sin conexión: no se pudieron cargar las observaciones.",
                        color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = Sania.dim.md))
                    else -> bloques.forEach { Bloque(it) }
                }
            }
        },
        confirmButton = {
            val pid = cita.pacienteId
            if (pid != null && onVerResumen != null) {
                TextButton(onClick = { onVerResumen(pid) }) {
                    Text("Resumen del ${LocalTerminologiaPaciente.current.paciente} →", color = c.navy, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = c.textoSuave) } },
        containerColor = c.superficie,
        shape = RoundedCornerShape(Sania.shape.lg.dp),
    )
}

@Composable
private fun Dato(etiqueta: String, valor: String, suave: Boolean = false) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(etiqueta.uppercase(), color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp).padding(top = 2.dp))
        Text(valor, color = if (suave) c.pend else c.texto, fontSize = Sania.txt.cuerpo, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(top = Sania.dim.md)) {
        Text(titulo, color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp))
        contenido()
    }
}

@Composable
private fun Bloque(b: BloqueDetalleCita) {
    val c = Sania.colors
    when (b) {
        is BloqueDetalleCita.Observaciones -> Seccion("OBSERVACIONES") {
            var expandido by remember(b.texto) { mutableStateOf(false) }
            val acciones = recordarAcciones()
            Column(Modifier.fillMaxWidth()) {
                // Atención a domicilio: la dirección, destacada, con su botón a Maps.
                if (b.direccion != null && b.urlMaps != null) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(c.pendBg).border(1.dp, c.pend, RoundedCornerShape(Sania.shape.sm.dp))
                            .padding(Sania.dim.md),
                    ) {
                        Text("📍 ${b.direccion}", color = c.pend, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                                .clickable { acciones.abrirUrl(b.urlMaps) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("📍 Abrir en Maps", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // El texto completo (con sus saltos de línea), salvo que sea solo la dirección.
                if (b.direccion == null || b.texto.trim().trimEnd('.') != b.direccion) {
                    if (b.direccion != null) Spacer(Modifier.height(8.dp))
                    Text(b.texto, color = c.texto, fontSize = Sania.txt.cuerpo,
                        maxLines = if (b.largo && !expandido) 4 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis)
                    if (b.largo) {
                        Text(if (expandido) "Ver menos" else "Ver más", color = c.navy, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp).clickable { expandido = !expandido })
                    }
                }
            }
        }
        is BloqueDetalleCita.Diagnostico -> Seccion("DIAGNÓSTICO") {
            Text(b.texto, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
        }
        is BloqueDetalleCita.NotasSesion -> Seccion("NOTAS DE LA SESIÓN" + (b.numero?.let { " #$it" } ?: "")) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.chipBg).padding(Sania.dim.md),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                b.tecnicas?.let { Text(it, color = c.texto, fontSize = Sania.txt.cuerpo) }
                b.evolucion?.let { Text("↗ $it", color = c.ok, fontSize = Sania.txt.cuerpo) }
            }
        }
    }
}
