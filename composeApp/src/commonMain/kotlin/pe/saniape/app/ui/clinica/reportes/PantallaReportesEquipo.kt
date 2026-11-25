package pe.saniape.app.ui.clinica.reportes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.FilaAgendador
import pe.saniape.app.data.staff.FilaCierre
import pe.saniape.app.data.staff.Periodo
import pe.saniape.app.data.staff.ReportesEquipoRepo
import pe.saniape.app.data.staff.ResultadoArqueo
import pe.saniape.app.data.staff.SedeActiva
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.rangoDelMes
import pe.saniape.app.data.staff.rangoMesAnterior
import pe.saniape.app.data.staff.resumenCierres
import pe.saniape.app.data.staff.totalesAgendador
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.clinica.ChipSede
import pe.saniape.app.ui.clinica.agenda.modales.textoSoles
import pe.saniape.app.ui.theme.Sania

/*
 * Las dos pestañas de Reportes del 24/11/2026, como la web: 📞 Quién agendó y
 * 🧮 Cierres de caja (esta, solo con permiso de finanzas).
 */

/** Cabecera navy + pestañas + periodo (este mes / mes anterior) y el contenido. */
@Composable
private fun MarcoReporte(
    ctx: ContextoStaff,
    titulo: String,
    vista: VistaReportes,
    onSalir: () -> Unit,
    onVista: (VistaReportes) -> Unit,
    periodo: Periodo,
    onPeriodo: (Periodo) -> Unit,
    contenido: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val c = Sania.colors
    val hoy = remember { hoyClinicaIso() }
    ManejarAtras(activo = true, onAtras = onSalir)
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg)) {
                Text("← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                    modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { onSalir() }.padding(vertical = 2.dp))
                Spacer(Modifier.height(2.dp))
                Text(titulo, color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                ChipSede(Modifier.padding(top = 4.dp))
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Sania.dim.lg, vertical = Sania.dim.md),
                verticalArrangement = Arrangement.spacedBy(Sania.dim.sm),
            ) {
                item(key = "vistas") { FilaVistasReportes(ctx, vista, onVista) }
                item(key = "periodo") {
                    val mes = rangoDelMes(hoy); val anterior = rangoMesAnterior(hoy)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChipPeriodo("Este mes", periodo == mes) { onPeriodo(mes) }
                        ChipPeriodo("Mes anterior", periodo == anterior) { onPeriodo(anterior) }
                        Text("${fechaCorta(periodo.desde)} – ${fechaCorta(periodo.hasta)}", color = c.textoSuave, fontSize = 12.sp)
                    }
                }
                contenido()
            }
        }
    }
}

@Composable
private fun ChipPeriodo(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

private fun fechaCorta(iso: String): String = iso.takeIf { it.length >= 10 }?.let { "${it.substring(8, 10)}/${it.substring(5, 7)}" } ?: iso

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
    ) { contenido() }
}

@Composable
private fun Mensaje(texto: String, error: Boolean = false) {
    val c = Sania.colors
    Text(texto, color = if (error) c.error else c.textoSuave, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
}

// ── 📞 Quién agendó ─────────────────────────────────────────────────────────

@Composable
fun PantallaQuienAgendo(ctx: ContextoStaff, onSalir: () -> Unit, onVista: (VistaReportes) -> Unit) {
    val c = Sania.colors
    var periodo by remember { mutableStateOf(rangoDelMes(hoyClinicaIso())) }
    var filas by remember { mutableStateOf<List<FilaAgendador>?>(null) }
    var error by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    LaunchedEffect(periodo) {
        cargando = true
        val r = ReportesEquipoRepo.quienAgendo(periodo)
        error = r == null; filas = r; cargando = false
    }
    MarcoReporte(ctx, "📞 Quién agendó", VistaReportes.Agendo, onSalir, onVista, periodo, { periodo = it }) {
        item(key = "ayuda") {
            Text("Las citas que agendó cada persona en el periodo y cuántas asistieron. Cuenta por la fecha en que se agendó.",
                color = c.textoSuave, fontSize = 12.sp)
        }
        val lista = filas.orEmpty()
        when {
            cargando -> item(key = "cargando") { Mensaje("Cargando…") }
            error -> item(key = "error") { Mensaje("No se pudo leer el reporte. Revisa tu conexión.", error = true) }
            lista.isEmpty() -> item(key = "vacio") { Mensaje("No se agendaron citas en este periodo.") }
            else -> {
                items(lista, key = { it.id ?: "sin-persona" }) { FilaAgendadorTarjeta(it) }
                item(key = "total") { FilaAgendadorTarjeta(totalesAgendador(lista), total = true) }
                item(key = "nota") {
                    Text("Se registra quién agenda desde el 24/11/2026: las citas anteriores salen como «sin registro».",
                        color = c.textoSuave, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun FilaAgendadorTarjeta(f: FilaAgendador, total: Boolean = false) {
    val c = Sania.colors
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.nombre, color = if (f.id == null) c.textoSuave else c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (f.rol.isNotBlank()) Text(f.rol, color = c.textoSuave, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${f.agendadas}", color = c.navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (total) "agendadas en total" else "agendadas", color = c.textoSuave, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dato("Asistieron", "${f.asistieron}", c.ok)
            Dato("No asistieron", "${f.noAsistieron}", c.error)
            Dato("Canceladas", "${f.canceladas}", c.texto)
            Dato("Por venir", "${f.porVenir}", c.texto)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dato("% asistencia", f.asistencia?.let { "$it%" } ?: "—", c.texto)
            Dato("Pacientes nuevos", "${f.pacientesNuevos}", c.texto)
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, color: androidx.compose.ui.graphics.Color) {
    val c = Sania.colors
    Column {
        Text(valor, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(etiqueta, color = c.textoSuave, fontSize = 10.sp)
    }
}

// ── 🧮 Cierres de caja ──────────────────────────────────────────────────────

@Composable
fun PantallaCierresCaja(ctx: ContextoStaff, onSalir: () -> Unit, onVista: (VistaReportes) -> Unit) {
    val c = Sania.colors
    val sedeEstado by SedeActiva.estado.collectAsState()
    val sede = sedeEstado.filtro?.sedeId
    var periodo by remember { mutableStateOf(rangoDelMes(hoyClinicaIso())) }
    var datos by remember { mutableStateOf<Pair<List<FilaCierre>, Map<String, String>>?>(null) }
    var error by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var soloDiferencias by remember { mutableStateOf(false) }
    LaunchedEffect(periodo, sede) {
        cargando = true
        val r = ReportesEquipoRepo.cierres(periodo, sede)
        error = r == null; datos = r; cargando = false
    }
    MarcoReporte(ctx, "🧮 Cierres de caja", VistaReportes.Cierres, onSalir, onVista, periodo, { periodo = it }) {
        val todas = datos?.first.orEmpty()
        val nombres = datos?.second.orEmpty()
        when {
            cargando -> item(key = "cargando") { Mensaje("Cargando…") }
            error -> item(key = "error") { Mensaje("No se pudo leer el reporte. Revisa tu conexión.", error = true) }
            todas.isEmpty() -> item(key = "vacio") { Mensaje("No hay cierres de caja en este periodo.") }
            else -> {
                val r = resumenCierres(todas)
                item(key = "resumen") {
                    Tarjeta {
                        Text("${r.cierres} cierres · ${r.cuadraron} cuadraron · ${r.conDiferencia} con diferencia",
                            color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Dato("Ingresos", textoSoles(r.ingresos), c.ok)
                            Dato("Egresos", textoSoles(r.egresos), c.error)
                            Dato("Retirado", textoSoles(r.retirado), c.texto)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Dato("Sobró", textoSoles(r.sobrante), c.info)
                            Dato("Faltó", textoSoles(r.faltante), c.error)
                        }
                    }
                }
                item(key = "filtro") {
                    Row { ChipPeriodo(if (soloDiferencias) "✓ Solo los que no cuadraron" else "Solo los que no cuadraron", soloDiferencias) { soloDiferencias = !soloDiferencias } }
                }
                val filas = if (soloDiferencias) todas.filter { it.resultado != ResultadoArqueo.CUADRO } else todas
                items(filas, key = { it.id }) { f -> FilaCierreTarjeta(f, f.cerradoPor?.let { nombres[it] }) }
            }
        }
    }
}

@Composable
private fun FilaCierreTarjeta(f: FilaCierre, cerro: String?) {
    val c = Sania.colors
    val (fg, bg) = when (f.resultado) {
        ResultadoArqueo.CUADRO -> c.ok to c.okBg
        ResultadoArqueo.SOBRA -> c.info to c.infoBg
        ResultadoArqueo.FALTA -> c.error to c.errorBg
    }
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pe.saniape.app.data.staff.fechaLegibleCorta(f.fecha), color = c.texto, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(f.resultado.etiqueta + if (f.resultado != ResultadoArqueo.CUADRO) " ${textoSoles(kotlin.math.abs(f.diferencia))}" else "",
                color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dato("Ingresos", textoSoles(f.ingresos), c.ok)
            Dato("Egresos", textoSoles(f.egresos), c.error)
            Dato("Esperado", textoSoles(f.esperado), c.texto)
            Dato("Contado", textoSoles(f.contado), c.texto)
        }
        f.retirado?.takeIf { it > 0 }?.let {
            Text("↘ Retirado al cerrar: ${textoSoles(it)}", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        cerro?.takeIf { it.isNotBlank() }?.let { Text("Cerró: $it", color = c.textoSuave, fontSize = 12.sp) }
        if (f.nota.isNotBlank()) Text("📝 ${f.nota}", color = c.textoSuave, fontSize = 12.sp)
    }
}
