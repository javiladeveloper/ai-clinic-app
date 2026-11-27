package pe.saniape.app.ui.clinica

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AsistenciaRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.MiAsistencia
import pe.saniape.app.ui.theme.Sania

/**
 * 🕘 Asistencia (control de asistencia del personal, 26/11/2026): marcar la
 * entrada o la salida y ver lo propio del mes. Todo viene calculado del servidor
 * ([AsistenciaRepo]); la hora de la marca la pone el servidor, no el teléfono.
 * Personal, reportes y correcciones viven en la web.
 */
@Composable
fun PantallaAsistencia(ctx: ContextoStaff) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var datos by remember { mutableStateOf<MiAsistencia?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var marcando by remember { mutableStateOf(false) }

    // 💵 Sus pagos de planilla ya hechos (cada quien, los suyos).
    var pagos by remember { mutableStateOf<List<pe.saniape.app.data.staff.MiPagoPlanilla>>(emptyList()) }

    suspend fun cargar() {
        val r = AsistenciaRepo.mia()
        if (r != null) datos = r
        cargando = false
        if (r?.activa == true && r.enLista) pagos = AsistenciaRepo.misPagos()
    }
    LaunchedEffect(ctx.clinicaId) { cargando = true; cargar() }

    // Lo que pasó al escanear el QR: se muestra grande, arriba.
    var resultadoQr by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun marcar(qr: String? = null) {
        if (marcando) return
        marcando = true
        scope.launch {
            val r = AsistenciaRepo.marcar(qr)
            if (qr != null) resultadoQr = r.ok to r.mensaje
            if (r.ok) pe.saniape.app.ui.Toaster.exito(r.mensaje) else pe.saniape.app.ui.Toaster.error(r.mensaje)
            cargar()
            marcando = false
        }
    }

    // 🕘 Escanear el QR de la recepción ES marcar: con los datos cargados y la persona en la
    // lista, se marca sola y la llave se consume (volver a la pantalla no repite la marca).
    val qrPendiente = pe.saniape.app.ui.QrAsistenciaPendiente.actual
    LaunchedEffect(qrPendiente, datos?.enLista, datos?.activa) {
        val d = datos ?: return@LaunchedEffect
        if (qrPendiente == null) return@LaunchedEffect
        val llave = pe.saniape.app.ui.QrAsistenciaPendiente.consumir() ?: return@LaunchedEffect
        if (d.activa && d.enLista) marcar(llave)
        else resultadoQr = false to "Escaneaste el QR, pero no estás en la lista de personal: no se marcó nada."
    }

    // 💵 Planilla (solo Admin): ver el periodo, armarlo y pagar. Atrás vuelve a Asistencia.
    var verPlanilla by remember { mutableStateOf(false) }
    pe.saniape.app.ui.ManejarAtras(activo = verPlanilla) { verPlanilla = false }
    if (verPlanilla) {
        PantallaPlanilla(hoy = pe.saniape.app.data.staff.hoyClinicaIso(), onCerrar = { verPlanilla = false })
        return
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🕘 Asistencia", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (ctx.esAdmin && datos?.activa == true) {
                    Text("💵 Planilla", color = c.sobreNavy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.sobreNavy, RoundedCornerShape(50))
                            .clickable { verPlanilla = true }.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }

            val d = datos
            when {
                cargando && d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                d == null -> Aviso("No se pudo cargar tu asistencia. Revisa tu conexión.", "Reintentar") { scope.launch { cargando = true; cargar() } }
                !d.activa -> Aviso("El control de asistencia no está activo en tu clínica.")
                !d.enLista -> Aviso(resultadoQr?.second ?: "Todavía no estás en la lista de personal que marca asistencia. Pídeselo al administrador.")
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = Sania.dim.xl),
                    verticalArrangement = Arrangement.spacedBy(Sania.dim.sm),
                ) {
                    resultadoQr?.let { (ok, texto) ->
                        item {
                            Spacer(Modifier.height(Sania.dim.lg))
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(if (ok) c.okBg else c.errorBg).padding(Sania.dim.lg),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(if (ok) "✅" else "⚠️", fontSize = 36.sp)
                                Text(texto, color = if (ok) c.ok else c.error, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(Sania.dim.lg))
                        // Hoy + el botón de marcar.
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.lg),
                        ) {
                            Text("Hoy" + (d.jornadaHoy?.let { " · tu horario $it" } ?: ""), color = c.textoSuave, fontSize = Sania.txt.pequeno)
                            Spacer(Modifier.height(4.dp))
                            if (d.marcasHoy.isEmpty()) {
                                Text("Todavía no marcas hoy.", color = c.texto, fontSize = Sania.txt.cuerpo)
                            } else {
                                d.marcasHoy.forEach { m ->
                                    Text(
                                        "${if (m.tipo == "salida") "Salida" else "Entrada"}  ${m.hora}" + if (m.medio.isNotBlank()) "  · ${m.medio}" else "",
                                        color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            if (d.tardanzaHoyMin > 0) {
                                Text("${d.tardanzaHoyMin} min de tardanza", color = c.pend, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(Sania.dim.lg))
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                                    .background(if (marcando) c.borde else c.navy)
                                    .clickable(enabled = !marcando) { marcar() }.padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (marcando) "Marcando…" else if (d.siguiente == "salida") "Marcar salida" else "Marcar entrada",
                                    color = c.sobreNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("La hora la pone el servidor. Una marca no se borra: si te equivocas, avísale al administrador.", color = c.textoSuave, fontSize = 12.sp)
                        }
                    }
                    item {
                        // El mes, en una línea por dato.
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.lg),
                        ) {
                            Text("Este mes", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                            Spacer(Modifier.height(4.dp))
                            Dato("Días trabajados", d.mes.dias.toString())
                            Dato("Tardanzas", if (d.mes.tardanzas > 0) "${d.mes.tardanzas} · ${d.mes.tardanzaTexto}" else "0")
                            Dato("Faltas", d.mes.faltas.toString())
                            Dato("Horas trabajadas", d.mes.horas)
                            if (d.mes.extra != "0 min") Dato("Horas extra", d.mes.extra)
                        }
                    }
                    if (pagos.isNotEmpty()) {
                        item { Text("💵 Mis pagos", color = c.textoSuave, fontSize = Sania.txt.pequeno, modifier = Modifier.padding(top = Sania.dim.sm)) }
                        items(pagos, key = { "pago-" + it.id }) { p ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.lg),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.periodo, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Pagado" + (p.pagadoEl?.let { " el $it" } ?: "") + (p.metodo?.let { " · $it" } ?: ""),
                                            color = c.textoSuave, fontSize = Sania.txt.pequeno,
                                        )
                                    }
                                    Text(pe.saniape.app.data.staff.dineroActivo(p.neto), color = c.ok, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                if (p.lineas.size > 1) {
                                    Spacer(Modifier.height(6.dp))
                                    p.lineas.forEach { l -> Dato(l.concepto, (if (l.resta) "− " else "") + pe.saniape.app.data.staff.dineroActivo(l.monto)) }
                                }
                            }
                        }
                    }
                    if (d.dias.isNotEmpty()) {
                        item { Text("Mis marcas", color = c.textoSuave, fontSize = Sania.txt.pequeno, modifier = Modifier.padding(top = Sania.dim.sm)) }
                        items(d.dias, key = { it.fecha }) { dia ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(dia.dia, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        (dia.marcas.ifBlank { "Sin marcas" }) + if (dia.corregida) "  · corregida" else "",
                                        color = c.textoSuave, fontSize = Sania.txt.pequeno,
                                    )
                                    if (dia.detalle.isNotBlank()) Text(dia.detalle, color = c.textoSuave, fontSize = 12.sp)
                                }
                                Text(
                                    dia.estado,
                                    color = when (dia.estado) { "Falta" -> c.error; "Tardanza", "Sin salida" -> c.pend; else -> c.ok },
                                    fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(Sania.dim.xl)) }
                }
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, color = c.textoSuave, fontSize = Sania.txt.cuerpo)
        Text(valor, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Aviso(texto: String, accion: String? = null, onAccion: (() -> Unit)? = null) {
    val c = Sania.colors
    Column(Modifier.fillMaxSize().padding(Sania.dim.xl), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🕘", fontSize = 40.sp)
        Spacer(Modifier.height(Sania.dim.sm))
        Text(texto, color = c.textoSuave, fontSize = Sania.txt.cuerpo, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (accion != null && onAccion != null) {
            Spacer(Modifier.height(Sania.dim.lg))
            Text(accion, color = c.navy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onAccion() }.padding(8.dp))
        }
    }
}
