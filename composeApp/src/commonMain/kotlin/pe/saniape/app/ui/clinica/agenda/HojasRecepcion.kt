package pe.saniape.app.ui.clinica.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.CobroDelDia
import pe.saniape.app.data.staff.DiaHorarios
import pe.saniape.app.data.staff.FilaHorario
import pe.saniape.app.data.staff.FinanzasRepo
import pe.saniape.app.data.staff.HorariosDiaRepo
import pe.saniape.app.data.staff.Tramo
import pe.saniape.app.data.staff.deudasConAdelanto
import pe.saniape.app.data.staff.duracionTexto
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.horaDeMin
import pe.saniape.app.data.staff.monedaActiva
import pe.saniape.app.data.staff.tramoTexto
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.theme.Sania

// «🕘 Horarios» y «💰 Cobros de hoy» a un toque desde la agenda (20/11/2026,
// gemelos de HorariosDelDia.tsx y CobrosDeHoy.tsx de la web). Jonathan tras DALU:
// «imagínate que está sentada y le preguntan por horario… tendría que entrar a
// consulta y horario» y «se me complica ver los pagos de hoy».

@Composable
private fun CabeceraHoja(titulo: String, subtitulo: String?, onCerrar: () -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg)) {
        Text("← Volver", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(onClick = onCerrar).padding(vertical = 2.dp))
        Spacer(Modifier.height(2.dp))
        Text(titulo, color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
        subtitulo?.let { Text(it, color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 12.sp) }
    }
}

private fun minutosAhora(): Int {
    val t = Clock.System.now().toLocalDateTime(pe.saniape.app.data.staff.ZONA_CLINICA)
    return t.hour * 60 + t.minute
}

// ───────────────────────── 🕘 Horarios ─────────────────────────

@Composable
fun PantallaHorariosDia(fecha: String, esHoy: Boolean, onCerrar: () -> Unit) {
    val c = Sania.colors
    ManejarAtras(activo = true, onAtras = onCerrar)
    var dia by remember(fecha) { mutableStateOf<DiaHorarios?>(null) }
    var error by remember(fecha) { mutableStateOf(false) }
    var ahora by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(fecha) {
        runCatching { HorariosDiaRepo.cargar(fecha) }.onSuccess { dia = it }.onFailure { error = true }
    }
    LaunchedEffect(esHoy) {
        while (esHoy) { ahora = minutosAhora(); delay(60_000) }
        ahora = null
    }
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CabeceraHoja("🕘 Horarios", pe.saniape.app.data.staff.fechaLegibleCorta(fecha), onCerrar)
            val d = dia
            when {
                error -> Text("No se pudieron leer los horarios. Revisa tu conexión.", color = c.error, fontSize = 13.sp, modifier = Modifier.padding(Sania.dim.lg))
                d == null -> Text("Cargando horarios…", color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.padding(Sania.dim.lg))
                d.filas.isEmpty() -> Text("No hay profesionales activos.", color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.padding(Sania.dim.lg))
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { LeyendaHorarios(ahora) }
                    items(d.filas, key = { it.terapeutaId }) { f -> TarjetaHorario(f, d.rango, ahora) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeyendaHorarios(ahora: Int?) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        @Composable fun item(color: Color, borde: Boolean, texto: String) = Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 18.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(color)
                .then(if (borde) Modifier.border(1.dp, c.borde, RoundedCornerShape(3.dp)) else Modifier))
            Spacer(Modifier.width(5.dp)); Text(texto, color = c.textoSuave, fontSize = 11.sp)
        }
        item(c.okBg, true, "Atiende"); item(c.error, false, "Ocupado"); item(c.fondo, true, "No atiende / ausente")
        ahora?.let { item(c.navy, false, "Ahora · ${horaDeMin(it)}") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaHorario(f: FilaHorario, rango: Tramo, ahora: Int?) {
    val c = Sania.colors
    val (estado, fondoEstado, colorEstado) = when {
        f.ausente != null -> Triple(f.ausente, c.pendBg, c.pend)
        f.flexible -> Triple("📅 A demanda", c.chipBg, c.navy)
        f.turno.isNotEmpty() -> Triple(f.turno.joinToString(" · ") { tramoTexto(it) }, c.okBg, c.ok)
        else -> Triple("No atiende este día", c.fondo, c.textoSuave)
    }
    val ocupadoAhora = ahora?.let { a -> f.ocupados.firstOrNull { a >= it.ini && a < it.fin } }
    val libreAhora = ahora?.let { a -> f.libres.firstOrNull { a >= it.ini && a < it.fin } }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (f.atiende) c.superficie else c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (f.atiende) c.navy else c.textoSuave), contentAlignment = Alignment.Center) {
                Text(iniciales(f.nombre), color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(f.nombre, color = if (f.atiende) c.texto else c.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (f.atiende) when {
                    ocupadoAhora != null -> Text("● Ocupado hasta ${horaDeMin(ocupadoAhora.fin)}", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    libreAhora != null -> Text("● Libre ahora · hasta ${horaDeMin(libreAhora.fin)}", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(estado, color = colorEstado, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(fondoEstado).padding(horizontal = 10.dp, vertical = 3.dp))
        Spacer(Modifier.height(10.dp))
        BarraDiaApp(f, rango, ahora)
        if (f.atiende) {
            Spacer(Modifier.height(8.dp))
            if (f.ocupados.isNotEmpty()) FilaTramos("Ocupado", c.error, c.errorBg, f.ocupados.map { "${tramoTexto(it)} · ${duracionTexto(it.fin - it.ini)}" })
            FilaTramos("Libre", c.ok, c.okBg, f.libres.map { tramoTexto(it) }.ifEmpty { listOf("sin horas libres") })
            if (f.bloqueados.isNotEmpty()) FilaTramos("Permiso", c.textoSuave, c.fondo, f.bloqueados.map { tramoTexto(it) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaTramos(rotulo: String, color: Color, fondo: Color, textos: List<String>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(rotulo, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp).padding(top = 3.dp))
        textos.forEach { t ->
            Text(t, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun BarraDiaApp(f: FilaHorario, rango: Tramo, ahora: Int?) {
    val c = Sania.colors
    val total = (rango.fin - rango.ini).toFloat()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        fun x(m: Int) = w * ((m - rango.ini) / total)
        fun ancho(d: Int) = w * (d / total)
        Column {
            Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp)).background(c.fondo).border(1.dp, c.borde, RoundedCornerShape(8.dp))) {
                val atiende = when { f.ausente != null -> emptyList(); f.flexible -> listOf(rango); else -> f.turno }
                atiende.forEach { t -> Box(Modifier.offset(x = x(t.ini)).width(ancho(t.fin - t.ini)).fillMaxHeight().background(c.okBg)) }
                if (f.ausente == null) f.bloqueados.forEach { b -> Box(Modifier.offset(x = x(b.ini)).width(ancho(b.fin - b.ini)).fillMaxHeight().background(c.textoSuave.copy(alpha = 0.35f))) }
                // Una línea por hora: se lee dónde empieza y termina cada bloque.
                var m = rango.ini + 60
                while (m < rango.fin) { Box(Modifier.offset(x = x(m)).width(1.dp).fillMaxHeight().background(c.borde)); m += 60 }
                if (f.ausente == null) f.ocupados.forEach { o ->
                    Box(
                        Modifier.offset(x = x(o.ini)).width(ancho(o.fin - o.ini)).fillMaxHeight().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(5.dp)).background(c.error),
                        contentAlignment = Alignment.Center,
                    ) {
                        if ((o.fin - o.ini) / total >= 0.07f) Text(horaDeMin(o.ini, true), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                ahora?.takeIf { it > rango.ini && it < rango.fin }?.let { a ->
                    Box(Modifier.offset(x = x(a)).width(2.dp).fillMaxHeight().background(c.navy))
                }
            }
            // Escala: cada dos horas (en el celular no caben todas).
            Box(Modifier.fillMaxWidth().height(14.dp).padding(top = 2.dp)) {
                var h = rango.ini
                while (h <= rango.fin) {
                    Text(horaDeMin(h, true).removeSuffix(":00"), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = x(h) - 4.dp))
                    h += 120
                }
            }
        }
    }
}

private fun iniciales(n: String): String =
    n.replace(Regex("^(lic|dr|dra|mg|tm|ps|tec)\\.?\\s+", RegexOption.IGNORE_CASE), "").split(Regex("\\s+"))
        .filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

// ───────────────────────── 💰 Cobros de hoy ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaCobrosHoy(hoy: String, citas: List<CitaStaff>, onCerrar: () -> Unit) {
    val c = Sania.colors
    ManejarAtras(activo = true, onAtras = onCerrar)
    var cobros by remember { mutableStateOf<List<CobroDelDia>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hoy) {
        val (d, e) = FinanzasRepo.cierre(hoy)
        if (d != null) cobros = d.cobros else error = e ?: "No se pudieron leer los cobros de hoy."
    }
    val moneda = monedaActiva()
    val deudas = remember(citas, hoy) { deudasConAdelanto(citas, hoy) }
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CabeceraHoja("💰 Cobros de hoy", pe.saniape.app.data.staff.fechaLegibleCorta(hoy), onCerrar)
            val lista = cobros
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Sania.dim.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    error != null -> item { Text(error!!, color = c.error, fontSize = 13.sp) }
                    lista == null -> item { Text("Cargando los cobros de hoy…", color = c.textoSuave, fontSize = 13.sp) }
                    else -> {
                        val total = lista.sumOf { it.monto }
                        item {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.okBg).padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("COBRADO HOY", color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("${lista.size} ${if (lista.size == 1) "cobro" else "cobros"} a pacientes", color = c.textoSuave, fontSize = 12.sp)
                                }
                                Text(formatearDinero(total, moneda), color = c.ok, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        val porMetodo = lista.groupBy { it.metodoPago ?: "Efectivo" }.mapValues { (_, v) -> v.sumOf { it.monto } }.entries.sortedByDescending { it.value }
                        if (porMetodo.isNotEmpty()) item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                porMetodo.forEach { (metodo, monto) ->
                                    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(metodo.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(formatearDinero(monto, moneda), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        // Lo que TODAVÍA hay que cobrar va antes de la lista (es lo accionable).
                        if (deudas.isNotEmpty()) item {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.pendBg).padding(12.dp)) {
                                Text("DEJARON ADELANTO · FALTA PAGAR (${deudas.size})", color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                deudas.forEach { d ->
                                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(d.paciente, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${d.hora} · dejó ${formatearDinero(d.abonado, moneda)} de ${formatearDinero(d.costo, moneda)}", color = c.textoSuave, fontSize = 11.sp)
                                        }
                                        Text("falta ${formatearDinero(d.costo - d.abonado, moneda)}", color = c.pend, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        if (lista.isEmpty()) item {
                            Text("Todavía no hay cobros hoy.", color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                        }
                        items(lista.sortedByDescending { it.createdAt ?: "" }) { k ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(k.createdAt?.let { pe.saniape.app.ui.clinica.finanzas.horaFin(it) } ?: "—", color = c.textoSuave, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(k.paciente ?: "Paciente", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${k.descripcion ?: "Cobro"} · ${k.metodoPago ?: "Efectivo"}", color = c.textoSuave, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatearDinero(k.monto, k.moneda ?: moneda), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
