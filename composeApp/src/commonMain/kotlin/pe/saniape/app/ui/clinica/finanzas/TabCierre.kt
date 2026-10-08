package pe.saniape.app.ui.clinica.finanzas

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.DiaCierre
import pe.saniape.app.data.staff.Finanzas
import pe.saniape.app.data.staff.FinanzasRepo
import pe.saniape.app.data.staff.SedeActiva
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.soles
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña "Cierre de caja" (gemela de components/finanzas/CierreCaja.tsx): el
 * día, lo que entró por método, el efectivo esperado vs el contado, la
 * diferencia y la nota, y cerrar (o actualizar el cierre). Los totales que se
 * guardan los recalcula el servidor (/api/staff/caja/cierre); la diferencia que
 * se ve mientras se escribe es solo una vista previa con la misma regla.
 * Multisede: cada local cierra su cajón; en "todas las sedes" no se cierra.
 */
@Composable
internal fun TabCierre() {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val hoy = remember { hoyClinicaIso() }
    var fecha by remember { mutableStateOf(hoy) }
    var dia by remember { mutableStateOf<DiaCierre?>(null) }
    var fallo by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var contado by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    var eligiendo by remember { mutableStateOf(false) }
    var verMovs by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    val sede by SedeActiva.estado.collectAsState()

    LaunchedEffect(fecha, sede.filtro, sede.sedeId, recarga) {
        fallo = null; dia = null
        val (d, err) = FinanzasRepo.cierre(fecha)
        if (d == null) { fallo = err ?: "No se pudo cargar la caja."; return@LaunchedEffect }
        dia = d
        // Si el día ya se cerró, se muestra lo que se contó ESE día (no un campo
        // en blanco que invite a recontar sin querer).
        contado = d.cierre?.efectivoContado?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
        nota = d.cierre?.nota ?: ""
    }

    if (eligiendo) DialogoFecha(
        inicial = fecha,
        onElegir = { f -> if (f > hoy) Toaster.error("No se puede cerrar un día que todavía no llega") else fecha = f },
        onCerrar = { eligiendo = false },
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                CajaSelectorForm("📅 " + (if (fecha == hoy) "Hoy · " else "") + fechaCortaFin(fecha) + " (" + fecha + ")") { eligiendo = true }
            }
            val d = dia
            if (d != null) {
                Spacer(Modifier.width(8.dp))
                val (txt, fg, bg) = when {
                    !d.cerrable && d.cerradasConsolidado != null -> Triple("🔒 ${d.cerradasConsolidado} de ${sede.sedes.size} sedes", c.navy, c.chipBg)
                    d.cierre != null -> Triple("🔒 Caja cerrada", c.ok, c.okBg)
                    d.cerrable -> Triple("Sin cerrar", c.pend, c.pendBg)
                    else -> Triple("", c.textoSuave, c.fondo)
                }
                if (txt.isNotEmpty()) Text(txt, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }

        val d = dia
        when {
            d == null && fallo != null -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fallo!!, color = c.textoSuave, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                BotonFin("Reintentar") { recarga++ }
            }
            d == null -> CargandoLista(filas = 3, conAvatar = false, conMargen = false)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CifraFin("Ingresos del día", d.totalIngresos, c.ok, Modifier.weight(1f))
                    CifraFin("Egresos del día", d.totalEgresos, c.error, Modifier.weight(1f))
                    CifraFin("Neto", d.neto, c.navy, Modifier.weight(1f))
                }
                TarjetaFin {
                    RotuloFin("Ingresos por método de pago", modifier = Modifier.padding(bottom = 8.dp))
                    if (d.porMetodo.isEmpty()) Text("Sin ingresos este día.", color = c.textoSuave, fontSize = 12.sp)
                    d.porMetodo.forEach { (met, total) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${iconoMetodoFin(met)}  $met", color = c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(soles(total), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TarjetaFin(Modifier.tourAncla("caja.efectivo")) {
                    RotuloFin("Arqueo de efectivo", modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("💵 Efectivo esperado en caja", color = c.texto, fontSize = 13.sp)
                            Text("cobros en efectivo − gastos en efectivo", color = c.textoSuave, fontSize = 11.sp)
                        }
                        Text(soles(d.esperado), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    val contadoNum = Finanzas.parsearMonto(contado)
                    if (d.cerrable) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Efectivo contado", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                colors = coloresCampoForm(), value = contado,
                                onValueChange = { contado = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                                placeholder = { Text("0.00", color = c.textoSuave) }, singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(130.dp),
                            )
                        }
                    }
                    val arqueo = contadoNum?.let { Finanzas.compararArqueo(d.esperado, it) }
                    if (arqueo != null) {
                        Spacer(Modifier.height(8.dp))
                        val cuadra = arqueo.estado == Finanzas.EstadoArqueo.CUADRA
                        AvisoFin((if (cuadra) "✓ " else "⚠ ") + arqueo.mensaje, if (cuadra) c.ok else c.error, if (cuadra) c.okBg else c.errorBg)
                        // La nota explica el descuadre: sin ella, un "faltan S/5" de hace dos semanas es un misterio.
                        if (!cuadra && d.cerrable) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                colors = coloresCampoForm(), value = nota, onValueChange = { nota = it.take(500) },
                                placeholder = { Text("¿Por qué no cuadra? (ej. vuelto de la Sra. Rosa)", color = c.textoSuave) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!d.cerrable) {
                        Text("🏢 Estás viendo todas las sedes. Cada local cuenta y cierra su propio cajón: elige una sede arriba para cerrar su caja.",
                            color = c.textoSuave, fontSize = 12.sp)
                    } else {
                        d.cierre?.let { ci ->
                            Text("Cerrada" + (ci.cerradoAt?.let { " el " + fechaHoraFin(it) } ?: "") + (ci.nota?.let { " · $it" } ?: ""),
                                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                        } ?: Text("Cuenta el efectivo y cierra el día.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                        BotonFin(
                            if (guardando) "Guardando…" else if (d.cierre != null) "↻ Actualizar cierre" else "🔒 Cerrar caja del día",
                            Modifier.fillMaxWidth().tourAncla("caja.cerrar"),
                            habilitado = !guardando && contadoNum != null,
                        ) {
                            val n = contadoNum ?: return@BotonFin
                            guardando = true
                            scope.launch {
                                val r = conIndicador(Gestion.GUARDANDO) { FinanzasRepo.cerrar(fecha, n, nota, actualizar = d.cierre != null) }
                                guardando = false
                                if (r.ok) { Toaster.exito(if (d.cierre != null) "Cierre actualizado" else "Caja cerrada"); recarga++ }
                                else Toaster.error(r.error ?: "No se pudo cerrar la caja")
                            }
                        }
                    }
                }
                // Movimientos del día: si el arqueo no cuadra, se revisan aquí mismo.
                TarjetaFin {
                    Row(Modifier.fillMaxWidth().clickable { verMovs = !verMovs }, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (verMovs) "▾ " else "▸ ", color = c.textoSuave, fontSize = 12.sp)
                        RotuloFin("Movimientos del día (${d.movimientos.size})")
                    }
                    if (verMovs) {
                        Spacer(Modifier.height(6.dp))
                        if (d.movimientos.isEmpty()) Text("Sin movimientos este día.", color = c.textoSuave, fontSize = 12.sp)
                        d.movimientos.forEach { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.createdAt?.let { horaFin(it) } ?: "—", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.width(48.dp))
                                Text(iconoMetodoFin(m.metodoPago), fontSize = 12.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(m.descripcion, color = c.texto, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                val esIn = m.tipo == "Ingreso"
                                Text((if (esIn) "+" else "−") + soles(m.monto).removePrefix("S/ "), color = if (esIn) c.ok else c.error,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Sania.dim.xxl))
            }
        }
    }
}

/** Timestamp (UTC) → "14:05" en la hora de la clínica. */
internal fun horaFin(ts: String): String = runCatching {
    val t = kotlinx.datetime.Instant.parse(ts).toLocalDateTime(pe.saniape.app.data.staff.ZONA_CLINICA)
    "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}.getOrDefault("—")

/** Timestamp (UTC) → "8 oct, 19:40" en la hora de la clínica. */
internal fun fechaHoraFin(ts: String): String = runCatching {
    val t = kotlinx.datetime.Instant.parse(ts).toLocalDateTime(pe.saniape.app.data.staff.ZONA_CLINICA)
    "${fechaCortaFin(t.date.toString())}, ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}.getOrDefault(ts)

