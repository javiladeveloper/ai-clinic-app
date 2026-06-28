package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.SesionFicha
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.EstadosColor
import pe.saniape.app.ui.theme.Sania

/** Estado de sesión que se puede fijar desde el menú ⋯ (igual que la web). */
private val ESTADOS_SESION = listOf("Reprogramada", "No asistió", "Cancelada", "Otro")

/**
 * Tarjeta de un tratamiento en la ficha: cabecera (procedimiento/estado/progreso/pago)
 * + al expandir, la lista de sus sesiones con acciones (Completar, estados) y "Dar de Alta".
 * Las escrituras van por endpoints (sesión/estado, tratamiento/alta) → reglas en la web.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaTratamiento(
    t: TratamientoPaciente,
    verPagos: Boolean,
    puedeSesiones: Boolean,
    onCompletarSesion: (SesionFicha) -> Unit,   // abre modal observaciones
    onCambioRealizado: () -> Unit,               // refrescar ficha tras acción
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var expandido by remember { mutableStateOf(false) }
    var sesiones by remember { mutableStateOf<List<SesionFicha>?>(null) }
    var accionando by remember { mutableStateOf(false) }
    var menuDe by remember { mutableStateOf<SesionFicha?>(null) }   // sesión con menú ⋯ abierto

    val estado = EstadosColor.cita(t.estado)
    val terminado = t.estado == "Alta" || t.estado == "Cancelado" || t.estado == "Suspendido"

    // Cargar sesiones al expandir (una vez).
    LaunchedEffect(expandido) {
        if (expandido && sesiones == null) {
            sesiones = runCatching { PacientesRepo.sesionesDe(t.id) }.getOrDefault(emptyList())
        }
    }

    fun recargarSesiones() {
        scope.launch {
            sesiones = runCatching { PacientesRepo.sesionesDe(t.id) }.getOrDefault(sesiones ?: emptyList())
            onCambioRealizado()
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
    ) {
        // Cabecera (tocable para expandir)
        Row(
            Modifier.fillMaxWidth().clickable { expandido = !expandido },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${modalidadIcono(t.modalidad)} ${t.procedimiento ?: "Tratamiento"}",
                color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(t.estado ?: "—", color = estado.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Text(if (expandido) "▴" else "▾", color = c.navy)
        }
        t.terapeutaNombre?.let {
            Text("con $it", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }

        // Progreso (si es por sesiones)
        if (t.modalidad != "Consulta" && t.totalSesiones > 0) {
            Spacer(Modifier.height(6.dp))
            val frac = (t.sesionesCompletadas.toFloat() / t.totalSesiones).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.chipBg)) {
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ok))
                }
                Spacer(Modifier.width(8.dp))
                Text("${t.sesionesCompletadas}/${t.totalSesiones} ses.", color = c.textoSuave, fontSize = 11.sp)
            }
        }

        // Estado de pago
        if (verPagos && t.estadoPago != null) {
            val pago = EstadosColor.cita(when (t.estadoPago) { "Pagado" -> "Confirmada"; "Parcial" -> "Pendiente"; else -> "Cancelada" })
            Spacer(Modifier.height(6.dp))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(pago.bg)
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("Pago: ${t.estadoPago}", color = pago.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Contenido expandido: sesiones + acciones ──
        if (expandido) {
            Spacer(Modifier.height(Sania.dim.md))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Spacer(Modifier.height(Sania.dim.md))

            when (val s = sesiones) {
                null -> Box(Modifier.fillMaxWidth().padding(Sania.dim.md), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                }
                else -> {
                    if (s.isEmpty()) {
                        Text("Sin sesiones registradas.", color = c.textoSuave, fontSize = 12.sp)
                    } else {
                        s.forEach { ses ->
                            FilaSesion(
                                ses = ses, verCosto = verPagos, puedeSesiones = puedeSesiones, accionando = accionando,
                                menuAbierto = menuDe?.id == ses.id,
                                onToggleMenu = { menuDe = if (menuDe?.id == ses.id) null else ses },
                                onCompletar = { onCompletarSesion(ses) },
                                onEstado = { nuevo ->
                                    menuDe = null
                                    if (accionando) return@FilaSesion
                                    accionando = true
                                    scope.launch {
                                        PacientesRepo.cambiarEstadoSesion(ses.id, nuevo)
                                        accionando = false
                                        recargarSesiones()
                                    }
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // Dar de alta (si el tratamiento sigue en curso y puede sesiones)
                    if (!terminado && puedeSesiones) {
                        Spacer(Modifier.height(Sania.dim.sm))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.ok.copy(alpha = 0.12f))
                                .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable(enabled = !accionando) {
                                    accionando = true
                                    scope.launch {
                                        PacientesRepo.darDeAlta(t.id)
                                        accionando = false
                                        onCambioRealizado()
                                    }
                                }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("✓ Dar de alta este tratamiento", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaSesion(
    ses: SesionFicha,
    verCosto: Boolean,
    puedeSesiones: Boolean,
    accionando: Boolean,
    menuAbierto: Boolean,
    onToggleMenu: () -> Unit,
    onCompletar: () -> Unit,
    onEstado: (String) -> Unit,
) {
    val c = Sania.colors
    val estado = EstadosColor.sesion(ses.estado)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.fondo).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sesión #${ses.numero}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${ses.fecha} ${ses.hora?.let { hora12(it) } ?: ""}".trim(), color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(ses.estado, color = estado.fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (verCosto && (ses.costo ?: 0.0) > 0) {
            Text("S/ ${formato2(ses.costo!!)}", color = c.teal, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        ses.motivoEstado?.takeIf { it.isNotBlank() }?.let {
            Text("Motivo: $it", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }

        // Acciones: solo en sesiones pendientes y con permiso.
        if (puedeSesiones && ses.pendiente) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniBtn("✓ Completar", c.navy, !accionando) { onCompletar() }
                MiniBtn("⋯ Estado", c.textoSuave, !accionando) { onToggleMenu() }
            }
            if (menuAbierto) {
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ESTADOS_SESION.forEach { e ->
                        MiniBtn(e, c.pend, !accionando) { onEstado(e) }
                    }
                }
            }
        }
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
    val cent = (n * 100).toLong()
    return "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
}
