package pe.saniape.app.ui.clinica.comisiones

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ComisionesRepo
import pe.saniape.app.data.staff.DetalleComision
import pe.saniape.app.data.staff.PacienteDetalleComision
import pe.saniape.app.data.staff.ReglasComisiones
import pe.saniape.app.data.staff.TramoComision
import pe.saniape.app.data.staff.TratamientoDetalleComision
import pe.saniape.app.data.staff.soles
import pe.saniape.app.ui.clinica.finanzas.fechaCortaFin
import pe.saniape.app.ui.theme.Sania

/**
 * La pirámide como barra segmentada (gemelo de <Piramide> de la web): cada nivel
 * ocupa lo que mide su tramo y se llena con lo logrado dentro. El relleno se
 * dibuja con drawBehind (sin medidas intrínsecas).
 */
@Composable
internal fun Piramide(tramos: List<TramoComision>, logrado: Double, nivelActual: String?) {
    val segs = remember(tramos, logrado) { ReglasComisiones.segmentosPiramide(tramos, logrado) }
    if (segs.isEmpty()) return
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            segs.forEach { s ->
                val relleno = if (s.alcanzado) colorNivel(s.indice) else c.lav
                val fondo = c.borde
                Box(
                    Modifier.weight(s.peso).fillMaxSize().drawBehind {
                        val r = CornerRadius(size.height / 2, size.height / 2)
                        drawRoundRect(fondo, cornerRadius = r)
                        if (s.llenado > 0f) drawRoundRect(relleno, size = Size(size.width * s.llenado, size.height), cornerRadius = r)
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            segs.forEach { s ->
                val esActual = s.nombre == nivelActual
                Text(
                    s.nombre, modifier = Modifier.weight(s.peso), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (esActual) c.texto else c.textoSuave, fontSize = 10.sp,
                    fontWeight = if (esActual) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * "Ver detalle · N paquetes": qué contó, escalonado paciente → tratamiento →
 * sesiones y pagos (Jonathan, 2026-10-01). Se carga al abrir y se vuelve a pedir
 * si cambia el mes elegido. El servidor solo deja ver el propio al profesional.
 */
@Composable
internal fun DetallePiramide(plantillaId: String, terapeutaId: String, periodo: String?, n: Int) {
    if (n <= 0) return
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var abierto by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Se guarda con el período con que se pidió: al cambiar de mes se recarga.
    var datos by remember { mutableStateOf<Pair<String?, DetalleComision>?>(null) }

    fun cargar() {
        cargando = true; error = null
        scope.launch {
            val (d, e) = ComisionesRepo.detalle(plantillaId, terapeutaId, periodo)
            cargando = false
            if (d != null) datos = periodo to d else error = e ?: "No se pudo cargar el detalle"
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            (if (abierto) "▾ Ocultar detalle" else "▸ Ver detalle · $n ${if (n == 1) "paquete" else "paquetes"}"),
            color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                abierto = !abierto
                if (abierto && (datos == null || datos?.first != periodo)) cargar()
            }.padding(vertical = 4.dp),
        )
        if (!abierto) return@Column
        val r = datos?.takeIf { it.first == periodo }?.second
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                .border(1.dp, c.borde, RoundedCornerShape(8.dp)).background(c.superficie).padding(8.dp),
        ) {
            when {
                cargando -> Text("Cargando…", color = c.textoSuave, fontSize = 12.sp)
                error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error ?: "", color = c.error, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Reintentar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { cargar() }.padding(4.dp))
                }
                r == null -> {}
                r.pacientes.isEmpty() -> Text("Ningún paquete cuenta en este período.", color = c.textoSuave, fontSize = 12.sp)
                else -> {
                    Text(
                        "${r.pacientes.size} ${if (r.pacientes.size == 1) LocalTerminologiaPaciente.current.paciente else LocalTerminologiaPaciente.current.pacientes} · del ${fechaCortaFin(r.desde)} al ${fechaCortaFin(ReglasComisiones.diaAnterior(r.hasta))}".uppercase(),
                        color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp),
                    )
                    r.pacientes.forEach { p -> FilaPacienteDetalle(p) }
                }
            }
        }
    }
}

@Composable
private fun FilaPacienteDetalle(p: PacienteDetalleComision) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    val k = p.tratamientos.size
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(c.fondo)) {
        Row(
            Modifier.fillMaxWidth().clickable { abierto = !abierto }.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (abierto) "▾" else "▸", color = c.textoSuave, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Text(p.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(" · $k ${if (k == 1) "paquete" else "paquetes"}  ", color = c.textoSuave, fontSize = 11.sp)
            Text(soles(p.total), color = c.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (abierto) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                p.tratamientos.forEach { t -> FilaTratamientoDetalle(t) }
            }
        }
    }
}

@Composable
private fun FilaTratamientoDetalle(t: TratamientoDetalleComision) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    val pagado = t.estadoPago == "Pagado"
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(c.superficie)) {
        Column(Modifier.fillMaxWidth().clickable { abierto = !abierto }.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (abierto) "▾" else "▸", color = c.textoSuave, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
                Text(t.servicio, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(soles(t.monto), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(start = 16.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${t.sesionesCompletadas}/${t.totalSesiones} sesiones", color = c.textoSuave, fontSize = 11.sp)
                t.fechaCuenta?.let { Text(" · contó el ${fechaCortaFin(it)}", color = c.textoSuave, fontSize = 11.sp) }
                Spacer(Modifier.width(6.dp))
                Text(t.estadoPago ?: t.estado, color = if (pagado) c.ok else c.pend, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (pagado) c.okBg else c.pendBg)
                        .padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
        if (abierto) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("SESIONES", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (t.sesiones.isEmpty()) Text("Todavía sin sesiones.", color = c.textoSuave, fontSize = 11.sp)
                t.sesiones.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "#${s.numero ?: "—"} · ${s.fecha?.let { fechaCortaFin(it) } ?: "sin fecha"}${s.hora?.let { " ${it.take(5)}" } ?: ""}",
                            color = c.texto, fontSize = 11.sp, modifier = Modifier.weight(1f),
                        )
                        val (fg, bg) = when (s.estado) {
                            "Completada" -> c.ok to c.okBg
                            "En progreso" -> c.info to c.infoBg
                            "Reprogramada" -> c.pend to c.pendBg
                            "No asistió", "Cancelada" -> c.error to c.errorBg
                            else -> c.textoSuave to c.fondo
                        }
                        Text(s.estado ?: "—", color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("PAGOS", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (t.pagos.isEmpty()) Text("Sin pagos registrados.", color = c.textoSuave, fontSize = 11.sp)
                t.pagos.forEach { pg ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text("${fechaCortaFin(pg.fecha)}${pg.metodo?.let { " · $it" } ?: ""}", color = c.texto, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(soles(pg.monto), color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
